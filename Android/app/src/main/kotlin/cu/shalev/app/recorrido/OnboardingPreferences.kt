package cu.shalev.app.recorrido

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Qué recorridos ya vio el usuario. Interfaz para poder usar un doble en memoria en las pruebas. */
interface RecorridosVistos {
    /** true cuando el recorrido ya se terminó u omitió. Emite de nuevo si cambia. */
    fun visto(recorrido: Recorrido): Flow<Boolean>

    /** Marca el recorrido como visto. Solo se llama al pulsar «Finalizar» u «Omitir» (o Atrás). */
    suspend fun marcarVisto(recorrido: Recorrido)

    /** Vuelve a mostrar todos los recorridos la próxima vez que se abra cada módulo. */
    suspend fun reiniciar()
}

/**
 * Preferences DataStore propio (`shalev_recorridos`), sin cifrar: solo guarda booleanos de interfaz, nada del negocio.
 * Una clave por módulo: `caja_tour_shown`, `ventas_tour_shown`, `inventario_tour_shown`, `servicios_tour_shown` y
 * `registros_tour_shown`. Ausente = no visto.
 *
 * La copia en la nube y la transferencia entre teléfonos ya están desactivadas para toda la app
 * (`data_extraction_rules.xml`); al reinstalar, los recorridos vuelven a mostrarse una vez.
 */
@Singleton
class OnboardingPreferences internal constructor(private val store: DataStore<Preferences>) : RecorridosVistos {

    @Inject constructor(@ApplicationContext context: Context) : this(context.recorridosDataStore)

    override fun visto(recorrido: Recorrido): Flow<Boolean> = store.data
        // Un archivo ilegible no debe romper la pantalla: se trata como «no visto» (lo peor es ver el recorrido otra vez).
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[clave(recorrido)] ?: false }
        .distinctUntilChanged()

    override suspend fun marcarVisto(recorrido: Recorrido) {
        store.edit { it[clave(recorrido)] = true }
    }

    override suspend fun reiniciar() {
        store.edit { prefs -> Recorrido.entries.forEach { prefs.remove(clave(it)) } }
    }

    companion object {
        const val ARCHIVO = "shalev_recorridos"
        fun clave(recorrido: Recorrido): Preferences.Key<Boolean> = booleanPreferencesKey("${recorrido.clave}_tour_shown")
    }
}

private val Context.recorridosDataStore: DataStore<Preferences> by preferencesDataStore(
    name = OnboardingPreferences.ARCHIVO,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Module
@InstallIn(SingletonComponent::class)
abstract class RecorridosModule {
    @Binds abstract fun recorridosVistos(impl: OnboardingPreferences): RecorridosVistos
}
