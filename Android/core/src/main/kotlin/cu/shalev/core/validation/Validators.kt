package cu.shalev.core.validation

import java.time.DateTimeException
import java.time.LocalDate

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

    /** Formato amplio del contrato con GL (5–20 alfanuméricos). En la app se valida con [carneCubano]. */
    fun ci(s: String) = CI.matches(s.trim())

    /**
     * Número de identidad permanente del carné cubano (Decreto-Ley 248/2007): 11 dígitos.
     * - 1–6: fecha de nacimiento AAMMDD.
     * - 7: siglo (9 → 1800; 0–5 → 1900; 6–8 → 2000).
     * - 8–11: consecutivo y sexo; no hay dígito de control público, así que se comprueba la fecha.
     * Se rechaza una fecha imposible (31 de abril, 29 de febrero de un año no bisiesto) o posterior a [hoy].
     */
    fun carneCubano(s: String, hoy: LocalDate = LocalDate.now()): Boolean = fechaCarne(s)?.let { !it.isAfter(hoy) } ?: false

    /** Fecha de nacimiento codificada en el carné, o null si el número no tiene ese formato. */
    fun fechaCarne(s: String): LocalDate? {
        val d = s.trim()
        if (d.length != 11 || !d.all { it in '0'..'9' }) return null
        val siglo = when (d[6]) {
            '9' -> 1800
            in '0'..'5' -> 1900
            else -> 2000 // 6, 7 y 8
        }
        return try {
            LocalDate.of(siglo + d.substring(0, 2).toInt(), d.substring(2, 4).toInt(), d.substring(4, 6).toInt())
        } catch (e: DateTimeException) {
            null
        }
    }

    /**
     * Dígito de control Luhn (ISO/IEC 7812) de las tarjetas de las redes RED (92), AIS (95) y Clásica (97):
     * detecta un dígito mal escrito o dos cambiados de sitio.
     */
    fun luhn(digitos: String): Boolean {
        if (digitos.isEmpty() || !digitos.all { it in '0'..'9' }) return false
        var suma = 0
        digitos.reversed().forEachIndexed { i, c ->
            var n = c - '0'
            if (i % 2 == 1) { n *= 2; if (n > 9) n -= 9 }
            suma += n
        }
        return suma % 10 == 0
    }

    /** 16 dígitos que empiezan por 9: número de tarjeta (las cuentas bancarias no llevan dígito Luhn). */
    fun pareceTarjeta(digitos: String): Boolean = digitos.length == 16 && digitos.startsWith("9") && digitos.all { it in '0'..'9' }

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

    /**
     * Móvil cubano: 8 dígitos que empiezan por 5 o, desde diciembre de 2023, por 6 (serie 63 de ETECSA), con o sin +53.
     * Los fijos (2, 3, 4, 7…) no reciben los SMS de Transfermóvil.
     */
    fun esMovilCubano(raw: String): Boolean {
        val n = normalize(raw) ?: return false
        return n.length == 11 && n.startsWith("+53") && n[3] in "56"
    }
}
