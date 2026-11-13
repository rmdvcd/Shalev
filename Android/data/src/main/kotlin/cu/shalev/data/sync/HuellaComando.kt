package cu.shalev.data.sync

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Huella estable del contenido de una acción; el id de transporte y la clave no forman parte del contenido. */
internal object HuellaComando {
    private const val HEX = "0123456789abcdef"

    fun calcular(accion: Accion): String {
        // El orden de un Map no altera la acción; ordenar evita falsos conflictos al reenviar CambiarPrecios.
        val canonica = when (accion) {
            is CambiarPrecios -> accion.copy(precios = accion.precios.toSortedMap())
            else -> accion
        }
        val bytes = SyncJson.encodeToString(Accion.serializer(), canonica).toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        bytes.fill(0)
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX[value ushr 4])
                append(HEX[value and 0x0f])
            }
        }
    }
}
