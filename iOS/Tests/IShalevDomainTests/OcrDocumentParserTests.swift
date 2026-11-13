import XCTest
@testable import IShalevDomain

final class OcrDocumentParserTests: XCTestCase {

    func testCSVParserDetectsSemicolonAndSuggestsInventoryColumns() {
        let csv = """
        Producto;Categoría;Existencias;Precio venta;Costo
        Refresco lata;Bebidas;24;280.00;190.50
        Agua 500 ml;Bebidas;12;150;95
        """
        let table = LocalDocumentReader.readCSV(csv)
        XCTAssertEqual(table.rows.count, 3)
        let suggested = OcrMapper.suggestColumns(headers: table.rows[0], target: .inventory)
        XCTAssertEqual(suggested[0], .name)
        XCTAssertEqual(suggested[1], .category)
        XCTAssertEqual(suggested[2], .quantity)
        XCTAssertEqual(suggested[3], .salePrice)
        XCTAssertEqual(suggested[4], .costPrice)
    }

    func testXLSXAndDOCXReadersParseStoredZipEntries() {
        let xlsx = makeStoredZip([
            ("xl/workbook.xml", """
             <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
               <sheets><sheet name="Servicios" sheetId="1"/></sheets>
             </workbook>
             """),
            ("xl/sharedStrings.xml", """
             <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
               <si><t>Servicio</t></si>
               <si><t>Importe</t></si>
               <si><t>Manicura</t></si>
             </sst>
             """),
            ("xl/worksheets/sheet1.xml", """
             <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
               <sheetData>
                 <row r="1"><c r="A1" t="s"><v>0</v></c><c r="C1" t="s"><v>1</v></c></row>
                 <row r="2"><c r="A2" t="s"><v>2</v></c><c r="C2"><v>600</v></c></row>
               </sheetData>
             </worksheet>
             """),
        ])
        let sheets = LocalDocumentReader.readXLSX(xlsx)
        XCTAssertEqual(sheets.count, 1)
        XCTAssertEqual(sheets[0].title, "Servicios")
        XCTAssertEqual(sheets[0].rows[0], ["Servicio", "", "Importe"])
        XCTAssertEqual(sheets[0].rows[1], ["Manicura", "", "600"])

        let docx = makeStoredZip([
            ("word/document.xml", """
             <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
               <w:body>
                 <w:p><w:r><w:t>Registro previo</w:t></w:r></w:p>
                 <w:tbl>
                   <w:tr>
                     <w:tc><w:p><w:r><w:t>Artículo</w:t></w:r></w:p></w:tc>
                     <w:tc><w:p><w:r><w:t>Cantidad</w:t></w:r></w:p></w:tc>
                   </w:tr>
                   <w:tr>
                     <w:tc><w:p><w:r><w:t>Pizza Napolitana</w:t></w:r></w:p></w:tc>
                     <w:tc><w:p><w:r><w:t>2</w:t></w:r></w:p></w:tc>
                   </w:tr>
                 </w:tbl>
               </w:body>
             </w:document>
             """),
        ])
        let doc = LocalDocumentReader.readDOCX(docx)
        XCTAssertEqual(doc.paragraphs, ["Registro previo"])
        XCTAssertEqual(doc.tables.count, 1)
        XCTAssertEqual(doc.tables[0].rows[1], ["Pizza Napolitana", "2"])
    }

    func testVisualRectangleSelectionAndCatalogMatching() {
        let blocks = [
            OcrTextBlock(id: 1, text: "Café", left: 0.10, top: 0.20, right: 0.25, bottom: 0.26),
            OcrTextBlock(id: 2, text: "expreso", left: 0.27, top: 0.20, right: 0.45, bottom: 0.26),
            OcrTextBlock(id: 3, text: "$1,250.50 CUP", left: 0.70, top: 0.20, right: 0.92, bottom: 0.26),
        ]
        let picked = OcrMapper.blocksInRect(blocks, x1: 0.08, y1: 0.18, x2: 0.50, y2: 0.30)
        XCTAssertEqual(picked.map(\.id), [1, 2])
        XCTAssertEqual(OcrMapper.extractMoney(blocks[2].text), "1250.50")

        let id = UUID()
        let candidates = [
            OcrSaleCandidate(id: id, name: "Cafe expreso doble", isService: false, maxAvailable: 5),
        ]
        XCTAssertEqual(OcrMapper.matchSaleCandidate("Café expreso", in: candidates)?.id, id)
    }

    private func makeStoredZip(_ entries: [(String, String)]) -> Data {
        var archive = Data()
        var centralDirectory = Data()
        for (name, content) in entries {
            let nameBytes = Data(name.utf8)
            let payload = Data(content.utf8)
            let localOffset = UInt32(archive.count)
            // Local file header
            archive.append(contentsOf: [0x50, 0x4B, 0x03, 0x04])
            appendLE16(20, to: &archive)
            appendLE16(0, to: &archive)
            appendLE16(0, to: &archive) // stored
            appendLE16(0, to: &archive)
            appendLE16(0, to: &archive)
            appendLE32(0, to: &archive)
            appendLE32(UInt32(payload.count), to: &archive)
            appendLE32(UInt32(payload.count), to: &archive)
            appendLE16(UInt16(nameBytes.count), to: &archive)
            appendLE16(0, to: &archive)
            archive.append(nameBytes)
            archive.append(payload)

            // Central directory header
            centralDirectory.append(contentsOf: [0x50, 0x4B, 0x01, 0x02])
            appendLE16(20, to: &centralDirectory)
            appendLE16(20, to: &centralDirectory)
            appendLE16(0, to: &centralDirectory)
            appendLE16(0, to: &centralDirectory)
            appendLE16(0, to: &centralDirectory)
            appendLE16(0, to: &centralDirectory)
            appendLE32(0, to: &centralDirectory)
            appendLE32(UInt32(payload.count), to: &centralDirectory)
            appendLE32(UInt32(payload.count), to: &centralDirectory)
            appendLE16(UInt16(nameBytes.count), to: &centralDirectory)
            appendLE16(0, to: &centralDirectory)
            appendLE16(0, to: &centralDirectory)
            appendLE16(0, to: &centralDirectory)
            appendLE16(0, to: &centralDirectory)
            appendLE32(0, to: &centralDirectory)
            appendLE32(localOffset, to: &centralDirectory)
            centralDirectory.append(nameBytes)
        }
        let cdOffset = UInt32(archive.count)
        let cdSize = UInt32(centralDirectory.count)
        archive.append(centralDirectory)
        archive.append(contentsOf: [0x50, 0x4B, 0x05, 0x06])
        appendLE16(0, to: &archive)
        appendLE16(0, to: &archive)
        appendLE16(UInt16(entries.count), to: &archive)
        appendLE16(UInt16(entries.count), to: &archive)
        appendLE32(cdSize, to: &archive)
        appendLE32(cdOffset, to: &archive)
        appendLE16(0, to: &archive)
        return archive
    }

    private func appendLE16(_ v: UInt16, to data: inout Data) {
        data.append(UInt8(v & 0xFF))
        data.append(UInt8((v >> 8) & 0xFF))
    }

    private func appendLE32(_ v: UInt32, to data: inout Data) {
        data.append(UInt8(v & 0xFF))
        data.append(UInt8((v >> 8) & 0xFF))
        data.append(UInt8((v >> 16) & 0xFF))
        data.append(UInt8((v >> 24) & 0xFF))
    }
}
