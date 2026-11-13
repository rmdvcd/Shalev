package cu.shalev.core.quantity

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Cantidad fraccionaria en milésimas (1.250 kg = 1250). Se usa para insumos y recetas; los productos
 * se cuentan en unidades enteras. Evita Double en existencias.
 */
@JvmInline
value class Cantidad(val milesimas: Long) : Comparable<Cantidad> {
    operator fun plus(o: Cantidad) = Cantidad(Math.addExact(milesimas, o.milesimas))
    operator fun minus(o: Cantidad) = Cantidad(Math.subtractExact(milesimas, o.milesimas))
    operator fun times(n: Long) = Cantidad(Math.multiplyExact(milesimas, n))
    override fun compareTo(other: Cantidad) = milesimas.compareTo(other.milesimas)
    val isNegative get() = milesimas < 0
    override fun toString() = format(this)

    companion object {
        val ZERO = Cantidad(0)
        fun enteras(n: Long) = Cantidad(Math.multiplyExact(n, 1000L))

        private val FORMAT = ThreadLocal.withInitial { DecimalFormat("#,##0.###", DecimalFormatSymbols(Locale.US)) }
        fun format(c: Cantidad): String = FORMAT.get()!!.format(BigDecimal.valueOf(c.milesimas, 3))

        /** 0.31.0: "1.25" / "-0.5" para editar (sin separador de miles; el mismo texto que muestra el formulario). */
        fun editable(c: Cantidad): String = BigDecimal.valueOf(c.milesimas, 3).stripTrailingZeros().toPlainString()

        /** "1.25" / "3" → milésimas. Máximo 3 decimales, sin negativos. */
        fun parse(text: String): Cantidad? = runCatching {
            val bd = BigDecimal(text.trim().replace(",", ""))
            if (bd.signum() < 0 || bd.scale() > 3) return null
            Cantidad(bd.setScale(3, RoundingMode.UNNECESSARY).movePointRight(3).longValueExact()) // no BigInteger.longValueExact (API 31)
        }.getOrNull()
    }
}
