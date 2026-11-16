package cu.shalev.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import cu.shalev.data.db.entity.EmpleadoEntity
import cu.shalev.domain.di.IoDispatcher
import cu.shalev.domain.repository.RespaldoRepository
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext

/**
 * Prepara y sirve una copia completa solo para la secundaria cuya solicitud fue autorizada por el dueño.
 * El archivo se conserva en la caché privada, se transmite en bloques por el canal cifrado y se elimina al confirmar
 * la importación o al vencer la caché. Nunca se registra su contenido ni la ruta.
 */
@Singleton
class CopiaBaseSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val respaldo: RespaldoRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private data class Preparada(val archivo: File, val sha256: String)

    private val locks = ConcurrentHashMap<String, Mutex>()
    private val preparadas = ConcurrentHashMap<String, Preparada>()
    private val directorio get() = File(context.cacheDir, CARPETA)

    suspend fun bloque(e: EmpleadoEntity, solicitudId: String, desde: Long, id: Long): BloqueBaseNegocio {
        if (!solicitudValida(e, solicitudId) || desde < 0) return error(id, solicitudId, desde, ERROR_NO_AUTORIZADA)
        val clave = clave(e.id, solicitudId)
        return locks.computeIfAbsent(clave) { Mutex() }.withLock {
            if (!solicitudValida(e, solicitudId)) return@withLock error(id, solicitudId, desde, ERROR_NO_AUTORIZADA)
            val preparada = preparar(e.id, solicitudId, clave)
                ?: return@withLock error(id, solicitudId, desde, ERROR_NO_DISPONIBLE)
            val tamano = preparada.archivo.length()
            if (desde > tamano) return@withLock error(id, solicitudId, desde, ERROR_NO_DISPONIBLE)
            val bytes = withContext(io) {
                RandomAccessFile(preparada.archivo, "r").use { raf ->
                    raf.seek(desde)
                    ByteArray(minOf(TAMANO_BLOQUE.toLong(), tamano - desde).toInt()).also { raf.readFully(it) }
                }
            }
            BloqueBaseNegocio(
                id = id,
                solicitudId = solicitudId,
                desde = desde,
                datos = Base64.getEncoder().encodeToString(bytes),
                total = tamano,
                sha256 = preparada.sha256,
            ).also { bytes.fill(0) }
        }
    }

    suspend fun descartar(empleadoId: Long, solicitudId: String) {
        val clave = clave(empleadoId, solicitudId)
        locks.computeIfAbsent(clave) { Mutex() }.withLock {
            preparadas.remove(clave)
            val prefijo = nombreBase(empleadoId, solicitudId)
            runCatching {
                File(directorio, "$prefijo.shalev").delete()
                File(directorio, "$prefijo.tmp").delete()
            }
        }
    }

    private suspend fun preparar(empleadoId: Long, solicitudId: String, clave: String): Preparada? {
        val vigente = preparadas[clave]?.takeIf {
            it.archivo.isFile && it.archivo.length() in 1..MAX_ARCHIVO &&
                it.archivo.lastModified() >= System.currentTimeMillis() - VIDA_COPIA_MS
        }
        if (vigente != null) return vigente
        preparadas.remove(clave)
        return withContext(io) {
            limpiarCaducadas()
            val dir = directorio.apply { mkdirs() }
            val nombre = nombreBase(empleadoId, solicitudId)
            val destino = File(dir, "$nombre.shalev")
            val temporal = File(dir, "$nombre.tmp")
            // Tras un reinicio no se reutiliza una copia a medio escribir ni un hash solo en memoria.
            destino.delete()
            temporal.delete()
            val resultado = try {
                temporal.outputStream().use { respaldo.exportarParaSincronizacion(it) }
            } catch (e: CancellationException) {
                temporal.delete()
                throw e
            } catch (_: Exception) {
                temporal.delete()
                return@withContext null
            }
            if (resultado !is cu.shalev.core.result.AppResult.Ok ||
                !temporal.isFile || temporal.length() !in 1..MAX_ARCHIVO
            ) {
                temporal.delete()
                return@withContext null
            }
            val movido = runCatching {
                java.nio.file.Files.move(
                    temporal.toPath(), destino.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
                true
            }.getOrElse { temporal.renameTo(destino) }
            if (!movido) {
                temporal.delete()
                destino.delete()
                return@withContext null
            }
            Preparada(destino, sha256(destino)).also { preparadas[clave] = it }
        }
    }

    private fun limpiarCaducadas() {
        val limite = System.currentTimeMillis() - VIDA_COPIA_MS
        directorio.listFiles().orEmpty().filter { it.lastModified() < limite }.forEach { f ->
            f.delete()
            val nombre = f.name.removeSuffix(".shalev")
            preparadas.entries.removeIf { it.value.archivo.name == f.name || it.key.endsWith(":$nombre") }
        }
    }

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").run {
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                update(buffer, 0, n)
            }
            buffer.fill(0)
        }
        digest().joinToString("") { "%02x".format(it) }
    }

    private fun solicitudValida(e: EmpleadoEntity, id: String): Boolean =
        UUID_ID.matches(id) && e.activo && e.solicitudImportacionId == id &&
            e.estadoSolicitudImportacion == ESTADO_AUTORIZADA

    private fun clave(empleadoId: Long, solicitudId: String) = "$empleadoId:${nombreBase(empleadoId, solicitudId)}"

    private fun nombreBase(empleadoId: Long, solicitudId: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(solicitudId.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }
        return "${empleadoId}_$hash"
    }

    private fun error(id: Long, solicitudId: String, desde: Long, motivo: String) =
        BloqueBaseNegocio(id, solicitudId, desde, error = motivo)

    companion object {
        const val TAMANO_BLOQUE = 48 * 1024
        const val ERROR_NO_AUTORIZADA = "no_autorizada"
        const val ERROR_NO_DISPONIBLE = "no_disponible"
        private const val MAX_ARCHIVO = 128L * 1024 * 1024 + 256
        private const val VIDA_COPIA_MS = 24 * 60 * 60 * 1000L
        private const val CARPETA = "copias_sync"
        private const val ESTADO_AUTORIZADA = "AUTORIZADA"
        private val UUID_ID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
    }
}
