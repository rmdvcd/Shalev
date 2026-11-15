package cu.shalev.app.avisos

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import cu.shalev.designsystem.component.AvisosTags
import cu.shalev.designsystem.component.ShalevAvisosState
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.TextosAvisos
import cu.shalev.designsystem.component.TipoAviso
import cu.shalev.designsystem.theme.ShalevTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La isla de avisos en pantalla: píldora, tarjeta, paginador, «Limpiar» en dos pasos, deslizar y tiempos. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class AvisosIslaUiTest {
    @get:Rule val rule = createComposeRule()

    private fun pintar(estado: ShalevAvisosState) = rule.setContent {
        ShalevTheme(darkTheme = false) { ShalevSnackbarHost(estado) }
    }

    @Test fun enReposoEsUnaPildoraConElMasNuevoYLaInsignia() {
        val s = ShalevAvisosState()
        s.mostrar("Producto eliminado.")
        s.mostrar("No se pudo guardar. Inténtalo de nuevo.")
        pintar(s)
        rule.onNodeWithTag(AvisosTags.PILDORA).assertIsDisplayed()
        rule.onNodeWithText("No se pudo guardar. Inténtalo de nuevo.").assertIsDisplayed()
        // La insignia se fusiona con la píldora (TalkBack la lee de corrido); se comprueba en el árbol sin fusionar.
        rule.onNodeWithTag(AvisosTags.INSIGNIA, useUnmergedTree = true).assertContentDescriptionEquals(TextosAvisos.insigniaDescripcion(2))
        rule.onAllNodesWithTag(AvisosTags.TARJETA).assertCountEquals(0)
    }

    @Test fun tocarAbreLaTarjetaConPaginadorYCerrar() {
        val s = ShalevAvisosState()
        s.mostrar("uno", TipoAviso.ERROR)
        s.mostrar("dos", TipoAviso.ERROR)
        pintar(s)
        rule.onNodeWithTag(AvisosTags.PILDORA).performClick()
        rule.onNodeWithTag(AvisosTags.TARJETA).assertIsDisplayed()
        rule.onNodeWithTag(AvisosTags.PAGINA).assertTextEquals(TextosAvisos.pagina(0, 2))
        rule.onNodeWithTag(AvisosTags.ANTERIOR).assertIsNotEnabled()
        rule.onNodeWithTag(AvisosTags.SIGUIENTE).performClick()
        rule.onNodeWithTag(AvisosTags.PAGINA).assertTextEquals(TextosAvisos.pagina(1, 2))
        rule.onNodeWithText("uno").assertIsDisplayed()
        rule.onNodeWithTag(AvisosTags.CERRAR).performClick()
        rule.waitForIdle()
        assertEquals(listOf("dos"), s.avisos.map { it.titulo })
    }

    @Test fun limpiarPideUnSegundoToque() {
        val s = ShalevAvisosState()
        s.mostrar("uno", TipoAviso.ERROR)
        s.mostrar("dos", TipoAviso.ERROR)
        s.abrir()
        pintar(s)
        rule.onNodeWithTag(AvisosTags.LIMPIAR).performClick()
        rule.onNodeWithText(TextosAvisos.BORRAR).assertIsDisplayed()
        assertEquals("el primer toque solo arma", 2, s.total)
        rule.onNodeWithTag(AvisosTags.LIMPIAR).performClick()
        rule.waitForIdle()
        assertEquals(0, s.total)
    }

    @Test fun laAccionCierraYDevuelveElResultado() {
        val s = ShalevAvisosState()
        var vistos = 0
        s.mostrar("Turno cerrado: 3 ventas", TipoAviso.EXITO, accion = "Ver", alAccion = { vistos++ })
        pintar(s)
        rule.onNodeWithTag(AvisosTags.TARJETA).assertIsDisplayed()
        rule.onNodeWithTag(AvisosTags.ACCION).performClick()
        rule.waitForIdle()
        assertEquals(1, vistos)
        assertEquals(0, s.total)
    }

    @Test fun deslizarCierraElAviso() {
        val s = ShalevAvisosState()
        s.mostrar("uno", TipoAviso.ERROR)
        s.mostrar("dos", TipoAviso.ERROR)
        pintar(s)
        rule.onNodeWithTag(AvisosTags.ISLA).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(listOf("uno"), s.avisos.map { it.titulo })
    }

    @Test fun elExitoSeVaSoloYElErrorSeQueda() {
        val s = ShalevAvisosState()
        pintar(s)
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { s.mostrar("Archivo guardado.") }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, s.total)
        rule.mainClock.advanceTimeBy(ShalevAvisosState.TIEMPO_CON_ACCION_MS)
        rule.mainClock.autoAdvance = true
        rule.waitUntil(timeoutMillis = 15_000) { s.total == 0 }
        rule.mainClock.autoAdvance = false

        rule.runOnIdle { s.mostrar("No se pudo guardar. Inténtalo de nuevo.") }
        rule.mainClock.advanceTimeBy(ShalevAvisosState.TIEMPO_CON_ACCION_MS * 3)
        rule.waitForIdle()
        assertEquals("el error se queda hasta que se cierra", 1, s.total)
    }

    @Test fun conLaTarjetaAbiertaNadaSeVaSolo() {
        val s = ShalevAvisosState()
        s.mostrar("Archivo guardado.")
        s.abrir()
        pintar(s)
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(ShalevAvisosState.TIEMPO_CON_ACCION_MS * 2)
        assertEquals(1, s.total)
    }

    @Test fun soloPintaUnAnfitrionAunqueHayaVarios() {
        val s = ShalevAvisosState()
        s.mostrar("Archivo guardado.")
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                Column {
                    ShalevSnackbarHost(s)
                    ShalevSnackbarHost(s)
                }
            }
        }
        rule.onAllNodesWithTag(AvisosTags.ISLA).assertCountEquals(1)
    }

    @Test fun laRegionVivaSeAnunciaSegunLaUrgencia() {
        val s = ShalevAvisosState()
        s.mostrar("Archivo guardado.")
        pintar(s)
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertExists()
        rule.runOnIdle { s.mostrar("No se pudo guardar.") }
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive)).assertExists()
    }
}
