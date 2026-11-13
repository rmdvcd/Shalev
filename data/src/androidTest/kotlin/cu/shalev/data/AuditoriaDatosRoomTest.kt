package cu.shalev.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.dto.*
import cu.shalev.data.foto.FotoRepositoryImpl
import cu.shalev.data.foto.FotosLocal
import cu.shalev.data.local.SecureDataStore
import cu.shalev.data.local.ShalevJson
import cu.shalev.data.repository.*
import cu.shalev.data.respaldo.BackupCipher
import cu.shalev.data.respaldo.RespaldoRepositoryImpl
import cu.shalev.data.security.KeystoreAead
import cu.shalev.data.sync.*
import cu.shalev.domain.fichacosto.*
import cu.shalev.domain.model.*
import cu.shalev.domain.usecase.LimpiarFotos
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Regresiones transaccionales con SQLite y Keystore reales, sin red. */
@RunWith(AndroidJUnit4::class)
class AuditoriaDatosRoomTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder(context, ShalevDatabase::class.java).build()
    private val ahora = Instant.parse("2026-11-13T12:00:00Z")
    private val clock = Clock { ahora }
    private val ds = SecureDataStore(context, KeystoreAead(context))
    private val config = ConfigSync(ds)
    private val preferencias = PreferenciasRepositoryImpl(ds, db)
    private val productos = ProductoRepositoryImpl(db, clock)
    private val insumos = InsumoRepositoryImpl(db, clock)
    private val servicios = ServicioRepositoryImpl(db, clock)
    private val turnos = TurnoRepositoryImpl(db)
    private val ventas = VentaRepositoryImpl(db)
    private val fichas = FichaCostoRepositoryImpl(db, context, clock, config, insumos, productos, servicios)
    private val gestion = GestionInventarioRepositoryImpl(db, productos, insumos, config, clock)
    private val fotos = FotosLocal(context, clock)
    private val respaldo = RespaldoRepositoryImpl(db, preferencias, clock, Dispatchers.IO, LicenciaFija(), EstadoAppEnMemoria(), context)
    private val mantenimiento = MantenimientoRepositoryImpl(context, db, preferencias, ds, Dispatchers.IO)
    private val prefsLegacy get() = context.getSharedPreferences("fichas_costo", Context.MODE_PRIVATE)
    private var fotosAnteriores = emptySet<String>()
    private fun <T> ok(r: AppResult<T>) = (r as? AppResult.Ok)?.value ?: error("Se esperaba Ok: $r")
    @Before fun antes() = runBlocking {
        config.hacerPrincipal(); ds.put("pref.v1", null)
        prefsLegacy.edit().clear().commit()
        fotosAnteriores = File(context.filesDir, "fotos").list().orEmpty().toSet()
    }
    @After fun despues() {
        prefsLegacy.edit().clear().commit()
        runBlocking { config.hacerPrincipal(); ds.put("pref.v1", null) }
        File(context.filesDir, "fotos").listFiles().orEmpty().filter { it.name !in fotosAnteriores }.forEach { it.delete() }
        db.close()
    }
    private fun ficha(id: Long = 1, nombre: String = "Pan") = FichaCostoGuardada(id, nombre, "100", UnidadMargen.PORCENTAJE, "", listOf(
        PartidaCosto("Harina", CategoriaCosto.MATERIAS, BigDecimal.ONE, "kg", BigDecimal("1.25"), MonedaCosto.CUP),
        PartidaCosto("Trabajo", CategoriaCosto.TRABAJO, BigDecimal.ONE, "prestación", BigDecimal("2.10"), MonedaCosto.CUP),
    ))
    private suspend fun producto(nombre: String = "Refresco", foto: String? = null, cantidad: Long = 0) = ok(productos.crear(Producto(
        categoria = "Bebidas", nombre = nombre, precioCosto = Cup(125), precioVenta = Cup(250), cantidad = cantidad, fotoUri = foto, creadoEn = ahora,
    ), null))
    private fun foto(): String = fotos.guardar("auditoria-${UUID.randomUUID()}.jpg", byteArrayOf(1, 2, 3, 4, 5))!!
    private suspend fun exportar(): ByteArray = ByteArrayOutputStream().also { ok(respaldo.exportar(CharArray(0), it) {}) }.toByteArray()
    private fun dto(archivo: ByteArray): RespaldoDto = ShalevJson.decodeFromString(RespaldoDto.serializer(), String(BackupCipher().descifrar(archivo, CharArray(0)), Charsets.UTF_8))
    private fun archivo(dto: RespaldoDto) = BackupCipher().cifrar(ShalevJson.encodeToString(RespaldoDto.serializer(), dto).toByteArray(), CharArray(0), ahora.toEpochMilli())

    @Test fun migracionDeFichasNoPierdeColisionesYEsIdempotenteInclusoSiElLegadoReaparece() = runBlocking {
        ok(fichas.guardar(ficha(nombre = "Actual")))
        val antigua = ficha(nombre = "Legada con el mismo id")
        val json = ShalevJson.encodeToString(ListSerializer(FichaCostoDto.serializer()), listOf(antigua.toDto()))
        prefsLegacy.edit().putString("lista", json).commit()
        assertEquals(setOf("Actual", antigua.nombre), fichas.observar().first().map { it.nombre }.toSet())
        assertFalse(prefsLegacy.contains("lista"))
        prefsLegacy.edit().putString("lista", json).commit() // caída después del commit Room y antes de limpiar el legado
        assertEquals(2, fichas.observar().first().size)
        assertEquals(2, db.gestionDao().fichas().map { it.id }.distinct().size)
    }

    @Test fun falloALaMitadDeExportarUnaFichaRevierteInsumosYArticulo() = runBlocking {
        val invalida = ficha().copy(partidas = ficha().partidas + PartidaCosto("Otro material", CategoriaCosto.MATERIAS, BigDecimal.ONE, "paquete", BigDecimal.ONE, MonedaCosto.CUP))
        assertTrue(fichas.exportar(invalida, DestinoFicha.INVENTARIO) is AppResult.Err)
        assertTrue(insumos.todos().isEmpty())
        assertTrue(db.productoDao().todos().isEmpty())
        assertTrue(db.recetaDao().todas().isEmpty())
    }

    @Test fun exportarFichaReutilizaCostoVigenteConvierteGramosYConservaCostosNoMateriales() = runBlocking {
        val harina = ok(insumos.crear(Insumo(nombre = "Harina", unidad = UnidadMedida.KILOGRAMO, precio = Cup(200), cantidad = Cantidad.enteras(1), creadoEn = ahora)))
        val f = ficha().copy(partidas = listOf(
            PartidaCosto("Harina", CategoriaCosto.MATERIAS, BigDecimal("500"), "g", BigDecimal("0.01"), MonedaCosto.CUP), ficha().partidas[1],
        ))
        val id = ok(fichas.exportar(f, DestinoFicha.INVENTARIO))
        assertEquals(1, insumos.todos().size)
        assertEquals(Cup(200), insumos.obtener(harina)!!.precio)
        assertEquals(Cantidad(500), productos.receta(id)!!.lineas.single().cantidad)
        assertEquals(Cup(210), productos.obtener(id)!!.costoAdicional)
        assertEquals(Cup(310), productos.obtener(id)!!.precioCosto)
        assertEquals(Cantidad.enteras(1), insumos.obtener(harina)!!.cantidad)
    }

    @Test fun respaldoIncluyeGestionFotosYPreferenciasYSeRecuperaTrasBorradoTotal() = runBlocking {
        val uri = foto()
        val p = producto(foto = uri)
        ok(fichas.guardar(ficha()))
        ok(gestion.recibir(NuevaRecepcion("recepcion-1", "Proveedor", "F-1", "Compra inicial", listOf(LineaRecepcion(TipoEntidad.PRODUCTO, p, Cantidad.enteras(3), Cup(125), "L-1"))), "Ana"))
        val t = ok(turnos.abrir(ahora, "Ana"))
        ok(ventas.registrar(Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.EFECTIVO,
            detalles = listOf(DetalleVenta(productoId = p, nombre = "Refresco", categoria = "Bebidas", cantidad = 1, precioBase = Cup(250), precioUnitario = Cup(250), costoUnitario = Cup(125))))))
        ok(productos.ajustarStock(p, 1, "Conteo"))
        val m = db.movimientoDao().ultimoDe("PRODUCTO", p)!!
        ok(turnos.corregirMovimiento(m.id, 2, "Se contó otra caja", ahora.toEpochMilli()))
        preferencias.guardarNiveles(NivelesMinimos(productoBajo = 9))
        val copia = exportar()
        assertEquals(1, dto(copia).fotos.size)
        assertEquals(1, dto(copia).fichasCosto.size)
        ok(mantenimiento.borrarTodo())
        assertTrue(db.productoDao().todos().isEmpty())
        assertTrue(db.gestionDao().fichas().isEmpty())
        assertFalse(File(URI(uri)).exists())
        assertEquals(NivelesMinimos(), preferencias.preferencias.first().niveles)
        ok(respaldo.importar(copia.inputStream(), CharArray(0)) {})
        assertEquals(4L, productos.obtener(p)!!.cantidad)
        assertEquals(1, fichas.observar().first().size)
        assertEquals(1, db.gestionDao().recepciones().size)
        assertEquals(1, db.gestionDao().consumos().size)
        assertEquals(1, db.gestionDao().revisiones().size)
        assertEquals(9L, preferencias.preferencias.first().niveles.productoBajo)
        val restaurada = productos.obtener(p)!!.fotoUri!!
        assertNotEquals(uri, restaurada)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), File(URI(restaurada)).readBytes())
    }

    @Test fun falloSqlDespuesDePrepararFotosRevierteDatosPreferenciasYArchivos() = runBlocking {
        val uri = foto()
        val p = producto(foto = uri, cantidad = 7)
        val i = ok(insumos.crear(Insumo(nombre = "Sal", precio = Cup(10), cantidad = Cantidad.enteras(1), creadoEn = ahora)))
        preferencias.guardarNiveles(NivelesMinimos(productoBajo = 3))
        val anterior = dto(exportar())
        val lineas = listOf(RecetaLineaDto(i, 1000), RecetaLineaDto(i, 1000)) // misma PK → fallo REAL durante el commit
        val danado = anterior.copy(recetas = listOf(RecetaDto(p, lineas)), preferencias = PreferenciasDto(productoBajo = 99))
        val archivosAntes = File(context.filesDir, "fotos").list().orEmpty().toSet()
        assertTrue(respaldo.importar(archivo(danado).inputStream(), CharArray(0)) {} is AppResult.Err)
        assertEquals(7L, productos.obtener(p)!!.cantidad)
        assertEquals(3L, preferencias.preferencias.first().niveles.productoBajo)
        assertEquals(uri, productos.obtener(p)!!.fotoUri)
        assertEquals(archivosAntes, File(context.filesDir, "fotos").list().orEmpty().toSet())
    }

    @Test fun cancelarAntesDeLaFaseFinalNoTocaDatosNiPreparaFotos() = runBlocking {
        val p = producto(foto = foto(), cantidad = 7)
        val copia = exportar()
        ok(productos.ajustarStock(p, -2, "Cambio posterior"))
        val archivosAntes = File(context.filesDir, "fotos").list().orEmpty().toSet()
        val trabajo = launch(start = CoroutineStart.LAZY) {
            respaldo.importar(copia.inputStream(), CharArray(0)) { etapa ->
                if (etapa == cu.shalev.domain.repository.EtapaRespaldo.IMPORTANDO) coroutineContext[Job]!!.cancel()
            }
        }
        trabajo.start(); trabajo.join()
        assertTrue(trabajo.isCancelled)
        assertEquals(5L, productos.obtener(p)!!.cantidad)
        assertEquals(archivosAntes, File(context.filesDir, "fotos").list().orEmpty().toSet())
    }

    @Test fun cancelarDespuesDelCommitNoBorraLasFotosQueAhoraReferenciaRoom() = runBlocking {
        val p = producto(foto = foto(), cantidad = 7)
        val copia = archivo(dto(exportar()).copy(licencia = LicenciaRespaldoDto("licencia-prueba", "MENSUAL", 1)))
        ok(productos.ajustarStock(p, -2, "Cambio posterior"))
        lateinit var trabajo: Job
        val base = EstadoAppEnMemoria()
        val estado = object : cu.shalev.domain.repository.EstadoAppRepository by base {
            override suspend fun editar(cambio: (EstadoApp) -> EstadoApp) { trabajo.cancel(); base.editar(cambio) }
        }
        val repo = RespaldoRepositoryImpl(db, preferencias, clock, Dispatchers.IO, LicenciaFija(), estado, context)
        trabajo = launch(start = CoroutineStart.LAZY) { repo.importar(copia.inputStream(), CharArray(0)) {} }
        trabajo.start(); trabajo.join()
        assertTrue(trabajo.isCancelled)
        assertEquals(7L, productos.obtener(p)!!.cantidad)
        val uri = productos.obtener(p)!!.fotoUri!!
        assertTrue(File(URI(uri)).isFile)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), File(URI(uri)).readBytes())
    }

    @Test fun fotosConHashIncorrectoSeRechazanAntesDeCambiarElNegocio() = runBlocking {
        val p = producto(foto = foto(), cantidad = 7)
        val anterior = dto(exportar())
        val alterado = anterior.copy(fotos = anterior.fotos.map { it.copy(sha256 = "0".repeat(64)) })
        assertTrue(respaldo.importar(archivo(alterado).inputStream(), CharArray(0)) {} is AppResult.Err)
        assertEquals(7L, productos.obtener(p)!!.cantidad)
    }

    @Test fun limpiezaDeFotosNoBorraLasDeOtroLocal() = runBlocking {
        val locales = LocalRepositoryImpl(db)
        locales.guardar("A"); val a = foto(); producto(foto = a)
        locales.guardar("B"); locales.seleccionar("B"); val b = foto(); producto(foto = b)
        val huerfana = foto()
        listOf(a, b, huerfana).forEach { File(URI(it)).setLastModified(0) }
        LimpiarFotos(productos, FotoRepositoryImpl(context, Dispatchers.IO, clock), servicios)()
        assertTrue(File(URI(a)).isFile)
        assertTrue(File(URI(b)).isFile)
        assertFalse(File(URI(huerfana)).exists())
    }

    @Test fun borradoTotalEliminaLegadoVinculacionYPendienteYNoResucitaPreferencias() = runBlocking {
        ds.put("pref.v1", ShalevJson.encodeToString(PreferenciasDto.serializer(), PreferenciasDto(productoBajo = 77)))
        assertEquals(77L, preferencias.preferencias.first().niveles.productoBajo)
        prefsLegacy.edit().putString("lista", ShalevJson.encodeToString(ListSerializer(FichaCostoDto.serializer()), listOf(ficha().toDto()))).commit()
        config.hacerSecundaria(DatosSecundaria("negocio", "Prueba", 3, "Ana", "ficticia", emptyList(), 1234,
            comandoPendiente = ComandoPendiente("pendiente", AjustarStockProducto(1, 2))))
        ok(mantenimiento.borrarTodo())
        assertNull(config.secundariaActual())
        assertEquals(TipoApp.PRINCIPAL, config.tipo.first())
        assertFalse(prefsLegacy.contains("lista"))
        assertEquals(NivelesMinimos(), PreferenciasRepositoryImpl(ds, db).preferencias.first().niveles)
        assertTrue(fichas.observar().first().isEmpty())
    }
}
