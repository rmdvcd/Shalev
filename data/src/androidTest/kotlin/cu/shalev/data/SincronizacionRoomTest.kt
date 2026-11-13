package cu.shalev.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.tx
import cu.shalev.data.repository.aplicarConteo
import cu.shalev.data.dto.conteoDeJson
import cu.shalev.data.db.entity.EmpleadoEntity
import cu.shalev.data.dto.toDto
import cu.shalev.data.repository.InsumoRepositoryImpl
import cu.shalev.data.repository.PerfilRepositoryImpl
import cu.shalev.data.repository.PreciosRepositoryImpl
import cu.shalev.data.repository.ProductoRepositoryImpl
import cu.shalev.data.repository.ServicioRepositoryImpl
import cu.shalev.data.repository.TurnoRepositoryImpl
import cu.shalev.data.repository.VentaRepositoryImpl
import cu.shalev.data.sync.AjustarStockProducto
import cu.shalev.data.sync.AlmacenSync
import cu.shalev.data.sync.CambiarPrecios
import cu.shalev.data.sync.Comando
import cu.shalev.data.sync.EjecutorComandos
import cu.shalev.data.sync.EliminarInsumo
import cu.shalev.data.sync.EliminarProducto
import cu.shalev.data.sync.ErroresRemotos
import cu.shalev.data.sync.GuardarProducto
import cu.shalev.data.sync.Instantanea
import cu.shalev.data.sync.LicenciaDto
import cu.shalev.data.sync.Recibidos
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Modulo
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.Venta
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 0.21.9 (P59) — sincronización principal ↔ secundaria con DOS bases Room reales (sin sockets): catálogo, ventas sin
 * duplicar, reenvíos, existencias que no «resucitan», privacidad del Perfil y comandos de la secundaria aplicados por
 * EjecutorComandos sobre los repositorios reales (permisos, clave única, errores remotos).
 */
@RunWith(AndroidJUnit4::class)
class SincronizacionRoomTest {

    private var ahora = Instant.parse("2026-10-03T15:00:00Z")
    private val clock = Clock { ahora }
    private fun bd() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ShalevDatabase::class.java).build()

    private val dbP = bd()
    private val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val ds = cu.shalev.data.local.SecureDataStore(contexto, cu.shalev.data.security.KeystoreAead(contexto))
    private val prefsP = cu.shalev.data.repository.PreferenciasRepositoryImpl(ds, dbP)
    private val fotosP = cu.shalev.data.foto.FotosLocal(ApplicationProvider.getApplicationContext(), clock)
    private val almP = AlmacenSync(dbP, prefsP, clock, fotosP)
    private val productosP = ProductoRepositoryImpl(dbP, clock)
    private val insumosP = InsumoRepositoryImpl(dbP, clock)
    private val turnosP = TurnoRepositoryImpl(dbP)
    private val ejecutor = EjecutorComandos(dbP, productosP, insumosP, ServicioRepositoryImpl(dbP, clock), PreciosRepositoryImpl(dbP), clock)

    private val dbS = bd()
    private val prefsS = cu.shalev.data.repository.PreferenciasRepositoryImpl(ds, dbS)
    private val fotosS = cu.shalev.data.foto.FotosLocal(ApplicationProvider.getApplicationContext(), clock)
    private val almS = AlmacenSync(dbS, prefsS, clock, fotosS)
    private val productosS = ProductoRepositoryImpl(dbS, clock)
    private val turnosS = TurnoRepositoryImpl(dbS)
    private val ventasS = VentaRepositoryImpl(dbS)

    @After fun cerrar() { dbP.close(); dbS.close() }

    private fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Ok)?.value ?: error("esperaba Ok y fue $r")

    private val licencia = LicenciaDto("PRUEBA")

    private suspend fun empleado(nombre: String, permisos: String = "EDITAR_INVENTARIO", telefono: String? = null): EmpleadoEntity {
        val id = dbP.syncDao().insertarEmpleado(EmpleadoEntity(nombre = nombre, permisos = permisos, creadoEn = 0, vinculadoEn = 0,
            ultimaSincronizacion = null, clave = null, codigoToken = null, codigoVence = null, activo = true, telefono = telefono))
        return dbP.syncDao().empleado(id)!!
    }

    private suspend fun refresco(cantidad: Long): Long = ok(productosP.crear(Producto(categoria = "Bebidas", nombre = "Refresco",
        precioCosto = Cup.ofPesos(60), precioVenta = Cup.ofPesos(100), cantidad = cantidad, creadoEn = ahora, fotoUri = "file:///foto.jpg"), null))

    private suspend fun venderEnS(turnoId: Long, productoId: Long, n: Long) = ventasS.registrar(Venta(turnoId = turnoId, fecha = ahora,
        metodoPago = MetodoPago.EFECTIVO, detalles = listOf(DetalleVenta(productoId = productoId, nombre = "Refresco", categoria = "Bebidas",
            cantidad = n, precioBase = Cup.ofPesos(100), precioUnitario = Cup.ofPesos(100), costoUnitario = Cup.ofPesos(60)))))

    private suspend fun instantanea(e: EmpleadoEntity): Instantanea = ok(almP.instantanea(e, "Bodega La Esquina", licencia))

    @Test fun conteoDelEmpleadoViajaSinAjustarYLaAprobacionNoSeDuplicaAlReenviar() = runBlocking {
        val p = refresco(10)
        val e = empleado("Luis")
        ok(almS.aplicarEnSecundaria(Recibidos(), instantanea(e)))
        val t = ok(turnosS.abrir(ahora, "Luis", Cup.ZERO))
        ok(venderEnS(t.id, p, 2))
        val snapshot = ok(turnosS.prepararConteo())
        val conteo = cu.shalev.domain.model.ConteoCierre(mapOf(20000 to 1L), inventario = snapshot.second.map { it.copy(contado = 7) }, motivo = "Una unidad rota")
        ok(turnosS.guardarConteo(t.id, conteo))
        val lote = almS.lotePendiente()
        ok(almP.aplicarLote(e.id, lote))
        val remoto = dbP.turnoDao().activoDeEmpleado(e.id)!!
        assertEquals(8L, productosP.obtener(p)!!.cantidad) // solo se descontó la venta
        assertEquals(conteo, remoto.conteoCierreJson!!.conteoDeJson())
        assertTrue(!cu.shalev.data.sync.CierreRemoto.conteoListo(turnosS.activo()))
        ok(dbP.tx { dbP.aplicarConteo(remoto.id, "Dueño", ahora.toEpochMilli()) })
        val aprobado = dbP.turnoDao().obtener(remoto.id)!!.conteoCierreJson!!.conteoDeJson()
        assertEquals(7L, productosP.obtener(p)!!.cantidad)
        ok(almP.aplicarLote(e.id, lote)) // respuesta perdida: no borra la aprobación
        ok(dbP.tx { dbP.aplicarConteo(remoto.id, "Dueño", ahora.toEpochMilli()) })
        assertEquals(7L, productosP.obtener(p)!!.cantidad)
        assertEquals(1, turnosP.movimientosDe(remoto.id).count { it.tipo == TipoMovimiento.AJUSTE })
        val uuid = dbS.turnoDao().activo()!!.uuid
        ok(almS.marcarConteoAprobado("otro-turno", aprobado))
        assertTrue(!cu.shalev.data.sync.CierreRemoto.conteoListo(turnosS.activo()))
        ok(almS.marcarConteoAprobado(uuid, aprobado))
        assertTrue(cu.shalev.data.sync.CierreRemoto.conteoListo(turnosS.activo()))
        ok(turnosS.cerrarSinAjustar(ahora, "Dueño", conteo.efectivo))
        ok(almP.aplicarLote(e.id, almS.lotePendiente()))
        assertEquals(7L, productosP.obtener(p)!!.cantidad)
        assertEquals("Dueño", turnosP.obtener(remoto.id)!!.conteoCierre!!.aprobadoPor)
    }

    // ------------------------------------------------------------------------------------------------ ida y vuelta

    @Test fun catalogoVaALaSecundariaYSusVentasVuelvenUnaSolaVez() = runBlocking {
        val p = refresco(10)
        val luis = empleado("Luis")
        prefsP.guardarModulos(setOf(Modulo.VENTAS))

        ok(almS.aplicarEnSecundaria(Recibidos(), instantanea(luis)))
        val enS = productosS.obtener(p)!!
        assertEquals(10L, enS.cantidad)
        assertNull(enS.fotoUri)                                              // la foto aún no está en el álbum de esta app
        assertEquals(Modulo.normalizar(setOf(Modulo.VENTAS)), prefsS.preferencias.first().modulos)

        // La secundaria vende 3 en su turno.
        val t = ok(turnosS.abrir(ahora, "Luis"))
        ok(venderEnS(t.id, p, 3))
        assertEquals(2, almS.pendientes())                                   // turno + venta
        val lote = almS.lotePendiente()
        assertEquals(1, lote.turnos.size)
        assertEquals(1, lote.ventas.size)
        assertEquals(-3L, lote.ventas.single().movimientos.single().delta)

        // La principal lo aplica; el reenvío del mismo lote no duplica nada.
        val rec = ok(almP.aplicarLote(luis.id, lote))
        assertEquals(lote.ventas.map { it.uuid }, rec.ventas)
        assertEquals(7L, productosP.obtener(p)!!.cantidad)
        assertEquals(rec, ok(almP.aplicarLote(luis.id, lote)))
        assertEquals(7L, productosP.obtener(p)!!.cantidad)
        val turnoLuis = dbP.syncDao().turnoPorUuid(lote.turnos.single().uuid)!!
        assertEquals(luis.id, turnoLuis.empleadoId)
        assertNull(turnoLuis.cerradoEn)
        assertNull(turnosP.activo())                                         // el turno de Luis no es el de la principal
        assertEquals(1, VentaRepositoryImpl(dbP).deTurno(turnoLuis.id).size)
        assertNotNull(dbP.syncDao().empleado(luis.id)!!.ultimaSincronizacion)

        ok(almS.aplicarEnSecundaria(rec, null))
        assertEquals(0, almS.pendientes())

        // Mientras: la secundaria vende 2 más sin conexión y el dueño suma 10.
        ok(venderEnS(t.id, p, 2))
        ok(productosP.ajustarStock(p, 10, "compra"))                         // principal: 17
        ok(almS.aplicarEnSecundaria(Recibidos(), instantanea(luis)))
        assertEquals(15L, productosS.obtener(p)!!.cantidad)                  // 17 del catálogo − 2 aún no enviadas

        // Cierra su turno: la principal recibe el cierre y la venta pendiente.
        ok(turnosS.cerrar(ahora.plusSeconds(600), "Luis"))
        val lote2 = almS.lotePendiente()
        assertEquals(1, lote2.ventas.size)
        val rec2 = ok(almP.aplicarLote(luis.id, lote2))
        assertTrue(rec2.turnos.single().cerrado)
        assertEquals(15L, productosP.obtener(p)!!.cantidad)
        assertNotNull(dbP.syncDao().turnoPorUuid(lote.turnos.single().uuid)!!.cerradoEn)
        ok(almS.aplicarEnSecundaria(rec2, null))
        assertEquals(0, almS.pendientes())
        assertEquals(15L, productosS.obtener(p)!!.cantidad)
    }

    @Test fun laSecundariaRecibeSoloLosDatosDeCobroDeSuEmpleadoYNoLosDelDueno() = runBlocking {
        val perfilP = PerfilRepositoryImpl(dbP)
        perfilP.guardar(Perfil(nombre = "Marta", apellidos = "Gómez Ruiz", ci = "80010112345",
            tarjetas = listOf(TarjetaBancaria(numero = "9204129912345678"), TarjetaBancaria(numero = "9224069987654321")),
            telefonos = listOf(Telefono(numero = "+5352223344"))))
        val guardado = perfilP.perfil.first()
        val segunda = guardado.tarjetas.single { it.numero.endsWith("4321") }
        val ana = empleado("Ana", telefono = "54445566")
        dbP.syncDao().cambiarCobro(ana.id, segunda.id, null)

        val inst = instantanea(dbP.syncDao().empleado(ana.id)!!)
        assertEquals("", inst.perfil!!.nombre)
        assertEquals("", inst.perfil!!.ci)
        ok(almS.aplicarEnSecundaria(Recibidos(), inst))
        val perfilS = PerfilRepositoryImpl(dbS).perfil.first()
        assertEquals("Ana", perfilS.nombre)                                  // el «usuario» de la secundaria es el empleado
        assertEquals("", perfilS.ci)
        assertEquals(listOf("9224069987654321"), perfilS.tarjetas.map { it.numero })
        assertEquals("9224069987654321", perfilS.tarjetaPago!!.numero)
        assertEquals("+5354445566", perfilS.telefonoPago!!.numero)           // su propio teléfono (C2)
    }

    @Test fun catalogoNuevoReemplazaElAnteriorConRecetasYBorrados() = runBlocking {
        val harina = ok(insumosP.crear(Insumo(nombre = "Harina", precio = Cup.ofPesos(10), cantidad = Cantidad.enteras(5), creadoEn = ahora)))
        val pan = ok(productosP.crear(Producto(categoria = Categorias.ELABORADO, nombre = "Pan", precioCosto = Cup.ZERO,
            precioVenta = Cup.ofPesos(20), cantidad = 0, creadoEn = ahora), null))
        ok(productosP.actualizar(productosP.obtener(pan)!!, Receta(pan, listOf(RecetaLinea(harina, Cantidad(100))))))
        val p = refresco(4)
        val luis = empleado("Luis")
        ok(almS.aplicarEnSecundaria(Recibidos(), instantanea(luis)))
        assertEquals(Cantidad(100), productosS.receta(pan)!!.lineas.single().cantidad)

        ok(productosP.eliminar(p))
        ok(almS.aplicarEnSecundaria(Recibidos(), instantanea(luis)))
        assertTrue(productosS.observarTodos().first().none { it.id == p })   // borrado en la principal → fuera de la lista
        assertEquals(listOf(pan), productosS.observarTodos().first().map { it.id })
        assertEquals(AlmacenSync.hash(instantanea(luis)), AlmacenSync.hash(instantanea(luis))) // misma huella si nada cambia
    }

    @Test fun laFotoDelCatalogoLlegaALaSecundariaYNoSePierdeAlReaplicarElCatalogo() = runBlocking {
        // La principal tiene la foto en su álbum; la instantánea la anuncia (por nombre, no por su ruta).
        val fotos = cu.shalev.data.foto.FotosLocal(ApplicationProvider.getApplicationContext(), clock)
        val nombre = "foto-producto-${System.nanoTime()}.jpg"
        fotos.guardar(nombre, "bytes-de-la-foto".toByteArray())
        val uri = fotos.uriLocal(nombre)!!
        val p = ok(productosP.crear(Producto(categoria = "Bebidas", nombre = "Refresco con foto",
            precioCosto = Cup.ofPesos(60), precioVenta = Cup.ofPesos(100), cantidad = 10, creadoEn = ahora, fotoUri = uri), null))
        val luis = empleado("Luis")

        val inst = instantanea(luis)
        assertEquals(listOf("producto"), inst.fotos.map { it.entidad })
        assertEquals(nombre, inst.fotos.single().nombre)
        assertEquals(p, inst.fotos.single().id)
        assertEquals(16L, inst.fotos.single().bytes) // "bytes-de-la-foto" son 16 bytes

        // La secundaria enlaza la foto a SU propio archivo (el de este teléfono), no a la ruta de la principal.
        ok(almS.aplicarEnSecundaria(Recibidos(), inst))
        assertEquals(uri, productosS.obtener(p)!!.fotoUri)

        // Y al reaplicar el catálogo (cambia el hash) la foto NO se pierde: el archivo sigue en el álbum.
        ok(productosP.ajustarStock(p, 1, "compra"))
        ok(almS.aplicarEnSecundaria(Recibidos(), instantanea(luis)))
        assertEquals(uri, productosS.obtener(p)!!.fotoUri)
    }

    @Test fun ventaSinExistenciasEnLaPrincipalQuedaNegativaYConAviso() = runBlocking {
        val p = refresco(3)
        val luis = empleado("Luis")
        ok(almS.aplicarEnSecundaria(Recibidos(), instantanea(luis)))
        ok(productosP.ajustarStock(p, -2, "rotura"))                         // principal: 1
        val t = ok(turnosS.abrir(ahora, "Luis"))
        ok(venderEnS(t.id, p, 3))                                            // la secundaria aún veía 3

        val lote = almS.lotePendiente()
        ok(almP.aplicarLote(luis.id, lote))
        assertEquals(-2L, productosP.obtener(p)!!.cantidad)
        val turnoLuis = dbP.syncDao().turnoPorUuid(lote.turnos.single().uuid)!!
        val mov = turnosP.movimientosDe(turnoLuis.id).single { it.tipo == TipoMovimiento.VENTA }
        assertTrue(mov.nota!!.contains(AlmacenSync.NOTA_SIN_EXISTENCIAS))
        assertEquals(-2L, mov.existenciaResultante)
    }

    @Test fun loteConUnTurnoDeOtroEmpleadoNoLoTocaNiLeCuelgaVentas() = runBlocking {
        val p = refresco(10)
        val luis = empleado("Luis")
        val ana = empleado("Ana")
        ok(almS.aplicarEnSecundaria(Recibidos(), instantanea(luis)))
        val t = ok(turnosS.abrir(ahora, "Luis"))
        ok(venderEnS(t.id, p, 1))
        val lote = almS.lotePendiente()

        ok(almP.aplicarLote(luis.id, lote))
        assertEquals(AppResult.Err(cu.shalev.core.result.AppError.SinPermiso), almP.aplicarLote(ana.id, lote)) // no se acepta ni se confirma el UUID de otra app
        assertEquals(luis.id, dbP.syncDao().turnoPorUuid(lote.turnos.single().uuid)!!.empleadoId)
        assertEquals(9L, productosP.obtener(p)!!.cantidad)
    }

    // ------------------------------------------------------------------------------------------------ comandos

    @Test fun comandosDeLaSecundariaRespetanPermisosClaveYErrores() = runBlocking {
        val p = refresco(10)
        val luis = empleado("Luis")                                          // solo EDITAR_INVENTARIO
        val harina = ok(insumosP.crear(Insumo(nombre = "Harina", precio = Cup.ofPesos(10), cantidad = Cantidad.enteras(5), creadoEn = ahora)))

        // Permiso.
        assertEquals(ErroresRemotos.PERMISO, ejecutor.resultado(Comando(1, CambiarPrecios(mapOf(p to 15_000L))), luis).error)
        assertEquals(Cup.ofPesos(100), productosP.obtener(p)!!.precioVenta)

        // Clave única: el reenvío no suma dos veces y devuelve el mismo resultado.
        val c = Comando(2, AjustarStockProducto(p, 5, "compra"), clave = "c0ffee00-0000-4000-8000-000000000001")
        val r1 = ejecutor.resultado(c, luis)
        assertEquals(15L, r1.valor)
        assertEquals(r1, ejecutor.resultado(c, luis))
        assertEquals(15L, productosP.obtener(p)!!.cantidad)
        // Sin clave (secundaria anterior): se ejecuta siempre.
        ejecutor.resultado(Comando(3, AjustarStockProducto(p, 1)), luis)
        ejecutor.resultado(Comando(3, AjustarStockProducto(p, 1)), luis)
        assertEquals(17L, productosP.obtener(p)!!.cantidad)

        // Editar desde la secundaria conserva la foto de la principal y aplica cantidadLeida (F8).
        val visto = productosP.obtener(p)!!
        ok(productosP.ajustarStock(p, -2, "venta en mostrador"))             // 15
        val dto = visto.copy(nombre = "Refresco cola", fotoUri = null).toDto()
        assertNull(ejecutor.resultado(Comando(4, GuardarProducto(dto, cantidadLeida = visto.cantidad)), luis).error)
        val editado = productosP.obtener(p)!!
        assertEquals("Refresco cola", editado.nombre)
        assertEquals("file:///foto.jpg", editado.fotoUri)
        assertEquals(15L, editado.cantidad)

        // Errores remotos con el mismo código que entiende la secundaria.
        assertEquals(ErroresRemotos.NO_ENCONTRADO, ejecutor.resultado(Comando(5, EliminarProducto(999)), luis).error)
        assertEquals(ErroresRemotos.STOCK, ejecutor.resultado(Comando(6, AjustarStockProducto(p, -100)), luis).error)
        val pan = ok(productosP.crear(Producto(categoria = Categorias.ELABORADO, nombre = "Pan", precioCosto = Cup.ZERO,
            precioVenta = Cup.ofPesos(20), cantidad = 0, creadoEn = ahora), Receta(0, listOf(RecetaLinea(harina, Cantidad(100))))))
        assertEquals(listOf(pan), productosP.productosQueUsan(harina).map { it.id })
        assertEquals(ErroresRemotos.EN_USO, ejecutor.resultado(Comando(7, EliminarInsumo(harina)), luis).error)
        assertTrue(EjecutorComandos.error(ErroresRemotos.EN_USO) is cu.shalev.core.result.AppError.EnUso)

        // La nota del ajuste dice desde qué app se hizo.
        val nota = cu.shalev.data.repository.RegistroRepositoryImpl(dbP).movimientos(cu.shalev.domain.repository.FiltroRegistro(texto = "compra")).first()
            .single { it.delta == 5L }.nota
        assertEquals("compra · Desde la app de Luis", nota)
    }
}
