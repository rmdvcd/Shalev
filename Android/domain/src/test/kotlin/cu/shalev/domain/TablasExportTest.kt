package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.model.EstadoCaducidad
import cu.shalev.domain.model.FichaInsumo
import cu.shalev.domain.model.FichaProducto
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.LineaConteo
import cu.shalev.domain.model.LineaFicha
import cu.shalev.domain.model.NivelStock
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.UnidadMedida
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Venta
import cu.shalev.domain.service.TablasExport
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TablasExportTest {
    @Test fun `ventas a tabla con total`() {
        val v = Venta(7, 1, T0, MetodoPago.EFECTIVO, listOf(DetalleVenta(productoId = 1, nombre = "Refresco", categoria = "Bebidas", cantidad = 2, precioBase = Cup.ofPesos(725), precioUnitario = Cup.ofPesos(725), costoUnitario = Cup.ofPesos(500))))
        val t = TablasExport.ventas(listOf(v), ZoneId.of("America/Havana"))
        assertEquals(listOf("01/09/2026 08:00", "", "Efectivo", "Refresco ×2", "2", "1,450 CUP", "1,000 CUP", "450 CUP", "Válida"), t.filas.single())
        assertEquals("Ventas", t.titulo)
        assertTrue(t.pie.last().endsWith("Total: 1,450 CUP"))
    }

    @Test fun `telefonos y tarjetas del archivo conservan los digitos almacenados`() {
        val transaccion = Transaccion(
            fecha = T0,
            importe = Cup.ofPesos(1450),
            numero = "TM123456",
            cliente = DatosCliente("Ana Pérez", "85010112345", "+5351234567"),
            tarjetaCobro = "9205129900001234",
            telefonoCobro = "+5359876543",
        )
        val tabla = TablasExport.transacciones(listOf(transaccion), ZoneId.of("America/Havana"))
        assertEquals("+5351234567", tabla.filas.single()[5])
        val campos = TablasExport.camposTransaccion(transaccion).toMap()
        assertEquals("+5351234567", campos["Teléfono"])
        assertEquals("9205129900001234", campos["Cobrado en la tarjeta"])
        assertEquals("+5359876543", campos["Teléfono de cobro"])
    }

    @Test fun `fichas visibles agrupan cantidades sin cambiar formato de exportacion`() {
        val producto = Producto(
            id = 8, categoria = "Bebidas", nombre = "Café", precioCosto = Cup.ofPesos(1234),
            precioVenta = Cup.ofPesos(2345), cantidad = 1234, nivelBajo = 1000, nivelCritico = 1234, creadoEn = T0,
        )
        val ficha = FichaProducto(producto, emptyList(), NivelStock.NORMAL, EstadoCaducidad.SIN_FECHA)
        val exportacion = TablasExport.camposFicha(ficha, paraClientes = false).toMap()
        val pantalla = TablasExport.camposFichaVisual(ficha).toMap()
        assertEquals("1,234 CUP", exportacion["Precio costo"])
        assertEquals("1\u202F234 CUP", pantalla["Precio costo"])
        assertEquals("1234", exportacion["Cantidad"])
        assertEquals("1\u202F234", pantalla["Cantidad"])
        assertEquals("1\u202F000", pantalla["Nivel bajo"])

        val elaborado = ficha.copy(
            producto = producto.copy(categoria = "Elaborado", cantidad = 0),
            receta = listOf(LineaFicha(1, "Harina", Cantidad(1_234_500), "kg", Cup.ofPesos(100))),
            alcanza = 1_234,
        )
        assertEquals("Harina 1,234.5 kg", TablasExport.camposFicha(elaborado, paraClientes = false).toMap()["Receta"])
        val camposVisibles = TablasExport.camposFichaVisual(elaborado).toMap()
        assertEquals("Harina 1\u202F234.5 kg", camposVisibles["Receta"])
        assertEquals("1\u202F234", camposVisibles["Alcanza para"])
    }

    @Test fun `ficha de insumo usa agrupacion visual pero su tabla conserva comas`() {
        val insumo = Insumo(
            id = 4, nombre = "Harina", unidad = UnidadMedida.KILOGRAMO, precio = Cup.ofPesos(1234),
            cantidad = Cantidad(1_234_500), nivelBajo = Cantidad(1_000_000), creadoEn = T0,
        )
        val campos = TablasExport.camposInsumo(FichaInsumo(insumo, NivelStock.NORMAL, emptyList())).toMap()
        assertEquals("1\u202F234 CUP / kg", campos["Precio (costo)"])
        assertEquals("1\u202F234.5 kg", campos["Cantidad"])
        assertEquals("1\u202F000 kg", campos["Nivel bajo"])
        assertEquals("1,234.5 kg", TablasExport.insumos(listOf(insumo)).filas.single()[2])
    }

    @Test fun `conteo visible agrupa cifras estrechas y el archivo conserva comas`() {
        val conteo = cu.shalev.domain.model.ConteoCierre(inventario = listOf(
            LineaConteo(TipoEntidad.INSUMO, 1, "Harina", "kg", 1_234_500, 2_000_000, 0),
            LineaConteo(TipoEntidad.PRODUCTO, 2, "Café", "u", 1_234, 2_000, 0),
        ))
        val exportacion = TablasExport.filasConteo(conteo)
        val pantalla = TablasExport.filasConteoVisual(conteo)
        assertEquals("1,234.5", exportacion[0][3])
        assertEquals("1\u202F234.5", pantalla[0][3])
        assertEquals("1234", exportacion[1][3])
        assertEquals("1\u202F234", pantalla[1][3])
    }

    @Test fun `inventario omite eliminados`() {
        val t = TablasExport.inventario(listOf(producto(1), producto(2).copy(eliminado = true)))
        assertEquals(1, t.filas.size)
    }

    /** 0.25.1: el turno en PDF/Excel lleva resumen, arqueo (con diferencia) y ventas; omite tablas vacías. */
    @Test fun `turno completo con arqueo`() {
        val z = ZoneId.of("America/Havana")
        val v = Venta(7, 1, T0, MetodoPago.EFECTIVO, listOf(DetalleVenta(productoId = 1, nombre = "Refresco", categoria = "Bebidas", cantidad = 2, precioBase = Cup.ofPesos(725), precioUnitario = Cup.ofPesos(725), costoUnitario = Cup.ofPesos(500))))
        val t = cu.shalev.domain.model.Turno(1, T0, T0.plusSeconds(3600), abiertoPor = "Ana Pérez", cerradoPor = "Ana Pérez", fondo = Cup.ofPesos(500), contado = Cup.ofPesos(1900))
        val d = cu.shalev.domain.model.DetalleTurno(t, listOf(v), emptyList(), emptyList())
        val tablas = TablasExport.turno(d, z)
        assertEquals(listOf("Turno del 01/09/2026 08:00", "Arqueo de caja", "Ventas"), tablas.map { it.titulo })
        val resumen = tablas[0].filas.associate { it[0] to it[1] }
        assertEquals("1,450 CUP", resumen["Total vendido"])
        assertEquals("01/09/2026 09:00 · Ana Pérez", resumen["Cierre"])
        val arqueo = tablas[1].filas.associate { it[0] to it[1] }
        assertTrue(arqueo.toString(), arqueo.values.any { it.contains("1,950 CUP") }) // esperado = 500 + 1,450
        assertEquals("Shalev_Turno_2026-09-01_0800.xlsx", TablasExport.nombreArchivoTurno(t, "xlsx", z))
        // Abierto: provisional; sin arqueo (turno antiguo) la tabla lo explica.
        val viejo = TablasExport.turno(cu.shalev.domain.model.DetalleTurno(cu.shalev.domain.model.Turno(2, T0), emptyList(), emptyList(), emptyList()), z)
        assertTrue(viejo[0].titulo.endsWith("(provisional)"))
        assertEquals(2, viejo.size)
        assertTrue(viejo[1].filas.isNotEmpty())
    }
}
