package cu.shalev.core

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.time.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 0.27.0: sustitutos de `LocalDate.ofInstant` (API 34) y `BigInteger.longValueExact` (API 31), que cerraban la app
 * en Android 8–13 / 8–11. Comprueba que se comportan igual que los originales.
 */
class ApiMinimaTest {
    @Test fun localDateUsaElDiaDeLaZona() {
        val i = Instant.parse("2026-10-05T03:30:00Z")
        assertEquals(LocalDate.of(2026, 10, 4), Dates.localDate(i, ZoneId.of("America/Havana")))
        assertEquals(LocalDate.of(2026, 10, 5), Dates.localDate(i, ZoneId.of("Europe/Berlin")))
    }

    @Test fun cupOfRedondeaYDesbordaComoAntes() {
        assertEquals(Cup(12346), Cup.of(BigDecimal("123.455")))
        assertEquals(Cup(-150), Cup.of(BigDecimal("-1.5")))
        val r = runCatching { Cup.of(BigDecimal("1e20")) }
        assertTrue(r.exceptionOrNull() is ArithmeticException)
    }

    @Test fun cantidadParseSigueIgual() {
        assertEquals(Cantidad(12_500), Cantidad.parse("12.5"))
        assertEquals(Cantidad(3_000), Cantidad.parse("3"))
        assertNull(Cantidad.parse("1.2345"))
        assertNull(Cantidad.parse("99999999999999999999"))
    }
}
