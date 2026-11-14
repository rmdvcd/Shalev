package cu.shalev.app.recorrido

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.designsystem.component.CoachMarkHost
import cu.shalev.designsystem.component.CoachMarkPaso
import cu.shalev.designsystem.component.rememberCoachMarkState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Puente entre las pantallas y [RecorridosVistos]. No toca ninguna lógica de negocio. */
@HiltViewModel
class RecorridosViewModel @Inject constructor(private val vistos: RecorridosVistos) : ViewModel() {
    fun visto(recorrido: Recorrido): Flow<Boolean> = vistos.visto(recorrido)

    /** Se guarda en viewModelScope: termina aunque la pantalla salga justo después de «Finalizar». */
    fun completar(recorrido: Recorrido) {
        viewModelScope.launch { vistos.marcarVisto(recorrido) }
    }
}

/**
 * Envuelve el contenido de un módulo con su recorrido. Se muestra solo la primera vez: observa el indicador de
 * DataStore y, si aún no se vio y la pantalla está [listo] (datos cargados, sin diálogos ni hojas abiertos), lo
 * inicia. Se marca como visto solo con «Finalizar», «Omitir» o Atrás; si se cierra la app a mitad, vuelve a salir.
 */
@Composable
fun RecorridoModulo(
    recorrido: Recorrido,
    listo: Boolean,
    modifier: Modifier = Modifier,
    viewModel: RecorridosViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    // Hasta leer DataStore se asume «visto» para que el recorrido nunca parpadee al entrar.
    val visto by remember(recorrido) { viewModel.visto(recorrido) }.collectAsState(initial = true)
    RecorridoModulo(
        pasos = recorrido.pasos,
        visto = visto,
        listo = listo,
        onCompletado = { viewModel.completar(recorrido) },
        modifier = modifier,
        content = content,
    )
}

/** Espera tras entrar a la pantalla: deja terminar la transición y que los objetivos se coloquen. */
internal const val RETARDO_RECORRIDO_MS = 600L

/** Versión sin Hilt (pruebas): el indicador [visto] y su guardado llegan de fuera. */
@Composable
fun RecorridoModulo(
    pasos: List<CoachMarkPaso>,
    visto: Boolean,
    listo: Boolean,
    onCompletado: () -> Unit,
    modifier: Modifier = Modifier,
    retardoMs: Long = RETARDO_RECORRIDO_MS,
    content: @Composable () -> Unit,
) {
    val estado = rememberCoachMarkState(pasos)
    LaunchedEffect(visto, listo) {
        if (visto || !listo || estado.activo) return@LaunchedEffect
        delay(retardoMs)
        // Empieza en cuanto algún objetivo esté en pantalla (p. ej. la lista termina de cargar).
        snapshotFlow { estado.hayObjetivos() }.first { it }
        estado.iniciar()
    }
    val terminar = {
        estado.cerrar()
        onCompletado()
    }
    CoachMarkHost(
        estado = estado,
        onFinalizar = terminar,
        onOmitir = terminar,
        visible = listo,
        modifier = modifier,
        content = content,
    )
    // Atrás con el recorrido abierto lo omite (no saca de la pantalla). Va después del contenido para registrarse
    // después de sus propios BackHandler y tener prioridad sobre ellos.
    BackHandler(enabled = estado.activo && listo, onBack = terminar)
}
