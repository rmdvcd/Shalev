package cu.shalev.app.caja

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.core.money.Cup
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.Arqueo
import cu.shalev.domain.model.TipoApp
import cu.shalev.domain.model.TipoMovimientoCaja
import cu.shalev.domain.repository.EstadoAppRepository
import cu.shalev.domain.repository.TipoAppRepository
import cu.shalev.domain.repository.TurnoRepository
import cu.shalev.domain.usecase.FondoSugerido
import cu.shalev.domain.model.RecordatorioRespaldo
import cu.shalev.domain.usecase.RegistrarMovimientoCaja
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CajaUiState(
    /** Arqueo del turno abierto de esta app (null = sin turno o turno sin fondo). */
    val arqueo: Arqueo? = null,
    /** Fondo propuesto al abrir (null = sin turno anterior con arqueo). */
    val sugerido: Cup? = null,
    val dialogoMovimiento: Boolean = false,
    val trabajando: Boolean = false,
    /** Recordatorio mensual de respaldo (solo principal). */
    val avisoRespaldo: RecordatorioRespaldo.Aviso? = null,
)

/**
 * 0.25.0 (§4, §5): arqueo del turno abierto, fondo propuesto, entradas/salidas de efectivo y recordatorio de respaldo.
 * ViewModel aparte para no tocar los constructores de Inicio/Venta (sus tests y la integración siguen igual).
 */
@HiltViewModel
class CajaViewModel @Inject constructor(
    turnos: TurnoRepository,
    private val fondoSugerido: FondoSugerido,
    private val registrar: RegistrarMovimientoCaja,
    private val estadoApp: EstadoAppRepository,
    tipoApp: TipoAppRepository,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(CajaUiState())
    private val mensajes = Channel<String>(Channel.BUFFERED)
    val eventos: Flow<String> = mensajes.receiveAsFlow()

    val state: StateFlow<CajaUiState> = combine(
        local,
        turnos.observarArqueoActivo().catch { emit(null) },
        estadoApp.estado.catch { emit(cu.shalev.domain.model.EstadoApp()) },
        tipoApp.tipo,
    ) { l, a, e, t ->
        l.copy(arqueo = a, avisoRespaldo = RecordatorioRespaldo.aviso(clock.now(), e, t == TipoApp.PRINCIPAL))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CajaUiState())

    init {
        cargarSugerido()
        // §4: la cuenta de 30 días empieza en la primera apertura con esta versión (no se avisa el primer día).
        viewModelScope.launch {
            runCatching {
                if (estadoApp.actual().cuentaRespaldoDesde == null) estadoApp.editar { it.copy(cuentaRespaldoDesde = it.cuentaRespaldoDesde ?: clock.now()) }
            }
        }
    }

    /** Se llama al mostrar el diálogo de apertura (el turno anterior puede haber cambiado). */
    fun cargarSugerido() {
        viewModelScope.launch {
            val s = try { fondoSugerido() } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            local.update { it.copy(sugerido = s) }
        }
    }

    fun abrirMovimiento() = local.update { it.copy(dialogoMovimiento = true) }
    fun cerrarMovimiento() = local.update { it.copy(dialogoMovimiento = false) }

    fun registrarMovimiento(tipo: TipoMovimientoCaja, importe: Cup, motivo: String) {
        if (local.value.trabajando) return
        val disponible = state.value.arqueo?.esperado
        if (tipo == TipoMovimientoCaja.SALIDA && disponible != null && importe > disponible) {
            mensajes.trySend("El importe supera el efectivo disponible en caja.")
            return
        }
        viewModelScope.launch {
            local.update { it.copy(trabajando = true) }
            val r = try { registrar(tipo, importe, motivo) } catch (e: CancellationException) { throw e } catch (e: Exception) { AppResult.Err(AppError.Desconocido()) }
            local.update { it.copy(trabajando = false, dialogoMovimiento = r is AppResult.Err) }
            mensajes.trySend(
                when {
                    r is AppResult.Ok -> TextosCaja.MOVIMIENTO_GUARDADO
                    r is AppResult.Err && r.error == AppError.TurnoCerrado -> "No hay turno abierto."
                    else -> "No se pudo registrar el movimiento."
                },
            )
        }
    }
}
