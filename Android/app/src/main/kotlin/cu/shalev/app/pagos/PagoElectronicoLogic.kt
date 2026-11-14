package cu.shalev.app.pagos

import cu.shalev.core.format.NumberPresentation
import cu.shalev.core.result.AppError
import cu.shalev.core.validation.Phone
import cu.shalev.domain.validation.Validadores

enum class TipoCuentaPago { TELEFONO, TARJETA }

/**
 * Código de banco de las tarjetas RED: son los dígitos 5–8 del número, no los 4 primeros.
 * Los 4 primeros (9205, 9225…) indican moneda y producto, y los emiten los tres bancos; por eso no identifican al
 * banco. Códigos: 0699 BANDEC, 1299 BPA, 9598 Banco Metropolitano (catálogo público de prefijos cubanos 0.5.2 y
 * titulares; ver docs/LOGOS_BANCOS.md). No valida la titularidad.
 */
enum class BancoCubano(val nombre: String, val sigla: String, val codigo: String) {
    BPA("Banco Popular de Ahorro", "BPA", "1299"),
    BANDEC("Banco de Crédito y Comercio", "BANDEC", "0699"),
    BANMET("Banco Metropolitano", "BANMET", "9598"),
}

/**
 * Dígitos 5–8 de una tarjeta RED (red 92), que es donde va el banco; null si aún no hay 8 dígitos.
 * Las redes AIS (95) y Clásica (97) no llevan código de banco en esa posición.
 */
fun codigoBanco(numero: String): String? {
    val digitos = numero.filter(Char::isDigit)
    return if (digitos.length < 8 || !digitos.startsWith("92")) null else digitos.substring(4, 8)
}

/** Identificación visual no autoritativa; sin código de banco conocido se deja el icono genérico. */
fun bancoPorTarjeta(numero: String): BancoCubano? {
    val codigo = codigoBanco(numero) ?: return null
    return BancoCubano.entries.firstOrNull { banco -> banco.codigo == codigo }
}

object TextosPago {
    const val TITULO = "Pago electrónico"
    const val EXPLICACION = "Cuando un cliente paga por transferencia, el código QR lleva la tarjeta o cuenta que recibe " +
        "el dinero y el teléfono que recibe el mensaje de confirmación. Elige cuáles usar."
    const val AVISO_BANCOS = "El logo se estima por el código de banco (dígitos 5 a 8); no verifica la titularidad."
    const val BANCO_ESTIMADO = "Identificación orientativa por el código de banco"

    /** Vista previa de la hoja: deja a la vista el código (dígitos 5–8) que eligió la miniatura. */
    fun bancoEstimado(codigo: String) = "Código de banco $codigo · orientativo"
    const val TELEFONO = "Teléfono de confirmación"
    const val TARJETA = "Tarjeta o cuenta que recibe el dinero"
    const val NINGUNO = "Ninguno"
    const val NUEVO_TELEFONO = "Otro teléfono"
    const val NUEVA_TARJETA = "Otra tarjeta o cuenta"
    const val EN_USO = "En uso"
    const val ERROR_TELEFONO = "Escribe un teléfono válido, por ejemplo 5123\u202F4567"
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

/** Formato visual de la cuenta en edición; en listas el número completo sigue enmascarado. */
fun formatoCuenta(numero: String): String = NumberPresentation.card(numero)

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
