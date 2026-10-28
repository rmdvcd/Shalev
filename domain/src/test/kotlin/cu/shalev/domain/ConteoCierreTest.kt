package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.domain.model.*
import cu.shalev.domain.service.TablasExport
import org.junit.Assert.*
import org.junit.Test

class ConteoCierreTest {
    private val linea = LineaConteo(TipoEntidad.PRODUCTO, 1, "Pan", "u", 10, 8, 1000)

    @Test fun incluyeTodasLasDenominacionesSolicitadas() {
        assertEquals(listOf(1, 3, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 20000), BilletesCUP.denominaciones)
        assertEquals(Cup.ofPesos(37003) + Cup(50), BilletesCUP.total(mapOf(2000 to 1L, 5000 to 3L, 20000 to 1L, 3 to 1L), Cup(50)))
        assertEquals(Cup.ZERO, BilletesCUP.total(emptyMap()))
    }
    @Test fun rechazaDenominacionesNegativasCantidadesExcesivasYDesbordamiento() {
        assertTrue(runCatching { BilletesCUP.total(mapOf(2 to 1L)) }.isFailure)
        assertTrue(runCatching { BilletesCUP.total(mapOf(100 to -1L)) }.isFailure)
        assertTrue(runCatching { BilletesCUP.total(mapOf(100 to 1000000L)) }.isFailure)
        assertTrue(runCatching { BilletesCUP.total(mapOf(1 to 1L), Cup(Long.MAX_VALUE)) }.isFailure)
        assertTrue(runCatching { BilletesCUP.total(emptyMap(), Cup(-1)) }.isFailure)
    }
    @Test fun lasDiferenciasExigenMotivoYNoSeAceptanCantidadesNegativasNiDuplicados() {
        val c = ConteoCierre(inventario = listOf(linea))
        assertFalse(c.valido())
        assertTrue(c.copy(motivo = "Merma comprobada").valido())
        assertFalse(c.copy(motivo = "x".repeat(161)).valido())
        assertFalse(c.copy(inventario = listOf(linea.copy(contado = -1)), motivo = "Merma").valido())
        assertFalse(c.copy(inventario = listOf(linea, linea), motivo = "Merma").valido())
        assertTrue(c.copy(inventario = listOf(linea.copy(contado = 10))).valido())
        assertFalse(c.copy(inventario = listOf(linea.copy(esperado = Long.MIN_VALUE)), motivo = "Merma").valido())
    }
    @Test fun exportaBilletesFraccionesYFaltantesSinCambiarLasUnidades() {
        val c = ConteoCierre(mapOf(5000 to 2L), Cup(50), listOf(linea,
            LineaConteo(TipoEntidad.INSUMO, 2, "Harina", "kg", 2500, 2250, 1000, "Insumos")), "Merma")
        val tablas = TablasExport.tablasConteo(c)
        assertEquals("10,000.50 CUP", tablas[0].filas.last()[2])
        // 0.32.0: la categoría encabeza la conciliación (el conteo es parcial y va por categorías).
        assertEquals(listOf("Insumos", "Harina", "kg", "2.5", "2.25", "-0.25"), tablas[1].filas.last())
        assertEquals("-2", tablas[1].filas.first().last())
        assertTrue(tablas[1].pie.contains("Pendiente de aprobación"))
    }
}
