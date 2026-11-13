package cu.shalev.data

import cu.shalev.data.repository.escaparLike
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.21.6: el texto del filtro de Registros se busca tal cual (LIKE con ESCAPE '\'). */
class BusquedaLiteralTest {
    @Test fun losComodinesSeEscapanEnVezDeQuitarse() {
        assertEquals("LOTE\\_7", escaparLike("LOTE_7"))       // antes «LOTE7»: no encontraba «LOTE_7»
        assertEquals("10\\%", escaparLike("10%"))
        assertEquals("a\\\\b", escaparLike("a\\b"))
        assertEquals("Café molido", escaparLike("Café molido"))
    }
}
