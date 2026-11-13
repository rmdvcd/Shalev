package cu.shalev.app.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * 0.19.0: atajos a los ajustes del sistema para conectar las apps vinculadas. La app no puede encender la wifi ni la
 * zona wifi por sí misma (haría falta un permiso que Shalev no pide), así que abre la pantalla del sistema y el usuario
 * la activa. Sin permisos. Devuelve false si el teléfono no tiene esa pantalla.
 */
object AjustesRed {

    /** Pantalla de wifi (app secundaria: conectarse a la wifi del local o a la zona wifi del teléfono principal). */
    fun abrirWifi(context: Context): Boolean = abrir(context, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)

    /**
     * Pantalla de zona wifi (app principal). `TETHER_SETTINGS` no es pública en el SDK pero existe en casi todos los
     * teléfonos; si falta, se abre «Redes e Internet» y, en último caso, la wifi.
     */
    fun abrirZonaWifi(context: Context): Boolean =
        abrir(context, ACCION_ZONA_WIFI, Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_WIFI_SETTINGS)

    private fun abrir(context: Context, vararg acciones: String): Boolean = acciones.any { a ->
        try {
            context.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    private const val ACCION_ZONA_WIFI = "android.settings.TETHER_SETTINGS"
}
