package cu.shalev.app.herramientas

import cu.shalev.domain.fichacosto.MonedaCosto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Permanencia de la calculadora de cambio: operación, moneda y tasa sobreviven al cierre de la app. */
class CalculadoraGuardadaTest {

    @Test fun idaYVuelta() {
        val a = AjustesCalculadora(ModoCambio.TASA, MonedaCosto.CUP, "350.50")
        assertEquals(a, CalculadoraGuardada.decodificar(CalculadoraGuardada.codificar(a)))
        val defecto = AjustesCalculadora()
        assertEquals(defecto, CalculadoraGuardada.decodificar(CalculadoraGuardada.codificar(defecto)))
    }

    @Test fun valorDanadoSeIgnora() {
        assertNull(CalculadoraGuardada.decodificar(null))
        assertNull(CalculadoraGuardada.decodificar("OTRO\u001FUSD\u001F350"))
        assertNull(CalculadoraGuardada.decodificar("CONVERTIR\u001FEUR\u001F350"))
        assertNull(CalculadoraGuardada.decodificar("CONVERTIR\u001FUSD\u001Fno-es-tasa"))
    }
}
