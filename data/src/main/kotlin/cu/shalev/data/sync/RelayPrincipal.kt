package cu.shalev.data.sync

import cu.shalev.data.db.ShalevDatabase
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Un canal por empleado. Su destino y autenticación se derivan de los secretos ya existentes. */
internal class RelayPrincipal(private val db: ShalevDatabase, private val config: ConfigSync) {
    private class Enlace(val vincular: Boolean) {
        @Volatile var vigente = true
        @Volatile var atendiendo = false
        @Volatile var socket: Socket? = null
        var job: Job? = null
        fun cerrar() { vigente = false; socket?.close(); job?.cancel() }
    }
    private val enlaces = ConcurrentHashMap<String, Enlace>()

    suspend fun ejecutar(scope: CoroutineScope, atender: suspend (Socket) -> Unit) {
        if (!RelaySync.habilitado) return
        try {
            while (scope.isActive) {
                val negocio = config.negocioId()
                val deseados = db.syncDao().empleados().filter { it.activo }.take(16).mapNotNull { e ->
                    val vincular = e.clave == null
                    val secreto = if (vincular) e.codigoToken?.takeIf { (e.codigoVence ?: 0) > System.currentTimeMillis() } else e.clave
                    secreto?.let {
                        val bytes = CriptoSync.dec(it)
                        try { RelaySync.sala(negocio, e.id, bytes, vincular) to vincular } finally { bytes.fill(0) }
                    }
                }.toMap()
                enlaces.entries.toList().forEach { (sala, enlace) ->
                    if (sala !in deseados) {
                        enlace.vigente = false
                        // Al consumir un QR, el cambio de clave no debe cortar VincularOk antes de enviarlo.
                        if (!(enlace.vincular && enlace.atendiendo)) enlace.cerrar()
                    }
                    if (enlace.job?.isCompleted == true) enlaces.remove(sala, enlace)
                }
                for ((sala, vincular) in deseados) {
                    if (enlaces.containsKey(sala)) continue
                    val enlace = Enlace(vincular)
                    enlaces[sala] = enlace
                    enlace.job = scope.launch {
                        try {
                            var reintento = 1_000L
                            while (isActive && enlace.vigente) {
                                val socket = RelaySync.abrir(sala, principal = true)
                                enlace.socket = socket
                                if (!enlace.vigente) { socket?.close(); break }
                                if (socket != null) {
                                    enlace.atendiendo = true
                                    try { atender(socket) } finally {
                                        socket.close(); enlace.atendiendo = false; enlace.socket = null
                                    }
                                    reintento = 1_000L
                                }
                                delay(reintento + Random.nextLong(1_000))
                                reintento = (reintento * 2).coerceAtMost(60_000L)
                            }
                        } finally { enlace.socket?.close() }
                    }
                }
                delay(2_000)
            }
        } finally { detener() }
    }

    fun detener() { enlaces.values.forEach { it.cerrar() }; enlaces.clear() }
}
