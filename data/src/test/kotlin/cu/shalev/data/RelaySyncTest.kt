package cu.shalev.data

import cu.shalev.data.sync.RelaySync
import cu.shalev.data.sync.SocketRelay
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString.Companion.toByteString
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RelaySyncTest {
    @Test fun endpointSoloHttpsSinCredencialesRutaNiParametros() {
        assertEquals("https://relay.example", RelaySync.validarEndpoint("https://relay.example/"))
        listOf("", "http://relay.example", "https://user:pass@relay.example", "https://relay.example/x", "https://relay.example?q=x", "https://relay.example#x", "https://relay.example:8443").forEach {
            assertNull(RelaySync.validarEndpoint(it))
        }
    }
    @Test fun salasSeparadasPorSecretoNegocioEmpleadoYFinalidad() {
        val secret = ByteArray(32) { 7 }
        val room = RelaySync.sala("negocio", 1, secret, false)
        assertTrue(room.matches(Regex("[a-f0-9]{64}")))
        assertEquals(room, RelaySync.sala("negocio", 1, secret, false))
        assertNotEquals(room, RelaySync.sala("negocio", 2, secret, false))
        assertNotEquals(room, RelaySync.sala("otro", 1, secret, false))
        assertNotEquals(room, RelaySync.sala("negocio", 1, secret, true))
        assertNotEquals(room, RelaySync.sala("negocio", 1, ByteArray(32) { 8 }, false))
    }
    @Test fun recibeBytesSinDependerDeLosLimitesDeMensajesWebsocket() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("ready")
                    webSocket.send(byteArrayOf(1, 2).toByteString())
                    webSocket.send(byteArrayOf(3).toByteString())
                }
            }))
            SocketRelay.abrir(server.url("/").toString(), 5_000).use { socket ->
                val input = socket.getInputStream()
                assertEquals(1, input.read()); assertEquals(2, input.read()); assertEquals(3, input.read())
            }
        }
    }
    @Test fun cerrarDespiertaLectorBloqueado() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("ready") }
            }))
            val executor = Executors.newSingleThreadExecutor()
            try {
                SocketRelay.abrir(server.url("/").toString(), 5_000).use { socket ->
                    val lectura = executor.submit<Int> { socket.getInputStream().read() }
                    socket.close()
                    assertEquals(-1, lectura.get(2, TimeUnit.SECONDS).toInt())
                }
            } finally { executor.shutdownNow() }
        }
    }
    @Test fun cierreOrdenadoNoPierdeUltimaRespuesta() {
        MockWebServer().use { server ->
            val recibido = CountDownLatch(1)
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.send("ready") }
                override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                    if (bytes == byteArrayOf(7, 8).toByteString()) recibido.countDown()
                }
            }))
            SocketRelay.abrir(server.url("/").toString(), 5_000).use { socket ->
                socket.getOutputStream().write(byteArrayOf(7, 8))
            }
            assertTrue(recibido.await(5, TimeUnit.SECONDS))
        }
    }
    @Test(expected = IOException::class) fun sinReadyNoAbreCanal() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
            SocketRelay.abrir(server.url("/").toString(), 100).close()
        }
    }
}
