package cu.shalev.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Reintenta una conexión fallida con backoff acotado y permite cancelar al desvincular. */
internal class ReintentoConexion(
    private val scope: CoroutineScope,
    private val esperaInicialMs: Long,
    private val esperaMaximaMs: Long,
    private val intentar: suspend () -> Boolean,
) {
    private var trabajo: Job? = null
    private var intentoActivo = false
    private var reintentarTrasIntento = false

    @Synchronized
    fun iniciar() {
        if (trabajo?.isActive == true) {
            // Una caída justo al abrir la sesión no debe perder la única señal de reintento.
            if (intentoActivo) reintentarTrasIntento = true
            return
        }
        require(esperaInicialMs > 0 && esperaMaximaMs >= esperaInicialMs)
        intentoActivo = false
        reintentarTrasIntento = false
        val nuevoTrabajo = scope.launch(start = CoroutineStart.LAZY) {
            var espera = esperaInicialMs
            while (isActive) {
                delay(espera)
                synchronized(this@ReintentoConexion) {
                    reintentarTrasIntento = false // Este intento ya cubrirá las solicitudes anteriores.
                    intentoActivo = true
                }
                val conectada = try {
                    intentar()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
                if (conectada) {
                    val terminar = synchronized(this@ReintentoConexion) {
                        intentoActivo = false
                        if (reintentarTrasIntento) {
                            reintentarTrasIntento = false
                            false
                        } else {
                            // Libera el puesto antes de terminar, para no perder una caída concurrente.
                            trabajo = null
                            true
                        }
                    }
                    if (terminar) return@launch
                    espera = esperaInicialMs
                } else {
                    synchronized(this@ReintentoConexion) {
                        intentoActivo = false
                        reintentarTrasIntento = false // El bucle ya reintentará con backoff.
                    }
                    espera = (espera * 2).coerceAtMost(esperaMaximaMs)
                }
            }
        }
        trabajo = nuevoTrabajo
        nuevoTrabajo.start()
    }

    @Synchronized
    fun detener() {
        intentoActivo = false
        reintentarTrasIntento = false
        trabajo?.cancel()
        trabajo = null
    }
}
