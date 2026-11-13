package cu.shalev.data.respaldo

import cu.shalev.data.io.leerAcotado
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado de respaldos con contraseña, portable entre dispositivos (sin Keystore).
 *
 * Formato «Shalev-BACKUP» v4 (0.27.0, T10, big-endian), el que se escribe:
 * ```
 * "SPVIBAK" (7) | versión (1) = 4 | indicador (1) | creadoEn ms (8) | iteraciones (4) | salt (16) | iv (12)
 *   | largo del cifrado (8) | SHA-256 del cifrado (32) | AES-256-GCM( gzip(json) ) + tag(16)
 * ```
 * - Indicador: 1 = con contraseña del usuario; 0 = sin contraseña (la clave sale de un secreto interno ofuscado de la
 *   app, con [ITERACIONES_SIN_CONTRASENA]). **Riesgo aceptado** (SECURITY.md): sin contraseña, cualquiera con Shalev
 *   puede abrir el archivo; el cifrado solo evita que se lea o se altere con otras herramientas.
 *   Auditoría 2026-10-14 (S-01): se comprobó que el «secreto» se deriva solo de constantes XOR publicadas en este
 *   repositorio (cualquiera puede reproducirlo sin el APK), así que el modo sin contraseña NO aporta
 *   confidencialidad: es ofuscación. Por eso la exportación trae la contraseña encendida por defecto. No subir iteraciones aquí esperando
 *   seguridad: sin un secreto real, el KDF no protege nada.
 * - v3 (P17) se sigue leyendo: igual que v4 pero sin el byte de indicador y siempre con contraseña.
 * - Contraseña vacía al cifrar = sin contraseña.
 * - KDF: PBKDF2-HMAC-SHA256, 310 000 iteraciones por defecto (OWASP 2023), salt aleatorio por archivo.
 * - AAD = los primeros [AAD] bytes (todo salvo la suma de control): alterar fecha, iteraciones, salt, iv o
 *   largo invalida el tag.
 * - **Largo + SHA-256 no son seguridad** (cualquiera puede recalcularlos; eso lo cubre GCM): sirven para decir,
 *   SIN contraseña, si el archivo llegó **cortado** o **dañado** ([ArchivoDanadoException]), y no confundirlo
 *   con una contraseña incorrecta.
 * - La fecha queda legible sin contraseña para avisar de qué día es la copia. No es un dato personal.
 * - v1 (P12) y v2 (P14) ya no se leen (P17, sin instalaciones reales): [VersionAnteriorException].
 *
 * Descompresión acotada a [MAX_PLANO] (defensa ante "zip bombs").
 */
class BackupCipher(
    private val random: SecureRandom = SecureRandom(),
    private val iteraciones: Int = ITERACIONES,
) {
    class ContrasenaIncorrectaException : Exception()
    open class FormatoException(msg: String) : Exception(msg)
    /** Respaldo de Shalev, pero de un formato anterior (v1/v2) que esta versión ya no abre. */
    class VersionAnteriorException(val version: Int) : FormatoException("respaldo de una versión anterior de Shalev ($version)")
    /** [incompleto]: faltan bytes (copia interrumpida); si no, sobran o cambiaron. */
    class ArchivoDanadoException(val incompleto: Boolean) : Exception(if (incompleto) "archivo incompleto" else "archivo dañado")

    /** Lo legible sin contraseña. [conContrasena] = false: se abre sin pedirla (v4 con indicador 0). */
    data class Cabecera(val version: Int, val creadoEnMs: Long, val conContrasena: Boolean = true)

    /** Contraseña vacía = sin contraseña (0.27.0, T10). */
    fun cifrar(plano: ByteArray, contrasena: CharArray, creadoEnMs: Long = 0): ByteArray =
        escribir(plano, contrasena, creadoEnMs, VERSION)

    /** Escribe el formato v3 anterior (siempre con contraseña). Solo para las pruebas de compatibilidad. */
    fun cifrarFormatoV3(plano: ByteArray, contrasena: CharArray, creadoEnMs: Long = 0): ByteArray {
        require(contrasena.isNotEmpty())
        return escribir(plano, contrasena, creadoEnMs, VERSION_V3)
    }

    private fun escribir(plano: ByteArray, contrasena: CharArray, creadoEnMs: Long, version: Byte): ByteArray {
        exigirTamanoRespaldo(plano.size.toLong())
        val conContrasena = contrasena.isNotEmpty()
        val iter = if (conContrasena) iteraciones else ITERACIONES_SIN_CONTRASENA
        val salt = ByteArray(SALT).also(random::nextBytes)
        val iv = ByteArray(IV).also(random::nextBytes)
        val comprimido = gzip(plano)
        val largo = comprimido.size.toLong() + TAG_BITS / 8
        val aadLen = aad(version.toInt())
        val buf = ByteBuffer.allocate(aadLen).put(MAGIC).put(version)
        if (version == VERSION) buf.put(if (conContrasena) CON_CONTRASENA else SIN_CONTRASENA)
        val aad = buf.putLong(creadoEnMs).putInt(iter).put(salt).put(iv).putLong(largo).array()
        val clave = if (conContrasena) derivar(contrasena, salt, iter) else derivarInterna(salt, iter)
        val cifrado = gcm(Cipher.ENCRYPT_MODE, clave, iv, aad).doFinal(comprimido)
        check(cifrado.size.toLong() == largo)
        // Un gzip de datos no compresibles puede crecer: nunca exportar un archivo que el lector rechazaría.
        exigirTamanoRespaldo(aad.size.toLong() + 32 + cifrado.size)
        return aad + sha256(cifrado) + cifrado
    }

    /**
     * Valida el archivo sin contraseña. Lanza [FormatoException] si no es de Shalev o es de otra versión del formato
     * ([VersionAnteriorException] si es v1/v2) y [ArchivoDanadoException] si está cortado o alterado.
     */
    fun inspeccionar(datos: ByteArray): Cabecera {
        exigirTamanoRespaldo(datos.size.toLong())
        if (datos.size < MAGIC.size + 1 || !datos.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw FormatoException("no es un respaldo Shalev")
        }
        val version = datos[MAGIC.size].toInt()
        if (version in 1 until VERSION_V3) throw VersionAnteriorException(version)
        if (version != VERSION_V3.toInt() && version != VERSION.toInt()) throw FormatoException("respaldo de una versión más nueva de Shalev ($version)")
        val aadLen = aad(version)
        val cabLen = aadLen + 32
        if (datos.size < cabLen) throw ArchivoDanadoException(incompleto = true)
        val buf = ByteBuffer.wrap(datos)
        buf.position(MAGIC.size + 1)
        val conContrasena = if (version == VERSION.toInt()) {
            when (buf.get()) {
                CON_CONTRASENA -> true
                SIN_CONTRASENA -> false
                else -> throw FormatoException("indicador de contraseña inválido")
            }
        } else true
        val creado = buf.long
        val iter = buf.int
        if (iter !in MIN_ITER..MAX_ITER) throw FormatoException("parámetros de cifrado inválidos")
        buf.position(aadLen - 8)
        val largo = buf.long
        val real = (datos.size - cabLen).toLong()
        if (largo < TAG_BITS / 8 || largo > MAX_PLANO) throw ArchivoDanadoException(incompleto = false)
        if (real < largo) throw ArchivoDanadoException(incompleto = true)
        if (real > largo) throw ArchivoDanadoException(incompleto = false)
        val esperado = datos.copyOfRange(aadLen, cabLen)
        val md = MessageDigest.getInstance("SHA-256").apply { update(datos, cabLen, real.toInt()) }
        if (!MessageDigest.isEqual(esperado, md.digest())) throw ArchivoDanadoException(incompleto = false)
        return Cabecera(version, creado, conContrasena)
    }

    /** Sin contraseña ([Cabecera.conContrasena] = false) la [contrasena] recibida se ignora. */
    fun descifrar(datos: ByteArray, contrasena: CharArray): ByteArray {
        val cab = inspeccionar(datos)
        val aadLen = aad(cab.version)
        val cabLen = aadLen + 32
        val buf = ByteBuffer.wrap(datos)
        buf.position(MAGIC.size + 1 + (if (cab.version == VERSION.toInt()) 1 else 0) + 8)
        val iter = buf.int
        val salt = ByteArray(SALT).also { buf.get(it) }
        val iv = ByteArray(IV).also { buf.get(it) }
        if (cab.conContrasena && contrasena.isEmpty()) throw ContrasenaIncorrectaException()
        val clave = if (cab.conContrasena) derivar(contrasena, salt, iter) else derivarInterna(salt, iter)
        val cipher = gcm(Cipher.DECRYPT_MODE, clave, iv, datos.copyOfRange(0, aadLen))
        val comprimido = try {
            cipher.doFinal(datos, cabLen, datos.size - cabLen)
        } catch (e: AEADBadTagException) {
            throw ContrasenaIncorrectaException()
        }
        return gunzip(comprimido)
    }

    private fun gcm(modo: Int, key: SecretKeySpec, iv: ByteArray, aad: ByteArray): Cipher =
        Cipher.getInstance(AES).apply {
            init(modo, key, GCMParameterSpec(TAG_BITS, iv))
            updateAAD(aad)
        }

    private fun derivar(contrasena: CharArray, salt: ByteArray, iter: Int): SecretKeySpec {
        val spec = PBEKeySpec(contrasena, salt, iter, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    /** Secreto interno de los respaldos sin contraseña: se arma en tiempo de ejecución (no queda como texto). */
    private fun derivarInterna(salt: ByteArray, iter: Int): SecretKeySpec {
        val secreto = CharArray(SECRETO.size) { i -> Char(SECRETO[i] xor MASCARA[i % MASCARA.size]) }
        try {
            return derivar(secreto, salt, iter)
        } finally {
            secreto.fill('\u0000')
        }
    }

    private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

    private fun gzip(b: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(b) } }.toByteArray()

    private fun gunzip(b: ByteArray): ByteArray = try {
        GZIPInputStream(b.inputStream()).use { it.leerAcotado(MAX_PLANO) { FormatoException("respaldo demasiado grande") } }
    } catch (e: java.io.IOException) {
        throw FormatoException("contenido corrupto")
    }

    companion object {
        private val MAGIC = "SPVIBAK".toByteArray(Charsets.US_ASCII)
        private const val VERSION: Byte = 4
        private const val VERSION_V3: Byte = 3
        private const val CON_CONTRASENA: Byte = 1
        private const val SIN_CONTRASENA: Byte = 0
        /** Ofuscación del secreto interno (XOR). No es seguridad: ver SECURITY.md (riesgo aceptado). */
        private val MASCARA = intArrayOf(0x5A, 0x13, 0x7C, 0x29, 0x44, 0x61, 0x0E, 0x38)
        private val SECRETO = intArrayOf(
            0x09, 0x43, 0x2A, 0x60, 0x69, 0x23, 0x4F, 0x7B, 0x11, 0x3E, 0x13, 0x47, 0x23, 0x0E, 0x6A, 0x5D,
            0x74, 0x21, 0x4C, 0x1B, 0x73, 0x4C, 0x3C, 0x0F, 0x6B, 0x5F, 0x3D, 0x7D, 0x1D, 0x24, 0x3F, 0x7E,
        )
        /** Iteraciones de PBKDF2 sin contraseña: el secreto no se adivina, así que bastan pocas. */
        const val ITERACIONES_SIN_CONTRASENA = 10_000
        private fun aad(version: Int): Int = if (version == VERSION.toInt()) AAD + 1 else AAD
        private const val SALT = 16
        private const val IV = 12
        private const val TAG_BITS = 128
        private const val AES = "AES/GCM/NoPadding"
        /** Parte autenticada de la cabecera v3 (todo salvo la suma de control); en v4 es un byte más (indicador). */
        const val AAD = 7 + 1 + 8 + 4 + SALT + IV + 8
        const val CABECERA = AAD + 32
        const val AAD_V4 = AAD + 1
        const val CABECERA_V4 = AAD_V4 + 32
        const val ITERACIONES = 310_000
        private const val MIN_ITER = 10_000
        private const val MAX_ITER = 5_000_000
        /** Tope del JSON descomprimido y del archivo leído. */
        const val MAX_PLANO = 128L * 1024 * 1024
    }
}

/** Mismo límite al escribir y leer; toma Long para probar los bordes sin reservar cientos de MiB en los tests. */
internal fun exigirTamanoRespaldo(bytes: Long) {
    if (bytes !in 0L..BackupCipher.MAX_PLANO) throw BackupCipher.FormatoException("respaldo demasiado grande")
}
