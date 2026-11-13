package cu.shalev.data.sync

import cu.shalev.data.dto.EmpleadoRespaldoDto
import cu.shalev.data.dto.RespaldoDto
import cu.shalev.data.local.ShalevJson
import cu.shalev.data.sync.CierreRemoto.Accion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.20.0: cierre de turno pedido (H5), protocolo compatible y empleados en el respaldo sin claves (H7). */
class Version020SyncTest {

    @Test fun `sin venta en curso el cierre pedido se aplica al momento`() {
        assertEquals(Accion.CERRAR, CierreRemoto.decidir(pedido = true, ventaEnCurso = false, turnoAbierto = true))
    }

    @Test fun `P43 con una venta en curso el cierre espera`() {
        assertEquals(Accion.ESPERAR, CierreRemoto.decidir(pedido = true, ventaEnCurso = true, turnoAbierto = true))
        assertEquals(Accion.ESPERAR, CierreRemoto.decidir(pedido = true, ventaEnCurso = true, turnoAbierto = false))
    }

    @Test fun `sin peticion no se cierra nada y sin turno la peticion se olvida`() {
        assertEquals(Accion.ESPERAR, CierreRemoto.decidir(pedido = false, ventaEnCurso = false, turnoAbierto = true))
        assertEquals(Accion.OLVIDAR, CierreRemoto.decidir(pedido = true, ventaEnCurso = false, turnoAbierto = false))
    }

    @Test fun `P-b un cierre fallido se reintenta con espera creciente hasta que sale`() {
        var llamadas = 0
        val esperas = mutableListOf<Long>()
        kotlinx.coroutines.runBlocking {
            CierreRemoto.reintentar({ ++llamadas > 3 }) { esperas += it } // falla 3 veces, a la 4ª cierra
        }
        assertEquals(4, llamadas)
        assertEquals(listOf(5_000L, 10_000L, 20_000L), esperas)
        assertEquals(CierreRemoto.REINTENTO_MAX_MS, CierreRemoto.esperaReintento(6))
        assertEquals(CierreRemoto.REINTENTO_MAX_MS, CierreRemoto.esperaReintento(500)) // sin desbordar
        // Sin fallo no hay espera
        val sinEspera = mutableListOf<Long>()
        kotlinx.coroutines.runBlocking { CierreRemoto.reintentar({ true }) { sinEspera += it } }
        assertTrue(sinEspera.isEmpty())
    }

    @Test fun `la peticion de cierre viaja como campo opcional del protocolo`() {
        val ok = SincronizarOk(id = 1, recibidos = Recibidos(), hash = "h", cerrarTurno = true)
        val json = SyncJson.encodeToString(Mensaje.serializer(), ok)
        assertTrue(json.contains("cerrarTurno"))
        val leido = SyncJson.decodeFromString(Mensaje.serializer(), json) as SincronizarOk
        assertTrue(leido.cerrarTurno)
        // Una principal 0.19.x no lo manda: la secundaria 0.20.0 entiende «no hay petición».
        val viejo = SyncJson.decodeFromString(Mensaje.serializer(), json.replace(",\"cerrarTurno\":true", "")) as SincronizarOk
        assertFalse(viejo.cerrarTurno)
    }

    @Test fun `la peticion pendiente sobrevive en los datos guardados de la secundaria`() {
        val d = DatosSecundaria("n", "Negocio", 3, "Luis", "clave", listOf("192.168.43.1"), 47_811, cierrePendiente = true)
        val json = SyncJson.encodeToString(DatosSecundaria.serializer(), d)
        assertTrue(SyncJson.decodeFromString(DatosSecundaria.serializer(), json).cierrePendiente)
        // Datos guardados por la 0.19.x (sin los campos nuevos): sin petición ni aviso.
        val viejo = SyncJson.decodeFromString(DatosSecundaria.serializer(), json.replace(",\"cierrePendiente\":true", ""))
        assertFalse(viejo.cierrePendiente || viejo.avisoCierre)
    }

    @Test fun `el respaldo lleva los empleados sin claves ni codigos`() {
        val dto = RespaldoDto(creadoEn = 0, empleados = listOf(EmpleadoRespaldoDto(3, "Luis", listOf("VENDER_PRODUCTOS"), 0, tarjetaId = 2)))
        val json = ShalevJson.encodeToString(RespaldoDto.serializer(), dto)
        listOf("clave", "codigoToken", "codigoVence", "vinculadoEn").forEach { assertFalse(it, json.contains(it)) }
        val leido = ShalevJson.decodeFromString(RespaldoDto.serializer(), json)
        assertEquals("Luis", leido.empleados.single().nombre)
        assertEquals(2L, leido.empleados.single().tarjetaId)
        assertEquals(5, RespaldoDto.VERSION)
        assertEquals(3, RespaldoDto.VERSION_MINIMA)
        // Respaldo anterior a 0.20.0: sin empleados.
        assertTrue(ShalevJson.decodeFromString(RespaldoDto.serializer(), "{\"creadoEn\":0}").empleados.isEmpty())
    }
}
