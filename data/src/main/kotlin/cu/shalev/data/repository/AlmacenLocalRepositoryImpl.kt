package cu.shalev.data.repository

import androidx.room.withTransaction
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.AlmacenLocalEntity
import cu.shalev.domain.repository.AlmacenLocal
import cu.shalev.domain.repository.AlmacenLocalRepository
import cu.shalev.domain.repository.NombresAlmacenLocal
import cu.shalev.domain.repository.VentanaUbicacion
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AlmacenLocalRepositoryImpl @Inject constructor(private val db: ShalevDatabase) : AlmacenLocalRepository {
    override fun observar() = db.almacenLocalDao().observar().map { xs ->
        xs.map { AlmacenLocal(it.nombre, it.inventario, it.servicios) }
    }

    override suspend fun seleccionar(ventana: VentanaUbicacion, nombre: String) {
        val limpio = NombresAlmacenLocal.validar(nombre)
        db.withTransaction {
            val dao = db.almacenLocalDao()
            val existente = dao.todos().firstOrNull { it.nombre.equals(limpio, ignoreCase = true) }?.nombre ?: limpio
            dao.insertar(AlmacenLocalEntity(existente))
            when (ventana) {
                VentanaUbicacion.INVENTARIO -> dao.inventario(existente)
                VentanaUbicacion.SERVICIOS -> dao.servicios(existente)
            }
        }
    }
}
