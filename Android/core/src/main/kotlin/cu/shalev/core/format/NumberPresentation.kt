package cu.shalev.core.format

/**
 * Formatos exclusivamente visuales para números que deben seguir almacenándose/editarse sin separadores.
 * La agrupación de tres cifras usa U+202F (narrow no-break space): mantiene juntos los grupos y ocupa poco ancho.
 */
object NumberPresentation {
    const val NARROW_NO_BREAK_SPACE = "\u202F"

    /** Agrupa un entero solo para lectura; el valor original sigue siendo el que se almacena o edita. */
    fun groupedInteger(value: Long): String {
        val raw = value.toString()
        val negative = raw.startsWith('-')
        val digits = raw.removePrefix("-")
        return buildString(digits.length + (digits.length - 1) / 3) {
            if (negative) append('-')
            digits.forEachIndexed { index, digit ->
                append(digit)
                val remaining = digits.length - index - 1
                if (remaining > 0 && remaining % 3 == 0) append(NARROW_NO_BREAK_SPACE)
            }
        }
    }

    /** Agrupa teléfonos de 10 cifras como «XX XXXX XXXX» y muestra los locales de 8 como «+53 XXXX XXXX». */
    fun phone(value: String): String {
        val digits = value.filter { it in '0'..'9' }
        val prefix = if (value.trimStart().startsWith('+')) "+" else ""
        val separator = NARROW_NO_BREAK_SPACE
        return when (digits.length) {
            10 -> prefix + digits.substring(0, 2) + separator + digits.substring(2, 6) + separator + digits.substring(6)
            8 -> if (prefix.isEmpty()) "+53" + separator + digits.substring(0, 4) + separator + digits.substring(4) else value
            else -> value
        }
    }

    /** Agrupa tarjetas/cuentas en bloques de cuatro. No altera datos y conserva cualquier valor ya enmascarado. */
    fun card(value: String): String {
        if ('•' in value) return value
        val digits = value.filter { it in '0'..'9' }
        return if (digits.isEmpty()) value else digits.chunked(4).joinToString("-")
    }
}
