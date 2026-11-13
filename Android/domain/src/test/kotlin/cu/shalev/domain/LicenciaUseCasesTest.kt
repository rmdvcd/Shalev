package cu.shalev.domain

import cu.shalev.domain.usecase.ActivarLicencia
import cu.shalev.domain.usecase.ProgramaLicencia
import cu.shalev.domain.usecase.VigilarLicencia
import cu.shalev.licencia.ActivationResult
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LicenciaUseCasesTest {

    @Test fun `licencia activa se revisa justo tras vencer si es antes del techo`() {
        val vence = T0.plusSeconds(120)
        assertEquals(Duration.ofSeconds(121), ProgramaLicencia.proximaRevision(LicenseState.Active(TipoLicencia.MENSUAL, vence, "x"), T0))
    }

    @Test fun `revision acotada entre minimo y maximo`() {
        val lejos = LicenseState.Active(TipoLicencia.ANUAL, T0.plus(Duration.ofDays(200)), "x")
        assertEquals(ProgramaLicencia.MAXIMO, ProgramaLicencia.proximaRevision(lejos, T0))
        val yaVencida = LicenseState.Active(TipoLicencia.MENSUAL, T0.minusSeconds(60), "x")
        assertEquals(ProgramaLicencia.MINIMO, ProgramaLicencia.proximaRevision(yaVencida, T0))
        assertEquals(ProgramaLicencia.MAXIMO, ProgramaLicencia.proximaRevision(LicenseState.Trial(3), T0))
        assertEquals(ProgramaLicencia.MAXIMO, ProgramaLicencia.proximaRevision(LicenseState.TrialExpired, T0))
    }

    @Test fun `vigilar emite la evaluacion actual y publica el snapshot`() = runBlocking<Unit> {
        val repo = FakeLicencia(estado = LicenseState.TrialExpired)
        val primera = VigilarLicencia(repo, FixedClock()).invoke().first()
        assertFalse(primera.estado.unlocked)
        assertEquals(primera, repo.snapshot.value)
    }

    @Test fun `activar re-evalua el estado`() = runBlocking<Unit> {
        val repo = FakeLicencia(activacion = ActivationResult.Rejected)
        ActivarLicencia(repo)("mensaje")
        assertEquals(1, repo.refrescos)
    }
}
