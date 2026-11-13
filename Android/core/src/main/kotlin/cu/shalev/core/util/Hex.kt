package cu.shalev.core.util

/** Hex en minúsculas de un ByteArray (2 caracteres por byte). Sin `String.format`: más rápido y sin locale. */
fun ByteArray.aHex(): String {
    val digitos = "0123456789abcdef"
    val salida = CharArray(size * 2)
    forEachIndexed { i, b ->
        val v = b.toInt() and 0xff
        salida[i * 2] = digitos[v ushr 4]
        salida[i * 2 + 1] = digitos[v and 0x0f]
    }
    return salida.concatToString()
}
