package cu.shalev.app.inicio

import cu.shalev.designsystem.component.ChartMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {

    @Test fun normalizaContraElMaximo() {
        assertEquals(listOf(0.5f, 1f, 0f), ChartMath.normalizar(listOf(50.0, 100.0, -10.0)))
        assertEquals(listOf(0f, 0f), ChartMath.normalizar(listOf(0.0, 0.0)))
        assertTrue(ChartMath.normalizar(emptyList()).isEmpty())
    }

    @Test fun seriesJuntasCompartenEscala() {
        val (venta, costo) = ChartMath.normalizarJuntas(listOf(listOf(200.0, 100.0), listOf(100.0, 50.0)))
        assertEquals(listOf(1f, 0.5f), venta)
        assertEquals(listOf(0.5f, 0.25f), costo)
    }

    @Test fun arcosCubrenLaVueltaConSeparacion() {
        val a = ChartMath.arcos(listOf(0.75, 0.25), separacion = 2f)
        assertEquals(-89f, a[0].first, 1e-4f)            // empieza a las 12 en punto (+ medio hueco)
        assertEquals(268f, a[0].second, 1e-4f)
        assertEquals(88f, a[1].second, 1e-4f)
        assertEquals(360f, a.sumOf { (it.second + 2f).toDouble() }.toFloat(), 1e-3f)
        assertEquals(listOf(-90f to 360f), ChartMath.arcos(listOf(1.0)))  // una sola porción: sin hueco
        assertTrue(ChartMath.arcos(listOf(0.0)).isEmpty())
    }

    @Test fun pocasEtiquetasEnElEjeX() {
        assertEquals(listOf(0, 1, 2), ChartMath.indicesEtiquetas(3))
        assertEquals(listOf(0, 8, 15, 23), ChartMath.indicesEtiquetas(24))
        assertTrue(ChartMath.indicesEtiquetas(0).isEmpty())
    }
}
