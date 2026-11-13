package cu.shalev.app.root

import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.PreferenciasRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.usecase.VigilarLicencia
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import cu.shalev.domain.model.LicenciaPrincipal
import cu.shalev.domain.repository.TipoAppRepository
import cu.shalev.licencia.LicenseState
import java.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Puerta de la app. Orden: Loading → Bloqueo → Onboarding → Main. El bloqueo se evalúa ANTES del onboarding. */
sealed interface RootState {
    data object Loading : RootState
    /** Bloqueo total: siempre abre el panel de Licencia (al arrancar o si vence con la app abierta). */
    data class Bloqueo(val snapshot: Licencia) : RootState
    data object Onboarding : RootState
    data class Main(val snapshot: Licencia) : RootState
    /** P37: app secundaria y la licencia de la principal no está activa (no se activa aquí: se pide al dueño). */
    data class BloqueoSecundaria(val snapshot: Licencia) : RootState
}

/**
 * Transición pura (testeable en JVM).
 * P37: [licPrincipal] != null → esta app es SECUNDARIA: manda la licencia de la principal (la propia no cuenta) y no
 * hay onboarding (los datos y la configuración vienen de la principal).
 */
fun siguienteEstado(previo: RootState, lic: Licencia?, onboarded: Boolean, licPrincipal: LicenseState? = null): RootState = when {
    lic == null -> RootState.Loading
    licPrincipal != null -> if (licPrincipal.unlocked) RootState.Main(lic.copy(estado = licPrincipal)) else RootState.BloqueoSecundaria(lic.copy(estado = licPrincipal))
    !lic.estado.unlocked -> RootState.Bloqueo(lic)
    !onboarded -> RootState.Onboarding
    else -> RootState.Main(lic)
}

fun estadosRaiz(licencia: Flow<Licencia?>, onboarding: Flow<Boolean>, licPrincipal: Flow<LicenseState?> = flowOf(null)): Flow<RootState> =
    combine(licencia, onboarding, licPrincipal) { l, o, p -> Triple(l, o, p) }
        .runningFold(RootState.Loading as RootState) { prev, (l, o, p) -> siguienteEstado(prev, l, o, p) }
        .drop(1)

/** Licencia de la principal re-evaluada cada minuto (el vencimiento llega sin sincronizar). null = app principal. */
@OptIn(ExperimentalCoroutinesApi::class)
fun licenciaDePrincipal(f: Flow<LicenciaPrincipal?>, ahora: () -> Instant = Instant::now): Flow<LicenseState?> =
    f.flatMapLatest { lp ->
        if (lp == null) flowOf<LicenseState?>(null)
        else flow<LicenseState?> { while (true) { emit(lp.estado(ahora())); delay(60_000) } }
    }.distinctUntilChanged()

@HiltViewModel
class RootViewModel @Inject constructor(
    private val licenciaRepo: LicenciaRepository,
    private val preferenciasRepo: PreferenciasRepository,
    private val vigilarLicencia: VigilarLicencia,
    tipoRepo: TipoAppRepository,
) : ViewModel() {

    val state: StateFlow<RootState> = estadosRaiz(licenciaRepo.snapshot, preferenciasRepo.onboardingCompletado, licenciaDePrincipal(tipoRepo.licenciaPrincipal))
        .stateIn(viewModelScope, SharingStarted.Eagerly, RootState.Loading)

    init {
        // Re-evaluación periódica mientras viva el ViewModel: un vencimiento en uso bloquea sin reiniciar.
        viewModelScope.launch { vigilarLicencia().collect { } }
    }

    /** Llamado al volver a primer plano (el reloj pudo cambiar o pasar días con la app en segundo plano). */
    fun refrescar() {
        viewModelScope.launch { licenciaRepo.refrescar() }
    }
}
