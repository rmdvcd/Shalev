package cu.shalev.app.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisenoTablaTest {
    private fun filas(n: Int) = (1..n).map { listOf("Cat", "Producto $it", "1\u202F450 CUP") }

    @Test fun paginasDe25FilasSinPerderNinguna() {
        assertEquals(1, DisenoTabla.paginas(filas(25)).size)
        val p = DisenoTabla.paginas(filas(51))
        assertEquals(listOf(25, 25, 1), p.map { it.size })
        assertEquals(filas(51), p.flatten())
        assertTrue(DisenoTabla.paginas(emptyList()).isEmpty())
    }

    @Test fun altoCreceConLasFilas() {
        val base = DisenoTabla.alto(0)
        assertEquals(2 * DisenoTabla.MARGEN + DisenoTabla.TITULO + DisenoTabla.CABECERA + DisenoTabla.PIE, base)
        assertEquals(base + 25 * DisenoTabla.FILA, DisenoTabla.alto(25))
    }

    @Test fun tituloSoloNumeraSiHayVariasImagenes() {
        assertEquals("Lista de precios", DisenoTabla.titulo("Lista de precios", 1, 1))
        assertEquals("Lista de precios · 2/3", DisenoTabla.titulo("Lista de precios", 2, 3))
    }

    @Test fun anchosSumanExactoYEstanAcotados() {
        val cols = listOf("Categoría", "Producto", "Precio")
        val f = listOf(listOf("A", "x".repeat(200), "1 CUP"))
        val a = DisenoTabla.anchos(cols, f)
        assertEquals(DisenoTabla.ANCHO - 2 * DisenoTabla.MARGEN, a.sum())
        // Un nombre enorme no se come la imagen: tope de 28 caracteres frente a mínimo de 9 («Categoría»).
        assertTrue(a[1] < 3 * a[0] + 50)
        assertEquals(listOf(333, 333, 334), DisenoTabla.anchos(listOf("aaaaaa", "bbbbbb", "cccccc"), emptyList(), 1000))
        assertTrue(DisenoTabla.anchos(emptyList(), emptyList()).isEmpty())
        // Filas cortas no rompen el cálculo.
        assertEquals(1000, DisenoTabla.anchos(cols, listOf(listOf("solo una")), 1000).sum())
    }

    @Test fun importesYCantidadesALaDerecha() {
        val f = filas(3)
        assertTrue(DisenoTabla.alDerecha(f, 2))
        assertFalse(DisenoTabla.alDerecha(f, 1))
        assertTrue(DisenoTabla.alDerecha(listOf(listOf("12"), listOf("1,000.5"), listOf("")), 0))
        assertFalse(DisenoTabla.alDerecha(emptyList(), 0))
    }
}
