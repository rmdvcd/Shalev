package cu.shalev.designsystem.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * 0.21.0 (C10, P46): filtros de entrada comunes a TODOS los campos. [ShalevTextField] aplica el suyo a cada pulsación,
 * así que un carácter no permitido simplemente no aparece (el usuario no tiene que borrar nada). La validación de
 * negocio (obligatorio, rango, Luhn…) sigue en el dominio; esto solo impide escribir lo que nunca sería válido.
 *
 * Suposición documentada: se recorta silenciosamente al máximo de caracteres (pegar un texto largo no da error).
 * Sin filtro explícito, cada campo usa [TEXTO]: sin caracteres de control y como mucho [MAX_TEXTO] caracteres.
 */
enum class FiltroEntrada(val teclado: KeyboardOptions, val maximo: Int) {
    /** Texto libre de una o varias líneas (descripción, nota). Quita caracteres de control (salvo el salto de línea). */
    TEXTO(KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), 500), // = MAX_TEXTO
    /** Textos largos que se pegan tal cual (mensaje de licencia, SMS, contraseña): solo quita caracteres de control. */
    LIBRE(KeyboardOptions.Default, 20_000),
    /** Nombres de productos, locales, empleados…: sin saltos de línea ni símbolos de control; espacios simples. */
    NOMBRE(KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), 80),
    /**
     * Nombre y apellidos de una persona (perfil, licencia, cliente): solo letras (con tildes y ñ), espacios, apóstrofo
     * y guion, como exige `Validators.nombre`. Hasta 80.
     */
    PERSONA(KeyboardOptions(capitalization = KeyboardCapitalization.Words), 80),
    /** Categoría de un producto o tipo de un servicio: como [NOMBRE], hasta 40 (`Validadores.MAX_CATEGORIA`). */
    CATEGORIA(KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), 40),
    /** Descripción breve que distingue artículos del mismo nombre: una línea, hasta 40 (`Validadores.MAX_DESCRIPCION`). */
    DESCRIPCION(KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), 40),
    /** Contraseña de un respaldo: cualquier carácter visible, sin saltos de línea; hasta 256. */
    CONTRASENA(KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), 256),
    /** Buscadores: una línea, sin control. */
    BUSQUEDA(KeyboardOptions(capitalization = KeyboardCapitalization.None), 60),
    /** Móvil cubano sin prefijo: 8 dígitos que empiezan por 5 o 6 (serie 63 de ETECSA desde 2023). */
    TELEFONO(KeyboardOptions(keyboardType = KeyboardType.Phone), 8),
    /** Tarjeta o cuenta bancaria: solo dígitos, hasta 20 (igual que el dominio). */
    TARJETA(KeyboardOptions(keyboardType = KeyboardType.Number), 20),
    /** Carné de identidad: 11 dígitos; los 6 primeros son la fecha de nacimiento AAMMDD (mes 01–12, día 01–31). */
    CARNE(KeyboardOptions(keyboardType = KeyboardType.Number), 11),
    /** Cantidades y niveles enteros (sin signo). */
    ENTERO(KeyboardOptions(keyboardType = KeyboardType.Number), 7),
    /** Cantidades de insumos: dígitos y UN separador decimal (punto o coma, que se guarda como punto), hasta 3 decimales. */
    DECIMAL(KeyboardOptions(keyboardType = KeyboardType.Decimal), 12),
    /** 0.31.0: cantidad CON SIGNO de un ajuste de existencias («-1.25», «+3»), hasta 3 decimales. */
    CANTIDAD(KeyboardOptions(keyboardType = KeyboardType.Decimal), 12),
    /** Importes en CUP: dígitos y UN separador decimal, hasta 2 decimales. */
    DINERO(KeyboardOptions(keyboardType = KeyboardType.Decimal), 12),
    /** Código de barras / QR del producto: letras, dígitos y - . sin espacios. */
    CODIGO(KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters), 64),
    /** Nº de transacción de Transfermóvil: letras y dígitos, en mayúsculas. */
    TRANSACCION(KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters), 30),
    /** Porcentaje 0–100 con hasta 2 decimales (ajustes de precio, que se guardan en puntos básicos). */
    PORCENTAJE(KeyboardOptions(keyboardType = KeyboardType.Decimal), 6),
    ;

    /** Lo que queda del texto escrito o pegado. Idempotente: aplicar dos veces da lo mismo. */
    fun aplicar(s: String): String = when (this) {
        TEXTO, LIBRE -> s.filter { it == '\n' || !it.isISOControl() }
        NOMBRE, CATEGORIA, DESCRIPCION -> unaLinea(s).trimStart()
        BUSQUEDA -> unaLinea(s)
        PERSONA -> persona(s)
        CONTRASENA -> s.filter { !it.isISOControl() }
        // Al pegar «+53 5XXXXXXX» se quita el prefijo del país (el campo es el móvil de 8 cifras).
        TELEFONO -> s.filter { it in '0'..'9' }.let { d -> if (d.length > 8 && d.startsWith("53")) d.drop(2) else d }
        TARJETA, CARNE, ENTERO -> s.filter { it in '0'..'9' }
        PORCENTAJE -> decimal(s, 2)
        DECIMAL -> decimal(s, 3)
        CANTIDAD -> decimal(s, 3, conSigno = true)
        DINERO -> decimal(s, 2)
        CODIGO -> s.filter { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '.' || it == '_' } // = dominio (3–64)
        TRANSACCION -> s.filter { it.isLetterOrDigit() && it.code < 128 }.uppercase()
    }.take(maximo)

    /** Rechaza entradas numéricas ambiguas sin convertir, por ejemplo, «-1.2» en «12».
     * Permite estados parciales mientras se escribe; el dominio valida el valor final.
     */
    fun aceptar(anterior: String, escrito: String): String {
        val valido = when (this) {
            DINERO, DECIMAL -> {
                val cifras = if (this == DINERO) 2 else 3
                escrito.length <= maximo && Regex("[0-9]*(?:[.,][0-9]{0,$cifras})?").matches(escrito)
            }
            CANTIDAD -> escrito.length <= maximo && Regex("[+-]?[0-9]*(?:[.,][0-9]{0,3})?").matches(escrito)
            ENTERO -> escrito.length <= maximo && escrito.all { it in '0'..'9' }
            PORCENTAJE -> escrito.isEmpty() || (
                escrito.length <= maximo && Regex("[0-9]*(?:[.,][0-9]{0,2})?").matches(escrito) &&
                    (escrito.replace(',', '.').trimEnd('.').ifEmpty { "0" }.toBigDecimalOrNull()?.let { it <= CIEN } ?: false)
                )
            // Se comprueba lo que quedaría tras limpiar («+53 5…», «850101-12345» pegados siguen valiendo).
            // Borrar siempre se permite (si no, quitar una cifra intermedia podría quedar bloqueado); el error lo da la validación.
            TELEFONO -> borra(anterior, escrito) || aplicar(escrito).let { it.isEmpty() || it[0] == '5' || it[0] == '6' }
            CARNE -> borra(anterior, escrito) || fechaParcialCarne(aplicar(escrito))
            else -> true
        }
        return if (valido) aplicar(escrito) else anterior
    }

    companion object {
        const val MAX_TEXTO = 500
        private val CIEN = java.math.BigDecimal(100)

        /** Una línea: los caracteres de control (salto de línea, tabulador…) pasan a ser un espacio; espacios simples. */
        private fun unaLinea(s: String): String =
            s.map { if (it.isISOControl()) ' ' else it }.joinToString("").replace(Regex(" {2,}"), " ")

        /** Letras (normalizadas a NFC, para que «á» escrita en dos pulsaciones cuente como una), espacio, ' y -. */
        private fun persona(s: String): String = java.text.Normalizer.normalize(unaLinea(s), java.text.Normalizer.Form.NFC)
            .map { c -> if (c == '’' || c == '‘' || c == '´' || c == '`') '\'' else if (c == '‐' || c == '–' || c == '—') '-' else c }
            .filter { it.isLetter() || it == ' ' || it == '\'' || it == '-' }
            .joinToString("")
            .replace(Regex(" {2,}"), " ").trimStart()

        private fun FiltroEntrada.borra(anterior: String, escrito: String) = aplicar(escrito).length < aplicar(anterior).length

        /** Lo escrito del carné encaja con una fecha AAMMDD: mes 01–12 y día 01–31 (el resto lo comprueba el dominio). */
        fun fechaParcialCarne(d: String): Boolean {
            if (d.length >= 3 && d[2] !in '0'..'1') return false
            if (d.length >= 4 && d.substring(2, 4).toInt() !in 1..12) return false
            if (d.length >= 5 && d[4] !in '0'..'3') return false
            if (d.length >= 6 && d.substring(4, 6).toInt() !in 1..31) return false
            return true
        }

        /** Dígitos y como mucho un separador (el primero que aparezca), con [decimales] cifras detrás. */
        private fun decimal(s: String, decimales: Int, conSigno: Boolean = false): String {
            val texto = s.trimStart()
            val out = StringBuilder()
            if (conSigno && (texto.firstOrNull() == '-' || texto.firstOrNull() == '+')) out.append(texto[0])
            var separador = false
            var tras = 0
            for (c in texto) {
                when {
                    c in '0'..'9' -> if (!separador) out.append(c) else if (tras < decimales) { out.append(c); tras++ }
                    // 0.21.6: la coma del teclado en español se guarda como punto. Antes «2,5» llegaba a Cantidad.parse, que quita
                    // las comas (separador de miles) y lo leía como 25.
                    (c == '.' || c == ',') && !separador -> { separador = true; out.append('.') }
                }
            }
            return out.toString()
        }
    }
}
