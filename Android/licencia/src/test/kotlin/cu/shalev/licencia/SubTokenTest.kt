package cu.shalev.licencia

import cu.shalev.licencia.crypto.B64
import cu.shalev.licencia.crypto.EcP256
import cu.shalev.licencia.secondary.FirmanteLicencia
import cu.shalev.licencia.secondary.SubToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Mitigación 7 (Opción A): la cuota de apps secundarias es un **permiso firmado** por la app principal
 * (`SISTEMA_LICENCIAS_CLIENTES.md` §8.4), no una auto-declaración de la secundaria.
 */
class SubTokenTest {
    private val t0 = Instant.parse("2026-10-13T12:00:00Z")
    private val principal = EcP256.generate()
    private val ajena = EcP256.generate()
    private val licenciaId = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    private val secundaria = "SPVI:01a2b3c4d5e6f708"

    private val firmante = object : FirmanteLicencia {
        override fun publicSpki(): ByteArray = principal.public.encoded
        override fun firmar(datos: ByteArray): ByteArray = EcP256.sign(principal.private, datos)
    }

    private fun emitir(
        ranura: Int = 1,
        permitidas: Int = 3,
        venceEn: Instant? = t0.plus(Duration.ofDays(30)),
        dispositivo: String = secundaria,
    ) = SubToken.emitir(firmante, licenciaId, permitidas, venceEn, dispositivo, ranura)

    /** Firma el texto indicado con la clave de la principal (para construir vectores «válidos» a mano). */
    private fun firmar(texto: String): String = texto + "." + B64.enc(EcP256.sign(principal.private, texto.toByteArray(Charsets.UTF_8)))

    @Test fun idaYVuelta() {
        val token = emitir(ranura = 2, permitidas = 3)
        val datos = SubToken.verificar(token, firmante.publicSpki(), secundaria, t0, cuotaMaxima = 3)
        assertNotNull(datos)
        assertEquals(licenciaId, datos!!.licenciaId)
        assertEquals(secundaria, datos.dispositivo)
        assertEquals(2, datos.ranura)
        assertEquals(3, datos.permitidas)
        assertEquals(t0.plus(Duration.ofDays(30)), datos.venceEn)
        assertTrue(datos.vigente(t0))
    }

    @Test fun laFirmaTieneQueSerDeLaPrincipal() {
        val token = emitir()
        assertNull(SubToken.verificar(token, ajena.public.encoded, secundaria, t0, cuotaMaxima = 3))
        // Un carácter cambiado en la firma o en el texto → rechazo.
        assertNull(SubToken.verificar(token.dropLast(1) + "A", firmante.publicSpki(), secundaria, t0, cuotaMaxima = 3))
        assertNull(SubToken.verificar(token.replaceFirst("|1|", "|9|"), firmante.publicSpki(), secundaria, t0, cuotaMaxima = 3))
    }

    @Test fun elDispositivoTieneQueCoincidir() {
        val token = emitir(dispositivo = "SPVI:otro000000000000")
        assertNull(SubToken.verificar(token, firmante.publicSpki(), secundaria, t0, cuotaMaxima = 3))
    }

    @Test fun laRanuraVaDentroDeLaCuota() {
        // El emisor no deja firmar una ranura fuera de la cuota.
        assertTrue(runCatching { emitir(ranura = 0, permitidas = 3) }.isFailure)
        assertTrue(runCatching { emitir(ranura = 4, permitidas = 3) }.isFailure)
        // Y el verificador tampoco acepta una ranura fuera de rango aunque venga firmada.
        val fuera = firmar("$licenciaId|$secundaria|7|3|${t0.plus(Duration.ofDays(30))}")
        assertNull(SubToken.verificar(fuera, firmante.publicSpki(), secundaria, t0, cuotaMaxima = 10))
    }

    @Test fun laCuotaNoPuedePasarDeLaDeLaLicencia() {
        val diez = emitir(ranura = 1, permitidas = 10)
        assertNotNull(SubToken.verificar(diez, firmante.publicSpki(), secundaria, t0, cuotaMaxima = 10))
        // La licencia de la principal solo cubre 5: el sub-token de 10 no autoriza.
        assertNull(SubToken.verificar(diez, firmante.publicSpki(), secundaria, t0, cuotaMaxima = 5))
        val fuera = firmar("$licenciaId|$secundaria|1|11|${t0.plus(Duration.ofDays(30))}")
        assertNull(SubToken.verificar(fuera, firmante.publicSpki(), secundaria, t0, cuotaMaxima = 10))
    }

    @Test fun unSubTokenVencidoNoAutoriza() {
        val vencido = emitir(venceEn = t0.minus(Duration.ofDays(1)))
        assertNull(SubToken.verificar(vencido, firmante.publicSpki(), secundaria, t0, cuotaMaxima = 3))
        // El mismo token «caduca» al pasar la fecha: la secundaria lo reevalúa en cada arranque.
        val valido = emitir(venceEn = t0.plus(Duration.ofDays(1)))
        assertNotNull(SubToken.verificar(valido, firmante.publicSpki(), secundaria, t0, cuotaMaxima = 3))
        assertNull(SubToken.verificar(valido, firmante.publicSpki(), secundaria, t0.plus(Duration.ofDays(2)), cuotaMaxima = 3))
    }

    @Test fun laPerpetuaNoCaduca() {
        val perpetua = emitir(venceEn = null)
        assertTrue(perpetua.startsWith("$licenciaId|$secundaria|1|3|${SubToken.PERPETUA}."))
        assertNotNull(SubToken.verificar(perpetua, firmante.publicSpki(), secundaria, t0.plus(Duration.ofDays(3650)), cuotaMaxima = 3))
    }

    @Test fun losSeparadoresNoSePuedenColar() {
        // Sin la canonicalización de campos, un `|` o un `.` dentro de un campo cambiaría el sentido del token.
        assertTrue(runCatching { emitir(dispositivo = "SPVI:a|b") }.isFailure)
        assertTrue(runCatching { SubToken.emitir(firmante, "id|falso", 3, null, secundaria, 1) }.isFailure)
        assertTrue(runCatching { SubToken.emitir(firmante, "id.falso", 3, null, secundaria, 1) }.isFailure)
    }

    @Test fun basuraNuncaLanza() {
        for (texto in listOf("", ".", "a.b", "|||||.AAA", "x".repeat(5000), "a|b|c|d|e.firma")) {
            assertNull(SubToken.verificar(texto, firmante.publicSpki(), secundaria, t0, cuotaMaxima = 10))
        }
    }
}
