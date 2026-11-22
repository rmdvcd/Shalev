package cu.shalev.licencia

import cu.shalev.licencia.contract.MensajesLicencia
import cu.shalev.licencia.contract.RequestPayload
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.contract.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T3: Validación y formateo del campo 'tx' (N.º de transferencia) en Android.
 */
class SolicitudTransferenciaTest {

    private val basePayload = RequestPayload(
        nombre = "Juan",
        apellidos = "Perez",
        ci = "90010112345",
        telefono = "+5351234567",
        tipo = TipoLicencia.MENSUAL,
        secundarias = 1,
        deviceId = "SPVI:testdevice123",
        devicePub = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
        solicitadaEn = "2026-11-20T12:00:00Z",
        nonce = "abc123nonce",
        via = Via.WHATSAPP,
    )

    @Test
    fun solicitudSinTransferenciaNoIncluyeLinea() {
        val msg = MensajesLicencia.solicitud(basePayload, "SPVIR1:CODIGO")
        assertFalse(msg.contains("Transferencia:"))
        assertTrue(msg.contains(MensajesLicencia.AVISO_FRESCURA))
    }

    @Test
    fun solicitudConTransferenciaIncluyeLineaAntesDeAvisoFrescura() {
        val payloadConTx = basePayload.copy(tx = "BR601ADLM8997")
        val msg = MensajesLicencia.solicitud(payloadConTx, "SPVIR1:CODIGO")
        assertTrue(msg.contains("Transferencia: BR601ADLM8997"))
        val idxTx = msg.indexOf("Transferencia: BR601ADLM8997")
        val idxAviso = msg.indexOf(MensajesLicencia.AVISO_FRESCURA)
        assertTrue("Transferencia debe ir antes del aviso de frescura", idxTx < idxAviso)
    }

    @Test
    fun constanteEnviarTxPorDefectoFalse() {
        assertFalse(MensajesLicencia.ENVIAR_TX_EN_SOLICITUD)
    }

    @Test
    fun patronTransferenciaValidaCorrectamente() {
        val regex = Regex("^[A-Za-z0-9-]{4,30}$")
        assertTrue("BR601ADLM8997".matches(regex))
        assertTrue("TX-123456".matches(regex))
        assertTrue("1234".matches(regex))
        assertTrue("A".repeat(30).matches(regex))

        assertFalse("123".matches(regex)) // < 4
        assertFalse("A".repeat(31).matches(regex)) // > 30
        assertFalse("TX@123".matches(regex)) // carácter inválido
    }
}
