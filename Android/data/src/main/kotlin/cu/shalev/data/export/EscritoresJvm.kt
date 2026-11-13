package cu.shalev.data.export

import cu.shalev.core.money.Money
import cu.shalev.domain.service.TablaExport
import java.io.OutputStream
import org.dhatim.fastexcel.Workbook

/**
 * XLSX con fastexcel (C6: ligero, sin Apache POI). Una hoja por tabla. Las celdas con formato monetario
 * ("1,450.00 CUP") se escriben como NÚMERO con formato [FORMATO_CUP] para que Excel pueda sumarlas.
 */
object XlsxWriter {
    /**
     * 0.25.1 (E1): sin comillas. La librería no escapa el atributo `formatCode`: con `"CUP"` el styles.xml quedaba mal
     * formado y Excel/LibreOffice decían que el archivo estaba dañado. `\C\U\P` son literales y se ve igual: «1,440.00 CUP».
     */
    const val FORMATO_CUP = "#,##0.00 \\C\\U\\P"
    private val HORA = Regex("""(\d):(\d)""")
    private val ESPACIOS = Regex("""\s{2,}""")
    private val INVALIDOS = Regex("[\\[\\]:*?/\\\\]")

    fun escribir(tablas: List<TablaExport>, out: OutputStream) {
        val wb = Workbook(out, "Shalev", "1.0")
        val usados = mutableSetOf<String>()
        tablas.forEach { t ->
            val ws = wb.newWorksheet(nombreHoja(t.titulo, usados))
            t.columnas.forEachIndexed { c, v -> ws.value(0, c, v) }
            ws.range(0, 0, 0, maxOf(0, t.columnas.lastIndex)).style().bold().fillColor("DDE3F0").set()
            // 0.26.0 (§5): importes, fechas y el valor de las tablas Dato/Valor en negrita (Excel no tiene seminegrita).
            val destacadas = BooleanArray(t.columnas.size) { DisenoPdf.destacada(t, it) }
            val anchos = anchosColumnas(t, destacadas)
            t.filas.forEachIndexed { r, fila ->
                fila.forEachIndexed { c, v ->
                    val cup = if (v.endsWith("CUP")) Money.parse(v) else null
                    val negrita = destacadas.getOrElse(c) { false }
                    val ajustar = anchos.getOrNull(c)?.let { it >= MAX_ANCHO && medir(v, negrita) + RELLENO > MAX_ANCHO } ?: false
                    if (cup != null) ws.value(r + 1, c, cup.toBigDecimal()) else ws.value(r + 1, c, v)
                    if (cup != null || negrita || ajustar) {
                        var st = ws.style(r + 1, c)
                        if (cup != null) st = st.format(FORMATO_CUP)
                        if (negrita) st = st.bold()
                        if (ajustar) st = st.wrapText(true)
                        st.set()
                    }
                }
            }
            // Pie (totales) en negrita: es dato importante.
            t.pie.forEachIndexed { i, v -> ws.value(t.filas.size + 2 + i, 0, v); ws.style(t.filas.size + 2 + i, 0).bold().set() }
            anchos.forEachIndexed { c, a -> ws.width(c, a) }
            ws.freezePane(0, 1)
        }
        wb.finish()
    }

    /** 0.26.0 (§8): ancho máximo de columna; el texto más largo se ajusta en varias líneas. */
    const val MAX_ANCHO = 80.0
    /** Margen a cada lado del texto, en «caracteres 0» de Calibri 11. */
    const val RELLENO = 1.5
    private const val MIN_ANCHO = 4.0

    /**
     * 0.26.0 (§8): anchos ajustados al texto que se VE (el importe como «1,450.00 CUP», no el número). Se mide con
     * [medir] (anchos aproximados de Calibri 11, la fuente de fastexcel) y la negrita ocupa un 10 % más. Nunca más
     * estrecha que su cabecera; como mucho [MAX_ANCHO] (lo que no cabe se ajusta en varias líneas).
     */
    fun anchosColumnas(t: TablaExport, destacadas: BooleanArray = BooleanArray(t.columnas.size) { DisenoPdf.destacada(t, it) }): List<Double> =
        t.columnas.indices.map { c ->
            val cab = medir(t.columnas[c], true)
            val cel = t.filas.maxOfOrNull { medir(it.getOrElse(c) { "" }, destacadas.getOrElse(c) { false }) } ?: 0.0
            (maxOf(cab, cel) + RELLENO).coerceIn(MIN_ANCHO, MAX_ANCHO).let { Math.round(it * 4) / 4.0 }
        }

    /** Ancho de [texto] en unidades de Excel (1 = el dígito «0» de Calibri 11). La línea más larga si hay saltos. */
    fun medir(texto: String, negrita: Boolean = false): Double {
        val base = texto.split('\n').maxOfOrNull { l -> l.sumOf { anchoCaracter(it) } } ?: 0.0
        return if (negrita) base * 1.1 else base
    }

    private fun anchoCaracter(ch: Char): Double = when {
        ch in "iljI.,;:'|!·" -> 0.45
        ch in "frt()[]-/ " -> 0.6
        ch in "mwMW@%" -> 1.45
        ch == '…' || ch == '•' -> 1.0
        ch.isDigit() -> 1.0
        ch.isUpperCase() -> 1.15
        ch.isLetter() -> 0.95
        else -> 0.9
    }

    /** Excel: ≤ 31 caracteres, sin []:*?/\ y únicos (sin distinguir mayúsculas). */
    internal fun nombreHoja(titulo: String, usados: MutableSet<String>): String {
        // 0.25.1: fechas legibles («Turno del 02-10-2026 08.00») en lugar de huecos.
        val base = titulo.replace('/', '-').replace(HORA, "\$1.\$2").replace(INVALIDOS, " ").replace(ESPACIOS, " ").trim().ifEmpty { "Hoja" }.take(31)
        var nombre = base
        var n = 2
        while (!usados.add(nombre.lowercase())) {
            val suf = " ($n)"
            nombre = base.take(31 - suf.length) + suf
            n++
        }
        return nombre
    }
}
