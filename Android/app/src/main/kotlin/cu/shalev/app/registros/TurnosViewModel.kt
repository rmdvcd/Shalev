package cu.shalev.app.registros

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.DetalleTurno
import cu.shalev.domain.model.Turno
import cu.shalev.domain.usecase.ObservarHistorialTurnos
import cu.shalev.domain.usecase.ObtenerDetalleTurno
import cu.shalev.domain.usecase.ExportarTurno
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.service.TablasExport
import cu.shalev.app.common.ArchivosApp
import cu.shalev.app.common.ExportadorArchivos
import cu.shalev.app.common.lanzarExportacion
import java.time.ZoneId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Registros → Turnos. La lista sale de la BD cifrada (SQLCipher) y se actualiza sola al abrir/cerrar.
 * Estados: Cargando → Éxito / Vacío / Error (genérico, con Reintentar).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TurnosViewModel @Inject constructor(observarHistorial: ObservarHistorialTurnos) : ViewModel() {

    private val intento = MutableStateFlow(0)

    val state: StateFlow<EstadoCarga<List<Turno>>> = intento.flatMapLatest {
        observarHistorial()
            .map<List<Turno>, EstadoCarga<List<Turno>>> { if (it.isEmpty()) EstadoCarga.Vacio(it) else EstadoCarga.Exito(it) }
            .onStart { emit(EstadoCarga.Cargando) }
            .catch { e -> if (e is CancellationException) throw e else emit(EstadoCarga.Error(TextosTurno.ERROR_CARGA)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoCarga.Idle)

    fun reintentar() { intento.value++ }
}

/**
 * Detalle de un turno del Registro. El id llega como argumento de navegación (`Route.TurnoDetalle.id`,
 * guardado por Navigation en el SavedStateHandle con el nombre de la propiedad).
 * Un turno cerrado es inmutable; si sigue abierto se recarga al volver a la pantalla ([refrescar]).
 */
/** 0.25.1: hoja «Compartir turno» (PDF o Excel) y archivo en preparación. */
data class ExportacionTurnoUi(val hoja: Boolean = false, val exportando: Boolean = false)

@HiltViewModel
class TurnoDetalleViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val obtenerDetalle: ObtenerDetalleTurno,
    private val exportarTurno: ExportarTurno,
    archivos: ArchivosApp,
) : ViewModel() {

    private val id: Long = saved.get<Long>(KEY_ID) ?: -1L

    /** Zona de las fechas del archivo (tests: fija). */
    internal var zona: ZoneId = ZoneId.systemDefault()

    private val _state = MutableStateFlow<EstadoCarga<DetalleTurno>>(EstadoCarga.Idle)
    val state: StateFlow<EstadoCarga<DetalleTurno>> = _state.asStateFlow()

    private val _exportacion = MutableStateFlow(ExportacionTurnoUi())
    val exportacion: StateFlow<ExportacionTurnoUi> = _exportacion.asStateFlow()
    private val eventosCh = Channel<EventoRegistros>(Channel.BUFFERED)
    val eventos: Flow<EventoRegistros> = eventosCh.receiveAsFlow()

    init { cargar() }

    /** ON_RESUME: solo hace falta si el turno sigue abierto (los cerrados no cambian). */
    fun refrescar() {
        val actual = _state.value
        if (actual is EstadoCarga.Exito && !actual.datos.provisional) return
        cargar()
    }

    fun reintentar() = cargar()

    private fun cargar() {
        viewModelScope.launch {
            if (_state.value !is EstadoCarga.Exito) _state.value = EstadoCarga.Cargando
            _state.value = try {
                when (val r = obtenerDetalle(id)) {
                    is AppResult.Ok -> EstadoCarga.Exito(r.value)
                    is AppResult.Err -> EstadoCarga.Error(if (r.error == AppError.NoEncontrado) TextosTurno.NO_ENCONTRADO else TextosTurno.ERROR_CARGA)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EstadoCarga.Error(TextosTurno.ERROR_CARGA)
            }
        }
    }

    // ---------------- 0.25.1: compartir el turno (PDF / Excel) ----------------

    private val turno: Turno? get() = (_state.value as? EstadoCarga.Exito)?.datos?.turno

    fun abrirExportar() { if (turno != null) _exportacion.update { it.copy(hoja = true) } }
    fun cerrarExportar() = _exportacion.update { it.copy(hoja = false) }

    private val exportador = ExportadorArchivos<Pair<FormatoExport, String>>(archivos)

    /** «Enviar»: crea el archivo (se vuelve a leer el turno, por si sigue abierto) y abre el selector del sistema. */
    fun enviar(formato: FormatoExport) {
        val t = turno ?: return
        cerrarExportar()
        trabajar {
            val nombre = TablasExport.nombreArchivoTurno(t, formato.extension, zona)
            when (val r = exportador.aTemporal(nombre) { exportarTurno(id, formato, it, zona) }) {
                is AppResult.Ok -> emitir(EventoRegistros.CompartirArchivo(r.value.archivo, formato.mime, TablasExport.tituloTurno(t, zona)))
                is AppResult.Err -> emitir(EventoRegistros.Mensaje(TextosRegistros.ERROR_EXPORTAR))
            }
        }
    }

    /** «Guardar en el teléfono»: pide el destino al sistema; el archivo se crea al volver ([guardarEn]). */
    fun pedirGuardar(formato: FormatoExport) {
        val t = turno ?: return
        val nombre = TablasExport.nombreArchivoTurno(t, formato.extension, zona)
        exportador.recordar(formato to nombre)
        cerrarExportar()
        emitir(EventoRegistros.GuardarComo(nombre, formato.mime))
    }

    fun guardarEn(uri: String?) {
        val (formato, nombre) = exportador.tomar(uri) ?: return
        trabajar {
            val r = exportador.aDestino(checkNotNull(uri)) { exportarTurno(id, formato, it, zona) }
            emitir(EventoRegistros.Mensaje(if (r is AppResult.Ok) TextosRegistros.guardado(nombre) else TextosRegistros.ERROR_EXPORTAR))
        }
    }

    private fun trabajar(bloque: suspend () -> Unit) {
        if (_exportacion.value.exportando) return
        lanzarExportacion(
            ocupado = { b -> _exportacion.update { it.copy(exportando = b) } },
            alFallar = { emitir(EventoRegistros.Mensaje(TextosRegistros.ERROR_EXPORTAR)) },
            bloque = bloque,
        )
    }

    private fun emitir(e: EventoRegistros) { eventosCh.trySend(e) }

    companion object {
        const val KEY_ID = "id"
    }
}
