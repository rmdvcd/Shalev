package cu.shalev.licencia

import cu.shalev.licencia.contract.IdLicencia
import cu.shalev.licencia.contract.ListaRevocaciones
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.contract.Via
import cu.shalev.licencia.crypto.EcP256
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.25.0: solicitud de recuperación (`recupera`) y lista pública de licencias revocadas. */
class RecuperacionTest {
    private val t0 = Instant.parse("2026-10-01T12:00:00Z")
    private val gl = FakeGl()
    private val clock = MutableClock(t0)
    private val store = InMemoryStore()
    private val manager = LicenseManager(store, InMemoryDeviceKey(), "SPVI:0a1b2c3d4e5f6071", gl.ecdh.public.encoded, listOf(gl.signing.public.encoded), clock)
    private val id = "7C9E6679-7425-40DE-944B-E07FC1F90AE7"
    private val input = SolicitudInput("María", "Pérez González", "85010112345", "5234 5678", TipoLicencia.ANUAL, Via.WHATSAPP)

    private fun lista(vararg ids: String, firma: java.security.KeyPair = gl.signing) =
        ListaRevocaciones.firmar(ListaRevocaciones.Datos(emitidaEn = t0.epochSecond, revocadas = ids.map(ListaRevocaciones::huella))) {
            EcP256.sign(firma.private, it)
        }

    @Test fun laSolicitudDeRecuperacionLlevaElIdEnElPayloadYEnElTexto() {
        val s = manager.buildRequest(input.copy(recupera = "  $id "))
        val p = gl.openRequest(s.texto)
        assertEquals(id.lowercase(), p.recupera)
        assertNull(p.renueva)
        assertTrue(s.texto.startsWith("Recuperar licencia Shalev"))
        assertTrue(s.texto.contains("Licencia anterior: ${id.lowercase()}"))
        assertTrue(s.texto.endsWith(s.codigo))
    }

    @Test fun recuperaInvalidoOJuntoARenovarSeRechaza() {
        assertTrue(runCatching { manager.buildRequest(input.copy(recupera = "no es un id")) }.isFailure)
        assertTrue(runCatching { manager.buildRequest(input.copy(recupera = id, renueva = id)) }.isFailure)
        assertNull(gl.openRequest(manager.buildRequest(input).texto).recupera)
    }

    @Test fun idDeLicenciaSeExtraeDelTextoDeGl() {
        assertEquals(id.lowercase(), IdLicencia.extraer("Licencia Shalev\nID: $id\nTipo: Anual"))
        assertEquals(id.lowercase(), IdLicencia.extraer(id))
        assertNull(IdLicencia.extraer("ID: 1234"))
    }

    @Test fun huellaNormalizaYNoRevelaElId() {
        assertEquals(ListaRevocaciones.huella(id), ListaRevocaciones.huella(" ${id.lowercase()} "))
        assertEquals(32, ListaRevocaciones.huella(id).length)
        assertFalse(ListaRevocaciones.huella(id).contains("7c9e"))
    }

    @Test fun listaFirmadaSeVerificaYFalsaONoSe() {
        val ok = lista(id)
        val datos = ListaRevocaciones.verificar(ok, listOf(gl.signing.public))!!
        assertTrue(ListaRevocaciones.contiene(datos, id))
        assertFalse(ListaRevocaciones.contiene(datos, "00000000-0000-0000-0000-000000000000"))
        assertNull(ListaRevocaciones.verificar(lista(id, firma = EcP256.generate()), listOf(gl.signing.public)))
        assertNull(ListaRevocaciones.verificar(ok.replace(ListaRevocaciones.huella(id), "0".repeat(32)), listOf(gl.signing.public)))
        assertNull(ListaRevocaciones.verificar("{}", listOf(gl.signing.public)))
        assertNull(ListaRevocaciones.verificar("basura", listOf(gl.signing.public)))
    }

    @Test fun aplicarRevocacionesSoloSiIncluyeLaLicenciaInstalada() = runBlocking {
        // Sin licencia instalada: nada.
        assertFalse(manager.aplicarRevocaciones(lista(id)))
        val iss = gl.issue(gl.openRequest(manager.buildRequest(input).texto), t0)
        assertTrue(manager.activate(iss.message) is ActivationResult.Accepted)
        val antes = manager.state()
        assertFalse(manager.aplicarRevocaciones(lista(id)))
        assertFalse(manager.aplicarRevocaciones(lista(iss.id, firma = EcP256.generate())))
        assertEquals(antes, manager.state())
        assertTrue(manager.aplicarRevocaciones(lista(id, iss.id)))
        assertNotEquals(antes, manager.state())
        assertFalse(manager.state().unlocked)
    }

    /** Archivo generado por `tools/licencia/probar_gl.py revocadas firmar` (Python): compatibilidad GL ↔ Shalev. */
    @Test fun listaFirmadaPorElScriptDeGlSeAcepta() {
        val texto = "{\"datos\":\"{\\\"v\\\":1,\\\"emitidaEn\\\":1791000000,\\\"revocadas\\\":[\\\"22bc3546ab2eb0ec2a9b68fdf86d474c\\\"]}\",\"firma\":\"YycDDst6oToxfkLYm6IEuCExuhQNPlTDjGCe36NskeDxMx2cKiBQEv9V_yy7CU2HF-PGzHep2ERghSftDO5bKA\"}"
        val prueba = java.security.KeyFactory.getInstance("EC").generatePublic(
            java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(
                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEvXxzuIsum0ztpiAistqL4TGTpbVu3Cbn33hC4kzQtesGBa2nvag6xqK4DX4xQED6R/8WuDushc7bAURRu3znGg==",
            )),
        )
        val d = ListaRevocaciones.verificar(texto, listOf(prueba))!!
        assertEquals(1_791_000_000L, d.emitidaEn)
        assertTrue(ListaRevocaciones.contiene(d, id))
        assertEquals("22bc3546ab2eb0ec2a9b68fdf86d474c", ListaRevocaciones.huella(id))
        assertNull(ListaRevocaciones.verificar(texto, listOf(gl.signing.public)))
    }
}
