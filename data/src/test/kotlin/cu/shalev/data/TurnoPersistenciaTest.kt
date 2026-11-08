package cu.shalev.data

import cu.shalev.core.money.Cup
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.TurnoEntity
import cu.shalev.data.dto.TurnoDto
import cu.shalev.data.dto.toDomain
import cu.shalev.data.dto.toDto
import cu.shalev.data.local.ShalevJson
import cu.shalev.data.mapper.toDomain
import cu.shalev.data.mapper.toEntity
import cu.shalev.domain.model.ResumenTurno
import cu.shalev.domain.model.Turno
import java.lang.reflect.Modifier
import java.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Turno de venta: migración 1→2, mapeo Room y respaldo (JVM, sin dispositivo). */
class TurnoPersistenciaTest {

    private val columnasV1 = setOf(
        "id", "abiertoEn", "cerradoEn", "numVentas", "unidades", "totalCent", "efectivoCent",
        "transferenciaCent", "costoCent", "numMovimientos",
    )

    private val cerrado = Turno(
        id = 3, abiertoEn = Instant.parse("2026-09-30T12:00:00Z"), cerradoEn = Instant.parse("2026-09-30T21:00:00Z"),
        resumen = ResumenTurno(4, 9, Cup.ofPesos(900), Cup.ofPesos(600), Cup.ofPesos(300), Cup.ofPesos(500), 7, 3, 1, 5, 2),
        abiertoPor = "Ana Pérez", cerradoPor = "Luis",
    )

    @Test fun esquemaV3ConsolidadoSoloRecreaLasVersionesDeDesarrollo() {
        // P29: v4 (servicios + precio de venta de insumos + clase en detalle_venta) con migración explícita 3→4.
        // P38: v5 (Principal/Secundaria: tabla empleado + uuid/empleadoId/sincronizado) con migración explícita 4→5.
        // 0.20.0: v6 (empleado: tarjetaId, telefonoId, cierreSolicitadoEn) con migración explícita 5→6.
        // 0.31.0 (mitigación 7): v12 (empleado: device) con migración 11→12.
        assertEquals(16, ShalevDatabase.VERSION)
        assertEquals(11, ShalevDatabase.MIGRACION_11_12.startVersion)
        assertEquals(12, ShalevDatabase.MIGRACION_11_12.endVersion)
        assertTrue(ShalevDatabase.SQL_11_12.all { it.startsWith("ALTER TABLE `empleado` ADD COLUMN") })
        // 0.27.0 (N2): v10 = clientes fijos (tabla nueva) y la marca en `transaccion`, con migración 9→10.
        assertEquals(9, ShalevDatabase.MIGRACION_9_10.startVersion)
        assertEquals(10, ShalevDatabase.MIGRACION_9_10.endVersion)
        assertTrue(ShalevDatabase.SQL_9_10.none { it.contains("DROP") || it.contains("DELETE") })
        // 0.26.0: v9 (empleado: fondo asignado, petición de apertura, versionCode) con migración 8→9.
        assertEquals(8, ShalevDatabase.MIGRACION_8_9.startVersion)
        assertEquals(9, ShalevDatabase.MIGRACION_8_9.endVersion)
        assertTrue(ShalevDatabase.SQL_8_9.all { it.startsWith("ALTER TABLE `empleado` ADD COLUMN") })
        // 0.25.0: v8 (caja, fondo/contado, anulación de ventas) con migración 7→8.
        assertEquals(7, ShalevDatabase.MIGRACION_7_8.startVersion)
        assertEquals(8, ShalevDatabase.MIGRACION_7_8.endVersion)
        // 0.21.0: v7 (empleado: telefono, cierrePedidoPorEmpleadoEn; movimiento: hechoPor) con migración 6→7.
        assertEquals(6, ShalevDatabase.MIGRACION_6_7.startVersion)
        assertEquals(7, ShalevDatabase.MIGRACION_6_7.endVersion)
        assertEquals(3, ShalevDatabase.SQL_6_7.size)
        assertTrue(ShalevDatabase.SQL_6_7.all { it.startsWith("ALTER TABLE `") && "ADD COLUMN" in it })
        assertEquals(5, ShalevDatabase.MIGRACION_5_6.startVersion)
        assertEquals(6, ShalevDatabase.MIGRACION_5_6.endVersion)
        assertEquals(3, ShalevDatabase.SQL_5_6.size)
        assertTrue(ShalevDatabase.SQL_5_6.all { it.startsWith("ALTER TABLE `empleado` ADD COLUMN") })
        assertEquals(4, ShalevDatabase.MIGRACION_4_5.startVersion)
        assertEquals(5, ShalevDatabase.MIGRACION_4_5.endVersion)
        assertTrue(ShalevDatabase.SQL_4_5.any { it.contains("`empleado`") })
        assertEquals(3, ShalevDatabase.MIGRACION_3_4.startVersion)
        assertEquals(4, ShalevDatabase.MIGRACION_3_4.endVersion)
        assertTrue(ShalevDatabase.SQL_3_4.any { it.contains("CREATE TABLE IF NOT EXISTS `servicio`") || it.contains("CREATE TABLE `servicio`") })
        // P17: solo las BD de desarrollo (v1/v2, sin clientes) se recrean; cualquier otra migración debe ser explícita.
        assertArrayEquals(intArrayOf(1, 2), ShalevDatabase.DESARROLLO)
        assertTrue(ShalevDatabase.VERSION !in ShalevDatabase.DESARROLLO)
        val campos = TurnoEntity::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()
        assertTrue(campos.containsAll(columnasV1))
    }

    @Test fun roomIdaYVueltaConservaUsuarioYDesglose() {
        assertEquals(cerrado, cerrado.toEntity().toDomain())
        val abierto = Turno(id = 4, abiertoEn = Instant.parse("2026-10-01T12:00:00Z"), abiertoPor = "Ana")
        assertEquals(abierto, abierto.toEntity().toDomain())
    }

    @Test fun turnoCerradoAntesDeV2SeLeeSinDesglose() {
        val viejo = TurnoEntity(1, 1_000, 2_000, 3, 5, 45_000, 45_000, 0, 24_000, 7).toDomain()
        assertEquals("", viejo.abiertoPor)
        assertNull(viejo.cerradoPor)
        val r = viejo.resumen!!
        assertEquals(7, r.numMovimientos)
        assertEquals(7, r.movimientosProducto) // sin desglose: todo cuenta como producto
        assertEquals(0, r.movimientosInsumo)
        assertEquals(Cup.ofPesos(210), r.ganancia)
    }

    @Test fun respaldoIdaYVueltaYRespaldosAntiguos() {
        val json = ShalevJson.encodeToString(TurnoDto.serializer(), cerrado.toDto())
        assertEquals(cerrado, ShalevJson.decodeFromString(TurnoDto.serializer(), json).toDomain())

        val antiguo = """{"id":1,"abiertoEn":1000,"cerradoEn":2000,"resumen":{"numVentas":2,"unidades":3,"totalCent":300,"efectivoCent":300,"transferenciaCent":0,"costoCent":100,"numMovimientos":2}}"""
        val t = ShalevJson.decodeFromString(TurnoDto.serializer(), antiguo).toDomain()
        assertEquals("", t.abiertoPor)
        assertEquals(2, t.resumen!!.movimientosProducto)
        assertEquals(0, t.resumen!!.ventasEfectivo)
    }
}
