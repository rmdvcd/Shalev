package cu.shalev.licencia

import cu.shalev.licencia.contract.MensajesLicencia
import cu.shalev.licencia.contract.RequestPayload
import cu.shalev.licencia.contract.TipoLicencia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1: Validación de constante AVISO_FRESCURA en Android:
 * - Texto exacto: "Vale 7 dias: enviala y paga cuanto antes; si pasa ese tiempo, genera otra solicitud."
 * - Caracteres 100% GSM-7 (sin tildes ni caracteres fuera de ASCII/GSM-7).
 * - Usado en solicitud, ampliación y recuperación.
 */
class SolicitudFrescuraTest {

    private val textoEsperado = "Vale 7 dias: enviala y paga cuanto antes; si pasa ese tiempo, genera otra solicitud."

    private val gsm7Chars = (
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001bÆæßÉ !\"#¤%&'()*+,-./" +
        "0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    ).toSet()

    @Test
    fun avisoFrescuraTextoExactoYGsm7() {
        assertEquals(textoEsperado, MensajesLicencia.AVISO_FRESCURA)
        for (ch in MensajesLicencia.AVISO_FRESCURA) {
            assertTrue("Carácter '$ch' debe ser GSM-7", gsm7Chars.contains(ch))
        }
    }

    @Test
    fun avisoFrescuraEstaEnSolicitudAmpliacionYRecuperacion() {
        val basePayload = RequestPayload(
            nombre = "Juan",
            apellidos = "Perez",
            ci = "90010112345",
            telefono = "+5351234567",
            tipo = TipoLicencia.MENSUAL,
            secundarias = 1,
            deviceId = "SPVI:testdevice123",
            devicePub = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
            requestedAt = "2026-11-20T12:00:00Z",
        )

        // 1. Solicitud
        val sol = MensajesLicencia.solicitud(basePayload, "SPVIR1:CODIGO")
        assertTrue(sol.contains(MensajesLicencia.AVISO_FRESCURA))

        // 2. Ampliación
        val ampPayload = basePayload.copy(amplia = "lic-12345", secundarias = 3)
        val amp = MensajesLicencia.ampliacion(ampPayload, "SPVIR1:CODIGO", secundariasPrevias = 1)
        assertTrue(amp.contains(MensajesLicencia.AVISO_FRESCURA))

        // 3. Recuperación
        val recPayload = basePayload.copy(recupera = "lic-12345")
        val rec = MensajesLicencia.recuperacion(recPayload, "SPVIR1:CODIGO")
        assertTrue(rec.contains(MensajesLicencia.AVISO_FRESCURA))
    }
}
