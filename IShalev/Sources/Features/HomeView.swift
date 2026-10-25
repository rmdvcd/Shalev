import SwiftUI
import Charts

@MainActor
struct HomeView: View {
    @ObservedObject var store: ShalevStore
    let onNewSale: () -> Void
    let onSellService: () -> Void
    let onShowInventory: () -> Void
    let onShowSettings: () -> Void
    var canSellProducts = true
    var canSellServices = true
    var canManageCash = true
    var canViewInventory = true
    var canViewRecords = true
    var canViewSales = true
    var canViewServices = true
    var canManageBusinessSettings = true
    var canChangePrices = true
    var isEmployeeSession = false
    var onSwitchToOwner: () -> Void = { }

    @State private var period: StatsPeriod = .year
    @State private var expandedCards: Set<String> = []
    @State private var shiftSheet: ShiftSheetAction?
    @State private var expandedFab = false

    private var periodSales: [Sale] {
        store.sales(for: period).filter { $0.isService ? canViewServices : canViewSales }
    }
    private var monthlySales: [(label: String, cents: Int64)] {
        let calendar = Calendar.current
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "es_ES")
        formatter.dateFormat = "MMM"
        let now = Date()
        return (0..<12).reversed().compactMap { offset in
            guard let month = calendar.date(byAdding: .month, value: -offset,
                                            to: calendar.date(from: calendar.dateComponents([.year, .month], from: now)) ?? now),
                  let interval = calendar.dateInterval(of: .month, for: month) else { return nil }
            let total = store.data.sales.filter {
                !$0.isCancelled && ($0.isService ? canViewServices : canViewSales)
                    && $0.date >= interval.start && $0.date < interval.end
            }.reduce(Int64(0)) { $0 + $1.totalCents }
            return (formatter.string(from: month).lowercased(), total)
        }
    }
    private var revenue: Int64 { periodSales.reduce(0) { $0 + $1.totalCents } }
    private var profit: Int64 { periodSales.reduce(0) { $0 + $1.profitCents } }
    private var periodTitle: String { period.rawValue }
    private var lowStockProducts: [Product] {
        store.activeProducts.filter { product in
            product.kind != .prepared && product.lowStockMilli > 0 && product.stockMilli <= product.lowStockMilli
                && (product.criticalStockMilli == 0 || product.stockMilli > product.criticalStockMilli)
        }
    }
    private var criticalProducts: [Product] {
        store.activeProducts.filter { $0.kind != .prepared && $0.criticalStockMilli > 0 && $0.stockMilli <= $0.criticalStockMilli }
    }
    private var expiringProducts: [Product] {
        let today = Calendar.current.startOfDay(for: Date())
        let limit = Calendar.current.date(byAdding: .day, value: 7, to: today) ?? today
        return store.activeProducts.filter { product in
            guard product.stockMilli > 0, let expiresAt = product.expiresAt else { return false }
            return Calendar.current.startOfDay(for: expiresAt) <= limit
        }.sorted { ($0.expiresAt ?? .distantFuture) < ($1.expiresAt ?? .distantFuture) }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                ShalevScreenTitle(title: "Shalev", isBrand: true)
                    .padding(.top, 10)
                if isEmployeeSession {
                    Button(action: onSwitchToOwner) {
                        Label("Cambiar a dueño / administrador", systemImage: "person.badge.key.fill")
                            .font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                    }
                    .frame(maxWidth: .infinity, alignment: .trailing)
                }

                shiftCard

                if canViewRecords {
                    Menu {
                        ForEach(StatsPeriod.allCases) { option in
                        Button {
                            period = option
                            expandedCards.removeAll()
                        } label: {
                            if period == option { Label(option.rawValue, systemImage: "checkmark") }
                            else { Text(option.rawValue) }
                        }
                    }
                } label: {
                    HStack(spacing: 13) {
                        Image(systemName: "calendar")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(Color.shalevPrimary)
                            .frame(width: 44, height: 44)
                            .background(Color.shalevPrimaryContainer, in: Circle())
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Período").font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                            Text(periodTitle).font(.system(size: 16, weight: .semibold)).foregroundStyle(Color.shalevText)
                        }
                        Spacer()
                        Image(systemName: "chevron.down").font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevSecondaryText)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.shalevSurfaceVariant, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Período: \(periodTitle)")

                if canViewInventory { inventoryAlerts }

                if canManageBusinessSettings || canChangePrices {
                    HStack(spacing: 12) {
                        if canManageBusinessSettings {
                            Button(action: onShowSettings) {
                                ShalevStatTile(title: "Pago electrónico", value: paymentSummary, symbol: "creditcard.fill")
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Pago electrónico. Abrir Ajustes")
                        }
                        if canChangePrices {
                            Button(action: onShowSettings) {
                                ShalevStatTile(title: "Precios", value: priceSummary, symbol: "dollarsign", tint: .shalevSecondary)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Precios. Abrir Ajustes")
                        }
                    }
                }

                accordion(title: "Ventas", value: Cup(cents: revenue).formatted, key: "sales") {
                    VStack(alignment: .leading, spacing: 12) {
                        Chart {
                            ForEach(Array(monthlySales.enumerated()), id: \.offset) { _, point in
                                BarMark(x: .value("Mes", point.label), y: .value("Ventas CUP", Double(point.cents) / 100))
                                    .foregroundStyle(Color.shalevPrimary)
                                    .cornerRadius(7)
                            }
                        }
                        .frame(height: 170)
                        .chartYAxis(.hidden)
                        .chartLegend(.hidden)
                        Text("Cada barra muestra las ventas de un mes. Total del período: \(Cup(cents: revenue).formatted)")
                            .font(.system(size: 12))
                            .foregroundStyle(Color.shalevSecondaryText)
                    }
                }

                accordion(title: "Ganancia neta", value: Cup(cents: profit).formatted, key: "profit") {
                    metricDescription(title: "Ventas menos costo", amount: Cup(cents: profit).formatted,
                                      detail: "\(periodSales.count) operaciones registradas en \(periodTitle.lowercased()).")
                }

                if canViewInventory {
                    accordion(title: "Inventario", value: "\(store.activeProducts.count) artículos", key: "inventory") {
                        VStack(alignment: .leading, spacing: 10) {
                            Chart {
                                ForEach(InventoryKind.allCases) { kind in
                                    BarMark(x: .value("Tipo", kind.rawValue),
                                            y: .value("Artículos", store.activeProducts.filter { $0.kind == kind }.count))
                                        .foregroundStyle(kind == .ingredient ? Color.shalevTertiary : kind == .prepared ? Color.shalevChartOrange : Color.shalevPrimary)
                                        .cornerRadius(7)
                                }
                            }
                            .frame(height: 145)
                            .chartYAxis(.hidden)
                            .chartLegend(.hidden)
                            Text("Recuento del catálogo por tipo de artículo.")
                                .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                        }
                    }
                }

                accordion(title: "Métodos de pago", value: "\(periodSales.filter { $0.paymentMethod == .cash }.count) en efectivo · \(periodSales.filter { $0.paymentMethod == .transfer }.count) transferencias", key: "payments") {
                    VStack(spacing: 8) {
                        summaryRow("Efectivo", amount: Cup(cents: periodSales.filter { $0.paymentMethod == .cash }.reduce(0) { $0 + $1.totalCents }).formatted)
                        summaryRow("Transferencias", amount: Cup(cents: periodSales.filter { $0.paymentMethod == .transfer }.reduce(0) { $0 + $1.totalCents }).formatted)
                    }
                }

                accordion(title: "Top 3", value: periodSales.isEmpty ? "Sin ventas" : "Más vendidos", key: "top") {
                    topProducts
                }
                }

            }
            .padding(.horizontal, 17)
            .padding(.bottom, 10)
        }
        .background(Color.shalevBackground)
        .overlay(alignment: .bottomTrailing) {
            if canSellProducts || canSellServices {
                VStack(alignment: .trailing, spacing: 10) {
                    if expandedFab && canSellProducts && canSellServices {
                        Button {
                            expandedFab = false
                            onNewSale()
                        } label: {
                            Label("Nueva venta", systemImage: "cart.fill")
                                .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
                                .padding(.horizontal, 15).frame(height: 44)
                                .background(Color.shalevSurface, in: Capsule())
                                .shadow(color: .black.opacity(0.1), radius: 5, x: 0, y: 2)
                        }
                        .buttonStyle(.plain)
                        Button {
                            expandedFab = false
                            onSellService()
                        } label: {
                            Label("Vender servicio", systemImage: "scissors")
                                .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
                                .padding(.horizontal, 15).frame(height: 44)
                                .background(Color.shalevSurface, in: Capsule())
                                .shadow(color: .black.opacity(0.1), radius: 5, x: 0, y: 2)
                        }
                        .buttonStyle(.plain)
                    }
                    ShalevFloatingButton(title: canSellProducts && canSellServices
                                         ? (expandedFab ? "Cerrar acciones" : "Acciones de venta")
                                         : (canSellProducts ? "Nueva venta" : "Vender servicio"),
                                         symbol: canSellProducts && canSellServices ? (expandedFab ? "xmark" : "plus")
                                         : (canSellProducts ? "cart.fill" : "scissors")) {
                        if canSellProducts && canSellServices {
                            withAnimation(.easeInOut(duration: 0.18)) { expandedFab.toggle() }
                        } else if canSellProducts { onNewSale() }
                        else { onSellService() }
                    }
                }
                .padding(.trailing, 18)
                .padding(.bottom, 14)
            }
        }
        .sheet(item: $shiftSheet) { action in
            Group {
                if action == .close { ShiftClosingSheet(store: store) }
                else { ShiftOperationSheet(store: store, action: action) }
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
    }

    private var shiftCard: some View {
        ShalevCard(padding: 15) {
            HStack(spacing: 13) {
                Image(systemName: "storefront.fill")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(store.openShift == nil ? Color.shalevSecondaryText : Color.shalevSuccess)
                    .frame(width: 44, height: 44)
                    .background(Color.shalevSurfaceVariant, in: Circle())
                VStack(alignment: .leading, spacing: 3) {
                    Text(store.openShift == nil ? "Turno cerrado" : "Turno abierto")
                        .font(.system(size: 16, weight: .semibold)).foregroundStyle(Color.shalevText)
                    Text(store.openShift.map { "Abierto \($0.openedAt.shalevDateTime)" } ?? (canManageCash ? "Ábrelo para vender" : "Sin permisos de caja"))
                        .font(.system(size: 13)).foregroundStyle(Color.shalevSecondaryText)
                        .lineLimit(1).minimumScaleFactor(0.8)
                }
                Spacer(minLength: 3)
                if canManageCash {
                    if store.openShift == nil {
                        Button { shiftSheet = .open } label: {
                            Image(systemName: "play.fill")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundStyle(Color.shalevOnPrimary)
                                .frame(width: 46, height: 46)
                                .background(Color.shalevPrimary, in: Capsule())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Abrir turno")
                    } else {
                        Menu {
                            Button("Registrar movimiento de caja", systemImage: "arrow.left.arrow.right") { shiftSheet = .cashMovement }
                            Button("Cerrar y contar", systemImage: "checkmark.circle") { shiftSheet = .close }
                        } label: {
                            Image(systemName: "ellipsis")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(Color.shalevPrimary)
                                .frame(width: 44, height: 44)
                                .background(Color.shalevPrimaryContainer, in: Circle())
                        }
                        .accessibilityLabel("Acciones del turno")
                    }
                }
            }
        }
    }

    private var inventoryAlerts: some View {
        VStack(spacing: 10) {
            ShalevSectionHeader(title: "Alertas de inventario", trailing: "\(criticalProducts.count + lowStockProducts.count + expiringProducts.count)")
                .padding(.horizontal, 2)
            if criticalProducts.isEmpty && lowStockProducts.isEmpty && expiringProducts.isEmpty {
                ShalevCard(padding: 16) {
                    HStack(spacing: 11) {
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.shalevSuccess).font(.system(size: 19))
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Sin alertas de existencias").font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
                            Text("Los niveles de inventario están en orden.").font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                        }
                        Spacer()
                    }
                }
            } else {
                Button(action: onShowInventory) {
                    HStack(spacing: 0) {
                        alertCount(number: criticalProducts.count, label: "Crítico", tint: .shalevError)
                        Rectangle().fill(Color.shalevOutlineVariant).frame(width: 1, height: 45)
                        alertCount(number: lowStockProducts.count, label: "Stock bajo", tint: .shalevSecondary)
                        Rectangle().fill(Color.shalevOutlineVariant).frame(width: 1, height: 45)
                        alertCount(number: expiringProducts.count, label: "Caducidad", tint: .shalevTertiary)
                    }
                    .padding(.vertical, 12)
                    .background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.shalevOutlineVariant.opacity(0.6), lineWidth: 0.7))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Alertas de inventario: \(criticalProducts.count) críticas, \(lowStockProducts.count) bajas y \(expiringProducts.count) caducadas o próximas a caducar. Abrir inventario")
                if !expiringProducts.isEmpty {
                    ShalevCard(padding: 12) {
                        let startOfToday = Calendar.current.startOfDay(for: Date())
                        VStack(alignment: .leading, spacing: 9) {
                            Text("Caducidad próxima o vencida")
                                .font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText)
                            ForEach(Array(expiringProducts.prefix(4))) { product in
                                HStack(spacing: 8) {
                                    Image(systemName: "calendar.badge.exclamationmark").foregroundStyle(Color.shalevTertiary)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(product.name).font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevText).lineLimit(1)
                                        if let expiry = product.expiresAt {
                                            let isExpired = Calendar.current.startOfDay(for: expiry) < startOfToday
                                            Text(isExpired ? "Vencido" : "Caduca el \(expiry.shalevDate)")
                                                .font(.system(size: 11, weight: .medium))
                                                .foregroundStyle(isExpired ? Color.shalevError : Color.shalevSecondaryText)
                                        }
                                    }
                                    Spacer(minLength: 4)
                                }
                            }
                            if expiringProducts.count > 4 {
                                Text("Y \(expiringProducts.count - 4) más")
                                    .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                            }
                        }
                    }
                }
            }
        }
    }

    private func alertCount(number: Int, label: String, tint: Color) -> some View {
        VStack(spacing: 3) {
            Text("\(number)").font(.system(size: 19, weight: .bold)).foregroundStyle(tint)
            Text(label).font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
        }
        .frame(maxWidth: .infinity)
    }

    private func accordion<Content: View>(title: String, value: String, key: String,
                                           @ViewBuilder content: @escaping () -> Content) -> some View {
        DisclosureGroup(isExpanded: binding(for: key)) {
            content().padding(.top, 12)
        } label: {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 5) {
                    Text(title).font(.system(size: 15, weight: .semibold)).foregroundStyle(Color.shalevText)
                    Text(value).font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevSecondaryText)
                        .lineLimit(2).minimumScaleFactor(0.82)
                }
                Spacer(minLength: 10)
                Image(systemName: expandedCards.contains(key) ? "chevron.up" : "chevron.down")
                    .font(.system(size: 12, weight: .bold)).foregroundStyle(Color.shalevSecondaryText)
            }
            .contentShape(Rectangle())
        }
        .tint(Color.shalevPrimary)
        .font(.system(size: 14))
        .padding(16)
        .background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.shalevOutlineVariant.opacity(0.55), lineWidth: 0.7))
        .shadow(color: .black.opacity(0.04), radius: 3, x: 0, y: 2)
    }

    private func binding(for key: String) -> Binding<Bool> {
        Binding(get: { expandedCards.contains(key) }, set: { isOpen in
            if isOpen { expandedCards.insert(key) } else { expandedCards.remove(key) }
        })
    }

    private func metricDescription(title: String, amount: String, detail: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.system(size: 13)).foregroundStyle(Color.shalevSecondaryText)
            Text(amount).font(.system(size: 20, weight: .bold)).foregroundStyle(Color.shalevText)
            Text(detail).font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
        }
    }

    private func summaryRow(_ title: String, amount: String) -> some View {
        HStack {
            Text(title).font(.system(size: 13)).foregroundStyle(Color.shalevSecondaryText)
            Spacer()
            Text(amount).font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText)
        }
    }

    private var topProducts: some View {
        let grouped = topRankedProducts
        return VStack(spacing: 10) {
            if grouped.isEmpty {
                Text("Todavía no hay ventas para mostrar.").font(.system(size: 13)).foregroundStyle(Color.shalevSecondaryText)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                ForEach(Array(grouped.enumerated()), id: \.offset) { index, item in
                    HStack(spacing: 10) {
                        Text("\(index + 1)")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(Color.shalevPrimary)
                            .frame(width: 26, height: 26)
                            .background(Color.shalevPrimaryContainer, in: Circle())
                        Text(item.name).font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevText).lineLimit(1)
                        Spacer()
                        Text("\(item.units) u · \(Cup(cents: item.totalCents).formatted)")
                            .font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.shalevSecondaryText)
                    }
                }
            }
        }
    }

    private var topRankedProducts: [RankedSaleItem] {
        var totals: [String: (units: Int, cents: Int64)] = [:]
        for sale in periodSales {
            for line in sale.lines {
                let previous = totals[line.name] ?? (units: 0, cents: 0)
                totals[line.name] = (units: previous.units + line.quantity, cents: previous.cents + line.subtotalCents)
            }
        }
        let ranked: [RankedSaleItem] = totals.map { entry in
            RankedSaleItem(name: entry.key, units: entry.value.units, totalCents: entry.value.cents)
        }
        let sorted = ranked.sorted { lhs, rhs in
            if lhs.units != rhs.units { return lhs.units > rhs.units }
            return lhs.totalCents > rhs.totalCents
        }
        return Array(sorted.prefix(3))
    }

    private var paymentSummary: String {
        let phone = store.data.settings.profile.transferPhone
        return phone.isEmpty ? "Configura tus datos de cobro" : "Tel. \(phone)"
    }

    private var priceSummary: String {
        let count = store.data.settings.priceAdjustments.filter(\.active).count
        return count == 0 ? "Sin ajustes activos" : "\(count) ajuste\(count == 1 ? "" : "s") activo\(count == 1 ? "" : "s")"
    }
}

private struct RankedSaleItem: Identifiable {
    var name: String
    var units: Int
    var totalCents: Int64
    var id: String { name }
}

private enum ShiftSheetAction: String, Identifiable, Equatable {
    case open, close, cashMovement
    var id: String { rawValue }
}

private enum ShiftClosingStage { case cash, inventory, review }

@MainActor
private struct ShiftClosingSheet: View {
    @ObservedObject var store: ShalevStore
    @Environment(\.dismiss) private var dismiss
    @State private var stage: ShiftClosingStage = .cash
    @State private var billCounts: [Int64: String] = [:]
    @State private var otherCash = ""
    @State private var countedStock: [UUID: String] = [:]
    @State private var inventoryCountSkipped = false
    @State private var inventoryConfirmed = false
    @State private var discrepancyReason = ""
    @State private var errorMessage: String?

    private var expectedCash: Int64 { store.openShift.map { store.expectedCashCents(for: $0.id) } ?? 0 }
    private var preview: ClosingPreview? { try? store.prepareClosingCount() }
    private var countedCash: Int64? {
        var total = otherCash.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Int64(0) : (Cup.parse(otherCash)?.cents ?? -1)
        guard total >= 0 else { return nil }
        for denomination in ShiftClosingDenominations.all {
            let text = billCounts[denomination] ?? ""
            guard !text.isEmpty else { continue }
            guard let count = FixedNumber.parse(text, unitScale: 1)?.cents else { return nil }
            let (units, overflow) = count.multipliedReportingOverflow(by: denomination * 100)
            guard !overflow else { return nil }
            let (newTotal, additionOverflow) = total.addingReportingOverflow(units)
            guard !additionOverflow else { return nil }
            total = newTotal
        }
        return total
    }
    private var cashDifference: Int64? { countedCash.map { $0 - expectedCash } }
    private var movedArticles: [ClosingInventoryLine] { preview?.movedArticles ?? [] }
    private var inventoryInputsValid: Bool {
        movedArticles.allSatisfy { FixedNumber.parse(countedStock[$0.productID] ?? "", unitScale: 1) != nil }
    }
    private var inventoryDifferences: [ClosingInventoryLine] {
        movedArticles.filter { line in
            guard let counted = FixedNumber.parse(countedStock[line.productID] ?? "", unitScale: 1)?.cents else { return true }
            return counted != line.expectedStockMilli
        }
    }
    private var reasonIsValid: Bool {
        (3...160).contains(discrepancyReason.trimmingCharacters(in: .whitespacesAndNewlines).count)
    }
    private var canReviewInventory: Bool {
        inventoryCountSkipped || (inventoryInputsValid && (inventoryDifferences.isEmpty || (inventoryConfirmed && reasonIsValid)))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    ShalevScreenTitle(title: "Cierre de turno", subtitle: subtitle)
                    switch stage {
                    case .cash: cashStep
                    case .inventory: inventoryStep
                    case .review: reviewStep
                    }
                }
                .padding(18)
            }
            .background(Color.shalevBackground)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Cancelar") { dismiss() } } }
            .alert("No se pudo cerrar el turno", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Entendido", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
    }

    private var subtitle: String {
        switch stage {
        case .cash: "1 de 3 · Cuenta cada denominación y otro efectivo."
        case .inventory: "2 de 3 · Concilia los artículos movidos en este turno."
        case .review: "3 de 3 · Revisa el resumen antes de cerrar."
        }
    }

    private var cashStep: some View {
        VStack(spacing: 14) {
            ShalevCard {
                VStack(spacing: 9) {
                    closingRow("Efectivo esperado", Cup(cents: expectedCash).formatted)
                    closingRow("Efectivo contado", countedCash.map { Cup(cents: $0).formatted } ?? "—")
                    Divider()
                    HStack {
                        Text("Diferencia").font(.system(size: 13)).foregroundStyle(Color.shalevSecondaryText)
                        Spacer()
                        Text(cashDifferenceText).font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(cashDifference == 0 ? Color.shalevSuccess : Color.shalevSecondary)
                    }
                }
            }
            ShalevCard {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Billetes").font(.system(size: 15, weight: .semibold)).foregroundStyle(Color.shalevText)
                    ForEach(ShiftClosingDenominations.all, id: \.self) { denomination in
                        HStack {
                            Text("\(denomination) CUP").font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevText)
                            Spacer()
                            TextField("0", text: billBinding(denomination))
                                .keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                                .frame(width: 90).padding(9)
                                .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 10))
                        }
                    }
                    Divider()
                    HStack {
                        Text("Otro efectivo (CUP)").font(.system(size: 13)).foregroundStyle(Color.shalevText)
                        Spacer()
                        TextField("0.00", text: $otherCash)
                            .keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                            .frame(width: 110).padding(9)
                            .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 10))
                    }
                }
            }
            Button { stage = .inventory } label: { Label("Continuar al inventario", systemImage: "arrow.right") }
                .buttonStyle(ShalevPrimaryButtonStyle()).disabled(countedCash == nil)
        }
    }

    private var inventoryStep: some View {
        VStack(spacing: 14) {
            ShalevCard {
                VStack(alignment: .leading, spacing: 10) {
                    Toggle("Omitir el conteo de inventario", isOn: $inventoryCountSkipped).tint(Color.shalevPrimary)
                    Text("Si lo omites, no se ajustará ninguna existencia. El cierre conservará esta decisión.")
                        .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                }
            }
            if !inventoryCountSkipped {
                ShalevCard {
                    VStack(alignment: .leading, spacing: 11) {
                        Text("Existencias contadas").font(.system(size: 15, weight: .semibold)).foregroundStyle(Color.shalevText)
                        if movedArticles.isEmpty {
                            Text("No hubo movimientos de inventario en este turno.").font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                        }
                        ForEach(movedArticles) { line in
                            VStack(alignment: .leading, spacing: 6) {
                                Text(line.productName).font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText)
                                HStack {
                                    Text("Esperado: \(FixedNumber.formatMilli(line.expectedStockMilli))")
                                        .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                                    Spacer()
                                    TextField("Contado", text: stockBinding(line.productID))
                                        .keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                                        .frame(width: 105).padding(8)
                                        .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 10))
                                }
                            }
                        }
                    }
                }
                if !inventoryDifferences.isEmpty {
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 10) {
                            Label("Revisa las diferencias", systemImage: "exclamationmark.triangle.fill")
                                .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevSecondary)
                            ForEach(inventoryDifferences) { line in
                                Text("\(line.productName): esperado \(FixedNumber.formatMilli(line.expectedStockMilli)), contado \(countedStock[line.productID] ?? "—")")
                                    .font(.system(size: 12)).foregroundStyle(Color.shalevText)
                            }
                            Toggle("Confirmo que revisé las diferencias", isOn: $inventoryConfirmed).tint(Color.shalevPrimary)
                            TextField("Motivo del ajuste (3–160 caracteres)", text: $discrepancyReason, axis: .vertical)
                                .lineLimit(2...4).padding(11)
                                .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
                        }
                    }
                }
            }
            HStack(spacing: 10) {
                Button { stage = .cash } label: { Label("Atrás", systemImage: "arrow.left") }
                    .buttonStyle(ShalevTonalButtonStyle())
                Button { stage = .review } label: { Label("Revisar cierre", systemImage: "arrow.right") }
                    .buttonStyle(ShalevPrimaryButtonStyle()).disabled(!canReviewInventory)
            }
        }
    }

    private var reviewStep: some View {
        VStack(spacing: 14) {
            if let preview {
                ShalevCard {
                    VStack(spacing: 9) {
                        closingRow("Ventas", "\(preview.salesCount)")
                        closingRow("Total de ventas", Cup(cents: preview.salesTotalCents).formatted)
                        closingRow("Efectivo / transferencias", "\(Cup(cents: preview.cashSalesCents).formatted) / \(Cup(cents: preview.transferSalesCents).formatted)")
                        closingRow("Ganancia", Cup(cents: preview.profitCents).formatted)
                        closingRow("Efectivo esperado", Cup(cents: expectedCash).formatted)
                        closingRow("Efectivo contado", countedCash.map { Cup(cents: $0).formatted } ?? "—")
                        closingRow("Diferencia de caja", cashDifferenceText)
                    }
                }
            }
            ShalevCard {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Inventario").font(.system(size: 15, weight: .semibold)).foregroundStyle(Color.shalevText)
                    if inventoryCountSkipped {
                        Text("Conteo omitido: no se ajustarán existencias.").font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                    } else if inventoryDifferences.isEmpty {
                        Text("Sin diferencias por ajustar.").font(.system(size: 12)).foregroundStyle(Color.shalevSuccess)
                    } else {
                        Text("\(inventoryDifferences.count) artículo(s) con ajuste.").font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.shalevSecondary)
                        Text(discrepancyReason).font(.system(size: 12)).foregroundStyle(Color.shalevText)
                    }
                }
            }
            Text("Al cerrar no podrás registrar más operaciones en este turno. El resumen quedará congelado.")
                .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText).multilineTextAlignment(.center)
            HStack(spacing: 10) {
                Button { stage = .inventory } label: { Label("Atrás", systemImage: "arrow.left") }
                    .buttonStyle(ShalevTonalButtonStyle())
                Button(action: finish) { Label("Cerrar turno", systemImage: "checkmark.circle.fill") }
                    .buttonStyle(ShalevPrimaryButtonStyle())
            }
        }
    }

    private var cashDifferenceText: String {
        guard let difference = cashDifference else { return "—" }
        if difference == 0 { return "Cuadra" }
        return "\(difference > 0 ? "+" : "−")\(Cup(cents: abs(difference)).formatted)"
    }

    private func closingRow(_ title: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(title).font(.system(size: 13)).foregroundStyle(Color.shalevSecondaryText)
            Spacer()
            Text(value).font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText).multilineTextAlignment(.trailing)
        }
    }

    private func billBinding(_ denomination: Int64) -> Binding<String> {
        Binding(get: { billCounts[denomination] ?? "" }, set: { billCounts[denomination] = $0 })
    }

    private func stockBinding(_ productID: UUID) -> Binding<String> {
        Binding(get: { countedStock[productID] ?? "" }, set: { countedStock[productID] = $0 })
    }

    private func finish() {
        var cashCounts: [Int64: Int64] = [:]
        for denomination in ShiftClosingDenominations.all {
            let text = billCounts[denomination] ?? ""
            cashCounts[denomination] = text.isEmpty ? 0 : (FixedNumber.parse(text, unitScale: 1)?.cents ?? 0)
        }
        var stock: [UUID: Int64] = [:]
        if !inventoryCountSkipped {
            for line in movedArticles {
                guard let value = FixedNumber.parse(countedStock[line.productID] ?? "", unitScale: 1)?.cents else {
                    errorMessage = "Completa todas las existencias contadas."; return
                }
                stock[line.productID] = value
            }
        }
        let count = ShiftClosingCount(cashCounts: cashCounts, otherCashCents: Cup.parse(otherCash)?.cents ?? 0,
                                      countedStockMilli: stock, inventoryCountSkipped: inventoryCountSkipped,
                                      inventoryConfirmed: inventoryConfirmed, discrepancyReason: discrepancyReason)
        do {
            _ = try store.closeShift(count: count)
            dismiss()
        } catch { errorMessage = error.localizedDescription }
    }
}

@MainActor
private struct ShiftOperationSheet: View {
    @ObservedObject var store: ShalevStore
    let action: ShiftSheetAction
    @Environment(\.dismiss) private var dismiss
    @State private var amountText = ""
    @State private var reason = ""
    @State private var movementKind: CashMovementKind = .income
    @State private var errorMessage: String?

    private var amount: Int64? { Cup.parse(amountText)?.cents }
    private var expected: Int64 { store.openShift.map { store.expectedCashCents(for: $0.id) } ?? 0 }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    ShalevScreenTitle(title: title, subtitle: subtitle)
                    if action == .close {
                        ShalevCard {
                            VStack(spacing: 9) {
                                HStack { Text("Efectivo esperado"); Spacer(); Text(Cup(cents: expected).formatted).fontWeight(.semibold) }
                                Divider()
                                HStack { Text("Diferencia"); Spacer(); Text(differenceText).fontWeight(.semibold).foregroundStyle(differenceColor) }
                            }
                            .font(.system(size: 14))
                            .foregroundStyle(Color.shalevText)
                        }
                    }
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 13) {
                            if action == .cashMovement {
                                Picker("Tipo de movimiento", selection: $movementKind) {
                                    ForEach(CashMovementKind.allCases) { kind in Text(kind.rawValue).tag(kind) }
                                }
                                .pickerStyle(.segmented)
                            }
                            VStack(alignment: .leading, spacing: 6) {
                                Text(action == .close ? "Efectivo contado" : "Importe")
                                    .font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                                TextField("0.00", text: $amountText)
                                    .keyboardType(.decimalPad)
                                    .font(.system(size: 18, weight: .semibold))
                                    .padding(13)
                                    .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 12))
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.shalevOutlineVariant, lineWidth: 1))
                                Text("Importes en CUP").font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                            }
                            if action == .cashMovement {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("Motivo").font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                                    TextField("Escribe un motivo", text: $reason, axis: .vertical)
                                        .lineLimit(2...4)
                                        .padding(12)
                                        .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 12))
                                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.shalevOutlineVariant, lineWidth: 1))
                                }
                            }
                            Button(action == .open ? "Abrir turno" : action == .close ? "Cerrar turno" : "Guardar movimiento") { submit() }
                                .buttonStyle(ShalevPrimaryButtonStyle())
                        }
                    }
                }
                .padding(18)
            }
            .background(Color.shalevBackground)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Color.shalevPrimary)
                }
            }
            .alert("No se pudo completar", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Entendido", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
        .onAppear {
            if action == .close { amountText = Cup(cents: expected).inputValue }
        }
    }

    private var title: String {
        switch action {
        case .open: "Abrir turno"
        case .close: "Cierre de caja"
        case .cashMovement: "Movimiento de caja"
        }
    }

    private var subtitle: String {
        switch action {
        case .open: "Indica el efectivo inicial de la caja."
        case .close: "Cuenta el efectivo disponible antes de cerrar."
        case .cashMovement: "Registra una entrada o salida con su motivo."
        }
    }

    private var differenceText: String {
        guard let amount else { return "—" }
        let difference = amount - expected
        if difference == 0 { return "Cuadra" }
        return "\(difference > 0 ? "+" : "−")\(Cup(cents: abs(difference)).formatted)"
    }

    private var differenceColor: Color {
        guard let amount else { return .shalevSecondaryText }
        return amount == expected ? .shalevSuccess : .shalevSecondary
    }

    private func submit() {
        guard let amount else { errorMessage = "Escribe un importe válido."; return }
        do {
            switch action {
            case .open:
                try store.openShift(openingCashCents: amount)
            case .close:
                try store.closeShift(countedCashCents: amount)
            case .cashMovement:
                try store.recordCashMovement(kind: movementKind, amountCents: amount, reason: reason)
            }
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
