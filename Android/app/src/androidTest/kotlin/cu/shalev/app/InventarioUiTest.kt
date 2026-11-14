package cu.shalev.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.common.LocalPermisosApp
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.insumos.AccionesInsumoForm
import cu.shalev.app.insumos.InsumoForm
import cu.shalev.app.insumos.InsumoFormContent
import cu.shalev.app.insumos.InsumoFormTags
import cu.shalev.app.insumos.InsumoFormUiState
import cu.shalev.app.inventario.AccionesInventario
import cu.shalev.app.inventario.ConfirmarEliminar
import cu.shalev.app.inventario.InventarioContent
import cu.shalev.app.inventario.InventarioTags
import cu.shalev.app.inventario.InventarioUiState
import cu.shalev.app.inventario.TextosInventario
import cu.shalev.app.producto.AccionesForm
import cu.shalev.app.producto.ProductoForm
import cu.shalev.app.producto.ProductoFormContent
import cu.shalev.app.producto.ProductoFormTags
import cu.shalev.app.producto.ProductoFormUiState
import cu.shalev.core.money.Cup
import cu.shalev.designsystem.component.GuardiaDobleToque
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.EstadoCaducidad
import cu.shalev.domain.model.FichaProducto
import cu.shalev.domain.model.ItemInventario
import cu.shalev.domain.model.Modulo
import cu.shalev.domain.model.NivelStock
import cu.shalev.domain.model.PermisosApp
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.VistaInventario
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventarioUiTest {
    @get:Rule val rule = createComposeRule()

    private fun avanzarPaso() {
        val desde = android.os.SystemClock.elapsedRealtime()
        rule.onNodeWithTag("alta.siguiente").performClick()
        rule.waitUntil(2_000) { android.os.SystemClock.elapsedRealtime() - desde >= GuardiaDobleToque.VENTANA_MS }
    }

    private val t0 = Instant.parse("2026-09-30T16:00:00Z")
    private fun p(id: Long, nombre: String, cantidad: Long = 10) = Producto(
        id = id, categoria = "Bebidas", nombre = nombre, precioCosto = Cup.ofPesos(60), precioVenta = Cup.ofPesos(1450),
        cantidad = cantidad, creadoEn = t0,
    )
    private val items = listOf(
        ItemInventario(p(1, "Refresco"), NivelStock.NORMAL, EstadoCaducidad.SIN_FECHA),
        ItemInventario(p(2, "Galletas", 1), NivelStock.CRITICO, EstadoCaducidad.PROXIMA),
    )
    private fun lista(seleccion: Set<Long> = emptySet(), ficha: FichaProducto? = null, confirmar: ConfirmarEliminar? = null) =
        InventarioUiState(vista = EstadoCarga.Exito(VistaInventario(items, 2, listOf("Bebidas"))), seleccion = seleccion, ficha = ficha, confirmar = confirmar)

    private fun inventario(state: InventarioUiState, acciones: AccionesInventario = AccionesInventario()) =
        rule.setContent { ShalevTheme(darkTheme = false) { InventarioContent(state, acciones) } }

    @Test fun checkboxSeleccionaYFilaAbreLaFicha() {
        var marcado: Long? = null
        var abierto: Long? = null
        inventario(lista(), AccionesInventario(onAlternar = { marcado = it }, onAbrirFicha = { abierto = it }))
        rule.onNodeWithText("Refresco").assertIsDisplayed()
        rule.onAllNodesWithText("1\u202F450 CUP", useUnmergedTree = true).onFirst().assertExists()
        rule.onNodeWithTag(InventarioTags.check(2)).assertIsOff().performClick()
        assertEquals(2L, marcado)
        assertEquals(null, abierto) // el checkbox no abre la ficha
        rule.onNodeWithTag(InventarioTags.fila(1)).performClick()
        assertEquals(1L, abierto)
    }

    @Test fun seleccionadosSeFijanAntesDelBuscadorYNoSeDuplican() {
        inventario(lista(seleccion = setOf(1)))
        rule.onNodeWithTag(InventarioTags.FIJADOS).assertIsDisplayed()
        rule.onNodeWithTag(InventarioTags.fila(1)).assertIsDisplayed()
        rule.onAllNodesWithTag(InventarioTags.fila(1)).assertCountEquals(1)
        val arriba = rule.onAllNodesWithTag(InventarioTags.FIJADOS).fetchSemanticsNodes().single().boundsInRoot.top
        val buscador = rule.onAllNodesWithTag(InventarioTags.BUSCAR).fetchSemanticsNodes().single().boundsInRoot.top
        assertTrue("la selección debe quedar sobre el buscador", arriba < buscador)
    }

    @Test fun filtroSeEligeConCombobox() {
        var aplicado: cu.shalev.domain.model.FiltroInventario? = null
        inventario(
            lista().copy(hoja = cu.shalev.app.inventario.HojaInventario.FILTRO),
            AccionesInventario(onFiltro = { aplicado = it }),
        )
        rule.onNodeWithTag(InventarioTags.FILTRO_ESTADO).performClick()
        rule.onNodeWithTag(InventarioTags.alertaOpcion(cu.shalev.domain.model.TipoAlerta.STOCK_CRITICO)).performClick()
        rule.onNodeWithTag(InventarioTags.FILTRO_TIPO).performClick()
        rule.onNodeWithTag(InventarioTags.tipoOpcion(cu.shalev.domain.model.TipoArticulo.ARTICULOS)).performClick()
        rule.onNodeWithTag(InventarioTags.FILTRO_CATEGORIA).performClick()
        rule.onNodeWithTag(InventarioTags.categoriaOpcion("Bebidas")).performClick()
        rule.onNodeWithTag(InventarioTags.FILTRO_APLICAR).performClick()
        assertEquals(
            cu.shalev.domain.model.FiltroInventario(categoria = "Bebidas", alerta = cu.shalev.domain.model.TipoAlerta.STOCK_CRITICO, tipo = cu.shalev.domain.model.TipoArticulo.ARTICULOS),
            aplicado,
        )
    }

    @Test fun barraDeSeleccionConEliminarYExportar() {
        var eliminar = false
        inventario(lista(seleccion = setOf(1, 2)), AccionesInventario(onEliminarSeleccion = { eliminar = true }))
        rule.onNode(hasText("2 seleccionados") and hasAnyAncestor(hasTestTag(InventarioTags.SELECCION))).assertIsDisplayed()
        rule.onNodeWithTag(InventarioTags.check(1)).assertIsOn()
        rule.onNodeWithTag(InventarioTags.ELIMINAR_SELECCION).performClick()
        assertEquals(true, eliminar)
    }

    @Test fun confirmacionAntesDeEliminar() {
        var confirmado = false
        inventario(lista(confirmar = ConfirmarEliminar.Seleccion(2)), AccionesInventario(onConfirmarEliminar = { confirmado = true }))
        rule.onNodeWithText("¿Eliminar 2 productos?").assertIsDisplayed()
        rule.onNodeWithText(TextosInventario.ELIMINAR_DETALLE).assertIsDisplayed()
        rule.onNodeWithContentDescriptionEliminar()
        assertEquals(true, confirmado)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionEliminar() =
        onNode(androidx.compose.ui.test.hasContentDescription("Eliminar") and androidx.compose.ui.test.hasClickAction()).performClick()

    /** P24: el estado vacío ya no repite «Agregar»; la única vía es el botón flotante (+), siempre visible. */
    @Test fun vacioYSinResultados() {
        inventario(InventarioUiState(vista = EstadoCarga.Vacio(VistaInventario(emptyList(), 0, emptyList()))), AccionesInventario())
        rule.onNodeWithText(TextosInventario.VACIO_TITULO).assertIsDisplayed()
        rule.onNodeWithTag(InventarioTags.AGREGAR).assertIsDisplayed()
        rule.onAllNodes(hasContentDescription(TextosInventario.AGREGAR) and hasClickAction()).assertCountEquals(1)
    }

    @Test fun filtradoSinCoincidenciasOfreceQuitarFiltros() {
        var quitado = false
        inventario(
            InventarioUiState(vista = EstadoCarga.Exito(VistaInventario(emptyList(), 5, emptyList()))),
            AccionesInventario(onQuitarFiltros = { quitado = true }),
        )
        rule.onNodeWithText(TextosInventario.SIN_RESULTADOS_TITULO).assertIsDisplayed()
        rule.onNodeWithTag(InventarioTags.QUITAR_FILTROS).performClick()
        assertEquals(true, quitado)
    }

    @Test fun accesosContextualesDeInventarioDeleganEnSusAcciones() {
        var fichas = false
        var gestion = false
        inventario(lista(), AccionesInventario(onFichasCosto = { fichas = true }, onGestionInventario = { gestion = true }))

        rule.onNodeWithTag(InventarioTags.FICHAS_COSTO).assertIsDisplayed().performClick()
        rule.onNodeWithTag(InventarioTags.GESTION_INVENTARIO).assertIsDisplayed().performClick()
        assertTrue(fichas)
        assertTrue(gestion)
    }

    @Test fun gestionDeInventarioSeOcultaSinPermiso() {
        rule.setContent {
            CompositionLocalProvider(LocalPermisosApp provides PermisosApp.PRINCIPAL.copy(modulos = setOf(Modulo.SERVICIOS))) {
                ShalevTheme(darkTheme = false) { InventarioContent(lista(), AccionesInventario()) }
            }
        }
        rule.onNodeWithTag(InventarioTags.FICHAS_COSTO).assertIsDisplayed()
        rule.onNodeWithTag(InventarioTags.GESTION_INVENTARIO).assertDoesNotExist()
    }

    @Test fun fichaConEditarCompartirYEliminar() {
        var editado: Long? = null
        val ficha = FichaProducto(p(2, "Galletas", 1), emptyList(), NivelStock.CRITICO, EstadoCaducidad.SIN_FECHA)
        inventario(lista(ficha = ficha), AccionesInventario(onEditar = { editado = it }))
        rule.onNodeWithTag(InventarioTags.FICHA).assertExists()
        rule.onNodeWithTag(InventarioTags.FICHA_COMPARTIR).assertExists()
        rule.onNodeWithTag(InventarioTags.FICHA_ELIMINAR).assertExists()
        rule.onNodeWithTag(InventarioTags.FICHA_EDITAR).performClick()
        assertEquals(2L, editado)
    }

    // ---------- Formulario ----------

    private fun formulario(state: ProductoFormUiState) =
        rule.setContent { ShalevTheme(darkTheme = false) { ProductoFormContent(state, AccionesForm()) } }

    @Test fun elaboradoOcultaFotoFechaCantidadYNivelesYMuestraRecetaEnSuPaso() {
        formulario(ProductoFormUiState(form = ProductoForm(categoria = "Elaborado")))
        avanzarPaso()
        rule.onNodeWithTag(ProductoFormTags.RECETA).assertExists()
        rule.onNodeWithTag(ProductoFormTags.FOTO).assertDoesNotExist()
        rule.onNodeWithTag(ProductoFormTags.FECHA).assertDoesNotExist()
        rule.onNodeWithTag(ProductoFormTags.COSTO).assertDoesNotExist() // se calcula con la receta
        avanzarPaso()
        // P26: sin existencias ni niveles propios (alcanza según sus insumos).
        rule.onNodeWithTag(ProductoFormTags.CANTIDAD).assertDoesNotExist()
        rule.onNodeWithTag(ProductoFormTags.BAJO).assertDoesNotExist()
        rule.onNodeWithTag(ProductoFormTags.CRITICO).assertDoesNotExist()
    }

    @Test fun articuloAvanzaPorTresPasosYOfreceRecepcionInicialAlFinal() {
        formulario(ProductoFormUiState(form = ProductoForm(categoria = "Bebidas"), intentado = true))
        rule.onNodeWithTag("alta.paso.1").assertExists()
        rule.onNodeWithTag(ProductoFormTags.FOTO).assertExists()
        rule.onNodeWithTag(ProductoFormTags.COSTO).assertDoesNotExist()
        rule.onNodeWithText("Escribe el nombre.", useUnmergedTree = true).assertExists()

        avanzarPaso()
        rule.onNodeWithTag("alta.paso.2").assertExists()
        rule.onNodeWithTag(ProductoFormTags.COSTO).assertExists()
        rule.onNodeWithTag(ProductoFormTags.VENTA).assertDoesNotExist()

        avanzarPaso()
        rule.onNodeWithTag("alta.paso.3").assertExists()
        rule.onNodeWithTag(ProductoFormTags.VENTA).assertExists()
        rule.onNodeWithTag(ProductoFormTags.CANTIDAD).assertExists()
        rule.onNodeWithTag("alta.guardar_recibir").assertExists()
        rule.onNodeWithTag("alta.guardar").assertExists()
    }

    @Test fun insumoAvanzaPorTresPasosYOfreceRecepcionInicialAlFinal() {
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                InsumoFormContent(InsumoFormUiState(form = InsumoForm()), AccionesInsumoForm())
            }
        }
        rule.onNodeWithTag(InsumoFormTags.NOMBRE).assertExists()
        rule.onNodeWithTag(InsumoFormTags.PRECIO).assertDoesNotExist()

        avanzarPaso()
        rule.onNodeWithTag(InsumoFormTags.PRECIO).assertExists()
        rule.onNodeWithTag(InsumoFormTags.CANTIDAD).assertExists()
        rule.onNodeWithTag(InsumoFormTags.PRECIO_VENTA).assertDoesNotExist()

        avanzarPaso()
        rule.onNodeWithTag(InsumoFormTags.PRECIO_VENTA).assertExists()
        rule.onNodeWithTag("alta.guardar_recibir").assertExists()
        rule.onNodeWithTag("alta.guardar").assertExists()
    }
}
