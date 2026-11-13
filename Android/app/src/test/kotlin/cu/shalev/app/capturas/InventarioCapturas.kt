package cu.shalev.app.capturas

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.inicio.TipoVenta
import cu.shalev.app.inventario.AccionesInventario
import cu.shalev.app.inventario.ConfirmarEliminar
import cu.shalev.app.inventario.HojaInventario
import cu.shalev.app.inventario.InventarioContent
import cu.shalev.app.inventario.InventarioUiState
import cu.shalev.app.inventario.TextosInventario
import cu.shalev.app.producto.AccionesForm
import cu.shalev.app.producto.LineaRecetaForm
import cu.shalev.app.producto.ProductoForm
import cu.shalev.app.producto.ProductoFormContent
import cu.shalev.app.producto.ProductoFormUiState
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.EstadoCaducidad
import cu.shalev.domain.model.FichaProducto
import cu.shalev.domain.model.FiltroInventario
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.ItemInventario
import cu.shalev.domain.model.LineaFicha
import cu.shalev.domain.model.NivelStock
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.UnidadMedida
import cu.shalev.domain.model.VistaInventario
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Inventario (lista, selección, ficha, hojas, confirmación) y formulario de producto. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = Captura.TELEFONO, application = Application::class)
class InventarioCapturas {
    @get:Rule val rule = createComposeRule()

    private val t0 = Captura.AHORA.minusSeconds(30L * 86_400)
    private fun p(id: Long, cat: String, nombre: String, venta: Long, cantidad: Long, cad: LocalDate? = null) = Producto(
        id = id, categoria = cat, nombre = nombre, precioCosto = Cup.ofPesos(venta * 6 / 10), precioVenta = Cup.ofPesos(venta),
        cantidad = cantidad, fechaCaducidad = cad, creadoEn = t0,
    )
    private val items = listOf(
        ItemInventario(p(1, "Bebidas", "Refresco de lata 355 ml", 150, 48), NivelStock.NORMAL, EstadoCaducidad.VIGENTE),
        ItemInventario(p(2, "Confituras", "Galletas de chocolate", 150, 1, LocalDate.of(2026, 10, 9)), NivelStock.CRITICO, EstadoCaducidad.PROXIMA),
        ItemInventario(p(3, "Bodega", "Aceite 1 L", 900, 4), NivelStock.BAJO, EstadoCaducidad.SIN_FECHA),
        ItemInventario(p(4, "Elaborado", "Pizza napolitana", 400, 0), NivelStock.NORMAL, EstadoCaducidad.SIN_FECHA, alcanza = 12), // P26
        ItemInventario(p(5, "Bebidas", "Malta 330 ml", 180, 24), NivelStock.NORMAL, EstadoCaducidad.VENCIDA),
        ItemInventario(p(6, "Bodega", "Arroz 1 kg", 320, 30), NivelStock.NORMAL, EstadoCaducidad.SIN_FECHA),
    )
    private val categorias = listOf("Bebidas", "Bodega", "Confituras", "Elaborado")
    private fun lista(f: (InventarioUiState) -> InventarioUiState = { it }) =
        f(InventarioUiState(vista = EstadoCarga.Exito(VistaInventario(items, items.size, categorias))))

    private fun inventario(id: String, s: InventarioUiState) = rule.capturar(id) { InventarioContent(s, AccionesInventario()) }

    @Test fun listaConNiveles() = inventario("02a_inventario_lista", lista())
    @Test fun cargando() = inventario("02e_inventario_cargando", InventarioUiState())
    @Test fun vacio() = inventario("02b_inventario_vacio", InventarioUiState(vista = EstadoCarga.Vacio(VistaInventario(emptyList(), 0, emptyList()))))
    @Test fun sinCoincidencias() = inventario(
        "02c_inventario_sin_resultados",
        InventarioUiState(vista = EstadoCarga.Exito(VistaInventario(emptyList(), 6, categorias)), filtro = FiltroInventario(texto = "zzz")),
    )
    @Test fun error() = inventario("02d_inventario_error", InventarioUiState(vista = EstadoCarga.Error("No se pudo cargar el inventario")))
    @Test fun filtroPorAlerta() = inventario(
        "02f2_inventario_filtro_critico",
        InventarioUiState(vista = EstadoCarga.Exito(VistaInventario(items.take(2).drop(1), 6, categorias)), filtro = FiltroInventario(alerta = TipoAlerta.STOCK_CRITICO)),
    )
    @Test fun seleccion() = inventario("02f_inventario_seleccion", lista { it.copy(seleccion = setOf(2, 3)) })
    @Test fun confirmarEliminar() = inventario("02m_inventario_dialogo_eliminar", lista { it.copy(seleccion = setOf(2, 3), confirmar = ConfirmarEliminar.Seleccion(2)) })
    @Test fun ficha() = inventario(
        "02k_inventario_ficha",
        lista { it.copy(ficha = FichaProducto(items[1].producto, emptyList(), NivelStock.CRITICO, EstadoCaducidad.PROXIMA)) },
    )
    @Test fun fichaLetraGrande() = rule.capturar("02z_inventario_ficha_letra_200") {
        LetraGrande {
            InventarioContent(
                lista { it.copy(ficha = FichaProducto(items[1].producto, emptyList(), NivelStock.CRITICO, EstadoCaducidad.PROXIMA)) },
                AccionesInventario(),
            )
        }
    }
    @Test fun fichaElaborado() = inventario(
        "02k2_inventario_ficha_elaborado",
        lista {
            it.copy(
                ficha = FichaProducto(
                    items[3].producto,
                    listOf(
                        LineaFicha(1, "Harina de trigo", Cantidad(250), "kg", Cup.ofPesos(70)),
                        LineaFicha(2, "Queso gouda", Cantidad(80), "kg", Cup.ofPesos(160)),
                    ),
                    NivelStock.NORMAL, EstadoCaducidad.SIN_FECHA, alcanza = 12,
                ),
            )
        },
    )
    @Test fun hojaFiltro() = inventario("02i_inventario_hoja_filtro", lista { it.copy(hoja = HojaInventario.FILTRO) })
    @Test fun hojaCompartirFicha() = inventario(
        "02l_inventario_hoja_compartir_ficha",
        lista { it.copy(ficha = FichaProducto(items[1].producto, emptyList(), NivelStock.CRITICO, EstadoCaducidad.PROXIMA), hoja = HojaInventario.COMPARTIR_FICHA) },
    )
    @Test fun modoVenta() = inventario("02o_inventario_modo_venta", lista { it.copy(modoVenta = TipoVenta.VENTA, seleccion = setOf(1, 4)) })
    @Test fun snackbar() = rule.capturar("02n_inventario_snackbar") {
        InventarioContent(lista { it.copy(trabajando = true) }, AccionesInventario(), snackbarCon(TextosInventario.eliminados(3, 0)))
    }
    @Test fun hojaExportar() = inventario("02j_inventario_hoja_exportar", lista { it.copy(seleccion = setOf(1, 2), hoja = HojaInventario.EXPORTAR) })

    // ---------- Formulario de producto ----------
    private val insumos = listOf(
        Insumo(id = 1, nombre = "Harina de trigo", unidad = UnidadMedida.KILOGRAMO, precio = Cup.ofPesos(280), cantidad = Cantidad.enteras(10), creadoEn = t0),
        Insumo(id = 2, nombre = "Queso gouda", unidad = UnidadMedida.KILOGRAMO, precio = Cup.ofPesos(2000), cantidad = Cantidad.enteras(3), creadoEn = t0),
        Insumo(id = 3, nombre = "Puré de tomate", unidad = UnidadMedida.KILOGRAMO, precio = Cup.ofPesos(450), cantidad = Cantidad.enteras(2), creadoEn = t0),
    )
    private fun form(id: String, s: ProductoFormUiState) = rule.capturar(id) { ProductoFormContent(s, AccionesForm()) }

    @Test fun productoNuevo() = form("08j_producto_nuevo", ProductoFormUiState())
    @Test @Config(qualifiers = Captura.LARGA) fun productoErrores() = form(
        "08k_producto_errores",
        ProductoFormUiState(form = ProductoForm(categoria = "Bebidas", precioVenta = "-1"), intentado = true),
    )
    @Test @Config(qualifiers = Captura.LARGA) fun productoEditar() = form(
        "08l_producto_editar",
        ProductoFormUiState(
            form = ProductoForm(
                id = 1, creadoEn = t0, categoria = "Bebidas", nombre = "Refresco de lata 355 ml", descripcion = "Sabor cola. Caja de 24.",
                fechaCaducidad = LocalDate.of(2027, 3, 15), precioCosto = "100.00", precioVenta = "150.00", cantidad = "48",
                nivelBajo = "12", nivelCritico = "4",
            ),
        ),
    )
    private val pizza = ProductoForm(
        id = 4, creadoEn = t0, categoria = "Elaborado", nombre = "Pizza napolitana", descripcion = "Masa fina, 10 pulgadas.",
        receta = listOf(
            LineaRecetaForm(1, "Harina de trigo", "kg", Cup.ofPesos(280), "0.25"),
            LineaRecetaForm(3, "Puré de tomate", "kg", Cup.ofPesos(450), "0.1"),
            LineaRecetaForm(2, "Queso gouda", "kg", Cup.ofPesos(2000), "0.08"),
        ),
        precioVenta = "400.00", cantidad = "4",
    )
    @Test @Config(qualifiers = Captura.LARGA) fun productoElaborado() = form("08m_producto_elaborado_receta", ProductoFormUiState(form = pizza, insumos = insumos))
    @Test fun productoHojaInsumos() = form("08n_producto_hoja_insumos", ProductoFormUiState(form = pizza, insumos = insumos, hojaInsumos = true))
    @Test fun productoDialogoSalir() = rule.capturar("08o_producto_dialogo_salir", antes = { onNodeWithContentDescription("Atrás").performClick() }) {
        ProductoFormContent(ProductoFormUiState(form = ProductoForm(categoria = "Bebidas", nombre = "Malta"), cambios = true), AccionesForm())
    }
    @Test fun productoElaboradoSinReceta() = form(
        "08p_producto_elaborado_sin_receta",
        ProductoFormUiState(form = ProductoForm(categoria = "Elaborado", nombre = "Pan con jamón"), intentado = true),
    )
}
