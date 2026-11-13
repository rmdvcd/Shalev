package cu.shalev.data.local

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cu.shalev.data.security.KeystoreAead
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.shalevStore: DataStore<Preferences> by preferencesDataStore(name = "spvi_secure")

/**
 * Preferences DataStore donde cada valor va cifrado con [KeystoreAead] (AAD = nombre de la clave,
 * así un valor no puede moverse a otra clave). El archivo está excluido de backup (xml/backup_rules).
 */
@Singleton
class SecureDataStore @Inject constructor(
    @ApplicationContext context: Context,
    private val aead: KeystoreAead,
) {
    private val store = context.shalevStore

    fun observe(key: String): Flow<String?> = store.data.map { it[stringPreferencesKey(key)]?.let { v -> open(key, v) } }

    suspend fun get(key: String): String? = observe(key).first()

    suspend fun put(key: String, value: String?) {
        store.edit { prefs ->
            val k = stringPreferencesKey(key)
            if (value == null) prefs.remove(k) else prefs[k] = seal(key, value)
        }
    }

    private fun seal(key: String, value: String): String =
        Base64.encodeToString(aead.encrypt(value.toByteArray(Charsets.UTF_8), key.toByteArray()), Base64.NO_WRAP)

    /** Valor ilegible (Keystore borrado) = ausente. Para la licencia equivale a "sin licencia" → bloqueo seguro. */
    private fun open(key: String, value: String): String? = runCatching {
        String(aead.decrypt(Base64.decode(value, Base64.NO_WRAP), key.toByteArray()), Charsets.UTF_8)
    }.getOrNull()
}
