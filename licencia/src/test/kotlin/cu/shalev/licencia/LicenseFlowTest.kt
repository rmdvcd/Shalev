package cu.shalev.licencia

import cu.shalev.core.validation.Validators
import cu.shalev.licencia.contract.EstadoLicencia
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.contract.Via
import cu.shalev.licencia.crypto.B64
import cu.shalev.licencia.crypto.EcP256
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class LicenseFlowTest {
    private val t0 = Instant.parse("2026-09-30T12:00:00Z")
    private val gl = FakeGl()
    private val clock = MutableClock(t0)
    private val store = InMemoryStore()
    private val device = InMemoryDeviceKey()
    private val deviceId = "SPVI:3f2a9c0d1e4b5a67"

    private fun manager(signKeys: List<ByteArray> = listOf(gl.signing.public.encoded)) = LicenseManager(
        store, device, deviceId, gl.ecdh.public.encoded, signKeys, clock,
    )

    private fun input(tipo: TipoLicencia = TipoLicencia.MENSUAL, via: Via = Via.WHATSAPP) =
        SolicitudInput("María", "Pérez González", "85010112345", "5234 5678", tipo, via)

    // ---------- Solicitud ----------

    @Test fun laSolicitudLlevaElPayloadV2Completo() {
        val req = gl.openRequest(manager().buildRequest(input()).texto)
        assertEquals(2, req.v)
        assertEquals("María", req.nombre)
        assertEquals("Pérez González", req.apellidos)
        assertEquals(Via.WHATSAPP, req.via)
        assertEquals("+5352345678", req.telefono)
        assertEquals(deviceId, req.deviceId)
        // devicePub: punto comprimido (33 B) de la clave del dispositivo.
        assertTrue(B64.urlDec(req.devicePub).contentEquals(EcP256.comprimir(device.publicSpki())))
        assertTrue(EcP256.descomprimir(B64.urlDec(req.devicePub)).encoded.contentEquals(device.publicSpki()))
        assertTrue(Validators.nonce(req.nonce))
        assertTrue(Validators.deviceId(req.deviceId))
        assertEquals(t0, Instant.parse(req.solicitadaEn))
        assertNull(req.renueva)
    }

    @Test fun elMensajeEsTextoIntroductorioRenglonYSolicitudCifrada() {
        val s = manager().buildRequest(input(TipoLicencia.SEMESTRAL).copy(secundarias = 3))
        val (intro, cifrada) = s.texto.split("\n\n").also { assertEquals(2, it.size) }
        assertEquals(s.codigo, cifrada)
        assertTrue(cifrada.startsWith("SPVIR1:"))
        assertEquals(
            listOf(
                "Solicitud de licencia Shalev", "Nombre: María Pérez González", "Carné de identidad: 85010112345",
                "Teléfono: +5352345678", "Tipo: Semestral", "Apps secundarias: 3", "Precio: 45,000.00 CUP",
                cu.shalev.licencia.contract.MensajesLicencia.AVISO_FRESCURA,
            ),
            intro.lines(),
        )
        // El código basta (QR): GL lo descifra sin el texto de arriba.
        assertEquals(3, gl.openRequest(s.codigo).secundarias)
    }

    @Test fun laSolicitudCifradaSoloLaAbreGl() {
        val s = manager().buildRequest(input())
        assertTrue(runCatching { cu.shalev.licencia.contract.SolicitudCifrada.abrir(s.codigo, EcP256.generate().private) }.isFailure)
        // Un carácter cambiado → GCM la rechaza.
        val i = s.codigo.length - 30
        val alterada = s.codigo.substring(0, i) + (if (s.codigo[i] == 'A') 'B' else 'A') + s.codigo.substring(i + 1)
        assertTrue(runCatching { gl.openRequest(alterada) }.isFailure)
        // Dos solicitudes iguales no se parecen (efímera y nonce nuevos).
        assertFalse(s.codigo == manager().buildRequest(input()).codigo)
    }

    @Test fun viaIsInsideTheCiphertext() {
        assertEquals(Via.SMS, gl.openRequest(manager().buildRequest(input(via = Via.SMS)).texto).via)
    }

    @Test fun elMensajeCabeComoPieDeImagenEnWhatsApp() {
        val s = manager().buildRequest(input())
        assertTrue("solicitud cifrada: ${s.codigo.length}", s.codigo.length in 400..650)
        assertTrue("mensaje: ${s.texto.length}", s.texto.length < 1000) // WhatsApp corta el pie de foto hacia 1 024
        // Con nombres largos (80 + 80) y renovación sigue cabiendo en un QR (≤ 2 331 B en nivel M).
        val largo = SolicitudInput("A".repeat(80), "B".repeat(80), "85010112345", "5234 5678", TipoLicencia.ANUAL, Via.WHATSAPP, 10, "d53a020b-4062-4825-8219-024f997173f8")
        assertTrue(manager().buildRequest(largo).codigo.length < 1200)
    }

    // ---------- Activación ----------

    @Test fun activateMonthlyLicense() = runBlocking {
        val m = manager()
        val req = gl.openRequest(m.buildRequest(input()).texto)
        val issued = gl.issue(req, emitidaEn = t0)
        val r = m.activate(issued.message)
        assertTrue(r is ActivationResult.Accepted)
        val s = m.state()
        assertTrue(s is LicenseState.Active)
        assertEquals("Licencia mensual: 30 días restantes", s.bannerText(t0, ZoneOffset.UTC))
    }

    @Test fun semiannualBannerInMonthsThenDays() = runBlocking {
        val m = manager()
        m.activate(gl.issue(gl.openRequest(m.buildRequest(input(TipoLicencia.SEMESTRAL)).texto), t0).message)
        assertEquals("Licencia semestral: 5 meses restantes", m.state().bannerText(t0, ZoneOffset.UTC))
        clock.plusDays(170)
        assertEquals("Licencia semestral: 10 días restantes", m.state().bannerText(clock.t, ZoneOffset.UTC))
    }

    @Test fun perpetualHasNoBanner() = runBlocking {
        val m = manager()
        m.activate(gl.issue(gl.openRequest(m.buildRequest(input(TipoLicencia.PERPETUA)).texto), t0).message)
        val s = m.state()
        assertTrue(s is LicenseState.Perpetual)
        assertNull(s.bannerText(t0))
    }

    @Test fun licenseExpiresAndLocks() = runBlocking {
        val m = manager()
        m.activate(gl.issue(gl.openRequest(m.buildRequest(input()).texto), t0).message)
        clock.plusDays(31)
        val s = m.state()
        assertTrue(s is LicenseState.Expired)
        assertFalse(s.unlocked)
    }

    @Test fun rejectsLicenseSignedByOtherKey() = runBlocking {
        val m = manager()
        val req = gl.openRequest(m.buildRequest(input()).texto)
        val r = m.activate(gl.issue(req, t0, signWith = EcP256.generate()).message)
        assertEquals(ActivationResult.Rejected, r)
        assertNull(store.lic)
    }

    @Test fun rejectsLicenseForOtherDeviceId() = runBlocking {
        val m = manager()
        val req = gl.openRequest(m.buildRequest(input()).texto)
        assertEquals(ActivationResult.Rejected, m.activate(gl.issue(req, t0, deviceIdOverride = "OTRAAPP:3f2a9c0d1e4b5a67").message))
    }

    @Test fun laLicenciaLargaYaNoSeActivaPeroLaInstaladaSigue() = runBlocking {
        val m = manager()
        val larga = gl.issueLarga(gl.openRequest(m.buildRequest(input()).texto), t0)
        assertEquals(ActivationResult.NotFound, m.activate(larga.message))
        assertNull(store.lic)
        // Instalada antes de 0.23.0: se sigue re-verificando hasta que venza.
        store.instalarLarga(larga)
        assertEquals(larga.id, m.evaluate().license!!.id)
        assertTrue(m.state() is LicenseState.Active)
        clock.plusDays(31)
        assertTrue(m.state() is LicenseState.Expired)
    }

    @Test fun soloCuentaElCodigoNoElTextoDeArriba() = runBlocking {
        val m = manager()
        val iss = gl.issue(gl.openRequest(m.buildRequest(input()).texto), t0)
        // GL podría escribir cualquier cosa arriba: no cambia nada (lo que cuenta va cifrado y firmado).
        val engañoso = iss.message.replace("Tipo: Mensual", "Tipo: Perpetua").replace("Apps secundarias: 5", "Apps secundarias: 10")
        assertTrue(m.activate(engañoso) is ActivationResult.Accepted)
        assertEquals(TipoLicencia.MENSUAL, m.evaluate().license!!.tipo)
        // Solo el código (QR) también vale.
        assertTrue(manager().activate(iss.codigo) is ActivationResult.Accepted)
    }

    @Test fun garbageIsNotFound() = runBlocking {
        assertEquals(ActivationResult.NotFound, manager().activate("hola"))
    }

    @Test fun newerRevocationReplacesActive() = runBlocking {
        val m = manager()
        val req = gl.openRequest(m.buildRequest(input()).texto)
        val first = gl.issue(req, t0)
        m.activate(first.message)
        clock.plusDays(2)
        m.activate(gl.issue(req, clock.t, estado = EstadoLicencia.REVOCADA, id = first.id).message)
        assertEquals(LicenseState.Revoked, m.state())
    }

    @Test fun olderLicenseIsIgnored() = runBlocking {
        val m = manager()
        val req = gl.openRequest(m.buildRequest(input()).texto)
        val old = gl.issue(req, t0.minusSeconds(86_400))
        m.activate(gl.issue(req, t0).message)
        assertEquals(ActivationResult.Outdated, m.activate(old.message))
    }

    @Test fun rotationKeepsOldLicensesValid() = runBlocking {
        val m = manager()
        m.activate(gl.issue(gl.openRequest(m.buildRequest(input()).texto), t0).message)
        val newKey = EcP256.generate().public.encoded
        val rotated = manager(listOf(newKey, gl.signing.public.encoded))
        assertTrue(rotated.state() is LicenseState.Active)
    }

    @Test fun tamperedStoredLicenseFallsBackToTrialState() = runBlocking {
        val m = manager()
        m.activate(gl.issue(gl.openRequest(m.buildRequest(input()).texto), t0).message)
        store.lic = store.lic!!.copy(licenseId = "00000000-0000-0000-0000-000000000000")
        assertTrue(m.state() is LicenseState.Trial)
    }

    // ---------- Prueba y reloj ----------

    @Test fun trialLastsSevenDays() = runBlocking {
        val m = manager()
        assertEquals(LicenseState.Trial(7), m.state())
        assertEquals("Periodo de prueba restante: 7 días", m.state().bannerText(t0))
        clock.plusDays(6)
        assertEquals(LicenseState.Trial(1), m.state())
        clock.plusDays(1)
        assertEquals(LicenseState.TrialExpired, m.state())
    }

    @Test fun clockRollbackIsDetected() = runBlocking {
        val m = manager()
        m.state()
        clock.plusDays(3); m.state()
        clock.plusDays(-2)
        assertEquals(LicenseState.ClockTampered, m.state())
        clock.plusDays(2)
        assertTrue(m.state() is LicenseState.Trial)
    }

    @Test fun missingKeysDisableRequests() {
        val m = LicenseManager(store, device, deviceId, null, emptyList(), clock)
        assertFalse(m.canRequest)
    }

    @Test fun solicitudDeAmpliacionCifraAmpliaYCobraSoloSecundariasNuevas() {
        val m = manager()
        val s = m.buildRequest(
            input(TipoLicencia.MENSUAL).copy(
                secundarias = 5,
                amplia = "3f2a9c0d-1e4b-5a67-8899-aabbccddeeff",
                secundariasPrevias = 2,
            ),
        )
        val (intro, cifrada) = s.texto.split("\n\n").also { assertEquals(2, it.size) }
        val req = gl.openRequest(cifrada)
        assertEquals("3f2a9c0d-1e4b-5a67-8899-aabbccddeeff", req.amplia)
        assertNull(req.renueva)
        assertNull(req.recupera)
        assertEquals(5, req.secundarias)
        assertTrue(intro.lines().contains("Ampliar secundarias Shalev"))
        assertTrue(intro.lines().contains("Apps secundarias actuales: 2"))
        assertTrue(intro.lines().contains("Secundarias adicionales: 3"))
        assertTrue(intro.lines().contains("Apps secundarias: 5"))
        assertTrue(intro.lines().contains("Precio: 3,000.00 CUP"))
        assertTrue(intro.lines().contains("Amplía: 3f2a9c0d-1e4b-5a67-8899-aabbccddeeff"))
    }
}
