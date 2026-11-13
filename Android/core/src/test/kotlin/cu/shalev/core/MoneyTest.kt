package cu.shalev.core

import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
import cu.shalev.core.money.Percent
import cu.shalev.core.quantity.Cantidad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun `formato oficial de Shalev`() {
        assertEquals("1,450 CUP", Money.format(Cup.ofPesos(1450)))
        assertEquals("0.05 CUP", Money.format(Cup(5)))
        assertEquals("1,234,567.89 CUP", Money.format(Cup(123_456_789)))
        assertEquals("-12.5 CUP", Money.format(Cup(-1250)))
        assertEquals("90,000 CUP", Money.cup(90_000))
    }

    @Test fun `parse acepta lo que teclea el usuario`() {
        assertEquals(Cup(145_000), Money.parse("1450"))
        assertEquals(Cup(145_050), Money.parse("1450.5"))
        assertEquals(Cup(145_000), Money.parse("1,450.00"))
        assertEquals(Cup(145_000), Money.parse(" 1,450 CUP "))
        assertNull(Money.parse("1.455"))
        assertNull(Money.parse("14,50"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
    }

    @Test fun `ajuste porcentual con redondeo half up`() {
        assertEquals(Cup(11_000), Cup(10_000).ajustar(1000))   // +10 %
        assertEquals(Cup(9_450), Cup(10_000).ajustar(-550))    // -5.5 %
        assertEquals(Cup(34), Cup(33).ajustar(250))            // 33 × 1.025 = 33.825 → 34
        assertEquals(Cup(0), Cup(10_000).ajustar(-10_000))
    }

    @Test fun `importe por milesimas`() {
        assertEquals(Cup(3_125), Cup(12_500).porMilesimas(250))  // 125.00 × 0.25 = 31.25
        assertEquals(Cup(1), Cup(3).porMilesimas(333))           // 0.03 × 0.333 = 0.00999 → 0.01
    }

    @Test fun formatoYEdicionConservanCadaCentavoYElSigno() {
        listOf(0L, 1L, -1L, 2550L, -2550L, Long.MAX_VALUE, Long.MIN_VALUE).forEach { centavos ->
            val importe = Cup(centavos)
            assertEquals(importe, Money.parse(Money.format(importe)))
            assertEquals(importe, Money.parse(Money.editar(importe)))
        }
    }

    @Test fun porcentajes() {
        assertEquals("+12.5 %", Percent.format(1250))
        assertEquals("-5 %", Percent.format(-500))
        assertEquals(1250, Percent.parse("12.5"))
        assertEquals(-500, Percent.parse("-5 %"))
        assertEquals(1000, Percent.parse("+10"))
        assertNull(Percent.parse("1.234"))
    }

    @Test fun cantidades() {
        assertEquals(Cantidad(1250), Cantidad.parse("1.25"))
        assertEquals(Cantidad(3000), Cantidad.parse("3"))
        assertNull(Cantidad.parse("-1"))
        assertNull(Cantidad.parse("1.2345"))
        assertEquals("1.25", Cantidad.format(Cantidad(1250)))
        assertEquals("1,000", Cantidad.format(Cantidad.enteras(1000)))
    }
}
