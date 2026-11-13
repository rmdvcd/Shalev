package cu.shalev.core

import cu.shalev.core.time.Dates
import cu.shalev.core.time.FormatoFecha
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** P24: las fechas se muestran con el formato del dispositivo. */
class FormatoFechaTest {
    private val instante = LocalDateTime.of(2026, 9, 30, 14, 5).toInstant(ZoneOffset.UTC)

    @After fun restaurar() { Dates.formato = FormatoFecha.PREDETERMINADO }

    @Test fun predeterminadoEsDiaMesAnio24h() {
        assertEquals("30/09/2026", Dates.day(LocalDate.of(2026, 9, 30)))
        assertEquals("30/09/2026 14:05", Dates.dayTime(instante, ZoneOffset.UTC))
    }

    @Test fun usaElFormatoDelDispositivo() {
        Dates.formato = FormatoFecha.crear("M/d/yy", "M/d", "h:mm a")
        assertEquals("9/30/26", Dates.day(LocalDate.of(2026, 9, 30)))
        assertEquals("9/30", Dates.dayMonth(instante, ZoneOffset.UTC))
    }

    @Test fun patronInvalidoVuelveAlPredeterminado() {
        assertSame(FormatoFecha.PREDETERMINADO, FormatoFecha.crear("dd/MM/yyyy", "dd/MM", "{{"))
    }
}
