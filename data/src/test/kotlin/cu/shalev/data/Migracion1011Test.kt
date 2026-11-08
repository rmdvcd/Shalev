package cu.shalev.data

import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.dto.PreferenciasDto
import cu.shalev.data.dto.ProductoDto
import cu.shalev.data.dto.toDomain
import cu.shalev.data.local.ShalevJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Migración 10→11: `codigo` sale de producto (se recrea la tabla conservando los datos). */
class Migracion1011Test {

    @Test fun migracion10_11RecreaProductoSinCodigoConservandoDatos() {
        // Versión actual del esquema (v16: `local` en catálogo e historial) y cadena de migraciones sin huecos.
        assertEquals(16, ShalevDatabase.VERSION)
        assertEquals(10, ShalevDatabase.MIGRACION_10_11.startVersion)
        assertEquals(11, ShalevDatabase.MIGRACION_10_11.endVersion)
        assertEquals(11, ShalevDatabase.MIGRACION_11_12.startVersion)
        assertEquals(12, ShalevDatabase.MIGRACION_11_12.endVersion)
        assertEquals(15, ShalevDatabase.MIGRACION_15_16.startVersion)
        assertEquals(16, ShalevDatabase.MIGRACION_15_16.endVersion)
        val sql = ShalevDatabase.SQL_10_11
        assertTrue(sql.any { it.startsWith("CREATE TABLE IF NOT EXISTS `producto_nuevo`") && "codigo" !in it })
        assertTrue(sql.any { it.startsWith("INSERT INTO `producto_nuevo`") && "FROM `producto`" in it })
        assertTrue(sql.any { it == "DROP TABLE `producto`" })
        assertTrue(sql.any { it.startsWith("ALTER TABLE `producto_nuevo` RENAME TO `producto`") })
        assertTrue(sql.none { "codigo" in it })
        assertTrue(sql.none { it.startsWith("DELETE") })
        assertTrue(sql.none { it.startsWith("DROP") && it != "DROP TABLE `producto`" })
    }

    @Test fun migracion15_16SellaTodoEnElLocalActivo() {
        val sql = ShalevDatabase.SQL_15_16
        // Si no hay ningún local, se crea «Local» y queda activo.
        assertTrue(sql.any { it.startsWith("INSERT INTO almacen_local") && "'Local'" in it })
        listOf("producto", "insumo", "servicio", "turno", "venta", "transaccion", "movimiento").forEach { tabla ->
            assertTrue(sql.any { it == "ALTER TABLE `$tabla` ADD COLUMN `local` TEXT NOT NULL DEFAULT ''" })
            assertTrue(sql.any { it.startsWith("UPDATE `$tabla` SET `local` = COALESCE(") })
        }
        assertTrue(sql.none { it.startsWith("DELETE") || it.startsWith("DROP") })
    }

    @Test fun respaldoViejoConCodigoYConsentimientoSeImporta() {
        val viejo = """{"id":1,"categoria":"Pan","nombre":"Pan suave","precioCostoCent":50,"precioVentaCent":100,"cantidad":10,"codigo":"7501234","creadoEn":1000,"actualizadoEn":2000}"""
        val p = ShalevJson.decodeFromString(ProductoDto.serializer(), viejo).toDomain()
        assertEquals("Pan suave", p.nombre)
        val prefs = ShalevJson.decodeFromString(
            PreferenciasDto.serializer(), """{"productoBajo":7,"consultasEnLinea":"DENEGADO"}""",
        ).toDomain()
        assertEquals(7, prefs.niveles.productoBajo)
    }
}
