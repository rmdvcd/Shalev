package cu.shalev.licencia

import cu.shalev.licencia.contract.CodigoLicencia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Código de licencia del contrato GL: 16 caracteres `[0-9A-Z]` en 4 grupos de 4 (`XXXX-XXXX-XXXX-XXXX`).
 * `SISTEMA_LICENCIAS_CLIENTES.md` §2.1–§2.2.
 */
class CodigoLicenciaTest {

    @Test fun seNormalizaIgnorandoGuionesEspaciosYMayusculas() {
        assertEquals("4K7N9X2MB8PQ3T5V", CodigoLicencia.normalizar("4K7N-9X2M-B8PQ-3T5V"))
        assertEquals("4K7N9X2MB8PQ3T5V", CodigoLicencia.normalizar(" 4k7n 9x2m b8pq 3t5v "))
        assertEquals("4K7N9X2MB8PQ3T5V", CodigoLicencia.normalizar("4K7N9X2MB8PQ3T5V"))
    }

    @Test fun seRechazaLoQueNoEsUnCodigoDelContrato() {
        assertNull(CodigoLicencia.normalizar("4K7N-9X2M-B8PQ-3T5")) // 15
        assertNull(CodigoLicencia.normalizar("4K7N-9X2M-B8PQ-3T5VV")) // 17
        assertNull(CodigoLicencia.normalizar("4K7N-9X2M-B8PQ-3T5!")) // fuera del alfabeto
        assertNull(CodigoLicencia.normalizar("")) // vacío
        assertFalse(CodigoLicencia.valido("SPVI2:ABC"))
    }

    @Test fun laClaveMaestraSoloValeEnDebug() {
        assertTrue(CodigoLicencia.esMaestra("GLMK-PERM-MAXS-9999", esDebug = true))
        assertTrue(CodigoLicencia.esMaestra("glmk permmaxs 9999", esDebug = true))
        assertTrue(CodigoLicencia.esMaestra("GLMKPERMMAXS9999", esDebug = true))
        // Mitigación 1 (Opción A): en release la comprobación devuelve siempre false.
        assertFalse(CodigoLicencia.esMaestra("GLMK-PERM-MAXS-9999", esDebug = false))
        assertFalse(CodigoLicencia.esMaestra("4K7N-9X2M-B8PQ-3T5V", esDebug = true))
    }

    @Test fun elModoDesarrolloArrancaApagado() {
        // Fail-closed: si el arranque de la app no lo activa, la clave maestra no vale.
        assertFalse(ModoDesarrollo.esDebug)
    }
}
