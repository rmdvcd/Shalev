package cu.shalev.app

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevLoading
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.ShalevTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Plan UI/UX/IX 2026-11 (SK-1) — Batería de accesibilidad sobre ATF (Accessibility Test Framework), en JVM con
 * Robolectric: `enableAccessibilityChecks()` engancha los checks (TouchTargetSize, TextContrast, ClickableSpan…)
 * al árbol de Compose y los lanza en cada acción que el test ejecuta sobre un nodo.
 *
 * Criterio calibrado el día 1 para mantener la señal limpia y no señalar falsos positivos del propio marco:
 *   - Los checks corren al interactuar (assert/click) con un nodo: aquí probamos los componentes que NO dependen
 *     de estado ni de datos de ejemplo, de modo que cualquier aviso se puede reproducir sin montar la app entera.
 *   - No se suprimen checks a mano (salvo causa justificada en comentario): la señal de ATF es la capa ruidosa
 *     pero universal que complementa las aserciones semánticas de [AuditoriaUiTest] (que no necesitan ATF).
 *   - Si un check legítimo aparece en un componente común, el arreglo va en `:designsystem` (un solo lugar),
 *     nunca silenciando el check.
 *
 * NOTA (dependencia): el artefacto `ui-test-junit4-accessibility` con `enableAccessibilityChecks()` solo existe
 * en Maven desde la línea 1.10 de Compose; con la BOM 2024.12.01 (Compose 1.7.6) se fija la versión por separado
 * en `gradle/libs.versions.toml`. Si un build sin red no puede bajarlo, la tarea falla en `dependencies`, no aquí.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class AccessibilityTest {

    @get:Rule
    val rule = createComposeRule().apply { enableAccessibilityChecks() }

    @Test
    fun elBotonPrimarioSuperaLosChecks() {
        rule.setContent {
            ShalevTheme {
                ShalevPrimaryButton(
                    text = "Guardar cambios",
                    onClick = {},
                    icon = ShalevIcons.Siguiente,
                    mostrarTexto = true,
                )
            }
        }

        rule.onNodeWithContentDescription("Guardar cambios").assertHasClickAction()
    }

    @Test
    fun laCardConTituloSuperaLosChecks() {
        rule.setContent {
            ShalevTheme {
                ShalevCard(title = "Tus datos") {
                    Text("Nombre completo")
                }
            }
        }

        rule.onNodeWithText("Nombre completo").assertExists()
    }

    @Test
    fun elEstadoDeCargaSuperaLosChecks() {
        rule.setContent {
            ShalevTheme {
                ShalevLoading()
            }
        }

        rule.onNodeWithContentDescription("Cargando").assertExists()
    }

    @Test
    fun pulsarElBotonPrimarioConChecksNoRompe() {
        var pulsado = false
        rule.setContent {
            ShalevTheme {
                ShalevPrimaryButton(
                    text = "Confirmar",
                    onClick = { pulsado = true },
                    icon = ShalevIcons.Siguiente,
                    mostrarTexto = true,
                )
            }
        }

        rule.onNodeWithContentDescription("Confirmar").performClick()
        assertTrue(pulsado)
    }
}
