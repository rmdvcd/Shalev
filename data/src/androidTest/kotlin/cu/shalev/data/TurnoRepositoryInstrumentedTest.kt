package cu.shalev.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.dto.aJson
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.EmpleadoEntity
import cu.shalev.data.db.entity.TurnoEntity
import cu.shalev.data.repository.InsumoRepositoryImpl
import cu.shalev.data.repository.PreciosRepositoryImpl
import cu.shalev.data.repository.ServicioRepositoryImpl
import cu.shalev.data.sync.AjustarStockProducto
import cu.shalev.data.sync.EjecutorComandos
import cu.shalev.data.repository.ProductoRepositoryImpl
import cu.shalev.data.repository.TurnoRepositoryImpl
import cu.shalev.data.repository.VentaRepositoryImpl
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.Venta
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Turno de venta contra Room real (en memoria; en la app la BD es SQLCipher). Comprueba que el cierre
 * congela ventas, métodos de pago, movimientos de productos e insumos y usuario, y que no se vende fuera de turno.
 */
@RunWith(AndroidJUnit4::class)
class TurnoRepositoryInstrumentedTest {

    private var ahora = Instant.parse("2026-09-30T12:00:00Z")
    private val clock = Clock { ahora }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ShalevDatabase::class.java).build()
    private val turnos = TurnoRepositoryImpl(db)
    private val ventas = VentaRepositoryImpl(db)
    private val productos = ProductoRepositoryImpl(db, clock)
    private val insumos = InsumoRepositoryImpl(db, clock)

    @After fun cerrarBd() = db.close()

    private fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Ok)?.value ?: error("esperaba Ok y fue $r")

    private fun venta(turnoId: Long, productoId: Long, cantidad: Long, metodo: MetodoPago) = Venta(
        turnoId = turnoId, fecha = ahora, metodoPago = metodo,
        detalles = listOf(DetalleVenta(productoId = productoId, nombre = "Refresco", categoria = "Bebidas", cantidad = cantidad,
            precioBase = Cup.ofPesos(100), precioUnitario = Cup.ofPesos(100), costoUnitario = Cup.ofPesos(60))),
    )

    @Test fun conciliarAlCerrarAjustaProductosEInsumosYRegistraQuienLoConfirma() = runBlocking {
        val p = ok(productos.crear(Producto(categoria = "Bebidas", nombre = "Refresco", precioCosto = Cup(60), precioVenta = Cup(100), cantidad = 10, creadoEn = ahora), null))
        val i = ok(insumos.crear(Insumo(nombre = "Harina", precio = Cup(10), cantidad = Cantidad(2500), creadoEn = ahora)))
        val t = ok(turnos.abrir(ahora, "Ana", Cup.ZERO))
        // 0.32.0: el conteo es PARCIAL: solo entran los artículos que cambiaron durante el turno (los que se movieron).
        ok(productos.ajustarStock(p, -2, "Venta")) // producto 10 -> 8
        ok(insumos.ajustarStock(i, Cantidad(-500), "Consumo")) // insumo 2500 -> 2000
        val snapshot = ok(turnos.prepararConteo())
        assertEquals(setOf(TipoEntidad.PRODUCTO, TipoEntidad.INSUMO), snapshot.second.map { it.entidad }.toSet())
        val conteo = cu.shalev.domain.model.ConteoCierre(mapOf(5000 to 1L, 20000 to 1L), inventario = snapshot.second.map {
            it.copy(contado = if (it.entidad == TipoEntidad.PRODUCTO) 6L else 2250L)
        }, motivo = "Conteo físico confirmado")
        ok(turnos.guardarConteo(t.id, conteo))
        assertEquals(8L, productos.obtener(p)!!.cantidad) // declarar NO ajusta
        assertEquals(2000L, insumos.obtener(i)!!.cantidad.milesimas)
        val cerrado = ok(turnos.cerrar(ahora.plusSeconds(60), "Dueño", conteo.efectivo))
        assertEquals(6L, productos.obtener(p)!!.cantidad) // la diferencia se aplica sobre el valor actual
        assertEquals(2250L, insumos.obtener(i)!!.cantidad.milesimas)
        assertEquals("Dueño", cerrado.conteoCierre!!.aprobadoPor)
        // Los movimientos de conciliación son los que llevan la nota del conteo (los del turno, los del movimiento de stock).
        val conciliacion = turnos.movimientosDe(t.id).filter { it.nota.orEmpty().contains("Conteo físico confirmado") }
        assertEquals(2, conciliacion.size)
        assertTrue(conciliacion.all { it.hechoPor == "Dueño" })
        assertTrue(turnos.cerrar(ahora, "Dueño", conteo.efectivo) is AppResult.Err)
        assertEquals(6L, productos.obtener(p)!!.cantidad) // no se repite
    }

    @Test fun stockCambiadoDespuesDeContarImpideCerrarYNoHaceAjustesParciales() = runBlocking {
        val p = ok(productos.crear(Producto(categoria = "Bebidas", nombre = "Refresco", precioCosto = Cup(60), precioVenta = Cup(100), cantidad = 10, creadoEn = ahora), null))
        val i = ok(insumos.crear(Insumo(nombre = "Harina", precio = Cup(10), cantidad = Cantidad(2500), creadoEn = ahora)))
        val t = ok(turnos.abrir(ahora, "Ana", Cup.ZERO))
        val snapshot = ok(turnos.prepararConteo())
        val conteo = cu.shalev.domain.model.ConteoCierre(inventario = snapshot.second.map { it.copy(contado = 0) }, motivo = "Merma")
        ok(turnos.guardarConteo(t.id, conteo))
        ahora = ahora.plusSeconds(1)
        ok(insumos.ajustarStock(i, Cantidad(-500), "Consumo posterior"))
        assertTrue(turnos.cerrar(ahora, "Ana", Cup.ZERO) is AppResult.Err)
        assertEquals(10L, productos.obtener(p)!!.cantidad)
        assertEquals(2000L, insumos.obtener(i)!!.cantidad.milesimas)
        assertNull(turnos.activo()!!.cerradoEn)
        assertNull(turnos.activo()!!.conteoCierre!!.aprobadoEn)
        assertEquals(t.id, turnos.activo()!!.id)
    }

    @Test fun elCierreLocalDeUnaSecundariaNoAplicaLosAjustesDelConteo() = runBlocking {
        val p = ok(productos.crear(Producto(categoria = "Bebidas", nombre = "Refresco", precioCosto = Cup(60), precioVenta = Cup(100), cantidad = 10, creadoEn = ahora), null))
        val t = ok(turnos.abrir(ahora, "Empleado", Cup.ZERO))
        val snapshot = ok(turnos.prepararConteo())
        val conteo = cu.shalev.domain.model.ConteoCierre(inventario = snapshot.second.map { it.copy(contado = 8) }, motivo = "Merma")
        ok(turnos.guardarConteo(t.id, conteo))
        assertTrue(turnos.cerrarSinAjustar(ahora, "Encargado", Cup.ZERO) is AppResult.Err)
        // Simula la autorización autenticada que ya aplicó la principal; no debe volver a ajustar aquí.
        val declarado = db.turnoDao().activo()!!
        db.syncDao().actualizarTurno(declarado.copy(conteoCierreJson = conteo.copy(aprobadoPor = "Encargado", aprobadoEn = ahora.toEpochMilli()).aJson()))
        ok(turnos.cerrarSinAjustar(ahora, "Encargado", Cup.ZERO))
        assertEquals(10L, productos.obtener(p)!!.cantidad)
        assertTrue(turnos.movimientosDe(t.id).isEmpty())
    }

    @Test fun cierreCongelaElRegistroCompleto() = runBlocking {
        val p = ok(productos.crear(Producto(categoria = "Bebidas", nombre = "Refresco", precioCosto = Cup.ofPesos(60), precioVenta = Cup.ofPesos(100), cantidad = 20, creadoEn = ahora), null))
        val i = ok(insumos.crear(Insumo(nombre = "Harina", precio = Cup.ofPesos(10), cantidad = Cantidad.enteras(5), creadoEn = ahora)))

        val t = ok(turnos.abrir(ahora, "Ana Pérez"))
        assertEquals(AppError.TurnoYaAbierto, (turnos.abrir(ahora, "Ana Pérez") as AppResult.Err).error)
        ahora = ahora.plusSeconds(600)
        ok(ventas.registrar(venta(t.id, p, 2, MetodoPago.EFECTIVO)))
        ok(ventas.registrar(venta(t.id, p, 1, MetodoPago.TRANSFERENCIA)))
        ok(insumos.ajustarStock(i, Cantidad(-1_500), "merma"))
        ahora = ahora.plusSeconds(3_000)

        val c = ok(turnos.cerrar(ahora, "Luis"))
        val r = c.resumen!!
        assertEquals("Ana Pérez", c.abiertoPor)
        assertEquals("Luis", c.cerradoPor)
        assertEquals(ahora, c.cerradoEn)
        assertEquals(2, r.numVentas)
        assertEquals(3L, r.unidades)
        assertEquals(Cup.ofPesos(300), r.total)
        assertEquals(Cup.ofPesos(200), r.totalEfectivo)
        assertEquals(Cup.ofPesos(100), r.totalTransferencia)
        assertEquals(1, r.ventasEfectivo)
        assertEquals(1, r.ventasTransferencia)
        assertEquals(Cup.ofPesos(180), r.costo)
        assertEquals(2, r.movimientosProducto) // dos salidas por venta (el ALTA fue antes de abrir)
        assertEquals(1, r.movimientosInsumo)
        assertEquals(3, r.numMovimientos)

        val movs = turnos.movimientosDe(t.id)
        assertEquals(listOf(TipoEntidad.PRODUCTO, TipoEntidad.PRODUCTO, TipoEntidad.INSUMO), movs.map { it.entidad })
        assertEquals(2, ventas.deTurno(t.id).size)
        assertEquals(c, turnos.ultimoCerrado())
        assertNull(turnos.activo())
    }

    @Test fun noSeVendeFueraDeTurnoNiSeAsocianMovimientosAUnTurnoCerrado() = runBlocking {
        val p = ok(productos.crear(Producto(categoria = "Bebidas", nombre = "Refresco", precioCosto = Cup.ofPesos(60), precioVenta = Cup.ofPesos(100), cantidad = 5, creadoEn = ahora), null))
        val t = ok(turnos.abrir(ahora, "Ana"))
        ok(turnos.cerrar(ahora.plusSeconds(60), "Ana"))

        val r = ventas.registrar(venta(t.id, p, 1, MetodoPago.EFECTIVO))
        assertEquals(AppError.TurnoCerrado, (r as AppResult.Err).error)
        assertEquals(5L, productos.obtener(p)!!.cantidad) // el stock no se tocó

        ok(productos.ajustarStock(p, 3, "compra")) // fuera de turno: se permite, pero sin turno
        assertTrue(turnos.movimientosDe(t.id).isEmpty())
        assertEquals(AppError.TurnoCerrado, (turnos.cerrar(ahora, "Ana") as AppResult.Err).error)
    }

    /**
     * 0.19.3: la PRINCIPAL es un punto de venta completo. Con un turno de una secundaria abierto en su BD, abre el suyo,
     * vende, ajusta stock y cierra; los cambios que pide la secundaria van al turno de la secundaria, no al suyo.
     */
    @Test fun laPrincipalVendeConSuPropioTurnoAunqueHayaSecundariasEnTurno() = runBlocking {
        val p = ok(productos.crear(Producto(categoria = "Bebidas", nombre = "Refresco", precioCosto = Cup.ofPesos(60), precioVenta = Cup.ofPesos(100), cantidad = 20, creadoEn = ahora), null))
        val sync = db.syncDao()
        val luis = sync.insertarEmpleado(EmpleadoEntity(nombre = "Luis", permisos = "EDITAR_INVENTARIO", creadoEn = 0, vinculadoEn = 0,
            ultimaSincronizacion = null, clave = null, codigoToken = null, codigoVence = null, activo = true))
        val turnoLuis = sync.insertarTurno(TurnoEntity(abiertoEn = ahora.toEpochMilli(), cerradoEn = null, numVentas = null, unidades = null,
            totalCent = null, efectivoCent = null, transferenciaCent = null, costoCent = null, numMovimientos = null,
            abiertoPor = "Luis", uuid = "turno-luis", empleadoId = luis))

        assertNull(turnos.activo()) // el turno de Luis no es el de la principal
        val t = ok(turnos.abrir(ahora, "Ana"))
        ok(ventas.registrar(venta(t.id, p, 2, MetodoPago.EFECTIVO)))
        ok(productos.ajustarStock(p, 5, "compra"))

        val ejecutor = EjecutorComandos(db, productos, insumos, ServicioRepositoryImpl(db, clock), PreciosRepositoryImpl(db))
        val r = ejecutor.resultado(1, sync.empleado(luis)!!, AjustarStockProducto(p, -1, "rotura"))
        assertNull(r.error)

        val deLuis = turnos.movimientosDe(turnoLuis)
        assertEquals(1, deLuis.size)
        assertTrue(deLuis.single().nota!!.contains("Luis"))

        val c = ok(turnos.cerrar(ahora.plusSeconds(60), "Ana"))
        assertEquals(1, c.resumen!!.numVentas)
        assertEquals(2, c.resumen!!.numMovimientos) // la salida por la venta y la compra; no la rotura de Luis
        assertEquals(22L, productos.obtener(p)!!.cantidad) // 20 − 2 + 5 − 1
        assertNull(db.turnoDao().obtener(turnoLuis)!!.cerradoEn) // cerrar el suyo no toca el de Luis
    }

    /**
     * 0.31.0: corregir un ajuste del turno abierto (Registros → Movimientos) cuadra el stock y la existencia de los
     * movimientos posteriores de esa entidad.
     */
    @Test fun corregirUnAjusteCuadraElStockYLaExistenciaDeLosSiguientes() = runBlocking {
        val p = ok(productos.crear(Producto(categoria = "Bebidas", nombre = "Refresco", precioCosto = Cup.ofPesos(60), precioVenta = Cup.ofPesos(100), cantidad = 10, creadoEn = ahora), null))
        val t = ok(turnos.abrir(ahora, "Ana"))
        ok(productos.ajustarStock(p, 5, "Llegó el pedido")) // se escribió 5 y eran 50
        ok(productos.ajustarStock(p, -3, "Merma"))
        assertEquals(12L, productos.obtener(p)!!.cantidad)
        val ajuste = turnos.movimientosDe(t.id).first { it.nota == "Llegó el pedido" }
        val merma = turnos.movimientosDe(t.id).first { it.nota == "Merma" }
        assertEquals(15L, ajuste.existenciaResultante)
        assertEquals(12L, merma.existenciaResultante)

        ok(turnos.corregirMovimiento(ajuste.id, 50, "eran 50 unidades", ahora.toEpochMilli()))

        assertEquals(57L, productos.obtener(p)!!.cantidad) // 10 + 50 − 3
        val corregido = turnos.movimientosDe(t.id).first { it.id == ajuste.id }
        assertEquals(50L, corregido.delta)
        assertEquals(60L, corregido.existenciaResultante) // 10 + 50
        assertEquals("Llegó el pedido · Corregido: eran 50 unidades", corregido.nota)
        assertEquals("la merma sigue cuadrando", 57L, turnos.movimientosDe(t.id).first { it.id == merma.id }.existenciaResultante)
    }

    /** Solo los ajustes del turno abierto; el insumo se corrige en su unidad y nunca queda stock negativo. */
    @Test fun soloSeCorrigenAjustesDelTurnoAbiertoYSinDejarStockNegativo() = runBlocking {
        val p = ok(productos.crear(Producto(categoria = "Bebidas", nombre = "Refresco", precioCosto = Cup.ofPesos(60), precioVenta = Cup.ofPesos(100), cantidad = 10, creadoEn = ahora), null))
        val i = ok(insumos.crear(Insumo(nombre = "Harina", precio = Cup.ofPesos(10), cantidad = Cantidad.enteras(5), creadoEn = ahora)))
        val t = ok(turnos.abrir(ahora, "Ana"))
        ok(insumos.ajustarStock(i, Cantidad(500), "compra")) // 5.5 kg
        ok(ventas.registrar(venta(t.id, p, 1, MetodoPago.EFECTIVO)))
        val compra = turnos.movimientosDe(t.id).first { it.tipo == TipoMovimiento.AJUSTE }
        val salida = turnos.movimientosDe(t.id).first { it.tipo == TipoMovimiento.VENTA }

        ok(turnos.corregirMovimiento(compra.id, 1250, "eran 1.25 kg", ahora.toEpochMilli())) // 0.5 → 1.25 kg
        assertEquals(6250L, insumos.obtener(i)!!.cantidad.milesimas)

        val stockAntes = productos.obtener(p)!!.cantidad
        assertEquals(
            AppError.Validacion("movimiento", AppError.Regla.NO_PERMITIDO),
            (turnos.corregirMovimiento(salida.id, 9, "no es un ajuste", ahora.toEpochMilli()) as AppResult.Err).error,
        )
        assertEquals("una salida por venta se corrige desde su venta", stockAntes, productos.obtener(p)!!.cantidad)

        assertEquals(
            AppError.StockInsuficiente(listOf("Harina")),
            (turnos.corregirMovimiento(compra.id, -9000, "merma imposible", ahora.toEpochMilli()) as AppResult.Err).error,
        )
        assertEquals(6250L, insumos.obtener(i)!!.cantidad.milesimas) // nada a medias
        assertEquals(1250L, turnos.movimientosDe(t.id).first { it.id == compra.id }.delta)

        ok(turnos.cerrar(ahora.plusSeconds(60), "Ana"))
        assertEquals(AppError.TurnoCerrado, (turnos.corregirMovimiento(compra.id, 500, "y merma", ahora.toEpochMilli()) as AppResult.Err).error)
        assertEquals(6250L, insumos.obtener(i)!!.cantidad.milesimas)

        // Con un turno nuevo abierto, el ajuste del turno ya cerrado sigue siendo intocable.
        ok(turnos.abrir(ahora.plusSeconds(120), "Ana"))
        assertEquals(
            AppError.Validacion("movimiento", AppError.Regla.NO_PERMITIDO),
            (turnos.corregirMovimiento(compra.id, 500, "y merma", ahora.toEpochMilli()) as AppResult.Err).error,
        )
        assertEquals(6250L, insumos.obtener(i)!!.cantidad.milesimas)
    }
}
