package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.ClaseArticulo
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.Granularidad
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.NivelStock
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.PeriodoPreset
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.Venta
import cu.shalev.domain.service.Estadisticas
import cu.shalev.domain.service.Stock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockEstadisticasTest {
    private val n = NivelesMinimos()
    private val hoy = LocalDate.of(2026, 9, 1)
    private val habana = ZoneId.of("America/Havana")

    @Test fun `niveles por defecto 5 y 1, excluyentes`() {
        assertEquals(NivelStock.NORMAL, Stock.nivel(producto(1, cantidad = 6), n))
        assertEquals(NivelStock.BAJO, Stock.nivel(producto(1, cantidad = 5), n))
        assertEquals(NivelStock.BAJO, Stock.nivel(producto(1, cantidad = 2), n))
        assertEquals(NivelStock.CRITICO, Stock.nivel(producto(1, cantidad = 1), n))
        // 0.21.0 (C1): 0 o menos = «Sin existencia» (sale de «Stock crítico»).
        assertEquals(NivelStock.SIN_EXISTENCIA, Stock.nivel(producto(1, cantidad = 0), n))
    }

    @Test fun `niveles propios prevalecen`() {
        assertEquals(NivelStock.BAJO, Stock.nivel(producto(1, cantidad = 15, bajo = 20, critico = 10), n))
        assertEquals(NivelStock.CRITICO, Stock.nivel(producto(1, cantidad = 10, bajo = 20, critico = 10), n))
        assertEquals(NivelStock.BAJO, Stock.nivel(insumo(1, cantidad = 3), n))
        assertEquals(NivelStock.CRITICO, Stock.nivel(insumo(1).copy(cantidad = Cantidad(500)), n))
    }

    @Test fun `conteo de alertas incluye vencidos y omite eliminados`() {
        val ps = listOf(
            producto(1, cantidad = 3),
            producto(2, cantidad = 0),
            producto(3, cantidad = 50, cad = hoy.plusDays(7)),
            producto(4, cantidad = 50, cad = hoy.minusDays(1)),
            producto(5, cantidad = 50, cad = hoy.plusDays(8)),
            producto(6, cantidad = 0).copy(eliminado = true),
        )
        val c = Stock.conteo(ps, listOf(insumo(1, cantidad = 4), insumo(2, cantidad = 1), insumo(3, cantidad = 1)), n, hoy, 7)
        assertEquals(1, c.stockBajo); assertEquals(0, c.stockCritico); assertEquals(1, c.sinExistencia) // 0.21.0 (C1)
        assertEquals(1, c.insumoBajo); assertEquals(2, c.insumoCritico)
        assertEquals(2, c.proximosACaducar)
        assertEquals(listOf(3L, 4L), Stock.filtrarProductos(ps, TipoAlerta.PROXIMO_A_CADUCAR, n, hoy, 7).map { it.id })
    }

    private fun venta(id: Long, fecha: Instant, vararg d: Triple<Long, Long, Pair<Long, Long>>, metodo: MetodoPago = MetodoPago.EFECTIVO) =
        Venta(id, 1, fecha, metodo, d.map { (pid, cant, pc) ->
            DetalleVenta(productoId = pid, nombre = "P$pid", categoria = "X", cantidad = cant, precioBase = Cup.ofPesos(pc.first), precioUnitario = Cup.ofPesos(pc.first), costoUnitario = Cup.ofPesos(pc.second))
        })

    @Test fun `top 3 con desempates por fecha de creacion`() {
        val ps = (1L..5L).map { producto(it, creado = T0.plusSeconds(it * 60)) }
        val vs = listOf(
            venta(1, T0, Triple(1, 5, 100L to 90L), Triple(2, 5, 100L to 50L), Triple(3, 2, 10L to 1L)),
            venta(2, T0, Triple(4, 1, 100L to 120L)),
        )
        val t = Estadisticas.top3(vs, ps)
        assertEquals(listOf(2L, 1L, 3L), t.masVendidos.map { it.productoId })        // empate 5/5 → gana el más reciente (2)
        assertEquals(listOf(5L, 4L, 3L), t.lentoMovimiento.map { it.productoId })    // 5 sin ventas; 4 vende 1; 3 vende 2
        assertEquals(listOf(2L, 1L, 3L), t.rentabilidad.map { it.productoId })       // ganancias 250, 50, 18; el 4 pierde
    }

    @Test fun `top 3 vacio sin ventas y lento desempata por el mas antiguo`() {
        val ps = (1L..4L).map { producto(it, creado = T0.plusSeconds(it)) }
        assertTrue(Estadisticas.top3(emptyList(), ps).masVendidos.isEmpty())
        val t = Estadisticas.top3(listOf(venta(1, T0, Triple(4, 1, 1L to 0L))), ps)
        assertEquals(listOf(1L, 2L, 3L), t.lentoMovimiento.map { it.productoId })
    }

    /** 1.0.1: los insumos con precio de venta cuentan como productos para las estadísticas. */
    @Test fun `top 3 cuenta los insumos con precio de venta como productos`() {
        val ps = listOf(producto(1, creado = T0.plusSeconds(60)), producto(2))
        val ins = listOf(
            cu.shalev.domain.model.Insumo(
                id = 1, nombre = "Harina", precio = Cup.ofPesos(10),
                cantidad = cu.shalev.core.quantity.Cantidad.enteras(10), creadoEn = T0, precioVenta = Cup.ofPesos(20),
            ),
            cu.shalev.domain.model.Insumo(
                id = 2, nombre = "Levadura", precio = Cup.ofPesos(5),
                cantidad = cu.shalev.core.quantity.Cantidad.enteras(4), creadoEn = T0,
            ), // sin precio de venta: solo receta, no cuenta como producto
        )
        val v = Venta(
            1, 1, T0, MetodoPago.EFECTIVO,
            listOf(
                DetalleVenta(productoId = 1, nombre = "P1", categoria = "X", cantidad = 3, precioBase = Cup.ofPesos(10), precioUnitario = Cup.ofPesos(10), costoUnitario = Cup.ofPesos(4)),
                DetalleVenta(productoId = 1, nombre = "Harina", categoria = "Insumos", cantidad = 2, precioBase = Cup.ofPesos(20), precioUnitario = Cup.ofPesos(20), costoUnitario = Cup.ofPesos(10), clase = ClaseArticulo.INSUMO),
            ),
        )
        val t = Estadisticas.top3(listOf(v), ps, ins)
        // Más vendido: P1 (3 u) y la Harina (2 u, id negativo del insumo).
        assertEquals(listOf(1L, -1L), t.masVendidos.map { it.productoId })
        assertEquals("Harina", t.masVendidos[1].nombre)
        // Lento movimiento: el producto sin ventas (2) y después la Harina (2 u) y P1 (3 u); la Levadura no entra.
        assertEquals(listOf(2L, -1L, 1L), t.lentoMovimiento.map { it.productoId })
        // Rentabilidad: Harina gana 20, P1 gana 18.
        assertEquals(listOf(-1L, 1L), t.rentabilidad.map { it.productoId })
    }

    /** 1.0.1: un insumo y un producto con el mismo id son artículos distintos (el insumo va con id negativo). */
    @Test fun `el id del insumo no se confunde con el de un producto igual`() {
        val ps = listOf(producto(1))
        val ins = listOf(
            cu.shalev.domain.model.Insumo(
                id = 1, nombre = "Azúcar", precio = Cup.ofPesos(10),
                cantidad = cu.shalev.core.quantity.Cantidad.enteras(10), creadoEn = T0, precioVenta = Cup.ofPesos(20),
            ),
        )
        val v = Venta(
            1, 1, T0, MetodoPago.EFECTIVO,
            listOf(
                DetalleVenta(productoId = 1, nombre = "P1", categoria = "X", cantidad = 1, precioBase = Cup.ofPesos(10), precioUnitario = Cup.ofPesos(10), costoUnitario = Cup.ofPesos(4)),
                DetalleVenta(productoId = 1, nombre = "Azúcar", categoria = "Insumos", cantidad = 5, precioBase = Cup.ofPesos(20), precioUnitario = Cup.ofPesos(20), costoUnitario = Cup.ofPesos(10), clase = ClaseArticulo.INSUMO),
            ),
        )
        val t = Estadisticas.top3(listOf(v), ps, ins)
        assertEquals(listOf(-1L, 1L), t.masVendidos.map { it.productoId }) // Azúcar (5 u) y P1 (1 u)
        assertEquals(2, t.lentoMovimiento.size) // los dos, sin mezclarse
    }

    @Test fun `al guardar una ficha sin tocar la cantidad se conservan las ventas hechas mientras tanto`() {
        assertEquals(7L, Stock.cantidadAlGuardar(leida = 10, enFormulario = 10, actual = 7))   // se vendieron 3: no se deshacen
        assertEquals(12L, Stock.cantidadAlGuardar(leida = 10, enFormulario = 12, actual = 7))  // la cambió: manda lo escrito
        assertEquals(10L, Stock.cantidadAlGuardar(leida = null, enFormulario = 10, actual = 7)) // sin dato: como antes
        assertEquals(-2L, Stock.cantidadAlGuardar(leida = 0, enFormulario = 0, actual = -2))   // tampoco borra un negativo
    }

    /** 0.21.6: en Cuba el horario de verano empieza a las 00:00 (8/3/2026): el día 8 empieza a la 01:00. */
    @Test fun `serie diaria con el cambio de hora de Cuba no pierde ventas`() {
        val mes = Estadisticas.rango(PeriodoPreset.MES, Instant.parse("2026-03-20T17:00:00Z"), habana)
        assertEquals(LocalDate.of(2026, 4, 1).atStartOfDay(habana).toInstant(), mes.hasta)
        // Una venta al mediodía de cada día del 6 al 12 de marzo
        val vs = (6..12).map { d -> venta(d.toLong(), LocalDate.of(2026, 3, d).atTime(12, 0).atZone(habana).toInstant(), Triple(1, 1, 100L to 60L)) }
        val s = Estadisticas.serie(vs, mes.desde, mes.hasta, habana)
        assertEquals(31, s.puntos.size)
        assertEquals(Cup.ofPesos(700), s.puntos.fold(Cup.ZERO) { acc, p -> acc + p.ventas }) // antes: solo 3 de 7 (del 6 al 8)
        (5..11).forEach { i -> assertEquals(Cup.ofPesos(100), s.puntos[i].ventas) }
        // Cada cubo empieza a las 00:00 locales, salvo el 8, que empieza a la 01:00 (las 00:00 no existen)
        assertEquals(LocalDate.of(2026, 3, 9).atStartOfDay(habana).toInstant(), s.puntos[8].inicio)
        // «Hoy» el día del cambio dura 23 h y el día siguiente empieza a medianoche
        val hoy = Estadisticas.rango(PeriodoPreset.HOY, Instant.parse("2026-03-08T15:00:00Z"), habana)
        assertEquals(Duration.ofHours(23), Duration.between(hoy.desde, hoy.hasta))
        assertEquals(LocalDate.of(2026, 3, 9).atStartOfDay(habana).toInstant(), hoy.hasta)
    }

    @Test fun `serie continua por hora dia o mes`() {
        val hoyR = Estadisticas.rango(PeriodoPreset.HOY, Instant.parse("2026-09-01T15:30:00Z"), habana)
        assertEquals(Duration.ofHours(24), Duration.between(hoyR.desde, hoyR.hasta))
        val vs = listOf(venta(1, hoyR.desde.plusSeconds(3600 * 2 + 5), Triple(1, 2, 100L to 60L)))
        val s = Estadisticas.serie(vs, hoyR.desde, hoyR.hasta, habana)
        assertEquals(Granularidad.HORA, s.granularidad)
        assertEquals(24, s.puntos.size)
        assertEquals(Cup.ofPesos(200), s.puntos[2].ventas)
        assertEquals(Cup.ofPesos(80), s.puntos[2].ganancia)

        val mes = Estadisticas.rango(PeriodoPreset.MES, Instant.parse("2026-09-15T12:00:00Z"), habana)
        assertEquals(Granularidad.DIA, Estadisticas.serie(emptyList(), mes.desde, mes.hasta, habana).granularidad)
        assertEquals(30, Estadisticas.serie(emptyList(), mes.desde, mes.hasta, habana).puntos.size)
        val anio = Estadisticas.rango(PeriodoPreset.ANIO, Instant.parse("2026-09-15T12:00:00Z"), habana)
        assertEquals(12, Estadisticas.serie(emptyList(), anio.desde, anio.hasta, habana).puntos.size)
    }

    @Test fun `distribuciones de dona`() {
        val cats = Estadisticas.distribucionCategorias(listOf(producto(1, cantidad = 30, categoria = "A"), producto(2, cantidad = 10, categoria = "B"), producto(3, cantidad = 0, categoria = "C")))
        assertEquals(listOf("A", "B"), cats.map { it.etiqueta })
        assertEquals(0.75, cats[0].fraccion, 1e-9)
        assertEquals(30_000L, cats[0].valor) // 0.27.0 (N1): en milésimas
        val met = Estadisticas.distribucionMetodos(listOf(venta(1, T0, Triple(1, 1, 300L to 0L)), venta(2, T0, Triple(1, 1, 100L to 0L), metodo = MetodoPago.TRANSFERENCIA)))
        assertEquals(listOf("Efectivo", "Transferencia"), met.map { it.etiqueta })
        assertTrue(Estadisticas.distribucionMetodos(emptyList()).isEmpty())
    }

    /** 0.27.0 (N1): los insumos entran en la dona Inventario, cada uno en su medida (milésimas). */
    @Test fun `dona inventario con insumos en su medida`() {
        val harina = cu.shalev.domain.model.Insumo(
            id = 1, nombre = "Harina", unidad = cu.shalev.domain.model.UnidadMedida.KILOGRAMO, precio = cu.shalev.core.money.Cup(0),
            cantidad = cu.shalev.core.quantity.Cantidad(2_500), creadoEn = T0,
        )
        val cats = Estadisticas.distribucionCategorias(listOf(producto(1, cantidad = 5, categoria = "A")), listOf(harina))
        assertEquals(listOf("A", Estadisticas.CATEGORIA_INSUMOS), cats.map { it.etiqueta })
        assertEquals(listOf(5_000L, 2_500L), cats.map { it.valor })
        assertTrue(Estadisticas.distribucionCategorias(emptyList(), listOf(harina.copy(cantidad = cu.shalev.core.quantity.Cantidad(0)))).isEmpty())
    }
}
