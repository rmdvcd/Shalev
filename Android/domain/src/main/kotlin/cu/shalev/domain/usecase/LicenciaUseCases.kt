package cu.shalev.domain.usecase

import cu.shalev.core.result.AppResult
import cu.shalev.core.result.onOk
import cu.shalev.core.validation.Phone
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.PerfilRepository
import cu.shalev.licencia.ActivationResult
import cu.shalev.licencia.SolicitudInput
import javax.inject.Inject
import cu.shalev.core.time.Clock
import cu.shalev.licencia.LicenseState
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * Construye la solicitud cifrada y sincroniza el Perfil con lo introducido en el panel
 * (Shalev.txt: "se actualizan automáticamente en Perfil"). El teléfono se antepone si no existía.
 */
class SolicitarLicencia @Inject constructor(
    private val licencia: LicenciaRepository,
    private val perfil: PerfilRepository,
) {
    suspend operator fun invoke(input: SolicitudInput): AppResult<cu.shalev.licencia.SolicitudGenerada> =
        licencia.construirSolicitud(input).onOk {
            val actual = perfil.perfil.first()
            val tel = Phone.normalize(input.telefono)
            perfil.guardar(
                actual.copy(
                    nombre = input.nombre.trim(),
                    apellidos = input.apellidos.trim(),
                    ci = input.ci.trim().uppercase(),
                    telefonos = if (tel == null || actual.telefonos.any { it.numero == tel }) actual.telefonos
                    else listOf(Telefono(numero = tel)) + actual.telefonos,
                ),
            )
        }
}

class ActivarLicencia @Inject constructor(private val repo: LicenciaRepository) {
    suspend operator fun invoke(mensaje: String): ActivationResult = repo.activar(mensaje).also { repo.refrescar() }
}

/**
 * Re-evalúa la licencia mientras la app está abierta, para que un vencimiento durante el uso bloquee
 * sin esperar al siguiente arranque. Cada evaluación re-verifica el envelope (firma + GCM): no hay
 * estado "cacheado" que se pueda congelar. Se recoge desde el ViewModel raíz (se cancela con él).
 */
class VigilarLicencia @Inject constructor(private val repo: LicenciaRepository, private val clock: Clock) {
    operator fun invoke(): Flow<Licencia> = flow {
        while (true) {
            val l = repo.refrescar()
            emit(l)
            delay(ProgramaLicencia.proximaRevision(l.estado, clock.now()).toMillis())
        }
    }
}

object ProgramaLicencia {
    /** Techo: cambios de día de la prueba y reloj movido se detectan en ≤ 15 min. */
    val MAXIMO: Duration = Duration.ofMinutes(15)
    val MINIMO: Duration = Duration.ofSeconds(5)

    /** Una licencia activa se revisa justo después de su vencimiento (+1 s) si ocurre antes del techo. */
    fun proximaRevision(estado: LicenseState, ahora: java.time.Instant): Duration = when (estado) {
        is LicenseState.Active -> Duration.between(ahora, estado.venceEn).plusSeconds(1).coerceIn(MINIMO, MAXIMO)
        else -> MAXIMO
    }
}
