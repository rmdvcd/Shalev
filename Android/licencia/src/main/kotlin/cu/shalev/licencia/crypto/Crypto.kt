package cu.shalev.licencia.crypto

import cu.shalev.licencia.contract.GlContract
import cu.shalev.core.util.aHex
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Error genérico: nunca se filtra el motivo concreto al usuario (contrato: "no filtrar detalles"). */
class LicenseCryptoException : Exception("Licencia no válida")

internal fun fail(): Nothing = throw LicenseCryptoException()

object B64 {
    fun enc(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    fun dec(s: String): ByteArray = try { Base64.getDecoder().decode(s) } catch (e: IllegalArgumentException) { fail() }
    fun urlNoPad(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    fun urlDec(s: String): ByteArray = try { Base64.getUrlDecoder().decode(s) } catch (e: IllegalArgumentException) { fail() }
}

/** Primitivas P-256 (secp256r1). */
object EcP256 {
    private val ORDER = BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16)

    fun generate(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    /** Decodifica SPKI DER y exige que la curva sea P-256. */
    fun publicKey(spkiDer: ByteArray): ECPublicKey {
        val key = try {
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(spkiDer)) as ECPublicKey
        } catch (e: Exception) { fail() }
        if (key.params.order != ORDER || key.params.curve.field.fieldSize != 256) fail()
        return key
    }

    /** Acepta SPKI en PEM o Base64 (con o sin saltos de línea). */
    fun decodeSpkiText(text: String): ByteArray {
        val body = text.lineSequence()
            .filterNot { it.trim().startsWith("-----") }
            .joinToString("")
            .filterNot { it.isWhitespace() }
        return B64.dec(body)
    }

    fun ecdh(priv: PrivateKey, pub: PublicKey): ByteArray = KeyAgreement.getInstance("ECDH").run {
        init(priv); doPhase(pub, true); generateSecret()
    }

    fun sign(priv: PrivateKey, data: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(priv); update(data); sign()
    }

    fun verify(pub: PublicKey, data: ByteArray, derSig: ByteArray): Boolean = try {
        Signature.getInstance("SHA256withECDSA").run { initVerify(pub); update(data); verify(derSig) }
    } catch (e: Exception) { false }

    /**
     * 0.22.0 (L-b): firma cruda r‖s (64 B, la de la licencia corta) → DER (la que entiende `Signature` en todas las
     * versiones de Android). Sin depender de `SHA256withECDSAinP1363Format`, que no existe en todos los proveedores.
     */
    fun rawToDer(raw: ByteArray): ByteArray {
        if (raw.size != 64) fail()
        fun entero(b: ByteArray): ByteArray {
            var i = 0
            while (i < b.size - 1 && b[i] == 0.toByte()) i++
            val t = b.copyOfRange(i, b.size)
            return if (t[0] < 0) byteArrayOf(0) + t else t
        }
        val r = entero(raw.copyOfRange(0, 32))
        val s = entero(raw.copyOfRange(32, 64))
        val seq = byteArrayOf(0x02, r.size.toByte()) + r + byteArrayOf(0x02, s.size.toByte()) + s
        return byteArrayOf(0x30, seq.size.toByte()) + seq
    }

    /** DER → r‖s (64 B). Lo usa el emisor (GL y el emisor simulado de los tests) para escribir la licencia corta. */
    fun derToRaw(der: ByteArray): ByteArray {
        fun leer(pos: Int): Pair<ByteArray, Int> {
            if (der[pos] != 0x02.toByte()) fail()
            val len = der[pos + 1].toInt()
            val v = der.copyOfRange(pos + 2, pos + 2 + len).dropWhile { it == 0.toByte() }.toByteArray()
            if (v.size > 32) fail()
            return (ByteArray(32 - v.size) + v) to pos + 2 + len
        }
        if (der.size < 8 || der[0] != 0x30.toByte()) fail()
        val (r, sig) = leer(2)
        val (s, _) = leer(sig)
        return r + s
    }

    private val P = BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16)
    private val B = BigInteger("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B", 16)
    private val PARAMS: ECParameterSpec by lazy {
        AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
    }

    /** Entero sin signo en exactamente 32 B big-endian. */
    fun fijo32(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        return when {
            b.size == 32 -> b
            b.size == 33 && b[0] == 0.toByte() -> b.copyOfRange(1, 33)
            b.size < 32 -> ByteArray(32 - b.size) + b
            else -> fail()
        }
    }

    /**
     * 0.23.0: punto comprimido SEC1 (33 B: `02|03` según la paridad de y, seguido de x). Lo usan la solicitud cifrada
     * (`devicePub`) y la licencia corta (`epk`) para ocupar menos que el SPKI DER (91 B).
     */
    fun comprimir(pub: ECPublicKey): ByteArray =
        byteArrayOf(if (pub.w.affineY.testBit(0)) 3 else 2) + fijo32(pub.w.affineX)

    fun comprimir(spkiDer: ByteArray): ByteArray = comprimir(publicKey(spkiDer))

    /** Inversa de [comprimir]: falla (genérico) si no es un punto de P-256. y = (x³ − 3x + b)^((p+1)/4) mod p. */
    fun descomprimir(b: ByteArray): ECPublicKey {
        if (b.size != 33 || (b[0] != 2.toByte() && b[0] != 3.toByte())) fail()
        val x = BigInteger(1, b.copyOfRange(1, 33))
        if (x >= P) fail()
        val rhs = x.pow(3).subtract(x.multiply(BigInteger.valueOf(3))).add(B).mod(P)
        var y = rhs.modPow(P.add(BigInteger.ONE).shiftRight(2), P)
        if (y.multiply(y).mod(P) != rhs) fail() // x no está en la curva
        if (y.testBit(0) != (b[0] == 3.toByte())) y = P.subtract(y)
        return try {
            KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), PARAMS)) as ECPublicKey
        } catch (e: Exception) { fail() }
    }

    /** SHA-256 hex (minúsculas) del SPKI DER. */
    fun fingerprint(spkiDer: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(spkiDer).aHex()
}

/** HKDF-SHA256 (RFC 5869). */
object Hkdf {
    fun sha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < length) {
            mac.update(t); mac.update(info); mac.update(counter.toByte())
            t = mac.doFinal()
            val n = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, n)
            pos += n; counter++
        }
        prk.fill(0)
        return out
    }
}

/** AES-256-GCM, IV 12 B, tag 16 B. La salida de Java es ct||tag. */
object AesGcm {
    fun encrypt(key: ByteArray, iv: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray =
        cipher(Cipher.ENCRYPT_MODE, key, iv, aad).doFinal(plain)

    fun decrypt(key: ByteArray, iv: ByteArray, aad: ByteArray, ctAndTag: ByteArray): ByteArray = try {
        cipher(Cipher.DECRYPT_MODE, key, iv, aad).doFinal(ctAndTag)
    } catch (e: Exception) { fail() }

    private fun cipher(mode: Int, key: ByteArray, iv: ByteArray, aad: ByteArray) =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GlContract.TAG_LEN * 8, iv))
            updateAAD(aad)
        }
}

/**
 * Par P-256 persistente del dispositivo. Su SPKI viaja en `devicePub`.
 * La privada nunca sale: solo se expone el acuerdo ECDH.
 */
interface DeviceKey {
    fun publicSpki(): ByteArray
    fun agree(peer: PublicKey): ByteArray
}
