package cu.shalev.app.herramientas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.designsystem.theme.ShalevTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalculadoraCambioUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun convierteEnAmbosSentidos() {
        rule.setContent { ShalevTheme { CalculadoraCambio(onDismiss = {}, vm = CalculadoraCambioViewModel(cu.shalev.app.FakeEstadoPantallas())) } }
        rule.onNodeWithText("Importe en USD").performScrollTo().performTextInput("2")
        rule.onNodeWithText("Tasa: CUP por 1 USD").performScrollTo().performTextInput("400")
        rule.onNodeWithText("Resultado: 800.00 CUP").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Moneda de origen").performScrollTo().performClick()
        rule.onNodeWithText("CUP").performClick()
        rule.onNodeWithText("Resultado: 0.01 USD").performScrollTo().assertIsDisplayed()
    }

    @Test fun calculaYUsaTasa() {
        rule.setContent { ShalevTheme { CalculadoraCambio(onDismiss = {}, vm = CalculadoraCambioViewModel(cu.shalev.app.FakeEstadoPantallas())) } }
        rule.onNodeWithText("Operación").performClick()
        rule.onNodeWithText("Calcular tasa desde importes").performClick()
        rule.onNodeWithText("Importe equivalente en CUP").performScrollTo().performTextInput("700")
        rule.onNodeWithText("Importe equivalente en USD").performScrollTo().performTextInput("2")
        rule.onNodeWithText("1 USD = 350 CUP").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Usar tasa en conversión").performScrollTo().performClick()
        rule.onNodeWithText("Importe en USD").performScrollTo().performTextInput("2")
        rule.onNodeWithText("Resultado: 700.00 CUP").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Cerrar calculadora").assertIsDisplayed()
    }
    @Test fun tasaCeroMuestraError() {
        rule.setContent { ShalevTheme { CalculadoraCambio(onDismiss = {}, vm = CalculadoraCambioViewModel(cu.shalev.app.FakeEstadoPantallas())) } }
        rule.onNodeWithText("Tasa: CUP por 1 USD").performScrollTo().performTextInput("350")
        rule.onNodeWithText("Tasa: CUP por 1 USD").performTextClearance()
        rule.onNodeWithText("Tasa: CUP por 1 USD").performTextInput("0")
        rule.onNodeWithText("La tasa debe ser mayor que cero.").performScrollTo().assertIsDisplayed()
    }

}
