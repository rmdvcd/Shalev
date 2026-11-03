package cu.shalev.domain.fichacosto

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** La tasa siempre expresa cuántos CUP equivalen a un USD, sin datos externos. */
object CalculoCambio {
    /** Acepta estados parciales al escribir, sin signos, exponentes ni separadores de miles. */
    fun entradaTasaValida(texto: String): Boolean = texto.matches(Regex("[0-9]{0,18}([.,][0-9]{0,32})?"))

    fun leerTasa(texto: String): BigDecimal? {
        if (!entradaTasaValida(texto) || texto.isBlank() || texto.endsWith('.') || texto.endsWith(',')) return null
        return texto.replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
    }

    fun convertir(importe: BigDecimal, cupPorUsd: BigDecimal, origen: MonedaCosto): BigDecimal {
        require(importe.signum() >= 0) { "El importe no puede ser negativo" }
        require(cupPorUsd.signum() > 0) { "La tasa debe ser mayor que cero" }
        return when (origen) {
            MonedaCosto.USD -> importe.multiply(cupPorUsd).setScale(2, RoundingMode.HALF_UP)
            MonedaCosto.CUP -> importe.divide(cupPorUsd, 2, RoundingMode.HALF_UP)
        }
    }

    /** Tasa orientativa con 12 cifras significativas, a partir de dos importes positivos. */
    fun tasa(cup: BigDecimal, usd: BigDecimal): BigDecimal {
        require(cup.signum() > 0 && usd.signum() > 0) { "Los importes deben ser mayores que cero" }
        return cup.divide(usd, MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
    }
}
