package cu.shalev.app.venta

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Estado y acceso a la habilitación especial del NotificationListenerService. */
object AccesoCapturaSms {
    private const val CLAVE_LISTENERS_HABILITADOS = "enabled_notification_listeners"

    fun concedido(context: Context): Boolean {
        val componente = ComponentName(context, CapturaSmsPagoService::class.java)
        return Settings.Secure.getString(context.contentResolver, CLAVE_LISTENERS_HABILITADOS)
            .orEmpty()
            .split(':')
            .any { ComponentName.unflattenFromString(it) == componente }
    }

    /** Android requiere que el usuario active este permiso especial manualmente en Ajustes. */
    fun abrirAjustes(context: Context) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }
}
