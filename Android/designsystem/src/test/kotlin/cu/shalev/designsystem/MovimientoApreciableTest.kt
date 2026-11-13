package cu.shalev.designsystem

import cu.shalev.designsystem.token.ShalevMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.28.1: las transiciones entre ventanas y las ventanas emergentes se tienen que apreciar en el teléfono (antes el
 * deslizamiento se completaba en <200 ms). Fija el ajuste para que no se vuelva a endurecer sin querer: muelle de
 * entrada blando con leve asentamiento. 0.31.0 (P10): al tocar la barra inferior el cambio es un fundido; lo que se
 * nota es el desplazamiento lateral al deslizar entre secciones.
 */
class MovimientoApreciableTest {
    @Test fun muelleDeEntradaBlandoConLeveAsentamiento() {
        val muelle = ShalevMotion.muelleEntrada<Float>()
        assertEquals(350f, muelle.stiffness)
        assertEquals(0.85f, muelle.dampingRatio)
        // Con asentamiento (amortiguación < 1), no el muelle seco de las salidas.
        assertTrue(muelle.dampingRatio < 1f)
        assertEquals(1f, ShalevMotion.muelle<Float>().dampingRatio)
        assertTrue(muelle.dampingRatio < ShalevMotion.muelle<Float>().dampingRatio)
    }

    @Test fun lasVentanasEmergentesNoSubenDesdeElBorde() {
        // 0.31.0 (P10): las ventanas y los diálogos «saltan» desde el centro de abajo. Se comprueba que el token
        // existe y sigue siendo un desplazamiento vertical (la mitad del recorrido, no el borde de la pantalla).
        assertNotNull(ShalevMotion.ventanaEntra)
        assertNotNull(ShalevMotion.ventanaSale)
        assertEquals(100, ShalevMotion.desplazamientoVentana(200))
        assertEquals(0, ShalevMotion.desplazamientoVentana(0))
    }
}
