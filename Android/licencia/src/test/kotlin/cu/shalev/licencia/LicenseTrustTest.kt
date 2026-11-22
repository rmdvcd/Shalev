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

    private val huellaEcdhGl = "sha256:0289:7025:2266:8423:1613:2ccd:18a7:3ecd:e0ec:878f:767f:e9a4:916b:d356:6771:a2e0"
    private val huellaFirmaGl = "sha256:d252:9bd9:0aa3:e8bb:d120:06e6:b4d0:7a23:9092:9a9e:7501:1a05:a80c:d994:aacb:64be"
    private val huellaFirma1022 = "sha256:113f:a5f6:af7f:f43e:a87e:5f16:c578:36bb:58d1:d73a:c7a8:bb65:568a:b0bf:6d1a:4b1c"
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
            listOf(huellaFirmaGl, huellaFirma1022, huellaFirma1005, huellaFirma1015b, huellaFirma1015a, huellaFirma0930)
                .map(LicenseTrust::normalizarHuella),
            LicenseTrust.signSpkis().map(EcP256::fingerprint),
        )
    }

    @Test fun lasClavesDeFirmaYDeCifradoSonDistintas() {
        assertFalse(LicenseTrust.signSpkis().any { it.contentEquals(LicenseTrust.ecdhSpki()!!) })
    }

    @Test fun huellaEnFormatoGlOHexPlano() {
        val hex = "028970252266842316132ccd18a73ecde0ec878f767fe9a4916bd3566771a2e0"
        assertEquals(hex, LicenseTrust.normalizarHuella(huellaEcdhGl))
        assertEquals(hex, LicenseTrust.normalizarHuella(" SHA256:0289:7025:2266:8423:1613:2CCD:18A7:3ECD:E0EC:878F:767F:E9A4:916B:D356:6771:A2E0 "))
        assertEquals(hex, LicenseTrust.normalizarHuella(hex))
    }

    @Test fun unaHuellaEquivocadaInvalidaLaClave() {
        val mala = LicenseTrust.PinnedKey(LicenseTrust.ECDH_KEY.spkiB64, huellaFirmaGl)
        with(LicenseTrust) { assertNull(mala.validated()) }
    }

    @Test fun cuandoDirectorioEfectivoExisteSusClavesMandanInclusoSiVacias() {
        val dirVacio = TrustDirectory.Directorio(
            v = 1,
            seq = 1,
            emitidaEn = java.time.Instant.parse("2026-11-20T00:00:00Z").epochSecond,
            claves = emptyList(),
        )
        TrustDirectory.effectiveDirectory = dirVacio
        try {
            assertTrue("Si hay directorio efectivo, no debe caer a claves fijas", LicenseTrust.signSpkis().isEmpty())
            assertNull("Si hay directorio efectivo, ecdh debe ser null sin caer a clave fija", LicenseTrust.ecdhSpki())
            assertFalse(LicenseTrust.canValidate)
        } finally {
            TrustDirectory.effectiveDirectory = null
        }
        // Sin directorio efectivo, vuelve al respaldo fijo
        assertTrue(LicenseTrust.signSpkis().isNotEmpty())
        assertNotNull(LicenseTrust.ecdhSpki())
        assertTrue(LicenseTrust.canValidate)
    }
}
