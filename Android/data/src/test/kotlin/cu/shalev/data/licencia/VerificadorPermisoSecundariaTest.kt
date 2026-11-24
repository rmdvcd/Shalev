package cu.shalev.data.licencia

import cu.shalev.data.sync.LicenciaDto
import cu.shalev.domain.model.LicenciaPrincipal
import cu.shalev.licencia.TrustDirectory
import cu.shalev.licencia.crypto.B64
import cu.shalev.licencia.crypto.EcP256
import cu.shalev.licencia.redeem.RedeemContract
import cu.shalev.licencia.secondary.FirmanteLicencia
import cu.shalev.licencia.secondary.SubToken
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Pruebas de ataque para la corrección D1 (IMPLEMENTACION_SHALEV.md §3.1, §3.2 y §3.3):
 *  1. Permiso firmado con clave del atacante -> bloqueado.
 *  2. Permiso quitado -> bloqueado (fail-closed).
 *  3. Cuota alterada -> bloqueado.
 *  4. OKM incorrecto -> descifrado falla -> bloqueado.
 *  5. Huella ajena -> bloqueado.
 *  6. Flujo legítimo de canje anclado -> aceptado.
 */
class VerificadorPermisoSecundariaTest {

    private val ahora = Instant.parse("2026-11-20T12:00:00Z")
    // `Clock` es la interfaz de `cu.shalev.core.time`, no un lambda: el verificador llama a `clock.now()`.
    private val clock = cu.shalev.core.time.Clock { ahora }
    private val deviceIdSecundaria = "SPVI:secundaria01"
    private val deviceIds = object : DeviceIdProvider {
        override val deviceId: String = deviceIdSecundaria
    }

    private val verificador = VerificadorPermisoSecundaria(deviceIds, clock)

    // Claves simuladas
    private val glRedeemKey = EcP256.generate()
    private val primaryDeviceKey = EcP256.generate()
    private val attackerKey = EcP256.generate()

    private val licenseId = "7c9e6679-7425-40de-944b-e07fc1f90ae7"

    @Before
    fun setUp() {
        // El contrato real del directorio es (v, seq, emitidaEn, claves, endpointBase, appIds): no hay `id`,
        // `alg` ni `validaHasta`, y `desde` es obligatorio.
        val claveDirectorio = TrustDirectory.Clave(
            kid = "gl-redeem-v1",
            uso = "canje",
            spkiDer = glRedeemKey.public.encoded,
            huella = EcP256.fingerprint(glRedeemKey.public.encoded),
            desde = 1_700_000_000,
            hasta = null,
            estado = "vigente",
        )
        val dir = TrustDirectory.Directorio(
            v = 1,
            seq = 1,
            emitidaEn = 1_763_668_800,
            claves = listOf(claveDirectorio),
            endpointBase = "https://example.com",
            appIds = emptyMap(),
        )
        TrustDirectory.effectiveDirectory = dir
    }

    private fun firmanteDe(par: java.security.KeyPair): FirmanteLicencia = object : FirmanteLicencia {
        override fun publicSpki(): ByteArray = par.public.encoded
        override fun firmar(datos: ByteArray): ByteArray = EcP256.sign(par.private, datos)
    }

    private fun crearLicenciaCanjeJson(
        secundarias: Int = 3,
        devicePubKey: java.security.KeyPair = primaryDeviceKey,
    ): String {
        val spkiB64 = B64.enc(devicePubKey.public.encoded)
        val thumbprint = RedeemContract.deviceThumbprint(devicePubKey.public.encoded)

        val transcript = RedeemContract.bytesFirmaLicencia(
            v = 1,
            licenseId = licenseId,
            appId = "SHALEV-ANDROID",
            type = "MENSUAL",
            status = "ACTIVA",
            issuedAt = "2026-11-20T12:00:00Z",
            expiresAt = "2026-12-20T12:00:00Z",
            deviceThumbprint = thumbprint,
            redemptionId = "redem-123",
            transferredFrom = null,
            secundarias = secundarias,
            devicePublicKey = spkiB64,
        )

        val sig = B64.enc(EcP256.sign(glRedeemKey.private, transcript))

        val licObj = JSONObject().apply {
            put("v", 1)
            put("id", licenseId)
            put("appId", "SHALEV-ANDROID")
            put("type", "MENSUAL")
            put("status", "ACTIVA")
            put("issuedAt", "2026-11-20T12:00:00Z")
            put("expiresAt", "2026-12-20T12:00:00Z")
            put("deviceThumbprint", thumbprint)
            put("redemptionId", "redem-123")
            put("secundarias", secundarias)
            put("devicePublicKey", spkiB64)
        }

        val envelope = JSONObject().apply {
            put("kid", "gl-redeem-v1")
            put("sig", sig)
            put("license", licObj)
        }

        return envelope.toString()
    }

    @Test
    fun ataque1_permisoFirmadoConClaveDelAtacanteEsBloqueado() {
        val licJson = crearLicenciaCanjeJson(secundarias = 3)
        // El atacante emite el sub-token con su propia clave privada
        val subTokenAtacante = SubToken.emitir(
            firmante = firmanteDe(attackerKey),
            licenciaId = licenseId,
            secundariasPermitidas = 3,
            venceEn = ahora.plusSeconds(86400 * 30),
            dispositivo = deviceIdSecundaria,
            ranura = 1,
        )

        val dto = LicenciaDto(
            clase = "ACTIVA",
            subToken = subTokenAtacante,
            firmaPub = B64.enc(attackerKey.public.encoded),
            ranura = 1,
            permitidas = 3,
            primariaLicenseJson = licJson,
        )

        val res = verificador.verificar(dto)
        assertEquals(LicenciaPrincipal.Clase.BLOQUEADA.name, res.clase)
    }

    @Test
    fun ataque2_permisoQuitadoEsBloqueado() {
        val licJson = crearLicenciaCanjeJson(secundarias = 3)
        // Sin subToken
        val dto = LicenciaDto(
            clase = "ACTIVA",
            subToken = null,
            firmaPub = B64.enc(primaryDeviceKey.public.encoded),
            ranura = 1,
            permitidas = 3,
            primariaLicenseJson = licJson,
        )

        val res = verificador.verificar(dto)
        assertEquals(LicenciaPrincipal.Clase.BLOQUEADA.name, res.clase)
    }

    @Test
    fun ataque3_cuotaAlteradaEsBloqueada() {
        val licJson = crearLicenciaCanjeJson(secundarias = 2) // Licencia solo permite 2
        // Sub-token emitido reclamando 5 secundarias
        val subTokenAlterado = SubToken.emitir(
            firmante = firmanteDe(primaryDeviceKey),
            licenciaId = licenseId,
            secundariasPermitidas = 5,
            venceEn = ahora.plusSeconds(86400 * 30),
            dispositivo = deviceIdSecundaria,
            ranura = 1,
        )

        val dto = LicenciaDto(
            clase = "ACTIVA",
            subToken = subTokenAlterado,
            firmaPub = B64.enc(primaryDeviceKey.public.encoded),
            ranura = 1,
            permitidas = 5,
            primariaLicenseJson = licJson,
        )

        val res = verificador.verificar(dto)
        assertEquals(LicenciaPrincipal.Clase.BLOQUEADA.name, res.clase)
    }

    @Test
    fun ataque4_okmIncorrectoEnSpvi2EsBloqueado() {
        // En SPVI2, divulgar un OKM corrupto o falso debe hacer que el AES-GCM falle y la licencia quede bloqueada
        val dto = LicenciaDto(
            clase = "ACTIVA",
            subToken = "token.falso",
            firmaPub = B64.enc(attackerKey.public.encoded),
            spvi2 = "SPVI2:" + "A".repeat(210),
            okm = B64.urlNoPad(ByteArray(44) { 0x55 }),
            primariaDeviceId = "SPVI:primaria12345",
            primariaDevicePub = B64.urlNoPad(ByteArray(33) { 0x02 }),
        )

        val res = verificador.verificar(dto)
        assertEquals(LicenciaPrincipal.Clase.BLOQUEADA.name, res.clase)
    }

    @Test
    fun ataque5_huellaAjenaEnSpvi2EsBloqueado() {
        // Huella que no corresponde al deviceId de la primaria
        val dto = LicenciaDto(
            clase = "ACTIVA",
            subToken = "token.falso",
            spvi2 = "SPVI2:" + "A".repeat(210),
            okm = B64.urlNoPad(ByteArray(44) { 0x11 }),
            primariaDeviceId = "SPVI:otraPrimaria",
            primariaDevicePub = B64.urlNoPad(ByteArray(33) { 0x03 }),
        )

        val res = verificador.verificar(dto)
        assertEquals(LicenciaPrincipal.Clase.BLOQUEADA.name, res.clase)
    }

    @Test
    fun flujoLegitimo_canjeAncladoEsAceptado() {
        val licJson = crearLicenciaCanjeJson(secundarias = 3)
        val subTokenValido = SubToken.emitir(
            firmante = firmanteDe(primaryDeviceKey),
            licenciaId = licenseId,
            secundariasPermitidas = 3,
            venceEn = ahora.plusSeconds(86400 * 30),
            dispositivo = deviceIdSecundaria,
            ranura = 2,
        )

        val dto = LicenciaDto(
            clase = "ACTIVA",
            subToken = subTokenValido,
            firmaPub = B64.enc(primaryDeviceKey.public.encoded),
            ranura = 2,
            permitidas = 3,
            primariaLicenseJson = licJson,
        )

        val res = verificador.verificar(dto)
        assertEquals("ACTIVA", res.clase)
        assertEquals(2, res.ranura)
        assertEquals(3, res.permitidas)
    }
}
