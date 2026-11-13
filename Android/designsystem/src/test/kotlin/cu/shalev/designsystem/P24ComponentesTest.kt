package cu.shalev.designsystem

import cu.shalev.designsystem.component.ChartMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P24: intensidad de las barras de Ventas según la escala Y. */
class P24ComponentesTest {
    @Test fun intensidadCreceConElValorYNuncaDesaparece() {
        assertEquals(ChartMath.INTENSIDAD_MIN, ChartMath.intensidad(0f), 1e-6f)
        assertEquals(1f, ChartMath.intensidad(1f), 1e-6f)
        assertEquals(0.65f, ChartMath.intensidad(0.5f), 1e-6f)
        // Fuera de rango se recorta (valores negativos o por encima del máximo de la escala).
        assertEquals(ChartMath.INTENSIDAD_MIN, ChartMath.intensidad(-3f), 1e-6f)
        assertEquals(1f, ChartMath.intensidad(7f), 1e-6f)
        assertTrue(ChartMath.intensidad(0.2f) < ChartMath.intensidad(0.8f))
    }
}
