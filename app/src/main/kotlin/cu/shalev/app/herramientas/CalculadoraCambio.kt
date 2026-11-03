package cu.shalev.app.herramientas

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevBottomSheet
import cu.shalev.designsystem.component.ShalevComboBox
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.domain.fichacosto.CalculoCambio
import cu.shalev.domain.fichacosto.CalculoFichaCosto
import cu.shalev.domain.fichacosto.MonedaCosto

private enum class ModoCambio(val titulo: String) {
    CONVERTIR("Convertir importe"),
    TASA("Calcular tasa desde importes"),
}

/** Conversor independiente: no modifica las partidas ni sus totales. */
@Composable
fun CalculadoraCambio(onDismiss: () -> Unit) {
    var modo by rememberSaveable { mutableStateOf(ModoCambio.CONVERTIR) }
    var origen by rememberSaveable { mutableStateOf(MonedaCosto.USD) }
    var importeTexto by rememberSaveable { mutableStateOf("") }
    var tasaTexto by rememberSaveable { mutableStateOf("") }
    var cupTexto by rememberSaveable { mutableStateOf("") }
    var usdTexto by rememberSaveable { mutableStateOf("") }
    val importe = CalculoFichaCosto.numero(importeTexto)
    val tasa = CalculoCambio.leerTasa(tasaTexto)
    val cup = CalculoFichaCosto.numero(cupTexto)?.takeIf { it.signum() > 0 }
    val usd = CalculoFichaCosto.numero(usdTexto)?.takeIf { it.signum() > 0 }

    ShalevBottomSheet(
        onDismiss = onDismiss,
        title = "Calculadora de cambio",
        footer = {
            ShalevIconAction(
                ShalevIcons.Cancelar, "Cerrar calculadora", onClick = onDismiss,
                style = IconActionStyle.Filled,
            )
        },
    ) {
        Text("Tasa orientativa introducida o calculada por ti; no se consulta ni actualiza por internet.")
        ShalevComboBox("Operación", ModoCambio.entries, modo, { it.titulo }, { modo = it })
        if (modo == ModoCambio.CONVERTIR) {
            ShalevComboBox("Moneda de origen", MonedaCosto.entries, origen, { it.name }, { origen = it })
            ShalevTextField(
                importeTexto, { importeTexto = it }, label = "Importe en ${origen.name}", filtro = FiltroEntrada.DECIMAL,
                isError = importeTexto.isNotBlank() && importe == null, errorText = "Escribe un importe válido.",
            )
            ShalevTextField(
                tasaTexto, { if (CalculoCambio.entradaTasaValida(it)) tasaTexto = it },
                label = "Tasa: CUP por 1 USD", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = tasaTexto.isNotBlank() && tasa == null, errorText = "La tasa debe ser mayor que cero.",
            )
            if (importe != null && tasa != null) {
                val destino = if (origen == MonedaCosto.USD) MonedaCosto.CUP else MonedaCosto.USD
                val resultado = CalculoCambio.convertir(importe, tasa, origen)
                Text("Resultado: ${resultado.toPlainString()} ${destino.name}", style = MaterialTheme.typography.titleMedium)
            }
            Text("El resultado se redondea a dos decimales.", style = MaterialTheme.typography.bodySmall)
        } else {
            ShalevTextField(
                cupTexto, { cupTexto = it }, label = "Importe equivalente en CUP", filtro = FiltroEntrada.DECIMAL,
                isError = cupTexto.isNotBlank() && cup == null, errorText = "Escribe un importe mayor que cero.",
            )
            ShalevTextField(
                usdTexto, { usdTexto = it }, label = "Importe equivalente en USD", filtro = FiltroEntrada.DECIMAL,
                isError = usdTexto.isNotBlank() && usd == null, errorText = "Escribe un importe mayor que cero.",
            )
            if (cup != null && usd != null) {
                val calculada = CalculoCambio.tasa(cup, usd).toPlainString()
                Text("1 USD = $calculada CUP", style = MaterialTheme.typography.titleMedium)
                Text("Toca la flecha para usar esta tasa al convertir.", style = MaterialTheme.typography.bodySmall)
                ShalevIconAction(
                    ShalevIcons.Siguiente, "Usar tasa en conversión", onClick = {
                        tasaTexto = calculada
                        modo = ModoCambio.CONVERTIR
                    }, style = IconActionStyle.Tonal,
                )
            }
            Text("La tasa se aproxima a 12 cifras significativas.", style = MaterialTheme.typography.bodySmall)
        }
        Text("No modifica ni convierte los totales de la ficha de costo.", style = MaterialTheme.typography.bodySmall)
    }
}
