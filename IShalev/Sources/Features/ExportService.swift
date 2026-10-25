import Foundation
import SwiftUI
import UIKit

struct ExportReport {
    var title: String
    var subtitle: String
    var headers: [String]
    var rows: [[String]]
}

struct ExportCard {
    var title: String
    var subtitle: String
    var description: String
    var price: String
    var imageData: Data?
}

private enum ExportImageError: Error { case empty }

enum ExportFormat: Equatable {
    case xlsx
    case csv
    case pdf

    var fileExtension: String {
        switch self {
        case .xlsx: "xlsx"
        case .csv: "csv"
        case .pdf: "pdf"
        }
    }
}

@MainActor
enum ShalevExporter {
    static func write(_ report: ExportReport, format: ExportFormat) throws -> URL {
        let timestamp = ISO8601DateFormatter().string(from: Date()).replacingOccurrences(of: ":", with: "-")
        let base = report.title.lowercased().folding(options: .diacriticInsensitive, locale: .current)
            .split(whereSeparator: { !$0.isLetter && !$0.isNumber }).joined(separator: "-")
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("shalev-\(base)-\(timestamp).\(format.fileExtension)")
        let data: Data
        switch format {
        case .xlsx: data = xlsxData(report)
        case .csv: data = csvData(report)
        case .pdf: data = pdfData(report)
        }
        try data.write(to: url, options: .atomic)
        return url
    }

    static func writePriceImages(_ report: ExportReport) throws -> [URL] {
        let pageSize = 12
        let pages = max(1, (report.rows.count + pageSize - 1) / pageSize)
        return try (0..<pages).map { page in
            let start = page * pageSize
            let end = min(report.rows.count, start + pageSize)
            let rows = start < end ? Array(report.rows[start..<end]) : []
            let pageReport = ExportReport(title: report.title, subtitle: report.subtitle, headers: report.headers, rows: rows)
            let data = tablePNG(pageReport, page: page + 1, pages: pages)
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("shalev-precios-\(UUID().uuidString)-\(page + 1).png")
            try data.write(to: url, options: .atomic)
            return url
        }
    }

    static func writeCards(_ cards: [ExportCard], businessName: String, comment: String) throws -> [URL] {
        guard !cards.isEmpty else { throw ExportImageError.empty }
        return try cards.enumerated().map { index, card in
            let data = cardPNG(card, businessName: businessName, comment: comment)
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("shalev-tarjeta-\(UUID().uuidString)-\(index + 1).png")
            try data.write(to: url, options: .atomic)
            return url
        }
    }

    private static func tablePNG(_ report: ExportReport, page: Int, pages: Int) -> Data {
        let width: CGFloat = 1_080
        let height = max(720, 320 + CGFloat(report.rows.count) * 76)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format)
        return renderer.pngData { _ in
            UIColor.systemBackground.setFill()
            UIRectFill(CGRect(x: 0, y: 0, width: width, height: height))
            drawText(report.title, in: CGRect(x: 48, y: 38, width: 984, height: 58), font: .boldSystemFont(ofSize: 42))
            drawText(report.subtitle, in: CGRect(x: 48, y: 104, width: 984, height: 70), font: .systemFont(ofSize: 23), color: .secondaryLabel)
            let headers = Array(report.headers.prefix(3))
            let widths: [CGFloat] = [500, 220, 264]
            var x: CGFloat = 48
            for (index, header) in headers.enumerated() {
                drawText(header, in: CGRect(x: x, y: 202, width: widths[index] - 12, height: 38),
                         font: .boldSystemFont(ofSize: 24), color: .secondaryLabel,
                         alignment: index == 2 ? .right : .left)
                x += widths[index]
            }
            UIColor.separator.setStroke()
            let rule = UIBezierPath(); rule.move(to: CGPoint(x: 48, y: 250)); rule.addLine(to: CGPoint(x: 1_032, y: 250)); rule.stroke()
            for (rowIndex, row) in report.rows.enumerated() {
                let y = 270 + CGFloat(rowIndex) * 76
                x = 48
                for column in 0..<min(3, row.count) {
                    drawText(row[column], in: CGRect(x: x, y: y, width: widths[column] - 12, height: 48),
                             font: .systemFont(ofSize: 25, weight: column == 2 ? .semibold : .regular),
                             alignment: column == 2 ? .right : .left)
                    x += widths[column]
                }
            }
            drawText("Shalev · \(page)/\(pages)", in: CGRect(x: 48, y: height - 48, width: 984, height: 28),
                     font: .systemFont(ofSize: 19), color: .secondaryLabel, alignment: .right)
        }
    }

    private static func cardPNG(_ card: ExportCard, businessName: String, comment: String) -> Data {
        let size = CGSize(width: 1_080, height: 1_350)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        return renderer.pngData { context in
            UIColor.systemBackground.setFill(); UIRectFill(CGRect(origin: .zero, size: size))
            let photoRect = CGRect(x: 48, y: 48, width: 984, height: 650)
            UIColor.secondarySystemBackground.setFill()
            UIBezierPath(roundedRect: photoRect, cornerRadius: 34).fill()
            if let data = card.imageData, let image = UIImage(data: data), image.size.width > 0, image.size.height > 0 {
                context.cgContext.saveGState()
                UIBezierPath(roundedRect: photoRect, cornerRadius: 34).addClip()
                let scale = max(photoRect.width / image.size.width, photoRect.height / image.size.height)
                let drawSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
                let origin = CGPoint(x: photoRect.midX - drawSize.width / 2, y: photoRect.midY - drawSize.height / 2)
                image.draw(in: CGRect(origin: origin, size: drawSize))
                context.cgContext.restoreGState()
            }
            drawText(card.subtitle.uppercased(), in: CGRect(x: 56, y: 742, width: 968, height: 38),
                     font: .boldSystemFont(ofSize: 23), color: .secondaryLabel)
            drawText(card.title, in: CGRect(x: 56, y: 792, width: 968, height: 118), font: .boldSystemFont(ofSize: 48))
            drawText(card.price, in: CGRect(x: 56, y: 922, width: 968, height: 80), font: .boldSystemFont(ofSize: 56))
            drawText(card.description, in: CGRect(x: 56, y: 1020, width: 968, height: 116),
                     font: .systemFont(ofSize: 27), color: .secondaryLabel)
            let note = comment.trimmingCharacters(in: .whitespacesAndNewlines)
            if !note.isEmpty {
                drawText(note, in: CGRect(x: 56, y: 1148, width: 968, height: 72), font: .systemFont(ofSize: 24))
            }
            drawText(businessName, in: CGRect(x: 56, y: 1_266, width: 968, height: 42),
                     font: .systemFont(ofSize: 25, weight: .semibold), color: .secondaryLabel, alignment: .center)
        }
    }

    private static func drawText(_ text: String, in rect: CGRect, font: UIFont, color: UIColor = .label,
                                 alignment: NSTextAlignment = .left) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = alignment
        paragraph.lineBreakMode = .byTruncatingTail
        (text as NSString).draw(with: rect, options: [.usesLineFragmentOrigin],
                                attributes: [.font: font, .foregroundColor: color, .paragraphStyle: paragraph], context: nil)
    }

    private static func xlsxData(_ report: ExportReport) -> Data {
        let rows = [[report.title], [report.subtitle], report.headers] + report.rows
        let sheetRows = rows.enumerated().map { index, row in
            let cells = row.enumerated().map { column, value in
                let reference = "\(columnName(column))\(index + 1)"
                let style = index == 0 || index == 2 ? " s=\"1\"" : ""
                return "<c r=\"\(reference)\" t=\"inlineStr\"\(style)><is><t xml:space=\"preserve\">\(xml(value))</t></is></c>"
            }.joined()
            return "<row r=\"\(index + 1)\">\(cells)</row>"
        }.joined()
        let lastColumn = columnName(max(0, report.headers.count - 1))
        let worksheet = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><dimension ref="A1:\(lastColumn)\(rows.count)"/><sheetViews><sheetView workbookViewId="0"><pane ySplit="3" topLeftCell="A4" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><sheetFormatPr defaultRowHeight="15"/><cols><col min="1" max="\(max(1, report.headers.count))" width="22" customWidth="1"/></cols><sheetData>\(sheetRows)</sheetData><autoFilter ref="A3:\(lastColumn)\(rows.count)"/></worksheet>
        """
        let workbook = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="\(xml(String(report.title.prefix(31))))" sheetId="1" r:id="rId1"/></sheets></workbook>
        """
        let entries = [
            ZipEntry(name: "[Content_Types].xml", data: Data("""
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>
            """.utf8)),
            ZipEntry(name: "_rels/.rels", data: Data("""
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>
            """.utf8)),
            ZipEntry(name: "xl/workbook.xml", data: Data(workbook.utf8)),
            ZipEntry(name: "xl/_rels/workbook.xml.rels", data: Data("""
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>
            """.utf8)),
            ZipEntry(name: "xl/styles.xml", data: Data("""
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>
            """.utf8)),
            ZipEntry(name: "xl/worksheets/sheet1.xml", data: Data(worksheet.utf8))
        ]
        return ZipArchive.make(entries: entries)
    }

    private static func columnName(_ index: Int) -> String {
        var value = index + 1
        var result = ""
        while value > 0 {
            value -= 1
            result = String(UnicodeScalar(65 + (value % 26))!) + result
            value /= 26
        }
        return result
    }

    private static func xml(_ value: String) -> String {
        value.replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
            .replacingOccurrences(of: "'", with: "&apos;")
    }

    private static func csvData(_ report: ExportReport) -> Data {
        var lines = [[report.title], [report.subtitle], report.headers]
        lines.append(contentsOf: report.rows)
        let text = lines.map { $0.map(csvCell).joined(separator: ";") }.joined(separator: "\r\n")
        return Data(([0xEF, 0xBB, 0xBF] + Array(text.utf8)))
    }

    private static func csvCell(_ value: String) -> String {
        let normalized = value.trimmingCharacters(in: .newlines)
        let safe = normalized.first.map { "=+-@".contains($0) ? "'" + normalized : normalized } ?? normalized
        return "\"" + safe.replacingOccurrences(of: "\"", with: "\"\"") + "\""
    }

    private static func pdfData(_ report: ExportReport) -> Data {
        let page = CGRect(x: 0, y: 0, width: 595, height: 842)
        let renderer = UIGraphicsPDFRenderer(bounds: page)
        return renderer.pdfData { context in
            let titleAttributes: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 20), .foregroundColor: UIColor.label]
            let subtitleAttributes: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 10), .foregroundColor: UIColor.secondaryLabel]
            let headerAttributes: [NSAttributedString.Key: Any] = [.font: UIFont.boldSystemFont(ofSize: 10), .foregroundColor: UIColor.label]
            let rowAttributes: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 9), .foregroundColor: UIColor.label]
            var y: CGFloat = 42

            func startPage() {
                context.beginPage()
                y = 42
                (report.title as NSString).draw(at: CGPoint(x: 38, y: y), withAttributes: titleAttributes)
                y += 30
                (report.subtitle as NSString).draw(in: CGRect(x: 38, y: y, width: 520, height: 26), withAttributes: subtitleAttributes)
                y += 36
                let headings = report.headers.joined(separator: "   |   ")
                (headings as NSString).draw(in: CGRect(x: 38, y: y, width: 520, height: 20), withAttributes: headerAttributes)
                y += 24
                UIColor.separator.setStroke()
                let rule = UIBezierPath(); rule.move(to: CGPoint(x: 38, y: y)); rule.addLine(to: CGPoint(x: 558, y: y)); rule.stroke()
                y += 8
            }

            startPage()
            for row in report.rows {
                if y > 780 { startPage() }
                let text = row.joined(separator: "   |   ")
                (text as NSString).draw(in: CGRect(x: 38, y: y, width: 520, height: 48), withAttributes: rowAttributes)
                y += 38
            }
            let footer = "Generado por Shalev para iOS · \(Date().shalevDateTime)"
            (footer as NSString).draw(at: CGPoint(x: 38, y: 807), withAttributes: subtitleAttributes)
        }
    }
}

private struct ZipEntry {
    let name: String
    let data: Data
}

/// ZIP mínimo sin compresión para el contenedor Open XML de Excel. No requiere dependencias externas.
private enum ZipArchive {
    static func make(entries: [ZipEntry]) -> Data {
        var output = Data()
        var central = Data()
        for entry in entries {
            let name = Data(entry.name.utf8)
            let checksum = crc32(entry.data)
            let offset = UInt32(output.count)
            append(UInt32(0x04034B50), to: &output)
            append(UInt16(20), to: &output)
            append(UInt16(0x0800), to: &output)
            append(UInt16(0), to: &output)
            append(UInt16(0), to: &output)
            append(UInt16(0), to: &output)
            append(checksum, to: &output)
            append(UInt32(entry.data.count), to: &output)
            append(UInt32(entry.data.count), to: &output)
            append(UInt16(name.count), to: &output)
            append(UInt16(0), to: &output)
            output.append(name)
            output.append(entry.data)

            append(UInt32(0x02014B50), to: &central)
            append(UInt16(20), to: &central)
            append(UInt16(20), to: &central)
            append(UInt16(0x0800), to: &central)
            append(UInt16(0), to: &central)
            append(UInt16(0), to: &central)
            append(UInt16(0), to: &central)
            append(checksum, to: &central)
            append(UInt32(entry.data.count), to: &central)
            append(UInt32(entry.data.count), to: &central)
            append(UInt16(name.count), to: &central)
            append(UInt16(0), to: &central)
            append(UInt16(0), to: &central)
            append(UInt16(0), to: &central)
            append(UInt16(0), to: &central)
            append(UInt32(0), to: &central)
            append(offset, to: &central)
            central.append(name)
        }
        let centralOffset = UInt32(output.count)
        output.append(central)
        append(UInt32(0x06054B50), to: &output)
        append(UInt16(0), to: &output)
        append(UInt16(0), to: &output)
        append(UInt16(entries.count), to: &output)
        append(UInt16(entries.count), to: &output)
        append(UInt32(central.count), to: &output)
        append(centralOffset, to: &output)
        append(UInt16(0), to: &output)
        return output
    }

    private static func crc32(_ data: Data) -> UInt32 {
        var crc = UInt32(0xFFFFFFFF)
        for byte in data {
            crc ^= UInt32(byte)
            for _ in 0..<8 { crc = (crc & 1) == 1 ? (crc >> 1) ^ 0xEDB88320 : crc >> 1 }
        }
        return crc ^ 0xFFFFFFFF
    }

    private static func append(_ value: UInt16, to data: inout Data) {
        data.append(UInt8(value & 0xFF))
        data.append(UInt8((value >> 8) & 0xFF))
    }

    private static func append(_ value: UInt32, to data: inout Data) {
        for shift in stride(from: 0, through: 24, by: 8) { data.append(UInt8((value >> UInt32(shift)) & 0xFF)) }
    }
}

private enum ExportScope: String, CaseIterable, Identifiable, Equatable {
    case inventory = "Inventario"
    case services = "Servicios"
    case sales = "Ventas"
    var id: String { rawValue }
}

@MainActor
struct ExportCenterView: View {
    @ObservedObject var store: ShalevStore
    var canExportInventory = true
    var canExportServices = true
    var canExportSales = true
    @State private var scope: ExportScope = .inventory
    @State private var shareFileURLs: [URL] = []
    @State private var showShareSheet = false
    @State private var exportComment = ""
    @State private var errorMessage: String?

    private var availableScopes: [ExportScope] {
        ExportScope.allCases.filter { scope in
            switch scope {
            case .inventory: canExportInventory
            case .services: canExportServices
            case .sales: canExportSales || canExportServices
            }
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 15) {
                ShalevScreenTitle(title: "Exportar", subtitle: "Libro de Excel, CSV o PDF")
                ShalevCard {
                    VStack(alignment: .leading, spacing: 12) {
                        ShalevSectionHeader(title: "Contenido")
                        Picker("Contenido", selection: $scope) {
                            ForEach(availableScopes) { Text($0.rawValue).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        Text("XLSX es un libro de Excel con filas y columnas; CSV es una alternativa de texto. Los datos no se envían a ningún servidor; eliges dónde compartir el archivo.")
                            .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                    }
                }
                ShalevCard {
                    VStack(spacing: 10) {
                        Button { export(.xlsx) } label: { Label("Exportar Excel (XLSX)", systemImage: "tablecells") }
                            .buttonStyle(ShalevPrimaryButtonStyle())
                        Button { export(.csv) } label: { Label("Exportar CSV", systemImage: "text.alignleft") }
                            .buttonStyle(ShalevTonalButtonStyle())
                        Button { export(.pdf) } label: { Label("Exportar PDF", systemImage: "doc.richtext") }
                            .buttonStyle(ShalevTonalButtonStyle())
                    }
                }
                if scope != .sales {
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 11) {
                            ShalevSectionHeader(title: "Imágenes para compartir")
                            TextField("Comentario opcional en las imágenes", text: $exportComment)
                                .padding(11).background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
                            Button(action: exportPriceImages) { Label("Lista de precios en PNG", systemImage: "photo") }
                                .buttonStyle(ShalevTonalButtonStyle())
                            Button(action: exportCards) { Label("Tarjetas de artículos en PNG", systemImage: "rectangle.portrait.on.rectangle.portrait") }
                                .buttonStyle(ShalevTonalButtonStyle())
                            Text("Las imágenes no incluyen costos, ganancias ni existencias.")
                                .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                        }
                    }
                }
                .padding(.bottom, 22)
            }
            .padding(18)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .background(Color.shalevBackground)
        .onAppear { if !availableScopes.contains(scope) { scope = availableScopes.first ?? .sales } }
        .onChange(of: availableScopes) { _, scopes in
            if !scopes.contains(scope) { scope = scopes.first ?? .sales }
        }
        .sheet(isPresented: $showShareSheet) {
            ShareSheet(items: shareFileURLs)
        }
        .alert("No se pudo exportar", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Entendido", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

    private var report: ExportReport {
        let subtitle = "\(store.data.settings.profile.businessName) · \(Date().shalevDate)"
        switch scope {
        case .inventory:
            return ExportReport(title: "Inventario", subtitle: subtitle,
                                headers: ["Artículo", "Tipo", "Categoría", "Existencias", "Unidad", "Costo CUP", "Precio CUP", "Caducidad"],
                                rows: store.activeProducts.map { product in
                                    [product.name, product.kind.rawValue, product.category,
                                     product.kind == .prepared ? "Elaborado" : FixedNumber.formatMilli(product.stockMilli), product.unit,
                                     Cup(cents: store.estimatedCostCents(for: product)).inputValue,
                                     Cup(cents: product.priceCents).inputValue, product.expiresAt?.shalevDate ?? ""]
                                })
        case .services:
            return ExportReport(title: "Servicios", subtitle: subtitle,
                                headers: ["Servicio", "Tipo", "Descripción", "Precio CUP", "Insumos"],
                                rows: store.activeServices.map { service in
                                    [service.name, service.type, service.description,
                                     Cup(cents: service.priceCents).inputValue,
                                     service.uses.map { use in
                                         let name = store.data.products.first(where: { $0.id == use.ingredientID })?.name ?? "Insumo"
                                         return "\(name): \(FixedNumber.formatMilli(use.milliUnits))"
                                     }.joined(separator: ", ")]
                                })
        case .sales:
            return ExportReport(title: "Ventas", subtitle: subtitle,
                                headers: ["Fecha", "Artículos", "Pago", "Total CUP", "Costo CUP", "Ganancia CUP", "Transacción", "Cliente", "Carné", "Teléfono", "Tarjeta de cobro", "Teléfono de cobro", "Vendedor", "Estado"],
                                rows: store.data.sales.filter { sale in
                                    sale.isService ? canExportServices : canExportSales
                                }.sorted { $0.date > $1.date }.map { sale in
                                    [sale.date.shalevDateTime, sale.lines.map { "\($0.quantity) × \($0.name)" }.joined(separator: ", "),
                                     sale.paymentMethod.rawValue, Cup(cents: sale.totalCents).inputValue,
                                     Cup(cents: sale.costCents).inputValue, Cup(cents: sale.profitCents).inputValue,
                                     sale.transactionNumber, sale.customer?.name ?? "", sale.customer?.identification ?? "",
                                     sale.customer?.phone ?? "", sale.paymentCard ?? "", sale.paymentPhone ?? "",
                                     sale.sellerName ?? "", sale.isCancelled ? "Anulada" : "Vigente"]
                                })
        }
    }

    private var priceReport: ExportReport {
        let business = store.data.settings.profile.businessName
        let comment = exportComment.trimmingCharacters(in: .whitespacesAndNewlines)
        let subtitle = "\(business) · \(Date().shalevDate)" + (comment.isEmpty ? "" : " · \(comment)")
        switch scope {
        case .inventory:
            return ExportReport(title: "Lista de precios", subtitle: subtitle, headers: ["Artículo", "Tipo", "Precio"],
                                rows: store.activeProducts.filter { $0.priceCents > 0 }.map {
                                    [$0.name, $0.kind.rawValue, Cup(cents: $0.priceCents).formatted]
                                })
        case .services:
            return ExportReport(title: "Precios de servicios", subtitle: subtitle, headers: ["Servicio", "Tipo", "Precio"],
                                rows: store.activeServices.map { [$0.name, $0.type, Cup(cents: $0.priceCents).formatted] })
        case .sales:
            return report
        }
    }

    private var exportCardModels: [ExportCard] {
        switch scope {
        case .inventory:
            return store.activeProducts.filter { $0.priceCents > 0 }.map {
                ExportCard(title: $0.name, subtitle: $0.kind.rawValue, description: $0.description,
                           price: Cup(cents: $0.priceCents).formatted, imageData: $0.imageData)
            }
        case .services:
            return store.activeServices.map {
                ExportCard(title: $0.name, subtitle: $0.type, description: $0.description,
                           price: Cup(cents: $0.priceCents).formatted, imageData: $0.imageData)
            }
        case .sales: return []
        }
    }

    private func exportPriceImages() {
        do {
            shareFileURLs = try ShalevExporter.writePriceImages(priceReport)
            showShareSheet = true
        } catch { errorMessage = "No se pudieron crear las imágenes." }
    }

    private func exportCards() {
        do {
            shareFileURLs = try ShalevExporter.writeCards(exportCardModels,
                                                           businessName: store.data.settings.profile.businessName,
                                                           comment: exportComment)
            showShareSheet = true
        } catch { errorMessage = "No se pudieron crear las tarjetas." }
    }

    private func export(_ format: ExportFormat) {
        do {
            shareFileURLs = [try ShalevExporter.write(report, format: format)]
            showShareSheet = true
        } catch {
            errorMessage = "Comprueba el espacio libre e inténtalo de nuevo."
        }
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    var items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) { }
}
