package cu.shalev.app.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import cu.shalev.domain.di.IoDispatcher
import cu.shalev.domain.service.TablaExport
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Prompt 14 · Inventario → Imagen: una tabla en PNG (una imagen por cada [DisenoTabla.POR_IMAGEN] filas) para
 * enviar por WhatsApp/Telegram, donde un PDF se abre peor.
 */

/** Distribución (pura, probada en JVM): páginas, anchos de columna y alto de cada imagen. */
object DisenoTabla {
    const val ANCHO = 1080
    const val MARGEN = 48
    const val TITULO = 120
    const val CABECERA = 72
    const val FILA = 64
    const val PIE = 64
    const val POR_IMAGEN = 25
    /** Límites (en caracteres) para repartir el ancho: ni columnas diminutas ni una que se lo coma todo. */
    private const val MIN_CAR = 6
    private const val MAX_CAR = 28

    fun paginas(filas: List<List<String>>): List<List<List<String>>> = filas.chunked(POR_IMAGEN)

    fun alto(nFilas: Int): Int = MARGEN * 2 + TITULO + CABECERA + nFilas * FILA + PIE

    /** "Lista de precios" o "Lista de precios · 2/3". */
    fun titulo(t: String, pagina: Int, total: Int): String = if (total <= 1) t else "$t · $pagina/$total"

    /** Ancho de cada columna, proporcional al texto más largo (cabecera incluida) y acotado. Suma exacta = [util]. */
    fun anchos(columnas: List<String>, filas: List<List<String>>, util: Int = ANCHO - 2 * MARGEN): List<Int> {
        if (columnas.isEmpty()) return emptyList()
        val pesos = columnas.indices.map { i ->
            (listOf(columnas[i]) + filas.map { it.getOrElse(i) { "" } }).maxOf { it.length }.coerceIn(MIN_CAR, MAX_CAR)
        }
        val total = pesos.sum()
        val base = pesos.map { it * util / total }
        return base.mapIndexed { i, w -> if (i == base.lastIndex) util - base.dropLast(1).sum() else w }
    }

    /** Columnas de importe/cantidad alineadas a la derecha (se detecta por el contenido). */
    fun alDerecha(filas: List<List<String>>, col: Int): Boolean =
        filas.isNotEmpty() && filas.all { f -> f.getOrElse(col) { "" }.let { v -> v.isEmpty() || v.endsWith(" CUP") || v.all { it.isDigit() || it in ",.-" } } }
}

open class ImagenTablaAndroid @Inject constructor(
    private val archivos: ArchivosApp,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val fondo = Color.WHITE
    private val marca = Color.parseColor("#1F5F70")     // teal oscuro de la marca: AA sobre blanco
    private val cabeceraFondo = Color.parseColor("#E3EEF1")
    private val cebra = Color.parseColor("#F5F7F8")
    private val texto = Color.parseColor("#1B1B1B")
    private val secundario = Color.parseColor("#5F6368")

    /** [nombreBase] sin extensión: "Shalev_precios" → Shalev_precios_1.png, Shalev_precios_2.png… */
    open suspend fun renderizar(tabla: TablaExport, nombreBase: String): List<File> = withContext(io) {
        val paginas = DisenoTabla.paginas(tabla.filas)
        val anchos = DisenoTabla.anchos(tabla.columnas, tabla.filas)
        val derecha = tabla.columnas.indices.map { DisenoTabla.alDerecha(tabla.filas, it) }
        paginas.mapIndexed { i, filas ->
            val alto = DisenoTabla.alto(filas.size)
            val bmp = Bitmap.createBitmap(DisenoTabla.ANCHO, alto, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(fondo)
            val m = DisenoTabla.MARGEN.toFloat()
            val tituloPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = marca; textSize = 52f; typeface = Typeface.DEFAULT_BOLD }
            c.drawText(recortar(DisenoTabla.titulo(tabla.titulo, i + 1, paginas.size), tituloPaint, DisenoTabla.ANCHO - 2 * m), m, m + 64f, tituloPaint)

            var y = m + DisenoTabla.TITULO
            c.drawRect(m, y, DisenoTabla.ANCHO - m, y + DisenoTabla.CABECERA, Paint().apply { color = cabeceraFondo })
            val cab = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = texto; textSize = 32f; typeface = Typeface.DEFAULT_BOLD }
            fila(c, tabla.columnas, anchos, derecha, y, DisenoTabla.CABECERA.toFloat(), cab)
            y += DisenoTabla.CABECERA

            val celda = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = texto; textSize = 32f }
            filas.forEachIndexed { k, f ->
                if (k % 2 == 1) c.drawRect(m, y, DisenoTabla.ANCHO - m, y + DisenoTabla.FILA, Paint().apply { color = cebra })
                fila(c, f, anchos, derecha, y, DisenoTabla.FILA.toFloat(), celda)
                y += DisenoTabla.FILA
            }
            val pie = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = secundario; textSize = 26f }
            c.drawText("Shalev", m, y + DisenoTabla.PIE - 20f, pie)

            val f = archivos.temporal("${nombreBase}_${i + 1}.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            f
        }
    }

    private fun fila(c: Canvas, valores: List<String>, anchos: List<Int>, derecha: List<Boolean>, y: Float, alto: Float, p: TextPaint) {
        var x = DisenoTabla.MARGEN.toFloat()
        val pad = 16f
        val base = y + alto / 2 - (p.descent() + p.ascent()) / 2
        valores.forEachIndexed { i, v ->
            val s = recortar(v, p, anchos[i] - 2 * pad)
            val tx = if (derecha[i]) x + anchos[i] - pad - p.measureText(s) else x + pad
            c.drawText(s, tx, base, p)
            x += anchos[i]
        }
    }

    private fun recortar(s: String, p: TextPaint, ancho: Float): String =
        TextUtils.ellipsize(s, p, ancho, TextUtils.TruncateAt.END).toString()
}
