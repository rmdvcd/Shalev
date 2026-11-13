package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.ConteoAlertas
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.Empleado
import cu.shalev.domain.model.FiltroRegistros
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.Venta
import cu.shalev.domain.service.RegistrosFiltro
import cu.shalev.domain.service.SesionVenta
import cu.shalev.domain.service.Stock
import cu.shalev.domain.service.TablasExport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.20.0: Vendedor (H1), cobro por empleado (H4), venta en curso (H5), existencias negativas (H6). */
class Version020Test {
    private val zona = ZoneId.of("America/Havana")

    // ---------------------------------------------------------------- H1 · Vendedor

    private fun venta(vendedor: String) = Venta(
        7, 1, T0, MetodoPago.EFECTIVO,
        listOf(DetalleVenta(productoId = 1, nombre = "Refresco", categoria = "Bebidas", cantidad = 1, precioBase = Cup.ofPesos(100), precioUnitario = Cup.ofPesos(100), costoUnitario = Cup.ofPesos(60))),
        vendedor = vendedor,
    )

    @Test fun `la tabla de ventas lleva la columna Vendedor tras la fecha`() {
        val t = TablasExport.ventas(listOf(venta("Luis")), zona)
        assertEquals("Vendedor", t.columnas[1])
        assertEquals("Luis", t.filas.single()[1])
    }

    @Test fun `la ficha muestra el vendedor pero los campos para el cliente no`() {
        assertTrue(TablasExport.camposVenta(venta("Luis"), zona).contains("Vendedor" to "Luis"))
        assertFalse(TablasExport.camposVenta(venta("Luis"), zona, interno = false).any { it.second.contains("Luis") })
        // Sin vendedor conocido (ventas sin turno): no se muestra una fila vacía.
        assertTrue(TablasExport.camposVenta(venta(""), zona).none { it.first == "Vendedor" })
    }

    @Test fun `filtro por vendedor sin distinguir mayusculas y contado como filtro activo`() {
        val f = FiltroRegistros(vendedor = "Luis")
        assertTrue(RegistrosFiltro.deVendedor("luis ", f))
        assertFalse(RegistrosFiltro.deVendedor("Ana", f))
        assertTrue(RegistrosFiltro.deVendedor("Ana", FiltroRegistros()))
        assertEquals(1, f.activos)
    }

    // ---------------------------------------------------------------- H4 · Cobro por empleado

    private val perfil = Perfil(
        nombre = "María", ci = "85010112345",
        tarjetas = listOf(TarjetaBancaria(1, "9200000000001111"), TarjetaBancaria(2, "9200000000002222")),
        telefonos = listOf(Telefono(1, "+5355555555"), Telefono(2, "+5356666666")),
        pagoTarjetaId = 1, pagoTelefonoId = 1,
    )

    @Test fun `el empleado recibe solo su tarjeta y su telefono, ya marcados de cobro`() {
        val p = perfil.paraEmpleado(tarjetaId = 2, telefonoId = 2)
        assertEquals(listOf(2L), p.tarjetas.map { it.id })
        assertEquals(listOf(2L), p.telefonos.map { it.id })
        assertEquals("9200000000002222", p.tarjetaPago?.numero)
        assertEquals("+5356666666", p.telefonoPago?.numero)
    }

    @Test fun `sin asignar o con una tarjeta borrada usa la predeterminada`() {
        val p = perfil.paraEmpleado(tarjetaId = null, telefonoId = 99)
        assertEquals(listOf(1L), p.tarjetas.map { it.id })
        assertEquals(listOf(1L), p.telefonos.map { it.id })
        // Sin predeterminada ni asignada: no viaja ninguna.
        val vacio = perfil.copy(pagoTarjetaId = null, pagoTelefonoId = null).paraEmpleado(null, null)
        assertTrue(vacio.tarjetas.isEmpty() && vacio.telefonos.isEmpty())
        assertNull(vacio.tarjetaPago)
    }

    // ---------------------------------------------------------------- H5 · Venta en curso y cierre pedido

    @Test fun `la venta en curso se marca y se libera una sola vez`() {
        val s = SesionVenta()
        assertFalse(s.enCurso.value)
        val a = s.iniciar()
        val b = s.iniciar()
        assertTrue(s.enCurso.value)
        s.terminar(a); s.terminar(a)
        assertTrue("queda otra venta abierta", s.enCurso.value)
        s.terminar(b)
        assertFalse(s.enCurso.value)
    }

    @Test fun `el cierre pedido solo cuenta con un turno abierto`() {
        val e = Empleado(id = 3, nombre = "Luis", permisos = emptySet(), creadoEn = T0, cierreSolicitadoEn = T0)
        assertFalse(e.cierrePedido)
        assertTrue(e.copy(turnoAbiertoDesde = T0).cierrePedido)
        assertFalse(e.copy(cierreSolicitadoEn = null, turnoAbiertoDesde = T0).cierrePedido)
    }

    // ---------------------------------------------------------------- H6 · Existencias negativas

    @Test fun `sin existencia cuenta productos e insumos con 0 o menos, nunca elaborados`() {
        val ps = listOf(producto(1, cantidad = -2), producto(2, cantidad = 0), producto(3, cantidad = -1, categoria = cu.shalev.domain.model.Categorias.ELABORADO))
        val xs = listOf(insumo(1).copy(cantidad = Cantidad(-500)), insumo(2))
        val c: ConteoAlertas = Stock.conteo(ps, xs, NivelesMinimos(), LocalDate.of(2026, 9, 1), 7)
        assertEquals(3, c.sinExistencia) // 0.21.0 (C1): el 0 también cuenta
        assertEquals(0, c.stockCritico)
        assertEquals(3, c.de(TipoAlerta.SIN_EXISTENCIA))
        assertEquals(listOf(1L, 2L), Stock.filtrarProductos(ps, TipoAlerta.SIN_EXISTENCIA, NivelesMinimos(), LocalDate.of(2026, 9, 1), 7).map { it.id })
        assertEquals(listOf(1L), Stock.filtrarInsumos(xs, TipoAlerta.SIN_EXISTENCIA, NivelesMinimos()).map { it.id })
    }
}
