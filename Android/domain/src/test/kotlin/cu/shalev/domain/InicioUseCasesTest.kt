package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.Granularidad
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.OpcionPeriodo
import cu.shalev.domain.model.Periodo
import cu.shalev.domain.model.PreajustePrecios
import cu.shalev.domain.model.Venta
import cu.shalev.domain.usecase.ActivarPreajuste
import cu.shalev.domain.usecase.ObtenerGraficosPeriodo
import cu.shalev.domain.usecase.ObtenerResumenGeneral
import cu.shalev.domain.usecase.PeriodoPorDefecto
import cu.shalev.domain.usecase.RangoDePreset
import cu.shalev.domain.usecase.ResolverPeriodo
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InicioUseCasesTest {
    private val zone = ZoneId.of("America/Havana")
    private val clock = FixedClock(T0)
    private val productos = FakeProductos()
    private val turnos = FakeTurnos()
    private val ventas = FakeVentas(productos, turnos)
    private val resolver = ResolverPeriodo(PeriodoPorDefecto(turnos, clock), RangoDePreset(clock))
    // 0.30.0 (F1): los casos de uso sacan el trabajo del hilo de UI; en tests, Unconfined lo mantiene determinista.
    private val graficos = ObtenerGraficosPeriodo(ventas, turnos, clock, Dispatchers.Unconfined)
    private val resumen = ObtenerResumenGeneral(ventas, productos, FakeInsumos(), FakeServicios(), clock, Dispatchers.Unconfined)

    private fun venta(turno: Long, fecha: Instant, pid: Long, cant: Long, precio: Long, costo: Long, metodo: MetodoPago = MetodoPago.EFECTIVO) =
        Venta(
            id = ventas.ventas.size + 1L, turnoId = turno, fecha = fecha, metodoPago = metodo,
            detalles = listOf(DetalleVenta(productoId = pid, nombre = "P$pid", categoria = "X", cantidad = cant, precioBase = Cup.ofPesos(precio), precioUnitario = Cup.ofPesos(precio), costoUnitario = Cup.ofPesos(costo))),
        ).also { ventas.ventas += it }

    @Test fun `TURNO sin turnos muestra hoy y lo indica`() = runBlocking<Unit> {
        val p = resolver(OpcionPeriodo.TURNO, zone)
        assertTrue(p is Periodo.Rango)
        val g = (graficos(p, pedidoTurno = true, zone) as AppResult.Ok).value
        assertTrue(g.sinTurnos)
        assertTrue(g.vacio)
        assertNull(g.margen)
        assertEquals(Granularidad.HORA, g.serie.granularidad)
    }

    @Test fun `TURNO usa el abierto y lo mide hasta ahora`() = runBlocking<Unit> {
        turnos.abrir(T0.minusSeconds(3 * 3600), "Ana")
        venta(1, T0.minusSeconds(3600), 1, 2, 100, 60)
        venta(1, T0.minusSeconds(60), 1, 1, 100, 60, MetodoPago.TRANSFERENCIA)
        val p = resolver(OpcionPeriodo.TURNO, zone)
        assertEquals(Periodo.DeTurno(1), p)
        val g = (graficos(p, pedidoTurno = true, zone) as AppResult.Ok).value
        assertFalse(g.sinTurnos)
        assertTrue(g.turno!!.abierto)
        assertEquals(Cup.ofPesos(300), g.totalVentas)
        assertEquals(Cup.ofPesos(180), g.totalCosto)
        assertEquals(Cup.ofPesos(120), g.ganancia)
        assertEquals(4000, g.margen) // 40 %
        assertFalse(g.vacio)
    }

    @Test fun `TURNO con el turno cerrado muestra el ultimo cerrado`() = runBlocking<Unit> {
        turnos.abrir(T0.minusSeconds(7200), "Ana"); venta(1, T0.minusSeconds(3600), 1, 1, 50, 10); turnos.cerrar(T0.minusSeconds(1800), "Ana")
        val p = resolver(OpcionPeriodo.TURNO, zone)
        val g = (graficos(p, true, zone) as AppResult.Ok).value
        assertFalse(g.turno!!.abierto)
        assertEquals(Cup.ofPesos(50), g.totalVentas)
    }

    @Test fun `turno inexistente es NoEncontrado`() = runBlocking<Unit> {
        assertEquals(AppResult.Err(AppError.NoEncontrado), graficos(Periodo.DeTurno(99), zone = zone))
    }

    @Test fun `rangos del selector`() = runBlocking<Unit> {
        venta(1, T0.minusSeconds(86_400L * 3), 1, 1, 100, 50) // hace 3 días
        venta(1, T0, 1, 1, 100, 50)
        fun total(o: OpcionPeriodo) = runBlocking { (graficos(resolver(o, zone), zone = zone) as AppResult.Ok).value.totalVentas }
        assertEquals(Cup.ofPesos(100), total(OpcionPeriodo.HOY))
        assertEquals(Cup.ofPesos(200), total(OpcionPeriodo.SEMANA))
        assertEquals(Cup.ofPesos(100), total(OpcionPeriodo.MES)) // T0 = 1 de septiembre: la otra es de agosto
        assertEquals(Cup.ofPesos(200), total(OpcionPeriodo.ANIO))
    }

    @Test fun `resumen general ultimos 30 dias e inventario actual`() = runBlocking<Unit> {
        productos.put(producto(1, cantidad = 30, categoria = "A", creado = T0.minusSeconds(100)), producto(2, cantidad = 10, categoria = "B", creado = T0))
        venta(1, T0.minusSeconds(86_400L * 40), 2, 50, 100, 10) // fuera de los 30 días
        venta(1, T0.minusSeconds(3600), 1, 3, 100, 60, MetodoPago.TRANSFERENCIA)
        val r = resumen(zone)
        assertEquals(30, r.dias)
        assertEquals(listOf("A", "B"), r.categorias.map { it.etiqueta })
        assertEquals(listOf("Transferencia"), r.metodosPago.map { it.etiqueta })
        assertEquals(listOf(1L), r.top3.masVendidos.map { it.productoId })
        assertEquals(listOf(2L, 1L), r.top3.lentoMovimiento.map { it.productoId })
        assertFalse(r.vacio)
    }

    @Test fun `resumen sin ventas oculta el Top 3`() = runBlocking<Unit> {
        productos.put(producto(1))
        val r = resumen(zone)
        assertTrue(r.top3.vacio)
        assertTrue(r.metodosPago.isEmpty())
        assertEquals(1, r.categorias.size)
    }

    @Test fun `activar y pausar un preajuste`() = runBlocking<Unit> {
        val precios = FakePrecios()
        precios.guardarPreajuste(PreajustePrecios(nombre = "Transferencia +10", puntosBasicos = 1000, productoIds = setOf(1)))
        val activar = ActivarPreajuste(precios)
        assertEquals(AppResult.Ok(Unit), activar(1, false))
        assertFalse(precios.preajustes.value.single().activo)
        assertEquals(AppResult.Ok(Unit), activar(1, true))
        assertTrue(precios.preajustes.value.single().activo)
        assertEquals(AppResult.Err(AppError.NoEncontrado), activar(9, true))
    }
}
