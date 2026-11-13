package cu.shalev.licencia

import cu.shalev.licencia.prueba.CifradoPrueba
import cu.shalev.licencia.prueba.CopiasCombinadas
import cu.shalev.licencia.prueba.DetectorRetroceso
import cu.shalev.licencia.prueba.LecturaCopia
import cu.shalev.licencia.prueba.MarcaReloj
import cu.shalev.licencia.prueba.Ofuscado
import cu.shalev.licencia.prueba.PngRegistro
import cu.shalev.licencia.prueba.RegistroExterno
import cu.shalev.licencia.prueba.RegistroPrueba
import cu.shalev.licencia.prueba.combinarCopias
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.26.0 (P74): registro de la prueba que sobrevive a desinstalar (todo en memoria; MediaStore no se prueba aquí). */
class RegistroPruebaTest {
    private val t0 = Instant.parse("2026-10-01T12:00:00Z")
    private val id = "3f2a9c0d1e4b5a67"

    @Test fun jsonConLosCamposPedidos() {
        val j = RegistroPrueba.aJson(RegistroPrueba(t0.toEpochMilli(), 7))
        assertTrue(j.contains("\"firstInstall\":${t0.toEpochMilli()}") && j.contains("\"trialDays\":7") && j.contains("\"version\":1"))
        assertEquals(RegistroPrueba(t0.toEpochMilli(), 7), RegistroPrueba.deJson(j))
        assertNull(RegistroPrueba.deJson("{\"firstInstall\":1,\"trialDays\":7,\"version\":2}"))
        assertNull(RegistroPrueba.deJson("no es json"))
    }

    @Test fun cifraConAesGcmIvDe12YSoloDescifraElMismoTelefono() {
        val c = CifradoPrueba(id)
        val r = RegistroPrueba(t0.toEpochMilli(), 7, lastSeen = t0.toEpochMilli())
        val blob = c.cifrar(r)
        assertEquals(12 + RegistroPrueba.aJson(r).toByteArray().size + 16, blob.size) // IV + texto + etiqueta de 128 bits
        assertFalse(blob.copyOfRange(0, 12).contentEquals(c.cifrar(r).copyOfRange(0, 12))) // IV aleatorio
        assertEquals(r, c.descifrar(blob))
        assertEquals(r, CifradoPrueba(id).descifrar(blob)) // la clave se deriva igual en cada arranque
        assertNull(CifradoPrueba("0000000000000000").descifrar(blob)) // otro teléfono
        assertNull(c.descifrar(blob.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() })) // modificado
        assertNull(c.descifrar(ByteArray(5)))
        assertNull(c.descifrar(null))
    }

    @Test fun pngValidoConElBlobDentro() {
        val blob = CifradoPrueba(id).cifrar(RegistroPrueba(t0.toEpochMilli(), 7))
        val png = PngRegistro.crear(blob)
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47), png.copyOfRange(0, 4))
        assertArrayEquals(blob, PngRegistro.extraer(png))
        assertNull(PngRegistro.extraer(png.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 1).toByte() })) // CRC
        assertNull(PngRegistro.extraer(byteArrayOf(1, 2, 3)))
        val img = javax.imageio.ImageIO.read(png.inputStream())
        assertEquals(PngRegistro.LADO, img.width) // los visores lo abren como una imagen normal
    }

    @Test fun nombresOfuscadosSinTextoPlano() {
        assertTrue(Ofuscado.nombreBin().matches(Regex("""\.sys_[0-9a-f]{8}\.bin""")))
        assertTrue(Ofuscado.nombreImagen().matches(Regex("""sys_[0-9a-f]{8}\.png""")))
        assertEquals(18, Ofuscado.sal().size)
        val clase = RegistroPrueba::class.java.classLoader.getResourceAsStream("cu/shalev/licencia/prueba/Ofuscado.class")!!.readBytes()
        assertFalse(String(clase, Charsets.ISO_8859_1).contains(String(Ofuscado.sal(), Charsets.UTF_8)))
    }

    @Test fun ganaElInicioMasAntiguoYSeIgnoranLasDanadas() {
        val viejo = RegistroPrueba(t0.minus(Duration.ofDays(20)).toEpochMilli(), 7, lastSeen = t0.minus(Duration.ofDays(1)).toEpochMilli())
        val nuevo = RegistroPrueba(t0.toEpochMilli(), 7, lastSeen = t0.toEpochMilli())
        val c = combinarCopias(mapOf(
            "interna" to listOf(LecturaCopia.Valida(nuevo)),
            "imagenes" to listOf(LecturaCopia.Ilegible, LecturaCopia.Valida(viejo)),
            "descargas" to listOf(LecturaCopia.Ausente),
            "documentos" to listOf(LecturaCopia.Ilegible),
        ))
        assertEquals(viejo.firstInstall, c.firstInstall)
        assertEquals(nuevo.lastSeen, c.lastSeen)
        assertEquals(setOf("imagenes", "documentos"), c.danadas)
        assertEquals(setOf("imagenes", "descargas", "documentos"), c.reescribir)
        assertFalse(c.primeraInstalacion)
        assertTrue(combinarCopias(mapOf("interna" to listOf(LecturaCopia.Ilegible))).primeraInstalacion) // dañada ≠ bloqueo
    }

    @Test fun relojMonotonoSoloEnElMismoArranque() {
        val h = 3_600_000L
        val antes = MarcaReloj(t0.toEpochMilli(), 1_000_000, 7)
        assertTrue(MarcaReloj(t0.toEpochMilli() - 3 * h, 1_000_000 + h, 7).retrocedioDesde(antes, 2 * h)) // atrasó 4 h
        assertFalse(MarcaReloj(t0.toEpochMilli() + h, 1_000_000 + h, 7).retrocedioDesde(antes, 2 * h))
        assertFalse(MarcaReloj(t0.toEpochMilli() - h, 1_000_000 + 10, 7).retrocedioDesde(antes, 2 * h)) // dentro de la tolerancia
        assertFalse(MarcaReloj(t0.toEpochMilli() - 9 * h, 500, 8).retrocedioDesde(antes, 2 * h)) // otro arranque: no se sabe
        assertEquals(antes, MarcaReloj.decodificar(antes.codificar()))
        assertNull(MarcaReloj.decodificar("x|y"))
    }

    // ------------------------------------------------------------------------------------------- con LicenseManager

    private val gl = FakeGl()
    private val clock = MutableClock(t0)
    private val device = InMemoryDeviceKey()

    private class Ext(var first: Long?, var last: Long? = null, val falla: Boolean = false) : RegistroExterno {
        var escrito: Pair<Long?, Long?>? = null
        override suspend fun sincronizar(trialStart: Long?, lastSeen: Long?, trialDays: Int): CopiasCombinadas<*> {
            if (falla) error("E/S")
            val r = CopiasCombinadas<String>(first, last, emptySet(), emptySet())
            escrito = listOfNotNull(first, trialStart).minOrNull() to listOfNotNull(last, lastSeen).maxOrNull()
            return r
        }
    }

    private fun manager(store: InMemoryStore, ext: RegistroExterno?, atras: Boolean = false) = LicenseManager(
        store, device, "SPVI:$id", gl.ecdh.public.encoded, listOf(gl.signing.public.encoded), clock,
        registroExterno = ext,
        detectorRetroceso = object : DetectorRetroceso { override suspend fun retrocedio(ahoraMs: Long) = atras },
    )

    @Test fun reinstalarNoReiniciaLaPrueba() = runBlocking {
        // App recién instalada (almacén vacío) pero la imagen de la instalación anterior dice que empezó hace 10 días.
        val ext = Ext(t0.minus(Duration.ofDays(10)).toEpochMilli())
        assertEquals(LicenseState.TrialExpired, manager(InMemoryStore(), ext).state())
        val ext2 = Ext(t0.minus(Duration.ofDays(3)).toEpochMilli())
        assertEquals(LicenseState.Trial(4), manager(InMemoryStore(), ext2).state())
    }

    @Test fun primeraInstalacionEmpiezaAhoraYLoDejaEscrito() = runBlocking {
        val store = InMemoryStore(); val ext = Ext(null)
        assertEquals(LicenseState.Trial(7), manager(store, ext).state())
        assertEquals(t0, store.trial)
        assertEquals(t0.toEpochMilli(), ext.escrito?.first)
    }

    @Test fun laInternaMasAntiguaTambienGana() = runBlocking {
        val store = InMemoryStore().apply { trial = t0.minus(Duration.ofDays(6)) }
        assertEquals(LicenseState.Trial(1), manager(store, Ext(t0.minus(Duration.ofDays(2)).toEpochMilli())).state())
        assertEquals(t0.minus(Duration.ofDays(6)), store.trial)
    }

    @Test fun relojAtrasadoTrasReinstalarSeDetecta() = runBlocking {
        // La última fecha vista (guardada fuera) es de mañana: el teléfono se atrasó y luego se reinstaló.
        val ext = Ext(t0.minus(Duration.ofDays(1)).toEpochMilli(), t0.plus(Duration.ofDays(1)).toEpochMilli())
        assertEquals(LicenseState.ClockTampered, manager(InMemoryStore(), ext).state())
    }

    @Test fun relojMonotonoBloqueaComoReloj() = runBlocking {
        assertEquals(LicenseState.ClockTampered, manager(InMemoryStore(), Ext(null), atras = true).state())
    }

    @Test fun unFalloDelRegistroNoBloqueaNada() = runBlocking {
        assertEquals(LicenseState.Trial(7), manager(InMemoryStore(), Ext(null, falla = true)).state())
    }

    @Test fun sinRegistroFuncionaComoAntes() = runBlocking {
        assertEquals(LicenseState.Trial(7), manager(InMemoryStore(), null).state())
    }

    @Test fun licenciaValidaMandaAunqueLaPruebaVenciera() = runBlocking {
        val store = InMemoryStore()
        val m = manager(store, Ext(t0.minus(Duration.ofDays(30)).toEpochMilli()))
        assertEquals(LicenseState.TrialExpired, m.state())
        val req = gl.openRequest(m.buildRequest(SolicitudInput("María", "Pérez González", "85010112345", "5234 5678",
            cu.shalev.licencia.contract.TipoLicencia.ANUAL, cu.shalev.licencia.contract.Via.WHATSAPP)).texto)
        val iss = gl.issue(req, t0)
        assertNotNull(iss)
        assertTrue(m.activate(iss.message) is ActivationResult.Accepted)
        assertTrue(m.state() is LicenseState.Active)
    }
}
