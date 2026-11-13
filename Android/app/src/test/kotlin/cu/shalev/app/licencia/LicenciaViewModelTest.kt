package cu.shalev.app.licencia

import cu.shalev.app.LicRepo
import cu.shalev.app.PerfilRepo
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.LicenciaInstalada
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.usecase.ActivarLicencia
import cu.shalev.domain.usecase.SolicitarLicencia
import cu.shalev.licencia.ActivationResult
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.contract.Via
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Panel de Licencia (también es la pantalla de bloqueo): sincronía con Perfil, solicitud y activación. */
@OptIn(ExperimentalCoroutinesApi::class)
class LicenciaViewModelTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private val t0 = Instant.parse("2026-09-30T16:00:00Z")
    private val completo = Perfil(nombre = "María", apellidos = "Pérez González", ci = "85010112345", telefonos = listOf(Telefono(1, "+5352345678")))
    private val eventos = mutableListOf<LicenciaEvento>()

    private fun TestScope.vm(lic: LicRepo, perfil: PerfilRepo): LicenciaViewModel {
        val vm = LicenciaViewModel(lic, perfil, SolicitarLicencia(lic, perfil), ActivarLicencia(lic), cu.shalev.app.PrefRepo())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { eventos += it } }
        return vm
    }

    @Test fun alAbrirSeReevaluaYSeCopiaElPerfilCompletoComoResumen() = runTest {
        val lic = LicRepo()
        val vm = vm(lic, PerfilRepo(completo))
        assertEquals(1, lic.refrescos)
        val f = vm.state.value.form
        assertEquals("María", f.nombre)
        assertEquals("+5352345678", f.telefono)
        assertFalse(f.editando) // datos válidos: resumen con «Editar»
        assertEquals(listOf("+5352345678"), vm.state.value.telefonosPerfil)
        assertFalse(vm.state.value.perfilVacio)
    }

    @Test fun perfilVacioAbreElFormularioYSinDatosNoSeSolicita() = runTest {
        val lic = LicRepo()
        val vm = vm(lic, PerfilRepo())
        assertTrue(vm.state.value.form.editando)
        assertTrue(vm.state.value.perfilVacio)
        vm.solicitarLicencia()
        assertTrue(vm.state.value.form.mostrarErrores)
        assertTrue(lic.solicitudes.isEmpty())
        assertTrue(eventos.isEmpty())
    }

    @Test fun solicitarEnviaPorLaViaElegidaYGuardaLosDatosEnElPerfil() = runTest {
        val lic = LicRepo().apply { respuestaSolicitud = AppResult.Ok(cu.shalev.licencia.SolicitudGenerada("Solicitud de licencia Shalev\n\nSPVIR1:abc", "SPVIR1:abc")) }
        val perfil = PerfilRepo()
        val vm = vm(lic, perfil)
        vm.editar { it.copy(nombre = "Ana", apellidos = "Díaz", ci = "85010112345", telefono = "5234 5678", via = Via.SMS, tipo = TipoLicencia.ANUAL) }
        vm.solicitarLicencia()
        assertEquals(LicenciaEvento.Enviar(Via.SMS, "Solicitud de licencia Shalev\n\nSPVIR1:abc"), eventos.single())
        assertEquals(TipoLicencia.ANUAL, lic.solicitudes.single().tipo)
        val p = perfil.perfil.first()
        assertEquals("Ana", p.nombre)
        assertEquals("85010112345", p.ci)
        assertFalse(vm.state.value.ocupado)
        assertFalse(vm.state.value.form.editando)
    }

    @Test fun errorAlSolicitarSeExplicaSinDetallesInternos() = runTest {
        val lic = LicRepo().apply { respuestaSolicitud = AppResult.Err(AppError.Almacenamiento) }
        val vm = vm(lic, PerfilRepo(completo))
        vm.solicitarLicencia()
        assertEquals(LicenciaEvento.Mensaje("No se pudo generar la solicitud"), eventos.single())
        lic.respuestaSolicitud = AppResult.Err(AppError.Validacion("ci", AppError.Regla.FORMATO))
        vm.solicitarLicencia()
        assertEquals(LicenciaEvento.Mensaje("Revisa los datos del formulario"), eventos.last())
    }

    @Test fun activarDesbloqueaLimpiaElTextoYReevalua() = runTest {
        val lic = LicRepo(LicenseState.Expired(TipoLicencia.MENSUAL))
        val vm = vm(lic, PerfilRepo(completo))
        vm.activarLicencia() // texto vacío: no se llama al repositorio
        assertTrue(lic.activaciones.isEmpty())
        val activa = LicenseState.Active(TipoLicencia.MENSUAL, t0.plusSeconds(30L * 86_400), "lic-1")
        lic.respuestaActivar = ActivationResult.Accepted(activa)
        val antes = lic.refrescos
        vm.editar { it.copy(mensajeLicencia = "Licencia Shalev lic-1 {…}") }
        vm.activarLicencia()
        assertEquals(listOf("Licencia Shalev lic-1 {…}"), lic.activaciones)
        assertEquals(LicenciaEvento.Mensaje("Licencia activada"), eventos.single())
        assertEquals("", vm.state.value.form.mensajeLicencia)
        assertEquals(activa, vm.state.value.licencia!!.estado)
        assertTrue(lic.refrescos > antes)
    }

    @Test fun licenciaRechazadaUOtraMasNuevaNoCambiaElEstado() = runTest {
        val lic = LicRepo(LicenseState.TrialExpired)
        val vm = vm(lic, PerfilRepo(completo))
        lic.respuestaActivar = ActivationResult.Rejected
        vm.editar { it.copy(mensajeLicencia = "texto") }; vm.activarLicencia()
        assertEquals(LicenciaEvento.Mensaje("La licencia no es válida para este dispositivo"), eventos.last())
        lic.respuestaActivar = ActivationResult.Outdated
        vm.editar { it.copy(mensajeLicencia = "texto") }; vm.activarLicencia()
        assertEquals(LicenciaEvento.Mensaje("Ya tienes instalada una licencia más reciente"), eventos.last())
        assertEquals(LicenseState.TrialExpired, vm.state.value.licencia!!.estado)
    }

    @Test fun alRenovarSePreseleccionaElTipoVigenteSalvoQueElUsuarioElijaOtro() = runTest {
        val lic = LicRepo()
        val instalada = LicenciaInstalada("lic-1", TipoLicencia.SEMESTRAL, t0, t0.plusSeconds(180L * 86_400))
        lic.flow.value = lic.flow.value!!.copy(estado = LicenseState.Active(TipoLicencia.SEMESTRAL, instalada.venceEn!!, "lic-1"), instalada = instalada)
        val vm = vm(lic, PerfilRepo(completo))
        assertEquals(TipoLicencia.SEMESTRAL, vm.state.value.form.tipo)
        vm.editar { it.copy(tipo = TipoLicencia.PERPETUA) }
        lic.flow.value = lic.flow.value!!.copy(huellaEmisor = "cd") // otra re-evaluación
        assertEquals(TipoLicencia.PERPETUA, vm.state.value.form.tipo)
    }

    @Test fun cambiosDelPerfilNoPisanLoQueSeEstaEscribiendo() = runTest {
        val perfil = PerfilRepo(completo)
        val vm = vm(LicRepo(), perfil)
        vm.abrirEdicion()
        vm.editar { it.copy(nombre = "Mar") }
        perfil.state.value = completo.copy(nombre = "Otra")
        assertEquals("Mar", vm.state.value.form.nombre)
        vm.editar { it.copy(nombre = "María") }
        vm.cerrarEdicion()
        perfil.state.value = completo.copy(nombre = "Lucía")
        assertEquals("Lucía", vm.state.value.form.nombre) // sin edición abierta, el Perfil manda
    }

    // ---------- 0.22.0 ----------

    private fun LicRepo.conInstalada(tipo: TipoLicencia, secundarias: Int): LicenciaInstalada {
        val i = LicenciaInstalada("lic-1", tipo, t0, t0.plusSeconds(180L * 86_400), secundarias)
        flow.value = flow.value!!.copy(estado = LicenseState.Active(tipo, i.venceEn!!, "lic-1", secundarias), instalada = i)
        return i
    }

    @Test fun renovarIgualRepiteTipoYSecundariasDeLaInstalada() = runTest {
        val lic = LicRepo().apply { conInstalada(TipoLicencia.SEMESTRAL, 3) }
        val vm = vm(lic, PerfilRepo(completo))
        vm.editar { it.copy(tipo = TipoLicencia.ANUAL).conSecundarias(0) }
        assertTrue(vm.renovarIgual())
        assertEquals(TipoLicencia.SEMESTRAL, vm.state.value.form.tipo)
        assertEquals(3, vm.state.value.form.secundarias)
        assertFalse(vm.state.value.form.mostrarErrores)
    }

    @Test fun renovarIgualSinDatosValidosLlevaADatosConLosErrores() = runTest {
        val lic = LicRepo().apply { conInstalada(TipoLicencia.MENSUAL, 2) }
        val vm = vm(lic, PerfilRepo())
        assertFalse(vm.renovarIgual())
        assertTrue(vm.state.value.form.mostrarErrores)
        assertTrue(vm.state.value.form.editando)
        assertEquals(2, vm.state.value.form.secundarias)
        // Sin licencia instalada no hay nada que repetir.
        assertFalse(vm(LicRepo(), PerfilRepo(completo)).renovarIgual())
    }

    @Test fun soloSePegaElMensajeElCodigoBastaAunqueVengaSolo() = runTest {
        val lic = LicRepo(LicenseState.TrialExpired)
        val vm = vm(lic, PerfilRepo(completo))
        val activa = LicenseState.Active(TipoLicencia.MENSUAL, t0.plusSeconds(30L * 86_400), "lic-1", 2)
        lic.respuestaActivar = ActivationResult.Accepted(activa)
        vm.editar { it.copy(mensajeLicencia = "SPVI2:abc") }
        vm.activarLicencia()
        assertEquals(listOf("SPVI2:abc"), lic.activaciones)
        assertEquals(LicenciaEvento.Mensaje("Licencia activada"), eventos.last())
        assertTrue(TextosActivacion.INSTRUCCION.contains("mensaje de activación"))
        assertFalse(TextosActivacion.INSTRUCCION.contains("SPVI2:"))
        assertFalse(TextosActivacion.INSTRUCCION.contains("QR"))
    }
}
