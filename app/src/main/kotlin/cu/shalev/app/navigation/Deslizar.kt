package cu.shalev.app.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * P29 · Deslizar entre secciones de la barra inferior.
 *
 * - Solo en los 4 destinos principales (no en formularios, Ajustes, venta ni selección para vender).
 * - Los gestos que un hijo ya consume (listas horizontales, pestañas desplazables, sliders) no cuentan.
 * - Se ignoran los toques que empiezan en las franjas de gestos del sistema (atrás por gesto en los bordes),
 *   para no chocar con la navegación del lanzador (gestos o 3 botones, cualquiera que sea el fabricante).
 * - 0.28.0: sin intercepción por pestañas: en Registros el gesto pasa a la sección vecina como en las demás.
 *
 * [dir] = +1 → sección siguiente (dedo hacia la izquierda), −1 → anterior.
 */
fun Modifier.deslizarEntreSecciones(activo: Boolean, onDeslizar: (dir: Int) -> Unit): Modifier = composed {
    if (!activo) return@composed this
    val density = LocalDensity.current
    val ld = LocalLayoutDirection.current
    val bordeIzq = WindowInsets.systemGestures.getLeft(density, ld).toFloat()
    val bordeDer = WindowInsets.systemGestures.getRight(density, ld).toFloat()
    val umbral = with(density) { UMBRAL.toPx() }
    val callback by rememberUpdatedState(onDeslizar)
    pointerInput(bordeIzq, bordeDer, umbral) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val x = down.position.x
            if (x < bordeIzq || x > size.width - bordeDer) return@awaitEachGesture
            var total = 0f
            val arrastre = awaitHorizontalTouchSlopOrCancellation(down.id) { change, sobra ->
                total += sobra
                change.consume()
            } ?: return@awaitEachGesture
            val completo = horizontalDrag(arrastre.id) { change ->
                total += change.positionChange().x
                change.consume()
            }
            if (completo && abs(total) >= umbral) callback(if (total < 0) 1 else -1)
        }
    }
}

private val UMBRAL = 72.dp

/**
 * 0.27.0 (T5): el gesto solo actúa si el destino actual es EXACTAMENTE una de las 4 rutas principales (Inicio,
 * Inventario, Servicios, Registros), no está en «elegir qué vender» ni en Ajustes, y ningún componente lo bloquea (ventana emergente,
 * menú del «+», campo con foco, modo selección).
 */
fun deslizarPermitido(enRutaRaiz: Boolean, enSeleccionVenta: Boolean, bloqueado: Boolean): Boolean =
    enRutaRaiz && !enSeleccionVenta && !bloqueado

/**
 * 0.31.0 (P10): cómo se anima el cambio de sección. El deslizamiento y el toque en la barra inferior se animan
 * distinto: el gesto tiene dirección (la ventana de destino entra desde el lado hacia donde se movió el dedo) y el
 * toque es un fundido.
 */
enum class CambioSeccion { FUNDIDO, ADELANTE, ATRAS }

/**
 * 0.31.0 (P10): decisión pura (probada en JVM). [dir] = dirección del deslizamiento que provocó el cambio
 * (+1 = sección siguiente, −1 = anterior, 0 = no fue un deslizamiento, sino un toque en la barra inferior).
 */
fun cambioSeccion(dir: Int): CambioSeccion = when (dir) {
    1 -> CambioSeccion.ADELANTE
    -1 -> CambioSeccion.ATRAS
    else -> CambioSeccion.FUNDIDO
}

/** Un gesto solo afecta a la navegación que lo originó, no a cambios posteriores de pestaña. */
data class GestoSeccion(val origenId: String, val destino: String, val direccion: Int)

fun cambioSeccion(gesto: GestoSeccion?, origenId: String, destino: String?): CambioSeccion =
    if (gesto != null && gesto.origenId == origenId && gesto.destino == destino) cambioSeccion(gesto.direccion)
    else CambioSeccion.FUNDIDO
