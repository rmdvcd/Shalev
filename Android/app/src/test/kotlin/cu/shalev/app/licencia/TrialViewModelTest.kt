package cu.shalev.app.licencia

import cu.shalev.app.LicRepo
import cu.shalev.app.licencia.prueba.TrialViewModel
import cu.shalev.domain.model.InfoRegistroPrueba
import cu.shalev.domain.model.TrialState
import cu.shalev.domain.repository.PruebaRepository
import cu.shalev.licencia.LicenseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 0.26.0 (P74): TrialViewModel expone el estado de la prueba y pide el permiso de fotos una sola vez. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrialViewModelTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private class Prueba : PruebaRepository {
        override val info = MutableStateFlow<InfoRegistroPrueba?>(InfoRegistroPrueba(instalacionNueva = true, primeraInstalacion = true, copiasDanadas = 0))
        override val permisoLectura = "android.permission.READ_MEDIA_IMAGES"
        var concedido = false
        var pedido = false
        override fun lecturaPermitida() = concedido
        override suspend fun permisoPedido() = pedido
        override suspend fun marcarPermisoPedido() { pedido = true }
    }

    @Test fun primeraInstalacionPideElPermisoUnaVezYReleeAlResponder() = runTest {
        val lic = LicRepo(LicenseState.Trial(7)); val p = Prueba()
        val vm = TrialViewModel(lic, p)
        assertEquals(TrialState.FirstInstall, vm.state.value)
        assertTrue(vm.pedirPermiso.value)
        vm.alResponder()
        assertTrue(p.pedido)
        assertEquals(1, lic.refrescos)
        assertFalse(vm.pedirPermiso.value)
    }

    @Test fun reinstaladaConLaPruebaVencidaQuedaExpired() = runTest {
        val lic = LicRepo(LicenseState.TrialExpired); val p = Prueba()
        p.info.value = InfoRegistroPrueba(instalacionNueva = true, primeraInstalacion = false, copiasDanadas = 0)
        val vm = TrialViewModel(lic, p)
        assertEquals(TrialState.Expired, vm.state.value)
        assertFalse(vm.pedirPermiso.value) // vencida: la puerta de Licencia ya bloquea; no hace falta el permiso
    }

    @Test fun relojAtrasadoEsTampered() = runTest {
        val vm = TrialViewModel(LicRepo(LicenseState.ClockTampered), Prueba())
        assertEquals(TrialState.Tampered, vm.state.value)
    }

    @Test fun ahoraNoTampocoVuelveAPreguntar() = runTest {
        val p = Prueba(); val vm = TrialViewModel(LicRepo(LicenseState.Trial(7)), p)
        vm.descartar()
        assertTrue(p.pedido)
        assertFalse(vm.pedirPermiso.value)
    }
}
