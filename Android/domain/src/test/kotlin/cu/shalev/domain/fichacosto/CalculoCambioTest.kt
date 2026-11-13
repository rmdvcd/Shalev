package cu.shalev.domain.fichacosto

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculoCambioTest {
    private fun n(s: String) = BigDecimal(s)

    @Test fun convierteEnAmbosSentidosConRedondeo() {
        assertEquals(n("801.00"), CalculoCambio.convertir(n("2"), n("400.5"), MonedaCosto.USD))
        assertEquals(n("2.00"), CalculoCambio.convertir(n("801"), n("400.5"), MonedaCosto.CUP))
        assertEquals(n("0.33"), CalculoCambio.convertir(n("1"), n("3"), MonedaCosto.CUP))
        assertEquals(n("0.00"), CalculoCambio.convertir(n("0"), n("3"), MonedaCosto.USD))
    }

    @Test fun calculaTasaDesdeImportesSinConvertirMonedas() {
        assertEquals(0, CalculoCambio.tasa(n("700"), n("2")).compareTo(n("350")))
        assertEquals(n("0.333333333333"), CalculoCambio.tasa(n("1"), n("3")))
        val pequena = CalculoCambio.tasa(n("0.001"), n("999999999"))
        assertTrue(pequena.signum() > 0)
        assertEquals(0, pequena.compareTo(CalculoCambio.leerTasa(pequena.toPlainString())))
        val grande = CalculoCambio.tasa(n("999999999999"), n("0.001"))
        assertEquals(0, grande.compareTo(CalculoCambio.leerTasa(grande.toPlainString())))
    }

    @Test fun aceptaTasasConPrecisionYRechazaEntradasInvalidas() {
        assertEquals(n("350.125"), CalculoCambio.leerTasa("350,125"))
        assertEquals(n("0.000000000001"), CalculoCambio.leerTasa("0.000000000001"))
        listOf("", "0", "-1", "1e3", "1.2.3", "1.", "1234567890123456789").forEach {
            assertNull(CalculoCambio.leerTasa(it))
        }
        assertTrue(CalculoCambio.entradaTasaValida("1,"))
    }

    @Test fun rechazaImportesNegativosYDivisorCero() {
        assertThrows(IllegalArgumentException::class.java) { CalculoCambio.convertir(n("1"), n("0"), MonedaCosto.CUP) }
        assertThrows(IllegalArgumentException::class.java) { CalculoCambio.convertir(n("-1"), n("350"), MonedaCosto.USD) }
        assertThrows(IllegalArgumentException::class.java) { CalculoCambio.tasa(n("0"), n("1")) }
        assertThrows(IllegalArgumentException::class.java) { CalculoCambio.tasa(n("1"), n("0")) }
    }
}
