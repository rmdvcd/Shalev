package cu.shalev.app.precios

import cu.shalev.app.PerfilRepo
import cu.shalev.app.PreciosRepo
import cu.shalev.app.ProdRepo
import cu.shalev.app.ServRepo
import cu.shalev.app.pagos.PagoElectronicoViewModel
import cu.shalev.app.pagos.TextosPago
import cu.shalev.app.pagos.TipoCuentaPago
import cu.shalev.app.prod
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.usecase.ActivarPreajuste
import cu.shalev.domain.usecase.AgregarTarjeta
import cu.shalev.domain.usecase.AgregarTelefono
import cu.shalev.domain.usecase.EditarTarjeta
import cu.shalev.domain.usecase.EditarTelefono
import cu.shalev.domain.usecase.EliminarTarjeta
import cu.shalev.domain.usecase.EliminarTelefono
import cu.shalev.domain.usecase.GuardarPerfil
import cu.shalev.domain.usecase.GuardarPreajuste
import cu.shalev.domain.usecase.SeleccionarPagoElectronico
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreciosYPagoViewModelTest {

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    // ---------------- Precios ----------------

    private val precios = PreciosRepo()
    private val productos = ProdRepo().apply { items.value = listOf(prod(1, "Refresco"), prod(2, "Pan"), prod(3, "Café")) }
    private fun preciosVm() = PreciosViewModel(
        precios, productos, ServRepo(), GuardarPreajuste(precios), ActivarPreajuste(precios),
    )

    @Test fun crearPreajusteParaVariosProductos() = runTest {
        val vm = preciosVm(); val msgs = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.mensajes.collect { msgs += it } }
        vm.nuevo()
        vm.guardar() // vacío: muestra errores, no guarda
        assertTrue(vm.state.value.errores.isNotEmpty())
        vm.cambiar { it.copy(porcentaje = "10", metodo = MetodoPago.TRANSFERENCIA) }
        vm.cambiar { it.copy(busqueda = "") }
        vm.elegirVisibles(true)
        assertEquals(setOf(1L, 2L, 3L), vm.state.value.form!!.productoIds)
        vm.alternarProducto(3)
        vm.guardar()
        assertNull(vm.state.value.form)
        val p = precios.preajustes.value.single()
        assertEquals(1000, p.puntosBasicos)
        assertEquals(setOf(1L, 2L), p.productoIds)
        assertEquals(MetodoPago.TRANSFERENCIA, p.metodoPago)
        assertEquals("Ajuste creado", msgs.last())
    }

    @Test fun pausarYBorrarPreajuste() = runTest {
        val vm = preciosVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.nuevo(); vm.cambiar { it.copy(porcentaje = "5", sube = false, productoIds = setOf(2)) }; vm.guardar()
        val p = vm.state.value.preajustes.single()
        vm.activar(p, false)
        assertEquals(false, precios.preajustes.value.single().activo)
        vm.editar(precios.preajustes.value.single())
        vm.pedirBorrado()
        assertEquals(p.id, vm.state.value.borrar!!.id)
        vm.confirmarBorrado()
        assertTrue(precios.preajustes.value.isEmpty())
    }

    // ---------------- Pago electrónico ----------------

    private val perfil = PerfilRepo()
    private fun pagoVm(): PagoElectronicoViewModel {
        val g = GuardarPerfil(perfil)
        return PagoElectronicoViewModel(
            perfil, SeleccionarPagoElectronico(perfil, g),
            AgregarTelefono(perfil, g), AgregarTarjeta(perfil, g), EditarTelefono(perfil, g), EditarTarjeta(perfil, g),
            EliminarTelefono(perfil, g), EliminarTarjeta(perfil, g),
        )
    }

    @Test fun listaEditableDesdeAjustesSinDuplicados() = runTest {
        val vm = pagoVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.nuevo(TipoCuentaPago.TARJETA)
        vm.editarEdicion { it.copy(numero = "9205129900001234", alias = "BANDEC") }
        vm.guardarEdicion()
        assertNull(vm.state.value.edicion)
        val id = perfil.state.value.tarjetas.single().id
        vm.nuevo(TipoCuentaPago.TARJETA)
        vm.editarEdicion { it.copy(numero = "9205 1299 0000 1234") }
        vm.guardarEdicion()
        assertEquals(TextosPago.DUPLICADO, vm.state.value.edicion!!.error)
        vm.cerrarEdicion()
        vm.editar(TipoCuentaPago.TARJETA, id)
        vm.editarEdicion { it.copy(numero = "9205 1299 0000 9999") }
        vm.guardarEdicion()
        assertEquals("9205129900009999", perfil.state.value.tarjetas.single().numero)
        vm.elegir(TipoCuentaPago.TARJETA, id)
        assertEquals(id, perfil.state.value.pagoTarjetaId)
        vm.pedirBorrado(TipoCuentaPago.TARJETA, id); vm.confirmarBorrado()
        assertTrue(perfil.state.value.tarjetas.isEmpty())
        assertNull(perfil.state.value.pagoTarjetaId)
    }
}
