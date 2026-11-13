package cu.shalev.app.common

import cu.shalev.core.time.Dates
import cu.shalev.core.time.FormatoFecha
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** P24: la guía del campo de fecha sigue el formato del dispositivo. */
class FechasUiTest {
    @After fun restaurar() { Dates.formato = FormatoFecha.PREDETERMINADO }

    @Test fun guiaSegunElFormato() {
        assertEquals("dd/mm/aaaa", FechasUi.guia())
        Dates.formato = FormatoFecha.crear("M/d/yy", "M/d", "h:mm a")
        assertEquals("m/d/aa", FechasUi.guia())
    }
}
