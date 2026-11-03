package cu.shalev.app.herramientas

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import cu.shalev.domain.repository.EstadoPantallasRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class ModoCambio(val titulo: String) {
    CONVERTIR("Convertir importe"),
    TASA("Calcular tasa desde importes"),
}

/** Los ajustes de la calculadora que se recuerdan en este teléfono: operación, moneda de origen y tasa. */
data class AjustesCalculadora(
    val modo: ModoCambio = ModoCambio.CONVERTIR,
    val origen: MonedaCosto = MonedaCosto.USD,
    val tasa: String = "",
)

/**
 * Permanencia de los ajustes de la calculadora al cerrar la app (EstadoPantallasRepository): la tasa del día no
 * hay que reescribirla en cada visita. Los importes NO se guardan (son cuentas del momento). Un valor dañado o de
 * otra versión se ignora (null = ajustes por defecto).
 */
internal object CalculadoraGuardada {
    const val CLAVE = "herramientas.calculadora"
    private const val SEPARADOR = "\u001F"

    fun codificar(a: AjustesCalculadora): String =
        listOf(a.modo.name, a.origen.name, a.tasa).joinToString(SEPARADOR)

    fun decodificar(s: String?): AjustesCalculadora? {
        val v = s?.split(SEPARADOR) ?: return null
        if (v.size != 3 || !CalculoCambio.entradaTasaValida(v[2])) return null
        return AjustesCalculadora(
            modo = ModoCambio.entries.firstOrNull { it.name == v[0] } ?: return null,
            origen = MonedaCosto.entries.firstOrNull { it.name == v[1] } ?: return null,
            tasa = v[2],
        )
    }
}

@HiltViewModel
class CalculadoraCambioViewModel @Inject constructor(
    private val estadoPantallas: EstadoPantallasRepository,
) : ViewModel() {

    private val _ajustes = MutableStateFlow(AjustesCalculadora())
    val ajustes: StateFlow<AjustesCalculadora> = _ajustes.asStateFlow()

    init {
        viewModelScope.launch {
            val guardados = CalculadoraGuardada.decodificar(estadoPantallas.observar(CalculadoraGuardada.CLAVE).first())
            // Solo se restaura si la persona aún no tocó nada en esta apertura.
            if (guardados != null) _ajustes.update { if (it == AjustesCalculadora()) guardados else it }
        }
    }

    fun modo(m: ModoCambio) = editar { it.copy(modo = m) }
    fun origen(m: MonedaCosto) = editar { it.copy(origen = m) }
    fun tasa(t: String) = editar { it.copy(tasa = t) }

    private fun editar(cambio: (AjustesCalculadora) -> AjustesCalculadora) {
        _ajustes.update(cambio)
        val valor = CalculadoraGuardada.codificar(_ajustes.value)
        viewModelScope.launch { estadoPantallas.guardar(CalculadoraGuardada.CLAVE, valor) }
    }
}

/** Conversor independiente: no modifica las partidas ni sus totales. */
@Composable
fun CalculadoraCambio(onDismiss: () -> Unit, vm: CalculadoraCambioViewModel = hiltViewModel()) {
    val ajustes by vm.ajustes.collectAsState()
    val modo = ajustes.modo
    val origen = ajustes.origen
    val tasaTexto = ajustes.tasa
    var importeTexto by rememberSaveable { mutableStateOf("") }
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
        ShalevComboBox("Operación", ModoCambio.entries, modo, { it.titulo }, { vm.modo(it) })
        if (modo == ModoCambio.CONVERTIR) {
            ShalevComboBox("Moneda de origen", MonedaCosto.entries, origen, { it.name }, { vm.origen(it) })
            ShalevTextField(
                importeTexto, { importeTexto = it }, label = "Importe en ${origen.name}", filtro = FiltroEntrada.DECIMAL,
                isError = importeTexto.isNotBlank() && importe == null, errorText = "Escribe un importe válido.",
            )
            ShalevTextField(
                tasaTexto, { if (CalculoCambio.entradaTasaValida(it)) vm.tasa(it) },
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
                        vm.tasa(calculada)
                        vm.modo(ModoCambio.CONVERTIR)
                    }, style = IconActionStyle.Tonal,
                )
            }
            Text("La tasa se aproxima a 12 cifras significativas.", style = MaterialTheme.typography.bodySmall)
        }
        Text("No modifica ni convierte los totales de la ficha de costo.", style = MaterialTheme.typography.bodySmall)
    }
}
