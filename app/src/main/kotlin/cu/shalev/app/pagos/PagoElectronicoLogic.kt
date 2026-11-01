package cu.shalev.app.pagos

import cu.shalev.core.result.AppError
import cu.shalev.core.validation.Phone
import cu.shalev.domain.validation.Validadores

enum class TipoCuentaPago { TELEFONO, TARJETA }

/**
 * Bancos asociados de forma orientativa a prefijos publicados. No es una tabla oficial ni valida la titularidad.
 * 9224 queda sin asignar porque las fuentes consultadas lo atribuyen a bancos distintos.
 */
enum class BancoCubano(val nombre: String, val sigla: String, val prefijos: Set<String>) {
    BPA("Banco Popular de Ahorro", "BPA", setOf("9205")),
    BANDEC("Banco de Crédito y Comercio", "BANDEC", setOf("9225")),
    BANMET("Banco Metropolitano", "BANMET", setOf("9226")),
}

/** Los 4 primeros dígitos que deciden la miniatura (se ignoran espacios y guiones); null si aún no hay 4. */
fun prefijoCuenta(numero: String): String? {
    val digitos = numero.filter(Char::isDigit)
    return if (digitos.length < 4) null else digitos.take(4)
}

/** Identificación visual no autoritativa; los prefijos desconocidos o ambiguos se dejan sin logo. */
fun bancoPorTarjeta(numero: String): BancoCubano? {
    val prefijo = prefijoCuenta(numero) ?: return null
    return BancoCubano.entries.firstOrNull { banco -> prefijo in banco.prefijos }
}

object TextosPago {
    const val TITULO = "Pago electrónico"
    const val EXPLICACION = "Cuando un cliente paga por transferencia, el código QR lleva la tarjeta o cuenta que recibe " +
        "el dinero y el teléfono que recibe el mensaje de confirmación. Elige cuáles usar."
    const val AVISO_BANCOS = "Los logos se estiman por el prefijo; no verifican el banco ni la titularidad."
    const val BANCO_ESTIMADO = "Identificación orientativa por prefijo"

    /** Vista previa de la hoja: deja a la vista el prefijo que eligió la miniatura. */
    fun bancoEstimado(prefijo: String) = "Prefijo $prefijo · identificación orientativa"
    const val TELEFONO = "Teléfono de confirmación"
    const val TARJETA = "Tarjeta o cuenta que recibe el dinero"
    const val NINGUNO = "Ninguno"
    const val NUEVO_TELEFONO = "Otro teléfono"
    const val NUEVA_TARJETA = "Otra tarjeta o cuenta"
    const val EN_USO = "En uso"
    const val ERROR_TELEFONO = "Escribe un teléfono válido, por ejemplo 5123 4567"
    const val ERROR_TARJETA = "Escribe solo los números de la tarjeta o cuenta (12 a 20 dígitos)"
    const val DUPLICADO = "Ese número ya está en la lista"
    const val ERROR_GENERICO = "No se pudo guardar. Inténtalo de nuevo."
    const val GUARDADO = "Pago electrónico guardado"
}

/** Validación en tiempo real (vacío = sin error: los campos nuevos son opcionales). */
fun errorTelefono(texto: String): String? =
    if (texto.isBlank() || Phone.normalize(texto) != null) null else TextosPago.ERROR_TELEFONO

fun errorTarjeta(texto: String): String? =
    if (texto.isBlank() || Validadores.normalizarCuenta(texto) != null) null else TextosPago.ERROR_TARJETA

fun errorNumero(tipo: TipoCuentaPago, texto: String): String? = when {
    texto.isBlank() -> if (tipo == TipoCuentaPago.TELEFONO) TextosPago.ERROR_TELEFONO else TextosPago.ERROR_TARJETA
    tipo == TipoCuentaPago.TELEFONO -> errorTelefono(texto)
    else -> errorTarjeta(texto)
}

/** "9205129900001234" → "9205 1299 0000 1234" (solo en la hoja de edición; en listas va enmascarado). */
fun formatoCuenta(numero: String): String = numero.chunked(4).joinToString(" ")

/** Error de dominio → texto simple (sin detalles internos). */
fun mensajePago(e: AppError): String = when (e) {
    is AppError.Validacion -> if (e.campo == "telefonos") TextosPago.ERROR_TELEFONO else TextosPago.ERROR_TARJETA
    is AppError.Duplicado -> TextosPago.DUPLICADO
    else -> TextosPago.ERROR_GENERICO
}

/** Ventana de alta/edición de un elemento de las listas de Ajustes. [id] null = nuevo. */
data class EdicionPago(
    val tipo: TipoCuentaPago,
    val id: Long? = null,
    val numero: String = "",
    val alias: String = "",
    val mostrarErrores: Boolean = false,
    val errorServidor: String? = null,
) {
    val error: String? get() = errorServidor ?: errorNumero(tipo, numero)
}
