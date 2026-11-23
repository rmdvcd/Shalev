package cu.shalev.licencia

import cu.shalev.licencia.contract.MensajesLicencia
import cu.shalev.licencia.contract.RequestPayload
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.contract.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-5: el mensaje de solicitud va por SMS. Un solo carácter fuera de GSM-7 obliga a la operadora a usar
 * UCS-2 (70 caracteres por parte en vez de 160). Los nombres con tilde se escriben en ASCII en la parte
 * legible, igual que DShalev; el payload cifrado conserva el nombre original.
 */
class MensajesGsm7Test {

    private val gsm7Basico = (
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001bÆæßÉ !\"#¤%&'()*+,-./" +
            "0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
        ).toSet()
    private val gsm7Extension = "^{}\\[~]|€".toSet()

    private fun base(nombre: String, apellidos: String) = RequestPayload(
        nombre = nombre,
        apellidos = apellidos,
        ci = "90010112345",
        telefono = "+5351234567",
        tipo = TipoLicencia.MENSUAL,
        secundarias = 2,
        deviceId = "SPVI:testdevice123",
        devicePub = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
        via = Via.WHATSAPP,
        solicitadaEn = "2026-11-20T12:00:00Z",
        nonce = "nonce123",
    )

    private fun fueraDeGsm7(texto: String): List<Char> =
        texto.toList().filter { it !in gsm7Basico && it !in gsm7Extension }.distinct()

    @Test
    fun nombresAcentuadosNoSacanElMensajeDeGsm7() {
        val casos = listOf(
            "José" to "Rodríguez Pérez",
            "Yoandry" to "Núñez Piña",
            "María Ángela" to "Hernández",
            "Lázaro" to "Ibáñez",
        )
        for ((nombre, apellidos) in casos) {
            val p = base(nombre, apellidos)
            val textos = listOf(
                MensajesLicencia.solicitud(p, "SPVIR1:CODIGO"),
                MensajesLicencia.ampliacion(p.copy(amplia = "lic-12345"), "SPVIR1:CODIGO", secundariasPrevias = 1),
                MensajesLicencia.recuperacion(p.copy(recupera = "lic-12345"), "SPVIR1:CODIGO"),
            )
            for (texto in textos) {
                assertTrue(
                    "$nombre $apellidos: caracteres fuera de GSM-7 ${fueraDeGsm7(texto)}",
                    fueraDeGsm7(texto).isEmpty(),
                )
            }
        }
    }

    @Test
    fun elNombreVisibleSeEscribeEnAscii() {
        val p = base("José", "Rodríguez Pérez")
        val solicitud = MensajesLicencia.solicitud(p, "SPVIR1:CODIGO")
        assertTrue(solicitud.lines().contains("Nombre: Jose Rodriguez Perez"))
        assertEquals("José", p.nombre)
    }
}
