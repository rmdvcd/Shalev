package cu.shalev.domain.usecase

import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.result.map
import cu.shalev.core.result.toResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.repository.ProductoRepository
import cu.shalev.domain.service.Recetas
import cu.shalev.domain.validation.Validadores
import javax.inject.Inject

/**
 * Crea o edita un insumo. Si cambia su precio, recalcula el costo de los Elaborados que lo usan
 * (el costo de las ventas ya hechas no cambia: está congelado en cada línea).
 */
class GuardarInsumo @Inject constructor(
    private val insumos: InsumoRepository,
    private val productos: ProductoRepository,
    private val clock: Clock,
) {
    /** [cantidadLeida]: existencia mostrada al abrir la ficha (0.21.6, ver `Stock.cantidadAlGuardar`); null = manda la escrita. */
    suspend operator fun invoke(insumo: Insumo, cantidadLeida: Cantidad? = null): AppResult<Long> {
        val now = clock.now()
        val i = insumo.copy(nombre = insumo.nombre.trim())
        Validadores.insumo(i).toResult().let { if (it is AppResult.Err) return it }
        if (i.id == 0L) return insumos.crear(i.copy(creadoEn = now, actualizadoEn = now))

        val actual = insumos.obtener(i.id) ?: return AppResult.Err(AppError.NoEncontrado)
        val r = insumos.actualizar(i.copy(creadoEn = actual.creadoEn, actualizadoEn = now), cantidadLeida)
        if (r is AppResult.Err) return r
        if (actual.precio != i.precio) recalcularElaborados(i.id)
        return AppResult.Ok(i.id)
    }

    private suspend fun recalcularElaborados(insumoId: Long) {
        for (p in productos.productosQueUsan(insumoId)) {
            val receta = productos.receta(p.id) ?: continue
            val mapa = insumos.obtenerVarios(receta.lineas.map { it.insumoId }).associateBy { it.id }
            val costo = (Recetas.costo(receta, mapa) as? AppResult.Ok)?.value?.plus(p.costoAdicional) ?: continue
            if (costo != p.precioCosto) productos.actualizar(p.copy(precioCosto = costo), receta)
        }
    }
}
