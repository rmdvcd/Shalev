package cu.shalev.domain.usecase

import cu.shalev.core.time.Clock
import cu.shalev.domain.model.ActualizacionObligatoria
import cu.shalev.domain.model.Actualizaciones
import cu.shalev.domain.model.ApkEnPrincipal
import cu.shalev.domain.model.EstadoApp
import cu.shalev.domain.repository.EstadoAppRepository
import javax.inject.Inject

/**
 * 0.26.0 (P73 §6): actualizaciones obligatorias, aplazables [ActualizacionObligatoria.DIAS] días.
 *
 * - Principal: cuenta si GitHub ofrece una versión más nueva **instalable** (APK + huella SHA-256). Una Release sin APK
 *   solo avisa: no se puede bloquear una app que no tiene cómo actualizarse.
 * - Secundaria: cuenta si su principal tiene un APK más nuevo. Si aún no se ha sincronizado en este arranque
 *   ([apkPrincipal] desconocido), se mantiene lo ya visto con la misma versión instalada.
 * - Sin GitHub configurado (principal) nunca hay nada pendiente → nunca hay bloqueo.
 */
class EstadoActualizacionObligatoria @Inject constructor(
    private val estadoApp: EstadoAppRepository,
    private val clock: Clock,
) {
    /** ¿Hay una versión nueva pendiente? Pura: la usan [registrar] y la pantalla. */
    fun hayNueva(
        e: EstadoApp,
        instalada: String,
        esSecundaria: Boolean,
        repoConfigurado: Boolean,
        apkPrincipal: ApkEnPrincipal?,
        versionCodeInstalado: Int,
    ): Boolean = if (esSecundaria) {
        apkPrincipal?.let { it.versionCode > versionCodeInstalado }
            ?: (e.obligatoriaDesde != null && e.obligatoriaPara == instalada)
    } else {
        repoConfigurado && Actualizaciones.aviso(e, instalada)?.let { it.apkUrl != null && it.sha256 != null } == true
    }

    fun estado(e: EstadoApp, instalada: String, hayNueva: Boolean): ActualizacionObligatoria.Estado =
        ActualizacionObligatoria.estado(clock.now(), e, instalada, hayNueva)

    /** Guarda (o borra) la fecha de la primera detección. No escribe si nada cambia. */
    suspend fun registrar(instalada: String, hayNueva: Boolean) {
        val ahora = clock.now()
        if (ActualizacionObligatoria.necesitaRegistro(estadoApp.actual(), instalada, hayNueva)) {
            estadoApp.editar { ActualizacionObligatoria.registrar(ahora, it, instalada, hayNueva) }
        }
    }

    /** «Más tarde» en Inicio: oculta el aviso hasta mañana. El plazo NO se mueve. */
    suspend fun aplazar() {
        val hasta = clock.now().plus(ActualizacionObligatoria.APLAZAR)
        estadoApp.editar { it.copy(aplazadaHasta = hasta) }
    }
}
