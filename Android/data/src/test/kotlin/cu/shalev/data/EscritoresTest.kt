package cu.shalev.data

import cu.shalev.data.export.XlsxWriter
import cu.shalev.domain.service.TablaExport
import java.io.ByteArrayOutputStream
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscritoresTest {
    private val tabla = TablaExport(
        titulo = "Ventas: septiembre/2026",
        columnas = listOf("Producto", "Importe"),
        filas = listOf(listOf("Refresco ñandú", "1,450 CUP"), listOf("Pan", "20 CUP")),
        pie = listOf("Total: 1,470 CUP"),
    )

    @Test fun `xlsx es un zip OOXML con una hoja por tabla e importes numéricos`() {
        val out = ByteArrayOutputStream()
        XlsxWriter.escribir(listOf(tabla, tabla.copy(titulo = "Ventas: septiembre/2026")), out)
        // ZipFile lee el directorio central, como Excel/LibreOffice (opczip escribe en streaming).
        val tmp = java.io.File.createTempFile("shalev", ".xlsx").apply { deleteOnExit(); writeBytes(out.toByteArray()) }
        val entradas = ZipFile(tmp).use { z ->
            z.entries().asSequence().associate { e -> e.name to z.getInputStream(e).readBytes().toString(Charsets.UTF_8) }
        }
        assertTrue(entradas.keys.containsAll(listOf("[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml")))
        val wb = entradas.getValue("xl/workbook.xml")
        assertTrue(wb, wb.contains("Ventas septiembre-2026\"") && wb.contains("(2)"))
        val hoja = entradas.getValue("xl/worksheets/sheet1.xml")
        assertTrue("importe como número", hoja.contains("<v>1450.00</v>") || hoja.contains("<v>1450</v>") || hoja.contains("<v>1450.0</v>"))
    }

    @Test fun `nombres de hoja válidos y únicos`() {
        val usados = mutableSetOf<String>()
        val a = XlsxWriter.nombreHoja("x".repeat(40), usados)
        val b = XlsxWriter.nombreHoja("X".repeat(40), usados)
        assertEquals(31, a.length)
        assertEquals(31, b.length)
        assertTrue(b.endsWith(" (2)"))
        assertEquals("Hoja", XlsxWriter.nombreHoja("[]", mutableSetOf()))
        assertEquals("Turno del 02-10-2026 08.00", XlsxWriter.nombreHoja("Turno del 02/10/2026 08:00", mutableSetOf()))
    }

    /** 0.25.1 (E1): TODAS las partes XML del xlsx están bien formadas (en 0.25.0 styles.xml no lo estaba). */
    @Test fun `todas las partes xml del xlsx estan bien formadas`() {
        val out = ByteArrayOutputStream()
        XlsxWriter.escribir(listOf(tabla), out)
        val tmp = java.io.File.createTempFile("shalev", ".xlsx").apply { deleteOnExit(); writeBytes(out.toByteArray()) }
        val f = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        var partes = 0
        ZipFile(tmp).use { z ->
            z.entries().asSequence().filter { it.name.endsWith(".xml") || it.name.endsWith(".rels") }.forEach { e ->
                val bytes = z.getInputStream(e).readBytes()
                try {
                    f.newDocumentBuilder().apply { setErrorHandler(null) }.parse(bytes.inputStream())
                } catch (ex: Exception) {
                    throw AssertionError("${e.name} mal formado: ${ex.message}")
                }
                partes++
            }
            val estilos = z.getInputStream(z.getEntry("xl/styles.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(estilos, estilos.contains("formatCode=\"#,##0.00 \\C\\U\\P\""))
        }
        assertTrue(partes >= 5)
    }

    /** 0.26.0 (§8): el ancho sigue al texto que se ve; nunca menor que la cabecera; lo muy largo se limita a 80. */
    @Test fun anchosAjustadosAlTexto() {
        val t = cu.shalev.domain.service.TablaExport(
            "Ventas", listOf("Fecha", "Importe", "Artículos", "Nº"),
            listOf(
                listOf("02/10/2026 09:20", "1,450 CUP", "Refresco de lata ×2", "7"),
                listOf("02/10/2026 09:31", "270 CUP", "x".repeat(200), "12"),
            ),
        )
        val a = XlsxWriter.anchosColumnas(t)
        assertTrue(a[0] >= XlsxWriter.medir("02/10/2026 09:20", true))
        assertTrue(a[1] >= XlsxWriter.medir("1,450 CUP", true))
        assertEquals(XlsxWriter.MAX_ANCHO, a[2], 0.001)
        assertTrue(a[3] >= XlsxWriter.medir("Nº", true) && a[3] < 6.0)
        assertTrue(XlsxWriter.medir("MMMM") > XlsxWriter.medir("iiii") * 2)
        assertTrue(XlsxWriter.medir("Total", true) > XlsxWriter.medir("Total"))
    }
}
