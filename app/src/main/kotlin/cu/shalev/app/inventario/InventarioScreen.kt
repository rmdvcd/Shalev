package cu.shalev.app.inventario

import cu.shalev.designsystem.component.ShalevCheckbox
import cu.shalev.domain.model.nombreCompleto
import cu.shalev.app.common.LocalPermisosApp
import cu.shalev.domain.model.IdArticulo
import cu.shalev.domain.model.FichaInsumo
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import cu.shalev.app.common.BarraContinuarVenta
import cu.shalev.app.common.BarraSeleccion
import cu.shalev.app.common.BotonFiltro
import cu.shalev.app.common.Compartir
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.navigation.Route
import cu.shalev.core.money.Money
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
import cu.shalev.designsystem.theme.ShalevTextos
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
import cu.shalev.domain.model.FichaProducto
import cu.shalev.domain.model.FiltroInventario
import cu.shalev.domain.model.ItemInventario
import cu.shalev.domain.model.TipoArticulo
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.service.TablasExport

/** Acciones de la pantalla (una sola clase para no arrastrar 20 lambdas y poder probar el Content sin Hilt). */
class AccionesInventario(
    val onBuscar: (String) -> Unit = {},
    val onFiltro: (FiltroInventario) -> Unit = {},
    val onQuitarFiltros: () -> Unit = {},
    val onReintentar: () -> Unit = {},
    val onAlternar: (Long) -> Unit = {},
    val onSeleccionarTodo: () -> Unit = {},
    val onLimpiarSeleccion: () -> Unit = {},
    val onAbrirFicha: (Long) -> Unit = {},
    val onCerrarFicha: () -> Unit = {},
    val onEditar: (Long) -> Unit = {},
    val onHoja: (HojaInventario) -> Unit = {},
    val onCerrarHoja: () -> Unit = {},
    val onAgregar: () -> Unit = {},
    /** P29: crear un insumo (categoría «Insumos»). */
    val onEliminarFicha: () -> Unit = {},
    val onEliminarSeleccion: () -> Unit = {},
    val onConfirmarEliminar: () -> Unit = {},
    val onCancelarEliminar: () -> Unit = {},
    val onComentarioPromocion: (String) -> Unit = {},
    val onExportar: (FormatoSalida) -> Unit = {},
    val onGuardar: (FormatoSalida) -> Unit = {},
    val onCompartirFicha: (FormatoSalida) -> Unit = {},
    /** Modo venta (Prompt 13). */
    val onContinuarVenta: () -> Unit = {},
    val onAtras: () -> Unit = {},
    /** P18 (A23): «Ayuda» desde el estado vacío. */
    val onAyuda: (() -> Unit)? = null,
)

@Composable
fun InventarioScreen(
    onNavigate: (Route) -> Unit,
    mensajeInicial: String? = null,
    onMensajeMostrado: () -> Unit = {},
    /** Modo venta: el usuario confirmó la selección (ids en orden de marcado). */
    onSeleccionVenta: (List<Long>) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: InventarioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    // CreateDocument fija el tipo MIME al crearse: uno por formato guardable.
    val guardarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExport.PDF.mime)) { viewModel.guardarEn(it?.toString()) }
    val guardarXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(FormatoExport.XLSX.mime)) { viewModel.guardarEn(it?.toString()) }
    LaunchedEffect(mensajeInicial) {
        if (mensajeInicial != null) { onMensajeMostrado(); snackbar.showSnackbar(mensajeInicial) }
    }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoInventario.Compartir ->
                    if (!Compartir.archivos(context, e.archivos, e.mime, e.asunto)) snackbar.showSnackbar(Compartir.SIN_APLICACION)
                is EventoInventario.GuardarComo -> (if (e.mime == FormatoExport.XLSX.mime) guardarXlsx else guardarPdf).launch(e.nombre)
                is EventoInventario.Navegar -> onNavigate(e.route)
                is EventoInventario.Mensaje -> snackbar.showSnackbar(e.texto)
                is EventoInventario.SeleccionVenta -> onSeleccionVenta(e.ids)
            }
        }
    }
    InventarioContent(
        state = state,
        snackbar = snackbar,
        acciones = AccionesInventario(
            onAyuda = { onNavigate(Route.Ayuda) },
            onBuscar = viewModel::buscar, onFiltro = viewModel::aplicarFiltro, onQuitarFiltros = viewModel::quitarFiltros,
            onReintentar = viewModel::reintentar, onAlternar = viewModel::alternar, onSeleccionarTodo = viewModel::seleccionarTodo,
            onLimpiarSeleccion = viewModel::limpiarSeleccion, onAbrirFicha = viewModel::abrirFicha, onCerrarFicha = viewModel::cerrarFicha,
            onEditar = viewModel::editar, onHoja = viewModel::mostrarHoja, onCerrarHoja = viewModel::cerrarHoja,
            onAgregar = viewModel::agregar,
            onEliminarFicha = viewModel::pedirEliminarFicha,
            onEliminarSeleccion = viewModel::pedirEliminarSeleccion, onConfirmarEliminar = viewModel::confirmarEliminar,
            onCancelarEliminar = viewModel::cancelarEliminar, onComentarioPromocion = viewModel::comentarioPromocion, onExportar = viewModel::exportar, onGuardar = viewModel::pedirGuardar,
            onCompartirFicha = viewModel::compartirFicha,
            onContinuarVenta = viewModel::confirmarSeleccionVenta, onAtras = onBack,
        ),
    )
}

@Composable
fun InventarioContent(
    state: InventarioUiState,
    acciones: AccionesInventario,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val venta = state.modoVenta
    // 1.0.2: ayuda de la pestaña (botón «?» de la esquina superior izquierda).
    val ayudaPestana = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Scaffold(
        topBar = {
            if (venta != null) {
                ShalevTopBar(title = SeleccionVenta.titulo(venta), onBack = acciones.onAtras, actions = {
                    BotonFiltro(state.filtro.activos, TextosInventario.FILTRAR, onClick = { acciones.onHoja(HojaInventario.FILTRO) }, modifier = Modifier.testTag(InventarioTags.FILTRO))
                })
            } else ShalevTopBar(
                title = TextosInventario.TITULO,
                onAyuda = { ayudaPestana.value = true },
                ayudaDescripcion = "Ayuda de Inventario",
                actions = {
                    BotonFiltro(state.filtro.activos, TextosInventario.FILTRAR, onClick = { acciones.onHoja(HojaInventario.FILTRO) }, modifier = Modifier.testTag(InventarioTags.FILTRO))
                    if (LocalPermisosApp.current.exportar) ShalevIconAction(
                        ShalevIcons.Compartir, TextosInventario.EXPORTAR, onClick = { acciones.onHoja(HojaInventario.EXPORTAR) },
                        enabled = state.items.isNotEmpty() || state.seleccion.isNotEmpty(), modifier = Modifier.testTag(InventarioTags.EXPORTAR),
                    )
                }
            )
        },
        bottomBar = {
            if (venta != null) BarraContinuarVenta(
                texto = SeleccionVenta.continuar(state.seleccion.size), haySeleccion = state.seleccion.isNotEmpty(),
                onLimpiar = acciones.onLimpiarSeleccion, onContinuar = acciones.onContinuarVenta,
                tagContinuar = InventarioTags.CONTINUAR_VENTA,
            )
        },
        // 0.26.0 (P73): el «+» va en la esquina inferior (estándar M3); la lista deja ShalevSize.fabClearance abajo
        // para que la última fila (importes a la derecha) no quede tapada.
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            if (venta == null && state.seleccion.isEmpty() && LocalPermisosApp.current.editarInventario) {
                ShalevFab(
                    ShalevIcons.Agregar, TextosInventario.AGREGAR, onClick = acciones.onAgregar,
                    // 1.0.2: el botón flotante lleva texto corto; la descripción completa queda para TalkBack.
                    etiqueta = "Agregar", modifier = Modifier.testTag(InventarioTags.AGREGAR),
                )
            }
        },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.trabajando) ShalevLinearProgress(Modifier.fillMaxWidth())
            ElementosFijados(state, acciones)
            Buscador(state, acciones)
            if (venta == null && state.seleccion.isNotEmpty()) BarraSeleccion(
                texto = TextosInventario.seleccionados(state.seleccion.size), todosMarcados = state.todosVisiblesSeleccionados,
                onLimpiar = acciones.onLimpiarSeleccion, onMarcarTodo = acciones.onSeleccionarTodo,
                onExportar = { acciones.onHoja(HojaInventario.EXPORTAR) }, onEliminar = acciones.onEliminarSeleccion,
                tag = InventarioTags.SELECCION, tagMarcarTodo = InventarioTags.SELECCIONAR_TODO,
                tagEliminar = InventarioTags.ELIMINAR_SELECCION,
            )
            cu.shalev.designsystem.component.BloquearGestos(state.seleccion.isNotEmpty()) // 0.27.0 (T5)
            Box(Modifier.weight(1f)) {
                when (val v = state.vista) {
                    EstadoCarga.Idle, EstadoCarga.Cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ShalevLoading() }
                    is EstadoCarga.Error -> ShalevEmptyState(title = "Algo salió mal", detail = v.mensaje, ilustracion = ShalevIlustracion.Error) {
                        ShalevSecondaryButton("Reintentar", icon = ShalevIcons.Reintentar, onClick = acciones.onReintentar)
                    }
                    // P24: sin botón «Agregar» aquí: repetía el botón flotante (+), que sigue visible.
                    is EstadoCarga.Vacio -> ShalevEmptyState(
                        title = TextosInventario.VACIO_TITULO, detail = TextosInventario.VACIO_DETALLE, ayuda = acciones.onAyuda,
                        ilustracion = ShalevIlustracion.Inventario,
                    )
                    is EstadoCarga.Exito -> when {
                        v.datos.items.isEmpty() -> ShalevEmptyState(
                            title = TextosInventario.SIN_RESULTADOS_TITULO, detail = TextosInventario.SIN_RESULTADOS_DETALLE,
                            ilustracion = ShalevIlustracion.SinResultados,
                        ) {
                            ShalevSecondaryButton(
                                TextosInventario.QUITAR_FILTROS, icon = ShalevIcons.QuitarFiltros, onClick = { acciones.onBuscar(""); acciones.onQuitarFiltros() },
                                modifier = Modifier.testTag(InventarioTags.QUITAR_FILTROS),
                            )
                        }
                        v.datos.items.none { it.producto.id !in state.seleccion } -> ShalevEmptyState(
                            title = TextosInventario.SELECCIONADOS_TITULO,
                            detail = TextosInventario.SELECCIONADOS_DETALLE,
                            ilustracion = ShalevIlustracion.SinResultados,
                        )
                        else -> Tabla(state, acciones)
                    }
                }
            }
        }
    }

    state.ficha?.let { Ficha(it, state, acciones) }
    state.fichaInsumo?.let { FichaDeInsumo(it, state, acciones) }
    when (state.hoja) {
        HojaInventario.FILTRO -> HojaFiltro(state, acciones)
        HojaInventario.EXPORTAR -> HojaExportar(state, acciones)
        HojaInventario.COMPARTIR_FICHA -> if (state.ficha != null) HojaCompartirFicha(state, acciones)
        null -> Unit
    }
    state.confirmar?.let { c ->
        ShalevDialog(
            title = when (c) {
                is ConfirmarEliminar.Uno -> TextosInventario.ELIMINAR_UNO
                is ConfirmarEliminar.Seleccion -> TextosInventario.eliminarVarios(c.cantidad)
            },
            text = (if (c is ConfirmarEliminar.Uno) "${c.nombre}. " else "") + TextosInventario.ELIMINAR_DETALLE,
            onDismiss = acciones.onCancelarEliminar,
            onConfirm = acciones.onConfirmarEliminar,
            confirmDescription = "Eliminar",
            destructive = true,
        )
    }

    if (ayudaPestana.value) cu.shalev.app.ayuda.DialogoAyudaPestana(
        pestana = cu.shalev.app.ayuda.PestanaAyuda.INVENTARIO,
        modulos = LocalPermisosApp.current.modulos,
        onDismiss = { ayudaPestana.value = false },
        onVerManual = acciones.onAyuda?.let { ir -> { ayudaPestana.value = false; ir() } },
    )
}

@Composable
private fun Buscador(state: InventarioUiState, acciones: AccionesInventario) {
    Column(Modifier.padding(horizontal = ShalevSpacing.md), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        ShalevTextField(
            filtro = FiltroEntrada.BUSQUEDA,
            value = state.filtro.texto, onValueChange = acciones.onBuscar, label = "Buscar",
            leadingIcon = ShalevIcons.Buscar, anchoCompleto = true,
            modifier = Modifier.fillMaxWidth().testTag(InventarioTags.BUSCAR),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md, Alignment.CenterHorizontally)) { // P28: contador (+ filtro) centrados
            if (state.vista is EstadoCarga.Exito) ShalevSecondaryText(TextosInventario.contador(state.items.size, state.total))
            resumenFiltro(state.filtro)?.let { r ->
                ShalevChip(label = r, selected = true, onClick = acciones.onQuitarFiltros, icon = ShalevIcons.Limpiar, supportingLabel = "Quitar filtro")
            }
        }
    }
}

@Composable
private fun ElementosFijados(state: InventarioUiState, acciones: AccionesInventario) {
    val fijados = state.elementosFijados
    if (fijados.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md).testTag(InventarioTags.FIJADOS),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
    ) {
        Text(
            TextosInventario.seleccionados(fijados.size),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { heading() },
        )
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = ShalevSize.listaCortaMax),
            contentPadding = PaddingValues(vertical = ShalevSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            items(fijados, key = { "fijado_${it.producto.id}" }) { i ->
                Fila(i, marcado = true, acciones = acciones, modoVenta = state.modoVenta != null, modifier = shalevAnimateItem())
            }
        }
    }
}

@Composable
private fun Tabla(state: InventarioUiState, acciones: AccionesInventario) {
    val visibles = state.items.filterNot { it.producto.id in state.seleccion }
    LazyColumn(
        Modifier.fillMaxSize().testTag(InventarioTags.LISTA),
        contentPadding = PaddingValues(start = ShalevSpacing.md, end = ShalevSpacing.md, top = ShalevSpacing.xs, bottom = ShalevSize.fabClearance),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
    ) {
        items(visibles, key = { it.producto.id }) { i ->
            Fila(i, marcado = false, acciones = acciones, modoVenta = state.modoVenta != null, modifier = shalevAnimateItem())
        }
    }
}

/**
 * 0.31.0 (P2): fila del Inventario. Debajo del nombre van la cantidad en stock (o para cuántos alcanza, en un
 * Elaborado) con su unidad, y debajo la categoría. En el extremo derecho, el precio de costo y el de venta, que se
 * distinguen solo por un tono más suave del primero. La alerta (stock, caducidad, nombre repetido) sigue al final
 * para que el color nunca sea el único aviso.
 */
@Composable
private fun Fila(i: ItemInventario, marcado: Boolean, acciones: AccionesInventario, modoVenta: Boolean = false, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val estado = if (modoVenta && !SeleccionVenta.vendible(i)) SeleccionVenta.motivo(i) else descripcionEstado(i)
    val costo = costoFila(i)
    val venta = ventaFila(i)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (marcado) cs.primaryContainer else Color.Transparent)
            .clickable(role = Role.Button) { if (modoVenta) acciones.onAlternar(i.producto.id) else acciones.onAbrirFicha(i.producto.id) }
            .heightIn(min = ShalevSize.touchTarget)
            .padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs)
            .testTag(InventarioTags.fila(i.producto.id))
            .semantics(mergeDescendants = true) {
                this.selected = marcado
                if (estado != null) stateDescription = estado
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
    ) {
        Box(
            Modifier.size(ShalevSize.indicatorWidth, ShalevSize.indicatorHeight)
                .clip(RoundedCornerShape(ShalevRadius.sm))
                .background(colorDe(tonoDe(i))),
        )
        cu.shalev.app.common.ConMiniatura(i.producto.fotoUri) { // 0.21.0 (C11)
            ShalevCheckbox(
                checked = marcado,
                onCheckedChange = { acciones.onAlternar(i.producto.id) },
                modifier = Modifier.testTag(InventarioTags.check(i.producto.id))
                    .semantics { contentDescription = "Seleccionar ${i.producto.nombreCompleto}" },
            )
        }
        Column(Modifier.weight(1f)) {
            Text(i.producto.nombreCompleto, style = MaterialTheme.typography.bodyLarge) // 0.24.0: «Nombre · Descripción»
            // 0.28.0: la fecha de caducidad («Vence 03/10/2026») va en seminegrita.
            subtituloFila(i)?.let { subt ->
                // El aviso (caducidad, stock, nombre repetido) siempre en 2 líneas: la fecha de
                // vencimiento no debe cortarse con "…" (02a: «Crítico · Vence 0…» ilegible).
                ShalevSecondaryText(
                    ShalevTextos.resaltar(subt, cu.shalev.app.common.FechasUi.texto(i.producto.fechaCaducidad)),
                    maxLines = 2,
                )
            }
            ShalevSecondaryText(valorFila(i)) // cantidad con su unidad (u, L, g, Kg, cajas…)
            ShalevSecondaryText(categoriaFila(i))
        }
        Column(horizontalAlignment = Alignment.End) {
            venta?.let {
                Text(
                    Money.format(it), style = ShalevTextos.datoEn(MaterialTheme.typography.bodyLarge),
                    color = cs.onSurface, textAlign = TextAlign.End,
                )
            }
            // El costo va en el mismo sitio, un tono más suave: es el dato secundario de la fila.
            ShalevSecondaryText(Money.format(costo), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun colorDe(t: TonoFila): Color = when (t) {
    TonoFila.NORMAL -> MaterialTheme.colorScheme.primary
    TonoFila.AVISO -> MaterialTheme.colorScheme.tertiary
    TonoFila.PELIGRO -> MaterialTheme.colorScheme.error
}

// ---------------- Ficha ----------------

@Composable
private fun Ficha(f: FichaProducto, state: InventarioUiState, acciones: AccionesInventario) {
    val p = f.producto
    ShalevBottomSheet(
        onDismiss = acciones.onCerrarFicha,
        title = p.nombreCompleto,
        footer = {
            val permisos = LocalPermisosApp.current
            if (permisos.editarInventario) ShalevIconAction(ShalevIcons.Editar, "Editar", onClick = { acciones.onEditar(p.id) }, style = IconActionStyle.Tonal, modifier = Modifier.testTag(InventarioTags.FICHA_EDITAR))
            if (permisos.exportar) ShalevIconAction(
                ShalevIcons.Compartir, "Compartir", onClick = { acciones.onHoja(HojaInventario.COMPARTIR_FICHA) },
                style = IconActionStyle.Tonal, enabled = !state.trabajando, modifier = Modifier.testTag(InventarioTags.FICHA_COMPARTIR),
            )
            if (permisos.editarInventario) ShalevIconAction(ShalevIcons.Eliminar, "Eliminar", onClick = acciones.onEliminarFicha, style = IconActionStyle.Tonal, modifier = Modifier.testTag(InventarioTags.FICHA_ELIMINAR))
        },
    ) {
        Column(Modifier.testTag(InventarioTags.FICHA), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            p.fotoUri?.let { uri ->
                AsyncImage(
                    model = uri, contentDescription = "Foto de ${p.nombre}", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(ShalevRadius.lg)),
                )
            }
            descripcionEstado(ItemInventario(p, f.nivel, f.caducidad))?.let {
                ShalevCard(tone = CardTone.Tonal) { Text("Atención: $it", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
            TablasExport.camposFicha(f, paraClientes = false).drop(1).forEach { (campo, valor) ->
                ShalevListItem(title = campo, value = valor, indicatorColor = null, valueMaxLines = 4) // ficha: receta y descripción enteras
            }
            ShalevListItem(
                title = "Ganancia por unidad", value = Money.format(f.ganancia), indicatorColor = null,
                valueColor = if (f.ganancia.isNegative) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }
    }
}

/** P29: ficha de un insumo (antes en Elaboración). 0.26.0: ya no se comparte (era solo texto). */
@Composable
private fun FichaDeInsumo(f: FichaInsumo, state: InventarioUiState, acciones: AccionesInventario) {
    val i = f.insumo
    val id = IdArticulo.deInsumo(i.id)
    ShalevBottomSheet(
        onDismiss = acciones.onCerrarFicha,
        title = i.nombre,
        footer = {
            val permisos = LocalPermisosApp.current
            if (permisos.editarInventario) ShalevIconAction(ShalevIcons.Editar, "Editar", onClick = { acciones.onEditar(id) }, style = IconActionStyle.Tonal, modifier = Modifier.testTag(InventarioTags.FICHA_EDITAR))
            if (permisos.editarInventario) ShalevIconAction(ShalevIcons.Eliminar, "Eliminar", onClick = acciones.onEliminarFicha, style = IconActionStyle.Tonal, modifier = Modifier.testTag(InventarioTags.FICHA_ELIMINAR))
        },
    ) {
        Column(Modifier.testTag(InventarioTags.FICHA), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            descripcionNivel(f.nivel)?.let {
                ShalevCard(tone = CardTone.Tonal) { Text("Atención: $it", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
            TablasExport.camposInsumo(f).drop(1).forEach { (campo, valor) ->
                ShalevListItem(title = campo, value = valor, indicatorColor = null, valueMaxLines = 4)
            }
            if (f.usadoEn.isEmpty()) ShalevSecondaryText("Todavía no lo usa ninguna receta ni servicio.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ---------------- Hojas ----------------

@Composable
private fun HojaFiltro(state: InventarioUiState, acciones: AccionesInventario) {
    var borrador by remember(state.filtro) { mutableStateOf(state.filtro) }
    val alertas: List<TipoAlerta?> = ALERTAS_FILTRO
    val categorias = (state.categorias + listOfNotNull(borrador.categoria)).distinctBy { it.lowercase() }
    ShalevBottomSheet(
        onDismiss = acciones.onCerrarHoja,
        title = TextosInventario.FILTRAR,
        footer = {
            ShalevSecondaryButton("Quitar filtros", icon = ShalevIcons.QuitarFiltros, onClick = { borrador = FiltroInventario(texto = borrador.texto) })
            ShalevPrimaryButton("Aplicar", icon = ShalevIcons.Aplicar, onClick = { acciones.onFiltro(borrador) }, modifier = Modifier.testTag(InventarioTags.FILTRO_APLICAR))
        },
    ) {
        ShalevComboBox(
            label = "Estado",
            opciones = alertas,
            seleccion = borrador.alerta,
            etiqueta = { a -> a?.let { etiqueta(it) } ?: "Todos" },
            onSeleccion = { borrador = borrador.copy(alerta = it) },
            leadingIcon = ShalevIcons.Alerta,
            tagOpcion = { a -> a?.let(InventarioTags::alertaOpcion) ?: InventarioTags.ALERTA_TODAS },
            modifier = Modifier.fillMaxWidth().testTag(InventarioTags.FILTRO_ESTADO),
        )
        ShalevComboBox(
            label = "Tipo",
            opciones = TipoArticulo.entries,
            seleccion = borrador.tipo,
            etiqueta = ::etiqueta,
            onSeleccion = { borrador = borrador.copy(tipo = it) },
            leadingIcon = ShalevIcons.Inventario,
            tagOpcion = InventarioTags::tipoOpcion,
            modifier = Modifier.fillMaxWidth().testTag(InventarioTags.FILTRO_TIPO),
        )
        if (categorias.isNotEmpty()) {
            val opcionesCategoria: List<String?> = listOf(null) + categorias
            ShalevComboBox(
                label = "Categoría",
                opciones = opcionesCategoria,
                seleccion = borrador.categoria,
                etiqueta = { it ?: "Todas" },
                onSeleccion = { borrador = borrador.copy(categoria = it) },
                leadingIcon = ShalevIcons.Lista,
                tagOpcion = { it?.let(InventarioTags::categoriaOpcion) ?: InventarioTags.CATEGORIA_TODAS },
                modifier = Modifier.fillMaxWidth().testTag(InventarioTags.FILTRO_CATEGORIA),
            )
        }
    }
}

@Composable
private fun HojaExportar(state: InventarioUiState, acciones: AccionesInventario) {
    val n = if (state.seleccion.isNotEmpty()) state.seleccion.size else state.items.size
    val promocionable = (if (state.seleccion.isNotEmpty()) state.elementosFijados else state.items).any { !it.producto.esInsumo }
    ShalevBottomSheet(onDismiss = acciones.onCerrarHoja, title = TextosInventario.EXPORTAR) {
        ShalevSecondaryText(
            if (state.seleccion.isNotEmpty()) "${TextosInventario.productos(n)} seleccionados" else "Lo que ves: ${TextosInventario.productos(n)}",
        )
        if (promocionable) ShalevTextField(
            value = state.comentarioPromocion, onValueChange = acciones.onComentarioPromocion,
            label = "Encabezado de promoción (opcional)",
        )
        FormatoSalida.EXPORTAR.filter { it.interno || promocionable }.forEach { f ->
            ShalevListItem(
                title = f.etiqueta, subtitle = f.destinatario, indicatorColor = null,
                leading = cu.shalev.app.common.iconoFormato(f)?.let { ic -> { Icon(ic, contentDescription = null, modifier = Modifier.size(ShalevSize.icon)) } },
                modifier = Modifier.testTag(InventarioTags.formato(f)),
                trailing = {
                    Row {
                        ShalevIconAction(ShalevIcons.Compartir, "Compartir ${f.etiqueta}", onClick = { acciones.onExportar(f) }, etiqueta = "Compartir")
                        if (f.guardable) ShalevIconAction(
                            ShalevIcons.Exportar, "Guardar ${f.etiqueta} en el dispositivo", onClick = { acciones.onGuardar(f) }, etiqueta = "Guardar",
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun HojaCompartirFicha(state: InventarioUiState, acciones: AccionesInventario) {
    ShalevBottomSheet(onDismiss = acciones.onCerrarHoja, title = "Compartir") {
        ShalevTextField(value = state.comentarioPromocion, onValueChange = acciones.onComentarioPromocion,
            label = "Encabezado de promoción (opcional)")
        FormatoSalida.COMPARTIR_FICHA.forEach { f ->
            ShalevListItem(
                title = f.etiqueta, subtitle = if (f == FormatoSalida.PDF) FormatoSalida.USO_INTERNO else null,
                indicatorColor = null, onClick = { acciones.onCompartirFicha(f) },
                leading = { Icon(cu.shalev.app.common.iconoFormato(f) ?: ShalevIcons.Compartir, contentDescription = null, modifier = Modifier.size(ShalevSize.icon)) },
                modifier = Modifier.testTag(InventarioTags.formato(f)),
            )
        }
    }
}

/** Opacidad del velo bajo el «+» abierto (la de los diálogos de Material 3). */
