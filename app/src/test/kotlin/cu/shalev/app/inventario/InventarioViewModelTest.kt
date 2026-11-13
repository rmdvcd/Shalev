package cu.shalev.app.inventario

import androidx.lifecycle.SavedStateHandle
import cu.shalev.app.FakeArchivos
import cu.shalev.app.FakeExportador
import cu.shalev.app.FakeFotos
import cu.shalev.app.FakeRenderer
import cu.shalev.app.FakeTabla
import cu.shalev.app.InsRepo
import cu.shalev.app.PrefRepo
import cu.shalev.app.ProdRepo
import cu.shalev.app.RelojFijo
import cu.shalev.app.inicio.AlertaPendiente
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.navigation.Route
import cu.shalev.app.prod
import cu.shalev.domain.model.FiltroInventario
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.TipoArticulo
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.usecase.EliminarProductos
import cu.shalev.domain.usecase.ExportarFicha
import cu.shalev.domain.usecase.ExportarInventario
import cu.shalev.domain.usecase.LimpiarFotos
import cu.shalev.domain.usecase.ObservarInventario
import cu.shalev.domain.usecase.ObtenerFichaProducto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InventarioViewModelTest {
    private val productos = ProdRepo()
    private val prefs = PrefRepo()
    private val reloj = RelojFijo()
    private val archivos = FakeArchivos()
    private val exportador = FakeExportador()
    private val renderer = FakeRenderer(archivos)
    private val tablaPng = FakeTabla(archivos)
    private val eventos = mutableListOf<EventoInventario>()
    private val insumosInv = cu.shalev.app.InsRepo()

    @Before fun antes() { AlertaPendiente.consumir(); Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun despues() { AlertaPendiente.consumir(); Dispatchers.resetMain() }

    private fun TestScope.vm(
        alerta: TipoAlerta? = null,
        saved: SavedStateHandle = SavedStateHandle(),
        repo: cu.shalev.domain.repository.ProductoRepository = productos,
    ): InventarioViewModel {
        alerta?.let(AlertaPendiente::pedir)
        val vm = InventarioViewModel(
            saved = saved,
            observarInventario = ObservarInventario(repo, insumosInv, prefs, reloj, Dispatchers.Unconfined),
            productosRepo = repo,
            obtenerFicha = ObtenerFichaProducto(productos, InsRepo(), prefs, reloj),
            eliminarProductos = EliminarProductos(productos),
            insumosRepo = insumosInv,
            obtenerFichaInsumo = cu.shalev.domain.usecase.ObtenerFichaInsumo(insumosInv, productos, prefs, cu.shalev.app.ServRepo()),
            eliminarInsumos = cu.shalev.domain.usecase.EliminarInsumos(insumosInv),
            exportarInventario = ExportarInventario(exportador),
            exportarFicha = ExportarFicha(exportador),
            tarjetas = renderer,
            imagenTabla = tablaPng,
            archivos = archivos,
            clock = reloj,
            limpiarFotos = LimpiarFotos(productos, FakeFotos(), cu.shalev.app.ServRepo()),
            estadoPantallas = cu.shalev.app.FakeEstadoPantallas(),
        )
        // 0.30.0 (F1): en la app el buscador espera 250 ms; los tests no quieren depender del reloj virtual.
        vm.debounceBusqueda = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { eventos += it } }
        return vm
    }

    private fun cargar() {
        productos.items.value = listOf(
            prod(1, "Refresco de cola", cantidad = 20),
            prod(2, "Galletas", cantidad = 3, categoria = "Dulces"),
            prod(3, "Café molido", cantidad = 0, categoria = "Víveres"),
        )
    }

    @Test fun reintentarElMismoFiltroRecuperaListaYSeleccionTrasUnError() = runTest {
        cargar()
        var fallar = true
        val repo = object : cu.shalev.domain.repository.ProductoRepository by productos {
            override fun observarTodos() = kotlinx.coroutines.flow.flow {
                if (fallar) error("fallo de lectura simulado")
                emitAll(productos.observarTodos())
            }
        }
        val vm = vm(repo = repo)
        assertTrue(vm.state.value.vista is EstadoCarga.Error)
        val filtro = vm.state.value.filtro
        fallar = false
        vm.reintentar()
        assertEquals(filtro, vm.state.value.filtro)
        assertTrue(vm.state.value.vista is EstadoCarga.Exito)
        vm.alternar(1)
        assertEquals(setOf(1L), vm.state.value.seleccion)
        assertEquals(1L, vm.state.value.elementosFijados.single().producto.id)
    }

    @Test fun vacioSinProductosYOrdenCronologico() = runTest {
        val vm = vm()
        assertTrue(vm.state.value.vista is EstadoCarga.Vacio)
        cargar()
        assertEquals(listOf(1L, 2L, 3L), vm.state.value.items.map { it.producto.id }) // más reciente primero
        assertEquals(3, vm.state.value.total)
    }

    @Test fun alertaDeInicioPreseleccionaElFiltro() = runTest {
        cargar()
        // 0.21.0 (C1): el Café a 0 está «Sin existencia».
        val vm = vm(alerta = TipoAlerta.SIN_EXISTENCIA)
        assertEquals(TipoAlerta.SIN_EXISTENCIA, vm.state.value.filtro.alerta)
        assertEquals(listOf(3L), vm.state.value.items.map { it.producto.id })
        vm.quitarFiltros()
        assertEquals(3, vm.state.value.items.size)
    }

    /**
     * 0.31.0: la alerta de Inicio viaja por [AlertaPendiente] (señal de proceso) y el Inventario la aplica al
     * construirse, una sola vez: la pestaña que se abra después no repite el filtro. El canal importa: el
     * `SavedStateHandle` que Hilt inyecta al ViewModel no es el de la entrada de navegación, y escribir en la entrada
     * no llegaba al filtro (fallo comprobado en el teléfono).
     */
    @Test fun laAlertaPendienteSeAplicaUnaVezYSeConsume() = runTest {
        cargar()
        AlertaPendiente.pedir(TipoAlerta.SIN_EXISTENCIA)
        val vm = vm()
        assertEquals(TipoAlerta.SIN_EXISTENCIA, vm.state.value.filtro.alerta)
        assertEquals(listOf(3L), vm.state.value.items.map { it.producto.id })
        val otra = vm()
        assertNull(otra.state.value.filtro.alerta)
        assertEquals(3, otra.state.value.items.size)
    }

    @Test fun alertaEnInventarioYaAbiertoSustituyeFiltrosAnteriores() = runTest {
        cargar()
        val vm = vm()
        vm.buscar("galletas")
        vm.aplicarFiltro(FiltroInventario(categoria = "Dulces"))
        vm.alternar(2)
        AlertaPendiente.pedir(TipoAlerta.SIN_EXISTENCIA)
        vm.aplicarAlertaPendiente()
        assertEquals(FiltroInventario(alerta = TipoAlerta.SIN_EXISTENCIA), vm.state.value.filtro)
        assertEquals(listOf(3L), vm.state.value.items.map { it.producto.id })
        assertTrue(vm.state.value.seleccion.isEmpty())
        assertNull(AlertaPendiente.consumir())
    }

    @Test fun buscadorYFiltroDeCategoria() = runTest {
        cargar()
        val vm = vm()
        vm.buscar("CAFE")
        assertEquals(listOf(3L), vm.state.value.items.map { it.producto.id }) // sin tildes ni mayúsculas
        vm.buscar("")
        vm.aplicarFiltro(FiltroInventario(categoria = "Dulces"))
        assertEquals(listOf(2L), vm.state.value.items.map { it.producto.id })
        assertNull(vm.state.value.hoja)
        vm.buscar("zzz")
        assertTrue(vm.state.value.vista is EstadoCarga.Exito) // hay productos: "Sin resultados", no "vacío"
        assertTrue(vm.state.value.items.isEmpty())
    }

    /**
     * 0.30.0 (F1): escribir rápido no dispara una consulta por tecla.
     *
     * Se cuentan las **reconsultas de verdad** (cada re-suscripción a `ObservarInventario` emite «Cargando»): el
     * estado de la pantalla cambia con cada tecla —el campo de texto no se retrasa—, pero la consulta a Room no.
     * Ocho pulsaciones seguidas = una sola consulta, y con el resultado de la palabra completa.
     */
    @Test fun losSeleccionadosSeConservanFijadosFueraDeLaBusqueda() = runTest {
        cargar()
        val vm = vm()
        vm.alternar(1)
        vm.alternar(3)
        vm.buscar("galle")
        assertEquals(listOf(1L, 3L), vm.state.value.elementosFijados.map { it.producto.id })
        assertEquals(listOf(2L), vm.state.value.items.map { it.producto.id })
    }

    @Test fun elBuscadorAgrupaLasPulsaciones() = runTest {
        cargar()
        val vm = vm()
        vm.debounceBusqueda = 250 // el retardo real de la app
        var consultas = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.state.map { it.vista }.distinctUntilChanged().collect { v -> if (v is EstadoCarga.Cargando) consultas++ }
        }
        val antesDeEscribir = consultas
        listOf("r", "re", "ref", "refr", "refre", "refres", "refresc", "refresco").forEach { vm.buscar(it) }
        runCurrent()
        assertEquals("mientras se escribe, ninguna consulta nueva", antesDeEscribir, consultas)
        advanceTimeBy(300)
        runCurrent()
        assertEquals("las 8 pulsaciones producen una sola consulta", antesDeEscribir + 1, consultas)
        assertEquals(listOf(1L), vm.state.value.items.map { it.producto.id })
    }

    @Test fun seleccionSobreviveALaBusquedaYMarcarTodoAlterna() = runTest {
        cargar()
        val vm = vm()
        vm.alternar(1)
        vm.buscar("galle")
        assertEquals(setOf(1L), vm.state.value.seleccion)
        vm.seleccionarTodo()
        assertEquals(setOf(1L, 2L), vm.state.value.seleccion)
        assertTrue(vm.state.value.todosVisiblesSeleccionados)
        vm.seleccionarTodo() // desmarca solo lo visible
        assertEquals(setOf(1L), vm.state.value.seleccion)
        vm.limpiarSeleccion()
        assertTrue(vm.state.value.seleccion.isEmpty())
    }

    @Test fun exportarPdfSinSeleccionUsaLoVisible() = runTest {
        cargar()
        val vm = vm()
        vm.buscar("a") // refresco de cola, galletas, café molido → los 3
        vm.aplicarFiltro(FiltroInventario(categoria = "Víveres"))
        vm.exportar(FormatoSalida.PDF)
        val e = eventos.filterIsInstance<EventoInventario.Compartir>().single()
        assertEquals(FormatoExport.PDF.mime, e.mime)
        assertEquals("Shalev_inventario_2026-09-30.pdf", e.archivos.single().name)
        assertEquals("PDF", e.archivos.single().readText())
        assertEquals(1, exportador.llamadas.single().first.single().filas.size)
        assertEquals(false, vm.state.value.trabajando)
    }

    @Test fun exportarExcelConSeleccionSoloLaSeleccion() = runTest {
        cargar()
        val vm = vm()
        vm.alternar(1); vm.alternar(3)
        vm.buscar("galle") // la selección no se ve, pero se exporta ella
        vm.exportar(FormatoSalida.EXCEL)
        val (tablas, formato) = exportador.llamadas.single()
        assertEquals(FormatoExport.XLSX, formato)
        assertEquals(2, tablas.single().filas.size)
        assertTrue(eventos.filterIsInstance<EventoInventario.Compartir>().single().archivos.single().name.endsWith(".xlsx"))
    }

    @Test fun tarjetasImagenYTextoParaClientes() = runTest {
        cargar()
        val vm = vm()
        vm.alternar(2)
        vm.exportar(FormatoSalida.TARJETAS)
        assertEquals(listOf(2L), renderer.renderizados.single().map { it.id })
        assertEquals("image/png", eventos.filterIsInstance<EventoInventario.Compartir>().single().mime)
        // Un solo artículo usa una tarjeta; varios usan una lista de precios.
        eventos.clear()
        vm.exportar(FormatoSalida.IMAGEN)
        assertEquals(listOf(2L), renderer.renderizados.last().map { it.id })
        assertTrue(tablaPng.renderizadas.isEmpty())
        vm.alternar(1)
        vm.exportar(FormatoSalida.IMAGEN)
        val tabla = tablaPng.renderizadas.single()
        assertEquals(listOf("Categoría", "Producto", "Precio"), tabla.columnas)
        assertEquals(2, tabla.filas.size)
        assertEquals("image/png", eventos.filterIsInstance<EventoInventario.Compartir>().last().mime)
    }

    @Test fun errorAlExportarSeAvisaSinDetalles() = runTest {
        cargar()
        exportador.fallar = true
        val vm = vm()
        vm.exportar(FormatoSalida.PDF)
        assertEquals(TextosInventario.ERROR_EXPORTAR, (eventos.single() as EventoInventario.Mensaje).texto)
    }

    @Test fun guardarEnElDispositivoPideDestinoYEscribe() = runTest {
        cargar()
        val vm = vm()
        vm.pedirGuardar(FormatoSalida.IMAGEN) // solo PDF/Excel se guardan
        assertTrue(eventos.isEmpty())
        vm.pedirGuardar(FormatoSalida.PDF)
        val g = eventos.single() as EventoInventario.GuardarComo
        assertEquals("application/pdf", g.mime)
        assertTrue(g.nombre.endsWith(".pdf"))
        vm.guardarEn("content://destino/1")
        assertEquals("PDF", archivos.destinos.getValue("content://destino/1").toString())
        assertEquals(TextosInventario.GUARDADO_EN_DISPOSITIVO, (eventos.last() as EventoInventario.Mensaje).texto)
        // Cancelar el selector no hace nada
        eventos.clear()
        vm.pedirGuardar(FormatoSalida.EXCEL); vm.guardarEn(null)
        assertEquals(1, eventos.size)
        vm.guardarEn("content://tarde") // sin petición pendiente: se ignora
        assertEquals(1, archivos.destinos.size)
    }

    @Test fun eliminarSeleccionConConfirmacionYPoda() = runTest {
        cargar()
        productos.fallarEliminar = setOf(3)
        val vm = vm()
        vm.alternar(1); vm.alternar(3)
        vm.pedirEliminarSeleccion()
        assertEquals(ConfirmarEliminar.Seleccion(2), vm.state.value.confirmar)
        vm.cancelarEliminar()
        assertEquals(3, productos.items.value.size)
        vm.pedirEliminarSeleccion(); vm.confirmarEliminar()
        assertEquals(listOf(2L, 3L), productos.items.value.map { it.id })
        assertTrue(vm.state.value.seleccion.isEmpty())
        assertEquals(TextosInventario.eliminados(1, 1), (eventos.last() as EventoInventario.Mensaje).texto)
    }

    @Test fun seleccionSePodaSiElProductoDesaparece() = runTest {
        cargar()
        val vm = vm()
        vm.alternar(2)
        productos.items.value = productos.items.value.filterNot { it.id == 2L }
        assertTrue(vm.state.value.seleccion.isEmpty())
    }

    @Test fun promocionLlevaComentarioOpcionalAcotado() = runTest {
        cargar()
        val vm = vm()
        vm.comentarioPromocion("Oferta de hoy")
        vm.exportar(FormatoSalida.TARJETAS)
        assertEquals("Oferta de hoy", renderer.comentarios.single())
        vm.comentarioPromocion("x".repeat(200))
        assertEquals(160, vm.state.value.comentarioPromocion.length)
    }

    @Test fun fichaEditarCompartirYEliminar() = runTest {
        cargar()
        val vm = vm()
        vm.abrirFicha(2)
        assertEquals("Galletas", vm.state.value.ficha?.producto?.nombre)
        vm.compartirFicha(FormatoSalida.TARJETAS)
        val c = eventos.filterIsInstance<EventoInventario.Compartir>().single()
        assertEquals("image/png", c.mime)
        vm.pedirEliminarFicha()
        assertEquals(ConfirmarEliminar.Uno(2, "Galletas"), vm.state.value.confirmar)
        vm.confirmarEliminar()
        assertNull(vm.state.value.ficha)
        assertEquals(TextosInventario.ELIMINADO, (eventos.last() as EventoInventario.Mensaje).texto)
        vm.abrirFicha(2)
        assertEquals(TextosInventario.FICHA_NO_DISPONIBLE, (eventos.last() as EventoInventario.Mensaje).texto)
    }

    @Test fun agregarYEditarNavegan() = runTest {
        cargar()
        val vm = vm()
        vm.agregar()
        vm.abrirFicha(1); vm.editar(1)
        assertNull(vm.state.value.ficha)
        assertEquals(
            listOf<Route>(Route.ProductoForm(), Route.ProductoForm(id = 1)),
            eventos.filterIsInstance<EventoInventario.Navegar>().map { it.route },
        )
        assertNotNull(vm)
    }

    @Test fun agregarConElFiltroInsumosAbreElFormularioDeInsumo() = runTest {
        cargar()
        val vm = vm()
        vm.aplicarFiltro(FiltroInventario(tipo = TipoArticulo.INSUMOS))
        vm.agregar()
        assertEquals(
            listOf<Route>(Route.InsumoForm()),
            eventos.filterIsInstance<EventoInventario.Navegar>().map { it.route },
        )
    }
}
