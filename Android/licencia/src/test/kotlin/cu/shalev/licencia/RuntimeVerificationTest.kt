package cu.shalev.licencia

import cu.shalev.licencia.contract.Envelope
import cu.shalev.licencia.contract.GlJson
import cu.shalev.licencia.contract.LicenciaCorta
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.contract.Via
import cu.shalev.licencia.crypto.B64
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checklist §7 del contrato, para la licencia corta cifrada (0.23.0): cada caso altera UNA parte de una licencia por lo
 * demás válida (efímera, texto cifrado, tag, firma, formato) y el resultado debe ser siempre el mismo rechazo genérico.
 * Al final, la licencia larga instalada antes de 0.23.0 sigue re-verificándose en cada evaluación.
 */
class RuntimeVerificationTest {
    private val t0 = Instant.parse("2026-09-30T12:00:00Z")
    private val gl = FakeGl()
    private val clock = MutableClock(t0)
    private val store = InMemoryStore()
    private val device = InMemoryDeviceKey()
    private val deviceId = "SPVI:3f2a9c0d1e4b5a67"

    private fun manager() = LicenseManager(store, device, deviceId, gl.ecdh.public.encoded, listOf(gl.signing.public.encoded), clock)

    private fun input() = SolicitudInput("María", "Pérez González", "85010112345", "52345678", TipoLicencia.MENSUAL, Via.WHATSAPP)

    private fun issued(m: LicenseManager = manager()) = gl.issue(gl.openRequest(m.buildRequest(input()).texto), t0)

    /** Cambia un bit del byte [index] de la parte binaria del código. */
    private fun flip(codigo: String, index: Int): String {
        val b = Base64.getUrlDecoder().decode(codigo.removePrefix(LicenciaCorta.PREFIJO))
        b[index] = (b[index].toInt() xor 0x01).toByte()
        return LicenciaCorta.PREFIJO + Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    private fun assertRejected(message: String) = runBlocking {
        assertEquals(ActivationResult.Rejected, manager().activate(message))
        assertNull("nada se persiste", store.lic)
    }

    @Test fun controlLaLicenciaIntactaSeAcepta() = runBlocking {
        val m = manager()
        assertTrue(m.activate(issued(m).message) is ActivationResult.Accepted)
    }

    @Test fun rechazaEfimeraAlterada() = assertRejected(flip(issued().codigo, 5))

    @Test fun rechazaPrefijoDeEfimeraInvalido() = assertRejected(flip(issued().codigo, 0)) // 02/03 → 03/02: otro punto

    @Test fun rechazaTextoCifradoAlterado() = assertRejected(flip(issued().codigo, LicenciaCorta.LARGO_EPK + 3))

    @Test fun rechazaTagAlterado() = assertRejected(flip(issued().codigo, LicenciaCorta.LARGO_EPK + LicenciaCorta.LARGO_CUERPO + 2))

    @Test fun rechazaFirmaAlterada() = assertRejected(flip(issued().codigo, LicenciaCorta.LARGO_BINARIO - 10))

    @Test fun rechazaFirmaDeOtraClave() = runBlocking {
        val m = manager()
        val req = gl.openRequest(m.buildRequest(input()).texto)
        assertRejected(gl.issue(req, t0, signWith = cu.shalev.licencia.crypto.EcP256.generate()).message)
    }

    @Test fun rechazaCodigoIncompletoComoNoEncontrado() = runBlocking {
        val c = issued().codigo
        assertEquals(ActivationResult.NotFound, manager().activate(c.dropLast(3)))
        assertEquals(ActivationResult.NotFound, manager().activate("SPVI1:" + c.removePrefix("SPVI2:"))) // la de 0.22.0
    }

    @Test fun rechazaDeviceIdQueNoCoincide() = runBlocking {
        val m = manager()
        val req = gl.openRequest(m.buildRequest(input()).texto)
        // Cifrada hacia nuestra clave, pero la huella es de otro ANDROID_ID.
        assertEquals(ActivationResult.Rejected, m.activate(gl.issue(req, t0, deviceIdOverride = "SPVI:0000000000000000").message))
    }

    @Test fun seGuardanIdVenceEnTipoYDeviceId() = runBlocking {
        val m = manager()
        val iss = issued(m)
        m.activate(iss.message)
        val s = store.lic!!
        assertEquals(iss.codigo, s.envelopeJson) // se guarda el código canónico, sin el texto de arriba
        assertEquals(iss.id, s.licenseId)
        assertEquals(TipoLicencia.MENSUAL, s.tipo)
        assertEquals(t0.plusSeconds(30L * 86_400).toString(), s.venceEn)
        assertEquals(deviceId, s.deviceId)
        assertEquals(iss.id, m.evaluate().license!!.id)
    }

    @Test fun metadatosGuardadosAlteradosInvalidanLaLicencia() = runBlocking {
        val m = manager()
        m.activate(issued(m).message)
        // Alguien edita la caché para convertir la mensual en perpetua: el código no coincide → se ignora.
        store.lic = store.lic!!.copy(tipo = TipoLicencia.PERPETUA)
        val ev = m.evaluate()
        assertNull(ev.license)
        assertTrue(ev.state is LicenseState.Trial)
    }

    @Test fun licenciaGuardadaSeReverificaEnCadaEvaluacion() = runBlocking {
        val m = manager()
        val iss = issued(m)
        m.activate(iss.message)
        store.lic = store.lic!!.copy(envelopeJson = flip(iss.codigo, LicenciaCorta.LARGO_EPK + 1))
        assertNull(m.evaluate().license)
    }

    @Test fun licenciaGuardadaNoSirveConOtraClaveDeDispositivo() = runBlocking {
        val m = manager()
        m.activate(issued(m).message)
        // Mismo almacén copiado a otro teléfono (otra clave en el Keystore): no se descifra.
        val otro = LicenseManager(store, InMemoryDeviceKey(), deviceId, gl.ecdh.public.encoded, listOf(gl.signing.public.encoded), clock)
        assertNull(otro.evaluate().license)
    }

    @Test fun largaInstaladaConClavesExtraOTagAlteradoSeDescarta() = runBlocking {
        val m = manager()
        val larga = gl.issueLarga(gl.openRequest(m.buildRequest(input()).texto), t0)
        store.instalarLarga(larga)
        assertEquals(larga.id, m.evaluate().license!!.id)
        val guardada = store.lic!!
        store.lic = guardada.copy(envelopeJson = guardada.envelopeJson.removeSuffix("}") + ",\"extra\":1}")
        assertNull(m.evaluate().license)
        val env = GlJson.decoder.decodeFromString(Envelope.serializer(), guardada.envelopeJson)
        val tag = B64.enc(B64.dec(env.tag).also { it[0] = (it[0].toInt() xor 1).toByte() })
        store.lic = guardada.copy(envelopeJson = GlJson.encoder.encodeToString(Envelope.serializer(), env.copy(tag = tag)))
        assertNull(m.evaluate().license)
    }
}
