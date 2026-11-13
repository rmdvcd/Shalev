package cu.shalev.data.db.entity

import androidx.room.Index
import java.util.UUID
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "almacen_local", indices = [Index(value = ["id"], unique = true)])
data class AlmacenLocalEntity(
    @PrimaryKey val nombre: String,
    @ColumnInfo(defaultValue = "0") val inventario: Boolean = false,
    @ColumnInfo(defaultValue = "0") val servicios: Boolean = false,
    /** Identidad estable: renombrar nunca cambia la pertenencia de una secundaria. */
    @ColumnInfo(defaultValue = "''") val id: String = UUID.randomUUID().toString(),
)
