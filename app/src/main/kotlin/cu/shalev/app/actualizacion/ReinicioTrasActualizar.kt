package cu.shalev.app.actualizacion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cu.shalev.app.MainActivity

/**
 * 1.0.4: marca que la próxima instalación la confirmó el usuario desde el cuadro de Actualización, para reabrir
 * Shalev al terminar (en vez de hacerlo tras cualquier actualización, por ejemplo al instalar por ADB).
 */
object ReinicioPendiente {
    private const val ARCHIVO = "actualizacion"
    private const val CLAVE = "reiniciar_tras_actualizar"

    fun armar(context: Context) {
        context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit().putBoolean(CLAVE, true).commit()
    }

    fun consumir(context: Context): Boolean {
        val prefs = context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
        val pendiente = prefs.getBoolean(CLAVE, false)
        if (pendiente) prefs.edit().putBoolean(CLAVE, false).commit()
        return pendiente
    }

    /** La instalación no llegó a hacerse: no se reabre nada en el próximo arranque. */
    fun desarmar(context: Context) {
        context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit().putBoolean(CLAVE, false).commit()
    }
}

/** Reabre Shalev en la versión nueva cuando la instalación la pidió la propia app. */
class ReinicioTrasActualizar : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!ReinicioPendiente.consumir(context)) return
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
