package cu.shalev.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cu.shalev.data.db.entity.AlmacenLocalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlmacenLocalDao {
    @Query("UPDATE almacen_local SET id = :id WHERE nombre = :nombre")
    suspend fun identidad(nombre: String, id: String)
    @Query("SELECT * FROM almacen_local WHERE id = :id LIMIT 1")
    suspend fun porId(id: String): AlmacenLocalEntity?
    @Query("SELECT * FROM almacen_local WHERE nombre = :nombre LIMIT 1")
    suspend fun porNombre(nombre: String): AlmacenLocalEntity?
    @Query("UPDATE almacen_local SET nombre = :nuevo WHERE nombre = :anterior")
    suspend fun renombrar(anterior: String, nuevo: String)
    /** Local activo (el único marcado para Inventario). Lo usan las consultas por local. */
    @Query("SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1")
    suspend fun activo(): String?
    @Query("SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1")
    fun observarActivo(): Flow<String?>
    @Query("SELECT * FROM almacen_local ORDER BY nombre COLLATE NOCASE")
    fun observar(): Flow<List<AlmacenLocalEntity>>
    @Query("SELECT * FROM almacen_local ORDER BY nombre COLLATE NOCASE")
    suspend fun todos(): List<AlmacenLocalEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(valor: AlmacenLocalEntity)
    @Query("UPDATE almacen_local SET inventario = (nombre = :nombre)")
    suspend fun inventario(nombre: String)
    @Query("UPDATE almacen_local SET servicios = (nombre = :nombre)")
    suspend fun servicios(nombre: String)
    @Query("DELETE FROM almacen_local WHERE nombre = :nombre")
    suspend fun borrarNombre(nombre: String)
    @Query("DELETE FROM almacen_local")
    suspend fun borrar()
}

/**
 * Local activo, creándolo si la base no tiene ninguno (respaldos y secundarias anteriores a la v16).
 * Todo lo que se escribe en catálogo o historial se marca con este nombre.
 */
suspend fun AlmacenLocalDao.localActivo(): String {
    activo()?.let { return it }
    val existente = todos().firstOrNull()?.nombre
    val nombre = existente ?: "Local".also { insertar(AlmacenLocalEntity(it, inventario = true, servicios = true)) }
    inventario(nombre)
    servicios(nombre)
    return nombre
}

/** Identidad estable del local seleccionado. */
suspend fun AlmacenLocalDao.idActivo(): String = porNombre(localActivo())!!.id
