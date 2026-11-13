package cu.shalev.data.licencia

import cu.shalev.licencia.StoredLicense
import cu.shalev.licencia.contract.TipoLicencia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Serialización de lo que se persiste para la licencia (sin Android: el cifrado lo cubre KeystoreAead en androidTest). */
class LicenciaCodecsTest {

    // ---------- DataStoreLicenseStore ----------

    @Test fun licenciaConMetadatosIdaYVuelta() {
        val l = StoredLicense("{env}", "ID-1", "2026-09-30T12:00:00Z", TipoLicencia.ANUAL, "2027-09-30T12:00:00Z", "SPVI:abc")
        assertEquals(l, DataStoreLicenseStore.decodificar(DataStoreLicenseStore.codificar(l)))
    }

    @Test fun formatoAntiguoSinMetadatosSigueLeyendose() {
        val v02 = """{"env":"{env}","id":"ID-1","at":"2026-09-30T12:00:00Z"}"""
        val l = DataStoreLicenseStore.decodificar(v02)!!
        assertEquals("ID-1", l.licenseId)
        assertNull(l.tipo); assertNull(l.venceEn); assertNull(l.deviceId)
    }

    @Test fun tipoDesconocidoOIlegibleEsSinLicencia() {
        assertNull(DataStoreLicenseStore.decodificar("""{"env":"e","id":"i","at":"a","tipo":"VITALICIA"}"""))
        assertNull(DataStoreLicenseStore.decodificar("{"))
    }
}
