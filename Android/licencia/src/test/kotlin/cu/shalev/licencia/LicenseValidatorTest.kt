package cu.shalev.licencia

import cu.shalev.licencia.contract.EstadoLicencia
import cu.shalev.licencia.contract.GlContract
import cu.shalev.licencia.contract.LicensePayload
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.crypto.EcP256
import cu.shalev.licencia.crypto.LicenseCryptoException
import java.time.Instant
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 0.21.9 (P59): reglas semánticas de [LicenseValidator] una a una (antes solo se probaban a través de LicenseManager).
 * Toda incoherencia es el mismo rechazo genérico (LicenseCryptoException), sin pistas.
 */
class LicenseValidatorTest {

    private val ahora = Instant.parse("2026-10-03T15:00:00Z")
    private val clave = EcP256.generate().public.encoded
    private val otraClave = EcP256.generate().public.encoded
    private val pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(clave) + "\n-----END PUBLIC KEY-----"

    private fun payload(
        tipo: TipoLicencia = TipoLicencia.MENSUAL,
        estado: EstadoLicencia = EstadoLicencia.ACTIVA,
        emitida: String = "2026-10-01T00:00:00Z",
        vence: String? = "2026-11-01T00:00:00Z",
        secundarias: Int? = null,
        id: String = "LIC-ABC",
        device: String = "dev-1",
        devicePub: String? = pem,
        v: Int = GlContract.VERSION,
    ) = LicensePayload(v = v, nombre = "Ana", apellidos = "Díaz", ci = "90020212345", deviceId = device, tipo = tipo,
        devicePub = devicePub, id = id, emitidaEn = emitida, venceEn = vence, estado = estado, secundarias = secundarias)

    private fun evaluar(p: LicensePayload, aadId: String = "LIC-ABC", device: String = "dev-1", pub: ByteArray = clave, now: Instant = ahora) =
        LicenseValidator.evaluate(p, aadId, device, pub, now)

    private fun rechazada(p: LicensePayload, aadId: String = "LIC-ABC", device: String = "dev-1", pub: ByteArray = clave) {
        assertThrows(LicenseCryptoException::class.java) { evaluar(p, aadId, device, pub) }
    }

    @Test fun activaVigenteConSecundariasPorDefectoOExplicitas() {
        assertEquals(LicenseState.Active(TipoLicencia.MENSUAL, Instant.parse("2026-11-01T00:00:00Z"), "LIC-ABC", 5), evaluar(payload()))
        assertEquals(LicenseState.Active(TipoLicencia.MENSUAL, Instant.parse("2026-11-01T00:00:00Z"), "LIC-ABC", 0), evaluar(payload(secundarias = 0)))
        assertEquals(10, (evaluar(payload(secundarias = 10)) as LicenseState.Active).secundarias)
        assertEquals("LIC-ABC", (evaluar(payload(), aadId = "lic-abc") as LicenseState.Active).id) // id sin distinguir mayúsculas
        evaluar(payload(devicePub = null))                                    // licencias sin clave del dispositivo
    }

    @Test fun activaVencidaPasaAExpiradaJustoAlVencer() {
        val vence = Instant.parse("2026-11-01T00:00:00Z")
        assertEquals(LicenseState.Expired(TipoLicencia.MENSUAL), evaluar(payload(), now = vence))
        assertEquals(LicenseState.Active::class, evaluar(payload(), now = vence.minusMillis(1))::class)
    }

    @Test fun perpetuaSinVencimiento() {
        assertEquals(LicenseState.Perpetual("LIC-ABC", 3), evaluar(payload(TipoLicencia.PERPETUA, EstadoLicencia.PERPETUA, vence = null, secundarias = 3)))
        assertEquals(LicenseState.Perpetual("LIC-ABC", 5), evaluar(payload(TipoLicencia.PERPETUA, EstadoLicencia.ACTIVA, vence = null)))
        rechazada(payload(TipoLicencia.PERPETUA, EstadoLicencia.PERPETUA, vence = "2027-01-01T00:00:00Z"))
        rechazada(payload(TipoLicencia.PERPETUA, EstadoLicencia.ACTIVA, vence = "2027-01-01T00:00:00Z"))
        rechazada(payload(TipoLicencia.ANUAL, EstadoLicencia.PERPETUA, vence = null))  // estado perpetua con tipo no perpetuo
    }

    @Test fun revocadaYVencidaPorElEmisor() {
        assertEquals(LicenseState.Revoked, evaluar(payload(estado = EstadoLicencia.REVOCADA)))
        assertEquals(LicenseState.Expired(TipoLicencia.ANUAL), evaluar(payload(TipoLicencia.ANUAL, EstadoLicencia.VENCIDA)))
    }

    @Test fun incoherenciasSonRechazoGenerico() {
        rechazada(payload(v = 2))                                             // otra versión del contrato
        rechazada(payload(), aadId = "LIC-OTRA")                              // el id no es el autenticado
        rechazada(payload(), device = "dev-2")                                // licencia de otro teléfono
        rechazada(payload(), pub = otraClave)                                 // clave del dispositivo distinta
        rechazada(payload(secundarias = 11))
        rechazada(payload(secundarias = -1))
        rechazada(payload(vence = null))                                      // activa sin vencimiento
        rechazada(payload(vence = "2026-10-01T00:00:00Z"))                    // vence = emitida
        rechazada(payload(vence = "2026-09-01T00:00:00Z"))                    // vence antes de emitirse
        rechazada(payload(emitida = "ayer"))
        rechazada(payload(vence = "2026-13-01T00:00:00Z"))
        rechazada(payload(estado = EstadoLicencia.REVOCADA, secundarias = 99))  // mal formada aunque esté revocada
    }
}
