package cu.shalev.licencia

import cu.shalev.licencia.crypto.B64
import cu.shalev.licencia.redeem.RedeemContract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * T4: Pruebas del contrato de canje por código en Android:
 * - Normalización de códigos (alfabeto sin I ni O, 16 símbolos).
 * - Transcript firmado canónico para solicitud de canje contra el vector oficial.
 * - Mensajes genéricos de error para evitar oráculos de existencia.
 */
class RedeemContractTest {

    @Test
    fun normalizarCodigoAceptaFormatosValidos() {
        assertEquals("4K7N9X2MB8PQ3T5V", RedeemContract.normalizarCodigo("4K7N-9X2M-B8PQ-3T5V"))
        assertEquals("4K7N9X2MB8PQ3T5V", RedeemContract.normalizarCodigo(" 4k7n 9x2m b8pq 3t5v "))
        assertEquals("4K7N9X2MB8PQ3T5V", RedeemContract.normalizarCodigo("4K7N9X2MB8PQ3T5V"))
    }

    @Test
    fun formatearCodigoInsertaGuionesEnGruposDeCuatro() {
        assertEquals("4K7N-9X2M-B8PQ-3T5V", RedeemContract.formatearCodigo("4K7N9X2MB8PQ3T5V"))
    }

    @Test
    fun normalizarCodigoRechazaSimbolosInvalidosOLongitudErronea() {
        assertThrows(IllegalArgumentException::class.java) {
            RedeemContract.normalizarCodigo("4K7N-9X2M-B8PQ-3T5") // 15
        }
        assertThrows(IllegalArgumentException::class.java) {
            RedeemContract.normalizarCodigo("4K7N-9X2M-B8PQ-3T5VV") // 17
        }
        assertThrows(IllegalArgumentException::class.java) {
            RedeemContract.normalizarCodigo("ABCD-EFGH-JKLM-NPQI") // I excluida
        }
        assertThrows(IllegalArgumentException::class.java) {
            RedeemContract.normalizarCodigo("ABCD-EFGH-JKLM-NPQO") // O excluida
        }
    }

    @Test
    fun transcriptFirmadoCanjeCoincideConVectorOficial() {
        val bytes = RedeemContract.bytesPruebaCanje(
            appId = "DSHALEV",
            code = "4K7N9X2MB8PQ3T5V",
            deviceKeyB64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEsg/+aDd+3cH8ZhZK5n77JMi/+seJn1aVw+l7XmJsOAbQRsvlmYx5suxdvhkhIbte6N8pFn196CH/jv0I9ipxog==",
            requestId = "11111111-1111-4111-8111-111111111111",
            requestedAt = "2026-11-10T12:00:00.000Z",
            nonceB64 = "AAECAwQFBgcICQoLDA0ODw==",
        )
        val esperadoB64 = "AAAAEkdMLVJFREVFTS1SRVFVRVNUMQAAAAExAAAAB0RTSEFMRVYAAAAQNEs3TjlYMk1COFBRM1Q1VgAAAHxNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUVzZy8rYURkKzNjSDhaaFpLNW43N0pNaS8rc2VKbjFhVncrbDdYbUpzT0FiUVJzdmxtWXg1c3V4ZHZoa2hJYnRlNk44cEZuMTk2Q0gvanYwSTlpcHhvZz09AAAAJDExMTExMTExLTExMTEtNDExMS04MTExLTExMTExMTExMTExMQAAABgyMDI2LTExLTEwVDEyOjAwOjAwLjAwMFoAAAAYQUFFQ0F3UUZCZ2NJQ1FvTERBME9Edz09"
        assertArrayEquals(B64.dec(esperadoB64), bytes)
    }

    @Test
    fun mensajeCodigoInvalidoOUsadoEsGenerico() {
        assertEquals(
            "Código no válido para esta aplicación o ya utilizado",
            RedeemContract.MENSAJE_CODIGO_INVALIDO_O_USADO,
        )
        assertEquals(
            "Aún no disponible en esta edición",
            RedeemContract.MOTIVO_AUN_NO_DISPONIBLE,
        )
    }
}

    /**
     * Fase 1.3: la base de canje publicada en el directorio es la única fuente del endpoint. Se valida la
     * regla pura que usan el repositorio y la pantalla, sin red ni claves reales.
     */
    @Test
    fun canjeSeCierraSiFaltaBaseHttpsOClaveDeCanje() {
        assertTrue(cu.shalev.licencia.redeem.RedeemContract.canjeActivo(
            habilitado = true, appId = "SHALEV-ANDROID", endpointBase = "https://licencias.ejemplo.test", tieneClaveCanje = true,
        ))
        assertFalse(cu.shalev.licencia.redeem.RedeemContract.canjeActivo(
            habilitado = false, appId = "SHALEV-ANDROID", endpointBase = "https://licencias.ejemplo.test", tieneClaveCanje = true,
        ))
        assertFalse(cu.shalev.licencia.redeem.RedeemContract.canjeActivo(
            habilitado = true, appId = "", endpointBase = "https://licencias.ejemplo.test", tieneClaveCanje = true,
        ))
        assertFalse(cu.shalev.licencia.redeem.RedeemContract.canjeActivo(
            habilitado = true, appId = "SHALEV-ANDROID", endpointBase = "https://licencias.ejemplo.test", tieneClaveCanje = false,
        ))
        assertFalse(cu.shalev.licencia.redeem.RedeemContract.canjeActivo(
            habilitado = true, appId = "SHALEV-ANDROID", endpointBase = "http://licencias.ejemplo.test", tieneClaveCanje = true,
        ))
    }
