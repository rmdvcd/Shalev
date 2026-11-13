package cu.shalev.data.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256-GCM con clave en Android Keystore (StrongBox si el dispositivo lo tiene; si no, TEE).
 * Formato: iv(12) || ct||tag. Sustituye a EncryptedSharedPreferences (obsoleto) para cifrar
 * valores de DataStore y envolver secretos (passphrase de SQLCipher, clave de software del dispositivo).
 */
@Singleton
class KeystoreAead @Inject constructor(@ApplicationContext private val context: Context) {

    private val ks: KeyStore = KeyStore.getInstance(ANDROID_KS).apply { load(null) }

    fun encrypt(plain: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        aad?.let(cipher::updateAAD)
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(blob: ByteArray, aad: ByteArray? = null): ByteArray {
        require(blob.size > IV_LEN) { "blob inválido" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_LEN))
        }
        aad?.let(cipher::updateAAD)
        return cipher.doFinal(blob, IV_LEN, blob.size - IV_LEN)
    }

    @Synchronized
    private fun key(): SecretKey {
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return generate(strongBox = StrongBox.available(context)) ?: generate(strongBox = false)!!
    }

    private fun generate(strongBox: Boolean): SecretKey? = try {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KS).apply { init(spec) }.generateKey()
    } catch (e: Exception) {
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && e is StrongBoxUnavailableException) null
        else if (strongBox) null
        else throw e
    }

    private companion object {
        const val ANDROID_KS = "AndroidKeyStore"
        const val ALIAS = "spvi_aead_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}

object StrongBox {
    fun available(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
}
