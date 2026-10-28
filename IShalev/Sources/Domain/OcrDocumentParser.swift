import Foundation
import zlib

enum OcrImportTarget: String, CaseIterable, Identifiable {
    case inventory
    case services
    case sales

    var id: String { rawValue }

    var title: String {
        switch self {
        case .inventory: "Escanear o importar inventario"
        case .services: "Escanear o importar servicios"
        case .sales: "Escanear o importar líneas de venta"
        }
    }
}

enum OcrField: String, CaseIterable, Identifiable, Hashable {
    case name
    case category
    case quantity
    case unit
    case salePrice
    case costPrice
    case amount
    case description
    case customer
    case phone
    case ci

    var id: String { rawValue }

    var label: String {
        switch self {
        case .name: "Nombre *"
        case .category: "Categoría / Tipo"
        case .quantity: "Existencias / Cantidad"
        case .unit: "Unidad (kg, L, u…)"
        case .salePrice: "Precio de venta (CUP) *"
        case .costPrice: "Precio de costo (CUP) *"
        case .amount: "Importe (CUP) *"
        case .description: "Descripción"
        case .customer: "Cliente"
        case .phone: "Teléfono"
        case .ci: "Carné de identidad"
        }
    }

    var shortLabel: String {
        switch self {
        case .name: "Nombre"
        case .category: "Categoría"
        case .quantity: "Cant."
        case .unit: "Unidad"
        case .salePrice: "P. Venta"
        case .costPrice: "Costo"
        case .amount: "Importe"
        case .description: "Descr."
        case .customer: "Cliente"
        case .phone: "Tel."
        case .ci: "CI"
        }
    }

    var isMoney: Bool {
        self == .salePrice || self == .costPrice || self == .amount
    }

    var isQuantity: Bool {
        self == .quantity
    }

    static func fields(for target: OcrImportTarget) -> [OcrField] {
        switch target {
        case .inventory: [.name, .category, .quantity, .unit, .salePrice, .costPrice, .description]
        case .services: [.name, .category, .amount, .description]
        case .sales: [.name, .quantity, .customer, .phone, .ci]
        }
    }
}

struct OcrTextBlock: Identifiable, Equatable {
    let id: Int
    let text: String
    let left: Double
    let top: Double
    let right: Double
    let bottom: Double

    var centerX: Double { (left + right) * 0.5 }
    var centerY: Double { (top + bottom) * 0.5 }
}

struct ImportedTable: Equatable {
    var title: String
    var rows: [[String]]
}

struct ImportedDocxContent: Equatable {
    var tables: [ImportedTable]
    var paragraphs: [String]
}

struct OcrSaleCandidate: Identifiable, Equatable {
    let id: UUID
    let name: String
    let isService: Bool
    let maxAvailable: Int
}

struct OcrDraftRow: Identifiable, Equatable {
    var id = UUID()
    var values: [OcrField: String] = [:]
    var isSelected: Bool = true

    subscript(field: OcrField) -> String {
        get { values[field] ?? "" }
        set { values[field] = newValue }
    }
}

// MARK: - Lector 100 % local para CSV/TSV, Excel (.xlsx) y Word (.docx)

enum LocalDocumentReader {

    static func readCSV(_ content: String, title: String = "Hoja CSV") -> ImportedTable {
        let lines = content
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .split(separator: "\n", omittingEmptySubsequences: true)
            .map(String.init)
            .filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        guard let first = lines.first else { return ImportedTable(title: title, rows: []) }
        let candidates: [Character] = ["\t", ";", ","]
        let delimiter = candidates.max(by: { a, b in
            first.filter { $0 == a }.count < first.filter { $0 == b }.count
        }) ?? ","
        let parsed = lines.map { parseCSVLine($0, delimiter: delimiter) }
            .filter { row in row.contains(where: { !$0.isEmpty }) }
        let width = parsed.map(\.count).max() ?? 0
        let padded = parsed.map { row in
            row.count < width ? row + Array(repeating: "", count: width - row.count) : row
        }
        return ImportedTable(title: title, rows: padded)
    }

    private static func parseCSVLine(_ line: String, delimiter: Character) -> [String] {
        var cells: [String] = []
        var current = ""
        var inQuotes = false
        let chars = Array(line)
        var i = 0
        while i < chars.count {
            let c = chars[i]
            if c == "\"" && inQuotes && i + 1 < chars.count && chars[i + 1] == "\"" {
                current.append("\"")
                i += 1
            } else if c == "\"" {
                inQuotes.toggle()
            } else if c == delimiter && !inQuotes {
                cells.append(current.trimmingCharacters(in: .whitespaces))
                current = ""
            } else {
                current.append(c)
            }
            i += 1
        }
        cells.append(current.trimmingCharacters(in: .whitespaces))
        return cells
    }

    static func readXLSX(_ archiveData: Data) -> [ImportedTable] {
        let entries = MiniZipReader.extractXMLFiles(from: archiveData)
        let sharedStrings = entries["xl/sharedStrings.xml"].map(XlsxSharedStringsParser.parse) ?? []
        let sheetNames = entries["xl/workbook.xml"].map(XlsxWorkbookParser.parse) ?? []
        let sheetKeys = entries.keys
            .filter { $0.hasPrefix("xl/worksheets/sheet") && $0.hasSuffix(".xml") }
            .sorted { a, b in
                let na = Int(a.replacingOccurrences(of: "xl/worksheets/sheet", with: "").replacingOccurrences(of: ".xml", with: "")) ?? Int.max
                let nb = Int(b.replacingOccurrences(of: "xl/worksheets/sheet", with: "").replacingOccurrences(of: ".xml", with: "")) ?? Int.max
                return na < nb
            }
        return sheetKeys.enumerated().compactMap { idx, key in
            guard let xml = entries[key] else { return nil }
            let rows = XlsxWorksheetParser.parse(xml, sharedStrings: sharedStrings)
            guard !rows.isEmpty else { return nil }
            let name = idx < sheetNames.count ? sheetNames[idx] : "Hoja \(idx + 1)"
            return ImportedTable(title: name, rows: rows)
        }
    }

    static func readDOCX(_ archiveData: Data) -> ImportedDocxContent {
        let entries = MiniZipReader.extractXMLFiles(from: archiveData)
        guard let docXML = entries["word/document.xml"] else {
            return ImportedDocxContent(tables: [], paragraphs: [])
        }
        return DocxDocumentParser.parse(docXML)
    }

    static func excelColumnIndex(_ cellRef: String) -> Int? {
        let letters = cellRef.prefix { ($0 >= "A" && $0 <= "Z") || ($0 >= "a" && $0 <= "z") }.uppercased()
        guard !letters.isEmpty else { return nil }
        var acc = 0
        for scalar in letters.unicodeScalars {
            acc = acc * 26 + Int(scalar.value - 64)
        }
        return acc - 1
    }
}

// MARK: - Lector ZIP por Directorio Central (admite entradas almacenadas y deflate con o sin data descriptor)

private enum MiniZipReader {
    static func extractXMLFiles(from data: Data) -> [String: Data] {
        let bytes = [UInt8](data)
        guard bytes.count >= 22 else { return [:] }
        let minEOCD = max(0, bytes.count - 65_557)
        var eocdOffset: Int?
        var pos = bytes.count - 22
        while pos >= minEOCD {
            if bytes[pos] == 0x50 && bytes[pos + 1] == 0x4B && bytes[pos + 2] == 0x05 && bytes[pos + 3] == 0x06 {
                eocdOffset = pos
                break
            }
            pos -= 1
        }
        guard let eocd = eocdOffset, eocd + 20 <= bytes.count else { return [:] }
        let totalEntries = Int(readU16(bytes, at: eocd + 10))
        let cdOffset = Int(readU32(bytes, at: eocd + 16))
        guard cdOffset >= 0, cdOffset <= bytes.count else { return [:] }

        var result: [String: Data] = [:]
        var cursor = cdOffset
        var totalUncompressed = 0
        for _ in 0..<totalEntries {
            guard cursor + 46 <= bytes.count,
                  bytes[cursor] == 0x50, bytes[cursor + 1] == 0x4B,
                  bytes[cursor + 2] == 0x01, bytes[cursor + 3] == 0x02 else { break }
            let method = readU16(bytes, at: cursor + 10)
            let compSize = Int(readU32(bytes, at: cursor + 20))
            let uncompSize = Int(readU32(bytes, at: cursor + 24))
            let nameLen = Int(readU16(bytes, at: cursor + 28))
            let extraLen = Int(readU16(bytes, at: cursor + 30))
            let commentLen = Int(readU16(bytes, at: cursor + 32))
            let localOffset = Int(readU32(bytes, at: cursor + 42))
            let nextRecord = cursor + 46 + nameLen + extraLen + commentLen
            guard cursor + 46 + nameLen <= bytes.count else { break }
            let name = String(decoding: bytes[(cursor + 46)..<(cursor + 46 + nameLen)], as: UTF8.self)
            cursor = nextRecord

            guard (name.hasSuffix(".xml") || name.hasSuffix(".rels")),
                  uncompSize >= 0, uncompSize <= 16 * 1024 * 1024,
                  totalUncompressed + uncompSize <= 32 * 1024 * 1024,
                  localOffset + 30 <= bytes.count,
                  bytes[localOffset] == 0x50, bytes[localOffset + 1] == 0x4B,
                  bytes[localOffset + 2] == 0x03, bytes[localOffset + 3] == 0x04 else { continue }
            let localNameLen = Int(readU16(bytes, at: localOffset + 26))
            let localExtraLen = Int(readU16(bytes, at: localOffset + 28))
            let dataStart = localOffset + 30 + localNameLen + localExtraLen
            guard compSize >= 0, dataStart + compSize <= bytes.count else { continue }
            let slice = Data(bytes[dataStart..<(dataStart + compSize)])
            if method == 0 {
                result[name] = slice
                totalUncompressed += slice.count
            } else if method == 8, let inflated = inflateRawDeflate(slice, expectedSize: uncompSize) {
                result[name] = inflated
                totalUncompressed += inflated.count
            }
        }
        return result
    }

    private static func inflateRawDeflate(_ compressed: Data, expectedSize: Int) -> Data? {
        if compressed.isEmpty { return Data() }
        var stream = z_stream()
        guard inflateInit2_(&stream, -MAX_WBITS, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size)) == Z_OK else {
            return nil
        }
        defer { inflateEnd(&stream) }
        var output = Data(capacity: max(expectedSize, 1024))
        let chunkSize = 16_384
        var buffer = [UInt8](repeating: 0, count: chunkSize)
        return compressed.withUnsafeBytes { rawInput -> Data? in
            guard let base = rawInput.bindMemory(to: Bytef.self).baseAddress else { return nil }
            stream.next_in = UnsafeMutablePointer(mutating: base)
            stream.avail_in = uInt(compressed.count)
            while true {
                let status = buffer.withUnsafeMutableBytes { rawOut -> Int32 in
                    stream.next_out = rawOut.bindMemory(to: Bytef.self).baseAddress
                    stream.avail_out = uInt(chunkSize)
                    return inflate(&stream, Z_NO_FLUSH)
                }
                let produced = chunkSize - Int(stream.avail_out)
                if produced > 0 { output.append(buffer, count: produced) }
                if output.count > 16 * 1024 * 1024 { return nil }
                if status == Z_STREAM_END { break }
                if status != Z_OK { return nil }
                if stream.avail_in == 0 && produced == 0 { break }
            }
            return output
        }
    }

    private static func readU16(_ bytes: [UInt8], at i: Int) -> UInt16 {
        UInt16(bytes[i]) | (UInt16(bytes[i + 1]) << 8)
    }

    private static func readU32(_ bytes: [UInt8], at i: Int) -> UInt32 {
        UInt32(bytes[i]) | (UInt32(bytes[i + 1]) << 8) | (UInt32(bytes[i + 2]) << 16) | (UInt32(bytes[i + 3]) << 24)
    }
}

// MARK: - Parsers OOXML (Excel y Word)

private final class XlsxSharedStringsParser: NSObject, XMLParserDelegate {
    private var strings: [String] = []
    private var insideSI = false
    private var insideT = false
    private var current = ""

    static func parse(_ data: Data) -> [String] {
        let helper = XlsxSharedStringsParser()
        let parser = XMLParser(data: data)
        parser.delegate = helper
        _ = parser.parse()
        return helper.strings
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?,
                qualifiedName qName: String?, attributes attributeDict: [String: String] = [:]) {
        let local = elementName.split(separator: ":").last.map(String.init) ?? elementName
        if local == "si" { insideSI = true; current = "" }
        else if insideSI && local == "t" { insideT = true }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if insideSI && insideT { current.append(string) }
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        let local = elementName.split(separator: ":").last.map(String.init) ?? elementName
        if local == "t" { insideT = false }
        else if local == "si" { insideSI = false; strings.append(current) }
    }
}

private final class XlsxWorkbookParser: NSObject, XMLParserDelegate {
    private var names: [String] = []

    static func parse(_ data: Data) -> [String] {
        let helper = XlsxWorkbookParser()
        let parser = XMLParser(data: data)
        parser.delegate = helper
        _ = parser.parse()
        return helper.names
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?,
                qualifiedName qName: String?, attributes attributeDict: [String: String] = [:]) {
        let local = elementName.split(separator: ":").last.map(String.init) ?? elementName
        if local == "sheet", let name = attributeDict["name"], !name.isEmpty {
            names.append(name)
        }
    }
}

private final class XlsxWorksheetParser: NSObject, XMLParserDelegate {
    private let shared: [String]
    private var rowsMap: [[Int: String]] = []
    private var currentRow: [Int: String] = [:]
    private var maxCol = -1
    private var nextCol = 0
    private var cellCol = 0
    private var cellType = ""
    private var insideValue = false
    private var cellBuffer = ""

    init(shared: [String]) { self.shared = shared }

    static func parse(_ data: Data, sharedStrings: [String]) -> [[String]] {
        let helper = XlsxWorksheetParser(shared: sharedStrings)
        let parser = XMLParser(data: data)
        parser.delegate = helper
        _ = parser.parse()
        guard helper.maxCol >= 0 else { return [] }
        let width = min(helper.maxCol + 1, 30)
        return helper.rowsMap.map { row in
            (0..<width).map { col in row[col] ?? "" }
        }
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?,
                qualifiedName qName: String?, attributes attributeDict: [String: String] = [:]) {
        let local = elementName.split(separator: ":").last.map(String.init) ?? elementName
        switch local {
        case "row":
            currentRow = [:]
            nextCol = 0
        case "c":
            let ref = attributeDict["r"] ?? ""
            cellCol = LocalDocumentReader.excelColumnIndex(ref) ?? nextCol
            nextCol = cellCol + 1
            cellType = attributeDict["t"] ?? ""
            cellBuffer = ""
            insideValue = false
        case "v", "t":
            insideValue = true
        default:
            break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if insideValue { cellBuffer.append(string) }
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        let local = elementName.split(separator: ":").last.map(String.init) ?? elementName
        switch local {
        case "v", "t":
            insideValue = false
        case "c":
            let raw = cellBuffer.trimmingCharacters(in: .whitespacesAndNewlines)
            let resolved: String
            if cellType == "s", let idx = Int(raw), shared.indices.contains(idx) {
                resolved = shared[idx].trimmingCharacters(in: .whitespacesAndNewlines)
            } else {
                resolved = raw
            }
            if !resolved.isEmpty {
                currentRow[cellCol] = resolved
                if cellCol > maxCol { maxCol = cellCol }
            }
        case "row":
            if !currentRow.isEmpty { rowsMap.append(currentRow) }
        default:
            break
        }
    }
}

private final class DocxDocumentParser: NSObject, XMLParserDelegate {
    private var tables: [ImportedTable] = []
    private var paragraphs: [String] = []
    private var tableDepth = 0
    private var currentTableRows: [[String]] = []
    private var currentRowCells: [String] = []
    private var currentCellText = ""
    private var currentParagraphText = ""
    private var insideTextRun = false

    static func parse(_ data: Data) -> ImportedDocxContent {
        let helper = DocxDocumentParser()
        let parser = XMLParser(data: data)
        parser.delegate = helper
        _ = parser.parse()
        return ImportedDocxContent(tables: helper.tables, paragraphs: helper.paragraphs)
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?,
                qualifiedName qName: String?, attributes attributeDict: [String: String] = [:]) {
        let local = elementName.split(separator: ":").last.map(String.init) ?? elementName
        switch local {
        case "tbl":
            tableDepth += 1
            if tableDepth == 1 { currentTableRows = [] }
        case "tr" where tableDepth == 1:
            currentRowCells = []
        case "tc" where tableDepth == 1:
            currentCellText = ""
        case "p" where tableDepth == 0:
            currentParagraphText = ""
        case "t":
            insideTextRun = true
        case "tab":
            if tableDepth == 1 { currentCellText.append("\t") } else { currentParagraphText.append("\t") }
        case "br", "cr":
            if tableDepth == 1 { currentCellText.append(" ") } else { currentParagraphText.append(" ") }
        default:
            break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        guard insideTextRun else { return }
        if tableDepth >= 1 { currentCellText.append(string) }
        else { currentParagraphText.append(string) }
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        let local = elementName.split(separator: ":").last.map(String.init) ?? elementName
        switch local {
        case "t":
            insideTextRun = false
        case "tc" where tableDepth == 1:
            currentRowCells.append(currentCellText.trimmingCharacters(in: .whitespacesAndNewlines))
        case "tr" where tableDepth == 1:
            if currentRowCells.contains(where: { !$0.isEmpty }) {
                currentTableRows.append(currentRowCells)
            }
        case "tbl":
            if tableDepth == 1 && !currentTableRows.isEmpty {
                let width = currentTableRows.map(\.count).max() ?? 0
                let padded = currentTableRows.map { r in
                    r.count < width ? r + Array(repeating: "", count: width - r.count) : r
                }
                tables.append(ImportedTable(title: "Tabla \(tables.count + 1)", rows: padded))
            }
            tableDepth = max(0, tableDepth - 1)
        case "p" where tableDepth == 0:
            let trimmed = currentParagraphText.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty { paragraphs.append(trimmed) }
        default:
            break
        }
    }
}

// MARK: - Mapeo visual de regiones, columnas y coincidencias de catálogo

enum OcrMapper {

    static func normalized(_ text: String) -> String {
        text.trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "es"))
            .lowercased()
            .replacingOccurrences(of: #"[^a-z0-9]+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
    }

    static func extractMoney(_ raw: String) -> String {
        let stripped = raw
            .replacingOccurrences(of: "CUP", with: "", options: .caseInsensitive)
            .replacingOccurrences(of: "MN", with: "", options: .caseInsensitive)
            .replacingOccurrences(of: "$", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard let range = stripped.range(of: #"\d[\d.,\s]*"#, options: .regularExpression) else { return "" }
        let matched = stripped[range].replacingOccurrences(of: " ", with: "")
        let norm = normalizeDecimalSeparator(matched)
        let parts = norm.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
        let wholeDigits = parts[0].filter(\.isNumber)
        let trimmedWhole = String(wholeDigits.drop(while: { $0 == "0" }))
        let whole = trimmedWhole.isEmpty ? (wholeDigits.isEmpty ? "" : "0") : trimmedWhole
        guard !whole.isEmpty else { return "" }
        let dec = parts.count > 1 ? String(parts[1].filter(\.isNumber).prefix(2)) : ""
        return dec.isEmpty ? whole : "\(whole).\(dec)"
    }

    static func extractQuantity(_ raw: String, allowDecimals: Bool = true) -> String {
        guard let range = raw.range(of: #"\d[\d.,]*"#, options: .regularExpression) else { return "" }
        let matched = String(raw[range])
        let norm = normalizeDecimalSeparator(matched)
        let parts = norm.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
        let wholeDigits = parts[0].filter(\.isNumber)
        let trimmedWhole = String(wholeDigits.drop(while: { $0 == "0" }))
        let whole = String((trimmedWhole.isEmpty ? (wholeDigits.isEmpty ? "" : "0") : trimmedWhole).prefix(7))
        guard !whole.isEmpty else { return "" }
        guard allowDecimals else { return whole }
        let dec = parts.count > 1 ? String(parts[1].filter(\.isNumber).prefix(3)) : ""
        return dec.isEmpty ? whole : "\(whole).\(dec)"
    }

    static func extractUnit(_ raw: String) -> String {
        let p = normalized(raw)
        if ["kg", "kilo", "kilos", "kilogramo", "kilogramos"].contains(p) || p.hasSuffix(" kg") { return "kg" }
        if ["g", "gr", "gramo", "gramos"].contains(p) || p.hasSuffix(" g") { return "g" }
        if ["l", "lt", "litro", "litros"].contains(p) || p.hasSuffix(" l") { return "L" }
        if ["ml", "mililitro", "mililitros"].contains(p) || p.hasSuffix(" ml") { return "ml" }
        if ["u", "ud", "uds", "unidad", "unidades"].contains(p) { return "u" }
        return ""
    }

    private static func normalizeDecimalSeparator(_ num: String) -> String {
        let lastComma = num.lastIndex(of: ",")
        let lastDot = num.lastIndex(of: ".")
        if let lc = lastComma, (lastDot == nil || lc > lastDot!) {
            let noDots = num.replacingOccurrences(of: ".", with: "")
            guard let idx = noDots.lastIndex(of: ",") else { return noDots }
            let decLen = noDots.distance(from: noDots.index(after: idx), to: noDots.endIndex)
            let commaCount = noDots.filter { $0 == "," }.count
            let prefixLen = noDots.distance(from: noDots.startIndex, to: idx)
            if (1...2).contains(decLen) || (decLen == 3 && commaCount == 1 && prefixLen <= 2) {
                let left = noDots[..<idx].replacingOccurrences(of: ",", with: "")
                let right = noDots[noDots.index(after: idx)...]
                return "\(left).\(right)"
            }
            return noDots.replacingOccurrences(of: ",", with: "")
        } else if let ld = lastDot, (lastComma == nil || ld > lastComma!) {
            let noCommas = num.replacingOccurrences(of: ",", with: "")
            guard let idx = noCommas.lastIndex(of: ".") else { return noCommas }
            let left = noCommas[..<idx].replacingOccurrences(of: ".", with: "")
            let right = noCommas[noCommas.index(after: idx)...]
            return "\(left).\(right)"
        }
        return num
    }

    static func sanitize(_ text: String, for field: OcrField) -> String {
        if field.isMoney { return extractMoney(text) }
        if field.isQuantity { return extractQuantity(text, allowDecimals: true) }
        switch field {
        case .unit:
            let u = extractUnit(text)
            return u.isEmpty ? String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(8)) : u
        case .phone:
            var digits = String(text.filter(\.isNumber))
            if digits.count > 8 && digits.hasPrefix("53") { digits = String(digits.dropFirst(2)) }
            return String(digits.prefix(15))
        case .ci:
            return String(text.filter { $0.isLetter || $0.isNumber }.uppercased().prefix(20))
        default:
            return String(
                text.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .prefix(80)
            )
        }
    }

    static func blocksInRect(_ blocks: [OcrTextBlock], x1: Double, y1: Double, x2: Double, y2: Double) -> [OcrTextBlock] {
        let minX = min(x1, x2), maxX = max(x1, x2)
        let minY = min(y1, y2), maxY = max(y1, y2)
        return blocks.filter { b in
            if (minX...maxX).contains(b.centerX) && (minY...maxY).contains(b.centerY) { return true }
            let ix = max(0, min(b.right, maxX) - max(b.left, minX))
            let iy = max(0, min(b.bottom, maxY) - max(b.top, minY))
            let area = max(1e-6, (b.right - b.left) * (b.bottom - b.top))
            return (ix * iy) / area >= 0.35
        }.sorted { a, b in
            let rowA = Int(a.top * 25.0)
            let rowB = Int(b.top * 25.0)
            return rowA != rowB ? rowA < rowB : a.left < b.left
        }
    }

    static func groupBlocksIntoTable(_ blocks: [OcrTextBlock]) -> ImportedTable {
        guard !blocks.isEmpty else { return ImportedTable(title: "Renglones detectados", rows: []) }
        let sorted = blocks.sorted { $0.centerY < $1.centerY }
        var lines: [[OcrTextBlock]] = []
        for b in sorted {
            let h = max(0.015, b.bottom - b.top)
            if let last = lines.last {
                let avgY = last.map(\.centerY).reduce(0, +) / Double(last.count)
                if abs(b.centerY - avgY) <= h * 0.75 {
                    lines[lines.count - 1].append(b)
                    continue
                }
            }
            lines.append([b])
        }
        let rows: [[String]] = lines.compactMap { rowBlocks in
            let lr = rowBlocks.sorted { $0.left < $1.left }
            var cols: [String] = []
            var prevRight: Double?
            for piece in lr {
                let gap = prevRight.map { piece.left - $0 } ?? 0
                if cols.isEmpty || gap > 0.045 {
                    cols.append(piece.text.trimmingCharacters(in: .whitespaces))
                } else {
                    cols[cols.count - 1] += " " + piece.text.trimmingCharacters(in: .whitespaces)
                }
                prevRight = piece.right
            }
            let cleaned = cols.filter { !$0.isEmpty }
            return cleaned.isEmpty ? nil : cleaned
        }
        let width = rows.map(\.count).max() ?? 0
        return ImportedTable(
            title: "Renglones detectados",
            rows: rows.map { $0.count < width ? $0 + Array(repeating: "", count: width - $0.count) : $0 }
        )
    }

    static func suggestColumns(headers: [String], target: OcrImportTarget) -> [Int: OcrField] {
        let allowed = Set(OcrField.fields(for: target))
        var result: [Int: OcrField] = [:]
        var used: Set<OcrField> = []
        for (idx, raw) in headers.enumerated() {
            let h = normalized(raw)
            let suggested: OcrField?
            if h.contains("nombre") || h.contains("producto") || h.contains("articulo")
                || h.contains("servicio") || h.contains("insumo") || h == "item" {
                suggested = .name
            } else if h.contains("costo") || h.contains("compra") {
                suggested = .costPrice
            } else if target == .services && (h.contains("importe") || h.contains("precio") || h.contains("tarifa") || h.contains("valor")) {
                suggested = .amount
            } else if h.contains("venta") || h.contains("precio") || h.contains("pvp") || h.contains("importe") {
                suggested = .salePrice
            } else if h.contains("cant") || h.contains("exist") || h.contains("stock") || (target == .sales && h.contains("unid")) {
                suggested = .quantity
            } else if h == "um" || h.contains("medida") || h == "unidad" {
                suggested = .unit
            } else if h.contains("categ") || h.contains("tipo") || h.contains("familia") {
                suggested = .category
            } else if h.contains("cliente") || h.contains("comprador") {
                suggested = .customer
            } else if h.contains("tel") || h.contains("movil") || h.contains("celular") {
                suggested = .phone
            } else if h == "ci" || h.contains("carne") || h.contains("identidad") {
                suggested = .ci
            } else if h.contains("desc") || h.contains("nota") || h.contains("detalle") {
                suggested = .description
            } else {
                suggested = nil
            }
            if let s = suggested, allowed.contains(s), !used.contains(s) {
                result[idx] = s
                used.insert(s)
            }
        }
        return result
    }

    static func looksLikeHeader(_ firstRow: [String], target: OcrImportTarget) -> Bool {
        !suggestColumns(headers: firstRow, target: target).isEmpty
    }

    static func matchSaleCandidate(_ query: String, in candidates: [OcrSaleCandidate]) -> OcrSaleCandidate? {
        let q = normalized(query)
        guard !q.isEmpty, !candidates.isEmpty else { return nil }
        if let exact = candidates.first(where: { normalized($0.name) == q }) { return exact }
        if let partial = candidates.first(where: {
            let n = normalized($0.name)
            return n.contains(q) || q.contains(n)
        }) { return partial }
        let qTokens = Set(q.split(separator: " ").map(String.init).filter { $0.count >= 2 })
        guard !qTokens.isEmpty else { return nil }
        return candidates
            .map { c -> (OcrSaleCandidate, Double) in
                let cTokens = Set(normalized(c.name).split(separator: " ").map(String.init).filter { $0.count >= 2 })
                guard !cTokens.isEmpty else { return (c, 0) }
                let common = qTokens.intersection(cTokens).count
                return (c, Double(common) / Double(max(qTokens.count, cTokens.count)))
            }
            .filter { $0.1 >= 0.5 }
            .max(by: { $0.1 < $1.1 })?
            .0
    }
}
