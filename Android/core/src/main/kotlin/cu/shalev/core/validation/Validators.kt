package cu.shalev.core.validation

/**
 * Validaciones alineadas con las de GL (contrato v1): el error se ve en Shalev con mensaje claro,
 * en vez de un rechazo mudo en el emisor.
 */
object Validators {
    /** Letras Unicode, espacios internos, apóstrofo y guion (GL acepta apellidos compuestos). 2–80. */
    private val NOMBRE = Regex("^\\p{L}[\\p{L} '\\-]{0,78}\\p{L}$")
    private val CI = Regex("^[A-Za-z0-9]{5,20}$")
    private val TELEFONO = Regex("^\\+?[0-9]{8,15}$")
    private val DEVICE_ID = Regex("^[A-Za-z0-9:_-]{8,128}$")
    private val NONCE = Regex("^[A-Za-z0-9_-]{16,128}$")

    fun nombre(s: String) = NOMBRE.matches(s.trim())
    fun ci(s: String) = CI.matches(s.trim())
    fun telefono(s: String) = TELEFONO.matches(s)
    fun deviceId(s: String) = DEVICE_ID.matches(s)
    fun nonce(s: String) = NONCE.matches(s)
}

object Phone {
    /** Normaliza a E.164. Números cubanos de 8 dígitos → +53XXXXXXXX. Null si no es normalizable. */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter(Char::isDigit)
        val e164 = when {
            trimmed.startsWith("+") -> "+$digits"
            trimmed.startsWith("00") && digits.length > 2 -> "+${digits.drop(2)}"
            digits.length == 8 -> "+53$digits"
            digits.length == 10 && digits.startsWith("53") -> "+$digits"
            digits.length in 11..15 -> "+$digits"
            else -> return null
        }
        return e164.takeIf(Validators::telefono)
    }
}
