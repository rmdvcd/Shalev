package cu.shalev.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.core.money.Cup
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.repository.ProductoRepositoryImpl
import cu.shalev.data.repository.TurnoRepositoryImpl
import cu.shalev.data.repository.VentaRepositoryImpl
import cu.shalev.data.respaldo.RespaldoRepositoryImpl
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.Preferencias
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.EtapaRespaldo
import cu.shalev.domain.repository.PreferenciasRepository
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prompt 15 — exportación/importación contra Room en memoria: ida y vuelta completa, y que un archivo cortado,
 * alterado o con otra contraseña NO toca los datos actuales (la importación es todo o nada).
 * El cifrado en sí está cubierto en BackupCipherTest (JVM).
 */
@RunWith(AndroidJUnit4::class)
class RespaldoRoomTest {

    private val ahora = Instant.parse("2026-09-30T12:00:00Z")
    private val clock = Clock { ahora }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ShalevDatabase::class.java).build()
    private val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val prefs = cu.shalev.data.repository.PreferenciasRepositoryImpl(cu.shalev.data.local.SecureDataStore(contexto, cu.shalev.data.security.KeystoreAead(contexto)), db)
    private val estadoApp = EstadoAppEnMemoria()
    private val respaldo = RespaldoRepositoryImpl(db, prefs, clock, Dispatchers.IO, LicenciaFija(), estadoApp, ApplicationProvider.getApplicationContext())
    private val productos = ProductoRepositoryImpl(db, clock)
    private val turnos = TurnoRepositoryImpl(db)
    private val ventas = VentaRepositoryImpl(db)

    @After fun cerrarBd() = db.close()

    private fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Ok)?.value ?: error("esperaba Ok y fue $r")
    private fun err(r: AppResult<*>): AppError = (r as? AppResult.Err)?.error ?: error("esperaba Err y fue $r")
    private fun clave() = "Respaldo-2026".toCharArray()

    private fun producto(nombre: String, cantidad: Long) = Producto(categoria = "Bebidas", nombre = nombre,
        precioCosto = Cup.ofPesos(60), precioVenta = Cup.ofPesos(100), cantidad = cantidad, creadoEn = ahora)

    private suspend fun nombres() = productos.observarTodos().first().map { it.nombre }.sorted()

    /** Estado A (1 producto, 1 venta en turno cerrado) exportado a bytes; luego se pasa al estado B. */
    private suspend fun exportarYCambiar(contrasena: CharArray? = null): ByteArray {
        val id = ok(productos.crear(producto("Refresco", 10), null))
        val p = productos.obtener(id)!!
        val t = ok(turnos.abrir(ahora, "Ana"))
        ok(ventas.registrar(Venta(turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(
            DetalleVenta(productoId = id, nombre = p.nombre, categoria = p.categoria, cantidad = 2,
                precioBase = p.precioVenta, precioUnitario = p.precioVenta, costoUnitario = p.precioCosto)))))
        ok(turnos.cerrar(ahora, "Ana"))
        prefs.guardarNiveles(NivelesMinimos(productoBajo = 9))

        val etapas = mutableListOf<EtapaRespaldo>()
        val out = ByteArrayOutputStream()
        val resumen = ok(respaldo.exportar(contrasena ?: clave(), out) { etapas += it })
        assertEquals(1, resumen.productos)
        assertEquals(1, resumen.ventas)
        assertTrue(etapas.isNotEmpty())

        // Estado B: otro producto y otras preferencias.
        ok(productos.crear(producto("Galletas", 3), null))
        prefs.guardarNiveles(NivelesMinimos(productoBajo = 3))
        assertEquals(listOf("Galletas", "Refresco"), nombres())
        return out.toByteArray()
    }

    @Test fun idaYVueltaRestauraElEstadoExportado() = runBlocking {
        val archivo = exportarYCambiar()
        val info = ok(respaldo.inspeccionar(archivo.inputStream()))
        assertEquals(4, info.version)
        assertTrue(info.conContrasena)
        assertEquals(ahora, info.creadoEn)
        val r = ok(respaldo.importar(archivo.inputStream(), clave()))
        assertEquals(1, r.productos)
        assertEquals(listOf("Refresco"), nombres())
        assertEquals(8L, productos.observarTodos().first().single().cantidad)
        assertEquals(1, ventas.entre(ahora.minusSeconds(1), ahora.plusSeconds(1)).size)
        assertEquals(9L, prefs.preferencias.first().niveles.productoBajo)
    }

    /** 0.27.0 (T10): sin contraseña se importa sin pedirla (la recibida se ignora). */
    @Test fun idaYVueltaSinContrasena() = runBlocking {
        val archivo = exportarYCambiar(CharArray(0))
        assertEquals(false, ok(respaldo.inspeccionar(archivo.inputStream())).conContrasena)
        ok(respaldo.importar(archivo.inputStream(), CharArray(0)))
        assertEquals(listOf("Refresco"), nombres())
    }

    /**
     * 0.27.0 (N2): «Cliente fijo» al vender lo guarda por carné (la segunda venta actualiza el teléfono, no duplica),
     * sus compras salen de las transferencias no anuladas, viaja en el respaldo y se puede quitar.
     */
    @Test fun clienteFijoSeGuardaAlVenderYViajaEnElRespaldo() = runBlocking {
        val clientes = cu.shalev.data.repository.ClienteFijoRepositoryImpl(db, clock)
        val id = ok(productos.crear(producto("Refresco", 10), null))
        val p = productos.obtener(id)!!
        val t = ok(turnos.abrir(ahora, "Ana"))
        var referencia = 1000
        suspend fun vender(tel: String, fijo: Boolean, ci: String = "85010112345") = ok(ventas.registrar(Venta(
            turnoId = t.id, fecha = ahora, metodoPago = MetodoPago.TRANSFERENCIA,
            detalles = listOf(DetalleVenta(productoId = id, nombre = p.nombre, categoria = p.categoria, cantidad = 1,
                precioBase = p.precioVenta, precioUnitario = p.precioVenta, costoUnitario = p.precioCosto)),
            transaccion = cu.shalev.domain.model.Transaccion(fecha = ahora, importe = p.precioVenta, numero = "MM${++referencia}FEJ987",
                cliente = cu.shalev.domain.model.DatosCliente("María Pérez", ci, tel), clienteFijo = fijo),
        )))
        vender("+5351234567", fijo = true)
        vender("+5359999999", fijo = true)
        vender("+5352222222", fijo = false, ci = "90020212345")
        val c = clientes.observar().first().single()
        assertEquals("+5359999999", c.telefono)
        assertEquals(2, c.compras)
        assertEquals(Cup.ofPesos(200), c.total)

        val out = ByteArrayOutputStream()
        ok(respaldo.exportar(clave(), out) {})
        clientes.quitar("85010112345")
        assertTrue(clientes.observar().first().isEmpty())
        ok(respaldo.importar(out.toByteArray().inputStream(), clave()))
        assertEquals(listOf("85010112345"), clientes.observar().first().map { it.ci })
    }

    @Test fun archivoCortadoNoTocaLosDatos() = runBlocking {
        val archivo = exportarYCambiar()
        val cortado = archivo.copyOf(archivo.size * 2 / 3)
        assertEquals(AppError.ArchivoDanado(incompleto = true), err(respaldo.inspeccionar(cortado.inputStream())))
        assertTrue(err(respaldo.importar(cortado.inputStream(), clave())) is AppError.ArchivoDanado)
        assertEquals(listOf("Galletas", "Refresco"), nombres())
        assertEquals(3L, prefs.preferencias.first().niveles.productoBajo)
    }

    @Test fun archivoAlteradoNoTocaLosDatos() = runBlocking {
        val archivo = exportarYCambiar()
        val alterado = archivo.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 0x5A).toByte() }
        assertTrue(err(respaldo.importar(alterado.inputStream(), clave())) is AppError.ArchivoDanado)
        val basura = ByteArray(4096) { (it * 31).toByte() }
        assertTrue(err(respaldo.importar(basura.inputStream(), clave())).let { it is AppError.FormatoInvalido || it is AppError.ArchivoDanado })
        assertEquals(listOf("Galletas", "Refresco"), nombres())
    }

    @Test fun contrasenaIncorrectaNoTocaLosDatos() = runBlocking {
        val archivo = exportarYCambiar()
        assertEquals(AppError.ContrasenaIncorrecta, err(respaldo.importar(archivo.inputStream(), "otra-clave".toCharArray())))
        assertEquals(listOf("Galletas", "Refresco"), nombres())
        assertEquals(3L, prefs.preferencias.first().niveles.productoBajo)
    }

    /** Preferencias en memoria (la app usa DataStore cifrado, fuera del alcance de este test). */
    class PrefsEnMemoria : PreferenciasRepository {
        val state = MutableStateFlow(Preferencias())
        override val preferencias = state
        override val onboardingCompletado = state.map { it.onboardingCompletado }
        override suspend fun completarOnboarding() { state.value = state.value.copy(onboardingCompletado = true) }
        override suspend fun guardarNiveles(n: NivelesMinimos) { state.value = state.value.copy(niveles = n) }
        override suspend fun guardarModulos(m: Set<cu.shalev.domain.model.Modulo>) { state.value = state.value.copy(modulos = cu.shalev.domain.model.Modulo.normalizar(m)) }
        override suspend fun guardarEmpleadosPrevistos(n: Int) { state.value = state.value.copy(empleadosPrevistos = n) }
        override suspend fun reemplazar(p: Preferencias) { state.value = p }
    }
}
