package cu.shalev.data.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passphrase de SQLCipher: 32 bytes aleatorios generados una vez, guardados envueltos con [KeystoreAead]
 * en noBackupFilesDir. La BD queda atada a este Keystore; por eso los respaldos se re-cifran con
 * contraseña del usuario (BackupCipher en :data).
 */
@Singleton
class DatabasePassphrase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aead: KeystoreAead,
) {
    private val file = File(context.noBackupFilesDir, "spvi_db_key.bin")

    /**
     * Devuelve una copia nueva en cada llamada. SupportOpenHelperFactory la conserva para reabrir la BD
     * (por eso no se limpia); vive en memoria del proceso igual que la propia conexión abierta.
     */
    @Synchronized
    fun get(): ByteArray {
        if (file.exists()) return aead.decrypt(file.readBytes(), AAD)
        val secret = ByteArray(32).also(SecureRandom()::nextBytes)
        file.writeBytes(aead.encrypt(secret, AAD))
        return secret
    }

    private companion object {
        val AAD = "spvi-db-v1".toByteArray()
    }
}
