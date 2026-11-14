package cu.shalev.app

import android.app.Application
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cu.shalev.app.common.CampoFecha
import cu.shalev.app.common.descripcionFiltro
import cu.shalev.app.precios.AccionesPrecios
import cu.shalev.app.precios.PreajusteForm
import cu.shalev.app.precios.PreciosContent
import cu.shalev.app.precios.PreciosUiState
import cu.shalev.designsystem.component.ChartSeries
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevAccordionCard
import cu.shalev.designsystem.component.ShalevAreaChart
import cu.shalev.designsystem.component.ShalevBarChart
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevFab
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevTextButton
import cu.shalev.designsystem.component.ShalevTextField
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
 * Auditoría de UI: comprueba semántica accesible, acciones de ventanas adaptables, gráficos sin eje Y y tooltip, y el
 * formulario de Precios a 360 dp. Son pruebas de comportamiento, no capturas: detectan etiquetas visibles incorrectas,
 * selección anunciada sin estado conmutable y el chip de Transferencia aplastado.
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
                    subtitle = "1\u202F000 CUP",
                    expanded = abierto.value,
                    onToggle = { abierto.value = !abierto.value },
                ) { androidx.compose.material3.Text("Detalle") }
            }
        }

        rule.onNodeWithText("1\u202F000 CUP").assertExists()
        rule.onNodeWithText("Ventas").performClick()
        rule.onAllNodesWithText("1\u202F000 CUP").assertCountEquals(0)
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
    fun fueraDeVentanasLasAccionesSiguenCompactasYAccesibles() {
        rule.setContent { ShalevTheme { ShalevIconAction(ShalevIcons.Agregar, "Agregar producto", onClick = {}) } }

        rule.onNodeWithContentDescription("Agregar producto").assertExists()
        rule.onAllNodesWithText("Agregar producto").assertCountEquals(0)
    }

    @Test
    fun elFabEsSoloIconoPorDefectoYSigueTeniendoDescripcion() {
        rule.setContent { ShalevTheme { ShalevFab(ShalevIcons.Agregar, "Agregar producto", onClick = {}) } }

        rule.onNodeWithContentDescription("Agregar producto").assertExists()
        rule.onAllNodesWithText("Agregar producto").assertCountEquals(0)
    }

    @Test
    fun aceptarEnElSelectorDeFechaEsTextoVisible() {
        rule.setContent { ShalevTheme { CampoFecha(fecha = null, onFecha = {}, label = "Fecha") } }

        rule.onNodeWithTag("campo_fecha_elegir").performClick()
        rule.onNodeWithContentDescription("Aceptar").assertExists()
        rule.onNodeWithText("Aceptar", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun graficoDeVentasOmiteEjeYConservaEtiquetasYAlternaTooltipAlTocar() {
        rule.setContent {
            ShalevTheme {
                ShalevBarChart(
                    values = listOf(100.0, 200.0), labels = listOf("08:00", "09:00"),
                    description = "Ventas de hoy", valueUnit = "CUP",
                )
            }
        }

        val grafico = rule.onNodeWithContentDescription("Ventas de hoy")
        grafico.assertExists()
        listOf("08:00", "09:00").forEach { rule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
        listOf("CUP", "200", "100", "0").forEach {
            rule.onAllNodesWithText(it, useUnmergedTree = true).assertCountEquals(0)
        }

        grafico.performTouchInput { click(center) }
        rule.onNodeWithText("09:00 · 200 CUP", useUnmergedTree = true).assertIsDisplayed()
        grafico.performTouchInput { click(center) }
        rule.onAllNodesWithText("09:00 · 200 CUP", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun graficoDeGananciaConservaEtiquetasDeFechaSinEjeY() {
        rule.setContent {
            ShalevTheme {
                ShalevAreaChart(
                    series = listOf(
                        ChartSeries("Venta", listOf(500.0, 300.0), Color.Blue),
                        ChartSeries("Costo", listOf(250.0, 150.0), Color.Red),
                    ),
                    labels = listOf("01/09", "02/09"), description = "Ganancia bruta",
                )
            }
        }

        rule.onNodeWithContentDescription("Ganancia bruta").assertExists()
        listOf("01/09", "02/09", "Venta", "Costo").forEach {
            rule.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed()
        }
        listOf("CUP", "500", "250", "0").forEach {
            rule.onAllNodesWithText(it, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // 1.0.5: las acciones de diálogos y ventanas muestran su etiqueta escrita, también durante una carga.
    // El pie no añade Cancelar: la ventana se cierra tocando fuera o con Atrás.
    @Test
    fun todasLasAccionesDeVentanaMuestranTextoYConservanLaDescripcion() {
        rule.setContent {
            ShalevTheme {
                ShalevDialog(
                    title = "Prueba de acciones",
                    onDismiss = {},
                    onConfirm = {},
                    confirmLoading = true,
                    content = {
                        ShalevIconAction(ShalevIcons.Eliminar, "Eliminar", onClick = {}, style = IconActionStyle.Tonal)
                        ShalevIconAction(ShalevIcons.AhoraNo, "Omitir", onClick = {}, style = IconActionStyle.Tonal)
                        ShalevIconAction(ShalevIcons.Exportar, "Exportar", onClick = {}, style = IconActionStyle.Tonal)
                        ShalevPrimaryButton("Guardar", onClick = {}, icon = ShalevIcons.Guardar, loading = true)
                        ShalevSecondaryButton("Cancelar", onClick = {}, icon = ShalevIcons.Cancelar)
                        ShalevTextButton("Volver", onClick = {}, icon = ShalevIcons.Atras)
                        ShalevFab(ShalevIcons.Agregar, "Agregar producto", onClick = {}, etiqueta = "Nuevo")
                        ShalevTextField(value = "dato", onValueChange = {}, label = "Buscar", clearable = true)
                    },
                )
            }
        }

        listOf("Confirmar", "Eliminar", "Omitir", "Exportar", "Guardar", "Cancelar", "Volver").forEach { etiqueta ->
            rule.onNodeWithContentDescription(etiqueta).assertExists()
            rule.onNodeWithText(etiqueta, useUnmergedTree = true).assertIsDisplayed()
        }
        rule.onNodeWithContentDescription("Agregar producto").assertExists()
        rule.onNodeWithText("Nuevo", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Limpiar Buscar").assertExists()
        rule.onNodeWithText("Limpiar", useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodesWithText("Cerrar").assertCountEquals(0)
        rule.onAllNodesWithText("Cerrar").assertCountEquals(0)
    }

    // 1.0.5: el pie conserva el orden y centra las etiquetas; si no caben en una fila, fluyen a la siguiente.
    @Test
    fun lasAccionesDelDialogoMantienenElOrdenCuandoElPieSeEnvuelve() {
        rule.setContent {
            ShalevTheme {
                ShalevDialog(
                    title = "Revisar cierre",
                    onDismiss = {},
                    onConfirm = {},
                    modifier = Modifier.widthIn(max = 240.dp),
                    secundarias = {
                        ShalevIconAction(ShalevIcons.Atras, "Atrás", onClick = {})
                        ShalevIconAction(ShalevIcons.AhoraNo, "Omitir", onClick = {})
                    },
                ) {
                    Text("El inventario cuadra.")
                }
            }
        }

        val orden = listOf("Atrás", "Omitir", "Confirmar").map { etiqueta ->
            rule.onNodeWithText(etiqueta, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertTrue("el pie estrecho debe envolver al menos una acción", orden.map { it.center.y }.distinct().size > 1)
        rule.onAllNodesWithText("Cancelar").assertCountEquals(0)
        rule.onAllNodesWithText("Cancelar").assertCountEquals(0)
        orden.zipWithNext().forEach { (anterior, siguiente) ->
            val mismaFila = kotlin.math.abs(anterior.center.y - siguiente.center.y) <= 1f
            assertTrue(
                "Se conserva el orden de izquierda a derecha o de arriba abajo",
                if (mismaFila) anterior.right <= siguiente.left else anterior.center.y < siguiente.center.y,
            )
        }
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
