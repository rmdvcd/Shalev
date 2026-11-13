package cu.shalev.app.insumos

import androidx.lifecycle.SavedStateHandle
import cu.shalev.app.ProdRepo
import cu.shalev.app.RelojFijo
import cu.shalev.app.T0
import cu.shalev.app.prod
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.UnidadMedida
import cu.shalev.domain.usecase.GuardarInsumo
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InsumoFormTest {

    // ---------------- Lógica ----------------

    private val valido = InsumoForm(nombre = "Harina", precio = "120.50", cantidad = "2.5", unidad = UnidadMedida.KILOGRAMO)

    @Test fun formularioValidoProduceElInsumo() {
        assertTrue(InsumoFormLogic.validar(valido).isEmpty())
        val i = InsumoFormLogic.aInsumo(valido.copy(nombre = "  Harina  ", nivelBajo = "5", nivelCritico = "1.5"), T0)!!
        assertEquals("Harina", i.nombre)
        assertEquals(Cup(12050), i.precio)
        assertEquals(Cantidad(2500), i.cantidad)
        assertEquals(Cantidad.enteras(5), i.nivelBajo)
        assertEquals(Cantidad(1500), i.nivelCritico)
        assertEquals(UnidadMedida.KILOGRAMO, i.unidad)
        assertEquals(T0, i.creadoEn)
    }

    @Test fun obligatoriosYFormatosTienenMensajesClaros() {
        val e = InsumoFormLogic.validar(InsumoForm())
        assertEquals("Escribe el nombre.", e[CamposInsumo.NOMBRE])
        assertEquals("Escribe el precio de costo.", e[CamposInsumo.PRECIO])
        assertEquals("Escribe cuánto tienes (puede ser 0).", e[CamposInsumo.CANTIDAD])
        assertNull(e[CamposInsumo.NIVEL_BAJO]) // opcionales

        val f = InsumoFormLogic.validar(valido.copy(precio = "12.345", cantidad = "1.2345", nivelBajo = "abc"))
        assertTrue(f[CamposInsumo.PRECIO]!!.startsWith("Escribe un importe válido"))
        assertEquals(InsumoFormLogic.FORMATO_CANTIDAD, f[CamposInsumo.CANTIDAD])
        assertEquals(InsumoFormLogic.FORMATO_CANTIDAD, f[CamposInsumo.NIVEL_BAJO])
        assertEquals(null, InsumoFormLogic.aInsumo(valido.copy(cantidad = "-1"), T0))
    }

    @Test fun criticoNoPuedeSuperarAlBajoReglaDelDominio() {
        val e = InsumoFormLogic.validar(valido.copy(nivelBajo = "2", nivelCritico = "3"))
        assertEquals("El nivel crítico debe ser 0 o más y no mayor que el nivel bajo.", e[CamposInsumo.NIVEL_CRITICO])
        assertTrue(InsumoFormLogic.validar(valido.copy(nivelBajo = "2", nivelCritico = "2")).isEmpty())
    }

    @Test fun desdeUnInsumoSeEditaSinSeparadorDeMiles() {
        val f = InsumoFormLogic.desde(ins(7, "Azúcar", cantidad = 1250, precio = 1450))
        assertEquals("1250", f.cantidad)
        assertEquals("1450", f.precio)
        assertEquals(7, f.id)
        assertTrue(InsumoFormLogic.validar(f).isEmpty())
    }

    // ---------------- ViewModel ----------------

    private val productos = ProdRepo()
    private val insumos = InsumosMem(productos)
    private val reloj = RelojFijo()
    private val eventos = mutableListOf<EventoInsumoForm>()

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private fun TestScope.vm(id: Long = 0, nombre: String? = null): InsumoFormViewModel {
        val vm = InsumoFormViewModel(SavedStateHandle(mapOf("id" to id, "nombre" to nombre)), insumos, GuardarInsumo(insumos, productos, reloj), reloj)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { eventos += it } }
        return vm
    }

    @Test fun erroresSoloEnCamposTocadosHastaIntentarGuardar() = runTest {
        val vm = vm()
        assertTrue(vm.state.value.errores.isEmpty())
        vm.nombre("")
        assertEquals(setOf(CamposInsumo.NOMBRE), vm.state.value.errores.keys)
        vm.guardar()
        assertEquals(setOf(CamposInsumo.NOMBRE, CamposInsumo.PRECIO, CamposInsumo.CANTIDAD), vm.state.value.errores.keys)
        assertTrue(insumos.guardados.isEmpty())
    }

    @Test fun nuevoInsumoSeGuardaYRegistraElAlta() = runTest {
        val vm = vm()
        vm.nombre("Aceite"); vm.precio("300"); vm.cantidad("2"); vm.unidad(UnidadMedida.LITRO)
        vm.guardar()
        assertEquals(EventoInsumoForm.Guardado("Insumo guardado."), eventos.last())
        val i = insumos.guardados.single()
        assertEquals(UnidadMedida.LITRO, i.unidad)
        assertEquals(listOf("ALTA" to Cantidad.enteras(2)), insumos.movimientos)
    }

    @Test fun editarCargaYAlCambiarPrecioRecalculaElCostoDelElaborado() = runTest {
        insumos.put(ins(1, "Harina", cantidad = 10, precio = 100))
        productos.items.value = listOf(prod(50, "Pan", categoria = Categorias.ELABORADO, costo = 50))
        productos.recetas[50] = Receta(50, listOf(RecetaLinea(1, Cantidad(500))))
        val vm = vm(id = 1)
        assertEquals("Harina", vm.state.value.form.nombre)
        assertEquals("10", vm.state.value.form.cantidad)
        vm.precio("200"); vm.cantidad("8")
        vm.guardar()
        assertEquals(EventoInsumoForm.Guardado("Cambios guardados."), eventos.last())
        assertEquals(Cup.ofPesos(100), productos.items.value.single().precioCosto) // 0.5 kg × 200 CUP
        assertEquals("AJUSTE" to Cantidad(-2000), insumos.movimientos.last()) // la edición de cantidad queda registrada
    }

    @Test fun insumoInexistenteMuestraAviso() = runTest {
        assertTrue(vm(id = 99).state.value.noEncontrado)
    }

    @Test fun nombreQueLlegaDelFormularioDeProducto() = runTest {
        assertEquals("Harina", vm(nombre = "Harina").state.value.form.nombre)
        assertEquals("", vm(nombre = " ").state.value.form.nombre)
    }
}
