package cu.shalev.app.ocr

import java.io.InputStream
import java.text.Normalizer
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import kotlin.math.min
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Destino funcional del asistente de escaneo e importación de documentos. */
enum class DestinoOcr(val titulo: String) {
    INVENTARIO("Escanear o importar inventario"),
    SERVICIOS("Escanear o importar servicios"),
    VENTAS("Escanear o importar líneas de venta"),
}

/** Campo mapeable manualmente (por toque o arrastre sobre el documento o por columna de tabla). */
enum class CampoOcr(
    val etiqueta: String,
    val corto: String,
    val obligatorio: Boolean = false,
    val esMoneda: Boolean = false,
    val esCantidad: Boolean = false,
) {
    NOMBRE("Nombre *", "Nombre", obligatorio = true),
    CATEGORIA("Categoría / Tipo", "Categoría"),
    CANTIDAD("Existencias / Cantidad", "Cant.", esCantidad = true),
    UNIDAD("Unidad (kg, L, u…)", "Unidad"),
    PRECIO_VENTA("Precio de venta (CUP) *", "P. Venta", obligatorio = true, esMoneda = true),
    PRECIO_COSTO("Precio de costo (CUP) *", "Costo", obligatorio = true, esMoneda = true),
    IMPORTE("Importe (CUP) *", "Importe", obligatorio = true, esMoneda = true),
    DESCRIPCION("Descripción", "Descr."),
    CLIENTE("Cliente", "Cliente"),
    TELEFONO("Teléfono", "Tel."),
    CI("Carné de identidad", "CI"),
    ;

    companion object {
        fun para(destino: DestinoOcr): List<CampoOcr> = when (destino) {
            DestinoOcr.INVENTARIO -> listOf(NOMBRE, CATEGORIA, CANTIDAD, UNIDAD, PRECIO_VENTA, PRECIO_COSTO, DESCRIPCION)
            DestinoOcr.SERVICIOS -> listOf(NOMBRE, CATEGORIA, IMPORTE, DESCRIPCION)
            DestinoOcr.VENTAS -> listOf(NOMBRE, CANTIDAD, CLIENTE, TELEFONO, CI)
        }
    }
}

/** Región de texto reconocida sobre una imagen o página PDF/Word, en coordenadas normalizadas [0f..1f]. */
data class BloqueOcr(
    val id: Int,
    val texto: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
}

/** Tabla extraída de un archivo Excel (`.xlsx`), CSV/TSV, tabla Word (`.docx`) o agrupación de líneas OCR. */
data class TablaImportada(
    val titulo: String,
    val filas: List<List<String>>,
)

/** Contenido extraído localmente de un archivo Word (`.docx`). */
data class ResultadoDocx(
    val tablas: List<TablaImportada>,
    val parrafos: List<String>,
)

/** Artículo o servicio activo del catálogo disponible para cruzar contra líneas escaneadas en Ventas. */
data class CandidatoVentaOcr(
    val id: Long,
    val nombre: String,
    val esServicio: Boolean,
    val topeDisponible: Long?,
)

/** Fila normalizada lista para importar según el destino. */
data class BorradorImportacionOcr(
    val valores: Map<CampoOcr, String> = emptyMap(),
    val seleccionada: Boolean = true,
) {
    operator fun get(campo: CampoOcr): String = valores[campo].orEmpty()
    fun con(campo: CampoOcr, valor: String): BorradorImportacionOcr =
        copy(valores = valores + (campo to valor))
}

/**
 * Lector 100 % local, gratuito y sin límite de peticiones para archivos `.csv`/`.tsv`, `.xlsx` y `.docx`.
 * No abre red ni requiere servicios externos.
 */
object LectorDocumentosLocal {

    fun leerCsv(contenido: String, titulo: String = "Hoja CSV"): TablaImportada {
        val lineas = contenido.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n')
            .filter { it.isNotBlank() }
        if (lineas.isEmpty()) return TablaImportada(titulo, emptyList())
        val muestra = lineas.first()
        val delimitador = listOf('\t', ';', ',').maxByOrNull { sep -> muestra.count { it == sep } } ?: ','
        val filas = lineas.map { parsearLineaCsv(it, delimitador) }
            .filter { fila -> fila.any { it.isNotBlank() } }
        val ancho = filas.maxOfOrNull { it.size } ?: 0
        return TablaImportada(titulo, filas.map { f -> if (f.size < ancho) f + List(ancho - f.size) { "" } else f })
    }

    private fun parsearLineaCsv(linea: String, sep: Char): List<String> {
        val celdas = mutableListOf<String>()
        val actual = StringBuilder()
        var enComillas = false
        var i = 0
        while (i < linea.length) {
            val c = linea[i]
            when {
                c == '"' && enComillas && i + 1 < linea.length && linea[i + 1] == '"' -> {
                    actual.append('"')
                    i++
                }
                c == '"' -> enComillas = !enComillas
                c == sep && !enComillas -> {
                    celdas += actual.toString().trim()
                    actual.setLength(0)
                }
                else -> actual.append(c)
            }
            i++
        }
        celdas += actual.toString().trim()
        return celdas
    }

    /** Lee las hojas de un libro Excel `.xlsx` estándar (OOXML en ZIP) sin librerías de red. */
    fun leerXlsx(input: InputStream): List<TablaImportada> {
        val entradas = leerEntradasZip(input)
        val cadenas = entradas["xl/sharedStrings.xml"]?.let(::parsearSharedStrings).orEmpty()
        val nombresHojas = entradas["xl/workbook.xml"]?.let(::parsearNombresHojas).orEmpty()
        val hojasXml = entradas.keys
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sortedBy { it.removePrefix("xl/worksheets/sheet").removeSuffix(".xml").toIntOrNull() ?: Int.MAX_VALUE }
        return hojasXml.mapIndexedNotNull { idx, ruta ->
            val bytes = entradas[ruta] ?: return@mapIndexedNotNull null
            val filas = parsearHojaXlsx(bytes, cadenas)
            if (filas.isEmpty()) null
            else TablaImportada(nombresHojas.getOrNull(idx) ?: "Hoja ${idx + 1}", filas)
        }
    }

    /** Lee tablas `<w:tbl>` y párrafos `<w:p>` de un documento Word `.docx`. */
    fun leerDocx(input: InputStream): ResultadoDocx {
        val entradas = leerEntradasZip(input)
        val docBytes = entradas["word/document.xml"] ?: return ResultadoDocx(emptyList(), emptyList())
        val doc = parsearXmlSeguro(docBytes) ?: return ResultadoDocx(emptyList(), emptyList())
        val tablas = mutableListOf<TablaImportada>()
        val nodosTabla = doc.getElementsByTagNameNS("*", "tbl")
        for (t in 0 until nodosTabla.length) {
            val tbl = nodosTabla.item(t) as? Element ?: continue
            val filas = mutableListOf<List<String>>()
            val hijosTr = tbl.childNodes
            for (r in 0 until hijosTr.length) {
                val tr = hijosTr.item(r) as? Element ?: continue
                if (tr.localName != "tr" && !tr.tagName.endsWith(":tr") && tr.tagName != "tr") continue
                val celdas = mutableListOf<String>()
                val hijosTc = tr.childNodes
                for (c in 0 until hijosTc.length) {
                    val tc = hijosTc.item(c) as? Element ?: continue
                    if (tc.localName != "tc" && !tc.tagName.endsWith(":tc") && tc.tagName != "tc") continue
                    celdas += textoWordNodo(tc).trim()
                }
                if (celdas.any { it.isNotBlank() }) filas += celdas
            }
            if (filas.isNotEmpty()) {
                val ancho = filas.maxOf { it.size }
                tablas += TablaImportada(
                    titulo = "Tabla ${tablas.size + 1}",
                    filas = filas.map { f -> if (f.size < ancho) f + List(ancho - f.size) { "" } else f },
                )
            }
        }
        val parrafos = mutableListOf<String>()
        val body = doc.getElementsByTagNameNS("*", "body").item(0) ?: doc.documentElement
        val hijos = body.childNodes
        for (i in 0 until hijos.length) {
            val n = hijos.item(i) as? Element ?: continue
            if (n.localName == "p" || n.tagName.endsWith(":p") || n.tagName == "p") {
                val txt = textoWordNodo(n).trim()
                if (txt.isNotEmpty()) parrafos += txt
            }
        }
        return ResultadoDocx(tablas, parrafos)
    }

    private fun leerEntradasZip(input: InputStream): Map<String, ByteArray> {
        val mapa = linkedMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            var totalBytes = 0L
            while (entry != null) {
                if (!entry.isDirectory && (entry.name.endsWith(".xml") || entry.name.endsWith(".rels"))) {
                    val bytes = zip.readBytes()
                    totalBytes += bytes.size
                    if (totalBytes <= 32L * 1024L * 1024L) {
                        mapa[entry.name] = bytes
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return mapa
    }

    private fun parsearXmlSeguro(bytes: ByteArray) = runCatching {
        val dbf = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        dbf.newDocumentBuilder().parse(bytes.inputStream())
    }.getOrNull()

    private fun parsearSharedStrings(bytes: ByteArray): List<String> {
        val doc = parsearXmlSeguro(bytes) ?: return emptyList()
        val items = doc.getElementsByTagNameNS("*", "si")
        return (0 until items.length).map { i -> textoPlanoXml(items.item(i)) }
    }

    private fun parsearNombresHojas(bytes: ByteArray): List<String> {
        val doc = parsearXmlSeguro(bytes) ?: return emptyList()
        val sheets = doc.getElementsByTagNameNS("*", "sheet")
        return (0 until sheets.length).mapNotNull { i ->
            (sheets.item(i) as? Element)?.getAttribute("name")?.takeIf { it.isNotBlank() }
        }
    }

    private fun parsearHojaXlsx(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val doc = parsearXmlSeguro(bytes) ?: return emptyList()
        val nodosFila = doc.getElementsByTagNameNS("*", "row")
        val filasMap = mutableListOf<Map<Int, String>>()
        var maxCol = -1
        for (r in 0 until nodosFila.length) {
            val rowEl = nodosFila.item(r) as? Element ?: continue
            val celdas = rowEl.getElementsByTagNameNS("*", "c")
            val fila = linkedMapOf<Int, String>()
            var siguienteCol = 0
            for (c in 0 until celdas.length) {
                val cell = celdas.item(c) as? Element ?: continue
                val ref = cell.getAttribute("r")
                val col = indiceColumnaExcel(ref) ?: siguienteCol
                siguienteCol = col + 1
                val tipo = cell.getAttribute("t")
                val valor = when (tipo) {
                    "s" -> {
                        val idx = primerHijoTexto(cell, "v")?.trim()?.toIntOrNull()
                        if (idx != null && idx in shared.indices) shared[idx] else ""
                    }
                    "inlineStr" -> cell.getElementsByTagNameNS("*", "is").item(0)?.let(::textoPlanoXml).orEmpty()
                    else -> primerHijoTexto(cell, "v").orEmpty()
                }.trim()
                if (valor.isNotEmpty()) {
                    fila[col] = valor
                    if (col > maxCol) maxCol = col
                }
            }
            if (fila.isNotEmpty()) filasMap += fila
        }
        if (maxCol < 0) return emptyList()
        val ancho = (maxCol + 1).coerceAtMost(30)
        return filasMap.map { m -> List(ancho) { col -> m[col].orEmpty() } }
    }

    private fun primerHijoTexto(el: Element, local: String): String? {
        val lista = el.getElementsByTagNameNS("*", local)
        return if (lista.length > 0) lista.item(0).textContent else null
    }

    private fun textoPlanoXml(node: Node): String {
        val sb = StringBuilder()
        fun recorrer(n: Node) {
            if (n.nodeType == Node.TEXT_NODE || n.nodeType == Node.CDATA_SECTION_NODE) {
                sb.append(n.nodeValue.orEmpty())
            } else {
                val hijos = n.childNodes
                for (i in 0 until hijos.length) recorrer(hijos.item(i))
            }
        }
        recorrer(node)
        return sb.toString()
    }

    private fun textoWordNodo(node: Node): String {
        val sb = StringBuilder()
        fun recorrer(n: Node) {
            if (n is Element) {
                val ln = n.localName ?: n.tagName.substringAfter(':')
                when (ln) {
                    "t" -> { sb.append(n.textContent.orEmpty()); return }
                    "tab" -> { sb.append('\t'); return }
                    "br", "cr" -> { sb.append(' '); return }
                }
            }
            val hijos = n.childNodes
            for (i in 0 until hijos.length) recorrer(hijos.item(i))
        }
        recorrer(node)
        return sb.toString()
    }

    internal fun indiceColumnaExcel(ref: String?): Int? {
        if (ref.isNullOrBlank()) return null
        val letras = ref.takeWhile { it in 'A'..'Z' || it in 'a'..'z' }.uppercase()
        if (letras.isEmpty()) return null
        var acc = 0
        for (ch in letras) acc = acc * 26 + (ch - 'A' + 1)
        return acc - 1
    }
}

/** Utilidades puras de mapeo visual (regiones arrastradas, columnas, normalización de importes y cruce de catálogo). */
object MapeadorOcr {

    fun plano(texto: String): String =
        Normalizer.normalize(texto.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /** Extrae un importe CUP válido ("$1,450.50 CUP" / "1450,5" → "1450.50" / "1450.5"). */
    fun extraerMoneda(raw: String): String {
        val limpio = raw.replace("CUP", "", ignoreCase = true)
            .replace("$", "")
            .replace("MN", "", ignoreCase = true)
            .trim()
        val m = Regex("""\d[\d.,\s]*""").find(limpio)?.value?.replace(" ", "") ?: return ""
        val normalizado = normalizarSeparadorDecimal(m)
        val partes = normalizado.split('.')
        val enteros = partes[0].filter { it.isDigit() }.trimStart('0').ifEmpty { if (normalizado.any { it.isDigit() }) "0" else "" }
        if (enteros.isEmpty()) return ""
        val dec = partes.getOrNull(1)?.filter { it.isDigit() }?.take(2).orEmpty()
        return if (dec.isEmpty()) enteros else "$enteros.$dec"
    }

    /** Extrae una cantidad no negativa ("12 uds" → "12", "1,25 kg" → "1.25"). */
    fun extraerCantidad(raw: String, permitirDecimales: Boolean = true): String {
        val m = Regex("""\d[\d.,]*""").find(raw.trim())?.value ?: return ""
        val normalizado = normalizarSeparadorDecimal(m)
        val partes = normalizado.split('.')
        val enteros = partes[0].filter { it.isDigit() }.trimStart('0').ifEmpty { if (normalizado.any { it.isDigit() }) "0" else "" }
        if (enteros.isEmpty()) return ""
        if (!permitirDecimales) return enteros.take(7)
        val dec = partes.getOrNull(1)?.filter { it.isDigit() }?.take(3).orEmpty()
        return if (dec.isEmpty()) enteros.take(7) else "${enteros.take(7)}.$dec"
    }

    fun extraerUnidad(raw: String): String {
        val p = plano(raw)
        return when {
            p in setOf("kg", "kilo", "kilos", "kilogramo", "kilogramos") || p.endsWith(" kg") -> "kg"
            p in setOf("g", "gr", "gramo", "gramos") || p.endsWith(" g") -> "g"
            p in setOf("l", "lt", "litro", "litros") || p.endsWith(" l") -> "L"
            p in setOf("ml", "mililitro", "mililitros") || p.endsWith(" ml") -> "ml"
            p in setOf("u", "ud", "uds", "unidad", "unidades") -> "u"
            else -> ""
        }
    }

    private fun normalizarSeparadorDecimal(num: String): String {
        val ultimaComa = num.lastIndexOf(',')
        val ultimoPunto = num.lastIndexOf('.')
        return when {
            ultimaComa > ultimoPunto -> {
                val sinPuntos = num.replace(".", "")
                val idx = sinPuntos.lastIndexOf(',')
                val decLen = sinPuntos.length - idx - 1
                if (decLen in 1..2 || (decLen == 3 && sinPuntos.count { it == ',' } == 1 && idx <= 2)) {
                    sinPuntos.substring(0, idx).replace(",", "") + "." + sinPuntos.substring(idx + 1)
                } else sinPuntos.replace(",", "")
            }
            ultimoPunto > ultimaComa -> {
                val sinComas = num.replace(",", "")
                val idx = sinComas.lastIndexOf('.')
                sinComas.substring(0, idx).replace(".", "") + "." + sinComas.substring(idx + 1)
            }
            else -> num
        }
    }

    /** Limpia el texto asignado manualmente a un campo según su tipo. */
    fun sanitizarParaCampo(campo: CampoOcr, texto: String): String = when {
        campo.esMoneda -> extraerMoneda(texto)
        campo.esCantidad -> extraerCantidad(texto, permitirDecimales = true)
        campo == CampoOcr.UNIDAD -> extraerUnidad(texto).ifEmpty { texto.trim().take(8) }
        campo == CampoOcr.TELEFONO -> texto.filter { it.isDigit() }.removePrefix("53").take(15)
        campo == CampoOcr.CI -> texto.filter { it.isLetterOrDigit() }.uppercase().take(20)
        else -> texto.replace(Regex("\\s+"), " ").trim().take(80)
    }

    /** Bloques OCR cuyo centro o al menos el 35 % de su área cae dentro del rectángulo normalizado arrastrado. */
    fun bloquesEnRectangulo(bloques: List<BloqueOcr>, x1: Float, y1: Float, x2: Float, y2: Float): List<BloqueOcr> {
        val minX = min(x1, x2)
        val maxX = max(x1, x2)
        val minY = min(y1, y2)
        val maxY = max(y1, y2)
        return bloques.filter { b ->
            val centroDentro = b.centerX in minX..maxX && b.centerY in minY..maxY
            if (centroDentro) return@filter true
            val ix = (min(b.right, maxX) - max(b.left, minX)).coerceAtLeast(0f)
            val iy = (min(b.bottom, maxY) - max(b.top, minY)).coerceAtLeast(0f)
            val areaBloque = ((b.right - b.left) * (b.bottom - b.top)).coerceAtLeast(1e-6f)
            (ix * iy) / areaBloque >= 0.35f
        }.sortedWith(compareBy<BloqueOcr> { (it.top * 25f).toInt() }.thenBy { it.left })
    }

    /** Convierte bloques OCR sueltos en una tabla visual agrupando por renglón vertical y orden horizontal. */
    fun agruparBloquesEnTabla(bloques: List<BloqueOcr>): TablaImportada {
        if (bloques.isEmpty()) return TablaImportada("Texto reconocido", emptyList())
        val ordenados = bloques.sortedBy { it.centerY }
        val renglones = mutableListOf<MutableList<BloqueOcr>>()
        for (b in ordenados) {
            val alto = (b.bottom - b.top).coerceAtLeast(0.015f)
            val existente = renglones.lastOrNull { fila ->
                val promY = fila.map { it.centerY }.average().toFloat()
                kotlin.math.abs(b.centerY - promY) <= alto * 0.75f
            }
            if (existente != null) existente += b else renglones += mutableListOf(b)
        }
        val filasTexto = renglones.map { fila ->
            val izqDer = fila.sortedBy { it.left }
            // Une palabras cercanas y separa columnas cuando hay un salto horizontal amplio o números finales.
            val columnas = mutableListOf<StringBuilder>()
            var prevRight: Float? = null
            for (pieza in izqDer) {
                val gap = prevRight?.let { pieza.left - it } ?: 0f
                if (columnas.isEmpty() || gap > 0.045f) {
                    columnas += StringBuilder(pieza.texto.trim())
                } else {
                    columnas.last().append(' ').append(pieza.texto.trim())
                }
                prevRight = pieza.right
            }
            columnas.map { it.toString().trim() }.filter { it.isNotEmpty() }
        }.filter { it.isNotEmpty() }
        val ancho = filasTexto.maxOfOrNull { it.size } ?: 0
        return TablaImportada(
            titulo = "Renglones detectados",
            filas = filasTexto.map { f -> if (f.size < ancho) f + List(ancho - f.size) { "" } else f },
        )
    }

    /** Detecta automáticamente a qué campo de Shalev corresponde cada encabezado de columna. */
    fun sugerirColumnas(encabezados: List<String>, destino: DestinoOcr): Map<Int, CampoOcr> {
        val permitidos = CampoOcr.para(destino).toSet()
        val asignados = linkedMapOf<Int, CampoOcr>()
        val usados = mutableSetOf<CampoOcr>()
        encabezados.forEachIndexed { idx, raw ->
            val h = plano(raw)
            val sugerido = when {
                h.contains("nombre") || h.contains("producto") || h.contains("articulo") ||
                    h.contains("servicio") || h.contains("insumo") || h == "item" -> CampoOcr.NOMBRE
                h.contains("costo") || h.contains("compra") -> CampoOcr.PRECIO_COSTO
                destino == DestinoOcr.SERVICIOS && (h.contains("importe") || h.contains("precio") || h.contains("tarifa") || h.contains("valor")) ->
                    CampoOcr.IMPORTE
                h.contains("venta") || h.contains("precio") || h.contains("pvp") || h.contains("importe") ->
                    CampoOcr.PRECIO_VENTA
                h.contains("cant") || h.contains("exist") || h.contains("stock") || h.contains("unid") && destino == DestinoOcr.VENTAS ->
                    CampoOcr.CANTIDAD
                h == "um" || h.contains("medida") || h == "unidad" -> CampoOcr.UNIDAD
                h.contains("categ") || h.contains("tipo") || h.contains("familia") -> CampoOcr.CATEGORIA
                h.contains("cliente") || h.contains("comprador") -> CampoOcr.CLIENTE
                h.contains("tel") || h.contains("movil") || h.contains("celular") -> CampoOcr.TELEFONO
                h == "ci" || h.contains("carne") || h.contains("identidad") -> CampoOcr.CI
                h.contains("desc") || h.contains("nota") || h.contains("detalle") -> CampoOcr.DESCRIPCION
                else -> null
            }
            if (sugerido != null && sugerido in permitidos && sugerido !in usados) {
                asignados[idx] = sugerido
                usados += sugerido
            }
        }
        return asignados
    }

    fun pareceEncabezado(primeraFila: List<String>, destino: DestinoOcr): Boolean =
        sugerirColumnas(primeraFila, destino).isNotEmpty()

    /** Busca el artículo o servicio activo más parecido al nombre leído por OCR o desde tabla. */
    fun emparejarArticuloVenta(nombreBuscado: String, candidatos: List<CandidatoVentaOcr>): CandidatoVentaOcr? {
        val q = plano(nombreBuscado)
        if (q.isEmpty() || candidatos.isEmpty()) return null
        candidatos.firstOrNull { plano(it.nombre) == q }?.let { return it }
        candidatos.firstOrNull { plano(it.nombre).contains(q) || q.contains(plano(it.nombre)) }?.let { return it }
        val tokensQ = q.split(' ').filter { it.length >= 2 }.toSet()
        if (tokensQ.isEmpty()) return null
        return candidatos
            .map { c ->
                val tc = plano(c.nombre).split(' ').filter { it.length >= 2 }.toSet()
                val comun = tokensQ.intersect(tc).size
                c to (if (tc.isEmpty()) 0.0 else comun.toDouble() / max(tokensQ.size, tc.size))
            }
            .filter { it.second >= 0.5 }
            .maxByOrNull { it.second }
            ?.first
    }
}
