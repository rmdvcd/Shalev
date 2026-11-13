package cu.shalev.data.sync

import android.content.Context
import android.os.Build
import cu.shalev.core.util.aHex
import cu.shalev.domain.model.InfoApp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

/**
 * 0.25.0 (P70): APK de actualización guardado en la app PRINCIPAL para repartirlo a sus secundarias por la red
 * local (sin que cada una lo baje de Internet), y archivo de recepción en la secundaria.
 *
 * - Principal: tras descargar de GitHub y comprobar el SHA-256, [guardar] copia el APK a `filesDir/actualizacion`
 *   (privado; no lo ve ninguna otra app) junto con sus datos. Solo se guarda el más nuevo.
 * - Secundaria: recibe por bloques en `cacheDir/actualizacion/recibida.apk` y comprueba el SHA-256 al terminar.
 */
@Singleton
class ApkLocal @Inject constructor(
    @ApplicationContext private val context: Context,
    private val info: InfoApp,
) {
    @Serializable
    private data class Meta(val nombre: String, val code: Int, val sha256: String, val bytes: Long)

    private val dir: File get() = File(context.filesDir, "actualizacion")
    private val apk: File get() = File(dir, "shalev.apk")
    private val meta: File get() = File(dir, "shalev.json")

    /** Archivo donde la secundaria va escribiendo los bloques recibidos. */
    val recepcion: File get() = File(context.cacheDir, "actualizacion/recibida.apk")

    /**
     * Principal: guarda [origen] (ya verificado) para repartirlo. [nombre] = versión («0.25.1»). El versionCode se lee del
     * propio APK; si no se puede leer, no se reparte (la secundaria no sabría si es más nuevo).
     */
    @Synchronized
    fun guardar(origen: File, nombre: String, sha256: String): Boolean {
        val code = versionCodeDe(origen) ?: return false
        val actual = disponible()
        if (actual != null && actual.code >= code) return true
        dir.mkdirs()
        val tmp = File(dir, "shalev.apk.tmp")
        origen.copyTo(tmp, overwrite = true)
        if (!tmp.renameTo(apk)) { apk.delete(); if (!tmp.renameTo(apk)) return false }
        meta.writeText(SyncJson.encodeToString(Meta.serializer(), Meta(nombre, code, sha256.lowercase(), apk.length())))
        return true
    }

    /** Principal: APK guardado (null si no hay o está incompleto). */
    @Synchronized
    fun disponible(): VersionApk? {
        val m = runCatching { SyncJson.decodeFromString(Meta.serializer(), meta.readText()) }.getOrNull() ?: return null
        if (!apk.isFile || apk.length() != m.bytes) return null
        return VersionApk(m.nombre, m.code, m.sha256, m.bytes)
    }

    /** Principal: lo que se ofrece a una secundaria con [versionCode] instalado (solo si es más nuevo). */
    fun ofrecerA(versionCode: Int?): VersionApk? = disponible()?.takeIf { versionCode != null && it.code > versionCode }

    /** Principal: bloque de [BLOQUE] bytes desde [desde], o error si el APK ya no es el pedido. */
    fun bloque(id: Long, sha256: String, desde: Long): BloqueApk {
        val v = disponible() ?: return BloqueApk(id, desde, error = SIN_APK)
        if (!v.sha256.equals(sha256, ignoreCase = true)) return BloqueApk(id, desde, error = OTRO_APK)
        if (desde < 0 || desde > v.bytes) return BloqueApk(id, desde, error = OTRO_APK)
        val n = minOf(BLOQUE.toLong(), v.bytes - desde).toInt()
        val buf = ByteArray(n)
        RandomAccessFile(apk, "r").use { f -> f.seek(desde); f.readFully(buf) }
        return BloqueApk(id, desde, Base64.getEncoder().encodeToString(buf), v.bytes)
    }

    /** ¿Es más nuevo que la app instalada? */
    fun masNuevo(v: VersionApk): Boolean = v.code > info.versionCode

    @Suppress("DEPRECATION")
    private fun versionCodeDe(f: File): Int? = runCatching {
        val pi = context.packageManager.getPackageArchiveInfo(f.absolutePath, 0) ?: return null
        if (pi.packageName != context.packageName) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.longVersionCode.toInt() else pi.versionCode
    }.getOrNull()

    companion object {
        const val BLOQUE = 256 * 1024
        const val SIN_APK = "sin_apk"
        const val OTRO_APK = "otro_apk"

        fun sha256(f: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            java.security.DigestInputStream(f.inputStream(), md).use { it.readBytes() }
            return md.digest().aHex()
        }
    }
}
