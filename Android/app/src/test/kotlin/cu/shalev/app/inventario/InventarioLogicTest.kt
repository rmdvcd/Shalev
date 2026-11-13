package cu.shalev.app.inventario

import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.EstadoCaducidad
import cu.shalev.domain.model.NivelStock
import cu.shalev.domain.model.ItemInventario
import cu.shalev.app.common.NombresArchivo
import cu.shalev.app.prod
import cu.shalev.domain.model.FiltroInventario
import cu.shalev.domain.model.TipoAlerta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventarioLogicTest {
    @Test fun tarjetasDeSeisPorImagen() {
        val ps = (1L..13L).map { prod(it) }
        assertEquals(listOf(6, 6, 1), DisenoTarjetas.grupos(ps).map { it.size })
        assertEquals(1, DisenoTarjetas.columnas(1))
        assertEquals(2, DisenoTarjetas.columnas(5))
        assertEquals(3, DisenoTarjetas.filas(6))
        assertEquals(1350, DisenoTarjetas.alto(1)) // 4:5, formato cómodo en WhatsApp
        assertEquals(40 * 2 + 3 * 600 + 2 * 24, DisenoTarjetas.alto(6))
        assertTrue(DisenoTarjetas.alto(2) < DisenoTarjetas.alto(3))
    }

    @Test fun nombresDeArchivoSeguros() {
        assertEquals("Ficha_Cafe_Serrano.pdf", NombresArchivo.sanear("Ficha: Café Serrano.pdf"))
        assertEquals("etc_passwd", NombresArchivo.sanear("../../etc/passwd"))
        assertEquals("shalev", NombresArchivo.sanear("¿¿??"))
        assertEquals(80, NombresArchivo.sanear("a".repeat(200)).length)
    }

    @Test fun filtroDeEstadoSoloOfreceLasCincoOpciones() {
        assertEquals(
            listOf(null, TipoAlerta.STOCK_BAJO, TipoAlerta.STOCK_CRITICO, TipoAlerta.PROXIMO_A_CADUCAR, TipoAlerta.SIN_EXISTENCIA),
            ALERTAS_FILTRO,
        )
        assertEquals("Stock bajo", etiqueta(TipoAlerta.STOCK_BAJO))
    }

    @Test fun formatosDisponibles() {        // 0.26.0: sin Texto. PDF y Excel son de uso interno (llevan costos); Imagen y Tarjetas son para clientes.
        assertEquals(listOf(FormatoSalida.PDF, FormatoSalida.EXCEL, FormatoSalida.IMAGEN, FormatoSalida.TARJETAS), FormatoSalida.EXPORTAR)
        assertEquals("Incluye costos", FormatoSalida.PDF.destinatario)
        assertEquals("Para clientes (sin costos)", FormatoSalida.IMAGEN.destinatario)
        assertTrue(FormatoSalida.PDF.interno && FormatoSalida.EXCEL.interno && !FormatoSalida.TARJETAS.interno)
        assertEquals(listOf(FormatoSalida.IMAGEN, FormatoSalida.TARJETAS), FormatoSalida.COMPARTIR_FICHA)
        assertFalse(FormatoSalida.entries.any { it.name == "TEXTO" })
        // Solo un archivo por «Guardar como»: imagen y tarjetas pueden ser varias → solo se envían.
        assertEquals(listOf(FormatoSalida.PDF, FormatoSalida.EXCEL), FormatoSalida.entries.filter { it.guardable })
    }

    @Test fun resumenDelFiltroYTextos() {
        assertNull(resumenFiltro(FiltroInventario(texto = "pan")))
        assertTrue(resumenFiltro(FiltroInventario(alerta = TipoAlerta.STOCK_CRITICO, categoria = "Dulces"))!!.contains("Dulces"))
        assertEquals("3 de 10 productos", TextosInventario.contador(3, 10))
        assertEquals("1 producto", TextosInventario.contador(1, 1))
        assertEquals("¿Eliminar 1 producto?", TextosInventario.eliminarVarios(1))
    }

    /** P26: la fila de un Elaborado dice cuánto alcanza con sus insumos; 0 se marca como peligro (con texto). */
    @Test fun filaDeElaboradoMuestraAlcance() {
        val pan = prod(9, "Pan", cantidad = 7, categoria = Categorias.ELABORADO)
        assertEquals("Alcanza para 3", valorFila(ItemInventario(pan, NivelStock.NORMAL, EstadoCaducidad.SIN_FECHA, alcanza = 3)))
        val agotado = ItemInventario(pan, NivelStock.NORMAL, EstadoCaducidad.SIN_FECHA, alcanza = 0)
        assertEquals("Sin insumos suficientes", valorFila(agotado))
        assertEquals(TonoFila.PELIGRO, tonoDe(agotado))
        assertEquals("7 u", valorFila(ItemInventario(prod(1, cantidad = 7), NivelStock.NORMAL, EstadoCaducidad.SIN_FECHA)))
    }
}
