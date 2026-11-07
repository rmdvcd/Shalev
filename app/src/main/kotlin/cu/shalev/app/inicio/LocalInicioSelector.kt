package cu.shalev.app.inicio

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.ubicacion.LocalViewModel
import cu.shalev.designsystem.component.ShalevComboBox
import cu.shalev.designsystem.token.ShalevSpacing

/** La selección es compartida; los nombres se administran en Ajustes. */
@Composable
fun LocalInicioSelector(viewModel: LocalViewModel) {
    val locales by viewModel.locales.collectAsStateWithLifecycle(initialValue = emptyList())
    val error by viewModel.error.collectAsStateWithLifecycle()
    ShalevComboBox(
        label = "Local", opciones = locales.map { it.nombre },
        seleccion = locales.firstOrNull { it.seleccionado }?.nombre ?: "Selecciona un local en Ajustes",
        etiqueta = { it }, onSeleccion = viewModel::seleccionar,
        modifier = Modifier.padding(horizontal = ShalevSpacing.md).testTag("local_inicio_selector"),
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = ShalevSpacing.md)) }
}
