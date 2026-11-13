package cu.shalev.designsystem.ilustracion

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

/** P24 añade nueve ilustraciones y P29 añade Servicios; se comprueban las 10 actuales en claro y oscuro. */
class ShalevIlustracionesTest {
    @Test fun todasSeConstruyenEnClaroYOscuro() {
        ShalevIlustracion.entries.forEach { tipo ->
            assertTrue("$tipo sin trazados", trazos(tipo) > 0)
            listOf(false, true).forEach { oscuro ->
                val v = construir(tipo, Color(0xFF2B4FA3), oscuro, 180.dp)
                assertTrue("$tipo: viewport", v.viewportWidth > 0f && v.viewportHeight > 0f)
                assertTrue("$tipo: ancho", v.defaultWidth == 180.dp)
            }
        }
    }
}
