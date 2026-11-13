package cu.shalev.app.root

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.bloqueo.BloqueoGate
import cu.shalev.app.navigation.MainScaffold
import cu.shalev.app.onboarding.ModoWizard
import cu.shalev.app.onboarding.OnboardingScreen
import cu.shalev.designsystem.component.ShalevLoading
import cu.shalev.designsystem.token.ShalevMotion

/**
 * Bloqueo y Onboarding son "puertas", no destinos del NavHost: así ningún back/deep link puede
 * saltárselas. Main contiene el NavHost con los módulos navegables.
 */
@Composable
fun ShalevRoot(viewModel: RootViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refrescar()
        onPauseOrDispose { }
    }
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { ShalevMotion.screenEnter togetherWith ShalevMotion.screenExit },
        label = "root",
    ) { s ->
        when (s) {
            RootState.Loading -> ShalevLoading()
            // 0.25.0 (§3.4): teléfono antiguo de una recuperación: datos borrados, pedir desinstalar.
            is RootState.Bloqueo -> if (s.snapshot.estado == cu.shalev.licencia.LicenseState.Revoked) cu.shalev.app.licencia.LicenciaTransferida() else BloqueoGate()
            // El asistente marca el onboarding como visto; RootViewModel pasa a Main solo.
            RootState.Onboarding -> OnboardingScreen(modo = ModoWizard.PRIMERA_VEZ, onTerminar = {})
            is RootState.Main -> MainScaffold(s.snapshot)
            is RootState.BloqueoSecundaria -> cu.shalev.app.vinculacion.BloqueoSecundaria()
        }
    }
    // Primera apertura: UNA ventana con el checklist de permisos (cámara, aviso, fotos).
    // Si se niegan, cada función los vuelve a pedir donde hacen falta.
    PermisosIniciales()
}
