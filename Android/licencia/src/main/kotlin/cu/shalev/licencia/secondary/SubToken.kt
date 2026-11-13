package cu.shalev.licencia.secondary

import cu.shalev.licencia.crypto.B64
import cu.shalev.licencia.crypto.EcP256
import java.time.Instant

/**
 * Clave de firma de la app **principal** (la que tiene la licencia). Firma los sub-tokens que autorizan a las apps
 * secundarias.
 */
interface FirmanteLicencia {
    /** SPKI DER de la clave pública de firma. Viaja con el sub-token para que la secundaria pueda comprobarlo. */
    fun publicSpki(): ByteArray

    /** Firma ECDSA P-256/SHA-256 en DER (el mismo formato que usa GL en `sig`). */
    fun firmar(datos: ByteArray): ByteArray
}

/**
 * Sub-token de app secundaria (mitigación 7 del sistema de licencias de GL, Opción A): la cuota de apps secundarias
 * deja de ser una auto-declaración y pasa a ser **un permiso firmado por la app principal**.
 *
 * Formato (`SISTEMA_LICENCIAS_CLIENTES.md` §8.4):
 * ```
 * <licenseId>|<deviceId secundaria>|<ranura>|<permitidas>|<venceEn ISO-8601 | PERPETUA>.<firma Base64 DER>
 * ```
 * - `ranura`: 1..`permitidas` (la cuota que trae la licencia).
 * - `venceEn`: el de la licencia principal; `PERPETUA` = sin vencimiento.
 * - La firma es ECDSA P-256/SHA-256 (DER) de la principal sobre el texto de arriba, tal cual.
 *
 * Shalev lo usa al vincular: la principal emite un sub-token para el teléfono que se vincula y la secundaria lo
 * verifica antes de dar por buena la licencia recibida (`LicenciaPrincipal`). El contrato es el mismo en las tres apps.
 */
object SubToken {
    const val PERPETUA = "PERPETUA"
    private const val SEPARADOR_CAMPOS = "|"
    private const val SEPARADOR_FIRMA = "."
    private const val LARGO_MAXIMO = 4096

    data class Datos(
        val licenciaId: String,
        val dispositivo: String,
        val ranura: Int,
        val permitidas: Int,
        val venceEn: Instant?,
    ) {
        /** Un sub-token perpetuo o con vencimiento posterior a [ahora] sigue autorizando. */
        fun vigente(ahora: Instant): Boolean = venceEn == null || ahora.isBefore(venceEn)
    }

    /**
     * Emite el sub-token de la ranura [ranura] para el dispositivo [dispositivo]. La cuota y el vencimiento salen de
     * la licencia instalada en la principal ([secundariasPermitidas] 0–10, [venceEn] `null` = perpetua).
     */
    fun emitir(
        firmante: FirmanteLicencia,
        licenciaId: String,
        secundariasPermitidas: Int,
        venceEn: Instant?,
        dispositivo: String,
        ranura: Int,
    ): String {
        require(secundariasPermitidas in 0..10) { "cuota inválida" }
        require(ranura in 1..secundariasPermitidas) { "ranura fuera de la cuota" }
        require(licenciaId.isNotBlank() && licenciaId.length <= 64) { "licencia inválida" }
        require(dispositivo.isNotBlank() && dispositivo.length <= 128) { "dispositivo inválido" }
        require(!tieneSeparadores(licenciaId) && !tieneSeparadores(dispositivo)) { "caracteres no admitidos" }
        val texto = listaDe(licenciaId, dispositivo, ranura, secundariasPermitidas, venceEn)
        return texto + SEPARADOR_FIRMA + B64.enc(firmante.firmar(texto.toByteArray(Charsets.UTF_8)))
    }

    /**
     * Verifica un sub-token con la clave pública de la principal. Devuelve sus datos **solo** si todo cuadra:
     * firma, dispositivo, ranura dentro de `1..permitidas`, `permitidas` dentro de la cuota de la licencia
     * ([cuotaMaxima]), formato correcto y no vencido. Cualquier otra cosa devuelve `null` (nunca lanza).
     */
    fun verificar(
        token: String,
        firmaSpki: ByteArray,
        dispositivoEsperado: String,
        ahora: Instant,
        cuotaMaxima: Int = 10,
    ): Datos? = runCatching {
        if (token.isEmpty() || token.length > LARGO_MAXIMO) return null
        val corte = token.lastIndexOf(SEPARADOR_FIRMA)
        if (corte <= 0 || corte == token.length - 1) return null
        val texto = token.substring(0, corte)
        val firma = B64.dec(token.substring(corte + 1))
        val campos = texto.split(SEPARADOR_CAMPOS)
        if (campos.size != 5) return null
        if (!EcP256.verify(EcP256.publicKey(firmaSpki), texto.toByteArray(Charsets.UTF_8), firma)) return null
        val ranura = campos[2].toIntOrNull() ?: return null
        val permitidas = campos[3].toIntOrNull() ?: return null
        if (permitidas !in 0..10 || ranura !in 1..permitidas || permitidas > cuotaMaxima) return null
        if (campos[1] != dispositivoEsperado) return null
        val vence = if (campos[4] == PERPETUA) null else Instant.parse(campos[4])
        Datos(campos[0], campos[1], ranura, permitidas, vence).takeIf { it.vigente(ahora) }
    }.getOrNull()

    /** `true` si el texto trae caracteres que rompen el formato (los separadores). */
    private fun tieneSeparadores(texto: String): Boolean = texto.contains(SEPARADOR_CAMPOS) || texto.contains(SEPARADOR_FIRMA)

    private fun listaDe(licenciaId: String, dispositivo: String, ranura: Int, permitidas: Int, venceEn: Instant?): String =
        listOf(licenciaId, dispositivo, ranura.toString(), permitidas.toString(), venceEn?.toString() ?: PERPETUA)
            .joinToString(SEPARADOR_CAMPOS)
}
