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
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.LicenciaInstalada
import cu.shalev.domain.repository.EtapaRespaldo
import cu.shalev.domain.repository.PreferenciasRepository
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.StoredLicense
import cu.shalev.licencia.contract.TipoLicencia
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

    /** La copia de sincronización es interna: no cuenta como respaldo del usuario y no pide contraseña. */
    @Test fun exportarParaSincronizacionNoReiniciaElRecordatorioDeRespaldo() = runBlocking {
        val respaldoPrevio = ahora.minusSeconds(10 * 24 * 60 * 60L)
        estadoApp.flujo.value = cu.shalev.domain.model.EstadoApp(ultimoRespaldo = respaldoPrevio)
        val out = ByteArrayOutputStream()
        ok(respaldo.exportarParaSincronizacion(out))

        val info = ok(respaldo.inspeccionar(out.toByteArray().inputStream()))
        assertEquals(false, info.conContrasena)
        assertEquals(respaldoPrevio, estadoApp.flujo.value.ultimoRespaldo)
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

    @Test fun mismoDispositivoRestauraLicenciaYClavesEcdh() = runBlocking {
        val devId = "SPVI:1122334455667788"
        val devIds = object : cu.shalev.data.licencia.DeviceIdProvider(contexto) {
            override val deviceId: String = devId
        }
        val devKey = object : cu.shalev.licencia.crypto.DeviceKey {
            var pub = byteArrayOf(1, 2, 3)
            var priv = byteArrayOf(4, 5, 6)
            override fun publicSpki() = pub
            override fun agree(peer: java.security.PublicKey) = byteArrayOf(9)
            override fun exportarClaves() = pub to priv
            override fun restaurarClaves(pub: ByteArray, privPkcs8: ByteArray) {
                this.pub = pub
                this.priv = privPkcs8
            }
        }
        val licInstalada = LicenciaInstalada("lic-abc", TipoLicencia.MENSUAL, ahora, ahora.plusSeconds(30 * 86400L))
        val licRepo = LicenciaFija(Licencia(LicenseState.Active(TipoLicencia.MENSUAL, ahora.plusSeconds(30 * 86400L), "lic-abc"), devId, true, null, licInstalada))
        val store = object : cu.shalev.licencia.LicenseStore {
            var guardada: StoredLicense? = StoredLicense("SPVI2:envelope_cifrado", "lic-abc", "2026-09-30T12:00:00Z", TipoLicencia.MENSUAL, null, devId)
            override suspend fun license() = guardada
            override suspend fun saveLicense(license: StoredLicense) { guardada = license }
            override suspend fun trialStart() = null
            override suspend fun setTrialStart(instant: Instant) {}
            override suspend fun lastSeen() = null
            override suspend fun setLastSeen(instant: Instant) {}
            override suspend fun migratedAt() = null
            override suspend fun markMigrated(at: Instant) { guardada = null }
            override suspend fun revokedAt() = null
            override suspend fun markRevoked(at: Instant) { guardada = null }
            override suspend fun clearRevoked() {}
        }
        val repo = RespaldoRepositoryImpl(db, prefs, clock, Dispatchers.IO, licRepo, estadoApp, contexto, store, devKey, devIds)

        val out = ByteArrayOutputStream()
        ok(repo.exportar(clave(), out) {})

        devKey.pub = byteArrayOf(99)
        devKey.priv = byteArrayOf(88)
        store.guardada = null
        licRepo.refrescado = false

        ok(repo.importar(out.toByteArray().inputStream(), clave()))

        assertEquals(byteArrayOf(1, 2, 3).toList(), devKey.pub.toList())
        assertEquals(byteArrayOf(4, 5, 6).toList(), devKey.priv.toList())
        assertEquals("lic-abc", store.guardada?.licenseId)
        assertEquals("SPVI2:envelope_cifrado", store.guardada?.envelopeJson)
        assertEquals(devId, store.guardada?.deviceId)
        assertTrue(licRepo.refrescado)
        assertEquals(null, estadoApp.flujo.value.licenciaRecuperable)
    }

    @Test fun distintoDispositivoNoRestauraClavesNiLicenciaYLaMarcaComoRecuperable() = runBlocking {
        val devIdOrigen = "SPVI:1122334455667788"
        val devIdDestino = "SPVI:9988776655443322"
        val devIdsOrigen = object : cu.shalev.data.licencia.DeviceIdProvider(contexto) { override val deviceId: String = devIdOrigen }
        val devIdsDestino = object : cu.shalev.data.licencia.DeviceIdProvider(contexto) { override val deviceId: String = devIdDestino }

        val devKeyOrigen = object : cu.shalev.licencia.crypto.DeviceKey {
            override fun publicSpki() = byteArrayOf(1, 2, 3)
            override fun agree(peer: java.security.PublicKey) = byteArrayOf(9)
            override fun exportarClaves() = byteArrayOf(1, 2, 3) to byteArrayOf(4, 5, 6)
        }
        val devKeyDestino = object : cu.shalev.licencia.crypto.DeviceKey {
            var pub = byteArrayOf(77)
            var priv = byteArrayOf(66)
            override fun publicSpki() = pub
            override fun agree(peer: java.security.PublicKey) = byteArrayOf(9)
            override fun exportarClaves() = pub to priv
            override fun restaurarClaves(pub: ByteArray, privPkcs8: ByteArray) { this.pub = pub; this.priv = privPkcs8 }
        }

        val licInstalada = LicenciaInstalada("lic-abc", TipoLicencia.MENSUAL, ahora, ahora.plusSeconds(30 * 86400L))
        val licRepoOrigen = LicenciaFija(Licencia(LicenseState.Active(TipoLicencia.MENSUAL, ahora.plusSeconds(30 * 86400L), "lic-abc"), devIdOrigen, true, null, licInstalada))
        val licRepoDestino = LicenciaFija(Licencia(LicenseState.Trial(7), devIdDestino, true, null))

        val storeOrigen = object : cu.shalev.licencia.LicenseStore {
            val guardada = StoredLicense("SPVI2:envelope_cifrado", "lic-abc", "2026-09-30T12:00:00Z", TipoLicencia.MENSUAL, null, devIdOrigen)
            override suspend fun license() = guardada
            override suspend fun saveLicense(license: StoredLicense) {}
            override suspend fun trialStart() = null
            override suspend fun setTrialStart(instant: Instant) {}
            override suspend fun lastSeen() = null
            override suspend fun setLastSeen(instant: Instant) {}
            override suspend fun migratedAt() = null
            override suspend fun markMigrated(at: Instant) {}
            override suspend fun revokedAt() = null
            override suspend fun markRevoked(at: Instant) {}
            override suspend fun clearRevoked() {}
        }
        val storeDestino = object : cu.shalev.licencia.LicenseStore {
            var guardada: StoredLicense? = null
            override suspend fun license() = guardada
            override suspend fun saveLicense(license: StoredLicense) { guardada = license }
            override suspend fun trialStart() = null
            override suspend fun setTrialStart(instant: Instant) {}
            override suspend fun lastSeen() = null
            override suspend fun setLastSeen(instant: Instant) {}
            override suspend fun migratedAt() = null
            override suspend fun markMigrated(at: Instant) { guardada = null }
            override suspend fun revokedAt() = null
            override suspend fun markRevoked(at: Instant) { guardada = null }
            override suspend fun clearRevoked() {}
        }

        val repoOrigen = RespaldoRepositoryImpl(db, prefs, clock, Dispatchers.IO, licRepoOrigen, estadoApp, contexto, storeOrigen, devKeyOrigen, devIdsOrigen)
        val repoDestino = RespaldoRepositoryImpl(db, prefs, clock, Dispatchers.IO, licRepoDestino, estadoApp, contexto, storeDestino, devKeyDestino, devIdsDestino)

        val out = ByteArrayOutputStream()
        ok(repoOrigen.exportar(clave(), out) {})

        ok(repoDestino.importar(out.toByteArray().inputStream(), clave()))

        assertEquals(byteArrayOf(77).toList(), devKeyDestino.pub.toList())
        assertEquals(byteArrayOf(66).toList(), devKeyDestino.priv.toList())
        assertEquals(null, storeDestino.guardada)
        assertEquals("lic-abc", estadoApp.flujo.value.licenciaRecuperable?.id)
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
