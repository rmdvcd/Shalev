package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Venta
import cu.shalev.domain.service.TablasExport
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TablasExportTest {
    @Test fun `ventas a tabla con total`() {
        val v = Venta(7, 1, T0, MetodoPago.EFECTIVO, listOf(DetalleVenta(productoId = 1, nombre = "Refresco", categoria = "Bebidas", cantidad = 2, precioBase = Cup.ofPesos(725), precioUnitario = Cup.ofPesos(725), costoUnitario = Cup.ofPesos(500))))
        val t = TablasExport.ventas(listOf(v), ZoneId.of("America/Havana"))
        assertEquals(listOf("01/09/2026 08:00", "", "Efectivo", "Refresco ×2", "2", "1,450 CUP", "1,000 CUP", "450 CUP", "Válida"), t.filas.single())
        assertEquals("Ventas", t.titulo)
        assertTrue(t.pie.last().endsWith("Total: 1,450 CUP"))
    }

    @Test fun `inventario omite eliminados`() {
        val t = TablasExport.inventario(listOf(producto(1), producto(2).copy(eliminado = true)))
        assertEquals(1, t.filas.size)
    }

    /** 0.25.1: el turno en PDF/Excel lleva resumen, arqueo (con diferencia) y ventas; omite tablas vacías. */
    @Test fun `turno completo con arqueo`() {
        val z = ZoneId.of("America/Havana")
        val v = Venta(7, 1, T0, MetodoPago.EFECTIVO, listOf(DetalleVenta(productoId = 1, nombre = "Refresco", categoria = "Bebidas", cantidad = 2, precioBase = Cup.ofPesos(725), precioUnitario = Cup.ofPesos(725), costoUnitario = Cup.ofPesos(500))))
        val t = cu.shalev.domain.model.Turno(1, T0, T0.plusSeconds(3600), abiertoPor = "Ana Pérez", cerradoPor = "Ana Pérez", fondo = Cup.ofPesos(500), contado = Cup.ofPesos(1900))
        val d = cu.shalev.domain.model.DetalleTurno(t, listOf(v), emptyList(), emptyList())
        val tablas = TablasExport.turno(d, z)
        assertEquals(listOf("Turno del 01/09/2026 08:00", "Arqueo de caja", "Ventas"), tablas.map { it.titulo })
        val resumen = tablas[0].filas.associate { it[0] to it[1] }
        assertEquals("1,450 CUP", resumen["Total vendido"])
        assertEquals("01/09/2026 09:00 · Ana Pérez", resumen["Cierre"])
        val arqueo = tablas[1].filas.associate { it[0] to it[1] }
        assertTrue(arqueo.toString(), arqueo.values.any { it.contains("1,950.00") }) // esperado = 500 + 1,450
        assertEquals("Shalev_Turno_2026-09-01_0800.xlsx", TablasExport.nombreArchivoTurno(t, "xlsx", z))
        // Abierto: provisional; sin arqueo (turno antiguo) la tabla lo explica.
        val viejo = TablasExport.turno(cu.shalev.domain.model.DetalleTurno(cu.shalev.domain.model.Turno(2, T0), emptyList(), emptyList(), emptyList()), z)
        assertTrue(viejo[0].titulo.endsWith("(provisional)"))
        assertEquals(2, viejo.size)
        assertTrue(viejo[1].filas.isNotEmpty())
    }
}
