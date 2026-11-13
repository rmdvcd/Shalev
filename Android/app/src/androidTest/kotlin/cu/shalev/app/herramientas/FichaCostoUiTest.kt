package cu.shalev.app.herramientas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.core.result.AppResult
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.fichacosto.DestinoFicha
import cu.shalev.domain.fichacosto.FichaCostoRepository
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FichaCostoUiTest {
    @get:Rule val rule = createComposeRule()
    private val modelos = ViewModelStore()
    @After fun fin() = rule.runOnUiThread { modelos.clear() }

    @Test fun agregarPartidaYAdvertirAlSalir() {
        // Persistencia/exportación se prueban con Room; aquí no se pide un contenedor Hilt a ComponentActivity.
        val repo = object : FichaCostoRepository {
            override fun observar() = flowOf(emptyList<FichaCostoGuardada>())
            override suspend fun guardar(ficha: FichaCostoGuardada) = AppResult.Ok(Unit)
            override suspend fun eliminar(id: Long) = AppResult.Ok(Unit)
            override suspend fun exportar(ficha: FichaCostoGuardada, destino: DestinoFicha) = AppResult.Ok(1L)
        }
        val vm = FichaCostoViewModel(repo).also { modelos.put("ficha", it) }
        rule.setContent { ShalevTheme(darkTheme = false) { FichaCostoScreen(onBack = {}, vm = vm) } }
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Agregar partida"))
        rule.onNodeWithContentDescription("Agregar partida").performClick()
        rule.onNodeWithText("Descripción").performTextInput("Harina")
        rule.onNodeWithContentDescription("Confirmar partida").performClick()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Harina"))
        rule.onNodeWithText("Harina").assertIsDisplayed()
        rule.onNodeWithContentDescription("Atrás").performClick()
        rule.onNodeWithText("¿Salir sin guardar?").assertIsDisplayed()
    }
}
