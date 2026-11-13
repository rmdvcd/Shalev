package cu.shalev.licencia.prueba

import cu.shalev.core.util.aHex
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.CRC32
import java.util.zip.Deflater
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 0.26.0 (P74): registro de la prueba que sobrevive a desinstalar (copias fuera de la app, ver
 * `data/licencia/prueba/RegistroPruebaAndroid`). JSON pedido: `{"firstInstall": Long, "trialDays": Int, "version": 1}`.
 * `lastSeen` (opcional, añadido): última fecha vista, para que la detección de reloj atrasado también siga tras reinstalar.
 */
@Serializable
data class RegistroPrueba(
    val firstInstall: Long,
    val trialDays: Int,
    val version: Int = VERSION,
    val lastSeen: Long? = null,
) {
    companion object {
        const val VERSION = 1
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun aJson(r: RegistroPrueba): String = json.encodeToString(serializer(), r)
        /** null = no es un registro válido (JSON roto, versión desconocida o valores imposibles). */
        fun deJson(s: String): RegistroPrueba? = runCatching { json.decodeFromString(serializer(), s) }.getOrNull()
            ?.takeIf { it.version == VERSION && it.firstInstall > 0 && it.trialDays in 1..365 }
    }
}

/**
 * Cifrado del registro. Clave = PBKDF2WithHmacSHA256(ANDROID_ID, sal fija ofuscada, 10 000 iteraciones, 256 bits);
 * AES/GCM/NoPadding con IV aleatorio de 12 bytes y etiqueta de 128 bits; blob = IV ‖ texto cifrado (con la etiqueta).
 * Protección débil a propósito (pensada para un usuario promedio): ANDROID_ID no es secreto.
 */
class CifradoPrueba(androidId: String, private val random: SecureRandom = SecureRandom()) {
    private val clave: SecretKeySpec = run {
        val spec = PBEKeySpec(androidId.ifEmpty { "0" }.toCharArray(), Ofuscado.sal(), ITERACIONES, BITS)
        try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun cifrar(r: RegistroPrueba): ByteArray {
        val iv = ByteArray(IV).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, clave, GCMParameterSpec(TAG, iv)) }
        return iv + c.doFinal(RegistroPrueba.aJson(r).toByteArray(Charsets.UTF_8))
    }

    /** null = no se pudo descifrar (otro teléfono, archivo dañado o modificado). Nunca lanza. */
    fun descifrar(blob: ByteArray?): RegistroPrueba? = runCatching {
        if (blob == null || blob.size < IV + TAG / 8 + 2) return null
        val c = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, clave, GCMParameterSpec(TAG, blob, 0, IV)) }
        RegistroPrueba.deJson(String(c.doFinal(blob, IV, blob.size - IV), Charsets.UTF_8))
    }.getOrNull()

    companion object {
        const val ITERACIONES = 10_000
        const val BITS = 256
        const val IV = 12
        const val TAG = 128
    }
}

/**
 * Cadenas sensibles sin texto plano en el APK (R8 no cifra cadenas): se guardan con XOR y se reconstruyen al usarse.
 * Solo frena a curiosos; no es seguridad real.
 */
object Ofuscado {
    private const val K = 0x5A
    private fun d(vararg b: Int): String = String(ByteArray(b.size) { (b[it] xor K).toByte() }, Charsets.UTF_8)

    // Sal de PBKDF2 (XOR).
    private val SAL = intArrayOf(0x29, 0x2A, 0x2C, 0x33, 0x77, 0x2A, 0x28, 0x2F, 0x3F, 0x38, 0x3B, 0x77, 0x29, 0x3B, 0x36, 0x77, 0x2C, 0x6B)
    // Semilla de los nombres de archivo (XOR).
    private val SEMILLA = intArrayOf(0x29, 0x2A, 0x2C, 0x33, 0x77, 0x28, 0x3F, 0x3D, 0x33, 0x29, 0x2E, 0x28, 0x35, 0x77, 0x2A, 0x28, 0x2F, 0x3F, 0x38, 0x3B)

    fun sal(): ByteArray = d(*SAL).toByteArray(Charsets.UTF_8)

    /** Huella corta y estable para los nombres de archivo (no revela qué son). */
    fun huella(): String = MessageDigest.getInstance("SHA-256").digest(d(*SEMILLA).toByteArray(Charsets.UTF_8))
        .take(4).toByteArray().aHex()

    /** Copias .bin en Download y Documents: `.sys_<huella>.bin` (oculto). */
    fun nombreBin(): String = ".sys_" + huella() + ".bin"

    /** Copia en Imágenes: un PNG normal (los archivos ocultos no entran en la galería ni en MediaStore.Images). */
    fun nombreImagen(): String = "sys_" + huella() + ".png"

    /** Copia interna (filesDir). */
    fun nombreInterno(): String = ".sys_" + huella() + ".bin"
}

/**
 * PNG válido (cuadrado de un color) con el blob cifrado en un fragmento privado `spVi` antes de IEND. Los visores lo
 * ignoran; MediaStore guarda el archivo tal cual. Así la copia puede vivir en Imágenes, la única colección que una app
 * reinstalada puede volver a leer (con el permiso de fotos) en Android 10+.
 */
object PngRegistro {
    private val FIRMA = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private const val TIPO = "spVi"
    const val LADO = 48

    fun crear(blob: ByteArray, rgb: Int = 0x2B4FA3): ByteArray {
        val ihdr = ByteBuffer.allocate(13).putInt(LADO).putInt(LADO).put(8).put(2).put(0).put(0).put(0).array()
        val fila = ByteArray(1 + LADO * 3).also { f ->
            for (x in 0 until LADO) { f[1 + x * 3] = (rgb shr 16).toByte(); f[2 + x * 3] = (rgb shr 8).toByte(); f[3 + x * 3] = rgb.toByte() }
        }
        val crudo = ByteArray(fila.size * LADO).also { for (y in 0 until LADO) fila.copyInto(it, y * fila.size) }
        val idat = Deflater(9).run {
            setInput(crudo); finish()
            val buf = ByteArray(crudo.size + 64); val n = deflate(buf); end(); buf.copyOf(n)
        }
        return FIRMA + trozo("IHDR", ihdr) + trozo("IDAT", idat) + trozo(TIPO, blob) + trozo("IEND", ByteArray(0))
    }

    /** El blob del fragmento `spVi` con su CRC correcto, o null. */
    fun extraer(png: ByteArray?): ByteArray? {
        if (png == null || png.size < FIRMA.size + 12 || !png.copyOfRange(0, FIRMA.size).contentEquals(FIRMA)) return null
        var i = FIRMA.size
        while (i + 12 <= png.size) {
            val largo = ByteBuffer.wrap(png, i, 4).int
            if (largo < 0 || i + 12 + largo > png.size) return null
            val tipo = String(png, i + 4, 4, Charsets.US_ASCII)
            if (tipo == TIPO) {
                val datos = png.copyOfRange(i + 8, i + 8 + largo)
                val crc = ByteBuffer.wrap(png, i + 8 + largo, 4).int
                return datos.takeIf { crc(tipo, it) == crc }
            }
            if (tipo == "IEND") return null
            i += 12 + largo
        }
        return null
    }

    private fun trozo(tipo: String, datos: ByteArray): ByteArray =
        ByteBuffer.allocate(12 + datos.size).putInt(datos.size).put(tipo.toByteArray(Charsets.US_ASCII)).put(datos).putInt(crc(tipo, datos)).array()

    private fun crc(tipo: String, datos: ByteArray): Int =
        CRC32().apply { update(tipo.toByteArray(Charsets.US_ASCII)); update(datos) }.value.toInt()
}

/** Lo que dio cada copia al leerla. */
sealed interface LecturaCopia {
    data object Ausente : LecturaCopia
    /** Existe pero no descifra: se IGNORA (P74: no bloquea) y se reescribe. */
    data object Ilegible : LecturaCopia
    data class Valida(val registro: RegistroPrueba) : LecturaCopia
}

/**
 * Resultado de juntar todas las copias: el `firstInstall` MÁS ANTIGUO y el `lastSeen` más reciente de las válidas;
 * qué copias hay que (re)escribir; si no había ninguna válida (primera instalación).
 */
data class CopiasCombinadas<C>(
    val firstInstall: Long?,
    val lastSeen: Long?,
    val reescribir: Set<C>,
    val danadas: Set<C>,
) {
    val primeraInstalacion: Boolean get() = firstInstall == null
}

fun <C> combinarCopias(lecturas: Map<C, List<LecturaCopia>>): CopiasCombinadas<C> {
    val validas = lecturas.values.flatten().filterIsInstance<LecturaCopia.Valida>().map { it.registro }
    val danadas = lecturas.filterValues { l -> l.any { it == LecturaCopia.Ilegible } }.keys
    val sinValida = lecturas.filterValues { l -> l.none { it is LecturaCopia.Valida } }.keys
    return CopiasCombinadas(
        firstInstall = validas.minOfOrNull { it.firstInstall },
        lastSeen = validas.mapNotNull { it.lastSeen }.maxOrNull(),
        reescribir = sinValida + danadas,
        danadas = danadas,
    )
}

/**
 * Detección de reloj atrasado con el reloj monótono (`SystemClock.elapsedRealtime()`), que el usuario no puede cambiar
 * pero vuelve a 0 al reiniciar: solo se comparan marcas del MISMO arranque (`Settings.Global.BOOT_COUNT`). Se combina
 * con la de `lastSeen` (que sí sobrevive a reinicios y, con el registro externo, a reinstalar).
 */
data class MarcaReloj(val pared: Long, val monotono: Long, val arranque: Int) {
    /** true = desde [previa] la fecha del teléfono retrocedió más que [toleranciaMs] respecto del tiempo real pasado. */
    fun retrocedioDesde(previa: MarcaReloj, toleranciaMs: Long): Boolean {
        if (previa.arranque != arranque || arranque < 0 || monotono < previa.monotono) return false
        val esperado = previa.pared + (monotono - previa.monotono)
        return pared < esperado - toleranciaMs
    }

    fun codificar(): String = "$pared|$monotono|$arranque"

    companion object {
        fun decodificar(s: String?): MarcaReloj? = s?.split('|')?.takeIf { it.size == 3 }?.let { p ->
            val a = p[0].toLongOrNull(); val b = p[1].toLongOrNull(); val c = p[2].toIntOrNull()
            if (a != null && b != null && c != null) MarcaReloj(a, b, c) else null
        }
    }
}

/** Puerto del registro externo (implementado en :data con MediaStore). */
interface RegistroExterno {
    /** Lee y junta todas las copias, restaura las que falten o estén dañadas y devuelve lo combinado. Nunca lanza. */
    suspend fun sincronizar(trialStart: Long?, lastSeen: Long?, trialDays: Int): CopiasCombinadas<*>
}

/** Puerto del reloj monótono (implementado en :data). true = la fecha del teléfono se atrasó en este arranque. */
interface DetectorRetroceso {
    suspend fun retrocedio(ahoraMs: Long): Boolean
}
