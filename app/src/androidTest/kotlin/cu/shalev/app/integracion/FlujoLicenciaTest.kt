package cu.shalev.app.integracion

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.licencia.LicenciaScreen
import cu.shalev.app.licencia.LicenciaTags
import cu.shalev.app.root.RootState
import cu.shalev.designsystem.component.ShalevStepperTextos
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prompt 15 — bloqueo y activación: RootViewModel decide la puerta (como ShalevRoot) y el panel de Licencia real
 * activa. Se reemplaza solo MainScaffold (necesita Hilt) por un marcador. La verificación criptográfica real de
 * la licencia GL está en :licencia (JVM) y en :data LicenciaInstrumentedTest (Keystore del dispositivo).
 */
@RunWith(AndroidJUnit4::class)
class FlujoLicenciaTest {
    @get:Rule val rule = createComposeRule()
    private val env = EntornoIntegracion()

    @After fun fin() = env.cerrar()

    private fun mostrarRaiz() {
        val root = env.rootVm()
        val licencia = env.licenciaVm()
        val entrada = env.entradaVm()
        val recuperacion = env.recuperacionVm()
        rule.setContent {
            ShalevTheme {
                val s by root.state.collectAsStateWithLifecycle()
                when (s) {
                    is RootState.Bloqueo -> LicenciaScreen(onBack = null, bloqueada = true, viewModel = licencia, entradaVm = entrada, recuperacionVm = recuperacion)
                    is RootState.Main -> Box(Modifier.testTag(MAIN)) { Text("Inicio") }
                    else -> Unit
                }
            }
        }
    }

    private fun esperar(tag: String) = rule.waitUntil(5_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun pegarYActivar(mensaje: String) {
        // El Perfil del entorno está incompleto: primero se dan los datos en la UI, no se salta su validación.
        listOf("Nombre" to "Ana", "Apellidos" to "Pérez González", "Carné de identidad" to "85010112345", "Teléfono" to "52345678").forEach { (campo, texto) ->
            rule.onNode(hasSetTextAction() and hasText(campo)).performScrollTo().performTextReplacement(texto)
        }
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        // Sin botón de llave: se avanza por los pasos de solicitud y activación.
        rule.onNodeWithContentDescription(ShalevStepperTextos.SIGUIENTE).performScrollTo().performClick()
        rule.onNodeWithContentDescription(ShalevStepperTextos.SIGUIENTE).performScrollTo().performClick()
        rule.onNode(hasSetTextAction() and hasText("Mensaje de licencia")).performScrollTo().performTextInput(mensaje)
        rule.onNodeWithTag(LicenciaTags.BOTON_ACTIVAR).performScrollTo().performClick()
    }

    @Test fun pruebaVencidaBloqueaYUnaLicenciaValidaDesbloquea() {
        env.licencia.fijar(LicenseState.TrialExpired)
        mostrarRaiz()
        esperar(LicenciaTags.AVISO_BLOQUEO)
        rule.onNodeWithTag(LicenciaTags.AVISO_BLOQUEO).assertIsDisplayed()
        rule.onNodeWithTag(MAIN).assertDoesNotExist()

        pegarYActivar("${LicMem.MENSAJE_VALIDO}\n{\"v\":1}")
        esperar(MAIN)
        assertEquals(1, env.licencia.activaciones.size)
    }

    @Test fun licenciaVencidaBloqueaYUnMensajeAjenoNoDesbloquea() {
        env.licencia.fijar(LicenseState.Expired(TipoLicencia.MENSUAL))
        mostrarRaiz()
        esperar(LicenciaTags.AVISO_BLOQUEO)
        pegarYActivar("Hola, esto no es una licencia")
        rule.waitUntil(5_000) { env.licencia.activaciones.isNotEmpty() }
        rule.waitForIdle()
        rule.onNodeWithTag(LicenciaTags.AVISO_BLOQUEO).assertExists()
        rule.onNodeWithTag(MAIN).assertDoesNotExist()
    }

    @Test fun perpetuaNuncaBloqueaYSiVenceConLaAppAbiertaSeBloquea() {
        env.licencia.fijar(LicenseState.Perpetual("lic-p"))
        mostrarRaiz()
        esperar(MAIN)
        // Una mensual que vence con la app abierta: la puerta pasa a Bloqueo sin reiniciar.
        rule.runOnIdle { env.licencia.fijar(LicenseState.Expired(TipoLicencia.MENSUAL)) }
        esperar(LicenciaTags.AVISO_BLOQUEO)
        assertTrue(rule.onAllNodesWithTag(MAIN).fetchSemanticsNodes().isEmpty())
    }

    private companion object { const val MAIN = "it.main" }
}
