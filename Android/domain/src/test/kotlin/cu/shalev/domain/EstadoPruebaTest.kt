package cu.shalev.domain

import cu.shalev.domain.model.InfoRegistroPrueba
import cu.shalev.domain.model.TrialState
import cu.shalev.domain.model.pedirPermisoRegistro
import cu.shalev.domain.model.trialState
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.26.0 (P74): estados de la prueba y cuándo se pide el permiso de fotos. */
class EstadoPruebaTest {
    private val nueva = InfoRegistroPrueba(instalacionNueva = true, primeraInstalacion = true, copiasDanadas = 0)
    private val reinstalada = InfoRegistroPrueba(instalacionNueva = true, primeraInstalacion = false, copiasDanadas = 1)

    @Test fun estados() {
        assertEquals(TrialState.FirstInstall, trialState(LicenseState.Trial(7), nueva))
        assertEquals(TrialState.Active(3), trialState(LicenseState.Trial(3), reinstalada))
        assertEquals(TrialState.Active(3), trialState(LicenseState.Trial(3), null))
        assertEquals(TrialState.Expired, trialState(LicenseState.TrialExpired, reinstalada))
        assertEquals(TrialState.Expired, trialState(LicenseState.Expired(TipoLicencia.MENSUAL), null))
        assertEquals(TrialState.Tampered, trialState(LicenseState.ClockTampered, null))
        assertEquals(TrialState.Active(null), trialState(LicenseState.Perpetual("x"), nueva))
    }

    @Test fun copiaDanadaNoEsManipulacion() {
        // Una copia que no descifra no cambia el estado: sigue la prueba (P74: no bloquea).
        assertEquals(TrialState.Active(5), trialState(LicenseState.Trial(5), reinstalada.copy(copiasDanadas = 3)))
    }

    @Test fun elPermisoSePideUnaVezRecienInstaladaYEnPrueba() {
        assertTrue(pedirPermisoRegistro(LicenseState.Trial(7), nueva, concedido = false, yaPedido = false))
        assertFalse(pedirPermisoRegistro(LicenseState.Trial(7), nueva, concedido = true, yaPedido = false))
        assertFalse(pedirPermisoRegistro(LicenseState.Trial(7), nueva, concedido = false, yaPedido = true))
        assertFalse(pedirPermisoRegistro(LicenseState.Trial(7), nueva.copy(instalacionNueva = false), concedido = false, yaPedido = false))
        assertFalse(pedirPermisoRegistro(LicenseState.Perpetual("x"), nueva, concedido = false, yaPedido = false))
        assertFalse(pedirPermisoRegistro(null, null, concedido = false, yaPedido = false))
    }
}
