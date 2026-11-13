package cu.shalev.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 0.27.0 (T5): mientras algún componente lo pide, el gesto de deslizar entre las secciones principales queda
 * desactivado: una ventana emergente abierta, el menú del «+» desplegado, un campo de texto con el foco (también el
 * buscador) o el modo selección. Cada componente lo registra con [BloquearGestos]; quien ofrece el gesto
 * (MainScaffold) provee [LocalBloqueoGestos] y consulta [activo].
 */
class BloqueoGestos {
    var cuenta by mutableIntStateOf(0)
        private set
    val activo: Boolean get() = cuenta > 0
    internal fun sumar() { cuenta++ }
    internal fun restar() { if (cuenta > 0) cuenta-- }
}

val LocalBloqueoGestos = staticCompositionLocalOf<BloqueoGestos?> { null }

/** Bloquea el deslizar entre secciones mientras [activo] sea true y el llamador siga en composición. */
@Composable
fun BloquearGestos(activo: Boolean = true) {
    val bloqueo = LocalBloqueoGestos.current ?: return
    DisposableEffect(bloqueo, activo) {
        if (activo) bloqueo.sumar()
        onDispose { if (activo) bloqueo.restar() }
    }
}
