package cu.shalev.app.venta

import androidx.lifecycle.SavedStateHandle
import cu.shalev.app.InsRepo
import cu.shalev.app.PerfilRepo
import cu.shalev.app.PreciosRepo
import cu.shalev.app.ProdRepo
import cu.shalev.app.RelojFijo
import cu.shalev.app.T0
import cu.shalev.app.TurnoRepo
import cu.shalev.app.VentaRepo
import cu.shalev.app.common.Entrada
import cu.shalev.app.common.EntradaCompartida
import cu.shalev.app.inicio.TipoVenta
import cu.shalev.app.prod
import cu.shalev.core.money.Cup
import cu.shalev.core.result.AppError
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.service.PagoQr
import cu.shalev.domain.usecase.AbrirTurno
import cu.shalev.domain.usecase.CotizarVenta
import cu.shalev.domain.usecase.ExtraerNumeroTransaccion
import cu.shalev.domain.usecase.ObservarPermisoVenta
import cu.shalev.domain.usecase.RegistrarVenta
import cu.shalev.domain.usecase.UsuarioActual
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VentaViewModelTest {
    private val reloj = RelojFijo()
    private val insumos = InsRepo()
    private val servicios = cu.shalev.app.ServRepo()
    private val productos = ProdRepo().also { it.items.value = listOf(prod(1, "Refresco", cantidad = 5, venta = 100), prod(2, "Galletas", cantidad = 2, venta = 50, costo = 20)) }
    private val turnos = TurnoRepo()
    private val perfil = PerfilRepo(
        Perfil(
            nombre = "Ana", tarjetas = listOf(TarjetaBancaria(1, "9248129970876454")), telefonos = listOf(Telefono(2, "+5351815604")),
            pagoTarjetaId = 1, pagoTelefonoId = 2,
        ),
    )
    private val ventas = VentaRepo()
    private val entrada = EntradaCompartida()
    private val clientes = cu.shalev.app.ClientesFijosRepo(
        listOf(
            cu.shalev.domain.model.ClienteFijo(
                nombreApellidos = "María Pérez", ci = "85010112345", telefono = "+5351234567",
                creadoEn = java.time.Instant.EPOCH, actualizadoEn = java.time.Instant.EPOCH,
            ),
        ),
    )
    private val eventos = mutableListOf<EventoVenta>()

    private val sms = """
        Banco Popular de Ahorro:  La Transferencia fue completada.
         Monto: 540 CUP
         Nro. Transaccion: BR601ADLM8997
    """.trimIndent()
    private val pagoSms = ExtraerNumeroTransaccion().detalle(sms)!!

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private fun TestScope.vm(saved: Map<String, Any?> = mapOf(VentaViewModel.KEY_TIPO to "VENTA")): VentaViewModel {
        val insumos = this@VentaViewModelTest.insumos
        val cotizar = CotizarVenta(productos, PreciosRepo(), insumos, servicios)
        val vm = VentaViewModel(
            saved = SavedStateHandle(saved),
            observarPermiso = ObservarPermisoVenta(turnos),
            productosRepo = productos,
            observarElaborados = cu.shalev.domain.usecase.ObservarElaborados(insumos, productos, Dispatchers.Unconfined),
            insumosRepo = insumos,
            observarServicios = cu.shalev.domain.usecase.ObservarServicios(servicios, insumos, Dispatchers.Unconfined),
            perfilRepo = perfil,
            abrirTurno = AbrirTurno(turnos, UsuarioActual(perfil), reloj),
            cotizar = cotizar,
            registrar = RegistrarVenta(cotizar, turnos, ventas, perfil, reloj),
            extraer = ExtraerNumeroTransaccion(),
            entrada = entrada,
            sesion = sesion,
            secundaria = secundaria,
            clientesFijos = clientes,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { eventos += it } }
        return vm
    }

    private val sesion = cu.shalev.domain.service.SesionVenta()
    private val secundaria = cu.shalev.app.SecundariaRepoFake()

    private suspend fun abrirTurno() { turnos.abrir(T0, "Ana") }

    // ---------------- Turno ----------------

    @Test fun sinTurnoSeBloqueaYNoSeRegistra() = runTest {
        val vm = vm()
        vm.recibirSeleccion(listOf(1))
        assertTrue(vm.state.value.bloqueada)
        vm.continuar()
        assertEquals(PasoVenta.CARRITO, vm.state.value.paso)   // no avanza sin turno
        vm.confirmarEfectivo()
        assertTrue(ventas.ventas.isEmpty())
        assertTrue(eventos.none { it is EventoVenta.ElegirProductos })  // sin turno no se abre el Inventario
    }

    @Test fun abrirTurnoDesdeLaVentaYLuegoVaAlInventario() = runTest {
        val vm = vm()
        vm.abrirTurno(Cup.ZERO)
        assertFalse(vm.state.value.bloqueada)
        assertEquals(EventoVenta.ElegirProductos(TipoVenta.VENTA, emptyList()), eventos.first { it is EventoVenta.ElegirProductos })
    }

    @Test fun conTurnoAbreElInventarioUnaSolaVez() = runTest {
        abrirTurno()
        vm()
        assertEquals(1, eventos.count { it is EventoVenta.ElegirProductos })
        eventos.clear()
        vm(mapOf(VentaViewModel.KEY_TIPO to "VENTA", VentaViewModel.KEY_AUTO to true))  // p. ej. tras girar/recrear
        assertTrue(eventos.none { it is EventoVenta.ElegirProductos })
    }

    @Test fun turnoCerradoAMitadDeVentaBloqueaElConfirmar() = runTest {
        abrirTurno()
        val vm = vm()
        vm.recibirSeleccion(listOf(1))
        vm.continuar()
        assertEquals(PasoVenta.COMPROBANTE, vm.state.value.paso)
        turnos.cerrar(T0, "Ana")
        assertTrue(vm.state.value.bloqueada)
        assertEquals(1, vm.state.value.lineas.size)             // el carrito se conserva
        vm.confirmarEfectivo()
        assertTrue(ventas.ventas.isEmpty())
    }

    // ---------------- Efectivo ----------------

    @Test fun flujoEfectivoCompleto() = runTest {
        abrirTurno()
        val vm = vm()
        vm.recibirSeleccion(listOf(1, 2))
        vm.cambiarCantidad(1, 3)
        vm.cambiarCantidad(2, 99)                                // tope = existencias (2)
        assertEquals(listOf(3L, 2L), vm.state.value.lineas.map { it.cantidad })
        vm.continuar()
        val s = vm.state.value
        assertEquals(PasoVenta.COMPROBANTE, s.paso)
        assertEquals(Cup.ofPesos(400), s.cotizacion!!.total)
        assertEquals(2, s.cotizacion!!.detalles.size)
        vm.confirmarEfectivo()
        assertEquals(MetodoPago.EFECTIVO, ventas.ventas.single().metodoPago)
        assertEquals(EventoVenta.Terminada("Venta registrada: 400 CUP en efectivo."), eventos.last())
        assertTrue(vm.state.value.lineas.isEmpty())
    }

    /** 0.20.0 (H5, P43): la venta abierta retiene el cierre pedido por el encargado hasta registrarse. */
    @Test fun cierrePedidoEsperaAQueTermineLaVenta() = runTest {
        abrirTurno()
        val vm = vm()
        assertTrue(sesion.enCurso.value)
        secundaria.flow.value = secundaria.flow.value.copy(cierrePendiente = true)
        assertTrue(vm.state.value.cierrePedido)
        vm.recibirSeleccion(listOf(1))
        vm.continuar()
        assertTrue("la venta sigue: no se corta a mitad", sesion.enCurso.value)
        vm.confirmarEfectivo()
        assertEquals(1, ventas.ventas.size)
        assertFalse("registrada la venta, el cierre ya puede aplicarse", sesion.enCurso.value)
    }

    @Test fun stockInsuficienteAlRegistrarVuelveAlCarritoConElError() = runTest {
        abrirTurno()
        val vm = vm()
        vm.recibirSeleccion(listOf(1))
        vm.continuar()
        ventas.errorRegistro = AppError.StockInsuficiente(listOf("Refresco"))
        vm.confirmarEfectivo()
        assertEquals(PasoVenta.CARRITO, vm.state.value.paso)
        assertEquals("No alcanza: Refresco. Ajusta las cantidades.", vm.state.value.error)
    }

    // ---------------- Transferencia ----------------

    @Test fun flujoTransferenciaConQrDatosYSmsPegado() = runTest {
        abrirTurno()
        val vm = vm()
        vm.recibirSeleccion(listOf(1))
        vm.elegirMetodo(MetodoPago.TRANSFERENCIA)
        vm.continuar()
        assertEquals(PasoVenta.QR, vm.state.value.paso)
        assertEquals(
            "TRANSFERMOVIL_ETECSA,TRANSFERENCIA,9248129970876454,51815604,",
            (vm.state.value.qr as PagoQr.Resultado.Ok).contenido,
        )
        vm.pagoRecibido()
        assertEquals(PasoVenta.CLIENTE, vm.state.value.paso)

        vm.confirmarTransferencia()                              // vacío → errores visibles, nada registrado
        assertTrue(ventas.ventas.isEmpty())
        assertEquals(4, vm.state.value.cliente.visibles().size)

        vm.editarCliente(CampoCliente.NOMBRE, "María Pérez")
        vm.editarCliente(CampoCliente.CI, "85010112345")
        vm.editarCliente(CampoCliente.TELEFONO, "51234567")
        vm.pegarSms(sms)
        assertEquals("BR601ADLM8997", vm.state.value.cliente.numero)
        assertNotNull(vm.state.value.avisoSms)                   // SMS de 540.00, venta de 100.00

        vm.confirmarTransferencia()
        val v = ventas.ventas.single()
        assertEquals(MetodoPago.TRANSFERENCIA, v.metodoPago)
        assertEquals("BR601ADLM8997", v.transaccion!!.numero)
        assertEquals("+5351234567", v.transaccion!!.cliente.telefono)
        assertEquals("9248129970876454", v.transaccion!!.tarjetaCobro)
    }

    @Test fun clienteFijoSugeridoRellenaLosDatosYSeMarcaEnLaVenta() = runTest {
        // 0.27.0 (N2): «mar» sugiere a María; al elegirla se rellenan carné y teléfono y queda marcado «Cliente fijo».
        abrirTurno()
        val vm = vm()
        vm.recibirSeleccion(listOf(1))
        vm.elegirMetodo(MetodoPago.TRANSFERENCIA)
        vm.continuar()
        vm.pagoRecibido()
        assertFalse(vm.state.value.cliente.fijo)
        vm.editarCliente(CampoCliente.NOMBRE, "mar")
        val s = vm.state.value.sugerencias.single()
        vm.elegirCliente(s)
        assertEquals("85010112345", vm.state.value.cliente.ci)
        assertTrue(vm.state.value.cliente.fijo)
        assertTrue(vm.state.value.sugerencias.isEmpty())        // nombre completo → ya no se sugiere
        vm.editarCliente(CampoCliente.NUMERO, "BR601ADLM8997")
        vm.confirmarTransferencia()
        val t = ventas.ventas.single().transaccion!!
        assertTrue(t.clienteFijo)
        assertEquals("María Pérez", t.cliente.nombreApellidos)
    }

    @Test fun sinMarcarClienteFijoLaVentaNoLoGuarda() = runTest {
        abrirTurno()
        val vm = vm()
        vm.recibirSeleccion(listOf(1))
        vm.elegirMetodo(MetodoPago.TRANSFERENCIA)
        vm.continuar()
        vm.pagoRecibido()
        vm.editarCliente(CampoCliente.NOMBRE, "Pedro Gómez")
        vm.editarCliente(CampoCliente.CI, "90020212345")
        vm.editarCliente(CampoCliente.TELEFONO, "52345678")
        vm.editarCliente(CampoCliente.NUMERO, "BR601ADLM8997")
        vm.clienteFijo(true); vm.clienteFijo(false)
        vm.confirmarTransferencia()
        assertFalse(ventas.ventas.single().transaccion!!.clienteFijo)
    }

    @Test fun sinTarjetaConfiguradaNoHayQrPeroSePuedeSeguir() = runTest {
        perfil.state.value = Perfil()
        abrirTurno()
        val vm = vm()
        vm.recibirSeleccion(listOf(1))
        vm.elegirMetodo(MetodoPago.TRANSFERENCIA)
        vm.continuar()
        assertEquals(PagoQr.Resultado.SinTarjeta, vm.state.value.qr)
        vm.configurarPago()
        assertEquals(EventoVenta.ConfigurarPago, eventos.last())
        // Vuelve de Pago electrónico con tarjeta: el QR aparece sin rehacer la venta.
        perfil.state.value = Perfil(tarjetas = listOf(TarjetaBancaria(1, "9248129970876454")), pagoTarjetaId = 1)
        assertTrue(vm.state.value.qr is PagoQr.Resultado.Ok)
    }

    @Test fun smsCompartidoConShalevLlenaElNumeroEnLaTransferencia() = runTest {
        abrirTurno()
        val vm = vm()
        vm.recibirSeleccion(listOf(1))
        vm.elegirMetodo(MetodoPago.TRANSFERENCIA)
        vm.continuar()
        entrada.publicar(Entrada.Texto(sms))
        assertEquals(PasoVenta.CLIENTE, vm.state.value.paso)
        assertEquals("BR601ADLM8997", vm.state.value.cliente.numero)
        assertNull(entrada.entrada.value)                        // consumido
    }

    @Test fun smsDeNotificacionSeCapturaDentroDelPasoDeTransferencia() = runTest {
        abrirTurno()
        val vm = vm()
        vm.recibirSeleccion(listOf(1))
        assertFalse(entrada.capturaSmsAutomaticaActiva())
        assertFalse(entrada.publicarSmsAutomatico(pagoSms))

        vm.elegirMetodo(MetodoPago.TRANSFERENCIA)
        vm.continuar()
        assertEquals(PasoVenta.QR, vm.state.value.paso)
        assertTrue(entrada.capturaSmsAutomaticaActiva())
        assertTrue(entrada.publicarSmsAutomatico(pagoSms))
        assertEquals(PasoVenta.CLIENTE, vm.state.value.paso)
        assertEquals("BR601ADLM8997", vm.state.value.cliente.numero)
        assertNull(entrada.entrada.value)

        vm.atras() // CLIENTE -> QR
        vm.atras() // QR -> CARRITO
        assertFalse(entrada.capturaSmsAutomaticaActiva())
    }

    @Test fun smsCompartidoFueraDelCobroNoSeUsa() = runTest {
        abrirTurno()
        val vm = vm()
        vm.recibirSeleccion(listOf(1))
        entrada.publicar(Entrada.Texto(sms))
        assertEquals("", vm.state.value.cliente.numero)
        assertNotNull(entrada.entrada.value)
    }

    @Test fun pegarSinTextoOSinNumeroAvisa() = runTest {
        abrirTurno()
        val vm = vm()
        vm.pegarSms(null)
        assertEquals(EventoVenta.Mensaje(TextosVenta.PORTAPAPELES_VACIO), eventos.last())
        vm.pegarSms("Hola, ya te pagué")
        assertEquals(EventoVenta.Mensaje(TextosVenta.SMS_SIN_NUMERO), eventos.last())
    }


    // ---------------- Navegación ----------------

    @Test fun atrasRetrocedePasosYPideConfirmarAntesDeDescartar() = runTest {
        abrirTurno()
        val vm = vm()
        vm.recibirSeleccion(listOf(1))
        vm.elegirMetodo(MetodoPago.TRANSFERENCIA)
        vm.continuar(); vm.pagoRecibido()
        vm.atras(); assertEquals(PasoVenta.QR, vm.state.value.paso)
        vm.atras(); assertEquals(PasoVenta.CARRITO, vm.state.value.paso)
        vm.atras(); assertTrue(vm.state.value.confirmarSalir)
        vm.cancelarSalir(); assertFalse(vm.state.value.confirmarSalir)
        vm.descartar()
        assertEquals(EventoVenta.Salir, eventos.last())
        assertTrue(vm.state.value.lineas.isEmpty())
        assertTrue(ventas.ventas.isEmpty())
    }

    @Test fun cancelarDesdeComprobanteOQrPideConfirmacionYConservaCarrito() = runTest {
        abrirTurno()
        val vm = vm()
        vm.recibirSeleccion(listOf(1))
        vm.continuar()
        assertEquals(PasoVenta.COMPROBANTE, vm.state.value.paso)
        vm.solicitarDescartar()
        assertTrue(vm.state.value.confirmarSalir)
        vm.cancelarSalir()
        assertEquals(PasoVenta.COMPROBANTE, vm.state.value.paso)
        assertEquals(1, vm.state.value.lineas.size)
        assertTrue(ventas.ventas.isEmpty())
        vm.atras()
        vm.elegirMetodo(MetodoPago.TRANSFERENCIA)
        vm.continuar()
        assertEquals(PasoVenta.QR, vm.state.value.paso)
        vm.solicitarDescartar()
        assertTrue(vm.state.value.confirmarSalir)
        vm.cancelarSalir()
        assertEquals(PasoVenta.QR, vm.state.value.paso)
        assertEquals(1, vm.state.value.lineas.size)
        vm.solicitarDescartar()
        vm.descartar()
        assertTrue(vm.state.value.lineas.isEmpty())
        assertTrue(ventas.ventas.isEmpty())
        assertEquals(EventoVenta.Salir, eventos.last())
    }

    @Test fun carritoSobreviveALaMuerteDelProceso() = runTest {
        abrirTurno()
        val vm = vm(mapOf(VentaViewModel.KEY_TIPO to "VENTA", VentaViewModel.KEY_AUTO to true, VentaViewModel.KEY_CARRITO to longArrayOf(2, 2, 1, 1)))
        assertEquals(listOf(2L, 1L), vm.state.value.lineas.map { it.producto.id })
        assertEquals(Cup.ofPesos(200), vm.state.value.totalEstimado)
    }
}
