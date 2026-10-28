import SwiftUI
import UIKit
import Vision
import PDFKit
import PhotosUI
import UniformTypeIdentifiers

struct OcrSalesImportResult: Equatable {
    var cartAdditions: [UUID: Int]
    var customerName: String?
    var customerPhone: String?
    var customerCI: String?
}

private enum ScannedPayload {
    case visual(title: String, image: UIImage, blocks: [OcrTextBlock], currentPage: Int, totalPages: Int, pdfData: Data?)
    case tabular(title: String, tables: [ImportedTable])
}

@MainActor
struct OcrImportSheet: View {
    let target: OcrImportTarget
    @ObservedObject var store: ShalevStore
    var salesCandidates: [OcrSaleCandidate] = []
    var onImportedMessage: (String) -> Void = { _ in }
    var onApplySales: (OcrSalesImportResult) -> Void = { _ in }

    @Environment(\.dismiss) private var dismiss

    @State private var processing = false
    @State private var errorMessage: String?
    @State private var payload: ScannedPayload?
    @State private var showAsTableFromOCR = false

    @State private var showCamera = false
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var showFileImporter = false
    @State private var fileFilterTypes: [UTType] = [.pdf]

    @State private var activeField: OcrField = .name
    @State private var blockAssignments: [Int: OcrField] = [:]
    @State private var currentDraft = OcrDraftRow()
    @State private var manualQueue: [OcrDraftRow] = []

    @State private var selectedTableIndex = 0
    @State private var skipHeaderRow = true
    @State private var columnMapping: [Int: OcrField] = [:]
    @State private var rowSelection: [Int: Bool] = [:]

    @State private var dragStart: CGPoint?
    @State private var dragEnd: CGPoint?

    private var fields: [OcrField] { OcrField.fields(for: target) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    sourceButtonsCard

                    if let errorMessage {
                        Text(errorMessage)
                            .font(.shalev(13, weight: .medium))
                            .foregroundStyle(Color.shalevError)
                            .padding(.horizontal, 4)
                    }

                    if processing {
                        HStack(spacing: 12) {
                            ProgressView()
                            Text("Reconociendo texto en el dispositivo…")
                                .font(.shalev(13))
                                .foregroundStyle(Color.shalevSecondaryText)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 20)
                    }

                    if let payload {
                        payloadContent(payload)
                    } else if !processing {
                        ShalevCard {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Reconocimiento local, gratuito y sin límite")
                                    .font(.shalev(15, weight: .semibold))
                                    .foregroundStyle(Color.shalevText)
                                Text("Toma una foto a tus apuntes manuscritos o impresos, o abre un PDF, Excel (.xlsx/.csv) o Word (.docx). Toca cualquier recuadro detectado o arrastra un rectángulo sobre el documento para asignarlo al campo elegido.")
                                    .font(.shalev(13))
                                    .foregroundStyle(Color.shalevSecondaryText)
                            }
                        }
                    }
                }
                .padding(16)
            }
            .background(Color.shalevBackground)
            .navigationTitle(target.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cerrar") { dismiss() }
                        .foregroundStyle(Color.shalevPrimary)
                        .disabled(processing)
                }
            }
            .sheet(isPresented: $showCamera) {
                OcrCameraCaptureView { image in
                    Task { await processImage(image, title: "Foto de cámara") }
                }
            }
            .onChange(of: selectedPhotoItem) { _, item in
                guard let item else { return }
                Task {
                    if let data = try? await item.loadTransferable(type: Data.self),
                       let img = UIImage(data: data) {
                        await processImage(img, title: "Imagen de la fototeca")
                    } else {
                        errorMessage = "No se pudo abrir la foto seleccionada."
                    }
                    selectedPhotoItem = nil
                }
            }
            .fileImporter(isPresented: $showFileImporter, allowedContentTypes: fileFilterTypes, allowsMultipleSelection: false) { result in
                switch result {
                case .success(let urls):
                    if let url = urls.first {
                        Task { await processFileURL(url) }
                    }
                case .failure:
                    errorMessage = "No se pudo abrir el archivo elegido."
                }
            }
            .onAppear {
                if let first = fields.first { activeField = first }
            }
        }
    }

    private var sourceButtonsCard: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("Elige el origen del documento:")
                    .font(.shalev(12, weight: .medium))
                    .foregroundStyle(Color.shalevSecondaryText)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        if UIImagePickerController.isSourceTypeAvailable(.camera) {
                            sourceButton(symbol: "camera.fill", label: "Cámara") {
                                showCamera = true
                            }
                        }
                        PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                            sourcePillLabel(symbol: "photo.fill", label: "Fotos")
                        }
                        .buttonStyle(.plain)
                        .disabled(processing)

                        sourceButton(symbol: "doc.richtext.fill", label: "PDF") {
                            fileFilterTypes = [.pdf]
                            showFileImporter = true
                        }
                        sourceButton(symbol: "tablecells.fill", label: "Excel / CSV") {
                            var types: [UTType] = [.commaSeparatedText, .plainText, .spreadsheet]
                            if let xlsx = UTType(filenameExtension: "xlsx") { types.insert(xlsx, at: 0) }
                            fileFilterTypes = types
                            showFileImporter = true
                        }
                        sourceButton(symbol: "doc.text.fill", label: "Word (.docx)") {
                            var types: [UTType] = [.data]
                            if let docx = UTType(filenameExtension: "docx") { types = [docx] }
                            fileFilterTypes = types
                            showFileImporter = true
                        }
                    }
                }
            }
        }
    }

    private func sourceButton(symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            sourcePillLabel(symbol: symbol, label: label)
        }
        .buttonStyle(.plain)
        .disabled(processing)
    }

    private func sourcePillLabel(symbol: String, label: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: symbol).font(.shalev(13, weight: .semibold))
            Text(label).font(.shalev(12, weight: .semibold))
        }
        .foregroundStyle(Color.shalevPrimary)
        .padding(.horizontal, 12)
        .frame(minHeight: 38)
        .background(Color.shalevSurfaceVariant, in: Capsule())
    }

    @ViewBuilder
    private func payloadContent(_ current: ScannedPayload) -> some View {
        switch current {
        case let .visual(title, image, blocks, currentPage, totalPages, pdfData):
            let groupedTable = OcrMapper.groupBlocksIntoTable(blocks)
            HStack {
                Text(title)
                    .font(.shalev(14, weight: .semibold))
                    .foregroundStyle(Color.shalevText)
                    .lineLimit(1)
                Spacer()
                if !blocks.isEmpty {
                    Button(showAsTableFromOCR ? "Lienzo visual" : "Ver como tabla") {
                        toggleTableMode(with: [groupedTable])
                    }
                    .font(.shalev(12, weight: .semibold))
                    .foregroundStyle(Color.shalevPrimary)
                }
            }

            if totalPages > 1, let pdfData {
                HStack(spacing: 12) {
                    ShalevSpinnerButton(direction: .decrease, label: "Página anterior", enabled: currentPage > 1 && !processing) {
                        Task { await processPDFData(pdfData, title: title, pageOneBased: currentPage - 1) }
                    }
                    Text("Página \(currentPage) de \(totalPages)")
                        .font(.shalev(13, weight: .semibold))
                    ShalevSpinnerButton(direction: .increase, label: "Página siguiente", enabled: currentPage < totalPages && !processing) {
                        Task { await processPDFData(pdfData, title: title, pageOneBased: currentPage + 1) }
                    }
                }
                .frame(maxWidth: .infinity)
            }

            if showAsTableFromOCR && !groupedTable.rows.isEmpty {
                tableMapperSection(tables: [groupedTable])
            } else {
                visualCanvasSection(image: image, blocks: blocks)
            }

        case let .tabular(_, tables):
            tableMapperSection(tables: tables)
        }
    }

    private func visualCanvasSection(image: UIImage, blocks: [OcrTextBlock]) -> some View {
        let aspect = max(0.45, min(2.2, Double(max(1, image.size.width)) / Double(max(1, image.size.height))))
        return VStack(alignment: .leading, spacing: 12) {
            Text("1. Elige el campo destino y toca un recuadro o arrastra un rectángulo sobre el documento:")
                .font(.shalev(12))
                .foregroundStyle(Color.shalevSecondaryText)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(fields) { f in
                        let val = currentDraft[f]
                        let isSelected = activeField == f
                        Button { activeField = f } label: {
                            Text(val.isEmpty ? f.label : "\(f.shortLabel): \(String(val.prefix(14)))")
                                .font(.shalev(12, weight: .semibold))
                                .foregroundStyle(isSelected ? Color.shalevOnPrimary : Color.shalevText)
                                .padding(.horizontal, 12)
                                .frame(minHeight: 34)
                                .background(isSelected ? Color.shalevPrimary : Color.shalevSurfaceVariant, in: Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            GeometryReader { geo in
                let size = geo.size
                ZStack {
                    Image(uiImage: image)
                        .resizable()
                        .frame(width: size.width, height: size.height)

                    Canvas { ctx, canvasSize in
                        for b in blocks {
                            let rect = CGRect(
                                x: b.left * canvasSize.width,
                                y: b.top * canvasSize.height,
                                width: max(6, (b.right - b.left) * canvasSize.width),
                                height: max(6, (b.bottom - b.top) * canvasSize.height)
                            )
                            let assigned = blockAssignments[b.id] != nil
                            let strokeColor: Color = assigned ? .orange : .shalevPrimary
                            ctx.fill(Path(rect), with: .color(strokeColor.opacity(assigned ? 0.24 : 0.10)))
                            ctx.stroke(Path(rect), with: .color(strokeColor), lineWidth: assigned ? 2.2 : 1.1)
                        }
                        if let s = dragStart, let e = dragEnd {
                            let dragRect = CGRect(
                                x: min(s.x, e.x),
                                y: min(s.y, e.y),
                                width: abs(e.x - s.x),
                                height: abs(e.y - s.y)
                            )
                            ctx.fill(Path(dragRect), with: .color(Color.orange.opacity(0.22)))
                            ctx.stroke(Path(dragRect), with: .color(Color.orange), lineWidth: 2.2)
                        }
                    }
                }
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            dragStart = value.startLocation
                            dragEnd = value.location
                        }
                        .onEnded { value in
                            handleCanvasGesture(start: value.startLocation, end: value.location, in: size, blocks: blocks)
                        }
                )
            }
            .aspectRatio(aspect, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Color.shalevOutlineVariant, lineWidth: 1))

            Text(blocks.isEmpty
                 ? "No se detectó texto automático en esta imagen. Puedes escribir los valores manualmente abajo."
                 : "\(blocks.count) bloques detectados · Toca un bloque o arrastra un rectángulo para rellenar «\(activeField.shortLabel)».")
                .font(.shalev(11))
                .foregroundStyle(Color.shalevSecondaryText)

            ShalevCard {
                VStack(alignment: .leading, spacing: 10) {
                    Text("2. Revisa o ajusta los valores asignados")
                        .font(.shalev(14, weight: .semibold))
                    ForEach(fields) { f in
                        draftFieldEditor(f)
                    }
                    HStack(spacing: 10) {
                        Button("Añadir a la cola") {
                            enqueueCurrentDraft()
                        }
                        .buttonStyle(ShalevSecondaryButtonStyle())
                        .disabled(currentDraft[.name].trimmingCharacters(in: .whitespaces).isEmpty)

                        Button("Importar") {
                            submitVisualDrafts()
                        }
                        .buttonStyle(ShalevPrimaryButtonStyle())
                        .disabled(currentDraft[.name].trimmingCharacters(in: .whitespaces).isEmpty && manualQueue.isEmpty)
                    }
                }
            }

            if !manualQueue.isEmpty {
                ShalevCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("En cola (\(manualQueue.count))")
                            .font(.shalev(14, weight: .semibold))
                        ForEach(Array(manualQueue.enumerated()), id: \.element.id) { idx, item in
                            HStack {
                                Text(summaryText(for: item))
                                    .font(.shalev(13))
                                    .lineLimit(1)
                                Spacer()
                                Button {
                                    manualQueue.remove(at: idx)
                                } label: {
                                    Image(systemName: "trash").foregroundStyle(Color.shalevError)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
        }
    }

    private func draftFieldEditor(_ field: OcrField) -> some View {
        let binding = Binding<String>(
            get: { currentDraft[field] },
            set: { currentDraft[field] = $0 }
        )
        let filter: ShalevInputFilter = field.isMoney ? .money : (field.isQuantity ? .quantity : (field == .phone ? .phone : (field == .ci ? .ci : .name())))
        let keyboard: UIKeyboardType = (field.isMoney || field.isQuantity) ? .decimalPad : (field == .phone ? .phonePad : .default)
        return VStack(alignment: .leading, spacing: 4) {
            Text(field.label)
                .font(.shalev(12, weight: .medium))
                .foregroundStyle(Color.shalevSecondaryText)
            TextField(field.shortLabel, text: binding)
                .keyboardType(keyboard)
                .shalevInputFilter(filter, text: binding)
                .textFieldStyle(ShalevTextFieldStyle())
            if target == .sales && field == .name && !currentDraft[.name].isEmpty {
                if let m = OcrMapper.matchSaleCandidate(currentDraft[.name], in: salesCandidates) {
                    Text("Coincide con: \(m.name) (máx. \(m.maxAvailable))")
                        .font(.shalev(11))
                        .foregroundStyle(Color.shalevPrimary)
                } else {
                    Text("Sin coincidencia en el catálogo vendible")
                        .font(.shalev(11))
                        .foregroundStyle(Color.shalevError)
                }
            }
        }
    }

    private func tableMapperSection(tables: [ImportedTable]) -> some View {
        let safeIdx = min(max(0, selectedTableIndex), max(0, tables.count - 1))
        let table = tables.isEmpty ? ImportedTable(title: "", rows: []) : tables[safeIdx]
        let dataRows = (skipHeaderRow && table.rows.count > 1) ? Array(table.rows.dropFirst()) : table.rows
        let colCount = table.rows.first?.count ?? 0
        let selectedCount = dataRows.indices.filter { rowSelection[$0] ?? true }.count

        return VStack(alignment: .leading, spacing: 12) {
            if tables.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(tables.indices, id: \.self) { idx in
                            Button(tables[idx].title) {
                                selectedTableIndex = idx
                                configureTableState(for: tables[idx])
                            }
                            .font(.shalev(12, weight: .semibold))
                            .foregroundStyle(idx == safeIdx ? Color.shalevOnPrimary : Color.shalevText)
                            .padding(.horizontal, 12)
                            .frame(minHeight: 32)
                            .background(idx == safeIdx ? Color.shalevPrimary : Color.shalevSurfaceVariant, in: Capsule())
                            .buttonStyle(.plain)
                        }
                    }
                }
            }

            ShalevCard {
                VStack(alignment: .leading, spacing: 10) {
                    Text("1. Asigna cada columna del documento a un campo de Shalev")
                        .font(.shalev(14, weight: .semibold))
                    Toggle("Omitir primera fila (encabezado)", isOn: $skipHeaderRow)
                        .font(.shalev(13))
                        .tint(Color.shalevPrimary)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(0..<colCount, id: \.self) { col in
                                let sample = table.rows.first.flatMap { col < $0.count ? String($0[col].prefix(16)) : nil } ?? ""
                                let assigned = columnMapping[col]
                                let letter = String(UnicodeScalar(65 + (col % 26))!)
                                Menu {
                                    Button("Ignorar columna") {
                                        columnMapping.removeValue(forKey: col)
                                    }
                                    ForEach(fields) { f in
                                        Button(f.label) {
                                            for (k, v) in columnMapping where v == f { columnMapping.removeValue(forKey: k) }
                                            columnMapping[col] = f
                                        }
                                    }
                                } label: {
                                    Text("Col \(letter)\(sample.isEmpty ? "" : " (\(sample))") → \(assigned?.shortLabel ?? "Ignorar")")
                                        .font(.shalev(12, weight: .semibold))
                                        .foregroundStyle(assigned != nil ? Color.shalevOnPrimary : Color.shalevText)
                                        .padding(.horizontal, 12)
                                        .frame(minHeight: 34)
                                        .background(assigned != nil ? Color.shalevPrimary : Color.shalevSurfaceVariant, in: Capsule())
                                }
                            }
                        }
                    }
                }
            }

            ShalevCard {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("2. Filas detectadas (\(selectedCount)/\(dataRows.count))")
                            .font(.shalev(14, weight: .semibold))
                        Spacer()
                    }
                    Button("Importar filas seleccionadas (\(selectedCount))") {
                        submitTableRows(dataRows)
                    }
                    .buttonStyle(ShalevPrimaryButtonStyle())
                    .disabled(selectedCount == 0)

                    Divider()

                    ForEach(Array(dataRows.prefix(150).enumerated()), id: \.offset) { idx, cells in
                        tableRowPreview(index: idx, cells: cells)
                    }
                }
            }
        }
    }

    private func tableRowPreview(index: Int, cells: [String]) -> some View {
        let isChecked = rowSelection[index] ?? true
        let nameCol = columnMapping.first(where: { $0.value == .name })?.key
        let primary = (nameCol.flatMap { $0 < cells.count ? cells[$0] : nil } ?? "").isEmpty
            ? (cells.first ?? "Fila \(index + 1)")
            : (nameCol.flatMap { $0 < cells.count ? cells[$0] : nil } ?? "")
        let details = cells.enumerated().compactMap { col, val -> String? in
            guard let f = columnMapping[col], f != .name, !val.isEmpty else { return nil }
            return "\(f.shortLabel): \(val)"
        }.joined(separator: " · ")

        var saleHint = ""
        if target == .sales && !primary.isEmpty {
            if let m = OcrMapper.matchSaleCandidate(primary, in: salesCandidates) {
                saleHint = m.maxAvailable <= 0 ? "Sin existencias" : "Máx. disp.: \(m.maxAvailable)"
            } else {
                saleHint = "Sin coincidencia en catálogo"
            }
        }

        return Button {
            rowSelection[index] = !isChecked
        } label: {
            HStack(spacing: 10) {
                Image(systemName: isChecked ? "checkmark.square.fill" : "square")
                    .foregroundStyle(isChecked ? Color.shalevPrimary : Color.shalevSecondaryText)
                VStack(alignment: .leading, spacing: 2) {
                    Text(primary.isEmpty ? "Fila \(index + 1)" : primary)
                        .font(.shalev(13, weight: .medium))
                        .foregroundStyle(Color.shalevText)
                        .lineLimit(1)
                    if !details.isEmpty || !saleHint.isEmpty {
                        Text([details, saleHint].filter { !$0.isEmpty }.joined(separator: " · "))
                            .font(.shalev(11))
                            .foregroundStyle(Color.shalevSecondaryText)
                            .lineLimit(1)
                    }
                }
                Spacer()
            }
            .padding(.vertical, 3)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Gestos y acciones

    private func toggleTableMode(with tables: [ImportedTable]) {
        showAsTableFromOCR.toggle()
        if showAsTableFromOCR, let first = tables.first {
            configureTableState(for: first)
        }
    }

    private func configureTableState(for table: ImportedTable) {
        let hasHeader = table.rows.count > 1 && OcrMapper.looksLikeHeader(table.rows[0], target: target)
        skipHeaderRow = hasHeader
        var map: [Int: OcrField] = [:]
        if let first = table.rows.first {
            let suggested = OcrMapper.suggestColumns(headers: first, target: target)
            if !suggested.isEmpty {
                map = suggested
            } else {
                for (col, f) in fields.prefix(first.count).enumerated() {
                    map[col] = f
                }
            }
        }
        columnMapping = map
        var sel: [Int: Bool] = [:]
        for i in 0..<table.rows.count { sel[i] = true }
        rowSelection = sel
    }

    private func handleCanvasGesture(start: CGPoint, end: CGPoint, in size: CGSize, blocks: [OcrTextBlock]) {
        defer {
            dragStart = nil
            dragEnd = nil
        }
        guard size.width > 0, size.height > 0 else { return }
        let dx = abs(end.x - start.x)
        let dy = abs(end.y - start.y)
        let picked: [OcrTextBlock]
        if dx < 8 && dy < 8 {
            let nx = min(1, max(0, Double(end.x / size.width)))
            let ny = min(1, max(0, Double(end.y / size.height)))
            if let hit = blocks.first(where: { (nx >= $0.left && nx <= $0.right) && (ny >= $0.top && ny <= $0.bottom) }) {
                picked = [hit]
            } else {
                picked = []
            }
        } else {
            picked = OcrMapper.blocksInRect(
                blocks,
                x1: min(1, max(0, Double(start.x / size.width))),
                y1: min(1, max(0, Double(start.y / size.height))),
                x2: min(1, max(0, Double(end.x / size.width))),
                y2: min(1, max(0, Double(end.y / size.height)))
            )
        }
        guard !picked.isEmpty else { return }
        for b in picked { blockAssignments[b.id] = activeField }
        let combined = picked.map(\.text).joined(separator: " ")
        currentDraft[activeField] = OcrMapper.sanitize(combined, for: activeField)
        if let idx = fields.firstIndex(of: activeField), idx + 1 < fields.count {
            activeField = fields[idx + 1]
        }
    }

    private func enqueueCurrentDraft() {
        manualQueue.append(currentDraft)
        currentDraft = OcrDraftRow()
        blockAssignments.removeAll()
        if let first = fields.first { activeField = first }
    }

    private func summaryText(for row: OcrDraftRow) -> String {
        var parts = [row[.name]]
        if !row[.quantity].isEmpty { parts.append("Cant. \(row[.quantity])") }
        let money = !row[.salePrice].isEmpty ? row[.salePrice] : row[.amount]
        if !money.isEmpty { parts.append("\(money) CUP") }
        return parts.joined(separator: " · ")
    }

    private func submitVisualDrafts() {
        var all = manualQueue
        if !currentDraft[.name].trimmingCharacters(in: .whitespaces).isEmpty {
            all.append(currentDraft)
        }
        executeImport(rows: all)
    }

    private func submitTableRows(_ rows: [[String]]) {
        let drafts: [OcrDraftRow] = rows.enumerated().map { idx, cells in
            var values: [OcrField: String] = [:]
            for (col, val) in cells.enumerated() {
                if let f = columnMapping[col] {
                    values[f] = OcrMapper.sanitize(val, for: f)
                }
            }
            return OcrDraftRow(values: values, isSelected: rowSelection[idx] ?? true)
        }
        executeImport(rows: drafts)
    }

    private func executeImport(rows: [OcrDraftRow]) {
        errorMessage = nil
        let selected = rows.filter(\.isSelected)
        guard !selected.isEmpty else {
            errorMessage = "Selecciona al menos una fila válida para importar."
            return
        }

        switch target {
        case .inventory:
            var ok = 0
            var failed = 0
            var firstErr: String?
            for r in selected {
                let name = r[.name].trimmingCharacters(in: .whitespacesAndNewlines)
                let catRaw = r[.category].trimmingCharacters(in: .whitespacesAndNewlines)
                let unitText = OcrMapper.extractUnit(r[.unit])
                let isIngredient = catRaw.caseInsensitiveCompare("Insumos") == .orderedSame
                    || (!unitText.isEmpty && unitText != "u")
                if isIngredient {
                    let costStr = OcrMapper.extractMoney(r[.costPrice]).isEmpty
                        ? OcrMapper.extractMoney(r[.salePrice])
                        : OcrMapper.extractMoney(r[.costPrice])
                    let saleStr = OcrMapper.extractMoney(r[.salePrice])
                    let qtyStr = OcrMapper.extractQuantity(r[.quantity], allowDecimals: true)
                    guard !name.isEmpty,
                          let cost = Cup.parse(costStr)?.cents,
                          let stockMilli = parseMilliUnits(qtyStr.isEmpty ? "0" : qtyStr) else {
                        failed += 1
                        if firstErr == nil { firstErr = "Revisa nombre, costo y cantidad en «\(name.isEmpty ? "fila sin nombre" : name)»." }
                        continue
                    }
                    let saleCents = saleStr.isEmpty ? 0 : (Cup.parse(saleStr)?.cents ?? 0)
                    let product = Product(
                        name: name,
                        category: "Insumos",
                        kind: .ingredient,
                        description: String(r[.description].prefix(40)),
                        stockMilli: max(0, stockMilli),
                        unit: unitText.isEmpty ? "u" : unitText,
                        priceCents: max(0, saleCents),
                        costCents: max(0, cost)
                    )
                    do {
                        try store.saveProduct(product)
                        ok += 1
                    } catch {
                        failed += 1
                        if firstErr == nil { firstErr = error.localizedDescription }
                    }
                } else {
                    let saleStr = OcrMapper.extractMoney(r[.salePrice])
                    let costStr = OcrMapper.extractMoney(r[.costPrice])
                    let qtyInt = Int64(OcrMapper.extractQuantity(r[.quantity], allowDecimals: false)) ?? 0
                    guard !name.isEmpty,
                          let saleCents = Cup.parse(saleStr)?.cents, saleCents > 0 else {
                        failed += 1
                        if firstErr == nil { firstErr = "Revisa nombre y precio de venta (> 0 CUP) en «\(name.isEmpty ? "fila sin nombre" : name)»." }
                        continue
                    }
                    let costCents = costStr.isEmpty ? 0 : (Cup.parse(costStr)?.cents ?? 0)
                    let product = Product(
                        name: name,
                        category: catRaw.isEmpty ? "General" : catRaw,
                        kind: .product,
                        description: String(r[.description].prefix(40)),
                        stockMilli: max(0, qtyInt * 1_000),
                        unit: "u",
                        priceCents: saleCents,
                        costCents: max(0, costCents)
                    )
                    do {
                        try store.saveProduct(product)
                        ok += 1
                    } catch {
                        failed += 1
                        if firstErr == nil { firstErr = error.localizedDescription }
                    }
                }
            }
            if ok > 0 {
                let msg = failed == 0
                    ? (ok == 1 ? "1 artículo importado al inventario." : "\(ok) artículos importados al inventario.")
                    : "\(ok) importados · \(failed) omitidos."
                onImportedMessage(msg)
                dismiss()
            } else {
                errorMessage = firstErr ?? "Ninguna fila cumplió las validaciones del inventario."
            }

        case .services:
            var ok = 0
            var failed = 0
            var firstErr: String?
            for r in selected {
                let name = r[.name].trimmingCharacters(in: .whitespacesAndNewlines)
                let type = r[.category].trimmingCharacters(in: .whitespacesAndNewlines)
                let amountStr = OcrMapper.extractMoney(r[.amount])
                guard !name.isEmpty, let cents = Cup.parse(amountStr)?.cents, cents > 0 else {
                    failed += 1
                    if firstErr == nil { firstErr = "Revisa nombre e importe (> 0 CUP) en «\(name.isEmpty ? "fila sin nombre" : name)»." }
                    continue
                }
                let service = ServiceItem(
                    name: name,
                    type: type.isEmpty ? "General" : type,
                    description: String(r[.description].prefix(40)),
                    priceCents: cents
                )
                do {
                    try store.saveService(service)
                    ok += 1
                } catch {
                    failed += 1
                    if firstErr == nil { firstErr = error.localizedDescription }
                }
            }
            if ok > 0 {
                let msg = failed == 0
                    ? (ok == 1 ? "1 servicio importado." : "\(ok) servicios importados.")
                    : "\(ok) servicios importados · \(failed) omitidos."
                onImportedMessage(msg)
                dismiss()
            } else {
                errorMessage = firstErr ?? "Ninguna fila cumplió las validaciones de servicios."
            }

        case .sales:
            var additions: [UUID: Int] = [:]
            var custName: String?
            var custPhone: String?
            var custCI: String?
            for r in selected {
                if custName == nil, !r[.customer].isEmpty { custName = r[.customer] }
                if custPhone == nil, !r[.phone].isEmpty { custPhone = r[.phone] }
                if custCI == nil, !r[.ci].isEmpty { custCI = r[.ci] }
                guard let match = OcrMapper.matchSaleCandidate(r[.name], in: salesCandidates),
                      match.maxAvailable > 0 else { continue }
                let qty = max(1, Int(OcrMapper.extractQuantity(r[.quantity], allowDecimals: false)) ?? 1)
                let sum = (additions[match.id] ?? 0) + qty
                additions[match.id] = min(sum, match.maxAvailable)
            }
            if additions.isEmpty {
                errorMessage = "Ninguna línea coincide con artículos o servicios con existencias disponibles."
            } else {
                onApplySales(OcrSalesImportResult(
                    cartAdditions: additions,
                    customerName: custName,
                    customerPhone: custPhone,
                    customerCI: custCI
                ))
                dismiss()
            }
        }
    }

    private func parseMilliUnits(_ raw: String) -> Int64? {
        guard let dec = Decimal(string: raw.replacingOccurrences(of: ",", with: ".")), dec >= 0 else { return nil }
        var scaled = dec * Decimal(1_000)
        var rounded = Decimal()
        NSDecimalRound(&rounded, &scaled, 0, .plain)
        return NSDecimalNumber(decimal: rounded).int64Value
    }

    // MARK: - Procesamiento local con Apple Vision y PDFKit

    private func processFileURL(_ url: URL) async {
        let accessed = url.startAccessingSecurityScopedResource()
        defer { if accessed { url.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: url) else {
            errorMessage = "No se pudo leer el archivo seleccionado."
            return
        }
        let ext = url.pathExtension.lowercased()
        let title = url.lastPathComponent
        if ext == "pdf" {
            await processPDFData(data, title: title, pageOneBased: 1)
        } else if ext == "xlsx" {
            let tables = LocalDocumentReader.readXLSX(data)
            if tables.isEmpty {
                errorMessage = "El archivo Excel no contiene celdas legibles."
            } else {
                selectedTableIndex = 0
                configureTableState(for: tables[0])
                payload = .tabular(title: title, tables: tables)
            }
        } else if ["csv", "tsv", "txt"].contains(ext) {
            let content = String(data: data, encoding: .utf8) ?? String(decoding: data, as: UTF8.self)
            let table = LocalDocumentReader.readCSV(content, title: title)
            if table.rows.isEmpty {
                errorMessage = "El archivo CSV está vacío."
            } else {
                selectedTableIndex = 0
                configureTableState(for: table)
                payload = .tabular(title: title, tables: [table])
            }
        } else if ext == "docx" {
            let doc = LocalDocumentReader.readDOCX(data)
            if !doc.tables.isEmpty {
                selectedTableIndex = 0
                configureTableState(for: doc.tables[0])
                payload = .tabular(title: title, tables: doc.tables)
            } else if !doc.paragraphs.isEmpty {
                let rendered = renderParagraphsCanvas(doc.paragraphs)
                payload = .visual(title: title, image: rendered.0, blocks: rendered.1, currentPage: 1, totalPages: 1, pdfData: nil)
            } else {
                errorMessage = "El documento Word no contiene tablas ni texto legible."
            }
        } else if let img = UIImage(data: data) {
            await processImage(img, title: title)
        } else {
            errorMessage = "Formato no admitido. Elige una imagen, PDF, Excel (.xlsx/.csv) o Word (.docx)."
        }
    }

    private func processImage(_ rawImage: UIImage, title: String) async {
        processing = true
        errorMessage = nil
        let normalized = normalizeImageOrientation(rawImage)
        let blocks = await Self.recognizeWithVision(normalized)
        blockAssignments.removeAll()
        showAsTableFromOCR = false
        payload = .visual(title: title, image: normalized, blocks: blocks, currentPage: 1, totalPages: 1, pdfData: nil)
        processing = false
    }

    private func processPDFData(_ data: Data, title: String, pageOneBased: Int) async {
        processing = true
        errorMessage = nil
        guard let doc = PDFDocument(data: data), doc.pageCount > 0 else {
            errorMessage = "No se pudo abrir el documento PDF."
            processing = false
            return
        }
        let total = doc.pageCount
        let idx = min(max(0, pageOneBased - 1), total - 1)
        guard let page = doc.page(at: idx) else {
            errorMessage = "No se pudo renderizar la página del PDF."
            processing = false
            return
        }
        let bounds = page.bounds(for: .mediaBox)
        let scale = min(3.0, max(1.5, 1600.0 / max(1.0, max(bounds.width, bounds.height))))
        let targetSize = CGSize(width: max(400, bounds.width * scale), height: max(400, bounds.height * scale))
        let renderer = UIGraphicsImageRenderer(size: targetSize)
        let renderedImage = renderer.image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(origin: .zero, size: targetSize))
            ctx.cgContext.saveGState()
            ctx.cgContext.translateBy(x: 0, y: targetSize.height)
            ctx.cgContext.scaleBy(x: targetSize.width / max(1, bounds.width), y: -targetSize.height / max(1, bounds.height))
            page.draw(with: .mediaBox, to: ctx.cgContext)
            ctx.cgContext.restoreGState()
        }
        let blocks = await Self.recognizeWithVision(renderedImage)
        blockAssignments.removeAll()
        showAsTableFromOCR = false
        payload = .visual(title: title, image: renderedImage, blocks: blocks, currentPage: idx + 1, totalPages: total, pdfData: data)
        processing = false
    }

    private func normalizeImageOrientation(_ img: UIImage) -> UIImage {
        let maxSide: CGFloat = 2048
        let ratio = min(1.0, maxSide / max(1, max(img.size.width, img.size.height)))
        let target = CGSize(width: max(1, img.size.width * ratio), height: max(1, img.size.height * ratio))
        let renderer = UIGraphicsImageRenderer(size: target)
        return renderer.image { _ in
            img.draw(in: CGRect(origin: .zero, size: target))
        }
    }

    private func renderParagraphsCanvas(_ paragraphs: [String]) -> (UIImage, [OcrTextBlock]) {
        let lines = Array(paragraphs.prefix(80))
        let width: CGFloat = 1100
        let margin: CGFloat = 44
        let lineHeight: CGFloat = 42
        let height = min(2048, max(500, CGFloat(lines.count) * lineHeight + margin * 2))
        let attrs: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 24, weight: .regular),
            .foregroundColor: UIColor.darkText,
        ]
        var blocks: [OcrTextBlock] = []
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height))
        let img = renderer.image { ctx in
            UIColor.white.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: width, height: height))
            var y = margin
            for (i, text) in lines.enumerated() {
                if y + lineHeight > height - margin { break }
                let clipped = String(text.prefix(90))
                let ns = clipped as NSString
                let sz = ns.size(withAttributes: attrs)
                ns.draw(at: CGPoint(x: margin, y: y), withAttributes: attrs)
                blocks.append(OcrTextBlock(
                    id: i + 1,
                    text: text,
                    left: Double(margin / width),
                    top: Double(y / height),
                    right: min(1.0, Double((margin + max(40, sz.width)) / width)),
                    bottom: min(1.0, Double((y + max(24, sz.height)) / height))
                ))
                y += lineHeight
            }
        }
        return (img, blocks)
    }

    nonisolated private static func recognizeWithVision(_ image: UIImage) async -> [OcrTextBlock] {
        guard let cgImage = image.cgImage else { return [] }
        return await Task.detached(priority: .userInitiated) {
            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            request.recognitionLanguages = ["es-ES", "en-US"]
            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            try? handler.perform([request])
            let observations = request.results ?? []
            var blocks: [OcrTextBlock] = []
            var nextID = 1
            for obs in observations {
                guard let candidate = obs.topCandidates(1).first else { continue }
                let full = candidate.string.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !full.isEmpty else { continue }
                let box = obs.boundingBox
                let top = min(1, max(0, 1.0 - Double(box.maxY)))
                let bottom = min(1, max(0, 1.0 - Double(box.minY)))
                blocks.append(OcrTextBlock(
                    id: nextID,
                    text: full,
                    left: min(1, max(0, Double(box.minX))),
                    top: top,
                    right: min(1, max(0, Double(box.maxX))),
                    bottom: bottom
                ))
                nextID += 1
            }
            return blocks.sorted { a, b in
                let ra = Int(a.top * 25.0), rb = Int(b.top * 25.0)
                return ra != rb ? ra < rb : a.left < b.left
            }
        }.value
    }
}

private struct OcrCameraCaptureView: UIViewControllerRepresentable {
    let onPicked: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: OcrCameraCaptureView
        init(parent: OcrCameraCaptureView) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController,
                                   didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let img = info[.originalImage] as? UIImage {
                parent.onPicked(img)
            }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}
