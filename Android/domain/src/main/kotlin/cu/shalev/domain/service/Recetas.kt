package cu.shalev.domain.service

import cu.shalev.core.money.Cup
import cu.shalev.core.money.sumOfCup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.Receta

object Recetas {

    /** Costo de 1 unidad del Elaborado = Σ precio del insumo × cantidad (HALF_UP por línea). */
    fun costo(receta: Receta, insumos: Map<Long, Insumo>, adicional: Cup = Cup.ZERO): AppResult<Cup> {
        val lineas = receta.lineas.map { l -> (insumos[l.insumoId] ?: return AppResult.Err(AppError.NoEncontrado)) to l.cantidad }
        return try { AppResult.Ok(lineas.sumOfCup { (i, c) -> i.precio.porMilesimas(c.milesimas) } + adicional) }
        catch (_: ArithmeticException) { AppResult.Err(AppError.Validacion("precioCosto", AppError.Regla.RANGO)) }
    }

    fun consumo(receta: Receta, unidades: Long): Map<Long, Cantidad> =
        receta.lineas.associate { it.insumoId to it.cantidad * unidades }

    /** Nombres de los insumos sin existencias suficientes para [consumo]. */
    fun faltantes(consumo: Map<Long, Cantidad>, insumos: Map<Long, Insumo>): List<String> =
        consumo.mapNotNull { (id, c) -> insumos[id]?.takeIf { it.cantidad < c }?.nombre ?: if (id !in insumos) "#$id" else null }

    /**
     * Unidades completas que alcanzan con las existencias actuales (mínimo entre insumos). Sin receta o con un
     * insumo desconocido → 0. Una línea con cantidad 0 no limita.
     */
    fun maxUnidades(receta: Receta, insumos: Map<Long, Insumo>): Long {
        if (receta.lineas.isEmpty()) return 0
        return receta.lineas.filter { it.cantidad.milesimas > 0 }.minOfOrNull { l ->
            val i = insumos[l.insumoId] ?: return 0
            (i.cantidad.milesimas.coerceAtLeast(0)) / l.cantidad.milesimas
        } ?: 0
    }
}
