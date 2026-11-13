package cu.shalev.designsystem.component

import android.annotation.SuppressLint
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/** Qué se quiere comunicar con la vibración. */
enum class TipoHaptico { Exito, Error, Seleccion }

/**
 * P18 (A06, A15): vibración breve para confirmar sin mirar la pantalla, útil en un mostrador con ruido.
 *
 * - Usa `View.performHapticFeedback`: respeta el ajuste «Respuesta táctil» del sistema y **no necesita el permiso
 *   VIBRATE**, así que el manifiesto no cambia.
 * - Siempre acompaña a un mensaje visible (snackbar, diálogo); nunca es la única señal.
 */
@Stable
class ShalevHaptics internal constructor(private val view: View?) {
    fun exito() = vibrar(TipoHaptico.Exito)
    fun error() = vibrar(TipoHaptico.Error)
    fun seleccion() = vibrar(TipoHaptico.Seleccion)

    fun vibrar(tipo: TipoHaptico) {
        view?.performHapticFeedback(constante(tipo, Build.VERSION.SDK_INT))
    }

    companion object {
        /** Sin efecto (previsualizaciones y tests). */
        val Ninguno = ShalevHaptics(null)

        /**
         * Constante del sistema según el tipo y la versión. CONFIRM y REJECT existen desde Android 11 (API 30); antes
         * se usan equivalentes de API 26.
         */
        @SuppressLint("InlinedApi")
        fun constante(tipo: TipoHaptico, sdk: Int): Int = when (tipo) {
            TipoHaptico.Exito -> if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            TipoHaptico.Error -> if (sdk >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            TipoHaptico.Seleccion -> HapticFeedbackConstants.CLOCK_TICK
        }
    }
}

@Composable
fun rememberShalevHaptics(): ShalevHaptics {
    val view = LocalView.current
    return remember(view) { ShalevHaptics(view) }
}
