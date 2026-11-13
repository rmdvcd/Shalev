package cu.shalev.data.sync

import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Sin sockets: el estado se serializa entre instancias para simular la muerte del proceso. */
class BandejaComandosTest {
    private val inicial = DatosSecundaria("negocio", "Mi negocio", 3, "Ana", "clave-ficticia", emptyList(), 1234, localId = "local-a")
    private var persistido = SyncJson.encodeToString(DatosSecundaria.serializer(), inicial)
    private fun leer() = SyncJson.decodeFromString(DatosSecundaria.serializer(), persistido)
    private fun escribir(d: DatosSecundaria) { persistido = SyncJson.encodeToString(DatosSecundaria.serializer(), d) }
    private fun bandeja() = BandejaComandos({ leer() }, { esperado, pendiente, recuperada ->
        val actual = leer()
        if (!mismaVinculacion(actual, esperado) || actual.comandoPendiente != esperado.comandoPendiente) false
        else { escribir(actual.copy(comandoPendiente = pendiente, operacionRecuperada = recuperada)); true }
    })

    @Test fun respuestaPerdidaSeRecuperaTrasReinicioConLaMismaClaveSinReaplicar() = runBlocking {
        var aplicaciones = 0
        val recibos = mutableMapOf<String, ResultadoComando>()
        val claves = mutableListOf<String>()
        suspend fun servidor(d: DatosSecundaria, p: ComandoPendiente): ResultadoComando {
            assertEquals("local-a", d.localId)
            assertEquals(p, leer().comandoPendiente) // se persistió antes de entrar a la red
            claves += p.clave
            return recibos.getOrPut(p.clave) { aplicaciones++; ResultadoComando(99, 15) }
        }
        val accion = AjustarStockProducto(2, 5, "Compra")
        val primera = bandeja().ejecutar(accion) { d, p -> servidor(d, p); null }
        assertFalse(primera.confirmada)
        assertNotNull(leer().comandoPendiente)
        val trasReinicio = bandeja().recuperar(::servidor)!!
        assertTrue(trasReinicio.confirmada)
        assertEquals(AppResult.Ok(15L), trasReinicio.resultado)
        assertEquals(1, aplicaciones)
        assertEquals(1, claves.distinct().size)
        assertNull(leer().comandoPendiente)
        assertEquals(true, leer().operacionRecuperada)
    }

    @Test fun unaAccionDistintaNoSeEncolaDetrasDeUnaIncierta() = runBlocking {
        bandeja().ejecutar(AjustarStockProducto(1, 3, null)) { _, _ -> null }
        val vieja = leer().comandoPendiente!!
        var enviada: Accion? = null
        val segunda = bandeja().ejecutar(AjustarStockProducto(1, 8, null)) { _, p -> enviada = p.accion; ResultadoComando(1, 13) }
        assertEquals(vieja.accion, enviada)
        assertEquals(AppResult.Err(AppError.Validacion("comandoRecuperado")), segunda.resultado)
        assertNull(leer().comandoPendiente)
    }

    @Test fun noHayConexionConservaLaOperacionExactaHastaLaProximaSincronizacion() = runBlocking {
        bandeja().ejecutar(AjustarStockProducto(1, 3, "conteo")) { _, _ -> null }
        val anterior = persistido
        assertFalse(bandeja().recuperar { _, _ -> null }!!.confirmada)
        assertEquals(anterior, persistido)
    }

    @Test fun cancelacionNoDescartaLaClaveDeUnaOperacionEnviada() = runBlocking {
        try {
            bandeja().ejecutar(AjustarStockProducto(1, 3, null)) { _, _ -> throw CancellationException("simulada") }
            fail("Se debe propagar la cancelación")
        } catch (_: CancellationException) { }
        assertNotNull(leer().comandoPendiente)
    }

    @Test fun rechazoDefinitivoLiberaElPendienteYConservaUnAviso() = runBlocking {
        bandeja().ejecutar(AjustarStockProducto(1, 3, null)) { _, _ -> null }
        val resultado = bandeja().recuperar { _, _ -> ResultadoComando(1, error = ErroresRemotos.PERMISO) }!!
        assertEquals(AppResult.Err(AppError.SinPermiso), resultado.resultado)
        assertNull(leer().comandoPendiente)
        assertEquals(false, leer().operacionRecuperada)
    }

    @Test fun respuestaTardiaNoBorraUnaVinculacionNueva() = runBlocking {
        val nuevo = inicial.copy(negocioId = "otro", comandoPendiente = ComandoPendiente("nueva", AjustarStockProducto(9, 7, null), "local-a"))
        val resultado = bandeja().ejecutar(AjustarStockProducto(1, 3, null)) { _, _ -> escribir(nuevo); ResultadoComando(1, 13) }
        assertEquals(AppResult.Err(AppError.NoEncontrado), resultado.resultado)
        assertEquals(nuevo, leer())
    }

    @Test fun noHayNadaQueRecuperarNoEnvíaNingunMensaje() = runBlocking {
        assertNull(bandeja().recuperar { _, _ -> error("No debe enviar nada") })
    }
}
