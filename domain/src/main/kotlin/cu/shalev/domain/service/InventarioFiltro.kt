package cu.shalev.domain.service

import cu.shalev.domain.model.Identificacion
import cu.shalev.domain.model.comoProducto
import cu.shalev.domain.model.IdArticulo
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.EstadoCaducidad
import cu.shalev.domain.model.FiltroInventario
import cu.shalev.domain.model.ItemInventario
import cu.shalev.domain.model.NivelStock
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.TipoArticulo
import cu.shalev.domain.model.VistaInventario
import java.text.Normalizer
import java.time.LocalDate

/**
 * Tabla del Inventario (pura). Conserva el orden cronológico que entrega el repositorio (más recientes
 * primero). El buscador ignora mayúsculas y tildes ("cafe" encuentra "Café").
 */
object InventarioFiltro {

    /** [alcanza] = productoId → unidades que permiten los insumos (Elaborados; sin entrada = 0). */
    fun aplicar(
        ps: List<Producto>, f: FiltroInventario, n: NivelesMinimos, hoy: LocalDate, diasAviso: Int,
        alcanza: Map<Long, Long> = emptyMap(),
        /** P29: los insumos entran como categoría «Insumos» (id negativo, ver [IdArticulo]). */
        insumos: List<Insumo> = emptyList(),
    ): VistaInventario {
        val porInsumo = insumos.associateBy { IdArticulo.deInsumo(it.id) }
        val repetidos = Identificacion.productosPorDiferenciar(ps.filterNot { it.eliminado }) // Igual que las alertas de Inicio
        val activos = (ps.filterNot { it.eliminado } + insumos.map { it.comoProducto() }).sortedByDescending { it.creadoEn }
        val q = plano(f.texto)
        val elementosCompletos = activos.map { p ->
            val i = porInsumo[p.id]
            if (i != null) ItemInventario(p, Stock.nivel(i, n), EstadoCaducidad.SIN_FECHA, insumo = i)
            else ItemInventario(p, Stock.nivel(p, n), caducidad(p, hoy, diasAviso), if (p.esElaborado) alcanza[p.id] ?: 0 else null, nombreRepetido = p.id in repetidos)
        }
        val items = elementosCompletos.asSequence()
            .filter { f.categoria == null || it.producto.categoria.equals(f.categoria, ignoreCase = true) }
            .filter {
                when (f.tipo) {
                    TipoArticulo.TODOS -> true
                    TipoArticulo.ARTICULOS -> !it.producto.esElaborado && !it.producto.esInsumo
                    TipoArticulo.ELABORADOS -> it.producto.esElaborado
                    TipoArticulo.INSUMOS -> it.producto.esInsumo
                }
            }
            .filter { q.isEmpty() || coincide(it.producto, q) }
            .filter { f.alerta == null || cumpleAlerta(it, f.alerta) }
            .toList()
        val categorias = activos.map { it.categoria.trim() }.distinctBy { it.lowercase() }.sortedBy { plano(it) }
        return VistaInventario(items, activos.size, categorias, elementosCompletos)
    }

    fun caducidad(p: Producto, hoy: LocalDate, diasAviso: Int): EstadoCaducidad {
        val fecha = p.fechaCaducidad ?: return EstadoCaducidad.SIN_FECHA
        return when {
            fecha.isBefore(hoy) -> EstadoCaducidad.VENCIDA
            !fecha.isAfter(hoy.plusDays(diasAviso.toLong())) -> EstadoCaducidad.PROXIMA
            else -> EstadoCaducidad.VIGENTE
        }
    }

    /** Mismas reglas que los contadores de Inicio ([Stock]) para que el número coincida con la lista. */
    private fun cumpleAlerta(i: ItemInventario, a: TipoAlerta): Boolean = if (i.insumo != null) when (a) {
        // P29: las alertas de insumos filtran las filas de insumos del Inventario.
        TipoAlerta.INSUMO_BAJO -> i.nivel == NivelStock.BAJO
        TipoAlerta.INSUMO_CRITICO -> i.nivel == NivelStock.CRITICO
        TipoAlerta.SIN_EXISTENCIA -> Stock.sinExistencia(i.insumo)
        else -> false
    } else when (a) {
        TipoAlerta.STOCK_BAJO -> i.nivel == NivelStock.BAJO
        TipoAlerta.STOCK_CRITICO -> i.nivel == NivelStock.CRITICO
        TipoAlerta.PROXIMO_A_CADUCAR -> i.caducidad == EstadoCaducidad.PROXIMA || i.caducidad == EstadoCaducidad.VENCIDA
        TipoAlerta.SIN_EXISTENCIA -> Stock.sinExistencia(i.producto)
        TipoAlerta.NOMBRE_REPETIDO -> i.nombreRepetido
        TipoAlerta.INSUMO_BAJO, TipoAlerta.INSUMO_CRITICO -> false
    }

    private fun coincide(p: Producto, q: String): Boolean =
        plano(p.nombre).contains(q) || plano(p.categoria).contains(q) ||
            p.descripcion?.let { plano(it).contains(q) } == true

    fun plano(s: String): String =
        Normalizer.normalize(s.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
