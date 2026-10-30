package cu.shalev.app.registros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.Anulacion
import cu.shalev.domain.model.ItemMovimiento
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.TipoApp
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.repository.TipoAppRepository
import cu.shalev.domain.repository.TurnoRepository
import cu.shalev.domain.service.CorreccionMovimiento
import cu.shalev.domain.usecase.CorregirMovimiento
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

/** 0.31.0: textos de la corrección de un ajuste desde Registros → Movimientos. */
object TextosMovimiento {
    const val EDITAR = "Editar ajuste"
    const val CANTIDAD = "Cambio en existencias"
    const val MOTIVO = "Motivo"
    const val MOTIVO_AYUDA = "De 3 a 60 caracteres. Queda anotado en el movimiento."
    const val AVISO = "Se corrige el movimiento y las existencias a la vez."
    const val FORMATO = "No es una cantidad válida."
    const val TURNO_CERRADO = "Solo se corrigen ajustes de turnos abiertos."
    const val ERROR = "No se pudo corregir. Revisa el motivo y las existencias."

    /** Ayuda del campo: en unidades enteras o en la unidad del insumo («en kg»). */
    fun ayuda(i: ItemMovimiento): String = when (i.movimiento.entidad) {
        TipoEntidad.PRODUCTO -> "Unidades enteras: 5, -2."
        TipoEntidad.INSUMO -> "En ${i.simbolo.ifBlank { "la unidad del insumo" }}: 1.25, -0.5."
    }

    fun aplicado(delta: Long, i: ItemMovimiento) = "Ajuste corregido: ${cantidadConSigno(delta, i.movimiento.entidad, i.simbolo)}."
}

object MovimientoAccionesTags {
    const val EDITAR = "movimiento_editar"
    const val GUARDAR = "movimiento_guardar"
    const val CANTIDAD = "movimiento_cantidad"
    const val MOTIVO = "movimiento_motivo"
}

data class MovimientoAccionesUiState(
    /** El ajuste de la ficha se puede corregir (principal, tipo Ajuste y del turno abierto). */
    val puede: Boolean = false,
    val dialogo: Boolean = false,
    val cantidad: String = "",
    val motivo: String = "",
    /** true = la cantidad escrita no se entiende. */
    val error: Boolean = false,
    val trabajando: Boolean = false,
) {
    /** Guardar habilitado: cantidad entendible y distinta de la del movimiento, y motivo de 3 a 60. */
    fun puedeGuardar(m: MovimientoInventario): Boolean {
        val d = CorreccionMovimiento.leer(cantidad, m.entidad) ?: return false
        return d != m.delta && ModificacionLogic.motivoValido(motivo) && !trabajando
    }
}

/**
 * 0.31.0: corregir la cantidad de un AJUSTE desde la ficha de Registros → Movimientos. Solo en la app principal y solo
 * con el turno del movimiento ABIERTO (los cerrados quedan congelados). ViewModel aparte: RegistrosViewModel y sus
 * tests no cambian.
 */
@HiltViewModel
class MovimientoAccionesViewModel @Inject constructor(
    private val turnos: TurnoRepository,
    private val tipoApp: TipoAppRepository,
    private val corregirMovimiento: CorregirMovimiento,
) : ViewModel() {

    private val _state = MutableStateFlow(MovimientoAccionesUiState())
    val state: StateFlow<MovimientoAccionesUiState> = _state.asStateFlow()
    private val mensajes = Channel<String>(Channel.BUFFERED)
    val eventos: Flow<String> = mensajes.receiveAsFlow()

    /** Al abrir la ficha de [i]. */
    fun comprobar(i: ItemMovimiento) {
        viewModelScope.launch {
            val abierto = try {
                turnos.activo()?.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.value = MovimientoAccionesUiState(
                puede = tipoApp.tipo.value == TipoApp.PRINCIPAL &&
                    CorreccionMovimiento.puede(i.movimiento.tipo, i.movimiento.turnoId, abierto),
            )
        }
    }

    fun abrir(i: ItemMovimiento) = _state.update {
        it.copy(
            dialogo = true, error = false, motivo = "",
            cantidad = CorreccionMovimiento.texto(i.movimiento.delta, i.movimiento.entidad),
        )
    }

    fun cerrar() = _state.update { it.copy(dialogo = false, error = false) }
    fun cantidad(t: String) = _state.update { it.copy(cantidad = t, error = false) }
    fun motivo(t: String) = _state.update { it.copy(motivo = t.take(Anulacion.MOTIVO_MAX), error = false) }

    fun guardar(i: ItemMovimiento, alTerminar: () -> Unit) {
        val s = _state.value
        if (s.trabajando) return
        val delta = CorreccionMovimiento.leer(s.cantidad, i.movimiento.entidad)
        if (delta == null) {
            _state.update { it.copy(error = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(trabajando = true, error = false) }
            val r = try {
                corregirMovimiento(i.movimiento.id, delta, s.motivo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppResult.Err(AppError.Desconocido())
            }
            _state.update { it.copy(trabajando = false) }
            when (r) {
                is AppResult.Ok -> {
                    mensajes.trySend(TextosMovimiento.aplicado(delta, i))
                    _state.update { it.copy(dialogo = false, puede = false) }
                    alTerminar()
                }
                is AppResult.Err -> mensajes.trySend(mensaje(r.error))
            }
        }
    }

    private fun mensaje(e: AppError): String = when {
        e == AppError.TurnoCerrado || (e as? AppError.Validacion)?.campo == "movimiento" -> TextosMovimiento.TURNO_CERRADO
        e is AppError.StockInsuficiente -> cu.shalev.app.venta.TextosVenta.mensaje(e)
        else -> TextosMovimiento.ERROR
    }
}

/** Botón de la ficha (solo si se puede) + diálogo «Editar ajuste». [onHecho] cierra la ficha y recarga. */
@Composable
fun AccionesFichaMovimiento(i: ItemMovimiento, vm: MovimientoAccionesViewModel, onMensaje: (String) -> Unit, onHecho: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(i.movimiento.id) { vm.comprobar(i) }
    LaunchedEffect(vm) { vm.eventos.collect(onMensaje) }
    if (s.puede) {
        ShalevButtonRow {
            ShalevIconAction(
                ShalevIcons.Editar, TextosMovimiento.EDITAR, onClick = { vm.abrir(i) },
                style = IconActionStyle.Tonal, modifier = Modifier.testTag(MovimientoAccionesTags.EDITAR),
            )
        }
    }
    if (s.dialogo) DialogoCorregir(i, s, vm, onHecho)
}

@Composable
private fun DialogoCorregir(i: ItemMovimiento, s: MovimientoAccionesUiState, vm: MovimientoAccionesViewModel, onHecho: () -> Unit) {
    ShalevDialog(
        title = TextosMovimiento.EDITAR, text = TextosMovimiento.AVISO,
        onDismiss = vm::cerrar, onConfirm = { vm.guardar(i, onHecho) }, confirmDescription = TextosMovimiento.EDITAR,
        confirmEnabled = s.puedeGuardar(i.movimiento), confirmLoading = s.trabajando,
        confirmTag = MovimientoAccionesTags.GUARDAR,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            Text(
                "${i.movimiento.nombre} · ${cantidadConSigno(i.movimiento.delta, i.movimiento.entidad, i.simbolo)}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            ShalevTextField(
                value = s.cantidad, onValueChange = vm::cantidad, label = "${TextosMovimiento.CANTIDAD} *",
                supportingText = TextosMovimiento.ayuda(i), isError = s.error, errorText = TextosMovimiento.FORMATO,
                filtro = FiltroEntrada.CANTIDAD, modifier = Modifier.testTag(MovimientoAccionesTags.CANTIDAD),
            )
            ShalevTextField(
                value = s.motivo, onValueChange = vm::motivo, label = "${TextosMovimiento.MOTIVO} *",
                supportingText = TextosMovimiento.MOTIVO_AYUDA, modifier = Modifier.testTag(MovimientoAccionesTags.MOTIVO),
            )
        }
    }
}
