package cu.shalev.data.db.entity

import kotlinx.serialization.Serializable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Fichas de costo del negocio: dentro de la misma base cifrada y del respaldo. */
@Serializable
@Entity(tableName = "ficha_costo", indices = [Index("localId")])
data class FichaCostoEntity(
    @PrimaryKey val id: Long,
    val localId: String,
    val nombre: String,
    val margen: String,
    val unidadMargen: String,
    val tasa: String,
    val partidasJson: String,
    val actualizadoEn: Long,
)

/** Recepción inmutable: proveedor y comprobante quedan congelados, no dependen del nombre actual del artículo. */
@Serializable
@Entity(tableName = "recepcion", indices = [Index("localId", "fecha")])
data class RecepcionEntity(
    @PrimaryKey val uuid: String,
    val localId: String,
    val fecha: Long,
    val proveedor: String,
    val comprobante: String,
    val motivo: String,
    val hechoPor: String,
    val totalCent: Long,
)

/** Todas las cantidades de lote van en milésimas, incluso para productos (1 u = 1000). */
@Serializable
@Entity(tableName = "lote_inventario", indices = [Index("recepcionId"), Index("localId", "entidad", "entidadId")])
data class LoteInventarioEntity(
    @PrimaryKey val id: String,
    val recepcionId: String,
    val localId: String,
    val entidad: String,
    val entidadId: Long,
    val nombre: String,
    val unidad: String,
    val codigo: String,
    val cantidadInicialMil: Long,
    val disponibleMil: Long,
    val costoUnitarioCent: Long,
    val caducidad: Long?,
)

/** Asignación de salidas a lotes; pertenece a una venta O a un movimiento manual, nunca a ambos. */
@Serializable
@Entity(tableName = "lote_consumo", indices = [Index("ventaId"), Index("loteId"), Index("movimientoId")])
data class LoteConsumoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ventaId: Long? = null,
    val loteId: String,
    val cantidadMil: Long,
    val movimientoId: Long? = null,
)

/** Bitácora append-only de correcciones; no sustituye ni trunca el antes/después de una corrección anterior. */
@Serializable
@Entity(tableName = "movimiento_revision", indices = [Index("movimientoId"), Index("local", "fecha")])
data class MovimientoRevisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val movimientoId: Long,
    val fecha: Long,
    val deltaAnterior: Long,
    val deltaNuevo: Long,
    val existenciaAnterior: Long,
    val existenciaNueva: Long,
    val hechoPor: String,
    val motivo: String,
    val local: String,
)

/** Preferencias del negocio en Room: se restauran en la misma transacción que las ventas y fichas. */
@Serializable
@Entity(tableName = "configuracion_negocio")
data class ConfiguracionNegocioEntity(@PrimaryKey val id: Int = 1, val preferenciasJson: String)

/** Metadatos de migración de ESTE teléfono, no del respaldo del negocio. Evita resucitar JSON consumidos. */
@Entity(tableName = "migracion_local")
data class MigracionLocalEntity(@PrimaryKey val clave: String)
