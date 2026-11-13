package cu.shalev.app.root

import androidx.lifecycle.viewModelScope
import cu.shalev.app.LicRepo
import cu.shalev.app.PrefRepo
import cu.shalev.app.RelojFijo
import cu.shalev.app.common.Entrada
import cu.shalev.app.common.EntradaCompartida
import cu.shalev.app.common.EntradaViewModel
import cu.shalev.domain.usecase.ProgramaLicencia
import cu.shalev.domain.usecase.VigilarLicencia
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Puerta de la app con el ViewModel real (la transición pura está en RootStateTest).
 * La vigilancia periódica corre en tiempo virtual; al final se cancela el viewModelScope (bucle infinito).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private val lic = LicRepo()
    private val prefs = PrefRepo()
    private val reloj = RelojFijo()

    private fun vm() = RootViewModel(lic, prefs, VigilarLicencia(lic, reloj), cu.shalev.app.TipoRepo())

    @Test fun primeraVezOnboardingYLuegoMain() = runTest {
        val vm = vm()
        assertEquals(RootState.Onboarding, vm.state.value)
        prefs.state.value = prefs.state.value.copy(onboardingCompletado = true)
        assertTrue(vm.state.value is RootState.Main)
        vm.viewModelScope.cancel()
    }

    @Test fun licenciaVencidaBloqueaAunqueFalteElOnboarding() = runTest {
        lic.flow.value = lic.flow.value!!.copy(estado = LicenseState.TrialExpired)
        val vm = vm()
        assertTrue(vm.state.value is RootState.Bloqueo)
        vm.viewModelScope.cancel()
    }

    @Test fun venceConLaAppAbiertaYSeBloqueaSinReiniciar() = runTest {
        prefs.state.value = prefs.state.value.copy(onboardingCompletado = true)
        val vm = vm()
        assertTrue(vm.state.value is RootState.Main)
        lic.flow.value = lic.flow.value!!.copy(estado = LicenseState.Expired(TipoLicencia.MENSUAL))
        assertEquals(RootState.Bloqueo(lic.flow.value!!), vm.state.value)
        // Activar otra licencia desbloquea en caliente.
        lic.flow.value = lic.flow.value!!.copy(estado = LicenseState.Perpetual("lic-p"))
        assertTrue(vm.state.value is RootState.Main)
        vm.viewModelScope.cancel()
    }

    @Test fun vigilanciaPeriodicaYRefrescoAlVolverAPrimerPlano() = runTest {
        val vm = vm()
        runCurrent()
        val inicial = lic.refrescos
        assertTrue("la vigilancia evalúa al arrancar", inicial >= 1)
        advanceTimeBy(ProgramaLicencia.MAXIMO.toMillis() + 1)
        assertEquals(inicial + 1, lic.refrescos) // prueba: revisión cada 15 min como máximo
        vm.refrescar()
        assertEquals(inicial + 2, lic.refrescos)
        vm.viewModelScope.cancel()
    }
}

/** La licencia compartida queda pendiente hasta activarse (los .shalev ya no entran por aquí). */
@OptIn(ExperimentalCoroutinesApi::class)
class EntradaViewModelTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    @Test fun soloLaLicenciaQuedaPendienteHastaTomarse() = runTest {
        val entrada = EntradaCompartida()
        val vm = EntradaViewModel(entrada)
        assertFalse(vm.licenciaPendiente.value)
        entrada.publicar(Entrada.Texto("Nro. Transaccion: BR601ADLM8997"))
        assertFalse(vm.licenciaPendiente.value) // el SMS es para la venta, no una licencia
        entrada.publicar(Entrada.Texto("SPVI2:codigo-de-licencia"))
        assertTrue(vm.licenciaPendiente.value)
        assertEquals("SPVI2:codigo-de-licencia", vm.tomarLicencia())
        assertFalse(vm.licenciaPendiente.value)
        vm.viewModelScope.cancel()
    }
}
