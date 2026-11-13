package cu.shalev.data.repository

import androidx.room.withTransaction
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.ConfiguracionNegocioEntity
import cu.shalev.data.dto.PreferenciasDto
import cu.shalev.data.dto.toDomain
import cu.shalev.data.dto.toDto
import cu.shalev.data.local.SecureDataStore
import cu.shalev.data.local.ShalevJson
import cu.shalev.domain.model.Modulo
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.Preferencias
import cu.shalev.domain.repository.PreferenciasRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Preferencias del negocio en Room/SQLCipher: importación y cambios comparten la transacción de negocio.
 * La primera lectura migra el JSON del DataStore cifrado. Solo el onboarding sigue siendo propio del teléfono.
 */
@Singleton
class PreferenciasRepositoryImpl @Inject constructor(private val ds: SecureDataStore, private val db: ShalevDatabase) : PreferenciasRepository {
    private val dto: Flow<PreferenciasDto> = flow {
        migrar()
        emitAll(db.gestionDao().observarConfiguracion().map { fila ->
            fila?.preferenciasJson?.let { ShalevJson.decodeFromString(PreferenciasDto.serializer(), it) } ?: PreferenciasDto()
        })
    }

    override val onboardingCompletado: Flow<Boolean> = ds.observe(K_ONBOARDING).map { it == "1" }.distinctUntilChanged()
    override val preferencias: Flow<Preferencias> = combine(dto, onboardingCompletado) { d, ob -> d.toDomain(ob) }.distinctUntilChanged()
    override suspend fun completarOnboarding() = ds.put(K_ONBOARDING, "1")
    override suspend fun guardarNiveles(n: NivelesMinimos) = editar { it.copy(niveles = n) }
    override suspend fun guardarModulos(m: Set<Modulo>) = editar { it.copy(modulos = Modulo.normalizar(m)) }
    override suspend fun guardarEmpleadosPrevistos(n: Int) = editar { it.copy(empleadosPrevistos = n.coerceIn(0, cu.shalev.licencia.contract.GlContract.SECUNDARIAS_MAX)) }

    /** El flag de onboarding NO se toca al restaurar un respaldo. */
    override suspend fun reemplazar(p: Preferencias) = editar { p }

    private suspend fun migrar() {
        if (db.gestionDao().configuracion() != null) return
        val antiguo = ds.get(K_PREF)
        // Leer fuera y volver a comprobar DENTRO evita sobrescribir una importación concurrente.
        val valor = antiguo?.let { ShalevJson.decodeFromString(PreferenciasDto.serializer(), it) } ?: PreferenciasDto()
        db.withTransaction {
            if (db.gestionDao().configuracion() == null) db.gestionDao().guardarConfiguracion(valor.fila())
        }
        // Room ya es el origen de verdad; una caída aquí solo deja un legado que nunca se vuelve a priorizar.
        ds.put(K_PREF, null)
    }

    private suspend fun editar(f: (Preferencias) -> Preferencias) {
        migrar()
        db.withTransaction {
            val actual = db.gestionDao().configuracion()?.preferenciasJson
                ?.let { ShalevJson.decodeFromString(PreferenciasDto.serializer(), it).toDomain() } ?: Preferencias()
            db.gestionDao().guardarConfiguracion(f(actual).toDto().fila())
        }
    }
    private fun PreferenciasDto.fila() = ConfiguracionNegocioEntity(preferenciasJson = ShalevJson.encodeToString(PreferenciasDto.serializer(), this))

    private companion object {
        const val K_PREF = "pref.v1"
        const val K_ONBOARDING = "pref.onboarding_done"
    }
}
