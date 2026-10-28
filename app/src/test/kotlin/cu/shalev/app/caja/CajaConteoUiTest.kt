package cu.shalev.app.caja

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cu.shalev.core.money.Cup
import cu.shalev.designsystem.theme.ShalevTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 0.31.0 (tarea 5): el conteo de billetes al cerrar el turno estaba implementado pero oculto tras un botón de solo
 * icono —y el texto que lo explicaba lo descarta `ShalevDialog` cuando hay contenido—. Ahora las denominaciones se ven
 * de entrada y «Omitir», en la esquina inferior derecha, pasa al total suelto (y desde ahí se vuelve al conteo).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class CajaConteoUiTest {
    @get:Rule val rule = createComposeRule()

    private var confirmado: Cup? = null

    private fun mostrar() {
        rule.setContent {
            ShalevTheme {
                DialogoContado(
                    titulo = "Cerrar turno", texto = "Cuenta el efectivo del turno", confirmar = "Cerrar turno",
                    arqueo = null, trabajando = false, onConfirmar = { confirmado = it }, onDismiss = {},
                )
            }
        }
    }

    @Test fun elConteoPorBilletesSeVeDeEntradaYNoSeConfirmaSinContar() {
        mostrar()
        rule.onNodeWithTag("billetes_1").assertExists()
        rule.onNodeWithTag(CajaTags.CONFIRMAR).assertIsNotEnabled() // nada contado: declarar 0 descuadraría la caja

        rule.onNodeWithTag("billetes_mas_1").performClick()
        rule.onNodeWithTag("billetes_mas_1").performClick()
        rule.onNodeWithTag(CajaTags.CONFIRMAR).assertIsEnabled().performClick()
        assertEquals(Cup.ofPesos(2), confirmado)
    }

    @Test fun incluyeBilleteDe2000YPuedeEscribirseDirectamente() {
        mostrar()
        rule.onNodeWithTag("billetes_1").performTextInput("1")
        rule.onNodeWithTag(CajaTags.CONFIRMAR).assertIsEnabled().performClick()
        assertEquals(Cup.ofPesos(1), confirmado)
    }

    @Test fun omitirPasaAlTotalSueltoYDesdeAhiSeVuelveAlConteo() {
        mostrar()
        rule.onNodeWithTag(CajaTags.OMITIR).assertExists().performClick()
        rule.onNodeWithTag("billetes_1").assertDoesNotExist()
        rule.onNodeWithTag(CajaTags.CONTADO).assertExists()
        rule.onNodeWithTag(CajaTags.CONFIRMAR).assertIsNotEnabled() // el total vacío no vale
        rule.onNodeWithTag(CajaTags.CONTADO).performTextInput("0")
        rule.onNodeWithTag(CajaTags.CONFIRMAR).assertIsEnabled() // el cero escrito a mano, sí
        rule.onNodeWithTag(CajaTags.POR_BILLETES).performClick()
        rule.onNodeWithTag("billetes_1").assertExists()
    }
}
