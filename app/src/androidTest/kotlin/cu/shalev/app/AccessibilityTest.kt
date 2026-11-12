package cu.shalev.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevLoading
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.ShalevTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plan UI/UX/IX 2026-11 (SK-1) — Batería de accesibilidad sobre ATF (Accessibility Test Framework):
 * `enableAccessibilityChecks()` (artefacto `ui-test-junit4-accessibility`) engancha los checks
 * (TouchTargetSize, TextContrast, ClickableSpan…) al árbol de Compose y los ejecuta en cada acción
 * del test que cambia la UI.
 *
 * Por qué es instrumentado y no JVM/Robolectric: la propia API lo dice — requiere API 34+ y
 * «currently does not work on Robolectric» (b/332778271). Por eso este archivo vive en
 * `androidTest`: se COMPILA en cada push del CI (`:app:compileDebugAndroidTestKotlin`) y se ejecuta
 * en el trabajo de emulador (semanal y a mano, ver ci.yml). En JVM la capa equivalente son las
 * aserciones semánticas de `AuditoriaUiTest`, que no necesitan ATF.
 *
 * Criterio de la batería:
 *   - Componentes comunes montados con `ShalevTheme` y sin estado ni datos de ejemplo, para que un
 *     aviso se reproduzca sin montar la app entera.
 *   - No se suprimen checks a mano (salvo causa justificada en comentario). Un aviso legítimo de un
 *     componente común se arregla en `:designsystem` — un solo lugar — nunca silenciando el check.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34) // enableAccessibilityChecks requiere Android 14 (API 34+)
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
