package cu.shalev.app.ubicacion

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.designsystem.component.ShalevComboBoxEditable
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.repository.AlmacenLocal
import cu.shalev.domain.repository.VentanaUbicacion
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Composable
fun LocalCampo(editable: Boolean, viewModel: LocalViewModel = hiltViewModel()) {
    // null distingue la carga de un historial realmente vacío y evita sobrescribir la selección al abrir.
    val flujo = remember(viewModel) {
        viewModel.locales.map<List<AlmacenLocal>, List<AlmacenLocal>?> { it }
            .catch { viewModel.error.value = "No se pudieron cargar los locales."; emit(null) }
    }
    val locales by flujo.collectAsStateWithLifecycle(initialValue = null)
    val error by viewModel.error.collectAsStateWithLifecycle()
    val guardando by viewModel.guardando.collectAsStateWithLifecycle()
    
    // El local único compartido (si hay uno, es el mismo para inventario y servicios)
    val actual = locales?.firstOrNull()?.nombre.orEmpty()
    
    var texto by rememberSaveable { mutableStateOf("") }
    var editado by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(actual, locales) { 
        if (!editado || texto.trim().equals(actual, true)) { 
            texto = actual; editado = false 
        } 
    }
    
    // Opciones: todos los locales existentes + el botón (+) como última opción
    val opciones = locales.orEmpty().map { it.nombre } + listOf("+")
    
    ShalevComboBoxEditable(
        valor = texto, onValor = { texto = it.take(80); editado = true },
        opciones = opciones,
        onGuardar = { 
            if (it == "+") {
                // El botón (+) abre el teclado y se confirma al pulsar Done (onImeAction)
                // Si el usuario escribe un nombre nuevo, se guarda aquí.
                // Si el valor es "+" (aunque es improbable por el filtro), lo ignoramos.
            } else {
                viewModel.seleccionar(it)
            }
        },
        enabled = editable && locales != null && !guardando, error = error,
        modifier = Modifier.padding(horizontal = ShalevSpacing.md).testTag("local_selector"),
    )
}
