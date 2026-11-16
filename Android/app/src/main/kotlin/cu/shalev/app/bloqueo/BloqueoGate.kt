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
import cu.shalev.app.respaldo.RespaldoScreen
import cu.shalev.app.soporte.SoporteScreen
import cu.shalev.app.vinculacion.VinculacionScreen
import cu.shalev.app.vinculacion.qr.QrVinculacionScreen
import cu.shalev.designsystem.token.ShalevMotion
import cu.shalev.domain.model.Vinculacion
import cu.shalev.licencia.LicenseState

/** Pantallas alcanzables durante el bloqueo. No hay NavHost: nada más es navegable. */
enum class PantallaBloqueo { LICENCIA, SOPORTE, SECUNDARIA, RESPALDO }

/** Atrás dentro de la puerta: SOPORTE → LICENCIA; en LICENCIA, atrás sale de la app (comportamiento del sistema). */
fun PantallaBloqueo.atras(): PantallaBloqueo? = when (this) {
    PantallaBloqueo.SOPORTE -> PantallaBloqueo.LICENCIA
    // 1.0.2: vincularse como secundaria y crear el respaldo previo se cancelan volviendo a la licencia.
    PantallaBloqueo.SECUNDARIA, PantallaBloqueo.RESPALDO -> PantallaBloqueo.LICENCIA
    PantallaBloqueo.LICENCIA -> null
}

/**
 * Bloqueo total (C9: solo por licencia). Al vencer la prueba o la licencia se abre DIRECTAMENTE el panel de
 * Licencia (Shalev.txt: "redirige al panel de licencia"), con aviso de bloqueo, solicitud y activación. Desde el
 * aviso se llega a Soporte y —desde 1.0.2, solo con la prueba vencida— a vincular este teléfono como app
 * secundaria ([Vinculacion.puedeSeguirComoSecundaria]): así el negocio sigue vendiendo con la licencia de su app
 * principal. El resto del bloqueo (licencia vencida, transferida o reloj manipulado) se resuelve con GL.
 * La única salida completa es activar una licencia válida o quedar vinculada: RootViewModel cambia de estado y
 * esta puerta desaparece sola.
 */
@Composable
fun BloqueoGate(estado: LicenseState) {
    val puedeSecundaria = Vinculacion.puedeSeguirComoSecundaria(estado)
    var pantalla by rememberSaveable { mutableStateOf(PantallaBloqueo.LICENCIA) }
    var escaneando by rememberSaveable { mutableStateOf(false) }
    var qrLeido by rememberSaveable { mutableStateOf<String?>(null) }
    if (pantalla == PantallaBloqueo.SECUNDARIA && escaneando) {
        BackHandler { escaneando = false }
    } else {
        pantalla.atras()?.let { destino -> BackHandler { pantalla = destino } }
    }
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
                onSecundaria = if (puedeSecundaria) ({ pantalla = PantallaBloqueo.SECUNDARIA }) else null,
            )
            PantallaBloqueo.SOPORTE -> SoporteScreen(onBack = { pantalla = PantallaBloqueo.LICENCIA })
            // La vinculación reutiliza la pantalla de Ajustes → Apps vinculadas: al entrar pregunta lo mismo
            // (teléfono, confirmación y respaldo previo) y el escáner devuelve el texto aquí, sin NavHost.
            PantallaBloqueo.SECUNDARIA -> if (escaneando) {
                QrVinculacionScreen(onBack = { escaneando = false }, onQrLeido = { c -> qrLeido = c; escaneando = false })
            } else {
                VinculacionScreen(
                    onBack = { pantalla = PantallaBloqueo.LICENCIA },
                    onEscanear = { escaneando = true },
                    qrLeido = qrLeido,
                    onQrConsumido = { qrLeido = null },
                    onIrARespaldo = { pantalla = PantallaBloqueo.RESPALDO },
                    abrirSecundariaEnBloqueo = true,
                )
            }
            PantallaBloqueo.RESPALDO -> RespaldoScreen(onBack = { pantalla = PantallaBloqueo.SECUNDARIA })
        }
    }
}
