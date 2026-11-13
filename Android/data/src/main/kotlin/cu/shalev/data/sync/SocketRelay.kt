package cu.shalev.data.sync

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

/** Adaptador de bytes: el protocolo y su cifrado no dependen de TCP o WebSocket. */
internal class SocketRelay private constructor() : Socket() {
    private val listo = CountDownLatch(1)
    private val cola = LinkedBlockingQueue<ByteArray>(256) // 16 MiB como máximo, sin crecimiento ilimitado.
    @Volatile private var cerrado = false
    @Volatile private var emparejado = false
    @Volatile private var espera = 60_000
    private var ws: WebSocket? = null
    private var bloque = byteArrayOf()
    private var posicion = 0
    private val entrada = object : InputStream() {
        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) == -1) -1 else b[0].toInt() and 255
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            require(off >= 0 && len >= 0 && off <= b.size - len)
            if (len == 0) return 0
            while (posicion == bloque.size) {
                if (cerrado && cola.isEmpty()) return -1
                bloque = try {
                    cola.poll(espera.toLong(), TimeUnit.MILLISECONDS) ?: throw SocketTimeoutException("Tiempo de espera agotado")
                } catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw IOException("Canal interrumpido", e) }
                posicion = 0
                if (bloque.isEmpty()) return -1
            }
            val n = minOf(len, bloque.size - posicion)
            bloque.copyInto(b, off, posicion, posicion + n)
            posicion += n
            return n
        }
    }
    private val salida = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()))
        override fun write(b: ByteArray, off: Int, len: Int) {
            require(off >= 0 && len >= 0 && off <= b.size - len)
            var p = off
            while (p < off + len) {
                val canal = ws ?: throw IOException("Canal no disponible")
                val limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                // Contrapresión: evita la cola de envío ilimitada durante fotos y APK.
                while (canal.queueSize() > 1024 * 1024 && !cerrado) {
                    if (System.nanoTime() >= limite) throw SocketTimeoutException("Canal ocupado")
                    try { Thread.sleep(10) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw IOException("Canal interrumpido", e) }
                }
                val n = minOf(CHUNK, off + len - p)
                if (cerrado || !emparejado || !canal.send(b.toByteString(p, n))) throw IOException("Canal cerrado")
                p += n
            }
        }
    }
    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text == "ready" && !emparejado) { emparejado = true; listo.countDown() } else close()
        }
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (!emparejado || bytes.size !in 1..CHUNK || !cola.offer(bytes.toByteArray())) close()
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { close() }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            finalizar()
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { finalizar() }
    }
    private fun finalizar() {
        cerrado = true
        listo.countDown()
        cola.offer(byteArrayOf()) // Despierta un lector; conserva los últimos bytes ya recibidos.
    }
    override fun close() {
        if (!cerrado) {
            finalizar()
            // close encola el cierre DESPUÉS de VincularOk/última respuesta; cancel perdería esos bytes.
            ws?.close(1000, null)
        }
    }
    override fun isClosed() = cerrado
    override fun getInputStream(): InputStream = entrada
    override fun getOutputStream(): OutputStream = salida
    override fun setSoTimeout(timeout: Int) { espera = if (timeout > 0) timeout else 60_000 }
    override fun getSoTimeout() = espera
    override fun setTcpNoDelay(on: Boolean) = Unit

    companion object {
        private const val CHUNK = 64 * 1024
        private val cliente = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(25, TimeUnit.SECONDS).build()

        fun abrir(url: String, esperaMs: Long): SocketRelay {
            val socket = SocketRelay()
            try {
                socket.ws = cliente.newWebSocket(Request.Builder().url(url).build(), socket.listener)
                if (!socket.listo.await(esperaMs, TimeUnit.MILLISECONDS) || socket.cerrado || !socket.emparejado)
                    throw IOException("Principal no disponible")
                return socket
            } catch (e: Exception) { socket.close(); socket.ws?.cancel(); throw e }
        }
    }
}
