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
fun AlmacenLocalCampo(ventana: VentanaUbicacion, editable: Boolean, viewModel: AlmacenLocalViewModel = hiltViewModel()) {
    // null distingue la carga de un historial realmente vacío y evita sobrescribir la selección al abrir.
    val flujo = remember(viewModel) {
        viewModel.ubicaciones.map<List<AlmacenLocal>, List<AlmacenLocal>?> { it }
            .catch { viewModel.error.value = "No se pudieron cargar los almacenes/locales."; emit(null) }
    }
    val ubicaciones by flujo.collectAsStateWithLifecycle(initialValue = null)
    val error by viewModel.error.collectAsStateWithLifecycle()
    val guardando by viewModel.guardando.collectAsStateWithLifecycle()
    val actual = ubicaciones?.firstOrNull {
        if (ventana == VentanaUbicacion.INVENTARIO) it.inventario else it.servicios
    }?.nombre.orEmpty()
    var texto by rememberSaveable(ventana) { mutableStateOf("") }
    var editado by rememberSaveable(ventana) { mutableStateOf(false) }
    LaunchedEffect(actual, ubicaciones) { if (!editado || texto.trim().equals(actual, true)) { texto = actual; editado = false } }
    ShalevComboBoxEditable(
        valor = texto, onValor = { texto = it.take(80); editado = true },
        opciones = ubicaciones.orEmpty().map { it.nombre },
        onGuardar = { viewModel.seleccionar(ventana, it) },
        enabled = editable && ubicaciones != null && !guardando, error = error,
        modifier = Modifier.padding(horizontal = ShalevSpacing.md).testTag("almacen_local_${ventana.name}"),
    )
}
