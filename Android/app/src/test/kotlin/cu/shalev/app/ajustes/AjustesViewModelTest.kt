package cu.shalev.app.ajustes

import androidx.lifecycle.viewModelScope
import cu.shalev.app.ConfRepo
import cu.shalev.app.LicRepo
import cu.shalev.app.PerfilRepo
import cu.shalev.app.PrefRepo
import cu.shalev.domain.model.ConfiguracionInicial
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.PlanConfiguracion
import cu.shalev.domain.usecase.ObservarResumenConfiguracion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/** Ajustes: resumen de la configuración pendiente. */
@OptIn(ExperimentalCoroutinesApi::class)
class AjustesViewModelTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private val perfil = PerfilRepo()
    private val lic = LicRepo()
    private val conf = ConfRepo()
    private val prefs = PrefRepo()

    @Test fun resumenSeRefleja() = runTest {
        val vm = AjustesViewModel(ObservarResumenConfiguracion(perfil, lic, conf), prefs, cu.shalev.app.TipoRepo(), cu.shalev.app.PrincipalRepoFake(), cu.shalev.app.SecundariaRepoFake())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} } // WhileSubscribed
        val inicial = PlanConfiguracion.resumen(Perfil(), lic.flow.value, ConfiguracionInicial())
        assertEquals(inicial, vm.state.value.resumen)

        perfil.state.value = Perfil(nombre = "María", apellidos = "Pérez", ci = "85010112345")
        assertNotEquals(inicial, vm.state.value.resumen) // el Perfil cuenta en el plan
        vm.viewModelScope.cancel()
    }
}
