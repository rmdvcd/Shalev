package cu.shalev.data.db

import androidx.room.withTransaction
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import kotlin.coroutines.cancellation.CancellationException

/** Aborta la transacción en curso (rollback) devolviendo un error tipado. Sin stacktrace: es control de flujo. */
internal class Abortar(val error: AppError) : RuntimeException(null, null, false, false)

internal fun abortar(error: AppError): Nothing = throw Abortar(error)

/**
 * Ejecuta [block] en UNA transacción SQLite: o se aplica todo o nada.
 * [Abortar] → Err(error); cualquier fallo de SQLite → Err(Almacenamiento). La cancelación se propaga.
 */
internal suspend fun <T> ShalevDatabase.tx(block: suspend () -> T): AppResult<T> = try {
    AppResult.Ok(withTransaction { block() })
} catch (e: Abortar) {
    AppResult.Err(e.error)
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    AppResult.Err(AppError.Almacenamiento)
}
