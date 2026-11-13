package cu.shalev.app.notificacion

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 0.19.2 (P40, opción A autorizada por el usuario) · Servicio en primer plano de tipo `connectedDevice` de la app
 * PRINCIPAL. No hace trabajo propio: el servidor de red local ya vive en `ServidorSync` (arrancado por ArranqueSync).
 * Este servicio solo evita que Android congele o cierre el proceso mientras hay secundarias que atender.
 *
 * - Lo arranca y lo para [AvisoTurno] según [PlanAviso]: principal con secundarias, y app a la vista o algún turno
 *   abierto. Su notificación es la misma del turno (ID 29): nunca hay dos.
 * - Permisos (normales, sin diálogo): FOREGROUND_SERVICE, FOREGROUND_SERVICE_CONNECTED_DEVICE y CHANGE_NETWORK_STATE
 *   (requisito de Android 14 para el tipo `connectedDevice`; Shalev no cambia ninguna red).
 * - START_NOT_STICKY y sin RECEIVE_BOOT_COMPLETED: si Android lo mata o el teléfono se reinicia, vuelve al abrir Shalev.
 * - Android 12+ no deja arrancarlo con la app en segundo plano: entonces no se arranca (se reintenta al volver).
 */
@AndroidEntryPoint
class ServicioSync : Service() {

    @Inject lateinit var aviso: AvisoTurno

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        try {
            ServiceCompat.startForeground(this, AvisoTurno.ID, aviso.notificacionServicio(), tipo)
        } catch (_: RuntimeException) {
            // ForegroundServiceStartNotAllowedException (app en segundo plano) o SecurityException: sin servicio.
            aviso.servicioTerminado()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!aviso.servicioNecesario()) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // DETACH: la notificación se queda y AvisoTurno decide si la actualiza (turno propio abierto) o la quita.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        aviso.servicioTerminado()
        super.onDestroy()
    }

    companion object {
        /** `true` si Android aceptó la petición (puede fallar después en onStartCommand). */
        fun arrancar(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, ServicioSync::class.java))
            true
        } catch (_: RuntimeException) {
            // IllegalStateException / ForegroundServiceStartNotAllowedException: app en segundo plano (Android 12+).
            false
        }

        fun parar(context: Context) {
            context.stopService(Intent(context, ServicioSync::class.java))
        }
    }
}
