package cu.shalev.app.servicios

import androidx.lifecycle.SavedStateHandle
import cu.shalev.app.FakeArchivos
import cu.shalev.app.FakeExportador
import cu.shalev.app.InsRepo
import cu.shalev.app.RelojFijo
import cu.shalev.app.ServRepo
import cu.shalev.app.T0
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.navigation.Route
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.FiltroServicios
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.Servicio
import cu.shalev.domain.usecase.EliminarServicios
import cu.shalev.domain.usecase.ExportarTablas
import cu.shalev.domain.usecase.ObservarServicios
import cu.shalev.domain.usecase.ObtenerFichaServicio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.emitAll
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

/** 0.21.9 (P59): ServiciosViewModel (lista, búsqueda, filtro, selección, ficha, borrar, modo venta). */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiciosViewModelTest {

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private val tablaPng = cu.shalev.app.FakeTabla(FakeArchivos())
    private val tarjetas = cu.shalev.app.FakeRenderer(FakeArchivos())
    private val servicios = ServRepo()
    private val insumos = InsRepo().apply {
        items.value = listOf(Insumo(id = 1, nombre = "Gel", precio = Cup.ofPesos(5), cantidad = Cantidad.enteras(1), creadoEn = T0))
    }

    private fun servicio(id: Long, nombre: String, tipo: String = "Peluquería", segundos: Long = id) =
        Servicio(id = id, nombre = nombre, tipo = tipo, importe = Cup.ofPesos(100), creadoEn = T0.plusSeconds(segundos))

    private fun poner(vararg s: Pair<Servicio, List<RecetaLinea>>) {
        servicios.items.value = s.associate { it.first.id to it.first }
        servicios.lineas.value = s.associate { it.first.id to it.second }
    }

    private fun vm(saved: SavedStateHandle = SavedStateHandle(), repo: cu.shalev.domain.repository.ServicioRepository = servicios) = ServiciosViewModel(
        saved, ObservarServicios(repo, insumos, Dispatchers.Unconfined), repo, ObtenerFichaServicio(servicios, insumos),
        EliminarServicios(servicios), ExportarTablas(FakeExportador()), FakeArchivos(), RelojFijo(), tablaPng, tarjetas, cu.shalev.app.FakeEstadoPantallas(),
    )

    @Test fun serviciosCompartenImagenYPromocionSinRecetas() = runTest {
        poner(servicio(1, "Corte") to emptyList())
        val vm = vm()
        val ev = eventos(vm)
        vm.exportar(cu.shalev.app.inventario.FormatoSalida.IMAGEN)
        assertTrue(ev.last() is EventoServicios.CompartirImagenes)
        assertEquals("Corte", tarjetas.renderizados.single().single().nombre)
        assertTrue(tablaPng.renderizadas.isEmpty())
        vm.comentarioPromocion("Oferta semanal")
        vm.exportar(cu.shalev.app.inventario.FormatoSalida.TARJETAS)
        assertEquals("Oferta semanal", tarjetas.comentarios.last())
        assertEquals("Corte", tarjetas.renderizados.last().single().nombre)
    }

    private fun TestScope.eventos(vm: ServiciosViewModel): MutableList<EventoServicios> {
        val l = mutableListOf<EventoServicios>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { l += it } }
        // 0.30.0 (F1): retardo del buscador a 0 para no depender del reloj virtual.
        vm.debounceBusqueda = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return l
    }

    @Test fun reintentarElMismoFiltroRecuperaServiciosYSeleccionTrasUnError() = runTest {
        poner(servicio(1, "Corte") to emptyList())
        var fallar = true
        val repo = object : cu.shalev.domain.repository.ServicioRepository by servicios {
            override fun observarTodos() = kotlinx.coroutines.flow.flow {
                if (fallar) error("fallo simulado")
                emitAll(servicios.observarTodos())
            }
        }
        val vm = vm(repo = repo)
        eventos(vm)
        assertTrue(vm.state.value.vista is EstadoCarga.Error)
        val filtro = vm.state.value.filtro
        fallar = false
        vm.reintentar()
        assertEquals(filtro, vm.state.value.filtro)
        assertTrue(vm.state.value.vista is EstadoCarga.Exito)
        vm.alternar(1)
        assertEquals(setOf(1L), vm.state.value.seleccion)
        assertEquals(1L, vm.state.value.elementosFijados.single().servicio.id)
    }

    @Test fun listaVaciaYLuegoConServiciosBuscablesYFiltrables() = runTest {
        val vm = vm()
        eventos(vm)
        assertTrue(vm.state.value.vista is EstadoCarga.Vacio)
        poner(servicio(1, "Corte") to emptyList(), servicio(2, "Manicura", tipo = "Uñas") to emptyList())
        assertTrue(vm.state.value.vista is EstadoCarga.Exito)
        assertEquals(listOf("Peluquería", "Uñas"), vm.state.value.tipos)

        vm.buscar("mani")
        assertEquals(listOf("Manicura"), vm.state.value.items.map { it.servicio.nombre })
        vm.buscar("x".repeat(200))
        assertEquals(80, vm.state.value.filtro.texto.length)               // el buscador se corta a 80
        vm.buscar("")
        vm.aplicarFiltro(FiltroServicios(tipo = "Peluquería"))
        assertEquals(listOf("Corte"), vm.state.value.items.map { it.servicio.nombre })
        vm.quitarFiltros()
        assertEquals(2, vm.state.value.items.size)
    }

    @Test fun seleccionSobreviveALaBusquedaYSeBorraEnBloque() = runTest {
        poner(servicio(1, "Corte") to emptyList(), servicio(2, "Peinado") to emptyList(), servicio(3, "Tinte") to emptyList())
        val vm = vm()
        val ev = eventos(vm)
        vm.alternar(1); vm.alternar(2)
        vm.buscar("tinte")
        assertEquals(setOf(1L, 2L), vm.state.value.seleccion)                // no se pierde al buscar
        assertEquals(listOf(1L, 2L), vm.state.value.elementosFijados.map { it.servicio.id })
        vm.seleccionarTodo()
        assertEquals(setOf(1L, 2L, 3L), vm.state.value.seleccion)
        vm.seleccionarTodo()                                                 // segunda vez: desmarca los visibles
        assertEquals(setOf(1L, 2L), vm.state.value.seleccion)

        vm.pedirEliminarSeleccion()
        assertEquals(ConfirmarEliminarServicio.Seleccion(2), vm.state.value.confirmar)
        vm.confirmarEliminar()
        assertEquals(EventoServicios.Mensaje("2 servicios eliminados."), ev.last())
        assertTrue(vm.state.value.seleccion.isEmpty())
        assertTrue(servicios.items.value.getValue(1).eliminado && servicios.items.value.getValue(2).eliminado)
        vm.buscar("")
        assertEquals(listOf("Tinte"), vm.state.value.items.map { it.servicio.nombre })
    }

    @Test fun laSeleccionSePodaSiOtroBorraElServicio() = runTest {
        poner(servicio(1, "Corte") to emptyList(), servicio(2, "Peinado") to emptyList())
        val vm = vm()
        eventos(vm)
        vm.alternar(1); vm.alternar(2)
        servicios.eliminar(1)                                                // p. ej. desde una secundaria
        assertEquals(setOf(2L), vm.state.value.seleccion)
    }

    @Test fun fichaEditarCompartirYEliminar() = runTest {
        poner(servicio(1, "Corte") to listOf(RecetaLinea(1, Cantidad(250))))
        val vm = vm()
        val ev = eventos(vm)
        vm.abrirFicha(1)
        val f = vm.state.value.ficha!!
        assertEquals("Gel", f.lineas.single().nombre)
        assertEquals(4L, f.alcanza)                                          // 1 / 0.25
        vm.editar(1)
        assertEquals(EventoServicios.Navegar(Route.ServicioForm(id = 1)), ev.last())
        assertNull(vm.state.value.ficha)

        vm.abrirFicha(1)
        vm.pedirEliminarFicha()
        assertEquals(ConfirmarEliminarServicio.Uno(1, "Corte"), vm.state.value.confirmar)
        vm.confirmarEliminar()
        assertEquals(EventoServicios.Mensaje(TextosServicios.ELIMINADO), ev.last())
        assertNull(vm.state.value.ficha)
        vm.abrirFicha(1)                                                     // ya no existe
        assertEquals(EventoServicios.Mensaje(TextosServicios.FICHA_NO_DISPONIBLE), ev.last())
        vm.abrirFicha(99)
        assertEquals(EventoServicios.Mensaje(TextosServicios.FICHA_NO_DISPONIBLE), ev.last())
    }

    @Test fun modoVentaSoloDejaElegirLoQueAlcanzaYDevuelveLosIds() = runTest {
        poner(
            servicio(1, "Corte") to emptyList(),
            servicio(2, "Tinte") to listOf(RecetaLinea(1, Cantidad.enteras(5))), // necesita 5 de Gel, hay 1
            servicio(3, "Peinado") to emptyList(),
        )
        val vm = vm(SavedStateHandle(mapOf(ServiciosViewModel.KEY_VENTA to true)))
        val ev = eventos(vm)
        assertTrue(vm.state.value.modoVenta)
        vm.alternar(2)
        assertEquals(EventoServicios.Mensaje(TextosServicios.NO_VENDIBLE), ev.last())
        assertTrue(vm.state.value.seleccion.isEmpty())
        vm.confirmarSeleccionVenta()                                         // sin nada elegido no hace nada
        assertTrue(ev.none { it is EventoServicios.SeleccionVenta })
        vm.seleccionarTodo()                                                 // solo los vendibles
        assertEquals(setOf(1L, 3L), vm.state.value.seleccion)
        vm.confirmarSeleccionVenta()
        assertEquals(setOf(1L, 3L), (ev.last() as EventoServicios.SeleccionVenta).ids.toSet())
    }
}
