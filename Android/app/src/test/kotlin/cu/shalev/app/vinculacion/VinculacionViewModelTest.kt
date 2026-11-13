package cu.shalev.app.vinculacion

import cu.shalev.app.LicRepo
import cu.shalev.app.PerfilRepo
import cu.shalev.app.SecundariaRepoFake
import cu.shalev.app.T0
import cu.shalev.app.TipoRepo
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.CodigoVinculacion
import cu.shalev.domain.model.Empleado
import cu.shalev.domain.model.EstadoPrincipal
import cu.shalev.domain.model.PermisoEmpleado
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Vinculacion
import cu.shalev.domain.repository.PrincipalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 0.21.9 (P59): VinculacionViewModel — agregar/editar secundarias, QR, quitar, cierres y paso a secundaria. */
@OptIn(ExperimentalCoroutinesApi::class)
class VinculacionViewModelTest {

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    /** Principal con resultados configurables y registro de llamadas. */
    private class Principal : PrincipalRepository {
        val empleados = MutableStateFlow<List<Empleado>>(emptyList())
        override fun observarEmpleados() = empleados
        val estadoFlow = MutableStateFlow(EstadoPrincipal())
        override val estado: StateFlow<EstadoPrincipal> = estadoFlow
        var agregar: AppResult<CodigoVinculacion>? = null
        var permisos: AppResult<Unit> = AppResult.Ok(Unit)
        var cierre: AppResult<Unit> = AppResult.Ok(Unit)
        val llamadas = mutableListOf<String>()
        override suspend fun agregarEmpleado(nombre: String, permisos: Set<PermisoEmpleado>): AppResult<CodigoVinculacion> {
            llamadas += "agregar:$nombre"
            return agregar ?: AppResult.Ok(codigo(7, nombre))
        }
        override suspend fun nuevoCodigo(empleadoId: Long): AppResult<CodigoVinculacion> { llamadas += "codigo:$empleadoId"; return AppResult.Ok(codigo(empleadoId, "Luis")) }
        override suspend fun cambiarPermisos(empleadoId: Long, permisos: Set<PermisoEmpleado>): AppResult<Unit> {
            llamadas += "permisos:$empleadoId:${permisos.map { it.name }.sorted()}"; return this.permisos
        }
        override suspend fun cambiarCobro(empleadoId: Long, tarjetaId: Long?, telefonoId: Long?): AppResult<Unit> {
            llamadas += "cobro:$empleadoId:$tarjetaId:$telefonoId"; return AppResult.Ok(Unit)
        }
        override suspend fun pedirCierre(empleadoId: Long): AppResult<Unit> { llamadas += "cierre:$empleadoId"; return cierre }
        override suspend fun aprobarCierre(empleadoId: Long): AppResult<Unit> { llamadas += "aprobar:$empleadoId"; return cierre }
        override suspend fun rechazarCierre(empleadoId: Long): AppResult<Unit> { llamadas += "rechazar:$empleadoId"; return cierre }
        override suspend fun quitarEmpleado(empleadoId: Long): AppResult<Unit> { llamadas += "quitar:$empleadoId"; return AppResult.Ok(Unit) }

        fun codigo(id: Long, nombre: String) = CodigoVinculacion("negocio-1", "Bodega", listOf("192.168.1.5"), 47_000, id, nombre,
            ByteArray(16) { it.toByte() }, T0.plusSeconds(600))
    }

    private val principal = Principal()
    private val secundaria = SecundariaRepoFake()
    private val ajustesDisp = cu.shalev.app.AjustesDispRepo(cu.shalev.domain.model.AjustesDispositivo(eligioSecundaria = true))
    private val perfil = PerfilRepo(Perfil(tarjetas = listOf(TarjetaBancaria(id = 3, numero = "9204129912345678")), pagoTarjetaId = 3))
    private val vm by lazy { VinculacionViewModel(TipoRepo(), principal, secundaria, perfil, LicRepo(), ajustesDisp) } // después de setMain

    private fun TestScope.mensajes(): MutableList<String> {
        val l = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.mensajes.collect { l += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return l
    }

    private val luis = Empleado(id = 7, nombre = "Luis", permisos = PermisoEmpleado.PREDETERMINADOS, creadoEn = T0)

    @Test fun agregarMuestraElQrYGuardaElCobroElegido() = runTest {
        mensajes()
        assertTrue(vm.state.value.puedeAgregar)
        assertEquals("9204129912345678", vm.state.value.tarjetaPredeterminada!!.numero)
        vm.agregar()
        vm.nombre("Luis " + "x".repeat(100))
        assertEquals(60, vm.state.value.form!!.nombre.length)                // tope de 60
        vm.nombre("Luis")
        vm.tarjeta(3)
        vm.guardarForm()
        assertNull(vm.state.value.form)
        val qr = vm.state.value.qr!!
        assertEquals("Luis", qr.nombreEmpleado)
        assertTrue(qr.contenido.isNotBlank())
        assertEquals(listOf("agregar:Luis", "cobro:7:3:null"), principal.llamadas)
        vm.cerrarQr()
        assertNull(vm.state.value.qr)
    }

    @Test fun errorDeNombreVaAlCampoYElLimiteAlAviso() = runTest {
        val m = mensajes()
        principal.agregar = AppResult.Err(AppError.Validacion("nombre", AppError.Regla.REQUERIDO))
        vm.agregar(); vm.guardarForm()
        assertNotNull(vm.state.value.form!!.errorNombre)
        vm.nombre("L")
        assertNull(vm.state.value.form!!.errorNombre)                        // escribir borra el error
        principal.agregar = AppResult.Err(AppError.SinPermiso)
        vm.guardarForm()
        assertEquals(VinculacionLogic.error(AppError.SinPermiso), m.last())
        assertNotNull(vm.state.value.form)                                   // el formulario sigue abierto
    }

    @Test fun elLimiteDeLaLicenciaImpideAgregarMas() = runTest {
        mensajes()
        principal.empleados.value = (1..Vinculacion.SECUNDARIAS_DEFECTO.toLong()).map { luis.copy(id = it) }
        assertFalse(vm.state.value.puedeAgregar)
    }

    @Test fun editarPermisosYCobro() = runTest {
        val m = mensajes()
        principal.empleados.value = listOf(luis)
        vm.editar(luis)
        vm.permiso(PermisoEmpleado.CAMBIAR_PRECIOS, true)
        vm.telefono(null)
        vm.guardarForm()
        assertEquals(VinculacionLogic.GUARDADO, m.last())
        assertTrue(principal.llamadas.first().startsWith("permisos:7:") && "CAMBIAR_PRECIOS" in principal.llamadas.first())
        assertEquals("cobro:7:null:null", principal.llamadas.last())

        // Si fallan los permisos no se toca el cobro.
        principal.llamadas.clear()
        principal.permisos = AppResult.Err(AppError.NoEncontrado)
        vm.editar(luis); vm.guardarForm()
        assertEquals(1, principal.llamadas.size)
        assertNotNull(vm.state.value.form)
    }

    @Test fun nuevoCodigoYQuitar() = runTest {
        val m = mensajes()
        principal.empleados.value = listOf(luis)
        vm.editar(luis); vm.nuevoCodigo()
        assertEquals(7L, vm.state.value.qr!!.codigo.empleadoId)
        vm.cerrarQr()
        vm.editar(luis); vm.pedirQuitar()
        assertEquals(luis, vm.state.value.quitar)
        assertNull(vm.state.value.form)
        vm.cancelarQuitar(); assertNull(vm.state.value.quitar)
        vm.editar(luis); vm.pedirQuitar(); vm.confirmarQuitar()
        assertEquals("App de Luis quitada", m.last())
        assertEquals("quitar:7", principal.llamadas.last())
    }

    @Test fun pedirCierreYResolverLaSolicitudDelEmpleado() = runTest {
        val m = mensajes()
        principal.empleados.value = listOf(luis.copy(turnoAbiertoDesde = T0))
        principal.estadoFlow.value = EstadoPrincipal(conectadas = setOf(7))
        vm.editar(luis); vm.pedirCierre()
        assertEquals(7L, vm.state.value.cerrarTurno!!.id)
        vm.confirmarCierre()
        assertEquals(VinculacionLogic.cierrePedido("Luis", true), m.last())

        principal.cierre = AppResult.Err(AppError.TurnoCerrado)
        vm.editar(luis); vm.aprobarCierre()
        assertEquals(VinculacionLogic.SIN_TURNO, m.last())
        principal.cierre = AppResult.Ok(Unit)
        vm.editar(luis); vm.rechazarCierre()
        assertEquals(VinculacionLogic.cierreRechazado("Luis"), m.last())
        assertEquals(listOf("cierre:7", "aprobar:7", "rechazar:7"), principal.llamadas)
    }

    /** 0.32.0: el fondo lo escribe quien abre el turno; la principal ya no lo asigna. */
    @Test fun laFichaNoMuestraFondoAsignado() {
        val v = luis.copy(vinculadoEn = T0, ultimaSincronizacion = T0)
        val subtitulo = VinculacionLogic.subtituloEmpleado(v, false, T0)
        assertTrue(subtitulo.contains("Última vez"))
        assertFalse(subtitulo.contains("fondo", ignoreCase = true))
    }

    @Test fun pasarASecundariaPideUnTelefonoValidoAntesDeEscanear() = runTest {
        val m = mensajes()
        var escaneos = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.escanear.collect { escaneos++ } }
        vm.pedirSecundaria()
        assertTrue(vm.state.value.confirmarSecundaria)
        vm.telefonoSecundaria("4123")
        vm.confirmarSecundaria()
        assertEquals(VinculacionLogic.ERROR_TELEFONO, vm.state.value.errorTelefono)
        assertEquals(0, escaneos)
        vm.telefonoSecundaria("5 444-55-66")                                 // el filtro deja solo dígitos
        assertNull(vm.state.value.errorTelefono)
        assertEquals("54445566", vm.state.value.telefonoSecundaria)
        vm.confirmarSecundaria()
        assertEquals(1, escaneos)
        assertFalse(vm.state.value.confirmarSecundaria)

        vm.qrLeido("SPVI2:qr")
        assertEquals("54445566", secundaria.telefonoVinculado)
        assertEquals(VinculacionLogic.error(AppError.SinPrincipal()), m.last()) // el fake no encuentra la principal
        assertFalse(vm.state.value.trabajando)
    }

    /** 0.27.0 (T9): una app principal (no eligió «Secundaria» al instalarse) nunca pasa a secundaria. */
    @Test fun unaPrincipalNuncaPasaASecundaria() = runTest {
        ajustesDisp.estado.value = cu.shalev.domain.model.AjustesDispositivo(eligioSecundaria = false)
        val m = mensajes()
        vm.pedirSecundaria()
        assertFalse(vm.state.value.confirmarSecundaria)
        assertEquals(VinculacionLogic.PRINCIPAL_NO_SECUNDARIA, m.last())
        vm.qrLeido("SPVI2:qr")
        assertNull(secundaria.telefonoVinculado)
        assertFalse(cu.shalev.domain.model.Vinculacion.puedeVincularseComoSecundaria(cu.shalev.domain.model.TipoApp.PRINCIPAL, eligioSecundaria = false, empleados = 0))
        assertFalse(cu.shalev.domain.model.Vinculacion.puedeVincularseComoSecundaria(cu.shalev.domain.model.TipoApp.PRINCIPAL, eligioSecundaria = true, empleados = 1))
        assertFalse(cu.shalev.domain.model.Vinculacion.puedeVincularseComoSecundaria(cu.shalev.domain.model.TipoApp.SECUNDARIA, eligioSecundaria = true, empleados = 0))
        assertTrue(cu.shalev.domain.model.Vinculacion.puedeVincularseComoSecundaria(cu.shalev.domain.model.TipoApp.PRINCIPAL, eligioSecundaria = true, empleados = 0))
    }

    @Test fun secundariaSincronizaYDesvincula() = runTest {
        val m = mensajes()
        vm.sincronizar()
        assertEquals(VinculacionLogic.error(AppError.SinPrincipal()), m.last())
        vm.pedirDesvincular(); assertTrue(vm.state.value.confirmarDesvincular)
        vm.confirmarDesvincular()
        assertFalse(vm.state.value.confirmarDesvincular)
        assertFalse(vm.state.value.trabajando)
    }
}
