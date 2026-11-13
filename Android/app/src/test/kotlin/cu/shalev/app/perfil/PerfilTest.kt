package cu.shalev.app.perfil

import cu.shalev.app.PerfilRepo
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.usecase.GuardarPerfil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class PerfilTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private val tel = Telefono(id = 7, numero = "+5352345678")
    private val repo = PerfilRepo(Perfil(nombre = "Ana", apellidos = "Díaz", ci = "85010112345", telefonos = listOf(tel), pagoTelefonoId = 7))
    private fun vm() = PerfilViewModel(repo, GuardarPerfil(repo), cu.shalev.app.PrefRepo(), cu.shalev.app.TipoRepo())

    // ---------- lógica

    @Test fun camposVaciosSonValidosPeroConTextoDebenCumplirLasReglas() {
        assertTrue(DatosPerfilForm().valido)
        val malo = DatosPerfilForm(nombre = "A1", apellidos = "X", ci = "12")
        assertEquals(TextosPerfil.ERROR_NOMBRE, malo.errorNombre)
        assertEquals(TextosPerfil.ERROR_NOMBRE, malo.errorApellidos)
        assertEquals(TextosPerfil.ERROR_CI, malo.errorCi)
        assertFalse(malo.valido)
    }

    @Test fun nombreConDigitosYSimbolosSeMarcaComoInvalidoMientrasSeEdita() {
        val form = DatosPerfilForm(nombre = "12345!!!")
        assertEquals(TextosPerfil.ERROR_NOMBRE, form.errorNombre)
        assertFalse(form.valido)
    }

    @Test fun aplicarNormalizaYConservaLasListas() {
        val p = DatosPerfilForm("  Luis ", "Pérez Gómez ", "ab12345").aplicarA(repo.state.value)
        assertEquals("Luis", p.nombre); assertEquals("Pérez Gómez", p.apellidos); assertEquals("AB12345", p.ci)
        assertEquals(listOf(tel), p.telefonos); assertEquals(7L, p.pagoTelefonoId)
    }

    @Test fun cambiadoIgnoraEspaciosYMayusculasDelCi() {
        val p = repo.state.value
        assertFalse(DatosPerfilForm(" Ana", "Díaz ", "85010112345").cambiado(p))
        assertTrue(DatosPerfilForm("Ana", "Díaz", "85010112346").cambiado(p))
    }

    // ---------- ViewModel

    @Test fun sinTocarNadaElFormularioSigueAlPerfilGuardado() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertEquals("Ana", vm.state.value.form.nombre)
        assertFalse(vm.state.value.sinGuardar)
        // Otro sitio (Licencia) actualiza el Perfil: se refleja al momento.
        repo.state.value = repo.state.value.copy(nombre = "Ana María")
        assertEquals("Ana María", vm.state.value.form.nombre)
    }

    @Test fun guardarEscribeSoloDatosPersonalesYAvisa() = runTest {
        val vm = vm(); val msgs = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.mensajes.collect { msgs += it } }
        vm.editar { it.copy(apellidos = "Díaz Soto") }
        assertTrue(vm.state.value.sinGuardar)
        // Mientras se edita, la lista cambia en Pago electrónico: no se pisa.
        repo.state.value = repo.state.value.copy(telefonos = listOf(tel, Telefono(id = 8, numero = "+5351111111")))
        vm.guardar()
        assertEquals("Díaz Soto", repo.state.value.apellidos)
        assertEquals(2, repo.state.value.telefonos.size)
        assertFalse(vm.state.value.sinGuardar)
        assertEquals(listOf(TextosPerfil.GUARDADO), msgs)
    }

    @Test fun datosInvalidosMuestranErroresYNoGuardan() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.editar { it.copy(ci = "1") }
        vm.guardar()
        assertTrue(vm.state.value.form.mostrarErrores)
        assertEquals("85010112345", repo.state.value.ci)
    }

    @Test fun descartarVuelveAlGuardado() = runTest {
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.editar { it.copy(nombre = "Otra") }
        vm.descartar()
        assertEquals("Ana", vm.state.value.form.nombre)
    }
}
