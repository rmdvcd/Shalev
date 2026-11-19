package cu.shalev.domain.model

/**
 * Tipo de dispositivo para apps principales y secundarias vinculadas:
 * - [CELULAR]: Teléfono móvil / dispositivo Android estándar.
 * - [PC]: Computadora de escritorio / punto de venta / Windows.
 */
enum class TipoDispositivo(val etiqueta: String) {
    CELULAR("Celular"),
    PC("PC de escritorio");

    companion object {
        fun deTexto(texto: String): TipoDispositivo {
            val t = texto.lowercase()
            return if (t.contains("pc") || t.contains("desktop") || t.contains("escritorio") ||
                t.contains("computadora") || t.contains("windows") || t.contains("laptop") ||
                t.contains("caja") || t.contains("pos")
            ) PC else CELULAR
        }
    }
}
