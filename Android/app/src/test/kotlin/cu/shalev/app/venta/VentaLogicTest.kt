package cu.shalev.app.venta

import cu.shalev.domain.model.ClienteFijo
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.EstadoCaducidad
import cu.shalev.domain.model.NivelStock
import cu.shalev.domain.model.ItemInventario
import cu.shalev.app.T0
import cu.shalev.app.common.Entrada
import cu.shalev.app.common.EntradaCompartida
import cu.shalev.app.inicio.TipoVenta
import cu.shalev.app.inventario.SeleccionVenta
import cu.shalev.app.prod
import cu.shalev.core.money.Cup
import cu.shalev.core.result.AppError
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.TipoArticulo
import cu.shalev.domain.usecase.SmsPago
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VentaLogicTest {

    // ---------------- Carrito ----------------

    @Test fun nuevaSeleccionConservaCantidadesYOrden() {
        val c = Carrito.desdeSeleccion(listOf(3, 1, 2, 3), mapOf(1L to 5L, 9L to 2L))
        assertEquals(listOf(3L, 1L, 2L), c.keys.toList())
        assertEquals(listOf(1L, 5L, 1L), c.values.toList())
    }

    @Test fun cantidadEntreUnoYLasExistencias() {
        val c = linkedMapOf(1L to 2L)
        assertEquals(5L, Carrito.cambiar(c, 1, 99, maximo = 5)[1])
        assertEquals(1L, Carrito.cambiar(c, 1, 0, maximo = 5)[1])
        assertEquals(Carrito.MAX_CANTIDAD, Carrito.cambiar(c, 1, 50_000, maximo = null)[1]) // sin tope: el límite del campo
        assertEquals(c, Carrito.cambiar(c, 7, 3, null))                                      // id ajeno: sin cambios
        assertTrue(Carrito.quitar(c, 1).isEmpty())
    }

    @Test fun lineasDescartanProductosBorrados() {
        val ps = mapOf(1L to prod(1), 2L to prod(2).copy(eliminado = true))
        val l = Carrito.lineas(linkedMapOf(1L to 2L, 2L to 1L, 3L to 1L), ps)
        assertEquals(listOf(1L), l.map { it.producto.id })
        assertEquals(Cup.ofPesos(200), Carrito.totalEstimado(l))
    }

    @Test fun parseCantidadSoloDigitosPositivos() {
        assertEquals(12L, Carrito.parseCantidad("12"))
        assertEquals(12L, Carrito.parseCantidad("1a2"))
        assertNull(Carrito.parseCantidad(""))
        assertNull(Carrito.parseCantidad("0"))
        assertEquals(1234L, Carrito.parseCantidad("123456"))  // máx. 4 cifras
    }

    @Test fun carritoSeAplanaYRestaura() {
        val c = linkedMapOf(4L to 2L, 1L to 7L)
        assertEquals(c, Carrito.restaurar(Carrito.aplanar(c)))
        assertTrue(Carrito.restaurar(null).isEmpty())
        assertTrue(Carrito.restaurar(longArrayOf(1)).isEmpty())
    }

    /** P26: el tope de un Elaborado es lo que alcanzan sus insumos; sus existencias guardadas se ignoran. */
    @Test fun elaboradoTopadoPorLoQueAlcanzanSusInsumos() {
        val pan = prod(9, "Pan", cantidad = 50, categoria = Categorias.ELABORADO)
        val l = Carrito.lineas(linkedMapOf(9L to 2L, 1L to 1L), mapOf(9L to pan, 1L to prod(1, cantidad = 5)), mapOf(9L to 3L))
        assertEquals(3L, l[0].maximo)
        assertEquals("Alcanza para 3", TextosVenta.disponibles(l[0]))
        assertEquals(5L, l[1].maximo)
        assertEquals("Hay 5", TextosVenta.disponibles(l[1]))
        val sinDato = Carrito.lineas(linkedMapOf(9L to 1L), mapOf(9L to pan)).single()
        assertEquals(0L, sinDato.maximo)
        assertEquals("Sin insumos suficientes", TextosVenta.disponibles(sinDato))
    }

    /** P24: un artículo solo muestra existencias al llegar al tope; un Elaborado (P26) siempre dice cuánto alcanza. */
    @Test fun existenciasSoloComoAviso() {
        assertFalse(TextosVenta.avisarExistencias(LineaCarrito(prod(1, cantidad = 5), cantidad = 2)))
        assertTrue(TextosVenta.avisarExistencias(LineaCarrito(prod(1, cantidad = 5), cantidad = 5)))
        assertTrue(TextosVenta.avisarExistencias(LineaCarrito(prod(9, "Pan", categoria = Categorias.ELABORADO), cantidad = 1, alcanza = 9)))
    }

    // ---------------- Formulario del cliente ----------------

    @Test fun sugerenciaClienteFijoMuestraCadaDatoEnSuPropiaLinea() {
        val cliente = ClienteFijo(
            nombreApellidos = "Ana Díaz", ci = "85010112345", telefono = "5123 4567",
            creadoEn = T0, actualizadoEn = T0,
        )
        assertEquals("Carné 85010112345\nTel. 5123 4567", TextosVenta.sugerencia(cliente))
    }

    @Test fun formularioVacioTieneCuatroErroresQueSoloSeVenTrasIntentar() {
        val f = FormCliente()
        assertEquals(CampoCliente.entries.toSet(), f.errores().keys)
        assertTrue(f.visibles().isEmpty())
        assertEquals(4, f.copy(intento = true).visibles().size)
        assertEquals(setOf(CampoCliente.CI), f.con(CampoCliente.CI, "12").visibles().keys)
    }

    @Test fun formularioNormalizaLoQueSeTeclea() {
        val f = FormCliente()
            .con(CampoCliente.NOMBRE, "María Pérez")
            .con(CampoCliente.CI, "850101-12345")
            .con(CampoCliente.TELEFONO, "5123 4567")
            .con(CampoCliente.NUMERO, "br601-adlm8997")
        assertEquals("85010112345", f.ci)
        assertEquals("BR601ADLM8997", f.numero)
        assertTrue(f.errores().isEmpty())
        assertEquals("María Pérez", f.datos().nombreApellidos)
    }

    @Test fun mensajesDeErrorSinJerga() {
        assertEquals("No alcanza: Pan, Sal. Ajusta las cantidades.", TextosVenta.mensaje(AppError.StockInsuficiente(listOf("Pan", "Sal"))))
        assertEquals("El turno está cerrado: ábrelo para vender.", TextosVenta.mensaje(AppError.TurnoCerrado))
        assertEquals(TextosVenta.ERROR, TextosVenta.mensaje(AppError.Desconocido(IllegalStateException("interno"))))
    }

    // ---------------- QR ----------------

    @Test fun qrSeGeneraDeterministaConPatronesDeEsquina() {
        val c = "TRANSFERMOVIL_ETECSA,TRANSFERENCIA,9248129970876454,51815604,"
        val m = MatrizQr.generar(c)
        assertTrue(m.lado >= 21 + 4)
        // Patrón de posición arriba-izquierda (7×7) tras 2 módulos de margen: esquina y centro negros.
        assertTrue(m[2, 2]); assertTrue(m[5, 5]); assertFalse(m[0, 0])
        val otra = MatrizQr.generar(c)
        assertEquals(m.lado, otra.lado)
        assertTrue((0 until m.lado).all { y -> (0 until m.lado).all { x -> m[x, y] == otra[x, y] } })
    }

    // ---------------- Inventario en modo venta ----------------

    @Test fun seleccionVentaReglas() {
        fun item(p: Producto, alcanza: Long? = null) = ItemInventario(p, NivelStock.NORMAL, EstadoCaducidad.SIN_FECHA, alcanza)
        assertTrue(SeleccionVenta.vendible(item(prod(1, cantidad = 1))))
        assertFalse(SeleccionVenta.vendible(item(prod(1, cantidad = 0))))
        assertEquals(SeleccionVenta.NO_VENDIBLE, SeleccionVenta.motivo(item(prod(1, cantidad = 0))))
        // P26: un Elaborado se puede vender solo si sus insumos alcanzan (sus existencias guardadas no cuentan).
        assertTrue(SeleccionVenta.vendible(item(prod(9, cantidad = 0, categoria = Categorias.ELABORADO), alcanza = 2)))
        assertFalse(SeleccionVenta.vendible(item(prod(9, cantidad = 5, categoria = Categorias.ELABORADO), alcanza = 0)))
        assertEquals(SeleccionVenta.NO_VENDIBLE_ELABORADO, SeleccionVenta.motivo(item(prod(9, categoria = Categorias.ELABORADO), alcanza = 0)))
        assertFalse(SeleccionVenta.vendible(item(prod(1).copy(eliminado = true))))
        // P29: la venta de productos muestra todo (artículos, elaborados e insumos vendibles).
        assertEquals(TipoArticulo.TODOS, SeleccionVenta.filtroInicial(TipoVenta.VENTA))
        assertEquals(TipoArticulo.TODOS, SeleccionVenta.filtroInicial(null))
        assertEquals(listOf(3L, 1L), SeleccionVenta.parse(SeleccionVenta.csv(listOf(3, 1))).toList())
        assertTrue(SeleccionVenta.parse(null).isEmpty())
        assertEquals(setOf(2L), SeleccionVenta.parse("x,2,,"))
    }

    // ---------------- Entrada compartida ----------------

    @Test fun entradaSeConsumeUnaSolaVez() {
        val e = EntradaCompartida()
        val t = Entrada.Texto("sms")
        e.publicar(t)
        assertTrue(e.consumir(t))
        assertFalse(e.consumir(t))
        assertNull(e.entrada.value)
        assertEquals(T0, T0) // (reloj de los fakes sin uso aquí)
    }

    @Test fun capturaAutomaticaSoloPublicaDatosMinimosConVentaActivaYSinOtraEntradaPendiente() {
        val e = EntradaCompartida()
        val pago = SmsPago("BR601ADLM8997", Cup.ofPesos(725))
        assertFalse(e.publicarSmsAutomatico(pago))
        e.habilitarCapturaSmsAutomatica(true)
        assertTrue(e.publicarSmsAutomatico(pago))
        assertEquals(Entrada.SmsPagoAutomatico(pago), e.entrada.value)
        assertFalse(e.publicarSmsAutomatico(SmsPago("OTRO123", null))) // no pisa la entrada pendiente
        val actual = e.entrada.value!!
        assertTrue(e.consumir(actual))
        assertNull(e.entrada.value)
        e.habilitarCapturaSmsAutomatica(false)
        assertFalse(e.publicarSmsAutomatico(SmsPago("DESPUES123", null)))
    }
}
