package cu.shalev.app.registros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cu.shalev.core.money.sumOfCup
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.Anulacion
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.TipoApp
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.TipoAppRepository
import cu.shalev.domain.repository.TurnoRepository
import cu.shalev.domain.service.LineaSolicitada
import cu.shalev.domain.usecase.AnularVenta
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 0.25.0 (§6bis): textos de anular / modificar. */
object TextosAnulacion {
    const val ANULAR = "Anular venta"
    const val MODIFICAR = "Modificar venta"
    const val MOTIVO = "Motivo"
    const val MOTIVO_AYUDA = "De 3 a 60 caracteres. Queda en Registros con la fecha y quién lo hizo."
    const val ANULAR_TEXTO = "La venta queda en Registros como ANULADA, deja de contar en los totales y en la caja, y se devuelven las existencias."
    const val AVISO_TRANSFERENCIA = "Era una transferencia: devuelve el dinero por Transfermóvil; Shalev no lo hace."
    const val ANULADA = "Venta anulada. Se devolvieron las existencias."
    fun modificada(total: String) = "Venta corregida: $total."
    const val SIN_LINEAS = "Deja al menos una línea; para quitarlas todas, anula la venta."
    const val TURNO_CERRADO = "Solo se anulan o modifican ventas de turnos abiertos."
    const val ERROR = "No se pudo completar. Revisa el motivo y las existencias."
}

object VentaAccionesTags {
    const val ANULAR = "venta_anular"
    const val MODIFICAR = "venta_modificar"
    const val MOTIVO = "venta_motivo"
    fun menos(i: Int) = "venta_mod_menos_$i"
    fun mas(i: Int) = "venta_mod_mas_$i"
}

data class VentaAccionesUiState(
    /** La venta abierta en la ficha se puede anular/modificar (principal, turno abierto, no anulada). */
    val puede: Boolean = false,
    val dialogo: DialogoVenta? = null,
    val trabajando: Boolean = false,
)

enum class DialogoVenta { ANULAR, MODIFICAR }

/**
 * 0.25.0 (§6bis): anular y modificar ventas desde la ficha de Registros. Solo en la app principal y solo con el turno de la
 * venta ABIERTO (los cerrados quedan congelados). ViewModel aparte: RegistrosViewModel y sus tests no cambian.
 */
@HiltViewModel
class VentaAccionesViewModel @Inject constructor(
    private val turnos: TurnoRepository,
    private val tipoApp: TipoAppRepository,
    private val anularVenta: AnularVenta,
) : ViewModel() {

    private val _state = MutableStateFlow(VentaAccionesUiState())
    val state: StateFlow<VentaAccionesUiState> = _state.asStateFlow()
    private val mensajes = Channel<String>(Channel.BUFFERED)
    val eventos: Flow<String> = mensajes.receiveAsFlow()

    /** Al abrir la ficha de [v]. */
    fun comprobar(v: Venta) {
        viewModelScope.launch {
            val ok = !v.anulada && tipoApp.tipo.value == TipoApp.PRINCIPAL &&
                (try { turnos.obtener(v.turnoId)?.abierto == true } catch (e: CancellationException) { throw e } catch (e: Exception) { false })
            _state.update { VentaAccionesUiState(puede = ok) }
        }
    }

    fun abrir(d: DialogoVenta) = _state.update { it.copy(dialogo = d) }
    /** 0.25.1 (B): la pantalla Modificar guardó: se cierra y la venta original ya no se puede tocar. */
    fun terminado() = _state.update { it.copy(dialogo = null, puede = false) }
    fun cerrar() = _state.update { it.copy(dialogo = null) }

    fun anular(v: Venta, motivo: String, alTerminar: () -> Unit) = trabajar(alTerminar) {
        anularVenta(v.id, motivo).let { r -> if (r is AppResult.Ok) AppResult.Ok(TextosAnulacion.ANULADA) else r as AppResult.Err }
    }

    private fun trabajar(alTerminar: () -> Unit, bloque: suspend () -> AppResult<String>) {
        if (_state.value.trabajando) return
        viewModelScope.launch {
            _state.update { it.copy(trabajando = true) }
            val r = try { bloque() } catch (e: CancellationException) { throw e } catch (e: Exception) { AppResult.Err(AppError.Desconocido()) }
            _state.update { it.copy(trabajando = false, dialogo = if (r is AppResult.Ok) null else it.dialogo) }
            when (r) {
                is AppResult.Ok -> { mensajes.trySend(r.value); _state.update { it.copy(puede = false) }; alTerminar() }
                is AppResult.Err -> mensajes.trySend(
                    when {
                        r.error == AppError.TurnoCerrado -> TextosAnulacion.TURNO_CERRADO
                        (r.error as? AppError.Validacion)?.campo == "lineas" -> TextosAnulacion.SIN_LINEAS
                        r.error is AppError.StockInsuficiente -> cu.shalev.app.venta.TextosVenta.mensaje(r.error)
                        else -> TextosAnulacion.ERROR
                    },
                )
            }
        }
    }
}

/** Lógica pura de la modificación (testeable). */
object ModificacionLogic {
    fun lineas(v: Venta, cantidades: List<Long>): List<LineaSolicitada> =
        v.detalles.zip(cantidades).filter { (_, c) -> c > 0 }.map { (d, c) -> LineaSolicitada(d.productoId, c, d.clase) }

    fun total(v: Venta, cantidades: List<Long>) = v.detalles.zip(cantidades).sumOfCup { (d, c) -> d.precioUnitario * c }

    fun motivoValido(m: String) = m.trim().replace(Regex("\\s+"), " ").length in Anulacion.MOTIVO_MIN..Anulacion.MOTIVO_MAX
}

/**
 * Botones de la ficha (solo si se puede) + diálogos. [onHecho] cierra la ficha y recarga.
 *
 * 0.31.0: [verVenta] («Ver la venta» de una transferencia) entra en esta misma fila —centrada— y **después** de
 * «Anular venta»; antes colgaba suelta del contenido y, al no centrar los `Shalev*Button` un `Modifier.align` ajeno
 * (el `modifier` del llamador se aplica dentro del `TooltipBox`), quedaba pegada a la izquierda.
 */
@Composable
fun AccionesFichaVenta(
    v: Venta, vm: VentaAccionesViewModel, onMensaje: (String) -> Unit, onHecho: () -> Unit,
    verVenta: (() -> Unit)? = null,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(v.id, v.anulada) { vm.comprobar(v) }
    LaunchedEffect(vm) { vm.eventos.collect(onMensaje) }
    if (s.puede || verVenta != null) ShalevButtonRow {
        if (s.puede) {
            ShalevIconAction(
                ShalevIcons.Editar, TextosAnulacion.MODIFICAR, onClick = { vm.abrir(DialogoVenta.MODIFICAR) },
                style = IconActionStyle.Tonal, modifier = Modifier.testTag(VentaAccionesTags.MODIFICAR),
            )
            ShalevIconAction(
                ShalevIcons.Eliminar, TextosAnulacion.ANULAR, onClick = { vm.abrir(DialogoVenta.ANULAR) },
                style = IconActionStyle.Tonal, modifier = Modifier.testTag(VentaAccionesTags.ANULAR),
            )
        }
        verVenta?.let {
            ShalevSecondaryButton(
                TextosRegistros.VER_VENTA, onClick = it, icon = ShalevIcons.Comprobante,
                modifier = Modifier.testTag(RegistrosTags.FICHA_VER_VENTA),
            )
        }
    }
    when (s.dialogo) {
        DialogoVenta.ANULAR -> DialogoAnular(v, s.trabajando, onConfirmar = { m -> vm.anular(v, m, onHecho) }, onDismiss = vm::cerrar)
        // 0.25.1 (B): pantalla completa (añadir artículos, cambiar el método de pago).
        DialogoVenta.MODIFICAR -> ModificarVentaPantalla(v, onCerrar = vm::cerrar, onHecho = { m -> vm.terminado(); onMensaje(m); onHecho() })
        null -> Unit
    }
}

@Composable
private fun CampoMotivo(motivo: String, onCambio: (String) -> Unit) {
    ShalevTextField(
        value = motivo, onValueChange = { onCambio(it.take(Anulacion.MOTIVO_MAX)) }, label = "${TextosAnulacion.MOTIVO} *",
        supportingText = TextosAnulacion.MOTIVO_AYUDA, modifier = Modifier.testTag(VentaAccionesTags.MOTIVO),
    )
}

@Composable
private fun DialogoAnular(v: Venta, trabajando: Boolean, onConfirmar: (String) -> Unit, onDismiss: () -> Unit) {
    var motivo by rememberSaveable { mutableStateOf("") }
    ShalevDialog(
        title = TextosAnulacion.ANULAR, text = TextosAnulacion.ANULAR_TEXTO,
        onDismiss = onDismiss, onConfirm = { onConfirmar(motivo) }, confirmDescription = TextosAnulacion.ANULAR,
        destructive = true, confirmEnabled = ModificacionLogic.motivoValido(motivo) && !trabajando, confirmLoading = trabajando,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            if (v.metodoPago == MetodoPago.TRANSFERENCIA) Text(TextosAnulacion.AVISO_TRANSFERENCIA, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            CampoMotivo(motivo) { motivo = it }
        }
    }
}
