package cu.shalev.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.repository.InsumoRepositoryImpl
import cu.shalev.data.repository.ProductoRepositoryImpl
import cu.shalev.data.repository.RegistroRepositoryImpl
import cu.shalev.data.repository.ServicioRepositoryImpl
import cu.shalev.data.repository.TurnoRepositoryImpl
import cu.shalev.data.repository.VentaRepositoryImpl
import cu.shalev.domain.model.ClaseArticulo
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.ElaboradoEnVenta
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.Servicio
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.UnidadMedida
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.FiltroRegistro
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 0.21.9 (P59) — inventario, servicios y Registros contra Room real en memoria. Cubre con SQL real lo que antes solo
 * tenía pruebas de lógica: F5 (búsqueda literal con ESCAPE), F7 (sumar con existencia negativa), F8 (la ficha abierta
 * no deshace ventas), servicios que gastan insumos, insumos vendidos sueltos y líneas repetidas de un producto.
 */
@RunWith(AndroidJUnit4::class)
class InventarioRoomTest {

    private var ahora = Instant.parse("2026-10-03T15:00:00Z")
    private val clock = Clock { ahora }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ShalevDatabase::class.java).build()
    private val productos = ProductoRepositoryImpl(db, clock)
    private val insumos = InsumoRepositoryImpl(db, clock)
    private val servicios = ServicioRepositoryImpl(db, clock)
    private val turnos = TurnoRepositoryImpl(db)
    private val ventas = VentaRepositoryImpl(db)
    private val registros = RegistroRepositoryImpl(db)

    @After fun cerrarBd() = db.close()

    private fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Ok)?.value ?: error("esperaba Ok y fue $r")
    private fun err(r: AppResult<*>): AppError = (r as? AppResult.Err)?.error ?: error("esperaba Err y fue $r")

    private suspend fun nuevoProducto(nombre: String, cantidad: Long): Producto =
        productos.obtener(ok(productos.crear(Producto(categoria = "Bebidas", nombre = nombre, precioCosto = Cup.ofPesos(60),
            precioVenta = Cup.ofPesos(100), cantidad = cantidad, creadoEn = ahora), null)))!!

    private suspend fun nuevoInsumo(nombre: String, unidades: Long, precioVenta: Cup? = null, unidad: UnidadMedida = UnidadMedida.UNIDAD): Insumo =
        insumos.obtener(ok(insumos.crear(Insumo(nombre = nombre, unidad = unidad, precio = Cup.ofPesos(10),
            cantidad = Cantidad.enteras(unidades), creadoEn = ahora, precioVenta = precioVenta))))!!

    private fun linea(p: Producto, n: Long) = DetalleVenta(productoId = p.id, nombre = p.nombre, categoria = p.categoria,
        cantidad = n, precioBase = p.precioVenta, precioUnitario = p.precioVenta, costoUnitario = p.precioCosto)

    private suspend fun vender(turnoId: Long, vararg lineas: DetalleVenta, elaborados: List<ElaboradoEnVenta> = emptyList(),
                               transaccion: Transaccion? = null) =
        ventas.registrar(Venta(turnoId = turnoId, fecha = ahora, metodoPago = if (transaccion == null) MetodoPago.EFECTIVO else MetodoPago.TRANSFERENCIA,
            detalles = lineas.toList(), transaccion = transaccion), elaborados)

    // ---------------------------------------------------------------- F5: búsqueda literal

    @Test fun busquedaDeRegistrosEsLiteralConPorcientoGuionBajoYBarra() = runBlocking {
        val conGuion = nuevoProducto("Ron A_B", 10)
        val parecido = nuevoProducto("Ron AXB", 10)
        val cien = nuevoProducto("Jugo 100%", 10)
        val mil = nuevoProducto("Jugo 1000", 10)
        val barra = nuevoProducto("Caja 1\\2", 10)
        val t = ok(turnos.abrir(ahora, "Ana"))
        listOf(conGuion, parecido, cien, mil, barra).forEach { ok(vender(t.id, linea(it, 1))) }

        suspend fun ventasCon(texto: String) =
            registros.ventas(FiltroRegistro(texto = texto)).first().flatMap { v -> v.detalles.map { it.nombre } }.sorted()

        assertEquals(listOf("Ron A_B"), ventasCon("A_B"))       // «_» no es comodín
        assertEquals(listOf("Jugo 100%"), ventasCon("100%"))    // «%» no es comodín
        assertEquals(listOf("Caja 1\\2"), ventasCon("1\\2"))     // la barra se busca tal cual
        assertEquals(listOf("Jugo 100%", "Jugo 1000"), ventasCon("jugo 100")) // sin distinguir mayúsculas
        assertEquals(5, registros.ventas(FiltroRegistro(texto = "  ")).first().size) // en blanco = sin filtro

        val movs = registros.movimientos(FiltroRegistro(texto = "A_B")).first()
        assertTrue(movs.isNotEmpty() && movs.all { it.nombre == "Ron A_B" })
    }

    @Test fun busquedaDeTransaccionesEsLiteral() = runBlocking {
        val p = nuevoProducto("Refresco", 10)
        val t = ok(turnos.abrir(ahora, "Ana"))
        fun tx(numero: String, nombre: String) = Transaccion(fecha = ahora, importe = Cup.ofPesos(100), numero = numero,
            cliente = DatosCliente(nombre, "90020212345", "+5353000000"))
        ok(vender(t.id, linea(p, 1), transaccion = tx("MM10040FEJ987", "Ana_Maria")))
        ok(vender(t.id, linea(p, 1), transaccion = tx("MM10040FEJ988", "AnaXMaria")))

        val conGuion = registros.transacciones(FiltroRegistro(texto = "Ana_")).first()
        assertEquals(listOf("MM10040FEJ987"), conGuion.map { it.numero })
        assertTrue(registros.transacciones(FiltroRegistro(texto = "%")).first().isEmpty())
        assertEquals(2, registros.transacciones(FiltroRegistro(texto = "fej98")).first().size)
    }

    // ---------------------------------------------------------------- F7: existencia negativa

    @Test fun conExistenciaNegativaSiempreSePuedeSumarPeroNoRestar() = runBlocking {
        val p = nuevoProducto("Refresco", 2)
        val i = nuevoInsumo("Harina", 1)
        // Así queda tras sincronizar ventas hechas sin existencias en otra app.
        db.syncDao().forzarStockProducto(p.id, -5, ahora.toEpochMilli())
        db.syncDao().forzarStockInsumo(i.id, -3_000, ahora.toEpochMilli())
        assertEquals(-3L, productos.obtener(p.id)!!.cantidad)

        assertEquals(-2L, ok(productos.ajustarStock(p.id, 1, "compra")))
        assertTrue(err(productos.ajustarStock(p.id, -1, null)) is AppError.StockInsuficiente)
        assertEquals(-2L, productos.obtener(p.id)!!.cantidad)

        assertEquals(Cantidad(-1_500), ok(insumos.ajustarStock(i.id, Cantidad(500), null)))
        assertTrue(err(insumos.ajustarStock(i.id, Cantidad(-1), null)) is AppError.StockInsuficiente)
        assertEquals(Cantidad(-1_500), insumos.obtener(i.id)!!.cantidad)
    }

    // ---------------------------------------------------------------- F8: ficha abierta

    @Test fun guardarUnaFichaAbiertaNoDeshaceLasVentasHechasMientras() = runBlocking {
        val p = nuevoProducto("Refresco", 10)
        val leida = p.cantidad
        val t = ok(turnos.abrir(ahora, "Ana"))
        ok(vender(t.id, linea(p, 3)))                                       // mientras la ficha está abierta

        ok(productos.actualizar(p.copy(nombre = "Refresco cola"), null, leida)) // no tocó la cantidad
        assertEquals(7L, productos.obtener(p.id)!!.cantidad)
        assertEquals("Refresco cola", productos.obtener(p.id)!!.nombre)
        val ediciones = { registros.movimientos(FiltroRegistro()) }
        assertTrue(ediciones().first().none { it.nota == "Edición" })

        ok(productos.actualizar(p.copy(cantidad = 12), null, leida))        // la cambió: manda lo contado (Stock.cantidadAlGuardar)
        assertEquals(12L, productos.obtener(p.id)!!.cantidad)
        val ed = ediciones().first().single { it.nota == "Edición" }
        assertEquals(TipoMovimiento.AJUSTE, ed.tipo)
        assertEquals(5L, ed.delta)                                          // 7 → 12
        assertEquals(12L, ed.existenciaResultante)

        ok(productos.actualizar(p.copy(cantidad = 4), null, null))          // versión anterior: manda lo escrito
        assertEquals(4L, productos.obtener(p.id)!!.cantidad)
    }

    @Test fun guardarUnInsumoAbiertoNoDeshaceLosConsumos() = runBlocking {
        val harina = nuevoInsumo("Harina", 5, unidad = UnidadMedida.KILOGRAMO)
        val leida = harina.cantidad
        ok(insumos.ajustarStock(harina.id, Cantidad(-1_250), "merma"))
        ok(insumos.actualizar(harina.copy(nombre = "Harina de trigo"), leida))
        assertEquals(Cantidad(3_750), insumos.obtener(harina.id)!!.cantidad)
        ok(insumos.actualizar(harina.copy(cantidad = Cantidad.enteras(6)), leida))
        assertEquals(Cantidad.enteras(6), insumos.obtener(harina.id)!!.cantidad) // la cambió: manda lo contado
        assertEquals(AppError.NoEncontrado, err(insumos.actualizar(harina.copy(id = 999), leida)))
    }

    // ---------------------------------------------------------------- Servicios

    @Test fun servicioGastaSusInsumosAlVenderseYBloqueaBorrarlos() = runBlocking {
        val tinta = nuevoInsumo("Tinta", 5)
        val papel = nuevoInsumo("Papel", 2)
        val sId = ok(servicios.crear(Servicio(nombre = "Impresión", tipo = "Oficina", importe = Cup.ofPesos(50), creadoEn = ahora),
            listOf(RecetaLinea(tinta.id, Cantidad.enteras(1)), RecetaLinea(papel.id, Cantidad.enteras(1)))))
        assertEquals(listOf("Oficina"), servicios.tiposEnUso())
        assertEquals(setOf(tinta.id, papel.id), servicios.insumos(sId).map { it.insumoId }.toSet())
        assertEquals(listOf(sId), servicios.serviciosQueUsan(tinta.id).map { it.id })
        assertTrue(err(insumos.eliminar(tinta.id)) is AppError.EnUso)

        val t = ok(turnos.abrir(ahora, "Ana"))
        val det = DetalleVenta(productoId = sId, nombre = "Impresión", categoria = "Oficina", cantidad = 2,
            precioBase = Cup.ofPesos(50), precioUnitario = Cup.ofPesos(50), costoUnitario = Cup.ZERO, clase = ClaseArticulo.SERVICIO)
        fun gasto(n: Long) = ElaboradoEnVenta(sId, "Impresión", n, mapOf(tinta.id to Cantidad.enteras(n), papel.id to Cantidad.enteras(n)), ClaseArticulo.SERVICIO)

        val vId = ok(vender(t.id, det, elaborados = listOf(gasto(2))))
        assertTrue(ventas.obtener(vId)!!.esServicio)
        assertEquals(Cantidad.enteras(3), insumos.obtener(tinta.id)!!.cantidad)
        assertEquals(Cantidad.ZERO, insumos.obtener(papel.id)!!.cantidad)
        val consumos = turnos.movimientosDe(t.id).filter { it.tipo == TipoMovimiento.CONSUMO }
        assertEquals(2, consumos.size)
        assertTrue(consumos.all { it.ventaId == vId && it.nota == "Venta de Impresión ×2" })

        // Sin papel: ni venta ni gasto de tinta (todo o nada).
        val antes = ventas.deTurno(t.id).size
        val e = err(vender(t.id, det.copy(cantidad = 1), elaborados = listOf(gasto(1))))
        assertEquals(AppError.StockInsuficiente(listOf("Papel")), e)
        assertEquals(antes, ventas.deTurno(t.id).size)
        assertEquals(Cantidad.enteras(3), insumos.obtener(tinta.id)!!.cantidad)

        // Eliminado: sale de la lista, suelta sus insumos y ya no se vende.
        ok(servicios.eliminar(sId))
        assertTrue(servicios.observarTodos().first().none { it.id == sId })
        assertTrue(servicios.obtener(sId)!!.eliminado)
        assertTrue(servicios.serviciosQueUsan(tinta.id).isEmpty())
        ok(insumos.eliminar(tinta.id))
        assertEquals(AppError.NoEncontrado, err(vender(t.id, det.copy(cantidad = 1), elaborados = listOf(ElaboradoEnVenta(sId, "Impresión", 1, emptyMap(), ClaseArticulo.SERVICIO)))))
        assertEquals(AppError.NoEncontrado, err(servicios.actualizar(servicios.obtener(sId)!!, emptyList())))
        assertEquals(AppError.NoEncontrado, err(servicios.eliminar(424242)))
    }

    @Test fun editarServicioReemplazaSusInsumosYConservaLaFechaDeAlta() = runBlocking {
        val a = nuevoInsumo("Champú", 3)
        val b = nuevoInsumo("Toalla", 3)
        val sId = ok(servicios.crear(Servicio(nombre = "Lavado", tipo = "Peluquería", importe = Cup.ofPesos(200), creadoEn = ahora),
            listOf(RecetaLinea(a.id, Cantidad(250)))))
        val creado = servicios.obtener(sId)!!.creadoEn
        ahora = ahora.plusSeconds(3_600)
        ok(servicios.actualizar(servicios.obtener(sId)!!.copy(importe = Cup.ofPesos(250), creadoEn = ahora, actualizadoEn = ahora),
            listOf(RecetaLinea(b.id, Cantidad.enteras(1)))))
        val s = servicios.obtener(sId)!!
        assertEquals(creado, s.creadoEn)
        assertEquals(Cup.ofPesos(250), s.importe)
        assertEquals(listOf(RecetaLinea(b.id, Cantidad.enteras(1))), servicios.insumos(sId))
        assertEquals(mapOf(sId to listOf(RecetaLinea(b.id, Cantidad.enteras(1)))), servicios.observarInsumos().first())
        ok(insumos.eliminar(a.id)) // ya no lo usa
    }

    // ---------------------------------------------------------------- Ventas

    @Test fun insumoVendidoSueltoDescuentaUnidadesEnterasYEsTodoONada() = runBlocking {
        val azucar = nuevoInsumo("Azúcar", 3, precioVenta = Cup.ofPesos(120), unidad = UnidadMedida.KILOGRAMO)
        val p = nuevoProducto("Refresco", 5)
        val t = ok(turnos.abrir(ahora, "Ana"))
        val suelto = DetalleVenta(productoId = azucar.id, nombre = "Azúcar", categoria = "Insumos", cantidad = 2,
            precioBase = Cup.ofPesos(120), precioUnitario = Cup.ofPesos(120), costoUnitario = Cup.ofPesos(10), clase = ClaseArticulo.INSUMO)

        ok(vender(t.id, suelto, linea(p, 1)))
        assertEquals(Cantidad.enteras(1), insumos.obtener(azucar.id)!!.cantidad)
        val mov = turnos.movimientosDe(t.id).single { it.entidad == TipoEntidad.INSUMO }
        assertEquals(TipoMovimiento.VENTA, mov.tipo)
        assertEquals(-2_000L, mov.delta)

        // Falta azúcar: tampoco se descuenta el refresco.
        assertEquals(AppError.StockInsuficiente(listOf("Azúcar")), err(vender(t.id, suelto, linea(p, 1))))
        assertEquals(4L, productos.obtener(p.id)!!.cantidad)
        assertEquals(1, ventas.deTurno(t.id).size)
    }

    @Test fun lineasRepetidasDelMismoProductoSeCompruebanJuntas() = runBlocking {
        val p = nuevoProducto("Refresco", 5)
        val t = ok(turnos.abrir(ahora, "Ana"))
        assertEquals(AppError.StockInsuficiente(listOf("Refresco")), err(vender(t.id, linea(p, 3), linea(p, 3))))
        assertEquals(5L, productos.obtener(p.id)!!.cantidad)
        ok(vender(t.id, linea(p, 2), linea(p, 3)))
        assertEquals(0L, productos.obtener(p.id)!!.cantidad)
        assertTrue(err(vender(t.id)) is AppError.Validacion) // sin líneas
        ok(productos.eliminar(p.id))
        assertEquals(AppError.NoEncontrado, err(vender(t.id, linea(p, 1))))
    }

    @Test fun vendedoresYVentasLlevanElNombreDeQuienAbrioElTurno() = runBlocking {
        val p = nuevoProducto("Refresco", 10)
        val t1 = ok(turnos.abrir(ahora, "Luis"))
        val v1 = ok(vender(t1.id, linea(p, 1)))
        ok(turnos.cerrar(ahora.plusSeconds(60), "Luis"))
        ahora = ahora.plusSeconds(120)
        val t2 = ok(turnos.abrir(ahora, "Ana"))
        ok(vender(t2.id, linea(p, 1)))
        ok(turnos.cerrar(ahora.plusSeconds(60), "Ana"))
        ok(turnos.abrir(ahora.plusSeconds(120), "Luis"))

        assertEquals(listOf("Ana", "Luis"), registros.vendedores().first())
        assertEquals("Luis", ventas.obtener(v1)!!.vendedor)
        assertEquals(listOf("Ana", "Luis"), registros.ventas(FiltroRegistro()).first().map { it.vendedor }) // más recientes primero
        assertEquals(2, turnos.observarHistorial().first().count { it.cerradoEn != null })
        assertNull(ventas.obtener(9_999))
        assertFalse(registros.ventas(FiltroRegistro(desde = ahora.plusSeconds(10_000))).first().isNotEmpty())
    }
}
