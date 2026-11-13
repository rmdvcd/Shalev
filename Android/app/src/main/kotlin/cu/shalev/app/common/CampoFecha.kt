package cu.shalev.app.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cu.shalev.core.time.Dates
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object FechasUi {
    fun texto(f: LocalDate?): String = f?.let { Dates.day(it) }.orEmpty()

    /** P24: guía del campo según el formato del dispositivo: "dd/MM/yyyy" → "dd/mm/aaaa", "M/d/yy" → "m/d/aa". */
    fun guia(): String = Dates.formato.dia.lowercase().replace('y', 'a')

    /** El DatePicker de M3 trabaja en milisegundos UTC a medianoche. */
    fun aMillis(f: LocalDate): Long = f.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun deMillis(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
}

/**
 * Fecha opcional: campo de solo lectura que abre el calendario (sin teclear formatos), con botón para borrarla.
 * El campo entero es pulsable y lo anuncia TalkBack como botón.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFecha(
    fecha: LocalDate?,
    onFecha: (LocalDate?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
    tag: String = "campo_fecha",
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        ShalevTextField(
            value = FechasUi.texto(fecha),
            onValueChange = {},
            label = label,
            placeholder = FechasUi.guia(),
            readOnly = true,
            clearable = false,
            enabled = enabled,
            supportingText = supportingText,
            leadingIcon = ShalevIcons.Reloj,
            modifier = Modifier.weight(1f).testTag(tag),
        )
        ShalevIconAction(ShalevIcons.Editar, "Elegir $label", onClick = { abierto = true }, enabled = enabled, modifier = Modifier.testTag("${tag}_elegir"))
        if (fecha != null) {
            ShalevIconAction(ShalevIcons.Limpiar, "Borrar $label", onClick = { onFecha(null) }, enabled = enabled, modifier = Modifier.testTag("${tag}_borrar"))
        }
    }
    if (abierto) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (fecha ?: LocalDate.now()).let(FechasUi::aMillis))
        val cerrar = remember { { abierto = false } }
        DatePickerDialog(
            onDismissRequest = cerrar,
            // 1.0.3: sin botón Cancelar (se cierra tocando fuera o con Atrás); solo el botón de Aceptar, centrado.
            confirmButton = {
                ShalevButtonRow {
                    ShalevIconAction(
                        ShalevIcons.Confirmar, "Aceptar", style = IconActionStyle.Filled,
                        onClick = { state.selectedDateMillis?.let { onFecha(FechasUi.deMillis(it)) }; cerrar() },
                    )
                }
            },
            dismissButton = {},
        ) {
            DatePicker(state = state, modifier = Modifier.fillMaxWidth())
        }
    }
}
