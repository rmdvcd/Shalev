package cu.shalev.app.venta

import androidx.compose.material3.Icon
import cu.shalev.designsystem.component.ShalevCheckbox
import cu.shalev.domain.model.nombreCompleto
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.BannerTone
import cu.shalev.designsystem.component.ShalevStatusBanner
import cu.shalev.app.notificacion.rememberPedirPermisoAviso
import cu.shalev.designsystem.component.ShalevTextoAjustable
import cu.shalev.designsystem.component.ShalevBarraAcciones
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.common.SecureWindow
import cu.shalev.app.venta.AccesoCapturaSms
import cu.shalev.app.inicio.TipoVenta
import cu.shalev.app.registros.TextosTurno
import cu.shalev.app.registros.hora
import cu.shalev.core.money.Money
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevChip
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevEmptyState
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevLinearProgress
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevLoading
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.component.rememberShalevHaptics
import cu.shalev.designsystem.component.shalevAnimateItem
import cu.shalev.designsystem.component.shalevContentWidth
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.ShalevTextos
import cu.shalev.designsystem.ilustracion.ShalevIlustracion
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.service.PagoQr
import cu.shalev.domain.usecase.ObservarPermisoVenta.Permiso
import java.time.ZoneId

object VentaTags {
    const val BLOQUEO = "venta_bloqueo"
    const val ABRIR_TURNO = "venta_abrir_turno"
    const val CARRITO = "venta_carrito"
    const val CONTINUAR = "venta_continuar"
    const val COMPROBANTE = "venta_comprobante"
    const val CONFIRMAR = "venta_confirmar"
    const val CANCELAR = "venta_cancelar"
    const val QR = "venta_qr"
    const val PAGO_RECIBIDO = "venta_pago_recibido"
    const val PEGAR_SMS = "venta_pegar_sms"
    const val CAPTURA_SMS_ACTIVAR = "venta_captura_sms_activar"
    const val ERROR = "venta_error"
    const val TOTAL = "venta_total"
    const val TURNO = "venta_turno"
    fun metodo(m: MetodoPago) = "venta_metodo_${m.name}"
    fun mas(id: Long) = "venta_mas_$id"
    fun menos(id: Long) = "venta_menos_$id"
    fun cantidad(id: Long) = "venta_cantidad_$id"
    fun campo(c: CampoCliente) = "venta_campo_${c.name}"
    fun sugerencia(ci: String) = "venta_sugerencia_$ci"
    const val CLIENTE_FIJO = "venta_cliente_fijo"
}

/** Acciones de la pantalla (una clase evita 20 lambdas sueltas en cada firma). */
class AccionesVenta(
    val onAtras: () -> Unit = {},
    val onAbrirTurno: () -> Unit = {},
    val onElegir: () -> Unit = {},
    val onCantidad: (Long, Long) -> Unit = { _, _ -> },
    val onQuitar: (Long) -> Unit = {},
    val onMetodo: (MetodoPago) -> Unit = {},
    val onContinuar: () -> Unit = {},
    val onConfirmarEfectivo: () -> Unit = {},
    val onDescartar: () -> Unit = {},
    val onCancelarSalir: () -> Unit = {},
    val onPagoRecibido: () -> Unit = {},
    val onConfigurarPago: () -> Unit = {},
    val onCampo: (CampoCliente, String) -> Unit = { _, _ -> },
    val onPegarSms: () -> Unit = {},
    val onConfirmarTransferencia: () -> Unit = {},
    /** 0.27.0 (N2). */
    val onElegirCliente: (cu.shalev.domain.model.ClienteFijo) -> Unit = {},
    val onClienteFijo: (Boolean) -> Unit = {},
    val onConfigurarCapturaSms: () -> Unit = {},
    val onImportarOcr: (() -> Unit)? = null,
)

/**
 * Venta completa (Shalev.txt «Venta»): Inventario (checkbox) → cantidades + método → comprobante (efectivo) o QR
 * (transferencia) → datos del cliente y nº de transacción → registro. Sin turno abierto se bloquea en cualquier paso.
 */
@Composable
fun VentaScreen(
    tipo: TipoVenta,
    onBack: () -> Unit,
    onElegirProductos: (TipoVenta, List<Long>) -> Unit,
    onTerminada: (String) -> Unit,
    onConfigurarPago: () -> Unit,
    seleccion: List<Long>?,
    onSeleccionConsumida: () -> Unit,
    viewModel: VentaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val capturaSmsAutomaticaActiva = rememberAccesoCapturaSms()
    val pedirPermisoAviso = rememberPedirPermisoAviso()
    LaunchedEffect(seleccion) {
        if (seleccion != null) { viewModel.recibirSeleccion(seleccion); onSeleccionConsumida() }
    }
    val haptics = rememberShalevHaptics()
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoVenta.Mensaje -> snackbar.showSnackbar(e.texto)
                is EventoVenta.ElegirProductos -> onElegirProductos(e.tipo, e.preseleccion)
                is EventoVenta.Terminada -> { haptics.exito(); onTerminada(e.mensaje) } // P18 (A06)
                EventoVenta.Salir -> onBack()
                EventoVenta.ConfigurarPago -> onConfigurarPago()
            }
        }
    }
    BackHandler(onBack = viewModel::atras)
    // 0.25.0 (§5.2): mismo diálogo de fondo que en Inicio.
    val cajaVm: cu.shalev.app.caja.CajaViewModel = hiltViewModel()
    val caja by cajaVm.state.collectAsStateWithLifecycle()
    var pedirFondo by rememberSaveable { mutableStateOf(false) }
    var mostrarOcr by rememberSaveable { mutableStateOf(false) }
    if (pedirFondo) cu.shalev.app.caja.DialogoFondo(
        sugerido = caja.sugerido, trabajando = state.abriendo,
        onConfirmar = { f -> pedirFondo = false; pedirPermisoAviso(); viewModel.abrirTurno(f) },
        onDismiss = { pedirFondo = false },
    )
    if (mostrarOcr) {
        cu.shalev.app.ocr.HojaImportarOcr(
            destino = cu.shalev.app.ocr.DestinoOcr.VENTAS,
            onDismiss = { mostrarOcr = false },
            candidatosVenta = viewModel.candidatosOcr(),
            onAplicarVenta = viewModel::importarDesdeOcr,
        )
    }
    VentaContent(
        tipo = tipo,
        state = state,
        snackbar = snackbar,
        capturaSmsAutomaticaActiva = capturaSmsAutomaticaActiva,
        acciones = AccionesVenta(
            onAtras = viewModel::atras, onAbrirTurno = { cajaVm.cargarSugerido(); pedirFondo = true }, onElegir = viewModel::elegirProductos,
            onImportarOcr = { mostrarOcr = true },
            onCantidad = viewModel::cambiarCantidad, onQuitar = viewModel::quitar, onMetodo = viewModel::elegirMetodo,
            onContinuar = viewModel::continuar, onConfirmarEfectivo = viewModel::confirmarEfectivo,
            onDescartar = viewModel::descartar, onCancelarSalir = viewModel::cancelarSalir,
            onPagoRecibido = viewModel::pagoRecibido,
            onConfigurarPago = viewModel::configurarPago,
            onCampo = viewModel::editarCliente,
            onElegirCliente = viewModel::elegirCliente, onClienteFijo = viewModel::clienteFijo,
            // El portapapeles se lee SOLO aquí, al tocar «Pegar SMS» (nunca en segundo plano).
            onPegarSms = { viewModel.pegarSms(clipboard.getText()?.text) },
            onConfirmarTransferencia = viewModel::confirmarTransferencia,
            onConfigurarCapturaSms = { AccesoCapturaSms.abrirAjustes(context) },
        ),
    )
}

@Composable
fun VentaContent(
    tipo: TipoVenta,
    state: VentaUiState,
    acciones: AccionesVenta,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    zona: ZoneId = ZoneId.systemDefault(),
    capturaSmsAutomaticaActiva: Boolean = false,
) {
    val titulo = when (state.paso) {
        PasoVenta.CARRITO -> TextosVenta.titulo(tipo == TipoVenta.SERVICIO)
        PasoVenta.COMPROBANTE -> TextosVenta.COMPROBANTE
        PasoVenta.QR -> TextosVenta.COBRO_TRANSFERENCIA
        PasoVenta.CLIENTE -> TextosVenta.DATOS_CLIENTE
    }
    Scaffold(
        topBar = {
            ShalevTopBar(
                title = titulo,
                onBack = acciones.onAtras,
                actions = {
                    if (state.paso == PasoVenta.CARRITO && state.permiso is Permiso.Permitido && acciones.onImportarOcr != null) {
                        cu.shalev.designsystem.component.ShalevIconAction(
                            cu.shalev.designsystem.icon.ShalevIcons.Ocr,
                            "Escanear o importar líneas de venta",
                            onClick = acciones.onImportarOcr,
                            modifier = Modifier.testTag("venta_ocr"),
                        )
                    }
                },
            )
        },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
        bottomBar = { if (state.permiso is Permiso.Permitido) BarraAcciones(state, acciones) },
        // 0.32.0 (A11): con el teclado abierto (paso de datos del cliente) la barra de acciones sube con él.
        // Sin esto, el botón de confirmar queda debajo del teclado (edge-to-edge no redimensiona la ventana).
        modifier = Modifier.imePadding(),
    ) { padding ->
        Column(Modifier.fillMaxSize().shalevContentWidth().padding(padding)) {
            if (state.trabajando) ShalevLinearProgress(Modifier.fillMaxWidth())
            Box(Modifier.weight(1f)) {
                when (state.permiso) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ShalevLoading() }
                    Permiso.SinTurno -> Bloqueo(state, acciones)
                    is Permiso.Permitido -> Column {
                        // 0.20.0 (H5, P43): el cierre pedido por el encargado espera a que termine esta venta.
                        if (state.cierrePedido) {
                            ShalevStatusBanner(
                                TextosVenta.CIERRE_PEDIDO, BannerTone.Aviso,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
                            )
                        }
                        if (state.paso != PasoVenta.QR && (state.paso != PasoVenta.CARRITO || state.lineas.isNotEmpty())) TotalVenta(state)
                        when (state.paso) {
                            PasoVenta.CARRITO -> {
                                ShalevSecondaryText(
                                    "Turno abierto · " + listOfNotNull("Desde las ${hora(state.permiso.turno.abiertoEn, zona)}", state.permiso.turno.abiertoPor.ifBlank { null }).joinToString(" · "),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md).testTag(VentaTags.TURNO),
                                )
                                PasoCarrito(state, acciones)
                            }
                            PasoVenta.COMPROBANTE -> PasoComprobante(state)
                            PasoVenta.QR -> { SecureWindow(); PasoQr(state, acciones, capturaSmsAutomaticaActiva) } // P18 (A13): tarjeta del vendedor
                            PasoVenta.CLIENTE -> { SecureWindow(); PasoCliente(state, acciones, capturaSmsAutomaticaActiva) }
                        }
                    }
                }
            }
        }
    }
    if (state.confirmarSalir) {
        ShalevDialog(
            title = TextosVenta.DESCARTAR_TITULO, text = TextosVenta.DESCARTAR_TEXTO,
            onDismiss = acciones.onCancelarSalir, onConfirm = acciones.onDescartar,
            confirmDescription = "Descartar", destructive = true,
        )
    }
}

@Composable
private fun Bloqueo(state: VentaUiState, acciones: AccionesVenta) {
    ShalevEmptyState(
        title = TextosTurno.SIN_TURNO_TITULO,
        detail = TextosTurno.SIN_TURNO_DETALLE + if (state.lineas.isNotEmpty()) " Lo elegido se conserva." else "",
        ilustracion = ShalevIlustracion.SinTurno,
        modifier = Modifier.testTag(VentaTags.BLOQUEO).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        ShalevPrimaryButton(
            text = if (state.abriendo) TextosTurno.ABRIENDO else TextosTurno.ABRIR,
            onClick = acciones.onAbrirTurno, icon = ShalevIcons.Turno, loading = state.abriendo, enabled = !state.abriendo,
            modifier = Modifier.testTag(VentaTags.ABRIR_TURNO),
        )
    }
}

// ---------------- Carrito ----------------

@Composable
private fun PasoCarrito(state: VentaUiState, acciones: AccionesVenta) {
    if (state.lineas.isEmpty()) {
        ShalevEmptyState(title = TextosVenta.CARRITO_VACIO_TITULO, detail = TextosVenta.CARRITO_VACIO_DETALLE, ilustracion = ShalevIlustracion.Carrito) {
            ShalevPrimaryButton(TextosVenta.ELEGIR, onClick = acciones.onElegir, icon = ShalevIcons.Inventario)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag(VentaTags.CARRITO),
        contentPadding = PaddingValues(ShalevSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
    ) {
        items(state.lineas, key = { it.producto.id }) { l -> Box(shalevAnimateItem()) { LineaEditable(l, acciones.onCantidad, acciones.onQuitar) } } // P18 (A18)
        item {
            ShalevButtonRow(Modifier.padding(top = ShalevSpacing.xs)) {
                ShalevSecondaryButton(TextosVenta.AGREGAR, onClick = acciones.onElegir, icon = ShalevIcons.AgregarCarrito)
            }
        }
        item {
            SelectorMetodo(state.metodo, acciones.onMetodo)
        }
        item { ErrorPaso(state.error) }
    }
}

/** Fila editable del carrito. 0.25.1: también la usa Modificar venta (Registros). */
@Composable
internal fun LineaEditable(l: LineaCarrito, onCantidad: (Long, Long) -> Unit, onQuitar: (Long) -> Unit) {
    val id = l.producto.id
    ShalevCard {
        ShalevListItem(
            title = l.producto.nombreCompleto,
            // P24: fila = dato principal (precio por unidad y subtotal). Las existencias de un artículo solo se muestran
            // al llegar al máximo; un Elaborado muestra siempre «Alcanza para N» (P26).
            // 0.28.0: el precio («12.00 CUP c/u») va en seminegrita.
            subtitleResaltado = ShalevTextos.resaltar(
                TextosVenta.unidad(l) + if (TextosVenta.avisarExistencias(l)) " · ${TextosVenta.disponibles(l)}" else "",
                Money.format(l.producto.precioVenta),
            ),
            subtitleMaxLines = 2,
            value = Money.format(l.subtotalEstimado),
            indicatorColor = null,
            leading = l.producto.fotoUri?.let { uri -> { cu.shalev.app.common.Miniatura(uri) } }, // 0.21.0 (C11)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            ShalevIconAction(
                ShalevIcons.Disminuir, "Una menos de ${l.producto.nombreCompleto}", onClick = { onCantidad(id, l.cantidad - 1) },
                enabled = l.cantidad > 1, style = IconActionStyle.Tonal, modifier = Modifier.testTag(VentaTags.menos(id)),
            )
            ShalevTextField(
                filtro = FiltroEntrada.ENTERO,
                value = l.cantidad.toString(),
                onValueChange = { t -> Carrito.parseCantidad(t)?.let { onCantidad(id, it) } },
                label = "Cantidad", clearable = false, textAlign = TextAlign.Center,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(ShalevSize.campoCantidad).testTag(VentaTags.cantidad(id)),
            )
            ShalevIconAction(
                ShalevIcons.Aumentar, "Una más de ${l.producto.nombreCompleto}", onClick = { onCantidad(id, l.cantidad + 1) },
                enabled = l.cantidad < l.maximo, style = IconActionStyle.Tonal, modifier = Modifier.testTag(VentaTags.mas(id)),
            )
            Box(Modifier.weight(1f))
            ShalevIconAction(ShalevIcons.Eliminar, "Quitar ${l.producto.nombreCompleto} de la venta", onClick = { onQuitar(id) })
        }
    }
}

// ---------------- Comprobante (efectivo) ----------------

/** Auditoría 2026-10-14 (§6.1): aviso visible (no bloquea) si alguna línea quedó al costo o por debajo. */
@Composable
private fun AvisoBajoCosto(state: VentaUiState) {
    val nombres = state.cotizacion?.advertencias.orEmpty()
    if (nombres.isEmpty()) return
    ShalevCard(tone = CardTone.Tonal) { ShalevSecondaryText(TextosVenta.bajoCosto(nombres)) }
}

@Composable
private fun PasoComprobante(state: VentaUiState) {
    val cot = state.cotizacion ?: return
    LazyColumn(Modifier.fillMaxSize().testTag(VentaTags.COMPROBANTE), contentPadding = PaddingValues(ShalevSpacing.md)) {
        item { AvisoBajoCosto(state) }
        item {
            ShalevCard {
                cot.detalles.forEach { d ->
                    ShalevListItem(
                        title = d.nombre,
                        // 0.28.0: los importes («2 × 12.00 CUP», «antes 10.00 CUP») van en seminegrita.
                        subtitleResaltado = ShalevTextos.resaltar(
                            TextosVenta.linea(d.cantidad, d.precioUnitario) +
                                if (d.precioUnitario != d.precioBase) " · " + TextosVenta.ajuste(d.precioBase, d.precioUnitario) else "",
                            Money.format(d.precioUnitario), Money.format(d.precioBase),
                        ),
                        value = Money.format(d.subtotal),
                        indicatorColor = null,
                    )
                }
                ShalevSecondaryText("Pago: ${TextosVenta.metodo(MetodoPago.EFECTIVO)}")
            }
        }
        item { ErrorPaso(state.error) }
    }
}

/**
 * P28: el total aparece UNA sola vez por paso, centrado y arriba (antes se repetía en la barra inferior, en el
 * comprobante, en el QR y en los datos del cliente). En el QR se llama «Importe a transferir».
 */
@Composable
private fun TotalVenta(state: VentaUiState) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs).testTag(VentaTags.TOTAL)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShalevSecondaryText(TextosVenta.etiquetaTotal(state.paso, state.cotizacion == null), textAlign = TextAlign.Center)
        ShalevTextoAjustable(
            Money.format(state.total), style = ShalevTextos.datoEn(MaterialTheme.typography.headlineSmall),
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------- QR (transferencia) ----------------

@Composable
private fun PasoQr(state: VentaUiState, acciones: AccionesVenta, capturaSmsAutomaticaActiva: Boolean) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ShalevSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            when (val qr = state.qr) {
                is PagoQr.Resultado.Ok -> QrCodigo(
                    contenido = qr.contenido,
                    descripcion = "Código QR de cobro para Transfermóvil, tarjeta terminada en ${qr.tarjeta.takeLast(4)}",
                    modifier = Modifier.testTag(VentaTags.QR),
                )
                null -> ShalevLoading()
                PagoQr.Resultado.SinTarjeta -> ShalevCard(tone = CardTone.Tonal) {
                    ShalevSecondaryText(TextosVenta.SIN_TARJETA)
                    ShalevButtonRow { ShalevSecondaryButton(TextosVenta.CONFIGURAR_PAGO, onClick = acciones.onConfigurarPago, icon = ShalevIcons.PagoElectronico) }
                }
            }
        }
        item { TotalVenta(state) }
        item { AvisoBajoCosto(state) }
        item { ShalevSecondaryText(TextosVenta.QR_AYUDA, textAlign = TextAlign.Center) }
        if (state.qr is PagoQr.Resultado.Ok) item { CapturaSmsUi(capturaSmsAutomaticaActiva, acciones.onConfigurarCapturaSms) }
    }
}

/** P28: título y chips centrados como grupo. 0.25.1: también en Modificar venta. */
@Composable
internal fun SelectorMetodo(metodo: MetodoPago, onMetodo: (MetodoPago) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(TextosVenta.METODO, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = ShalevSpacing.md).semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs), modifier = Modifier.padding(top = ShalevSpacing.xs)) {
            MetodoPago.entries.forEach { m ->
                ShalevChip(
                    label = TextosVenta.metodo(m), selected = metodo == m, onClick = { onMetodo(m) },
                    icon = if (m == MetodoPago.EFECTIVO) ShalevIcons.Efectivo else ShalevIcons.Transferencia,
                    modifier = Modifier.testTag(VentaTags.metodo(m)),
                )
            }
        }
    }
}

// ---------------- Datos del cliente ----------------

/** Campos del cliente de una transferencia (como elementos de una lista). 0.25.1: también en Modificar venta. */
internal fun LazyListScope.camposCliente(
    f: FormCliente,
    onCampo: (CampoCliente, String) -> Unit,
    /** 0.27.0 (N2): sugerencias de clientes fijos, justo debajo del nombre (solo en Nueva venta). */
    debajoDelNombre: (LazyListScope.() -> Unit)? = null,
) {
    CampoCliente.entries.forEach { c ->
        item(key = c.name) {
            CampoClienteField(f, c, onCampo, Modifier.fillMaxWidth().testTag(VentaTags.campo(c)))
        }
        if (c == CampoCliente.NOMBRE) debajoDelNombre?.invoke(this)
    }
}

/**
 * Un campo del cliente: filtro, teclado, etiqueta y error. 0.31.0: lo usan también la ficha y la corrección de datos
 * de un cliente fijo (Registros → Clientes).
 */
@Composable
internal fun CampoClienteField(f: FormCliente, c: CampoCliente, onCampo: (CampoCliente, String) -> Unit, modifier: Modifier = Modifier) {
    val error = f.visibles()[c]
    ShalevTextField(
        filtro = when (c) { CampoCliente.CI -> FiltroEntrada.CARNE; CampoCliente.NUMERO -> FiltroEntrada.TRANSACCION; else -> FiltroEntrada.LIBRE },
        value = when (c) {
            CampoCliente.NOMBRE -> f.nombre
            CampoCliente.CI -> f.ci
            CampoCliente.TELEFONO -> f.telefono
            CampoCliente.NUMERO -> f.numero
        },
        onValueChange = { onCampo(c, it) },
        label = c.etiqueta,
        isError = error != null, errorText = error, validarAlSalir = true, forzarError = f.intento,
        keyboardOptions = when (c) {
            CampoCliente.NOMBRE -> KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            CampoCliente.CI -> KeyboardOptions(keyboardType = KeyboardType.Number)
            CampoCliente.TELEFONO -> KeyboardOptions(keyboardType = KeyboardType.Phone)
            CampoCliente.NUMERO -> KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        },
        modifier = modifier,
    )
}

@Composable
private fun PasoCliente(state: VentaUiState, acciones: AccionesVenta, capturaSmsAutomaticaActiva: Boolean) {
    val f = state.cliente
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(ShalevSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
    ) {
        camposCliente(f, acciones.onCampo, debajoDelNombre = {
            // 0.27.0 (N2): hasta 3 clientes fijos; tocar uno rellena carné y teléfono.
            items(state.sugerencias, key = { "cliente_${it.ci}" }) { c ->
                ShalevListItem(
                    title = c.nombreApellidos, subtitle = TextosVenta.sugerencia(c), indicatorColor = null,
                    leading = { Icon(ShalevIcons.Perfil, contentDescription = null) },
                    onClick = { acciones.onElegirCliente(c) },
                    modifier = Modifier.testTag(VentaTags.sugerencia(c.ci)),
                )
            }
        })
        if (state.qr is PagoQr.Resultado.Ok) item { CapturaSmsUi(capturaSmsAutomaticaActiva, acciones.onConfigurarCapturaSms) }
        item(key = "cliente_fijo") {
            ShalevListItem(
                title = TextosVenta.CLIENTE_FIJO, subtitle = TextosVenta.CLIENTE_FIJO_AYUDA, subtitleMaxLines = 3, indicatorColor = null,
                leading = { ShalevCheckbox(checked = f.fijo, onCheckedChange = null) },
                onClick = { acciones.onClienteFijo(!f.fijo) },
                modifier = Modifier.testTag(VentaTags.CLIENTE_FIJO),
            )
        }
        item {
            // P24 (proximidad): el botón de pegar va centrado junto a su explicación.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ShalevSecondaryButton(TextosVenta.PEGAR_SMS, onClick = acciones.onPegarSms, icon = ShalevIcons.Pegar, modifier = Modifier.testTag(VentaTags.PEGAR_SMS))
                // 0.20.0 (H3): en una app secundaria el SMS llega al teléfono del dueño, no al del empleado.
                ShalevSecondaryText(TextosVenta.PEGAR_AYUDA, textAlign = TextAlign.Center, modifier = Modifier.padding(top = ShalevSpacing.xs))
            }
        }
        state.avisoSms?.let { a ->
            item {
                ShalevCard(tone = CardTone.Tonal) {
                    Text(a, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
        item { ErrorPaso(state.error) }
    }
}

@Composable
private fun CapturaSmsUi(activa: Boolean, onConfigurar: () -> Unit) {
    ShalevCard(tone = CardTone.Tonal) {
        Text(
            if (activa) TextosVenta.CAPTURA_SMS_ACTIVA_TITULO else TextosVenta.CAPTURA_SMS_TITULO,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        ShalevSecondaryText(if (activa) TextosVenta.CAPTURA_SMS_ACTIVA else TextosVenta.CAPTURA_SMS_AYUDA)
        ShalevSecondaryButton(
            text = if (activa) TextosVenta.CAPTURA_SMS_ADMINISTRAR else TextosVenta.CAPTURA_SMS_ACTIVAR,
            onClick = onConfigurar,
            icon = if (activa) ShalevIcons.Configurar else ShalevIcons.Sms,
            modifier = Modifier.testTag(VentaTags.CAPTURA_SMS_ACTIVAR),
        )
    }
}

@Composable
private fun rememberAccesoCapturaSms(): Boolean {
    val context = LocalContext.current
    val propietario = LocalLifecycleOwner.current
    var concedido by remember(context) { mutableStateOf(AccesoCapturaSms.concedido(context)) }
    DisposableEffect(context, propietario) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) concedido = AccesoCapturaSms.concedido(context)
        }
        propietario.lifecycle.addObserver(observador)
        onDispose { propietario.lifecycle.removeObserver(observador) }
    }
    return concedido
}

@Composable
private fun ErrorPaso(error: String?) {
    if (error == null) return
    Text(
        error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = ShalevSpacing.xs).testTag(VentaTags.ERROR).semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

// ---------------- Barra inferior (P28: solo iconos, centrados y separados 16dp; el total va arriba) ----------------

@Composable
private fun BarraAcciones(state: VentaUiState, acciones: AccionesVenta) {
    if (state.paso == PasoVenta.CARRITO && state.lineas.isEmpty()) return
    val habilitado = !state.trabajando && !state.bloqueada
    ShalevBarraAcciones {
        when (state.paso) {
            PasoVenta.CARRITO -> ShalevPrimaryButton(
                // 0.32.0 (A12): el icono dice la acción (continuar), no el método de pago: el de «Efectivo» a 24dp
                // se confundía con una cámara. El método elegido ya se ve en los chips de arriba.
                "Continuar", onClick = acciones.onContinuar, enabled = state.puedeContinuar, loading = state.trabajando,
                icon = ShalevIcons.Siguiente,
                modifier = Modifier.testTag(VentaTags.CONTINUAR),
            )
            PasoVenta.COMPROBANTE -> {
                ShalevIconAction(ShalevIcons.Cancelar, "Cancelar la venta", onClick = acciones.onDescartar, style = IconActionStyle.Tonal, modifier = Modifier.testTag(VentaTags.CANCELAR))
                ShalevIconAction(ShalevIcons.Confirmar, "Confirmar la venta", onClick = acciones.onConfirmarEfectivo, enabled = habilitado, style = IconActionStyle.Filled, modifier = Modifier.testTag(VentaTags.CONFIRMAR))
            }
            PasoVenta.QR -> {
                ShalevIconAction(ShalevIcons.Cancelar, "Cancelar la venta", onClick = acciones.onDescartar, style = IconActionStyle.Tonal, modifier = Modifier.testTag(VentaTags.CANCELAR))
                ShalevIconAction(ShalevIcons.Confirmar, TextosVenta.PAGO_RECIBIDO, onClick = acciones.onPagoRecibido, enabled = habilitado, style = IconActionStyle.Filled, modifier = Modifier.testTag(VentaTags.PAGO_RECIBIDO))
            }
            PasoVenta.CLIENTE -> {
                ShalevIconAction(ShalevIcons.Cancelar, "Volver al código QR", onClick = acciones.onAtras, style = IconActionStyle.Tonal, modifier = Modifier.testTag(VentaTags.CANCELAR))
                ShalevIconAction(ShalevIcons.Confirmar, "Registrar la venta", onClick = acciones.onConfirmarTransferencia, enabled = habilitado, style = IconActionStyle.Filled, modifier = Modifier.testTag(VentaTags.CONFIRMAR))
            }
        }
    }
}
