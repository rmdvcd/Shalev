package cu.shalev.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.precios.AccionesPrecios
import cu.shalev.app.precios.PreciosContent
import cu.shalev.app.precios.PreciosTags
import cu.shalev.app.precios.PreciosUiState
import cu.shalev.app.precios.PreajusteForm
import cu.shalev.designsystem.theme.ShalevTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreciosUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun permiteAñadirTodosYLaSeleccionNoSeDuplica() {
        val productos = listOf(prod(1, "Refresco"), prod(2, "Pan"))
        val state = PreciosUiState(
            cargado = true,
            productos = productos,
            form = PreajusteForm(porcentaje = "10", productoIds = linkedSetOf(1L)),
        )
        val acciones = AccionesPrecios(
            onNuevo = {}, onEditar = {}, onActivar = { _, _ -> }, onCambiar = {}, onProducto = {},
            onElegirVisibles = {}, onGuardar = {}, onCerrar = {}, onBorrar = {},
            onConfirmarBorrado = {}, onCancelarBorrado = {},
        )
        rule.setContent { ShalevTheme(darkTheme = false) { PreciosContent(state, onBack = {}, acciones = acciones) } }

        rule.onNodeWithTag(PreciosTags.ELEGIR_MOSTRADOS).assertIsDisplayed()
        rule.onNodeWithTag(PreciosTags.BUSCAR).assertIsDisplayed()
        rule.onAllNodesWithTag(PreciosTags.producto(1)).assertCountEquals(1)
        val añadirTodos = rule.onAllNodesWithTag(PreciosTags.ELEGIR_MOSTRADOS).fetchSemanticsNodes().single().boundsInRoot.top
        val buscador = rule.onAllNodesWithTag(PreciosTags.BUSCAR).fetchSemanticsNodes().single().boundsInRoot.top
        assertTrue("Añadir todos debe quedar encima del buscador", añadirTodos < buscador)
    }
}
