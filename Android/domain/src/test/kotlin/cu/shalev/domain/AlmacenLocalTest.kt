package cu.shalev.domain

import cu.shalev.domain.repository.NombresLocal
import org.junit.Assert.assertEquals
import org.junit.Test

class AlmacenLocalTest {
    @Test fun normalizaEspaciosSinPerderAcentos() {
        assertEquals("Almacén central", NombresLocal.validar("  Almacén\n  central  "))
    }
    @Test(expected = IllegalArgumentException::class) fun rechazaVacio() {
        NombresLocal.validar("  ")
    }
    @Test(expected = IllegalArgumentException::class) fun rechazaNombreLargo() {
        NombresLocal.validar("a".repeat(81))
    }
    @Test fun admiteLimite() { assertEquals(80, NombresLocal.validar("a".repeat(80)).length) }
}
