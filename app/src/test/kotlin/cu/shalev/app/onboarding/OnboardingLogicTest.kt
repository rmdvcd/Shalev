package cu.shalev.app.onboarding

import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.DatosIniciales
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.model.ResumenConfiguracion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingLogicTest {
    private val todos = PasoConfiguracion.entries.toList()

    // ---------- Plan ----------

    @Test fun primeraVezSinNadaHechoEmpiezaConBienvenidaYMuestraTodo() {
        // Instalación limpia: nada está hecho, así que pendientes = todos los pasos aplicables.
        val plan = planWizard(ModoWizard.PRIMERA_VEZ, ResumenConfiguracion(todos, todos))
        // 0.21.0 (C12): tras la bienvenida, el recorrido (tipo de app, objetivo, empleados).
        assertEquals(
            listOf(PasoWizard.BIENVENIDA) + PASOS_TOUR +
                listOf(PasoWizard.DATOS, PasoWizard.ALERTAS, PasoWizard.PRUEBA),
            plan,
        )
    }

    @Test fun primeraVezNoRepiteLoYaHecho() {
        // Quien activó su licencia antes del asistente ya dio sus datos en la pantalla de Licencia (guardados en
        // el Perfil al solicitar): el asistente NO los vuelve a pedir; solo muestra lo que falta.
        val plan = planWizard(ModoWizard.PRIMERA_VEZ, ResumenConfiguracion(todos, listOf(PasoConfiguracion.ALERTAS)))
        assertEquals(listOf(PasoWizard.BIENVENIDA) + PASOS_TOUR + listOf(PasoWizard.ALERTAS), plan)
        assertTrue("los datos no se piden dos veces", PasoWizard.DATOS !in plan)
    }

    @Test fun retomarMuestraSoloLoPendiente() {
        val plan = planWizard(ModoWizard.RETOMAR, ResumenConfiguracion(todos, listOf(PasoConfiguracion.ALERTAS, PasoConfiguracion.PRUEBA)))
        assertEquals(listOf(PasoWizard.ALERTAS, PasoWizard.PRUEBA), plan)
    }

    @Test fun retomarConTodoHechoPermiteRevisar() {
        assertEquals(3, planWizard(ModoWizard.RETOMAR, ResumenConfiguracion(todos, emptyList())).size)
    }

    @Test fun accesoDirectoAUnPaso() {
        assertEquals(listOf(PasoWizard.ALERTAS), planWizard(ModoWizard.RETOMAR, ResumenConfiguracion(todos, todos), PasoConfiguracion.ALERTAS))
    }

    // ---------- Datos ----------

    @Test fun datosVaciosSonValidos() {
        assertTrue(erroresDatos(DatosIniciales()).isEmpty())
    }

    @Test fun seMarcanTodosLosCamposInvalidosALaVez() {
        val e = erroresDatos(DatosIniciales(nombre = "A1", apellidos = "Pérez", ci = "1", telefono = "12"))
        assertEquals(setOf("nombre", "ci", "telefono"), e.keys)
    }

    // ---------- Niveles ----------

    @Test fun nivelesRecomendados5y1() {
        val n = NivelesForm.RECOMENDADOS.aNiveles()!!
        assertEquals(NivelesMinimos(), n)
        assertEquals("5", NivelesForm.RECOMENDADOS.productoBajo)
        assertEquals("1", NivelesForm.RECOMENDADOS.insumoCritico)
    }

    @Test fun insumosAdmitenDecimalesYProductosNo() {
        val f = NivelesForm(productoBajo = "2.5", insumoBajo = "2.5", insumoCritico = "0.25")
        assertTrue(CampoNivel.PRODUCTO_BAJO in f.errores())
        val ok = f.copy(productoBajo = "3").aNiveles()!!
        assertEquals(Cantidad(2500), ok.insumoBajo)
        assertEquals(Cantidad(250), ok.insumoCritico)
    }

    @Test fun criticoMayorQueBajoSeMarcaEnCritico() {
        val e = NivelesForm(productoBajo = "2", productoCritico = "3").errores()
        assertEquals(NivelesForm.CRITICO_MAYOR, e[CampoNivel.PRODUCTO_CRITICO])
        assertNull(NivelesForm(productoBajo = "2", productoCritico = "3").aNiveles())
    }

    @Test fun vacioNegativoOEnormeNoValen() {
        listOf("", "-1", "abc", "9999999").forEach { v ->
            assertTrue(v, CampoNivel.PRODUCTO_BAJO in NivelesForm(productoBajo = v).errores())
        }
    }

    @Test fun botonesMasYMenosNuncaBajanDeCero() {
        val f = NivelesForm(productoCritico = "0").sumar(CampoNivel.PRODUCTO_CRITICO, -1)
        assertEquals("0", f.productoCritico)
        assertEquals("6", NivelesForm().sumar(CampoNivel.PRODUCTO_BAJO, 1).productoBajo)
        assertEquals("2.5", NivelesForm(insumoBajo = "1.5").sumar(CampoNivel.INSUMO_BAJO, 1).insumoBajo)
        assertEquals("1", NivelesForm(productoBajo = "basura").sumar(CampoNivel.PRODUCTO_BAJO, 1).productoBajo)
    }

    @Test fun formDesdeNivelesGuardadosSinSeparadorDeMiles() {
        val f = NivelesForm.desde(NivelesMinimos(insumoBajo = Cantidad.enteras(1500)))
        assertEquals("1500", f.insumoBajo)
        assertEquals(Cantidad.enteras(1500), f.aNiveles()!!.insumoBajo)
    }

    // ---------- Estado de pantalla ----------

    private val plan = listOf(PasoWizard.BIENVENIDA, PasoWizard.DATOS, PasoWizard.ALERTAS, PasoWizard.PRUEBA)

    @Test fun numeracionNoCuentaLaBienvenida() {
        val s = OnboardingUiState(cargando = false, pasos = plan, indice = 1)
        assertEquals(1, s.numeroPaso); assertEquals(3, s.totalPasos)
    }

    @Test fun textosDelBotonPrincipal() {
        val base = OnboardingUiState(cargando = false, pasos = plan)
        assertEquals("Comenzar", base.textoBotonPrincipal)
        assertEquals("Siguiente", base.copy(indice = 1).textoBotonPrincipal)
        assertEquals("Empezar a usar Shalev", base.copy(indice = 3).textoBotonPrincipal)
        assertEquals("Terminar", base.copy(indice = 3, modo = ModoWizard.RETOMAR).textoBotonPrincipal)
    }

    @Test fun soloLosPasosConDatosSeOmiten() {
        val base = OnboardingUiState(cargando = false, pasos = plan)
        assertFalse(base.pasoOmitible)                // bienvenida
        assertTrue(base.copy(indice = 1).pasoOmitible) // datos
        assertFalse(base.copy(indice = 3).pasoOmitible) // prueba (informativa)
    }
}
