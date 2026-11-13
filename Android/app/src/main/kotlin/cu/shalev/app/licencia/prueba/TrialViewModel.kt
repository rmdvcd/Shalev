package cu.shalev.app.licencia.prueba

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.domain.model.TrialState
import cu.shalev.domain.model.pedirPermisoRegistro
import cu.shalev.domain.model.trialState
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.PruebaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object TrialTags {
    const val DIALOGO = "prueba_permiso"
}

/**
 * 0.26.0 (P74): estado de la prueba para la UI ([TrialState]) y el permiso de fotos, que se pide UNA vez por
 * instalación, junto al resto de permisos en el checklist de la primera apertura
 * ([cu.shalev.app.root.PermisosIniciales]). La pantalla bloqueante de [TrialState.Expired] y [TrialState.Tampered] es la
 * puerta de licencia que ya existe (`RootState.Bloqueo` → `BloqueoGate`): no se duplica.
 */
@HiltViewModel
class TrialViewModel @Inject constructor(
    private val licencias: LicenciaRepository,
    private val prueba: PruebaRepository,
) : ViewModel() {

    val state: StateFlow<TrialState> = combine(licencias.snapshot, prueba.info) { l, i -> l?.let { trialState(it.estado, i) } }
        .filterNotNull()
        .stateIn(viewModelScope, SharingStarted.Eagerly, TrialState.Active(null))

    private val pedido = MutableStateFlow<Boolean?>(null)

    init { viewModelScope.launch { pedido.value = prueba.permisoPedido() } }

    /** true = mostrar el checklist y, si acepta, el diálogo del sistema. */
    val pedirPermiso: StateFlow<Boolean> = combine(licencias.snapshot, prueba.info, pedido) { l, i, p ->
        p != null && pedirPermisoRegistro(l?.estado, i, prueba.lecturaPermitida(), p)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Tras el diálogo del sistema (conceda o no): no se vuelve a pedir y se relee el registro con el permiso nuevo. */
    fun alResponder() {
        viewModelScope.launch {
            prueba.marcarPermisoPedido(); pedido.value = true
            licencias.refrescar()
        }
    }

    fun descartar() {
        viewModelScope.launch { prueba.marcarPermisoPedido(); pedido.value = true }
    }
}
