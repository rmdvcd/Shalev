package cu.shalev.app.registros

import androidx.compose.foundation.layout.size
import cu.shalev.designsystem.component.ShalevCheckbox
import cu.shalev.designsystem.token.ShalevSize
import androidx.compose.material3.Icon
import cu.shalev.app.common.LocalPermisosApp
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.ShalevTextoAjustable
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.common.BarraSeleccion
import cu.shalev.app.common.CampoFecha
import cu.shalev.app.common.Compartir
import cu.shalev.app.common.SecureWindow
import cu.shalev.app.common.descripcionFiltro
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.navigation.Route
import cu.shalev.designsystem.component.ShalevBadge
import cu.shalev.designsystem.component.ShalevBottomSheet
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevChip
import cu.shalev.designsystem.component.ShalevComboBox
import cu.shalev.designsystem.component.ShalevEmptyState
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevLinearProgress
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevLoading
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.ShalevTab
import cu.shalev.designsystem.component.ShalevTabs
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.component.shalevAnimateItem
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.ilustracion.ShalevIlustracion
import cu.shalev.designsystem.theme.ShalevTextos
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.FiltroRegistros
import cu.shalev.domain.model.ItemMovimiento
import cu.shalev.domain.model.PeriodoRegistro
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Turno
import cu.shalev.domain.model.Venta
import kotlinx.coroutines.launch
import cu.shalev.core.money.Money
import cu.shalev.domain.model.VistaRegistro
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.service.TablasExport
import cu.shalev.domain.usecase.TipoRegistro
import java.time.ZoneId

class AccionesRegistros(
    val onPestana: (PestanaRegistros) -> Unit = {},
    val onBuscar: (String) -> Unit = {},
    val onAbrirFiltro: () -> Unit = {},
    val onCerrarFiltro: () -> Unit = {},
    val onAplicarFiltro: (FiltroRegistros) -> Unit = {},
    /** true = también vacía el buscador. */
    val onQuitarFiltros: (Boolean) -> Unit = {},
    val onReintentar: () -> Unit = {},
    val onAbrirExportar: () -> Unit = {},
    val onCerrarExportar: () -> Unit = {},
    val onEnviar: (FormatoExport) -> Unit = {},
    val onGuardar: (FormatoExport) -> Unit = {},
    val onAbrirVenta: (Long) -> Unit = {},
    val onAbrirTransferencia: (Long) -> Unit = {},
    val onAbrirMovimiento: (Long) -> Unit = {},
    val onCerrarFicha: () -> Unit = {},
    val onVerVenta: () -> Unit = {},
    val onTurno: (Long) -> Unit = {},
    val onReintentarTurnos: () -> Unit = {},
    /** 0.31.0 (P3): marcar filas con la casilla (exportar sale solo de lo marcado). */
    val onAlternar: (Long) -> Unit = {},
    val onSeleccionarTodo: () -> Unit = {},
    val onLimpiarSeleccion: () -> Unit = {},
    /** P18 (A23): «Ayuda» desde el estado vacío. */
    val onAyuda: (() -> Unit)? = null,
    /**
     * 0.25.0 (§6bis): anular / modificar en la ficha de una venta (solo principal y turno abierto).
     * 0.31.0: el segundo parámetro es «Ver la venta» de una transferencia (null en una venta); va en la misma fila.
     */
    val accionesVenta: (@Composable (Venta, (() -> Unit)?) -> Unit)? = null,
    /** 0.31.0: corregir la cantidad de un ajuste en la ficha de un movimiento (solo principal, turno abierto). */
    val accionesMovimiento: (@Composable (ItemMovimiento) -> Unit)? = null,
)

/** Registros (barra inferior): Ventas, Transferencias recibidas, Movimientos, Clientes (0.27.0) y Turnos en una sola pantalla. */
@Composable
fun RegistrosScreen(
    onNavigate: (Route) -> Unit,
    viewModel: RegistrosViewModel = hiltViewModel(),
    turnosViewModel: TurnosViewModel = hiltViewModel(),
    clientesViewModel: ClientesFijosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val turnos by turnosViewModel.state.collectAsStateWithLifecycle()
    val clientes by clientesViewModel.state.collectAsStateWithLifecycle()
    // Datos de clientes (nombre, carné, número de transferencia): sin capturas ni miniatura en Recientes mientras se ven.
    if (state.datos is VistaRegistro.Transferencias || state.ficha is FichaRegistro.DeTransferencia) SecureWindow()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    // CreateDocument fija el tipo MIME al crearse: uno por formato guardable.
    val guardarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExport.PDF.mime)) { viewModel.guardarEn(it?.toString()) }
    val guardarXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExport.XLSX.mime)) { viewModel.guardarEn(it?.toString()) }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoRegistros.CompartirArchivo ->
                    if (!Compartir.archivos(context, listOf(e.archivo), e.mime, e.asunto)) snackbar.showSnackbar(Compartir.SIN_APLICACION)
                is EventoRegistros.GuardarComo -> (if (e.mime == FormatoExport.XLSX.mime) guardarXlsx else guardarPdf).launch(e.nombre)
                is EventoRegistros.Mensaje -> snackbar.showSnackbar(e.texto)
            }
        }
    }
    // 0.28.0: sin swipe entre pestañas: el gesto pasa a la sección vecina como en las demás.
    val orden = pestanasVisibles(LocalPermisosApp.current)
    // 0.21.0 (C12): si la pestaña elegida es de un módulo desactivado, se pasa a la primera visible.
    LaunchedEffect(orden, state.pestana) { if (state.pestana !in orden) viewModel.pestana(orden.first()) }
    RegistrosContent(
        state = state,
        turnos = turnos,
        snackbar = snackbar,
        clientes = clientes,
        accionesClientes = AccionesClientes(
            onAbrir = clientesViewModel::abrir, onCerrar = clientesViewModel::cerrar, onPedirQuitar = clientesViewModel::pedirQuitar,
            onCancelarQuitar = clientesViewModel::cancelarQuitar, onQuitar = clientesViewModel::quitar,
            onEditar = clientesViewModel::editar, onCampoEdicion = clientesViewModel::campoEdicion,
            onCancelarEdicion = clientesViewModel::cancelarEdicion, onGuardarEdicion = clientesViewModel::guardarEdicion,
        ),
        acciones = AccionesRegistros(
            onAyuda = { onNavigate(Route.Ayuda) },
            onPestana = viewModel::pestana, onBuscar = viewModel::buscar, onAbrirFiltro = viewModel::abrirFiltro,
            onCerrarFiltro = viewModel::cerrarFiltro, onAplicarFiltro = viewModel::aplicarFiltro, onQuitarFiltros = viewModel::quitarFiltros,
            onReintentar = viewModel::reintentar, onAbrirVenta = viewModel::abrirVenta,
            onAbrirExportar = viewModel::abrirExportar, onCerrarExportar = viewModel::cerrarExportar,
            onEnviar = viewModel::enviar, onGuardar = viewModel::pedirGuardar,
            onAbrirTransferencia = viewModel::abrirTransferencia, onAbrirMovimiento = viewModel::abrirMovimiento,
            onCerrarFicha = viewModel::cerrarFicha, onVerVenta = viewModel::verVentaDeTransferencia,
            onTurno = { onNavigate(Route.TurnoDetalle(it)) }, onReintentarTurnos = turnosViewModel::reintentar,
            onAlternar = viewModel::alternar, onSeleccionarTodo = viewModel::seleccionarTodo,
            onLimpiarSeleccion = viewModel::limpiarSeleccion,
            accionesVenta = { v, verVenta ->
                val accionesVm: VentaAccionesViewModel = hiltViewModel()
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                AccionesFichaVenta(
                    v, accionesVm,
                    onMensaje = { m -> scope.launch { snackbar.showSnackbar(m) } },
                    onHecho = { viewModel.cerrarFicha(); viewModel.reintentar() },
                    verVenta = verVenta,
                )
            },
            accionesMovimiento = { i ->
                val accionesVm: MovimientoAccionesViewModel = hiltViewModel()
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                AccionesFichaMovimiento(
                    i, accionesVm,
                    onMensaje = { m -> scope.launch { snackbar.showSnackbar(m) } },
                    onHecho = { viewModel.cerrarFicha(); viewModel.reintentar() },
                )
            },
        ),
    )
}

@Composable
fun RegistrosContent(
    state: RegistrosUiState,
    turnos: EstadoCarga<List<Turno>>,
    acciones: AccionesRegistros,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    zona: ZoneId = ZoneId.systemDefault(),
    clientes: ClientesUiState = ClientesUiState(cargando = false),
    accionesClientes: AccionesClientes = AccionesClientes(),
) {
    val tipo = state.tipo
    // 1.0.2: ayuda de la pestaña (botón «?» de la esquina superior izquierda).
    val ayudaPestana = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Scaffold(
        topBar = {
            ShalevTopBar(
                title = TextosRegistros.TITULO,
                onAyuda = { ayudaPestana.value = true },
                ayudaDescripcion = "Ayuda de Registros",
                actions = {
                    if (tipo != null) {
                        val n = state.filtro.activos
                        val textoFiltro = if (tipo == TipoRegistro.MOVIMIENTOS) TextosRegistros.FILTRAR_FECHA else TextosRegistros.FILTRAR
                        BadgedBox(badge = {
                            if (n > 0) ShalevBadge(n, Modifier.clearAndSetSemantics { })
                        }) {
                            ShalevIconAction(
                                ShalevIcons.Filtrar, descripcionFiltro(textoFiltro, n),
                                onClick = acciones.onAbrirFiltro, selected = n > 0, modifier = Modifier.testTag(RegistrosTags.FILTRO),
                                etiqueta = textoFiltro,
                            )
                        }
                        val exportar = LocalPermisosApp.current.exportar
                        if (exportar) ShalevIconAction(
                            ShalevIcons.Compartir, TextosRegistros.EXPORTAR, onClick = acciones.onAbrirExportar,
                            enabled = state.puedeCompartir && !state.exportando, modifier = Modifier.testTag(RegistrosTags.EXPORTAR),
                        )
                    }
                }
            )
        },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.exportando) {
                ShalevLinearProgress(Modifier.testTag(RegistrosTags.EXPORTANDO).semantics { contentDescription = TextosRegistros.EXPORTANDO })
            }
            Pestanas(state.pestana, acciones, pestanasVisibles(LocalPermisosApp.current))
            when {
                state.pestana == PestanaRegistros.CLIENTES -> ClientesFijosTab(clientes, accionesClientes, zona)
                tipo == null -> Turnos(turnos, acciones, zona)
                else -> Tabla(state, tipo, acciones, zona)
            }
        }
    }
    state.ficha?.let { Ficha(it, acciones, zona) }
    // 0.21.0 (C8): en la secundaria solo están los registros de su empleado: sin filtro «Vendedor».
    val vendedores = if (LocalPermisosApp.current.esSecundaria) emptyList() else state.vendedores
    if (state.hojaFiltro && tipo != null) HojaFiltro(state.filtro, tipo, acciones, vendedores)
    if (state.hojaExportar && tipo != null) HojaExportar(state, tipo, acciones)

    if (ayudaPestana.value) cu.shalev.app.ayuda.DialogoAyudaPestana(
        pestana = cu.shalev.app.ayuda.PestanaAyuda.REGISTROS,
        modulos = LocalPermisosApp.current.modulos,
        onDismiss = { ayudaPestana.value = false },
        onVerManual = acciones.onAyuda?.let { ir -> { ayudaPestana.value = false; ir() } },
    )
}

/** Prompt 14: PDF o Excel de lo que se ve; cada formato se puede enviar a otra app o guardar en el teléfono. */
@Composable
private fun HojaExportar(state: RegistrosUiState, tipo: TipoRegistro, acciones: AccionesRegistros) {
    ShalevBottomSheet(onDismiss = acciones.onCerrarExportar, title = TextosRegistros.tituloExportar(tipo)) {
        // 0.31.0 (P3): con filas marcadas, el documento sale de la selección, no de todo lo que se ve.
        val n = if (state.seleccion.isNotEmpty()) state.seleccion.size else state.datos?.cantidad ?: 0
        ShalevSecondaryText(
            if (state.seleccion.isNotEmpty()) TextosRegistros.seleccionados(tipo, n)
            else TextosRegistros.alcanceExportar(tipo, n, detalleFiltro(state)),
        )
        if (tipo == TipoRegistro.TRANSACCIONES) ShalevSecondaryText(TextosRegistros.AVISO_DATOS_CLIENTES)
        listOf(FormatoExport.PDF, FormatoExport.XLSX).forEach { f ->
            val etiqueta = if (f == FormatoExport.PDF) "PDF" else "Excel"
            ShalevListItem(
                title = etiqueta, indicatorColor = null,
                leading = { Icon(cu.shalev.app.common.iconoFormato(f), contentDescription = null, modifier = Modifier.size(ShalevSize.icon)) },
                modifier = Modifier.testTag(RegistrosTags.formato(f)),
                trailing = {
                    Row {
                        ShalevIconAction(
                            ShalevIcons.Compartir, "Enviar $etiqueta a otra app", etiqueta = "Enviar",
                            onClick = { acciones.onEnviar(f) }, modifier = Modifier.testTag(RegistrosTags.enviar(f)),
                        )
                        ShalevIconAction(
                            ShalevIcons.Exportar, "Guardar $etiqueta en el teléfono", etiqueta = "Guardar",
                            onClick = { acciones.onGuardar(f) }, modifier = Modifier.testTag(RegistrosTags.guardar(f)),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun Pestanas(actual: PestanaRegistros, acciones: AccionesRegistros, orden: List<PestanaRegistros>) {
    ShalevTabs(
        tabs = orden.map { ShalevTab(it.etiqueta, testTag = RegistrosTags.pestana(it)) },
        selectedIndex = orden.indexOf(actual).coerceAtLeast(0),
        onSelect = { acciones.onPestana(orden[it]) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.xs),
    )
}

// ---------------- Tablas ----------------

@Composable
private fun Tabla(state: RegistrosUiState, tipo: TipoRegistro, acciones: AccionesRegistros, zona: ZoneId) {
    Column(Modifier.fillMaxSize()) {
        Buscador(state, tipo, acciones)
        // 0.31.0 (P3): con casillas marcadas, cuántas son y qué se puede hacer con ellas.
        if (state.seleccion.isNotEmpty()) BarraSeleccion(
            texto = TextosRegistros.seleccionados(tipo, state.seleccion.size), todosMarcados = state.todosSeleccionados,
            onLimpiar = acciones.onLimpiarSeleccion, onMarcarTodo = acciones.onSeleccionarTodo,
            onExportar = acciones.onAbrirExportar,
            tag = RegistrosTags.SELECCION, tagMarcarTodo = RegistrosTags.SELECCIONAR_TODO,
            tagExportar = RegistrosTags.EXPORTAR_SELECCION,
        )
        cu.shalev.designsystem.component.BloquearGestos(state.seleccion.isNotEmpty()) // 0.31.0 (P3)
        Box(Modifier.weight(1f)) {
            when (val v = state.vista) {
                EstadoCarga.Idle, EstadoCarga.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ShalevLoading() }
                is EstadoCarga.Error -> ShalevEmptyState(title = "Algo salió mal", detail = v.mensaje, ilustracion = ShalevIlustracion.Error) {
                    ShalevSecondaryButton("Reintentar", icon = ShalevIcons.Reintentar, onClick = acciones.onReintentar, modifier = Modifier.testTag(RegistrosTags.REINTENTAR))
                }
                is EstadoCarga.Vacio -> ShalevEmptyState(
                    title = TextosRegistros.vacioTitulo(tipo), detail = TextosRegistros.vacioDetalle(tipo), ayuda = acciones.onAyuda,
                    ilustracion = ShalevIlustracion.Registros,
                )
                is EstadoCarga.Exito -> if (v.datos.cantidad == 0) {
                    ShalevEmptyState(
                        title = TextosRegistros.SIN_RESULTADOS_TITULO, detail = TextosRegistros.SIN_RESULTADOS_DETALLE,
                        ilustracion = ShalevIlustracion.SinResultados,
                    ) {
                        ShalevSecondaryButton(
                            TextosRegistros.QUITAR_FILTROS, icon = ShalevIcons.QuitarFiltros, onClick = { acciones.onQuitarFiltros(true) },
                            modifier = Modifier.testTag(RegistrosTags.QUITAR_FILTROS),
                        )
                    }
                } else {
                    Lista(v.datos, state, acciones, zona)
                }
            }
        }
    }
}

@Composable
private fun Buscador(state: RegistrosUiState, tipo: TipoRegistro, acciones: AccionesRegistros) {
    Column(Modifier.padding(horizontal = ShalevSpacing.md), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        ShalevTextField(
            filtro = FiltroEntrada.BUSQUEDA,
            value = state.filtro.texto, onValueChange = acciones.onBuscar, label = "Buscar",
            placeholder = TextosRegistros.buscar(tipo), leadingIcon = ShalevIcons.Buscar, anchoCompleto = true,
            modifier = Modifier.fillMaxWidth().testTag(RegistrosTags.BUSCAR),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md, Alignment.CenterHorizontally)) { // P28: contador (+ filtro) centrados
            state.datos?.let {
                // 0.28.0: el importe del resumen («3 ventas · 1 250.00 CUP») va en seminegrita.
                val total = when (it) {
                    is VistaRegistro.Ventas -> Money.format(it.total)
                    is VistaRegistro.Transferencias -> Money.format(it.total)
                    is VistaRegistro.Movimientos -> null
                }
                val texto = TextosRegistros.resumen(it)
                if (total == null) ShalevSecondaryText(texto, modifier = Modifier.testTag(RegistrosTags.RESUMEN))
                else ShalevSecondaryText(ShalevTextos.resaltar(texto, total), modifier = Modifier.testTag(RegistrosTags.RESUMEN))
            }
            resumenFiltro(state.filtro, tipo)?.let { r ->
                ShalevChip(label = r, selected = true, onClick = { acciones.onQuitarFiltros(false) }, icon = ShalevIcons.Limpiar, supportingLabel = "Quitar filtro")
            }
        }
    }
}

@Composable
private fun Lista(datos: VistaRegistro, state: RegistrosUiState, acciones: AccionesRegistros, zona: ZoneId) {
    LazyColumn(
        Modifier.fillMaxSize().testTag(RegistrosTags.LISTA),
        contentPadding = PaddingValues(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
    ) {
        when (datos) {
            is VistaRegistro.Ventas -> ventas(datos.items, state.seleccion, acciones, zona)
            is VistaRegistro.Transferencias -> transferencias(datos.items, state.seleccion, acciones, zona)
            is VistaRegistro.Movimientos -> movimientos(datos.items, state.seleccion, acciones, zona)
        }
    }
}

/** 0.31.0 (P3): casilla de selección al principio de cada fila, igual que en Inventario. */
@Composable
private fun CasillaRegistro(marcado: Boolean, id: Long, nombre: String, onAlternar: (Long) -> Unit) {
    ShalevCheckbox(
        checked = marcado, onCheckedChange = { onAlternar(id) },
        modifier = Modifier.testTag(RegistrosTags.check(id)).semantics { contentDescription = "Seleccionar $nombre" },
    )
}

private fun LazyListScope.ventas(xs: List<Venta>, marcados: Set<Long>, acciones: AccionesRegistros, zona: ZoneId) {
    items(xs, key = { "v${it.id}" }) { v ->
        ShalevListItem(
            tituloEsDato = true, title = tituloFila(v, zona), subtitle = subtituloFila(v), value = valorFila(v), indicatorColor = null,
            leading = { CasillaRegistro(v.id in marcados, v.id, tituloFila(v, zona), acciones.onAlternar) },
            onClick = { acciones.onAbrirVenta(v.id) }, modifier = shalevAnimateItem().testTag(RegistrosTags.venta(v.id)),
        )
    }
}

private fun LazyListScope.transferencias(xs: List<Transaccion>, marcados: Set<Long>, acciones: AccionesRegistros, zona: ZoneId) {
    items(xs, key = { "t${it.id}" }) { t ->
        ShalevListItem(
            tituloEsDato = true, title = tituloFila(t, zona), subtitle = subtituloFila(t), value = valorFila(t), indicatorColor = null,
            leading = { CasillaRegistro(t.id in marcados, t.id, tituloFila(t, zona), acciones.onAlternar) },
            onClick = { acciones.onAbrirTransferencia(t.id) }, modifier = shalevAnimateItem().testTag(RegistrosTags.transferencia(t.id)),
        )
    }
}

private fun LazyListScope.movimientos(xs: List<ItemMovimiento>, marcados: Set<Long>, acciones: AccionesRegistros, zona: ZoneId) {
    items(xs, key = { "m${it.movimiento.id}" }) { i ->
        val estado = descripcionMovimiento(i)
        ShalevListItem(
            tituloEsDato = true, title = tituloFila(i, zona), subtitle = subtituloFila(i), value = valorFila(i),
            indicatorColor = colorMovimiento(i),
            leading = { CasillaRegistro(i.movimiento.id in marcados, i.movimiento.id, tituloFila(i, zona), acciones.onAlternar) },
            onClick = { acciones.onAbrirMovimiento(i.movimiento.id) },
            modifier = shalevAnimateItem().testTag(RegistrosTags.movimiento(i.movimiento.id)).semantics { stateDescription = estado },
        )
    }
}

@Composable
private fun colorMovimiento(i: ItemMovimiento): Color =
    if (esEntrada(i)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

// ---------------- Turnos (Prompt 7) ----------------

@Composable
private fun Turnos(state: EstadoCarga<List<Turno>>, acciones: AccionesRegistros, zona: ZoneId) {
    Box(Modifier.fillMaxSize()) {
        when (state) {
            EstadoCarga.Idle, EstadoCarga.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ShalevLoading() }
            is EstadoCarga.Error -> ShalevEmptyState(title = "Algo salió mal", detail = state.mensaje, ilustracion = ShalevIlustracion.Error) {
                ShalevSecondaryButton("Reintentar", icon = ShalevIcons.Reintentar, onClick = acciones.onReintentarTurnos, modifier = Modifier.testTag(RegistrosTags.REINTENTAR))
            }
            is EstadoCarga.Vacio -> ShalevEmptyState(
                title = TextosTurno.SIN_TURNOS_TITULO, detail = TextosTurno.SIN_TURNOS_DETALLE, ilustracion = ShalevIlustracion.SinTurno,
            )
            is EstadoCarga.Exito -> ListaTurnos(state.datos, acciones.onTurno, zona)
        }
    }
}

@Composable
private fun ListaTurnos(turnos: List<Turno>, onTurno: (Long) -> Unit, zona: ZoneId) {
    // 0.31.0 (P3): los turnos también se marcan con casilla. Aquí no hay acción conjunta (no se exportan), así que
    // la selección es local a la pantalla.
    var marcados by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    Column(Modifier.fillMaxSize()) {
        if (marcados.isNotEmpty()) BarraSeleccionSola(marcados.size) { marcados = emptySet() }
        LazyColumn(
            Modifier.fillMaxSize().testTag(RegistrosTags.LISTA),
            contentPadding = PaddingValues(ShalevSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            items(turnos, key = { it.id }) { t ->
                ShalevListItem(
                    title = tituloTurno(t, zona), tituloEsDato = true,
                    subtitleResaltado = ShalevTextos.resaltar(subtituloTurno(t, zona), horasTurno(t, zona)),
                    value = valorTurno(t),
                    valueColor = if (t.abierto) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    indicatorColor = if (t.abierto) MaterialTheme.colorScheme.primary else null,
                    selected = t.abierto,
                    leading = {
                        ShalevCheckbox(
                            checked = t.id in marcados,
                            onCheckedChange = { marcados = if (t.id in marcados) marcados - t.id else marcados + t.id },
                            modifier = Modifier.testTag(RegistrosTags.check(t.id))
                                .semantics { contentDescription = "Seleccionar ${tituloTurno(t, zona)}" },
                        )
                    },
                    onClick = { onTurno(t.id) },
                    modifier = shalevAnimateItem().testTag(RegistrosTags.turno(t.id)),
                )
            }
        }
    }
}

/** 0.31.0 (P3): barra de selección donde no hay acción conjunta (Turnos, Clientes): cuántas hay y cómo quitarlas. */
@Composable
private fun BarraSeleccionSola(n: Int, onQuitar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.xs).testTag(RegistrosTags.SELECCION),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShalevIconAction(
            ShalevIcons.Cancelar, "Quitar selección", onClick = onQuitar,
            modifier = Modifier.testTag(RegistrosTags.LIMPIAR_SELECCION),
        )
        Text("$n seleccionados", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    }
}

// ---------------- Ventana del elemento ----------------

@Composable
private fun Ficha(f: FichaRegistro, acciones: AccionesRegistros, zona: ZoneId) {
    val (titulo, campos) = when (f) {
        is FichaRegistro.DeVenta -> TablasExport.tituloVenta(f.venta, zona) to TablasExport.camposVenta(f.venta, zona)
        is FichaRegistro.DeTransferencia -> TablasExport.tituloTransaccion(f.transaccion, zona) to TablasExport.camposTransaccion(f.transaccion, zona)
        is FichaRegistro.DeMovimiento -> TablasExport.tituloMovimiento(f.item, zona) to TablasExport.camposMovimiento(f.item, zona)
    }
    ShalevBottomSheet(
        onDismiss = acciones.onCerrarFicha,
        title = titulo,
    ) {
        // P28: el total (o el importe de la transferencia) va UNA vez, centrado arriba; no se repite en la tabla.
        val total = campos.firstOrNull { it.first == "Total" || it.first == "Importe" }
        Column(Modifier.testTag(RegistrosTags.FICHA), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            total?.let { (etiqueta, valor) ->
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                    ShalevSecondaryText(etiqueta, textAlign = TextAlign.Center)
                    ShalevTextoAjustable(valor, style = ShalevTextos.datoEn(MaterialTheme.typography.headlineSmall), textAlign = TextAlign.Center)
                }
            }
            val lineas = if (f is FichaRegistro.DeVenta) TablasExport.lineasVenta(f.venta) else emptyList()
            val detalle = lineas.map { it.first }.toSet() // ya se muestran con el unitario debajo, no en campos
            (campos - setOfNotNull(total)).filterNot { it.first in detalle }.forEach { (campo, valor) ->
                ShalevListItem(title = campo, value = valor, indicatorColor = null, valueMaxLines = 4)
            } // nota y cliente enteros
            lineas.forEach { (titulo, subtitulo, valor) ->
                ShalevListItem(title = titulo, subtitle = subtitulo, value = valor, indicatorColor = null, valueMaxLines = 4)
            }
            if (f is FichaRegistro.DeVenta) acciones.accionesVenta?.invoke(f.venta, null)
            if (f is FichaRegistro.DeTransferencia) {
                // 0.31.0: el importe y el nº de transacción son de la venta que pagó la transferencia: se corrigen aquí mismo.
                f.venta?.let { v -> acciones.accionesVenta?.invoke(v, acciones.onVerVenta) }
                // Sin la venta cargada (o sin acciones de venta, p. ej. en capturas) el botón va solo, centrado como los demás.
                if (f.venta == null || acciones.accionesVenta == null) ShalevButtonRow {
                    ShalevSecondaryButton(
                        TextosRegistros.VER_VENTA, icon = ShalevIcons.Comprobante, onClick = acciones.onVerVenta,
                        modifier = Modifier.testTag(RegistrosTags.FICHA_VER_VENTA),
                    )
                }
            }
            if (f is FichaRegistro.DeMovimiento) acciones.accionesMovimiento?.invoke(f.item)
        }
    }
}

// ---------------- Hoja Filtrar ----------------

@Composable
private fun HojaFiltro(actual: FiltroRegistros, tipo: TipoRegistro, acciones: AccionesRegistros, vendedores: List<String> = emptyList()) {
    var form by remember(actual) { mutableStateOf(FiltroRegistrosLogic.desde(actual)) }
    var errores by remember(actual) { mutableStateOf(emptyMap<String, String>()) }
    val opcionesPeriodo = PeriodoRegistro.entries
    val opcionesVendedor: List<String?> = listOf(null) + (vendedores + listOfNotNull(form.vendedor)).distinct()
    val conImporte = tipo != TipoRegistro.MOVIMIENTOS
    ShalevBottomSheet(
        onDismiss = acciones.onCerrarFiltro,
        title = if (conImporte) TextosRegistros.FILTRAR else TextosRegistros.FILTRAR_FECHA,
        footer = {
            ShalevSecondaryButton(TextosRegistros.QUITAR_FILTROS, icon = ShalevIcons.QuitarFiltros, onClick = { form = FormFiltroRegistros(); errores = emptyMap() })
            ShalevPrimaryButton(
                "Aplicar", icon = ShalevIcons.Aplicar,
                onClick = {
                    val (filtro, e) = FiltroRegistrosLogic.aplicar(form, actual, tipo)
                    errores = e
                    if (filtro != null) acciones.onAplicarFiltro(filtro)
                },
                modifier = Modifier.testTag(RegistrosTags.FILTRO_APLICAR),
            )
        },
    ) {
        Seccion("Fecha")
        ShalevComboBox(
            label = "Período",
            opciones = opcionesPeriodo,
            seleccion = form.periodo,
            etiqueta = ::etiqueta,
            onSeleccion = { form = form.copy(periodo = it); errores = errores - CamposFiltroRegistros.FECHAS },
            leadingIcon = ShalevIcons.Fecha,
            tagOpcion = RegistrosTags::periodo,
            modifier = Modifier.fillMaxWidth().testTag(RegistrosTags.FILTRO_PERIODO),
        )
        if (form.periodo == PeriodoRegistro.PERSONALIZADO) {
            CampoFecha(form.desde, { form = form.copy(desde = it) }, label = "Desde", tag = RegistrosTags.FILTRO_DESDE, modifier = Modifier.fillMaxWidth())
            CampoFecha(
                form.hasta, { form = form.copy(hasta = it) }, label = "Hasta", tag = RegistrosTags.FILTRO_HASTA, modifier = Modifier.fillMaxWidth(),
                supportingText = errores[CamposFiltroRegistros.FECHAS] ?: "Incluye el día completo. Deja una vacía para no poner límite.",
            )
        }
        if (conImporte) {
            Seccion("Importe")
            ShalevTextField(
                filtro = FiltroEntrada.DINERO,
                value = form.importeMin, onValueChange = { form = form.copy(importeMin = it); errores = errores - CamposFiltroRegistros.MIN },
                label = "Desde (CUP)", placeholder = "Sin mínimo",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = CamposFiltroRegistros.MIN in errores, errorText = errores[CamposFiltroRegistros.MIN],
                modifier = Modifier.testTag(RegistrosTags.FILTRO_MIN),
            )
            ShalevTextField(
                filtro = FiltroEntrada.DINERO,
                value = form.importeMax, onValueChange = { form = form.copy(importeMax = it); errores = errores - CamposFiltroRegistros.MAX },
                label = "Hasta (CUP)", placeholder = "Sin máximo",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = CamposFiltroRegistros.MAX in errores, errorText = errores[CamposFiltroRegistros.MAX],
                modifier = Modifier.testTag(RegistrosTags.FILTRO_MAX),
            )
        }
        // 0.20.0 (H1): con apps secundarias, quién vendió. Con un solo nombre no aporta nada (Hick) y no se muestra.
        if (conImporte && (vendedores.size > 1 || form.vendedor != null)) {
            ShalevComboBox(
                label = TextosRegistros.VENDEDOR,
                opciones = opcionesVendedor,
                seleccion = form.vendedor,
                etiqueta = { it ?: TextosRegistros.TODOS_VENDEDORES },
                onSeleccion = { form = form.copy(vendedor = it) },
                leadingIcon = ShalevIcons.Perfil,
                tagOpcion = { it?.let(RegistrosTags::vendedor) ?: RegistrosTags.VENDEDOR_TODOS },
                modifier = Modifier.fillMaxWidth().testTag(RegistrosTags.FILTRO_VENDEDOR),
            )
        }
    }
}

@Composable
private fun Seccion(t: String) {
    Text(t, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = ShalevSpacing.xs).semantics { heading() })
}
