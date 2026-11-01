package cu.shalev.app.navigation

import cu.shalev.designsystem.component.BloqueoGestos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El gesto de deslizar solo está activo en los 4 destinos principales y cuando no hay bloqueos. */
class DeslizarTest {
    @Test fun ajustesNoFormaParteDeLaBarraInferior() {
        assertEquals(4, TopLevel.entries.size)
        assertFalse(TopLevel.entries.any { it.route == Route.Ajustes })
    }

    @Test fun soloEnRutaRaizYSinBloqueos() {
        assertTrue(deslizarPermitido(enRutaRaiz = true, enSeleccionVenta = false, bloqueado = false))
        assertFalse(deslizarPermitido(enRutaRaiz = false, enSeleccionVenta = false, bloqueado = false)) // subpantalla
        assertFalse(deslizarPermitido(enRutaRaiz = true, enSeleccionVenta = true, bloqueado = false))  // elegir qué vender
        assertFalse(deslizarPermitido(enRutaRaiz = true, enSeleccionVenta = false, bloqueado = true))  // diálogo, «+», foco…
    }

    // 0.31.0 (P10): el deslizamiento desliza lateral; el toque en la barra inferior funde.
    @Test fun elDeslizamientoLlevaDeslizamientoLateralYElToqueFundido() {
        assertEquals(CambioSeccion.ADELANTE, cambioSeccion(1))
        assertEquals(CambioSeccion.ATRAS, cambioSeccion(-1))
        assertEquals(CambioSeccion.FUNDIDO, cambioSeccion(0))
    }

    @Test fun direccionDelGestoSoloAplicaAlOrigenYDestinoCorrespondientes() {
        val gesto = GestoSeccion("entrada-1", "INVENTARIO", -1)
        assertEquals(CambioSeccion.ATRAS, cambioSeccion(gesto, "entrada-1", "INVENTARIO"))
        assertEquals(CambioSeccion.FUNDIDO, cambioSeccion(gesto, "entrada-2", "INVENTARIO"))
        assertEquals(CambioSeccion.FUNDIDO, cambioSeccion(gesto, "entrada-1", "SERVICIOS"))
        assertEquals(CambioSeccion.FUNDIDO, cambioSeccion(null, "entrada-1", "INVENTARIO"))
        assertEquals(CambioSeccion.ADELANTE, cambioSeccion(gesto.copy(direccion = 1), "entrada-1", "INVENTARIO"))
    }

    @Test fun elBloqueoCuentaLosComponentesQueLoPiden() {
        val b = BloqueoGestos()
        assertFalse(b.activo)
        assertTrue(b.cuenta == 0)
    }
}
