package cu.shalev.app.ubicacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.domain.repository.LocalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AlmacenLocalViewModel @Inject constructor(private val repository: LocalRepository) : ViewModel() {
    val ubicaciones = repository.observar()
    val error = MutableStateFlow<String?>(null)
    val guardando = MutableStateFlow(false)

    fun seleccionar(ventana: String, nombre: String) {
        // Delegar a LocalViewModel para mantener compatibilidad
        // (ventana se ignora porque el local es único para ambas)
        LocalViewModel(repository).seleccionar(nombre)
    }
}
