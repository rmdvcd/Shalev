package cu.shalev.data

import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.dto.AlmacenLocalDto
import cu.shalev.data.dto.RespaldoDto
import cu.shalev.data.local.ShalevJson
import org.junit.Assert.*
import org.junit.Test

class AlmacenLocalPersistenciaTest {
    @Test fun migracionSoloAnadeTabla() {
        assertEquals(16, ShalevDatabase.VERSION)
        assertEquals(14, ShalevDatabase.MIGRACION_14_15.startVersion)
        assertEquals(15, ShalevDatabase.MIGRACION_14_15.endVersion)
        assertEquals(1, ShalevDatabase.SQL_14_15.size)
        assertTrue(ShalevDatabase.SQL_14_15.single().startsWith("CREATE TABLE IF NOT EXISTS `almacen_local`"))
    }
    @Test fun respaldoConservaHistorialYSelecciones() {
        val dto = RespaldoDto(creadoEn = 0, almacenesLocales = listOf(AlmacenLocalDto("Almacén", true), AlmacenLocalDto("Tienda", servicios = true)))
        assertEquals(dto, ShalevJson.decodeFromString(RespaldoDto.serializer(), ShalevJson.encodeToString(RespaldoDto.serializer(), dto)))
    }
    @Test fun respaldoAnteriorNoTieneUbicaciones() {
        assertTrue(ShalevJson.decodeFromString(RespaldoDto.serializer(), """{"creadoEn":0}""").almacenesLocales.isEmpty())
    }
}
