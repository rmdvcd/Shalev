package cu.shalev.app.ajustes

import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.model.ResumenConfiguracion
import org.junit.Assert.assertEquals
import org.junit.Test

class AjustesTextosTest {
    private val pasos = PasoConfiguracion.entries.toList()

    @Test fun subtituloDeConfiguracion() {
        assertEquals("Todo listo", ResumenConfiguracion(pasos, emptyList()).subtitulo())
        assertEquals("Falta 1 paso", ResumenConfiguracion(pasos, listOf(PasoConfiguracion.ALERTAS)).subtitulo())
        assertEquals("Faltan 3 pasos", ResumenConfiguracion(pasos, pasos).subtitulo())
        assertEquals("Cargando…", (null as ResumenConfiguracion?).subtitulo())
    }
}
