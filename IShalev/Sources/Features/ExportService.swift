import Foundation
import SwiftUI
import UIKit

struct ExportReport {
    var title: String
    var subtitle: String
    var headers: [String]
    var rows: [[String]]
}

enum ExportFormat: Equatable {
    case csv
    case pdf
}

@MainActor
enum ShalevExporter {
    static func write(_ report: ExportReport, format: ExportFormat) throws -> URL {
        let timestamp = ISO8601DateFormatter().string(from: Date()).replacingOccurrences(of: ":", with: "-")
        let base = report.title.lowercased().folding(options: .diacriticInsensitive, locale: .current)
            .split(whereSeparator: { !$0.isLetter && !$0.isNumber }).joined(separator: "-")
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("shalev-\(base)-\(timestamp).\(format == .csv ? "csv" : "pdf")")
        let data = format == .csv ? csvData(report) : pdfData(report)
        try data.write(to: url, options: .atomic)
        return url
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
    @State private var shareFileURL: URL?
    @State private var showShareSheet = false
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
                ShalevScreenTitle(title: "Exportar", subtitle: "PDF o archivo compatible con Excel")
                ShalevCard {
                    VStack(alignment: .leading, spacing: 12) {
                        ShalevSectionHeader(title: "Contenido")
                        Picker("Contenido", selection: $scope) {
                            ForEach(availableScopes) { Text($0.rawValue).tag($0) }
                        }
                        .pickerStyle(.segmented)
                        Text("El CSV se puede abrir con Microsoft Excel, Numbers o LibreOffice. Los datos no se envían a ningún servidor; elige tú dónde compartir el archivo.")
                            .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                    }
                }
                ShalevCard {
                    VStack(spacing: 10) {
                        Button { export(.csv) } label: { Label("Exportar para Excel (CSV)", systemImage: "tablecells") }
                            .buttonStyle(ShalevPrimaryButtonStyle())
                        Button { export(.pdf) } label: { Label("Exportar PDF", systemImage: "doc.richtext") }
                            .buttonStyle(ShalevTonalButtonStyle())
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
            if let shareFileURL { ShareSheet(items: [shareFileURL]) }
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
                                headers: ["Fecha", "Artículos", "Pago", "Total CUP", "Costo CUP", "Ganancia CUP", "Transacción", "Estado"],
                                rows: store.data.sales.filter { sale in
                                    sale.isService ? canExportServices : canExportSales
                                }.sorted { $0.date > $1.date }.map { sale in
                                    [sale.date.shalevDateTime, sale.lines.map { "\($0.quantity) × \($0.name)" }.joined(separator: ", "),
                                     sale.paymentMethod.rawValue, Cup(cents: sale.totalCents).inputValue,
                                     Cup(cents: sale.costCents).inputValue, Cup(cents: sale.profitCents).inputValue,
                                     sale.transactionNumber, sale.isCancelled ? "Anulada" : "Vigente"]
                                })
        }
    }

    private func export(_ format: ExportFormat) {
        do {
            shareFileURL = try ShalevExporter.write(report, format: format)
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
