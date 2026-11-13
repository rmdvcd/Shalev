package cu.shalev.domain.model

import cu.shalev.core.money.Cup
import java.time.Instant

/**
 * 0.27.0 (N2): cliente fijo. Se guarda al registrar una venta por transferencia con «Cliente fijo» marcado
 * (identificado por el carné) y sirve para autocompletar sus datos en las ventas siguientes.
 * [compras], [total] y [ultimaCompra] salen de las transferencias de ventas no anuladas con ese carné.
 */
data class ClienteFijo(
    val id: Long = 0,
    val nombreApellidos: String,
    val ci: String,
    val telefono: String,
    val creadoEn: Instant,
    val actualizadoEn: Instant,
    val compras: Int = 0,
    val total: Cup = Cup.ZERO,
    val ultimaCompra: Instant? = null,
) {
    val datos: DatosCliente get() = DatosCliente(nombreApellidos, ci, telefono)

    companion object {
        const val MAX_SUGERENCIAS = 3
        /** Letras escritas a partir de las cuales se sugieren clientes. */
        const val MIN_LETRAS = 2

        /**
         * Hasta [MAX_SUGERENCIAS] clientes cuyo nombre contiene [texto] (sin distinguir mayúsculas ni tildes),
         * primero los que empiezan por él. Nada con menos de [MIN_LETRAS] letras o si ya coincide exactamente.
         */
        fun sugerencias(clientes: List<ClienteFijo>, texto: String): List<ClienteFijo> {
            val t = normalizar(texto)
            if (t.length < MIN_LETRAS) return emptyList()
            return clientes.asSequence()
                .map { it to normalizar(it.nombreApellidos) }
                .filter { (_, n) -> n.contains(t) && n != t }
                .sortedWith(compareBy({ !it.second.startsWith(t) }, { it.second }))
                .map { it.first }
                .take(MAX_SUGERENCIAS)
                .toList()
        }

        fun normalizar(s: String): String =
            java.text.Normalizer.normalize(s.trim().lowercase(), java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ")
    }
}
