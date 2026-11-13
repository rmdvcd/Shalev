package cu.shalev.app.respaldo

import cu.shalev.app.FakeArchivos
import cu.shalev.app.FakeExportador
import cu.shalev.app.PerfilRepo
import cu.shalev.app.PreciosRepo
import cu.shalev.app.ProdRepo
import cu.shalev.app.RelojFijo
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.repository.EtapaRespaldo
import cu.shalev.domain.repository.InfoRespaldo
import cu.shalev.domain.repository.RespaldoRepository
import cu.shalev.domain.repository.ResumenRespaldo
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.usecase.ExportarRespaldo
import cu.shalev.domain.usecase.ImportarRespaldo
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Archivo "CIFRADO" = respaldo v3 intacto; "CORTADO" = incompleto; "DANADO" = bytes cambiados;
 * "V2" = formato anterior (ya no se abre); cualquier otra cosa = no es de Shalev.
 */
private class RespaldoRepo : RespaldoRepository {
    var exportados = 0
    var contrasenaVista: String? = null
    var fallarExport = false
    var importadoSinContrasena: String? = null
    val etapas = mutableListOf<EtapaRespaldo>()
    override suspend fun exportar(contrasena: CharArray, destino: OutputStream, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> {
        if (fallarExport) return AppResult.Err(AppError.Almacenamiento)
        listOf(EtapaRespaldo.PREPARANDO, EtapaRespaldo.CIFRANDO, EtapaRespaldo.ESCRIBIENDO).forEach { etapas += it; progreso(it) }
        exportados++; contrasenaVista = String(contrasena)
        destino.write("CIFRADO".toByteArray())
        return AppResult.Ok(ResumenRespaldo(3, 2, 10, 5))
    }
    override suspend fun inspeccionar(origen: InputStream): AppResult<InfoRespaldo> = when (String(origen.readBytes())) {
        "CIFRADO" -> AppResult.Ok(InfoRespaldo(3, Instant.parse("2026-09-29T15:00:00Z"), 7))
        "ABIERTO" -> AppResult.Ok(InfoRespaldo(4, Instant.parse("2026-09-29T15:00:00Z"), 7, conContrasena = false))
        "V2" -> AppResult.Err(AppError.FormatoInvalido("respaldo de una versión anterior de Shalev (2)"))
        "CORTADO" -> AppResult.Err(AppError.ArchivoDanado(incompleto = true))
        "DANADO" -> AppResult.Err(AppError.ArchivoDanado(incompleto = false))
        else -> AppResult.Err(AppError.FormatoInvalido("no es un respaldo Shalev"))
    }
    override suspend fun importar(origen: InputStream, contrasena: CharArray, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> {
        val datos = String(origen.readBytes())
        if (datos == "ABIERTO") {                                                    // 0.27.0 (T10): sin contraseña
            importadoSinContrasena = String(contrasena)
            return AppResult.Ok(ResumenRespaldo(1, 0, 2, 0))
        }
        if (datos != "CIFRADO") return AppResult.Err(AppError.FormatoInvalido("x"))
        listOf(EtapaRespaldo.LEYENDO, EtapaRespaldo.VERIFICANDO, EtapaRespaldo.DESCIFRANDO).forEach { etapas += it; progreso(it) }
        if (String(contrasena) != "correcta1") return AppResult.Err(AppError.ContrasenaIncorrecta)
        etapas += EtapaRespaldo.IMPORTANDO; progreso(EtapaRespaldo.IMPORTANDO)
        return AppResult.Ok(ResumenRespaldo(1, 0, 2, 0))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RespaldoTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private val repo = RespaldoRepo()
    private val archivos = FakeArchivos()
    private val exportador = FakeExportador()
    private val zona = ZoneId.of("America/Havana")

    private fun vm() = RespaldoViewModel(
        ExportarRespaldo(repo), ImportarRespaldo(repo), repo,
        archivos, RelojFijo(), cu.shalev.app.actualizacion.RespaldoPrevio(
            java.io.File(System.getProperty("java.io.tmpdir"), "copia_previa_test_inexistente"), ExportarRespaldo(repo), ImportarRespaldo(repo), cu.shalev.app.EstadoAppRepo(),
        ),
    )

    // ---------- lógica

    @Test fun nombreSinDatosPersonales() {
        assertEquals("Shalev_respaldo_2026-09-30.shalev", nombreRespaldo(LocalDate.of(2026, 9, 30)))
    }

    @Test fun contrasenaMinimaYRepetida() {
        assertEquals(TextosRespaldo.ERROR_CONTRASENA, ExportForm(true, "corta", "corta").errorContrasena)
        assertEquals(TextosRespaldo.ERROR_REPETIR, ExportForm(true, "larga123", "larga124").errorRepetir)
        assertTrue(ExportForm(true, "larga123", "larga123").valido)
    }

    @Test fun resumenSoloConteos() {
        assertEquals("Respaldo guardado: 0 productos, 0 insumos, 0 ventas", resumenRespaldo(ResumenRespaldo(0, 0, 0, 0), false))
        assertEquals(
            "Respaldo importado: 1 producto, 0 insumos, 2 ventas",
            resumenRespaldo(ResumenRespaldo(1, 0, 2, 0), true),
        )
    }

    @Test fun etapasConPasoYTotal() {
        assertEquals(Progreso(2, 3, "Cifrando con tu contraseña…"), EtapasRespaldo.progreso(EtapaRespaldo.CIFRANDO))
        val p = EtapasRespaldo.progreso(EtapaRespaldo.DESCIFRANDO)
        assertEquals("Etapa 3 de 4 · Comprobando la contraseña…", p.etiqueta)
        assertEquals(0.75f, p.fraccion)
    }

    @Test fun fallosExplicadosSinCulparALaContrasena() {
        assertEquals("El archivo está incompleto", fallo(AppError.ArchivoDanado(true)).titulo)
        assertEquals("El archivo está dañado", fallo(AppError.ArchivoDanado(false)).titulo)
        assertEquals("Respaldo de una versión más nueva", fallo(AppError.FormatoInvalido("respaldo de una versión más nueva de Shalev (3)")).titulo)
        assertEquals("Respaldo de una versión anterior", fallo(AppError.FormatoInvalido("respaldo de una versión anterior de Shalev (2)")).titulo)
        assertEquals(TextosRespaldo.FORMATO, fallo(AppError.FormatoInvalido("no es un respaldo Shalev")).titulo)
        listOf(AppError.ArchivoDanado(true), AppError.FormatoInvalido("x"), AppError.Almacenamiento).forEach {
            assertFalse(fallo(it).exito)
            assertTrue(fallo(it).detalle.endsWith("Tus datos no se han tocado."))
        }
        // El archivo ya se comprobó intacto antes de pedir la contraseña: el error solo puede ser la contraseña.
        assertEquals(TextosRespaldo.CONTRASENA_INCORRECTA, mensajeRespaldo(AppError.ContrasenaIncorrecta))
    }

    @Test fun describeElArchivoYAvisaQueSeReemplaza() {
        val info = InfoRespaldo(3, Instant.parse("2026-09-29T15:00:00Z"), 7)
        assertEquals("Respaldo del 29/09/2026", describirArchivo(info, zona))
        assertEquals("Respaldo de Shalev", describirArchivo(info.copy(creadoEn = null), zona))
        assertEquals("Respaldo de Shalev", describirArchivo(null, zona))
        assertTrue(TextosRespaldo.AVISO_IMPORTAR.contains("TODOS"))
    }

    // ---------- exportar

    @Test fun guardarEnTelefonoPideDestinoEscribeCifradoYConfirma() = runTest {
        val vm = vm(); val ev = mutableListOf<EventoRespaldo>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { ev += it } }
        vm.editarExport { it.copy(conContrasena = true) }
        vm.guardarEnTelefono() // protegido pero sin contraseña: errores, nada más
        assertTrue(vm.state.value.export.mostrarErrores)
        assertTrue(ev.isEmpty())
        vm.editarExport { it.copy(conContrasena = true, contrasena = "secreta12", repetir = "secreta12") }
        vm.guardarEnTelefono()
        assertEquals(EventoRespaldo.ElegirDestino("Shalev_respaldo_2026-09-30.shalev"), ev.single())
        vm.destinoElegido("content://doc/1")
        assertArrayEquals("CIFRADO".toByteArray(), archivos.destinos["content://doc/1"]!!.toByteArray())
        assertEquals(1, repo.exportados)
        assertEquals("secreta12", repo.contrasenaVista)
        assertEquals(listOf(EtapaRespaldo.PREPARANDO, EtapaRespaldo.CIFRANDO, EtapaRespaldo.ESCRIBIENDO), repo.etapas)
        // Confirmación: diálogo con el nombre y el recordatorio de la contraseña; progreso terminado.
        val r = vm.state.value.resultado!!
        assertTrue(r.exito)
        assertEquals("Respaldo guardado", r.titulo)
        assertTrue(r.detalle.startsWith("Shalev_respaldo_2026-09-30.shalev\nRespaldo guardado: 3 productos, 2 insumos, 10 ventas."))
        assertNull(vm.state.value.progreso)
        assertFalse(vm.state.value.ocupado)
        // La contraseña no se queda en pantalla.
        assertEquals(ExportForm(), vm.state.value.export)
        vm.cerrarResultado()
        assertNull(vm.state.value.resultado)
        // P18 (pregunta 3): el asistente queda en el paso 3 «Respaldo listo» hasta «Hacer otro respaldo».
        assertEquals(PasosRespaldo.textoHecho("Shalev_respaldo_2026-09-30.shalev", enviado = false), vm.state.value.exportado)
        vm.nuevoRespaldo()
        assertNull(vm.state.value.exportado)
    }

    /**
     * 0.27.0 (T10): la contraseña es opcional. Auditoría 2026-10-14 (S-01): viene ENCENDIDA por defecto (el modo
     * sin contraseña solo ofusca); sin contraseña solo se exporta si el usuario la apaga expresamente.
     */
    @Test fun exportarSinContrasenaSoloSiSeApaga() = runTest {
        val vm = vm(); val ev = mutableListOf<EventoRespaldo>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { ev += it } }
        assertTrue(vm.state.value.export.conContrasena)
        vm.editarExport { it.copy(conContrasena = false, contrasena = "quedo-escrita", repetir = "x") } // apagada: se ignora
        assertTrue(vm.state.value.export.valido)
        vm.guardarEnTelefono()
        assertEquals(EventoRespaldo.ElegirDestino("Shalev_respaldo_2026-09-30.shalev"), ev.single())
        vm.destinoElegido("content://doc/2")
        assertEquals("", repo.contrasenaVista)
        assertTrue(vm.state.value.resultado!!.detalle.contains(TextosRespaldo.SIN_CONTRASENA_AVISO))
        assertTrue(vm.state.value.exportado!!.contains(TextosRespaldo.SIN_CONTRASENA_AVISO))
    }

    @Test fun cancelarElSelectorNoHaceNada() = runTest {
        val vm = vm()
        vm.destinoElegido(null)
        assertEquals(0, repo.exportados)
    }

    // ---------- importar

    @Test fun primeroSeCompruebaElArchivoLuegoLaContrasenaConProgresoYConfirmacion() = runTest {
        archivos.origenes["content://r"] = "CIFRADO".toByteArray()
        val vm = vm()
        vm.archivoElegido("content://r", "Shalev_completo_2026-09-29.shalev")
        val imp = vm.state.value.importacion!!
        assertEquals(3, imp.info!!.version)
        assertEquals("Shalev_completo_2026-09-29.shalev", imp.nombre)
        assertTrue(repo.etapas.isEmpty()) // aún no se ha descifrado nada
        vm.editarImport("mala")
        vm.confirmarImport()
        assertEquals(TextosRespaldo.CONTRASENA_INCORRECTA, vm.state.value.importacion!!.error)
        assertEquals("", vm.state.value.importacion!!.contrasena)
        repo.etapas.clear()
        vm.editarImport("correcta1")
        vm.confirmarImport()
        assertNull(vm.state.value.importacion)
        assertEquals(EtapaRespaldo.entries.drop(3), repo.etapas)
        assertEquals(ResultadoRespaldo(true, "Respaldo importado", "Respaldo importado: 1 producto, 0 insumos, 2 ventas."), vm.state.value.resultado)
        assertNull(vm.state.value.progreso)
    }

    @Test fun archivoCortadoDanadoOAjenoSeExplicaSinPedirContrasena() = runTest {
        val vm = vm()
        mapOf(
            "CORTADO" to "El archivo está incompleto", "DANADO" to "El archivo está dañado",
            "V2" to "Respaldo de una versión anterior", "JPEG" to TextosRespaldo.FORMATO,
        ).forEach { (datos, titulo) ->
            archivos.origenes["content://$datos"] = datos.toByteArray()
            vm.archivoElegido("content://$datos")
            assertNull(vm.state.value.importacion)
            assertEquals(titulo, vm.state.value.resultado!!.titulo)
            vm.cerrarResultado()
        }
        vm.archivoElegido("content://no-existe")
        assertEquals(TextosRespaldo.NO_SE_PUDO_LEER, vm.state.value.resultado!!.titulo)
        assertTrue(repo.etapas.isEmpty())
    }

    @Test fun importarSinContrasenaNoLeeElArchivo() = runTest {
        archivos.origenes["content://r"] = "CIFRADO".toByteArray()
        val vm = vm()
        vm.archivoElegido("content://r")
        vm.confirmarImport()
        assertNotNull(vm.state.value.importacion!!.error)
        assertTrue(repo.etapas.isEmpty())
    }

    /** 0.27.0 (T10): un archivo sin contraseña se importa tras confirmar, sin pedirla. */
    @Test fun archivoSinContrasenaNoLaPide() = runTest {
        archivos.origenes["content://a"] = "ABIERTO".toByteArray()
        val vm = vm()
        vm.archivoElegido("content://a")
        val imp = vm.state.value.importacion!!
        assertFalse(PasosRespaldo.pideContrasena(imp))
        assertEquals(4, PasosRespaldo.pasoImportar(imp))
        vm.confirmarImport()
        assertEquals("", repo.importadoSinContrasena)
        assertNull(vm.state.value.importacion)
        assertTrue(vm.state.value.resultado!!.exito)
    }
}
