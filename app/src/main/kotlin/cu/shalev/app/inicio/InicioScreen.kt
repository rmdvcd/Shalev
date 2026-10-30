package cu.shalev.app.inicio

import cu.shalev.designsystem.theme.ShalevTextos
import cu.shalev.designsystem.component.BannerTone
import cu.shalev.app.notificacion.rememberPedirPermisoAviso
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.navigation.Route
import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
import cu.shalev.core.money.Percent
import cu.shalev.core.time.Dates
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.ChartSeries
import cu.shalev.designsystem.component.ShalevAccordionCard
import cu.shalev.designsystem.component.ShalevAreaChart
import cu.shalev.designsystem.component.ShalevBarChart
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevComboBox
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevDonutChart
import cu.shalev.designsystem.component.ShalevFab
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevLoading
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.ShalevStatusBanner
import cu.shalev.designsystem.component.ShalevTextoAjustable
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.component.shalevAnimateItem
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.AlertTone
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.designsystem.theme.of
import cu.shalev.designsystem.token.ColorTokens
import cu.shalev.designsystem.token.ShalevMotion
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.GraficosPeriodo
import cu.shalev.domain.model.OpcionPeriodo
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.ResumenGeneral
import cu.shalev.domain.model.TopPersona
import cu.shalev.domain.model.TopItem
import java.time.ZoneId
import kotlinx.coroutines.launch

object InicioTags {
    const val LISTA = "inicio_lista"
    const val BANNER = "inicio_banner"
    const val TURNO = "inicio_turno"
    const val CIERRE = "inicio_cierre_remoto"
    const val NUEVA_VENTA = "inicio_nueva_venta"
    const val SOLICITUD = "inicio_solicitud_cierre"
    const val SOLICITUDES = "inicio_solicitudes_cierre"
    const val APERTURAS = "inicio_aperturas"
    const val DESACTUALIZADAS = "inicio_desactualizadas"
    const val PERIODO = "inicio_periodo"
    const val PAGO = "inicio_pago"
    const val PRECIOS = "inicio_precios"
    const val VENTAS = "inicio_ventas"
    const val GANANCIA = "inicio_ganancia"
    const val INVENTARIO = "inicio_inventario"
    const val METODOS = "inicio_metodos"
    const val REINTENTAR = "inicio_reintentar"
    const val DIALOGO_VENTA = "inicio_dialogo_venta"
    const val RESPALDO = "inicio_respaldo"
    const val CAJA = "inicio_caja"
    const val ACTUALIZACION = "inicio_actualizacion"
    fun alerta(t: cu.shalev.domain.model.TipoAlerta) = "inicio_alerta_${t.name}"
    fun periodo(o: OpcionPeriodo) = "inicio_periodo_${o.name}"
    fun top(t: TipoTop) = "inicio_top_${t.name}"
    fun tipoVenta(t: TipoVenta) = "inicio_tipo_venta_${t.name}"
}

/**
 * Qué acordeones de Inicio están abiertos. Vive en [InicioContent] con `remember` (no `rememberSaveable`) y se crea
 * fuera del `LazyColumn`: el estado sobrevive al desplazamiento, pero al cambiar de ventana la pantalla sale de la
 * composición y todo vuelve a estar cerrado. [abiertosAlInicio] solo lo usan las capturas.
 */
@Stable
class AcordeonesInicio(private val abiertosAlInicio: Boolean = false) {
    private val estado = mutableStateMapOf<String, Boolean>()
    fun abierto(clave: String): Boolean = estado[clave] ?: abiertosAlInicio
    fun alternar(clave: String) { estado[clave] = !abierto(clave) }
}

class AccionesInicio(
    val onNavigate: (Route) -> Unit,
    /**
     * 0.31.0: contar una alerta de inventario abre la pestaña Inventario con ese filtro. No es una ruta: el filtro
     * viaja en el estado del destino (ver `MainScaffold.abrirAlertaInventario`).
     */
    val onVerAlerta: (TipoAlerta) -> Unit = {},
    val onTurno: (Boolean) -> Unit,
    val onNuevaVenta: () -> Unit,
    val onElegirVenta: (TipoVenta) -> Unit,
    val onConfirmarSolicitudCierre: (Cup) -> Unit = {},
    val onConfirmarCierre: (Cup) -> Unit,
    val onCerrarDialogo: () -> Unit,
    val onPeriodo: (OpcionPeriodo) -> Unit,
    val onPago: () -> Unit,
    val onReintentar: () -> Unit,
    /** 0.20.0 (H5). */
    val onDescartarAvisoCierre: () -> Unit = {},
    /** 0.25.0 (§5): apertura con fondo, entradas/salidas de efectivo y conteo del cierre pedido. */
    val onAbrirTurno: (Cup) -> Unit = {},
    val onMovimientoCaja: () -> Unit = {},
    val onDeclararContado: (Cup) -> Unit = {},
    /** 0.25.1 (D): «Ahora no» en el conteo y «Contar» en la tarjeta del cierre pedido. */
    val onPosponerConteo: () -> Unit = {},
    val onContar: () -> Unit = {},
    /** 0.25.0 (§6): tarjeta «Versión X disponible». */
    val onActualizar: () -> Unit = {},
    val onDescartarActualizacion: () -> Unit = {},
    val onCancelarDescarga: () -> Unit = {},
)

@Composable
fun InicioScreen(
    onNavigate: (Route) -> Unit,
    /** 0.31.0: alerta de inventario pulsada (la pestaña Inventario se abre filtrada). */
    onVerAlerta: (TipoAlerta) -> Unit = {},
    /** Aviso al volver de otra pantalla (p. ej. «Venta registrada: …»). */
    mensajeInicial: String? = null,
    onMensajeMostrado: () -> Unit = {},
    viewModel: InicioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(mensajeInicial) {
        if (mensajeInicial != null) { onMensajeMostrado(); snackbar.showSnackbar(mensajeInicial) }
    }
    val scope = rememberCoroutineScope()
    val pedirPermisoAviso = rememberPedirPermisoAviso()

    // Al volver (p. ej. tras una venta) se recalculan los gráficos. Nada se lee en segundo plano.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refrescar() }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoInicio.IrA -> onNavigate(e.route)
                is EventoInicio.Mensaje -> snackbar.showSnackbar(e.texto)
                is EventoInicio.TurnoCerrado -> scope.launch {
                    val r = snackbar.showSnackbar(e.texto, actionLabel = "Ver", withDismissAction = true, duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) onNavigate(Route.TurnoDetalle(e.turnoId))
                }
            }
        }
    }

    val permisos = cu.shalev.app.common.LocalPermisosApp.current
    // 0.21.0 (C6): en la principal, empleados que piden cerrar su turno (aviso que abre Apps vinculadas).
    val solicitudesVm: SolicitudesCierreViewModel = hiltViewModel()
    val solicitudes by solicitudesVm.pendientes.collectAsStateWithLifecycle()
    // 0.25.0 (§4, §5): caja y recordatorio de respaldo en un ViewModel aparte.
    val conteoVm: cu.shalev.app.caja.CierreConteoViewModel = hiltViewModel()
    val cajaVm: cu.shalev.app.caja.CajaViewModel = hiltViewModel()
    val caja by cajaVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(cajaVm) { cajaVm.eventos.collect { snackbar.showSnackbar(it) } }
    val actVm: cu.shalev.app.actualizacion.ActualizacionViewModel = hiltViewModel()
    val actualizacion by actVm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(actVm) { actVm.eventos.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(state.dialogo) { if (state.dialogo == DialogoInicio.AbrirTurno) cajaVm.cargarSugerido() }
    if (caja.dialogoMovimiento) cu.shalev.app.caja.DialogoMovimientoCaja(
        trabajando = caja.trabajando,
        onConfirmar = cajaVm::registrarMovimiento,
        onDismiss = cajaVm::cerrarMovimiento,
        efectivoDisponible = caja.arqueo?.esperado,
    )
    InicioContent(
        state = state,
        acciones = AccionesInicio(
            onNavigate = onNavigate,
            onVerAlerta = onVerAlerta,
            onTurno = { abrir -> if (abrir) pedirPermisoAviso(); viewModel.cambiarTurno(abrir) },
            // 0.21.0 (C12): con un solo tipo de venta permitido se va directo, sin el diálogo.
            onNuevaVenta = { tiposVenta(permisos).singleOrNull()?.let(viewModel::elegirVenta) ?: viewModel.nuevaVenta() },
            onElegirVenta = viewModel::elegirVenta,
            onConfirmarCierre = viewModel::confirmarCierreTurno,
            onCerrarDialogo = viewModel::cerrarDialogo,
            onPeriodo = viewModel::elegirPeriodo,
            onPago = { onNavigate(Route.PagoElectronico) }, // P28: la misma pantalla que en Ajustes
            onReintentar = viewModel::refrescar,
            onDescartarAvisoCierre = viewModel::descartarAvisoCierre,
            onConfirmarSolicitudCierre = viewModel::confirmarSolicitudCierre,
            onAbrirTurno = viewModel::abrirTurnoCon,
            onMovimientoCaja = cajaVm::abrirMovimiento,
            onDeclararContado = viewModel::declararContado,
            onPosponerConteo = viewModel::posponerConteo,
            onContar = viewModel::contarAhora,
            onActualizar = actVm::actualizar,
            onDescartarActualizacion = actVm::descartar,
            onCancelarDescarga = actVm::cancelar,
        ),
        actualizacion = actualizacion,
        solicitudesCierre = solicitudes,
        caja = caja,
        snackbar = snackbar,
        conteoVm = conteoVm,
    )
}

/** Contenido sin estado (testeable sin Hilt). */
@Composable
fun InicioContent(
    state: InicioUiState,
    acciones: AccionesInicio,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    zona: ZoneId = ZoneId.systemDefault(),
    solicitudesCierre: Int = 0,
    caja: cu.shalev.app.caja.CajaUiState = cu.shalev.app.caja.CajaUiState(),
    actualizacion: cu.shalev.app.actualizacion.EstadoActualizacion = cu.shalev.app.actualizacion.EstadoActualizacion(),
    /** Solo para capturas: abre todos los acordeones (en la app empiezan siempre cerrados). */
    acordeonesAbiertos: Boolean = false,
    conteoVm: cu.shalev.app.caja.CierreConteoViewModel? = null,
) {
    val permisos = cu.shalev.app.common.LocalPermisosApp.current
    val acordeones = remember { AcordeonesInicio(acordeonesAbiertos) }
    // 1.0.2: ayuda de la pestaña (botón «?» de la esquina superior izquierda).
    var ayudaPestana by remember { mutableStateOf(false) }
    // 0.27.0 (T2): con letra grande caben menos alertas por fila (ancho de pantalla menos el relleno lateral).
    val maxAlertas = alertasPorFila(
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 2 * ShalevSpacing.md.value,
        androidx.compose.ui.platform.LocalDensity.current.fontScale,
    )
    Scaffold(
        topBar = {
            ShalevTopBar(
                title = "Shalev", marca = true,
                onAyuda = { ayudaPestana = true }, ayudaDescripcion = "Ayuda de Inicio",
            )
        },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        // Capa propia en vez del hueco del Scaffold: con una fila a todo el ancho dentro del hueco,
        // el Scaffold la medía más ancha que la pantalla y los botones quedaban medio fuera.
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                // 0.31.0 (P1): el turno, el selector de período y el banner de licencia NO se desplazan: se quedan
                // fijos arriba, uno debajo de otro, mientras el resto de Inicio hace scroll.
                BloqueFijo(state, acciones, permisos, zona)
                val estadoLista = remember { LazyListState() }
                // 0.31.0 (P1): Inicio se abre siempre por arriba: la lista no guarda su posición (Android la
                // restauraba y aparecía a media lista) y, como los datos llegan después del primer dibujo, al
                // insertarse las alertas por encima de lo que ya se veía Compose ancla el primer elemento visible
                // y la desplazaba sola. En la primera carga vuelve al principio; después manda el usuario.
                var primeraCarga by remember { mutableStateOf(true) }
                LaunchedEffect(state.alertas, state.resumen) {
                    if (primeraCarga && (state.alertas.isNotEmpty() || state.resumen !is EstadoCarga.Idle)) {
                        primeraCarga = false
                        estadoLista.scrollToItem(0)
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize().testTag(InicioTags.LISTA),
                    state = estadoLista,
                    contentPadding = PaddingValues(
                        start = ShalevSpacing.md, end = ShalevSpacing.md,
                        // 0.31.0 (P1): menos hueco entre «Shalev» y lo primero que se ve.
                        top = ShalevSpacing.xs,
                        // Hueco para que los botones flotantes no tapen la última fila.
                        bottom = ShalevSpacing.md + ShalevSize.fabClearance,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
                ) {
            // 0.20.0 (H5): el encargado cerró (o pidió cerrar) el turno desde la app principal.
            if (state.cierrePedido || state.avisoCierre) {
                item(key = "cierre") {
                    ShalevStatusBanner(
                        text = if (state.cierrePedido) TextosInicio.CIERRE_PEDIDO else TextosInicio.CIERRE_HECHO,
                        tone = BannerTone.Aviso,
                        // 0.25.1 (D): con el conteo pospuesto, la tarjeta ofrece «Contar» (también al tocarla).
                        detail = if (state.cierrePedido && state.conteoPospuesto && state.pideConteo) TextosInicio.CONTAR_PENDIENTE else null,
                        actionIcon = when {
                            state.cierrePedido && state.conteoPospuesto && state.pideConteo -> ShalevIcons.Efectivo
                            state.cierrePedido -> null
                            else -> ShalevIcons.Cancelar
                        },
                        actionDescription = when {
                            state.cierrePedido && state.conteoPospuesto && state.pideConteo -> TextosInicio.CONTAR
                            state.cierrePedido -> null
                            else -> "Cerrar aviso"
                        },
                        onAction = when {
                            state.cierrePedido && state.conteoPospuesto && state.pideConteo -> acciones.onContar
                            state.cierrePedido -> null
                            else -> acciones.onDescartarAvisoCierre
                        },
                        onClick = if (state.cierrePedido && state.conteoPospuesto && state.pideConteo) acciones.onContar else null,
                        modifier = Modifier.testTag(InicioTags.CIERRE).then(shalevAnimateItem()),
                    )
                }
            }
            // 0.21.0 (C6): solicitud de cierre del empleado (secundaria) y su respuesta; en la principal, las pendientes.
            if (state.cierreSolicitado || state.cierreRechazado) {
                item(key = "solicitud") {
                    ShalevStatusBanner(
                        text = if (state.cierreRechazado) TextosInicio.CIERRE_RECHAZADO else TextosInicio.CIERRE_SOLICITADO,
                        tone = BannerTone.Aviso,
                        actionIcon = if (state.cierreRechazado) ShalevIcons.Cancelar else null,
                        actionDescription = if (state.cierreRechazado) "Cerrar aviso" else null,
                        onAction = if (state.cierreRechazado) acciones.onDescartarAvisoCierre else null,
                        modifier = Modifier.testTag(InicioTags.SOLICITUD).then(shalevAnimateItem()),
                    )
                }
            }
            if (solicitudesCierre > 0 && !permisos.esSecundaria) {
                item(key = "solicitudes") {
                    ShalevStatusBanner(
                        text = TextosInicio.solicitudesCierre(solicitudesCierre),
                        tone = BannerTone.Aviso,
                        onClick = { acciones.onNavigate(Route.Vinculacion) },
                        modifier = Modifier.testTag(InicioTags.SOLICITUDES).then(shalevAnimateItem()),
                    )
                }
            }
            // 0.25.0 (§4): recordatorio mensual de respaldo (solo principal); al tocarlo se abre Respaldo.
            caja.avisoRespaldo?.let { a ->
                item(key = "respaldo") {
                    ShalevStatusBanner(
                        text = TextosInicio.avisoRespaldo(a.dias),
                        tone = BannerTone.Info,
                        onClick = { acciones.onNavigate(Route.Respaldo) },
                        modifier = Modifier.testTag(InicioTags.RESPALDO).then(shalevAnimateItem()),
                    )
                }
            }
            if (actualizacion.tarjetaVisible) item(key = "actualizacion") { TarjetaActualizacion(actualizacion, acciones) }
            // 0.21.0 (C12): sin Ventas ni Inventario no hay alertas de existencias.
            if (permisos.verInventario) alertas(state.alertas, acciones, maxAlertas)
            // 0.21.0 (C9): en la secundaria, solo turno, Nueva venta y alertas; los accesos y los gráficos son del dueño.
            if (!permisos.esSecundaria) item(key = "accesos") {
                val pago: @Composable (Modifier) -> Unit = { m ->
                    // Teléfono y cuenta/tarjeta en seminegrita (solo los números, no «Tel.» ni «Cuenta»).
                    Acceso(
                        "Pago electrónico",
                        ShalevTextos.resaltar(state.pago.texto, state.pago.telefono.orEmpty(), state.pago.cuenta.orEmpty()),
                        ShalevIcons.PagoElectronico, acciones.onPago, m.testTag(InicioTags.PAGO),
                    )
                }
                val precios: @Composable (Modifier) -> Unit = { m ->
                    Acceso(
                        "Precios", androidx.compose.ui.text.AnnotatedString(textoPrecios(state.preajustesActivos, state.preajustesTotal)), ShalevIcons.Precios,
                        { acciones.onNavigate(Route.Precios) }, m.testTag(InicioTags.PRECIOS),
                    )
                }
                // 0.27.0 (T2): con letra muy grande (1 alerta por fila) las dos tarjetas van una debajo de otra
                // para no partir «electrónico».
                if (maxAlertas < 2) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md)) {
                        pago(Modifier.fillMaxWidth())
                        precios(Modifier.fillMaxWidth())
                    }
                } else {
                    // 1.0.2: cada tarjeta se ajusta a su propio texto (sin estirarse a la altura de la otra).
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md)) {
                        pago(Modifier.weight(1f))
                        precios(Modifier.weight(1f))
                    }
                }
            }
            if (!permisos.esSecundaria) {
                graficosPeriodo(state.graficos, state.opcion, zona, acciones.onReintentar, acordeones)
                resumenGeneral(state.resumen, acciones.onReintentar, acordeones)
            }
            }
            }
            // «Nueva venta» (izquierda) y «Movimiento de caja» (derecha): flotantes en las esquinas
            // inferiores y solo visibles con el turno abierto (sin turno no se vende).
            if (state.turnoAbierto && permisos.vender) {
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ShalevFab(
                        ShalevIcons.Venta, "Nueva venta", onClick = acciones.onNuevaVenta, etiqueta = "Vender",
                        modifier = Modifier.testTag(InicioTags.NUEVA_VENTA),
                    )
                    ShalevFab(
                        ShalevIcons.Efectivo, cu.shalev.app.caja.TextosCaja.MOVIMIENTO_TITULO, onClick = acciones.onMovimientoCaja,
                        etiqueta = "Caja", modifier = Modifier.testTag(InicioTags.CAJA),
                    )
                }
            }
        }
    }

    when (state.dialogo) {
        DialogoInicio.NuevaVenta -> NuevaVentaDialog(state.turnoAbierto, acciones, tiposVenta(permisos))
        DialogoInicio.AbrirTurno -> cu.shalev.app.caja.DialogoFondo(
            sugerido = caja.sugerido, trabajando = state.cambiandoTurno,
            onConfirmar = acciones.onAbrirTurno, onDismiss = acciones.onCerrarDialogo,
        )
        DialogoInicio.CerrarTurno -> cu.shalev.app.caja.DialogoCierreCompleto(
            viewModel = conteoVm,
            titulo = TextosInicio.CERRAR_TURNO_TITULO,
            texto = TextosInicio.CERRAR_TURNO_TEXTO,
            confirmar = "Cerrar turno",
            arqueo = state.arqueo,
            trabajando = state.cambiandoTurno,
            onConfirmar = acciones.onConfirmarCierre,
            onDismiss = acciones.onCerrarDialogo,
        )
        DialogoInicio.SolicitarCierre -> cu.shalev.app.caja.DialogoCierreCompleto(
            viewModel = conteoVm,
            titulo = TextosInicio.SOLICITAR_CIERRE_TITULO,
            texto = TextosInicio.SOLICITAR_CIERRE_TEXTO,
            confirmar = "Solicitar cierre",
            arqueo = state.arqueo,
            trabajando = false,
            onConfirmar = acciones.onConfirmarSolicitudCierre,
            onDismiss = acciones.onCerrarDialogo,
        )
        null -> if (state.debeContar) cu.shalev.app.caja.DialogoCierreCompleto(
            viewModel = conteoVm,
            titulo = TextosInicio.CONTAR_TITULO,
            texto = TextosInicio.CONTAR_TEXTO,
            confirmar = "Enviar conteo",
            arqueo = state.arqueo,
            trabajando = false,
            onConfirmar = acciones.onDeclararContado,
            // 0.25.1 (D): «Ahora no» lo oculta; sin conteo el cierre no se aplica y vuelve al regresar a Inicio o a los 15 min.
            onDismiss = acciones.onPosponerConteo,
            dismissDescription = TextosInicio.AHORA_NO,
        )
    }

    if (ayudaPestana) cu.shalev.app.ayuda.DialogoAyudaPestana(
        pestana = cu.shalev.app.ayuda.PestanaAyuda.INICIO,
        modulos = permisos.modulos,
        onDismiss = { ayudaPestana = false },
        onVerManual = {
            ayudaPestana = false
            acciones.onNavigate(Route.Ayuda)
        },
    )
}

// ---------------- Actualización (0.25.0, §6) ----------------

@Composable
private fun TarjetaActualizacion(a: cu.shalev.app.actualizacion.EstadoActualizacion, acciones: AccionesInicio) {
    val T = cu.shalev.app.actualizacion.TextosActualizacion
    ShalevCard(tone = CardTone.Tonal, modifier = Modifier.testTag(InicioTags.ACTUALIZACION)) {
        Text(
            if (a.desdePrincipal != null) T.desdePrincipal(a.version.orEmpty()) else T.disponible(a.version.orEmpty(), a.bytes),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        // 0.26.0 (§6): fecha límite en seminegrita (dato).
        a.limite?.let { Text(T.obligatoriaDesde(it), style = ShalevTextos.datoEn(MaterialTheme.typography.bodyMedium)) }
        a.disponible?.notas?.takeIf { a.desdePrincipal == null }?.let(T::notasVisibles)?.takeIf { it.isNotBlank() }?.let { ShalevSecondaryText(it.take(400), maxLines = 4, textAlign = TextAlign.Start) }
        if (a.progreso != null) {
            ShalevSecondaryText(T.DESCARGANDO + " ${(a.progreso * 100).toInt()} %", textAlign = TextAlign.Start)
            cu.shalev.designsystem.component.ShalevLinearProgress(modifier = Modifier.fillMaxWidth(), progress = a.progreso)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = ShalevSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.lg, Alignment.CenterHorizontally),
        ) {
            if (a.progreso == null) {
                ShalevPrimaryButton(T.ACTUALIZAR, onClick = acciones.onActualizar, icon = ShalevIcons.Importar)
                // 0.26.0: «Más tarde» oculta el aviso hasta mañana (también en la secundaria); el plazo no cambia.
                ShalevSecondaryButton(T.DESCARTAR, onClick = acciones.onDescartarActualizacion, icon = ShalevIcons.AhoraNo)
            } else {
                ShalevSecondaryButton(T.CANCELAR, onClick = acciones.onCancelarDescarga, icon = ShalevIcons.Cancelar)
            }
        }
    }
}

// ---------------- Turno y Nueva venta ----------------


/**
 * 0.31.0 (P1): primer contenedor de Inicio y el único que no se desplaza: el turno, debajo el selector de período y,
 * debajo, el banner de licencia (cuando lo hay), los tres a todo el ancho. En una secundaria no hay selector.
 */
@Composable
private fun BloqueFijo(state: InicioUiState, acciones: AccionesInicio, permisos: cu.shalev.domain.model.PermisosApp, zona: ZoneId) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
    ) {
        TurnoContenedor(state, acciones, zona)
        if (!permisos.esSecundaria) SelectorPeriodo(state.opcion, acciones.onPeriodo)
        state.banner?.let { texto ->
            ShalevStatusBanner(
                text = texto,
                tone = state.bannerTono,
                detail = detalleBanner(state.bannerTono),
                onClick = { acciones.onNavigate(Route.Licencia) },
                modifier = Modifier.testTag(InicioTags.BANNER),
            )
        }
    }
}

/**
 * Solo el turno: «Nueva venta» y «Movimiento de caja» son botones flotantes (solo con turno abierto).
 */
@Composable
private fun TurnoContenedor(
    state: InicioUiState,
    acciones: AccionesInicio,
    zona: ZoneId,
) {
    val abierto = state.turnoAbierto
    // 1.0.2: contenedor ajustado a su texto, con relleno vertical ligero.
    ShalevCard(modifier = Modifier.testTag(InicioTags.TURNO), paddingVertical = ShalevSpacing.xs) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(value = abierto, enabled = !state.cambiandoTurno, role = Role.Switch, onValueChange = acciones.onTurno),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            Icon(ShalevIcons.Turno, contentDescription = null, tint = if (abierto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(if (abierto) TextosInicio.TURNO_ABIERTO else TextosInicio.TURNO_CERRADO, style = MaterialTheme.typography.titleMedium)
                ShalevSecondaryText(
                    state.turno?.takeIf { abierto }?.let { t ->
                        // 0.28.0: la hora («Desde las 08:30») va en seminegrita.
                        ShalevTextos.resaltar("Desde las ${Dates.time(t.abiertoEn, zona)}", Dates.time(t.abiertoEn, zona))
                    } ?: ShalevTextos.resaltar("Ábrelo para vender"),
                    textAlign = TextAlign.Start,
                )
                // 0.25.0 (§5.2): efectivo esperado del turno abierto.
                state.arqueo?.takeIf { abierto }?.let { a ->
                    ShalevSecondaryText(
                        ShalevTextos.resaltar(
                            cu.shalev.app.caja.TextosCaja.esperado(a.esperado), Money.format(a.esperado),
                        ),
                        textAlign = TextAlign.Start,
                    )
                }
            }
            Switch(checked = abierto, onCheckedChange = null, enabled = !state.cambiandoTurno)
        }
    }
}

@Composable
private fun NuevaVentaDialog(turnoAbierto: Boolean, acciones: AccionesInicio, tipos: List<TipoVenta>) {
    ShalevDialog(
        title = "Nueva venta",
        onDismiss = acciones.onCerrarDialogo,
        onConfirm = null,
        modifier = Modifier.testTag(InicioTags.DIALOGO_VENTA),
    ) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            if (!turnoAbierto) ShalevSecondaryText(TextosInicio.AVISO_TURNO_CERRADO)
            tipos.forEach { t ->
                ShalevListItem(
                    title = t.etiqueta,
                    subtitle = t.detalle,
                    indicatorColor = null,
                    leading = { Icon(if (t == TipoVenta.VENTA) ShalevIcons.Venta else ShalevIcons.Servicios, contentDescription = null) },
                    onClick = { acciones.onElegirVenta(t) },
                    modifier = Modifier.testTag(InicioTags.tipoVenta(t)),
                )
            }
        }
    }
}

// ---------------- Alertas ----------------

private fun LazyListScope.alertas(alertas: List<AlertaUi>, acciones: AccionesInicio, maxPorFila: Int) {
    if (alertas.isEmpty()) return
    item(key = "alertas_titulo") { Seccion("Alertas de inventario") }
    // 0.27.0 (T14): reparto equitativo (1, 2, 3, 2+2, 3+2, 3+3…); en cada fila, mismo ancho y misma altura. Una fila
    // incompleta queda centrada con el mismo ancho por tarjeta que la fila más llena.
    val reparto = repartoAlertas(alertas.size, maxPorFila)
    val porFila = reparto.firstOrNull() ?: 1
    var desde = 0
    reparto.forEachIndexed { fila, cuantas ->
        val grupo = alertas.subList(desde, desde + cuantas)
        desde += cuantas
        item(key = "alertas_$fila") {
            Row(
                Modifier.fillMaxWidth().then(shalevAnimateItem()),
                horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md, Alignment.CenterHorizontally),
            ) {
                val hueco = (porFila - cuantas) / 2f
                if (hueco > 0f) Spacer(Modifier.weight(hueco))
                grupo.forEach { a ->
                    ContadorAlerta(
                        etiqueta = a.etiqueta, cantidad = a.cantidad, tono = a.tono,
                        onClick = { acciones.onVerAlerta(a.tipo) },
                        modifier = Modifier.weight(1f).testTag(InicioTags.alerta(a.tipo)),
                    )
                }
                if (hueco > 0f) Spacer(Modifier.weight(hueco))
            }
        }
    }
}

/**
 * Contador de alerta: número en el color semántico de la alerta (AA en ambos temas). Contador 0 → oculto
 * (con animación). Pulsar → navega a Inventario filtrado.
 * 0.21.1 (H4): SinExistencia va relleno (fondo error, número y texto onError) para no confundirse con
 * «Stock crítico», que usa el mismo rojo sobre la superficie.
 */
@Composable
private fun ContadorAlerta(
    etiqueta: String,
    cantidad: Int,
    tono: AlertTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible = cantidad > 0, modifier = modifier, enter = ShalevMotion.enterVertical, exit = ShalevMotion.exitVertical) {
        val relleno = tono == AlertTone.SinExistencia
        ShalevCard(
            onClick = onClick,
            tone = if (relleno) CardTone.Error else CardTone.Default,
            paddingVertical = ShalevSpacing.xs,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$etiqueta: $cantidad" },
        ) {
            // 0.27.0 (T14): contenido centrado; la tarjeta mide lo que su contenido y el texto puede ocupar 2 líneas.
            Column(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
                if (relleno) {
                    val sobreError = MaterialTheme.colorScheme.onError
                    Text(cantidad.toString(), style = MaterialTheme.typography.headlineSmall, color = sobreError, textAlign = TextAlign.Center)
                    Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = sobreError, textAlign = TextAlign.Center)
                } else {
                    Text(
                        cantidad.toString(), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                        color = if (tono == AlertTone.Revisar) MaterialTheme.colorScheme.primary else ShalevTheme.colors.of(tono),
                    )
                    ShalevSecondaryText(etiqueta, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * 0.27.0 (T3): medalla del Top 3. El número del puesto (1, 2, 3) dentro de un círculo oro, plata o bronce; el número
 * usa el peso de dato y un color con contraste AA sobre cada medalla. TalkBack lee «Primer puesto»… Sin decoración extra.
 */
@Composable
private fun MedallaPuesto(numero: Int, modifier: Modifier = Modifier) {
    val (fondo, texto) = when (numero) {
        1 -> Color(ColorTokens.MEDALLA_ORO) to Color(ColorTokens.ON_MEDALLA_ORO)
        2 -> Color(ColorTokens.MEDALLA_PLATA) to Color(ColorTokens.ON_MEDALLA_PLATA)
        else -> Color(ColorTokens.MEDALLA_BRONCE) to Color(ColorTokens.ON_MEDALLA_BRONCE)
    }
    val descripcion = when (numero) {
        1 -> "Primer puesto"
        2 -> "Segundo puesto"
        else -> "Tercer puesto"
    }
    Box(
        modifier.size(ShalevSize.medalla).background(fondo, CircleShape).clearAndSetSemantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) {
        Text("$numero", style = MaterialTheme.typography.titleSmall, fontWeight = ShalevTextos.PESO_DATO, color = texto)
    }
}

@Composable
private fun Acceso(titulo: String, detalle: androidx.compose.ui.text.AnnotatedString, icono: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    // 1.0.2: la tarjeta mide lo que su texto, con un relleno vertical ligero.
    ShalevCard(onClick = onClick, tone = CardTone.Tonal, modifier = modifier, paddingVertical = ShalevSpacing.xs) {
        Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        // 0.27.0 (T2): con letra grande el texto pasa a varias líneas y la tarjeta crece.
        Text(titulo, style = MaterialTheme.typography.titleSmall)
        ShalevSecondaryText(detalle)
    }
}

// ---------------- Período ----------------

@Composable
private fun SelectorPeriodo(opcion: OpcionPeriodo, onPeriodo: (OpcionPeriodo) -> Unit) {
    // P24: combobox (una fila, se ve la opción elegida; el resto al abrir).
    ShalevComboBox(
        label = "Período",
        opciones = OpcionPeriodo.entries,
        seleccion = opcion,
        etiqueta = { it.etiqueta() },
        onSeleccion = onPeriodo,
        leadingIcon = ShalevIcons.Fecha,
        tagOpcion = { InicioTags.periodo(it) },
        modifier = Modifier.testTag(InicioTags.PERIODO),
    )
}

// ---------------- Gráficos del período ----------------

private fun LazyListScope.graficosPeriodo(
    estado: EstadoCarga<GraficosPeriodo>, opcion: OpcionPeriodo, zona: ZoneId, onReintentar: () -> Unit, ac: AcordeonesInicio,
) {
    when (estado) {
        EstadoCarga.Idle, EstadoCarga.Cargando -> {
            item(key = "ventas") { CardGrafico("Ventas", InicioTags.VENTAS, ac) { Cargando() } }
            item(key = "ganancia") { CardGrafico("Ganancia Neta", InicioTags.GANANCIA, ac) { Cargando() } }
        }
        is EstadoCarga.Error -> item(key = "graficos_error") { CardError(estado.mensaje, onReintentar) }
        is EstadoCarga.Vacio -> {
            val g = estado.datos
            item(key = "ventas") {
                CardGrafico("Ventas", InicioTags.VENTAS, ac) {
                    g?.let { Subtitulo(descripcionPeriodo(it, opcion, zona)) }
                    Vacio(TextosInicio.SIN_VENTAS)
                }
            }
            item(key = "ganancia") { CardGrafico("Ganancia Neta", InicioTags.GANANCIA, ac) { Vacio(TextosInicio.SIN_VENTAS) } }
        }
        is EstadoCarga.Exito -> {
            val g = estado.datos
            item(key = "ventas") { VentasCard(g, opcion, zona, ac) }
            item(key = "ganancia") { GananciaCard(g, ac) }
        }
    }
}

@Composable
private fun VentasCard(g: GraficosPeriodo, opcion: OpcionPeriodo, zona: ZoneId, ac: AcordeonesInicio) {
    val puntos = g.serie.puntos
    CardGrafico("Ventas", InicioTags.VENTAS, ac, subtitle = resumenVentas(g)) {
        // 0.28.0: la hora o fecha del turno («Turno actual, desde las 08:30») va en seminegrita.
        val t = g.turno
        Subtitulo(
            ShalevTextos.resaltar(
                descripcionPeriodo(g, opcion, zona),
                t?.let { Dates.time(it.abiertoEn, zona) }.orEmpty(),
                t?.let { Dates.dayMonthTime(it.abiertoEn, zona) }.orEmpty(),
                t?.cerradoEn?.let { Dates.time(it, zona) }.orEmpty(),
            ),
        )
        ShalevTextoAjustable(Money.format(g.totalVentas), style = ShalevTextos.datoEn(MaterialTheme.typography.titleLarge), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        ShalevBarChart(
            values = puntos.map { it.ventas.enPesos() },
            labels = puntos.map { etiquetaPunto(it.inicio, g.serie.granularidad, zona) },
            description = descripcionVentas(g, zona),
        )
    }
}

@Composable
private fun GananciaCard(g: GraficosPeriodo, ac: AcordeonesInicio) {
    val colores = ShalevTheme.colors.chart
    val puntos = g.serie.puntos
    CardGrafico("Ganancia Neta", InicioTags.GANANCIA, ac, subtitle = resumenGanancia(g)) {
        // P24/P28: dos columnas del mismo ancho (Costo y Ganancia); el importe se reduce si no cabe, nunca se parte.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            // P28: sin «Venta»: es el mismo total que muestra la tarjeta Ventas justo encima.
            Dato("Costo", Money.format(g.totalCosto), Modifier.weight(1f))
            Dato("Ganancia", Money.format(g.ganancia), Modifier.weight(1f))
        }
        g.margen?.let { Subtitulo("Margen ${Percent.format(it).removePrefix("+")}") }
        ShalevAreaChart(
            series = listOf(
                ChartSeries("Venta", puntos.map { it.ventas.enPesos() }, colores[0]),
                ChartSeries("Costo", puntos.map { it.costo.enPesos() }, colores[1]),
            ),
            labels = emptyList(),
            description = descripcionGanancia(g),
        )
    }
}

// ---------------- Resumen general (sin selector) ----------------

private fun LazyListScope.resumenGeneral(estado: EstadoCarga<ResumenGeneral>, onReintentar: () -> Unit, ac: AcordeonesInicio) {
    when (estado) {
        EstadoCarga.Idle, EstadoCarga.Cargando -> {
            item(key = "inventario") { CardGrafico("Inventario", InicioTags.INVENTARIO, ac) { Cargando() } }
            item(key = "metodos") { CardGrafico("Métodos de pago", InicioTags.METODOS, ac) { Cargando() } }
        }
        is EstadoCarga.Error -> item(key = "resumen_error") { CardError(estado.mensaje, onReintentar) }
        is EstadoCarga.Vacio -> estado.datos?.let { resumenCon(it, ac) } ?: item(key = "inventario") {
            CardGrafico("Inventario", InicioTags.INVENTARIO, ac) { Vacio(TextosInicio.SIN_INVENTARIO) }
        }
        is EstadoCarga.Exito -> resumenCon(estado.datos, ac)
    }
}

private fun LazyListScope.resumenCon(r: ResumenGeneral, ac: AcordeonesInicio) {
    item(key = "inventario") {
        CardGrafico("Inventario", InicioTags.INVENTARIO, ac, subtitle = resumenInventario(r)) {
            if (r.categorias.isEmpty()) Vacio(TextosInicio.SIN_INVENTARIO) else {
                // 0.27.0 (N1): valores en milésimas (productos e insumos, cada uno en su medida) → números sin unidad.
                val slices = porcionesUi(r.categorias) { cantidadInventario(it) }
                Subtitulo(TextosInicio.EXISTENCIAS_POR_CATEGORIA)
                ShalevDonutChart(slices, descripcionDona("inventario por categorías", slices), centerText = cantidadInventario(r.categorias.sumOf { it.valor }))
            }
        }
    }
    item(key = "metodos") {
        CardGrafico("Métodos de pago", InicioTags.METODOS, ac, subtitle = resumenMetodos(r)) {
            Subtitulo("Últimos ${r.dias} días")
            if (r.metodosPago.isEmpty()) Vacio(TextosInicio.SIN_PAGOS) else {
                val slices = porcionesUi(r.metodosPago) { Money.format(Cup(it)) }
                ShalevDonutChart(slices, descripcionDona("métodos de pago", slices))
            }
        }
    }
    top(TipoTop.MAS_VENDIDO, r.top3.masVendidos, r.dias, ac)
    top(TipoTop.LENTO, r.top3.lentoMovimiento, r.dias, ac)
    top(TipoTop.RENTABILIDAD, r.top3.rentabilidad, r.dias, ac)
    // P29: indicadores propios de los servicios (ocultos si no se vendió ninguno en el período).
    top(TipoTop.SERVICIO_TOP, r.servicios.masVendidos, r.dias, ac)
    top(TipoTop.SERVICIO_MENOS, r.servicios.menosVendidos, r.dias, ac)
    topPersonas(TipoTop.EMPLEADO, r.empleados, r.dias, "venta", "ventas", ac)
    topPersonas(TipoTop.CLIENTE, r.clientes, r.dias, "compra", "compras", ac)
}

/** Top 3: oculto si no hay datos (Shalev.txt). */
private fun LazyListScope.top(tipo: TipoTop, items: List<TopItem>, dias: Int, ac: AcordeonesInicio) {
    if (items.isEmpty()) return
    item(key = "top_${tipo.name}") {
        ShalevAccordionCard(
            title = tipo.titulo, subtitle = resumenTop(tipo, items),
            expanded = ac.abierto(InicioTags.top(tipo)), onToggle = { ac.alternar(InicioTags.top(tipo)) },
            modifier = Modifier.testTag(InicioTags.top(tipo)).then(shalevAnimateItem()),
        ) {
            Subtitulo("Últimos $dias días")
            items.forEachIndexed { i, it ->
                ShalevListItem(
                    title = it.nombre,
                    subtitle = subtituloTop(tipo, it),
                    value = valorTop(tipo, it),
                    indicatorColor = null,
                    // 0.27.0 (T3): medalla oro, plata o bronce con el número del puesto.
                    leading = { puestoTop(i)?.let { n -> MedallaPuesto(n) } },
                )
            }
        }
    }
}

/** Top 3 de personas (empleados y clientes): oculto si no hay datos. */
private fun LazyListScope.topPersonas(tipo: TipoTop, items: List<TopPersona>, dias: Int, singular: String, plural: String, ac: AcordeonesInicio) {
    if (items.isEmpty()) return
    item(key = "top_${tipo.name}") {
        ShalevAccordionCard(
            title = tipo.titulo, subtitle = resumenTopPersonas(items),
            expanded = ac.abierto(InicioTags.top(tipo)), onToggle = { ac.alternar(InicioTags.top(tipo)) },
            modifier = Modifier.testTag(InicioTags.top(tipo)).then(shalevAnimateItem()),
        ) {
            Subtitulo("Últimos $dias días")
            items.forEachIndexed { i, it ->
                ShalevListItem(
                    title = it.nombre,
                    subtitle = if (it.operaciones == 1) "1 $singular" else "${it.operaciones} $plural",
                    value = Money.format(it.importe),
                    indicatorColor = null,
                    leading = { puestoTop(i)?.let { n -> MedallaPuesto(n) } },
                )
            }
        }
    }
}

// ---------------- Piezas comunes ----------------

@Composable
private fun CardGrafico(titulo: String, tag: String, ac: AcordeonesInicio, subtitle: String? = null, content: @Composable () -> Unit) {
    // Acordeón cerrado de inicio; la clave es el tag (único por tarjeta).
    ShalevAccordionCard(title = titulo, subtitle = subtitle, expanded = ac.abierto(tag), onToggle = { ac.alternar(tag) }, modifier = Modifier.testTag(tag)) { content() }
}

@Composable
private fun Subtitulo(texto: String) = ShalevSecondaryText(texto, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

@Composable
private fun Subtitulo(texto: androidx.compose.ui.text.AnnotatedString) =
    ShalevSecondaryText(texto, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

@Composable
private fun Vacio(texto: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = ShalevSpacing.lg), contentAlignment = Alignment.Center) {
        ShalevSecondaryText(texto, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Cargando() {
    Box(Modifier.fillMaxWidth().height(ShalevSize.chartHeight), contentAlignment = Alignment.Center) {
        ShalevLoading(forma = cu.shalev.designsystem.component.FormaSkeleton.GRAFICO)
    }
}

@Composable
private fun CardError(mensaje: String, onReintentar: () -> Unit) {
    ShalevCard(tone = CardTone.Tonal) {
        Text(
            mensaje, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
        ShalevSecondaryButton(
            "Reintentar", onClick = onReintentar, icon = ShalevIcons.Reintentar,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag(InicioTags.REINTENTAR),
        )
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ShalevSecondaryText(etiqueta, textAlign = TextAlign.Center)
        ShalevTextoAjustable(valor, style = ShalevTextos.dato, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Seccion(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
}
