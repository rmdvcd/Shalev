package cu.shalev.app.actualizacion

import android.app.Application
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.EstadoApp
import cu.shalev.domain.model.EstadoSecundaria
import cu.shalev.domain.model.InfoApp
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.repository.ActualizacionesRepository
import cu.shalev.domain.repository.EstadoAppRepository
import cu.shalev.domain.repository.EtapaRespaldo
import cu.shalev.domain.repository.InfoRespaldo
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.MantenimientoRepository
import cu.shalev.domain.repository.RespaldoRepository
import cu.shalev.domain.repository.ResumenRespaldo
import cu.shalev.domain.repository.SecundariaRepository
import cu.shalev.domain.usecase.ComprobarActualizaciones
import cu.shalev.domain.usecase.EstadoActualizacionObligatoria
import cu.shalev.domain.usecase.ExportarRespaldo
import cu.shalev.domain.usecase.ImportarRespaldo
import cu.shalev.licencia.ActivationResult
import cu.shalev.licencia.SolicitudGenerada
import cu.shalev.licencia.SolicitudInput
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 0.31.0 (P5): cerrar el cuadro de «Actualizar» a mitad del proceso no lo deja colgado:
 * el trabajo en curso se cancela y el resultado tardío no reabre el cuadro solo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProcesoActualizarTest {
    private lateinit var dir: File
    private lateinit var estadoApp: EstadoMem
    private lateinit var github: GithubPuerta

    @Before fun preparar() {
        dir = Files.createTempDirectory("proceso").toFile()
        estadoApp = EstadoMem()
        github = GithubPuerta()
    }

    @After fun limpiar() { dir.deleteRecursively() }

    private fun gestor(): GestorActualizacion {
        val contexto = RuntimeEnvironment.getApplication()
        val comprobar = ComprobarActualizaciones(estadoApp, github, LicenciaVacia(), MantenimientoNulo(), Clock { Instant.now() })
        val respaldo = RespaldoPrevio(File(dir, "previo"), ExportarRespaldo(RespaldoNulo()), ImportarRespaldo(RespaldoNulo()), estadoApp)
        return GestorActualizacion(
            contexto, comprobar, github, estadoApp, SecundariaSuelta(), InstaladorApk(contexto),
            InfoApp("0.30.0", 51, "dueno/repo"), EstadoActualizacionObligatoria(estadoApp, Clock { Instant.now() }), respaldo,
        )
    }

    @Test fun cerrarDuranteLaComprobacionNoReabreElCuadro() = runBlocking {
        val g = gestor()
        g.actualizarAhora()
        withTimeout(10_000) { g.estado.first { it.proceso is ProcesoActualizar.EnCurso } }
        g.cerrarProceso()
        withTimeout(10_000) { g.estado.first { it.proceso == null } }
        github.puerta.complete(AppResult.Err(AppError.Red.SinConexion))
        delay(2_000) // margen para que un resultado tardío aterrice
        assertNull("el cuadro se reabrió solo tras cerrar", g.estado.value.proceso)
    }

    @Test fun sinCerrarMuestraElResultadoYCierra() = runBlocking {
        val g = gestor()
        g.actualizarAhora()
        withTimeout(10_000) { g.estado.first { it.proceso is ProcesoActualizar.EnCurso } }
        github.puerta.complete(AppResult.Err(AppError.Red.SinConexion))
        withTimeout(10_000) { g.estado.first { it.proceso == ProcesoActualizar.SinConexion } }
        g.cerrarProceso()
        withTimeout(10_000) { g.estado.first { it.proceso == null } }
        assertTrue(github.llamadas == 1)
    }

    /** 1.0.1: al comprobar que estás al día el cuadro se cierra solo, sin que nadie lo toque. */
    @Test fun alDiaSeCierraSolo() = runBlocking {
        val g = gestor()
        g.actualizarAhora()
        withTimeout(10_000) { g.estado.first { it.proceso is ProcesoActualizar.EnCurso } }
        github.puerta.complete(AppResult.Ok(null)) // no hay versión nueva
        withTimeout(10_000) { g.estado.first { it.proceso == ProcesoActualizar.AlDia } }
        withTimeout(10_000) { g.estado.first { it.proceso == null } }
        assertNull(g.estado.value.proceso)
    }

    /** 1.0.1: «Sin conexión» NO se cierra solo: el usuario tiene que leerlo y cerrarlo. */
    @Test fun sinConexionNoSeCierraSolo() = runBlocking {
        val g = gestor()
        g.actualizarAhora()
        withTimeout(10_000) { g.estado.first { it.proceso is ProcesoActualizar.EnCurso } }
        github.puerta.complete(AppResult.Err(AppError.Red.SinConexion))
        withTimeout(10_000) { g.estado.first { it.proceso == ProcesoActualizar.SinConexion } }
        delay(GestorActualizacion.CIERRE_AUTO_MS * 2)
        assertEquals(ProcesoActualizar.SinConexion, g.estado.value.proceso)
        g.cerrarProceso()
        withTimeout(10_000) { g.estado.first { it.proceso == null } }
    }

    /** La comprobación se queda esperando hasta que el test la suelta (red lenta). */
    private class GithubPuerta : ActualizacionesRepository {
        override val repoConfigurado = true
        val puerta = CompletableDeferred<AppResult<cu.shalev.domain.model.InfoActualizacion?>>()
        var llamadas = 0

        override suspend fun ultimaVersion(): AppResult<cu.shalev.domain.model.InfoActualizacion?> {
            llamadas++
            return puerta.await()
        }

        override suspend fun listaRevocaciones(): AppResult<String> = AppResult.Err(AppError.Red.SinConexion)
        override suspend fun descargar(info: cu.shalev.domain.model.InfoActualizacion, progreso: (Long, Long) -> Unit): AppResult<File> =
            AppResult.Err(AppError.Red.SinConexion)
    }

    private class EstadoMem : EstadoAppRepository {
        val flujo = MutableStateFlow(EstadoApp())
        override val estado: Flow<EstadoApp> = flujo
        override suspend fun actual() = flujo.value
        override suspend fun editar(cambio: (EstadoApp) -> EstadoApp) { flujo.value = cambio(flujo.value) }
        override suspend fun borrar() { flujo.value = EstadoApp() }
    }

    private class LicenciaVacia : LicenciaRepository {
        override val snapshot: StateFlow<Licencia?> = MutableStateFlow(null)
        override suspend fun refrescar(): Licencia = error("sin licencia")
        override suspend fun construirSolicitud(input: SolicitudInput): AppResult<SolicitudGenerada> = error("sin licencia")
        override suspend fun activar(mensaje: String): ActivationResult = error("sin licencia")
    }

    private class MantenimientoNulo : MantenimientoRepository {
        override suspend fun borrarTodo(): AppResult<Unit> = AppResult.Ok(Unit)
    }

    private class SecundariaSuelta : SecundariaRepository {
        override val estado = MutableStateFlow(EstadoSecundaria())
        override suspend fun vincular(contenidoQr: String, telefono: String): AppResult<Unit> = AppResult.Err(AppError.Red.SinConexion)
        override suspend fun solicitarCierre(contado: cu.shalev.core.money.Cup): AppResult<Unit> = AppResult.Err(AppError.Red.SinConexion)
        override suspend fun sincronizarAhora(): AppResult<Unit> = AppResult.Err(AppError.Red.SinConexion)
        override suspend fun desvincular(): AppResult<Unit> = AppResult.Err(AppError.Red.SinConexion)
        override suspend fun descartarAvisoCierre() = Unit
    }

    private class RespaldoNulo : RespaldoRepository {
        override suspend fun exportar(contrasena: CharArray, destino: OutputStream, progreso: (EtapaRespaldo) -> Unit) =
            AppResult.Ok(ResumenRespaldo(0, 0, 0, 0))

        override suspend fun inspeccionar(origen: InputStream): AppResult<InfoRespaldo> = AppResult.Err(AppError.Red.SinConexion)
        override suspend fun importar(origen: InputStream, contrasena: CharArray, progreso: (EtapaRespaldo) -> Unit) =
            AppResult.Err(AppError.Red.SinConexion)
    }
}
