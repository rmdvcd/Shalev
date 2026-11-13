package cu.shalev.app.bloqueo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cu.shalev.app.licencia.LicenciaScreen
import cu.shalev.app.soporte.SoporteScreen
import cu.shalev.designsystem.token.ShalevMotion

/** Pantallas alcanzables durante el bloqueo. No hay NavHost: nada más es navegable. */
enum class PantallaBloqueo { LICENCIA, SOPORTE }

/** Atrás dentro de la puerta: SOPORTE → LICENCIA; en LICENCIA, atrás sale de la app (comportamiento del sistema). */
fun PantallaBloqueo.atras(): PantallaBloqueo? = when (this) {
    PantallaBloqueo.SOPORTE -> PantallaBloqueo.LICENCIA
    PantallaBloqueo.LICENCIA -> null
}

/**
 * Bloqueo total (C9: solo por licencia). Al vencer la prueba o la licencia se abre DIRECTAMENTE el panel de
 * Licencia (Shalev.txt: "redirige al panel de licencia"), con aviso de bloqueo, solicitud y activación. Desde el
 * aviso se llega a Soporte. La única salida es activar una licencia válida: RootViewModel cambia de estado y
 * esta puerta desaparece sola.
 */
@Composable
fun BloqueoGate() {
    var pantalla by rememberSaveable { mutableStateOf(PantallaBloqueo.LICENCIA) }
    pantalla.atras()?.let { destino -> BackHandler { pantalla = destino } }
    AnimatedContent(
        targetState = pantalla,
        transitionSpec = { ShalevMotion.screenEnter togetherWith ShalevMotion.screenExit },
        label = "bloqueo",
    ) { p ->
        when (p) {
            PantallaBloqueo.LICENCIA -> LicenciaScreen(
                onBack = null,
                bloqueada = true,
                onSoporte = { pantalla = PantallaBloqueo.SOPORTE },
            )
            PantallaBloqueo.SOPORTE -> SoporteScreen(onBack = { pantalla = PantallaBloqueo.LICENCIA })
        }
    }
}
