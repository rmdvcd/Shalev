package cu.shalev.licencia

import cu.shalev.licencia.contract.GlContract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Mitigación 8 (Opción A): la AAD del envelope v1 se canonicaliza con **longitud prefijada** (4 bytes big-endian por
 * campo), igual que `CryptoEngine.canonical` de GL. Sin ella, `"a|b"` con un campo que contenga `|` podría producir
 * el mismo texto que otra combinación de campos (inyección de delimitadores).
 */
class CanonicalAadTest {

    private fun esperado(vararg partes: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (p in partes) {
            val b = p.toByteArray(Charsets.UTF_8)
            out.write(byteArrayOf(((b.size ushr 24) and 0xff).toByte(), ((b.size ushr 16) and 0xff).toByte(), ((b.size ushr 8) and 0xff).toByte(), (b.size and 0xff).toByte()))
            out.write(b)
        }
        return out.toByteArray()
    }

    @Test fun laLongitudVaDelanteDeCadaCampo() {
        val partes = listOf("1", GlContract.ALG, "abc", GlContract.KID)
        assertArrayEquals(esperado(*partes.toTypedArray()), GlContract.canonical(partes))
        // 4 bytes de longitud + el texto, en orden.
        val bytes = GlContract.canonical(partes)
        assertEquals(4 + 1 + 4 + GlContract.ALG.length + 4 + 3 + 4 + GlContract.KID.length, bytes.size)
        assertEquals(1, bytes[3].toInt()) // longitud de "1"
        assertEquals('1'.code.toByte(), bytes[4])
    }

    @Test fun elRepartoDeCamposNoSePuedeConfundir() {
        // "a" + "bc" y "ab" + "c" dan textos distintos (con concatenación con barras no se distinguirían).
        assertNotEquals(
            GlContract.canonical(listOf("1", "a", "bc")).toList(),
            GlContract.canonical(listOf("1", "ab", "c")).toList(),
        )
        assertNotEquals(
            GlContract.canonical(listOf("1", "a|b", "c")).toList(),
            GlContract.canonical(listOf("1", "a", "b|c")).toList(),
        )
    }

    @Test fun laAadDeSolicitudYLalicenciaUsanLaCanonicalizacion() {
        val epk = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE"
        assertArrayEquals(
            esperado(GlContract.VERSION.toString(), GlContract.ALG, epk, GlContract.KID),
            GlContract.requestAad(epk),
        )
        // La heredada (solo para re-verificar licencias largas antiguas) es la de concatenación con barras, y NO
        // coincide con la canonicalizada.
        assertEquals("1|${GlContract.ALG}|$epk|${GlContract.KID}", GlContract.requestAadHeredada(epk).toString(Charsets.UTF_8))
        assertNotEquals(GlContract.requestAad(epk).toList(), GlContract.requestAadHeredada(epk).toList())
        assertArrayEquals(
            esperado(GlContract.VERSION.toString(), GlContract.ALG, "b7c29e10-91a4-4a25-9e6b-7c8d9e0f1a2b"),
            GlContract.licenseAad("b7c29e10-91a4-4a25-9e6b-7c8d9e0f1a2b"),
        )
        // Y la heredada sigue siendo la de antes (solo para re-verificar licencias ya emitidas).
        assertEquals(
            "1|${GlContract.ALG}|b7c29e10-91a4-4a25-9e6b-7c8d9e0f1a2b",
            GlContract.licenseAadHeredada("b7c29e10-91a4-4a25-9e6b-7c8d9e0f1a2b").toString(Charsets.UTF_8),
        )
    }
}
