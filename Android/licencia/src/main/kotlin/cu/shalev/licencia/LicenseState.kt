package cu.shalev.licencia

import cu.shalev.licencia.contract.GlContract
import cu.shalev.licencia.contract.TipoLicencia
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

sealed interface LicenseState {
    /** true = la app completa está accesible; false = solo el panel de Licencia. */
    val unlocked: Boolean

    data class Trial(val daysLeft: Int) : LicenseState { override val unlocked = true }
    data object TrialExpired : LicenseState { override val unlocked = false }
    data class Active(
        val tipo: TipoLicencia, val venceEn: Instant, val id: String, val secundarias: Int = GlContract.SECUNDARIAS_DEFECTO,
    ) : LicenseState { override val unlocked = true }
    data class Perpetual(val id: String, val secundarias: Int = GlContract.SECUNDARIAS_DEFECTO) : LicenseState { override val unlocked = true }
    data class Expired(val tipo: TipoLicencia) : LicenseState { override val unlocked = false }
    data object Revoked : LicenseState { override val unlocked = false }
    /** El reloj del sistema retrocedió respecto a la última hora vista. */
    data object ClockTampered : LicenseState { override val unlocked = false }
}

/**
 * 0.21.0 (C4): apps secundarias que cubre este estado. La prueba y las licencias sin el campo, [GlContract.SECUNDARIAS_DEFECTO].
 * Con la app bloqueada no importa (no se vende ni se vincula), pero se devuelve el mismo valor para no quitar vínculos.
 */
val LicenseState.secundariasPermitidas: Int get() = when (this) {
    is LicenseState.Active -> secundarias
    is LicenseState.Perpetual -> secundarias
    else -> GlContract.SECUNDARIAS_DEFECTO
}

/** Días restantes redondeados hacia arriba (quedan 2 h → "1 día"). */
internal fun daysUntil(now: Instant, end: Instant): Long {
    val hours = Duration.between(now, end).toHours()
    return if (hours <= 0) 0 else (hours + 23) / 24
}

private fun dias(n: Long) = if (n == 1L) "1 día" else "$n días"
private fun meses(n: Long) = if (n == 1L) "1 mes" else "$n meses"
private fun restantes(n: Long) = if (n == 1L) "restante" else "restantes"

/**
 * Texto del banner de Inicio (Shalev.txt):
 * prueba → días; Mensual → días; Semestral/Anual → meses (días en el último mes); Perpetua → sin banner.
 */
fun LicenseState.bannerText(now: Instant, zone: ZoneId = ZoneId.systemDefault()): String? = when (this) {
    is LicenseState.Trial -> "Periodo de prueba restante: ${dias(daysLeft.toLong())}"
    is LicenseState.Active -> {
        val d = daysUntil(now, venceEn)
        val m = ChronoUnit.MONTHS.between(now.atZone(zone), venceEn.atZone(zone))
        when {
            tipo == TipoLicencia.MENSUAL || m < 1 -> "Licencia ${tipo.etiqueta.lowercase()}: ${dias(d)} ${restantes(d)}"
            else -> "Licencia ${tipo.etiqueta.lowercase()}: ${meses(m)} ${restantes(m)}"
        }
    }
    else -> null
}
