package cu.shalev.data

import cu.shalev.domain.model.TipoEntidad
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.dao.registrar
import cu.shalev.data.repository.ClienteFijoRepositoryImpl
import cu.shalev.data.repository.InsumoRepositoryImpl
import cu.shalev.data.repository.PreciosRepositoryImpl
import cu.shalev.data.repository.ProductoRepositoryImpl
import cu.shalev.data.repository.RegistroRepositoryImpl
import cu.shalev.data.repository.TurnoRepositoryImpl
import cu.shalev.data.repository.VentaRepositoryImpl
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.PreajustePrecios
import cu.shalev.domain.model.ElaboradoEnVenta
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.FiltroRegistro
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prompt 15 — repositorios de :data contra Room real EN MEMORIA (sin SQLCipher, sin archivos, sin red).
 * Complementa TurnoRepositoryInstrumentedTest (cierre de turno) con inventario, recetas, ventas en
 * Efectivo/Transferencia, producción al vuelo, atomicidad, Registros y Precios.
 */
@RunWith(AndroidJUnit4::class)
class RepositoriosRoomTest {

    private var ahora = Instant.parse("2026-09-30T12:00:00Z")
    private val clock = Clock { ahora }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ShalevDatabase::class.java).build()
    private val productos = ProductoRepositoryImpl(db, clock)
    private val insumos = InsumoRepositoryImpl(db, clock)
    private val turnos = TurnoRepositoryImpl(db)
    private val ventas = VentaRepositoryImpl(db)
    private val registros = RegistroRepositoryImpl(db)
    private val precios = PreciosRepositoryImpl(db)
    private val clientes = ClienteFijoRepositoryImpl(db, clock)

    @After fun cerrarBd() = db.close()

    private fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Ok)?.value ?: error("esperaba Ok y fue $r")
    private fun err(r: AppResult<*>): AppError = (r as? AppResult.Err)?.error ?: error("esperaba Err y fue $r")

    private fun producto(nombre: String, cantidad: Long, categoria: String = "Bebidas", venta: Long = 100, costo: Long = 60) =
        Producto(categoria = categoria, nombre = nombre, precioCosto = Cup.ofPesos(costo), precioVenta = Cup.ofPesos(venta),
            cantidad = cantidad, creadoEn = ahora)

    private fun detalle(p: Producto, cantidad: Long) = DetalleVenta(productoId = p.id, nombre = p.nombre, categoria = p.categoria,
        cantidad = cantidad, precioBase = p.precioVenta, precioUnitario = p.precioVenta, costoUnitario = p.precioCosto)

    private val cliente = DatosCliente("Ana Díaz Rey", "90020212345", "+5353000000")

    // ---------------- Inventario ----------------

    @Test fun productoSeCreaAjustaYEliminaSinPerderHistorial() = runBlocking {
        val id = ok(productos.crear(producto("Refresco", 10), null))
        assertEquals(7L, ok(productos.ajustarStock(id, -3, "rotura")))
        assertTrue(err(productos.ajustarStock(id, -8, null)) is AppError.StockInsuficiente) // nunca negativo
        assertEquals(7L, productos.obtener(id)!!.cantidad)

        ok(productos.eliminar(id))
        assertTrue(productos.observarTodos().first().none { it.id == id })   // fuera de la lista
        assertTrue(productos.obtener(id)!!.eliminado)                         // pero sigue para el historial
        assertEquals(AppError.NoEncontrado, err(productos.ajustarStock(id, 1, null)))
        val tipos = registros.movimientos(FiltroRegistro()).first().filter { it.entidadId == id }.map { it.tipo }.toSet()
        assertTrue(TipoMovimiento.ALTA in tipos && TipoMovimiento.AJUSTE in tipos)
    }

    @Test fun insumoUsadoEnRecetaNoSePuedeEliminar() = runBlocking {
        val harina = ok(insumos.crear(Insumo(nombre = "Harina", precio = Cup.ofPesos(10), cantidad = Cantidad.enteras(5), creadoEn = ahora)))
        val pan = ok(productos.crear(producto("Pan", 0, Categorias.ELABORADO), null))
        ok(productos.actualizar(productos.obtener(pan)!!, Receta(pan, listOf(RecetaLinea(harina, Cantidad.enteras(1))))))
        assertEquals(listOf(pan), productos.productosQueUsan(harina).map { it.id })
        assertTrue(err(insumos.eliminar(harina)) is AppError.EnUso)
        assertEquals(Cantidad.enteras(1), productos.receta(pan)!!.lineas.single().cantidad)
    }

    // ---------------- Ventas ----------------

    @Test fun ventaEnEfectivoDescuentaStockYQuedaEnElTurno() = runBlocking {
        val p = productos.obtener(ok(productos.crear(producto("Refresco", 10), null)))!!
        val t = ok(turnos.abrir(ahora, "Ana"))
        val id = ok(ventas.registrar(Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(detalle(p, 4)))))
        assertEquals(6L, productos.obtener(p.id)!!.cantidad)
        val v = ventas.obtener(id)!!
        assertEquals(Cup.ofPesos(400), v.total)
        assertNull(v.transaccion)
        assertEquals(listOf(id), ventas.deTurno(t.id).map { it.id })
        assertTrue(turnos.movimientosDe(t.id).any { it.tipo == TipoMovimiento.VENTA && it.ventaId == id && it.delta == -4L })
    }

    /**
     * 0.31.0 (tarea 4): el importe de lo vendido no depende del precio de hoy. `actualizarPrecios` solo escribe en
     * `producto`: ni el detalle, ni la venta, ni el histórico, ni el resumen del turno cambian de importe.
     */
    @Test fun cambiarElPrecioNoAlteraElImporteDeLoVendido() = runBlocking {
        val p = productos.obtener(ok(productos.crear(producto("Refresco", 10, venta = 30), null)))!!
        val t = ok(turnos.abrir(ahora, "Ana"))
        val id = ok(ventas.registrar(Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(detalle(p, 4)))))
        assertEquals(Cup.ofPesos(120), ventas.obtener(id)!!.total)

        ok(productos.actualizarPrecios(mapOf(p.id to Cup.ofPesos(99))))
        assertEquals(Cup.ofPesos(99), productos.obtener(p.id)!!.precioVenta) // el catálogo sí cambia
        assertEquals(Cup.ofPesos(30), ventas.obtener(id)!!.detalles.single().precioUnitario)
        assertEquals(Cup.ofPesos(120), ventas.obtener(id)!!.total)
        assertEquals(Cup.ofPesos(120), registros.ventas(FiltroRegistro()).first().single().total) // ni al releer el histórico
        ok(turnos.cerrar(ahora, "Ana"))
        assertEquals(Cup.ofPesos(120), turnos.obtener(t.id)!!.resumen!!.total)
    }

    @Test fun ventaPorTransferenciaGuardaLaTransaccionConElCliente() = runBlocking {
        val p = productos.obtener(ok(productos.crear(producto("Refresco", 10), null)))!!
        val t = ok(turnos.abrir(ahora, "Ana"))
        val tr = Transaccion(fecha = ahora, importe = Cup.ofPesos(200), numero = "BR601ADLM8997", cliente = cliente, tarjetaCobro = "9204 **** 1234")
        val id = ok(ventas.registrar(Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.TRANSFERENCIA, detalles = listOf(detalle(p, 2)), transaccion = tr)))
        val guardada = ventas.obtener(id)!!.transaccion!!
        assertEquals(id, guardada.ventaId)
        assertEquals("BR601ADLM8997", guardada.numero)
        assertEquals(cliente, guardada.cliente)
        assertEquals(listOf("BR601ADLM8997"), registros.transacciones(FiltroRegistro(texto = "90020212345")).first().map { it.numero })
    }

    @Test fun ventaConStockInsuficienteOTurnoCerradoEsTodoONada() = runBlocking {
        val a = productos.obtener(ok(productos.crear(producto("Refresco", 10), null)))!!
        val b = productos.obtener(ok(productos.crear(producto("Galletas", 1), null)))!!
        val t = ok(turnos.abrir(ahora, "Ana"))
        val venta = Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(detalle(a, 2), detalle(b, 3)))
        assertEquals(AppError.StockInsuficiente(listOf("Galletas")), err(ventas.registrar(venta)))
        assertEquals(10L, productos.obtener(a.id)!!.cantidad) // la primera línea tampoco se descontó
        assertTrue(ventas.deTurno(t.id).isEmpty())

        ok(turnos.cerrar(ahora, "Ana"))
        assertEquals(AppError.TurnoCerrado, err(ventas.registrar(venta.copy(detalles = listOf(detalle(a, 1))))))
        assertEquals(10L, productos.obtener(a.id)!!.cantidad)
    }

    /** P26: el Elaborado se vende directamente: CONSUMO de sus insumos ligado a la venta, sin tocar su existencia. */
    @Test fun ventaDeElaboradoConsumeInsumosEnLaMismaTransaccionYRollbackSiFalta() = runBlocking {
        val harina = ok(insumos.crear(Insumo(nombre = "Harina", precio = Cup.ofPesos(10), cantidad = Cantidad.enteras(2), creadoEn = ahora)))
        val refresco = productos.obtener(ok(productos.crear(producto("Refresco", 5), null)))!!
        val pan = productos.obtener(ok(productos.crear(producto("Pan", 0, Categorias.ELABORADO, costo = 10), null)))!!
        val t = ok(turnos.abrir(ahora, "Ana"))
        val e = ElaboradoEnVenta(pan.id, pan.nombre, 2, mapOf(harina to Cantidad.enteras(2)))
        val id = ok(ventas.registrar(
            Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(detalle(pan, 2), detalle(refresco, 1))), listOf(e),
        ))
        assertEquals(0L, productos.obtener(pan.id)!!.cantidad)          // sin existencias propias: no se tocó
        assertEquals(4L, productos.obtener(refresco.id)!!.cantidad)
        assertEquals(Cantidad(0), insumos.obtener(harina)!!.cantidad)
        val movs = turnos.movimientosDe(t.id).filter { it.ventaId == id }
        assertEquals(setOf(TipoMovimiento.CONSUMO, TipoMovimiento.VENTA), movs.map { it.tipo }.toSet())
        assertTrue(movs.none { it.entidadId == pan.id && it.entidad == TipoEntidad.PRODUCTO })
        assertEquals("Venta de Pan ×2", movs.single { it.tipo == TipoMovimiento.CONSUMO }.nota)

        // Ya no queda harina: la siguiente venta falla y no deja rastro (tampoco el refresco).
        val otra = ventas.registrar(
            Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(detalle(pan, 1), detalle(refresco, 1))),
            listOf(e.copy(unidades = 1, consumo = mapOf(harina to Cantidad.enteras(1)))),
        )
        assertEquals(AppError.StockInsuficiente(listOf("Harina")), err(otra))
        assertEquals(1, ventas.deTurno(t.id).size)
        assertEquals(4L, productos.obtener(refresco.id)!!.cantidad)
    }

    // ---------------- Registros ----------------

    @Test fun registrosFiltranPorFechaImporteYTexto() = runBlocking {
        val p = productos.obtener(ok(productos.crear(producto("Refresco", 50), null)))!!
        val g = productos.obtener(ok(productos.crear(producto("Galletas", 50, venta = 1000), null)))!!
        val t = ok(turnos.abrir(ahora, "Ana"))
        val ayer = ahora
        ok(ventas.registrar(Venta(turnoId = t.id, fecha = ayer, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(detalle(p, 1)))))   // 100
        ahora = ahora.plus(Duration.ofDays(1))
        ok(ventas.registrar(Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(detalle(g, 2)))))   // 2000

        assertEquals(2, registros.ventas(FiltroRegistro()).first().size)
        assertEquals(1, registros.ventas(FiltroRegistro(desde = ayer.plus(Duration.ofHours(12)))).first().size)
        assertEquals(Cup.ofPesos(100), registros.ventas(FiltroRegistro(importeMax = Cup.ofPesos(500))).first().single().total)
        assertEquals(Cup.ofPesos(2000), registros.ventas(FiltroRegistro(importeMin = Cup.ofPesos(500))).first().single().total)
        assertEquals("Galletas", registros.ventas(FiltroRegistro(texto = "gall")).first().single().detalles.single().nombre)
        // Los comodines de LIKE escritos por el usuario no amplían la búsqueda.
        assertTrue(registros.ventas(FiltroRegistro(texto = "%%x%")).first().isEmpty())
        // Más reciente primero.
        assertEquals(Cup.ofPesos(2000), registros.ventas(FiltroRegistro()).first().first().total)
    }

    // ---------------- Precios ----------------

    @Test fun preajustesSeGuardanActivanYEliminan() = runBlocking {
        val p = ok(productos.crear(producto("Refresco", 5), null))
        val id = ok(precios.guardarPreajuste(PreajustePrecios(nombre = "Mayorista", puntosBasicos = -1000, productoIds = setOf(p), metodoPago = MetodoPago.EFECTIVO)))
        ok(precios.guardarPreajuste(PreajustePrecios(nombre = "Pausado", puntosBasicos = 500, productoIds = setOf(p), activo = false)))
        assertEquals(listOf("Mayorista"), precios.preajustesActivos().map { it.nombre })
        assertEquals(setOf(p), precios.preajustesActivos().single().productoIds)
        ok(precios.eliminarPreajuste(id))
        assertTrue(precios.preajustesActivos().isEmpty())

        ok(productos.actualizarPrecios(mapOf(p to Cup.ofPesos(120))))
        assertEquals(Cup.ofPesos(120), productos.obtener(p)!!.precioVenta)
        assertEquals(AppError.NoEncontrado, err(productos.actualizarPrecios(mapOf(p to Cup.ofPesos(1), 999L to Cup.ofPesos(1)))))
        assertNotNull(productos.obtener(p))
        assertEquals(Cup.ofPesos(120), productos.obtener(p)!!.precioVenta) // atómico
    }

    // ---------------- Clientes fijos (0.31.0) ----------------

    /**
     * 0.31.0: corregir desde la ficha el nombre, el teléfono y el carné de un cliente fijo (se guarda como en la
     * venta: teléfono en E.164), sin aceptar el carné de otro cliente y dejando sus compras anteriores con los datos
     * nuevos.
     */
    @Test fun clienteFijoSeCorrigeYNoAceptaUnCarneDeOtroCliente() = runBlocking {
        val dao = db.clienteFijoDao()
        dao.registrar("Maria Perez", "85010112345", "51234567", ahora.toEpochMilli())
        dao.registrar("Jose Gomez", "90020212345", "+5359876543", ahora.toEpochMilli())
        // Una compra suya (venta por transferencia) para comprobar que sigue al carné corregido.
        val p = productos.obtener(ok(productos.crear(producto("Refresco", 10), null)))!!
        val t = ok(turnos.abrir(ahora, "Ana"))
        val tr = Transaccion(fecha = ahora, importe = Cup.ofPesos(100), numero = "BR601ADLM8998",
            cliente = DatosCliente("Maria Perez", "85010112345", "51234567"))
        ok(ventas.registrar(Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.TRANSFERENCIA, detalles = listOf(detalle(p, 1)), transaccion = tr)))
        assertEquals(1, clientes.observar().first().first { it.ci == "85010112345" }.compras)

        assertTrue(clientes.actualizar("85010112345", "María Pérez López", "85010112345", "51234568"))
        val m = clientes.observar().first().first { it.ci == "85010112345" }
        assertEquals("María Pérez López", m.nombreApellidos)
        assertEquals("+5351234568", m.telefono)
        // La compra anterior queda también con los datos corregidos.
        assertEquals("María Pérez López", db.ventaDao().todasTransacciones().single().clienteNombre)
        assertEquals("+5351234568", db.ventaDao().todasTransacciones().single().clienteTelefono)

        // El carné de otro cliente no se acepta: no cambia nada.
        assertFalse(clientes.actualizar("85010112345", "María Pérez", "90020212345", "+5351234567"))
        assertEquals("María Pérez López", clientes.observar().first().first { it.ci == "85010112345" }.nombreApellidos)

        // Un carné libre sí: el cliente pasa a tenerlo, conserva sus datos y sus compras anteriores lo siguen.
        assertTrue(clientes.actualizar("85010112345", "María Pérez López", "85010199999", "+5351234568"))
        val nuevo = clientes.observar().first().first { it.ci == "85010199999" }
        assertEquals("María Pérez López", nuevo.nombreApellidos)
        assertEquals(1, nuevo.compras)
        val trFinal = db.ventaDao().todasTransacciones().single()
        assertEquals("85010199999", trFinal.clienteCi)
        assertEquals("María Pérez López", trFinal.clienteNombre)
        assertEquals("+5351234568", trFinal.clienteTelefono)
        assertEquals(2, clientes.observar().first().size)
    }
}
