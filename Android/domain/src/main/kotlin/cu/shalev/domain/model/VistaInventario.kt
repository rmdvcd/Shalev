package cu.shalev.domain.model

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad

enum class TipoArticulo { TODOS, ARTICULOS, ELABORADOS, INSUMOS }

enum class EstadoCaducidad { SIN_FECHA, VIGENTE, PROXIMA, VENCIDA }

/**
 * Buscador + botón de filtro del Inventario. [alerta] llega preseleccionada al tocar un contador de Inicio
 * (solo STOCK_BAJO, STOCK_CRITICO o PROXIMO_A_CADUCAR aplican a productos).
 */
data class FiltroInventario(
    val texto: String = "",
    val categoria: String? = null,
    val alerta: TipoAlerta? = null,
    val tipo: TipoArticulo = TipoArticulo.TODOS,
) {
    /** Filtros del botón (el texto del buscador no cuenta): para el distintivo del icono. */
    val activos: Int get() = listOf(categoria != null, alerta != null, tipo != TipoArticulo.TODOS).count { it }
    val vacio: Boolean get() = activos == 0 && texto.isBlank()
}

/**
 * Fila de la tabla: el producto + su indicador visual (nivel de stock y caducidad).
 * [alcanza] (P26, solo Elaborados): unidades que permiten los insumos ahora («Alcanza para N»); null en artículos.
 */
data class ItemInventario(
    val producto: Producto,
    val nivel: NivelStock,
    val caducidad: EstadoCaducidad,
    val alcanza: Long? = null,
    /** P29: no null = la fila es este insumo ([producto] es su vista como fila, con id negativo). */
    val insumo: Insumo? = null,
    /** 0.24.0: comparte nombre con otro producto y no se distingue por la descripción ([Identificacion]). */
    val nombreRepetido: Boolean = false,
)

/**
 * [total] = productos activos antes de filtrar; [categorias] = las que existen, para el filtro.
 * [elementosCompletos] conserva las filas sin filtrar para fijar las seleccionadas aunque no coincidan con el buscador.
 */
data class VistaInventario(
    val items: List<ItemInventario>,
    val total: Int,
    val categorias: List<String>,
    val elementosCompletos: List<ItemInventario> = items,
)

/** Línea de receta resuelta para la ficha (nombre y unidad del insumo, costo por unidad de Elaborado). */
data class LineaFicha(val insumoId: Long, val nombre: String, val cantidad: Cantidad, val simbolo: String, val costo: Cup)

data class FichaProducto(
    val producto: Producto,
    val receta: List<LineaFicha>,
    val nivel: NivelStock,
    val caducidad: EstadoCaducidad,
    /** P26, solo Elaborados: «Alcanza para N» según los insumos actuales; null en artículos. */
    val alcanza: Long? = null,
) {
    val ganancia: Cup get() = producto.precioVenta - producto.precioCosto
}
