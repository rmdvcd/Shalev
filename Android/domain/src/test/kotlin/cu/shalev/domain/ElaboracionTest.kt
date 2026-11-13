package cu.shalev.domain

import cu.shalev.domain.service.Stock
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.FichaInsumo
import cu.shalev.domain.model.FiltroInsumos
import cu.shalev.domain.model.NivelStock
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.UnidadMedida
import cu.shalev.domain.model.UsoEnReceta
import cu.shalev.domain.model.UsoInsumo
import cu.shalev.domain.repository.ExportadorDocumentos
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.service.InsumosFiltro
import cu.shalev.domain.service.TablaExport
import cu.shalev.domain.service.TablasExport
import cu.shalev.domain.usecase.EliminarInsumos
import cu.shalev.domain.usecase.ExportarInsumos
import cu.shalev.domain.usecase.ObservarElaboracion
import cu.shalev.domain.usecase.ObservarElaborados
import cu.shalev.domain.usecase.ObtenerFichaInsumo
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.Dispatchers

class ElaboracionTest {

    private val n = NivelesMinimos() // insumos: bajo 5, crítico 1

    private fun ins(id: Long, nombre: String, cantidad: Long, precio: Long = 10, bajo: Long? = null, creado: Long = id) =
        insumo(id, nombre, precio, cantidad).copy(nivelBajo = bajo?.let(Cantidad::enteras), creadoEn = T0.plusSeconds(creado))

    // ---------------- Filtro y vista ----------------

    @Test fun `buscador ignora tildes y mayusculas y conserva el orden del repositorio`() {
        val xs = listOf(ins(3, "Azúcar", 10), ins(2, "Harina", 10), ins(1, "azucar moreno", 10))
        val v = InsumosFiltro.aplicar(xs, emptyMap(), FiltroInsumos(texto = "AZUCAR"), n)
        assertEquals(listOf(3L, 1L), v.items.map { it.insumo.id })
        assertEquals(3, v.total)
    }

    @Test fun `filtro por alerta usa las mismas reglas que Inicio`() {
        val xs = listOf(ins(1, "Sal", 1), ins(2, "Aceite", 4), ins(3, "Harina", 50), ins(4, "Queso", 8, bajo = 10))
        val bajo = InsumosFiltro.aplicar(xs, emptyMap(), FiltroInsumos(alerta = TipoAlerta.INSUMO_BAJO), n)
        val critico = InsumosFiltro.aplicar(xs, emptyMap(), FiltroInsumos(alerta = TipoAlerta.INSUMO_CRITICO), n)
        assertEquals(setOf(2L, 4L), bajo.items.map { it.insumo.id }.toSet()) // el nivel propio (10) manda sobre el global
        assertEquals(listOf(1L), critico.items.map { it.insumo.id })
        assertEquals(NivelStock.CRITICO, critico.items.single().nivel)
        // Una alerta de productos no aplica a insumos.
        assertTrue(InsumosFiltro.aplicar(xs, emptyMap(), FiltroInsumos(alerta = TipoAlerta.STOCK_BAJO), n).items.isEmpty())
    }

    @Test fun `filtro por uso en recetas y conteo de usos`() {
        val recetas = listOf(
            Receta(10, listOf(RecetaLinea(1, Cantidad.enteras(1)), RecetaLinea(2, Cantidad(500)))),
            Receta(11, listOf(RecetaLinea(1, Cantidad.enteras(2)))),
        )
        val usos = InsumosFiltro.usos(recetas)
        assertEquals(mapOf(1L to 2, 2L to 1), usos)
        val xs = listOf(ins(1, "Harina", 10), ins(2, "Sal", 10), ins(3, "Vainilla", 10))
        assertEquals(listOf(1L, 2L), InsumosFiltro.aplicar(xs, usos, FiltroInsumos(uso = UsoInsumo.EN_RECETAS), n).items.map { it.insumo.id })
        assertEquals(listOf(3L), InsumosFiltro.aplicar(xs, usos, FiltroInsumos(uso = UsoInsumo.SIN_USO), n).items.map { it.insumo.id })
        assertEquals(2, FiltroInsumos(alerta = TipoAlerta.INSUMO_BAJO, uso = UsoInsumo.SIN_USO).activos)
    }

    @Test fun `ObservarElaboracion reacciona a insumos, recetas y filtro`() = runBlocking {
        val insumos = FakeInsumos().apply { put(ins(1, "Harina", 10), ins(2, "Sal", 10)) }
        val productos = FakeProductos().apply { this.insumos = insumos }
        val filtro = MutableStateFlow(FiltroInsumos(uso = UsoInsumo.EN_RECETAS))
        val uc = ObservarElaboracion(insumos, productos, FakePreferencias(), Dispatchers.Unconfined)
        assertTrue(uc(filtro).first().items.isEmpty())

        productos.crear(producto(0, "Pan", categoria = "Elaborado"), Receta(0, listOf(RecetaLinea(1, Cantidad(250)))))
        assertEquals(listOf(1L), uc(filtro).first().items.map { it.insumo.id })
        assertEquals(1, uc(filtro).first().items.single().usos)

        filtro.value = FiltroInsumos(texto = "sal")
        assertEquals(listOf(2L), uc(filtro).first().items.map { it.insumo.id })
    }

    // ---------------- Ficha ----------------

    @Test fun `ficha indica en que elaborados se usa y el valor de lo que hay`() = runBlocking {
        val insumos = FakeInsumos().apply { put(ins(1, "Harina", 3, precio = 20)) }
        val productos = FakeProductos().apply { this.insumos = insumos }
        productos.crear(producto(0, "Pan", categoria = "Elaborado"), Receta(0, listOf(RecetaLinea(1, Cantidad(250)))))
        productos.crear(producto(0, "Galleta", categoria = "Elaborado"), Receta(0, listOf(RecetaLinea(1, Cantidad(100)))))
        val f = (ObtenerFichaInsumo(insumos, productos, FakePreferencias(), FakeServicios())(1) as AppResult.Ok).value
        assertEquals(listOf("Galleta", "Pan"), f.usadoEn.map { it.nombre })
        assertEquals(Cantidad(250), f.usadoEn.first { it.nombre == "Pan" }.cantidad)
        assertEquals(Cup.ofPesos(60), f.valorExistencias)
        assertEquals(NivelStock.BAJO, f.nivel)
        assertTrue(ObtenerFichaInsumo(insumos, productos, FakePreferencias(), FakeServicios())(99) is AppResult.Err)
    }

    @Test fun `campos de la ficha llevan los atributos con su unidad`() {
        val i = ins(1, "Harina", 3, precio = 20, bajo = 5).copy(unidad = UnidadMedida.KILOGRAMO, cantidad = Cantidad(2500))
        val t = TablasExport.camposInsumo(FichaInsumo(i, NivelStock.BAJO, listOf(UsoEnReceta(9, "Pan", Cantidad(250))))).toMap()
        assertEquals("Harina", t["Nombre"])
        assertEquals("20 CUP / kg", t["Precio (costo)"])
        assertEquals("2.5 kg", t["Cantidad"])
        assertEquals("5 kg", t["Nivel bajo"])
        assertEquals("Pan (0.25 kg por unidad)", t["Usado en"])
        assertTrue(!t.containsKey("Nivel crítico")) // opcional y vacío: no se muestra
    }

    // ---------------- Borrado y exportación ----------------

    @Test fun `borrado multiple informa los insumos que usa una receta`() = runBlocking {
        val repo = FakeInsumos().apply { put(ins(1, "Harina", 1), ins(2, "Sal", 1), ins(3, "Aceite", 1)); enUso = setOf(1) }
        val r = EliminarInsumos(repo)(listOf(1, 2, 3, 3))
        assertEquals(2, r.eliminados)
        assertEquals(listOf("Harina"), r.enUso)
        assertEquals(0, r.fallidos)
        assertEquals(setOf(1L), repo.items.value.keys)
    }

    @Test fun `exportar la seleccion reutiliza ExportarInsumos`() = runBlocking {
        val tablas = mutableListOf<TablaExport>()
        val exportador = object : ExportadorDocumentos {
            override suspend fun exportar(tablas2: List<TablaExport>, formato: FormatoExport, destino: OutputStream): AppResult<Unit> {
                tablas += tablas2; return AppResult.Ok(Unit)
            }
        }
        val uc = ExportarInsumos(FakeInsumos(), exportador)
        assertTrue(uc(emptyList(), FormatoExport.PDF, ByteArrayOutputStream()) is AppResult.Err)
        assertTrue(uc(listOf(ins(1, "Harina", 2)), FormatoExport.PDF, ByteArrayOutputStream()) is AppResult.Ok)
        val t = tablas.single()
        assertEquals(listOf("Nombre", "Precio (costo)", "Cantidad", "Nivel bajo", "Nivel crítico"), t.columnas)
        assertEquals(listOf("Harina", "10 CUP / u", "2 u", "", ""), t.filas.single())
    }

    // ---------------- Elaborados: «Alcanza para N» ----------------

    @Test fun `lo que alcanza lo marca el insumo mas escaso`() {
        val p = producto(10, "Pan", categoria = "Elaborado")
        val receta = Receta(10, listOf(RecetaLinea(1, Cantidad(250)), RecetaLinea(2, Cantidad(10))))
        val xs = mapOf(1L to ins(1, "Harina", 2), 2L to ins(2, "Sal", 1).copy(cantidad = Cantidad(35)))
        val e = InsumosFiltro.disponible(p, receta, xs)
        assertEquals(3, e.alcanza) // harina da para 8, sal para 3
        assertEquals(Cup(260), e.costoUnidad) // 0.25 × 10.00 + 0.010 × 10.00 = 2.60 CUP
    }

    @Test fun `receta con insumo inexistente no alcanza y avisa`() {
        val p = producto(10, "Pan", categoria = "Elaborado")
        val e = InsumosFiltro.disponible(p, Receta(10, listOf(RecetaLinea(7, Cantidad(100)))), emptyMap())
        assertEquals(0, e.alcanza)
        assertNull(e.costoUnidad)
        assertEquals(listOf("Insumo eliminado"), e.faltantes(1))
        assertTrue(!InsumosFiltro.disponible(p, null, emptyMap()).tieneReceta)
    }

    @Test fun `alcance de los Elaborados se recalcula al cambiar los insumos`() = runBlocking {
        val insumos = FakeInsumos().apply { put(ins(1, "Harina", 2)) }
        val productos = FakeProductos().apply { this.insumos = insumos }
        productos.crear(producto(0, "Pan", categoria = "Elaborado", cantidad = 0), Receta(0, listOf(RecetaLinea(1, Cantidad(250)))))
        val elaborados = ObservarElaborados(insumos, productos, Dispatchers.Unconfined)
        assertEquals(8, elaborados().first().single().alcanza)
        insumos.ajustarStock(1, Cantidad(-750), null) // p. ej., tras vender 3
        assertEquals(5, elaborados().first().single().alcanza)
        assertEquals(listOf("Harina"), elaborados().first().single().faltantes(6))
    }

    /** P26: un Elaborado no tiene existencias → nunca cuenta como stock bajo/crítico; solo sus insumos alertan. */
    @Test fun `Elaborados no generan alertas de stock`() {
        val n = NivelesMinimos()
        val pan = producto(10, "Pan", categoria = "Elaborado", cantidad = 0)
        assertEquals(NivelStock.NORMAL, Stock.nivel(pan, n))
        val c = Stock.conteo(listOf(pan, producto(11, "Refresco", cantidad = 0)), emptyList(), n, java.time.LocalDate.of(2026, 1, 1), 7)
        assertEquals(0, c.stockCritico); assertEquals(1, c.sinExistencia) // 0.21.0 (C1): el Refresco a 0
    }
}
