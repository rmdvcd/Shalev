package cu.shalev.data.foto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.io.leerAcotado
import cu.shalev.domain.di.IoDispatcher
import cu.shalev.domain.repository.FotoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Parte JVM pura del almacén (probada sin Android): nombres, URIs `file://`, borrado SOLO dentro de
 * la carpeta de fotos (defensa contra rutas manipuladas) y limpieza de huérfanas.
 */
class AlmacenFotos(dir: File, private val clock: Clock) {
    val dir: File = dir.canonicalFile

    fun nuevoArchivo(): File { dir.mkdirs(); return File(dir, "${UUID.randomUUID()}.jpg") }

    fun uriDe(f: File): String = "file://" + f.canonicalFile.absolutePath

    /** Archivo de una URI propia, o null si apunta fuera de la carpeta de fotos. */
    fun archivoDe(uri: String): File? {
        if (!uri.startsWith("file://")) return null
        val f = runCatching { File(uri.removePrefix("file://")).canonicalFile }.getOrNull() ?: return null
        return f.takeIf { it.parentFile == dir && it.name.endsWith(".jpg") }
    }

    fun eliminar(uri: String): Boolean = archivoDe(uri)?.delete() == true

    /**
     * 0.32.0: archivo con ese nombre dentro de la carpeta de fotos (el que llega de la principal por la red local).
     * null si el nombre no es válido o el archivo no está: nunca se sale de la carpeta.
     */
    fun archivoPorNombre(nombre: String): File? =
        if (!NOMBRE_OK.matches(nombre)) null else File(dir, nombre).takeIf { it.isFile }
    /** 0.32.0: guarda los bytes de una foto con el nombre que le dio la principal. null si el nombre no vale. */
    fun guardarBytes(nombre: String, datos: ByteArray): File? {
        if (!NOMBRE_OK.matches(nombre)) return null
        dir.mkdirs()
        val f = File(dir, nombre)
        f.outputStream().use { it.write(datos) }
        return f
    }


    /** Borra las no usadas con más de [GRACIA_MS] (un formulario abierto no pierde su foto). */
    fun limpiar(enUso: Set<String>): Int {
        val limite = clock.now().toEpochMilli() - GRACIA_MS
        val usados = enUso.mapNotNull(::archivoDe).toSet()
        return dir.listFiles().orEmpty().count { f -> f.isFile && f !in usados && f.lastModified() < limite && f.delete() }
    }

    companion object {
        const val GRACIA_MS = 24 * 60 * 60 * 1000L

        /** 0.32.0: solo nombres propios (UUID .jpg): nada de rutas ni de subcarpetas. */
        val NOMBRE_OK = Regex("[A-Za-z0-9_-]{1,64}\\.jpg")
    }
}

/** Cálculos de tamaño (puros). */
object Imagenes {
    const val LADO_MAX = 1024
    const val MAX_BYTES_IMPORTAR = 20L * 1024 * 1024

    /** Potencia de 2 que deja el lado mayor ≥ [max] (BitmapFactory.inSampleSize). */
    fun inSampleSize(w: Int, h: Int, max: Int = LADO_MAX): Int {
        var s = 1
        while (maxOf(w, h) / (s * 2) >= max) s *= 2
        return s
    }

    /** Tamaño final con el lado mayor ≤ [max], conservando la proporción. */
    fun escalado(w: Int, h: Int, max: Int = LADO_MAX): Pair<Int, Int> {
        val m = maxOf(w, h)
        if (m <= max) return w to h
        return maxOf(1, (w.toLong() * max / m).toInt()) to maxOf(1, (h.toLong() * max / m).toInt())
    }

    /** Lee como máximo [max] bytes; null si el origen es mayor (no se trunca una imagen en silencio). */
    fun leerLimitado(input: InputStream, max: Long): ByteArray? = try {
        input.leerAcotado(max) { DemasiadoGrande }
    } catch (e: DemasiadoGrande) {
        null
    }

    private object DemasiadoGrande : Exception()
}

/**
 * Fotos de productos en `filesDir/fotos` (privado, dentro del sandbox de la app). Toda imagen se decodifica
 * y se RE-CODIFICA a JPEG ≤ 1024 px: se descartan metadatos (EXIF con GPS, etc.) y cualquier contenido que no
 * sea imagen. Solo fotos propias (cámara/galería): nunca se descarga nada de internet.
 */
@Singleton
class FotoRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    clock: Clock,
) : FotoRepository {

    private val almacen = AlmacenFotos(File(context.filesDir, "fotos"), clock)

    override suspend fun importar(origen: String): AppResult<String> = withContext(io) {
        try {
            val bytes = context.contentResolver.openInputStream(Uri.parse(origen))?.use {
                Imagenes.leerLimitado(it, Imagenes.MAX_BYTES_IMPORTAR)
            } ?: return@withContext AppResult.Err(AppError.FormatoInvalido("imagen"))
            guardar(bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Err(AppError.Almacenamiento)
        }
    }

    override suspend fun eliminar(uri: String) { withContext(io) { almacen.eliminar(uri) } }
    override suspend fun limpiarHuerfanas(enUso: Set<String>) { withContext(io) { runCatching { almacen.limpiar(enUso) } } }

    /** Decodifica (muestreado), corrige la orientación EXIF, escala y re-codifica. */
    private fun guardar(bytes: ByteArray): AppResult<String> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return AppResult.Err(AppError.FormatoInvalido("imagen"))
        val opts = BitmapFactory.Options().apply { inSampleSize = Imagenes.inSampleSize(bounds.outWidth, bounds.outHeight) }
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return AppResult.Err(AppError.FormatoInvalido("imagen"))
        val (w, h) = Imagenes.escalado(bmp.width, bmp.height)
        val m = Matrix()
        if (w != bmp.width) m.postScale(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
        rotacion(bytes).takeIf { it != 0f }?.let(m::postRotate)
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val f = almacen.nuevoArchivo()
        f.outputStream().use { if (!bmp.compress(Bitmap.CompressFormat.JPEG, 85, it)) return AppResult.Err(AppError.Almacenamiento) }
        return AppResult.Ok(almacen.uriDe(f))
    }

    private fun rotacion(bytes: ByteArray): Float = runCatching {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
}
