package cu.shalev.app

import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.activity.SystemBarStyle
import android.os.Build
import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.clearAndSetSemantics
import cu.shalev.app.acceso.AccesoViewModel
import cu.shalev.app.acceso.Autenticador
import cu.shalev.app.acceso.GuardiaAcceso
import cu.shalev.app.acceso.PantallaBloqueoClave
import cu.shalev.app.acceso.TextosAcceso
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import cu.shalev.app.common.Entrada
import cu.shalev.app.common.EntradaCompartida
import cu.shalev.app.common.FormatoFechaDispositivo
import cu.shalev.app.root.RootState
import cu.shalev.app.root.RootViewModel
import cu.shalev.app.root.ShalevRoot
import cu.shalev.designsystem.theme.ShalevTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Única Activity. Edge-to-edge estándar (barras transparentes, iconos según tema): la app no oculta ni
 * modifica barra de estado/navegación ni notch; solo aplica insets.
 *
 * 0.27.0 (T11): FragmentActivity (lo pide BiometricPrompt; no hace falta AppCompat). Con el acceso con clave activado,
 * la pantalla de bloqueo se dibuja ENCIMA de la app sin destruir su estado.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val rootViewModel: RootViewModel by viewModels()
    private val accesoViewModel: AccesoViewModel by viewModels()
    @Inject lateinit var guardiaAcceso: GuardiaAcceso

    /** «Compartir con Shalev» (SMS de PAGOxMOVIL o licencia). launchMode=singleTask: llega a la venta en curso. */
    @Inject lateinit var entradaCompartida: EntradaCompartida

    override fun onCreate(savedInstanceState: Bundle?) {
        // La splash del sistema (#478EA1 + icono) se mantiene hasta evaluar la licencia.
        installSplashScreen().setKeepOnScreenCondition { rootViewModel.state.value is RootState.Loading }
        // P29 (barras de navegación): con 3 botones Android pinta un velo translúcido sobre la barra; la app ya dibuja
        // su propio fondo detrás (barra inferior, barra de acciones, Scaffold), así que el velo solo ensuciaba el
        // color. Barras transparentes en los 3 modos (gestos, 2 y 3 botones); los iconos se adaptan al tema.
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        FormatoFechaDispositivo.aplicar(this) // P24: fecha y hora con el formato del dispositivo
        ProcessLifecycleOwner.get().lifecycle.addObserver(guardiaAcceso) // idempotente al recrearse
        setContent {
            ShalevTheme {
                // P29: en horizontal, el contenido no queda bajo el notch/cámara (Scaffold solo reserva las barras).
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
                ) {
                    val bloqueada by accesoViewModel.bloqueada.collectAsStateWithLifecycle()
                    // Mientras se lee la preferencia (null) no se dibuja la app: así nunca asoma con el candado puesto.
                    Box(Modifier.fillMaxSize().then(if (bloqueada != false) Modifier.clearAndSetSemantics { } else Modifier)) {
                        if (bloqueada != null) ShalevRoot(rootViewModel)
                    }
                    if (bloqueada == true) PantallaBloqueoClave(onDesbloquear = ::pedirClave)
                }
            }
        }
        // Tras recrearse (giro) el Intent sería el mismo: solo se procesa en el primer arranque.
        if (savedInstanceState == null) recibir(intent)
    }

    /** 0.27.0 (T11): diálogo del sistema; sin bloqueo de pantalla en el teléfono, la opción se apaga sola. */
    private fun pedirClave() {
        if (!Autenticador.disponible(this)) { accesoViewModel.sinBloqueoDelTelefono(); return }
        Autenticador.pedir(this, TextosAcceso.PROMPT_TITULO, alConfirmar = accesoViewModel::desbloqueada, alFallar = {})
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        recibir(intent)
    }

    private fun recibir(intent: Intent?) {
        when (val e = EntradaCompartida.desdeIntent(intent)) {
            null -> Unit
            // Los .shalev solo entran por el gestor de archivos desde Respaldo: lo compartido desde
            // otras apps se ignora (queda la app abierta sin hacer nada con el archivo).
            is Entrada.Archivo -> Unit
            else -> entradaCompartida.publicar(e)
        }
    }

    override fun onResume() {
        super.onResume()
        FormatoFechaDispositivo.aplicar(this)
        // Re-evalúa al volver (p. ej. tras cambiar la fecha del sistema o expirar mientras estaba en segundo plano).
        rootViewModel.refrescar()
    }
}
