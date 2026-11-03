package cu.shalev.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Protección contra la doble pulsación: un segundo toque en menos de [VENTANA_MS] sobre la MISMA acción se ignora.
 *
 * Antes, pulsar dos veces seguidas un botón (doble toque mecánico, o repetir porque la pantalla aún no había
 * reaccionado) ejecutaba la acción dos veces: dos navegaciones apiladas, dos exportaciones, dos envíos. Los botones
 * de Shalev envuelven su `onClick` con esta guardia; las acciones largas siguen protegidas además por el estado
 * «ocupado» de cada pantalla (defensa en profundidad). La lógica es pura (el reloj se inyecta) para probarla en JVM.
 */
class GuardiaDobleToque(
    private val ventanaMs: Long = VENTANA_MS,
    private val reloj: () -> Long = { System.currentTimeMillis() },
) {
    private var ultimoPermitido: Long? = null

    /**
     * true = se permite ejecutar la acción. Solo el primer toque de cada ventana pasa; un reloj que retrocede
     * (el usuario cambió la hora del teléfono) nunca bloquea: la diferencia negativa no cae en la ventana.
     */
    fun intentar(): Boolean {
        val ahora = reloj()
        val anterior = ultimoPermitido
        if (anterior != null && ahora - anterior in 0 until ventanaMs) return false
        ultimoPermitido = ahora
        return true
    }

    companion object {
        /** Ventana anti-rebote: cubre el doble toque mecánico sin frenar toques deliberados seguidos. */
        const val VENTANA_MS: Long = 600
    }
}

@Composable
fun rememberGuardiaDobleToque(): GuardiaDobleToque = remember { GuardiaDobleToque() }

/**
 * Envuelve [onClick] con la [GuardiaDobleToque] de esta composición. Devuelve siempre la misma lambda (estable):
 * lee la acción y el interruptor [activa] más recientes en cada toque.
 */
@Composable
fun rememberAccionUnica(onClick: () -> Unit, activa: Boolean = true): () -> Unit {
    val guardia = rememberGuardiaDobleToque()
    val accionActual by rememberUpdatedState(onClick)
    val activaActual by rememberUpdatedState(activa)
    return remember { { if (!activaActual || guardia.intentar()) accionActual() } }
}
