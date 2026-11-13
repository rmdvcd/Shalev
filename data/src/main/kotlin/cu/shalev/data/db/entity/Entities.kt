package cu.shalev.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Esquema Room (cifrado con SQLCipher). Convenciones:
 *  - Importes en centavos (…Cent: Long), cantidades de insumo en milésimas (…Mil: Long).
 *  - Instantes en epoch millis UTC (Long); fechas de calendario en epochDay (Long).
 *  - Enums como TEXT con su name(): legible en respaldos y estable ante reordenaciones.
 *  - Sin TypeConverters: entidades planas y mapeo explícito en mapper/.
 */

@Entity(
    tableName = "producto",
    indices = [
        Index("categoria"),
        Index("eliminado", "creadoEn"),
        Index("fechaCaducidad"),
    ],
)
data class ProductoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoria: String,
    val nombre: String,
    val descripcion: String?,
    val fotoUri: String?,
    val fechaCaducidad: Long?,
    val precioCostoCent: Long,
    val precioVentaCent: Long,
    val cantidad: Long,
    val nivelBajo: Long?,
    val nivelCritico: Long?,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val eliminado: Boolean,
    /** v16: local al que pertenece el artículo (su inventario es independiente por local). */
    @ColumnInfo(defaultValue = "") val local: String = "",
    @ColumnInfo(defaultValue = "0") val costoAdicionalCent: Long = 0,
)

@Entity(tableName = "insumo", indices = [Index("creadoEn"), Index("nombre")])
data class InsumoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val unidad: String,
    val precioCent: Long,
    val cantidadMil: Long,
    val nivelBajoMil: Long?,
    val nivelCriticoMil: Long?,
    val creadoEn: Long,
    val actualizadoEn: Long,
    /** v4 (P29): precio de venta por unidad de medida; null = no se vende suelto. */
    val precioVentaCent: Long? = null,
    /** v16: local dueño del insumo (stock independiente por local). */
    @ColumnInfo(defaultValue = "") val local: String = "",
)

@Entity(
    tableName = "receta_linea",
    primaryKeys = ["productoId", "insumoId"],
    foreignKeys = [
        ForeignKey(entity = ProductoEntity::class, parentColumns = ["id"], childColumns = ["productoId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = InsumoEntity::class, parentColumns = ["id"], childColumns = ["insumoId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("insumoId")],
)
data class RecetaLineaEntity(
    val productoId: Long,
    val insumoId: Long,
    val cantidadMil: Long,
)

/**
 * Resumen congelado: columnas nulas mientras el turno está abierto.
 * Incluye usuario de apertura/cierre, nº de ventas por método y movimientos por tipo de entidad.
 */
@Entity(tableName = "turno", indices = [Index("abiertoEn"), Index("cerradoEn"), Index(value = ["uuid"], unique = true)])
data class TurnoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val abiertoEn: Long,
    val cerradoEn: Long?,
    val numVentas: Int?,
    val unidades: Long?,
    val totalCent: Long?,
    val efectivoCent: Long?,
    val transferenciaCent: Long?,
    val costoCent: Long?,
    val numMovimientos: Int?,
    val abiertoPor: String? = null,
    val cerradoPor: String? = null,
    val ventasEfectivo: Int? = null,
    val ventasTransferencia: Int? = null,
    val movimientosProducto: Int? = null,
    val movimientosInsumo: Int? = null,
    /** v5 (P37): id global del turno (lo genera quien lo abre; la principal lo usa para no duplicar). */
    val uuid: String? = null,
    /** v5 (P37): en la principal, la app secundaria que lo abrió (null = turno de la propia principal). */
    val empleadoId: Long? = null,
    /** v5 (P37): en una secundaria, la principal ya tiene este turno en su estado actual (abierto/cerrado). */
    @ColumnInfo(defaultValue = "0") val sincronizado: Boolean = false,
    /** v8 (0.25.0): fondo de caja al abrir (null = turno anterior a 0.25.0, sin arqueo). */
    val fondoCent: Long? = null,
    /** v8: efectivo contado al cerrar (en una secundaria con el turno abierto: el declarado al solicitar el cierre). */
    val contadoCent: Long? = null,
    /** v8: entradas/salidas de efectivo y ventas anuladas, congeladas al cerrar. */
    val entradasCent: Long? = null,
    val salidasCent: Long? = null,
    val numAnuladas: Int? = null,
    val conteoCierreJson: String? = null,
    /** v16: local del turno (todo lo que ocurre en él pertenece a ese local). */
    @ColumnInfo(defaultValue = "") val local: String = "",
)

/** Totales desnormalizados (suma de sus detalles) para filtros por importe y agregados rápidos. */
@Entity(
    tableName = "venta",
    foreignKeys = [ForeignKey(entity = TurnoEntity::class, parentColumns = ["id"], childColumns = ["turnoId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("fecha"), Index("turnoId"), Index("totalCent"), Index(value = ["uuid"], unique = true)],
)
data class VentaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnoId: Long,
    val fecha: Long,
    val metodoPago: String,
    val totalCent: Long,
    val costoCent: Long,
    val unidades: Long,
    /** v5 (P37): id global de la venta (idempotencia al sincronizar). */
    val uuid: String? = null,
    /** v5 (P37): en la principal, la app secundaria que la hizo (null = venta de la propia principal). */
    val empleadoId: Long? = null,
    /** v5 (P37): en una secundaria, la principal ya la recibió. */
    @ColumnInfo(defaultValue = "0") val sincronizado: Boolean = false,
    /** v8 (0.25.0): anulada en la app principal (null = válida). */
    val anuladaEn: Long? = null,
    val motivoAnulacion: String? = null,
    val anuladaPor: String? = null,
    /** v8: «Modificar»: id de la venta original a la que sustituye. */
    val corrigeVentaId: Long? = null,
    /**
     * v8: en la principal, cambio hecho aquí (anulación, o venta corregida) en el turno de una secundaria que aún hay que
     * enviarle para que su registro y su caja coincidan. 0 = nada pendiente.
     */
    @ColumnInfo(defaultValue = "0") val bajarCambio: Boolean = false,
    /** v16: local de la venta (se copia del turno). */
    @ColumnInfo(defaultValue = "") val local: String = "",
)

/**
 * v8 (0.25.0): entrada o salida de efectivo del turno (con motivo). [uuid] = id global (sincronización sin duplicar);
 * [sincronizado] = en una secundaria, la principal ya lo recibió.
 */
@Entity(
    tableName = "movimiento_caja",
    foreignKeys = [ForeignKey(entity = TurnoEntity::class, parentColumns = ["id"], childColumns = ["turnoId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("turnoId"), Index(value = ["uuid"], unique = true)],
)
data class MovimientoCajaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnoId: Long,
    val fecha: Long,
    val tipo: String,
    val importeCent: Long,
    val motivo: String,
    val hechoPor: String,
    val uuid: String? = null,
    @ColumnInfo(defaultValue = "0") val sincronizado: Boolean = false,
)

@Entity(
    tableName = "detalle_venta",
    foreignKeys = [ForeignKey(entity = VentaEntity::class, parentColumns = ["id"], childColumns = ["ventaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("ventaId"), Index("productoId")],
)
data class DetalleVentaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ventaId: Long,
    val productoId: Long,
    val nombre: String,
    val categoria: String,
    val cantidad: Long,
    val precioBaseCent: Long,
    val precioUnitarioCent: Long,
    val costoUnitarioCent: Long,
    /** v4 (P29): PRODUCTO, INSUMO o SERVICIO (indica a qué tabla apunta [productoId]). */
    @ColumnInfo(defaultValue = "PRODUCTO") val clase: String = "PRODUCTO",
)

@Entity(
    tableName = "transaccion",
    foreignKeys = [ForeignKey(entity = VentaEntity::class, parentColumns = ["id"], childColumns = ["ventaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("ventaId", unique = true), Index("fecha"), Index("numero"), Index("importeCent")],
)
data class TransaccionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ventaId: Long,
    val fecha: Long,
    val importeCent: Long,
    val numero: String,
    val clienteNombre: String,
    val clienteCi: String,
    val clienteTelefono: String,
    val tarjetaCobro: String?,
    val telefonoCobro: String?,
    /** 0.27.0 (N2): en la venta se marcó «Cliente fijo» (viaja a la principal al sincronizar). */
    @ColumnInfo(defaultValue = "0") val clienteFijo: Boolean = false,
    /** v16: local de la venta asociada. */
    @ColumnInfo(defaultValue = "") val local: String = "",
    @ColumnInfo(defaultValue = "0") val referenciaDuplicada: Boolean = false,
)

/**
 * 0.27.0 (N2): cliente fijo (registro + autocompletar en las ventas por transferencia). El carné es único: guardar
 * otra vez el mismo carné actualiza el nombre y el teléfono. Las compras se calculan de las transferencias.
 */
@Entity(tableName = "cliente_fijo", indices = [Index(value = ["ci"], unique = true)])
data class ClienteFijoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombreApellidos: String,
    val ci: String,
    val telefono: String,
    val creadoEn: Long,
    val actualizadoEn: Long,
)

@Entity(tableName = "movimiento", indices = [Index("fecha"), Index("entidad", "entidadId"), Index("turnoId")])
data class MovimientoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fecha: Long,
    val tipo: String,
    val entidad: String,
    val entidadId: Long,
    val nombre: String,
    val delta: Long,
    val existencia: Long,
    val turnoId: Long?,
    val ventaId: Long?,
    val nota: String?,
    /** v7 (0.21.0, C7): quién hizo el ajuste desde una app secundaria. Vacío = el del turno o, sin turno, el dueño. */
    @androidx.room.ColumnInfo(defaultValue = "") val hechoPor: String = "",
    /** v16: local del movimiento (el del turno o el del artículo ajustado). */
    @ColumnInfo(defaultValue = "") val local: String = "",
)

/** Fila única (id = 1). */
@Entity(tableName = "perfil")
data class PerfilEntity(
    @PrimaryKey val id: Long = 1,
    val nombre: String,
    val apellidos: String,
    val ci: String,
    val pagoTarjetaId: Long?,
    val pagoTelefonoId: Long?,
)

@Entity(tableName = "tarjeta", indices = [Index("numero", unique = true)])
data class TarjetaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val numero: String,
    val alias: String?,
)

@Entity(tableName = "telefono", indices = [Index("numero", unique = true)])
data class TelefonoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val numero: String,
    val alias: String?,
)

@Entity(tableName = "preajuste")
data class PreajusteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val puntosBasicos: Int,
    val metodoPago: String?,
    val importeMinimoCent: Long?,
    val activo: Boolean,
)

@Entity(
    tableName = "preajuste_producto",
    primaryKeys = ["preajusteId", "productoId"],
    foreignKeys = [
        ForeignKey(entity = PreajusteEntity::class, parentColumns = ["id"], childColumns = ["preajusteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductoEntity::class, parentColumns = ["id"], childColumns = ["productoId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("productoId")],
)
data class PreajusteProductoEntity(
    val preajusteId: Long,
    val productoId: Long,
)

@Entity(
    tableName = "preajuste_servicio",
    primaryKeys = ["preajusteId", "servicioId"],
    foreignKeys = [
        ForeignKey(entity = PreajusteEntity::class, parentColumns = ["id"], childColumns = ["preajusteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ServicioEntity::class, parentColumns = ["id"], childColumns = ["servicioId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("servicioId")],
)
data class PreajusteServicioEntity(
    val preajusteId: Long,
    val servicioId: Long,
)

/** v4 (P29): servicios que ofrece el negocio (borrado lógico). */
@Entity(tableName = "servicio", indices = [Index("tipo"), Index("eliminado", "creadoEn")])
data class ServicioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val tipo: String,
    val importeCent: Long,
    val descripcion: String?,
    val fotoUri: String?,
    val creadoEn: Long,
    val actualizadoEn: Long,
    val eliminado: Boolean,
    /** v16: local que ofrece el servicio (catálogo independiente por local). */
    @ColumnInfo(defaultValue = "") val local: String = "",
    @ColumnInfo(defaultValue = "0") val costoAdicionalCent: Long = 0,
)
@Entity(
    tableName = "servicio_insumo",
    primaryKeys = ["servicioId", "insumoId"],
    foreignKeys = [
        ForeignKey(entity = ServicioEntity::class, parentColumns = ["id"], childColumns = ["servicioId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = InsumoEntity::class, parentColumns = ["id"], childColumns = ["insumoId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("insumoId")],
)
data class ServicioInsumoEntity(
    val servicioId: Long,
    val insumoId: Long,
    val cantidadMil: Long,
)

/**
 * v5 (P37): apps secundarias registradas en la principal. [clave] = clave de 32 B acordada al vincular (Base64; la BD
 * ya va cifrada con SQLCipher). [codigoToken]/[codigoVence] = QR de un solo uso pendiente. [activo] = false cuando el
 * dueño la quita: se conserva la fila (y la clave) para poder decirle a esa app, de forma autenticada, que está fuera.
 */
@Entity(tableName = "empleado")
data class EmpleadoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    /** Nombres de PermisoEmpleado separados por comas. */
    val permisos: String,
    val creadoEn: Long,
    val vinculadoEn: Long?,
    val ultimaSincronizacion: Long?,
    val clave: String?,
    val codigoToken: String?,
    val codigoVence: Long?,
    val activo: Boolean,
    /** v6 (0.20.0, H4): ids de TarjetaPago/TelefonoPago del Perfil. NULL = los predeterminados. */
    val tarjetaId: Long? = null,
    val telefonoId: Long? = null,
    /** v6 (0.20.0, H5): el dueño pidió cerrar el turno de esta app (epoch ms). */
    val cierreSolicitadoEn: Long? = null,
    /** v7 (0.21.0, C2): teléfono que escribió el empleado al vincularse (8 dígitos). Va en su QR de cobro. */
    val telefono: String? = null,
    /** v7 (0.21.0, C6): el empleado pidió cerrar su turno (epoch ms). 0 = el dueño lo rechazó y falta avisarle. */
    val cierrePedidoPorEmpleadoEn: Long? = null,
    /** v9 (0.26.0, P73 §4): fondo de caja que asignó el dueño para el PRÓXIMO turno de esta app (NULL = sin asignar). */
    val fondoAsignadoCent: Long? = null,
    /** v9: cuándo se asignó (epoch ms). Sirve de identificador: la secundaria lo devuelve al gastarlo. */
    val fondoAsignadoEn: Long? = null,
    /** v9: el empleado pidió abrir turno (pide el fondo) (epoch ms). Se borra al asignarlo. */
    val aperturaSolicitadaEn: Long? = null,
    /** v9: versionCode de la app secundaria (lo envía al sincronizar; NULL = 0.24 o anterior / aún sin sincronizar). */
    val versionCode: Int? = null,
    /**
     * v12 (0.31.0, mitigación 7): `deviceId` (`SPVI:…`) de la app secundaria de este empleado. Lo manda al vincularse y
     * en cada saludo; la principal lo necesita para firmarle el sub-token de su ranura. NULL = todavía no se conoce
     * (app anterior a la 0.31.0 o secundaria que no lo ha mandado).
     */
    val device: String? = null,
    /** Local asignado al vincular; no depende del selector de la principal. */
    val localId: String? = null,
)

/**
 * Recibo permanente de un comando remoto con clave: la acción y su respuesta se guardan bajo la misma transacción.
 * El hash cubre el contenido completo de la acción; la clave queda acotada al empleado que la envió.
 */
@Entity(tableName = "comando_recibo", primaryKeys = ["empleadoId", "clave"])
data class ReciboComandoEntity(
    val empleadoId: Long,
    val clave: String,
    val huella: String,
    val valor: Long?,
    val error: String?,
)
