package cu.shalev.app.precios

import cu.shalev.app.prod
import cu.shalev.core.money.Cup
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.PreajustePrecios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreciosLogicTest {

    @Test fun porcentajeConSignoSegunSubirOBajar() {
        assertEquals(1000, PreajusteForm(porcentaje = "10").puntosBasicos)
        assertEquals(-1250, PreajusteForm(porcentaje = "12,5", sube = false).puntosBasicos)
        assertNull(PreajusteForm(porcentaje = "-5").puntosBasicos) // el signo lo decide Subir/Bajar
        assertNull(PreajusteForm(porcentaje = "abc").puntosBasicos)
        assertNull(PreajusteForm(porcentaje = "0").puntosBasicos)
    }

    @Test fun validacionEnPalabrasSimples() {
        val vacio = validar(PreajusteForm())
        assertEquals(TextosPrecios.ERROR_PORCENTAJE, vacio[CampoPrecio.PORCENTAJE])
        assertEquals(TextosPrecios.ERROR_PRODUCTOS, vacio[CampoPrecio.PRODUCTOS])
        assertEquals(TextosPrecios.ERROR_SUBIR, validar(PreajusteForm(porcentaje = "101", productoIds = setOf(1)))[CampoPrecio.PORCENTAJE])
        assertEquals(TextosPrecios.ERROR_BAJAR, validar(PreajusteForm(porcentaje = "91", sube = false, productoIds = setOf(1)))[CampoPrecio.PORCENTAJE])
        assertTrue(validar(PreajusteForm(porcentaje = "5", productoIds = setOf(1))).isEmpty())
        assertTrue(validar(PreajusteForm(porcentaje = "5", servicioIds = setOf(2))).isEmpty())
    }

    @Test fun nombreAutomaticoSiNoSeEscribe() {
        val f = PreajusteForm(porcentaje = "10", metodo = MetodoPago.TRANSFERENCIA, importeMinimo = "5000", productoIds = setOf(1))
        assertEquals("+10 % con Transferencia", nombreSugerido(f))
        assertEquals("+10 % con Transferencia", aPreajuste(f).nombre)
        assertEquals("Mayoreo", aPreajuste(f.copy(nombre = "  Mayoreo ")).nombre)
        assertEquals("-5 %", nombreSugerido(PreajusteForm(porcentaje = "5", sube = false)))
    }

    @Test fun idaYVueltaFormularioPreajuste() {
        val p = PreajustePrecios(7, "X", -1250, setOf(1, 2), MetodoPago.EFECTIVO, Cup.ofPesos(1000), activo = false)
        val f = desde(p)
        assertEquals("12.5", f.porcentaje)
        assertEquals(false, f.sube)
        assertEquals("", f.importeMinimo)
        assertEquals(p.copy(importeMinimo = null), aPreajuste(f))
    }

    @Test fun vistaPreviaConElPrimerProductoElegido() {
        val ps = listOf(prod(1, "Refresco", venta = 100), prod(2, "Pan", venta = 50))
        assertEquals("Pan: 50 CUP → 55 CUP", vistaPrevia(PreajusteForm(porcentaje = "10", productoIds = setOf(2)), ps))
        assertEquals("Refresco: 100 CUP → 10 CUP", vistaPrevia(PreajusteForm(porcentaje = "90", sube = false, productoIds = setOf(1)), ps))
        assertNull(vistaPrevia(PreajusteForm(porcentaje = "10"), ps))
    }

    @Test fun resumenYBusqueda() {
        val p = PreajustePrecios(1, "X", 500, setOf(1, 2, 3), MetodoPago.TRANSFERENCIA, Cup.ofPesos(5000))
        assertEquals("Solo Transferencia · 2 artículos", resumenPreajuste(p, existentes = 2))
        assertEquals("Cualquier pago · 1 artículo", resumenPreajuste(p.copy(metodoPago = null, importeMinimo = null, productoIds = setOf(1))))
        val ps = (1L..60L).map { prod(it, if (it == 7L) "Café Serrano" else "P$it") }
        assertEquals(TextosPrecios.MAX_LISTA, filtrar(ps, "").size)
        assertEquals(listOf(7L), filtrar(ps, "café").map { it.id })
    }
}
