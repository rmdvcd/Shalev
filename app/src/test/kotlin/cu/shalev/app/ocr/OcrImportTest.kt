package cu.shalev.app.ocr

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrImportTest {

    @Test
    fun `lector CSV detecta punto y coma y mapea encabezados de inventario`() {
        val csv = """
            Producto;Categoría;Existencias;Precio venta;Costo
            Refresco lata;Bebidas;24;280.00;190.50
            Agua 500 ml;Bebidas;12;150;95
        """.trimIndent()
        val tabla = LectorDocumentosLocal.leerCsv(csv)
        assertEquals(3, tabla.filas.size)
        val mapeo = MapeadorOcr.sugerirColumnas(tabla.filas.first(), DestinoOcr.INVENTARIO)
        assertEquals(CampoOcr.NOMBRE, mapeo[0])
        assertEquals(CampoOcr.CATEGORIA, mapeo[1])
        assertEquals(CampoOcr.CANTIDAD, mapeo[2])
        assertEquals(CampoOcr.PRECIO_VENTA, mapeo[3])
        assertEquals(CampoOcr.PRECIO_COSTO, mapeo[4])
    }

    @Test
    fun `lector XLSX local extrae cadenas compartidas y celdas dispersas`() {
        val xlsxBytes = crearZipEnMemoria(
            "xl/workbook.xml" to """
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheets><sheet name="Inventario" sheetId="1"/></sheets>
                </workbook>
            """.trimIndent(),
            "xl/sharedStrings.xml" to """
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <si><t>Servicio</t></si>
                  <si><t>Importe</t></si>
                  <si><t>Corte clásico</t></si>
                </sst>
            """.trimIndent(),
            "xl/worksheets/sheet1.xml" to """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="C1" t="s"><v>1</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>2</v></c>
                      <c r="C2"><v>450</v></c>
                    </row>
                  </sheetData>
                </worksheet>
            """.trimIndent(),
        )
        val hojas = LectorDocumentosLocal.leerXlsx(xlsxBytes.inputStream())
        assertEquals(1, hojas.size)
        assertEquals("Inventario", hojas[0].titulo)
        assertEquals(listOf("Servicio", "", "Importe"), hojas[0].filas[0])
        assertEquals(listOf("Corte clásico", "", "450"), hojas[0].filas[1])
    }

    @Test
    fun `lector DOCX local extrae tablas y parrafos`() {
        val docxBytes = crearZipEnMemoria(
            "word/document.xml" to """
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:t>Lista manuscrita digitalizada</w:t></w:r></w:p>
                    <w:tbl>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t>Artículo</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>Cantidad</w:t></w:r></w:p></w:tc>
                      </w:tr>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t>Pizza Napolitana</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>3</w:t></w:r></w:p></w:tc>
                      </w:tr>
                    </w:tbl>
                  </w:body>
                </w:document>
            """.trimIndent(),
        )
        val res = LectorDocumentosLocal.leerDocx(docxBytes.inputStream())
        assertEquals(listOf("Lista manuscrita digitalizada"), res.parrafos)
        assertEquals(1, res.tablas.size)
        assertEquals(listOf("Pizza Napolitana", "3"), res.tablas[0].filas[1])
    }

    @Test
    fun `seleccion visual en rectangulo y sanitizacion de moneda y catalogo`() {
        val bloques = listOf(
            BloqueOcr(1, "Café", 0.10f, 0.20f, 0.25f, 0.26f),
            BloqueOcr(2, "expreso", 0.27f, 0.20f, 0.45f, 0.26f),
            BloqueOcr(3, "$1,250.50 CUP", 0.70f, 0.20f, 0.92f, 0.26f),
        )
        val elegidos = MapeadorOcr.bloquesEnRectangulo(bloques, 0.08f, 0.18f, 0.50f, 0.30f)
        assertEquals(listOf(1, 2), elegidos.map { it.id })
        assertEquals("1250.50", MapeadorOcr.extraerMoneda(bloques[2].texto))

        val candidatos = listOf(
            CandidatoVentaOcr(id = 10L, nombre = "Cafe expreso doble", esServicio = false, topeDisponible = 4L),
            CandidatoVentaOcr(id = 11L, nombre = "Jugo natural", esServicio = false, topeDisponible = 8L),
        )
        val match = MapeadorOcr.emparejarArticuloVenta("Café expreso", candidatos)
        assertTrue(match != null && match.id == 10L)
    }

    private fun crearZipEnMemoria(vararg entradas: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((ruta, texto) in entradas) {
                zip.putNextEntry(ZipEntry(ruta))
                zip.write(texto.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
