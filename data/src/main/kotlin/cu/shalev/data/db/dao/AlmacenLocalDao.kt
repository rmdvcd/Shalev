package cu.shalev.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cu.shalev.data.db.entity.AlmacenLocalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlmacenLocalDao {
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
