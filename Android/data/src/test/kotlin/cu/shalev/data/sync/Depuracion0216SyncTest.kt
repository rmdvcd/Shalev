package cu.shalev.data.sync

import cu.shalev.data.dto.InsumoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.21.6: la existencia leída al abrir la ficha viaja como campo opcional (compatible en ambos sentidos). */
class Depuracion0216SyncTest {
    private val dto = InsumoDto(id = 4, nombre = "Harina", unidad = "KG", precioCent = 1000, cantidadMil = 2500, creadoEn = 0, actualizadoEn = 0)

    @Test fun `cantidad leida viaja y una secundaria anterior no la manda`() {
        val json = SyncJson.encodeToString(Accion.serializer(), GuardarInsumo(dto, cantidadLeidaMil = 3000))
        assertTrue(json.contains("cantidadLeidaMil"))
        assertEquals(3000L, (SyncJson.decodeFromString(Accion.serializer(), json) as GuardarInsumo).cantidadLeidaMil)
        // Sin el campo (secundaria 0.21.5 o anterior): null = manda la cantidad escrita, como antes.
        val viejo = SyncJson.encodeToString(Accion.serializer(), GuardarInsumo(dto))
        assertTrue(!viejo.contains("cantidadLeidaMil"))
        assertNull((SyncJson.decodeFromString(Accion.serializer(), viejo) as GuardarInsumo).cantidadLeidaMil)
    }
}
