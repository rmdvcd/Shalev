package cu.shalev.domain.model

import cu.shalev.licencia.LicenseState

/**
 * 0.26.0 (P74): estado del periodo de prueba visto por la UI ([TrialViewModel] en :app).
 * - [FirstInstall]: primera instalación en este teléfono (no había ninguna copia del registro de la prueba).
 * - [Active]: prueba en curso ([diasRestantes]) o licencia válida ([diasRestantes] = null).
 * - [Expired]: prueba (o licencia) vencida → bloqueo (pantalla de Licencia).
 * - [Tampered]: fecha del teléfono atrasada (por `lastSeen` o por el reloj monótono) → bloqueo hasta corregirla.
 * Una copia del registro que no descifra NO es manipulación (P74: no bloquea): se ignora y se reescribe.
 */
sealed interface TrialState {
    data object FirstInstall : TrialState
    data class Active(val diasRestantes: Int?) : TrialState
    data object Expired : TrialState
    data object Tampered : TrialState
}

/** Lo último que leyó el registro externo de la prueba en este arranque de la app. */
data class InfoRegistroPrueba(
    /** No había copia interna al arrancar: app recién instalada (o con los datos borrados). Fijo durante el proceso. */
    val instalacionNueva: Boolean,
    /** Ninguna copia válida (ni interna ni externa): primera instalación en este teléfono. */
    val primeraInstalacion: Boolean,
    /** Copias que existían pero no descifraban (se ignoraron y se reescribieron). */
    val copiasDanadas: Int,
)

fun trialState(estado: LicenseState, info: InfoRegistroPrueba?): TrialState = when (estado) {
    LicenseState.ClockTampered -> TrialState.Tampered
    LicenseState.TrialExpired, is LicenseState.Expired, LicenseState.Revoked -> TrialState.Expired
    is LicenseState.Trial -> if (info?.primeraInstalacion == true) TrialState.FirstInstall else TrialState.Active(estado.daysLeft)
    is LicenseState.Active, is LicenseState.Perpetual -> TrialState.Active(null)
}

/**
 * Pedir el permiso de fotos (una vez por instalación) solo cuando sirve: app recién instalada, en prueba, sin el permiso
 * y sin haberlo pedido ya. Con él, Shalev puede leer la copia en Imágenes que dejó una instalación anterior.
 */
fun pedirPermisoRegistro(estado: LicenseState?, info: InfoRegistroPrueba?, concedido: Boolean, yaPedido: Boolean): Boolean =
    estado is LicenseState.Trial && info?.instalacionNueva == true && !concedido && !yaPedido
