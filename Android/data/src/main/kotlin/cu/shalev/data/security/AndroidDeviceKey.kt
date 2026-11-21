package cu.shalev.data.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import cu.shalev.licencia.crypto.DeviceKey
import cu.shalev.licencia.crypto.EcP256
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Par P-256 persistente del dispositivo (decisión D2.5). Su SPKI viaja en `devicePub`.
 *  - API ≥ 31: Keystore con PURPOSE_AGREE_KEY (StrongBox si lo admite; si no, TEE). La privada no sale.
 *  - API 26–30: Keystore no hace ECDH → clave por software envuelta con [KeystoreAead] en noBackupFilesDir.
 * Un equipo que se actualiza de 30 a 31 conserva su clave por software (no invalida la licencia).
 */
@Singleton
class AndroidDeviceKey @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aead: KeystoreAead,
) : DeviceKey {

    private val ks: KeyStore = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
    private val softFile = File(context.noBackupFilesDir, "spvi_device_key.bin")

    private var _impl: DeviceKey? = null
    private val impl: DeviceKey
        get() {
            if (_impl == null) {
                _impl = when {
                    softFile.exists() -> loadSoftware() ?: createSoftware()
                    ks.containsAlias(EC_ALIAS) -> KeystoreEc()
                    else -> createSoftware()
                }
            }
            return _impl!!
        }

    override fun publicSpki(): ByteArray = impl.publicSpki()
    override fun agree(peer: PublicKey): ByteArray = impl.agree(peer)
    override fun sign(data: ByteArray): ByteArray = impl.sign(data)

    override fun exportarClaves(): Pair<ByteArray, ByteArray>? = runCatching {
        if (!softFile.exists()) {
            impl
        }
        if (softFile.exists()) {
            val buf = ByteBuffer.wrap(softFile.readBytes())
            val pub = ByteArray(buf.int).also { buf.get(it) }
            val enc = ByteArray(buf.remaining()).also { buf.get(it) }
            val privBytes = aead.decrypt(enc, aad = pub)
            pub to privBytes
        } else null
    }.getOrNull()

    override fun restaurarClaves(pub: ByteArray, privPkcs8: ByteArray) {
        val enc = aead.encrypt(privPkcs8, aad = pub)
        softFile.writeBytes(ByteBuffer.allocate(4 + pub.size + enc.size).putInt(pub.size).put(pub).put(enc).array())
        val priv = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privPkcs8))
        _impl = Software(pub, priv)
        runCatching { if (ks.containsAlias(EC_ALIAS)) ks.deleteEntry(EC_ALIAS) }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createKeystoreEc() {
        // 0.32.0 (S7): la misma clave sirve para ECDH (licencia corta) y para firmar la prueba de posesión del canje.
        // Los teléfonos con una clave anterior (solo AGREE) conservan su identidad y su licencia; el canje directo les
        // pide volver a instalar la app (el Keystore no permite añadir propósitos a una clave ya creada).
        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(
            EC_ALIAS,
            KeyProperties.PURPOSE_AGREE_KEY or KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setIsStrongBoxBacked(strongBox)
            .build()
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KS)
        try {
            if (!StrongBox.available(context)) error("sin StrongBox")
            gen.initialize(spec(strongBox = true)); gen.generateKeyPair()
        } catch (e: Exception) {
            // StrongBox no disponible o sin soporte de ECDH: TEE.
            gen.initialize(spec(strongBox = false)); gen.generateKeyPair()
        }
    }

    private inner class KeystoreEc : DeviceKey {
        override fun publicSpki(): ByteArray = ks.getCertificate(EC_ALIAS).publicKey.encoded
        override fun agree(peer: PublicKey): ByteArray = KeyAgreement.getInstance("ECDH", ANDROID_KS).run {
            init(ks.getKey(EC_ALIAS, null) as PrivateKey)
            doPhase(peer, true)
            generateSecret()
        }
        override fun sign(data: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA", ANDROID_KS).run {
            initSign(ks.getKey(EC_ALIAS, null) as PrivateKey)
            update(data)
            sign()
        }
    }

    private class Software(private val pub: ByteArray, private val priv: PrivateKey) : DeviceKey {
        override fun publicSpki() = pub
        override fun agree(peer: PublicKey) = EcP256.ecdh(priv, peer)
        override fun sign(data: ByteArray) = EcP256.sign(priv, data)
    }

    /** Formato: [len pub:int][pub][AEAD(pkcs8)] con AAD = pub. */
    private fun createSoftware(): DeviceKey {
        val kp = EcP256.generate()
        val pub = kp.public.encoded
        val enc = aead.encrypt(kp.private.encoded, aad = pub)
        softFile.writeBytes(ByteBuffer.allocate(4 + pub.size + enc.size).putInt(pub.size).put(pub).put(enc).array())
        return Software(pub, kp.private)
    }

    private fun loadSoftware(): DeviceKey? = runCatching {
        val buf = ByteBuffer.wrap(softFile.readBytes())
        val pub = ByteArray(buf.int).also { buf.get(it) }
        val enc = ByteArray(buf.remaining()).also { buf.get(it) }
        val priv = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(aead.decrypt(enc, aad = pub)))
        Software(pub, priv)
    }.getOrElse {
        // Keystore borrado o archivo corrupto: la licencia anterior ya no es descifrable → clave nueva.
        softFile.delete(); null
    }

    private companion object {
        const val ANDROID_KS = "AndroidKeyStore"
        const val EC_ALIAS = "spvi_device_ecdh_v1"
    }
}
