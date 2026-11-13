package cu.shalev.app.licencia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P18 (A08): reglas del asistente de Licencia (solo presentación). */
class LicenciaPasosTest {
    private val valido = LicenciaForm(nombre = "María", apellidos = "Pérez", ci = "85010112345", telefono = "52345678")

    @Test fun empiezaSinRepetirLosDatosYaDados() {
        // Sin datos (o incompletos): se piden. Con los datos ya completos (del Perfil: asistente inicial o una
        // solicitud anterior), no se vuelven a pedir: se entra en «Pide la licencia».
        assertEquals(PasoLicencia.DATOS, pasoInicialLicencia(LicenciaForm()))
        assertEquals(PasoLicencia.DATOS, pasoInicialLicencia(valido.copy(ci = "")))
        assertEquals(PasoLicencia.SOLICITAR, pasoInicialLicencia(valido))
        assertEquals(PasoLicencia.ACTIVAR, pasoInicialLicencia(valido.copy(mensajeLicencia = "SPVI-LIC…")))
    }

    @Test fun ordenDeLosPasos() {
        assertNull(PasoLicencia.DATOS.anterior)
        assertEquals(PasoLicencia.SOLICITAR, PasoLicencia.DATOS.siguiente)
        assertEquals(PasoLicencia.ACTIVAR, PasoLicencia.SOLICITAR.siguiente)
        assertNull(PasoLicencia.ACTIVAR.siguiente)
    }

    @Test fun soloSeAvanzaDesdeDatosConDatosValidos() {
        assertTrue(puedeAvanzar(PasoLicencia.DATOS, valido))
        assertFalse(puedeAvanzar(PasoLicencia.DATOS, valido.copy(ci = "1")))
        assertTrue(puedeAvanzar(PasoLicencia.SOLICITAR, LicenciaForm()))
        assertFalse("Activar es el último paso", puedeAvanzar(PasoLicencia.ACTIVAR, valido))
    }
}
