package cu.shalev.app.inicio

import org.junit.Assert.assertFalse
import androidx.lifecycle.SavedStateHandle
import cu.shalev.app.InsRepo
import cu.shalev.app.LicRepo
import cu.shalev.app.PerfilRepo
import cu.shalev.app.PreciosRepo
import cu.shalev.app.PrefRepo
import cu.shalev.app.ProdRepo
import cu.shalev.app.RelojFijo
import cu.shalev.app.T0
import cu.shalev.app.TurnoRepo
import cu.shalev.app.VentaRepo
import cu.shalev.app.navigation.Route
import cu.shalev.app.prod
import cu.shalev.core.money.Cup
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.GraficosPeriodo
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.OpcionPeriodo
import cu.shalev.domain.model.Periodo
import cu.shalev.domain.model.ResumenGeneral
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.Venta
import cu.shalev.domain.usecase.AbrirTurno
import cu.shalev.domain.usecase.CerrarTurno
import cu.shalev.domain.usecase.ObservarAlertas
import cu.shalev.domain.usecase.ObtenerGraficosPeriodo
import cu.shalev.domain.usecase.ObtenerResumenGeneral
import cu.shalev.domain.usecase.PeriodoPorDefecto
import cu.shalev.domain.usecase.RangoDePreset
import cu.shalev.domain.usecase.ResolverPeriodo
import cu.shalev.domain.usecase.UsuarioActual
import cu.shalev.licencia.LicenseState
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
class InicioViewModelTest {

    private val reloj = RelojFijo()
    private val lic = LicRepo()
    private val productos = ProdRepo()
    private val insumos = InsRepo()
    private val prefs = PrefRepo()
    private val perfil = PerfilRepo()
    private val precios = PreciosRepo()
    private val turnos = TurnoRepo()
    private val ventas = VentaRepo()
    private val saved = SavedStateHandle()

    private fun vm() = InicioViewModel(
        saved, lic, ObservarAlertas(productos, insumos, prefs, reloj, Dispatchers.Unconfined), perfil,
        precios, turnos, productos,
        ResolverPeriodo(PeriodoPorDefecto(turnos, reloj), RangoDePreset(reloj)),
        ObtenerGraficosPeriodo(ventas, turnos, reloj, Dispatchers.Unconfined),
        ObtenerResumenGeneral(ventas, productos, insumos, cu.shalev.app.ServRepo(), reloj, Dispatchers.Unconfined),
        AbrirTurno(turnos, UsuarioActual(perfil), reloj), CerrarTurno(turnos, UsuarioActual(perfil), reloj), reloj,
        secundaria, cu.shalev.app.FakeEstadoPantallas(),
    )

    private val secundaria = cu.shalev.app.SecundariaRepoFake()

    /** Suscribe la UI (el state es WhileSubscribed) y recoge los eventos de un solo uso. */
    private fun TestScope.conUi(vm: InicioViewModel): MutableList<EventoInicio> {
        val eventos = mutableListOf<EventoInicio>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { eventos += it } }
        return eventos
    }

    private fun venta(turnoId: Long, pesos: Long, costo: Long, metodo: MetodoPago = MetodoPago.EFECTIVO, productoId: Long = 1, unidades: Long = 1) = Venta(
        turnoId = turnoId, fecha = reloj.ahora, metodoPago = metodo,
        detalles = listOf(DetalleVenta(productoId = productoId, nombre = "P$productoId", categoria = "Bebidas", cantidad = unidades,
            precioBase = Cup.ofPesos(pesos), precioUnitario = Cup.ofPesos(pesos), costoUnitario = Cup.ofPesos(costo))),
    )

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    @Test fun sinDatosTodoVacioYBannerDePrueba() = runTest {
        val vm = vm(); conUi(vm)
        val s = vm.state.value
        assertEquals("Periodo de prueba restante: 5 días", s.banner)
        assertTrue(s.alertas.isEmpty())
        assertTrue(s.graficos is EstadoCarga.Vacio)
        assertTrue((s.graficos as EstadoCarga.Vacio<GraficosPeriodo>).datos!!.sinTurnos)
        assertTrue(s.resumen is EstadoCarga.Vacio)
        assertEquals(false, s.turnoAbierto)
        assertEquals(OpcionPeriodo.TURNO, s.opcion)
    }

    /** 0.20.0 (H5): en una secundaria con el cierre pedido no se empieza otra venta; tras el cierre, aviso descartable. */
    @Test fun cierrePedidoBloqueaNuevasVentasYAvisaAlCerrar() = runTest {
        val vm = vm(); conUi(vm)
        assertTrue(vm.state.value.puedeEmpezarVenta)
        secundaria.flow.value = secundaria.flow.value.copy(cierrePendiente = true)
        assertTrue(vm.state.value.cierrePedido)
        assertFalse(vm.state.value.puedeEmpezarVenta)
        secundaria.flow.value = secundaria.flow.value.copy(cierrePendiente = false, turnoCerradoPorPrincipal = true)
        assertTrue(vm.state.value.avisoCierre)
        assertTrue(vm.state.value.puedeEmpezarVenta)
        vm.descartarAvisoCierre()
        assertFalse(vm.state.value.avisoCierre)
    }

    /** 0.25.1 (D): «Ahora no» oculta el conteo pedido sin cerrar nada; vuelve con «Contar» o al regresar a Inicio. */
    @Test fun conteoPedidoSePuedePosponer() = runTest {
        secundaria.flow.value = secundaria.flow.value.copy(vinculada = true)
        val vm = vm(); conUi(vm)
        vm.cambiarTurno(abrir = true)
        vm.abrirTurnoCon(Cup.ofPesos(100))
        secundaria.flow.value = secundaria.flow.value.copy(cierrePendiente = true)
        assertTrue(vm.state.value.debeContar)
        vm.posponerConteo()
        assertFalse(vm.state.value.debeContar)
        assertTrue(vm.state.value.pideConteo)
        assertTrue(vm.state.value.turnoAbierto) // nada se cierra solo
        vm.contarAhora()
        assertTrue(vm.state.value.debeContar)
        vm.posponerConteo()
        vm.refrescar() // volver a Inicio
        assertTrue(vm.state.value.debeContar)
        assertEquals(15L, InicioViewModel.POSPONER_CONTEO.inWholeMinutes)
    }

    /** 0.21.0 (C6, P46 «a»): el empleado no cierra su turno: lo solicita una vez y espera la aprobación del dueño. */
    @Test fun secundariaSoloSolicitaElCierre() = runTest {
        secundaria.flow.value = secundaria.flow.value.copy(vinculada = true)
        val vm = vm(); val eventos = conUi(vm)
        vm.cambiarTurno(abrir = true)
        assertEquals(DialogoInicio.AbrirTurno, vm.state.value.dialogo) // 0.25.0: primero el fondo
        vm.abrirTurnoCon(Cup.ZERO)
        assertTrue(vm.state.value.turnoAbierto)
        vm.cambiarTurno(abrir = false)
        assertEquals(DialogoInicio.SolicitarCierre, vm.state.value.dialogo)
        vm.confirmarSolicitudCierre(Cup.ofPesos(150))
        assertNull(vm.state.value.dialogo)
        assertTrue(vm.state.value.cierreSolicitado)
        assertEquals(Cup.ofPesos(150), secundaria.contadoDeclarado) // 0.25.0: el conteo viaja con la solicitud
        assertTrue(vm.state.value.turnoAbierto) // sigue vendiendo hasta que el dueño apruebe
        assertEquals(EventoInicio.Mensaje(TextosInicio.SOLICITUD_ENVIADA), eventos.last())
        vm.cambiarTurno(abrir = false) // segunda vez: solo informa
        assertNull(vm.state.value.dialogo)
        assertEquals(EventoInicio.Mensaje(TextosInicio.CIERRE_SOLICITADO), eventos.last())
    }

    @Test fun perpetuaOcultaElBanner() = runTest {
        lic.flow.value = lic.flow.value!!.copy(estado = LicenseState.Perpetual("L1"))
        val vm = vm(); conUi(vm)
        assertNull(vm.state.value.banner)
    }

    @Test fun alertasConContadorYLasDeCeroOcultas() = runTest {
        productos.items.value = listOf(prod(1, cantidad = 0), prod(2, cantidad = 3), prod(3, cantidad = 50))
        val vm = vm(); conUi(vm)
        val a = vm.state.value.alertas
        // 0.21.0 (C1): el producto a 0 cuenta como «Sin existencia», no como «Stock crítico» (que queda a 0 y se oculta).
        assertEquals(listOf(TipoAlerta.SIN_EXISTENCIA to 1, TipoAlerta.STOCK_BAJO to 1), a.map { it.tipo to it.cantidad })
        productos.items.value = listOf(prod(3, cantidad = 50))
        assertTrue(vm.state.value.alertas.isEmpty())
    }

    @Test fun abrirTurnoYVenderActualizaElGrafico() = runTest {
        val vm = vm(); val eventos = conUi(vm)
        vm.cambiarTurno(abrir = true)
        assertEquals(DialogoInicio.AbrirTurno, vm.state.value.dialogo) // 0.25.0: primero el fondo
        vm.abrirTurnoCon(Cup.ZERO)
        assertTrue(vm.state.value.turnoAbierto)
        assertEquals(EventoInicio.Mensaje("Turno abierto. Ya puedes vender."), eventos.last())
        ventas.registrar(venta(1, 300, 100))
        vm.refrescar()
        val g = vm.state.value.graficos
        assertTrue(g is EstadoCarga.Exito)
        g as EstadoCarga.Exito<GraficosPeriodo>
        assertEquals(Periodo.DeTurno(1), g.datos.periodo)
        assertEquals(Cup.ofPesos(300), g.datos.totalVentas)
        assertEquals(Cup.ofPesos(200), g.datos.ganancia)
    }

    @Test fun cerrarTurnoPideConfirmacionYResume() = runTest {
        turnos.abrir(T0.minusSeconds(3600), "Ana")
        val vm = vm(); val eventos = conUi(vm)
        vm.cambiarTurno(abrir = false)
        assertEquals(DialogoInicio.CerrarTurno, vm.state.value.dialogo)
        assertTrue("sigue abierto hasta confirmar", vm.state.value.turnoAbierto)
        vm.confirmarCierreTurno(Cup.ZERO)
        assertNull(vm.state.value.dialogo)
        assertEquals(false, vm.state.value.turnoAbierto)
        assertEquals(EventoInicio.TurnoCerrado(1, "Turno cerrado: 2 ventas, 300 CUP."), eventos.last())
        // El gráfico pasa a mostrar el último turno cerrado.
        val g = vm.state.value.graficos
        val datos = when (g) { is EstadoCarga.Vacio -> g.datos; is EstadoCarga.Exito -> g.datos; else -> null }
        assertEquals(Periodo.DeTurno(1), datos!!.periodo)
    }

    @Test fun nuevaVentaNoAbreElTurnoImplicitamente() = runTest {
        val vm = vm(); val eventos = conUi(vm)
        vm.nuevaVenta()
        assertEquals(DialogoInicio.NuevaVenta, vm.state.value.dialogo)
        vm.elegirVenta(TipoVenta.SERVICIO)
        assertNull(vm.state.value.dialogo)
        // Prompt 7: con turno cerrado se navega a Venta, que muestra el bloqueo; el turno sigue cerrado.
        assertEquals(false, vm.state.value.turnoAbierto)
        assertTrue(turnos.turnos.value.isEmpty())
        assertEquals(EventoInicio.IrA(Route.Venta("SERVICIO")), eventos.last())
    }

    @Test fun errorDeCargaGenericoYReintento() = runTest {
        ventas.fallar = true
        val vm = vm(); conUi(vm)
        assertEquals(EstadoCarga.Error(TextosInicio.ERROR_CARGA), vm.state.value.graficos)
        assertEquals(EstadoCarga.Error(TextosInicio.ERROR_CARGA), vm.state.value.resumen)
        ventas.fallar = false
        vm.refrescar()
        assertTrue(vm.state.value.graficos is EstadoCarga.Vacio)
        assertTrue(vm.state.value.resumen is EstadoCarga.Vacio)
    }

    @Test fun periodoElegidoSeConservaYRecarga() = runTest {
        turnos.abrir(T0.minusSeconds(3600), "Ana")
        ventas.registrar(venta(1, 100, 40))
        val vm = vm(); conUi(vm)
        vm.elegirPeriodo(OpcionPeriodo.SEMANA)
        assertEquals(OpcionPeriodo.SEMANA, vm.state.value.opcion)
        assertEquals("SEMANA", saved.get<String>(InicioViewModel.KEY_PERIODO))
        val g = vm.state.value.graficos as EstadoCarga.Exito<GraficosPeriodo>
        assertTrue(g.datos.periodo is Periodo.Rango)
        assertEquals(Cup.ofPesos(100), g.datos.totalVentas)
    }

    @Test fun resumenConTop3YMetodosDePago() = runTest {
        productos.items.value = listOf(prod(1, "Refresco", cantidad = 20), prod(2, "Pan", cantidad = 20, categoria = "Panadería"))
        turnos.abrir(T0.minusSeconds(3600), "Ana")
        ventas.registrar(venta(1, 100, 60, productoId = 1, unidades = 5))
        ventas.registrar(venta(1, 50, 20, MetodoPago.TRANSFERENCIA, productoId = 2, unidades = 1))
        val vm = vm(); conUi(vm)
        val r = (vm.state.value.resumen as EstadoCarga.Exito<ResumenGeneral>).datos
        assertEquals("Refresco", r.top3.masVendidos.first().nombre)
        assertEquals(setOf("Efectivo", "Transferencia"), r.metodosPago.map { it.etiqueta }.toSet())
        assertEquals(setOf("Bebidas", "Panadería"), r.categorias.map { it.etiqueta }.toSet())
    }
}
