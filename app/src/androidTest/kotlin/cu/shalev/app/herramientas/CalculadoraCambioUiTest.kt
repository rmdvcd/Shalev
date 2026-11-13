package cu.shalev.app.herramientas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.designsystem.theme.ShalevTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalculadoraCambioUiTest {
    @get:Rule val rule = createComposeRule()
    private val modelos = ViewModelStore()
    @After fun fin() = rule.runOnUiThread { modelos.clear() }

    private fun mostrar() {
        val vm = CalculadoraCambioViewModel(cu.shalev.app.FakeEstadoPantallas()).also { modelos.put("cambio", it) }
        rule.setContent { ShalevTheme { CalculadoraCambio(onDismiss = {}, vm = vm) } }
    }

    @Test fun convierteEnAmbosSentidos() {
        mostrar()
        rule.onNodeWithText("Importe en USD").performScrollTo().performTextInput("2")
        rule.onNodeWithText("Tasa: CUP por 1 USD").performScrollTo().performTextInput("400")
        rule.onNodeWithText("Resultado: 800 CUP").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Moneda de origen").performScrollTo().performClick()
        rule.onNodeWithText("CUP").performClick()
        rule.onNodeWithText("Resultado: 0.01 USD").performScrollTo().assertIsDisplayed()
    }

    @Test fun calculaYUsaTasa() {
        mostrar()
        rule.onNodeWithText("Operación").performClick()
        rule.onNodeWithText("Calcular tasa desde importes").performClick()
        rule.onNodeWithText("Importe equivalente en CUP").performScrollTo().performTextInput("700")
        rule.onNodeWithText("Importe equivalente en USD").performScrollTo().performTextInput("2")
        rule.onNodeWithText("1 USD = 350 CUP").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Usar tasa en conversión").performScrollTo().performClick()
        rule.onNodeWithText("Importe en USD").performScrollTo().performTextInput("2")
        rule.onNodeWithText("Resultado: 700 CUP").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Cerrar calculadora").assertIsDisplayed()
    }
    @Test fun tasaCeroMuestraError() {
        mostrar()
        rule.onNodeWithText("Tasa: CUP por 1 USD").performScrollTo().performTextInput("350")
        rule.onNodeWithText("Tasa: CUP por 1 USD").performTextClearance()
        rule.onNodeWithText("Tasa: CUP por 1 USD").performTextInput("0")
        rule.onNodeWithText("La tasa debe ser mayor que cero.").performScrollTo().assertIsDisplayed()
    }

}
