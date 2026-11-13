package cu.shalev.domain.model

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import java.time.Instant

/**
 * P29: servicio que ofrece el negocio (sustituye a la pestaña Elaboración). Nombre, Tipo e Importe obligatorios;
 * Foto y Descripción opcionales. Puede consumir insumos ([RecetaLinea] por cada vez que se presta).
 * [tipo] es texto libre que agrupa servicios (como las Categorías del Inventario).
 * Borrado lógico ([eliminado]) para no romper el historial de ventas.
 */
data class Servicio(
    val id: Long = 0,
    val nombre: String,
    val tipo: String,
    val importe: Cup,
    val descripcion: String? = null,
    val fotoUri: String? = null,
    val creadoEn: Instant,
    val actualizadoEn: Instant = creadoEn,
    val eliminado: Boolean = false,
    /** Mano de obra, gastos directos e indirectos por unidad; no se pierden al recalcular la receta. */
    val costoAdicional: Cup = Cup.ZERO,
)

/** Insumo que gasta UNA vez el servicio, resuelto con nombre, unidad y existencias para la UI. */
data class LineaServicio(
    val insumoId: Long,
    val nombre: String,
    val simbolo: String,
    val porVez: Cantidad,
    val disponible: Cantidad,
    val existe: Boolean = true,
)

/**
 * Servicio con sus insumos. [alcanza] = veces que se puede prestar con los insumos actuales; null si no consume
 * insumos (sin límite). [costo] = costo de los insumos por vez (null si falta alguno).
 */
data class ServicioDisponible(val servicio: Servicio, val lineas: List<LineaServicio>, val costo: Cup?) {
    val consumeInsumos: Boolean get() = lineas.isNotEmpty()
    val alcanza: Long?
        get() = if (lineas.isEmpty()) null
        else if (lineas.any { !it.existe || it.porVez <= Cantidad.ZERO }) 0
        else lineas.minOf { it.disponible.milesimas.coerceAtLeast(0) / it.porVez.milesimas }
    val vendible: Boolean get() = alcanza?.let { it > 0 } ?: true
}

/** Buscador + filtro por tipo de la lista de Servicios. */
data class FiltroServicios(val texto: String = "", val tipo: String? = null) {
    val activos: Int get() = if (tipo != null) 1 else 0
    val vacio: Boolean get() = activos == 0 && texto.isBlank()
}

/** [total] = servicios activos antes de filtrar; [tipos] = los tipos que existen, para el filtro y el formulario. */
/** 0.24.0: [porDiferenciar] = ids de servicios con el mismo nombre que otro y sin descripción que los distinga. */
data class VistaServicios(
    val items: List<ServicioDisponible>, val total: Int, val tipos: List<String>, val porDiferenciar: Set<Long> = emptySet(),
)

/**
 * P29: los insumos aparecen en el Inventario como una categoría más. Para no mezclar ids de dos tablas en las
 * listas, la selección y el carrito, un insumo se representa con su id en NEGATIVO.
 */
object IdArticulo {
    fun deInsumo(insumoId: Long): Long = -insumoId
    fun esInsumo(id: Long): Boolean = id < 0
    fun insumoId(id: Long): Long = -id
}
