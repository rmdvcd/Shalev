package cu.shalev.app.caja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.ConteoCierre
import cu.shalev.domain.model.LineaConteo
import cu.shalev.domain.repository.TurnoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Conserva el conteo sin ajustar existencias; solo el cierre o la aprobación de la principal las ajustan. */
@HiltViewModel
class CierreConteoViewModel @Inject constructor(private val turnos: TurnoRepository) : ViewModel() {
    private val _lineas = MutableStateFlow<List<LineaConteo>?>(null)
    val lineas = _lineas.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _guardando = MutableStateFlow(false)
    val guardando = _guardando.asStateFlow()
    private var turnoId: Long? = null

    fun cargar() {
        _lineas.value = null
        _error.value = null
        viewModelScope.launch {
            when (val r = turnos.prepararConteo()) {
                is AppResult.Ok -> { turnoId = r.value.first; _lineas.value = r.value.second }
                is AppResult.Err -> _error.value = "No se pudo cargar el inventario del turno. Vuelve a intentarlo."
            }
        }
    }
    fun guardar(conteo: ConteoCierre, terminado: () -> Unit) {
        if (_guardando.value) return
        val id = turnoId ?: return
        _guardando.value = true
        viewModelScope.launch {
            try {
                when (turnos.guardarConteo(id, conteo)) {
                    is AppResult.Ok -> terminado()
                    is AppResult.Err -> _error.value = "No se guardó el conteo. El inventario puede haber cambiado; vuelve a cargarlo y contar."
                }
            } finally { _guardando.value = false }
        }
    }
}
