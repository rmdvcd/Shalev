package cu.shalev.domain

import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.service.CorreccionMovimiento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.31.0: lógica pura de la corrección de un ajuste desde Registros → Movimientos. */
class CorreccionMovimientoTest {

    @Test fun soloLosAjustesDelTurnoAbiertoSePuedenCorregir() {
        assertTrue(CorreccionMovimiento.puede(TipoMovimiento.AJUSTE, turnoId = 7, turnoAbierto = 7))
        assertFalse("otra acción no: se corrige desde su venta", CorreccionMovimiento.puede(TipoMovimiento.VENTA, 7, 7))
        assertFalse("una alta nace con el artículo", CorreccionMovimiento.puede(TipoMovimiento.ALTA, 7, 7))
        assertFalse("turno ya cerrado", CorreccionMovimiento.puede(TipoMovimiento.AJUSTE, 6, 7))
        assertFalse("sin turno (ajuste fuera de turno) no hay turno que abrir", CorreccionMovimiento.puede(TipoMovimiento.AJUSTE, null, 7))
        assertFalse("sin turno abierto", CorreccionMovimiento.puede(TipoMovimiento.AJUSTE, 7, null))
    }

    @Test fun laDiferenciaEsLoQueSeSumaAhoraAlStock() {
        assertEquals(45L, CorreccionMovimiento.diferencia(5L, 50L))
        assertEquals(-45L, CorreccionMovimiento.diferencia(50L, 5L))
        assertEquals(0L, CorreccionMovimiento.diferencia(5L, 5L))
    }

    @Test fun leeUnidadesDeProductoConSigno() {
        assertEquals(5L, CorreccionMovimiento.leer("5", TipoEntidad.PRODUCTO))
        assertEquals(5L, CorreccionMovimiento.leer(" +5 ", TipoEntidad.PRODUCTO))
        assertEquals(-5L, CorreccionMovimiento.leer("-5", TipoEntidad.PRODUCTO))
        assertEquals(0L, CorreccionMovimiento.leer("0", TipoEntidad.PRODUCTO))
        assertNull(CorreccionMovimiento.leer("", TipoEntidad.PRODUCTO))
        assertNull(CorreccionMovimiento.leer("dos", TipoEntidad.PRODUCTO))
        assertNull(CorreccionMovimiento.leer("-", TipoEntidad.PRODUCTO))
    }

    @Test fun leeLaUnidadDelInsumoHastaTresDecimales() {
        assertEquals(1250L, CorreccionMovimiento.leer("1.25", TipoEntidad.INSUMO))
        assertEquals(-500L, CorreccionMovimiento.leer("-0.5", TipoEntidad.INSUMO))
        assertEquals(3L, CorreccionMovimiento.leer("0.003", TipoEntidad.INSUMO))
        assertNull("cuatro decimales no son una cantidad", CorreccionMovimiento.leer("0.1234", TipoEntidad.INSUMO))
        assertNull(CorreccionMovimiento.leer("kg", TipoEntidad.INSUMO))
    }

    @Test fun escribeElTextoEditable() {
        assertEquals("5", CorreccionMovimiento.texto(5L, TipoEntidad.PRODUCTO))
        assertEquals("-3", CorreccionMovimiento.texto(-3L, TipoEntidad.PRODUCTO))
        assertEquals("1.25", CorreccionMovimiento.texto(1250L, TipoEntidad.INSUMO))
        assertEquals("-1.25", CorreccionMovimiento.texto(-1250L, TipoEntidad.INSUMO))
        assertEquals("1000", CorreccionMovimiento.texto(1_000_000L, TipoEntidad.INSUMO)) // 1000 con separador de miles fuera
        assertEquals("0", CorreccionMovimiento.texto(0L, TipoEntidad.INSUMO))
    }

    @Test fun laNotaCuentaQueSeCorrigioYPorQue() {
        assertEquals("Corregido: llegó el pedido doble", CorreccionMovimiento.nota(null, "  llegó   el pedido doble "))
        assertEquals("Compra · Corregido: merma", CorreccionMovimiento.nota("Compra", "merma"))
        assertEquals("Corregido: merma", CorreccionMovimiento.nota("  ", "merma"))
    }
}
