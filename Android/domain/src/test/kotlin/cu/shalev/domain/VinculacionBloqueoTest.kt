package cu.shalev.domain

import cu.shalev.domain.model.Vinculacion
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.0.2: con la prueba vencida la app puede seguir usándose como secundaria de una principal con licencia activa.
 * Solo con ese estado: el resto del bloqueo se resuelve con GL y no se ofrece el vínculo.
 */
class VinculacionBloqueoTest {
    private val ahora = Instant.parse("2026-11-15T12:00:00Z")

    @Test fun laPruebaVencidaPermiteSeguirComoSecundaria() {
        assertTrue(Vinculacion.puedeSeguirComoSecundaria(LicenseState.TrialExpired))
    }

    @Test fun elRestoDelBloqueoNoOfreceElVinculo() {
        assertFalse(Vinculacion.puedeSeguirComoSecundaria(LicenseState.Revoked))
        assertFalse(Vinculacion.puedeSeguirComoSecundaria(LicenseState.ClockTampered))
        assertFalse(Vinculacion.puedeSeguirComoSecundaria(LicenseState.Expired(TipoLicencia.MENSUAL)))
    }

    @Test fun unaLicenciaValidaNoEsUnBloqueo() {
        assertFalse(Vinculacion.puedeSeguirComoSecundaria(LicenseState.Trial(daysLeft = 3)))
        assertFalse(Vinculacion.puedeSeguirComoSecundaria(LicenseState.Perpetual("x")))
        assertFalse(
            Vinculacion.puedeSeguirComoSecundaria(
                LicenseState.Active(TipoLicencia.MENSUAL, ahora.plusSeconds(86_400), "x"),
            ),
        )
    }
}
