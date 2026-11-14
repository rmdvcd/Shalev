package cu.shalev.app.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AsistenteAltaTest {
    @Test fun transicionesSeMantienenDentroDeLosPasos() {
        assertEquals(1, NavegacionAsistenteAlta.anterior(1, 3))
        assertEquals(2, NavegacionAsistenteAlta.anterior(3, 3))
        assertEquals(2, NavegacionAsistenteAlta.siguiente(1, 3))
        assertEquals(3, NavegacionAsistenteAlta.siguiente(3, 3))
    }

    @Test fun erroresLlevanAlPrimerPasoCorrespondiente() {
        val pasos = listOf(setOf("nombre", "categoria"), setOf("costo", "receta"), setOf("precio", "cantidad"))
        assertEquals(1, NavegacionAsistenteAlta.pasoConError(setOf("precio", "nombre"), pasos))
        assertEquals(3, NavegacionAsistenteAlta.pasoConError(setOf("cantidad"), pasos))
        assertNull(NavegacionAsistenteAlta.pasoConError(setOf("foto"), pasos))
    }
}
