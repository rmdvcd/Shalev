package cu.shalev.data.sync

import cu.shalev.data.BuildConfig
import cu.shalev.core.util.aHex
import java.net.URI
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** Endpoint fijado por el distribuidor: nunca se acepta un servidor arbitrario desde un QR. */
internal object RelaySync {
    val endpoint: String? get() = validarEndpoint(BuildConfig.SHALEV_RELAY_URL)
    val habilitado: Boolean get() = endpoint != null

    fun validarEndpoint(valor: String): String? = runCatching {
        val uri = URI(valor)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null)
        require(uri.rawQuery == null && uri.rawFragment == null && uri.path in listOf("", "/"))
        require(uri.port == -1 || uri.port == 443)
        valor.trimEnd('/')
    }.getOrNull()

    /** Capacidad distinta para vinculación y sesión; no revela la clave ni el identificador del negocio. */
    fun sala(negocio: String, empleado: Long, secreto: ByteArray, vincular: Boolean): String =
        CriptoSync.hmac(secreto, "shalev-relay-v1:${if (vincular) "vincular" else "sesion"}:$negocio:$empleado".toByteArray()).aHex()

    suspend fun abrir(sala: String, principal: Boolean): Socket? {
        val base = endpoint ?: return null
        return try {
            runInterruptible(Dispatchers.IO) {
                SocketRelay.abrir(base.replaceFirst("https://", "wss://") + "/v1/$sala/${if (principal) "principal" else "secundaria"}", if (principal) 120_000 else 25_000)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }
}
