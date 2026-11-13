package cu.shalev.licencia

import cu.shalev.licencia.crypto.EcP256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Claves ancladas: el emisor GL vigente y las firmas históricas conocidas. */
class LicenseTrustTest {

    private val huellaEcdhGl = "sha256:fb3a:f7fc:e8a2:e86d:4e83:5351:d677:6aec:a9b6:5071:21d7:a417:9cd7:4b1b:0a53:078d"
    private val huellaFirmaGl = "sha256:113f:a5f6:af7f:f43e:a87e:5f16:c578:36bb:58d1:d73a:c7a8:bb65:568a:b0bf:6d1a:4b1c"
    private val huellaFirma1005 = "sha256:58d4:3aac:6f1b:099a:e86c:4375:b7c7:ffb5:8631:2399:246c:1f26:f861:e302:9c83:8c4e"
    private val huellaFirma1015b = "sha256:e693:6c20:2461:57af:0f91:2313:94a4:80a5:0d8c:d150:34a1:091b:08af:b121:2828:0a6e"
    private val huellaFirma1015a = "sha256:d9c4:d821:1880:bc79:dda6:f92a:0c0d:f6a4:dc0d:a00a:191f:9c05:229d:4c1f:3be7:998e"
    private val huellaFirma0930 = "sha256:8a91:bcfb:19dc:af1b:af6e:0bb7:c82a:9410:e9b5:aa1d:fea0:cab8:241c:52d1:bcbd:6888"

    @Test fun ecdhKeyIsTheCurrentGlEmitter() {
        assertTrue("LicenseTrust.ECDH_KEY vacía", LicenseTrust.ECDH_KEY.spkiB64.isNotBlank())
        assertNotNull("LicenseTrust.ECDH_KEY no parsea o no coincide con su huella", LicenseTrust.ecdhSpki())
        assertEquals(LicenseTrust.normalizarHuella(huellaEcdhGl), EcP256.fingerprint(LicenseTrust.ecdhSpki()!!))
    }

    @Test fun allKnownGlSigningKeysArePinnedAndValid() {
        assertTrue("LicenseTrust.SIGN_KEYS vacío", LicenseTrust.SIGN_KEYS.isNotEmpty())
        assertEquals(LicenseTrust.SIGN_KEYS.size, LicenseTrust.signSpkis().size)
        assertTrue(LicenseTrust.canValidate)
        assertEquals(
            listOf(huellaFirmaGl, huellaFirma1005, huellaFirma1015b, huellaFirma1015a, huellaFirma0930)
                .map(LicenseTrust::normalizarHuella),
            LicenseTrust.signSpkis().map(EcP256::fingerprint),
        )
    }

    @Test fun lasClavesDeFirmaYDeCifradoSonDistintas() {
        assertFalse(LicenseTrust.signSpkis().any { it.contentEquals(LicenseTrust.ecdhSpki()!!) })
    }

    @Test fun huellaEnFormatoGlOHexPlano() {
        val hex = "fb3af7fce8a2e86d4e835351d6776aeca9b6507121d7a4179cd74b1b0a53078d"
        assertEquals(hex, LicenseTrust.normalizarHuella(huellaEcdhGl))
        assertEquals(hex, LicenseTrust.normalizarHuella(" SHA256:FB3A:F7FC:E8A2:E86D:4E83:5351:D677:6AEC:A9B6:5071:21D7:A417:9CD7:4B1B:0A53:078D "))
        assertEquals(hex, LicenseTrust.normalizarHuella(hex))
    }

    @Test fun unaHuellaEquivocadaInvalidaLaClave() {
        val mala = LicenseTrust.PinnedKey(LicenseTrust.ECDH_KEY.spkiB64, huellaFirmaGl)
        with(LicenseTrust) { assertNull(mala.validated()) }
    }
}
