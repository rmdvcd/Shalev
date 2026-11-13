package cu.shalev.domain.usecase

import cu.shalev.core.time.Dates
import cu.shalev.domain.di.IoDispatcher
import cu.shalev.domain.repository.ServicioRepository
import cu.shalev.domain.service.Recetas
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.FichaProducto
import cu.shalev.domain.model.FiltroInventario
import cu.shalev.domain.model.LineaFicha
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.VistaInventario
import cu.shalev.domain.repository.ExportadorDocumentos
import cu.shalev.domain.repository.FotoRepository
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.repository.PreferenciasRepository
import cu.shalev.domain.repository.ProductoRepository
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.service.InventarioFiltro
import cu.shalev.domain.service.Stock
import cu.shalev.domain.service.TablasExport
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId
import cu.shalev.domain.model.Preferencias
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn

/** Tabla del Inventario reactiva: productos × preferencias (niveles, días de aviso) × filtro. */
class ObservarInventario @Inject constructor(
    private val productos: ProductoRepository,
    private val insumos: InsumoRepository,
    private val preferencias: PreferenciasRepository,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * 0.30.0 (F1): [InventarioFiltro.aplicar] recorre, cruza y ordena todos los productos (y usa una `LocalDate`),
     * y esto se recalculaba en el hilo del colector —el Main del ViewModel— cada vez que cambiaba cualquier flujo.
     */
    operator fun invoke(filtro: Flow<FiltroInventario>, zone: ZoneId = ZoneId.systemDefault()): Flow<VistaInventario> =
        combine(productos.observarTodos(), alcanceElaborados(productos, insumos), insumos.observarTodos(), preferencias.preferencias, filtro) { ps, al, xs, pref, f ->
            InventarioFiltro.aplicar(ps, f, pref.niveles, Dates.localDate(clock.now(), zone), Preferencias.DIAS_AVISO_CADUCIDAD, al, xs)
        }.flowOn(io)
}

/** Ficha al tocar un producto: datos + receta resuelta (Elaborado) + indicadores. */
class ObtenerFichaProducto @Inject constructor(
    private val productos: ProductoRepository,
    private val insumos: InsumoRepository,
    private val preferencias: PreferenciasRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(id: Long, zone: ZoneId = ZoneId.systemDefault()): AppResult<FichaProducto> {
        val p = productos.obtener(id)?.takeUnless { it.eliminado } ?: return AppResult.Err(AppError.NoEncontrado)
        val pref = preferencias.preferencias.first()
        var alcanza: Long? = null
        val lineas = if (p.esElaborado) {
            val r = productos.receta(id)
            val xs = r?.let { rec -> insumos.obtenerVarios(rec.lineas.map { it.insumoId }).associateBy { it.id } }.orEmpty()
            alcanza = r?.let { Recetas.maxUnidades(it, xs) } ?: 0
            r?.lineas.orEmpty().mapNotNull { l ->
                xs[l.insumoId]?.let { LineaFicha(it.id, it.nombre, l.cantidad, it.unidad.simbolo, it.precio.porMilesimas(l.cantidad.milesimas)) }
            }
        } else emptyList()
        val hoy = Dates.localDate(clock.now(), zone)
        return AppResult.Ok(FichaProducto(p, lineas, Stock.nivel(p, pref.niveles), InventarioFiltro.caducidad(p, hoy, Preferencias.DIAS_AVISO_CADUCIDAD), alcanza))
    }
}

/** Borrado de los productos marcados. Continúa aunque uno falle y devuelve cuántos se eliminaron. */
class EliminarProductos @Inject constructor(private val repo: ProductoRepository) {
    data class Resultado(val eliminados: Int, val fallidos: Int)

    suspend operator fun invoke(ids: Collection<Long>): Resultado {
        var ok = 0
        ids.distinct().forEach { if (repo.eliminar(it) is AppResult.Ok) ok++ }
        return Resultado(ok, ids.distinct().size - ok)
    }
}

/** Botón Exportar: la selección (o, sin selección, lo que se ve filtrado) en PDF, Excel o texto. */
class ExportarInventario @Inject constructor(private val exportador: ExportadorDocumentos) {
    suspend operator fun invoke(productos: List<Producto>, formato: FormatoExport, destino: OutputStream): AppResult<Unit> {
        if (productos.isEmpty()) return AppResult.Err(AppError.Validacion("productos", AppError.Regla.REQUERIDO))
        return exportador.exportar(listOf(TablasExport.inventario(productos)), formato, destino)
    }
}

/** Compartir la ficha en PDF (tabla Campo/Valor). El texto y la imagen se generan en presentación. */
class ExportarFicha @Inject constructor(private val exportador: ExportadorDocumentos) {
    suspend operator fun invoke(ficha: FichaProducto, destino: OutputStream): AppResult<Unit> =
        exportador.exportar(listOf(TablasExport.ficha(ficha)), FormatoExport.PDF, destino)
}

/** Limpieza de fotos huérfanas (productos borrados o formularios abandonados). */
class LimpiarFotos @Inject constructor(
    private val productos: ProductoRepository,
    private val fotos: FotoRepository,
    private val servicios: ServicioRepository,
) {
    suspend operator fun invoke() {
        // P29: también las fotos de los servicios están en uso.
        val enUso = productos.fotosEnUso() + servicios.fotosEnUso()
        fotos.limpiarHuerfanas(enUso)
    }
}
