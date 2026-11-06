package cu.shalev.app.inicio

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import cu.shalev.designsystem.component.ShalevComboBoxEditable
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.repository.Local
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Combobox único en Inicio para seleccionar el Local (único para Inventario y Servicios).
 * Incluye el botón (+) como última opción para introducir un local nuevo mediante el teclado.
 */
@Composable
fun LocalInicioSelector(viewModel: cu.shalev.app.ubicacion.LocalViewModel = hiltViewModel()) {
    // null distingue la carga de un historial realmente vacío
    val flujo = remember(viewModel) {
        viewModel.locales.map<List<Local>, List<Local>?> { it }
            .catch { viewModel.error.value = "No se pudieron cargar los locales."; emit(null) }
    }
    val locales by flujo.collectAsStateWithLifecycle(initialValue = null)
    val error by viewModel.error.collectAsStateWithLifecycle()
    val guardando by viewModel.guardando.collectAsStateWithLifecycle()
    
    val actual = locales?.firstOrNull()?.nombre.orEmpty()
    
    var texto by remember { mutableStateOf("") }
    var editado by remember { mutableStateOf(false) }
    
    // El combobox único en Inicio
    // Opciones: los locales existentes + el botón (+)
    val opciones = locales.orEmpty().map { it.nombre } + listOf("+")
    
    ShalevComboBoxEditable(
        valor = texto, onValor = { texto = it.take(80); editado = true },
        opciones = opciones,
        onGuardar = { 
            if (it == "+") {
                // El usuario pulsó el botón (+). El teclado se abre.
                // La confirmación se hace mediante el teclado (onImeAction en ShalevComboBoxEditable)
                // o al pulsar el botón de guardar si el texto no es "+".
                // Si el texto es "+", se ignora (es solo el botón).
            } else {
                viewModel.seleccionar(it)
                texto = it
                editado = false
            }
        },
        enabled = !guardando, error = error,
        modifier = Modifier.padding(horizontal = ShalevSpacing.md).testTag("local_inicio_selector"),
    )
}
