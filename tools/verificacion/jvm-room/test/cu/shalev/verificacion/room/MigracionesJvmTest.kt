package cu.shalev.verificacion.room

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import cu.shalev.data.db.ShalevDatabase
import java.io.File
import java.sql.DriverManager
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOLO tools/verificacion (0.21.9, P59). Versión JVM de Android/data/src/androidTest/.../EsquemaTest (que en Gradle usa
 * MigrationTestHelper y SQLiteDatabase nativos): crea con JDBC una BD de la versión antigua a partir del esquema
 * exportado (<VERSION>.json) y la abre con Room y las migraciones reales. Room valida cada tabla, índice y clave foránea contra
 * las entidades al terminar la migración, y lanza IllegalStateException si algo no coincide.
 */
class MigracionesJvmTest {

    private val archivo: File = JdbcOpenHelper.archivo(BD)
    private val esquema: JSONObject by lazy {
        val dir = System.getProperty("shalev.esquemas") ?: error("falta -Dshalev.esquemas=<Android/data/schemas/cu.shalev.data.db.ShalevDatabase>")
        JSONObject(File(dir, "12.json").readText()).getJSONObject("database")
    }

    @After fun limpiar() { archivo.delete() }

    private fun crearAntigua(version: Int, sql: List<String>, vararg inserts: String) {
        archivo.delete()
        DriverManager.getConnection("jdbc:sqlite:" + archivo.absolutePath).use { c ->
            c.createStatement().use { st ->
                (sql + inserts).forEach { st.execute(it) }
                st.execute("PRAGMA user_version = $version")
            }
        }
    }

    private fun <T> abrirConRoom(bloque: (SupportSQLiteDatabase) -> T): T {
        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), ShalevDatabase::class.java, archivo.absolutePath)
            .addMigrations(ShalevDatabase.MIGRACION_3_4, ShalevDatabase.MIGRACION_4_5, ShalevDatabase.MIGRACION_5_6, ShalevDatabase.MIGRACION_6_7, ShalevDatabase.MIGRACION_7_8, ShalevDatabase.MIGRACION_8_9, ShalevDatabase.MIGRACION_9_10, ShalevDatabase.MIGRACION_10_11, ShalevDatabase.MIGRACION_11_12, ShalevDatabase.MIGRACION_12_13, ShalevDatabase.MIGRACION_13_14, ShalevDatabase.MIGRACION_14_15, ShalevDatabase.MIGRACION_15_16, ShalevDatabase.MIGRACION_16_17, ShalevDatabase.MIGRACION_17_18)
            .allowMainThreadQueries().build()
        try {
            return bloque(room.openHelper.writableDatabase) // abrir = migrar + validar
        } finally {
            room.close()
        }
    }

    private fun entidades(): List<JSONObject> = esquema.getJSONArray("entities").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private fun sinColumnas(sql: String, cols: List<String>) = cols.fold(sql) { s, c -> s.replace(Regex(",\\s*`$c` [^,)]*"), "") }
    private fun crear(e: JSONObject) = e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName"))
    private fun indices(e: JSONObject, saltar: (JSONObject) -> Boolean = { false }): List<String> {
        val ix = e.optJSONArray("indices") ?: return emptyList()
        return (0 until ix.length()).map { ix.getJSONObject(it) }.filterNot(saltar)
            .map { it.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")) }
    }

    private fun crearV12(e: JSONObject): String = if (e.getString("tableName") == "turno")
        sinColumnas(crear(e), listOf("conteoCierreJson")) else crear(e)

    @Test fun migracion12a13ConservaEfectivoYDejaConteoNulo() {
        crearAntigua(12, entidades().flatMap { listOf(crearV12(it)) + indices(it) },
            "INSERT INTO turno (abiertoEn, fondoCent, contadoCent) VALUES (1000, 10000, 12000)")
        abrirConRoom { db ->
            db.query("SELECT fondoCent, contadoCent, conteoCierreJson FROM turno").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(10000L, c.getLong(0)); assertEquals(12000L, c.getLong(1)); assertTrue(c.isNull(2))
            }
        }
    }

    /** v9 = v10 sin la tabla `cliente_fijo` (0.27.0) ni la columna `clienteFijo` de `transaccion` (y sin la `device` de la v12). */
    private fun entidadesV9(): List<JSONObject> = entidades().filter { it.getString("tableName") != "cliente_fijo" }
    private fun crearV9(e: JSONObject): String {
        var sql = crearV12(e)
        when (e.getString("tableName")) {
            "transaccion" -> sql = sinColumnas(sql, V10_TRANSACCION)
            "empleado" -> sql = sinColumnas(sql, V12_EMPLEADO) // v12 (0.31.0): solo la añade la última migración
        }
        return sql
    }

    /** v11 = el esquema actual sin la columna `device` que añade la v12 (0.31.0) en `empleado`. */
    private fun crearV11(e: JSONObject): String =
        if (e.getString("tableName") == "empleado") sinColumnas(crearV12(e), V12_EMPLEADO) else crearV12(e)

    private fun sqlV11(): List<String> = entidades().flatMap { e -> listOf(crearV11(e)) + indices(e) }
    private fun sqlV9(): List<String> = entidadesV9().flatMap { e -> listOf(crearV9(e)) + indices(e) }

    /** v7 = v8 sin la tabla `movimiento_caja` ni las columnas de la v8 en `turno` y `venta`. */
    private fun entidadesV7(): List<JSONObject> = entidadesV9().filter { it.getString("tableName") != "movimiento_caja" }
    private fun crearV7(e: JSONObject): String = when (e.getString("tableName")) {
        "turno" -> sinColumnas(crearV12(e), V8_TURNO)
        "venta" -> sinColumnas(crear(e), V8_VENTA)
        else -> crearV8(e)
    }

    /** v8 = v9 sin las columnas de la v9 (0.26.0) en `empleado`. */
    private fun crearV8(e: JSONObject): String =
        if (e.getString("tableName") == "empleado") sinColumnas(crearV9(e), V9_EMPLEADO) else crearV9(e)

    private fun sqlV8(): List<String> = entidadesV9().flatMap { e -> listOf(crearV8(e)) + indices(e) }

    private fun sqlV7(): List<String> = entidadesV7().flatMap { e -> listOf(crearV7(e)) + indices(e) }

    /** v5 = v7 sin lo que añaden la v6 y la v7. */
    private fun sqlV5(): List<String> = entidadesV7().flatMap { e ->
        var sql = crearV7(e)
        when (e.getString("tableName")) {
            "empleado" -> sql = sinColumnas(sql, V6_EMPLEADO + V7_EMPLEADO)
            "movimiento" -> sql = sinColumnas(sql, listOf("hechoPor"))
        }
        listOf(sql) + indices(e)
    }

    /** v4 = v5 sin `empleado` ni las columnas de sincronización (y sin sus índices). */
    private fun sqlV4(): List<String> = entidadesV7().filter { it.getString("tableName") != "empleado" }.flatMap { e ->
        var sql = crearV7(e)
        val t = e.getString("tableName")
        if (t == "movimiento") sql = sinColumnas(sql, listOf("hechoPor"))
        if (t == "turno" || t == "venta") sql = sinColumnas(sql, listOf("uuid", "empleadoId", "sincronizado"))
        listOf(sql) + indices(e) { it.getJSONArray("columnNames").toString().contains("uuid") }
    }

    @Test fun laBdCreadaDesdeElEsquemaSeAbreConLasEntidadesActuales() {
        val sql = entidades().flatMap { listOf(crear(it)) + indices(it) } + esquema.getJSONArray("setupQueries").let { a -> (0 until a.length()).map { a.getString(it) } }
        crearAntigua(ShalevDatabase.VERSION, sql,
            "INSERT INTO producto (categoria, nombre, precioCostoCent, precioVentaCent, cantidad, creadoEn, actualizadoEn, eliminado) VALUES ('Bebidas', 'Café', 6000, 10000, 3, 0, 0, 0)")
        val nombre = abrirConRoom { db -> db.query("SELECT nombre FROM producto").use { it.moveToFirst(); it.getString(0) } }
        assertEquals("Café", nombre)
    }

    @Test fun migracion4a7ConservaTurnosYVentasYDejaElEsquemaEsperado() {
        crearAntigua(4, sqlV4(),
            "INSERT INTO turno (abiertoEn, abiertoPor) VALUES (1000, 'Ana')",
            "INSERT INTO producto (categoria, nombre, precioCostoCent, precioVentaCent, cantidad, creadoEn, actualizadoEn, eliminado) VALUES ('Bebidas', 'Café', 6000, 10000, 3, 0, 0, 0)",
            "INSERT INTO movimiento (fecha, tipo, entidad, entidadId, nombre, delta, existencia) VALUES (1000, 'ALTA', 'PRODUCTO', 1, 'Café', 3, 3)")
        abrirConRoom { db ->
            db.query("SELECT abiertoPor, uuid, empleadoId, sincronizado FROM turno").use {
                assertTrue(it.moveToFirst())
                assertEquals("Ana", it.getString(0)); assertTrue(it.isNull(1)); assertTrue(it.isNull(2)); assertEquals(0, it.getInt(3))
            }
            db.query("SELECT hechoPor FROM movimiento").use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0) || it.getString(0) == "") }
            db.query("SELECT COUNT(*) FROM empleado").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.query("PRAGMA user_version").use { it.moveToFirst(); assertEquals(ShalevDatabase.VERSION, it.getInt(0)) }
        }
    }

    @Test fun migracion5a7ConservaElEmpleadoConLasColumnasNuevasANull() {
        crearAntigua(5, sqlV5(), "INSERT INTO empleado (nombre, permisos, creadoEn, activo) VALUES ('Luis', 'VENDER_PRODUCTOS', 0, 1)")
        abrirConRoom { db ->
            db.query("SELECT nombre, tarjetaId, telefonoId, cierreSolicitadoEn, telefono, cierrePedidoPorEmpleadoEn FROM empleado").use {
                assertTrue(it.moveToFirst())
                assertEquals("Luis", it.getString(0))
                (1..5).forEach { i -> assertTrue("columna $i", it.isNull(i)) }
            }
        }
    }

    @Test fun migracion6a7AnadeTelefonoSolicitudDeCierreYHechoPor() {
        crearAntigua(6, sqlV5() + ShalevDatabase.SQL_5_6, "INSERT INTO empleado (nombre, permisos, creadoEn, activo) VALUES ('Luis', 'VENDER_PRODUCTOS', 0, 1)")
        abrirConRoom { db ->
            db.query("SELECT telefono, cierrePedidoPorEmpleadoEn FROM empleado").use {
                assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)); assertTrue(it.isNull(1))
            }
        }
    }

    @Test fun migracion7a8DejaTurnosSinArqueoYVentasValidas() {
        crearAntigua(7, sqlV7(),
            "INSERT INTO turno (abiertoEn, cerradoEn, numVentas, unidades, totalCent, efectivoCent, transferenciaCent, costoCent, numMovimientos, abiertoPor, sincronizado) VALUES (1000, 2000, 1, 1, 10000, 10000, 0, 6000, 1, 'Ana', 0)",
            "INSERT INTO venta (turnoId, fecha, metodoPago, totalCent, costoCent, unidades, sincronizado) VALUES (1, 1500, 'EFECTIVO', 10000, 6000, 1, 0)")
        abrirConRoom { db ->
            db.query("SELECT fondoCent, contadoCent, entradasCent, salidasCent, numAnuladas FROM turno").use {
                assertTrue(it.moveToFirst()); (0..4).forEach { i -> assertTrue("columna $i", it.isNull(i)) }
            }
            db.query("SELECT anuladaEn, motivoAnulacion, anuladaPor, corrigeVentaId, bajarCambio FROM venta").use {
                assertTrue(it.moveToFirst()); (0..3).forEach { i -> assertTrue("columna $i", it.isNull(i)) }; assertEquals(0, it.getInt(4))
            }
            db.query("SELECT COUNT(*) FROM movimiento_caja").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.query("PRAGMA user_version").use { it.moveToFirst(); assertEquals(ShalevDatabase.VERSION, it.getInt(0)) }
        }
    }

    @Test fun migracion8a9DejaLasSecundariasSinFondoAsignado() {
        crearAntigua(8, sqlV8(), "INSERT INTO empleado (nombre, permisos, creadoEn, activo) VALUES ('Luis', 'VENDER_PRODUCTOS', 0, 1)")
        abrirConRoom { db ->
            db.query("SELECT nombre, fondoAsignadoCent, fondoAsignadoEn, aperturaSolicitadaEn, versionCode FROM empleado").use {
                assertTrue(it.moveToFirst()); assertEquals("Luis", it.getString(0))
                (1..4).forEach { i -> assertTrue("columna $i", it.isNull(i)) }
            }
            db.query("PRAGMA user_version").use { it.moveToFirst(); assertEquals(ShalevDatabase.VERSION, it.getInt(0)) }
        }
    }

    @Test fun migracion9a10ConservaLasTransferenciasYCreaClientesFijosVacia() {
        crearAntigua(9, sqlV9(),
            "INSERT INTO turno (abiertoEn, cerradoEn, numVentas, unidades, totalCent, efectivoCent, transferenciaCent, costoCent, numMovimientos, abiertoPor, sincronizado) VALUES (1000, 2000, 1, 1, 10000, 0, 10000, 6000, 1, 'Ana', 0)",
            "INSERT INTO venta (turnoId, fecha, metodoPago, totalCent, costoCent, unidades, sincronizado, bajarCambio) VALUES (1, 1500, 'TRANSFERENCIA', 10000, 6000, 1, 0, 0)",
            "INSERT INTO transaccion (ventaId, fecha, importeCent, numero, clienteNombre, clienteCi, clienteTelefono) VALUES (1, 1500, 10000, 'MM10040FEJ987', 'Ana Pérez', '85010112345', '51234567')")
        abrirConRoom { db ->
            db.query("SELECT clienteCi, clienteFijo FROM transaccion").use {
                assertTrue(it.moveToFirst()); assertEquals("85010112345", it.getString(0)); assertEquals(0, it.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM cliente_fijo").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.query("PRAGMA user_version").use { it.moveToFirst(); assertEquals(ShalevDatabase.VERSION, it.getInt(0)) }
        }
    }

    @Test fun migracion11a12AnadeElDeviceDeLaSecundaria() {
        crearAntigua(11, sqlV11(), "INSERT INTO empleado (nombre, permisos, creadoEn, activo) VALUES ('Luis', 'VENDER_PRODUCTOS', 0, 1)")
        abrirConRoom { db ->
            db.query("SELECT nombre, device, versionCode FROM empleado").use {
                assertTrue(it.moveToFirst()); assertEquals("Luis", it.getString(0))
                assertTrue(it.isNull(1)); assertTrue(it.isNull(2))
            }
            db.query("PRAGMA user_version").use { it.moveToFirst(); assertEquals(ShalevDatabase.VERSION, it.getInt(0)) }
        }
    }

    @Test(expected = IllegalStateException::class)
    fun unaBdQueNoCoincideConElEsquemaSeRechaza() {
        // Control negativo: si la validación de Room no se ejecutara en esta JVM, las demás pruebas no demostrarían nada.
        crearAntigua(6, sqlV5()) // dice ser v6 pero le faltan las columnas de la v6
        abrirConRoom { db -> db.query("SELECT 1").close() }
    }

    private companion object {
        const val BD = "spvi-migraciones-jvm.db"
        val V6_EMPLEADO = listOf("tarjetaId", "telefonoId", "cierreSolicitadoEn")
        val V7_EMPLEADO = listOf("telefono", "cierrePedidoPorEmpleadoEn")
        val V8_TURNO = listOf("fondoCent", "contadoCent", "entradasCent", "salidasCent", "numAnuladas")
        val V8_VENTA = listOf("anuladaEn", "motivoAnulacion", "anuladaPor", "corrigeVentaId", "bajarCambio")
        val V9_EMPLEADO = listOf("fondoAsignadoCent", "fondoAsignadoEn", "aperturaSolicitadaEn", "versionCode")
        val V10_TRANSACCION = listOf("clienteFijo")
        /** Columnas que añade la v12 (0.31.0, mitigación 7) en `empleado`. */
        val V12_EMPLEADO = listOf("device")
    }
}
