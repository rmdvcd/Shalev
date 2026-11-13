package cu.shalev.licencia

import cu.shalev.licencia.contract.CodigoLicencia
import cu.shalev.licencia.contract.GlContract
import cu.shalev.licencia.contract.SolicitudCifrada
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.contract.Via
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Clave maestra de desarrollo `GLMK-PERM-MAXS-9999` (mitigación 1, Opción A): activa Shalev de forma perpetua con el
 * máximo de apps secundarias, **solo en builds de depuración**. En release se ignora incluso si quedó instalada.
 */
class MasterKeyTest {
    private val t0 = Instant.parse("2026-10-13T12:00:00Z")
    private val gl = FakeGl()
    private val clock = MutableClock(t0)
    private val store = InMemoryStore()
    private val device = InMemoryDeviceKey()
    private val deviceId = "SPVI:aa11bb22cc33dd44"

    private fun manager(esDebug: Boolean, firmante: cu.shalev.licencia.secondary.FirmanteLicencia? = null) = LicenseManager(
        store, device, deviceId, gl.ecdh.public.encoded, listOf(gl.signing.public.encoded), clock,
        esDebug = esDebug, firmante = firmante,
    )

    @Test fun enDebugActivaPerpetuaConDiezSecundarias() = runBlocking {
        val m = manager(esDebug = true)
        val r = m.activate(CodigoLicencia.MAESTRA)
        assertTrue(r is ActivationResult.Accepted)
        assertEquals(LicenseState.Perpetual(LicenseManager.ID_MAESTRA, GlContract.SECUNDARIAS_MAX), (r as ActivationResult.Accepted).state)
        val ev = m.evaluate()
        assertEquals(GlContract.SECUNDARIAS_MAX, ev.state.secundariasPermitidas)
        assertTrue(ev.state.unlocked)
        // Queda guardada marcada como maestra y se re-verifica al arrancar.
        assertTrue(store.lic!!.maestra)
        assertEquals(LicenseManager.ID_MAESTRA, ev.license!!.id)
    }

    @Test fun seAdmiteEscritaConEspaciosOGuionesYMinusculas() = runBlocking {
        val m = manager(esDebug = true)
        assertTrue(m.activate("  glmk-perm-maxs-9999  ") is ActivationResult.Accepted)
        assertTrue(m.esClaveMaestra("GLMK PERMMAXS 9999"))
    }

    @Test fun enReleaseNoSeAcepta() = runBlocking {
        val m = manager(esDebug = false)
        assertFalse(m.esClaveMaestra(CodigoLicencia.MAESTRA))
        assertTrue(m.activate(CodigoLicencia.MAESTRA) !is ActivationResult.Accepted)
        assertNull(store.lic)
        // Y sigue en periodo de prueba (7 días), como cualquier instalación nueva.
        assertTrue(m.evaluate().state is LicenseState.Trial)
    }

    @Test fun unaLicenciaMaestraInstaladaSeIgnoraEnRelease() = runBlocking {
        // Se instaló en un build de depuración…
        manager(esDebug = true).activate(CodigoLicencia.MAESTRA)
        assertNotNull(store.lic)
        // …y ahora la app es de release: la licencia maestra no vale (vuelve a la prueba).
        val release = manager(esDebug = false)
        assertTrue(release.evaluate().state is LicenseState.Trial)
        assertNull(release.evaluate().license)
        // En depuración sí vale (mismo almacenamiento).
        assertTrue(manager(esDebug = true).evaluate().state is LicenseState.Perpetual)
    }

    @Test fun noSustituyeAUnaLicenciaRealMasNueva() = runBlocking {
        val m = manager(esDebug = true)
        val req = gl.openRequest(m.buildRequest(SolicitudInput("Ana", "Díaz", "90010112345", "52345678", TipoLicencia.ANUAL, Via.WHATSAPP)).texto)
        val real = gl.issue(req, emitidaEn = t0)
        assertTrue(m.activate(real.message) is ActivationResult.Accepted)
        val id = store.lic!!.licenseId
        // La clave maestra no pisa una licencia auténtica ya instalada con el mismo `emitidaEn`… se instala después,
        // así que sustituye; lo que NO puede es quedar por debajo de la instalada en `instalar()` (Outdated).
        assertTrue(m.activate(CodigoLicencia.MAESTRA) is ActivationResult.Accepted)
        assertEquals(LicenseManager.ID_MAESTRA, store.lic!!.licenseId)
        assertFalse(store.lic!!.licenseId == id)
    }

    @Test fun conLaMaestraSePuedenEmitirSubTokensHastaDiez() = runBlocking {
        val firmante = emisorDePrueba()
        val m = manager(esDebug = true, firmante = firmante)
        // Sin licencia (periodo de prueba) la cuota es la de siempre para la prueba ([GlContract.SECUNDARIAS_DEFECTO]).
        assertEquals(GlContract.SECUNDARIAS_DEFECTO, m.cuotaSecundarias())
        m.activate(CodigoLicencia.MAESTRA)
        assertEquals(GlContract.SECUNDARIAS_MAX, m.cuotaSecundarias())
        val token = m.emitirSubToken("SPVI:secundaria1", ranura = 1)
        assertNotNull(token)
        val datos = cu.shalev.licencia.secondary.SubToken.verificar(
            token!!, firmante.publicSpki(), "SPVI:secundaria1", t0, GlContract.SECUNDARIAS_MAX,
        )
        assertEquals(LicenseManager.ID_MAESTRA, datos!!.licenciaId)
        assertEquals(GlContract.SECUNDARIAS_MAX, datos.permitidas)
        assertNull(m.emitirSubToken("SPVI:secundaria1", ranura = 11))
    }

    @Test fun laSolicitudCompactaSigueFuncionandoConLaMaestra() = runBlocking {
        // La clave maestra no cambia el flujo normal: se puede seguir pidiendo una licencia a GL.
        val m = manager(esDebug = true)
        val s = m.buildRequest(SolicitudInput("Luis", "Gómez", "88020212345", "5555 6666", TipoLicencia.MENSUAL, Via.SMS))
        assertTrue(s.codigo.startsWith(SolicitudCifrada.PREFIJO))
        assertEquals("Luis", gl.openRequest(s.codigo).nombre)
    }

    private fun emisorDePrueba(): cu.shalev.licencia.secondary.FirmanteLicencia {
        val kp = cu.shalev.licencia.crypto.EcP256.generate()
        return object : cu.shalev.licencia.secondary.FirmanteLicencia {
            override fun publicSpki(): ByteArray = kp.public.encoded
            override fun firmar(datos: ByteArray): ByteArray = cu.shalev.licencia.crypto.EcP256.sign(kp.private, datos)
        }
    }
}
