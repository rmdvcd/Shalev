package cu.shalev.data.licencia

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import cu.shalev.data.security.KeystoreAead
import cu.shalev.licencia.crypto.EcP256
import cu.shalev.licencia.secondary.FirmanteLicencia
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 0.31.0 (mitigación 7, Opción A): clave de firma de ESTA instalación, la que avala los sub-tokens que autorizan a las
 * apps secundarias.
 *
 * Es una clave P-256 **de firma**, distinta de la clave ECDH del dispositivo ([AndroidDeviceKey]): aquella se usa para
 * descifrar licencias y rotarla invalidaría cualquier licencia emitida. El alias es propio y nunca se borra al migrar
 * ni al desvincular (si se pierde, las secundarias dejan de poder verificar sus permisos y hay que volver a vincular).
 *
 *  - Preferente: Keystore `PURPOSE_SIGN` (la privada no sale del TEE; firma DER `SHA256withECDSA`, igual que GL).
 *  - Si el Keystore no puede (equipo sin soporte): clave por software envuelta con [KeystoreAead] en `noBackupFilesDir`,
 *    el mismo respaldo que usa la clave ECDH en API 26–30.
 */
@Singleton
class FirmanteLicenciaAndroid @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aead: KeystoreAead,
) : FirmanteLicencia {

    private val ks: KeyStore = KeyStore.getInstance(ANDROID_KS).apply { load(null) }
    private val softFile = File(context.noBackupFilesDir, ARCHIVO_SOFTWARE)

    private val impl: FirmanteLicencia by lazy {
        when {
            softFile.exists() -> loadSoftware() ?: createSoftware()
            ks.containsAlias(ALIAS) -> KeystoreFirma()
            else -> runCatching { createKeystore(); KeystoreFirma() }.getOrElse { createSoftware() }
        }
    }

    override fun publicSpki(): ByteArray = impl.publicSpki()

    override fun firmar(datos: ByteArray): ByteArray = impl.firmar(datos)

    private fun createKeystore() {
        val generador = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KS)
        generador.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec(CURVA))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build(),
        )
        generador.generateKeyPair()
    }

    /** Firma con la clave del Keystore: el proveedor tiene que ser el del Keystore (la privada no es exportable). */
    private inner class KeystoreFirma : FirmanteLicencia {
        override fun publicSpki(): ByteArray = ks.getCertificate(ALIAS).publicKey.encoded

        override fun firmar(datos: ByteArray): ByteArray = Signature.getInstance(FIRMA, ANDROID_KS).run {
            initSign(ks.getKey(ALIAS, null) as PrivateKey)
            update(datos)
            sign()
        }
    }

    private class Software(private val pub: ByteArray, private val priv: PrivateKey) : FirmanteLicencia {
        override fun publicSpki(): ByteArray = pub

        override fun firmar(datos: ByteArray): ByteArray = EcP256.sign(priv, datos)
    }

    /** Formato: [len pub:int][pub][AEAD(pkcs8)] con AAD = pub (el mismo que la clave ECDH por software). */
    private fun createSoftware(): FirmanteLicencia {
        val par = EcP256.generate()
        val pub = par.public.encoded
        val cifrado = aead.encrypt(par.private.encoded, aad = pub)
        softFile.writeBytes(ByteBuffer.allocate(4 + pub.size + cifrado.size).putInt(pub.size).put(pub).put(cifrado).array())
        return Software(pub, par.private)
    }

    private fun loadSoftware(): FirmanteLicencia? = runCatching {
        val buffer = ByteBuffer.wrap(softFile.readBytes())
        val pub = ByteArray(buffer.int).also { buffer.get(it) }
        val cifrado = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val priv = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(aead.decrypt(cifrado, aad = pub)))
        Software(pub, priv)
    }.getOrElse {
        // Archivo corrupto o Keystore borrado: se emite una clave nueva (las secundarias tendrán que volver a vincularse).
        softFile.delete(); null
    }

    private companion object {
        const val ANDROID_KS = "AndroidKeyStore"
        const val ALIAS = "spvi_licencia_firma_v1"
        const val CURVA = "secp256r1"
        const val FIRMA = "SHA256withECDSA"
        const val ARCHIVO_SOFTWARE = "spvi_licencia_firma.bin"
    }
}
