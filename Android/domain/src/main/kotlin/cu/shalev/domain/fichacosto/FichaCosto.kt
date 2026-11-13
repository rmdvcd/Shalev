package cu.shalev.domain.fichacosto

import java.math.BigDecimal
import java.math.RoundingMode

enum class MonedaCosto { CUP, USD }

enum class CategoriaCosto(val titulo: String) {
    MATERIAS("Materias primas y materiales"),
    TRABAJO("Fuerza de trabajo directa"),
    OTROS("Otros gastos directos"),
    INDIRECTOS("Gastos indirectos de producción"),
}

enum class UnidadMargen(val titulo: String) { PORCENTAJE("Porcentaje"), FACTOR("Factor") }

/** Cantidades y precios decimales, sin mezclar monedas ni usar coma flotante. */
data class PartidaCosto(
    val descripcion: String,
    val categoria: CategoriaCosto,
    val cantidad: BigDecimal,
    val unidad: String,
    val costoUnitario: BigDecimal,
    val moneda: MonedaCosto,
) {
    init {
        require(descripcion.isNotBlank() && unidad.isNotBlank())
        require(cantidad.signum() >= 0 && costoUnitario.signum() >= 0)
    }
    val subtotal: BigDecimal get() = cantidad.multiply(costoUnitario).setScale(2, RoundingMode.HALF_UP)
}

data class TotalesCosto(
    val porCategoria: Map<CategoriaCosto, Map<MonedaCosto, BigDecimal>>,
    val costoTotal: Map<MonedaCosto, BigDecimal>,
    val precioEstimado: Map<MonedaCosto, BigDecimal>?,
)

/** Cálculo equivalente al modelo de GFC: cada partida se redondea antes de sumar. */
object CalculoFichaCosto {
    fun calcular(partidas: List<PartidaCosto>, margen: BigDecimal? = null, unidad: UnidadMargen = UnidadMargen.PORCENTAJE): TotalesCosto {
        require(margen == null || margen.signum() >= 0)
        val cero = BigDecimal.ZERO.setScale(2)
        val porCategoria = CategoriaCosto.entries.associateWith { categoria ->
            MonedaCosto.entries.associateWith { moneda ->
                partidas.filter { it.categoria == categoria && it.moneda == moneda }
                    .fold(cero) { suma, partida -> suma + partida.subtotal }
            }
        }
        val total = MonedaCosto.entries.associateWith { moneda ->
            porCategoria.values.fold(cero) { suma, categoria -> suma + categoria.getValue(moneda) }
        }
        val precio = margen?.let { valor ->
            val factor = if (unidad == UnidadMargen.PORCENTAJE) BigDecimal.ONE + valor.movePointLeft(2) else valor
            total.mapValues { (_, costo) -> costo.multiply(factor).setScale(2, RoundingMode.HALF_UP) }
        }
        return TotalesCosto(porCategoria, total, precio)
    }

    /** Acepta punto o coma decimal, sin signo ni notación exponencial. */
    fun numero(texto: String): BigDecimal? {
        val normalizado = texto.trim().replace(',', '.')
        if (!normalizado.matches(Regex("[0-9]{1,12}(\\.[0-9]{1,4})?"))) return null
        return normalizado.toBigDecimalOrNull()
    }
}
