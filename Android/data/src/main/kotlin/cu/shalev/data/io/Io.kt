package cu.shalev.data.io

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Lee hasta [max] bytes; si hay más, lanza [alExceder]. Un solo bucle para todos los topes
 * (respaldo, foto, red local): leer sin tope expone a archivos/tramas maliciosas enormes.
 */
internal fun InputStream.leerAcotado(max: Long, alExceder: () -> Exception): ByteArray {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0L
    while (true) {
        val n = read(buf)
        if (n < 0) return out.toByteArray()
        total += n
        if (total > max) throw alExceder()
        out.write(buf, 0, n)
    }
}
