package cu.shalev.data.foto

import android.content.Context
import cu.shalev.core.time.Clock
import cu.shalev.core.util.aHex
import cu.shalev.data.sync.BloqueFoto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 0.32.0: fotos del catálogo en la sincronización por la red local.
 *
 * - Principal: sirve los bloques de la foto que tiene (la secundaria pide solo las que le faltan).
 * - Secundaria: guarda los bloques recibidos con el MISMO nombre (así no se descargan dos veces).
 *
 * El SHA-256 se calcula una vez por foto y se queda en memoria: no se relee el disco en cada bloque.
 */
@Singleton
class FotosLocal @Inject constructor(
    @ApplicationContext context: Context,
    clock: Clock,
) {
    private val almacen = AlmacenFotos(File(context.filesDir, "fotos"), clock)
    private val hashes = ConcurrentHashMap<String, String>()

    /** ¿Está ya la foto [nombre] en esta app? */
    fun existe(nombre: String): Boolean = almacen.archivoPorNombre(nombre) != null

    /**
     * El nombre del archivo de una foto, **aunque la ruta sea de otro teléfono** (la principal): de una ruta
     * `file://…/abc.jpg` sale `abc.jpg`. Null si el nombre no es válido (nada de rutas ni subcarpetas).
     */
    fun nombreDe(uri: String): String? {
        val n = uri.substringAfterLast('/')
        return n.takeIf { AlmacenFotos.NOMBRE_OK.matches(it) }
    }

    /** La ruta propia del archivo [nombre], si está en el álbum de esta app (null si no está). */
    fun uriLocal(nombre: String): String? = almacen.archivoPorNombre(nombre)?.let { almacen.uriDe(it) }

    /** Tamaño del archivo (null si no está). */
    fun tamano(nombre: String): Long? = almacen.archivoPorNombre(nombre)?.length()

    /** SHA-256 del archivo (se calcula una vez y se cachea). null si el archivo no está. */
    fun sha256(nombre: String): String? {
        val f = almacen.archivoPorNombre(nombre) ?: return null
        return hashes.getOrPut(nombre) {
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { i ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = i.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().aHex()
        }
    }

    /** Bloque de la foto [nombre] desde [desde] (hasta [max] bytes). null si la foto no está o el inicio no vale. */
    fun bloque(nombre: String, desde: Long, max: Int): ByteArray? {
        val f = almacen.archivoPorNombre(nombre) ?: return null
        if (desde < 0 || desde > f.length()) return null
        val n = minOf(max.toLong(), f.length() - desde).toInt()
        val buf = ByteArray(n)
        RandomAccessFile(f, "r").use { r -> r.seek(desde); r.readFully(buf) }
        return buf
    }

    /** Guarda la foto descargada con su nombre; devuelve la URI propia. null si el nombre no vale. */
    fun guardar(nombre: String, datos: ByteArray): String? {
        val f = almacen.guardarBytes(nombre, datos) ?: return null
        hashes.remove(nombre)
        return almacen.uriDe(f)
    }

    /** SHA-256 de unos bytes (para comprobar lo que llega de la principal). */
    fun sha256De(datos: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(datos).aHex()

    /** Bloque de la foto [nombre] para repartirla por la red local (la base la hace el mensaje). */
    fun bloque(id: Long, nombre: String, desde: Long): BloqueFoto {
        val total = tamano(nombre) ?: return BloqueFoto(id, desde, error = SIN_FOTO)
        if (desde < 0 || desde > total) return BloqueFoto(id, desde, error = SIN_FOTO)
        val datos = bloque(nombre, desde, BLOQUE) ?: return BloqueFoto(id, desde, error = SIN_FOTO)
        return BloqueFoto(id, desde, datos = Base64.getEncoder().encodeToString(datos), total = total, sha256 = sha256(nombre))
    }

    companion object {
        /** Tamaño del bloque (como el del APK). */
        const val BLOQUE = 256 * 1024
        const val SIN_FOTO = "sin_foto"
    }
}
