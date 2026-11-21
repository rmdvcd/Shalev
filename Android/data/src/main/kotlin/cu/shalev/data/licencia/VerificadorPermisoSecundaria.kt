package cu.shalev.data.licencia

import cu.shalev.core.time.Clock
import cu.shalev.data.sync.LicenciaDto
import cu.shalev.domain.model.LicenciaPrincipal
import cu.shalev.licencia.LicenseTrust
import cu.shalev.licencia.TrustDirectory
import cu.shalev.licencia.contract.GlContract
import cu.shalev.licencia.crypto.AesGcm
import cu.shalev.licencia.crypto.B64
import cu.shalev.licencia.crypto.EcP256
import cu.shalev.licencia.redeem.RedeemContract
import cu.shalev.licencia.secondary.SubToken
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 0.31.0 y Corrección D1 (IMPLEMENTACION_SHALEV.md §3.1 y §3.2):
 * Comprueba el **permiso firmado** que la app principal manda junto con su licencia,
 * exigiendo anclaje criptográfico estricto a la firma de GL (fail-closed):
 *
 *  - Sin permiso presente: la licencia queda **bloqueada** (nunca se acepta sin permiso).
 *  - §3.1 Canje: anclado con la licencia primaria firmada por el servicio GL (`primariaLicenseJson`),
 *    de donde se extrae `devicePublicKey` (cuyo SHA-256 coincide con `deviceThumbprint`).
 *    El sub-token se valida exclusivamente con ese `devicePublicKey` anclado (rechaza claves ajenas).
 *  - §3.2 SPVI2: divulga `okm` (44 B) + `spvi2` + `primariaDeviceId` + `primariaDevicePub` (33 B).
 *    La secundaria verifica la firma GL de `SPVI2`, descifra con `okm`, comprueba la huella
 *    del cuerpo contra `primariaDeviceId` y `primariaDevicePub`, y comprueba que la cuota autorizada
 *    coincida con el permiso firmado.
 *  - Ataques detectados: clave del atacante, permiso quitado, cuota alterada, okm incorrecto o huella ajena
 *    resultan indefectiblemente en [LicenciaPrincipal.Clase.BLOQUEADA].
 */
@Singleton
class VerificadorPermisoSecundaria @Inject constructor(
    private val deviceIds: DeviceIdProvider,
    private val clock: Clock,
) {

    fun verificar(licencia: LicenciaDto): LicenciaDto {
        // Una licencia ya bloqueada se queda como está.
        if (licencia.clase == LicenciaPrincipal.Clase.BLOQUEADA.name) return licencia

        // D1: Sin permiso presente, queda bloqueada (fail-closed; nunca se acepta sin permiso).
        val token = licencia.subToken?.takeIf { it.isNotBlank() } ?: return bloquear()

        // Determinar anclaje de la clave pública autorizada
        val resolucion = resolverClaveAnclada(licencia) ?: return bloquear()
        val firmaBytesAnclada = resolucion.firmaSpki
        val cuotaPermitidaPorLicencia = resolucion.secundariasPermitidas

        // Verificar sub-token con la clave anclada
        val datos = runCatching {
            SubToken.verificar(
                token = token,
                firmaSpki = firmaBytesAnclada,
                dispositivoEsperado = deviceIds.deviceId,
                ahora = clock.now(),
                cuotaMaxima = minOf(GlContract.SECUNDARIAS_MAX, cuotaPermitidaPorLicencia),
                esClaveComprometida = { spki ->
                    TrustDirectory.effectiveDirectory?.isKeyCompromised(spki) == true
                },
            )
        }.getOrNull() ?: return bloquear()

        // Comprobación de cuota y ranura
        if (datos.permitidas > cuotaPermitidaPorLicencia) return bloquear()
        if (licencia.permitidas != null && licencia.permitidas != datos.permitidas) return bloquear()
        if (licencia.ranura != null && licencia.ranura != datos.ranura) return bloquear()

        return licencia.copy(ranura = datos.ranura, permitidas = datos.permitidas)
    }

    private data class ClaveAnclada(
        val firmaSpki: ByteArray,
        val secundariasPermitidas: Int,
    )

    /**
     * Resuelve la clave de la principal verificando su anclaje a una firma de GL (§3.1 Canje o §3.2 SPVI2).
     */
    private fun resolverClaveAnclada(licencia: LicenciaDto): ClaveAnclada? {
        // Caso §3.1: Primaria con licencia de canje
        if (!licencia.primariaLicenseJson.isNullOrBlank()) {
            return verificarAnclajeCanje(licencia.primariaLicenseJson, licencia.firmaPub)
        }

        // Caso §3.2: Primaria con licencia SPVI2
        if (!licencia.spvi2.isNullOrBlank() && !licencia.okm.isNullOrBlank() &&
            !licencia.primariaDeviceId.isNullOrBlank() && !licencia.primariaDevicePub.isNullOrBlank()
        ) {
            return verificarAnclajeSpvi2(
                spvi2 = licencia.spvi2,
                okmB64 = licencia.okm,
                primariaDeviceId = licencia.primariaDeviceId,
                primariaDevicePubB64 = licencia.primariaDevicePub,
                firmaPubDto = licencia.firmaPub,
                cuotaDeclarada = licencia.permitidas,
            )
        }

        return null
    }

    private fun verificarAnclajeCanje(licenciaJson: String, firmaPubDto: String?): ClaveAnclada? = runCatching {
        val root = JSONObject(licenciaJson)
        val envelope = if (root.has("license") && root.get("license") is JSONObject) root.getJSONObject("license") else root
        val licData = envelope.getJSONObject("license")
        val sigB64 = envelope.getString("sig")
        val kid = envelope.optString("kid", "gl-redeem-v1")

        // 1. Clave de canje autorizada desde el directorio
        val redeemKey = TrustDirectory.effectiveDirectory?.redeemKey(kid)
            ?: TrustDirectory.effectiveDirectory?.canjeKeys()?.firstOrNull()
            ?: return null

        val v = licData.getInt("v")
        val licenseId = licData.getString("id")
        val appId = licData.getString("appId")
        val type = licData.getString("type")
        val status = licData.getString("status")
        val issuedAt = licData.getString("issuedAt")
        val expiresAt = if (licData.has("expiresAt") && !licData.isNull("expiresAt")) licData.getString("expiresAt") else null
        val deviceThumbprint = licData.getString("deviceThumbprint")
        val redemptionId = licData.getString("redemptionId")
        val transferredFrom = if (licData.has("transferredFrom") && !licData.isNull("transferredFrom")) licData.getString("transferredFrom") else null
        val secundarias = licData.optInt("secundarias", 0)
        val devicePublicKeyB64 = licData.getString("devicePublicKey")
        val devicePublicKeyBytes = B64.dec(devicePublicKeyB64)

        // 2. Verificar firma del servicio GL sobre el transcript canónico
        val bytesFirmados = RedeemContract.bytesFirmaLicencia(
            v = v,
            licenseId = licenseId,
            appId = appId,
            type = type,
            status = status,
            issuedAt = issuedAt,
            expiresAt = expiresAt,
            deviceThumbprint = deviceThumbprint,
            redemptionId = redemptionId,
            transferredFrom = transferredFrom,
            secundarias = secundarias,
            devicePublicKey = devicePublicKeyB64,
        )

        val firmaValida = runCatching {
            EcP256.verify(EcP256.publicKey(redeemKey), bytesFirmados, B64.dec(sigB64))
        }.getOrDefault(false)
        if (!firmaValida) return null

        // 3. Verificar SHA-256(devicePublicKey) == deviceThumbprint
        val thumbprintCalculado = RedeemContract.deviceThumbprint(devicePublicKeyBytes)
        if (thumbprintCalculado != deviceThumbprint) return null

        // 4. Si viene firmaPub en el DTO, debe coincidir exactamente con devicePublicKey firmado por el servicio
        if (!firmaPubDto.isNullOrBlank()) {
            val dtoPubBytes = B64.dec(firmaPubDto)
            if (!dtoPubBytes.contentEquals(devicePublicKeyBytes)) return null
        }

        ClaveAnclada(devicePublicKeyBytes, secundarias)
    }.getOrNull()

    private fun verificarAnclajeSpvi2(
        spvi2: String,
        okmB64: String,
        primariaDeviceId: String,
        primariaDevicePubB64: String,
        firmaPubDto: String?,
        cuotaDeclarada: Int?,
    ): ClaveAnclada? = runCatching {
        if (!spvi2.startsWith("SPVI2:")) return null
        val b64 = spvi2.removePrefix("SPVI2:").filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        if (b64.length != 210) return null
        val raw = B64.urlDec(b64)
        if (raw.size != 157) return null

        val epk = raw.copyOfRange(0, 33)
        val ctAndTag = raw.copyOfRange(33, 93)
        val firmaRs = raw.copyOfRange(93, 157)

        val dominioEpkCtTag = "SPVI-L2|".toByteArray(StandardCharsets.UTF_8) + raw.copyOfRange(0, 93)
        val firmaDer = EcP256.rawToDer(firmaRs)

        // 1. Verificar firma de GL sobre SPVI2
        val keys = LicenseTrust.signSpkis()
        val firmaValida = keys.any { k ->
            runCatching { EcP256.verify(EcP256.publicKey(k), dominioEpkCtTag, firmaDer) }.getOrDefault(false)
        }
        if (!firmaValida) return null

        // 2. Descifrar con OKM divulgado (32 B clave + 12 B IV)
        val okm = B64.urlDec(okmB64)
        if (okm.size != 44) return null
        val claveOkm = okm.copyOfRange(0, 32)
        val ivOkm = okm.copyOfRange(32, 44)
        val aad = "SPVI-L2|".toByteArray(StandardCharsets.UTF_8) + epk

        val cuerpoClaro = AesGcm.decrypt(claveOkm, ivOkm, aad, ctAndTag)
        if (cuerpoClaro.size != 44) return null
        if (cuerpoClaro[0].toInt() != 0x02) return null

        // 3. Verificar huella del cuerpo contra primariaDeviceId y primariaDevicePub (33 B)
        val pubPrimariaComprimida = B64.urlDec(primariaDevicePubB64)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("SPVI-L2|".toByteArray(StandardCharsets.UTF_8))
        digest.update(primariaDeviceId.toByteArray(StandardCharsets.UTF_8))
        digest.update("|".toByteArray(StandardCharsets.UTF_8))
        digest.update(pubPrimariaComprimida)
        val huellaEsperada = digest.digest().copyOfRange(0, 16)

        val huellaCuerpo = cuerpoClaro.copyOfRange(17, 33)
        if (!huellaCuerpo.contentEquals(huellaEsperada)) return null

        val secundariasPermitidas = cuerpoClaro[35].toInt() and 0xFF
        if (cuotaDeclarada != null && cuotaDeclarada > secundariasPermitidas) return null

        // 4. Clave anclada derivada de la clave pública del dispositivo de la primaria
        val pubKey = EcP256.descomprimir(pubPrimariaComprimida)
        val primariaSpki = pubKey.encoded
        if (!firmaPubDto.isNullOrBlank()) {
            val dtoPubBytes = B64.dec(firmaPubDto)
            if (!dtoPubBytes.contentEquals(primariaSpki)) return null
        }

        ClaveAnclada(primariaSpki, secundariasPermitidas)
    }.getOrNull()

    /** Lo único que la secundaria puede afirmar sin un permiso válido: la licencia de la principal no la cubre. */
    private fun bloquear() = LicenciaDto(LicenciaPrincipal.Clase.BLOQUEADA.name)
}
