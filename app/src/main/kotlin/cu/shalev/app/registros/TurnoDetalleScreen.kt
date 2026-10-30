package cu.shalev.app.registros

import androidx.compose.foundation.layout.size
import cu.shalev.designsystem.token.ShalevSize
import androidx.compose.material3.Icon
import cu.shalev.designsystem.component.ShalevTextoAjustable
import cu.shalev.designsystem.theme.ShalevTextos
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.core.money.Money
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevEmptyState
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevLoading
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevTab
import cu.shalev.designsystem.component.ShalevTabs
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.ilustracion.ShalevIlustracion
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.DetalleTurno
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.TipoEntidad
import java.time.Instant
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import cu.shalev.app.common.Compartir
import cu.shalev.app.common.LocalPermisosApp
import cu.shalev.designsystem.component.ShalevBottomSheet
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevLinearProgress
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.domain.service.FormatoExport
import java.time.ZoneId

object TurnoDetalleTags {
    const val ARQUEO = "turno_arqueo"
    const val MOV_CAJA = "turno_mov_caja"
    const val LISTA = "turno_lista"
    const val TOTAL = "turno_detalle.total"
    const val CABECERA = "turno_cabecera"
    const val PROVISIONAL = "turno_provisional"
    const val TOTALES = "turno_totales"
    const val METODOS = "turno_metodos"
    const val VENDIDOS = "turno_vendidos"
    const val INSUMOS = "turno_insumos"
    const val MOV_PRODUCTOS = "turno_mov_productos"
    const val MOV_INSUMOS = "turno_mov_insumos"
    const val VENTAS = "turno_ventas"
    const val REINTENTAR = "turno_reintentar"
    fun pestana(p: PestanaTurno) = "turno_pestana_${p.name.lowercase()}"
    const val COMPARTIR = "turno_compartir"
    const val EXPORTANDO = "turno_exportando"
    fun formato(f: FormatoExport) = "turno_formato_${f.extension}"
    fun enviar(f: FormatoExport) = "turno_enviar_${f.extension}"
    fun guardar(f: FormatoExport) = "turno_guardar_${f.extension}"
}

/** 0.25.1: acciones de «Compartir turno» (PDF o Excel; enviar a otra app o guardar en el teléfono). */
class AccionesExportarTurno(
    val onAbrir: () -> Unit,
    val onCerrar: () -> Unit,
    val onEnviar: (FormatoExport) -> Unit,
    val onGuardar: (FormatoExport) -> Unit,
)

@Composable
fun TurnoDetalleScreen(onBack: () -> Unit, viewModel: TurnoDetalleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exportacion by viewModel.exportacion.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refrescar() }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
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
    TurnoDetalleContent(
        state, onBack = onBack, onReintentar = viewModel::reintentar, exportacion = exportacion, snackbar = snackbar,
        exportar = AccionesExportarTurno(viewModel::abrirExportar, viewModel::cerrarExportar, viewModel::enviar, viewModel::pedirGuardar),
    )
}

@Composable
fun TurnoDetalleContent(
    state: EstadoCarga<DetalleTurno>,
    onBack: () -> Unit,
    onReintentar: () -> Unit,
    zona: ZoneId = ZoneId.systemDefault(),
    ahora: Instant = Instant.now(),
    pestanaInicial: PestanaTurno = PestanaTurno.RESUMEN,
    exportacion: ExportacionTurnoUi = ExportacionTurnoUi(),
    exportar: AccionesExportarTurno? = null,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val titulo = (state as? EstadoCarga.Exito)?.datos?.turno?.let { tituloDetalleTurno(it, zona) } ?: "Turno"
    // P38: sin el permiso «Exportar» (secundaria) no se comparte; la secundaria solo tiene sus propios turnos.
    val puedeExportar = exportar != null && state is EstadoCarga.Exito && LocalPermisosApp.current.exportar
    Scaffold(
        topBar = {
            ShalevTopBar(title = titulo, onBack = onBack, actions = {
                if (puedeExportar) ShalevIconAction(
                    ShalevIcons.Compartir, TextosTurno.COMPARTIR, onClick = exportar!!.onAbrir, enabled = !exportacion.exportando,
                    modifier = Modifier.testTag(TurnoDetalleTags.COMPARTIR),
                )
            })
        },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (exportacion.exportando) {
                ShalevLinearProgress(Modifier.testTag(TurnoDetalleTags.EXPORTANDO).semantics { contentDescription = TextosRegistros.EXPORTANDO })
            }
            Box(Modifier.fillMaxSize()) {
                when (state) {
                    EstadoCarga.Idle, EstadoCarga.Cargando, is EstadoCarga.Vacio ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ShalevLoading() }
                    is EstadoCarga.Error -> ShalevEmptyState(
                        title = "Algo salió mal", detail = state.mensaje,
                        ilustracion = if (state.mensaje == TextosTurno.NO_ENCONTRADO) ShalevIlustracion.NoExiste else ShalevIlustracion.Error,
                    ) {
                        if (state.mensaje != TextosTurno.NO_ENCONTRADO) {
                            ShalevSecondaryButton("Reintentar", icon = ShalevIcons.Reintentar, onClick = onReintentar, modifier = Modifier.testTag(TurnoDetalleTags.REINTENTAR))
                        }
                    }
                    is EstadoCarga.Exito -> Detalle(state.datos, zona, ahora, pestanaInicial)
                }
            }
        }
    }
    if (puedeExportar && exportacion.hoja) HojaExportarTurno((state as EstadoCarga.Exito).datos, exportar!!)
}

/** 0.25.1: PDF o Excel del turno completo; cada formato se envía a otra app o se guarda en el teléfono. */
@Composable
private fun HojaExportarTurno(d: DetalleTurno, a: AccionesExportarTurno) {
    ShalevBottomSheet(onDismiss = a.onCerrar, title = TextosTurno.COMPARTIR) {
        ShalevSecondaryText(TextosTurno.alcanceExportar(d))
        listOf(FormatoExport.PDF, FormatoExport.XLSX).forEach { f ->
            val etiqueta = if (f == FormatoExport.PDF) "PDF" else "Excel"
            ShalevListItem(
                title = etiqueta, indicatorColor = null,
                leading = { Icon(cu.shalev.app.common.iconoFormato(f), contentDescription = null, modifier = Modifier.size(ShalevSize.icon)) },
                modifier = Modifier.testTag(TurnoDetalleTags.formato(f)),
                trailing = {
                    Row {
                        ShalevIconAction(ShalevIcons.Compartir, "Enviar $etiqueta a otra app", etiqueta = "Enviar", onClick = { a.onEnviar(f) }, modifier = Modifier.testTag(TurnoDetalleTags.enviar(f)))
                        ShalevIconAction(ShalevIcons.Exportar, "Guardar $etiqueta en el teléfono", etiqueta = "Guardar", onClick = { a.onGuardar(f) }, modifier = Modifier.testTag(TurnoDetalleTags.guardar(f)))
                    }
                },
            )
        }
    }
}

/** P24: el detalle tenía 7 tablas seguidas; ahora se agrupan en pestañas (Resumen · Ventas · Inventario). */
enum class PestanaTurno(val etiqueta: String) { RESUMEN("Resumen"), VENTAS("Ventas"), INVENTARIO("Inventario"), CAJA("Caja") }

@Composable
private fun Detalle(d: DetalleTurno, zona: ZoneId, ahora: Instant, pestanaInicial: PestanaTurno) {
    var pestana by rememberSaveable { mutableStateOf(pestanaInicial) }
    val orden = PestanaTurno.entries
    Column(Modifier.fillMaxSize()) {
        ShalevTabs(
            tabs = listOf(
                ShalevTab(PestanaTurno.RESUMEN.etiqueta, testTag = TurnoDetalleTags.pestana(PestanaTurno.RESUMEN)),
                ShalevTab(PestanaTurno.VENTAS.etiqueta, count = d.ventas.size, testTag = TurnoDetalleTags.pestana(PestanaTurno.VENTAS)),
                ShalevTab(
                    PestanaTurno.INVENTARIO.etiqueta, count = d.movimientosProducto.size + d.movimientosInsumo.size,
                    testTag = TurnoDetalleTags.pestana(PestanaTurno.INVENTARIO),
                ),
                // 0.25.0 (§5.2): arqueo de caja y entradas/salidas de efectivo.
                ShalevTab(PestanaTurno.CAJA.etiqueta, count = d.caja.size.takeIf { it > 0 }, testTag = TurnoDetalleTags.pestana(PestanaTurno.CAJA)),
            ),
            selectedIndex = orden.indexOf(pestana),
            onSelect = { pestana = orden[it] },
            modifier = Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.xs),
        )
        LazyColumn(
            Modifier.fillMaxSize().testTag(TurnoDetalleTags.LISTA),
            contentPadding = PaddingValues(ShalevSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        ) {
            when (pestana) {
                PestanaTurno.RESUMEN -> resumen(d, zona, ahora)
                PestanaTurno.VENTAS -> ventas(d, zona)
                PestanaTurno.INVENTARIO -> inventario(d, zona)
                PestanaTurno.CAJA -> caja(d, zona)
            }
        }
    }
}

private fun LazyListScope.resumen(d: DetalleTurno, zona: ZoneId, ahora: Instant) {
    val r = d.resumen
    val t = d.turno
    // P28: el total, una sola vez, centrado y arriba (como en Venta).
    item(key = "total") {
        Column(Modifier.fillMaxWidth().testTag(TurnoDetalleTags.TOTAL).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
            ShalevSecondaryText("Total vendido", textAlign = TextAlign.Center)
            ShalevTextoAjustable(Money.format(r.total), style = ShalevTextos.datoEn(MaterialTheme.typography.headlineSmall), textAlign = TextAlign.Center)
        }
    }
    // P28: sin la fila «Abierto/Cerrado · 08:05 – 19:45»: repetía las horas de Apertura y Cierre. Que está abierto lo
    // dicen el color de la tarjeta y la nota de debajo; que está cerrado, la fila Cierre.
    item(key = "cabecera") {
        ShalevCard(modifier = Modifier.testTag(TurnoDetalleTags.CABECERA), tone = if (t.abierto) CardTone.Highlight else CardTone.Default) {
            ShalevListItem(title = "Apertura", subtitle = t.abiertoPor.ifBlank { null }, value = fechaHora(t.abiertoEn, zona), indicatorColor = null)
            t.cerradoEn?.let { c ->
                ShalevListItem(title = "Cierre", subtitle = t.cerradoPor, value = fechaHora(c, zona), indicatorColor = null)
            }
            ShalevListItem(title = "Duración", value = duracionTexto(t.duracion(ahora)), indicatorColor = null)
        }
    }
    if (d.provisional) {
        item(key = "provisional") {
            ShalevSecondaryText(TextosTurno.PROVISIONAL, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag(TurnoDetalleTags.PROVISIONAL))
        }
    }
    item(key = "totales") {
        ShalevCard(title = "Totales", titleCentered = true, modifier = Modifier.testTag(TurnoDetalleTags.TOTALES)) {
            // P28: sin «Ventas» ni «Movimientos de inventario»: los números ya están en las pestañas de arriba,
            // y el total vendido va arriba, centrado.
            ShalevListItem(title = "Unidades vendidas", value = r.unidades.toString(), indicatorColor = null)
            ShalevListItem(title = "Costo", value = Money.format(r.costo), indicatorColor = null)
            ShalevListItem(
                title = "Ganancia neta", value = Money.format(r.ganancia), indicatorColor = null,
                valueColor = if (r.ganancia.centavos < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
    item(key = "metodos") {
        ShalevCard(title = "Métodos de pago", titleCentered = true, modifier = Modifier.testTag(TurnoDetalleTags.METODOS)) {
            ShalevListItem(title = etiqueta(MetodoPago.EFECTIVO), value = Money.format(r.totalEfectivo), indicatorColor = null)
            ShalevListItem(title = etiqueta(MetodoPago.TRANSFERENCIA), value = Money.format(r.totalTransferencia), indicatorColor = null)
        }
    }
    if (d.vacio) {
        item(key = "vacio") { ShalevSecondaryText(TextosTurno.SIN_ACTIVIDAD, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
}

private fun LazyListScope.ventas(d: DetalleTurno, zona: ZoneId) {
    if (d.ventas.isEmpty()) {
        item(key = "sin_ventas") { ShalevEmptyState(title = TextosTurno.SIN_VENTAS, ilustracion = ShalevIlustracion.Carrito) }
        return
    }
    seccion("vendidos", "Productos vendidos", TurnoDetalleTags.VENDIDOS, d.vendidos.isNotEmpty()) {
        d.vendidos.forEach { ShalevListItem(title = it.nombre, value = unidadesTexto(it.unidades), indicatorColor = null) }
    }
    seccion("ventas", "Ventas", TurnoDetalleTags.VENTAS, true) {
        d.ventas.forEach { v ->
            // Los artículos ya no se ven en la fila (solo hora y total); TalkBack los sigue leyendo.
            // 0.25.0: la anulada sigue en la lista, marcada (no suma en los totales).
            ShalevListItem(
                title = tituloVenta(v, zona), tituloEsDato = true, value = Money.format(v.total), indicatorColor = null,
                subtitle = v.anulacion?.let { "ANULADA · ${it.motivo}" } ?: v.corrigeVentaId?.let { "Corrige #$it" },
                valueColor = if (v.anulada) MaterialTheme.colorScheme.onSurfaceVariant else androidx.compose.ui.graphics.Color.Unspecified,
                subtitleMaxLines = 2,
                modifier = Modifier.semantics { stateDescription = (if (v.anulada) "Anulada. " else "") + subtituloVenta(v) },
            )
        }
    }
}

private fun LazyListScope.caja(d: DetalleTurno, zona: ZoneId) {
    item(key = "arqueo") {
        ShalevCard(title = "Arqueo de caja", titleCentered = true, modifier = Modifier.testTag(TurnoDetalleTags.ARQUEO)) {
            cu.shalev.app.caja.TablaArqueo(d.arqueo)
        }
    }
    d.turno.conteoCierre?.let { conteo ->
        item(key = "conteo_cierre") {
            ShalevCard { cu.shalev.app.caja.ResumenConteo(conteo) }
        }
    }
    seccion("mov_caja", "Entradas y salidas de efectivo", TurnoDetalleTags.MOV_CAJA, d.caja.isNotEmpty()) {
        d.caja.forEach { m ->
            ShalevListItem(
                title = m.motivo,
                subtitleResaltado = ShalevTextos.resaltar(
                    listOf(cu.shalev.app.caja.TextosCaja.tipo(m.tipo), hora(m.fecha, zona), m.hechoPor).filter { it.isNotBlank() }.joinToString(" · "),
                    hora(m.fecha, zona),
                ),
                value = (if (m.tipo == cu.shalev.domain.model.TipoMovimientoCaja.SALIDA) "−" else "+") + Money.format(m.importe),
                indicatorColor = null, subtitleMaxLines = 2,
            )
        }
    }
}

private fun LazyListScope.inventario(d: DetalleTurno, zona: ZoneId) {
    if (d.insumos.isEmpty() && d.movimientosProducto.isEmpty() && d.movimientosInsumo.isEmpty()) {
        item(key = "sin_mov") { ShalevEmptyState(title = TextosTurno.SIN_MOVIMIENTOS, ilustracion = ShalevIlustracion.Inventario) }
        return
    }
    seccion("insumos", "Insumos (variación neta)", TurnoDetalleTags.INSUMOS, d.insumos.isNotEmpty()) {
        d.insumos.forEach { ShalevListItem(title = it.nombre, value = cantidadInsumo(it.cantidad, it.simbolo), indicatorColor = null) }
    }
    seccion("mov_productos", "Movimientos de productos", TurnoDetalleTags.MOV_PRODUCTOS, d.movimientosProducto.isNotEmpty()) {
        d.movimientosProducto.forEach { m ->
            ShalevListItem(
                title = m.nombre, subtitleResaltado = ShalevTextos.resaltar(subtituloMovimiento(m, zona), hora(m.fecha, zona)),
                value = cantidadConSigno(m.delta, TipoEntidad.PRODUCTO), indicatorColor = null,
            )
        }
    }
    seccion("mov_insumos", "Movimientos de insumos", TurnoDetalleTags.MOV_INSUMOS, d.movimientosInsumo.isNotEmpty()) {
        val simbolos = d.insumos.associate { it.insumoId to it.simbolo }
        d.movimientosInsumo.forEach { m ->
            ShalevListItem(
                title = m.nombre, subtitleResaltado = ShalevTextos.resaltar(subtituloMovimiento(m, zona), hora(m.fecha, zona)),
                value = cantidadConSigno(m.delta, TipoEntidad.INSUMO, simbolos[m.entidadId].orEmpty()), indicatorColor = null,
            )
        }
    }
}

/** Sección en tarjeta; oculta si no hay datos. */
private fun LazyListScope.seccion(key: String, titulo: String, tag: String, visible: Boolean, content: @Composable () -> Unit) {
    if (!visible) return
    item(key = key) {
        ShalevCard(title = titulo, titleCentered = true, modifier = Modifier.testTag(tag)) { content() }
    }
}
