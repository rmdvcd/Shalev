package cu.shalev.data

import cu.shalev.core.money.Cup
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.dto.*
import cu.shalev.data.local.ShalevJson
import cu.shalev.data.mapper.toEntity
import cu.shalev.data.mapper.toDomain
import cu.shalev.domain.model.*
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class ConteoCierrePersistenciaTest {
    private val conteo = ConteoCierre(mapOf(20000 to 2L, 5000 to 1L), Cup(75),
        listOf(LineaConteo(TipoEntidad.INSUMO, 1, "Harina", "kg", 2000, 1500, 1000)), "Merma", "Ana", 2000)
    private val turno = Turno(id = 1, abiertoEn = Instant.EPOCH, contado = conteo.efectivo, conteoCierre = conteo)

    @Test fun conservaConteoEnRoomJsonYRespaldo() {
        assertEquals(conteo, conteo.aJson().conteoDeJson())
        assertEquals(turno, turno.toEntity().toDomain())
        val json = ShalevJson.encodeToString(turno.toDto())
        assertEquals(turno, ShalevJson.decodeFromString<TurnoDto>(json).toDomain())
    }
    @Test fun unTurnoAnteriorNoInventaConteo() {
        val t = ShalevJson.decodeFromString<TurnoDto>("""{"id":1,"abiertoEn":0,"contadoCent":100}""").toDomain()
        assertNull(t.conteoCierre)
        assertEquals(Cup(100), t.contado)
    }
    @Test fun cierreRemotoEsperaLaAprobacionDelInventarioAunqueYaHayaEfectivo() {
        val pendiente = turno.copy(conteoCierre = conteo.copy(aprobadoPor = null, aprobadoEn = null), fondo = Cup.ZERO)
        assertFalse(cu.shalev.data.sync.CierreRemoto.conteoListo(pendiente))
        assertTrue(cu.shalev.data.sync.CierreRemoto.conteoListo(pendiente.copy(conteoCierre = conteo)))
        assertTrue(cu.shalev.data.sync.CierreRemoto.conteoListo(pendiente.copy(conteoCierre = null)))
    }
    @Test fun migracionSoloAnadeColumnaOpcionalSinBorrarDatos() {
        assertEquals(12, ShalevDatabase.MIGRACION_12_13.startVersion)
        assertEquals(13, ShalevDatabase.MIGRACION_12_13.endVersion)
        assertEquals(listOf("ALTER TABLE `turno` ADD COLUMN `conteoCierreJson` TEXT"), ShalevDatabase.SQL_12_13)
    }
}
