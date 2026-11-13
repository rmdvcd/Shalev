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
    /** Nombres de personas, productos, categorías…: sin saltos de línea ni símbolos de control; espacios simples. */
    NOMBRE(KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), 80),
    /** Buscadores: una línea, sin control. */
    BUSQUEDA(KeyboardOptions(capitalization = KeyboardCapitalization.None), 60),
    /** Móvil cubano sin prefijo: solo dígitos, 8. */
    TELEFONO(KeyboardOptions(keyboardType = KeyboardType.Phone), 8),
    /** Tarjeta o cuenta bancaria: solo dígitos, hasta 20 (igual que el dominio). */
    TARJETA(KeyboardOptions(keyboardType = KeyboardType.Number), 20),
    /** Carné de identidad: solo dígitos, 11. */
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
    /** Porcentaje entero 0–100 (ajustes de precio). */
    PORCENTAJE(KeyboardOptions(keyboardType = KeyboardType.Number), 3),
    ;

    /** Lo que queda del texto escrito o pegado. Idempotente: aplicar dos veces da lo mismo. */
    fun aplicar(s: String): String = when (this) {
        TEXTO, LIBRE -> s.filter { it == '\n' || !it.isISOControl() }
        NOMBRE, BUSQUEDA -> s.map { if (it.isISOControl()) ' ' else it }.joinToString("").replace(Regex(" {2,}"), " ")
        // Al pegar «+53 5XXXXXXX» se quita el prefijo del país (el campo es el móvil de 8 cifras).
        TELEFONO -> s.filter { it in '0'..'9' }.let { d -> if (d.length > 8 && d.startsWith("53")) d.drop(2) else d }
        TARJETA, CARNE, ENTERO, PORCENTAJE -> s.filter { it in '0'..'9' }
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
            PORCENTAJE -> escrito.isEmpty() ||
                (escrito.length <= maximo && escrito.all { it in '0'..'9' } && (escrito.toIntOrNull() ?: 101) <= 100)
            else -> true
        }
        return if (valido) aplicar(escrito) else anterior
    }

    companion object {
        const val MAX_TEXTO = 500

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
