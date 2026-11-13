package cu.shalev.app.integracion

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cu.shalev.app.ConfRepo
import cu.shalev.app.FakeArchivos
import cu.shalev.app.FakeExportador
import cu.shalev.app.PerfilRepo
import cu.shalev.app.PrefRepo
import cu.shalev.app.common.EntradaCompartida
import cu.shalev.app.inicio.InicioViewModel
import cu.shalev.app.licencia.LicenciaViewModel
import cu.shalev.app.respaldo.RespaldoViewModel
import cu.shalev.app.root.RootViewModel
import cu.shalev.app.venta.VentaViewModel
import cu.shalev.core.money.Cup
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.repository.InsumoRepositoryImpl
import cu.shalev.data.repository.PreciosRepositoryImpl
import cu.shalev.data.repository.ProductoRepositoryImpl
import cu.shalev.data.repository.TurnoRepositoryImpl
import cu.shalev.data.repository.VentaRepositoryImpl
import cu.shalev.data.respaldo.RespaldoRepositoryImpl
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.Preferencias
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.usecase.AbrirTurno
import cu.shalev.domain.usecase.ActivarLicencia
import cu.shalev.domain.usecase.CerrarTurno
import cu.shalev.domain.usecase.CotizarVenta
import cu.shalev.domain.usecase.ExportarRespaldo
import cu.shalev.domain.usecase.ExtraerNumeroTransaccion
import cu.shalev.domain.usecase.GuardarProducto
import cu.shalev.domain.usecase.ImportarRespaldo
import cu.shalev.domain.usecase.ObservarAlertas
import cu.shalev.domain.usecase.ObservarPermisoVenta
import cu.shalev.domain.usecase.ObtenerGraficosPeriodo
import cu.shalev.domain.usecase.ObtenerResumenGeneral
import cu.shalev.domain.usecase.PeriodoPorDefecto
import cu.shalev.domain.usecase.RangoDePreset
import cu.shalev.domain.usecase.RegistrarVenta
import cu.shalev.domain.usecase.ResolverPeriodo
import cu.shalev.domain.usecase.SolicitarLicencia
import cu.shalev.domain.usecase.UsuarioActual
import cu.shalev.domain.usecase.VigilarLicencia
import cu.shalev.licencia.ActivationResult
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.SolicitudInput
import cu.shalev.licencia.contract.TipoLicencia
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Prompt 15 — entorno de los tests de integración de :app.
 *
 * Datos de negocio REALES: Room en memoria + repositorios de :data (productos, insumos, turnos, ventas, precios,
 * respaldo). Se simulan solo los bordes que dependen del dispositivo o de terceros: Keystore/DataStore (perfil,
 * preferencias, licencia), cámara y selector de archivos del sistema.
 */
class EntornoIntegracion(licencia: LicenseState = LicenseState.Trial(5)) {
    var ahora: Instant = Instant.parse("2026-09-30T16:00:00Z") // 12:00 en La Habana
    val clock = Clock { ahora }

    val db: ShalevDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ShalevDatabase::class.java)
        .allowMainThreadQueries() // las aserciones leen la BD desde el hilo del test
        .build()
    val productos = ProductoRepositoryImpl(db, clock)
    val insumos = InsumoRepositoryImpl(db, clock)
    val servicios = cu.shalev.data.repository.ServicioRepositoryImpl(db, clock)
    val turnos = TurnoRepositoryImpl(db)
    val ventas = VentaRepositoryImpl(db)
    val precios = PreciosRepositoryImpl(db)

    val perfil = PerfilRepo(
        Perfil(
            nombre = "Ana", tarjetas = listOf(TarjetaBancaria(1, "9248129970876454")), telefonos = listOf(Telefono(2, "+5351815604")),
            pagoTarjetaId = 1, pagoTelefonoId = 2,
        ),
    )
    private val contexto = ApplicationProvider.getApplicationContext<android.content.Context>()
    val prefs = cu.shalev.data.repository.PreferenciasRepositoryImpl(
        cu.shalev.data.local.SecureDataStore(contexto, cu.shalev.data.security.KeystoreAead(contexto)), db,
    ).also { kotlinx.coroutines.runBlocking { it.completarOnboarding() } }
    val licencia = LicMem(licencia, clock)
    val conf = ConfRepo()
    val archivos = FakeArchivos()
    val entrada = EntradaCompartida()
    val estadoApp = cu.shalev.app.EstadoAppRepo()
    val respaldo = RespaldoRepositoryImpl(db, prefs, clock, Dispatchers.IO, this.licencia, estadoApp, ApplicationProvider.getApplicationContext())

    private val modelos = androidx.lifecycle.ViewModelStore()
    private fun <T : androidx.lifecycle.ViewModel> conservar(vm: T): T = vm.also {
        modelos.put(java.util.UUID.randomUUID().toString(), it)
    }
    fun cerrar() {
        // Cancelar observadores antes de cerrar Room: no dejan consultas vivas en el siguiente caso.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync { modelos.clear() }
        db.close()
    }

    private val usuario = UsuarioActual(perfil)
    private val cotizar = CotizarVenta(productos, precios, insumos, servicios)

    fun ventaVm(tipo: String = "VENTA") = conservar(VentaViewModel(
        saved = SavedStateHandle(mapOf(VentaViewModel.KEY_TIPO to tipo)),
        observarPermiso = ObservarPermisoVenta(turnos),
        productosRepo = productos,
        observarElaborados = cu.shalev.domain.usecase.ObservarElaborados(insumos, productos, Dispatchers.Unconfined),
        insumosRepo = insumos,
        observarServicios = cu.shalev.domain.usecase.ObservarServicios(servicios, insumos, Dispatchers.Unconfined),
        perfilRepo = perfil,
        abrirTurno = AbrirTurno(turnos, usuario, clock),
        cotizar = cotizar,
        registrar = RegistrarVenta(cotizar, turnos, ventas, perfil, clock),
        extraer = ExtraerNumeroTransaccion(),
        entrada = entrada,
        sesion = sesionVenta,
        secundaria = secundaria,
        clientesFijos = cu.shalev.data.repository.ClienteFijoRepositoryImpl(db, clock),
    ))

    /** 0.20.0 (H5): venta en curso y estado de app secundaria (aquí siempre principal: sin cierre pedido). */
    val sesionVenta = cu.shalev.domain.service.SesionVenta()
    val secundaria = cu.shalev.app.SecundariaRepoFake()

    fun inicioVm() = conservar(InicioViewModel(
        SavedStateHandle(), licencia, ObservarAlertas(productos, insumos, prefs, clock, Dispatchers.Unconfined), perfil,
        precios, turnos, productos,
        ResolverPeriodo(PeriodoPorDefecto(turnos, clock), RangoDePreset(clock)),
        ObtenerGraficosPeriodo(ventas, turnos, clock, Dispatchers.Unconfined),
        ObtenerResumenGeneral(ventas, productos, insumos, servicios, clock, Dispatchers.Unconfined),
        AbrirTurno(turnos, usuario, clock), CerrarTurno(turnos, usuario, clock), clock,
        secundaria, cu.shalev.app.FakeEstadoPantallas(),
    ))

    fun respaldoVm() = conservar(RespaldoViewModel(
        ExportarRespaldo(respaldo), ImportarRespaldo(respaldo), respaldo,
        archivos, clock, cu.shalev.app.actualizacion.RespaldoPrevio(
            java.io.File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "copia_previa_integracion"),
            ExportarRespaldo(respaldo), ImportarRespaldo(respaldo), estadoApp,
        ),
    ))

    fun licenciaVm() = conservar(LicenciaViewModel(
        licencia, perfil, SolicitarLicencia(licencia, perfil), ActivarLicencia(licencia), prefs,
    ))

    fun entradaVm() = conservar(cu.shalev.app.common.EntradaViewModel(entrada))
    fun recuperacionVm() = conservar(cu.shalev.app.licencia.RecuperacionViewModel(estadoApp))
    fun conteoVm() = conservar(cu.shalev.app.caja.CierreConteoViewModel(turnos))
    fun cajaVm() = conservar(cu.shalev.app.caja.CajaViewModel(
        turnos, cu.shalev.domain.usecase.FondoSugerido(turnos),
        cu.shalev.domain.usecase.RegistrarMovimientoCaja(turnos, usuario, clock), estadoApp, cu.shalev.app.TipoRepo(), clock,
    ))
    fun rootVm() = conservar(RootViewModel(licencia, prefs, VigilarLicencia(licencia, clock), cu.shalev.app.TipoRepo()))
}

// ------------------------------------------------------------------ bordes simulados

/**
 * Licencia simulada (el Keystore y la criptografía GL reales se prueban en :data LicenciaInstrumentedTest y en
 * :licencia). Acepta el mensaje que contiene [MENSAJE_VALIDO] y pasa a Active (30 días); el resto → Rejected.
 */
class LicMem(estado: LicenseState, private val clock: Clock) : LicenciaRepository {
    private val flow = MutableStateFlow<Licencia?>(Licencia(estado, "SPVI:3f2a9c0d1e4b5a67", puedeSolicitar = true, huellaEmisor = "ab12"))
    override val snapshot: StateFlow<Licencia?> = flow
    val activaciones = mutableListOf<String>()
    fun fijar(estado: LicenseState) { flow.value = flow.value!!.copy(estado = estado) }
    override suspend fun refrescar(): Licencia = flow.value!!
    override suspend fun construirSolicitud(input: SolicitudInput): AppResult<cu.shalev.licencia.SolicitudGenerada> =
        AppResult.Ok(cu.shalev.licencia.SolicitudGenerada("Solicitud\n\nSPVIR1:x", "SPVIR1:x"))
    override suspend fun activar(mensaje: String): ActivationResult {
        activaciones += mensaje
        if (MENSAJE_VALIDO !in mensaje) return ActivationResult.Rejected
        val nuevo = LicenseState.Active(TipoLicencia.MENSUAL, clock.now().plus(Duration.ofDays(30)), "lic-1")
        fijar(nuevo)
        return ActivationResult.Accepted(nuevo)
    }

    companion object { const val MENSAJE_VALIDO = "Licencia Shalev lic-1" }
}

fun cup(pesos: Long) = Cup.ofPesos(pesos)
