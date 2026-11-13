package cu.shalev.app.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Marca la ventana con FLAG_SECURE mientras la pantalla está en composición (Licencia, Perfil):
 * sin capturas ni vista previa en Recientes. Contador por referencias para pantallas solapadas.
 */
@Composable
fun SecureWindow() {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        SecureCounter.acquire(activity)
        onDispose { SecureCounter.release(activity) }
    }
}

/**
 * Marca con FLAG_SECURE la ventana de un diálogo: no hereda la de la Activity (datos del cliente en «Modificar
 * venta» y en la corrección de un cliente fijo).
 */
@Composable
fun SecureDialogWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { }
    }
}

private object SecureCounter {
    private var count = 0

    fun acquire(activity: Activity) {
        if (count++ == 0) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(activity: Activity) {
        if (count > 0 && --count == 0) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 0.27.0: la Activity que hay detrás de un Context de Compose (para el diálogo de huella/PIN). */
fun actividadDe(context: Context): Activity? = context.findActivity()
