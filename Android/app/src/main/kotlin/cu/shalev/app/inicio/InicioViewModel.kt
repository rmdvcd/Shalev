package cu.shalev.app.inicio

import cu.shalev.domain.repository.SecundariaRepository
import cu.shalev.app.common.mensajeSync
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.app.navigation.Route
import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.designsystem.component.BannerTone
import cu.shalev.domain.model.GraficosPeriodo
import cu.shalev.domain.model.OpcionPeriodo
import cu.shalev.domain.model.ResumenGeneral
import cu.shalev.domain.model.Turno
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.PerfilRepository
import cu.shalev.domain.repository.PreciosRepository
import cu.shalev.domain.repository.ProductoRepository
import cu.shalev.domain.repository.TurnoRepository
import cu.shalev.domain.usecase.AbrirTurno
import cu.shalev.domain.usecase.CerrarTurno
import cu.shalev.domain.usecase.ObservarAlertas
import cu.shalev.domain.usecase.ObtenerGraficosPeriodo
import cu.shalev.domain.usecase.ObtenerResumenGeneral
import cu.shalev.domain.usecase.ResolverPeriodo
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InicioUiState(
    val banner: String? = null,
    /** P18 (A10): Info / Aviso / Crítico según los días que quedan. */
    val bannerTono: BannerTone = BannerTone.Info,
    val alertas: List<AlertaUi> = emptyList(),
    val pago: PagoResumen = PagoResumen(null, null),
    val preajustesActivos: Int = 0,
    val preajustesTotal: Int = 0,
    val turno: Turno? = null,
    val cambiandoTurno: Boolean = false,
    val opcion: OpcionPeriodo = OpcionPeriodo.TURNO,
    val graficos: EstadoCarga<GraficosPeriodo> = EstadoCarga.Idle,
    val resumen: EstadoCarga<ResumenGeneral> = EstadoCarga.Idle,
    val dialogo: DialogoInicio? = null,
    /** 0.20.0 (H5): app secundaria con el cierre de turno pedido por el encargado (aún sin aplicar). */
    val cierrePedido: Boolean = false,
    /** 0.20.0 (H5): el último turno lo cerró el encargado; aviso hasta que el empleado lo descarte. */
    val avisoCierre: Boolean = false,
    /** 0.21.0 (C6): app secundaria: el turno no se cierra, se solicita. */
    val esSecundaria: Boolean = false,
    val cierreSolicitado: Boolean = false,
    val cierreRechazado: Boolean = false,
    /** 0.25.0 (§5): arqueo del turno abierto (null = sin turno o turno sin fondo, anterior a 0.25.0). */
    val arqueo: cu.shalev.domain.model.Arqueo? = null,
    /** 0.25.1 (D): el empleado tocó «Ahora no» en el conteo; vuelve al regresar a Inicio o a los 15 minutos. */
    val conteoPospuesto: Boolean = false,
) {
    val turnoAbierto: Boolean get() = turno?.abierto == true
    /** P43: con el cierre pedido no se empieza otra venta (la que esté en curso sí termina). */
    val puedeEmpezarVenta: Boolean get() = !cierrePedido
    /**
     * 0.25.0 (§5.3): secundaria con el cierre pedido por el encargado y turno con fondo pero sin contar: hay que contar
     * el efectivo; el cierre no se aplica sin conteo.
     */
    val pideConteo: Boolean get() = esSecundaria && cierrePedido && turno?.abierto == true && turno.fondo != null && turno.contado == null
    /** El diálogo de conteo se muestra solo; con «Ahora no» queda la tarjeta con «Contar» (nada se cierra solo). */
    val debeContar: Boolean get() = pideConteo && !conteoPospuesto
}

sealed interface DialogoInicio {
    data object NuevaVenta : DialogoInicio
    /** 0.25.0 (§5.2): fondo de caja obligatorio al abrir. */
    data object AbrirTurno : DialogoInicio
    data object CerrarTurno : DialogoInicio
    /** 0.21.0 (C6). */
    data object SolicitarCierre : DialogoInicio
}

sealed interface EventoInicio {
    data class IrA(val route: Route) : EventoInicio
    data class Mensaje(val texto: String) : EventoInicio
    /** Turno recién cerrado: aviso con acción "Ver" que abre su registro. */
    data class TurnoCerrado(val turnoId: Long, val texto: String) : EventoInicio
}

/**
 * Inicio. Las partes "vivas" (banner, alertas, pago, precios, turno) son flujos de Room/DataStore.
 * Los gráficos se recalculan al cambiar el período, el turno o el inventario (una venta descuenta stock) y
 * al volver a la pantalla ([refrescar]). Mientras recarga se conservan los datos previos (sin parpadeo).
 */
@HiltViewModel
class InicioViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val licenciaRepo: LicenciaRepository,
    observarAlertas: ObservarAlertas,
    private val perfilRepo: PerfilRepository,
    private val preciosRepo: PreciosRepository,
    private val turnosRepo: TurnoRepository,
    private val productosRepo: ProductoRepository,
    private val resolverPeriodo: ResolverPeriodo,
    private val obtenerGraficos: ObtenerGraficosPeriodo,
    private val obtenerResumen: ObtenerResumenGeneral,
    private val abrirTurno: AbrirTurno,
    private val cerrarTurno: CerrarTurno,
    private val clock: Clock,
    private val secundaria: SecundariaRepository,
    private val estadoPantallas: cu.shalev.domain.repository.EstadoPantallasRepository,
) : ViewModel() {

    /** Zona para agrupar por hora/día; sustituible en tests. */
    internal var zona: ZoneId = ZoneId.systemDefault()

    private val opcion: StateFlow<OpcionPeriodo> = saved.getStateFlow(KEY_PERIODO, OpcionPeriodo.TURNO.name)
        .map { n -> OpcionPeriodo.entries.firstOrNull { it.name == n } ?: OpcionPeriodo.TURNO }
        .stateIn(viewModelScope, SharingStarted.Eagerly, OpcionPeriodo.TURNO)
    private val refresco = MutableStateFlow(0)
    private val graficos = MutableStateFlow<EstadoCarga<GraficosPeriodo>>(EstadoCarga.Idle)
    private val resumen = MutableStateFlow<EstadoCarga<ResumenGeneral>>(EstadoCarga.Idle)
    private val dialogo = MutableStateFlow<DialogoInicio?>(null)
    private val cambiandoTurno = MutableStateFlow(false)
    private val turno = turnosRepo.observarActivo().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val eventosCh = Channel<EventoInicio>(Channel.BUFFERED)
    val eventos: Flow<EventoInicio> = eventosCh.receiveAsFlow()

    private data class Vivo(
        val banner: String?, val tono: BannerTone, val alertas: List<AlertaUi>, val pago: PagoResumen, val activos: Int, val total: Int,
        val arqueo: cu.shalev.domain.model.Arqueo?,
    )

    private val vivo: Flow<Vivo> = combine(
        licenciaRepo.snapshot, observarAlertas(), perfilRepo.perfil, preciosRepo.observarPreajustes(),
        turnosRepo.observarArqueoActivo().catch { emit(null) },
    ) { lic, alertas, perfil, preajustes, arqueo ->
        Vivo(
            bannerInicio(lic, clock.now(), zona), tonoBanner(lic, clock.now()), alertasVisibles(alertas), perfil.pagoResumen(),
            preajustes.count { it.activo }, preajustes.size, arqueo,
        )
    }

    private data class Panel(
        val opcion: OpcionPeriodo, val graficos: EstadoCarga<GraficosPeriodo>, val resumen: EstadoCarga<ResumenGeneral>,
        val dialogo: DialogoInicio?, val cambiando: Boolean,
    )

    private val panel: Flow<Panel> = combine(opcion, graficos, resumen, dialogo, cambiandoTurno) { o, g, r, d, c -> Panel(o, g, r, d, c) }

    /** 0.25.1 (D): «Ahora no» en el conteo pedido por el encargado. */
    private val conteoPospuesto = MutableStateFlow(false)
    private var finPospuesto: kotlinx.coroutines.Job? = null

    val state: StateFlow<InicioUiState> = combine(vivo, panel, turno, secundaria.estado, conteoPospuesto) { v, p, t, s, pospuesto ->
        InicioUiState(
            banner = v.banner, bannerTono = v.tono, alertas = v.alertas, pago = v.pago, preajustesActivos = v.activos, preajustesTotal = v.total,
            turno = t, cambiandoTurno = p.cambiando, opcion = p.opcion, graficos = p.graficos, resumen = p.resumen, dialogo = p.dialogo,
            cierrePedido = s.cierrePendiente, avisoCierre = s.turnoCerradoPorPrincipal,
            esSecundaria = s.vinculada, cierreSolicitado = s.cierreSolicitado, cierreRechazado = s.cierreRechazado,
            arqueo = v.arqueo, conteoPospuesto = pospuesto,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InicioUiState())

    init {
        // Permanencia del período al cerrar la app: si el proceso no traía elección (arranque en frío), se
        // restaura la última guardada; si la traía (muerte del proceso), manda la de esta sesión.
        if (saved.get<String>(KEY_PERIODO) == null) {
            viewModelScope.launch {
                val guardado = estadoPantallas.observar(KEY_PERIODO).first()
                if (guardado != null && saved.get<String>(KEY_PERIODO) == null) saved[KEY_PERIODO] = guardado
            }
        }
        val inventario = obtenerResumen.cambios()
        val turnoId = turno.map { it?.id to it?.abierto }.distinctUntilChanged()
        viewModelScope.launch {
            combine(opcion, turnoId, inventario, refresco) { o, _, _, _ -> o }.collectLatest { cargarGraficos(it) }
        }
        viewModelScope.launch {
            combine(inventario, turnoId, refresco) { _, _, _ -> Unit }.collectLatest { cargarResumen() }
        }
    }

    // ---------------- Acciones ----------------

    fun elegirPeriodo(o: OpcionPeriodo) {
        saved[KEY_PERIODO] = o.name
        viewModelScope.launch { estadoPantallas.guardar(KEY_PERIODO, o.name) } // sobrevive al cierre de la app
    }

    /** Al volver a Inicio (ON_RESUME) o tras "Reintentar". */
    fun refrescar() { refresco.value++; contarAhora() }

    /** 0.25.1 (D): oculta el conteo hasta volver a Inicio o durante [POSPONER_CONTEO]. No cierra nada. */
    fun posponerConteo() {
        conteoPospuesto.value = true
        finPospuesto?.cancel()
        finPospuesto = viewModelScope.launch { kotlinx.coroutines.delay(POSPONER_CONTEO); conteoPospuesto.value = false }
    }

    /** «Contar» en la tarjeta del cierre pedido (o al volver a Inicio): el diálogo vuelve a salir. */
    fun contarAhora() {
        finPospuesto?.cancel(); finPospuesto = null
        conteoPospuesto.value = false
    }

    fun cambiarTurno(abrir: Boolean) {
        if (cambiandoTurno.value) return
        if (!abrir) {
            val s = state.value
            dialogo.value = when {
                !s.esSecundaria -> DialogoInicio.CerrarTurno
                // 0.21.0 (C6): el empleado solo lo solicita (una vez).
                s.cierreSolicitado || s.cierrePedido -> { viewModelScope.launch { emitir(EventoInicio.Mensaje(TextosInicio.CIERRE_SOLICITADO)) }; null }
                else -> DialogoInicio.SolicitarCierre
            }
            return
        }
        // 0.25.0 (§5.2): primero el fondo de caja (obligatorio, puede ser 0).
        dialogo.value = DialogoInicio.AbrirTurno
    }

    /** 0.25.0: confirma el diálogo de apertura con el [fondo] escrito. */
    fun abrirTurnoCon(fondo: Cup) {
        if (cambiandoTurno.value) return
        dialogo.value = null
        viewModelScope.launch {
            cambiandoTurno.value = true
            when (val r = abrirTurno(fondo)) {
                is AppResult.Ok -> emitir(EventoInicio.Mensaje("Turno abierto. Ya puedes vender."))
                is AppResult.Err -> if (r.error != AppError.TurnoYaAbierto) emitir(EventoInicio.Mensaje(mensaje(r.error)))
            }
            cambiandoTurno.value = false
        }
    }

    /** 0.25.0: [contado] = efectivo contado (obligatorio; «Cuadra» lo rellena con el esperado). */
    fun confirmarCierreTurno(contado: Cup) {
        if (cambiandoTurno.value) return // una apertura/cierre ya está en curso: no se duplica
        dialogo.value = null
        cambiandoTurno.value = true // síncrono: bloquea otra pulsación antes de lanzar el trabajo
        viewModelScope.launch {
            when (val r = cerrarTurno(contado)) {
                is AppResult.Ok -> {
                    val res = r.value.resumen
                    emitir(
                        EventoInicio.TurnoCerrado(
                            r.value.id,
                            if (res == null) "Turno cerrado."
                            else "Turno cerrado: ${res.numVentas} ${if (res.numVentas == 1) "venta" else "ventas"}, ${Money.format(res.total)}.",
                        ),
                    )
                }
                is AppResult.Err -> if (r.error != AppError.TurnoCerrado) emitir(EventoInicio.Mensaje(mensaje(r.error)))
            }
            cambiandoTurno.value = false
        }
    }

    /** 0.21.0 (C6): el empleado confirma «Solicitar cierre». */
    fun confirmarSolicitudCierre(contado: Cup) {
        if (cambiandoTurno.value) return // envío en curso: la solicitud no se manda dos veces
        dialogo.value = null
        cambiandoTurno.value = true
        viewModelScope.launch {
            try {
                when (val r = secundaria.solicitarCierre(contado)) {
                    is AppResult.Ok -> emitir(EventoInicio.Mensaje(TextosInicio.SOLICITUD_ENVIADA))
                    is AppResult.Err -> emitir(EventoInicio.Mensaje(mensaje(r.error)))
                }
            } finally {
                cambiandoTurno.value = false
            }
        }
    }

    /** 0.25.0 (§5.3): conteo pedido por el cierre del encargado; con él, el cierre pendiente se aplica. */
    fun declararContado(contado: Cup) {
        if (cambiandoTurno.value) return
        cambiandoTurno.value = true
        viewModelScope.launch {
            try {
                val r = try { secundaria.solicitarCierre(contado) } catch (e: CancellationException) { throw e } catch (e: Exception) { AppResult.Err(AppError.Desconocido()) }
                if (r is AppResult.Err) emitir(EventoInicio.Mensaje(mensaje(r.error)))
            } finally {
                cambiandoTurno.value = false
            }
        }
    }

    fun nuevaVenta() { if (state.value.puedeEmpezarVenta) dialogo.value = DialogoInicio.NuevaVenta }

    /** 0.20.0 (H5): el empleado leyó «Tu turno lo cerró el encargado». */
    fun descartarAvisoCierre() { viewModelScope.launch { secundaria.descartarAvisoCierre() } }

    fun cerrarDialogo() { dialogo.value = null }

    /**
     * Opción del diálogo "Nueva venta". Prompt 7: NO se abre el turno implícitamente. Se navega a Venta, que
     * con el turno cerrado muestra el bloqueo con mensaje claro y el botón explícito "Abrir turno".
     */
    fun elegirVenta(tipo: TipoVenta) {
        dialogo.value = null
        viewModelScope.launch { emitir(EventoInicio.IrA(Route.Venta(tipo.name))) }
    }

    // ---------------- Carga ----------------

    private suspend fun cargarGraficos(o: OpcionPeriodo) {
        if (!graficos.value.tieneDatos) graficos.value = EstadoCarga.Cargando
        graficos.value = cargar {
            val periodo = resolverPeriodo(o, zona)
            when (val r = obtenerGraficos(periodo, pedidoTurno = o == OpcionPeriodo.TURNO, zone = zona)) {
                is AppResult.Ok -> if (r.value.vacio) EstadoCarga.Vacio(r.value) else EstadoCarga.Exito(r.value)
                is AppResult.Err -> EstadoCarga.Error(TextosInicio.ERROR_CARGA)
            }
        }
    }

    private suspend fun cargarResumen() {
        if (!resumen.value.tieneDatos) resumen.value = EstadoCarga.Cargando
        resumen.value = cargar {
            val r = obtenerResumen(zona)
            if (r.vacio) EstadoCarga.Vacio(r) else EstadoCarga.Exito(r)
        }
    }

    /** Ningún fallo de E/S llega a la UI como excepción ni con detalles internos. */
    private suspend fun <T> cargar(bloque: suspend () -> EstadoCarga<T>): EstadoCarga<T> = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        EstadoCarga.Error(TextosInicio.ERROR_CARGA)
    }

    private fun emitir(e: EventoInicio) { eventosCh.trySend(e) }

    companion object {
        const val KEY_PERIODO = "inicio.periodo"
        /** 0.25.1 (D): tiempo que «Ahora no» oculta el conteo. */
        val POSPONER_CONTEO: kotlin.time.Duration = kotlin.time.Duration.parse("15m")

        fun mensaje(e: AppError): String = when (e) {
            AppError.TurnoYaAbierto -> "Ya hay un turno abierto."
            AppError.TurnoCerrado -> "El turno ya estaba cerrado."
            AppError.LicenciaBloqueada -> "La licencia no permite esta acción."
            else -> mensajeSync(e) ?: "No se pudo completar la acción. Inténtalo de nuevo."
        }
    }
}
