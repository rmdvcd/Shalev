package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.fichacosto.*
import cu.shalev.domain.model.*
import cu.shalev.domain.service.LineaSolicitada
import cu.shalev.domain.service.InsumosFiltro
import cu.shalev.domain.usecase.*
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AuditoriaIntegridadTest {
    private val clock = FixedClock()
    private val insumos = FakeInsumos()
    private val productos = FakeProductos().also { it.insumos = insumos }
    private val turnos = FakeTurnos()
    private val ventas = FakeVentas(productos, turnos)
    private val perfil = FakePerfil()
    private val servicios = FakeServicios()
    private val cotizar = CotizarVenta(productos, FakePrecios(), insumos, servicios)
    private val registrar = RegistrarVenta(cotizar, turnos, ventas, perfil, clock)
    private fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Ok)?.value ?: error("Se esperaba Ok: $r")

    @Test fun cambiarUnPrecioTrasAceptarElCobroObligaARevisarSinRegistrarNiDescontar() = runBlocking {
        productos.put(producto(1, "Pan", cantidad = 10, venta = 30, costo = 20))
        ok(AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO))
        val pedido = listOf(LineaSolicitada(1, 2))
        val aceptada = ok(cotizar(pedido, MetodoPago.EFECTIVO))
        ok(productos.actualizarPrecios(mapOf(1L to Cup(3_050))))
        val resultado = registrar(pedido, MetodoPago.EFECTIVO, aceptada = aceptada)
        assertEquals(AppResult.Err(AppError.Validacion("cotizacion", AppError.Regla.NO_PERMITIDO)), resultado)
        assertTrue(ventas.ventas.isEmpty())
        assertEquals(10L, productos.obtener(1)!!.cantidad)
    }

    @Test fun unCambioDeCostoAunqueNoCambieElTotalTambienRequiereRevision() = runBlocking {
        val p = producto(1, "Pan", cantidad = 10, venta = 30, costo = 20)
        productos.put(p)
        ok(AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO))
        val pedido = listOf(LineaSolicitada(1, 2))
        val aceptada = ok(cotizar(pedido, MetodoPago.EFECTIVO))
        productos.put(p.copy(precioCosto = Cup(2_025)))
        assertTrue(registrar(pedido, MetodoPago.EFECTIVO, aceptada = aceptada) is AppResult.Err)
        assertTrue(ventas.ventas.isEmpty())
    }

    @Test fun cotizacionSinCambiosConservaCentavosYSePuedeRegistrar() = runBlocking {
        productos.put(producto(1, "Pan", cantidad = 10).copy(precioVenta = Cup(3_025), precioCosto = Cup(2_001)))
        ok(AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO))
        val pedido = listOf(LineaSolicitada(1, 2))
        val aceptada = ok(cotizar(pedido, MetodoPago.EFECTIVO))
        val id = ok(registrar(pedido, MetodoPago.EFECTIVO, aceptada = aceptada))
        assertEquals(Cup(6_050), ventas.obtener(id)!!.total)
        assertEquals(Cup(2_001), ventas.obtener(id)!!.detalles.single().costoUnitario)
    }

    @Test fun unCambioEntreLaPrimeraLecturaYElCommitDelCobroNoSeCuela() = runBlocking {
        productos.put(producto(1, "Pan", cantidad = 10, venta = 30, costo = 20))
        ok(AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO))
        val repo = object : cu.shalev.domain.repository.VentaRepository by ventas {
            override suspend fun registrarVerificada(venta: Venta, elaborados: List<ElaboradoEnVenta>, verificar: suspend () -> AppResult<Unit>): AppResult<Long> {
                productos.put(productos.obtener(1)!!.copy(precioVenta = Cup(3_001)))
                return when (val r = verificar()) { is AppResult.Err -> r; is AppResult.Ok -> ventas.registrar(venta, elaborados) }
            }
        }
        val registrar = RegistrarVenta(cotizar, turnos, repo, perfil, clock)
        assertEquals(AppResult.Err(AppError.Validacion("cotizacion", AppError.Regla.NO_PERMITIDO)), registrar(listOf(LineaSolicitada(1, 1)), MetodoPago.EFECTIVO))
        assertTrue(ventas.ventas.isEmpty())
        assertEquals(10L, productos.obtener(1)!!.cantidad)
    }

    @Test fun modificarRevalidaLasLineasNuevasAntesDeAnularLaOriginal() = runBlocking {
        productos.put(producto(1, "Pan", cantidad = 10, venta = 30, costo = 20))
        productos.put(producto(2, "Otro", cantidad = 10, venta = 30, costo = 20))
        ok(AbrirTurno(turnos, UsuarioActual(perfil), clock)(Cup.ZERO))
        val id = ok(registrar(listOf(LineaSolicitada(1, 1)), MetodoPago.EFECTIVO))
        val repo = object : cu.shalev.domain.repository.VentaRepository by ventas {
            override suspend fun modificarVerificada(id: Long, anulacion: Anulacion, nueva: Venta, elaborados: List<ElaboradoEnVenta>, verificar: suspend () -> AppResult<Unit>): AppResult<Long> {
                productos.put(productos.obtener(2)!!.copy(precioVenta = Cup(3_001)))
                return when (val r = verificar()) { is AppResult.Err -> r; is AppResult.Ok -> ventas.modificar(id, anulacion, nueva, elaborados) }
            }
        }
        val modificar = ModificarVenta(cotizar, repo, turnos, perfil, UsuarioActual(perfil), clock, SecundariaFake())
        val resultado = modificar(id, listOf(LineaSolicitada(1, 1), LineaSolicitada(2, 1)), MetodoPago.EFECTIVO, null, "Agregar un artículo")
        assertEquals(AppResult.Err(AppError.Validacion("cotizacion", AppError.Regla.NO_PERMITIDO)), resultado)
        assertFalse(ventas.obtener(id)!!.anulada)
        assertEquals(1, ventas.ventas.size)
        assertEquals(9L, productos.obtener(1)!!.cantidad)
        assertEquals(10L, productos.obtener(2)!!.cantidad)
    }

    @Test fun elaboradoIncluyeManoDeObraYOtrosEnCotizacionYFicha() = runBlocking {
        val i = insumo(1, "Harina", precio = 10, cantidad = 10)
        insumos.put(i)
        val p = producto(9, "Pan", cantidad = 0, venta = 100, categoria = Categorias.ELABORADO).copy(costoAdicional = Cup(1_025))
        productos.put(p)
        val receta = Receta(9, listOf(RecetaLinea(1, Cantidad(1_500))))
        productos.recetas[9] = receta
        assertEquals(Cup(2_525), ok(cotizar(listOf(LineaSolicitada(9, 1)), MetodoPago.EFECTIVO)).detalles.single().costoUnitario)
        assertEquals(Cup(2_525), InsumosFiltro.disponible(p, receta, mapOf(1L to i)).costoUnidad)
    }

    @Test fun cantidadesEImportesFueraDeLongDevuelvenErrorSinDesbordarse() = runBlocking {
        val p = producto(1, "Pan", cantidad = 10, venta = 30, costo = 20).copy(cantidad = Long.MAX_VALUE)
        productos.put(p)
        assertTrue(cotizar(listOf(LineaSolicitada(1, Long.MAX_VALUE), LineaSolicitada(1, 1)), MetodoPago.EFECTIVO) is AppResult.Err)
        assertTrue(cotizar(listOf(LineaSolicitada(1, Long.MAX_VALUE)), MetodoPago.EFECTIVO) is AppResult.Err)
        val r = Receta(1, listOf(RecetaLinea(1, Cantidad(Long.MAX_VALUE))))
        insumos.put(insumo(1, "Harina", precio = 10, cantidad = 10))
        assertTrue(cu.shalev.domain.service.Recetas.costo(r, mapOf(1L to insumos.obtener(1)!!)) is AppResult.Err)
    }

    @Test fun recepcionesExigenDatosDeOrigenYCantidadesCompatibles() {
        val linea = LineaRecepcion(TipoEntidad.PRODUCTO, 1, Cantidad(1_000), Cup(125), "L-1")
        val r = NuevaRecepcion("identificador", "Proveedor", "C-1", "Compra inicial", listOf(linea))
        assertTrue(recepcionValida(r))
        assertFalse(recepcionValida(r.copy(proveedor = " ")))
        assertFalse(recepcionValida(r.copy(comprobante = "")))
        assertFalse(recepcionValida(r.copy(lineas = emptyList())))
        assertFalse(recepcionValida(r.copy(lineas = listOf(linea.copy(cantidad = Cantidad(1_250))))))
        assertTrue(recepcionValida(r.copy(lineas = listOf(linea.copy(entidad = TipoEntidad.INSUMO, cantidad = Cantidad(1_250))))))
    }

    @Test fun mermaYConsumoInternoSoloSonSalidasYSiempreRequierenMotivo() {
        assertTrue(ajusteValido(1, Cantidad(-500), MotivoMovimiento.MERMA, "Envase dañado"))
        assertFalse(ajusteValido(1, Cantidad(500), MotivoMovimiento.MERMA, "Envase dañado"))
        assertFalse(ajusteValido(1, Cantidad(500), MotivoMovimiento.CONSUMO_INTERNO, "Uso del negocio"))
        assertFalse(ajusteValido(1, Cantidad(-500), MotivoMovimiento.CORRECCION, " "))
        assertFalse(ajusteValido(1, Cantidad(500), MotivoMovimiento.COMPRA, "Compra nueva"))
    }

    @Test fun conversionDeMateriasEsExactaYNoAdivinaDensidades() {
        assertEquals(Cantidad(1_250), ReglasFichaCosto.convertir(BigDecimal("1250"), UnidadMedida.GRAMO, UnidadMedida.KILOGRAMO))
        assertEquals(Cantidad(250_000), ReglasFichaCosto.convertir(BigDecimal("0.25"), UnidadMedida.LITRO, UnidadMedida.MILILITRO))
        assertNull(ReglasFichaCosto.convertir(BigDecimal("1"), UnidadMedida.LITRO, UnidadMedida.KILOGRAMO))
        assertNull(ReglasFichaCosto.convertir(BigDecimal("0.1"), UnidadMedida.GRAMO, UnidadMedida.KILOGRAMO)) // 0.0001 kg
        assertNull(ReglasFichaCosto.unidad("paquete"))
    }

    @Test fun precioDeFichaConvierteUsdSinRedondearAPesosEnteros() {
        val f = FichaCostoGuardada(1, "Servicio", "0", UnidadMargen.PORCENTAJE, "300.25", listOf(
            PartidaCosto("Trabajo", CategoriaCosto.TRABAJO, BigDecimal.ONE, "servicio", BigDecimal("0.50"), MonedaCosto.USD),
        ))
        assertEquals(Cup(15_013), ReglasFichaCosto.precio(f))
        assertNull(ReglasFichaCosto.precio(f.copy(tasaCupPorUsd = "")))
    }
    private class SecundariaFake : cu.shalev.domain.repository.SecundariaRepository {
        override val estado = kotlinx.coroutines.flow.MutableStateFlow(EstadoSecundaria())
        override suspend fun vincular(contenidoQr: String, telefono: String) = AppResult.Ok(Unit)
        override suspend fun solicitarCierre(contado: Cup) = AppResult.Ok(Unit)
        override suspend fun sincronizarAhora() = AppResult.Ok(Unit)
        override suspend fun desvincular() = AppResult.Ok(Unit)
        override suspend fun descartarAvisoCierre() = Unit
    }

}
