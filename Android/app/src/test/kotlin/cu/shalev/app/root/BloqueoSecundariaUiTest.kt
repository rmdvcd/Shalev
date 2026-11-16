package cu.shalev.app.root

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cu.shalev.app.licencia.LicenciaAcciones
import cu.shalev.app.licencia.LicenciaContent
import cu.shalev.app.licencia.LicenciaTags
import cu.shalev.app.licencia.LicenciaUiState
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.Licencia
import cu.shalev.licencia.LicenseState
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.0.2: con la prueba vencida, el aviso de bloqueo ofrece «Seguir como app secundaria». Sin esa salida habilitada
 * (licencia vencida, transferida o reloj manipulado) el botón no existe.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class BloqueoSecundariaUiTest {
    @get:Rule val rule = createComposeRule()

    private val bloqueada = Licencia(LicenseState.TrialExpired, "SPVI:7F3A-92C1-D04E-11B8", puedeSolicitar = true, huellaEmisor = null)

    private fun acciones(onSecundaria: (() -> Unit)?) = LicenciaAcciones(
        onBack = null,
        onEditar = {},
        onAbrirEdicion = {},
        onCerrarEdicion = {},
        onSolicitar = {},
        onActivar = {},
        onPegar = {},
        onSoporte = {},
        onSecundaria = onSecundaria,
    )

    private fun mostrar(onSecundaria: (() -> Unit)?) = rule.setContent {
        ShalevTheme {
            LicenciaContent(
                state = LicenciaUiState(licencia = bloqueada),
                ahora = Instant.now(),
                bloqueada = true,
                acciones = acciones(onSecundaria),
            )
        }
    }

    /**
     * En el entorno de prueba el teclado se considera visible y `AutoColapsarPanelesAlEscribir` cierra los paneles,
     * así que se abre el aviso de bloqueo antes de buscar el botón (en el teléfono aparece abierto).
     */
    private fun abrirAvisoDeBloqueo() {
        val visible = rule.onAllNodesWithTag(LicenciaTags.SECUNDARIA).fetchSemanticsNodes().isNotEmpty()
        if (!visible) rule.onNodeWithTag(LicenciaTags.AVISO_BLOQUEO).performClick()
    }

    @Test fun laPruebaVencidaOfreceSeguirComoSecundaria() {
        var pulsado = false
        mostrar { pulsado = true }
        abrirAvisoDeBloqueo()
        rule.onNodeWithTag(LicenciaTags.SECUNDARIA).performScrollTo().assertIsDisplayed().performClick()
        assertTrue("el botón debe abrir la vinculación como secundaria", pulsado)
    }

    @Test fun sinSalidaHabilitadaNoSeOfrece() {
        mostrar(null)
        abrirAvisoDeBloqueo()
        rule.onNodeWithTag(LicenciaTags.SECUNDARIA).assertDoesNotExist()
    }
}
