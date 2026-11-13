package cu.shalev.app

import cu.shalev.app.caja.ConteoForm
import cu.shalev.domain.model.TipoEntidad
import org.junit.Assert.*
import org.junit.Test

class ConteoFormTest {
    @Test fun productosSoloEnterosYConCeroExplicito() {
        val p = TipoEntidad.PRODUCTO
        assertEquals(0L, ConteoForm.cantidad("0", p))
        assertEquals(123L, ConteoForm.cantidad("123", p))
        listOf("", "-1", "1.5", "1,5", "1 000", "9".repeat(30), "1e3").forEach { assertNull(ConteoForm.cantidad(it, p)) }
    }
    @Test fun insumosAdmitenTresDecimalesConPuntoOComa() {
        val i = TipoEntidad.INSUMO
        assertEquals(1250L, ConteoForm.cantidad("1,250", i))
        assertEquals(1250L, ConteoForm.cantidad("1.250", i))
        assertEquals(1L, ConteoForm.cantidad("0.001", i))
        assertEquals(0L, ConteoForm.cantidad("0", i))
        listOf("", "-1", "1.2345", "1,000.25", "1 000", "1e3", "1,").forEach { assertNull(ConteoForm.cantidad(it, i)) }
    }
}
