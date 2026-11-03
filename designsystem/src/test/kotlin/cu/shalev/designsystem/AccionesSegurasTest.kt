package cu.shalev.designsystem

import cu.shalev.designsystem.component.GuardiaDobleToque
import cu.shalev.designsystem.component.PanelesInformativos
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Doble pulsación y paneles informativos: la lógica es pura (el reloj se inyecta), sin componer nada.
 */
class AccionesSegurasTest {

    // ---------- GuardiaDobleToque ----------

    @Test fun primerToqueSiemprePasa() {
        var ahora = 1_000L
        val g = GuardiaDobleToque { ahora }
        assertTrue(g.intentar())
    }

    @Test fun segundoToqueDentroDeLaVentanaSeIgnora() {
        var ahora = 1_000L
        val g = GuardiaDobleToque { ahora }
        assertTrue(g.intentar())
        ahora += 100 // doble toque mecánico
        assertFalse(g.intentar())
        ahora += GuardiaDobleToque.VENTANA_MS - 101 // aún dentro de la ventana desde el toque permitido
        assertFalse(g.intentar())
    }

    @Test fun trasLaVentanaSeVuelveAPermitir() {
        var ahora = 1_000L
        val g = GuardiaDobleToque { ahora }
        assertTrue(g.intentar())
        ahora += GuardiaDobleToque.VENTANA_MS
        assertTrue(g.intentar())
    }

    @Test fun toqueIgnoradoNoProlongaLaVentana() {
        var ahora = 0L
        val g = GuardiaDobleToque { ahora }
        assertTrue(g.intentar())
        ahora = GuardiaDobleToque.VENTANA_MS - 1
        assertFalse(g.intentar()) // ignorado: la ventana sigue contando desde el primer toque
        ahora = GuardiaDobleToque.VENTANA_MS
        assertTrue(g.intentar())
    }

    @Test fun relojQueRetrocedeNoBloquea() {
        var ahora = 5_000L
        val g = GuardiaDobleToque { ahora }
        assertTrue(g.intentar())
        ahora = 4_000L // la persona cambió la hora del teléfono
        assertTrue(g.intentar())
    }

    @Test fun guardiasIndependientesNoSeBloquean() {
        var ahora = 1_000L
        val a = GuardiaDobleToque { ahora }
        val b = GuardiaDobleToque { ahora }
        assertTrue(a.intentar())
        assertTrue("otro botón sí puede actuar en el mismo instante", b.intentar())
    }

    // ---------- PanelesInformativos ----------

    @Test fun porDefectoSoloLosDeclaradosEstanAbiertos() {
        val p = PanelesInformativos(setOf("estado"))
        assertTrue(p.abierto("estado"))
        assertFalse(p.abierto("precios"))
    }

    @Test fun alternarAbreYCierra() {
        val p = PanelesInformativos()
        p.alternar("precios")
        assertTrue(p.abierto("precios"))
        p.alternar("precios")
        assertFalse(p.abierto("precios"))
    }

    @Test fun colapsarTodosCierraTambienLosAbiertosPorDefecto() {
        val p = PanelesInformativos(setOf("estado", "bloqueo"))
        p.abrir("precios")
        p.colapsarTodos() // lo que ocurre al aparecer el teclado
        assertFalse(p.abierto("estado"))
        assertFalse(p.abierto("bloqueo"))
        assertFalse(p.abierto("precios"))
    }

    @Test fun colapsarUnoNoAfectaALosDemas() {
        val p = PanelesInformativos(setOf("estado", "precios"))
        p.colapsar("precios")
        assertFalse(p.abierto("precios"))
        assertTrue(p.abierto("estado"))
    }
}
