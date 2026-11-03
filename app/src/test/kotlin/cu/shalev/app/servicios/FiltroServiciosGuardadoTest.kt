package cu.shalev.app.servicios

import cu.shalev.domain.model.FiltroServicios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Permanencia del filtro de Servicios al cerrar la app: solo el tipo, no el texto del buscador. */
class FiltroServiciosGuardadoTest {

    @Test fun idaYVuelta() {
        val vuelta = FiltroServiciosGuardado.decodificar(FiltroServiciosGuardado.codificar(FiltroServicios(texto = "corte", tipo = "Peluquería")))
        assertEquals(FiltroServicios(tipo = "Peluquería"), vuelta)
    }

    @Test fun sinFiltroSeOlvidaElAjuste() {
        assertNull(FiltroServiciosGuardado.codificar(FiltroServicios()))
        assertNull(FiltroServiciosGuardado.decodificar(null))
        assertNull(FiltroServiciosGuardado.decodificar("  "))
    }
}
