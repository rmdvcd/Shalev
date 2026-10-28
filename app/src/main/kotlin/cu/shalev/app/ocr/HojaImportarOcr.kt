package cu.shalev.app.ocr

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.common.FotosTemporales
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSpacing
import kotlinx.coroutines.launch

/** Línea resuelta para incorporar a una venta abierta respetando la capacidad disponible. */
data class LineaVentaImportadaOcr(
    val articuloId: Long,
    val cantidad: Long,
)

data class ResultadoVentaOcr(
    val lineas: List<LineaVentaImportadaOcr>,
    val clienteNombre: String? = null,
    val clienteTelefono: String? = null,
    val clienteCi: String? = null,
)

/**
 * Asistente a pantalla completa de escaneo OCR gratuito e ilimitado (ML Kit empaquetado) y lectura local de
 * Excel (`.xlsx`/`.csv`), Word (`.docx`) y PDF con mapeo visual manual de regiones y columnas.
 */
@Composable
fun HojaImportarOcr(
    destino: DestinoOcr,
    onDismiss: () -> Unit,
    onImportadoMensaje: (String) -> Unit = {},
    candidatosVenta: List<CandidatoVentaOcr> = emptyList(),
    onAplicarVenta: (ResultadoVentaOcr) -> Unit = {},
    viewModel: ImportarOcrViewModel = hiltViewModel(),
) {
    val vmState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var procesando by remember { mutableStateOf(false) }
    var errorLocal by remember { mutableStateOf<String?>(null) }
    var documento by remember { mutableStateOf<DocumentoEscaneado?>(null) }
    var modoTablaDesdeOcr by remember { mutableStateOf(false) }
    var uriCamaraPendiente by rememberSaveable { mutableStateOf<String?>(null) }

    val campos = remember(destino) { CampoOcr.para(destino) }
    var campoActivo by remember(destino) { mutableStateOf(campos.first()) }
    val asignacionBloque = remember { mutableStateMapOf<Int, CampoOcr>() }
    var borradorActual by remember(destino) { mutableStateOf(BorradorImportacionOcr()) }
    var colaManual by remember { mutableStateOf<List<BorradorImportacionOcr>>(emptyList()) }

    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { ev ->
            when (ev) {
                is EventoImportarOcr.Importado -> {
                    onImportadoMensaje(ev.mensaje)
                    onDismiss()
                }
            }
        }
    }

    fun cargar(accion: suspend () -> Result<DocumentoEscaneado>) {
        scope.launch {
            procesando = true
            errorLocal = null
            viewModel.limpiarError()
            accion()
                .onSuccess { doc ->
                    documento = doc
                    modoTablaDesdeOcr = false
                    asignacionBloque.clear()
                }
                .onFailure { e -> errorLocal = e.message ?: "No se pudo procesar el archivo." }
            procesando = false
        }
    }

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val raw = uriCamaraPendiente
        uriCamaraPendiente = null
        if (ok && raw != null) {
            cargar { MotorOcrAndroid.procesarImagen(context, Uri.parse(raw)).map { it } }
        }
    }
    fun lanzarCamara() {
        val u = FotosTemporales.nuevaUri(context)
        uriCamaraPendiente = u.toString()
        camara.launch(u)
    }
    val permisoCamara = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) lanzarCamara()
        else errorLocal = "Permite la cámara en los ajustes del teléfono para escanear documentos."
    }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) cargar { MotorOcrAndroid.procesarImagen(context, uri).map { it } }
    }
    val selectorArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) cargar { MotorOcrAndroid.procesarDocumento(context, uri) }
    }

    Dialog(
        onDismissRequest = { if (!vmState.guardando && !procesando) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    ShalevTopBar(
                        title = destino.titulo,
                        onBack = { if (!vmState.guardando && !procesando) onDismiss() },
                    )
                },
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
                ) {
                    BarraFuentesOcr(
                        habilitado = !procesando && !vmState.guardando,
                        onCamara = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                lanzarCamara()
                            } else permisoCamara.launch(Manifest.permission.CAMERA)
                        },
                        onGaleria = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onPdf = { selectorArchivo.launch(arrayOf("application/pdf")) },
                        onExcel = {
                            selectorArchivo.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain",
                                ),
                            )
                        },
                        onWord = {
                            selectorArchivo.launch(
                                arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                            )
                        },
                    )

                    val msgError = errorLocal ?: vmState.error
                    if (msgError != null) {
                        Text(
                            text = msgError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    if (procesando) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = ShalevSpacing.lg),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(ShalevSpacing.md))
                            ShalevSecondaryText("Reconociendo texto en el dispositivo…")
                        }
                    }

                    when (val doc = documento) {
                        null -> if (!procesando) {
                            ShalevCard(Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                                    Text(
                                        "Reconocimiento local, gratuito y sin límite",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    ShalevSecondaryText(
                                        "Haz una foto a tus apuntes manuscritos o impresos, o abre un PDF, Excel (.xlsx/.csv) o Word (.docx). " +
                                            "Toca cualquier recuadro detectado o arrastra un rectángulo sobre el documento para asignarlo al campo elegido.",
                                    )
                                }
                            }
                        }

                        is DocumentoEscaneado.Visual -> {
                            val tablaAgrupada = remember(doc.bloques) { MapeadorOcr.agruparBloquesEnTabla(doc.bloques) }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(doc.titulo, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                if (doc.bloques.isNotEmpty()) {
                                    FilterChip(
                                        selected = modoTablaDesdeOcr,
                                        onClick = { modoTablaDesdeOcr = !modoTablaDesdeOcr },
                                        label = { Text(if (modoTablaDesdeOcr) "Lienzo visual" else "Ver como tabla") },
                                    )
                                }
                            }

                            if (doc.totalPaginas > 1 && doc.pdfUri != null) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    ShalevIconAction(
                                        ShalevIcons.Disminuir,
                                        "Página anterior",
                                        enabled = doc.paginaActual > 1 && !procesando,
                                        style = IconActionStyle.Tonal,
                                        onClick = { cargar { MotorOcrAndroid.procesarPdf(context, doc.pdfUri, doc.paginaActual - 1).map { it } } },
                                    )
                                    Spacer(Modifier.width(ShalevSpacing.md))
                                    Text("Página ${doc.paginaActual} de ${doc.totalPaginas}", style = MaterialTheme.typography.labelLarge)
                                    Spacer(Modifier.width(ShalevSpacing.md))
                                    ShalevIconAction(
                                        ShalevIcons.Aumentar,
                                        "Página siguiente",
                                        enabled = doc.paginaActual < doc.totalPaginas && !procesando,
                                        style = IconActionStyle.Tonal,
                                        onClick = { cargar { MotorOcrAndroid.procesarPdf(context, doc.pdfUri, doc.paginaActual + 1).map { it } } },
                                    )
                                }
                            }

                            if (modoTablaDesdeOcr && tablaAgrupada.filas.isNotEmpty()) {
                                MapeadorTablaVisual(
                                    destino = destino,
                                    tablas = listOf(tablaAgrupada),
                                    candidatosVenta = candidatosVenta,
                                    guardando = vmState.guardando,
                                    onConfirmarFilas = { filas ->
                                        ejecutarImportacion(destino, filas, candidatosVenta, viewModel, onAplicarVenta, onDismiss) { errorLocal = it }
                                    },
                                )
                            } else {
                                SeccionLienzoVisual(
                                    destino = destino,
                                    doc = doc,
                                    campos = campos,
                                    campoActivo = campoActivo,
                                    onElegirCampo = { campoActivo = it },
                                    asignacionBloque = asignacionBloque,
                                    borrador = borradorActual,
                                    onCambiarBorrador = { borradorActual = it },
                                    cola = colaManual,
                                    onEncolar = {
                                        colaManual = colaManual + borradorActual
                                        borradorActual = BorradorImportacionOcr()
                                        asignacionBloque.clear()
                                        campoActivo = campos.first()
                                    },
                                    onQuitarDeCola = { idx -> colaManual = colaManual.filterIndexed { i, _ -> i != idx } },
                                    guardando = vmState.guardando,
                                    candidatosVenta = candidatosVenta,
                                    onImportar = {
                                        val lote = if (borradorActual[CampoOcr.NOMBRE].isNotBlank()) colaManual + borradorActual else colaManual
                                        ejecutarImportacion(destino, lote, candidatosVenta, viewModel, onAplicarVenta, onDismiss) { errorLocal = it }
                                    },
                                )
                            }
                        }

                        is DocumentoEscaneado.Tabular -> {
                            MapeadorTablaVisual(
                                destino = destino,
                                tablas = doc.tablas,
                                candidatosVenta = candidatosVenta,
                                guardando = vmState.guardando,
                                onConfirmarFilas = { filas ->
                                    ejecutarImportacion(destino, filas, candidatosVenta, viewModel, onAplicarVenta, onDismiss) { errorLocal = it }
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(ShalevSpacing.lg))
                }
            }
        }
    }
}

private fun ejecutarImportacion(
    destino: DestinoOcr,
    filas: List<BorradorImportacionOcr>,
    candidatosVenta: List<CandidatoVentaOcr>,
    viewModel: ImportarOcrViewModel,
    onAplicarVenta: (ResultadoVentaOcr) -> Unit,
    onDismiss: () -> Unit,
    onError: (String) -> Unit,
) {
    when (destino) {
        DestinoOcr.INVENTARIO -> viewModel.importarInventario(filas)
        DestinoOcr.SERVICIOS -> viewModel.importarServicios(filas)
        DestinoOcr.VENTAS -> {
            val seleccionadas = filas.filter { it.seleccionada }
            val acumuladas = linkedMapOf<Long, Long>()
            var cliente: String? = null
            var tel: String? = null
            var ci: String? = null
            for (f in seleccionadas) {
                if (cliente == null) cliente = f[CampoOcr.CLIENTE].trim().ifEmpty { null }
                if (tel == null) tel = f[CampoOcr.TELEFONO].trim().ifEmpty { null }
                if (ci == null) ci = f[CampoOcr.CI].trim().ifEmpty { null }
                val cand = MapeadorOcr.emparejarArticuloVenta(f[CampoOcr.NOMBRE], candidatosVenta) ?: continue
                val cant = MapeadorOcr.extraerCantidad(f[CampoOcr.CANTIDAD], permitirDecimales = false)
                    .ifEmpty { "1" }
                    .toLongOrNull()
                    ?.coerceAtLeast(1L) ?: 1L
                val previa = acumuladas[cand.id] ?: 0L
                val suma = previa + cant
                val tope = cand.topeDisponible
                val final = if (tope != null) suma.coerceAtMost(tope.coerceAtLeast(0L)) else suma
                if (final > 0L) acumuladas[cand.id] = final
            }
            if (acumuladas.isEmpty()) {
                onError("Ninguna línea coincide con artículos o servicios con existencias disponibles.")
            } else {
                onAplicarVenta(
                    ResultadoVentaOcr(
                        lineas = acumuladas.map { (id, c) -> LineaVentaImportadaOcr(id, c) },
                        clienteNombre = cliente,
                        clienteTelefono = tel,
                        clienteCi = ci,
                    ),
                )
                onDismiss()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BarraFuentesOcr(
    habilitado: Boolean,
    onCamara: () -> Unit,
    onGaleria: () -> Unit,
    onPdf: () -> Unit,
    onExcel: () -> Unit,
    onWord: () -> Unit,
) {
    ShalevCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            ShalevSecondaryText("Elige el origen del documento:")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
            ) {
                ShalevIconAction(ShalevIcons.Camara, "Cámara (manuscrito o impreso)", onClick = onCamara, enabled = habilitado, style = IconActionStyle.Tonal, modifier = Modifier.testTag("ocr.camara"))
                ShalevIconAction(ShalevIcons.Galeria, "Foto de la galería", onClick = onGaleria, enabled = habilitado, style = IconActionStyle.Tonal, modifier = Modifier.testTag("ocr.galeria"))
                ShalevIconAction(ShalevIcons.Pdf, "Abrir PDF", onClick = onPdf, enabled = habilitado, style = IconActionStyle.Tonal, modifier = Modifier.testTag("ocr.pdf"))
                ShalevIconAction(ShalevIcons.Excel, "Abrir Excel (.xlsx / .csv)", onClick = onExcel, enabled = habilitado, style = IconActionStyle.Tonal, modifier = Modifier.testTag("ocr.excel"))
                ShalevIconAction(ShalevIcons.ElegirArchivo, "Abrir Word (.docx)", onClick = onWord, enabled = habilitado, style = IconActionStyle.Tonal, modifier = Modifier.testTag("ocr.word"))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeccionLienzoVisual(
    destino: DestinoOcr,
    doc: DocumentoEscaneado.Visual,
    campos: List<CampoOcr>,
    campoActivo: CampoOcr,
    onElegirCampo: (CampoOcr) -> Unit,
    asignacionBloque: MutableMap<Int, CampoOcr>,
    borrador: BorradorImportacionOcr,
    onCambiarBorrador: (BorradorImportacionOcr) -> Unit,
    cola: List<BorradorImportacionOcr>,
    onEncolar: () -> Unit,
    onQuitarDeCola: (Int) -> Unit,
    guardando: Boolean,
    candidatosVenta: List<CandidatoVentaOcr>,
    onImportar: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        ShalevSecondaryText("1. Elige el campo destino y toca un recuadro o arrastra un rectángulo sobre la imagen:")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            for (c in campos) {
                val tieneValor = borrador[c].isNotBlank()
                FilterChip(
                    selected = campoActivo == c,
                    onClick = { onElegirCampo(c) },
                    label = { Text(if (tieneValor) "${c.corto}: ${borrador[c].take(14)}" else c.etiqueta) },
                )
            }
        }

        val ratio = remember(doc.bitmap) {
            (doc.bitmap.width.coerceAtLeast(1).toFloat() / doc.bitmap.height.coerceAtLeast(1).toFloat()).coerceIn(0.45f, 2.2f)
        }
        var lienzoSize by remember { mutableStateOf(IntSize.Zero) }
        var dragInicio by remember { mutableStateOf<Offset?>(null) }
        var dragFin by remember { mutableStateOf<Offset?>(null) }

        fun asignarBloques(seleccionados: List<BloqueOcr>) {
            if (seleccionados.isEmpty()) return
            seleccionados.forEach { asignacionBloque[it.id] = campoActivo }
            val unido = seleccionados.joinToString(" ") { it.texto }
            val limpio = MapeadorOcr.sanitizarParaCampo(campoActivo, unido)
            onCambiarBorrador(borrador.con(campoActivo, limpio))
            val idx = campos.indexOf(campoActivo)
            if (idx in 0 until campos.lastIndex) onElegirCampo(campos[idx + 1])
        }

        val colorPrimario = MaterialTheme.colorScheme.primary
        val colorSecundario = MaterialTheme.colorScheme.tertiary

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(ShalevRadius.lg))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(ShalevRadius.lg))
                .onSizeChanged { lienzoSize = it }
                .pointerInput(doc.bloques, campoActivo, lienzoSize) {
                    detectTapGestures { pos ->
                        if (lienzoSize.width <= 0 || lienzoSize.height <= 0) return@detectTapGestures
                        val nx = (pos.x / lienzoSize.width).coerceIn(0f, 1f)
                        val ny = (pos.y / lienzoSize.height).coerceIn(0f, 1f)
                        val tocado = doc.bloques.firstOrNull { nx in it.left..it.right && ny in it.top..it.bottom }
                        if (tocado != null) asignarBloques(listOf(tocado))
                    }
                }
                .pointerInput(doc.bloques, campoActivo, lienzoSize) {
                    detectDragGestures(
                        onDragStart = { start -> dragInicio = start; dragFin = start },
                        onDrag = { change, _ -> change.consume(); dragFin = change.position },
                        onDragCancel = { dragInicio = null; dragFin = null },
                        onDragEnd = {
                            val s = dragInicio
                            val e = dragFin
                            dragInicio = null
                            dragFin = null
                            if (s != null && e != null && lienzoSize.width > 0 && lienzoSize.height > 0) {
                                val seleccionados = MapeadorOcr.bloquesEnRectangulo(
                                    doc.bloques,
                                    (s.x / lienzoSize.width).coerceIn(0f, 1f),
                                    (s.y / lienzoSize.height).coerceIn(0f, 1f),
                                    (e.x / lienzoSize.width).coerceIn(0f, 1f),
                                    (e.y / lienzoSize.height).coerceIn(0f, 1f),
                                )
                                asignarBloques(seleccionados)
                            }
                        },
                    )
                },
        ) {
            Image(
                bitmap = remember(doc.bitmap) { doc.bitmap.asImageBitmap() },
                contentDescription = "Documento escaneado con regiones seleccionables",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(Modifier.fillMaxSize()) {
                for (b in doc.bloques) {
                    val asignado = asignacionBloque[b.id]
                    val tl = Offset(b.left * size.width, b.top * size.height)
                    val sz = Size(
                        ((b.right - b.left) * size.width).coerceAtLeast(6f),
                        ((b.bottom - b.top) * size.height).coerceAtLeast(6f),
                    )
                    val color = if (asignado != null) colorSecundario else colorPrimario
                    drawRect(color = color.copy(alpha = if (asignado != null) 0.24f else 0.10f), topLeft = tl, size = sz)
                    drawRect(color = color, topLeft = tl, size = sz, style = Stroke(width = if (asignado != null) 4f else 2f))
                }
                val s = dragInicio
                val e = dragFin
                if (s != null && e != null) {
                    val left = minOf(s.x, e.x)
                    val top = minOf(s.y, e.y)
                    val rectSize = Size(kotlin.math.abs(e.x - s.x), kotlin.math.abs(e.y - s.y))
                    drawRect(color = colorSecundario.copy(alpha = 0.22f), topLeft = Offset(left, top), size = rectSize)
                    drawRect(color = colorSecundario, topLeft = Offset(left, top), size = rectSize, style = Stroke(width = 4f))
                }
            }
        }

        if (doc.bloques.isEmpty()) {
            ShalevSecondaryText("No se detectó texto automático en esta imagen. Puedes escribir o ajustar los campos manualmente abajo.")
        } else {
            ShalevSecondaryText("${doc.bloques.size} bloques detectados · Toca un bloque o arrastra un rectángulo para rellenar «${campoActivo.corto}».")
        }

        ShalevCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                Text("2. Revisa o ajusta los valores asignados", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                for (c in campos) {
                    val filtro = when {
                        c.esMoneda -> FiltroEntrada.DINERO
                        c.esCantidad -> FiltroEntrada.DECIMAL
                        c == CampoOcr.TELEFONO -> FiltroEntrada.TELEFONO
                        c == CampoOcr.CI -> FiltroEntrada.CARNE
                        else -> FiltroEntrada.NOMBRE
                    }
                    val teclado = when {
                        c.esMoneda || c.esCantidad -> KeyboardType.Decimal
                        c == CampoOcr.TELEFONO -> KeyboardType.Phone
                        else -> KeyboardType.Text
                    }
                    val apoyo = if (destino == DestinoOcr.VENTAS && c == CampoOcr.NOMBRE && borrador[c].isNotBlank()) {
                        val m = MapeadorOcr.emparejarArticuloVenta(borrador[c], candidatosVenta)
                        if (m != null) "Coincide con: ${m.nombre}${m.topeDisponible?.let { " (máx. $it)" }.orEmpty()}"
                        else "Sin coincidencia en el catálogo vendible"
                    } else null
                    ShalevTextField(
                        value = borrador[c],
                        onValueChange = { onCambiarBorrador(borrador.con(c, it)) },
                        label = c.etiqueta,
                        supportingText = apoyo,
                        filtro = filtro,
                        keyboardOptions = KeyboardOptions(keyboardType = teclado),
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShalevSecondaryButton(
                        text = "Añadir a la cola para seguir escaneando",
                        onClick = onEncolar,
                        icon = ShalevIcons.AgregarALista,
                        enabled = borrador[CampoOcr.NOMBRE].isNotBlank() && !guardando,
                    )
                    ShalevPrimaryButton(
                        text = "Importar",
                        onClick = onImportar,
                        icon = ShalevIcons.Confirmar,
                        enabled = (borrador[CampoOcr.NOMBRE].isNotBlank() || cola.isNotEmpty()) && !guardando,
                        loading = guardando,
                    )
                }
            }
        }

        if (cola.isNotEmpty()) {
            ShalevCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                    Text("En cola (${cola.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    cola.forEachIndexed { idx, item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item[CampoOcr.NOMBRE] +
                                    item[CampoOcr.CANTIDAD].takeIf { it.isNotBlank() }?.let { " · Cant. $it" }.orEmpty() +
                                    (item[CampoOcr.PRECIO_VENTA].ifEmpty { item[CampoOcr.IMPORTE] }).takeIf { it.isNotBlank() }?.let { " · $it CUP" }.orEmpty(),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            ShalevIconAction(ShalevIcons.Eliminar, "Quitar fila", onClick = { onQuitarDeCola(idx) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapeadorTablaVisual(
    destino: DestinoOcr,
    tablas: List<TablaImportada>,
    candidatosVenta: List<CandidatoVentaOcr>,
    guardando: Boolean,
    onConfirmarFilas: (List<BorradorImportacionOcr>) -> Unit,
) {
    var indiceTabla by remember(tablas) { mutableIntStateOf(0) }
    val tabla = tablas.getOrElse(indiceTabla) { TablaImportada("", emptyList()) }
    val camposPermitidos = remember(destino) { CampoOcr.para(destino) }

    var omitirEncabezado by remember(tabla) {
        mutableStateOf(tabla.filas.size > 1 && MapeadorOcr.pareceEncabezado(tabla.filas.first(), destino))
    }
    val mapeoColumnas = remember(tabla, destino) {
        mutableStateMapOf<Int, CampoOcr>().apply {
            if (tabla.filas.isNotEmpty()) {
                val sugerido = MapeadorOcr.sugerirColumnas(tabla.filas.first(), destino)
                if (sugerido.isNotEmpty()) putAll(sugerido)
                else {
                    // Asignación posicional inicial editable manualmente.
                    camposPermitidos.take(tabla.filas.first().size).forEachIndexed { col, c -> put(col, c) }
                }
            }
        }
    }
    val filasDatos = remember(tabla, omitirEncabezado) {
        if (omitirEncabezado && tabla.filas.size > 1) tabla.filas.drop(1) else tabla.filas
    }
    val seleccionFilas = remember(filasDatos) {
        mutableStateMapOf<Int, Boolean>().apply {
            filasDatos.indices.forEach { put(it, true) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        if (tablas.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                tablas.forEachIndexed { idx, t ->
                    FilterChip(
                        selected = idx == indiceTabla,
                        onClick = { indiceTabla = idx },
                        label = { Text(t.titulo) },
                    )
                }
            }
        }

        ShalevCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                Text("1. Asigna cada columna del documento a un campo de Shalev", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = omitirEncabezado, onCheckedChange = { omitirEncabezado = it })
                    Text("Omitir primera fila (encabezado)", style = MaterialTheme.typography.bodyMedium)
                }

                val numCols = tabla.filas.firstOrNull()?.size ?: 0
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                    for (col in 0 until numCols) {
                        var menuAbierto by remember { mutableStateOf(false) }
                        val asignado = mapeoColumnas[col]
                        val muestra = tabla.filas.firstOrNull()?.getOrNull(col)?.take(16).orEmpty()
                        Box {
                            FilterChip(
                                selected = asignado != null,
                                onClick = { menuAbierto = true },
                                label = {
                                    Text("Col ${('A' + (col % 26))}${if (muestra.isNotEmpty()) " ($muestra)" else ""} → ${asignado?.corto ?: "Ignorar"}")
                                },
                            )
                            DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                                DropdownMenuItem(
                                    text = { Text("Ignorar columna") },
                                    onClick = { mapeoColumnas.remove(col); menuAbierto = false },
                                )
                                for (c in camposPermitidos) {
                                    DropdownMenuItem(
                                        text = { Text(c.etiqueta) },
                                        onClick = {
                                            // Evita duplicar el mismo campo en dos columnas.
                                            mapeoColumnas.entries.removeAll { it.value == c }
                                            mapeoColumnas[col] = c
                                            menuAbierto = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ShalevCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                val marcadas = seleccionFilas.count { it.value }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("2. Filas detectadas ($marcadas/${filasDatos.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    ShalevPrimaryButton(
                        text = "Importar filas seleccionadas",
                        icon = ShalevIcons.Confirmar,
                        enabled = marcadas > 0 && !guardando,
                        loading = guardando,
                        onClick = {
                            val borradores = filasDatos.mapIndexed { idx, celdas ->
                                val vals = linkedMapOf<CampoOcr, String>()
                                celdas.forEachIndexed { col, valor ->
                                    val campo = mapeoColumnas[col] ?: return@forEachIndexed
                                    vals[campo] = MapeadorOcr.sanitizarParaCampo(campo, valor)
                                }
                                BorradorImportacionOcr(valores = vals, seleccionada = seleccionFilas[idx] == true)
                            }
                            onConfirmarFilas(borradores)
                        },
                    )
                }

                HorizontalDivider()

                filasDatos.take(150).forEachIndexed { idx, celdas ->
                    val nombreCol = mapeoColumnas.entries.firstOrNull { it.value == CampoOcr.NOMBRE }?.key
                    val nombreMuestra = nombreCol?.let { celdas.getOrNull(it) }.orEmpty().ifBlank { celdas.firstOrNull().orEmpty() }
                    val detalle = celdas.mapIndexedNotNull { col, v ->
                        val c = mapeoColumnas[col]
                        if (c != null && c != CampoOcr.NOMBRE && v.isNotBlank()) "${c.corto}: $v" else null
                    }.joinToString(" · ")
                    val avisoVenta = if (destino == DestinoOcr.VENTAS && nombreMuestra.isNotBlank()) {
                        val m = MapeadorOcr.emparejarArticuloVenta(nombreMuestra, candidatosVenta)
                        when {
                            m == null -> " · Sin coincidencia en catálogo"
                            m.topeDisponible != null && m.topeDisponible <= 0L -> " · Sin existencias"
                            m.topeDisponible != null -> " · Máx. disp.: ${m.topeDisponible}"
                            else -> " · ${m.nombre}"
                        }
                    } else ""
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { seleccionFilas[idx] = !(seleccionFilas[idx] ?: true) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = seleccionFilas[idx] ?: true,
                            onCheckedChange = { seleccionFilas[idx] = it },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(nombreMuestra.ifBlank { "Fila ${idx + 1}" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (detalle.isNotBlank() || avisoVenta.isNotBlank()) {
                                ShalevSecondaryText((detalle + avisoVenta).removePrefix(" · "))
                            }
                        }
                    }
                }
            }
        }
    }
}
