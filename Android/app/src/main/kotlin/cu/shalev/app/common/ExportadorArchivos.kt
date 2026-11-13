package cu.shalev.app.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream

/**
 * P17 S5: el «Enviar / Guardar en el teléfono» de PDF, Excel y respaldos, que antes repetían Inventario, Elaboración,
 * Registros y Respaldo.
 *
 * - **Enviar**: [aTemporal] escribe en la carpeta privada de compartir (FileProvider). Si falla, borra el archivo a medias.
 * - **Guardar**: [recordar] guarda qué se iba a exportar mientras el sistema muestra «Guardar como». Cuando llega la URI,
 *   [tomar] lo recupera una sola vez y [aDestino] escribe en ella.
 *
 * Sin permisos de almacenamiento: solo SAF y la carpeta de caché de la app. [P] es lo que cada pantalla necesita recordar
 * (un formato, una tabla congelada…).
 */
class ExportadorArchivos<P : Any>(private val archivos: ArchivosApp) {

    private var pendiente: P? = null

    /** Lo que se exportará cuando el usuario elija el destino. */
    fun recordar(p: P) { pendiente = p }

    /** Lo pendiente, una sola vez. null si no había nada o si el usuario canceló el selector ([uri] null). */
    fun tomar(uri: String?): P? {
        val p = pendiente
        pendiente = null
        return if (uri == null) null else p
    }

    /** Escribe en un archivo nuevo de la carpeta de compartir. Si falla, no deja nada a medias. */
    suspend fun <T> aTemporal(nombre: String, escribir: suspend (OutputStream) -> AppResult<T>): AppResult<Escrito<T>> {
        val f = archivos.temporal(nombre)
        val r = runCatching { f.outputStream() }.getOrNull()?.use { escribir(it) } ?: AppResult.Err(AppError.Almacenamiento)
        return when (r) {
            is AppResult.Ok -> AppResult.Ok(Escrito(f, r.value))
            is AppResult.Err -> { f.delete(); r }
        }
    }

    /** Escribe en el destino elegido con «Guardar como». Si no se puede abrir: [AppError.Almacenamiento]. */
    suspend fun <T> aDestino(uri: String, escribir: suspend (OutputStream) -> AppResult<T>): AppResult<T> =
        archivos.abrirDestino(uri)?.use { escribir(it) } ?: AppResult.Err(AppError.Almacenamiento)

    data class Escrito<T>(val archivo: File, val valor: T)
}

/**
 * Trabajo de exportación en segundo plano, común a las pantallas. Marca la pantalla como ocupada antes de empezar
 * (evita que un doble toque lo lance dos veces) y la libera al terminar. Cualquier fallo inesperado acaba en
 * [alFallar], nunca en un cierre de la app.
 */
fun ViewModel.lanzarExportacion(ocupado: (Boolean) -> Unit, alFallar: suspend () -> Unit, bloque: suspend () -> Unit): Job {
    ocupado(true)
    return viewModelScope.launch {
        try {
            bloque()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            alFallar()
        } finally {
            ocupado(false)
        }
    }
}
