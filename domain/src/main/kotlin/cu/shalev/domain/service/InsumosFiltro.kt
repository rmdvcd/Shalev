package cu.shalev.domain.service

import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.ElaboradoDisponible
import cu.shalev.domain.model.FiltroInsumos
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.ItemInsumo
import cu.shalev.domain.model.LineaProduccion
import cu.shalev.domain.model.NivelStock
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.UsoInsumo
import cu.shalev.domain.model.VistaInsumos

/**
 * Lista de Elaboración (pura). Conserva el orden cronológico del repositorio (más recientes primero).
 * Mismo buscador que Inventario ([InventarioFiltro.plano]: sin mayúsculas ni tildes) y mismas reglas de
 * nivel que los contadores de Inicio ([Stock.nivel]) para que los números coincidan.
 */
object InsumosFiltro {

    /** Nº de recetas en las que aparece cada insumo. */
    fun usos(recetas: Collection<Receta>): Map<Long, Int> =
        recetas.flatMap { r -> r.lineas.map { it.insumoId }.distinct() }.groupingBy { it }.eachCount()

    fun aplicar(xs: List<Insumo>, usos: Map<Long, Int>, f: FiltroInsumos, n: NivelesMinimos): VistaInsumos {
        val q = InventarioFiltro.plano(f.texto)
        val items = xs.asSequence()
            .filter { q.isEmpty() || InventarioFiltro.plano(it.nombre).contains(q) }
            .map { ItemInsumo(it, Stock.nivel(it, n), usos[it.id] ?: 0) }
            .filter {
                when (f.uso) {
                    UsoInsumo.TODOS -> true
                    UsoInsumo.EN_RECETAS -> it.usos > 0
                    UsoInsumo.SIN_USO -> it.usos == 0
                }
            }
            .filter {
                when (f.alerta) {
                    null -> true
                    TipoAlerta.INSUMO_BAJO -> it.nivel == NivelStock.BAJO
                    TipoAlerta.INSUMO_CRITICO -> it.nivel == NivelStock.CRITICO
                    TipoAlerta.SIN_EXISTENCIA -> Stock.sinExistencia(it.insumo)
                    else -> false // las alertas de productos no aplican a insumos
                }
            }
            .toList()
        return VistaInsumos(items, xs.size)
    }

    /** Receta del Elaborado resuelta contra las existencias actuales (sección Elaborados). */
    fun disponible(p: Producto, receta: Receta?, insumos: Map<Long, Insumo>): ElaboradoDisponible {
        val lineas = receta?.lineas.orEmpty().map { l ->
            val i = insumos[l.insumoId]
            LineaProduccion(
                insumoId = l.insumoId, nombre = i?.nombre ?: "Insumo eliminado", simbolo = i?.unidad?.simbolo.orEmpty(),
                porUnidad = l.cantidad, disponible = i?.cantidad ?: cu.shalev.core.quantity.Cantidad.ZERO, existe = i != null,
            )
        }
        val costo = receta?.takeIf { it.lineas.isNotEmpty() }?.let { (Recetas.costo(it, insumos) as? AppResult.Ok)?.value }
        return ElaboradoDisponible(p, lineas, costo?.plus(p.costoAdicional))
    }
}
