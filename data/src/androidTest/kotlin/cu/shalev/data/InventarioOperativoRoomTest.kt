package cu.shalev.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.local.SecureDataStore
import cu.shalev.data.repository.*
import cu.shalev.data.security.KeystoreAead
import cu.shalev.data.sync.ConfigSync
import cu.shalev.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InventarioOperativoRoomTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, ShalevDatabase::class.java).build()
    private val ahora = Instant.parse("2026-11-13T12:00:00Z")
    private val clock = Clock { ahora }
    private val config = ConfigSync(SecureDataStore(context, KeystoreAead(context)))
    private val productos = ProductoRepositoryImpl(db, clock)
    private val insumos = InsumoRepositoryImpl(db, clock)
    private val turnos = TurnoRepositoryImpl(db)
    private val ventas = VentaRepositoryImpl(db)
    private val gestion = GestionInventarioRepositoryImpl(db, productos, insumos, config, clock)
    private val locales = LocalRepositoryImpl(db)
    private fun <T> ok(r: AppResult<T>) = (r as? AppResult.Ok)?.value ?: error("Se esperaba Ok: $r")
    @Before fun antes() = runBlocking { config.hacerPrincipal() }
    @After fun despues() { db.close() }

    private suspend fun producto(cantidad: Long = 0, costo: Cup = Cup(100)) = ok(productos.crear(Producto(
        categoria = "Bebidas", nombre = "Refresco", cantidad = cantidad, precioCosto = costo, precioVenta = Cup(1_000), creadoEn = ahora,
    ), null))
    private fun recepcion(vararg lineas: LineaRecepcion) = NuevaRecepcion(UUID.randomUUID().toString(), "Proveedor Uno", "F-123", "Compra del día", lineas.toList())
    private fun linea(id: Long, cantidad: Long, lote: String, fecha: LocalDate? = null) = LineaRecepcion(TipoEntidad.PRODUCTO, id, Cantidad.enteras(cantidad), Cup(250), lote, fecha)
    private suspend fun vender(id: Long, cantidad: Long): AppResult<Long> {
        val turno = turnos.activo() ?: ok(turnos.abrir(ahora, "Ana"))
        val p = productos.obtener(id)!!
        return ventas.registrar(Venta(turnoId = turno.id, fecha = ahora, metodoPago = MetodoPago.EFECTIVO,
            detalles = listOf(DetalleVenta(productoId = id, nombre = p.nombre, categoria = p.categoria, cantidad = cantidad,
                precioBase = p.precioVenta, precioUnitario = p.precioVenta, costoUnitario = p.precioCosto))))
    }

    @Test fun recepcionActualizaExistenciaCostoPromedioYCongelaDatosDeOrigen() = runBlocking {
        val p = producto(2, Cup(100))
        ok(gestion.recibir(recepcion(linea(p, 2, "B-01")), "Ana"))
        assertEquals(4L, productos.obtener(p)!!.cantidad)
        assertEquals(Cup(175), productos.obtener(p)!!.precioCosto)
        val r = gestion.recepciones().first().single()
        assertEquals("Proveedor Uno", r.proveedor)
        assertEquals("F-123", r.comprobante)
        assertEquals(Cup(500), r.total)
        assertEquals("Ana", r.autor)
        assertEquals(Cantidad.enteras(2), gestion.lotes().first().single().disponible)
        assertEquals("COMPRA", db.movimientoDao().ultimoDe("PRODUCTO", p)!!.motivo)
    }

    @Test fun reintentarLaMismaRecepcionNoSumaDosVecesYReutilizarIdConOtrosDatosSeRechaza() = runBlocking {
        val p = producto()
        val r = recepcion(linea(p, 2, "L-1"))
        assertEquals(ok(gestion.recibir(r, "Ana")), ok(gestion.recibir(r, "Ana")))
        assertEquals(2L, productos.obtener(p)!!.cantidad)
        assertEquals(1, db.gestionDao().recepciones().size)
        assertTrue(gestion.recibir(r.copy(comprobante = "Otro"), "Ana") is AppResult.Err)
        assertEquals(2L, productos.obtener(p)!!.cantidad)
    }

    @Test fun unaLineaDeOtroLocalAbortaTodaLaRecepcion() = runBlocking {
        locales.guardar("A"); val a = producto(2)
        locales.guardar("B"); locales.seleccionar("B"); val b = producto(1)
        locales.seleccionar("A")
        val resultado = gestion.recibir(recepcion(linea(a, 2, "A"), linea(b, 4, "B")), "Ana")
        assertEquals(AppResult.Err(AppError.SinPermiso), resultado)
        assertEquals(2L, productos.obtener(a)!!.cantidad)
        assertEquals(Cup(100), productos.obtener(a)!!.precioCosto)
        assertTrue(db.gestionDao().recepciones().isEmpty())
        assertTrue(db.gestionDao().lotes().isEmpty())
    }

    @Test fun fefoConsumePrimeroLaCaducidadMasCercanaYAnularDevuelveEsosLotesUnaSolaVez() = runBlocking {
        val p = producto()
        ok(gestion.recibir(recepcion(linea(p, 3, "Tarde", LocalDate.parse("2027-01-01")), linea(p, 3, "Pronto", LocalDate.parse("2026-12-01")), linea(p, 2, "Sin fecha")), "Ana"))
        val venta = ok(vender(p, 4))
        val saldos = gestion.lotes().first().associate { it.codigo to it.disponible.milesimas }
        assertEquals(mapOf("Pronto" to 0L, "Tarde" to 2_000L, "Sin fecha" to 2_000L), saldos)
        assertEquals(4_000L, db.gestionDao().consumosDe(venta).sumOf { it.cantidadMil })
        ok(ventas.anular(venta, Anulacion(ahora, "Cobro cancelado", "Ana")))
        assertEquals(8L, productos.obtener(p)!!.cantidad)
        assertEquals(8_000L, gestion.lotes().first().sumOf { it.disponible.milesimas })
        assertTrue(db.gestionDao().consumosDe(venta).isEmpty())
        assertTrue(ventas.anular(venta, Anulacion(ahora, "Repetida", "Ana")) is AppResult.Err)
        assertEquals(8_000L, gestion.lotes().first().sumOf { it.disponible.milesimas })
    }

    @Test fun ventaSinStockNoDejaConsumosNiVentasParciales() = runBlocking {
        val p = producto()
        ok(gestion.recibir(recepcion(linea(p, 2, "L-1")), "Ana"))
        val antes = db.gestionDao().lotes()
        assertTrue(vender(p, 3) is AppResult.Err)
        assertEquals(antes, db.gestionDao().lotes())
        assertTrue(db.gestionDao().consumos().isEmpty())
        assertTrue(db.ventaDao().todas().isEmpty())
        assertEquals(2L, productos.obtener(p)!!.cantidad)
    }

    @Test fun mermaDescuentaLoteYDejaAutorYMotivoEstructurado() = runBlocking {
        val p = producto()
        ok(gestion.recibir(recepcion(linea(p, 5, "L-1")), "Ana"))
        ok(gestion.ajustar(TipoEntidad.PRODUCTO, p, Cantidad.enteras(-2), MotivoMovimiento.MERMA, "Envases rotos", "Luis"))
        assertEquals(3L, productos.obtener(p)!!.cantidad)
        assertEquals(3_000L, gestion.lotes().first().single().disponible.milesimas)
        val m = db.movimientoDao().ultimoDe("PRODUCTO", p)!!
        assertEquals("MERMA", m.motivo)
        assertEquals("Luis", m.hechoPor)
        assertTrue(m.nota!!.contains("Envases rotos"))
    }

    @Test fun corregirUnaMermaRestituyeSoloLosLotesDeEsaSalidaYConservaTodasLasRevisiones() = runBlocking {
        val p = producto()
        ok(gestion.recibir(recepcion(linea(p, 6, "L-1")), "Ana"))
        ok(turnos.abrir(ahora, "Ana"))
        ok(gestion.ajustar(TipoEntidad.PRODUCTO, p, Cantidad.enteras(-4), MotivoMovimiento.MERMA, "Envases rotos", "Ana"))
        val m = db.movimientoDao().ultimoDe("PRODUCTO", p)!!
        assertEquals(2_000L, gestion.lotes().first().single().disponible.milesimas)
        assertTrue(turnos.corregirMovimiento(m.id, 1, "Una merma no es entrada", ahora.toEpochMilli()) is AppResult.Err)
        assertEquals(2L, productos.obtener(p)!!.cantidad)
        assertTrue(db.gestionDao().revisiones().isEmpty())
        ok(turnos.corregirMovimiento(m.id, -2, "Solo eran dos", ahora.toEpochMilli()))
        assertEquals(4L, productos.obtener(p)!!.cantidad)
        assertEquals(4_000L, gestion.lotes().first().single().disponible.milesimas)
        ok(turnos.corregirMovimiento(m.id, -3, "Quedó otro roto", ahora.toEpochMilli()))
        assertEquals(3_000L, gestion.lotes().first().single().disponible.milesimas)
        ok(turnos.corregirMovimiento(m.id, 0, "No había roturas", ahora.toEpochMilli()))
        assertEquals(6L, productos.obtener(p)!!.cantidad)
        assertEquals(6_000L, gestion.lotes().first().single().disponible.milesimas)
        assertTrue(db.gestionDao().consumosDeMovimiento(m.id).isEmpty())
        assertEquals(listOf(-4L, -2L, -3L), db.gestionDao().revisiones().map { it.deltaAnterior })
    }

    @Test fun unaRecepcionEsInmutableAunqueSeHayaHechoEnElTurnoAbierto() = runBlocking {
        val p = producto()
        ok(turnos.abrir(ahora, "Ana"))
        ok(gestion.recibir(recepcion(linea(p, 3, "L-1")), "Ana"))
        val m = db.movimientoDao().ultimoDe("PRODUCTO", p)!!
        assertTrue(turnos.corregirMovimiento(m.id, 1, "Cantidad distinta", ahora.toEpochMilli()) is AppResult.Err)
        assertEquals(3L, productos.obtener(p)!!.cantidad)
        assertEquals(3_000L, gestion.lotes().first().single().disponible.milesimas)
        assertTrue(db.gestionDao().revisiones().isEmpty())
    }

    @Test fun unidadDeUnInsumoConHistorialNoSeReinterpretaComoOtraMedida() = runBlocking {
        val id = ok(insumos.crear(Insumo(nombre = "Harina", unidad = UnidadMedida.KILOGRAMO, precio = Cup(100), cantidad = Cantidad(1_000), creadoEn = ahora)))
        val i = insumos.obtener(id)!!
        assertEquals(AppResult.Err(AppError.Validacion("unidad", AppError.Regla.NO_PERMITIDO)), insumos.actualizar(i.copy(unidad = UnidadMedida.GRAMO)))
        assertEquals(UnidadMedida.KILOGRAMO, insumos.obtener(id)!!.unidad)
    }

    @Test fun recibirInsumosAdmiteMilesimasYRecalculaCostoCompletoDelElaborado() = runBlocking {
        val i = ok(insumos.crear(Insumo(nombre = "Harina", precio = Cup(100), cantidad = Cantidad(1_000), creadoEn = ahora)))
        val p = ok(productos.crear(Producto(categoria = Categorias.ELABORADO, nombre = "Pan", precioCosto = Cup(175),
            precioVenta = Cup(500), cantidad = 0, creadoEn = ahora, costoAdicional = Cup(125)), Receta(0, listOf(RecetaLinea(i, Cantidad(500))))))
        ok(gestion.recibir(recepcion(LineaRecepcion(TipoEntidad.INSUMO, i, Cantidad(1_000), Cup(200), "Harina-1")), "Ana"))
        assertEquals(Cup(150), insumos.obtener(i)!!.precio)
        assertEquals(Cup(200), productos.obtener(p)!!.precioCosto) // 0.5 × 1.50 + 1.25
        assertEquals(Cantidad(2_000), insumos.obtener(i)!!.cantidad)
    }

    @Test fun desbordarExistenciasNoLasConvierteEnRealesNiDejaMovimientos() = runBlocking {
        val p = producto()
        db.syncDao().forzarStockProducto(p, Long.MAX_VALUE, ahora.toEpochMilli())
        assertTrue(productos.ajustarStock(p, 1, "No cabe en Long") is AppResult.Err)
        assertEquals(Long.MAX_VALUE, productos.obtener(p)!!.cantidad)
        assertTrue(db.movimientoDao().todos().isEmpty())
    }

    @Test fun corregirNoPermiteDesbordarNiDiferenciaNiSaldosHistoricos() = runBlocking {
        val p = producto(5)
        ok(turnos.abrir(ahora, "Ana"))
        ok(productos.ajustarStock(p, 1, "Entrada manual"))
        val m = db.movimientoDao().ultimoDe("PRODUCTO", p)!!
        assertEquals(AppResult.Err(AppError.Validacion("cantidad", AppError.Regla.RANGO)), turnos.corregirMovimiento(m.id, Long.MIN_VALUE, "No cabe diferencia", 0))
        ok(productos.ajustarStock(p, -1, "Salida posterior"))
        val posterior = db.movimientoDao().ultimoDe("PRODUCTO", p)!!
        db.movimientoDao().actualizar(posterior.copy(existencia = Long.MAX_VALUE))
        assertEquals(AppResult.Err(AppError.Validacion("cantidad", AppError.Regla.RANGO)), turnos.corregirMovimiento(m.id, 2, "Desborda historial", 0))
        assertEquals(5L, productos.obtener(p)!!.cantidad)
        assertEquals(Long.MAX_VALUE, db.movimientoDao().obtener(posterior.id)!!.existencia)
        assertTrue(db.gestionDao().revisiones().isEmpty())
    }

    @Test fun recepcionQueCubreSaldoNegativoNoCreaDisponibilidadFicticiaDeLote() = runBlocking {
        val p = producto()
        db.syncDao().forzarStockProducto(p, -5, ahora.toEpochMilli())
        ok(gestion.recibir(recepcion(linea(p, 7, "Reposición")), "Ana"))
        assertEquals(2L, productos.obtener(p)!!.cantidad)
        assertEquals(2_000L, gestion.lotes().first().single().disponible.milesimas)
        assertEquals(7_000L, gestion.lotes().first().single().inicial.milesimas)
    }
}
