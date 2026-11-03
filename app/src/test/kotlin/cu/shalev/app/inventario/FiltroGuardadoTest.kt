package cu.shalev.app.inventario

import cu.shalev.domain.model.FiltroInventario
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.TipoArticulo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Permanencia del filtro del Inventario al cerrar la app: ida y vuelta, sin el texto del buscador. */
class FiltroGuardadoTest {

    @Test fun idaYVueltaSinTexto() {
        val f = FiltroInventario(texto = "pan", categoria = "Panadería", alerta = TipoAlerta.STOCK_BAJO, tipo = TipoArticulo.ELABORADOS)
        assertEquals(f.copy(texto = ""), FiltroInventarioGuardado.decodificar(FiltroInventarioGuardado.codificar(f)))
    }

    @Test fun filtroVacioTambienViaja() {
        assertEquals(FiltroInventario(), FiltroInventarioGuardado.decodificar(FiltroInventarioGuardado.codificar(FiltroInventario())))
    }

    @Test fun valorDanadoODeOtraVersionSeIgnora() {
        assertNull(FiltroInventarioGuardado.decodificar(null))
        assertNull(FiltroInventarioGuardado.decodificar("solo-esto"))
        assertNull(FiltroInventarioGuardado.decodificar("a\u001FNO_EXISTE\u001FTODOS"))
        assertNull(FiltroInventarioGuardado.decodificar("a\u001F\u001FTIPO_FUTURO"))
    }
}
