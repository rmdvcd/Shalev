package cu.shalev.app.licencia

import cu.shalev.core.money.Money
import cu.shalev.licencia.contract.TipoLicencia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.30.1: la Card «Precios de la licencia» muestra, por tipo, el precio base y lo que suma cada app secundaria. */
class PreciosLicenciaTest {
    @Test fun hayUnaFilaPorCadaTipoDeLicencia() {
        assertEquals(4, TipoLicencia.entries.size)
        TipoLicencia.entries.forEach { t ->
            assertTrue(Money.cup(t.precioCup).isNotBlank())
            assertTrue(t.precioCup > 0 && t.precioSecundariaCup > 0)
        }
    }

    @Test fun elTextoDeSecundariaUsaElImporteDelTipo() {
        TipoLicencia.entries.forEach { t ->
            val texto = TextosLicencia.precioSecundaria(t)
            assertTrue(texto, texto.startsWith("Cada app secundaria: +"))
            assertTrue(texto, texto.endsWith(Money.cup(t.precioSecundariaCup)))
        }
    }

    @Test fun elTextoGeneralCitaElMaximoDeSecundarias() {
        assertTrue(TextosLicencia.PRECIOS_DETALLE.contains("hasta 10"))
        assertEquals("Precios de la licencia", TextosLicencia.PRECIOS_TITULO)
    }

    @Test fun lasSecundariasSumanAlTotalSegunElPrecio() {
        TipoLicencia.entries.forEach { t -> assertEquals(t.precioCup + 3 * t.precioSecundariaCup, t.precio(3)) }
    }
}
