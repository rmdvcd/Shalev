package cu.shalev.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.core.money.Cup
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.dao.idActivo
import cu.shalev.data.db.entity.EmpleadoEntity
import cu.shalev.data.db.entity.TurnoEntity
import cu.shalev.data.dto.toDto
import cu.shalev.data.foto.FotosLocal
import cu.shalev.data.repository.*
import cu.shalev.data.sync.*
import cu.shalev.domain.model.*
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalesPermisosCobroRoomTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, ShalevDatabase::class.java).build()
    private val ahora = Instant.parse("2026-11-13T12:00:00Z")
    private val clock = Clock { ahora }
    private val productos = ProductoRepositoryImpl(db, clock)
    private val turnos = TurnoRepositoryImpl(db)
    private val ventas = VentaRepositoryImpl(db)
    private val locales = LocalRepositoryImpl(db)
    private val sync = AlmacenSync(db, RespaldoRoomTest.PrefsEnMemoria(), clock, FotosLocal(context, clock))
    private val ejecutor = EjecutorComandos(db, productos, InsumoRepositoryImpl(db, clock), ServicioRepositoryImpl(db, clock), PreciosRepositoryImpl(db), clock)
    private fun <T> ok(r: AppResult<T>) = (r as? AppResult.Ok)?.value ?: error("Se esperaba Ok: $r")
    @After fun despues() { db.close() }
    private suspend fun producto(nombre: String = "Refresco") = ok(productos.crear(Producto(categoria = "Bebidas", nombre = nombre,
        precioCosto = Cup(125), precioVenta = Cup(250), cantidad = 10, creadoEn = ahora), null))
    private suspend fun empleado(): EmpleadoEntity {
        val e = EmpleadoEntity(id = 3, nombre = "Ana", permisos = "EDITAR_INVENTARIO", creadoEn = 0, vinculadoEn = 0,
            ultimaSincronizacion = null, clave = null, codigoToken = null, codigoVence = null, activo = true, localId = db.almacenLocalDao().idActivo())
        db.syncDao().insertarEmpleado(e)
        return e
    }
    private fun detalle(p: Long) = DetalleVenta(productoId = p, nombre = "Refresco", categoria = "Bebidas", cantidad = 1,
        precioBase = Cup(250), precioUnitario = Cup(250), costoUnitario = Cup(125))
    private fun venta(t: Long, p: Long, numero: String) = Venta(turnoId = t, fecha = ahora, metodoPago = MetodoPago.TRANSFERENCIA,
        detalles = listOf(detalle(p)), transaccion = Transaccion(fecha = ahora, importe = Cup(250), numero = numero, cliente = DatosCliente("Ana Pérez", "85010112345", "+5351234567")))

    @Test fun editarExistenciasNoPermiteCambiarPrecioDentroDeGuardarProducto() = runBlocking {
        val p = producto(); val e = empleado()
        val actual = productos.obtener(p)!!
        val no = ejecutor.resultado(Comando(1, GuardarProducto(actual.copy(cantidad = 12, precioVenta = Cup(300)).toDto()), "precio-oculto", e.localId), e)
        assertEquals(ErroresRemotos.PERMISO, no.error)
        assertEquals(10L, productos.obtener(p)!!.cantidad)
        assertEquals(Cup(250), productos.obtener(p)!!.precioVenta)
        val si = ejecutor.resultado(Comando(2, GuardarProducto(actual.copy(cantidad = 12).toDto()), "solo-stock", e.localId), e)
        assertNull(si.error)
        assertEquals(12L, productos.obtener(p)!!.cantidad)
        assertEquals(Cup(250), productos.obtener(p)!!.precioVenta)
    }

    @Test fun empleadoSigueEnSuLocalAunqueElDuenoSeleccioneOtroYLoRenombre() = runBlocking {
        locales.guardar("A"); val a = producto("Solo A"); val e = empleado()
        locales.guardar("B"); locales.seleccionar("B"); val b = producto("Solo B")
        val inst = ok(sync.instantanea(e, "Negocio", LicenciaDto("PRUEBA")))
        assertEquals(listOf(a), inst.productos.map { it.id })
        assertEquals("A", inst.local)
        assertEquals(e.localId, inst.localId)
        assertNull(ejecutor.resultado(Comando(1, AjustarStockProducto(a, 2), "local-a", e.localId), e).error)
        assertEquals(12L, productos.obtener(a)!!.cantidad)
        assertEquals(ErroresRemotos.PERMISO, ejecutor.resultado(Comando(2, AjustarStockProducto(b, 2), "local-b", e.localId), e).error)
        assertEquals(10L, productos.obtener(b)!!.cantidad)
        locales.guardar("Almacén", "A")
        val renombrado = ok(sync.instantanea(e, "Negocio", LicenciaDto("PRUEBA")))
        assertEquals("Almacén", renombrado.local)
        assertEquals(e.localId, renombrado.localId)
    }

    @Test fun turnoRemotoAbiertoTambienBloqueaCambiarDeLocal() = runBlocking {
        locales.guardar("A"); locales.guardar("B"); locales.seleccionar("B")
        db.turnoDao().insertar(TurnoEntity(abiertoEn = 0, empleadoId = 3, local = "A"))
        try { locales.seleccionar("A"); fail("No se debe cambiar con turno remoto abierto") } catch (_: IllegalArgumentException) { }
        assertEquals("B", db.almacenLocalDao().activo())
    }

    @Test fun noSePuedeVenderUnArticuloDeOtroLocal() = runBlocking {
        locales.guardar("A"); producto("Solo A")
        locales.guardar("B"); locales.seleccionar("B"); val b = producto("Solo B")
        locales.seleccionar("A"); val t = ok(turnos.abrir(ahora, "Ana"))
        assertEquals(AppResult.Err(AppError.SinPermiso), ventas.registrar(venta(t.id, b, "BR601ADLM8997")))
        assertTrue(db.ventaDao().todas().isEmpty())
        assertEquals(10L, productos.obtener(b)!!.cantidad)
    }

    @Test fun referenciaNormalizadaSeRechazaSinCambiosYSeLiberaAlAnular() = runBlocking {
        val p = producto(); val t = ok(turnos.abrir(ahora, "Ana"))
        val primera = ok(ventas.registrar(venta(t.id, p, "BR601ADLM8997")))
        assertEquals(AppResult.Err(AppError.Duplicado("numeroTransaccion")), ventas.registrar(venta(t.id, p, " br601adlm8997 ")))
        assertEquals(9L, productos.obtener(p)!!.cantidad)
        assertEquals(1, db.ventaDao().todas().size)
        ok(ventas.anular(primera, Anulacion(ahora, "Cobro cancelado", "Ana")))
        ok(ventas.registrar(venta(t.id, p, "BR601ADLM8997")))
        assertEquals(9L, productos.obtener(p)!!.cantidad)
        assertTrue(db.ventaDao().todasTransacciones().none { it.referenciaDuplicada })
    }

    @Test fun ventasOfflineRealesConReferenciaRepetidaNoSeDescartanYQuedanMarcadas() = runBlocking {
        val p = producto(); val e = empleado()
        val tu = TurnoSync("turno-offline", Turno(abiertoEn = ahora, abiertoPor = "Ana").toDto(), e.localId)
        fun enviada(uuid: String) = VentaSync(uuid, tu.uuid, venta(0, p, "BR601ADLM8997").toDto(), listOf(
            MovimientoInventario(fecha = ahora, tipo = TipoMovimiento.VENTA, entidad = TipoEntidad.PRODUCTO, entidadId = p, nombre = "Refresco", delta = -1, existenciaResultante = 0).toDto(),
        ))
        val lote = Lote(listOf(tu), listOf(enviada("venta-1"), enviada("venta-2")))
        val recibidos = ok(sync.aplicarLote(e.id, lote))
        assertEquals(2, recibidos.ventas.size)
        assertEquals(2, db.ventaDao().todas().size)
        assertTrue(db.ventaDao().todasTransacciones().all { it.referenciaDuplicada })
        assertEquals(8L, productos.obtener(p)!!.cantidad)
        ok(sync.aplicarLote(e.id, lote))
        assertEquals(8L, productos.obtener(p)!!.cantidad)
    }
}
