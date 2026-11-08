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
        // Respaldos antiguos pueden contener dos selecciones distintas: priorizar Inventario.
        val actual = xs.firstOrNull { it.inventario } ?: xs.firstOrNull { it.servicios }
        xs.map { Local(it.nombre, it.nombre == actual?.nombre) }
    }

    override suspend fun seleccionar(nombre: String) {
        db.withTransaction {
            val dao = db.almacenLocalDao()
            val existente = dao.todos().firstOrNull { it.nombre == nombre }
                ?: throw IllegalArgumentException("El local ya no existe.")
            // Cambiar de local con un turno abierto mezclaría la caja de dos locales.
            if (existente.nombre != dao.activo() && db.turnoDao().activo() != null) {
                throw IllegalArgumentException("Cierra el turno antes de cambiar de local.")
            }
            dao.inventario(existente.nombre)
            dao.servicios(existente.nombre)
        }
    }

    override suspend fun guardar(nombre: String, anterior: String?) {
        val limpio = NombresLocal.validar(nombre)
        db.withTransaction {
            val dao = db.almacenLocalDao()
            val todos = dao.todos()
            val previo = anterior?.let { nombreAnterior -> todos.firstOrNull { it.nombre == nombreAnterior }
                ?: throw IllegalArgumentException("El local ya no existe.") }
            require(todos.none { it.nombre != anterior && it.nombre.equals(limpio, ignoreCase = true) }) {
                "Ya existe un local con ese nombre."
            }
            if (previo != null) dao.borrarNombre(previo.nombre)
            dao.insertar(AlmacenLocalEntity(limpio, inventario = previo?.inventario ?: false, servicios = previo?.servicios ?: false))
            // v16: el nombre del local es la clave del catálogo y del historial; renombrarlo arrastra sus datos.
            if (previo != null && previo.nombre != limpio) {
                val sql = db.openHelper.writableDatabase
                TABLAS_CON_LOCAL.forEach { sql.execSQL("UPDATE `$it` SET `local` = ? WHERE `local` = ?", arrayOf(limpio, previo.nombre)) }
            }
            if (todos.isEmpty()) {
                dao.inventario(limpio)
                dao.servicios(limpio)
            }
        }
    }

    private companion object {
        /** Tablas cuya fila pertenece a un local (v16). */
        val TABLAS_CON_LOCAL = listOf("producto", "insumo", "servicio", "turno", "venta", "transaccion", "movimiento")
    }
}
