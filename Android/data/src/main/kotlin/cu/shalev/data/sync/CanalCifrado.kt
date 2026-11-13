package cu.shalev.data.sync

import cu.shalev.data.io.leerAcotado
import cu.shalev.licencia.crypto.AesGcm
import cu.shalev.licencia.crypto.EcP256
import cu.shalev.licencia.crypto.Hkdf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Fallo del protocolo (mensaje ilegible, clave incorrecta, tamaño excesivo…). Nunca lleva datos del negocio. */
class ProtocoloException(mensaje: String) : IOException(mensaje)

/** Primitivas del protocolo P37 (JVM puro, testeadas en CanalCifradoTest). */
object CriptoSync {
    private val azar = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    const val CLAVE_BYTES = 32
    const val NONCE_BYTES = 16

    fun aleatorio(n: Int): ByteArray = ByteArray(n).also(azar::nextBytes)
    fun enc(b: ByteArray): String = b64.encodeToString(b)
    fun dec(s: String): ByteArray = try { b64d.decode(s) } catch (e: IllegalArgumentException) { throw ProtocoloException("base64") }

    fun hmac(clave: ByteArray, vararg partes: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(clave, "HmacSHA256"))
        partes.forEach { update(it) }
        doFinal()
    }

    fun iguales(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    // ---- vinculación (ECDH P-256 autenticado con el token del QR)
    private val ETQ_C = "spvi-vinc-c".toByteArray()
    private val ETQ_S = "spvi-vinc-s".toByteArray()
    private val INFO_CLAVE = "spvi-clave-empleado-v1".toByteArray()

    fun macVinculoSecundaria(token: ByteArray, pubC: ByteArray) = hmac(token, ETQ_C, pubC)
    fun macVinculoPrincipal(token: ByteArray, pubS: ByteArray, pubC: ByteArray) = hmac(token, ETQ_S, pubS, pubC)

    fun parNuevo(): KeyPair = EcP256.generate()

    /** Clave definitiva del empleado: HKDF(ECDH, sal = token). Las dos apps llegan a la misma sin que viaje. */
    fun claveEmpleado(propio: KeyPair, pubOtroSpki: ByteArray, token: ByteArray): ByteArray {
        val compartido = EcP256.ecdh(propio.private, EcP256.publicKey(pubOtroSpki))
        return try { Hkdf.sha256(compartido, token, INFO_CLAVE, CLAVE_BYTES) } finally { compartido.fill(0) }
    }

    // ---- sesión
    /** Claves de sesión por sentido: una conexión nueva = claves nuevas (no se puede reenviar tráfico viejo). */
    fun clavesSesion(clave: ByteArray, nonceC: ByteArray, nonceS: ByteArray): Pair<ByteArray, ByteArray> {
        val sal = nonceC + nonceS
        return Hkdf.sha256(clave, sal, "spvi-c2s-v1".toByteArray(), CLAVE_BYTES) to
            Hkdf.sha256(clave, sal, "spvi-s2c-v1".toByteArray(), CLAVE_BYTES)
    }

    fun macRechazo(clave: ByteArray, nonceC: ByteArray, motivo: String) = hmac(clave, "spvi-rechazo".toByteArray(), nonceC, motivo.toByteArray())
}

/**
 * Tramas: longitud (4 B, big-endian) + contenido. Máximo [MAX_TRAMA] para que un mensaje malicioso no agote la memoria.
 */
object Tramas {
    const val MAX_TRAMA = 16 * 1024 * 1024

    /** 0.32.0: tamaño del trozo al escribir/leer (para poder informar del avance). */
    private const val TROZO = 64 * 1024

    fun escribir(out: OutputStream, datos: ByteArray, progreso: ((Long, Long) -> Unit)? = null) {
        val d = DataOutputStream(out)
        d.writeInt(datos.size)
        var hecho = 0
        while (hecho < datos.size) {
            val n = minOf(TROZO, datos.size - hecho)
            d.write(datos, hecho, n)
            hecho += n
            progreso?.invoke(hecho.toLong(), datos.size.toLong())
        }
        d.flush()
    }

    fun leer(input: InputStream, progreso: ((Long, Long) -> Unit)? = null): ByteArray {
        val d = DataInputStream(input)
        val n = try { d.readInt() } catch (e: EOFException) { throw e }
        if (n < 0 || n > MAX_TRAMA) throw ProtocoloException("trama de $n bytes")
        val buf = ByteArray(n)
        var hecho = 0
        while (hecho < n) {
            val leido = d.read(buf, hecho, n - hecho)
            if (leido < 0) throw ProtocoloException("trama incompleta")
            hecho += leido
            progreso?.invoke(hecho.toLong(), n.toLong())
        }
        return buf
    }

    fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { bos -> GZIPOutputStream(bos).use { it.write(b) } }.toByteArray()

    fun gunzip(b: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(b)).use { it.leerAcotado(4L * MAX_TRAMA) { ProtocoloException("contenido demasiado grande") } }
}

/**
 * Canal cifrado de una sesión: AES-256-GCM con clave distinta por sentido, IV aleatorio de 12 B y número de secuencia
 * en los datos autenticados (AAD). Un mensaje reordenado, repetido o alterado no se descifra y la sesión se corta.
 * No es seguro para hilos: quien lo usa serializa las escrituras (Mutex) y lee desde una sola corrutina.
 */
class CanalCifrado(
    private val input: InputStream,
    private val output: OutputStream,
    private val claveEnvio: ByteArray,
    private val claveRecepcion: ByteArray,
    private val etiquetaEnvio: Byte,
    private val etiquetaRecepcion: Byte,
) {
    private var secEnvio = 0L
    private var secRecepcion = 0L

    fun enviar(m: Mensaje, progreso: ((Long, Long) -> Unit)? = null) {
        val plano = Tramas.gzip(SyncJson.encodeToString(Mensaje.serializer(), m).toByteArray(Charsets.UTF_8))
        val iv = CriptoSync.aleatorio(IV)
        val ct = AesGcm.encrypt(claveEnvio, iv, aad(etiquetaEnvio, secEnvio++), plano)
        Tramas.escribir(output, iv + ct, progreso)
    }

    fun recibir(progreso: ((Long, Long) -> Unit)? = null): Mensaje {
        val trama = Tramas.leer(input, progreso)
        if (trama.size < IV + 16) throw ProtocoloException("trama corta")
        val plano = try {
            AesGcm.decrypt(claveRecepcion, trama.copyOfRange(0, IV), aad(etiquetaRecepcion, secRecepcion++), trama.copyOfRange(IV, trama.size))
        } catch (e: Exception) {
            throw ProtocoloException("no se pudo descifrar")
        }
        return try {
            SyncJson.decodeFromString(Mensaje.serializer(), String(Tramas.gunzip(plano), Charsets.UTF_8))
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw ProtocoloException("mensaje ilegible")
        }
    }

    fun borrarClaves() {
        claveEnvio.fill(0); claveRecepcion.fill(0)
    }

    companion object {
        const val IV = 12
        const val DE_SECUNDARIA: Byte = 1
        const val DE_PRINCIPAL: Byte = 2

        private fun aad(etiqueta: Byte, sec: Long): ByteArray =
            ByteBuffer.allocate(8 + 1 + 8).put("SPVI-S1\u0000".toByteArray()).put(etiqueta).putLong(sec).array()

        fun paraSecundaria(input: InputStream, output: OutputStream, claves: Pair<ByteArray, ByteArray>) =
            CanalCifrado(input, output, claves.first, claves.second, DE_SECUNDARIA, DE_PRINCIPAL)

        fun paraPrincipal(input: InputStream, output: OutputStream, claves: Pair<ByteArray, ByteArray>) =
            CanalCifrado(input, output, claves.second, claves.first, DE_PRINCIPAL, DE_SECUNDARIA)
    }
}

/** Saludo en claro (JSON sin comprimir). */
object Saludos {
    fun enviar(out: OutputStream, s: Saludo) = Tramas.escribir(out, SyncJson.encodeToString(Saludo.serializer(), s).toByteArray(Charsets.UTF_8))

    fun recibir(input: InputStream): Saludo {
        val t = Tramas.leer(input)
        if (t.size > 64 * 1024) throw ProtocoloException("saludo demasiado grande")
        return try {
            SyncJson.decodeFromString(Saludo.serializer(), String(t, Charsets.UTF_8))
        } catch (e: Exception) {
            throw ProtocoloException("saludo ilegible")
        }
    }
}
