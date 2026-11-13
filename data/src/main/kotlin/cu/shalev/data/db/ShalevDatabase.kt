package cu.shalev.data.db

import cu.shalev.data.db.entity.ClienteFijoEntity
import cu.shalev.data.db.dao.ClienteFijoDao
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import cu.shalev.data.db.dao.CajaDao
import cu.shalev.data.db.dao.ServicioDao
import cu.shalev.data.db.entity.ServicioInsumoEntity
import cu.shalev.data.db.entity.ServicioEntity
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import cu.shalev.data.db.dao.InsumoDao
import cu.shalev.data.db.dao.MantenimientoDao
import cu.shalev.data.db.dao.MovimientoDao
import cu.shalev.data.db.dao.PerfilDao
import cu.shalev.data.db.dao.PreciosDao
import cu.shalev.data.db.dao.ProductoDao
import cu.shalev.data.db.dao.RecetaDao
import cu.shalev.data.db.dao.TurnoDao
import cu.shalev.data.db.dao.VentaDao
import cu.shalev.data.db.entity.DetalleVentaEntity
import cu.shalev.data.db.entity.EmpleadoEntity
import cu.shalev.data.db.dao.SyncDao
import cu.shalev.data.db.entity.InsumoEntity
import cu.shalev.data.db.entity.MovimientoEntity
import cu.shalev.data.db.entity.MovimientoCajaEntity
import cu.shalev.data.db.entity.PerfilEntity
import cu.shalev.data.db.entity.PreajusteEntity
import cu.shalev.data.db.entity.PreajusteProductoEntity
import cu.shalev.data.db.entity.PreajusteServicioEntity
import cu.shalev.data.db.entity.ProductoEntity
import cu.shalev.data.db.entity.RecetaLineaEntity
import cu.shalev.data.db.entity.TarjetaEntity
import cu.shalev.data.db.entity.TelefonoEntity
import cu.shalev.data.db.entity.TransaccionEntity
import cu.shalev.data.db.entity.TurnoEntity
import cu.shalev.data.db.entity.VentaEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        ProductoEntity::class, InsumoEntity::class, RecetaLineaEntity::class, TurnoEntity::class,
        VentaEntity::class, DetalleVentaEntity::class, TransaccionEntity::class, MovimientoEntity::class,
        PerfilEntity::class, TarjetaEntity::class, TelefonoEntity::class, PreajusteEntity::class,
        PreajusteProductoEntity::class, PreajusteServicioEntity::class, ServicioEntity::class, ServicioInsumoEntity::class, EmpleadoEntity::class,
        MovimientoCajaEntity::class, ClienteFijoEntity::class, cu.shalev.data.db.entity.AlmacenLocalEntity::class,
        cu.shalev.data.db.entity.ReciboComandoEntity::class,
        cu.shalev.data.db.entity.FichaCostoEntity::class, cu.shalev.data.db.entity.RecepcionEntity::class,
        cu.shalev.data.db.entity.LoteInventarioEntity::class, cu.shalev.data.db.entity.LoteConsumoEntity::class,
        cu.shalev.data.db.entity.MovimientoRevisionEntity::class, cu.shalev.data.db.entity.ConfiguracionNegocioEntity::class,
    ],
    version = ShalevDatabase.VERSION,
    exportSchema = true,
)
abstract class ShalevDatabase : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun recetaDao(): RecetaDao
    abstract fun insumoDao(): InsumoDao
    abstract fun movimientoDao(): MovimientoDao
    abstract fun ventaDao(): VentaDao
    abstract fun turnoDao(): TurnoDao
    abstract fun perfilDao(): PerfilDao
    abstract fun preciosDao(): PreciosDao
    abstract fun mantenimientoDao(): MantenimientoDao
    abstract fun servicioDao(): ServicioDao
    abstract fun syncDao(): SyncDao
    abstract fun cajaDao(): CajaDao
    abstract fun clienteFijoDao(): ClienteFijoDao

    abstract fun gestionDao(): cu.shalev.data.db.dao.GestionDao

    abstract fun almacenLocalDao(): cu.shalev.data.db.dao.AlmacenLocalDao

    companion object {
        /**
         * v3 (P17): esquema consolidado, sin `tasa_cambio` ni `caducidad_codigo`. Es la base de las migraciones
         * futuras: cada versión nueva añade una `Migration(3, 4)`… explícita (regla: nunca borrar datos de usuarios).
         */
        const val VERSION = 18
        /** v18: identidad estable de locales, costo completo, fichas y trazabilidad de inventario. Sin borrado. */
        val MIGRACION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) { SQL_17_18.forEach(db::execSQL) }
        }
        internal val SQL_17_18 = listOf(
            "ALTER TABLE `almacen_local` ADD COLUMN `id` TEXT NOT NULL DEFAULT ''",
            "UPDATE `almacen_local` SET `id` = lower(hex(randomblob(16))) WHERE id = ''",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_almacen_local_id` ON `almacen_local` (`id`)",
            "ALTER TABLE `empleado` ADD COLUMN `localId` TEXT",
            "UPDATE `empleado` SET localId = (SELECT id FROM almacen_local WHERE nombre = COALESCE((SELECT local FROM turno WHERE turno.empleadoId = empleado.id AND cerradoEn IS NULL LIMIT 1), (SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1)))",
            "ALTER TABLE `movimiento` ADD COLUMN `motivo` TEXT",
            "ALTER TABLE `transaccion` ADD COLUMN `referenciaDuplicada` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `producto` ADD COLUMN `costoAdicionalCent` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `servicio` ADD COLUMN `costoAdicionalCent` INTEGER NOT NULL DEFAULT 0",
            "CREATE TABLE IF NOT EXISTS `configuracion_negocio` (`id` INTEGER NOT NULL, `preferenciasJson` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `ficha_costo` (`id` INTEGER NOT NULL, `localId` TEXT NOT NULL, `nombre` TEXT NOT NULL, `margen` TEXT NOT NULL, `unidadMargen` TEXT NOT NULL, `tasa` TEXT NOT NULL, `partidasJson` TEXT NOT NULL, `actualizadoEn` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_ficha_costo_localId` ON `ficha_costo` (`localId`)",
            "CREATE TABLE IF NOT EXISTS `recepcion` (`uuid` TEXT NOT NULL, `localId` TEXT NOT NULL, `fecha` INTEGER NOT NULL, `proveedor` TEXT NOT NULL, `comprobante` TEXT NOT NULL, `motivo` TEXT NOT NULL, `hechoPor` TEXT NOT NULL, `totalCent` INTEGER NOT NULL, PRIMARY KEY(`uuid`))",
            "CREATE INDEX IF NOT EXISTS `index_recepcion_localId_fecha` ON `recepcion` (`localId`, `fecha`)",
            "CREATE TABLE IF NOT EXISTS `lote_inventario` (`id` TEXT NOT NULL, `recepcionId` TEXT NOT NULL, `localId` TEXT NOT NULL, `entidad` TEXT NOT NULL, `entidadId` INTEGER NOT NULL, `nombre` TEXT NOT NULL, `unidad` TEXT NOT NULL, `codigo` TEXT NOT NULL, `cantidadInicialMil` INTEGER NOT NULL, `disponibleMil` INTEGER NOT NULL, `costoUnitarioCent` INTEGER NOT NULL, `caducidad` INTEGER, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_lote_inventario_recepcionId` ON `lote_inventario` (`recepcionId`)",
            "CREATE INDEX IF NOT EXISTS `index_lote_inventario_localId_entidad_entidadId` ON `lote_inventario` (`localId`, `entidad`, `entidadId`)",
            "CREATE TABLE IF NOT EXISTS `lote_consumo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `ventaId` INTEGER NOT NULL, `loteId` TEXT NOT NULL, `cantidadMil` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_lote_consumo_ventaId` ON `lote_consumo` (`ventaId`)",
            "CREATE INDEX IF NOT EXISTS `index_lote_consumo_loteId` ON `lote_consumo` (`loteId`)",
            "CREATE TABLE IF NOT EXISTS `movimiento_revision` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `movimientoId` INTEGER NOT NULL, `fecha` INTEGER NOT NULL, `deltaAnterior` INTEGER NOT NULL, `deltaNuevo` INTEGER NOT NULL, `existenciaAnterior` INTEGER NOT NULL, `existenciaNueva` INTEGER NOT NULL, `hechoPor` TEXT NOT NULL, `motivo` TEXT NOT NULL, `local` TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_movimiento_revision_movimientoId` ON `movimiento_revision` (`movimientoId`)",
            "CREATE INDEX IF NOT EXISTS `index_movimiento_revision_local_fecha` ON `movimiento_revision` (`local`, `fecha`)",
        )
        /**
         * v17: recibos persistentes de comandos idempotentes de las secundarias. No se podan: se conservan para que
         * un reintento tardío nunca vuelva a aplicar una acción ya confirmada.
         */
        val MIGRACION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) { SQL_16_17.forEach(db::execSQL) }
        }
        internal val SQL_16_17 = listOf(
            "CREATE TABLE IF NOT EXISTS `comando_recibo` (`empleadoId` INTEGER NOT NULL, `clave` TEXT NOT NULL, `huella` TEXT NOT NULL, `valor` INTEGER, `error` TEXT, PRIMARY KEY(`empleadoId`, `clave`))",
        )
        /**
         * v16: cada local tiene su propio catálogo y su propio historial. Los datos existentes se asignan al local
         * activo; si no hay ninguno, se crea «Local».
         */
        val MIGRACION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_15_16.forEach(db::execSQL)
            }
        }
        internal val SQL_15_16 = buildList {
            add("INSERT INTO almacen_local(nombre, inventario, servicios) SELECT 'Local', 1, 1 WHERE NOT EXISTS (SELECT 1 FROM almacen_local)")
            val activo = "COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), (SELECT nombre FROM almacen_local ORDER BY nombre LIMIT 1), 'Local')"
            listOf("producto", "insumo", "servicio", "turno", "venta", "transaccion", "movimiento").forEach { tabla ->
                add("ALTER TABLE `$tabla` ADD COLUMN `local` TEXT NOT NULL DEFAULT ''")
                add("UPDATE `$tabla` SET `local` = $activo")
            }
        }
        /** Ubicaciones generales de Inventario/Servicios. No modifica los registros existentes. */
        val MIGRACION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) { SQL_14_15.forEach(db::execSQL) }
        }
        internal val SQL_14_15 = listOf(
            "CREATE TABLE IF NOT EXISTS `almacen_local` (`nombre` TEXT NOT NULL, `inventario` INTEGER NOT NULL DEFAULT 0, `servicios` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`nombre`))",
        )
        val MIGRACION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) { SQL_13_14.forEach(db::execSQL) }
        }
        internal val SQL_13_14 = listOf(
            "CREATE TABLE IF NOT EXISTS `preajuste_servicio` (`preajusteId` INTEGER NOT NULL, `servicioId` INTEGER NOT NULL, PRIMARY KEY(`preajusteId`, `servicioId`), FOREIGN KEY(`preajusteId`) REFERENCES `preajuste`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`servicioId`) REFERENCES `servicio`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_preajuste_servicio_servicioId` ON `preajuste_servicio` (`servicioId`)",
            // El mínimo deja de formar parte de la función de Precios.
            "UPDATE `preajuste` SET `importeMinimoCent` = NULL",
        )
        val MIGRACION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_12_13.forEach(db::execSQL)
            }
        }
        internal val SQL_12_13 = listOf(
            "ALTER TABLE `turno` ADD COLUMN `conteoCierreJson` TEXT",
        )
        const val NOMBRE = "shalev.db"

        /**
         * v4 (P29): servicios (+ insumos que consumen), precio de venta opcional de insumos y clase de cada línea
         * vendida. Solo añade: no toca ni borra datos existentes (las líneas antiguas quedan como PRODUCTO).
         * El SQL replica EXACTAMENTE el que genera Room para las entidades (si no, la validación del esquema falla).
         */
        val MIGRACION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_3_4.forEach(db::execSQL)
            }
        }

        internal val SQL_3_4 = listOf(
            "ALTER TABLE `insumo` ADD COLUMN `precioVentaCent` INTEGER",
            "ALTER TABLE `detalle_venta` ADD COLUMN `clase` TEXT NOT NULL DEFAULT 'PRODUCTO'",
            "CREATE TABLE IF NOT EXISTS `servicio` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nombre` TEXT NOT NULL, " +
                "`tipo` TEXT NOT NULL, `importeCent` INTEGER NOT NULL, `descripcion` TEXT, `fotoUri` TEXT, " +
                "`creadoEn` INTEGER NOT NULL, `actualizadoEn` INTEGER NOT NULL, `eliminado` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_servicio_tipo` ON `servicio` (`tipo`)",
            "CREATE INDEX IF NOT EXISTS `index_servicio_eliminado_creadoEn` ON `servicio` (`eliminado`, `creadoEn`)",
            "CREATE TABLE IF NOT EXISTS `servicio_insumo` (`servicioId` INTEGER NOT NULL, `insumoId` INTEGER NOT NULL, " +
                "`cantidadMil` INTEGER NOT NULL, PRIMARY KEY(`servicioId`, `insumoId`), " +
                "FOREIGN KEY(`servicioId`) REFERENCES `servicio`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`insumoId`) REFERENCES `insumo`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_servicio_insumo_insumoId` ON `servicio_insumo` (`insumoId`)",
        )

        /**
         * v5 (P37): apps secundarias. Tabla `empleado` y, en `turno` y `venta`, id global (`uuid`), app secundaria de
         * origen (`empleadoId`) y marca de enviado (`sincronizado`). Solo añade: los datos existentes no cambian
         * (sus turnos y ventas quedan como de la propia principal).
         */
        val MIGRACION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_4_5.forEach(db::execSQL)
            }
        }

        internal val SQL_4_5 = listOf(
            "ALTER TABLE `turno` ADD COLUMN `uuid` TEXT",
            "ALTER TABLE `turno` ADD COLUMN `empleadoId` INTEGER",
            "ALTER TABLE `turno` ADD COLUMN `sincronizado` INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE `venta` ADD COLUMN `uuid` TEXT",
            "ALTER TABLE `venta` ADD COLUMN `empleadoId` INTEGER",
            "ALTER TABLE `venta` ADD COLUMN `sincronizado` INTEGER NOT NULL DEFAULT 0",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_turno_uuid` ON `turno` (`uuid`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_venta_uuid` ON `venta` (`uuid`)",
            "CREATE TABLE IF NOT EXISTS `empleado` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nombre` TEXT NOT NULL, " +
                "`permisos` TEXT NOT NULL, `creadoEn` INTEGER NOT NULL, `vinculadoEn` INTEGER, `ultimaSincronizacion` INTEGER, " +
                "`clave` TEXT, `codigoToken` TEXT, `codigoVence` INTEGER, `activo` INTEGER NOT NULL)",
        )

        /**
         * v6 (0.20.0): en `empleado`, tarjeta y teléfono de cobro propios (H4) y la petición de cierre de turno del
         * dueño (H5). Solo añade columnas que admiten NULL: los empleados existentes cobran con los predeterminados.
         */
        val MIGRACION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_5_6.forEach(db::execSQL)
            }
        }

        internal val SQL_5_6 = listOf(
            "ALTER TABLE `empleado` ADD COLUMN `tarjetaId` INTEGER",
            "ALTER TABLE `empleado` ADD COLUMN `telefonoId` INTEGER",
            "ALTER TABLE `empleado` ADD COLUMN `cierreSolicitadoEn` INTEGER",
        )

        /**
         * v7 (0.21.0): en `empleado`, el teléfono que escribió el empleado al vincularse (C2) y su solicitud de cierre
         * de turno (C6; 0 = rechazada, pendiente de avisarle). En `movimiento`, quién hizo un ajuste desde una app
         * secundaria (C7; vacío = el del turno o el dueño). Solo columnas con NULL o con valor por defecto.
         */
        val MIGRACION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_6_7.forEach(db::execSQL)
            }
        }

        internal val SQL_6_7 = listOf(
            "ALTER TABLE `empleado` ADD COLUMN `telefono` TEXT",
            "ALTER TABLE `empleado` ADD COLUMN `cierrePedidoPorEmpleadoEn` INTEGER",
            "ALTER TABLE `movimiento` ADD COLUMN `hechoPor` TEXT NOT NULL DEFAULT ''",
        )

        /**
         * v8 (0.25.0): arqueo de caja (fondo, contado y totales de caja en `turno`; tabla `movimiento_caja`) y ventas
         * anuladas/modificadas en la principal (`venta`). Solo columnas con NULL o con valor por defecto y una tabla
         * nueva: los turnos anteriores quedan «sin arqueo» y las ventas anteriores, válidas.
         */
        val MIGRACION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_7_8.forEach(db::execSQL)
            }
        }

        internal val SQL_7_8 = listOf(
            "ALTER TABLE `turno` ADD COLUMN `fondoCent` INTEGER",
            "ALTER TABLE `turno` ADD COLUMN `contadoCent` INTEGER",
            "ALTER TABLE `turno` ADD COLUMN `entradasCent` INTEGER",
            "ALTER TABLE `turno` ADD COLUMN `salidasCent` INTEGER",
            "ALTER TABLE `turno` ADD COLUMN `numAnuladas` INTEGER",
            "ALTER TABLE `venta` ADD COLUMN `anuladaEn` INTEGER",
            "ALTER TABLE `venta` ADD COLUMN `motivoAnulacion` TEXT",
            "ALTER TABLE `venta` ADD COLUMN `anuladaPor` TEXT",
            "ALTER TABLE `venta` ADD COLUMN `corrigeVentaId` INTEGER",
            "ALTER TABLE `venta` ADD COLUMN `bajarCambio` INTEGER NOT NULL DEFAULT 0",
            "CREATE TABLE IF NOT EXISTS `movimiento_caja` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `turnoId` INTEGER NOT NULL, " +
                "`fecha` INTEGER NOT NULL, `tipo` TEXT NOT NULL, `importeCent` INTEGER NOT NULL, `motivo` TEXT NOT NULL, " +
                "`hechoPor` TEXT NOT NULL, `uuid` TEXT, `sincronizado` INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(`turnoId`) REFERENCES `turno`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE INDEX IF NOT EXISTS `index_movimiento_caja_turnoId` ON `movimiento_caja` (`turnoId`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_movimiento_caja_uuid` ON `movimiento_caja` (`uuid`)",
        )

        /**
         * v9 (0.26.0, P73 §4): fondo de caja asignado por el dueño a cada secundaria y su petición de apertura, más el
         * versionCode de la secundaria. Solo columnas que admiten NULL: las secundarias ya vinculadas quedan «sin fondo
         * asignado».
         */
        val MIGRACION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_8_9.forEach(db::execSQL)
            }
        }

        internal val SQL_8_9 = listOf(
            "ALTER TABLE `empleado` ADD COLUMN `fondoAsignadoCent` INTEGER",
            "ALTER TABLE `empleado` ADD COLUMN `fondoAsignadoEn` INTEGER",
            "ALTER TABLE `empleado` ADD COLUMN `aperturaSolicitadaEn` INTEGER",
            "ALTER TABLE `empleado` ADD COLUMN `versionCode` INTEGER",
        )

        /**
         * v10 (0.27.0, N2): clientes fijos (tabla `cliente_fijo`, carné único) y la marca «cliente fijo» de cada
         * transferencia (para que viaje en la sincronización). Solo añade: las transferencias anteriores quedan sin marca.
         */
        val MIGRACION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_9_10.forEach(db::execSQL)
            }
        }

        internal val SQL_9_10 = listOf(
            "ALTER TABLE `transaccion` ADD COLUMN `clienteFijo` INTEGER NOT NULL DEFAULT 0",
            "CREATE TABLE IF NOT EXISTS `cliente_fijo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nombreApellidos` TEXT NOT NULL, " +
                "`ci` TEXT NOT NULL, `telefono` TEXT NOT NULL, `creadoEn` INTEGER NOT NULL, `actualizadoEn` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_cliente_fijo_ci` ON `cliente_fijo` (`ci`)",
        )

        /**
         * v11 (0.30.0): sale el código de barras de producto. SQLite no permite borrar columnas, así que se recrea
         * la tabla `producto` sin `codigo` (ni su índice) copiando todas las demás columnas: no se pierde ningún dato.
         * El SQL replica EXACTAMENTE el que genera Room para la entidad (si no, la validación del esquema falla).
         */
        val MIGRACION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_10_11.forEach(db::execSQL)
            }
        }

        /**
         * v12 (0.31.0, mitigación 7): `empleado.device` = `deviceId` de la app secundaria vinculada. Solo añade una
         * columna a NULL: las secundarias anteriores quedan sin identificar hasta que vuelvan a saludar (y entonces la
         * principal les firma su sub-token). El esquema exportado (12.json) lo genera Room al compilar.
         */
        val MIGRACION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_11_12.forEach(db::execSQL)
            }
        }

        internal val SQL_11_12 = listOf(
            "ALTER TABLE `empleado` ADD COLUMN `device` TEXT",
        )

        internal val SQL_10_11 = listOf(
            "CREATE TABLE IF NOT EXISTS `producto_nuevo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoria` TEXT NOT NULL, " +
                "`nombre` TEXT NOT NULL, `descripcion` TEXT, `fotoUri` TEXT, `fechaCaducidad` INTEGER, " +
                "`precioCostoCent` INTEGER NOT NULL, `precioVentaCent` INTEGER NOT NULL, `cantidad` INTEGER NOT NULL, " +
                "`nivelBajo` INTEGER, `nivelCritico` INTEGER, `creadoEn` INTEGER NOT NULL, `actualizadoEn` INTEGER NOT NULL, " +
                "`eliminado` INTEGER NOT NULL)",
            "INSERT INTO `producto_nuevo` (`id`, `categoria`, `nombre`, `descripcion`, `fotoUri`, `fechaCaducidad`, " +
                "`precioCostoCent`, `precioVentaCent`, `cantidad`, `nivelBajo`, `nivelCritico`, `creadoEn`, `actualizadoEn`, " +
                "`eliminado`) SELECT `id`, `categoria`, `nombre`, `descripcion`, `fotoUri`, `fechaCaducidad`, `precioCostoCent`, " +
                "`precioVentaCent`, `cantidad`, `nivelBajo`, `nivelCritico`, `creadoEn`, `actualizadoEn`, `eliminado` FROM `producto`",
            "DROP TABLE `producto`",
            "ALTER TABLE `producto_nuevo` RENAME TO `producto`",
            "CREATE INDEX IF NOT EXISTS `index_producto_categoria` ON `producto` (`categoria`)",
            "CREATE INDEX IF NOT EXISTS `index_producto_eliminado_creadoEn` ON `producto` (`eliminado`, `creadoEn`)",
            "CREATE INDEX IF NOT EXISTS `index_producto_fechaCaducidad` ON `producto` (`fechaCaducidad`)",
        )

        /** Únicas versiones que se pueden recrear vacías: las de desarrollo anteriores a la 0.13.0 (sin clientes). */
        internal val DESARROLLO = intArrayOf(1, 2)

        /**
         * BD cifrada con SQLCipher. La passphrase (32 B aleatorios envueltos por el Keystore) la da [cu.shalev.data.security.DatabasePassphrase].
         * Solo v1/v2 (desarrollo) se recrean; cualquier otra migración que falte rompe en desarrollo.
         */
        fun crear(context: Context, passphrase: ByteArray): ShalevDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, ShalevDatabase::class.java, NOMBRE)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addMigrations(MIGRACION_3_4, MIGRACION_4_5, MIGRACION_5_6, MIGRACION_6_7, MIGRACION_7_8, MIGRACION_8_9, MIGRACION_9_10, MIGRACION_10_11, MIGRACION_11_12, MIGRACION_12_13, MIGRACION_13_14, MIGRACION_14_15, MIGRACION_15_16, MIGRACION_16_17, MIGRACION_17_18)
                .fallbackToDestructiveMigrationFrom(*DESARROLLO)
                .build()
        }
    }
}
