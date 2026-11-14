package cu.shalev.core.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import cu.shalev.core.format.NumberPresentation

/** Importe en CUP almacenado en centavos. Nunca Double. Aritmética con desbordamiento detectado. */
@JvmInline
value class Cup(val centavos: Long) : Comparable<Cup> {
    operator fun plus(o: Cup) = Cup(Math.addExact(centavos, o.centavos))
    operator fun minus(o: Cup) = Cup(Math.subtractExact(centavos, o.centavos))
    operator fun times(qty: Long) = Cup(Math.multiplyExact(centavos, qty))
    operator fun unaryMinus() = Cup(Math.negateExact(centavos))
    override fun compareTo(other: Cup) = centavos.compareTo(other.centavos)
    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(centavos, 2)
    val isNegative: Boolean get() = centavos < 0

    /** Ajuste porcentual en puntos básicos (1000 = +10 %, -550 = -5.5 %), redondeo HALF_UP al centavo. */
    fun ajustar(puntosBasicos: Int): Cup =
        of(toBigDecimal().multiply(BigDecimal.valueOf(10_000L + puntosBasicos)).divide(BigDecimal.valueOf(10_000L)))

    /** Importe × cantidad en milésimas (insumos: 1.250 kg = 1250), redondeo HALF_UP. */
    fun porMilesimas(milesimas: Long): Cup =
        of(toBigDecimal().multiply(BigDecimal.valueOf(milesimas)).divide(BigDecimal.valueOf(1000L)))

    override fun toString() = Money.format(this)

    companion object {
        val ZERO = Cup(0)
        fun ofPesos(pesos: Long) = Cup(Math.multiplyExact(pesos, 100L))
        // BigDecimal.longValueExact (API 1), no BigInteger.longValueExact, que es de Android 12 (API 31).
        fun of(amount: BigDecimal) = Cup(amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact())
    }
}

fun Iterable<Cup>.sum(): Cup = fold(Cup.ZERO, Cup::plus)
inline fun <T> Iterable<T>.sumOfCup(selector: (T) -> Cup): Cup = fold(Cup.ZERO) { acc, t -> acc + selector(t) }

/** Presentación sin ceros decimales innecesarios, pero sin perder centavos ni el signo. */
object Money {
    private val FORMAT = ThreadLocal.withInitial {
        DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US).apply {
            groupingSeparator = NumberPresentation.NARROW_NO_BREAK_SPACE.single()
        }).apply { roundingMode = RoundingMode.HALF_UP }
    }
    // Los documentos/tablas de exportación conservan el formato anterior; las pantallas usan [format].
    private val EXPORT_FORMAT = ThreadLocal.withInitial {
        DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)).apply { roundingMode = RoundingMode.HALF_UP }
    }
    private val INPUT = Regex("^-?[0-9]{1,3}(,[0-9]{3})*(\\.[0-9]{1,2})?$|^-?[0-9]+(\\.[0-9]{1,2})?$")

    /** Importe para mostrar en pantalla, con agrupación tipográfica estrecha cada tres cifras. */
    fun format(amount: Cup): String = "${FORMAT.get()!!.format(amount.toBigDecimal())} CUP"

    /** Formato histórico para documentos y exportaciones; no usar para poblar campos de edición. */
    fun formatExport(amount: Cup): String = "${EXPORT_FORMAT.get()!!.format(amount.toBigDecimal())} CUP"
    /** Texto de edición independiente de la presentación y sin separadores de miles. */
    fun editar(amount: Cup): String = amount.toBigDecimal().stripTrailingZeros().toPlainString()

    fun cup(pesos: Long): String = format(Cup.ofPesos(pesos))

    /**
     * Interpreta lo que teclea el usuario: "1450", "1450.5", "1,450.00", "1,450.00 CUP".
     * No acepta más de 2 decimales ni separadores mal agrupados (null en ese caso).
     */
    fun parse(text: String): Cup? {
        val t = text.trim().removeSuffix("CUP").trim()
        if (!INPUT.matches(t)) return null
        return runCatching { Cup.of(BigDecimal(t.replace(",", ""))) }.getOrNull()
    }
}

/** Porcentajes en puntos básicos: 1250 → "+12.5 %". */
object Percent {
    private val FORMAT = ThreadLocal.withInitial { DecimalFormat("0.##", DecimalFormatSymbols(Locale.US)) }
    fun format(puntosBasicos: Int): String {
        val sign = if (puntosBasicos > 0) "+" else ""
        return "$sign${FORMAT.get()!!.format(BigDecimal.valueOf(puntosBasicos.toLong(), 2))} %"
    }

    /** "12.5" / "-5" / "+10" → puntos básicos. Máximo 2 decimales. */
    fun parse(text: String): Int? = runCatching {
        BigDecimal(text.trim().removeSuffix("%").trim().removePrefix("+")).movePointRight(2).intValueExact()
    }.getOrNull()
}
