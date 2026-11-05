package cu.shalev.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "almacen_local")
data class AlmacenLocalEntity(
    @PrimaryKey val nombre: String,
    @ColumnInfo(defaultValue = "0") val inventario: Boolean = false,
    @ColumnInfo(defaultValue = "0") val servicios: Boolean = false,
)
