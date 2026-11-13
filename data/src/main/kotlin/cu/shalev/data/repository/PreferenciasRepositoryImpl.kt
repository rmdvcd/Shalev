package cu.shalev.data.repository

import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.ConfiguracionNegocioEntity
import androidx.room.withTransaction
import cu.shalev.data.dto.PreferenciasDto
import cu.shalev.data.dto.toDomain
import cu.shalev.data.dto.toDto
import cu.shalev.data.local.SecureDataStore
import cu.shalev.data.local.ShalevJson
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.Preferencias
import cu.shalev.domain.repository.PreferenciasRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import cu.shalev.domain.model.Modulo

/**
 * Preferencias en el DataStore cifrado (AES-GCM con clave del Keystore, ver [SecureDataStore]):
 * un único JSON versionable en "pref.v1" (escritura atómica de todo el objeto) + el flag de onboarding.
 * Sustituye a EncryptedSharedPreferences (deprecada en security-crypto 1.1.0-alpha07).
 */
@Singleton
class PreferenciasRepositoryImpl @Inject constructor(private val ds: SecureDataStore, private val db: ShalevDatabase) : PreferenciasRepository {

    private val mutex = Mutex()

    private val dto: Flow<PreferenciasDto> = combine(ds.observe(K_PREF), db.gestionDao().observarConfiguracion()) { antiguo, fila ->
        (fila?.preferenciasJson ?: antiguo)?.let {
            ShalevJson.decodeFromString(PreferenciasDto.serializer(), it)
        } ?: PreferenciasDto()
    }

    override val onboardingCompletado: Flow<Boolean> = ds.observe(K_ONBOARDING).map { it == "1" }.distinctUntilChanged()

    override val preferencias: Flow<Preferencias> =
        combine(dto, onboardingCompletado) { d, ob -> d.toDomain(ob) }.distinctUntilChanged()

    override suspend fun completarOnboarding() = ds.put(K_ONBOARDING, "1")

    override suspend fun guardarNiveles(n: NivelesMinimos) = editar { it.copy(niveles = n) }
    override suspend fun guardarModulos(m: Set<Modulo>) = editar { it.copy(modulos = Modulo.normalizar(m)) }
    override suspend fun guardarEmpleadosPrevistos(n: Int) =
        editar { it.copy(empleadosPrevistos = n.coerceIn(0, cu.shalev.licencia.contract.GlContract.SECUNDARIAS_MAX)) }

    /** El flag de onboarding NO se toca: restaurar un respaldo no debe volver a mostrar la bienvenida. */
    override suspend fun reemplazar(p: Preferencias) = editar { p }

    private suspend fun editar(f: (Preferencias) -> Preferencias) = mutex.withLock {
        val actual = dto.first().toDomain()
        db.withTransaction {
            db.gestionDao().guardarConfiguracion(ConfiguracionNegocioEntity(preferenciasJson = ShalevJson.encodeToString(PreferenciasDto.serializer(), f(actual).toDto())))
        }
    }

    private companion object {
        const val K_PREF = "pref.v1"
        const val K_ONBOARDING = "pref.onboarding_done"
    }
}
