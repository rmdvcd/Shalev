package cu.shalev.app.servicios

import androidx.compose.foundation.layout.size
import cu.shalev.designsystem.component.ShalevCheckbox
import cu.shalev.domain.model.nombreCompleto
import cu.shalev.app.common.LocalPermisosApp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import cu.shalev.app.common.BarraContinuarVenta
import cu.shalev.app.common.BarraSeleccion
import cu.shalev.app.common.BotonFiltro
import cu.shalev.app.common.Compartir
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.inventario.FormatoSalida
import cu.shalev.app.navigation.Route
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevBottomSheet
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevChip
import cu.shalev.designsystem.component.ShalevComboBox
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevEmptyState
import cu.shalev.designsystem.component.ShalevFab
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
import cu.shalev.designsystem.component.shalevAnimateItem
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.ilustracion.ShalevIlustracion
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.FiltroServicios
import cu.shalev.domain.model.ServicioDisponible
import cu.shalev.domain.service.FormatoExport

/** Acciones de la pantalla (una clase para no arrastrar 25 lambdas y poder probar el Content sin Hilt). */
class AccionesServicios(
    val onBuscar: (String) -> Unit = {},
    val onFiltro: (FiltroServicios) -> Unit = {},
    val onQuitarFiltros: () -> Unit = {},
    val onReintentar: () -> Unit = {},
    val onAlternar: (Long) -> Unit = {},
    val onSeleccionarTodo: () -> Unit = {},
    val onLimpiarSeleccion: () -> Unit = {},
    val onAbrirFicha: (Long) -> Unit = {},
    val onCerrarFicha: () -> Unit = {},
    val onEditar: (Long) -> Unit = {},
    val onHoja: (HojaServicios) -> Unit = {},
    val onCerrarHoja: () -> Unit = {},
    val onAgregar: () -> Unit = {},
    val onEliminarFicha: () -> Unit = {},
    val onEliminarSeleccion: () -> Unit = {},
    val onConfirmarEliminar: () -> Unit = {},
    val onCancelarEliminar: () -> Unit = {},
    val onComentarioPromocion: (String) -> Unit = {},
    val onExportar: (FormatoSalida) -> Unit = {},
    val onGuardar: (FormatoSalida) -> Unit = {},
    val onContinuarVenta: () -> Unit = {},
    val onAtras: () -> Unit = {},
    val onAyuda: (() -> Unit)? = null,
    val onImportarOcr: (() -> Unit)? = null,
)

@Composable
fun ServiciosScreen(
    onNavigate: (Route) -> Unit,
    mensajeInicial: String? = null,
    onMensajeMostrado: () -> Unit = {},
    onSeleccionVenta: (List<Long>) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: ServiciosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var mostrarOcr by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val guardarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExport.PDF.mime)) { viewModel.guardarEn(it?.toString()) }
    val guardarXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExport.XLSX.mime)) { viewModel.guardarEn(it?.toString()) }
    LaunchedEffect(mensajeInicial) {
        if (mensajeInicial != null) { onMensajeMostrado(); snackbar.showSnackbar(mensajeInicial) }
    }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoServicios.Compartir ->
                    if (!Compartir.archivos(context, listOf(e.archivo), e.mime, e.asunto)) snackbar.showSnackbar(Compartir.SIN_APLICACION)
                is EventoServicios.CompartirImagenes ->
                    if (!Compartir.archivos(context, e.archivos, "image/png", TextosServicios.TITULO)) snackbar.showSnackbar(Compartir.SIN_APLICACION)
                is EventoServicios.GuardarComo -> (if (e.mime == FormatoExport.XLSX.mime) guardarXlsx else guardarPdf).launch(e.nombre)
                is EventoServicios.Navegar -> onNavigate(e.route)
                is EventoServicios.Mensaje -> snackbar.showSnackbar(e.texto)
                is EventoServicios.SeleccionVenta -> onSeleccionVenta(e.ids)
            }
        }
    }
    if (mostrarOcr) {
        cu.shalev.app.ocr.HojaImportarOcr(
            destino = cu.shalev.app.ocr.DestinoOcr.SERVICIOS,
            onDismiss = { mostrarOcr = false },
            onImportadoMensaje = { msg -> scope.launch { snackbar.showSnackbar(msg) } },
        )
    }
    ServiciosContent(
        state = state,
        snackbar = snackbar,
        acciones = AccionesServicios(
            onAyuda = { onNavigate(Route.Ayuda) },
            onImportarOcr = { mostrarOcr = true },
            onBuscar = viewModel::buscar, onFiltro = viewModel::aplicarFiltro, onQuitarFiltros = viewModel::quitarFiltros,
            onReintentar = viewModel::reintentar, onAlternar = viewModel::alternar, onSeleccionarTodo = viewModel::seleccionarTodo,
            onLimpiarSeleccion = viewModel::limpiarSeleccion, onAbrirFicha = viewModel::abrirFicha, onCerrarFicha = viewModel::cerrarFicha,
            onEditar = viewModel::editar, onHoja = viewModel::mostrarHoja,
            onCerrarHoja = viewModel::cerrarHoja, onAgregar = viewModel::agregar, onEliminarFicha = viewModel::pedirEliminarFicha,
            onEliminarSeleccion = viewModel::pedirEliminarSeleccion, onConfirmarEliminar = viewModel::confirmarEliminar,
            onCancelarEliminar = viewModel::cancelarEliminar, onComentarioPromocion = viewModel::comentarioPromocion, onExportar = viewModel::exportar, onGuardar = viewModel::pedirGuardar,
            onContinuarVenta = viewModel::confirmarSeleccionVenta, onAtras = onBack,
        ),
    )
}

@Composable
fun ServiciosContent(
    state: ServiciosUiState,
    acciones: AccionesServicios,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val venta = state.modoVenta
    Scaffold(
        topBar = {
            if (venta) {
                ShalevTopBar(title = TextosServicios.ELIGE, onBack = acciones.onAtras, actions = {
                    BotonFiltro(state.filtro.activos, TextosServicios.FILTRAR, onClick = { acciones.onHoja(HojaServicios.FILTRO) }, modifier = Modifier.testTag(ServiciosTags.FILTRO), enabled = state.tipos.isNotEmpty() || state.filtro.activos > 0)
                })
            } else ShalevTopBar(title = TextosServicios.TITULO, actions = {
                if (LocalPermisosApp.current.editarInventario && acciones.onImportarOcr != null) ShalevIconAction(
                    ShalevIcons.Ocr, "Escanear o importar servicios", onClick = acciones.onImportarOcr,
                    modifier = Modifier.testTag("servicios_ocr"),
                )
                BotonFiltro(state.filtro.activos, TextosServicios.FILTRAR, onClick = { acciones.onHoja(HojaServicios.FILTRO) }, modifier = Modifier.testTag(ServiciosTags.FILTRO), enabled = state.tipos.isNotEmpty() || state.filtro.activos > 0)
                if (LocalPermisosApp.current.exportar) ShalevIconAction(
                    ShalevIcons.Compartir, TextosServicios.EXPORTAR, onClick = { acciones.onHoja(HojaServicios.EXPORTAR) },
                    enabled = state.items.isNotEmpty() || state.seleccion.isNotEmpty(), modifier = Modifier.testTag(ServiciosTags.EXPORTAR),
                )
            })
        },
        bottomBar = {
            if (venta) BarraContinuarVenta(
                texto = TextosServicios.elegidos(state.seleccion.size), haySeleccion = state.seleccion.isNotEmpty(),
                onLimpiar = acciones.onLimpiarSeleccion, onContinuar = acciones.onContinuarVenta,
                tagContinuar = ServiciosTags.CONTINUAR,
            )
        },
        // 0.26.0 (P73): el «+» va en la esquina inferior (estándar M3); la lista deja ShalevSize.fabClearance abajo
        // para que la última fila (importes a la derecha) no quede tapada.
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            if (!venta && state.seleccion.isEmpty() && LocalPermisosApp.current.editarInventario) {
                ShalevFab(ShalevIcons.Agregar, TextosServicios.AGREGAR, onClick = acciones.onAgregar, modifier = Modifier.testTag(ServiciosTags.AGREGAR))
            }
        },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.trabajando) ShalevLinearProgress(Modifier.fillMaxWidth())
            ElementosFijados(state, acciones)
            Buscador(state, acciones)
            if (!venta && state.seleccion.isNotEmpty()) BarraSeleccion(
                texto = TextosServicios.seleccionados(state.seleccion.size), todosMarcados = state.todosVisiblesSeleccionados,
                onLimpiar = acciones.onLimpiarSeleccion, onMarcarTodo = acciones.onSeleccionarTodo,
                onExportar = { acciones.onHoja(HojaServicios.EXPORTAR) }, onEliminar = acciones.onEliminarSeleccion,
                tag = ServiciosTags.SELECCION, tagMarcarTodo = ServiciosTags.SELECCIONAR_TODO,
                tagEliminar = ServiciosTags.ELIMINAR_SELECCION,
            )
            cu.shalev.designsystem.component.BloquearGestos(state.seleccion.isNotEmpty()) // 0.27.0 (T5)
            Box(Modifier.weight(1f)) {
                when (val v = state.vista) {
                    EstadoCarga.Idle, EstadoCarga.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ShalevLoading() }
                    is EstadoCarga.Error -> ShalevEmptyState(title = "Algo salió mal", detail = v.mensaje, ilustracion = ShalevIlustracion.Error) {
                        ShalevSecondaryButton("Reintentar", icon = ShalevIcons.Reintentar, onClick = acciones.onReintentar)
                    }
                    // Sin botón propio: «Agregar» ya está en el botón flotante (no se repite).
                    is EstadoCarga.Vacio -> ShalevEmptyState(
                        title = TextosServicios.VACIO_TITULO, detail = TextosServicios.VACIO_DETALLE,
                        ilustracion = ShalevIlustracion.Servicios, ayuda = acciones.onAyuda,
                    )
                    is EstadoCarga.Exito -> when {
                        v.datos.items.isEmpty() -> ShalevEmptyState(title = TextosServicios.SIN_RESULTADOS_TITULO, detail = TextosServicios.SIN_RESULTADOS_DETALLE, ilustracion = ShalevIlustracion.SinResultados) {
                            ShalevSecondaryButton(
                                TextosServicios.QUITAR_FILTROS, icon = ShalevIcons.QuitarFiltros, onClick = { acciones.onBuscar(""); acciones.onQuitarFiltros() },
                                modifier = Modifier.testTag(ServiciosTags.QUITAR_FILTROS),
                            )
                        }
                        v.datos.items.none { it.servicio.id !in state.seleccion } -> ShalevEmptyState(
                            title = TextosServicios.SELECCIONADOS_TITULO,
                            detail = TextosServicios.SELECCIONADOS_DETALLE,
                            ilustracion = ShalevIlustracion.SinResultados,
                        )
                        else -> Lista(state, acciones)
                    }
                }
            }
        }
    }

    state.ficha?.let { Ficha(it, state, acciones) }
    when (state.hoja) {
        HojaServicios.FILTRO -> HojaFiltro(state, acciones)
        HojaServicios.EXPORTAR -> HojaExportar(state, acciones)
        null -> Unit
    }
    state.confirmar?.let { c ->
        ShalevDialog(
            title = when (c) {
                is ConfirmarEliminarServicio.Uno -> TextosServicios.ELIMINAR_UNO
                is ConfirmarEliminarServicio.Seleccion -> TextosServicios.eliminarVarios(c.cantidad)
            },
            text = (if (c is ConfirmarEliminarServicio.Uno) "${c.nombre}. " else "") + TextosServicios.ELIMINAR_DETALLE,
            onDismiss = acciones.onCancelarEliminar,
            onConfirm = acciones.onConfirmarEliminar,
            confirmDescription = "Eliminar",
            destructive = true,
        )
    }
}

@Composable
private fun Buscador(state: ServiciosUiState, acciones: AccionesServicios) {
    Column(Modifier.padding(horizontal = ShalevSpacing.md), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        ShalevTextField(
            filtro = FiltroEntrada.BUSQUEDA,
            value = state.filtro.texto, onValueChange = acciones.onBuscar, label = "Buscar",
            leadingIcon = ShalevIcons.Buscar,
            modifier = Modifier.fillMaxWidth().testTag(ServiciosTags.BUSCAR),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md, Alignment.CenterHorizontally)) {
            if (state.vista is EstadoCarga.Exito) ShalevSecondaryText(TextosServicios.contador(state.items.size, state.total))
            ServiciosLogic.resumenFiltro(state.filtro)?.let { r ->
                ShalevChip(label = r, selected = true, onClick = acciones.onQuitarFiltros, icon = ShalevIcons.Limpiar, supportingLabel = "Quitar filtro")
            }
        }
    }
}

@Composable
private fun ElementosFijados(state: ServiciosUiState, acciones: AccionesServicios) {
    val fijados = state.elementosFijados.ifEmpty { state.items.filter { it.servicio.id in state.seleccion } }
    if (fijados.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md).testTag(ServiciosTags.FIJADOS),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
    ) {
        Text(
            TextosServicios.seleccionados(fijados.size),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { heading() },
        )
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = ShalevSize.listaCortaMax),
            contentPadding = PaddingValues(vertical = ShalevSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            items(fijados, key = { "fijado_${it.servicio.id}" }) { s ->
                Fila(
                    s, marcado = true, acciones = acciones, modoVenta = state.modoVenta,
                    repetido = s.servicio.id in state.datos?.porDiferenciar.orEmpty(), modifier = shalevAnimateItem(),
                )
            }
        }
    }
}

@Composable
private fun Lista(state: ServiciosUiState, acciones: AccionesServicios) {
    val visibles = state.items.filterNot { it.servicio.id in state.seleccion }
    LazyColumn(
        Modifier.fillMaxSize().testTag(ServiciosTags.LISTA),
        contentPadding = PaddingValues(start = ShalevSpacing.md, end = ShalevSpacing.md, top = ShalevSpacing.xs, bottom = ShalevSize.fabClearance),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
    ) {
        items(visibles, key = { it.servicio.id }) { s ->
            Fila(
                s, marcado = false, acciones = acciones, modoVenta = state.modoVenta,
                repetido = s.servicio.id in state.datos?.porDiferenciar.orEmpty(), modifier = shalevAnimateItem(),
            )
        }
    }
}

@Composable
private fun Fila(s: ServicioDisponible, marcado: Boolean, acciones: AccionesServicios, modoVenta: Boolean, repetido: Boolean = false, modifier: Modifier = Modifier) {
    val id = s.servicio.id
    val agotado = s.alcanza == 0L
    ShalevListItem(
        title = s.servicio.nombreCompleto,
        subtitle = ServiciosLogic.subtitulo(s, repetido),
        subtitleMaxLines = if (repetido) 2 else 1,
        value = ServiciosLogic.valor(s),
        indicatorColor = if (agotado) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        selected = marcado,
        leading = {
            cu.shalev.app.common.ConMiniatura(s.servicio.fotoUri) { // 0.21.0 (C11)
                ShalevCheckbox(
                    checked = marcado,
                    onCheckedChange = { acciones.onAlternar(id) },
                    modifier = Modifier.testTag(ServiciosTags.check(id)).semantics { contentDescription = "Seleccionar ${s.servicio.nombreCompleto}" },
                )
            }
        },
        onClick = { if (modoVenta) acciones.onAlternar(id) else acciones.onAbrirFicha(id) },
        modifier = modifier.testTag(ServiciosTags.fila(id)).semantics { if (agotado) stateDescription = TextosServicios.NO_VENDIBLE },
    )
}

// ---------------- Ficha ----------------

@Composable
private fun Ficha(f: ServicioDisponible, state: ServiciosUiState, acciones: AccionesServicios) {
    val s = f.servicio
    ShalevBottomSheet(
        onDismiss = acciones.onCerrarFicha,
        title = s.nombreCompleto,
        footer = {
            val permisos = LocalPermisosApp.current
            if (permisos.editarInventario) ShalevIconAction(ShalevIcons.Editar, "Editar", onClick = { acciones.onEditar(s.id) }, style = IconActionStyle.Tonal, modifier = Modifier.testTag(ServiciosTags.FICHA_EDITAR))
            if (permisos.editarInventario) ShalevIconAction(ShalevIcons.Eliminar, "Eliminar", onClick = acciones.onEliminarFicha, style = IconActionStyle.Tonal, modifier = Modifier.testTag(ServiciosTags.FICHA_ELIMINAR))
        },
    ) {
        Column(Modifier.testTag(ServiciosTags.FICHA), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            s.fotoUri?.let { uri ->
                AsyncImage(
                    model = uri, contentDescription = "Foto de ${s.nombre}", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(ShalevRadius.lg)),
                )
            }
            if (f.alcanza == 0L) {
                ShalevCard(tone = CardTone.Tonal) {
                    Text("Atención: ${TextosServicios.NO_VENDIBLE}", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            ServiciosLogic.campos(f).forEach { (campo, valor) ->
                ShalevListItem(title = campo, value = valor, indicatorColor = null, valueMaxLines = 4)
            }
            if (!f.consumeInsumos) ShalevSecondaryText(TextosServicios.SIN_INSUMOS_FICHA, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---------------- Hojas ----------------

@Composable
private fun HojaFiltro(state: ServiciosUiState, acciones: AccionesServicios) {
    var borrador by remember(state.filtro) { mutableStateOf(state.filtro) }
    val tipos = (state.tipos + listOfNotNull(borrador.tipo)).distinct()
    val opciones: List<String?> = listOf(null) + tipos
    ShalevBottomSheet(
        onDismiss = acciones.onCerrarHoja,
        title = TextosServicios.FILTRAR,
        footer = {
            ShalevSecondaryButton("Quitar filtros", icon = ShalevIcons.QuitarFiltros, onClick = { borrador = FiltroServicios(texto = borrador.texto) })
            ShalevPrimaryButton("Aplicar", icon = ShalevIcons.Aplicar, onClick = { acciones.onFiltro(borrador) }, modifier = Modifier.testTag(ServiciosTags.FILTRO_APLICAR))
        },
    ) {
        ShalevComboBox(
            label = "Tipo",
            opciones = opciones,
            seleccion = borrador.tipo,
            etiqueta = { it ?: "Todos" },
            onSeleccion = { borrador = borrador.copy(tipo = it) },
            leadingIcon = ShalevIcons.Lista,
            tagOpcion = { it?.let(ServiciosTags::tipoOpcion) ?: ServiciosTags.TIPO_TODOS },
            modifier = Modifier.fillMaxWidth().testTag(ServiciosTags.FILTRO_TIPO),
        )
    }
}

@Composable
private fun HojaExportar(state: ServiciosUiState, acciones: AccionesServicios) {
    val n = if (state.seleccion.isNotEmpty()) state.seleccion.size else state.items.size
    ShalevBottomSheet(onDismiss = acciones.onCerrarHoja, title = TextosServicios.EXPORTAR) {
        ShalevSecondaryText(
            if (state.seleccion.isNotEmpty()) "${TextosServicios.servicios(n).replaceFirstChar { it.uppercase() }} seleccionados"
            else "Lo que ves: ${TextosServicios.servicios(n)}",
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        ShalevTextField(
            value = state.comentarioPromocion, onValueChange = acciones.onComentarioPromocion,
            label = "Encabezado de promoción (opcional)",
        )
        FORMATOS_EXPORTAR_SERVICIOS.forEach { f ->
            ShalevListItem(
                title = f.etiqueta, indicatorColor = null,
                leading = cu.shalev.app.common.iconoFormato(f)?.let { ic -> { Icon(ic, contentDescription = null, modifier = Modifier.size(ShalevSize.icon)) } },
                modifier = Modifier.testTag(ServiciosTags.formato(f)),
                trailing = {
                    Row {
                        ShalevIconAction(ShalevIcons.Compartir, "Compartir ${f.etiqueta}", onClick = { acciones.onExportar(f) })
                        if (f.guardable) ShalevIconAction(ShalevIcons.Exportar, "Guardar ${f.etiqueta} en el dispositivo", onClick = { acciones.onGuardar(f) })
                    }
                },
            )
        }
    }
}
