package cu.shalev.domain.service

import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento

/**
 * 0.31.0: corregir la cantidad de un ajuste de existencias desde Registros → Movimientos. Lógica pura (se prueba
 * sin Android): qué se puede corregir, cómo se lee la cantidad escrita y qué queda anotado.
 */
object CorreccionMovimiento {

    /**
     * Solo los ajustes manuales de existencias del turno ABIERTO: una venta, un alta, una baja o una devolución
     * por anulación son consecuencia de otra acción y se corrigen desde ella (Registros → Ventas).
     */
    fun puede(tipo: TipoMovimiento, turnoId: Long?, turnoAbierto: Long?): Boolean =
        tipo == TipoMovimiento.AJUSTE && turnoId != null && turnoId == turnoAbierto

    /** Lo que hay que sumarle AHORA al stock para que el ajuste pase de [viejo] a [nuevo]. */
    fun diferencia(viejo: Long, nuevo: Long): Long = Math.subtractExact(nuevo, viejo)

    /**
     * Lee la cantidad escrita: «5», «+5», «-5» (producto, unidades enteras) y «1.25», «-0.5» (insumo, hasta 3
     * decimales en la unidad del insumo). `null` si no es un número válido.
     */
    fun leer(texto: String, entidad: TipoEntidad): Long? {
        val t = texto.trim()
        if (t.isEmpty()) return null
        val negativo = t.startsWith("-")
        val cuerpo = t.removePrefix("-").removePrefix("+").trim()
        if (cuerpo.isEmpty()) return null
        val v = when (entidad) {
            TipoEntidad.PRODUCTO -> cuerpo.toLongOrNull()
            TipoEntidad.INSUMO -> Cantidad.parse(cuerpo)?.milesimas
        } ?: return null
        return if (negativo) -v else v
    }

    /** El texto editable de una cantidad: «5» (producto) y «-1.25» (insumo). */
    fun texto(delta: Long, entidad: TipoEntidad): String = when (entidad) {
        TipoEntidad.PRODUCTO -> delta.toString()
        TipoEntidad.INSUMO -> Cantidad.editable(Cantidad(delta))
    }

    /** Lo que queda en la nota del movimiento: «Compra · Corregido: llegó el doble». */
    fun nota(anterior: String?, motivo: String): String {
        val correccion = "Corregido: ${motivo.trim().replace(Regex("\\s+"), " ")}"
        return listOfNotNull(anterior?.trim()?.takeIf { it.isNotEmpty() }, correccion).joinToString(" · ")
    }
}
