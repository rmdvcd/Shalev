package cu.shalev.app

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cu.shalev.app.common.descripcionFiltro
import cu.shalev.app.precios.AccionesPrecios
import cu.shalev.app.precios.PreajusteForm
import cu.shalev.app.precios.PreciosContent
import cu.shalev.app.precios.PreciosUiState
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevAccordionCard
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevFab
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.icon.ShalevIcons
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
 * Auditoría de UI: comprueba semántica accesible, iconos y etiquetas de ventanas, además del formulario de Precios a 360 dp.
 * Son pruebas de comportamiento, no capturas: detectan etiquetas visibles incorrectas, selección anunciada sin estado
 * conmutable y el chip de Transferencia aplastado.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class AuditoriaUiTest {
    @get:Rule val rule = createComposeRule()

    private val accionesPrecios = AccionesPrecios(
        onNuevo = {}, onEditar = {}, onActivar = { _, _ -> }, onCambiar = {}, onProducto = {}, onElegirVisibles = {},
        onGuardar = {}, onCerrar = {}, onBorrar = {}, onConfirmarBorrado = {}, onCancelarBorrado = {},
    )

    @Test
    fun elTituloDeUnaCardEsEncabezado() {
        rule.setContent { ShalevTheme { ShalevCard(title = "Tus datos") {} } }

        rule.onNodeWithText("Tus datos")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun elAcordeonOcultaElResumenAlAbrirse() {
        val abierto = mutableStateOf(false)
        rule.setContent {
            ShalevTheme {
                ShalevAccordionCard(
                    title = "Ventas",
                    subtitle = "1,000 CUP",
                    expanded = abierto.value,
                    onToggle = { abierto.value = !abierto.value },
                ) { androidx.compose.material3.Text("Detalle") }
            }
        }

        rule.onNodeWithText("1,000 CUP").assertExists()
        rule.onNodeWithText("Ventas").performClick()
        rule.onNodeWithText("1,000 CUP").assertDoesNotExist()
    }

    @Test
    fun unBotonNoConmutableNoAnunciaSeleccion() {
        rule.setContent { ShalevTheme { ShalevIconAction(ShalevIcons.Agregar, "Agregar", onClick = {}) } }

        val config = rule.onNodeWithContentDescription("Agregar").fetchSemanticsNode().config
        assertFalse(config.contains(SemanticsProperties.Selected))
    }

    @Test
    fun unBotonConmutableSiAnunciaSeleccion() {
        rule.setContent { ShalevTheme { ShalevIconAction(ShalevIcons.Filtrar, "Filtrar", onClick = {}, selected = true) } }

        val config = rule.onNodeWithContentDescription("Filtrar").fetchSemanticsNode().config
        assertTrue(config[SemanticsProperties.Selected])
    }

    @Test
    fun lasAccionesMuestranIconoYConservanDescripcionAccesible() {
        rule.setContent { ShalevTheme { ShalevIconAction(ShalevIcons.Agregar, "Agregar producto", onClick = {}) } }

        rule.onNodeWithContentDescription("Agregar producto").assertExists()
        rule.onNodeWithText("Agregar producto").assertDoesNotExist()
    }

    @Test
    fun elFabEsSoloIconoPorDefectoYSigueTeniendoDescripcion() {
        rule.setContent { ShalevTheme { ShalevFab(ShalevIcons.Agregar, "Agregar producto", onClick = {}) } }

        rule.onNodeWithContentDescription("Agregar producto").assertExists()
        rule.onNodeWithText("Agregar producto").assertDoesNotExist()
    }

    // 1.0.3: en ventanas emergentes no se dibuja etiqueta escrita (la descripción accesible sigue completa) y el pie
    // ya no lleva Cancelar: se cierra tocando fuera o con Atrás.
    @Test
    fun lasAccionesDeVentanaUsanSoloIconoYConservanLaDescripcion() {
        rule.setContent {
            ShalevTheme {
                ShalevDialog(
                    title = "Prueba de acciones",
                    onDismiss = {},
                    onConfirm = {},
                    content = {
                        ShalevIconAction(ShalevIcons.Eliminar, "Eliminar", onClick = {}, style = IconActionStyle.Tonal)
                        ShalevIconAction(ShalevIcons.AhoraNo, "Omitir", onClick = {}, style = IconActionStyle.Tonal)
                        ShalevIconAction(ShalevIcons.Exportar, "Exportar", onClick = {}, style = IconActionStyle.Tonal)
                    },
                )
            }
        }

        listOf("Confirmar", "Eliminar", "Omitir", "Exportar").forEach { etiqueta ->
            rule.onNodeWithContentDescription(etiqueta).assertExists()
            rule.onNodeWithText(etiqueta).assertDoesNotExist()
        }
        rule.onNodeWithText("Cancelar").assertDoesNotExist()
        rule.onNodeWithContentDescription("Cancelar").assertDoesNotExist()
    }

    @Test
    fun unaFilaNoSeleccionableNoAnunciaSeleccion() {
        rule.setContent { ShalevTheme { ShalevListItem(title = "Producto", onClick = {}) } }

        val config = rule.onNodeWithText("Producto").fetchSemanticsNode().config
        assertFalse(config.contains(SemanticsProperties.Selected))
    }

    @Test
    fun laDescripcionDelFiltroIncluyeElConteo() {
        assertEquals("Filtrar", descripcionFiltro("Filtrar", 0))
        assertEquals("Filtrar, 1 filtro activo", descripcionFiltro("Filtrar", 1))
        assertEquals("Filtrar, 2 filtros activos", descripcionFiltro("Filtrar", 2))
    }

    // 1.0.3: el método de pago del ajuste es un selector, no una fila de chips que se aplastaba a 360 dp.
    @Test
    fun elSelectorDeMetodoYElChipSeVenEnterosEnElFormularioDePrecios() {
        rule.setContent {
            ShalevTheme {
                PreciosContent(
                    state = PreciosUiState(
                        cargado = true,
                        form = PreajusteForm(nombre = "Transferencia +5 %", porcentaje = "5", productoIds = setOf(1)),
                    ),
                    onBack = {},
                    acciones = accionesPrecios,
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_000)

        rule.onNodeWithText("Método de pago").assertExists()
        rule.onNodeWithText("Cualquier pago").assertExists()
        val ancho = rule.onNodeWithText("Subir precio").fetchSemanticsNode().boundsInRoot.width
        val minimo = with(rule.density) { 100.dp.toPx() }
        assertTrue("el chip quedó aplastado: ${ancho}px (mínimo $minimo)", ancho >= minimo)
    }
}
