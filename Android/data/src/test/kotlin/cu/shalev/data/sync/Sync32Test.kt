package cu.shalev.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.32.0: la sincronización es siempre manual, el fondo lo escribe quien abre el turno y las fotos del catálogo
 * viajan aparte (por bloques). Aquí se comprueba la convivencia con las apps anteriores.
 */
class Sync32Test {
    private val d = DatosSecundaria("n", "Negocio", 3, "Luis", "clave", listOf("192.168.43.1"), 47_811)

    @Test fun `los datos guardados por una app anterior se leen sin los campos del fondo`() {
        val json = SyncJson.encodeToString(DatosSecundaria.serializer(), d)
        val viejo = SyncJson.decodeFromString(
            DatosSecundaria.serializer(),
            json.replace("}", ",\"modo\":\"AUTOMATICA\",\"principalAsignaFondo\":true,\"fondoCent\":150000,\"pideFondo\":true}"),
        )
        assertEquals("Luis", viejo.nombreEmpleado)
        assertEquals(47_811, viejo.puerto)
    }

    @Test fun `una principal anterior (sin fotos) se entiende igual`() {
        val viejo = SyncJson.decodeFromString(
            Mensaje.serializer(),
            """{"t":"sync_ok","id":1,"recibidos":{},"hash":"h"}""",
        ) as SincronizarOk
        assertNull(viejo.instantanea)
        // Y la instantánea de una principal 0.31 (sin el campo de fotos) también.
        val inst = SyncJson.decodeFromString(
            Instantanea.serializer(),
            """{"nombreNegocio":"N","empleado":{"nombre":"Luis","permisos":[]},"licencia":{"clase":"PRUEBA"}}""",
        )
        assertTrue(inst.fotos.isEmpty())
        assertEquals("", inst.local)
    }

    @Test fun `la instantánea avisa de las fotos del catálogo`() {
        val inst = Instantanea(
            nombreNegocio = "N",
            empleado = EmpleadoInfo("Luis", emptyList()),
            licencia = LicenciaDto("PRUEBA"),
            productos = emptyList(),
            fotos = listOf(FotoCatalogoDto("producto", 7, "abc.jpg", 1234)),
            local = "Centro",
        )
        val otra = SyncJson.decodeFromString(Instantanea.serializer(), SyncJson.encodeToString(Instantanea.serializer(), inst))
        assertEquals(1, otra.fotos.size)
        assertEquals("abc.jpg", otra.fotos.first().nombre)
        assertEquals(1234L, otra.fotos.first().bytes)
        assertEquals("Centro", otra.local)
    }

    @Test fun `el bloque de una foto lleva su hash`() {
        val b = BloqueFoto(1, 0, datos = "QUJD", total = 3, sha256 = "abc")
        assertEquals("abc", b.sha256)
        assertEquals(0L, b.desde)
        assertFalse(b.error != null)
    }
}
