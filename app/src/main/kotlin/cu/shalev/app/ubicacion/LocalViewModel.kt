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
class LocalViewModel @Inject constructor(private val repository: LocalRepository) : ViewModel() {
    val locales = repository.observar()
    val error = MutableStateFlow<String?>(null)
    val guardando = MutableStateFlow(false)

    fun seleccionar(nombre: String) {
        if (guardando.value) return
        guardando.value = true
        error.value = null
        viewModelScope.launch {
            try {
                // Al ser un único local, se asocia a ambas ventanas (Inventario y Servicios)
                repository.seleccionar(nombre)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                error.value = e.message
            } catch (_: Exception) {
                error.value = "No se pudo guardar el local. Inténtalo de nuevo."
            } finally {
                guardando.value = false
            }
        }
    }
}
