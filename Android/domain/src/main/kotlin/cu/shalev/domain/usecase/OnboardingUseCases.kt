package cu.shalev.domain.usecase

import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.validation.Phone
import cu.shalev.core.validation.Validators
import cu.shalev.domain.model.DatosIniciales
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.model.PlanConfiguracion
import cu.shalev.domain.model.ResumenConfiguracion
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.repository.ConfiguracionInicialRepository
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.PerfilRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

/*
 * Casos de uso del asistente de primera ejecución y de los permisos contextuales.
 * Principio: nada de lo que hay aquí bloquea la app; todo se puede saltar y completar después en Ajustes.
 */

/** Perfil + licencia + progreso → pasos aplicables y pendientes. */
class ObservarResumenConfiguracion @Inject constructor(
    private val perfil: PerfilRepository,
    private val licencia: LicenciaRepository,
    private val conf: ConfiguracionInicialRepository,
) {
    operator fun invoke(): Flow<ResumenConfiguracion> =
        combine(perfil.perfil, licencia.snapshot, conf.estado) { p, l, c -> PlanConfiguracion.resumen(p, l, c) }
            .distinctUntilChanged()
}

/**
 * Guarda los datos del asistente en el Perfil (Room + SQLCipher). Los campos vacíos se guardan vacíos
 * (el asistente muestra lo que ya había, así que lo visible es lo que el usuario quiere). El teléfono se
 * antepone a la lista del Perfil si es nuevo; nunca se borran teléfonos existentes.
 * Todo vacío y Perfil vacío = no hay nada que guardar (no es un error: el paso es opcional).
 */
class GuardarDatosIniciales @Inject constructor(
    private val perfil: PerfilRepository,
    private val guardarPerfil: GuardarPerfil,
) {
    suspend operator fun invoke(d: DatosIniciales): AppResult<Unit> {
        validar(d)?.let { return AppResult.Err(it) }
        val actual = perfil.perfil.first()
        if (d.vacios && actual.vacio) return AppResult.Ok(Unit)
        val tel = d.telefono.takeIf { it.isNotBlank() }?.let(Phone::normalize)
        val telefonos = if (tel == null || actual.telefonos.any { it.numero == tel }) actual.telefonos
        else listOf(Telefono(numero = tel)) + actual.telefonos
        return guardarPerfil(actual.copy(nombre = d.nombre, apellidos = d.apellidos, ci = d.ci, telefonos = telefonos))
    }

    companion object {
        /** Primer error de formato, o null. Los campos vacíos son válidos. */
        fun validar(d: DatosIniciales): AppError.Validacion? = when {
            d.nombre.isNotBlank() && !Validators.nombre(d.nombre) -> AppError.Validacion("nombre")
            d.apellidos.isNotBlank() && !Validators.nombre(d.apellidos) -> AppError.Validacion("apellidos")
            d.ci.isNotBlank() && !Validators.ci(d.ci) -> AppError.Validacion("ci")
            d.telefono.isNotBlank() && Phone.normalize(d.telefono) == null -> AppError.Validacion("telefono")
            else -> null
        }
    }
}

/** Guarda los niveles de alerta (validados por [GuardarNiveles]) y marca el paso como hecho. */
class GuardarAlertasIniciales @Inject constructor(
    private val guardarNiveles: GuardarNiveles,
    private val conf: ConfiguracionInicialRepository,
) {
    suspend operator fun invoke(n: NivelesMinimos): AppResult<Unit> = when (val r = guardarNiveles(n)) {
        is AppResult.Ok -> { conf.confirmar(PasoConfiguracion.ALERTAS); r }
        is AppResult.Err -> r
    }
}


