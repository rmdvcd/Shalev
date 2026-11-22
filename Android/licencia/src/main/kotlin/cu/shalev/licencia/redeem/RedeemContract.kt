package cu.shalev.licencia.redeem

import cu.shalev.licencia.crypto.B64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Constantes y funciones de canonicalización del protocolo de canje GL v1 (Anexo B / S7).
 */
object RedeemContract {
    const val VERSION_SERVICIO = 1
    const val VERSION_LICENCIA = 1
    const val VERSION_ESTADO = 1

    const val DOMINIO_SOLICITUD_CANJE = "GL-REDEEM-REQUEST1"
    const val DOMINIO_SOLICITUD_ESTADO = "GL-REDEEM-STATUS-REQUEST1"
    const val DOMINIO_LICENCIA = "GL-REDEEM-LICENSE1"
    const val DOMINIO_ESTADO = "GL-REDEEM-STATUS1"

    const val DEFAULT_APP_ID = "SHALEV-ANDROID"
    const val MENSAJE_CODIGO_INVALIDO_O_USADO = "Código no válido para esta aplicación o ya utilizado"
    const val MOTIVO_AUN_NO_DISPONIBLE = "Aún no disponible en esta edición"

    private val SEPARADORES = Regex("[\\s\\-]")
    private val ALFABETO_CODIGO = Regex("^[0-9A-HJ-NP-Z]{16}$")

    /** Normaliza un código de canje eliminando guiones y espacios y pasando a mayúsculas. */
    fun normalizarCodigo(entrada: String): String {
        val limpia = entrada.trim().uppercase().replace(SEPARADORES, "")
        require(ALFABETO_CODIGO.matches(limpia)) { "El código debe tener 16 caracteres alfanuméricos válidos" }
        return limpia
    }

    /** Formatea un código normalizado en 4 grupos de 4 caracteres. */
    fun formatearCodigo(normalizado: String): String {
        require(normalizado.length == 16) { "Longitud de código inválida" }
        return normalizado.chunked(4).joinToString("-")
    }

    /** Serializa [partes] con prefijo uint32 big-endian por cada elemento. */
    fun canonico(partes: List<String>): ByteArray {
        var totalBytes = 0
        val encodedParts = partes.map {
            val bytes = it.toByteArray(Charsets.UTF_8)
            totalBytes += 4 + bytes.size
            bytes
        }
        val buffer = ByteBuffer.allocate(totalBytes).order(ByteOrder.BIG_ENDIAN)
        for (bytes in encodedParts) {
            buffer.putInt(bytes.size)
            buffer.put(bytes)
        }
        return buffer.array()
    }

    /** Huella de dispositivo (thumbprint): SHA-256 en Base64url sin relleno. */
    fun deviceThumbprint(spkiDer: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(spkiDer)
        return B64.encUrlNoPad(digest)
    }

    /** Bytes para la firma de la prueba de canje (proof). */
    fun bytesPruebaCanje(
        appId: String,
        code: String,
        deviceKeyB64: String,
        requestId: String,
        requestedAt: String,
        nonceB64: String,
        v: Int = VERSION_SERVICIO,
    ): ByteArray = canonico(
        listOf(
            DOMINIO_SOLICITUD_CANJE,
            v.toString(),
            appId,
            code,
            deviceKeyB64,
            requestId,
            requestedAt,
            nonceB64,
        )
    )

    /** Bytes para la firma de la prueba de consulta de estado. */
    fun bytesPruebaEstado(
        appId: String,
        licenseId: String,
        deviceKeyB64: String,
        requestId: String,
        requestedAt: String,
        nonceB64: String,
        v: Int = VERSION_SERVICIO,
    ): ByteArray = canonico(
        listOf(
            DOMINIO_SOLICITUD_ESTADO,
            v.toString(),
            appId,
            licenseId,
            deviceKeyB64,
            requestId,
            requestedAt,
            nonceB64,
        )
    )

    /** Bytes firmados por el emisor GL sobre el contenido de la licencia. */
    fun bytesFirmaLicencia(
        v: Int,
        licenseId: String,
        appId: String,
        type: String,
        status: String,
        issuedAt: String,
        expiresAt: String?,
        deviceThumbprint: String,
        redemptionId: String,
        transferredFrom: String?,
        secundarias: Int,
        devicePublicKey: String?,
    ): ByteArray = canonico(
        listOf(
            DOMINIO_LICENCIA,
            v.toString(),
            licenseId,
            appId,
            type,
            status,
            issuedAt,
            expiresAt ?: "",
            deviceThumbprint,
            redemptionId,
            transferredFrom ?: "",
            secundarias.toString(),
            devicePublicKey ?: "",
        )
    )

    /** Bytes firmados por el emisor GL sobre el contenido del estado. */
    fun bytesFirmaEstado(
        v: Int,
        licenseId: String,
        appId: String,
        type: String,
        status: String,
        issuedAt: String,
        expiresAt: String?,
        deviceThumbprint: String,
        redemptionId: String,
        transferredFrom: String?,
        secundarias: Int,
        devicePublicKey: String?,
        checkedAt: String,
    ): ByteArray = canonico(
        listOf(
            DOMINIO_ESTADO,
            v.toString(),
            licenseId,
            appId,
            type,
            status,
            issuedAt,
            expiresAt ?: "",
            deviceThumbprint,
            redemptionId,
            transferredFrom ?: "",
            secundarias.toString(),
            devicePublicKey ?: "",
            checkedAt,
        )
    )
}
