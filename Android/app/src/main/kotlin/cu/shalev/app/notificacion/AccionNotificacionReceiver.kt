package cu.shalev.app.notificacion

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cu.shalev.app.actualizacion.GestorActualizacion
import cu.shalev.domain.repository.SecundariaRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Botones de las notificaciones que actúan sin abrir la app (Notificacion.md §2.5: un verbo por aviso):
 * «Cancelar» la descarga de la actualización y «Reintentar» una sincronización cortada. No exportado: solo lo
 * invocan los PendingIntent inmutables de Shalev.
 */
@AndroidEntryPoint
class AccionNotificacionReceiver : BroadcastReceiver() {
    @Inject lateinit var gestor: GestorActualizacion
    @Inject lateinit var secundaria: SecundariaRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CANCELAR_ACTUALIZACION -> gestor.cancelar()
            REINTENTAR_SYNC -> {
                // goAsync mantiene vivo el proceso mientras empieza; la sincronización sigue aunque tarde más de lo que
                // Android concede a un receptor (se suelta antes de ese límite para no provocar un ANR).
                val pendiente = goAsync()
                val trabajo = scope.launch { secundaria.sincronizarAhora() }
                scope.launch {
                    withTimeoutOrNull(MARGEN_MS) { trabajo.join() }
                    pendiente.finish()
                }
            }
        }
    }

    companion object {
        const val CANCELAR_ACTUALIZACION = "cu.shalev.app.notificacion.CANCELAR_ACTUALIZACION"
        const val REINTENTAR_SYNC = "cu.shalev.app.notificacion.REINTENTAR_SYNC"

        private const val MARGEN_MS = 8_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun intent(context: Context, accion: String): PendingIntent = PendingIntent.getBroadcast(
            context, accion.hashCode(),
            Intent(context, AccionNotificacionReceiver::class.java).setAction(accion),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
