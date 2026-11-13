package cu.shalev.app.actualizacion

import cu.shalev.app.actualizacion.RespaldoPrevio.Restauracion
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.EstadoApp
import cu.shalev.domain.model.InfoApp
import cu.shalev.domain.model.LicenciaRecuperable
import cu.shalev.domain.repository.EstadoAppRepository
import cu.shalev.domain.repository.EtapaRespaldo
import cu.shalev.domain.repository.InfoRespaldo
import cu.shalev.domain.repository.RespaldoRepository
import cu.shalev.domain.repository.ResumenRespaldo
import cu.shalev.domain.usecase.ExportarRespaldo
import cu.shalev.domain.usecase.ImportarRespaldo
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 0.30.1: respaldo automático antes de actualizar y restauración al abrir la versión nueva. */
class RespaldoPrevioTest {
    private lateinit var dir: File
    private val repo = RepoFalso()
    private val estado = EstadoMem()
    private lateinit var previo: RespaldoPrevio

    @Before fun preparar() {
        dir = Files.createTempDirectory("previo").toFile()
        previo = RespaldoPrevio(File(dir, "respaldo_actualizacion"), ExportarRespaldo(repo), ImportarRespaldo(repo), estado)
    }

    @After fun limpiar() { dir.deleteRecursively() }

    @Test fun crearGuardaElRespaldoSinContrasenaYNoCuentaComoRespaldoExportado() = runTest {
        val antes = Instant.parse("2026-09-01T00:00:00Z")
        estado.flujo.value = EstadoApp(ultimoRespaldo = antes)
        assertTrue(previo.crear("0.30.0/51"))
        assertTrue(previo.existe)
        assertEquals(0, repo.contrasenaExportada) // vacía: respaldo sin contraseña
        assertEquals(antes, estado.flujo.value.ultimoRespaldo) // el recordatorio mensual no se reinicia
    }

    @Test fun sinRespaldoNoSeActualiza() = runTest {
        repo.falla = true
        assertFalse(previo.crear("0.30.0/51"))
        assertFalse(previo.existe)
        assertTrue(File(dir, "respaldo_actualizacion").listFiles().orEmpty().isEmpty()) // nada a medias
    }

    @Test fun actualizarConservaLaCopiaSinSobrescribirLaBaseMigrada() = runTest {
        previo.crear("0.30.0/51")
        repeat(2) {
            assertEquals(Restauracion.CONSERVADO, previo.restaurarSiCorresponde("0.30.1/52"))
            assertNull(repo.importado)
            assertTrue(previo.existe)
        }
    }

    @Test fun instalacionCanceladaTampocoRestauraNiBorraLaCopia() = runTest {
        previo.crear("0.30.0/51")
        assertEquals(Restauracion.CONSERVADO, previo.restaurarSiCorresponde("0.30.0/51"))
        assertNull(repo.importado)
        assertTrue(previo.existe)
    }

    @Test fun laCopiaSoloSeExportaCuandoSePideExplicitamente() = runTest {
        previo.crear("0.30.0/51")
        val destino = java.io.ByteArrayOutputStream()
        assertTrue(previo.copiarA(destino) is AppResult.Ok)
        assertEquals("datos", destino.toString("UTF-8"))
        assertNull(repo.importado)
        assertTrue(previo.existe)
    }

    @Test fun descartarBorraTodo() = runTest {
        previo.crear("0.30.0/51")
        previo.descartar()
        assertFalse(previo.existe)
        assertTrue(File(dir, "respaldo_actualizacion").listFiles().orEmpty().isEmpty())
    }

    @Test fun laClaveDistingueCompilacionesConElMismoNombre() {
        assertEquals("0.30.0/51", RespaldoPrevio.clave(InfoApp("0.30.0", 51, "")))
        assertTrue(RespaldoPrevio.clave(InfoApp("0.30.0", 51, "")) != RespaldoPrevio.clave(InfoApp("0.30.0", 52, "")))
    }

    @Test fun lasNotasNoMencionanRepositorioNiGithub() {
        val notas = "Mejoras en Inicio\nDescarga en github.com/x/y\nVer el repositorio\nNueva tarjeta de Licencia"
        val visibles = TextosActualizacion.notasVisibles(notas)
        assertEquals("Mejoras en Inicio\nNueva tarjeta de Licencia", visibles)
        listOf(TextosActualizacion.SIN_REPO, TextosActualizacion.GUARDANDO_RESPALDO,
            TextosActualizacion.ERROR_RESPALDO_PREVIO, TextosActualizacion.RESPALDO_RESTAURADO, TextosActualizacion.RESPALDO_NO_RESTAURADO,
            TextosActualizacion.disponible("0.30.1", 1_048_576), TextosActualizacion.desdePrincipal("0.30.1"),
        ).forEach { texto ->
            assertFalse(texto, texto.contains("github", ignoreCase = true) || texto.contains("repositorio", ignoreCase = true))
        }
    }

    private class RepoFalso : RespaldoRepository {
        var falla = false
        var fallaImportar = false
        var contrasenaExportada = -1
        var contrasenaImportada = -1
        var importado: String? = null
        var dejaLicenciaRecuperable: () -> Unit = {}

        override suspend fun exportar(contrasena: CharArray, destino: OutputStream, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> {
            contrasenaExportada = contrasena.size
            if (falla) return AppResult.Err(AppError.Almacenamiento)
            destino.write("datos".toByteArray())
            return AppResult.Ok(ResumenRespaldo(0, 0, 0, 0))
        }

        override suspend fun inspeccionar(origen: InputStream): AppResult<InfoRespaldo> = AppResult.Err(AppError.Almacenamiento)

        override suspend fun importar(origen: InputStream, contrasena: CharArray, progreso: (EtapaRespaldo) -> Unit): AppResult<ResumenRespaldo> {
            contrasenaImportada = contrasena.size
            if (fallaImportar) return AppResult.Err(AppError.Almacenamiento)
            importado = String(origen.readBytes())
            dejaLicenciaRecuperable()
            return AppResult.Ok(ResumenRespaldo(0, 0, 0, 0))
        }
    }

    private class EstadoMem : EstadoAppRepository {
        val flujo = MutableStateFlow(EstadoApp())
        override val estado: Flow<EstadoApp> = flujo
        override suspend fun actual() = flujo.value
        override suspend fun editar(cambio: (EstadoApp) -> EstadoApp) { flujo.value = cambio(flujo.value) }
        override suspend fun borrar() { flujo.value = EstadoApp() }
    }
}
