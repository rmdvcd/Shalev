package cu.shalev.app.onboarding

import org.junit.Assert.assertFalse
import cu.shalev.domain.model.Modulo
import androidx.lifecycle.SavedStateHandle
import cu.shalev.app.ConfRepo
import cu.shalev.app.PrefRepo
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.ConfiguracionInicial
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.PerfilRepository
import cu.shalev.domain.usecase.GuardarAlertasIniciales
import cu.shalev.domain.usecase.GuardarDatosIniciales
import cu.shalev.domain.usecase.GuardarNiveles
import cu.shalev.domain.usecase.GuardarPerfil
import cu.shalev.domain.usecase.ObservarResumenConfiguracion
import cu.shalev.licencia.ActivationResult
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.SolicitudInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    // ---------- Fakes mínimos ----------

    private class Perfiles : PerfilRepository {
        val state = MutableStateFlow(Perfil())
        override val perfil = state
        override suspend fun guardar(perfil: Perfil) { state.value = perfil }
    }

    private class Lic : LicenciaRepository {
        val flow = MutableStateFlow<Licencia?>(Licencia(LicenseState.Trial(7), "SPVI:x", false, null))
        override val snapshot: StateFlow<Licencia?> = flow
        override suspend fun refrescar() = flow.value!!
        override suspend fun construirSolicitud(input: SolicitudInput): AppResult<cu.shalev.licencia.SolicitudGenerada> =
            AppResult.Ok(cu.shalev.licencia.SolicitudGenerada("", ""))
        override suspend fun activar(mensaje: String) = ActivationResult.NotFound
    }

    private val perfiles = Perfiles(); private val prefs = PrefRepo(); private val conf = ConfRepo(); private val lic = Lic()
    private val ajustesDisp = cu.shalev.app.AjustesDispRepo()

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) = OnboardingViewModel(
        saved,
        ObservarResumenConfiguracion(perfiles, lic, conf), perfiles, prefs, lic,
        GuardarDatosIniciales(perfiles, GuardarPerfil(perfiles)),
        GuardarAlertasIniciales(GuardarNiveles(prefs), conf), conf,
        ajustesDisp, cu.shalev.app.acceso.GuardarAccesoClave(cu.shalev.app.acceso.GuardiaAcceso(), ajustesDisp),
    )

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    @Test fun recorridoCompletoGuardaCadaPaso() {
        val vm = vm().also { it.iniciar(ModoWizard.PRIMERA_VEZ) }
        assertEquals(PasoWizard.BIENVENIDA, vm.state.value.paso)
        vm.siguiente()

        // 0.21.0 (C12): tipo de app (principal), objetivo y empleados se guardan en las preferencias.
        assertEquals(PasoWizard.TIPO_APP, vm.state.value.paso)
        vm.siguiente()
        assertFalse(ajustesDisp.estado.value.eligioSecundaria) // 0.27.0 (T9): eligió principal
        // 0.27.0 (T11): acceso con clave, opcional (se puede saltar).
        assertEquals(PasoWizard.ACCESO_CLAVE, vm.state.value.paso)
        assertTrue(vm.state.value.pasoOmitible)
        vm.guardarAccesoClave(true)
        assertTrue(ajustesDisp.estado.value.accesoConClave)
        assertTrue(vm.state.value.accesoClave)
        vm.siguiente()
        assertEquals(PasoWizard.OBJETIVO, vm.state.value.paso)
        vm.alternarModulo(Modulo.VENTAS); vm.alternarModulo(Modulo.INVENTARIO) // desmarca ambos
        vm.alternarModulo(Modulo.SERVICIOS)
        assertTrue(vm.state.value.modulos.isEmpty()); assertFalse(vm.state.value.puedeSeguir)
        vm.siguiente()
        assertEquals(PasoWizard.OBJETIVO, vm.state.value.paso) // sin nada marcado no avanza
        vm.alternarModulo(Modulo.VENTAS)
        assertEquals(setOf(Modulo.VENTAS, Modulo.INVENTARIO), vm.state.value.modulos) // Ventas arrastra Inventario
        vm.siguiente()
        assertEquals(setOf(Modulo.VENTAS, Modulo.INVENTARIO), prefs.state.value.modulos)
        assertEquals(PasoWizard.EMPLEADOS, vm.state.value.paso)
        assertEquals(5, vm.state.value.licencia?.secundariasPermitidas) // C4: la prueba cubre 5 secundarias
        vm.cambiarEmpleados(3); vm.cambiarEmpleados(99)
        assertEquals(5, vm.state.value.empleados) // nunca pasa de la cuota de la licencia
        vm.cambiarEmpleados(2)
        vm.siguiente()
        assertEquals(2, prefs.state.value.empleadosPrevistos)
        assertEquals(PasoWizard.DATOS, vm.state.value.paso)

        // Datos: inválido no avanza; válido se guarda en el Perfil.
        vm.editarDatos { it.copy(nombre = "Ana", telefono = "12") }
        vm.siguiente()
        assertEquals(PasoWizard.DATOS, vm.state.value.paso)
        assertTrue(vm.state.value.mostrarErroresDatos)
        vm.editarDatos { it.copy(telefono = "52345678") }
        vm.siguiente()
        assertEquals("Ana", perfiles.state.value.nombre)
        assertEquals(PasoWizard.ALERTAS, vm.state.value.paso)

        // Alertas: crítico > bajo no avanza; valores válidos se guardan y marcan el paso.
        vm.editarNivel(CampoNivel.PRODUCTO_CRITICO, "9")
        vm.siguiente()
        assertEquals(PasoWizard.ALERTAS, vm.state.value.paso)
        vm.restablecerNiveles()
        vm.sumarNivel(CampoNivel.PRODUCTO_BAJO, 5)
        vm.siguiente()
        assertEquals(10L, prefs.state.value.niveles.productoBajo)
        assertTrue(PasoConfiguracion.ALERTAS in conf.state.value.confirmados)

        // Prueba → fin.
        assertEquals(PasoWizard.PRUEBA, vm.state.value.paso)
        vm.siguiente()
        assertTrue(PasoConfiguracion.PRUEBA in conf.state.value.confirmados)
        assertTrue(prefs.state.value.onboardingCompletado)
    }

    @Test fun saltarTodoNoBloqueaYNoGuardaNada() {
        val vm = vm().also { it.iniciar(ModoWizard.PRIMERA_VEZ) }
        vm.omitirTodo()
        assertTrue(prefs.state.value.onboardingCompletado)
        assertTrue(perfiles.state.value.vacio)
        assertTrue(conf.state.value.confirmados.isEmpty())
        assertEquals(NivelesMinimos(), prefs.state.value.niveles) // los valores por defecto siguen aplicando
    }

    @Test fun omitirUnPasoAvanzaSinGuardar() {
        val vm = vm().also { it.iniciar(ModoWizard.RETOMAR, PasoConfiguracion.ALERTAS) }
        assertEquals(listOf(PasoWizard.ALERTAS), vm.state.value.pasos)
        vm.editarNivel(CampoNivel.PRODUCTO_BAJO, "50")
        vm.omitirPaso()
        assertEquals(5L, prefs.state.value.niveles.productoBajo)
    }

    @Test fun retomaEnElPasoGuardadoTrasMatarElProceso() {
        val vm = vm(SavedStateHandle(mapOf("onboarding.indice" to 6))).also { it.iniciar(ModoWizard.PRIMERA_VEZ) }
        assertEquals(PasoWizard.ALERTAS, vm.state.value.paso)
    }

    @Test fun secundariaTerminaElRecorridoYPideVincularse() {
        TourPendiente.consumirVincularSecundaria()
        val vm = vm().also { it.iniciar(ModoWizard.PRIMERA_VEZ) }
        vm.siguiente()
        vm.elegirTipo(true)
        assertEquals("Vincular con el dueño", vm.state.value.textoBotonPrincipal)
        vm.siguiente()
        assertTrue(prefs.state.value.onboardingCompletado)
        assertTrue(TourPendiente.consumirVincularSecundaria())
        assertTrue(ajustesDisp.estado.value.eligioSecundaria) // 0.27.0 (T9)
        assertFalse(TourPendiente.consumirVincularSecundaria()) // se consume una sola vez
        assertTrue(perfiles.state.value.vacio) // la secundaria no rellena datos ni avisos
    }

    @Test fun retomarDesdeAjustesSoloPendientes() {
        conf.state.value = ConfiguracionInicial(setOf(PasoConfiguracion.ALERTAS))
        perfiles.state.value = Perfil(nombre = "Ana")
        val vm = vm().also { it.iniciar(ModoWizard.RETOMAR) }
        assertEquals(listOf(PasoWizard.PRUEBA), vm.state.value.pasos)
    }

    @Test fun primeraVezTrasActivarLicenciaNoRepideLosDatos() {
        // La app arrancó bloqueada: la pantalla de Licencia pidió los datos y solicitar los guardó en el Perfil.
        // Al llegar después el asistente de primera vez, sus datos NO se vuelven a pedir.
        perfiles.state.value = Perfil(nombre = "Ana", apellidos = "Pérez", ci = "85010112345")
        val vm = vm().also { it.iniciar(ModoWizard.PRIMERA_VEZ) }
        assertFalse("los datos ya dados en Licencia no se piden otra vez", PasoWizard.DATOS in vm.state.value.pasos)
    }
}
