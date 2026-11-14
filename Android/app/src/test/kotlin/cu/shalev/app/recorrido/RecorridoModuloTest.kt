package cu.shalev.app.recorrido

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cu.shalev.app.ayuda.AyudaPestanaTags
import cu.shalev.app.ayuda.DialogoAyudaPestana
import cu.shalev.app.ayuda.PestanaAyuda
import cu.shalev.designsystem.component.CoachMarkPaso
import cu.shalev.designsystem.component.CoachMarkTags
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.coachMark
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.ShalevTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Recorrido guiado por módulo: solo sale si no se vio, avanza y retrocede, se guarda solo con Finalizar u Omitir,
 * salta los objetivos que no están y no bloquea la pantalla de debajo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class RecorridoModuloTest {
    @get:Rule val rule = createComposeRule()

    private val pasos = listOf(
        CoachMarkPaso("arriba", "Primer paso", "Toca aquí para empezar."),
        CoachMarkPaso("no_existe", "Paso sin objetivo", "No debe salir."),
        CoachMarkPaso("abajo", "Segundo paso", "Toca aquí para terminar."),
    )

    private var completados by mutableIntStateOf(0)
    private var toquesDebajo = 0

    private fun pantalla(visto: Boolean = false, listo: Boolean = true) {
        rule.setContent {
            ShalevTheme {
                RecorridoModulo(
                    pasos = pasos, visto = visto, listo = listo, onCompletado = { completados++ }, retardoMs = 0,
                ) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Text("Objetivo de arriba", Modifier.testTag("t_arriba").coachMark("arriba"))
                        ShalevPrimaryButton(
                            "Acción de la pantalla", onClick = { toquesDebajo++ }, icon = ShalevIcons.Agregar,
                            mostrarTexto = true, modifier = Modifier.testTag("t_accion"),
                        )
                        Text("Objetivo de abajo", Modifier.testTag("t_abajo").coachMark("abajo"))
                    }
                }
            }
        }
    }

    private fun esperarGlobo() = rule.waitUntil(5_000) {
        rule.onAllNodesWithTag(CoachMarkTags.GLOBO).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun primeraVezMuestraElRecorridoYFinalizarLoGuardaUnaVez() {
        pantalla()
        esperarGlobo()
        rule.onNodeWithText("Primer paso").assertIsDisplayed()
        // El paso cuyo objetivo no está en pantalla se salta: son 2, no 3.
        rule.onNodeWithText("1 de 2").assertIsDisplayed()
        rule.onAllNodesWithTag(CoachMarkTags.ANTERIOR).assertCountEquals(0)

        rule.onNodeWithTag(CoachMarkTags.SIGUIENTE).performClick()
        rule.onNodeWithText("Segundo paso").assertIsDisplayed()
        rule.onNodeWithText("2 de 2").assertIsDisplayed()
        rule.onAllNodesWithTag(CoachMarkTags.OMITIR).assertCountEquals(0)

        rule.onNodeWithTag(CoachMarkTags.ANTERIOR).performClick()
        rule.onNodeWithText("Primer paso").assertIsDisplayed()
        assertEquals(0, completados)

        rule.onNodeWithTag(CoachMarkTags.SIGUIENTE).performClick()
        rule.onNodeWithTag(CoachMarkTags.FINALIZAR).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag(CoachMarkTags.GLOBO).assertCountEquals(0)
        assertEquals(1, completados)
    }

    @Test
    fun omitirCierraYLoGuarda() {
        pantalla()
        esperarGlobo()
        rule.onNodeWithTag(CoachMarkTags.OMITIR).performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag(CoachMarkTags.GLOBO).assertCountEquals(0)
        assertEquals(1, completados)
    }

    @Test
    fun yaVistoNoSeMuestra() {
        pantalla(visto = true)
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        rule.onAllNodesWithTag(CoachMarkTags.GLOBO).assertCountEquals(0)
        rule.onAllNodesWithTag(CoachMarkTags.VELO).assertCountEquals(0)
    }

    @Test
    fun conLaPantallaOcupadaEsperaYNoGuardaNada() {
        pantalla(listo = false)
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        rule.onAllNodesWithTag(CoachMarkTags.GLOBO).assertCountEquals(0)
        assertEquals(0, completados)
    }

    @Test
    fun elVeloNoBloqueaLaPantalla() {
        pantalla()
        esperarGlobo()
        rule.onNodeWithTag(CoachMarkTags.VELO).assertExists()
        rule.onNodeWithTag("t_accion").performClick()
        assertEquals(1, toquesDebajo)
        // Usar la pantalla no cierra ni guarda el recorrido.
        rule.onNodeWithTag(CoachMarkTags.GLOBO).assertIsDisplayed()
        assertEquals(0, completados)
    }

    @Test
    fun laAyudaDeLaPestanaRepiteElRecorridoSinVolverAGuardarlo() {
        var ayuda by mutableStateOf(true)
        rule.setContent {
            ShalevTheme {
                RecorridoModulo(pasos = pasos, visto = true, listo = true, onCompletado = { completados++ }, retardoMs = 0) {
                    Column(Modifier.fillMaxSize()) {
                        Text("Objetivo de arriba", Modifier.coachMark("arriba"))
                        if (ayuda) DialogoAyudaPestana(PestanaAyuda.INVENTARIO, emptySet(), onDismiss = { ayuda = false })
                    }
                }
            }
        }
        rule.onNodeWithTag(AyudaPestanaTags.VER_RECORRIDO).performClick()
        esperarGlobo()
        rule.onNodeWithText("1 de 1").assertIsDisplayed()
        assertEquals(0, completados)
    }

    @Test
    fun sinRecorridoLaAyudaNoOfreceVerlo() {
        rule.setContent {
            ShalevTheme { DialogoAyudaPestana(PestanaAyuda.INVENTARIO, emptySet(), onDismiss = {}) }
        }
        rule.onAllNodesWithTag(AyudaPestanaTags.VER_RECORRIDO).assertCountEquals(0)
    }
}
