package cu.shalev.data.sync

import cu.shalev.data.dto.EmpleadoRespaldoDto
import cu.shalev.data.dto.RespaldoDto
import cu.shalev.data.local.ShalevJson
import cu.shalev.data.sync.CierreRemoto.Accion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun `la solicitud de importacion y sus bloques se serializan en el protocolo`() {
        val solicitud = "38ea980e-f4cc-4504-9e99-10f870f28b23"
        val peticion = Sincronizar(4, Lote(turnos = emptyList(), ventas = emptyList()), solicitudImportacionId = solicitud)
        val jsonPeticion = SyncJson.encodeToString(Mensaje.serializer(), peticion)
        val decodificada = SyncJson.decodeFromString(Mensaje.serializer(), jsonPeticion) as Sincronizar
        assertEquals(solicitud, decodificada.solicitudImportacionId)

        val respuesta = SincronizarOk(
            id = 4, recibidos = Recibidos(), hash = "h",
            solicitudImportacionId = solicitud, estadoSolicitudImportacion = "AUTORIZADA",
        )
        val jsonRespuesta = SyncJson.encodeToString(Mensaje.serializer(), respuesta)
        val respuestaLeida = SyncJson.decodeFromString(Mensaje.serializer(), jsonRespuesta) as SincronizarOk
        assertEquals(solicitud, respuestaLeida.solicitudImportacionId)
        assertEquals("AUTORIZADA", respuestaLeida.estadoSolicitudImportacion)

        val bloque = BloqueBaseNegocio(5, solicitud, 0, datos = "YWJj", total = 3, sha256 = "a".repeat(64))
        assertEquals(bloque, SyncJson.decodeFromString(Mensaje.serializer(), SyncJson.encodeToString(Mensaje.serializer(), bloque)))

        val acuse = ConfirmarBaseNegocio(6, solicitud)
        assertEquals(acuse, SyncJson.decodeFromString(Mensaje.serializer(), SyncJson.encodeToString(Mensaje.serializer(), acuse)))
        val confirmada = BaseNegocioConfirmada(6, confirmada = true)
        assertEquals(confirmada, SyncJson.decodeFromString(Mensaje.serializer(), SyncJson.encodeToString(Mensaje.serializer(), confirmada)))
        assertFalse(BaseNegocioConfirmada(7, confirmada = false).confirmada)
    }

    @Test fun `la peticion y el punto de recuperacion sobreviven en los datos guardados de la secundaria`() {
        val solicitud = "38ea980e-f4cc-4504-9e99-10f870f28b23"
        val d = DatosSecundaria(
            "n", "Negocio", 3, "Luis", "clave", listOf("192.168.43.1"), 47_811,
            cierrePendiente = true,
            solicitudImportacionId = solicitud,
            estadoSolicitudImportacion = "AUTORIZADA",
            baseImportadaLocalmente = true,
        )
        val json = SyncJson.encodeToString(DatosSecundaria.serializer(), d)
        val leido = SyncJson.decodeFromString(DatosSecundaria.serializer(), json)
        assertTrue(leido.cierrePendiente)
        assertEquals(solicitud, leido.solicitudImportacionId)
        assertEquals("AUTORIZADA", leido.estadoSolicitudImportacion)
        assertTrue(leido.baseImportadaLocalmente)
        // Datos guardados por una versión anterior, sin los campos nuevos: valores seguros por defecto.
        val viejoJson = json
            .replace(",\"cierrePendiente\":true", "")
            .replace(",\"solicitudImportacionId\":\"$solicitud\"", "")
            .replace(",\"estadoSolicitudImportacion\":\"AUTORIZADA\"", "")
            .replace(",\"baseImportadaLocalmente\":true", "")
        val viejo = SyncJson.decodeFromString(DatosSecundaria.serializer(), viejoJson)
        assertFalse(viejo.cierrePendiente || viejo.avisoCierre || viejo.baseImportadaLocalmente)
        assertNull(viejo.solicitudImportacionId)
        assertEquals("NINGUNA", viejo.estadoSolicitudImportacion)
    }

    @Test fun `el respaldo lleva los empleados sin claves ni codigos`() {
        val dto = RespaldoDto(creadoEn = 0, empleados = listOf(EmpleadoRespaldoDto(3, "Luis", listOf("VENDER_PRODUCTOS"), 0, tarjetaId = 2)))
        val json = ShalevJson.encodeToString(RespaldoDto.serializer(), dto)
        listOf("clave", "codigoToken", "codigoVence", "vinculadoEn", "solicitudImportacionId", "estadoSolicitudImportacion").forEach {
            assertFalse(it, json.contains(it))
        }
        val leido = ShalevJson.decodeFromString(RespaldoDto.serializer(), json)
        assertEquals("Luis", leido.empleados.single().nombre)
        assertEquals(2L, leido.empleados.single().tarjetaId)
        assertEquals(5, RespaldoDto.VERSION)
        assertEquals(3, RespaldoDto.VERSION_MINIMA)
        // Respaldo anterior a 0.20.0: sin empleados.
        assertTrue(ShalevJson.decodeFromString(RespaldoDto.serializer(), "{\"creadoEn\":0}").empleados.isEmpty())
    }
}
