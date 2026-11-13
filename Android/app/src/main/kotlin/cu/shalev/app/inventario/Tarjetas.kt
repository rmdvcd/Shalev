package cu.shalev.app.inventario

import cu.shalev.domain.model.nombreCompleto
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import cu.shalev.app.common.ArchivosApp
import cu.shalev.core.money.Money
import cu.shalev.domain.di.IoDispatcher
import cu.shalev.domain.model.Producto
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Compartir como Imagen / exportar "cards" (Shalev.txt: promoción por mensajería y redes). Solo datos para
 * clientes: foto, nombre, categoría y precio. Nunca costo ni existencias.
 */

/** Distribución (pura): 1 producto = tarjeta grande 4:5; varios = rejilla 2 columnas, hasta 6 por imagen. */
object DisenoTarjetas {
    const val POR_IMAGEN = 6
    const val ANCHO = 1080

    fun grupos(productos: List<Producto>): List<List<Producto>> = productos.chunked(POR_IMAGEN)
    fun columnas(n: Int): Int = if (n <= 1) 1 else 2
    fun filas(n: Int): Int = (n + columnas(n) - 1) / columnas(n)
    fun alto(n: Int): Int = if (n <= 1) 1350 else MARGEN * 2 + CABECERA + filas(n) * ALTO_CELDA + (filas(n) - 1) * SEPARACION

    const val MARGEN = 40
    const val SEPARACION = 24
    const val CABECERA = 0
    const val ALTO_CELDA = 600
}

open class TarjetasAndroid @Inject constructor(
    private val archivos: ArchivosApp,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    private val fondo = Color.parseColor("#F8F9FA")
    private val marca = Color.parseColor("#1F5F70")   // teal oscuro de la marca: contraste AA sobre blanco
    private val placeholder = Color.parseColor("#478EA1")
    private val texto = Color.parseColor("#1B1B1B")
    private val secundario = Color.parseColor("#5F6368")

    open suspend fun renderizar(productos: List<Producto>): List<File> = renderizar(productos, "")

    open suspend fun renderizar(productos: List<Producto>, comentario: String): List<File> = withContext(io) {
        DisenoTarjetas.grupos(productos).mapIndexed { i, grupo ->
            val alto = DisenoTarjetas.alto(grupo.size)
            val encabezado = comentario.trim().take(160)
            val pincel = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = marca; textSize = 42f; typeface = Typeface.DEFAULT_BOLD }
            val cabecera = encabezado.takeIf { it.isNotEmpty() }?.let {
                StaticLayout.Builder.obtain(it, 0, it.length, pincel, DisenoTarjetas.ANCHO - 96).build()
            }
            val espacio = cabecera?.let { it.height + 80 } ?: 0
            val bmp = Bitmap.createBitmap(DisenoTarjetas.ANCHO, alto + espacio, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(fondo)
            cabecera?.let {
                c.save()
                c.translate(48f, 40f)
                it.draw(c)
                c.restore()
                c.translate(0f, espacio.toFloat())
            }
            if (grupo.size == 1) {
                tarjeta(c, grupo[0], RectF(48f, 48f, DisenoTarjetas.ANCHO - 48f, alto - 48f), grande = true)
            } else {
                val m = DisenoTarjetas.MARGEN.toFloat()
                val sep = DisenoTarjetas.SEPARACION.toFloat()
                val w = (DisenoTarjetas.ANCHO - 2 * m - sep) / 2
                grupo.forEachIndexed { k, p ->
                    val x = m + (k % 2) * (w + sep)
                    val y = m + (k / 2) * (DisenoTarjetas.ALTO_CELDA + sep)
                    tarjeta(c, p, RectF(x, y, x + w, y + DisenoTarjetas.ALTO_CELDA), grande = false)
                }
            }
            val f = archivos.temporal("Shalev_productos_${i + 1}.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            f
        }
    }

    private fun tarjeta(c: Canvas, p: Producto, r: RectF, grande: Boolean) {
        val radio = 32f
        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 0, 0, 0) }
        c.drawRoundRect(RectF(r.left, r.top + 4, r.right, r.bottom + 4), radio, radio, sombra)
        c.drawRoundRect(r, radio, radio, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

        val pad = if (grande) 48f else 28f
        val altoFoto = r.height() * if (grande) 0.62f else 0.56f
        val zonaFoto = RectF(r.left, r.top, r.right, r.top + altoFoto)
        foto(c, p, zonaFoto, radio)

        var y = zonaFoto.bottom + pad * 0.8f
        val ancho = (r.width() - 2 * pad).toInt()
        val nombre = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = texto; textSize = if (grande) 64f else 38f; typeface = Typeface.DEFAULT_BOLD
        }
        y += dibujarTexto(c, p.nombreCompleto, nombre, r.left + pad, y, ancho, 2)
        val cat = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = secundario; textSize = if (grande) 40f else 26f }
        y += 8f + dibujarTexto(c, p.categoria, cat, r.left + pad, y + 8f, ancho, 1)
        val precio = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = marca; textSize = if (grande) 76f else 42f; typeface = Typeface.DEFAULT_BOLD
        }
        dibujarTexto(c, Money.format(p.precioVenta), precio, r.left + pad, r.bottom - pad - precio.textSize * 1.2f, ancho, 1)
    }

    /**
     * Auditoría 2026-10-14 (O-04): decodifica la foto ACOTADA a la zona donde se va a dibujar (antes se cargaba
     * a resolución completa de la cámara: varios MP por tarjeta y riesgo de memoria al compartir muchas).
     * Primero lee solo las dimensiones y luego submuestrea con inSampleSize (potencias de 2).
     */
    private fun decodificarAcotada(ruta: String, anchoZona: Int, altoZona: Int): Bitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(ruta, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
        var muestra = 1
        while (limites.outWidth / (muestra * 2) >= anchoZona.coerceAtLeast(1) &&
            limites.outHeight / (muestra * 2) >= altoZona.coerceAtLeast(1)
        ) muestra *= 2
        return BitmapFactory.decodeFile(ruta, BitmapFactory.Options().apply { inSampleSize = muestra })
    }

    /** Foto local recortada al centro; sin foto, un bloque de color con la inicial. */
    private fun foto(c: Canvas, p: Producto, zona: RectF, radio: Float) {
        val bmp = p.fotoUri?.takeIf { it.startsWith("file://") }?.let {
            runCatching { decodificarAcotada(it.removePrefix("file://"), zona.width().toInt(), zona.height().toInt()) }.getOrNull()
        }
        c.save()
        val clip = android.graphics.Path().apply {
            addRoundRect(zona, floatArrayOf(radio, radio, radio, radio, 0f, 0f, 0f, 0f), android.graphics.Path.Direction.CW)
        }
        c.clipPath(clip)
        if (bmp != null) {
            val escala = maxOf(zona.width() / bmp.width, zona.height() / bmp.height)
            val sw = (zona.width() / escala).toInt(); val sh = (zona.height() / escala).toInt()
            val sx = (bmp.width - sw) / 2; val sy = (bmp.height - sh) / 2
            c.drawBitmap(bmp, Rect(sx, sy, sx + sw, sy + sh), zona, Paint(Paint.FILTER_BITMAP_FLAG))
            bmp.recycle()
        } else {
            c.drawRect(zona, Paint().apply { color = placeholder })
            val inicial = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = zona.height() * 0.4f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
            }
            c.drawText(p.nombre.trim().take(1).uppercase(), zona.centerX(), zona.centerY() - (inicial.descent() + inicial.ascent()) / 2, inicial)
        }
        c.restore()
    }

    private fun dibujarTexto(c: Canvas, s: String, paint: TextPaint, x: Float, y: Float, ancho: Int, maxLineas: Int): Float {
        val l = StaticLayout.Builder.obtain(s, 0, s.length, paint, ancho)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLineas)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        c.save(); c.translate(x, y); l.draw(c); c.restore()
        return l.height.toFloat()
    }
}
