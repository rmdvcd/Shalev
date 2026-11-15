package cu.shalev.app.ubicacion

import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.app.recorrido.ObjetivosRecorrido
import cu.shalev.designsystem.component.coachMark
import cu.shalev.app.recorrido.Recorrido
import cu.shalev.app.recorrido.recorridoVentana
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.designsystem.component.ShalevBottomSheet
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevTextButton
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.designsystem.token.ShalevSpacing

object LocalesTags {
    fun local(nombre: String) = "locales.local.$nombre"
}

@Composable
fun LocalesAjustes(viewModel: LocalViewModel, onCerrar: () -> Unit) {
    val locales by viewModel.locales.collectAsStateWithLifecycle(initialValue = emptyList())
    val error by viewModel.error.collectAsStateWithLifecycle()
    val guardando by viewModel.guardando.collectAsStateWithLifecycle()
    var editando by remember { mutableStateOf(false) }
    var anterior by remember { mutableStateOf<String?>(null) }
    var nombre by remember { mutableStateOf("") }
    ShalevBottomSheet(onDismiss = onCerrar, title = "Locales", recorrido = recorridoVentana(Recorrido.VENTANA_LOCALES)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            Text("Añade o edita locales. Cada uno tiene su propio catálogo e inventario.")
            if (!editando) error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            val verde = ShalevTheme.colors.success
            locales.forEachIndexed { n, local ->
                ShalevListItem(
                    title = local.nombre,
                    subtitle = if (local.seleccionado) "Local seleccionado" else "",
                    indicatorColor = if (local.seleccionado) verde else null,
                    selected = local.seleccionado,
                    selectedContainerColor = verde.copy(alpha = 0.16f),
                    trailing = if (local.seleccionado) {
                        { Icon(ShalevIcons.Confirmar, contentDescription = null, tint = verde) }
                    } else null,
                    modifier = Modifier.testTag(LocalesTags.local(local.nombre))
                        .then(if (n == 0) Modifier.coachMark(ObjetivosRecorrido.VENTANA_LISTA) else Modifier),
                    onClick = {
                        viewModel.error.value = null
                        anterior = local.nombre; nombre = local.nombre; editando = true
                    },
                )
            }
            ShalevTextButton("Añadir local", etiqueta = "Añadir", onClick = {
                viewModel.error.value = null
                anterior = null; nombre = ""; editando = true
            }, icon = ShalevIcons.Agregar, modifier = Modifier.fillMaxWidth().coachMark(ObjetivosRecorrido.VENTANA_AGREGAR))
        }
    }
    if (editando) ShalevDialog(
        title = if (anterior == null) "Añadir local" else "Editar local",
        onDismiss = { if (!guardando) editando = false },
        onConfirm = { viewModel.guardar(nombre, anterior) { editando = false } },
        confirmDescription = "Guardar", confirmEnabled = nombre.isNotBlank() && !guardando,
        confirmLoading = guardando,
        recorrido = recorridoVentana(Recorrido.VENTANA_LOCAL),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            ShalevTextField(
                value = nombre, onValueChange = { nombre = it.take(80) }, label = "Nombre del local", filtro = FiltroEntrada.NOMBRE,
                modifier = Modifier.coachMark(ObjetivosRecorrido.VENTANA_NOMBRE),
            )
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
        }
    }
}
