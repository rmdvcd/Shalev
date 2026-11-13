package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.ClaseArticulo
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.FiltroServicios
import cu.shalev.domain.model.IdArticulo
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.Servicio
import cu.shalev.domain.model.Venta
import cu.shalev.domain.model.comoProducto
import cu.shalev.domain.model.vendible
import cu.shalev.domain.service.Estadisticas
import cu.shalev.domain.service.LineaSolicitada
import cu.shalev.domain.usecase.CotizarVenta
import cu.shalev.domain.usecase.GuardarServicio
import cu.shalev.domain.usecase.ServiciosFiltro
import cu.shalev.domain.validation.Validadores
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P29: Servicios (sustituyen a Elaboración) e insumos vendibles desde el Inventario. */
class ServiciosTest {

    private fun servicio(id: Long, nombre: String = "S$id", tipo: String = "Peluquería", importe: Long = 200, creado: java.time.Instant = T0) =
        Servicio(id = id, nombre = nombre, tipo = tipo, importe = Cup.ofPesos(importe), creadoEn = creado)

    private val productos = FakeProductos()
    private val insumos = FakeInsumos().apply { put(insumo(1, "Tinte", precio = 30, cantidad = 5)) }
    private val servicios = FakeServicios()
    private val cotizar = CotizarVenta(productos, FakePrecios(), insumos, servicios)

    @Test fun alcanceYCostoSalenDeLosInsumosPorVez() {
        val d = ServiciosFiltro.disponible(servicio(1), listOf(RecetaLinea(1, Cantidad.enteras(2))), insumos.items.value)
        assertEquals(2L, d.alcanza) // 5 / 2 = 2 veces
        assertEquals(Cup.ofPesos(60), d.costo)
        assertTrue(d.vendible)
        val sinInsumos = ServiciosFiltro.disponible(servicio(2), emptyList(), emptyMap())
        assertNull(sinInsumos.alcanza) // sin tope
        assertTrue(sinInsumos.vendible)
        val borrado = ServiciosFiltro.disponible(servicio(3), listOf(RecetaLinea(99, Cantidad.enteras(1))), emptyMap())
        assertEquals(0L, borrado.alcanza)
        assertFalse(borrado.vendible)
    }

    @Test fun filtroPorTipoYTextoSinTildes() {
        val ss = listOf(servicio(1, "Corte de pelo"), servicio(2, "Manicura", tipo = "Uñas"), servicio(3, "Peinado").copy(eliminado = true))
        val v = ServiciosFiltro.aplicar(ss, emptyMap(), emptyMap(), FiltroServicios(texto = "UNAS"))
        assertEquals(listOf("Manicura"), v.items.map { it.servicio.nombre })
        assertEquals(2, v.total)
        assertEquals(listOf("Peluquería", "Uñas"), v.tipos)
        assertEquals(1, ServiciosFiltro.aplicar(ss, emptyMap(), emptyMap(), FiltroServicios(tipo = "peluquería")).items.size)
    }

    @Test fun validacionDelServicio() {
        assertTrue(Validadores.servicio(servicio(1), emptyList()).isEmpty())
        val malo = servicio(1, nombre = " ", tipo = "", importe = 0)
        assertEquals(setOf("nombre", "tipo", "importe"), Validadores.servicio(malo, emptyList()).map { it.campo }.toSet())
        val repetido = listOf(RecetaLinea(1, Cantidad.enteras(1)), RecetaLinea(1, Cantidad.enteras(2)))
        assertTrue(Validadores.servicio(servicio(1), repetido).any { it.campo == "insumos" })
    }

    @Test fun guardarServicioCreaYEdita() = runTest {
        val guardar = GuardarServicio(servicios, FixedClock())
        val id = (guardar(servicio(0), listOf(RecetaLinea(1, Cantidad.enteras(1)))) as AppResult.Ok).value
        assertEquals(1, servicios.insumos(id).size)
        assertTrue(guardar(servicio(id, nombre = "Corte"), emptyList()) is AppResult.Ok)
        assertEquals("Corte", servicios.obtener(id)?.nombre)
        assertTrue(servicios.insumos(id).isEmpty())
    }

    @Test fun ventaDeServiciosCobraElImporteYTopaPorInsumos() = runTest {
        servicios.crear(servicio(10, importe = 250), listOf(RecetaLinea(1, Cantidad.enteras(2))))
        servicios.crear(servicio(11, importe = 100), emptyList())
        val ok = cotizar(listOf(LineaSolicitada(10, 2, ClaseArticulo.SERVICIO), LineaSolicitada(11, 40, ClaseArticulo.SERVICIO)), MetodoPago.EFECTIVO)
        val c = (ok as AppResult.Ok).value
        assertEquals(Cup.ofPesos(500 + 4000), c.total)
        assertTrue(c.detalles.all { it.clase == ClaseArticulo.SERVICIO })
        assertEquals(Cup.ofPesos(60), c.detalles.first { it.productoId == 10L }.costoUnitario)
        // 3 veces necesitan 6 de tinte y solo hay 5.
        val sinInsumos = cotizar(listOf(LineaSolicitada(10, 3, ClaseArticulo.SERVICIO)), MetodoPago.EFECTIVO)
        assertTrue((sinInsumos as AppResult.Err).error is AppError.StockInsuficiente)
    }

    @Test fun productosYServiciosNoSeMezclanEnUnaVenta() = runTest {
        productos.crear(producto(0, "Refresco"), null)
        servicios.crear(servicio(10), emptyList())
        val pid = productos.items.value.keys.first()
        val r = cotizar(listOf(LineaSolicitada(pid, 1), LineaSolicitada(10, 1, ClaseArticulo.SERVICIO)), MetodoPago.EFECTIVO)
        assertEquals(AppError.Validacion("lineas", AppError.Regla.NO_PERMITIDO), (r as AppResult.Err).error)
    }

    @Test fun insumoConPrecioSeVendeEnUnidadesEnteras() = runTest {
        insumos.put(insumo(2, "Azúcar", precio = 40, cantidad = 3).copy(cantidad = Cantidad(3500), precioVenta = Cup.ofPesos(60)))
        val azucar = insumos.items.value.getValue(2)
        assertTrue(azucar.vendible)
        assertEquals(IdArticulo.deInsumo(2), azucar.comoProducto().id)
        assertEquals(3L, azucar.comoProducto().cantidad) // 3.5 kg → 3 unidades enteras
        assertEquals(Categorias.INSUMOS, azucar.comoProducto().categoria)
        val c = (cotizar(listOf(LineaSolicitada(2, 3, ClaseArticulo.INSUMO)), MetodoPago.EFECTIVO) as AppResult.Ok).value
        assertEquals(ClaseArticulo.INSUMO, c.detalles.single().clase)
        assertEquals(2L, c.detalles.single().productoId) // id real del insumo, en positivo
        assertEquals(Cup.ofPesos(180), c.total)
        assertTrue(cotizar(listOf(LineaSolicitada(2, 4, ClaseArticulo.INSUMO)), MetodoPago.EFECTIVO) is AppResult.Err)
        // Sin precio de venta no se puede vender.
        assertFalse(insumos.items.value.getValue(1).vendible)
        assertTrue(cotizar(listOf(LineaSolicitada(1, 1, ClaseArticulo.INSUMO)), MetodoPago.EFECTIVO) is AppResult.Err)
    }

    @Test fun indicadoresDeServicios() {
        val ss = listOf(servicio(1, "Corte"), servicio(2, "Tinte"), servicio(3, "Peinado"))
        fun d(id: Long, n: Long) = DetalleVenta(productoId = id, nombre = "S$id", categoria = "Peluquería", cantidad = n,
            precioBase = Cup.ofPesos(100), precioUnitario = Cup.ofPesos(100), costoUnitario = Cup.ZERO, clase = ClaseArticulo.SERVICIO)
        val ventas = listOf(Venta(turnoId = 1, fecha = T0, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(d(1, 3), d(2, 1))))
        val t = Estadisticas.topServicios(ventas, ss)
        assertEquals(listOf("Corte", "Tinte"), t.masVendidos.map { it.nombre })
        assertEquals(listOf("Peinado", "Tinte", "Corte"), t.menosVendidos.map { it.nombre })
        assertEquals(0L, t.menosVendidos.first().unidades)
        // Sin ventas de servicios, los indicadores se ocultan.
        assertTrue(Estadisticas.topServicios(emptyList(), ss).vacio)
    }
}
