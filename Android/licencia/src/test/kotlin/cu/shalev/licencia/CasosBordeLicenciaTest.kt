package cu.shalev.licencia

import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.contract.Via
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Casos borde de licencia del Prompt 15: licencia cifrada hacia otra clave, perpetua a muy largo plazo y vencida → renovada.
 * Todo en memoria y sin red (FakeGl emite con claves generadas en el test).
 */
class CasosBordeLicenciaTest {
    private val t0 = Instant.parse("2026-09-30T12:00:00Z")
    private val gl = FakeGl()
    private val clock = MutableClock(t0)
    private val store = InMemoryStore()
    private val device = InMemoryDeviceKey()
    private val deviceId = "SPVI:3f2a9c0d1e4b5a67"

    private fun manager() = LicenseManager(store, device, deviceId, gl.ecdh.public.encoded, listOf(gl.signing.public.encoded), clock)
    private fun input(tipo: TipoLicencia) = SolicitudInput("María", "Pérez González", "85010112345", "5234 5678", tipo, Via.WHATSAPP)

    @Test fun licenciaCifradaHaciaOtraClaveNoSirveAquiAunqueLaHuellaSeaLaNuestra() = runBlocking {
        val m = manager()
        assertTrue(m.state() is LicenseState.Trial)
        // Una solicitud manipulada con la devicePub de otro: GL cifra hacia esa clave y este teléfono no la descifra.
        val req = gl.openRequest(m.buildRequest(input(TipoLicencia.ANUAL)).texto)
        val otra = cu.shalev.licencia.crypto.EcP256.generate().public as java.security.interfaces.ECPublicKey
        assertEquals(ActivationResult.Rejected, m.activate(gl.issue(req, t0, cifrarPara = otra).message))
        assertNull("no se persiste nada", store.lic)
        assertTrue("sigue en prueba", m.state() is LicenseState.Trial)
    }

    @Test fun laSolicitudDeShalevSiempreLlevaSuClavePublicaComprimida() {
        repeat(3) { assertEquals(33, cu.shalev.licencia.crypto.B64.urlDec(gl.openRequest(manager().buildRequest(input(TipoLicencia.MENSUAL)).texto).devicePub).size) }
    }

    @Test fun perpetuaSigueDesbloqueadaVeinteAnosDespues() = runBlocking {
        val m = manager()
        m.activate(gl.issue(gl.openRequest(m.buildRequest(input(TipoLicencia.PERPETUA)).texto), t0).message)
        clock.t = t0.plus(Duration.ofDays(365L * 20))
        val s = m.state()
        assertTrue(s is LicenseState.Perpetual)
        assertTrue(s.unlocked)
    }

    @Test fun mensualVencidaBloqueaYUnaRenovacionLaReactiva() = runBlocking {
        val m = manager()
        m.activate(gl.issue(gl.openRequest(m.buildRequest(input(TipoLicencia.MENSUAL)).texto), t0).message)
        clock.plusDays(31)
        val vencida = m.state()
        assertEquals(LicenseState.Expired(TipoLicencia.MENSUAL), vencida)
        assertFalse(vencida.unlocked)
        // Renovación emitida hoy: vuelve a quedar activa sin reinstalar nada.
        val renovada = m.activate(gl.issue(gl.openRequest(m.buildRequest(input(TipoLicencia.MENSUAL)).texto), clock.t).message)
        assertTrue(renovada is ActivationResult.Accepted)
        assertTrue(m.state() is LicenseState.Active)
    }
}
