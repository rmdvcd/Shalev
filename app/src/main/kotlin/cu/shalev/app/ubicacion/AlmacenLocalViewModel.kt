package cu.shalev.app.ubicacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.domain.repository.AlmacenLocalRepository
import cu.shalev.domain.repository.VentanaUbicacion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AlmacenLocalViewModel @Inject constructor(private val repository: AlmacenLocalRepository) : ViewModel() {
    val ubicaciones = repository.observar()
    val error = MutableStateFlow<String?>(null)
    val guardando = MutableStateFlow(false)

    fun seleccionar(ventana: VentanaUbicacion, nombre: String) {
        if (guardando.value) return
        guardando.value = true
        error.value = null
        viewModelScope.launch {
            try {
                repository.seleccionar(ventana, nombre)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error.value = e.message
            } catch (_: Exception) {
                error.value = "No se pudo guardar el almacén/local. Inténtalo de nuevo."
            } finally {
                guardando.value = false
            }
        }
    }
}
