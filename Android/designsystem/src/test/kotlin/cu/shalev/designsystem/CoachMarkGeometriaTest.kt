package cu.shalev.designsystem

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import cu.shalev.designsystem.component.CoachMarkGeometria
import cu.shalev.designsystem.component.CoachMarkPaso
import cu.shalev.designsystem.component.CoachMarkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recorrido guiado: dónde va el globo y cómo avanza el estado (sin Compose UI). */
class CoachMarkGeometriaTest {
    private val pantalla = IntSize(1000, 2000)
    private val globo = IntSize(600, 400)
    private val margen = 32
    private val separacion = 24

    private fun posicion(foco: Rect?) = CoachMarkGeometria.posicionGlobo(pantalla, globo, foco, margen, separacion)

    @Test fun objetivoArribaPoneElGloboDebajoCentradoSobreElObjetivo() {
        val p = posicion(Rect(400f, 100f, 600f, 200f))
        assertEquals(IntOffset(200, 224), p)
    }

    @Test fun objetivoAbajoPoneElGloboEncima() {
        val p = posicion(Rect(800f, 1800f, 950f, 1900f))
        // Encima del objetivo y pegado al margen derecho (no se sale de la pantalla).
        assertEquals(IntOffset(1000 - 600 - margen, 1800 - separacion - 400), p)
    }

    @Test fun sinObjetivoElGloboVaCentrado() {
        assertEquals(IntOffset(200, 800), posicion(null))
    }

    @Test fun objetivoQueOcupaCasiTodoDejaElGloboDentroDeLaPantalla() {
        val p = posicion(Rect(0f, 50f, 1000f, 1950f))
        assertTrue(p.y >= margen && p.y + globo.height <= pantalla.height - margen)
        assertTrue(p.x >= margen && p.x + globo.width <= pantalla.width - margen)
    }

    @Test fun globoMasAnchoQueLaPantallaQuedaEnElMargen() {
        val p = CoachMarkGeometria.posicionGlobo(IntSize(500, 2000), IntSize(600, 300), Rect(0f, 0f, 100f, 100f), margen, separacion)
        assertEquals(margen, p.x)
    }

    @Test fun estadoSoloRecorreLosPasosSinObjetivoNoInicia() {
        val estado = CoachMarkState(listOf(CoachMarkPaso("a", "A", "a"), CoachMarkPaso("b", "B", "b")))
        assertFalse(estado.hayObjetivos())
        assertFalse(estado.iniciar())
        assertFalse(estado.activo)
        assertNull(estado.pasoActual)
    }
}
