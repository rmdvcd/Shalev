package cu.shalev.domain.model

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad

/** Botón de filtro de Elaboración: si el insumo forma parte de alguna receta. */
enum class UsoInsumo { TODOS, EN_RECETAS, SIN_USO }

/**
 * Buscador + filtro de la lista de insumos. [alerta] llega preseleccionada al tocar un contador de Inicio
 * (solo INSUMO_BAJO o INSUMO_CRITICO aplican a insumos).
 */
data class FiltroInsumos(
    val texto: String = "",
    val alerta: TipoAlerta? = null,
    val uso: UsoInsumo = UsoInsumo.TODOS,
) {
    /** Filtros del botón (el texto del buscador no cuenta): para el distintivo del icono. */
    val activos: Int get() = listOf(alerta != null, uso != UsoInsumo.TODOS).count { it }
    val vacio: Boolean get() = activos == 0 && texto.isBlank()
}

/** Fila de la lista: el insumo, su nivel de existencias y en cuántas recetas aparece. */
data class ItemInsumo(val insumo: Insumo, val nivel: NivelStock, val usos: Int)

/** [total] = insumos existentes antes de filtrar (para distinguir "vacío" de "sin resultados"). */
data class VistaInsumos(val items: List<ItemInsumo>, val total: Int)

/** Elaborado cuya receta usa el insumo, con la cantidad que gasta por unidad producida. */
/** P29: [servicio] = true si quien lo gasta es un servicio (entonces [productoId] es el id del servicio). */
data class UsoEnReceta(val productoId: Long, val nombre: String, val cantidad: Cantidad, val servicio: Boolean = false)

/** Ficha al tocar un insumo. */
data class FichaInsumo(val insumo: Insumo, val nivel: NivelStock, val usadoEn: List<UsoEnReceta>) {
    /** Lo que vale lo que hay en existencia, a precio de costo. */
    val valorExistencias: Cup get() = insumo.precio.porMilesimas(insumo.cantidad.milesimas)
}

/** Línea de receta de un Elaborado: cuánto gasta 1 unidad y cuánto hay del insumo. */
data class LineaProduccion(
    val insumoId: Long,
    val nombre: String,
    val simbolo: String,
    val porUnidad: Cantidad,
    val disponible: Cantidad,
    val existe: Boolean = true,
)

/**
 * Elaborado con su receta y existencias de insumos (redefinición P26): un Elaborado NO tiene existencias propias,
 * se vende directamente mientras los insumos alcancen. [costoUnidad] es null si falta algún insumo de la receta.
 */
data class ElaboradoDisponible(val producto: Producto, val lineas: List<LineaProduccion>, val costoUnidad: Cup?) {
    val tieneReceta: Boolean get() = lineas.isNotEmpty()

    /** «Alcanza para N»: unidades que se pueden vender ahora mismo (el insumo más escaso manda). */
    val alcanza: Long
        get() = if (lineas.isEmpty() || lineas.any { !it.existe || it.porUnidad <= Cantidad.ZERO }) 0
        else lineas.minOf { it.disponible.milesimas.coerceAtLeast(0) / it.porUnidad.milesimas }

    /** Consumo de cada insumo al vender [unidades]. */
    fun consumo(unidades: Long): List<Pair<LineaProduccion, Cantidad>> =
        if (unidades <= 0) emptyList() else lineas.map { it to it.porUnidad * unidades }

    /** Nombres de los insumos que no alcanzan para [unidades]. */
    fun faltantes(unidades: Long): List<String> =
        consumo(unidades).filter { (l, c) -> !l.existe || l.disponible < c }.map { it.first.nombre }
}
