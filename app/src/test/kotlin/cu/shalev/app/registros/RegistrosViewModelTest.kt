package cu.shalev.app.registros

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import cu.shalev.app.FakeArchivos
import cu.shalev.app.FakeExportador
import cu.shalev.app.InsRepo
import cu.shalev.app.RelojFijo
import cu.shalev.app.T0
import cu.shalev.app.VentaRepo
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.FiltroRegistros
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.PeriodoRegistro
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.UnidadMedida
import cu.shalev.domain.model.VistaRegistro
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.usecase.ExportarTablas
import cu.shalev.domain.usecase.ObservarRegistro
import cu.shalev.domain.usecase.TipoRegistro
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrosViewModelTest {
    private val repo = RegRepo()
    private val insumos = InsRepo()
    private val ventas = VentaRepo()
    private val reloj = RelojFijo() // 30/09/2026 12:00 en La Habana
    private val eventos = mutableListOf<EventoRegistros>()
    private val exportador = FakeExportador()
    private val archivos = FakeArchivos()
    /** El mismo objeto entre dos ViewModels simula que el sistema restaura el estado tras cerrar el proceso. */
    private val saved = SavedStateHandle()

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private fun TestScope.vm(): RegistrosViewModel {
        val vm = RegistrosViewModel(ObservarRegistro(repo, insumos, reloj, Dispatchers.Unconfined), ventas, ExportarTablas(exportador), archivos, reloj, saved, cu.shalev.app.FakeEstadoPantallas())
        vm.zona = ZoneId.of("America/Havana")
        // 0.30.0 (F1): retardo del buscador a 0 para no depender del reloj virtual.
        vm.debounceBusqueda = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { eventos += it } }
        return vm
    }

    private fun ids(vm: RegistrosViewModel): List<Long> = when (val d = vm.state.value.datos) {
        is VistaRegistro.Ventas -> d.items.map { it.id }
        is VistaRegistro.Transferencias -> d.items.map { it.id }
        is VistaRegistro.Movimientos -> d.items.map { it.movimiento.id }
        null -> emptyList()
    }

    private fun cargar() {
        repo.ventas.value = listOf(
            venta(1, 100, minutos = -60 * 24 * 3, nombre = "Café molido"), // hace 3 días
            venta(2, 500, nombre = "Pan"),
            venta(3, 50, metodo = MetodoPago.TRANSFERENCIA),
        )
        repo.transacciones.value = listOf(transferencia(3, 50), transferencia(4, 2000, minutos = -60 * 24 * 40, cliente = "José Pérez"))
        repo.movimientos.value = listOf(
            movimiento(1, "Pan", 4, tipo = TipoMovimiento.PRODUCCION),
            movimiento(2, "Harina", -500, entidad = TipoEntidad.INSUMO, tipo = TipoMovimiento.CONSUMO),
        )
    }

    @Test fun vacioSinRegistrosYOrdenCronologico() = runTest {
        val vm = vm()
        assertTrue(vm.state.value.vista is EstadoCarga.Vacio)
        assertFalse(vm.state.value.puedeCompartir)
        cargar()
        assertEquals(listOf(3L, 2L, 1L), ids(vm)) // más reciente primero
        assertEquals("3 ventas · 650.00 CUP", TextosRegistros.resumen(vm.state.value.datos!!))
    }

    @Test fun cadaTablaTieneSuBuscadorYSuFiltro() = runTest {
        cargar()
        val vm = vm()
        vm.buscar("CAFE")
        assertEquals(listOf(1L), ids(vm))
        vm.pestana(PestanaRegistros.TRANSFERENCIAS)
        assertEquals(listOf(3L, 4L), ids(vm)) // la búsqueda de Ventas no afecta a Transferencias
        vm.buscar("perez")
        assertEquals(listOf(4L), ids(vm))
        vm.pestana(PestanaRegistros.VENTAS)
        assertEquals("CAFE", vm.state.value.filtro.texto) // se conserva al volver
        assertEquals(listOf(1L), ids(vm))
    }

    @Test fun filtroPorFechaEImporte() = runTest {
        cargar()
        val vm = vm()
        vm.aplicarFiltro(FiltroRegistros(periodo = PeriodoRegistro.HOY))
        assertEquals(listOf(3L, 2L), ids(vm))
        assertEquals(Instant.parse("2026-09-30T04:00:00Z"), repo.consultas.last().desde)
        vm.aplicarFiltro(FiltroRegistros(periodo = PeriodoRegistro.HOY, importeMin = Cup.ofPesos(100)))
        assertEquals(listOf(2L), ids(vm))
        assertEquals(2, vm.state.value.filtro.activos)
        vm.quitarFiltros()
        assertEquals(listOf(3L, 2L, 1L), ids(vm))
    }

    @Test fun rangoPersonalizadoIncluyeElUltimoDia() = runTest {
        cargar()
        val vm = vm()
        vm.pestana(PestanaRegistros.TRANSFERENCIAS)
        vm.aplicarFiltro(FiltroRegistros(periodo = PeriodoRegistro.PERSONALIZADO, desde = LocalDate.of(2026, 8, 1), hasta = LocalDate.of(2026, 8, 21)))
        assertEquals(listOf(4L), ids(vm)) // 21/08 12:00 (40 días antes) entra aunque «hasta» sea ese mismo día
    }

    @Test fun movimientosSoloPorFechaYConUnidad() = runTest {
        cargar()
        insumos.items.value = listOf(Insumo(id = 2, nombre = "Harina", unidad = UnidadMedida.KILOGRAMO, precio = Cup.ofPesos(1), cantidad = Cantidad.ZERO, creadoEn = T0))
        val vm = vm()
        vm.pestana(PestanaRegistros.MOVIMIENTOS)
        vm.aplicarFiltro(FiltroRegistros(importeMin = Cup.ofPesos(999_999)))
        assertEquals(listOf(2L, 1L), ids(vm)) // el importe no se aplica a movimientos
        assertNull(repo.consultas.last().importeMin)
        val harina = (vm.state.value.datos as VistaRegistro.Movimientos).items.first()
        assertEquals("-0.5 kg", valorFila(harina))
    }

    @Test fun sinResultadosNoEsVacioYQuitarFiltrosVaciaElBuscador() = runTest {
        cargar()
        val vm = vm()
        vm.buscar("zzz")
        assertTrue(vm.state.value.vista is EstadoCarga.Exito) // «Sin resultados», no «Aún no hay ventas»
        assertEquals(0, vm.state.value.datos!!.cantidad)
        vm.quitarFiltros(tambienTexto = true)
        assertEquals("", vm.state.value.filtro.texto)
        assertEquals(3, vm.state.value.datos!!.cantidad)
    }

    @Test fun tocarUnaFilaAbreSuVentana() = runTest {
        cargar()
        val vm = vm()
        vm.abrirVenta(2)
        assertEquals(2L, (vm.state.value.ficha as FichaRegistro.DeVenta).venta.id)
        vm.cerrarFicha()
        assertNull(vm.state.value.ficha)
        vm.abrirVenta(99) // ya no está en la lista: no abre nada
        assertNull(vm.state.value.ficha)
    }

    @Test fun transferenciaLlevaASuVenta() = runTest {
        cargar()
        ventas.ventas += venta(3, 50, metodo = MetodoPago.TRANSFERENCIA)
        val vm = vm()
        vm.pestana(PestanaRegistros.TRANSFERENCIAS)
        vm.abrirTransferencia(3)
        vm.verVentaDeTransferencia()
        assertEquals(3L, (vm.state.value.ficha as FichaRegistro.DeVenta).venta.id)

        vm.abrirTransferencia(4) // su venta no existe en este teléfono
        vm.verVentaDeTransferencia()
        assertEquals(TextosRegistros.VENTA_NO_DISPONIBLE, (eventos.last() as EventoRegistros.Mensaje).texto)
    }

    @Test fun laTablaExportadaIncluyeFiltroYTotal() = runTest {
        cargar()
        val vm = vm()
        vm.aplicarFiltro(FiltroRegistros(periodo = PeriodoRegistro.HOY))
        vm.buscar("pan")
        vm.enviar(FormatoExport.PDF)
        val tabla = exportador.llamadas.last().first.single()
        assertEquals("Ventas (Hoy · búsqueda «pan»)", tabla.titulo)
        assertEquals("Total: 500.00 CUP", tabla.pie.last())
    }

    // ---------------- Exportar PDF / Excel (Prompt 14) ----------------

    @Test fun enviarExcelDeLoQueSeVeConFiltroYSinTopeDeFilas() = runTest {
        cargar()
        val vm = vm()
        vm.aplicarFiltro(FiltroRegistros(periodo = PeriodoRegistro.HOY))
        vm.buscar("pan")
        vm.abrirExportar()
        assertTrue(vm.state.value.hojaExportar)
        vm.enviar(FormatoExport.XLSX)
        assertFalse(vm.state.value.hojaExportar)
        assertFalse(vm.state.value.exportando)
        val (tablas, formato) = exportador.llamadas.single()
        assertEquals(FormatoExport.XLSX, formato)
        assertEquals("Ventas (Hoy · búsqueda «pan»)", tablas.single().titulo)
        assertEquals(vm.state.value.datos!!.cantidad, tablas.single().filas.size)
        val e = eventos.last() as EventoRegistros.CompartirArchivo
        assertEquals("Shalev_ventas_2026-09-30.xlsx", e.archivo.name)
        assertEquals(FormatoExport.XLSX.mime, e.mime)
        assertEquals("XLSX", e.archivo.readText())
    }

    @Test fun guardarPdfPideDestinoEscribeYConfirma() = runTest {
        cargar()
        val vm = vm()
        vm.pestana(PestanaRegistros.MOVIMIENTOS)
        vm.pedirGuardar(FormatoExport.PDF)
        val g = eventos.last() as EventoRegistros.GuardarComo
        assertEquals("Shalev_movimientos_2026-09-30.pdf", g.nombre)
        assertEquals(FormatoExport.PDF.mime, g.mime)
        vm.guardarEn("content://doc/1")
        assertEquals("PDF", archivos.destinos.getValue("content://doc/1").toString())
        assertEquals(EventoRegistros.Mensaje("Guardado: Shalev_movimientos_2026-09-30.pdf"), eventos.last())
        // Cancelar el selector, o una URI sin petición pendiente, no escribe nada.
        vm.pedirGuardar(FormatoExport.XLSX); vm.guardarEn(null); vm.guardarEn("content://tarde")
        assertEquals(1, archivos.destinos.size)
    }

    @Test fun losDocumentosDeTransferenciasLlevanElCarneCompleto() = runTest {
        cargar()
        val vm = vm()
        vm.pestana(PestanaRegistros.TRANSFERENCIAS)
        vm.enviar(FormatoExport.PDF)
        assertTrue(exportador.llamadas.single().first.single().filas.flatten().contains("90020212345"))
    }

    @Test fun errorAlExportarSeAvisaYBorraElTemporal() = runTest {
        cargar()
        exportador.fallar = true
        val vm = vm()
        vm.enviar(FormatoExport.PDF)
        assertEquals(EventoRegistros.Mensaje(TextosRegistros.ERROR_EXPORTAR), eventos.last())
        assertTrue(archivos.dir.listFiles().orEmpty().isEmpty())
    }

    @Test fun sinFilasNoSeExporta() = runTest {
        val vm = vm() // repositorio vacío
        vm.abrirExportar(); vm.enviar(FormatoExport.PDF); vm.pedirGuardar(FormatoExport.PDF)
        assertFalse(vm.state.value.hojaExportar)
        assertTrue(exportador.llamadas.isEmpty())
        assertTrue(eventos.isEmpty())
    }

    @Test fun textosDeLaHojaExportar() {
        assertEquals(
            "Lo que ves: 1 venta (Hoy)",
            TextosRegistros.alcanceExportar(TipoRegistro.VENTAS, 1, "Hoy"),
        )
        assertEquals("Shalev_transferencias_2026-09-30.pdf", TextosRegistros.nombreArchivo(TipoRegistro.TRANSACCIONES, LocalDate.of(2026, 9, 30), "pdf"))
        assertNull(detalleFiltro(RegistrosUiState()))
    }

    @Test fun turnosNoConsultaRegistrosNiComparte() = runTest {
        cargar()
        val vm = vm()
        val antes = repo.consultas.size
        vm.pestana(PestanaRegistros.TURNOS)
        assertEquals(antes, repo.consultas.size)
        assertNull(vm.state.value.tipo)
        vm.abrirFiltro(); vm.enviar(FormatoExport.PDF)
        assertFalse(vm.state.value.hojaFiltro)
        assertEquals(0, eventos.size)
    }

    @Test fun errorDeCargaYReintentar() = runTest {
        repo.fallar = true
        val vm = vm()
        assertEquals(TextosRegistros.ERROR_CARGA, (vm.state.value.vista as EstadoCarga.Error).mensaje)
        repo.fallar = false
        cargar()
        vm.reintentar()
        assertEquals(3, vm.state.value.datos!!.cantidad)
    }

    @Test fun abrirYCerrarLaHojaDeFiltro() = runTest {
        val vm = vm()
        vm.abrirFiltro()
        assertTrue(vm.state.value.hojaFiltro)
        vm.aplicarFiltro(FiltroRegistros(texto = "ignorado", periodo = PeriodoRegistro.SIETE_DIAS))
        assertFalse(vm.state.value.hojaFiltro)
        assertEquals("", vm.state.value.filtro.texto) // el buscador no lo cambia la hoja
        assertEquals(TipoRegistro.VENTAS, vm.state.value.tipo)
    }

    // P18 (A12): filtros persistentes.
    @Test fun pestanaBusquedaYFiltroSobrevivenAlCierreDelProceso() = runTest {
        cargar()
        val a = vm()
        a.pestana(PestanaRegistros.MOVIMIENTOS)
        a.buscar("café")
        a.pestana(PestanaRegistros.VENTAS)
        a.aplicarFiltro(FiltroRegistros(periodo = PeriodoRegistro.HOY, importeMin = Cup.ofPesos(100)))
        a.viewModelScope.cancel()

        val b = vm()
        assertEquals(PestanaRegistros.VENTAS, b.state.value.pestana)
        assertEquals(PeriodoRegistro.HOY, b.state.value.filtro.periodo)
        assertEquals(Cup.ofPesos(100), b.state.value.filtro.importeMin)
        b.pestana(PestanaRegistros.MOVIMIENTOS)
        assertEquals("café", b.state.value.filtro.texto)
        b.viewModelScope.cancel()
    }

}
