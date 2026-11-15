package cu.shalev.app.perfil

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.platform.LocalContext
import cu.shalev.domain.model.Perfil
import cu.shalev.designsystem.theme.ShalevTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ajustes → Perfil: el botón Mostrar abre la ventana con el ID de la licencia y Copiar lo deja en el portapapeles. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class PerfilIdLicenciaUiTest {
    @get:Rule val rule = createComposeRule()

    private val uuid = "3f2b8c1e-5a7d-4e9f-b0c2-9d8e7f6a5b4c"
    private val perfil = Perfil(nombre = "Ana", apellidos = "Díaz", ci = "85010112345")

    private lateinit var contexto: Context

    private fun mostrar(id: IdLicenciaUi?) = rule.setContent {
        contexto = LocalContext.current
        ShalevTheme(darkTheme = false) {
            PerfilContent(PerfilUiState(true, perfil, DatosPerfilForm.desde(perfil), idLicencia = id), {}, AccionesPerfil({}, {}, {}))
        }
    }

    @Test fun mostrarAbreElIdYCopiarLoLlevaAlPortapapeles() {
        mostrar(IdLicenciaUi(uuid))
        rule.onNodeWithTag(PerfilTags.ID_MOSTRAR).performScrollTo().performClick()
        rule.onNodeWithTag(PerfilTags.ID_TEXTO).assertIsDisplayed().assertTextEquals(uuid)
        rule.onNodeWithText(TextosIdLicencia.AVISO).assertIsDisplayed()

        rule.onNodeWithTag(PerfilTags.ID_COPIAR).performClick()
        val cm = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip!!
        assertEquals(uuid, clip.getItemAt(0).text.toString())
        assertTrue(clip.description.extras!!.getBoolean("android.content.extra.IS_SENSITIVE"))
        // Se cierra la ventana y se avisa.
        rule.onAllNodesWithTag(PerfilTags.ID_DIALOGO).assertCountEquals(0)
        rule.onNodeWithText(TextosIdLicencia.COPIADO).assertIsDisplayed()
    }

    @Test fun sinLicenciaActivadaLoExplicaYNoHayCopiar() {
        mostrar(IdLicenciaUi(null))
        rule.onNodeWithTag(PerfilTags.ID_MOSTRAR).performScrollTo().performClick()
        rule.onNodeWithText(TextosIdLicencia.SIN_LICENCIA).assertIsDisplayed()
        rule.onAllNodesWithTag(PerfilTags.ID_COPIAR).assertCountEquals(0)
    }

    @Test fun laAppSecundariaNoMuestraLaTarjeta() {
        mostrar(null)
        rule.onAllNodesWithTag(PerfilTags.ID_MOSTRAR).assertCountEquals(0)
    }
}
