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
import cu.shalev.domain.model.TopPersona
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

    private fun venta(turno: Long, fecha: Instant, pid: Long, cant: Long, precio: Long, costo: Long, metodo: MetodoPago = MetodoPago.EFECTIVO, local: String = "") =
        Venta(
            id = ventas.ventas.size + 1L, turnoId = turno, fecha = fecha, metodoPago = metodo, local = local,
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
        suspend fun total(o: OpcionPeriodo) = (graficos(resolver(o, zone), zone = zone) as AppResult.Ok).value.totalVentas
        assertEquals(Cup.ofPesos(100), total(OpcionPeriodo.HOY))
        assertEquals(Cup.ofPesos(200), total(OpcionPeriodo.SEMANA))
        assertEquals(Cup.ofPesos(100), total(OpcionPeriodo.MES)) // T0 = 1 de septiembre: la otra es de agosto
        assertEquals(Cup.ofPesos(200), total(OpcionPeriodo.ANIO))
    }

    @Test fun `la serie de cada período termina en su último cubo visible y omite el futuro`() = runBlocking<Unit> {
        venta(1, T0.plusSeconds(3600), 1, 1, 100, 50) // se consulta, pero aún está una hora en el futuro
        val puntosEsperados = mapOf(
            OpcionPeriodo.HOY to 9, // medianoche local hasta la hora actual incluida
            OpcionPeriodo.SEMANA to 7, // seis días anteriores y hoy
            OpcionPeriodo.MES to 1, // el mes actual solo ha empezado
            OpcionPeriodo.ANIO to 9, // enero hasta el mes actual
        )
        puntosEsperados.forEach { (opcion, cantidad) ->
            val g = (graficos(resolver(opcion, zone), zone = zone) as AppResult.Ok).value
            assertEquals(opcion.name, cantidad, g.serie.puntos.size)
            assertEquals("la venta futura queda fuera de ${opcion.name}", Cup.ZERO, g.totalVentas)
        }
    }

    @Test fun `resumen general del periodo elegido e inventario actual`() = runBlocking<Unit> {
        productos.put(producto(1, cantidad = 30, categoria = "A", creado = T0.minusSeconds(100)), producto(2, cantidad = 10, categoria = "B", creado = T0))
        venta(1, T0.minusSeconds(86_400L * 40), 2, 50, 100, 10) // fuera de los 7 días
        venta(1, T0.minusSeconds(3600), 1, 3, 100, 60, MetodoPago.TRANSFERENCIA)
        val r = resumen(resolver(OpcionPeriodo.SEMANA, zone), zone)
        assertEquals(listOf("A", "B"), r.categorias.map { it.etiqueta })
        assertEquals(listOf("Transferencia"), r.metodosPago.map { it.etiqueta })
        assertEquals(listOf(1L), r.top3.masVendidos.map { it.productoId })
        assertEquals(listOf(2L, 1L), r.top3.lentoMovimiento.map { it.productoId })
        assertFalse(r.vacio)
    }

    /** Decisión del usuario (2026-11-15): Métodos de pago y los Top 3 siguen al selector de período. */
    @Test fun `metodos de pago y Top 3 cambian con el periodo`() = runBlocking<Unit> {
        productos.put(producto(1, categoria = "A"), producto(2, categoria = "A"))
        venta(1, T0.minusSeconds(86_400L * 3), 2, 5, 100, 10) // hace 3 días: entra en 7 días, no en Hoy
        venta(1, T0.minusSeconds(3600), 1, 1, 100, 60, MetodoPago.TRANSFERENCIA)
        venta(1, T0.plusSeconds(3600), 2, 9, 100, 10) // futura: nunca cuenta
        val hoy = resumen(resolver(OpcionPeriodo.HOY, zone), zone)
        assertEquals(listOf("Transferencia"), hoy.metodosPago.map { it.etiqueta })
        assertEquals(listOf(1L), hoy.top3.masVendidos.map { it.productoId })
        val semana = resumen(resolver(OpcionPeriodo.SEMANA, zone), zone)
        assertEquals(setOf("Efectivo", "Transferencia"), semana.metodosPago.map { it.etiqueta }.toSet())
        assertEquals(listOf(2L, 1L), semana.top3.masVendidos.map { it.productoId })
    }

    @Test fun `con Turno el resumen es solo de las ventas del turno`() = runBlocking<Unit> {
        productos.put(producto(1), producto(2))
        turnos.abrir(T0.minusSeconds(7200), "Ana"); venta(1, T0.minusSeconds(5000), 2, 4, 100, 10); turnos.cerrar(T0.minusSeconds(4000), "Ana")
        turnos.abrir(T0.minusSeconds(3000), "Ana"); venta(2, T0.minusSeconds(60), 1, 1, 100, 60, MetodoPago.TRANSFERENCIA)
        val r = resumen(resolver(OpcionPeriodo.TURNO, zone), zone)
        assertEquals(listOf("Transferencia"), r.metodosPago.map { it.etiqueta })
        assertEquals(listOf(1L), r.top3.masVendidos.map { it.productoId })
    }

    /** 1.0.6: «Ventas por local» es la excepción: cruza TODOS los locales del período, no solo el activo. */
    @Test fun `ranking de locales sigue al periodo y cruza los locales`() = runBlocking<Unit> {
        productos.put(producto(1))
        venta(1, T0.minusSeconds(86_400L * 3), 1, 1, 1000, 50, local = "Cafetería") // hace 3 días: entra en 7 días, no en Hoy
        venta(1, T0.minusSeconds(3600), 1, 2, 200, 50, MetodoPago.TRANSFERENCIA, local = "Dulcería")
        venta(1, T0.minusSeconds(1800), 1, 1, 50, 10, local = "Cafetería")
        venta(1, T0.plusSeconds(3600), 1, 9, 100, 10, local = "Dulcería") // futura: nunca cuenta
        val hoy = resumen(resolver(OpcionPeriodo.HOY, zone), zone)
        assertEquals(
            listOf(
                TopPersona("Dulcería", Cup.ofPesos(400), 1, ganancia = Cup.ofPesos(300)),
                TopPersona("Cafetería", Cup.ofPesos(50), 1, ganancia = Cup.ofPesos(40)),
            ),
            hoy.locales,
        )
        val semana = resumen(resolver(OpcionPeriodo.SEMANA, zone), zone)
        assertEquals(
            listOf(
                TopPersona("Cafetería", Cup.ofPesos(1050), 2, ganancia = Cup.ofPesos(990)),
                TopPersona("Dulcería", Cup.ofPesos(400), 1, ganancia = Cup.ofPesos(300)),
            ),
            semana.locales,
        )
    }

    @Test fun `con Turno el ranking de locales es solo el local del turno`() = runBlocking<Unit> {
        productos.put(producto(1))
        turnos.abrir(T0.minusSeconds(7200), "Ana"); venta(1, T0.minusSeconds(5000), 1, 4, 100, 10, local = "Cafetería"); turnos.cerrar(T0.minusSeconds(4000), "Ana")
        turnos.abrir(T0.minusSeconds(3000), "Ana"); venta(2, T0.minusSeconds(60), 1, 1, 200, 60, MetodoPago.TRANSFERENCIA, local = "Dulcería")
        val r = resumen(resolver(OpcionPeriodo.TURNO, zone), zone)
        assertEquals(listOf(TopPersona("Dulcería", Cup.ofPesos(200), 1, ganancia = Cup.ofPesos(140))), r.locales)
    }

    @Test fun `resumen sin ventas oculta el Top 3`() = runBlocking<Unit> {
        productos.put(producto(1))
        val r = resumen(resolver(OpcionPeriodo.HOY, zone), zone)
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
