package cu.shalev.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
import cu.shalev.designsystem.icon.ShalevIcons

/** Texto libre con historial de valores y confirmación explícita; no guarda cada pulsación. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShalevComboBoxEditable(
    valor: String,
    onValor: (String) -> Unit,
    opciones: List<String>,
    onGuardar: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = abierto && enabled,
        onExpandedChange = { abierto = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShalevTextField(
                value = valor, onValueChange = onValor, label = "Almacén/Local",
                modifier = Modifier.weight(1f).menuAnchor(MenuAnchorType.PrimaryEditable),
                enabled = enabled, isError = error != null, errorText = error,
                supportingText = "Escribe y guarda, o selecciona un local existente",
                imeAction = ImeAction.Done, onImeAction = { if (valor.isNotBlank()) onGuardar(valor) },
            )
            ShalevIconAction(
                ShalevIcons.Desplegar, "Seleccionar almacén/local", onClick = { abierto = !abierto },
                enabled = enabled && opciones.isNotEmpty(),
                modifier = Modifier.menuAnchor(MenuAnchorType.SecondaryEditable),
            )
            ShalevIconAction(ShalevIcons.Confirmar, "Guardar almacén/local", onClick = { onGuardar(valor) }, enabled = enabled && valor.isNotBlank())
        }
        ExposedDropdownMenu(expanded = abierto && enabled && opciones.isNotEmpty(), onDismissRequest = { abierto = false }) {
            opciones.forEach { nombre ->
                DropdownMenuItem(text = { Text(nombre) }, onClick = {
                    abierto = false
                    onValor(nombre)
                    onGuardar(nombre)
                })
            }
        }
    }
}
