package cu.shalev.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Huella estable del contenido y compatibilidad del protocolo de comandos únicos. */
class ComandosUnicosTest {

    @Test
    fun laHuellaNoDependeDelOrdenDelMapaPeroSiDelContenido() {
        val primero = CambiarPrecios(linkedMapOf(2L to 200L, 1L to 100L))
        val mismo = CambiarPrecios(linkedMapOf(1L to 100L, 2L to 200L))
        val distinto = CambiarPrecios(linkedMapOf(1L to 101L, 2L to 200L))

        assertEquals(HuellaComando.calcular(primero), HuellaComando.calcular(mismo))
        assertFalse(HuellaComando.calcular(primero) == HuellaComando.calcular(distinto))
    }

    @Test
    fun `campos nuevos opcionales compatibles con 0_21_6`() {
        val c = SyncJson.encodeToString(Mensaje.serializer(), Comando(5, EliminarProducto(9), clave = "k"))
        assertEquals("k", (SyncJson.decodeFromString(Mensaje.serializer(), c) as Comando).clave)
        val viejo = SyncJson.encodeToString(Mensaje.serializer(), Comando(5, EliminarProducto(9)))
        assertFalse(viejo.contains("clave"))
        val h = SyncJson.encodeToString(Saludo.serializer(), HolaOk("n", comandosUnicos = true))
        assertEquals(true, (SyncJson.decodeFromString(Saludo.serializer(), h) as HolaOk).comandosUnicos)
        // Principal 0.21.6 o anterior: sin el campo → la secundaria no reenvía.
        assertEquals(false, (SyncJson.decodeFromString(Saludo.serializer(), h.replace(",\"comandosUnicos\":true", "")) as HolaOk).comandosUnicos)
    }
}
