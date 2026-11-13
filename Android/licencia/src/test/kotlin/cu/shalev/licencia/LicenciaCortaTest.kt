package cu.shalev.licencia

import cu.shalev.licencia.contract.EstadoLicencia
import cu.shalev.licencia.contract.LicenciaCorta
import cu.shalev.licencia.contract.MensajesLicencia
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.contract.Via
import cu.shalev.licencia.crypto.B64
import cu.shalev.licencia.crypto.DeviceKey
import cu.shalev.licencia.crypto.EcP256
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.23.0 (Prompt 64): licencia corta CIFRADA (`SPVI2:`), única forma que se activa; mensaje «características + renglón +
 * código»; lectura desde cualquier texto o QR; renovación sin perder días. El emisor simulado ([FakeGl.issue]) sigue la
 * especificación de [LicenciaCorta] (la misma que recibe GL en `docs/GL_PROMPT_0.23.md`).
 */
class LicenciaCortaTest {
    private val t0 = Instant.parse("2026-10-03T20:00:00Z")
    private val gl = FakeGl()
    private val clock = MutableClock(t0)
    private val store = InMemoryStore()
    private val device = InMemoryDeviceKey()
    private val deviceId = "SPVI:3f2a9c0d1e4b5a67"

    private fun manager(d: DeviceKey = device, id: String = deviceId, s: InMemoryStore = store) =
        LicenseManager(s, d, id, gl.ecdh.public.encoded, listOf(gl.signing.public.encoded), clock)

    private fun input(tipo: TipoLicencia = TipoLicencia.MENSUAL, secundarias: Int = 2, renueva: String? = null) =
        SolicitudInput("María", "Pérez González", "85010112345", "5234 5678", tipo, Via.SMS, secundarias, renueva)

    private fun pedir(m: LicenseManager = manager(), tipo: TipoLicencia = TipoLicencia.MENSUAL, secundarias: Int = 2) =
        gl.openRequest(m.buildRequest(input(tipo, secundarias)).texto)

    // ---------- Formato ----------

    @Test fun cuerpoDe44BytesYCodigoDe216Caracteres() {
        val huella = LicenciaCorta.huella(deviceId, EcP256.comprimir(device.publicSpki()))
        val c = LicenciaCorta.Campos(
            "d53a020b-4062-4825-8219-024f997173f8", huella, TipoLicencia.SEMESTRAL, EstadoLicencia.ACTIVA, 10,
            t0, t0.plus(Duration.ofDays(180)),
        )
        val cuerpo = LicenciaCorta.cuerpo(c)
        assertEquals(44, cuerpo.size)
        assertEquals(c, LicenciaCorta.leer(cuerpo))
        val texto = LicenciaCorta.emitir(c, EcP256.publicKey(device.publicSpki()), { EcP256.sign(gl.signing.private, it) })
        assertEquals(216, texto.length) // 2 SMS si va sola
        // Solo caracteres del alfabeto GSM-7 básico (sin extensiones que cuenten doble).
        assertTrue(texto.all { it.isLetterOrDigit() && it.code < 128 || it in ":-_" })
        // El cuerpo NO aparece en claro en el código (va cifrado).
        assertFalse(java.util.Base64.getUrlDecoder().decode(texto.removePrefix("SPVI2:")).toList().windowed(16).any { it == cuerpo.copyOfRange(1, 17).toList() })
        val p = LicenciaCorta.leer(LicenciaCorta.cuerpo(c.copy(tipo = TipoLicencia.PERPETUA, estado = EstadoLicencia.PERPETUA, venceEn = null)))!!
        assertNull(p.venceEn)
        // Formato, tipo o estado desconocidos → mal formado.
        assertNull(LicenciaCorta.leer(cuerpo.copyOf().also { it[0] = 1 }))
        assertNull(LicenciaCorta.leer(cuerpo.copyOf().also { it[33] = 4 }))
        assertNull(LicenciaCorta.leer(cuerpo.copyOf().also { it[34] = 9 }))
        assertNull(LicenciaCorta.leer(cuerpo.copyOf(43)))
    }

    @Test fun elMensajeDeGlEsCaracteristicasRenglonYCodigo() {
        val iss = gl.issue(pedir(), t0)
        val (arriba, codigo) = iss.message.split("\n\n").also { assertEquals(2, it.size) }
        assertEquals(iss.codigo, codigo)
        assertEquals(
            listOf("Licencia Shalev", "Tipo: Mensual", "Apps secundarias: 2", "Emitida: 03/10/2026", "Vence: 02/11/2026", "ID: ${iss.id}"),
            arriba.lines(),
        )
        // Sin tildes fuera de GSM-7: por SMS no pasa a UCS-2.
        assertTrue(iss.message.all { it.code < 128 })
        val perpetua = gl.issue(pedir(tipo = TipoLicencia.PERPETUA, secundarias = 0), t0)
        assertTrue(perpetua.message.contains("Vence: nunca"))
    }

    @Test fun seEncuentraDentroDeCualquierTextoAunqueLaAppPartaLaLinea() {
        val codigo = gl.issue(pedir(), t0).codigo
        val partido = codigo.substring(0, 70) + "\n " + codigo.substring(70, 150) + "\n" + codigo.substring(150)
        for (texto in listOf(codigo, "Hola, tu licencia:\n\n$codigo\nGracias", partido, "«$codigo»")) {
            assertEquals(texto, codigo, LicenciaCorta.extraer(texto)?.texto)
        }
        assertNull(LicenciaCorta.extraer("SPVI2:corto"))
        assertNull(LicenciaCorta.extraer(codigo.dropLast(1)))
        assertNull(LicenciaCorta.extraer("sin licencia"))
    }

    @Test fun puntoComprimidoYFirmaCrudaEnLosDosSentidos() {
        repeat(40) { i ->
            val kp = EcP256.generate()
            val c = EcP256.comprimir(kp.public as ECPublicKey)
            assertEquals(33, c.size)
            assertArrayEquals(kp.public.encoded, EcP256.descomprimir(c).encoded)
            val der = EcP256.sign(gl.signing.private, "mensaje $i".toByteArray())
            assertTrue(EcP256.verify(gl.signing.public, "mensaje $i".toByteArray(), EcP256.rawToDer(EcP256.derToRaw(der))))
        }
        // x fuera de la curva, prefijo inválido o tamaño incorrecto → falla.
        assertTrue(runCatching { EcP256.descomprimir(ByteArray(33).also { it[0] = 2; it[32] = 7 }) }.isFailure) // x = 7: fuera
        assertTrue(runCatching { EcP256.descomprimir(ByteArray(33).also { it[0] = 2; it[32] = 5 }) }.isSuccess) // x = 5: en la curva
        assertTrue(runCatching { EcP256.descomprimir(ByteArray(33).also { it[0] = 4 }) }.isFailure)
        assertTrue(runCatching { EcP256.descomprimir(ByteArray(32)) }.isFailure)
        val raw = ByteArray(64).also { it[0] = 0x80.toByte(); it[31] = 1; it[34] = 5; it[63] = 7 }
        assertArrayEquals(raw, EcP256.derToRaw(EcP256.rawToDer(raw)))
    }

    // ---------- Activación ----------

    @Test fun activaYSobreviveAlReinicio() = runBlocking {
        val m = manager()
        val issued = gl.issue(pedir(m), t0)
        val r = m.activate(issued.message)
        assertEquals(ActivationResult.Accepted(LicenseState.Active(TipoLicencia.MENSUAL, t0.plus(Duration.ofDays(30)), issued.id, 2)), r)
        assertEquals(issued.codigo, store.lic!!.envelopeJson)
        // Otro arranque (gestor nuevo): se vuelve a verificar la firma, a descifrar y a comprobar la huella.
        val ev = manager().evaluate()
        assertEquals(LicenseState.Active(TipoLicencia.MENSUAL, t0.plus(Duration.ofDays(30)), issued.id, 2), ev.state)
        assertEquals(2, ev.license!!.secundarias)
        clock.plusDays(31)
        assertEquals(LicenseState.Expired(TipoLicencia.MENSUAL), manager().state())
    }

    @Test fun perpetuaYRevocada() = runBlocking {
        val perpetua = gl.issue(pedir(tipo = TipoLicencia.PERPETUA, secundarias = 0), t0)
        assertEquals(ActivationResult.Accepted(LicenseState.Perpetual(perpetua.id, 0)), manager().activate(perpetua.message))
        clock.plusDays(1)
        val revocada = gl.issue(pedir(), clock.t, estado = EstadoLicencia.REVOCADA)
        assertEquals(ActivationResult.Accepted(LicenseState.Revoked), manager().activate(revocada.message))
        assertEquals(LicenseState.Revoked, manager().state())
    }

    @Test fun seRechazaSiEsDeOtroTelefonoONoLaFirmoGl() = runBlocking {
        val req = pedir()
        // Huella de otra clave (aunque cifrada hacia la nuestra).
        val deOtro = gl.issue(req, t0, huellaDe = LicenciaCorta.huella(deviceId, EcP256.comprimir(InMemoryDeviceKey().publicSpki()))).message
        assertEquals(ActivationResult.Rejected, manager().activate(deOtro))
        // Mismo dispositivo, otro deviceId.
        assertEquals(ActivationResult.Rejected, manager(id = "SPVI:0000000000000000").activate(gl.issue(req, t0).message))
        // Firmada por otra clave.
        assertEquals(ActivationResult.Rejected, manager().activate(gl.issue(req, t0, signWith = EcP256.generate()).message))
        // Firma de ceros.
        val bueno = LicenciaCorta.extraer(gl.issue(req, t0).message)!!
        assertEquals(ActivationResult.Rejected, manager().activate(LicenciaCorta.Codigo(bueno.epk, bueno.cifrado, ByteArray(64)).texto))
        // Secundarias fuera de rango firmadas por GL: mal formada.
        assertEquals(ActivationResult.Rejected, manager().activate(gl.issue(req, t0, secundarias = 11).message))
        assertNull(store.lic)
    }

    @Test fun copiarElDeviceIdYLaClavePublicaAOtroTelefonoNoBasta() = runBlocking {
        val issued = gl.issue(pedir(), t0)
        // «Clon»: dice tener la clave pública del original, pero su clave privada es otra → no descifra.
        val clon = object : DeviceKey {
            private val propia = InMemoryDeviceKey()
            override fun publicSpki(): ByteArray = device.publicSpki()
            override fun agree(peer: PublicKey): ByteArray = propia.agree(peer)
        }
        assertEquals(ActivationResult.Rejected, manager(d = clon, s = InMemoryStore()).activate(issued.message))
        manager().activate(issued.message)
        val copia = InMemoryStore().apply { lic = store.lic }
        assertTrue(manager(d = clon, s = copia).state() is LicenseState.Trial)
    }

    @Test fun masAntiguaQueLaInstaladaYTextoSinLicencia() = runBlocking {
        val req = pedir()
        manager().activate(gl.issue(req, t0).message)
        assertEquals(ActivationResult.Outdated, manager().activate(gl.issue(req, t0.minusSeconds(3600)).message))
        assertEquals(ActivationResult.NotFound, manager().activate("hola"))
        // La larga de antes ya no se activa (ni siquiera junto a una corta ajena).
        clock.plusDays(1)
        val larga = gl.issueLarga(req, clock.t).message
        assertEquals(ActivationResult.NotFound, manager().activate(larga))
        val cortaAjena = gl.issue(req, clock.t, huellaDe = ByteArray(16)).message
        assertEquals(ActivationResult.Rejected, manager().activate("$cortaAjena\n$larga"))
    }

    // ---------- Renovación (L-e) ----------

    @Test fun laSolicitudDiceQueLicenciaSeRenuevaSoloSiHayUnaConVencimiento() = runBlocking {
        val m = manager()
        assertNull(m.licenciaRenovable())
        val primera = m.buildRequest(input())
        assertFalse("sin renovación no hay línea Renueva", primera.texto.contains("Renueva"))
        assertNull(gl.openRequest(primera.codigo).renueva)

        val issued = gl.issue(pedir(m), t0)
        m.activate(issued.message)
        val id = m.licenciaRenovable()
        assertEquals(issued.id, id)
        val renovacion = m.buildRequest(input(renueva = id))
        assertTrue(renovacion.texto.lines().contains("Renueva: ${issued.id}"))
        assertEquals(issued.id, gl.openRequest(renovacion.codigo).renueva)
        clock.plusDays(40) // vencida: se sigue pudiendo renovar con el mismo id
        assertEquals(issued.id, manager().licenciaRenovable())

        clock.plusDays(1)
        manager().activate(gl.issue(pedir(tipo = TipoLicencia.PERPETUA), clock.t).message)
        assertNull("una perpetua no se renueva", manager().licenciaRenovable())
    }

    @Test fun unaRenovacionQueEmpiezaAlTerminarLaActualSeAceptaConSuFechaFinal() = runBlocking {
        val m = manager()
        m.activate(gl.issue(pedir(m), t0).message)
        clock.plusDays(25) // quedan 5 días
        // GL: venceEn = venceEn anterior + 30 días; emitidaEn = ahora.
        val renovada = gl.issue(pedir(m), clock.t, venceEn = t0.plus(Duration.ofDays(60)))
        val r = m.activate(renovada.message)
        assertEquals(ActivationResult.Accepted(LicenseState.Active(TipoLicencia.MENSUAL, t0.plus(Duration.ofDays(60)), renovada.id, 2)), r)
        clock.plusDays(30) // día 55: la primera ya habría vencido, la renovada no
        assertTrue(manager().state() is LicenseState.Active)
    }

    // ---------- Vector de referencia (el mismo de docs/GL_PROMPT_0.23.md) ----------

    /**
     * Escrito con `tools/licencia/probar_gl.py vector` (Python, otra implementación) con una clave de firma de PRUEBA y
     * el dispositivo de prueba de [GoldenVectorTest]. Si Shalev y la especificación divergen, falla aquí.
     */
    @Test fun vectorDeReferenciaEscritoEnPython() = runBlocking {
        val dispositivo = object : DeviceKey {
            private val priv = java.security.KeyFactory.getInstance("EC").generatePrivate(java.security.spec.PKCS8EncodedKeySpec(
                B64.dec("MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCA3KZoM7faOt6GlH6Bv4WpLS+4FC4Xovdfd2ugoNaBjOg=="),
            ))
            override fun publicSpki() = B64.dec(
                "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEK5kYd7uxSBsUt+q594XUEwK8q5hlC4kphLGcihzTMwrBrGTCFE8v3A+ZgWNDCfdIJb/Uwn0M1roveviZvkv8og==",
            )
            override fun agree(peer: PublicKey) = EcP256.ecdh(priv, peer)
        }
        val idGolden = "SPVI:golden0000000001"
        val firmaPrueba = B64.dec("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEvXxzuIsum0ztpiAistqL4TGTpbVu3Cbn33hC4kzQtesGBa2nvag6xqK4DX4xQED6R/8WuDushc7bAURRu3znGg==")
        assertEquals("AiuZGHe7sUgbFLfqufeF1BMCvKuYZQuJKYSxnIoc0zMK", B64.urlNoPad(EcP256.comprimir(dispositivo.publicSpki())))
        val huella = LicenciaCorta.huella(idGolden, EcP256.comprimir(dispositivo.publicSpki()))
        assertEquals("fb445359b06179d282c7970ea4adab32", huella.joinToString("") { "%02x".format(it) })
        val emitida = Instant.parse("2026-10-03T20:00:00Z")
        val campos = LicenciaCorta.Campos(
            "7c9e6679-7425-40de-944b-e07fc1f90ae7", huella, TipoLicencia.MENSUAL, EstadoLicencia.ACTIVA, 2, emitida, emitida.plus(Duration.ofDays(30)),
        )
        assertEquals("027c9e6679742540de944be07fc1f90ae7fb445359b06179d282c7970ea4adab320000026ac15ec06ae8ebc0", LicenciaCorta.cuerpo(campos).joinToString("") { "%02x".format(it) })
        val mensaje = """
Licencia Shalev
Tipo: Mensual
Apps secundarias: 2
Emitida: 03/10/2026
Vence: 02/11/2026
ID: 7c9e6679-7425-40de-944b-e07fc1f90ae7

SPVI2:A8Ntuq3AH5-S0FVM2Itwox5ElM8JLkdH1v90_SJsF6KzCtzdFYKcGWYWoXp1RC0191zsFibEJjMpJmCWwJWjkLnY7KDTfPDFw4lrwTr5VFo_db5f84NP9QfStCUSFj1KIUNpD9H8p_8K7QgDB6aD8uxVBqwaW6Y3zXFuiog_-kx0MpKG3I-JHQk6dMkJiHLm0Vm83e5d5pYbhcWUZQ
""".trim()
        assertEquals(MensajesLicencia.licencia(campos, "SPVI2:A8Ntuq3AH5-S0FVM2Itwox5ElM8JLkdH1v90_SJsF6KzCtzdFYKcGWYWoXp1RC0191zsFibEJjMpJmCWwJWjkLnY7KDTfPDFw4lrwTr5VFo_db5f84NP9QfStCUSFj1KIUNpD9H8p_8K7QgDB6aD8uxVBqwaW6Y3zXFuiog_-kx0MpKG3I-JHQk6dMkJiHLm0Vm83e5d5pYbhcWUZQ"), mensaje)
        val m = LicenseManager(InMemoryStore(), dispositivo, idGolden, null, listOf(firmaPrueba), MutableClock(emitida.plusSeconds(60)))
        assertEquals(
            ActivationResult.Accepted(LicenseState.Active(TipoLicencia.MENSUAL, emitida.plus(Duration.ofDays(30)), campos.id, 2)),
            m.activate(mensaje),
        )
    }
}
