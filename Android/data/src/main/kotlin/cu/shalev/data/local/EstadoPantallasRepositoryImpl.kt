package cu.shalev.data.local

import cu.shalev.domain.repository.EstadoPantallasRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [EstadoPantallasRepository] sobre el DataStore cifrado: una entrada por ajuste bajo el prefijo `pantalla.`
 * (p. ej. `pantalla.inicio.periodo`). Los valores son cortos y opacos; un valor ilegible lo descarta quien lee
 * (cada codificador de pantalla devuelve null y la pantalla arranca en su valor por defecto).
 */
@Singleton
class EstadoPantallasRepositoryImpl @Inject constructor(private val store: SecureDataStore) : EstadoPantallasRepository {

    override fun observar(clave: String): Flow<String?> = store.observe(PREFIJO + clave).distinctUntilChanged()

    override suspend fun guardar(clave: String, valor: String?) {
        store.put(PREFIJO + clave, valor)
    }

    private companion object {
        const val PREFIJO = "pantalla."
    }
}
