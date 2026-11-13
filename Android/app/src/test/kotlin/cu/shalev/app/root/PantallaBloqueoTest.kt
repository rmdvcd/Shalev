package cu.shalev.app.root

import cu.shalev.app.bloqueo.PantallaBloqueo
import cu.shalev.app.bloqueo.atras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** El bloqueo abre directamente Licencia; Soporte vuelve a Licencia; atrás en Licencia sale de la app. */
class PantallaBloqueoTest {
    @Test fun navegacionAtras() {
        assertEquals(PantallaBloqueo.LICENCIA, PantallaBloqueo.SOPORTE.atras())
        assertNull(PantallaBloqueo.LICENCIA.atras())
    }

    @Test fun licenciaEsLaPrimeraPantalla() {
        assertEquals(PantallaBloqueo.LICENCIA, PantallaBloqueo.entries.first())
    }
}
