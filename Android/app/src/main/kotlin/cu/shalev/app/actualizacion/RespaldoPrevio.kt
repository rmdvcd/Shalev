package cu.shalev.app.actualizacion

import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.InfoApp
import cu.shalev.domain.repository.EstadoAppRepository
import cu.shalev.domain.usecase.ExportarRespaldo
import cu.shalev.domain.usecase.ImportarRespaldo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import kotlinx.coroutines.CancellationException
import javax.inject.Singleton

/**
 * 0.30.1: respaldo automático alrededor de cada actualización, para no perder los datos del usuario.
 *
 * 1. [crear] se llama justo antes de abrir el instalador: guarda un respaldo completo (el mismo formato `.shalev`, sin
 *    contraseña) en la carpeta PRIVADA de la app y anota qué versión estaba instalada. Si no se puede crear, no se
 *    actualiza.
 * 2. [restaurarSiCorresponde] ya NO importa al abrir una versión nueva: Room migra normalmente. La copia
 *    se conserva para recuperación explícita desde Respaldo y nunca pisa operaciones posteriores a su creación.
 *
 * Es un respaldo interno: no cuenta como «respaldo exportado» para el recordatorio mensual de Inicio, ni deja una
 * licencia «recuperable» en el estado de la app. Solo sale de esta carpeta al guardarla explícitamente desde
 * Respaldo; no se envía por red ni se escribe su contenido en los registros.
 */
class RespaldoPrevio(
    private val dir: File,
    private val exportar: ExportarRespaldo,
    private val importar: ImportarRespaldo,
    private val estadoApp: EstadoAppRepository,
) {
    enum class Restauracion { NADA_QUE_RESTAURAR, CONSERVADO, RESTAURADO, FALLO }

    private val archivo get() = File(dir, "previo.shalev")
    private val marca get() = File(dir, "previo.version")

    /** Hay un respaldo previo guardado (y pendiente de restaurar o de descartar). */
    val existe: Boolean get() = archivo.exists()

    /** Crea el respaldo antes de instalar. [version] identifica la versión instalada ahora. true = listo. */
    suspend fun crear(version: String): Boolean {
        val tmp = File(dir, "previo.tmp")
        val ultimo = intentar { estadoApp.actual().ultimoRespaldo }
        val resultado = intentar {
            dir.mkdirs()
            tmp.outputStream().use { exportar(CharArray(0), it) }
        }
        // Exportar reinicia el recordatorio mensual; este respaldo es interno, así que se deja como estaba.
        intentar { estadoApp.editar { it.copy(ultimoRespaldo = ultimo) } }
        if (resultado !is AppResult.Ok) { tmp.delete(); return false }
        val listo = runCatching {
            java.nio.file.Files.move(tmp.toPath(), archivo.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            marca.writeText(version)
            true
        }.getOrDefault(false)
        if (!listo) descartar()
        return listo
    }

    /** Apertura normal: no se restaura NUNCA automáticamente, aunque haya cambiado la versión. */
    suspend fun restaurarSiCorresponde(version: String): Restauracion {
        if (!archivo.exists()) return Restauracion.NADA_QUE_RESTAURAR
        return Restauracion.CONSERVADO
    }

    /** Copia de recuperación a una ubicación elegida explícitamente. Importarla usa el asistente normal de Respaldo. */
    suspend fun copiarA(destino: java.io.OutputStream): AppResult<Unit> = try {
        archivo.inputStream().use { it.copyTo(destino) }; destino.flush()
        AppResult.Ok(Unit)
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { AppResult.Err(cu.shalev.core.result.AppError.Almacenamiento) }

    /** Descarte explícito o borrado total del negocio. */
    fun descartar() {
        runCatching { archivo.delete() }
        runCatching { marca.delete() }
        runCatching { File(dir, "previo.tmp").delete() }
    }

    /** Como `runCatching`, pero sin tragarse la cancelación de la corrutina. */
    private suspend inline fun <T> intentar(bloque: () -> T): T? = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Clave de versión que se anota: el nombre y el código (dos compilaciones con el mismo nombre no se confunden). */
        fun clave(info: InfoApp): String = "${info.versionName}/${info.versionCode}"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RespaldoPrevioModule {
    @Provides @Singleton
    fun respaldoPrevio(
        @ApplicationContext context: android.content.Context,
        exportar: ExportarRespaldo,
        importar: ImportarRespaldo,
        estadoApp: EstadoAppRepository,
    ): RespaldoPrevio = RespaldoPrevio(File(context.filesDir, "respaldo_actualizacion"), exportar, importar, estadoApp)
}
