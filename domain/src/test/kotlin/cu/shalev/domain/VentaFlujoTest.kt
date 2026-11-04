package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.service.LineaSolicitada
import cu.shalev.domain.service.PagoQr
import cu.shalev.domain.service.PlanificadorVenta
import cu.shalev.domain.service.Recetas
import cu.shalev.domain.usecase.AbrirTurno
import cu.shalev.domain.usecase.CotizarVenta
import cu.shalev.domain.usecase.ExtraerNumeroTransaccion
import cu.shalev.domain.usecase.RegistrarVenta
import cu.shalev.domain.usecase.UsuarioActual
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P26: venta directa de Elaborados desde sus insumos; Prompt 13: SMS reales de PAGOxMOVIL y QR de Transfermóvil. */
class VentaFlujoTest {
    private val clock = FixedClock()
    private val insumos = FakeInsumos()
    private val productos = FakeProductos().also { it.insumos = insumos }
    private val turnos = FakeTurnos()
    private val ventas = FakeVentas(productos, turnos)
    private val perfil = FakePerfil()
    private val cotizar = CotizarVenta(productos, FakePrecios(), insumos, FakeServicios())
    private val registrar = RegistrarVenta(cotizar, turnos, ventas, perfil, clock)

    private fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Ok)?.value ?: error("esperaba Ok y fue $r")
    private fun err(r: AppResult<*>): AppError = (r as? AppResult.Err)?.error ?: error("esperaba Err y fue $r")

    /** Pan: receta = 2 u de harina (10 u) + 1 u de sal (3 u) → alcanza para 3. Sus [guardadas] «u» se ignoran (P26). */
    private fun montarPan(guardadas: Long = 0) {
        insumos.put(insumo(1, "Harina", precio = 10, cantidad = 10), insumo(2, "Sal", precio = 5, cantidad = 3))
        productos.put(producto(9, "Pan", cantidad = guardadas, venta = 50, costo = 99, categoria = Categorias.ELABORADO))
        productos.recetas[9] = Receta(9, listOf(RecetaLinea(1, Cantidad.enteras(2)), RecetaLinea(2, Cantidad.enteras(1))))
    }

    // ---------------- Planificador / recetas ----------------

    @Test fun maxUnidadesEsElMinimoEntreInsumos() {
        montarPan()
        assertEquals(3L, Recetas.maxUnidades(productos.recetas[9]!!, insumos.items.value))
        assertEquals(0L, Recetas.maxUnidades(Receta(9, emptyList()), insumos.items.value))
        assertEquals(0L, Recetas.maxUnidades(Receta(9, listOf(RecetaLinea(99, Cantidad.enteras(1)))), insumos.items.value))
    }

    /** P26: un Elaborado se cotiza contra lo que alcanzan sus insumos, nunca contra su `cantidad`. */
    @Test fun elaboradoSeCotizaSoloContraLoQueAlcanza() {
        val pan = producto(9, "Pan", cantidad = 50, costo = 99, categoria = Categorias.ELABORADO)
        val r = ok(PlanificadorVenta.cotizar(listOf(LineaSolicitada(9, 3)), mapOf(9L to pan), MetodoPago.EFECTIVO, emptyList(), mapOf(9L to 3L), mapOf(9L to Cup.ofPesos(25))))
        assertEquals(mapOf(9L to 3L), r.elaborados)
        assertEquals(Cup.ofPesos(25), r.detalles.single().costoUnitario) // costo por receta, no el guardado
        val corto = PlanificadorVenta.cotizar(listOf(LineaSolicitada(9, 3)), mapOf(9L to pan), MetodoPago.EFECTIVO, emptyList(), mapOf(9L to 2L))
        assertEquals(AppError.StockInsuficiente(listOf("Pan")), err(corto)) // 50 «u» guardadas no cuentan
        val sinDato = PlanificadorVenta.cotizar(listOf(LineaSolicitada(9, 1)), mapOf(9L to pan), MetodoPago.EFECTIVO, emptyList())
        assertEquals(AppError.StockInsuficiente(listOf("Pan")), err(sinDato))
    }

    @Test fun articuloNormalSeCotizaContraSusExistencias() {
        val refresco = producto(1, "Refresco", cantidad = 1)
        val r = PlanificadorVenta.cotizar(listOf(LineaSolicitada(1, 2)), mapOf(1L to refresco), MetodoPago.EFECTIVO, emptyList(), mapOf(1L to 99L))
        assertEquals(AppError.StockInsuficiente(listOf("Refresco")), err(r))
        assertTrue(ok(PlanificadorVenta.cotizar(listOf(LineaSolicitada(1, 1)), mapOf(1L to refresco), MetodoPago.EFECTIVO, emptyList())).elaborados.isEmpty())
    }

    // ---------------- Registro: el Elaborado se vende directamente desde sus insumos ----------------

    @Test fun ventaDeElaboradoDescuentaSoloSusInsumos() = runBlocking {
        montarPan(guardadas = 7)
        ok(AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO))
        val cot = ok(cotizar(listOf(LineaSolicitada(9, 3)), MetodoPago.EFECTIVO))
        assertEquals(mapOf(9L to 3L), cot.elaborados)

        val id = ok(registrar(listOf(LineaSolicitada(9, 3)), MetodoPago.EFECTIVO))
        assertEquals(7L, productos.obtener(9)!!.cantidad)                      // el Elaborado no tiene existencias que tocar
        assertEquals(Cantidad.enteras(4), insumos.items.value[1]!!.cantidad)  // 10 − 3×2
        assertEquals(Cantidad.enteras(0), insumos.items.value[2]!!.cantidad)  // 3 − 3×1
        val e = ventas.elaborados.single()
        assertEquals(3L, e.unidades)
        assertEquals(mapOf(1L to Cantidad.enteras(6), 2L to Cantidad.enteras(3)), e.consumo)
        val venta = ventas.obtener(id)!!
        assertEquals(Cup.ofPesos(150), venta.total)
        assertEquals(Cup.ofPesos(25), venta.detalles.single().costoUnitario)    // 2×10 + 1×5 en ese momento
        assertTrue(turnos.movimientos.none { it.entidadId == 9L })               // sin movimiento VENTA/PRODUCCION del Elaborado
    }

    @Test fun sinInsumosSuficientesNoSeRegistraNada() = runBlocking {
        montarPan(guardadas = 100)
        ok(AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO))
        assertEquals(AppError.StockInsuficiente(listOf("Pan")), err(registrar(listOf(LineaSolicitada(9, 4)), MetodoPago.EFECTIVO)))
        assertTrue(ventas.ventas.isEmpty())
        assertEquals(Cantidad.enteras(10), insumos.items.value[1]!!.cantidad)
    }

    @Test fun ventaMixtaArticuloYElaborado() = runBlocking {
        montarPan()
        productos.put(producto(1, "Refresco", cantidad = 5, venta = 30, costo = 20))
        ok(AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO))
        ok(registrar(listOf(LineaSolicitada(9, 1), LineaSolicitada(1, 2)), MetodoPago.EFECTIVO))
        assertEquals(3L, productos.obtener(1)!!.cantidad)
        assertEquals(Cantidad.enteras(8), insumos.items.value[1]!!.cantidad)
        assertEquals(listOf(1L), turnos.movimientos.map { it.entidadId })
    }

    // ---------------- El precio de lo vendido queda congelado ----------------

    /**
     * 0.31.0 (tarea 4): cambiar el precio de venta no reescribe lo vendido; el detalle guarda el importe del momento
     * y el histórico lee de ahí, no del catálogo. (El precio solo se refresca al *modificar* una venta, que es otra.)
     */
    @Test fun cambiarElPrecioNoTocaLasVentasRegistradas() = runBlocking {
        productos.put(producto(1, "Refresco", cantidad = 10, venta = 30, costo = 20))
        ok(AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO))
        val id = ok(registrar(listOf(LineaSolicitada(1, 4)), MetodoPago.EFECTIVO))
        assertEquals(Cup.ofPesos(120), ventas.obtener(id)!!.total)

        ok(productos.actualizarPrecios(mapOf(1L to Cup.ofPesos(99))))

        val v = ventas.obtener(id)!!
        assertEquals(Cup.ofPesos(30), v.detalles.single().precioUnitario)
        assertEquals(Cup.ofPesos(20), v.detalles.single().costoUnitario)
        assertEquals(Cup.ofPesos(120), v.total)
    }

    /** Igual con el costo de un elaborado: se congela con el precio de los insumos del momento. */
    @Test fun cambiarElPrecioDeUnInsumoNoRearmaElCostoDeLoVendido() = runBlocking {
        montarPan()
        ok(AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO))
        val id = ok(registrar(listOf(LineaSolicitada(9, 2)), MetodoPago.EFECTIVO))
        assertEquals(Cup.ofPesos(25), ventas.obtener(id)!!.detalles.single().costoUnitario) // 2×10 + 1×5
        insumos.put(insumo(1, "Harina", precio = 100, cantidad = 10), insumo(2, "Sal", precio = 5, cantidad = 3))
        assertEquals(Cup.ofPesos(25), ventas.obtener(id)!!.detalles.single().costoUnitario)
        assertEquals(Cup.ofPesos(100), ventas.obtener(id)!!.total) // 2 × 50, el precio del Pan aquel día
    }

    /** Dos Elaborados que comparten insumo se validan por separado; juntos no alcanzan → nada se registra. */
    @Test fun insumoCompartidoQueNoAlcanzaParaAmbosNoRegistraNada() = runBlocking {
        montarPan()
        productos.put(producto(10, "Galleta", venta = 60, costo = 10, categoria = Categorias.ELABORADO))
        productos.recetas[10] = Receta(10, listOf(RecetaLinea(1, Cantidad.enteras(5))))
        ok(AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO))
        // Pan ×3 = 6 de harina (alcanza), Galleta ×1 = 5 (alcanza); juntos 11 > 10.
        assertEquals(AppError.StockInsuficiente(listOf("Harina")), err(registrar(listOf(LineaSolicitada(9, 3), LineaSolicitada(10, 1)), MetodoPago.EFECTIVO)))
        assertTrue(ventas.ventas.isEmpty())
        assertEquals(Cantidad.enteras(10), insumos.items.value[1]!!.cantidad)
    }

    @Test fun sinTurnoNoSeVendeNiSeConsumen() = runBlocking {
        montarPan()
        assertEquals(AppError.TurnoCerrado, err(registrar(listOf(LineaSolicitada(9, 1)), MetodoPago.EFECTIVO)))
        assertEquals(Cantidad.enteras(10), insumos.items.value[1]!!.cantidad)
    }

    // ---------------- SMS de PAGOxMOVIL (muestras reales, datos enmascarados) ----------------

    private val extraer = ExtraerNumeroTransaccion()

    private val transferencia = """
        Banco Popular de Ahorro:  La Transferencia fue completada.
         Fecha: 30/9/2026
         Beneficiario: 9212XXXXXXXX4041
         Ordenante: 9248XXXXXXXX6454
         Monto: 540 CUP
         Nro. Transaccion: BR601ADLM8997
         Saldo restante: CR 4086.20 CUP
    """.trimIndent()

    private val pago = """
        Banco Popular de Ahorro: Pago completado.
         Fecha: 30/9/2026
         Entidad: Mercado de Rance
         Id Compra: 8900367034
         Importe: 1700 CUP
         Importe pagado: 1632 CUP
         No. Transaccion: BR601AG4M9997
         Saldo disponible: DB 2454.20 CUP
    """.trimIndent()

    private val otraTransferencia = """
        Banco Popular de Ahorro:  La Transferencia fue completada.
         Fecha: 30/9/2026
         Beneficiario: 9238XXXXXXXX4369
         Ordenante: 9248XXXXXXXX6454
         Monto: 710 CUP
         Nro. Transaccion: BR601AG6DJ997
         Saldo restante: CR 1744.20 CUP
    """.trimIndent()

    @Test fun smsDeTransferenciaReal() {
        val d = extraer.detalle(transferencia)!!
        assertEquals("BR601ADLM8997", d.numero)
        assertEquals(Cup.ofPesos(540), d.importe)
    }

    @Test fun smsDePagoRealUsaNoTransaccionYElImportePagado() {
        val d = extraer.detalle(pago)!!
        assertEquals("BR601AG4M9997", d.numero)
        assertEquals(Cup.ofPesos(1632), d.importe)
    }

    @Test fun conversacionEnteraTomaLaUltimaTransaccion() {
        val hilo = listOf(
            "Usted se ha autenticado en la plataforma de pagos moviles, en el Banco Popular de Ahorro, puede comenzar a utilizar nuestros servicios de pagos a traves del movil",
            transferencia, pago, otraTransferencia,
        ).joinToString("\n\n")
        val d = extraer.detalle(hilo)!!
        assertEquals("BR601AG6DJ997", d.numero)
        assertEquals(Cup.ofPesos(710), d.importe)
        assertEquals("BR601AG6DJ997", extraer(hilo))
    }

    @Test fun textoSinTransaccionDevuelveNull() {
        assertNull(extraer("Usted se ha autenticado en la plataforma de pagos moviles"))
        assertNull(extraer("Transaccion: ABCDEFGH"))  // sin dígitos: no es un nº de transacción
        assertNull(extraer(""))
    }

    // ---------------- QR de Transfermóvil ----------------

    @Test fun qrCoincideConElDeTransfermovilDecodificado() {
        val p = Perfil(
            tarjetas = listOf(TarjetaBancaria(1, "9248129970876454")), telefonos = listOf(Telefono(2, "+5351815604")),
            pagoTarjetaId = 1, pagoTelefonoId = 2,
        )
        val r = PagoQr.transfermovil(p) as PagoQr.Resultado.Ok
        // Cadena exacta leída del QR de la captura (Mis Cuentas → QR), con la coma final.
        assertEquals("TRANSFERMOVIL_ETECSA,TRANSFERENCIA,9248129970876454,51815604,", r.contenido)
    }

    @Test fun qrSinTarjetaNoSeGeneraYSinMovilDejaElCampoVacio() {
        assertEquals(PagoQr.Resultado.SinTarjeta, PagoQr.transfermovil(Perfil()))
        val soloTarjeta = Perfil(tarjetas = listOf(TarjetaBancaria(1, "9248129970876454")), pagoTarjetaId = 1)
        assertEquals("TRANSFERMOVIL_ETECSA,TRANSFERENCIA,9248129970876454,,", (PagoQr.transfermovil(soloTarjeta) as PagoQr.Resultado.Ok).contenido)
        val conMovil = Perfil(
            tarjetas = listOf(TarjetaBancaria(1, "9248129970876454")), telefonos = listOf(Telefono(2, "+5351815604")),
            pagoTarjetaId = 1, pagoTelefonoId = 2,
        )
        assertEquals("51815604", (PagoQr.transfermovil(conMovil) as PagoQr.Resultado.Ok).telefono)
        val extranjero = Perfil(
            tarjetas = listOf(TarjetaBancaria(1, "9248129970876454")), telefonos = listOf(Telefono(2, "+34600111222")),
            pagoTarjetaId = 1, pagoTelefonoId = 2,
        )
        assertNull((PagoQr.transfermovil(extranjero) as PagoQr.Resultado.Ok).telefono)
    }
}
