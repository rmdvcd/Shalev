package cu.shalev.app.registros

import cu.shalev.app.ClientesFijosRepo
import cu.shalev.app.venta.CampoCliente
import cu.shalev.domain.model.ClienteFijo
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 0.31.0: corrección de los datos de un cliente fijo desde su ficha (Registros → Clientes): nombre, carné y teléfono,
 * con las validaciones de la venta, sin admitir el carné de otro cliente y sin perder la ficha al cambiarlo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientesFijosViewModelTest {
    private val maria = cliente("María Pérez", "85010112345", "+5351234567")
    private val jose = cliente("José Gómez", "90020212345", "+5359876543")

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private fun cliente(nombre: String, ci: String, telefono: String) = ClienteFijo(
        nombreApellidos = nombre, ci = ci, telefono = telefono, creadoEn = Instant.EPOCH, actualizadoEn = Instant.EPOCH,
    )

    private fun TestScope.vm(inicial: List<ClienteFijo> = listOf(maria)): Pair<ClientesFijosViewModel, ClientesFijosRepo> {
        val repo = ClientesFijosRepo(inicial)
        val vm = ClientesFijosViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm to repo
    }

    @Test fun laCorreccionLlegaConLosDatosYLosGuarda() = runTest {
        val (vm, repo) = vm()
        vm.abrir(maria.ci)
        vm.editar(maria)
        assertEquals("María Pérez", vm.state.value.edicion?.nombre)
        assertEquals(maria.ci, vm.state.value.edicion?.ci)

        vm.campoEdicion(CampoCliente.NOMBRE, "María Pérez López")
        vm.campoEdicion(CampoCliente.TELEFONO, "51234568")
        vm.guardarEdicion()

        assertNull(vm.state.value.edicion)
        assertEquals(maria.ci, vm.state.value.fichaCi) // la ficha sigue abierta con los datos nuevos
        assertEquals("María Pérez López", repo.estado.value.single().nombreApellidos)
        assertEquals("51234568", repo.estado.value.single().telefono)
    }

    @Test fun unCarneInvalidoNoGuardaYMuestraElErrorDelCampo() = runTest {
        val (vm, repo) = vm()
        vm.abrir(maria.ci)
        vm.editar(maria)
        vm.campoEdicion(CampoCliente.CI, "850")
        vm.guardarEdicion()

        val f = vm.state.value.edicion
        assertNotNull(f)
        assertTrue(f!!.intento)
        assertTrue(f.visibles().containsKey(CampoCliente.CI))
        assertEquals(maria.ci, repo.estado.value.single().ci)
    }

    @Test fun elCarneDeOtroClienteNoSeAcepta() = runTest {
        val (vm, repo) = vm(listOf(maria, jose))
        vm.abrir(maria.ci)
        vm.editar(maria)
        vm.campoEdicion(CampoCliente.CI, jose.ci)
        vm.guardarEdicion()

        assertEquals(TextosClientes.CARNE_OCUPADO, vm.state.value.errorEdicion)
        assertNotNull(vm.state.value.edicion) // sigue abierta para corregir el carné
        assertEquals(setOf(maria.ci, jose.ci), repo.estado.value.map { it.ci }.toSet())
    }

    @Test fun alCambiarElCarneLaFichaSigueAbiertaConElNuevo() = runTest {
        val (vm, repo) = vm()
        vm.abrir(maria.ci)
        vm.editar(maria)
        vm.campoEdicion(CampoCliente.CI, "85010199999")
        vm.guardarEdicion()

        assertEquals("85010199999", vm.state.value.fichaCi)
        assertNull(vm.state.value.edicion)
        assertEquals("85010199999", repo.estado.value.single().ci)
    }

    @Test fun cancelarNoCambiaNada() = runTest {
        val (vm, repo) = vm()
        vm.abrir(maria.ci)
        vm.editar(maria)
        vm.campoEdicion(CampoCliente.NOMBRE, "Otra Persona")
        vm.cancelarEdicion()

        assertNull(vm.state.value.edicion)
        assertEquals("María Pérez", repo.estado.value.single().nombreApellidos)
    }
}
