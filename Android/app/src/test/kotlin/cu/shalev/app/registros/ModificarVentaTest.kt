package cu.shalev.app.registros

import cu.shalev.app.InsRepo
import cu.shalev.app.PerfilRepo
import cu.shalev.app.PreciosRepo
import cu.shalev.app.ProdRepo
import cu.shalev.app.RelojFijo
import cu.shalev.app.SecundariaRepoFake
import cu.shalev.app.ServRepo
import cu.shalev.app.T0
import cu.shalev.app.TurnoRepo
import cu.shalev.app.VentaRepo
import cu.shalev.app.prod
import cu.shalev.app.venta.CampoCliente
import cu.shalev.core.money.Cup
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.ResumenTurno
import cu.shalev.domain.model.Venta
import cu.shalev.domain.usecase.CotizarVenta
import cu.shalev.domain.usecase.ModificarVenta
import cu.shalev.domain.usecase.ObservarElaborados
import cu.shalev.domain.usecase.ObservarServicios
import cu.shalev.domain.usecase.UsuarioActual
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 0.25.1 (B): Modificar venta a pantalla completa: añadir artículos y cambiar el método de pago. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModificarVentaTest {
    private val productos = ProdRepo()
    private val insumos = InsRepo()
    private val servicios = ServRepo()
    private val turnos = TurnoRepo()
    private val ventas = VentaRepo()
    private val perfil = PerfilRepo(Perfil(nombre = "Ana", apellidos = "Pérez"))
    private val reloj = RelojFijo()

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(): ModificarVentaViewModel {
        val cotizar = CotizarVenta(productos, PreciosRepo(), insumos, servicios)
        return ModificarVentaViewModel(
            productos, ObservarElaborados(insumos, productos, Dispatchers.Unconfined), insumos, ObservarServicios(servicios, insumos, Dispatchers.Unconfined),
            ModificarVenta(cotizar, ventas, turnos, perfil, UsuarioActual(perfil), reloj, SecundariaRepoFake()),
        )
    }

    /** Venta original: 2 Refrescos a 100 (precio de entonces) en efectivo, en el turno abierto. */
    private suspend fun original(): Venta {
        turnos.abrir(T0, "Ana Pérez", Cup.ofPesos(500))
        productos.items.value = listOf(prod(1, "Refresco", cantidad = 8, venta = 120), prod(2, "Galletas", cantidad = 1, venta = 50, costo = 20))
        val v = Venta(
            turnoId = 1, fecha = T0, metodoPago = MetodoPago.EFECTIVO,
            detalles = listOf(DetalleVenta(productoId = 1, nombre = "Refresco", categoria = "Bebidas", cantidad = 2, precioBase = Cup.ofPesos(100), precioUnitario = Cup.ofPesos(100), costoUnitario = Cup.ofPesos(60))),
        )
        ventas.registrar(v, emptyList())
        return ventas.ventas.single()
    }

    @Test fun anadirLineaConPrecioActualYConservarElDeEntonces() = runTest {
        val v = original()
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.iniciar(v)
        assertFalse(vm.state.value.cambiada)
        // La línea original muestra su precio de entonces y su tope suma lo ya vendido (8 + 2).
        val refresco = vm.state.value.lineas.single()
        assertEquals(Cup.ofPesos(100), refresco.producto.precioVenta)
        assertEquals(10L, refresco.maximo)
        vm.abrirBusqueda()
        vm.buscar("gall")
        val galletas = vm.state.value.resultados.single()
        vm.anadir(galletas)
        vm.anadir(galletas) // tope: solo hay 1
        assertEquals(mapOf(1L to 2L, 2L to 1L), vm.state.value.cantidades)
        assertEquals(Cup.ofPesos(250), vm.state.value.total)
        assertTrue(vm.state.value.cambiada)
        assertFalse(vm.state.value.puedeGuardar) // falta el motivo
        vm.editarMotivo("Olvidó las galletas")
        vm.guardar()
        val nueva = ventas.ventas.last()
        assertEquals(v.id, nueva.corrigeVentaId)
        assertEquals(listOf(1L to Cup.ofPesos(100), 2L to Cup.ofPesos(50)), nueva.detalles.map { it.productoId to it.precioUnitario })
        assertTrue(ventas.ventas.first().anulada)
    }

    @Test fun cambiarEfectivoATransferenciaExigeElNumeroYMueveLaCaja() = runTest {
        val v = original()
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.iniciar(v)
        vm.elegirMetodo(MetodoPago.TRANSFERENCIA)
        vm.editarMotivo("Pagó por Transfermóvil")
        assertTrue(vm.state.value.puedeGuardar)
        vm.guardar()
        assertEquals(TextosModificar.TRANSFERENCIA_INCOMPLETA, vm.state.value.error)
        assertTrue(vm.state.value.cliente.intento)
        assertEquals(1, ventas.ventas.size) // nada guardado
        vm.editarCliente(CampoCliente.NOMBRE, "Luis Gómez Ruiz")
        vm.editarCliente(CampoCliente.CI, "85010112345")
        vm.editarCliente(CampoCliente.TELEFONO, "53512345")
        vm.editarCliente(CampoCliente.NUMERO, "MM10040FEJ987")
        vm.guardar()
        assertNull(vm.state.value.error)
        val nueva = ventas.ventas.last()
        assertEquals(MetodoPago.TRANSFERENCIA, nueva.metodoPago)
        assertEquals("MM10040FEJ987", nueva.transaccion?.numero)
        // Caja: la venta original (anulada) deja de contar y la nueva no es efectivo.
        val resumen = ResumenTurno.calcular(ventas.ventas, emptyList())
        assertEquals(Cup.ZERO, resumen.totalEfectivo)
        assertEquals(Cup.ofPesos(200), resumen.totalTransferencia)
    }

    @Test fun sinExistenciasAlGuardarSaleElMismoAvisoQueEnUnaVenta() = runTest {
        val v = original()
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.iniciar(v)
        vm.abrirBusqueda()
        vm.anadir(vm.state.value.resultados.first { it.producto.id == 2L })
        // Otra venta se lleva las últimas galletas antes de guardar.
        productos.items.value = productos.items.value.map { if (it.id == 2L) it.copy(cantidad = 0) else it }
        vm.editarMotivo("Añadir galletas")
        vm.guardar()
        assertEquals(1, ventas.ventas.size)
        val e = vm.state.value.error
        assertTrue(e, e != null && e.isNotBlank() && e != TextosAnulacion.ERROR)
    }

    @Test fun quitarTodoNoSeGuardaYSinCambiosTampoco() = runTest {
        val v = original()
        val vm = vm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.iniciar(v)
        vm.editarMotivo("Prueba de motivo")
        vm.guardar()
        assertEquals(TextosModificar.SIN_CAMBIOS, vm.state.value.error)
        vm.quitar(1)
        assertTrue(vm.state.value.lineas.isEmpty())
        assertFalse(vm.state.value.puedeGuardar)
        vm.guardar()
        assertEquals(TextosAnulacion.SIN_LINEAS, vm.state.value.error)
        assertEquals(1, ventas.ventas.size)
    }

    @Test fun logicaPura() {
        val v = Venta(
            id = 3, turnoId = 1, fecha = T0, metodoPago = MetodoPago.EFECTIVO,
            detalles = listOf(DetalleVenta(productoId = 4, nombre = "Harina", categoria = "Insumos", cantidad = 1, precioBase = Cup.ofPesos(80), precioUnitario = Cup.ofPesos(80), costoUnitario = Cup.ofPesos(50), clase = cu.shalev.domain.model.ClaseArticulo.INSUMO)),
        )
        assertEquals(mapOf(-4L to 1L), EdicionVenta.inicial(v))
        // Un artículo que ya no existe se muestra con los datos de la venta.
        val l = EdicionVenta.lineas(v, EdicionVenta.inicial(v), emptyMap(), emptyMap()).single()
        assertEquals("Harina", l.producto.nombre)
        assertEquals(cu.shalev.domain.model.ClaseArticulo.INSUMO, l.clase)
        assertEquals(mapOf(1L to 3L), EdicionVenta.anadir(mapOf(1L to 3L), 1, 3))
        assertEquals(mapOf(1L to 3L, 2L to 1L), EdicionVenta.anadir(mapOf(1L to 3L), 2, 5))
    }
}
