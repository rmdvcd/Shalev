package cu.shalev.data.repository

import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.abortar
import cu.shalev.data.db.tx
import cu.shalev.data.mapper.productosEntities
import cu.shalev.data.mapper.serviciosEntities
import cu.shalev.data.mapper.toDomain
import cu.shalev.data.mapper.toEntity
import cu.shalev.domain.model.PreajustePrecios
import cu.shalev.domain.repository.PreciosRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PreciosRepositoryImpl @Inject constructor(private val db: ShalevDatabase) : PreciosRepository {

    private val dao = db.preciosDao()
    private val productos = db.productoDao()
    private val servicios = db.servicioDao()

    override fun observarPreajustes(): Flow<List<PreajustePrecios>> = dao.observarPreajustes().map { l -> l.map { it.toDomain() } }
    override suspend fun preajustesActivos() = dao.activos().map { it.toDomain() }

    /** Los productos inexistentes o eliminados se descartan en silencio (pudieron borrarse mientras se editaba). */
    override suspend fun guardarPreajuste(p: PreajustePrecios): AppResult<Long> = db.tx {
        val id = if (p.id == 0L) dao.insertar(p.toEntity().copy(id = 0)) else p.id.also { dao.guardar(p.toEntity()) }
        dao.borrarProductos(id)
        val validos = p.productoIds.toList().chunked(900).flatMap { productos.obtenerVarios(it) }
            .filterNot { it.eliminado }.map { it.id }.toSet()
        dao.insertarProductos(p.copy(productoIds = validos).productosEntities(id))
        dao.borrarServicios(id)
        val serviciosValidos = p.servicioIds.toList().chunked(900).flatMap { servicios.obtenerVarios(it) }
            .filterNot { it.eliminado }.map { it.id }.toSet()
        dao.insertarServicios(p.copy(servicioIds = serviciosValidos).serviciosEntities(id))
        id
    }

    override suspend fun eliminarPreajuste(id: Long): AppResult<Unit> = db.tx {
        if (dao.borrar(id) == 0) abortar(AppError.NoEncontrado)
    }
}
