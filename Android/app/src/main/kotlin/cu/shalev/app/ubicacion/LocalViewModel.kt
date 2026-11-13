package cu.shalev.app.ubicacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.domain.repository.LocalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@HiltViewModel
class LocalViewModel @Inject constructor(private val repository: LocalRepository) : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val locales = repository.observar().catch { error.value = "No se pudieron cargar los locales."; emit(emptyList()) }
    val guardando = MutableStateFlow(false)

    fun seleccionar(nombre: String) = ejecutar { repository.seleccionar(nombre) }

    fun guardar(nombre: String, anterior: String?, terminado: () -> Unit) = ejecutar {
        repository.guardar(nombre, anterior)
        terminado()
    }

    private fun ejecutar(accion: suspend () -> Unit) {
        if (guardando.value) return
        guardando.value = true
        error.value = null
        viewModelScope.launch {
            try {
                accion()
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
