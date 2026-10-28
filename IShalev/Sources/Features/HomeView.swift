import SwiftUI
import Charts

/// Período de Inicio, con las mismas opciones que Android: el turno abierto (o el último cerrado),
/// hoy, semana, mes y año. `Turno` es la opción por defecto.
enum HomePeriod: String, CaseIterable, Identifiable, Equatable {
    case shift = "Turno"
    case today = "Hoy"
    case week = "Esta semana"
    case month = "Este mes"
    case year = "Este año"

    var id: String { rawValue }

    var statsPeriod: StatsPeriod? {
        switch self {
        case .shift: nil
        case .today: .today
        case .week: .week
        case .month: .month
        case .year: .year
        }
    }
}

@MainActor
struct HomeView: View {
    @ObservedObject var store: ShalevStore
    @ObservedObject var sync: SyncManager
    let onNewSale: () -> Void
    let onSellService: () -> Void
    let onShowInventory: () -> Void
    let onShowSettings: () -> Void
    var licenseBanner: String? = nil
    var onShowLicense: () -> Void = { }
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

    @State private var period: HomePeriod = .shift
    @State private var expandedCards: Set<String> = []
    @State private var shiftSheet: ShiftSheetAction?
    @State private var showSaleTypeDialog = false

    /// Turno del que habla el período «Turno»: el abierto o, si no hay, el último cerrado.
    private var referenceShift: Shift? {
        if let open = store.openShift { return open }
        return store.data.shifts
            .filter { $0.closedAt != nil && $0.employeeID == nil }
            .sorted { ($0.closedAt ?? .distantPast) > ($1.closedAt ?? .distantPast) }
            .first
    }

    private var periodSales: [Sale] {
        let base: [Sale]
        if let stats = period.statsPeriod {
            base = store.sales(for: stats)
        } else if let shift = referenceShift {
            base = store.data.sales.filter { $0.shiftID == shift.id && !$0.isCancelled }
        } else {
            base = []
        }
        return base.filter { $0.isService ? canViewServices : canViewSales }
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
    private var cost: Int64 { periodSales.reduce(0) { $0 + $1.costCents } }

    /// Productos e insumos cuentan para las alertas; los elaborados no tienen existencias propias.
    private var sellableStock: [Product] { store.activeProducts.filter { $0.kind != .prepared } }
    private var criticalProducts: [Product] {
        sellableStock.filter { $0.criticalStockMilli > 0 && $0.stockMilli <= $0.criticalStockMilli }
    }
    private var lowStockProducts: [Product] {
        sellableStock.filter { product in
            product.lowStockMilli > 0 && product.stockMilli <= product.lowStockMilli
                && (product.criticalStockMilli == 0 || product.stockMilli > product.criticalStockMilli)
        }
    }
    private var expiringProducts: [Product] {
        let today = Calendar.current.startOfDay(for: Date())
        let limit = Calendar.current.date(byAdding: .day, value: 7, to: today) ?? today
        return sellableStock.filter { product in
            guard product.stockMilli > 0, let expiresAt = product.expiresAt else { return false }
            return Calendar.current.startOfDay(for: expiresAt) <= limit
        }.sorted { ($0.expiresAt ?? .distantFuture) < ($1.expiresAt ?? .distantFuture) }
    }
    private var alertTotal: Int { criticalProducts.count + lowStockProducts.count + expiringProducts.count }

    var body: some View {
        VStack(spacing: 0) {
            fixedHeader
            ScrollView {
                VStack(spacing: 14) {
                    if canViewInventory { inventoryAlerts }
                    if canManageBusinessSettings || canChangePrices { accessTiles }
                    if canViewRecords {
                        salesAccordion
                        profitAccordion
                    }
                    if canViewInventory { inventoryAccordion }
                    if canViewRecords {
                        paymentAccordion
                        topAccordions
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 96)
            }
        }
        .background(Color.shalevBackground)
        .overlay(alignment: .bottom) {
            if store.openShift != nil, canSellProducts || canManageCash {
                HStack(spacing: 12) {
                    if canSellProducts {
                        ShalevFloatingButton(title: "Nueva venta", symbol: "cart.fill", action: startSale)
                    }
                    Spacer(minLength: 0)
                    if canManageCash {
                        ShalevFloatingButton(title: "Movimiento de caja", symbol: "banknote.fill") {
                            shiftSheet = .cashMovement
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 14)
            }
        }
        .confirmationDialog("Nueva venta", isPresented: $showSaleTypeDialog, titleVisibility: .visible) {
            Button("Productos") { onNewSale() }
            if canSellServices { Button("Servicios") { onSellService() } }
            Button("Cancelar", role: .cancel) { }
        } message: {
            Text(store.openShift == nil ? "Turno cerrado: ábrelo para vender." : "Elige qué vas a vender.")
        }
        .sheet(item: $shiftSheet) { action in
            Group {
                if action == .close { ShiftClosingSheet(store: store) }
                else { ShiftOperationSheet(store: store, sync: sync, action: action) }
            }
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
    }

    // MARK: - Bloque fijo (no se desplaza)

    private var fixedHeader: some View {
        VStack(spacing: 12) {
            ShalevScreenTitle(title: "Shalev", isBrand: true)
                .padding(.top, 10)
            if isEmployeeSession {
                Button(action: onSwitchToOwner) {
                    Label("Cambiar a dueño / administrador", systemImage: "person.badge.key.fill")
                        .font(.shalev(12, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }
            shiftCard
            if canViewRecords { periodSelector }
            if let licenseBanner { licenseBannerView(licenseBanner) }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 12)
        .background(Color.shalevBackground)
    }

    private var shiftCard: some View {
        ShalevCard(padding: 15) {
            Button(action: shiftAction) {
                HStack(spacing: 13) {
                    Image(systemName: "storefront.fill")
                        .font(.shalev(20, weight: .semibold))
                        .foregroundStyle(store.openShift == nil ? Color.shalevSecondaryText : Color.shalevPrimary)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(store.openShift == nil ? "Turno cerrado" : "Turno abierto")
                            .font(.shalev(16, weight: .semibold)).foregroundStyle(Color.shalevText)
                        Text(store.openShift.map { "Desde las \($0.openedAt.shalevTime)" } ?? "Ábrelo para vender")
                            .font(.shalev(13)).foregroundStyle(Color.shalevSecondaryText)
                            .lineLimit(1).minimumScaleFactor(0.8)
                        if let shift = store.openShift {
                            Text("Efectivo esperado: \(Cup(cents: store.expectedCashCents(for: shift.id)).formatted)")
                                .font(.shalev(12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                                .lineLimit(1).minimumScaleFactor(0.8)
                        }
                    }
                    Spacer(minLength: 3)
                    Toggle("", isOn: .constant(store.openShift != nil))
                        .labelsHidden()
                        .tint(Color.shalevPrimary)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!canManageCash)
            .accessibilityLabel(store.openShift == nil ? "Abrir turno" : "Cerrar turno")
        }
    }

    private func startSale() {
        if canSellServices { showSaleTypeDialog = true } else { onNewSale() }
    }

    private func shiftAction() {
        guard canManageCash else { return }
        shiftSheet = store.openShift == nil ? .open : .close
    }

    private var periodSelector: some View {
        Menu {
            ForEach(HomePeriod.allCases) { option in
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
                    .font(.shalev(18, weight: .semibold))
                    .foregroundStyle(Color.shalevPrimary)
                    .frame(width: 44, height: 44)
                    .background(Color.shalevPrimaryContainer, in: Circle())
                VStack(alignment: .leading, spacing: 3) {
                    Text("Período").font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                    Text(period.rawValue).font(.shalev(16, weight: .semibold)).foregroundStyle(Color.shalevText)
                }
                Spacer()
                Image(systemName: "chevron.down").font(.shalev(13, weight: .semibold)).foregroundStyle(Color.shalevSecondaryText)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Color.shalevSurfaceVariant, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Período: \(period.rawValue)")
    }

    private func licenseBannerView(_ text: String) -> some View {
        Button(action: onShowLicense) {
            HStack(spacing: 11) {
                Image(systemName: "key.fill").font(.shalev(15, weight: .semibold))
                Text(text)
                    .font(.shalev(13, weight: .semibold))
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
                Spacer(minLength: 6)
                Image(systemName: "chevron.right").font(.shalev(12, weight: .semibold))
            }
            .foregroundStyle(Color.shalevOnPrimaryContainer)
            .padding(.horizontal, 15)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(text). Abrir licencia")
    }

    // MARK: - Alertas y accesos

    private var inventoryAlerts: some View {
        VStack(spacing: 10) {
            ShalevSectionHeader(title: "Alertas de inventario")
                .padding(.horizontal, 2)
            if alertTotal == 0 {
                ShalevCard(padding: 16) {
                    HStack(spacing: 11) {
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.shalevSuccess).font(.shalev(19))
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Sin alertas de existencias").font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevText)
                            Text("Los niveles de inventario están en orden.").font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
                        }
                        Spacer()
                    }
                }
            } else {
                Button(action: onShowInventory) {
                    HStack(spacing: 8) {
                        if criticalProducts.count > 0 {
                            alertCount(number: criticalProducts.count, label: "Stock inventario crítico", tint: .shalevError)
                        }
                        if lowStockProducts.count > 0 {
                            alertCount(number: lowStockProducts.count, label: "Stock inventario bajo", tint: .shalevSecondary)
                        }
                        if expiringProducts.count > 0 {
                            alertCount(number: expiringProducts.count, label: "Próximo a caducar", tint: .shalevTertiary)
                        }
                    }
                    .padding(.vertical, 12)
                    .padding(.horizontal, 6)
                    .background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.shalevOutlineVariant.opacity(0.6), lineWidth: 0.7))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Alertas de inventario: \(criticalProducts.count) críticas, \(lowStockProducts.count) bajas y \(expiringProducts.count) próximas a caducar. Abrir inventario")
            }
        }
    }

    private func alertCount(number: Int, label: String, tint: Color) -> some View {
        VStack(spacing: 4) {
            Text("\(number)").font(.shalev(20, weight: .bold)).foregroundStyle(tint)
            Text(label)
                .font(.shalev(12, weight: .medium))
                .foregroundStyle(Color.shalevSecondaryText)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
    }

    private var accessTiles: some View {
        HStack(alignment: .top, spacing: 12) {
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

    // MARK: - Acordeones

    private var salesAccordion: some View {
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
                    .font(.shalev(12))
                    .foregroundStyle(Color.shalevSecondaryText)
            }
        }
    }

    private var profitAccordion: some View {
        accordion(title: "Ganancia neta", value: Cup(cents: profit).formatted, key: "profit") {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    metricBlock(title: "Costo", amount: Cup(cents: cost).formatted)
                    metricBlock(title: "Ganancia", amount: Cup(cents: profit).formatted)
                }
                if revenue > 0 {
                    let margin = Double(profit) / Double(revenue) * 100
                    Text("Margen \(String(format: "%.1f", margin)) %")
                        .font(.shalev(12, weight: .medium))
                        .foregroundStyle(Color.shalevSecondaryText)
                }
                Text("\(periodSales.count) operaciones registradas en \(period.rawValue.lowercased()).")
                    .font(.shalev(12))
                    .foregroundStyle(Color.shalevSecondaryText)
            }
        }
    }

    private func metricBlock(title: String, amount: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
            Text(amount)
                .font(.shalev(18, weight: .bold))
                .foregroundStyle(Color.shalevText)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var inventoryAccordion: some View {
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
                    .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
            }
        }
    }

    private var paymentAccordion: some View {
        let cash = periodSales.filter { $0.paymentMethod == .cash }
        let transfer = periodSales.filter { $0.paymentMethod == .transfer }
        return accordion(title: "Métodos de pago",
                         value: "\(cash.count) en efectivo · \(transfer.count) transferencias",
                         key: "payments") {
            VStack(spacing: 8) {
                summaryRow("Efectivo", amount: Cup(cents: cash.reduce(0) { $0 + $1.totalCents }).formatted)
                summaryRow("Transferencias", amount: Cup(cents: transfer.reduce(0) { $0 + $1.totalCents }).formatted)
            }
        }
    }

    @ViewBuilder
    private var topAccordions: some View {
        if !rankedItems.isEmpty {
            topAccordion(title: "Más vendido", value: topSold.first.map { "\($0.name)" } ?? "Sin ventas",
                         key: "top_sold", items: topSold, detail: { "\($0.units) u · \(Cup(cents: $0.cents).formatted)" },
                         amount: { Cup(cents: $0.cents).formatted })
            topAccordion(title: "Lento movimiento", value: slowMoving.first.map { "\($0.name)" } ?? "Sin ventas",
                         key: "top_slow", items: slowMoving, detail: { "\($0.units) u vendidas" },
                         amount: { Cup(cents: $0.cents).formatted })
            topAccordion(title: "Rentabilidad", value: topProfit.first.map { "\($0.name)" } ?? "Sin ventas",
                         key: "top_profit", items: topProfit, detail: { "Ganancia \(Cup(cents: $0.profit).formatted)" },
                         amount: { Cup(cents: $0.profit).formatted })
        }
    }

    private func topAccordion(title: String, value: String, key: String, items: [RankedItem],
                              detail: @escaping (RankedItem) -> String,
                              amount: @escaping (RankedItem) -> String) -> some View {
        accordion(title: title, value: value, key: key) {
            VStack(alignment: .leading, spacing: 10) {
                Text("En \(period.rawValue.lowercased())")
                    .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
                ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                    HStack(spacing: 11) {
                        medal(position: index + 1)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.name).font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevText).lineLimit(1)
                            Text(detail(item)).font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                        }
                        Spacer(minLength: 6)
                        Text(amount(item))
                            .font(.shalev(12, weight: .semibold))
                            .foregroundStyle(Color.shalevText)
                            .lineLimit(1).minimumScaleFactor(0.8)
                    }
                }
            }
        }
    }

    private func medal(position: Int) -> some View {
        var background = Color(red: 0.788, green: 0.541, blue: 0.294)
        var foreground = Color(red: 0.169, green: 0.086, blue: 0)
        if position == 1 {
            background = Color(red: 0.949, green: 0.788, blue: 0.298)
            foreground = Color(red: 0.239, green: 0.180, blue: 0)
        } else if position == 2 {
            background = Color(red: 0.773, green: 0.800, blue: 0.839)
            foreground = Color(red: 0.122, green: 0.161, blue: 0.200)
        }
        return Text("\(position)")
            .font(.shalev(12, weight: .bold))
            .foregroundStyle(foreground)
            .frame(width: 30, height: 30)
            .background(background, in: Circle())
            .accessibilityLabel(position == 1 ? "Primer puesto" : position == 2 ? "Segundo puesto" : "Tercer puesto")
    }

    private func accordion<Content: View>(title: String, value: String, key: String,
                                           @ViewBuilder content: @escaping () -> Content) -> some View {
        DisclosureGroup(isExpanded: binding(for: key)) {
            content().padding(.top, 12)
        } label: {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 5) {
                    Text(title).font(.shalev(15, weight: .semibold)).foregroundStyle(Color.shalevText)
                    Text(value).font(.shalev(13, weight: .semibold)).foregroundStyle(Color.shalevSecondaryText)
                        .lineLimit(2).minimumScaleFactor(0.82)
                }
                Spacer(minLength: 10)
                Image(systemName: expandedCards.contains(key) ? "chevron.up" : "chevron.down")
                    .font(.shalev(12, weight: .bold)).foregroundStyle(Color.shalevSecondaryText)
            }
            .contentShape(Rectangle())
        }
        .tint(Color.shalevPrimary)
        .font(.shalev(14))
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

    private func summaryRow(_ title: String, amount: String) -> some View {
        HStack {
            Text(title).font(.shalev(13)).foregroundStyle(Color.shalevSecondaryText)
            Spacer()
            Text(amount).font(.shalev(13, weight: .semibold)).foregroundStyle(Color.shalevText)
        }
    }

    // MARK: - Datos derivados

    private var rankedItems: [RankedItem] {
        var totals: [String: (units: Int, cents: Int64, profit: Int64)] = [:]
        for sale in periodSales {
            for line in sale.lines {
                var entry = totals[line.name] ?? (units: 0, cents: 0, profit: 0)
                entry.units += line.quantity
                entry.cents += line.subtotalCents
                entry.profit += line.subtotalCents - line.costCents
                totals[line.name] = entry
            }
        }
        return totals.map { RankedItem(name: $0.key, units: $0.value.units, cents: $0.value.cents, profit: $0.value.profit) }
    }

    private var topSold: [RankedItem] {
        Array(rankedItems.sorted {
            if $0.units != $1.units { return $0.units > $1.units }
            return $0.cents > $1.cents
        }.prefix(3))
    }

    private var slowMoving: [RankedItem] {
        Array(rankedItems.sorted {
            if $0.units != $1.units { return $0.units < $1.units }
            return $0.cents < $1.cents
        }.prefix(3))
    }

    private var topProfit: [RankedItem] {
        Array(rankedItems.sorted { $0.profit > $1.profit }.prefix(3))
    }

    private var paymentSummary: String {
        let phone = store.data.settings.profile.paymentPhone?.number ?? ""
        let card = store.data.settings.profile.paymentCard?.number.filter(\.isNumber) ?? ""
        var lines: [String] = []
        if !phone.isEmpty { lines.append("Tel. \(phone)") }
        if card.count >= 4 { lines.append("Cuenta •••• \(card.suffix(4))") }
        return lines.isEmpty ? "Sin configurar" : lines.joined(separator: "\n")
    }

    private var priceSummary: String {
        let count = store.data.settings.priceAdjustments.filter(\.active).count
        return count == 0 ? "Sin ajustes activos" : "\(count) ajuste\(count == 1 ? "" : "s") activo\(count == 1 ? "" : "s")"
    }
}

private struct RankedItem: Identifiable {
    var name: String
    var units: Int
    var cents: Int64
    var profit: Int64
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
    @State private var closingCount: ShiftClosingCount?
    @State private var billCounts: [Int: String] = [:]
    @State private var otherCash = ""
    @State private var countedStock: [UUID: String] = [:]
    @State private var inventoryCountSkipped = false
    @State private var inventoryConfirmed = false
    @State private var discrepancyReason = ""
    @State private var errorMessage: String?

    private var expectedCash: Int64 { store.openShift.map { store.expectedCashCents(for: $0.id) } ?? 0 }
    private var validShiftSales: [Sale] {
        guard let shiftID = store.openShift?.id else { return [] }
        return store.data.sales.filter { $0.shiftID == shiftID && !$0.isCancelled }
    }
    private var salesTotal: Int64 { validShiftSales.reduce(0) { $0 + $1.totalCents } }
    private var cashSalesTotal: Int64 { validShiftSales.filter { $0.paymentMethod == .cash }.reduce(0) { $0 + $1.totalCents } }
    private var transferSalesTotal: Int64 { validShiftSales.filter { $0.paymentMethod == .transfer }.reduce(0) { $0 + $1.totalCents } }
    private var profitTotal: Int64 { validShiftSales.reduce(0) { $0 + $1.profitCents } }
    private var preview: ShiftClosingCount? { closingCount }
    private var countedCash: Int64? {
        var total = otherCash.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Int64(0) : (Cup.parse(otherCash)?.cents ?? -1)
        guard total >= 0 else { return nil }
        for denomination in ShiftClosingCount.denominations {
            let text = billCounts[denomination] ?? ""
            guard !text.isEmpty else { continue }
            guard let count = FixedNumber.parse(text, multiplier: 1) else { return nil }
            let (units, overflow) = count.multipliedReportingOverflow(by: Int64(denomination) * 100)
            guard !overflow else { return nil }
            let (newTotal, additionOverflow) = total.addingReportingOverflow(units)
            guard !additionOverflow else { return nil }
            total = newTotal
        }
        return total
    }
    private var cashDifference: Int64? { countedCash.map { $0 - expectedCash } }
    private var movedArticles: [ShiftInventoryCount] { preview?.inventory ?? [] }
    private var inventoryInputsValid: Bool {
        movedArticles.allSatisfy { FixedNumber.parse(countedStock[$0.productID] ?? "", multiplier: 1_000) != nil }
    }
    private var inventoryDifferences: [ShiftInventoryCount] {
        movedArticles.filter { line in
            guard let counted = FixedNumber.parse(countedStock[line.productID] ?? "", multiplier: 1_000) else { return true }
            return counted != line.expectedMilli
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
                    ShalevStepper(current: stageNumber, total: 3, title: stageTitle,
                                  onBack: stageBack, onNext: stageNext)
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
            .onAppear { if closingCount == nil { closingCount = try? store.prepareClosingCount() } }
        }
    }

    private var stageNumber: Int {
        switch stage {
        case .cash: 1
        case .inventory: 2
        case .review: 3
        }
    }

    private var stageTitle: String {
        switch stage {
        case .cash: "Cuenta el efectivo"
        case .inventory: "Conteo de inventario"
        case .review: "Revisar cierre"
        }
    }

    private var stageBack: (() -> Void)? {
        switch stage {
        case .cash:
            return nil
        case .inventory:
            return { stage = .cash }
        case .review:
            return { stage = .inventory }
        }
    }

    private var stageNext: (() -> Void)? {
        switch stage {
        case .cash:
            guard countedCash != nil else { return nil }
            return { stage = .inventory }
        case .inventory:
            guard canReviewInventory else { return nil }
            return { stage = .review }
        case .review:
            return nil
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
                        Text("Diferencia").font(.shalev(13)).foregroundStyle(Color.shalevSecondaryText)
                        Spacer()
                        Text(cashDifferenceText).font(.shalev(14, weight: .semibold))
                            .foregroundStyle(cashDifference == 0 ? Color.shalevSuccess : Color.shalevSecondary)
                    }
                }
            }
            ShalevCard {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Billetes").font(.shalev(15, weight: .semibold)).foregroundStyle(Color.shalevText)
                    ForEach(ShiftClosingCount.denominations, id: \.self) { denomination in
                        HStack {
                            Text("\(denomination) CUP").font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevText)
                            Spacer()
                            TextField("0", text: billBinding(denomination))
                                .keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                                .frame(width: 90).padding(9)
                                .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 10))
                        }
                    }
                    HStack(spacing: 10) {
                        Text("¿No quieres contar billete por billete?").font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                        Spacer(minLength: 6)
                        Button {
                            fillToMatchExpected()
                        } label: {
                            Text("Cuadra").font(.shalev(12, weight: .semibold))
                        }
                        .buttonStyle(ShalevTonalButtonStyle())
                        .accessibilityHint("Rellena el efectivo contado con el esperado")
                    }
                    Divider()
                    HStack {
                        Text("Otro efectivo (CUP)").font(.shalev(13)).foregroundStyle(Color.shalevText)
                        Spacer()
                        TextField("0.00", text: $otherCash)
                            .keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                            .frame(width: 110).padding(9)
                            .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 10))
                    }
                }
            }
            if countedCash == nil {
                Text("Escribe el conteo (o toca Cuadra) para continuar.")
                    .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                    .multilineTextAlignment(.center).frame(maxWidth: .infinity)
            }
        }
    }

    private var inventoryStep: some View {
        VStack(spacing: 14) {
            ShalevCard {
                VStack(alignment: .leading, spacing: 10) {
                    Toggle("Omitir el conteo de inventario", isOn: $inventoryCountSkipped).tint(Color.shalevPrimary)
                    Text("Si lo omites, no se ajustará ninguna existencia. El cierre conservará esta decisión.")
                        .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                }
            }
            if !inventoryCountSkipped {
                ShalevCard {
                    VStack(alignment: .leading, spacing: 11) {
                        Text("Existencias contadas").font(.shalev(15, weight: .semibold)).foregroundStyle(Color.shalevText)
                        if movedArticles.isEmpty {
                            Text("No hubo movimientos de inventario en este turno.").font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
                        }
                        ForEach(articleCategories, id: \.self) { category in
                            Text(category.isEmpty ? "Sin categoría" : category)
                                .font(.shalev(12, weight: .semibold))
                                .foregroundStyle(Color.shalevPrimary)
                                .padding(.top, 4)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .accessibilityAddTraits(.isHeader)
                            ForEach(movedArticles.filter { $0.category == category }) { line in
                                VStack(alignment: .leading, spacing: 6) {
                                    Text(line.productName).font(.shalev(13, weight: .semibold)).foregroundStyle(Color.shalevText)
                                    HStack {
                                        Text("Esperado: \(FixedNumber.formatMilli(line.expectedMilli)) \(line.unit)")
                                            .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                                        Spacer()
                                        TextField("Contado", text: stockBinding(line.productID))
                                            .keyboardType(.decimalPad).multilineTextAlignment(.trailing)
                                            .frame(width: 105).padding(8)
                                            .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 10))
                                            .accessibilityLabel("Contado de \(line.productName)")
                                    }
                                }
                            }
                        }
                    }
                }
                if !inventoryDifferences.isEmpty {
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 10) {
                            Label("Revisa las diferencias", systemImage: "exclamationmark.triangle.fill")
                                .font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevSecondary)
                            ForEach(inventoryDifferences) { line in
                                Text("\(line.productName): esperado \(FixedNumber.formatMilli(line.expectedMilli)), contado \(countedStock[line.productID] ?? "—")")
                                    .font(.shalev(12)).foregroundStyle(Color.shalevText)
                            }
                            Toggle("Confirmo que revisé las diferencias", isOn: $inventoryConfirmed).tint(Color.shalevPrimary)
                            TextField("Motivo del ajuste (3–160 caracteres)", text: $discrepancyReason, axis: .vertical)
                                .lineLimit(2...4).padding(11)
                                .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
                        }
                    }
                }
            }
            if !inventoryCountSkipped && !canReviewInventory {
                Text(inventoryInputsValid
                     ? "Confirma que revisaste las diferencias y escribe el motivo para continuar."
                     : "Completa todas las existencias contadas para continuar.")
                    .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                    .multilineTextAlignment(.center).frame(maxWidth: .infinity)
            }
        }
    }

    private var reviewStep: some View {
        VStack(spacing: 14) {
            ShalevCard {
                VStack(spacing: 9) {
                    closingRow("Ventas", "\(validShiftSales.count)")
                    closingRow("Total de ventas", Cup(cents: salesTotal).formatted)
                    closingRow("Efectivo / transferencias", "\(Cup(cents: cashSalesTotal).formatted) / \(Cup(cents: transferSalesTotal).formatted)")
                    closingRow("Ganancia", Cup(cents: profitTotal).formatted)
                    closingRow("Efectivo esperado", Cup(cents: expectedCash).formatted)
                    closingRow("Efectivo contado", countedCash.map { Cup(cents: $0).formatted } ?? "—")
                    closingRow("Diferencia de caja", cashDifferenceText)
                }
            }
            ShalevCard {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Inventario").font(.shalev(15, weight: .semibold)).foregroundStyle(Color.shalevText)
                    if inventoryCountSkipped {
                        Text("Conteo omitido: no se ajustarán existencias.").font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
                    } else if inventoryDifferences.isEmpty {
                        Text("Sin diferencias por ajustar.").font(.shalev(12)).foregroundStyle(Color.shalevSuccess)
                    } else {
                        Text("\(inventoryDifferences.count) artículo(s) con ajuste.").font(.shalev(12, weight: .semibold)).foregroundStyle(Color.shalevSecondary)
                        Text(discrepancyReason).font(.shalev(12)).foregroundStyle(Color.shalevText)
                    }
                }
            }
            Text("Al cerrar no podrás registrar más operaciones en este turno. El resumen quedará congelado.")
                .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText).multilineTextAlignment(.center)
            Button(action: finish) { Label("Cerrar turno", systemImage: "checkmark.circle.fill") }
                .buttonStyle(ShalevPrimaryButtonStyle())
        }
    }

    private var cashDifferenceText: String {
        guard let difference = cashDifference else { return "—" }
        if difference == 0 { return "Cuadra" }
        return "\(difference > 0 ? "+" : "−")\(Cup(cents: abs(difference)).formatted)"
    }

    private func closingRow(_ title: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(title).font(.shalev(13)).foregroundStyle(Color.shalevSecondaryText)
            Spacer()
            Text(value).font(.shalev(13, weight: .semibold)).foregroundStyle(Color.shalevText).multilineTextAlignment(.trailing)
        }
    }

    private var articleCategories: [String] {
        var seen = Set<String>()
        return movedArticles.compactMap { seen.insert($0.category).inserted ? $0.category : nil }
    }

    /// «Cuadra»: deja el contado igual al esperado repartiendo la diferencia en «Otro efectivo».
    private func fillToMatchExpected() {
        var billsTotal: Int64 = 0
        for denomination in ShiftClosingCount.denominations {
            let text = billCounts[denomination] ?? ""
            guard let count = FixedNumber.parse(text, multiplier: 1) else { continue }
            billsTotal += count * Int64(denomination) * 100
        }
        let remaining = expectedCash - billsTotal
        otherCash = remaining > 0 ? Cup(cents: remaining).inputValue : "0"
    }

    private func billBinding(_ denomination: Int) -> Binding<String> {
        Binding(get: { billCounts[denomination] ?? "" }, set: { billCounts[denomination] = $0 })
    }

    private func stockBinding(_ productID: UUID) -> Binding<String> {
        Binding(get: { countedStock[productID] ?? "" }, set: { countedStock[productID] = $0 })
    }

    private func finish() {
        guard var count = preview else { errorMessage = "No hay un turno abierto para cerrar."; return }
        var cashCounts: [Int: Int] = [:]
        for denomination in ShiftClosingCount.denominations {
            let text = billCounts[denomination] ?? ""
            if !text.isEmpty {
                guard let parsed = FixedNumber.parse(text, multiplier: 1),
                      let value = Int(exactly: parsed) else { errorMessage = "Revisa el conteo de billetes."; return }
                cashCounts[denomination] = value
            }
        }
        count.bills = cashCounts
        count.otherCashCents = Cup.parse(otherCash)?.cents ?? 0
        count.inventoryCountSkipped = inventoryCountSkipped
        count.reason = discrepancyReason
        if inventoryCountSkipped {
            count.inventory = []
        } else {
            for index in count.inventory.indices {
                let productID = count.inventory[index].productID
                guard let value = FixedNumber.parse(countedStock[productID] ?? "", multiplier: 1_000) else {
                    errorMessage = "Completa todas las existencias contadas."; return
                }
                count.inventory[index].countedMilli = value
            }
        }
        do {
            try store.closeShift(count: count)
            dismiss()
        } catch { errorMessage = error.localizedDescription }
    }
}

@MainActor
private struct ShiftOperationSheet: View {
    @ObservedObject var store: ShalevStore
    @ObservedObject var sync: SyncManager
    let action: ShiftSheetAction
    @Environment(\.dismiss) private var dismiss
    @State private var amountText = ""
    @State private var reason = ""
    @State private var movementKind: CashMovementKind = .income
    @State private var errorMessage: String?
    @State private var working = false

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
                            .font(.shalev(14))
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
                                    .font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                                TextField("0.00", text: $amountText)
                                    .keyboardType(.decimalPad)
                                    .font(.shalev(18, weight: .semibold))
                                    .padding(13)
                                    .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 12))
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.shalevOutlineVariant, lineWidth: 1))
                                Text("Importes en CUP").font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                            }
                            if action == .cashMovement {
                                VStack(alignment: .leading, spacing: 6) {
                                    Text("Motivo").font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                                    TextField("Escribe un motivo", text: $reason, axis: .vertical)
                                        .lineLimit(2...4)
                                        .padding(12)
                                        .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 12))
                                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.shalevOutlineVariant, lineWidth: 1))
                                }
                            }
                            Button(action == .open ? "Abrir turno" : action == .close ? "Cerrar turno" : "Guardar movimiento") { submit() }
                                .buttonStyle(ShalevPrimaryButtonStyle())
                                .disabled(working)
                            if working { ProgressView("Sincroniza antes de abrir…").frame(maxWidth: .infinity) }
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
        if action == .open {
            working = true
            Task {
                do {
                    try await sync.prepareToOpenShift()
                    try store.openShift(openingCashCents: amount)
                    dismiss()
                } catch { errorMessage = error.localizedDescription }
                working = false
            }
            return
        }
        do {
            switch action {
            case .open: break
            case .close: try store.closeShift(countedCashCents: amount)
            case .cashMovement: try store.recordCashMovement(kind: movementKind, amountCents: amount, reason: reason)
            }
            dismiss()
        } catch { errorMessage = error.localizedDescription }
    }
}
