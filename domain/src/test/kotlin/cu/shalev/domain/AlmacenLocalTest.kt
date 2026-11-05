package cu.shalev.domain

import cu.shalev.domain.repository.NombresAlmacenLocal
import org.junit.Assert.assertEquals
import org.junit.Test

class AlmacenLocalTest {
    @Test fun normalizaEspaciosSinPerderAcentos() {
        assertEquals("Almacén central", NombresAlmacenLocal.validar("  Almacén\n  central  "))
    }
    @Test(expected = IllegalArgumentException::class) fun rechazaVacio() {
        NombresAlmacenLocal.validar("  ")
    }
    @Test(expected = IllegalArgumentException::class) fun rechazaNombreLargo() {
        NombresAlmacenLocal.validar("a".repeat(81))
    }
    @Test fun admiteLimite() { assertEquals(80, NombresAlmacenLocal.validar("a".repeat(80)).length) }
}
