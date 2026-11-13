package cu.shalev.core.result

import kotlin.coroutines.cancellation.CancellationException

/**
 * Como [runCatching], pero deja pasar la cancelación de corrutinas (0.21.6).
 *
 * `runCatching` alrededor de una llamada `suspend` también atrapa la [CancellationException]: la corrutina
 * cancelada (p. ej. al cerrar la pantalla) seguía ejecutando el resto del bloque como si la llamada hubiese
 * fallado. Esta versión la relanza y convierte en fallo solo los errores reales.
 */
inline fun <T> runCatchingCancelable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
