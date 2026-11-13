package cu.shalev.licencia

import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.contract.Via
import cu.shalev.licencia.crypto.B64
import cu.shalev.licencia.crypto.DeviceKey
import cu.shalev.licencia.crypto.EcP256
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import cu.shalev.core.time.Clock
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant

/**
 * Prueba de compatibilidad REAL con GL (se omite hasta que haya claves y vector).
 *
 * 1. Rellena LicenseTrust con las claves de GL.
 * 2. `./gradlew testDebugUnitTest --tests '*GoldenVectorTest.printRequest'` → copia el JSON impreso.
 * 3. Pégalo en GL: DEBE aceptarlo (valida la dirección Shalev → GL). Emite una licencia MENSUAL.
 * 4. Pega el mensaje COMPLETO recibido (con el id si GL lo añade) en [GL_LICENSE_MESSAGE].
 * 5. Ejecuta `verifyRealLicense` (valida la dirección GL → Shalev, incluida la cuestión del licenseId).
 *
 * La clave de dispositivo de prueba es solo para tests y no protege nada.
 */
class GoldenVectorTest {

    private val testDevice = object : DeviceKey {
        private val priv = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(B64.dec(
            "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCA3KZoM7faOt6GlH6Bv4WpLS+4FC4Xovdfd2ugoNaBjOg==",
        )))
        private val pub = B64.dec(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEK5kYd7uxSBsUt+q594XUEwK8q5hlC4kphLGcihzTMwrBrGTCFE8v3A+ZgWNDCfdIJb/Uwn0M1roveviZvkv8og==",
        )
        override fun publicSpki() = pub
        override fun agree(peer: PublicKey) = EcP256.ecdh(priv, peer)
    }

    private val deviceId = "SPVI:golden0000000001"

    /** Pega aquí el mensaje de licencia emitido por GL para la solicitud impresa por printRequest. */
    // 0.22.0: licencia REAL emitida por GL el 2026-10-03 para printRequest (MENSUAL, 2 secundarias).
    // Copia: tools/licencia/respuestas/licencia_gl_mensual_2.txt.
    private val GL_LICENSE_MESSAGE = """
Licencia Shalev MENSUAL · 2 secundarias · 8 000 CUP
ID: d53a020b-4062-4825-8219-024f997173f8
{"v":1,"alg":"ECIES-P256-AES256GCM-v1","epk":"MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfAqgiTaL3VIaZNtkTMTsEEwCvrkebVhnJDNO93ScBAZMeSn88gK09EO94/cKUiA5OqeWXJfpZhRNBT4FVvzvLw==","iv":"CgTpyZMuI59ZvP2e","ct":"6A5QCaufa9NfFmrHIW/3TjZ4pnzd2fmgGnl3VRU36B0VxKtQ4rG9ksZR3rXTv/fAqp50UdrMAw0KyUXSsY9ZsB/33jX0fQjVDWfu6ImNGk/Zpr/dCpJfVUg8hX2n/9FfLKY4fOGHEu0iznC0ZQsxkJyyDe5alG63Ga42+V4wLpVM2cAIMDzatop9q6Psvj1Rq8ewMbsy/xHkngcbt/qLQ6KZugeBQtSGiHMtr+hWxJDXbckKWnppd0qhyXjcOMqdHrsxumrS/8pwtWx2P4287Lm2mvhyhOfIk2ayH4KgDtX4OMe/OWxGVae4OWIQMRdumzY0f/kYZW5QPqHzMbFlCDT9oxwGqYnf+4TX6tE8UnxpabrJn6/h9pn1wAbBOeeowv1CwXfjKWXakUc7jUEKosDiDkcv6N/dYUceOLerOghZnZD3wHMrdmjcyol8azDAM0kHBvyzgccDjeEIUSor+PkwEwidKb3LNevo+P8MgVpGLhW4wchDH2Rs+eEqzPc1YCFqW/4PTIXX5sLX1XhoVXRc/kMreWpB+RokjPact6I=","tag":"l7T2Iz+bnGBqkp8zEVEj4g==","sig":"MEYCIQC3w8nzKzxbnHsIm4t2ZMxUo4dY1rKAYE8/NMR7M3engQIhAI4XcfzkeZI6JO1C/Xw32gCmYeiUbAF9DT84q2Cyk3CU","kid":"gl-sign-v1"}
"""

    /** Si la ECDH no está fijada en el build (D3.1), pega aquí la SPKI exportada desde GL para el vector. */
    private val GL_ECDH_SPKI = ""

    private fun ecdh(): ByteArray? = LicenseTrust.ecdhSpki()
        ?: GL_ECDH_SPKI.takeIf { it.isNotBlank() }?.let(cu.shalev.licencia.crypto.EcP256::decodeSpkiText)

    private fun manager(clock: Clock) = LicenseManager(
        InMemoryStore(), testDevice, deviceId, ecdh(), LicenseTrust.signSpkis(), clock,
    )

    @Test fun printRequest() {
        assumeTrue("Claves de GL no configuradas", ecdh() != null && LicenseTrust.canValidate)
        val s = manager { Instant.now() }.buildRequest(
            SolicitudInput("Prueba", "Compatibilidad Shalev", "00000000000", "51815604", TipoLicencia.MENSUAL, Via.WHATSAPP),
        )
        println("\n===== SOLICITUD Shalev PARA PEGAR EN GL =====\n${s.texto}\n===========================================\n")
    }

    /**
     * 0.23.0: la licencia LARGA real de GL ya no se activa (solo la corta cifrada), pero si estaba instalada se sigue
     * re-verificando con la criptografía de verdad (firma de GL + descifrado con la clave del dispositivo).
     */
    @Test fun verifyRealLicense() = runBlocking {
        assumeTrue("Clave de firma de GL no configurada", LicenseTrust.canValidate)
        assumeTrue("Sin vector de GL", GL_LICENSE_MESSAGE.isNotBlank())
        assertEquals(ActivationResult.NotFound, manager { Instant.now() }.activate(GL_LICENSE_MESSAGE))
        val json = GL_LICENSE_MESSAGE.lines().first { it.startsWith("{") }
        val id = "d53a020b-4062-4825-8219-024f997173f8"
        val store = InMemoryStore().apply {
            lic = StoredLicense(json, id, "2026-10-03T20:03:02.754Z", TipoLicencia.MENSUAL, "2026-11-02T20:03:02.754Z", deviceId)
        }
        // Fecha fija dentro de su vigencia: tipo, vencimiento y secundarias tal como los emitió GL.
        val vigente = LicenseManager(store, testDevice, deviceId, ecdh(), LicenseTrust.signSpkis(), { Instant.parse("2026-10-10T00:00:00Z") })
        assertEquals(
            LicenseState.Active(TipoLicencia.MENSUAL, Instant.parse("2026-11-02T20:03:02.754Z"), id, 2),
            vigente.state(),
        )
        // Con otra clave de dispositivo no vale.
        val otro = LicenseManager(store, InMemoryDeviceKey(), deviceId, ecdh(), LicenseTrust.signSpkis(), { Instant.parse("2026-10-10T00:00:00Z") })
        assertTrue(otro.state() is LicenseState.Trial)
    }
}
