package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Actualizaciones
import cu.shalev.domain.model.Anulacion
import cu.shalev.domain.model.Arqueo
import cu.shalev.domain.model.EstadoApp
import cu.shalev.domain.model.EstadoArqueo
import cu.shalev.domain.model.EstadoSecundaria
import cu.shalev.domain.model.InfoActualizacion
import cu.shalev.domain.model.InfoApp
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.RecordatorioRespaldo
import cu.shalev.domain.model.ResumenTurno
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.TipoMovimientoCaja
import cu.shalev.domain.model.validas
import cu.shalev.domain.repository.ActualizacionesRepository
import cu.shalev.domain.repository.EstadoAppRepository
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.SecundariaRepository
import cu.shalev.domain.service.LineaSolicitada
import cu.shalev.domain.usecase.AbrirTurno
import cu.shalev.domain.usecase.AnularVenta
import cu.shalev.domain.usecase.CerrarTurno
import cu.shalev.domain.usecase.ComprobarActualizaciones
import cu.shalev.domain.usecase.CotizarVenta
import cu.shalev.domain.usecase.Devueltos
import cu.shalev.domain.usecase.FondoSugerido
import cu.shalev.domain.usecase.ModificarVenta
import cu.shalev.domain.usecase.RegistrarMovimientoCaja
import cu.shalev.domain.usecase.RegistrarVenta
import cu.shalev.domain.usecase.UsuarioActual
import cu.shalev.licencia.InstalledLicense
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import java.io.File
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.25.0 (P70): arqueo de caja, anular/modificar ventas, recordatorio de respaldo, actualizaciones y revocaciones. */
class Version025Test {
    private val clock = FixedClock()
    private val insumos = FakeInsumos()
    private val productos = FakeProductos().also { it.insumos = insumos }
    private val turnos = FakeTurnos()
    private val ventas = FakeVentas(productos, turnos)
    private val perfil = FakePerfil()
    private val usuario = UsuarioActual(perfil)
    private val secundaria = SecundariaFake()
    private val cotizar = CotizarVenta(productos, FakePrecios(), insumos, FakeServicios())
    private val registrar = RegistrarVenta(cotizar, turnos, ventas, perfil, clock)
    private val abrir = AbrirTurno(turnos, usuario, clock)
    private val cerrar = CerrarTurno(turnos, usuario, clock)
    private val caja = RegistrarMovimientoCaja(turnos, usuario, clock)
    private val anular = AnularVenta(ventas, usuario, clock, secundaria)
    private val modificar = ModificarVenta(cotizar, ventas, turnos, perfil, usuario, clock, secundaria)

    private fun <T> ok(r: AppResult<T>): T = (r as? AppResult.Ok)?.value ?: error("esperaba Ok y fue $r")
    private fun err(r: AppResult<*>): AppError = (r as? AppResult.Err)?.error ?: error("esperaba Err y fue $r")
    private fun pesos(n: Long) = Cup.ofPesos(n)

    // ---------------- §5 Arqueo ----------------

    @Test fun esperadoYDiferencia() {
        val a = Arqueo(fondo = pesos(500), ventasEfectivo = pesos(1200), entradas = pesos(100), salidas = pesos(300), contado = null)
        assertEquals(pesos(1500), a.esperado)
        assertNull(a.diferencia)
        assertNull(a.estado)
        assertEquals(EstadoArqueo.CUADRA, a.copy(contado = pesos(1500)).estado)
        assertEquals(EstadoArqueo.SOBRANTE, a.copy(contado = pesos(1520)).estado)
        assertEquals(EstadoArqueo.FALTANTE, a.copy(contado = pesos(1450)).estado)
        assertEquals(pesos(-50), a.copy(contado = pesos(1450)).diferencia)
    }

    @Test fun fondoYContadoNoPuedenSerNegativos() = runBlocking {
        assertEquals(AppError.Validacion("fondo", AppError.Regla.RANGO), err(abrir(pesos(-1))))
        assertNull(turnos.activo())
        ok(abrir(Cup.ZERO)) // 0 vale
        assertEquals(AppError.Validacion("contado", AppError.Regla.RANGO), err(cerrar(pesos(-1))))
        assertTrue(turnos.activo() != null)
    }

    @Test fun elFondoPropuestoEsLoContadoDelTurnoAnterior() = runBlocking {
        val sugerido = FondoSugerido(turnos)
        assertNull(sugerido())
        ok(abrir(pesos(300)))
        ok(cerrar(pesos(450)))
        assertEquals(pesos(450), sugerido())
    }

    @Test fun movimientosDeCajaValidadosYEnElArqueo() = runBlocking {
        assertEquals(AppError.TurnoCerrado, err(caja(TipoMovimientoCaja.ENTRADA, pesos(10), "Cambio")))
        ok(abrir(pesos(200)))
        assertEquals(AppError.Validacion("importe", AppError.Regla.RANGO), err(caja(TipoMovimientoCaja.SALIDA, Cup.ZERO, "Proveedor")))
        assertEquals(AppError.Validacion("motivo", AppError.Regla.REQUERIDO), err(caja(TipoMovimientoCaja.SALIDA, pesos(5), "   ")))
        assertEquals(AppError.Validacion("motivo", AppError.Regla.RANGO), err(caja(TipoMovimientoCaja.SALIDA, pesos(5), "ab")))
        assertEquals(AppError.Validacion("motivo", AppError.Regla.RANGO), err(caja(TipoMovimientoCaja.SALIDA, pesos(5), "x".repeat(61))))
        ok(caja(TipoMovimientoCaja.ENTRADA, pesos(50), "  Cambio   del  banco "))
        ok(caja(TipoMovimientoCaja.SALIDA, pesos(80), "Pago al proveedor"))
        assertEquals("Cambio del banco", turnos.caja.first().motivo)
        productos.put(producto(1, venta = 100, cantidad = 10))
        ok(registrar(listOf(LineaSolicitada(1, 2)), MetodoPago.EFECTIVO))
        val t = turnos.activo()!!
        val a = Arqueo.de(t, ResumenTurno.calcular(ventas.deTurno(t.id), turnos.movimientosDe(t.id), turnos.cajaDe(t.id)))!!
        assertEquals(pesos(200 + 200 + 50 - 80), a.esperado)
        val cerrado = ok(cerrar(pesos(370)))
        assertEquals(pesos(370), cerrado.contado)
        assertEquals(EstadoArqueo.CUADRA, Arqueo.de(cerrado, cerrado.resumen!!)!!.estado)
    }

    @Test fun turnoSinFondoNoTieneArqueo() {
        val t = cu.shalev.domain.model.Turno(id = 1, abiertoEn = T0, abiertoPor = "Ana")
        assertNull(Arqueo.de(t, ResumenTurno.VACIO))
    }

    // ---------------- §6bis Anular / modificar ----------------

    private suspend fun ventaDe(cant: Long, metodo: MetodoPago = MetodoPago.EFECTIVO): Long {
        if (turnos.activo() == null) ok(abrir(pesos(100)))
        return ok(registrar(listOf(LineaSolicitada(1, cant)), metodo))
    }

    @Test fun anularDevuelveExistenciasYSaleDeLosTotales() = runBlocking {
        productos.put(producto(1, venta = 100, cantidad = 10))
        val id = ventaDe(3)
        assertEquals(7L, productos.obtener(1)!!.cantidad)
        assertEquals(AppError.Validacion("motivo", AppError.Regla.RANGO), err(anular(id, "no")))
        ok(anular(id, "Cliente devolvió"))
        assertEquals(10L, productos.obtener(1)!!.cantidad)
        val v = ventas.obtener(id)!!
        assertTrue(v.anulada)
        assertEquals("Cliente devolvió", v.anulacion!!.motivo)
        assertEquals(TipoMovimiento.ANULACION, turnos.movimientos.last().tipo)
        assertTrue(ventas.ventas.validas().isEmpty())
        val r = ResumenTurno.calcular(ventas.deTurno(v.turnoId), turnos.movimientosDe(v.turnoId))
        assertEquals(Cup.ZERO, r.total)
        assertEquals(Cup.ZERO, r.totalEfectivo) // el esperado de caja baja
        // Dos veces no.
        assertEquals(AppError.Validacion("venta", AppError.Regla.NO_PERMITIDO), err(anular(id, "Otra vez")))
    }

    @Test fun noSeAnulaEnTurnoCerradoNiEnUnaSecundaria() = runBlocking {
        productos.put(producto(1, venta = 100, cantidad = 10))
        val id = ventaDe(1)
        secundaria.flow.value = EstadoSecundaria(vinculada = true)
        assertEquals(AppError.Validacion("venta", AppError.Regla.NO_PERMITIDO), err(anular(id, "Error de cobro")))
        secundaria.flow.value = EstadoSecundaria()
        ok(cerrar(pesos(200)))
        assertEquals(AppError.TurnoCerrado, err(anular(id, "Error de cobro")))
        assertFalse(ventas.obtener(id)!!.anulada)
    }

    @Test fun modificarCreaElParEnlazadoConLosPreciosOriginales() = runBlocking {
        productos.put(producto(1, venta = 100, cantidad = 10))
        val id = ventaDe(3)
        productos.put(producto(1, venta = 150, cantidad = 7)) // el precio cambió después
        // 9 unidades: hay 7 + las 3 que devuelve la anulación = 10.
        val nueva = ok(modificar(id, listOf(LineaSolicitada(1, 9)), MetodoPago.EFECTIVO, null, "Eran nueve"))
        val original = ventas.obtener(id)!!
        val corregida = ventas.obtener(nueva)!!
        assertTrue(original.anulada)
        assertEquals(Anulacion.motivoModificada(nueva, "Eran nueve"), original.anulacion!!.motivo)
        assertEquals(id, corregida.corrigeVentaId)
        assertEquals(original.turnoId, corregida.turnoId)
        assertEquals(pesos(100), corregida.detalles.single().precioUnitario)
        assertEquals(1L, productos.obtener(1)!!.cantidad)
    }

    @Test fun modificarComprobandoExistenciasYLineas() = runBlocking {
        productos.put(producto(1, venta = 100, cantidad = 5))
        val id = ventaDe(2)
        assertEquals(AppError.Validacion("lineas", AppError.Regla.REQUERIDO), err(modificar(id, emptyList(), MetodoPago.EFECTIVO, null, "Vacía")))
        assertTrue(err(modificar(id, listOf(LineaSolicitada(1, 6)), MetodoPago.EFECTIVO, null, "Demasiado")) is AppError.StockInsuficiente)
        assertFalse(ventas.obtener(id)!!.anulada)
    }

    @Test fun devueltosEsElContrarioDeLasSalidas() {
        val m = MovimientoInventario(fecha = T0, tipo = TipoMovimiento.VENTA, entidad = TipoEntidad.PRODUCTO, entidadId = 4, nombre = "A", delta = -3, existenciaResultante = 1)
        val i = m.copy(entidad = TipoEntidad.INSUMO, entidadId = 9, delta = -2500)
        val d = Devueltos.de(listOf(m, i))
        assertEquals(mapOf(4L to 3L), d.productos)
        assertEquals(mapOf(9L to 2500L), d.insumosMil)
        assertEquals(13L, d.aplicar(producto(4, cantidad = 10)).cantidad)
    }

    // ---------------- §4 Recordatorio de respaldo ----------------

    @Test fun recordatorioDeRespaldoMensual() {
        val ahora = T0
        fun hace(d: Long) = ahora.minus(Duration.ofDays(d))
        assertNull(RecordatorioRespaldo.aviso(ahora, EstadoApp(ultimoRespaldo = hace(29)), esPrincipal = true))
        assertEquals(30L, RecordatorioRespaldo.aviso(ahora, EstadoApp(ultimoRespaldo = hace(30)), true)!!.dias)
        assertEquals(31L, RecordatorioRespaldo.aviso(ahora, EstadoApp(ultimoRespaldo = hace(31)), true)!!.dias)
        // Nunca hecho: cuenta desde la primera apertura con 0.25.0 (no avisa el primer día).
        assertNull(RecordatorioRespaldo.aviso(ahora, EstadoApp(), true))
        assertNull(RecordatorioRespaldo.aviso(ahora, EstadoApp(cuentaRespaldoDesde = hace(5)), true))
        assertNull(RecordatorioRespaldo.aviso(ahora, EstadoApp(cuentaRespaldoDesde = hace(40)), true)!!.dias)
        // Secundaria: nunca.
        assertNull(RecordatorioRespaldo.aviso(ahora, EstadoApp(ultimoRespaldo = hace(90)), esPrincipal = false))
    }

    // ---------------- §6 Actualizaciones ----------------

    @Test fun comparacionDeVersiones() {
        assertTrue(Actualizaciones.esMasNueva("v0.25.10", "0.25.9"))
        assertTrue(Actualizaciones.esMasNueva("0.26.0", "0.25.9"))
        assertFalse(Actualizaciones.esMasNueva("0.25.0", "0.25.0"))
        assertFalse(Actualizaciones.esMasNueva("0.24.9", "0.25.0"))
        assertEquals(0, Actualizaciones.comparar("0.25.0-beta", "0.25.0"))
    }

    @Test fun intervaloSemanalYRepositorioVacio() {
        val e = EstadoApp(ultimaComprobacion = T0.minus(Duration.ofDays(6)))
        assertFalse(Actualizaciones.debeComprobar(T0, e, true))
        assertTrue(Actualizaciones.debeComprobar(T0, e.copy(ultimaComprobacion = T0.minus(Duration.ofDays(7))), true))
        assertTrue(Actualizaciones.debeComprobar(T0, EstadoApp(), true))
        assertTrue("reloj atrasado", Actualizaciones.debeComprobar(T0, e.copy(ultimaComprobacion = T0.plusSeconds(3600)), true))
        assertFalse(Actualizaciones.debeComprobar(T0, EstadoApp(), repoConfigurado = false))
        assertFalse(InfoApp("0.25.0", 46, "").repoConfigurado)
        assertTrue(InfoApp("0.25.0", 46, "usuario/shalev").repoConfigurado)
        assertFalse(InfoApp("0.25.0", 46, "https://evil/x").repoConfigurado)
    }

    @Test fun avisoSoloSiEsMasNueva() {
        val info = InfoActualizacion("0.25.1", "https://github.com/u/shalev/releases/tag/v0.25.1")
        assertEquals(info, Actualizaciones.aviso(EstadoApp(disponible = info), "0.25.0"))
        assertNull("ya instalada", Actualizaciones.aviso(EstadoApp(disponible = info), "0.25.1"))
    }

    private val estadoApp = EstadoMem()
    private val github = GithubFake()
    private val diario = mutableListOf<String>()
    private val licencia = LicRevocable()
    private val comprobar = ComprobarActualizaciones(estadoApp, github, licencia, FakeMantenimiento(diario), clock)

    @Test fun sinRepositorioNoSeConsultaNada() = runBlocking {
        github.repoConfigurado = false
        val r = comprobar("0.25.0", forzar = true)
        assertNull(r.aviso)
        assertEquals(0, github.consultas)
    }

    @Test fun guardaLaVersionNuevaYLaFecha() = runBlocking {
        github.ultima = InfoActualizacion("0.25.1", "p", bytes = 18_000_000)
        val r = comprobar("0.25.0")
        assertEquals("0.25.1", r.aviso!!.version)
        assertEquals(T0, estadoApp.flujo.value.ultimaComprobacion)
        // Dentro de la semana: no vuelve a consultar.
        comprobar("0.25.0")
        assertEquals(1, github.consultas)
    }

    @Test fun errorDeRedNoMarcaLaComprobacion() = runBlocking {
        github.error = true
        val r = comprobar("0.25.0")
        assertTrue(r.error)
        assertNull(estadoApp.flujo.value.ultimaComprobacion)
    }

    @Test fun licenciaRevocadaBorraLosDatos() = runBlocking {
        licencia.instalar()
        licencia.revocada = true
        val r = comprobar("0.25.0")
        assertTrue(r.revocada)
        assertEquals(listOf("borrar"), diario)
        assertEquals(1, licencia.refrescos)
    }

    @Test fun sinLicenciaInstaladaNoSeMiraLaListaDeRevocadas() = runBlocking {
        licencia.revocada = true
        val r = comprobar("0.25.0")
        assertFalse(r.revocada)
        assertTrue(diario.isEmpty())
        assertEquals(0, github.revocaciones)
    }

    /** 0.26.0 (§6): sin interruptor: siempre se consultan versiones y revocadas. */
    @Test fun laConsultaDeVersionesYaNoSePuedeDesactivar() = runBlocking {
        licencia.instalar()
        github.ultima = InfoActualizacion("0.25.1", "p")
        val r = comprobar("0.25.0")
        assertEquals("0.25.1", r.aviso?.version)
        assertEquals(1, github.consultas)
        assertEquals(1, github.revocaciones)
    }

    // ---------------- Fakes locales ----------------

    private class SecundariaFake : SecundariaRepository {
        val flow = MutableStateFlow(EstadoSecundaria())
        override val estado: StateFlow<EstadoSecundaria> = flow
        override suspend fun vincular(contenidoQr: String, telefono: String): AppResult<Unit> = AppResult.Ok(Unit)
        override suspend fun solicitarCierre(contado: Cup): AppResult<Unit> = AppResult.Ok(Unit)
        override suspend fun sincronizarAhora(): AppResult<Unit> = AppResult.Ok(Unit)
        override suspend fun desvincular(): AppResult<Unit> = AppResult.Ok(Unit)
        override suspend fun descartarAvisoCierre() = Unit
    }

    private class EstadoMem : EstadoAppRepository {
        val flujo = MutableStateFlow(EstadoApp())
        override val estado: Flow<EstadoApp> = flujo
        override suspend fun actual() = flujo.value
        override suspend fun editar(cambio: (EstadoApp) -> EstadoApp) { flujo.value = cambio(flujo.value) }
        override suspend fun borrar() { flujo.value = EstadoApp() }
    }

    private class GithubFake : ActualizacionesRepository {
        override var repoConfigurado = true
        var ultima: InfoActualizacion? = null
        var error = false
        var consultas = 0
        var revocaciones = 0
        override suspend fun ultimaVersion(): AppResult<InfoActualizacion?> {
            consultas++
            return if (error) AppResult.Err(AppError.Red.SinConexion) else AppResult.Ok(ultima)
        }
        override suspend fun listaRevocaciones(): AppResult<String> { revocaciones++; return AppResult.Ok("{}") }
        override suspend fun descargar(info: InfoActualizacion, progreso: (Long, Long) -> Unit): AppResult<File> = AppResult.Err(AppError.NoEncontrado)
    }

    private class LicRevocable : LicenciaRepository {
        var revocada = false
        var refrescos = 0
        private val flow = MutableStateFlow<Licencia?>(Licencia(LicenseState.Trial(5), "SPVI:abc12345", true, "h"))
        override val snapshot: StateFlow<Licencia?> = flow
        fun instalar() {
            flow.value = flow.value!!.copy(instalada = InstalledLicense("7c9e6679-7425-40de-944b-e07fc1f90ae7", TipoLicencia.MENSUAL, T0, null))
        }
        override suspend fun refrescar(): Licencia { refrescos++; return flow.value!! }
        override suspend fun aplicarRevocaciones(texto: String) = revocada
        override suspend fun construirSolicitud(input: cu.shalev.licencia.SolicitudInput) = error("no usado")
        override suspend fun activar(mensaje: String) = error("no usado")
    }
}
