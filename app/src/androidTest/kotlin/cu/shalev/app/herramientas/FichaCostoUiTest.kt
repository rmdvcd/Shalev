package cu.shalev.app.herramientas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.designsystem.theme.ShalevTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FichaCostoUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun agregarPartidaYAdvertirAlSalir() {
        rule.setContent { ShalevTheme(darkTheme = false) { FichaCostoScreen(onBack = {}) } }
        rule.onNodeWithContentDescription("Agregar partida").performClick()
        rule.onNodeWithText("Descripción").performTextInput("Harina")
        rule.onNodeWithContentDescription("Confirmar partida").performClick()
        rule.onNodeWithText("Harina").assertIsDisplayed()
        rule.onNodeWithContentDescription("Atrás").performClick()
        rule.onNodeWithText("¿Salir del cálculo?").assertIsDisplayed()
    }
}
