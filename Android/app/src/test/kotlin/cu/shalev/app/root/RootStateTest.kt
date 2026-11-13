package cu.shalev.app.root

import cu.shalev.domain.model.Licencia
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RootStateTest {
    private fun lic(e: LicenseState) = Licencia(e, "SPVI:x", true, "ab")
    private val activa = lic(LicenseState.Active(TipoLicencia.MENSUAL, Instant.parse("2026-10-30T00:00:00Z"), "id"))
    private val vencida = lic(LicenseState.Expired(TipoLicencia.MENSUAL))

    @Test fun bloqueoSeEvaluaAntesDelOnboarding() {
        assertTrue(siguienteEstado(RootState.Loading, vencida, onboarded = false) is RootState.Bloqueo)
        assertEquals(RootState.Onboarding, siguienteEstado(RootState.Loading, activa, onboarded = false))
    }

    @Test fun bloqueoAlArrancarYAlVencerEnUsoEsElMismoEstado() {
        // En ambos casos BloqueoGate abre directamente el panel de Licencia.
        assertEquals(RootState.Bloqueo(vencida), siguienteEstado(RootState.Loading, vencida, true))
        assertEquals(RootState.Bloqueo(vencida), siguienteEstado(RootState.Main(activa), vencida, true))
        assertEquals(RootState.Bloqueo(vencida), siguienteEstado(RootState.Bloqueo(vencida), vencida, true))
    }

    @Test fun activarDesbloquea() {
        assertTrue(siguienteEstado(RootState.Bloqueo(vencida), activa, true) is RootState.Main)
    }

    @Test fun sinSnapshotEsCarga() {
        assertEquals(RootState.Loading, siguienteEstado(RootState.Main(activa), null, true))
    }

    @Test fun flujoCompletoMainABloqueo() = runBlocking<Unit> {
        val licencias = MutableStateFlow<Licencia?>(activa)
        val estados = mutableListOf<RootState>()
        val job = launch(Dispatchers.Unconfined) {
            estadosRaiz(licencias, flowOf(true)).collect { estados += it }
        }
        licencias.value = vencida
        job.cancel()
        assertTrue(estados.first() is RootState.Main)
        assertEquals(RootState.Bloqueo(vencida), estados.last())
    }

    // ---- P37: app secundaria
    @Test fun secundariaUsaLaLicenciaDeLaPrincipalYNoTieneOnboarding() {
        val trial = LicenseState.Trial(4)
        // La licencia propia (vencida) no cuenta; la de la principal sí.
        val s = siguienteEstado(RootState.Loading, vencida, onboarded = false, licPrincipal = trial)
        assertEquals(RootState.Main(vencida.copy(estado = trial)), s)
        assertTrue(siguienteEstado(RootState.Loading, activa, true, LicenseState.Revoked) is RootState.BloqueoSecundaria)
    }
}
