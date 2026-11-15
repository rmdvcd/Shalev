package cu.shalev.app.root

import android.Manifest
import android.app.Application
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import cu.shalev.designsystem.theme.ShalevTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Primera apertura: el checklist pide los permisos uno a uno y marca cada fila (Permitido / Denegado) según se
 * responde al diálogo del sistema. El diálogo del sistema se sustituye por un registro de resultados falso.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class PermisosInicialesUiTest {
    @get:Rule val rule = createComposeRule()

    private val cam = Manifest.permission.CAMERA
    private val notif = Manifest.permission.POST_NOTIFICATIONS
    private val fotos = Manifest.permission.READ_MEDIA_IMAGES

    /** Diálogos del sistema abiertos: (código, permiso). */
    private class RegistroFalso : ActivityResultRegistry() {
        val pedidos = mutableListOf<Pair<Int, String>>()
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            pedidos += requestCode to input as String
        }
    }

    private val registro = RegistroFalso()
    private var terminado = false
    private var omitido = false

    private fun mostrar(yaConcedido: (String) -> Boolean = { false }) = rule.setContent {
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry: ActivityResultRegistry = registro }
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
            ShalevTheme(darkTheme = false) {
                ChecklistPermisosIniciales(PermisosIniciales.lista(35), yaConcedido, { terminado = true }, { omitido = true })
            }
        }
    }

    private fun esperarPausa() {
        rule.mainClock.advanceTimeBy(PAUSA_ENTRE_PERMISOS_MS + 200)
        rule.waitForIdle()
    }

    private fun responder(concedido: Boolean) {
        val (codigo, _) = registro.pedidos.last()
        rule.runOnUiThread { registro.dispatchResult(codigo, concedido) }
        rule.waitForIdle()
    }

    private fun marca(permiso: String, texto: String) = rule.onNodeWithTag(PermisosTags.estado(permiso)).assert(hasText(texto))

    @Test fun pideUnoAUnoYMarcaCadaFila() {
        mostrar()
        // Cada permiso con su para qué, todos pendientes.
        TipoPermisoInicial.entries.forEach { rule.onNodeWithText(it.detalle).assertIsDisplayed() }
        listOf(cam, notif, fotos).forEach { marca(it, "Pendiente") }

        rule.onNodeWithTag(PermisosTags.CONTINUAR).performClick()
        esperarPausa()
        assertEquals(listOf(cam), registro.pedidos.map { it.second })
        marca(cam, "Pidiendo")
        marca(notif, "Pendiente")

        responder(true)
        marca(cam, "Permitido")
        esperarPausa()
        assertEquals(listOf(cam, notif), registro.pedidos.map { it.second })
        marca(notif, "Pidiendo")

        responder(false)
        marca(notif, "Denegado")
        esperarPausa()
        assertEquals(fotos, registro.pedidos.last().second)
        responder(true)
        marca(fotos, "Permitido")

        // Terminado: resumen, cómo activar lo negado y «Listo».
        rule.onNodeWithText(TextosPermisosIniciales.resumen(2, 3)).assertIsDisplayed()
        rule.onNodeWithText(TextosPermisosIniciales.AJUSTES).assertIsDisplayed()
        esperarPausa()
        assertEquals("no se vuelve a pedir nada", 3, registro.pedidos.size)
        assertFalse(terminado)
        rule.onNodeWithTag(PermisosTags.CONTINUAR).assert(hasContentDescription(TextosPermisosIniciales.LISTO)).performClick()
        assertTrue(terminado)
        assertFalse(omitido)
    }

    @Test fun losYaConcedidosSalenMarcadosYNoSePiden() {
        mostrar { it == cam }
        marca(cam, "Permitido")
        rule.onNodeWithTag(PermisosTags.CONTINUAR).performClick()
        esperarPausa()
        assertEquals(listOf(notif), registro.pedidos.map { it.second })
    }

    @Test fun todoConcedidoSinRespuestasNegadasNoMuestraElAvisoDeAjustes() {
        mostrar { it != fotos }
        rule.onNodeWithTag(PermisosTags.CONTINUAR).performClick()
        esperarPausa()
        responder(true)
        rule.onNodeWithText(TextosPermisosIniciales.resumen(3, 3)).assertIsDisplayed()
        rule.onAllNodesWithText(TextosPermisosIniciales.AJUSTES).assertCountEquals(0)
    }

    @Test fun omitirNoPideNada() {
        mostrar()
        rule.onNodeWithTag(PermisosTags.OMITIR).performClick()
        esperarPausa()
        assertTrue(omitido)
        assertTrue(registro.pedidos.isEmpty())
    }

    @Test fun conTodoConcedidoDeAntesNoHayNadaQuePreguntar() {
        mostrar { true }
        rule.waitForIdle()
        assertTrue(terminado)
        assertTrue(registro.pedidos.isEmpty())
    }
}
