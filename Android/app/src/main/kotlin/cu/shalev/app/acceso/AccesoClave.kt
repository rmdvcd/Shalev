package cu.shalev.app.acceso

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.app.common.SecureWindow
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.repository.AjustesDispositivoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 0.27.0 (T11) · Acceso con clave (opcional): la huella, la cara o el PIN/patrón del propio teléfono.
 *
 * Regla pura (probada en JVM): con el acceso activado, se pide al abrir la app desde cero y al volver tras
 * [ESPERA_MS] o más en segundo plano. Al volver antes (compartir un PDF, la cámara, WhatsApp) no se pide. El tiempo se
 * mide con el reloj de arranque del teléfono (`elapsedRealtime`), nunca con la hora de pared; si ese reloj retrocede
 * (el teléfono se reinició), se pide.
 */
object ReglaAcceso {
    const val ESPERA_MS = 10 * 60 * 1000L

    fun debePedir(activo: Boolean, arranqueEnFrio: Boolean, enFondoDesdeMs: Long?, ahoraMs: Long): Boolean = when {
        !activo -> false
        arranqueEnFrio -> true
        enFondoDesdeMs == null -> false
        ahoraMs < enFondoDesdeMs -> true
        else -> ahoraMs - enFondoDesdeMs >= ESPERA_MS
    }
}

/** Textos del acceso con clave (puros, en lenguaje llano). */
object TextosAcceso {
    const val TITULO = "Acceso con clave"
    const val DETALLE = "Pide tu huella, tu cara o el PIN del teléfono al abrir Shalev y al volver tras 10 minutos."
    const val SIN_BLOQUEO = "Configura un bloqueo de pantalla en Ajustes del teléfono para usar esta opción."
    const val BLOQUEADA = "Shalev está bloqueada"
    const val BLOQUEADA_DETALLE = "Desbloquéala con tu huella, tu cara o el PIN del teléfono."
    const val DESBLOQUEAR = "Desbloquear"
    const val PROMPT_TITULO = "Desbloquear Shalev"
    const val PROMPT_CONFIRMAR = "Confirma que eres tú"
    const val ACTIVADO = "Acceso con clave activado"
    const val DESACTIVADO = "Acceso con clave desactivado"
    const val NO_CONFIRMADO = "No se pudo confirmar. Inténtalo de nuevo."
}

/** Estado del candado en este proceso. */
enum class EstadoCandado { PEDIR, LIBRE }

/**
 * Guarda del proceso (una por app): sabe si la app arrancó en frío y cuándo pasó a segundo plano. La vuelta del
 * segundo plano la avisa `ProcessLifecycleOwner` (MainActivity la registra). No destruye nada: la pantalla de bloqueo
 * se dibuja ENCIMA de la app, así que una venta a medias sigue ahí al desbloquear.
 */
@Singleton
class GuardiaAcceso @Inject constructor() : DefaultLifecycleObserver {
    private val _estado = MutableStateFlow(EstadoCandado.PEDIR) // arranque en frío
    val estado: StateFlow<EstadoCandado> = _estado.asStateFlow()
    private var enFondoDesde: Long? = null

    fun alIrAFondo(ahoraMs: Long) { if (_estado.value == EstadoCandado.LIBRE) enFondoDesde = ahoraMs }

    fun alVolver(ahoraMs: Long) {
        val desde = enFondoDesde ?: return
        enFondoDesde = null
        if (ReglaAcceso.debePedir(activo = true, arranqueEnFrio = false, enFondoDesdeMs = desde, ahoraMs = ahoraMs)) _estado.value = EstadoCandado.PEDIR
    }

    fun desbloquear() { _estado.value = EstadoCandado.LIBRE }

    override fun onStop(owner: LifecycleOwner) = alIrAFondo(SystemClock.elapsedRealtime())
    override fun onStart(owner: LifecycleOwner) = alVolver(SystemClock.elapsedRealtime())
}

/** Pide la huella/cara o el PIN del teléfono con el diálogo del sistema. */
object Autenticador {
    /** ¿El teléfono tiene bloqueo de pantalla (PIN, patrón, contraseña)? Sin él, la opción aparece desactivada. */
    fun disponible(context: Context): Boolean =
        ContextCompat.getSystemService(context, KeyguardManager::class.java)?.isDeviceSecure == true

    /**
     * BIOMETRIC_WEAK | DEVICE_CREDENTIAL: la combinación que la librería admite en todas las versiones (26+); el PIN o
     * el patrón del teléfono siempre sirven de alternativa. Cancelar llama a [alFallar] (la pantalla de bloqueo sigue).
     */
    fun pedir(activity: FragmentActivity, titulo: String, alConfirmar: () -> Unit, alFallar: () -> Unit) {
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = alConfirmar()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = alFallar()
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { alFallar() }
    }
}

/** Guarda la preferencia tras confirmar la identidad y deja la app abierta (no se bloquea en ese mismo momento). */
class GuardarAccesoClave @Inject constructor(
    private val guardia: GuardiaAcceso,
    private val ajustes: AjustesDispositivoRepository,
) {
    suspend operator fun invoke(activo: Boolean) {
        guardia.desbloquear()
        ajustes.guardarAccesoConClave(activo)
    }
}

/**
 * Une la preferencia de este teléfono con el estado de la guarda. [bloqueada] = null mientras se lee la preferencia
 * (se dibuja solo el fondo, sin datos), true = pantalla de bloqueo, false = la app.
 */
@HiltViewModel
class AccesoViewModel @Inject constructor(
    private val guardia: GuardiaAcceso,
    private val ajustes: AjustesDispositivoRepository,
    private val guardarAcceso: GuardarAccesoClave,
) : ViewModel() {
    val activo: StateFlow<Boolean> = ajustes.ajustes.map { it.accesoConClave }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val bloqueada: StateFlow<Boolean?> = combine(ajustes.ajustes, guardia.estado) { a, e -> a.accesoConClave && e == EstadoCandado.PEDIR }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun desbloqueada() = guardia.desbloquear()

    /** El teléfono ya no tiene bloqueo de pantalla: la opción se apaga sola (nadie queda fuera de su app). */
    fun sinBloqueoDelTelefono() = viewModelScope.launch {
        ajustes.guardarAccesoConClave(false)
        guardia.desbloquear()
    }

    /** Ajustes/recorrido: activar o desactivar, SIEMPRE tras confirmar la identidad. */
    fun guardar(activo: Boolean) = viewModelScope.launch { guardarAcceso(activo) }
}

object AccesoTags {
    const val PANTALLA = "acceso.bloqueo"
    const val DESBLOQUEAR = "acceso.desbloquear"
    const val FILA = "acceso.fila"
}

/**
 * Pantalla de bloqueo: overlay sobre toda la app (como el bloqueo por actualización: Box + Surface, sin early return).
 * Sin datos de la app y con FLAG_SECURE. Atrás no la saltea. Al aparecer pide la clave una vez; cancelar la deja aquí.
 */
@Composable
fun PantallaBloqueoClave(onDesbloquear: () -> Unit, pedirAlEntrar: Boolean = true) {
    SecureWindow()
    BackHandler(enabled = true) { }
    LaunchedEffect(Unit) { if (pedirAlEntrar) onDesbloquear() }
    Surface(Modifier.fillMaxSize().testTag(AccesoTags.PANTALLA), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(ShalevSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(ShalevIcons.Bloqueo, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                TextosAcceso.BLOQUEADA, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            ShalevSecondaryText(TextosAcceso.BLOQUEADA_DETALLE, textAlign = TextAlign.Center)
            ShalevButtonRow {
                ShalevPrimaryButton(
                    TextosAcceso.DESBLOQUEAR, onClick = onDesbloquear, icon = ShalevIcons.Huella,
                    modifier = Modifier.testTag(AccesoTags.DESBLOQUEAR),
                )
            }
        }
    }
}

/**
 * Fila «Acceso con clave» (Ajustes y recorrido inicial). Sin bloqueo de pantalla en el teléfono aparece desactivada
 * con la explicación. Activar o desactivar pide antes la huella, la cara o el PIN del teléfono.
 */
@Composable
fun FilaAccesoClave(activo: Boolean, onCambiar: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val disponible = androidx.compose.runtime.remember { Autenticador.disponible(context) }
    cu.shalev.designsystem.component.ShalevListItem(
        title = TextosAcceso.TITULO,
        subtitle = if (disponible) TextosAcceso.DETALLE else TextosAcceso.SIN_BLOQUEO,
        subtitleMaxLines = 4,
        indicatorColor = null,
        leading = { Icon(ShalevIcons.Huella, contentDescription = null) },
        trailing = { androidx.compose.material3.Switch(checked = activo && disponible, onCheckedChange = null, enabled = disponible) },
        onClick = if (disponible) ({ onCambiar(!activo) }) else null,
        modifier = modifier.testTag(AccesoTags.FILA),
    )
}

/**
 * Devuelve la acción «cambiar el acceso con clave»: primero confirma la identidad con el diálogo del sistema y, si sale
 * bien, llama a [guardar]. [mensaje] informa del resultado.
 */
@Composable
fun rememberCambiarAccesoClave(guardar: (Boolean) -> Unit, mensaje: (String) -> Unit): (Boolean) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val guardarActual = androidx.compose.runtime.rememberUpdatedState(guardar)
    val mensajeActual = androidx.compose.runtime.rememberUpdatedState(mensaje)
    return androidx.compose.runtime.remember(context) {
        { nuevo: Boolean ->
            val activity = cu.shalev.app.common.actividadDe(context) as? FragmentActivity
            if (activity != null) {
                Autenticador.pedir(
                    activity, TextosAcceso.PROMPT_CONFIRMAR,
                    alConfirmar = {
                        guardarActual.value(nuevo)
                        mensajeActual.value(if (nuevo) TextosAcceso.ACTIVADO else TextosAcceso.DESACTIVADO)
                    },
                    alFallar = { mensajeActual.value(TextosAcceso.NO_CONFIRMADO) },
                )
            }
        }
    }
}
