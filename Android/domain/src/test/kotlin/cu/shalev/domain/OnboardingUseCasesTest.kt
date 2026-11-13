package cu.shalev.domain

import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.ConfiguracionInicial
import cu.shalev.domain.model.DatosIniciales
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.PlanConfiguracion
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.usecase.GuardarAlertasIniciales
import cu.shalev.domain.usecase.GuardarDatosIniciales
import cu.shalev.domain.usecase.GuardarNiveles
import cu.shalev.domain.usecase.GuardarPerfil
import cu.shalev.domain.usecase.ObservarResumenConfiguracion
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingUseCasesTest {

    private fun lic(estado: LicenseState = LicenseState.Trial(7), huella: String? = null) =
        Licencia(estado, "SPVI:x", huella != null, huella)

    // ---------- Plan ----------

    @Test fun primeraVezTodoPendiente() {
        val r = PlanConfiguracion.resumen(Perfil(), lic(), ConfiguracionInicial())
        assertEquals(PasoConfiguracion.entries.toList(), r.pasos)
        assertEquals(r.pasos, r.pendientes)
        assertFalse(r.completa)
    }

    @Test fun nombreAntiguoDeLaClaveNoEsUnPaso() {
        assertEquals(listOf("DATOS", "ALERTAS", "PRUEBA"), PasoConfiguracion.entries.map { it.name })
    }

    @Test fun conLicenciaNoSeExplicaLaPrueba() {
        val activa = lic(LicenseState.Active(TipoLicencia.ANUAL, Instant.parse("2027-01-01T00:00:00Z"), "id"))
        assertFalse(PasoConfiguracion.PRUEBA in PlanConfiguracion.pasos(activa))
    }

    @Test fun losPasosSeDeducenDeLosDatosReales() {
        val r = PlanConfiguracion.resumen(
            Perfil(nombre = "Ana"), lic(huella = "ab"),
            ConfiguracionInicial(setOf(PasoConfiguracion.ALERTAS, PasoConfiguracion.PRUEBA)),
        )
        assertTrue(r.completa)
    }

    @Test fun resumenObservable() = runBlocking<Unit> {
        val licencia = FakeLicencia().also { it.refrescar() }
        val conf = FakeConfiguracionInicial()
        val uc = ObservarResumenConfiguracion(FakePerfil(), licencia, conf)
        assertEquals(3, uc().first().pendientes.size)
        conf.confirmar(PasoConfiguracion.ALERTAS)
        assertFalse(PasoConfiguracion.ALERTAS in uc().first().pendientes)
    }

    // ---------- Datos ----------

    @Test fun datosVaciosNoSonError() = runBlocking<Unit> {
        val perfil = FakePerfil()
        assertTrue(GuardarDatosIniciales(perfil, GuardarPerfil(perfil))(DatosIniciales()) is AppResult.Ok)
        assertTrue(perfil.state.value.vacio)
    }

    @Test fun datosParcialesSeGuardanYElTelefonoSeAntepone() = runBlocking<Unit> {
        val perfil = FakePerfil(Perfil(telefonos = listOf(Telefono(1, "+5353334444"))))
        val r = GuardarDatosIniciales(perfil, GuardarPerfil(perfil))(DatosIniciales(nombre = " Ana ", telefono = "5234 5678"))
        assertTrue(r is AppResult.Ok)
        val p = perfil.state.value
        assertEquals("Ana", p.nombre)
        assertEquals(2, p.telefonos.size)
        assertEquals(p.telefonos.first().numero, cu.shalev.core.validation.Phone.normalize("52345678"))
    }

    @Test fun campoEscritoConFormatoInvalidoSeRechaza() = runBlocking<Unit> {
        val perfil = FakePerfil()
        val r = GuardarDatosIniciales(perfil, GuardarPerfil(perfil))(DatosIniciales(nombre = "Ana", telefono = "12"))
        assertEquals("telefono", ((r as AppResult.Err).error as AppError.Validacion).campo)
        assertTrue(perfil.state.value.vacio)
    }

    // ---------- Alertas ----------

    @Test fun alertasPorDefectoSon5y1() {
        val n = NivelesMinimos()
        assertEquals(5L, n.productoBajo); assertEquals(1L, n.productoCritico)
        assertEquals(Cantidad.enteras(5), n.insumoBajo); assertEquals(Cantidad.enteras(1), n.insumoCritico)
    }

    @Test fun alertasValidasSeGuardanYMarcanElPaso() = runBlocking<Unit> {
        val prefs = FakePreferencias(); val conf = FakeConfiguracionInicial()
        val r = GuardarAlertasIniciales(GuardarNiveles(prefs), conf)(NivelesMinimos(productoBajo = 10, productoCritico = 2))
        assertTrue(r is AppResult.Ok)
        assertEquals(10L, prefs.state.value.niveles.productoBajo)
        assertTrue(PasoConfiguracion.ALERTAS in conf.state.value.confirmados)
    }

    @Test fun criticoMayorQueBajoNoSeGuardaNiMarca() = runBlocking<Unit> {
        val prefs = FakePreferencias(); val conf = FakeConfiguracionInicial()
        val r = GuardarAlertasIniciales(GuardarNiveles(prefs), conf)(NivelesMinimos(productoBajo = 1, productoCritico = 5))
        assertTrue(r is AppResult.Err)
        assertFalse(PasoConfiguracion.ALERTAS in conf.state.value.confirmados)
    }
}
