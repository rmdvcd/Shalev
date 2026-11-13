package cu.shalev.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cu.shalev.data.db.ShalevDatabase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El esquema exportado por Room (`data/schemas/cu.shalev.data.db.ShalevDatabase/<VERSION>.json`, versionado) es la base de
 * las migraciones futuras.
 *
 * - Se crea una BD **desde el JSON** y se abre con las entidades actuales. Room compara el hash de identidad y falla
 *   si el JSON no corresponde a las entidades.
 * - **4→5:** se crea una BD v4 (el esquema v5 sin lo que añade la v5) con un turno y una venta, se abre con Room y
 *   se comprueba que `MIGRACION_4_5` deja exactamente el esquema esperado (Room valida cada tabla) y conserva los datos.
 * - **5→6 (0.20.0):** BD v5 (el esquema v6 sin las 3 columnas nuevas de `empleado`) con un empleado; `MIGRACION_5_6`
 *   las añade a NULL y conserva la fila.
 * - Si alguien cambia una entidad sin subir [ShalevDatabase.VERSION], KSP reescribe el JSON de esa versión: el cambio
 *   aparece en el diff y debe convertirse en una versión nueva con su `Migration(anterior, nueva)`.
 * - **11→12 (0.31.0):** BD v11 (el esquema v12 sin la columna `device` de `empleado`) con un empleado; `MIGRACION_11_12`
 *   la añade a NULL y conserva la fila.
 * - Solo las versiones de desarrollo (1 y 2, sin clientes) se pueden recrear vacías.
 */
@RunWith(AndroidJUnit4::class)
class EsquemaTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ShalevDatabase::class.java)

    private val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After fun limpiar() { contexto.deleteDatabase(BD) }

    @Test
    fun laBdCreadaDesdeElEsquemaSeAbreConLasEntidadesActuales() {
        helper.createDatabase(BD, ShalevDatabase.VERSION).apply {
            execSQL("INSERT INTO producto (categoria, nombre, precioCostoCent, precioVentaCent, cantidad, creadoEn, actualizadoEn, eliminado) VALUES ('Bebidas', 'Café', 6000, 10000, 3, 0, 0, 0)")
            close()
        }
        val db = Room.databaseBuilder(contexto, ShalevDatabase::class.java, BD).allowMainThreadQueries().build()
        try {
            val c = db.openHelper.readableDatabase.query("SELECT nombre FROM producto")
            c.use { assertEquals(true, it.moveToFirst()); assertEquals("Café", it.getString(0)) }
        } finally {
            db.close()
        }
    }

    @Test
    fun migracion16a17CreaLaTablaDeRecibosConElEsquemaActual() {
        helper.createDatabase(BD, 16).close()
        val db = Room.databaseBuilder(contexto, ShalevDatabase::class.java, BD)
            .addMigrations(ShalevDatabase.MIGRACION_16_17, ShalevDatabase.MIGRACION_17_18)
            .allowMainThreadQueries().build()
        try {
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM comando_recibo").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migracion17a18ConservaElNegocioYFijaIdentidadDelLocalDelEmpleado() {
        helper.createDatabase(BD, 17).apply {
            execSQL("INSERT INTO almacen_local(nombre,inventario,servicios) VALUES ('Tienda',1,1)")
            execSQL("INSERT INTO producto(categoria,nombre,precioCostoCent,precioVentaCent,cantidad,creadoEn,actualizadoEn,eliminado,local) VALUES ('Bebidas','Refresco',125,250,7,0,0,0,'Tienda')")
            execSQL("INSERT INTO empleado(id,nombre,permisos,creadoEn,activo) VALUES (3,'Ana','EDITAR_INVENTARIO',0,1)")
            execSQL("INSERT INTO turno(abiertoEn,empleadoId,local) VALUES (0,3,'Tienda')")
            close()
        }
        helper.runMigrationsAndValidate(BD, 18, true, ShalevDatabase.MIGRACION_17_18).use { db ->
            db.query("SELECT cantidad,precioCostoCent,costoAdicionalCent FROM producto").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(7L,c.getLong(0)); assertEquals(125L,c.getLong(1)); assertEquals(0L,c.getLong(2))
            }
            db.query("SELECT e.localId = l.id, length(l.id) FROM empleado e JOIN almacen_local l ON l.nombre = 'Tienda'").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(1,c.getInt(0)); assertEquals(32,c.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM ficha_costo").use { c -> assertTrue(c.moveToFirst()); assertEquals(0,c.getInt(0)) }
        }
    }

    @Test
    fun migracion12a13ConservaCajaYAnadeConteoNulo() {
        helper.createDatabase(BD, 12).apply {
            execSQL("INSERT INTO turno (abiertoEn, abiertoPor, fondoCent, contadoCent) VALUES (1000, 'Ana', 10000, 12000)")
            close()
        }
        helper.runMigrationsAndValidate(BD, 13, true, ShalevDatabase.MIGRACION_12_13).use { db ->
            db.query("SELECT fondoCent, contadoCent, conteoCierreJson FROM turno").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(10000L, c.getLong(0))
                assertEquals(12000L, c.getLong(1))
                assertTrue(c.isNull(2))
            }
        }
    }

    @Test
    fun soloLasVersionesDeDesarrolloSeRecreanVacias() {
        assertArrayEquals(intArrayOf(1, 2), ShalevDatabase.DESARROLLO)
        assertFalse(ShalevDatabase.VERSION in ShalevDatabase.DESARROLLO)
    }

    @Test
    fun migracion4a5ConservaLosDatosYDejaElEsquemaEsperado() {
        val esquema = JSONObject(
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("cu.shalev.data.db.ShalevDatabase/12.json").bufferedReader().use { it.readText() },
        ).getJSONObject("database")
        val f = contexto.getDatabasePath(BD).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
            sqlV4(esquema).forEach(db::execSQL)
            db.execSQL(insertMinimo(db, "turno"))
            db.version = 4
        }
        val room = Room.databaseBuilder(contexto, ShalevDatabase::class.java, BD)
            .addMigrations(ShalevDatabase.MIGRACION_3_4, ShalevDatabase.MIGRACION_4_5, ShalevDatabase.MIGRACION_5_6, ShalevDatabase.MIGRACION_6_7, ShalevDatabase.MIGRACION_7_8, ShalevDatabase.MIGRACION_8_9, ShalevDatabase.MIGRACION_9_10, ShalevDatabase.MIGRACION_10_11, ShalevDatabase.MIGRACION_11_12, ShalevDatabase.MIGRACION_12_13, ShalevDatabase.MIGRACION_13_14, ShalevDatabase.MIGRACION_14_15, ShalevDatabase.MIGRACION_15_16, ShalevDatabase.MIGRACION_16_17, ShalevDatabase.MIGRACION_17_18)
            .allowMainThreadQueries().build()
        try {
            // Abrir dispara la migración y la validación de Room (lanza si alguna tabla no coincide con las entidades).
            room.openHelper.readableDatabase.query("SELECT uuid, empleadoId, sincronizado FROM turno").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.isNull(0)); assertTrue(it.isNull(1)); assertEquals(0, it.getInt(2))
            }
            room.openHelper.readableDatabase.query("SELECT COUNT(*) FROM empleado").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        } finally {
            room.close()
        }
    }

    @Test
    fun migracion5a6AnadeElCobroYElCierreDelEmpleado() {
        val esquema = JSONObject(
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("cu.shalev.data.db.ShalevDatabase/12.json").bufferedReader().use { it.readText() },
        ).getJSONObject("database")
        val f = contexto.getDatabasePath(BD).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
            sqlV5(esquema).forEach(db::execSQL)
            db.execSQL("INSERT INTO empleado (nombre, permisos, creadoEn, activo) VALUES ('Luis', 'VENDER_PRODUCTOS', 0, 1)")
            db.version = 5
        }
        val room = Room.databaseBuilder(contexto, ShalevDatabase::class.java, BD)
            .addMigrations(ShalevDatabase.MIGRACION_3_4, ShalevDatabase.MIGRACION_4_5, ShalevDatabase.MIGRACION_5_6, ShalevDatabase.MIGRACION_6_7, ShalevDatabase.MIGRACION_7_8, ShalevDatabase.MIGRACION_8_9, ShalevDatabase.MIGRACION_9_10, ShalevDatabase.MIGRACION_10_11, ShalevDatabase.MIGRACION_11_12, ShalevDatabase.MIGRACION_12_13, ShalevDatabase.MIGRACION_13_14, ShalevDatabase.MIGRACION_14_15, ShalevDatabase.MIGRACION_15_16, ShalevDatabase.MIGRACION_16_17, ShalevDatabase.MIGRACION_17_18)
            .allowMainThreadQueries().build()
        try {
            room.openHelper.readableDatabase.query("SELECT nombre, tarjetaId, telefonoId, cierreSolicitadoEn FROM empleado").use {
                assertTrue(it.moveToFirst())
                assertEquals("Luis", it.getString(0))
                assertTrue(it.isNull(1)); assertTrue(it.isNull(2)); assertTrue(it.isNull(3))
            }
        } finally {
            room.close()
        }
    }

    /** Esquema v5 = el exportado sin las columnas que añade la v6 en `empleado`. */
    private fun sqlV5(esquema: JSONObject): List<String> {
        val nuevas = listOf("tarjetaId", "telefonoId", "cierreSolicitadoEn")
        val out = mutableListOf<String>()
        val entidades = esquema.getJSONArray("entities")
        for (i in 0 until entidades.length()) {
            val e = entidades.getJSONObject(i)
            val tabla = e.getString("tableName")
            if (tabla == "movimiento_caja" || tabla == "cliente_fijo") continue // v8 y v10
            var sql = sinV8(tabla, e.getString("createSql").replace("\${TABLE_NAME}", tabla))
            if (tabla == "empleado") (nuevas + V7_EMPLEADO + V9_EMPLEADO + V12_EMPLEADO).forEach { c -> sql = sql.replace(Regex(",\\s*`$c` [^,)]*"), "") }
            if (tabla == "movimiento") sql = sql.replace(Regex(",\\s*`hechoPor` [^,)]*"), "")
            out += sql
            val indices = e.optJSONArray("indices") ?: continue
            for (k in 0 until indices.length()) out += indices.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}", tabla)
        }
        return out
    }

    /** Esquema v4 = el exportado sin la tabla `empleado`, sin `uuid`/`empleadoId`/`sincronizado` y sin sus índices. */
    private fun sqlV4(esquema: JSONObject): List<String> {
        val nuevas = listOf("uuid", "empleadoId", "sincronizado")
        val out = mutableListOf<String>()
        val entidades = esquema.getJSONArray("entities")
        for (i in 0 until entidades.length()) {
            val e = entidades.getJSONObject(i)
            val tabla = e.getString("tableName")
            if (tabla == "empleado" || tabla == "movimiento_caja" || tabla == "cliente_fijo") continue // v5, v8 y v10
            var sql = sinV8(tabla, e.getString("createSql").replace("\${TABLE_NAME}", tabla))
            if (tabla == "movimiento") sql = sql.replace(Regex(",\\s*`hechoPor` [^,)]*"), "")
            if (tabla == "turno" || tabla == "venta") {
                nuevas.forEach { c -> sql = sql.replace(Regex(",\\s*`$c` [^,)]*"), "") }
            }
            out += sql
            val indices = e.optJSONArray("indices") ?: continue
            for (k in 0 until indices.length()) {
                val ix = indices.getJSONObject(k)
                if (ix.getJSONArray("columnNames").toString().contains("uuid")) continue
                out += ix.getString("createSql").replace("\${TABLE_NAME}", tabla)
            }
        }
        return out
    }

    /**
     * Quita las columnas que añaden las versiones posteriores a las que se recrean aquí: la v8 (0.25.0) en `turno` y
     * `venta`, la v9/v12 en `empleado` y la v10 (0.27.0) en `transaccion`. Sin esto, el `ALTER TABLE` de la migración
     * correspondiente vuelve a añadir una columna que ya venía del `12.json` y falla con «duplicate column name».
     */
    private fun sinV8(tabla: String, sql: String): String = when (tabla) {
        "turno" -> V8_TURNO
        "venta" -> V8_VENTA
        "empleado" -> V9_EMPLEADO + V12_EMPLEADO
        "transaccion" -> V10_TRANSACCION
        else -> emptyList()
    }.fold(sql) { s, c -> s.replace(Regex(",\\s*`$c` [^,)]*"), "") }

    /** INSERT con valores neutros para todas las columnas NOT NULL sin DEFAULT de [tabla] (no depende del esquema exacto). */
    private fun insertMinimo(db: SQLiteDatabase, tabla: String): String {
        val cols = mutableListOf<String>(); val vals = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info(`$tabla`)", null).use { c ->
            while (c.moveToNext()) {
                val nombre = c.getString(1); val tipo = c.getString(2); val notNull = c.getInt(3) == 1
                val pk = c.getInt(5) > 0; val porDefecto = !c.isNull(4)
                if (pk || !notNull || porDefecto) continue
                cols += "`$nombre`"; vals += if (tipo.equals("TEXT", ignoreCase = true)) "'x'" else "0"
            }
        }
        return "INSERT INTO `$tabla` (${cols.joinToString()}) VALUES (${vals.joinToString()})"
    }

    @Test
    fun migracion6a7AnadeTelefonoSolicitudDeCierreYHechoPor() {
        val esquema = JSONObject(
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("cu.shalev.data.db.ShalevDatabase/12.json").bufferedReader().use { it.readText() },
        ).getJSONObject("database")
        val f = contexto.getDatabasePath(BD).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
            sqlV5(esquema).forEach(db::execSQL)
            ShalevDatabase.SQL_5_6.forEach(db::execSQL)
            db.execSQL("INSERT INTO empleado (nombre, permisos, creadoEn, activo) VALUES ('Luis', 'VENDER_PRODUCTOS', 0, 1)")
            db.version = 6
        }
        val room = Room.databaseBuilder(contexto, ShalevDatabase::class.java, BD)
            .addMigrations(ShalevDatabase.MIGRACION_5_6, ShalevDatabase.MIGRACION_6_7, ShalevDatabase.MIGRACION_7_8, ShalevDatabase.MIGRACION_8_9, ShalevDatabase.MIGRACION_9_10, ShalevDatabase.MIGRACION_10_11, ShalevDatabase.MIGRACION_11_12, ShalevDatabase.MIGRACION_12_13, ShalevDatabase.MIGRACION_13_14, ShalevDatabase.MIGRACION_14_15, ShalevDatabase.MIGRACION_15_16, ShalevDatabase.MIGRACION_16_17, ShalevDatabase.MIGRACION_17_18)
            .allowMainThreadQueries().build()
        try {
            room.openHelper.readableDatabase.query("SELECT telefono, cierrePedidoPorEmpleadoEn FROM empleado").use {
                assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)); assertTrue(it.isNull(1))
            }
        } finally {
            room.close()
        }
    }

    @Test
    fun migracion11a12AnadeElDeviceDeLaSecundaria() {
        val esquema = JSONObject(
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("cu.shalev.data.db.ShalevDatabase/12.json").bufferedReader().use { it.readText() },
        ).getJSONObject("database")
        val f = contexto.getDatabasePath(BD).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
            sqlV11(esquema).forEach(db::execSQL)
            db.execSQL("INSERT INTO empleado (nombre, permisos, creadoEn, activo) VALUES ('Luis', 'VENDER_PRODUCTOS', 0, 1)")
            db.version = 11
        }
        val room = Room.databaseBuilder(contexto, ShalevDatabase::class.java, BD)
            .addMigrations(ShalevDatabase.MIGRACION_11_12, ShalevDatabase.MIGRACION_12_13, ShalevDatabase.MIGRACION_13_14, ShalevDatabase.MIGRACION_14_15, ShalevDatabase.MIGRACION_15_16, ShalevDatabase.MIGRACION_16_17, ShalevDatabase.MIGRACION_17_18)
            .allowMainThreadQueries().build()
        try {
            room.openHelper.readableDatabase.query("SELECT nombre, device FROM empleado").use {
                assertTrue(it.moveToFirst())
                assertEquals("Luis", it.getString(0))
                assertTrue(it.isNull(1))
            }
        } finally {
            room.close()
        }
    }

    /** Esquema v11 = el exportado sin la columna `device` que añade la v12 en `empleado` (todo lo demás, idéntico). */
    private fun sqlV11(esquema: JSONObject): List<String> {
        val out = mutableListOf<String>()
        val entidades = esquema.getJSONArray("entities")
        for (i in 0 until entidades.length()) {
            val e = entidades.getJSONObject(i)
            val tabla = e.getString("tableName")
            var sql = e.getString("createSql").replace("\${TABLE_NAME}", tabla)
            if (tabla == "empleado") V12_EMPLEADO.forEach { c -> sql = sql.replace(Regex(",\\s*`$c` [^,)]*"), "") }
            out += sql
            val indices = e.optJSONArray("indices") ?: continue
            for (k in 0 until indices.length()) out += indices.getJSONObject(k).getString("createSql").replace("\${TABLE_NAME}", tabla)
        }
        return out
    }

    private companion object {
        const val BD = "esquema-test.db"
        /** Columnas que añade la v7 (0.21.0) en `empleado`. */
        val V7_EMPLEADO = listOf("telefono", "cierrePedidoPorEmpleadoEn")
        val V8_TURNO = listOf("fondoCent", "contadoCent", "entradasCent", "salidasCent", "numAnuladas")
        val V8_VENTA = listOf("anuladaEn", "motivoAnulacion", "anuladaPor", "corrigeVentaId", "bajarCambio")
        /** Columna que añade la v10 (0.27.0) en `transaccion`. */
        val V10_TRANSACCION = listOf("clienteFijo")
        /** Columnas que añade la v9 (0.26.0) en `empleado`. */
        val V9_EMPLEADO = listOf("fondoAsignadoCent", "fondoAsignadoEn", "aperturaSolicitadaEn", "versionCode")
        /** Columnas que añade la v12 (0.31.0, mitigación 7) en `empleado`. */
        val V12_EMPLEADO = listOf("device")
    }
}
