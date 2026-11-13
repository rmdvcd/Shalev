package cu.shalev.data.repository

import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.abortar
import cu.shalev.data.db.dao.localActivo
import cu.shalev.data.db.entity.ServicioInsumoEntity
import cu.shalev.data.db.tx
import cu.shalev.data.mapper.toDomain
import cu.shalev.data.mapper.toEntity
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.Servicio
import cu.shalev.domain.repository.ServicioRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** P29: servicios + insumos que consumen, siempre escritos juntos en una transacción. */
@Singleton
class ServicioRepositoryImpl @Inject constructor(
    private val db: ShalevDatabase,
    private val clock: Clock,
) : ServicioRepository {

    private val dao = db.servicioDao()
    private val locales = db.almacenLocalDao()

    override suspend fun fotosEnUso(): Set<String> = dao.todos().filterNot { it.eliminado }.mapNotNull { it.fotoUri }.toSet()

    override fun observarTodos(): Flow<List<Servicio>> = kotlinx.coroutines.flow.flow {
        val local = kotlinx.coroutines.currentCoroutineContext()[OrigenMovimiento]?.local
        if (local != null) emit(dao.todos().filter { it.local == local && !it.eliminado }.map { it.toDomain() })
        else emitAll(dao.observarTodos().map { l -> l.map { it.toDomain() } })
    }

    override fun observarInsumos(): Flow<Map<Long, List<RecetaLinea>>> =
        dao.observarInsumos().map { l -> l.groupBy({ it.servicioId }, { it.toDomain() }) }

    override suspend fun obtener(id: Long) = dao.obtener(id)?.toDomain()
    override suspend fun obtenerVarios(ids: Collection<Long>) =
        if (ids.isEmpty()) emptyList() else ids.distinct().chunked(900).flatMap { dao.obtenerVarios(it) }.map { it.toDomain() }
    override suspend fun insumos(servicioId: Long) = dao.insumos(servicioId).map { it.toDomain() }
    override suspend fun tiposEnUso() = dao.tiposEnUso()

    override suspend fun crear(servicio: Servicio, insumos: List<RecetaLinea>): AppResult<Long> = db.tx {
        val id = dao.insertar(servicio.toEntity().copy(id = 0, eliminado = false, local = locales.localOperacion()))
        guardarInsumos(id, insumos)
        id
    }

    override suspend fun actualizar(servicio: Servicio, insumos: List<RecetaLinea>): AppResult<Unit> = db.tx {
        val viejo = dao.obtener(servicio.id)?.takeUnless { it.eliminado } ?: abortar(AppError.NoEncontrado)
        if (viejo.local != locales.localOperacion()) abortar(AppError.SinPermiso)
        dao.actualizar(servicio.toEntity().copy(creadoEn = viejo.creadoEn, eliminado = false, local = viejo.local))
        dao.borrarInsumosDe(servicio.id)
        guardarInsumos(servicio.id, insumos)
    }

    /** Lógico: las ventas guardan nombre e importe congelados. Se sueltan sus insumos para poder borrarlos. */
    override suspend fun eliminar(id: Long): AppResult<Unit> = db.tx {
        val servicio = dao.obtener(id)?.takeUnless { it.eliminado } ?: abortar(AppError.NoEncontrado)
        if (servicio.local != locales.localOperacion()) abortar(AppError.SinPermiso)
        if (dao.marcarEliminado(id, clock.now().toEpochMilli()) == 0) abortar(AppError.NoEncontrado)
        dao.borrarInsumosDe(id)
    }

    override suspend fun serviciosQueUsan(insumoId: Long): List<Servicio> =
        obtenerVarios(dao.serviciosQueUsan(insumoId)).filterNot { it.eliminado }

    private suspend fun guardarInsumos(id: Long, insumos: List<RecetaLinea>) {
        val local = locales.localOperacion()
        for (l in insumos) {
            val insumo = db.insumoDao().obtener(l.insumoId) ?: abortar(AppError.NoEncontrado)
            if (insumo.local != local) abortar(AppError.SinPermiso)
        }
        if (insumos.isNotEmpty()) dao.insertarInsumos(insumos.map { ServicioInsumoEntity(id, it.insumoId, it.cantidad.milesimas) })
    }
}
