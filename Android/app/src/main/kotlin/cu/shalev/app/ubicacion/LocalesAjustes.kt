package cu.shalev.app.ubicacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.designsystem.component.ShalevBottomSheet
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevTextButton
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing

@Composable
fun LocalesAjustes(viewModel: LocalViewModel, onCerrar: () -> Unit) {
    val locales by viewModel.locales.collectAsStateWithLifecycle(initialValue = emptyList())
    val error by viewModel.error.collectAsStateWithLifecycle()
    val guardando by viewModel.guardando.collectAsStateWithLifecycle()
    var editando by remember { mutableStateOf(false) }
    var anterior by remember { mutableStateOf<String?>(null) }
    var nombre by remember { mutableStateOf("") }
    ShalevBottomSheet(onDismiss = onCerrar, title = "Locales") {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            Text("Añade o edita locales. Cada uno tiene su propio catálogo e inventario.")
            if (!editando) error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            locales.forEach { local ->
                ShalevListItem(title = local.nombre, subtitle = if (local.seleccionado) "Seleccionado" else "",
                    indicatorColor = null, onClick = {
                        viewModel.error.value = null
                        anterior = local.nombre; nombre = local.nombre; editando = true
                    })
            }
            ShalevTextButton("Añadir local", onClick = {
                viewModel.error.value = null
                anterior = null; nombre = ""; editando = true
            }, icon = ShalevIcons.Agregar, modifier = Modifier.fillMaxWidth())
        }
    }
    if (editando) ShalevDialog(
        title = if (anterior == null) "Añadir local" else "Editar local",
        onDismiss = { if (!guardando) editando = false },
        onConfirm = { viewModel.guardar(nombre, anterior) { editando = false } },
        confirmDescription = "Guardar", confirmEnabled = nombre.isNotBlank() && !guardando,
        confirmLoading = guardando,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            ShalevTextField(value = nombre, onValueChange = { nombre = it.take(80) }, label = "Nombre del local")
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
        }
    }
}
