package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.EstadoCaducidad
import cu.shalev.domain.model.FichaProducto
import cu.shalev.domain.model.FiltroInventario
import cu.shalev.domain.model.LineaFicha
import cu.shalev.domain.model.NivelStock
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.TipoArticulo
import cu.shalev.domain.service.InventarioFiltro
import cu.shalev.domain.service.TablasExport
import cu.shalev.domain.usecase.EliminarProductos
import cu.shalev.domain.usecase.ObtenerFichaProducto
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscanerInventarioTest {

    // ---------------- Tabla del Inventario ----------------

    private val hoy = LocalDate.of(2026, 9, 1)
    private val n = NivelesMinimos()
    private val ps = listOf(
        producto(1, "Café Serrano", cantidad = 30, categoria = "Alimentos", cad = hoy.plusDays(3)),
        producto(2, "Refresco", cantidad = 4, categoria = "Bebidas", cad = hoy.minusDays(1)),
        producto(3, "Pizza", cantidad = 1, categoria = "Elaborado"),
        producto(4, "Cerveza", cantidad = 50, categoria = "bebidas", cad = hoy.plusDays(60)),
        producto(5, "Borrado", categoria = "Bebidas").copy(eliminado = true),
    )

    @Test fun conservaElOrdenYMarcaNivelYCaducidad() {
        val v = InventarioFiltro.aplicar(ps, FiltroInventario(), n, hoy, 7, alcanza = mapOf(3L to 6L))
        assertEquals(listOf(1L, 2L, 3L, 4L), v.items.map { it.producto.id })
        assertEquals(4, v.total)
        // P26: la Pizza (Elaborado) no tiene existencias → nunca bajo/crítico; lleva «Alcanza para 6».
        assertEquals(listOf(NivelStock.NORMAL, NivelStock.BAJO, NivelStock.NORMAL, NivelStock.NORMAL), v.items.map { it.nivel })
        assertEquals(listOf(null, null, 6L, null), v.items.map { it.alcanza })
        assertEquals(
            listOf(EstadoCaducidad.PROXIMA, EstadoCaducidad.VENCIDA, EstadoCaducidad.SIN_FECHA, EstadoCaducidad.VIGENTE),
            v.items.map { it.caducidad },
        )
        assertEquals(listOf("Alimentos", "Bebidas", "Elaborado"), v.categorias) // sin duplicar "bebidas"
    }

    @Test fun conservaLasFilasSinFiltrarParaMantenerLaSeleccion() {
        val v = InventarioFiltro.aplicar(ps, FiltroInventario(texto = "cafe"), n, hoy, 7)
        assertEquals(listOf(1L), v.items.map { it.producto.id })
        assertEquals(listOf(1L, 2L, 3L, 4L), v.elementosCompletos.map { it.producto.id })
    }

    @Test fun buscadorIgnoraTildesYBuscaNombreCategoriaYDescripcion() {
        val conDescripcion = ps.map { if (it.id == 1L) it.copy(descripcion = "Grano tostado de montaña") else it }
        fun ids(texto: String) = InventarioFiltro.aplicar(conDescripcion, FiltroInventario(texto = texto), n, hoy, 7).items.map { it.producto.id }

        assertEquals(listOf(1L), ids(" CAFE "))
        assertEquals(listOf(2L, 4L), ids("bebidas"))
        assertEquals(listOf(1L), ids("tostado"))
    }

    @Test fun filtrosDeCategoriaTipoYAlerta() {
        fun ids(f: FiltroInventario) = InventarioFiltro.aplicar(ps, f, n, hoy, 7).items.map { it.producto.id }
        assertEquals(listOf(2L, 4L), ids(FiltroInventario(categoria = "BEBIDAS")))
        assertEquals(listOf(3L), ids(FiltroInventario(tipo = TipoArticulo.ELABORADOS)))
        assertEquals(listOf(1L, 2L, 4L), ids(FiltroInventario(tipo = TipoArticulo.ARTICULOS)))
        assertEquals(listOf(2L), ids(FiltroInventario(alerta = TipoAlerta.STOCK_BAJO)))
        assertEquals(emptyList<Long>(), ids(FiltroInventario(alerta = TipoAlerta.STOCK_CRITICO))) // P26: el Elaborado no alerta
        assertEquals(listOf(1L, 2L), ids(FiltroInventario(alerta = TipoAlerta.PROXIMO_A_CADUCAR)))
        assertEquals(2, FiltroInventario(categoria = "x", alerta = TipoAlerta.STOCK_BAJO, texto = "y").activos)
    }

    // ---------------- Ficha, guardado y borrado ----------------

    @Test fun fichaParaClientesNoLlevaCostoNiExistencias() {
        val p = producto(1, "Refresco", cantidad = 12, venta = 150, costo = 90, bajo = 5, cad = hoy).copy(descripcion = "Lata 355 ml")
        val f = FichaProducto(p, emptyList(), NivelStock.NORMAL, EstadoCaducidad.PROXIMA)
        val clientes = TablasExport.camposFicha(f, paraClientes = true)
        assertTrue(clientes.contains("Precio" to "150 CUP"))
        assertTrue(clientes.contains("Caducidad" to "01/09/2026"))
        val texto = clientes.joinToString { "${it.first}: ${it.second}" }
        assertFalse(texto.contains("90.00")); assertFalse(texto.contains("Cantidad")); assertFalse(texto.contains("Nivel"))
        assertFalse(texto.contains("Código")) // sin código de barras: la ficha no lo muestra
        val pdf = TablasExport.ficha(f)
        assertEquals(listOf("Campo", "Valor"), pdf.columnas)
        assertTrue(pdf.filas.contains(listOf("Precio costo", "90 CUP")))
        assertTrue(pdf.filas.contains(listOf("Nivel bajo", "5")))
        assertFalse(pdf.filas.any { it[0] == "Nivel crítico" }) // sin dato, no aparece
        assertFalse(pdf.filas.any { it[0] == "Código" }) // sin código de barras: la ficha no lo muestra
    }

    @Test fun fichaDeElaboradoResuelveLaReceta() = runBlocking<Unit> {
        val insumos = FakeInsumos().apply { put(insumo(7, "Harina", precio = 20)) }
        val productos = FakeProductos().apply {
            put(producto(3, "Pizza", categoria = "Elaborado"))
            recetas[3] = cu.shalev.domain.model.Receta(3, listOf(cu.shalev.domain.model.RecetaLinea(7, Cantidad(250))))
        }
        val f = (ObtenerFichaProducto(productos, insumos, FakePreferencias(), FixedClock())(3) as AppResult.Ok).value
        assertEquals(listOf(LineaFicha(7, "Harina", Cantidad(250), "u", Cup.ofPesos(5))), f.receta)
        assertTrue(TablasExport.ficha(f).filas.contains(listOf("Receta", "Harina 0.25 u")))
        assertEquals(AppResult.Err(AppError.NoEncontrado), ObtenerFichaProducto(productos, insumos, FakePreferencias(), FixedClock())(99))
    }

    @Test fun eliminarVariosCuentaLosFallidos() = runBlocking<Unit> {
        val productos = FakeProductos().apply { put(producto(1), producto(2)) }
        assertEquals(EliminarProductos.Resultado(2, 1), EliminarProductos(productos)(listOf(1, 2, 2, 9)))
        assertTrue(productos.items.value.getValue(1).eliminado)
    }
}
