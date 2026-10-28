package cu.shalev.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Resultado procesado de abrir una imagen, PDF, Excel, CSV o Word localmente. */
sealed interface DocumentoEscaneado {
    data class Visual(
        val titulo: String,
        val bitmap: Bitmap,
        val bloques: List<BloqueOcr>,
        val paginaActual: Int = 1,
        val totalPaginas: Int = 1,
        val pdfUri: Uri? = null,
    ) : DocumentoEscaneado

    data class Tabular(
        val titulo: String,
        val tablas: List<TablaImportada>,
    ) : DocumentoEscaneado
}

/**
 * Motor OCR 100 % local, gratuito y sin límite de solicitudes basado en ML Kit Text Recognition v2
 * (modelo latino empaquetado) + `PdfRenderer` + lectores nativos de `.xlsx`, `.csv` y `.docx`.
 */
object MotorOcrAndroid {

    private const val MAX_LADO_PX = 2048

    suspend fun procesarImagen(context: Context, uri: Uri): Result<DocumentoEscaneado.Visual> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = decodificarImagenEscalada(context, uri)
                    ?: error("No se pudo abrir la imagen seleccionada.")
                val bloques = reconocerTexto(bitmap)
                DocumentoEscaneado.Visual(
                    titulo = nombreArchivo(context, uri) ?: "Imagen escaneada",
                    bitmap = bitmap,
                    bloques = bloques,
                )
            }
        }

    suspend fun procesarPdf(context: Context, uri: Uri, paginaUnoBase: Int = 1): Result<DocumentoEscaneado.Visual> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: error("No se pudo abrir el documento PDF.")
                pfd.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        val total = renderer.pageCount.coerceAtLeast(1)
                        val indice = (paginaUnoBase - 1).coerceIn(0, total - 1)
                        val bitmap = renderer.openPage(indice).use { page ->
                            val escala = (1600f / max(page.width, page.height).coerceAtLeast(1)).coerceIn(1.5f, 3f)
                            val w = (page.width * escala).toInt().coerceIn(400, MAX_LADO_PX)
                            val h = (page.height * escala).toInt().coerceIn(400, MAX_LADO_PX)
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                                Canvas(bmp).drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                        val bloques = reconocerTexto(bitmap)
                        DocumentoEscaneado.Visual(
                            titulo = nombreArchivo(context, uri) ?: "Documento PDF",
                            bitmap = bitmap,
                            bloques = bloques,
                            paginaActual = indice + 1,
                            totalPaginas = total,
                            pdfUri = uri,
                        )
                    }
                }
            }
        }

    suspend fun procesarDocumento(context: Context, uri: Uri): Result<DocumentoEscaneado> =
        withContext(Dispatchers.IO) {
            runCatching {
                val nombre = nombreArchivo(context, uri).orEmpty()
                val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
                val ext = nombre.substringAfterLast('.', "").lowercase()
                when {
                    ext == "pdf" || mime == "application/pdf" ->
                        procesarPdf(context, uri, 1).getOrThrow()

                    ext == "xlsx" || mime.contains("spreadsheetml") -> {
                        val tablas = context.contentResolver.openInputStream(uri)?.use(LectorDocumentosLocal::leerXlsx)
                            ?: error("No se pudo leer el archivo Excel (.xlsx).")
                        if (tablas.isEmpty()) error("El archivo Excel no contiene celdas con datos.")
                        DocumentoEscaneado.Tabular(nombre.ifBlank { "Libro Excel" }, tablas)
                    }

                    ext in setOf("csv", "tsv", "txt") || mime.startsWith("text/") -> {
                        val texto = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("No se pudo leer el archivo de texto/CSV.")
                        val tabla = LectorDocumentosLocal.leerCsv(texto, nombre.ifBlank { "Tabla CSV" })
                        if (tabla.filas.isEmpty()) error("El archivo CSV está vacío.")
                        DocumentoEscaneado.Tabular(tabla.titulo, listOf(tabla))
                    }

                    ext == "docx" || mime.contains("wordprocessingml") -> {
                        val res = context.contentResolver.openInputStream(uri)?.use(LectorDocumentosLocal::leerDocx)
                            ?: error("No se pudo abrir el documento Word (.docx).")
                        when {
                            res.tablas.isNotEmpty() ->
                                DocumentoEscaneado.Tabular(nombre.ifBlank { "Documento Word" }, res.tablas)
                            res.parrafos.isNotEmpty() ->
                                renderizarParrafosComoLienzo(nombre.ifBlank { "Documento Word" }, res.parrafos)
                            else -> error("El documento Word no contiene tablas ni texto legible.")
                        }
                    }

                    mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp") ->
                        procesarImagen(context, uri).getOrThrow()

                    else -> error("Formato no admitido. Elige una imagen, PDF, Excel (.xlsx/.csv) o Word (.docx).")
                }
            }
        }

    private suspend fun reconocerTexto(bitmap: Bitmap): List<BloqueOcr> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val texto: Text = suspendCancellableCoroutine { cont ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
                    .addOnCanceledListener { if (cont.isActive) cont.cancel() }
            }
            val w = bitmap.width.coerceAtLeast(1).toFloat()
            val h = bitmap.height.coerceAtLeast(1).toFloat()
            val salida = mutableListOf<BloqueOcr>()
            var siguienteId = 1
            for (block in texto.textBlocks) {
                for (line in block.lines) {
                    // Si la línea contiene elementos separados (útil para separar nombre y precio en una misma fila),
                    // exponemos cada palabra/elemento con su recuadro exacto para selección manual precisa.
                    val elementos = line.elements.filter { it.text.isNotBlank() && it.boundingBox != null }
                    if (elementos.size > 1) {
                        // Agrupa palabras contiguas del mismo tramo y separa números/precios en bloques propios.
                        var grupoTexto = StringBuilder()
                        var gLeft = 0f
                        var gTop = 0f
                        var gRight = 0f
                        var gBottom = 0f
                        fun volcarGrupo() {
                            if (grupoTexto.isNotEmpty()) {
                                salida += BloqueOcr(
                                    id = siguienteId++,
                                    texto = grupoTexto.toString().trim(),
                                    left = (gLeft / w).coerceIn(0f, 1f),
                                    top = (gTop / h).coerceIn(0f, 1f),
                                    right = (gRight / w).coerceIn(0f, 1f),
                                    bottom = (gBottom / h).coerceIn(0f, 1f),
                                )
                                grupoTexto.setLength(0)
                            }
                        }
                        var prevEsNumero: Boolean? = null
                        for (el in elementos) {
                            val r = el.boundingBox ?: continue
                            val txt = el.text.trim()
                            val esNum = txt.any { it.isDigit() }
                            val saltoGrande = grupoTexto.isNotEmpty() && (r.left - gRight) > (r.height() * 1.4f)
                            if (grupoTexto.isNotEmpty() && (saltoGrande || prevEsNumero != esNum)) {
                                volcarGrupo()
                            }
                            if (grupoTexto.isEmpty()) {
                                grupoTexto.append(txt)
                                gLeft = r.left.toFloat()
                                gTop = r.top.toFloat()
                                gRight = r.right.toFloat()
                                gBottom = r.bottom.toFloat()
                            } else {
                                grupoTexto.append(' ').append(txt)
                                gTop = minOf(gTop, r.top.toFloat())
                                gRight = maxOf(gRight, r.right.toFloat())
                                gBottom = maxOf(gBottom, r.bottom.toFloat())
                            }
                            prevEsNumero = esNum
                        }
                        volcarGrupo()
                    } else {
                        val rect = line.boundingBox ?: continue
                        val txt = line.text.trim()
                        if (txt.isNotEmpty()) {
                            salida += BloqueOcr(
                                id = siguienteId++,
                                texto = txt,
                                left = (rect.left / w).coerceIn(0f, 1f),
                                top = (rect.top / h).coerceIn(0f, 1f),
                                right = (rect.right / w).coerceIn(0f, 1f),
                                bottom = (rect.bottom / h).coerceIn(0f, 1f),
                            )
                        }
                    }
                }
            }
            salida
        } finally {
            runCatching { recognizer.close() }
        }
    }

    private fun renderizarParrafosComoLienzo(titulo: String, parrafos: List<String>): DocumentoEscaneado.Visual {
        val ancho = 1200
        val margen = 48f
        val altoLinea = 44f
        val lineas = parrafos.take(80)
        val alto = ((lineas.size * altoLinea) + margen * 2).toInt().coerceIn(500, MAX_LADO_PX)
        val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp).apply { drawColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 28, 30)
            textSize = 26f
        }
        val bloques = mutableListOf<BloqueOcr>()
        var y = margen + 28f
        var id = 1
        for (p in lineas) {
            if (y > alto - margen) break
            val recorte = p.take(90)
            canvas.drawText(recorte, margen, y, paint)
            val tw = paint.measureText(recorte).coerceAtLeast(40f)
            bloques += BloqueOcr(
                id = id++,
                texto = p,
                left = (margen / ancho).coerceIn(0f, 1f),
                top = ((y - 28f) / alto).coerceIn(0f, 1f),
                right = ((margen + tw) / ancho).coerceIn(0f, 1f),
                bottom = ((y + 8f) / alto).coerceIn(0f, 1f),
            )
            y += altoLinea
        }
        return DocumentoEscaneado.Visual(titulo = titulo, bitmap = bmp, bloques = bloques)
    }

    private fun decodificarImagenEscalada(context: Context, uri: Uri): Bitmap? {
        val opcionesTam = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opcionesTam) }
        if (opcionesTam.outWidth <= 0 || opcionesTam.outHeight <= 0) return null
        var sample = 1
        while (opcionesTam.outWidth / sample > MAX_LADO_PX || opcionesTam.outHeight / sample > MAX_LADO_PX) {
            sample *= 2
        }
        val opciones = BitmapFactory.Options().apply { inSampleSize = sample }
        val bruto = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
            ?: return null
        val grados = context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (grados == 0f) return bruto
        val m = Matrix().apply { postRotate(grados) }
        return Bitmap.createBitmap(bruto, 0, 0, bruto.width, bruto.height, m, true)
    }

    private fun nombreArchivo(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment
    }.getOrNull()
}
