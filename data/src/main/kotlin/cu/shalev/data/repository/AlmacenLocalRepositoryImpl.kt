package cu.shalev.data.repository

import androidx.room.withTransaction
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.AlmacenLocalEntity
import cu.shalev.domain.repository.Local
import cu.shalev.domain.repository.LocalRepository
import cu.shalev.domain.repository.NombresLocal
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LocalRepositoryImpl @Inject constructor(private val db: ShalevDatabase) : LocalRepository {
    override fun observar() = db.almacenLocalDao().observar().map { xs ->
        xs.map { Local(it.nombre) }
    }

    override suspend fun seleccionar(nombre: String) {
        val limpio = NombresLocal.validar(nombre)
        db.withTransaction {
            val dao = db.almacenLocalDao()
            val existente = dao.todos().firstOrNull { it.nombre.equals(limpio, ignoreCase = true) }?.nombre ?: limpio
            dao.insertar(AlmacenLocalEntity(existente, inventario = true, servicios = true))
        }
    }
}
