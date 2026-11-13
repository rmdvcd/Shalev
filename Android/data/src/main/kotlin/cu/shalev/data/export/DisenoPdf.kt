package cu.shalev.data.export

import cu.shalev.domain.service.TablaExport

/**
 * 0.25.1 (E2): reglas de maquetación de las tablas del PDF, sin Android (testeables en la JVM):
 * - Más de [MAX_COLUMNAS_VERTICAL] columnas → hoja A4 **horizontal**.
 * - Las columnas «compactas» (fechas, importes, cantidades, códigos, CI, teléfonos, números de transacción) reciben el
 *   ancho de su contenido más largo: nunca se cortan. El resto se reparte lo que sobra y, si no cabe, se corta con «…».
 * - Importes y cantidades van alineados a la derecha (cabecera incluida).
 */
object DisenoPdf {
    const val MAX_COLUMNAS_VERTICAL = 5
    const val RELLENO = 8f
    /** Ancho mínimo de una columna de texto libre (nombre, nota…). */
    const val MIN_LIBRE = 48f
    private const val MAX_NATURAL = 260f

    private val NUMERO = Regex("""^[-+−]?[\d.,]+(\s?(CUP|%|[\p{L}]{1,6}))?$""")
    private val FECHA = Regex("""^\d{2}/\d{2}/\d{4}( \d{2}:\d{2})?$""")
    /** Códigos de barras, CI (también oculto «••••345»), teléfonos y números de transacción: sin espacios internos. */
    private val CODIGO = Regex("""^[+•\dA-Z][\dA-Z•\-]{2,24}$""")

    fun horizontal(t: TablaExport): Boolean = t.columnas.size > MAX_COLUMNAS_VERTICAL

    private fun valores(t: TablaExport, c: Int): List<String> = t.filas.take(200).mapNotNull { it.getOrNull(c)?.trim()?.takeIf(String::isNotEmpty) }

    /** Importes y cantidades: a la derecha. */
    fun derecha(t: TablaExport, c: Int): Boolean = valores(t, c).let { v -> v.isNotEmpty() && v.all { NUMERO.matches(it) } }

    /**
     * 0.26.0 (§5): columnas que van en seminegrita (PDF) o negrita (Excel): importes, cantidades y fechas, y la columna
     * Valor de las tablas Dato/Valor (resumen, arqueo, ficha, perfil).
     */
    fun destacada(t: TablaExport, c: Int): Boolean {
        if (t.columnas.size == 2 && c == 1 && t.columnas[1] == "Valor") return true
        return valores(t, c).let { v -> v.isNotEmpty() && v.all { (NUMERO.matches(it) && !CODIGO_BARRAS.matches(it)) || FECHA.matches(it) } }
    }

    /** 0.26.0: un código de barras o un teléfono (8 o más cifras seguidas) no es un importe: no va destacado. */
    private val CODIGO_BARRAS = Regex("""^\+?\d{8,}$""")

    /** No se corta nunca. */
    fun compacta(t: TablaExport, c: Int): Boolean =
        valores(t, c).let { v -> v.isNotEmpty() && v.all { NUMERO.matches(it) || FECHA.matches(it) || CODIGO.matches(it) } }

    /**
     * Anchos para un ancho útil [util]. [medir] da el ancho del texto (negrita = cabecera o columna destacada). Las compactas reciben su
     * ancho natural; las libres se reparten el resto en proporción a su contenido (mínimo [MIN_LIBRE]). Si ni así cabe,
     * todo se escala en proporción (caso extremo: muchas columnas compactas).
     */
    fun anchos(t: TablaExport, util: Float, medir: (String, Boolean) -> Float): FloatArray {
        val n = t.columnas.size
        if (n == 0) return FloatArray(0)
        val natural = FloatArray(n) { c ->
            val cab = medir(t.columnas[c], true)
            // 0.26.0: las destacadas (seminegrita) se miden como negrita para que no se corten.
            val destaca = destacada(t, c)
            val cel = valores(t, c).maxOfOrNull { medir(it, destaca) } ?: 0f
            (maxOf(cab, cel) + RELLENO).coerceAtMost(MAX_NATURAL)
        }
        val fija = BooleanArray(n) { compacta(t, it) }
        val total = natural.sum()
        if (total <= util) {
            // Sobra espacio: se reparte entre las libres (o entre todas si no hay libres).
            val libres = (0 until n).filter { !fija[it] }.ifEmpty { (0 until n).toList() }
            val base = libres.sumOf { natural[it].toDouble() }.toFloat().coerceAtLeast(1f)
            val extra = util - total
            return FloatArray(n) { c -> natural[c] + if (c in libres) extra * natural[c] / base else 0f }
        }
        val usadoFijas = (0 until n).filter { fija[it] }.sumOf { natural[it].toDouble() }.toFloat()
        val libres = (0 until n).filter { !fija[it] }
        val resto = util - usadoFijas
        if (libres.isNotEmpty() && resto >= libres.size * MIN_LIBRE) {
            val pesoLibres = libres.sumOf { natural[it].toDouble() }.toFloat().coerceAtLeast(1f)
            val minimos = FloatArray(n) { c -> if (c in libres) MIN_LIBRE else 0f }
            val repartir = resto - libres.size * MIN_LIBRE
            return FloatArray(n) { c ->
                if (fija[c]) natural[c] else minimos[c] + repartir * natural[c] / pesoLibres
            }.also { a ->
                // Una libre nunca recibe más de lo que necesita: lo que sobre vuelve a las demás libres.
                var sobra = 0f
                libres.forEach { c -> if (a[c] > natural[c]) { sobra += a[c] - natural[c]; a[c] = natural[c] } }
                val faltan = libres.filter { a[it] < natural[it] }
                if (sobra > 0f && faltan.isNotEmpty()) faltan.forEach { a[it] += sobra / faltan.size }
                else if (sobra > 0f) libres.forEach { a[it] += sobra / libres.size }
            }
        }
        return FloatArray(n) { c -> util * natural[c] / total }
    }
}
