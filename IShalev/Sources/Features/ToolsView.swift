import SwiftUI

private enum CostCurrency: String, Codable, CaseIterable, Identifiable {
    case cup = "CUP"
    case usd = "USD"
    var id: String { rawValue }
}

private enum CostCategory: String, Codable, CaseIterable, Identifiable {
    case materials = "Materias primas y materiales"
    case labor = "Fuerza de trabajo directa"
    case direct = "Otros gastos directos"
    case indirect = "Gastos indirectos de producción"
    var id: String { rawValue }
}

private struct CostPart: Codable, Identifiable, Equatable {
    var id = UUID()
    var name = ""
    var category: CostCategory = .materials
    var quantity = "1"
    var unit = "unidad"
    var unitCost = "0"
    var currency: CostCurrency = .cup

    var subtotal: Decimal {
        (Decimal(string: quantity.replacingOccurrences(of: ",", with: ".")) ?? 0)
        * (Decimal(string: unitCost.replacingOccurrences(of: ",", with: ".")) ?? 0)
    }
}

private struct CostSheet: Codable, Identifiable, Equatable {
    var id = UUID()
    var name = ""
    var margin = ""
    var usdRate = ""
    var parts: [CostPart] = []

    func total(_ currency: CostCurrency, category: CostCategory? = nil) -> Decimal {
        parts.filter { $0.currency == currency && (category == nil || $0.category == category) }
            .reduce(Decimal.zero) { $0 + $1.subtotal }
    }

    var exportCUP: Decimal? {
        let cup = total(.cup)
        let usd = total(.usd)
        guard usd == 0 || (Decimal(string: usdRate.replacingOccurrences(of: ",", with: ".")) ?? 0) > 0 else { return nil }
        let base = cup + usd * (Decimal(string: usdRate.replacingOccurrences(of: ",", with: ".")) ?? 0)
        let percent = Decimal(string: margin.replacingOccurrences(of: ",", with: ".")) ?? 0
        return base * (1 + percent / 100)
    }
}

@MainActor
struct ToolsView: View {
    @ObservedObject var store: ShalevStore
    @AppStorage("shalev.costSheets") private var savedJSON = ""
    @State private var sheets: [CostSheet] = []
    @State private var query = ""
    @State private var expanded: Set<UUID> = []
    @State private var draft = CostSheet()
    @State private var editingPart: CostPart?
    @State private var editingPartIndex: Int?
    @State private var message: String?
    @State private var showCurrencyCalculator = false

    private var filteredSheets: [CostSheet] {
        query.isEmpty ? sheets : sheets.filter { $0.name.localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        VStack(spacing: 0) {
            ShalevScreenTitle(title: "Herramientas")
                .padding(.horizontal, 16).padding(.vertical, 10)
            ScrollView {
                VStack(spacing: 14) {
                    search
                    savedSheets
                    categoryTotals
                    editor
                }
                .padding(.horizontal, 16).padding(.bottom, 96)
            }
        }
        .background(Color.shalevBackground)
        .onAppear(perform: load)
        .sheet(item: $editingPart) { part in
            CostPartEditor(part: part) { updated in
                if let index = editingPartIndex { draft.parts[index] = updated }
                else { draft.parts.append(updated) }
                editingPart = nil; editingPartIndex = nil
            }
        }
        .sheet(isPresented: $showCurrencyCalculator) { CurrencyCalculatorView() }
        .alert("Herramientas", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("Entendido", role: .cancel) { message = nil }
        } message: { Text(message ?? "") }
    }

    private var search: some View {
        HStack {
            Image(systemName: "magnifyingglass").foregroundStyle(Color.shalevSecondaryText)
            TextField("Buscar ficha", text: $query)
                .textInputAutocapitalization(.words)
            if !query.isEmpty { Button { query = "" } label: { Image(systemName: "xmark.circle.fill") }.buttonStyle(.plain) }
        }
        .padding(13).background(Color.shalevSurfaceVariant, in: RoundedRectangle(cornerRadius: 16))
        .accessibilityElement(children: .contain)
    }

    @ViewBuilder private var savedSheets: some View {
        if !filteredSheets.isEmpty {
            ShalevSectionHeader(title: "Fichas guardadas")
            ForEach(filteredSheets) { sheet in
                ShalevCard(padding: 0) {
                    Button {
                        withAnimation { if expanded.contains(sheet.id) { expanded.remove(sheet.id) } else { expanded.insert(sheet.id) } }
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(sheet.name).font(.shalev(15, weight: .semibold)).foregroundStyle(Color.shalevText)
                                Text(sheet.exportCUP.map { "\(whole($0)) CUP" } ?? "Falta la tasa CUP por USD")
                                    .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
                            }
                            Spacer()
                            Image(systemName: expanded.contains(sheet.id) ? "chevron.up" : "chevron.down")
                        }.padding(15).contentShape(Rectangle())
                    }.buttonStyle(.plain)
                    if expanded.contains(sheet.id) {
                        Divider()
                        HStack {
                            toolButton("Editar", "pencil") { draft = sheet }
                            toolButton("Inventario", "archivebox.fill") { exportInventory(sheet) }
                            toolButton("Servicios", "storefront.fill") { exportService(sheet) }
                            toolButton("Eliminar", "trash.fill", destructive: true) { delete(sheet) }
                        }.padding(12)
                    }
                }
            }
        }
    }

    private var categoryTotals: some View {
        DisclosureGroup {
            VStack(alignment: .leading, spacing: 9) {
                ForEach(CostCategory.allCases) { category in
                    Text("\(category.rawValue): \(whole(draft.total(.cup, category: category))) CUP · \(whole(draft.total(.usd, category: category))) USD")
                        .font(.shalev(12)).foregroundStyle(Color.shalevText)
                }
                Text("Costo total: \(whole(draft.total(.cup))) CUP · \(whole(draft.total(.usd))) USD")
                    .font(.shalev(14, weight: .semibold))
            }.frame(maxWidth: .infinity, alignment: .leading).padding(.top, 10)
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text("Totales por categoría").font(.shalev(15, weight: .semibold))
                Text("\(whole(draft.total(.cup))) CUP · \(whole(draft.total(.usd))) USD").font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
            }
        }
        .padding(15).background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 18))
    }

    private var editor: some View {
        VStack(alignment: .leading, spacing: 13) {
            HStack {
                Text(sheets.contains(where: { $0.id == draft.id }) ? "Editar ficha" : "Nueva ficha").font(.shalev(18, weight: .semibold))
                Spacer()
                Button { draft = CostSheet() } label: { Label("Nueva", systemImage: "plus") }
            }
            TextField("Producto o servicio", text: $draft.name)
                .textFieldStyle(.roundedBorder)
            Text("Partidas").font(.shalev(15, weight: .semibold))
            if draft.parts.isEmpty { Text("Todavía no hay partidas.").foregroundStyle(Color.shalevSecondaryText) }
            ForEach(Array(draft.parts.enumerated()), id: \.element.id) { index, part in
                ShalevCard(padding: 13) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(part.name).font(.shalev(14, weight: .semibold)) // fila 1
                        Text("\(part.category.rawValue) · \(part.quantity) \(part.unit) × \(whole(part.unitCost)) \(part.currency.rawValue)")
                            .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText) // fila 2
                        Text("Importe: \(whole(part.subtotal)) \(part.currency.rawValue)")
                            .font(.shalev(14, weight: .semibold)) // fila 3
                        HStack {
                            Spacer()
                            Button { editingPartIndex = index; editingPart = part } label: { Image(systemName: "pencil") }
                            Button(role: .destructive) { draft.parts.remove(at: index) } label: { Image(systemName: "trash") }
                        }.buttonStyle(.borderless)
                    }
                }
            }
            Button {
                editingPartIndex = nil; editingPart = CostPart()
            } label: { Label("Agregar partida", systemImage: "plus.circle.fill").frame(maxWidth: .infinity) }
                .buttonStyle(.bordered) // al final de la lista
            TextField("Margen de ganancia % (opcional)", text: $draft.margin).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
            TextField("Tasa CUP por 1 USD (para exportar)", text: $draft.usdRate).keyboardType(.decimalPad).textFieldStyle(.roundedBorder)
            Button { saveDraft() } label: { Label("Guardar ficha", systemImage: "checkmark.circle.fill").frame(maxWidth: .infinity) }
                .buttonStyle(.borderedProminent).tint(Color.shalevPrimary)
            Button { showCurrencyCalculator = true } label: { Label("Calculadora de cambio", systemImage: "arrow.left.arrow.right") }
        }
        .padding(15).background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 18))
    }

    private func toolButton(_ title: String, _ symbol: String, destructive: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) { VStack(spacing: 4) { Image(systemName: symbol); Text(title).font(.shalev(10)) } }
            .foregroundStyle(destructive ? Color.red : Color.shalevPrimary).frame(maxWidth: .infinity)
    }

    private func load() {
        guard let data = savedJSON.data(using: .utf8), let decoded = try? JSONDecoder().decode([CostSheet].self, from: data) else { return }
        sheets = decoded
    }
    private func persist() {
        guard let data = try? JSONEncoder().encode(sheets), let value = String(data: data, encoding: .utf8) else { return }
        savedJSON = value
    }
    private func saveDraft() {
        draft.name = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !draft.name.isEmpty, !draft.parts.isEmpty else { message = "Escribe un nombre y agrega al menos una partida."; return }
        if let index = sheets.firstIndex(where: { $0.id == draft.id }) { sheets[index] = draft } else { sheets.append(draft) }
        persist(); message = "Ficha guardada."
    }
    private func delete(_ sheet: CostSheet) {
        sheets.removeAll { $0.id == sheet.id }; expanded.remove(sheet.id)
        if draft.id == sheet.id { draft = CostSheet() }; persist()
    }
    private func exportInventory(_ sheet: CostSheet) {
        guard let total = sheet.exportCUP else { message = "Escribe la tasa CUP por 1 USD antes de exportar."; return }
        let rate = Decimal(string: sheet.usdRate.replacingOccurrences(of: ",", with: ".")) ?? 0
        let cost = cents(sheet.total(.cup) + sheet.total(.usd) * rate)
        let price = max(cents(total), cost + 100)
        do {
            let uses = try ensureIngredients(for: sheet, rate: rate)
            try store.saveProduct(Product(name: sheet.name, category: "Elaborado", kind: .prepared, stockMilli: 0, unit: "u", priceCents: price, costCents: cost, uses: uses))
            message = "Ficha exportada al Inventario como Elaborado con \(uses.count) insumos."
        } catch { message = error.localizedDescription }
    }
    private func exportService(_ sheet: CostSheet) {
        guard let total = sheet.exportCUP else { message = "Escribe la tasa CUP por 1 USD antes de exportar."; return }
        let rate = Decimal(string: sheet.usdRate.replacingOccurrences(of: ",", with: ".")) ?? 0
        do {
            let uses = try ensureIngredients(for: sheet, rate: rate)
            try store.saveService(ServiceItem(name: sheet.name, type: "General", priceCents: max(100, cents(total)), uses: uses))
            message = "Ficha exportada a Servicios con \(uses.count) insumos."
        } catch { message = error.localizedDescription }
    }

    private func ensureIngredients(for sheet: CostSheet, rate: Decimal) throws -> [IngredientUse] {
        var uses: [IngredientUse] = []
        let materialParts = sheet.parts.filter { $0.category == .materials }
        guard !materialParts.isEmpty else { throw BusinessError.invalidRecipe }
        for part in materialParts {
            let trimmedName = part.name.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmedName.isEmpty else { continue }
            // Buscar insumo existente por nombre (insensible a mayúsculas)
            if let existing = store.activeProducts.first(where: { $0.kind == .ingredient && $0.isActive && $0.name.lowercased() == trimmedName.lowercased() }) {
                let milli = milliUnits(from: part.quantity)
                uses.append(IngredientUse(ingredientID: existing.id, milliUnits: milli))
                continue
            }
            // Crear nuevo insumo con el costo unitario convertido a CUP
            let unitCostDecimal = Decimal(string: part.unitCost.replacingOccurrences(of: ",", with: ".")) ?? 0
            let costInCup: Decimal = part.currency == .cup ? unitCostDecimal : unitCostDecimal * rate
            let costCents = cents(costInCup)
            let alertDefaults = store.data.settings.effectiveInventoryAlertDefaults
            let newIngredient = Product(name: trimmedName, category: "Insumos", kind: .ingredient, stockMilli: 0,
                unit: part.unit.isEmpty ? "u" : part.unit, lowStockMilli: alertDefaults.ingredientLowMilli,
                criticalStockMilli: alertDefaults.ingredientCriticalMilli, priceCents: 0, costCents: costCents, uses: [])
            try store.saveProduct(newIngredient)
            guard let created = store.activeProducts.first(where: { $0.kind == .ingredient && $0.isActive && $0.name.lowercased() == trimmedName.lowercased() }) else {
                throw BusinessError.invalidRecipe
            }
            let milli = milliUnits(from: part.quantity)
            uses.append(IngredientUse(ingredientID: created.id, milliUnits: milli))
        }
        guard !uses.isEmpty else { throw BusinessError.invalidRecipe }
        return uses
    }

    private func milliUnits(from quantityString: String) -> Int64 {
        let normalized = quantityString.replacingOccurrences(of: ",", with: ".")
        guard let decimal = Decimal(string: normalized), decimal > 0 else { return 1_000 }
        var scaled = decimal * Decimal(1_000)
        var rounded = Decimal()
        NSDecimalRound(&rounded, &scaled, 0, .plain)
        let value = NSDecimalNumber(decimal: rounded).int64Value
        return max(1, value)
    }
    private func cents(_ value: Decimal) -> Int64 {
        var scaled = absDecimal(value) * 100
        var rounded = Decimal()
        NSDecimalRound(&rounded, &scaled, 0, .plain)
        return NSDecimalNumber(decimal: rounded).int64Value
    }
}

private struct CostPartEditor: View {
    @Environment(\.dismiss) private var dismiss
    @State var part: CostPart
    let save: (CostPart) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Picker("Categoría", selection: $part.category) { ForEach(CostCategory.allCases) { Text($0.rawValue).tag($0) } }
                TextField("Descripción", text: $part.name)
                TextField("Cantidad", text: $part.quantity).keyboardType(.decimalPad)
                TextField("Unidad de medida", text: $part.unit)
                TextField("Costo unitario", text: $part.unitCost).keyboardType(.decimalPad)
                Picker("Moneda", selection: $part.currency) { ForEach(CostCurrency.allCases) { Text($0.rawValue).tag($0) } }
            }
            .navigationTitle("Partida")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Guardar") { if !part.name.trimmingCharacters(in: .whitespaces).isEmpty, part.subtotal >= 0 { save(part); dismiss() } }
                }
            }
        }
    }
}

private struct CurrencyCalculatorView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var usd = ""
    @State private var rate = ""
    private var result: Decimal { (Decimal(string: usd) ?? 0) * (Decimal(string: rate) ?? 0) }
    var body: some View {
        NavigationStack {
            Form {
                TextField("Importe USD", text: $usd).keyboardType(.decimalPad)
                TextField("Tasa CUP por 1 USD", text: $rate).keyboardType(.decimalPad)
                Text("Resultado: \(whole(result)) CUP").font(.headline)
            }.navigationTitle("Calculadora de cambio")
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Listo") { dismiss() } } }
        }
    }
}

private func whole(_ value: Decimal) -> String {
    let formatter = NumberFormatter(); formatter.numberStyle = .decimal; formatter.maximumFractionDigits = 0; formatter.minimumFractionDigits = 0
    formatter.roundingMode = .halfUp; return formatter.string(from: NSDecimalNumber(decimal: absDecimal(value))) ?? "0"
}
private func whole(_ value: String) -> String { whole(Decimal(string: value.replacingOccurrences(of: ",", with: ".")) ?? 0) }
private func absDecimal(_ value: Decimal) -> Decimal { value < 0 ? -value : value }
