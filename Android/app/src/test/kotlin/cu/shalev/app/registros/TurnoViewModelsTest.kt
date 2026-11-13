package cu.shalev.app.registros

import androidx.lifecycle.SavedStateHandle
import cu.shalev.app.InsRepo
import cu.shalev.app.PerfilRepo
import cu.shalev.app.RelojFijo
import cu.shalev.app.T0
import cu.shalev.app.TurnoRepo
import cu.shalev.app.VentaRepo
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.venta.VentaViewModel
import cu.shalev.core.money.Cup
import cu.shalev.domain.model.DetalleTurno
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.Turno
import cu.shalev.domain.model.Venta
import cu.shalev.domain.usecase.AbrirTurno
import cu.shalev.domain.usecase.ObservarHistorialTurnos
import cu.shalev.domain.usecase.ObservarPermisoVenta
import cu.shalev.domain.usecase.ObservarPermisoVenta.Permiso
import cu.shalev.domain.usecase.ObtenerDetalleTurno
import cu.shalev.domain.usecase.UsuarioActual
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TurnoViewModelsTest {
    private val turnos = TurnoRepo()
    private val ventas = VentaRepo()
    private val insumos = InsRepo()
    private val perfil = PerfilRepo(Perfil(nombre = "Ana", apellidos = "Pérez"))
    private val reloj = RelojFijo()

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private fun venta(turnoId: Long, pesos: Long, metodo: MetodoPago) = Venta(
        turnoId = turnoId, fecha = T0, metodoPago = metodo,
        detalles = listOf(DetalleVenta(productoId = 1, nombre = "Refresco", categoria = "Bebidas", cantidad = 1,
            precioBase = Cup.ofPesos(pesos), precioUnitario = Cup.ofPesos(pesos), costoUnitario = Cup.ofPesos(pesos / 2))),
    )

    // ---------------- Registros → Turnos ----------------

    @Test fun registroVacioYLuegoConTurnosAbiertoPrimero() = runTest {
        val vm = TurnosViewModel(ObservarHistorialTurnos(turnos))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertTrue(vm.state.value is EstadoCarga.Vacio)
        turnos.turnos.value = listOf(Turno(1, T0.minusSeconds(7200), T0.minusSeconds(3600)), Turno(2, T0))
        val lista = (vm.state.value as EstadoCarga.Exito<List<Turno>>).datos
        assertEquals(listOf(2L, 1L), lista.map { it.id })
        vm.reintentar()
        assertTrue(vm.state.value is EstadoCarga.Exito)
    }

    // ---------------- Detalle ----------------

    private val exportador = cu.shalev.app.FakeExportador()
    private val archivos = cu.shalev.app.FakeArchivos()

    private fun detalleVm(id: Long) = TurnoDetalleViewModel(
        SavedStateHandle(mapOf(TurnoDetalleViewModel.KEY_ID to id)),
        ObtenerDetalleTurno(turnos, ventas, insumos),
        cu.shalev.domain.usecase.ExportarTurno(ObtenerDetalleTurno(turnos, ventas, insumos), exportador),
        archivos,
    ).apply { zona = java.time.ZoneOffset.UTC }

    // ---------------- 0.25.1: compartir el turno (PDF / Excel) ----------------

    @Test fun enviarPdfDelTurnoLlevaResumenArqueoYVentas() = runTest {
        turnos.abrir(T0, "Ana Pérez")
        ventas.registrar(venta(1, 100, MetodoPago.EFECTIVO))
        turnos.cerrar(T0.plusSeconds(3600), "Ana Pérez")
        val vm = detalleVm(1)
        val eventos = mutableListOf<EventoRegistros>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { eventos += it } }
        vm.abrirExportar()
        assertTrue(vm.exportacion.value.hoja)
        vm.enviar(cu.shalev.domain.service.FormatoExport.PDF)
        testScheduler.advanceUntilIdle()
        org.junit.Assert.assertFalse(vm.exportacion.value.hoja)
        org.junit.Assert.assertFalse(vm.exportacion.value.exportando)
        val e = eventos.last() as EventoRegistros.CompartirArchivo
        assertTrue(e.archivo.name, e.archivo.name.startsWith("Shalev_Turno_") && e.archivo.name.endsWith(".pdf"))
        val (tablas, formato) = exportador.llamadas.single()
        assertEquals(cu.shalev.domain.service.FormatoExport.PDF, formato)
        val titulos = tablas.map { it.titulo }
        assertTrue(titulos.toString(), titulos[0].startsWith("Turno del ") && !titulos[0].contains("provisional"))
        assertEquals("Arqueo de caja", titulos[1])
        assertTrue(titulos.toString(), titulos.any { it.startsWith("Ventas") })
    }

    @Test fun guardarExcelDelTurnoEnElDestinoElegido() = runTest {
        turnos.abrir(T0, "Ana Pérez")
        val vm = detalleVm(1)
        val eventos = mutableListOf<EventoRegistros>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { eventos += it } }
        vm.pedirGuardar(cu.shalev.domain.service.FormatoExport.XLSX)
        val g = eventos.last() as EventoRegistros.GuardarComo
        assertTrue(g.nombre, g.nombre.endsWith(".xlsx"))
        vm.guardarEn("content://doc/t")
        testScheduler.advanceUntilIdle()
        assertEquals("XLSX", archivos.bytes("content://doc/t").decodeToString())
        // Turno abierto: el título avisa que los datos son provisionales.
        assertTrue(exportador.llamadas.single().first[0].titulo.endsWith("(provisional)"))
        assertTrue((eventos.last() as EventoRegistros.Mensaje).texto.startsWith("Guardado: Shalev_Turno_"))
    }

    @Test fun exportarTurnoQueFallaAvisaSinCerrarLaApp() = runTest {
        turnos.abrir(T0, "Ana Pérez")
        exportador.fallar = true
        val vm = detalleVm(1)
        val eventos = mutableListOf<EventoRegistros>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { eventos += it } }
        vm.enviar(cu.shalev.domain.service.FormatoExport.PDF)
        testScheduler.advanceUntilIdle()
        assertEquals(TextosRegistros.ERROR_EXPORTAR, (eventos.last() as EventoRegistros.Mensaje).texto)
    }

    @Test fun turnoInexistenteNoAbreLaHojaDeExportar() = runTest {
        val vm = detalleVm(99)
        vm.abrirExportar()
        org.junit.Assert.assertFalse(vm.exportacion.value.hoja)
    }

    @Test fun detalleDeTurnoCerradoNoSeRecarga() = runTest {
        turnos.abrir(T0, "Ana Pérez")
        ventas.registrar(venta(1, 100, MetodoPago.EFECTIVO))
        turnos.cerrar(T0.plusSeconds(3600), "Ana Pérez")
        val vm = detalleVm(1)
        val d = (vm.state.value as EstadoCarga.Exito<DetalleTurno>).datos
        assertEquals("Ana Pérez", d.turno.abiertoPor)
        assertEquals("Ana Pérez", d.turno.cerradoPor)
        assertEquals(1, d.ventas.size)
        ventas.fallar = true // un cerrado no se vuelve a leer al volver a la pantalla
        vm.refrescar()
        assertTrue(vm.state.value is EstadoCarga.Exito)
    }

    @Test fun detalleDeTurnoAbiertoEsProvisionalYSeRefresca() = runTest {
        turnos.abrir(T0, "Ana")
        val vm = detalleVm(1)
        val d1 = (vm.state.value as EstadoCarga.Exito<DetalleTurno>).datos
        assertTrue(d1.provisional)
        assertEquals(0, d1.resumen.numVentas)
        ventas.registrar(venta(1, 100, MetodoPago.EFECTIVO))
        ventas.registrar(venta(1, 50, MetodoPago.TRANSFERENCIA))
        vm.refrescar()
        val r = (vm.state.value as EstadoCarga.Exito<DetalleTurno>).datos.resumen
        assertEquals(2, r.numVentas)
        assertEquals(Cup.ofPesos(100), r.totalEfectivo)
        assertEquals(1, r.ventasTransferencia)
    }

    @Test fun detalleInexistenteOErrorDeLectura() = runTest {
        assertEquals(EstadoCarga.Error(TextosTurno.NO_ENCONTRADO), detalleVm(99).state.value)
        turnos.abrir(T0, "Ana")
        ventas.fallar = true
        val vm = detalleVm(1)
        assertEquals(EstadoCarga.Error(TextosTurno.ERROR_CARGA), vm.state.value)
        ventas.fallar = false
        vm.reintentar()
        assertTrue(vm.state.value is EstadoCarga.Exito)
    }

    // ---------------- Bloqueo de venta ----------------

    private fun ventaVm(): VentaViewModel {
        val productos = cu.shalev.app.ProdRepo()
        val insumos = cu.shalev.app.InsRepo()
        val cotizar = cu.shalev.domain.usecase.CotizarVenta(productos, cu.shalev.app.PreciosRepo(), insumos, cu.shalev.app.ServRepo())
        return VentaViewModel(
            androidx.lifecycle.SavedStateHandle(), ObservarPermisoVenta(turnos), productos,
            cu.shalev.domain.usecase.ObservarElaborados(insumos, productos, Dispatchers.Unconfined), insumos,
            cu.shalev.domain.usecase.ObservarServicios(cu.shalev.app.ServRepo(), insumos, Dispatchers.Unconfined), perfil, AbrirTurno(turnos, UsuarioActual(perfil), reloj), cotizar,
            cu.shalev.domain.usecase.RegistrarVenta(cotizar, turnos, cu.shalev.app.VentaRepo(), perfil, reloj),
            cu.shalev.domain.usecase.ExtraerNumeroTransaccion(), cu.shalev.app.common.EntradaCompartida(),
            sesion = cu.shalev.domain.service.SesionVenta(), secundaria = cu.shalev.app.SecundariaRepoFake(),
            clientesFijos = cu.shalev.app.ClientesFijosRepo(),
        )
    }

    private val VentaViewModel.mensajes
        get() = eventos.filterIsInstance<cu.shalev.app.venta.EventoVenta.Mensaje>().map { it.texto }

    @Test fun sinTurnoLaVentaEstaBloqueadaYSePuedeAbrirDesdeAhi() = runTest {
        val vm = ventaVm(); val msgs = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.mensajes.collect { msgs += it } }
        assertTrue(vm.state.value.bloqueada)
        vm.abrirTurno(Cup.ZERO)
        val p = vm.state.value.permiso
        assertTrue(p is Permiso.Permitido)
        assertEquals("Ana Pérez", (p as Permiso.Permitido).turno.abiertoPor)
        assertEquals(TextosTurno.TURNO_ABIERTO, msgs.last())
    }

    @Test fun siElTurnoSeCierraMientrasVendeSeBloqueaAlInstante() = runTest {
        turnos.abrir(T0, "Ana")
        val vm = ventaVm()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        assertTrue(vm.state.value.permiso is Permiso.Permitido)
        turnos.cerrar(T0.plusSeconds(60), "Ana")
        assertTrue(vm.state.value.bloqueada)
    }

    @Test fun errorAlAbrirDaMensajeGenerico() = runTest {
        turnos.fallarAbrir = true
        val vm = ventaVm(); val msgs = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.mensajes.collect { msgs += it } }
        vm.abrirTurno(Cup.ZERO)
        assertTrue(vm.state.value.bloqueada)
        assertEquals(false, vm.state.value.abriendo)
        assertEquals(TextosTurno.ERROR_ABRIR, msgs.last())
    }
}
