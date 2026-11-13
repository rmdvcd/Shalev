package cu.shalev.app.acceso

import cu.shalev.app.AjustesDispRepo
import cu.shalev.domain.model.AjustesDispositivo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 0.27.0 (T11): acceso con huella/cara o PIN del teléfono. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccesoClaveTest {
    private val min = 60_000L

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    @Test fun reglaDeTiempo() {
        assertFalse(ReglaAcceso.debePedir(activo = false, arranqueEnFrio = true, enFondoDesdeMs = null, ahoraMs = 0))  // desactivado → nunca
        assertTrue(ReglaAcceso.debePedir(activo = true, arranqueEnFrio = true, enFondoDesdeMs = null, ahoraMs = 0))    // arranque en frío
        assertFalse(ReglaAcceso.debePedir(true, false, enFondoDesdeMs = 1_000, ahoraMs = 1_000 + 9 * min))             // < 10 min
        assertTrue(ReglaAcceso.debePedir(true, false, enFondoDesdeMs = 1_000, ahoraMs = 1_000 + 10 * min))             // ≥ 10 min
        assertTrue(ReglaAcceso.debePedir(true, false, enFondoDesdeMs = 50_000, ahoraMs = 10))                          // reloj hacia atrás
        assertFalse(ReglaAcceso.debePedir(true, false, enFondoDesdeMs = null, ahoraMs = 5))                            // nunca fue al fondo
    }

    @Test fun guardaDelProceso() {
        val g = GuardiaAcceso()
        assertEquals(EstadoCandado.PEDIR, g.estado.value) // arranque en frío
        g.desbloquear()
        g.alIrAFondo(1_000); g.alVolver(1_000 + 3 * min)  // compartir un PDF y volver
        assertEquals(EstadoCandado.LIBRE, g.estado.value)
        g.alIrAFondo(10_000); g.alVolver(10_000 + 11 * min)
        assertEquals(EstadoCandado.PEDIR, g.estado.value)
    }

    @Test fun viewModelSoloBloqueaConElAccesoActivado() {
        val ajustes = AjustesDispRepo()
        val guardia = GuardiaAcceso()
        val vm = AccesoViewModel(guardia, ajustes, GuardarAccesoClave(guardia, ajustes))
        assertEquals(false, vm.bloqueada.value)           // desactivado: la app se abre sin pedir nada
        vm.guardar(true)                                   // activarlo no bloquea en ese momento
        assertTrue(ajustes.estado.value.accesoConClave)
        assertEquals(false, vm.bloqueada.value)
        guardia.alIrAFondo(0); guardia.alVolver(10 * min)
        assertEquals(true, vm.bloqueada.value)
        vm.desbloqueada()
        assertEquals(false, vm.bloqueada.value)
        ajustes.estado.value = AjustesDispositivo(accesoConClave = true)
        vm.sinBloqueoDelTelefono()                         // el teléfono ya no tiene bloqueo: se apaga sola
        assertFalse(ajustes.estado.value.accesoConClave)
    }
}
