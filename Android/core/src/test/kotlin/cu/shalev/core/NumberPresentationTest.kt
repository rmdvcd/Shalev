package cu.shalev.core

import cu.shalev.core.format.NumberPresentation
import org.junit.Assert.assertEquals
import org.junit.Test

class NumberPresentationTest {
    @Test fun enterosAgrupanMilesSoloParaPresentacion() {
        assertEquals("0", NumberPresentation.groupedInteger(0))
        assertEquals("12", NumberPresentation.groupedInteger(12))
        assertEquals("1\u202F234\u202F567", NumberPresentation.groupedInteger(1_234_567))
        assertEquals("-1\u202F234", NumberPresentation.groupedInteger(-1_234))
        assertEquals("-9\u202F223\u202F372\u202F036\u202F854\u202F775\u202F808", NumberPresentation.groupedInteger(Long.MIN_VALUE))
    }

    @Test fun telefonosUsanGruposEstrechosSinReescribirLaFormaInterna() {
        assertEquals("+53\u202F5123\u202F4567", NumberPresentation.phone("+5351234567"))
        assertEquals("+53\u202F5123\u202F4567", NumberPresentation.phone("51234567"))
        assertEquals("+12345678", NumberPresentation.phone("+12345678"))
        assertEquals("+34600111222", NumberPresentation.phone("+34600111222"))
    }

    @Test fun tarjetasSeAgrupanConGuionesYLasEnmascaradasSiguenOcultas() {
        assertEquals("9205-1299-0000-1234", NumberPresentation.card("9205129900001234"))
        assertEquals("•••• 8054", NumberPresentation.card("•••• 8054"))
    }
}
