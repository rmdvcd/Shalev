package cu.shalev.data.sync

import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Una operación incierta por vinculación, persistida ANTES de enviarla. Separada del socket para probar reinicios,
 * pérdida de respuesta y cambios de vinculación sin red ni Android. No es una cola de ventas (estas siguen en Room).
 */
internal class BandejaComandos(
    private val leer: suspend () -> DatosSecundaria?,
    private val guardar: suspend (DatosSecundaria, ComandoPendiente?, Boolean?) -> Boolean,
) {
    private val mutex = Mutex()
    data class Entrega(val resultado: AppResult<Long?>, val confirmada: Boolean)

    suspend fun ejecutar(accion: Accion, enviar: suspend (DatosSecundaria, ComandoPendiente) -> ResultadoComando?): Entrega = mutex.withLock {
        val datos = leer() ?: return@withLock Entrega(AppResult.Err(AppError.NoEncontrado), false)
        val anterior = datos.comandoPendiente
        if (anterior != null && (HuellaComando.calcular(anterior.accion) != HuellaComando.calcular(accion) || anterior.localId != datos.localId)) {
            val resuelto = resolver(datos, anterior, enviar, recuperada = true)
            if (!resuelto.confirmada) return@withLock resuelto
            // La acción nueva NO se envía después de recuperar otra: antes hay que revisar su resultado.
            return@withLock Entrega(AppResult.Err(AppError.Validacion("comandoRecuperado")), true)
        }
        val pendiente = anterior ?: ComandoPendiente(UUID.randomUUID().toString(), accion, datos.localId)
        if (anterior == null && !guardar(datos, pendiente, null)) return@withLock Entrega(AppResult.Err(AppError.NoEncontrado), false)
        resolver(datos.copy(comandoPendiente = pendiente), pendiente, enviar, recuperada = anterior != null)
    }

    /** Se llama al conectar y antes de sincronizar: un reinicio no deja la confirmación esperando otro cambio. */
    suspend fun recuperar(enviar: suspend (DatosSecundaria, ComandoPendiente) -> ResultadoComando?): Entrega? = mutex.withLock {
        val datos = leer() ?: return@withLock null
        val pendiente = datos.comandoPendiente ?: return@withLock null
        resolver(datos, pendiente, enviar, recuperada = true)
    }

    private suspend fun resolver(datos: DatosSecundaria, pendiente: ComandoPendiente,
        enviar: suspend (DatosSecundaria, ComandoPendiente) -> ResultadoComando?, recuperada: Boolean): Entrega {
        val respuesta = enviar(datos, pendiente) ?: return Entrega(AppResult.Err(AppError.Validacion("comandoPendiente")), false)
        // Comparación y escritura atómicas: una respuesta tardía jamás borra datos de otra vinculación/local.
        if (!guardar(datos, null, if (recuperada) respuesta.error == null else null))
            return Entrega(AppResult.Err(AppError.NoEncontrado), false)
        return Entrega(respuesta.error?.let { AppResult.Err(EjecutorComandos.error(it)) } ?: AppResult.Ok(respuesta.valor), true)
    }
}

internal fun mismaVinculacion(a: DatosSecundaria, b: DatosSecundaria): Boolean =
    a.negocioId == b.negocioId && a.empleadoId == b.empleadoId && a.clave == b.clave && a.localId == b.localId
