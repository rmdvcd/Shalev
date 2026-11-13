package cu.shalev.data.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import cu.shalev.domain.service.TablaExport
import java.io.OutputStream

/**
 * PDF A4 con [PdfDocument] del framework (sin dependencias). Tablas paginadas con cabecera repetida y texto truncado
 * con "…" si no cabe. Paleta en escala de grises de alto contraste (imprime bien y cumple AA).
 * 0.25.1 (E2): con más de 5 columnas la hoja va horizontal; fechas, importes y códigos nunca se cortan e importes y
 * cantidades van a la derecha ([DisenoPdf]).
 */
object PdfWriter {
    private const val A4_CORTO = 595
    private const val A4_LARGO = 842
    private const val M = 36f
    private const val FILA = 16f

    fun escribir(tablas: List<TablaExport>, out: OutputStream) {
        val doc = PdfDocument()
        try {
            Pintor(doc).apply { tablas.forEach(::tabla); cerrar() }
            doc.writeTo(out)
            out.flush()
        } finally {
            doc.close()
        }
    }

    private class Pintor(private val doc: PdfDocument) {
        private val titulo = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        private val cabecera = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        private val texto = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = 0xFF202124.toInt() }
        /** 0.26.0 (§5): importes, fechas y valores en seminegrita (600; en Android 8 no hay pesos: negrita). */
        private val dato = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f; color = 0xFF202124.toInt()
            typeface = if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 600, false) else Typeface.DEFAULT_BOLD
        }
        private val pie = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = 0xFF5F6368.toInt() }
        private val fondo = Paint().apply { color = 0xFFE8EAED.toInt() }
        private val linea = Paint().apply { color = 0xFFBDC1C6.toInt(); strokeWidth = 0.5f }

        private var numPagina = 0
        private var pagina: PdfDocument.Page? = null
        private var y = 0f
        private var W = A4_CORTO
        private var H = A4_LARGO

        private fun nueva() {
            cerrarPagina()
            numPagina++
            pagina = doc.startPage(PdfDocument.PageInfo.Builder(W, H, numPagina).create())
            y = M
        }

        /** Cambia la orientación de las páginas siguientes; si la actual tiene otra, la tabla empieza en página nueva. */
        private fun orientacion(horizontal: Boolean): Boolean {
            val w = if (horizontal) A4_LARGO else A4_CORTO
            if (w == W) return false
            W = w; H = if (horizontal) A4_CORTO else A4_LARGO
            return true
        }

        private fun cerrarPagina() {
            pagina?.let {
                it.canvas.drawText("Shalev · página $numPagina", M, H - M / 2, pie)
                doc.finishPage(it)
            }
            pagina = null
        }

        fun cerrar() { if (pagina == null) nueva(); cerrarPagina() }

        fun tabla(t: TablaExport) {
            val cambia = orientacion(DisenoPdf.horizontal(t))
            if (pagina == null || cambia || y > H - M - FILA * 4) nueva() else y += FILA
            val c = pagina!!.canvas
            c.drawText(t.titulo, M, y + 14f, titulo)
            y += 26f
            val anchos = DisenoPdf.anchos(t, W - 2 * M) { s, negrita -> (if (negrita) cabecera else texto).measureText(s) }
            val derecha = BooleanArray(t.columnas.size) { DisenoPdf.derecha(t, it) }
            val pinceles = Array(t.columnas.size) { if (DisenoPdf.destacada(t, it)) dato else texto }
            fun filaCabecera() {
                val cv = pagina!!.canvas
                cv.drawRect(M, y, W - M, y + FILA, fondo)
                celdas(t.columnas, anchos, derecha) { cabecera }
            }
            filaCabecera()
            t.filas.forEach { f ->
                if (y + FILA > H - M - FILA) { nueva(); filaCabecera() }
                celdas(f, anchos, derecha) { pinceles.getOrElse(it) { texto } }
                pagina!!.canvas.drawLine(M, y, W - M, y, linea)
            }
            t.pie.forEach { p ->
                if (y + FILA > H - M - FILA) nueva()
                pagina!!.canvas.drawText(p, M, y + 12f, cabecera)
                y += FILA
            }
        }

        private fun celdas(valores: List<String>, anchos: FloatArray, derecha: BooleanArray, pincel: (Int) -> TextPaint) {
            var x = M
            val cv = pagina!!.canvas
            valores.forEachIndexed { i, v ->
                val w = anchos.getOrElse(i) { 0f }
                val paint = pincel(i)
                val s = TextUtils.ellipsize(v, paint, (w - 6f).coerceAtLeast(0f), TextUtils.TruncateAt.END).toString()
                val dx = if (derecha.getOrElse(i) { false }) w - 3f - paint.measureText(s) else 3f
                cv.drawText(s, x + dx, y + 11.5f, paint)
                x += w
            }
            y += FILA
        }
    }
}
