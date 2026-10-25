import SwiftUI

private enum RecordSheet: Identifiable {
    case sale(Sale)
    case shift(Shift)
    case customer(Customer)
    var id: String {
        switch self {
        case .sale(let sale): "sale-\(sale.id.uuidString)"
        case .shift(let shift): "shift-\(shift.id.uuidString)"
        case .customer(let customer): "customer-\(customer.id.uuidString)"
        }
    }
}

private struct MovementRecord: Identifiable {
    var id: String
    var date: Date
    var title: String
    var detail: String
    var amountCents: Int64?
    var isNegative: Bool
}

@MainActor
struct RecordsView: View {
    @ObservedObject var store: ShalevStore
    var canExport = true
    var canCorrectSales = true
    var canManageCash = true
    var canViewInventory = true
    var canViewSales = true
    var canViewServices = true
    @State private var selectedTab: RecordsTab = .sales
    @State private var search = ""
    @State private var sheet: RecordSheet?
    @State private var shareFileURL: URL?
    @State private var showShareSheet = false
    @State private var exportError: String?

    private var sales: [Sale] {
        store.data.sales.filter { sale in
            guard sale.isService ? canViewServices : canViewSales else { return false }
            return switch selectedTab {
            case .sales: !sale.isService
            case .services: sale.isService
            case .transfers: sale.paymentMethod == .transfer
            default: false
            }
        }.filter { sale in
            search.isEmpty || sale.lines.contains(where: { $0.name.localizedCaseInsensitiveContains(search) })
                || sale.customer?.name.localizedCaseInsensitiveContains(search) == true
                || sale.transactionNumber.localizedCaseInsensitiveContains(search)
                || Cup(cents: sale.totalCents).formatted.localizedCaseInsensitiveContains(search)
        }
        .sorted { $0.date > $1.date }
    }

    private var movements: [MovementRecord] {
        let inventory = canViewInventory ? store.data.inventoryMovements.map { movement in
            MovementRecord(id: movement.id.uuidString, date: movement.date, title: movement.productName,
                           detail: "\(movement.reason) · \(FixedNumber.formatMilli(movement.deltaMilli))", amountCents: nil,
                           isNegative: movement.deltaMilli < 0)
        } : []
        let cash = canManageCash ? store.data.cashMovements.map { movement in
            MovementRecord(id: movement.id.uuidString, date: movement.date,
                           title: movement.kind.rawValue + " de caja",
                           detail: movement.reason,
                           amountCents: movement.amountCents,
                           isNegative: movement.kind == .expense)
        } : []
        return (inventory + cash).filter { search.isEmpty || $0.title.localizedCaseInsensitiveContains(search) || $0.detail.localizedCaseInsensitiveContains(search) }
            .sorted { $0.date > $1.date }
    }

    private var customers: [Customer] {
        store.data.customers.filter {
            search.isEmpty || $0.name.localizedCaseInsensitiveContains(search)
                || $0.identification.contains(search) || $0.phone.localizedCaseInsensitiveContains(search)
        }.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    private func customerSummary(_ customer: Customer) -> String {
        let purchases = store.purchases(for: customer)
        let total = purchases.reduce(Int64(0)) { $0 + $1.totalCents }
        return "\(purchases.count) compra\(purchases.count == 1 ? "" : "s") · \(Cup(cents: total).formatted)"
    }

    private var shifts: [Shift] {
        store.data.shifts.filter { shift in
            search.isEmpty || shift.openedAt.shalevDate.localizedCaseInsensitiveContains(search)
                || (shift.closedAt?.shalevDate.localizedCaseInsensitiveContains(search) ?? false)
        }.sorted { $0.openedAt > $1.openedAt }
    }

    private var salesTotal: Int64 { sales.filter { !$0.isCancelled }.reduce(0) { $0 + $1.totalCents } }
    private var visibleTabs: [RecordsTab] {
        RecordsTab.allCases.filter { tab in
            switch tab {
            case .sales: canViewSales
            case .services: canViewServices
            case .transfers: canViewSales || canViewServices
            case .movements: canViewInventory || canManageCash
            case .shifts: canManageCash
            case .customers: canViewSales || canViewServices
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                ShalevScreenTitle(title: "Registros", subtitle: "Operaciones, caja y turnos")
                Spacer(minLength: 0)
                if canExport && selectedTab != .customers {
                    Menu {
                        Button("Excel (XLSX)", systemImage: "tablecells") { export(.xlsx) }
                        Button("CSV", systemImage: "text.alignleft") { export(.csv) }
                        Button("PDF", systemImage: "doc.richtext") { export(.pdf) }
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 17, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                            .frame(width: 42, height: 42).background(Color.shalevPrimaryContainer, in: Circle())
                    }
                    .accessibilityLabel("Exportar registros")
                }
            }
            .padding(.horizontal, 17).padding(.top, 8).padding(.bottom, 12)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(visibleTabs) { tab in
                        ShalevFilterChip(title: tab.rawValue, selected: selectedTab == tab) {
                            selectedTab = tab
                            search = ""
                        }
                    }
                }
                .padding(.horizontal, 17).padding(.vertical, 5)
            }
            ShalevSearchField(text: $search, placeholder: "Buscar en registros")
                .padding(.horizontal, 17).padding(.top, 9)

            if [.sales, .services, .transfers].contains(selectedTab), !sales.isEmpty {
                HStack {
                    Text("\(sales.count) operaciones").font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                    Spacer()
                    Text(Cup(cents: salesTotal).formatted).font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText)
                }
                .padding(.horizontal, 20).padding(.vertical, 10)
            } else {
                Text(summaryCount)
                    .font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                    .padding(.vertical, 10)
            }

            ScrollView {
                LazyVStack(spacing: 8) {
                    switch selectedTab {
                    case .sales, .services, .transfers:
                        if sales.isEmpty {
                            emptyState
                        } else {
                            ForEach(sales) { sale in
                                Button { sheet = .sale(sale) } label: { SaleRecordRow(sale: sale) }
                                    .buttonStyle(.plain)
                            }
                        }
                    case .movements:
                        if movements.isEmpty { emptyState }
                        else { ForEach(movements) { MovementRecordRow(record: $0) } }
                    case .customers:
                        if customers.isEmpty { emptyState }
                        else {
                            ForEach(customers) { customer in
                                Button { sheet = .customer(customer) } label: {
                                    CustomerRecordRow(customer: customer, purchaseSummary: customerSummary(customer))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    case .shifts:
                        if shifts.isEmpty { emptyState }
                        else {
                            ForEach(shifts) { shift in
                                Button { sheet = .shift(shift) } label: { ShiftRecordRow(shift: shift, store: store) }
                                    .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(.horizontal, 16).padding(.top, 4).padding(.bottom, 18)
            }
        }
        .background(Color.shalevBackground)
        .onAppear { if !visibleTabs.contains(selectedTab) { selectedTab = visibleTabs.first ?? .sales } }
        .onChange(of: visibleTabs) { _, tabs in
            if !tabs.contains(selectedTab) { selectedTab = tabs.first ?? .sales }
        }
        .sheet(item: $sheet) { route in
            switch route {
            case .sale(let sale): SaleDetailView(sale: sale, store: store, allowModifications: canCorrectSales, onDone: { sheet = nil })
            case .shift(let shift): ShiftDetailView(shift: shift, store: store)
            case .customer(let customer): CustomerDetailView(store: store, customerID: customer.id)
            }
        }
        .sheet(isPresented: $showShareSheet) {
            if let shareFileURL { ShareSheet(items: [shareFileURL]) }
        }
        .alert("No se pudo exportar", isPresented: Binding(get: { exportError != nil }, set: { if !$0 { exportError = nil } })) {
            Button("Entendido", role: .cancel) { exportError = nil }
        } message: { Text(exportError ?? "") }
    }

    private var exportReport: ExportReport {
        let business = store.data.settings.profile.businessName
        let subtitle = "\(business) · \(Date().shalevDate) · \(search.isEmpty ? "Todos los registros" : "Filtro: \(search)")"
        switch selectedTab {
        case .sales, .services, .transfers:
            return ExportReport(title: selectedTab.rawValue, subtitle: subtitle,
                                headers: ["Fecha", "Artículos", "Pago", "Total CUP", "Costo CUP", "Ganancia CUP", "Transacción", "Cliente", "Carné", "Teléfono", "Tarjeta de cobro", "Teléfono de cobro", "Vendedor", "Estado"],
                                rows: sales.map { sale in
                                    [sale.date.shalevDateTime,
                                     sale.lines.map { "\($0.quantity) × \($0.name)" }.joined(separator: ", "),
                                     sale.paymentMethod.rawValue, Cup(cents: sale.totalCents).inputValue,
                                     Cup(cents: sale.costCents).inputValue, Cup(cents: sale.profitCents).inputValue,
                                     sale.transactionNumber, sale.customer?.name ?? "", sale.customer?.identification ?? "",
                                     sale.customer?.phone ?? "", sale.paymentCard ?? "", sale.paymentPhone ?? "",
                                     sale.sellerName ?? "", sale.isCancelled ? "Anulada" : "Vigente"]
                                })
        case .movements:
            let headers = ["Fecha", "Tipo", "Detalle", "Variación", "Importe CUP"]
            let inventoryRows: [[String]] = canViewInventory ? store.data.inventoryMovements.filter {
                search.isEmpty || $0.productName.localizedCaseInsensitiveContains(search) || $0.reason.localizedCaseInsensitiveContains(search)
            }.map { [ $0.date.shalevDateTime, "Inventario", "\($0.productName) · \($0.reason)", FixedNumber.formatMilli($0.deltaMilli), "" ] } : []
            let cashRows: [[String]] = canManageCash ? store.data.cashMovements.filter {
                search.isEmpty || $0.reason.localizedCaseInsensitiveContains(search) || $0.kind.rawValue.localizedCaseInsensitiveContains(search)
            }.map { [ $0.date.shalevDateTime, "Caja · \($0.kind.rawValue)", $0.reason, "", Cup(cents: $0.amountCents).inputValue ] } : []
            return ExportReport(title: "Movimientos", subtitle: subtitle, headers: headers, rows: inventoryRows + cashRows)
        case .customers:
            return ExportReport(title: "Clientes fijos", subtitle: subtitle,
                                headers: ["Nombre", "Carné de identidad", "Teléfono", "Actualizado"],
                                rows: customers.map { [$0.name, $0.identification, $0.phone, $0.updatedAt.shalevDateTime] })
        case .shifts:
            return ExportReport(title: "Turnos", subtitle: subtitle,
                                headers: ["Apertura", "Cierre", "Fondo inicial CUP", "Esperado CUP", "Contado CUP", "Estado"],
                                rows: shifts.map { shift in
                                    [shift.openedAt.shalevDateTime, shift.closedAt?.shalevDateTime ?? "",
                                     Cup(cents: shift.openingCashCents).inputValue,
                                     Cup(cents: store.expectedCashCents(for: shift.id)).inputValue,
                                     shift.countedCashCents.map { Cup(cents: $0).inputValue } ?? "",
                                     shift.closedAt == nil ? "Abierto" : "Cerrado"]
                                })
        }
    }

    private func export(_ format: ExportFormat) {
        do {
            shareFileURL = try ShalevExporter.write(exportReport, format: format)
            showShareSheet = true
        } catch {
            exportError = "No se pudo crear el archivo. Comprueba el espacio libre e inténtalo de nuevo."
        }
    }

    private var summaryCount: String {
        switch selectedTab {
        case .sales, .services, .transfers: "\(sales.count) operaciones"
        case .movements: "\(movements.count) movimientos"
        case .customers: "\(customers.count) clientes fijos"
        case .shifts: "\(shifts.count) turnos"
        }
    }

    @ViewBuilder private var emptyState: some View {
        ShalevEmptyState(symbol: selectedTab == .customers ? "person.2" : selectedTab == .shifts ? "clock" : "doc.text.magnifyingglass",
                         title: search.isEmpty ? "Sin registros todavía" : "No hay coincidencias",
                         detail: search.isEmpty ? "Las ventas y los movimientos de caja aparecerán aquí." : "Prueba con otra búsqueda.")
            .padding(.horizontal, 12)
    }
}

private struct SaleRecordRow: View {
    let sale: Sale

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: sale.paymentMethod.symbol)
                .font(.system(size: 17, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                .frame(width: 44, height: 44).background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 4) {
                Text(sale.isService ? "Servicio · \(sale.lines.first?.name ?? "")" : (sale.lines.count == 1 ? sale.lines[0].name : "Venta · \(sale.lines.count) artículos"))
                    .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText).lineLimit(1)
                HStack(spacing: 5) {
                    Text(sale.date.shalevDateTime)
                    Text("·")
                    Text(sale.paymentMethod.rawValue)
                }
                .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
            }
            Spacer(minLength: 6)
            VStack(alignment: .trailing, spacing: 4) {
                Text(Cup(cents: sale.totalCents).formatted).font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText)
                if sale.isCancelled {
                    Text("Anulada").font(.system(size: 10, weight: .semibold)).foregroundStyle(Color.shalevError)
                } else if sale.paymentMethod == .transfer && !sale.transactionNumber.isEmpty {
                    Text("N.º \(sale.transactionNumber)").font(.system(size: 10)).foregroundStyle(Color.shalevSecondaryText).lineLimit(1)
                }
            }
        }
        .padding(12)
        .background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.shalevOutlineVariant.opacity(0.55), lineWidth: 0.6))
    }
}

private struct MovementRecordRow: View {
    let record: MovementRecord
    var body: some View {
        HStack(spacing: 11) {
            Image(systemName: record.amountCents == nil ? "shippingbox" : "arrow.left.arrow.right")
                .font(.system(size: 15, weight: .semibold)).foregroundStyle(record.isNegative ? Color.shalevSecondary : Color.shalevPrimary)
                .frame(width: 40, height: 40).background(Color.shalevSurfaceVariant, in: Circle())
            VStack(alignment: .leading, spacing: 3) {
                Text(record.title).font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText).lineLimit(1)
                Text("\(record.detail) · \(record.date.shalevDateTime)").font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText).lineLimit(2)
            }
            Spacer(minLength: 4)
            if let amount = record.amountCents {
                Text("\(record.isNegative ? "−" : "+")\(Cup(cents: amount).formatted)")
                    .font(.system(size: 11, weight: .semibold)).foregroundStyle(record.isNegative ? Color.shalevSecondary : Color.shalevSuccess)
                    .lineLimit(1).minimumScaleFactor(0.75)
            }
        }
        .padding(12).background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 15, style: .continuous))
    }
}

private struct CustomerRecordRow: View {
    let customer: Customer
    let purchaseSummary: String
    var body: some View {
        HStack(spacing: 11) {
            Image(systemName: "person.crop.circle.fill").font(.system(size: 23)).foregroundStyle(Color.shalevPrimary)
            VStack(alignment: .leading, spacing: 4) {
                Text(customer.name).font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
                Text(maskedID.isEmpty ? customer.phone : "CI · \(maskedID)")
                    .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                Text(purchaseSummary).font(.system(size: 11, weight: .medium)).foregroundStyle(Color.shalevPrimary)
            }
            Spacer()
            if !customer.phone.isEmpty { Image(systemName: "phone.fill").font(.system(size: 12)).foregroundStyle(Color.shalevTertiary) }
        }
        .padding(13).background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 15, style: .continuous))
    }
    private var maskedID: String {
        guard customer.identification.count > 3 else { return customer.identification }
        return "••••••••\(customer.identification.suffix(3))"
    }
}

@MainActor
private struct CustomerDetailView: View {
    @ObservedObject var store: ShalevStore
    let customerID: UUID
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var identification: String
    @State private var phone: String
    @State private var errorMessage: String?
    @State private var confirmingRemoval = false

    init(store: ShalevStore, customerID: UUID) {
        self.store = store
        self.customerID = customerID
        let customer = store.data.customers.first(where: { $0.id == customerID })
        _name = State(initialValue: customer?.name ?? "")
        _identification = State(initialValue: customer?.identification ?? "")
        _phone = State(initialValue: customer?.phone ?? "")
    }

    private var purchases: [Sale] {
        guard let customer = store.data.customers.first(where: { $0.id == customerID }) else { return [] }
        return store.purchases(for: customer)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    ShalevScreenTitle(title: "Cliente fijo", subtitle: "Editar el carné también corrige sus compras anteriores.")
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 10) {
                            TextField("Nombre y apellidos", text: $name)
                                .textContentType(.name).textFieldStyle(SaleEditFieldStyle())
                            TextField("Carné cubano de 11 dígitos", text: $identification)
                                .keyboardType(.numberPad).textFieldStyle(SaleEditFieldStyle())
                                .onChange(of: identification) { _, value in
                                    identification = String(value.filter(\.isNumber).prefix(11))
                                }
                            TextField("Teléfono", text: $phone)
                                .keyboardType(.phonePad).textFieldStyle(SaleEditFieldStyle())
                            Button { save() } label: { Label("Guardar cambios", systemImage: "checkmark") }
                                .buttonStyle(ShalevPrimaryButtonStyle())
                        }
                    }
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Compras (\(purchases.count))")
                                .font(.system(size: 16, weight: .semibold)).foregroundStyle(Color.shalevText)
                            if purchases.isEmpty {
                                Text("Este cliente todavía no tiene compras.").font(.system(size: 13)).foregroundStyle(Color.shalevSecondaryText)
                            } else {
                                ForEach(purchases) { sale in
                                    HStack(alignment: .top) {
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(sale.date.shalevDateTime).font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                                            Text(sale.lines.map(\.name).joined(separator: ", "))
                                                .font(.system(size: 13)).foregroundStyle(Color.shalevText).lineLimit(2)
                                            if sale.isCancelled { Text("Anulada").font(.system(size: 11, weight: .semibold)).foregroundStyle(Color.shalevError) }
                                        }
                                        Spacer()
                                        Text(Cup(cents: sale.totalCents).formatted).font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.shalevText)
                                    }
                                    if sale.id != purchases.last?.id { Divider() }
                                }
                            }
                        }
                    }
                    Button(role: .destructive) { confirmingRemoval = true } label: {
                        Label("Quitar de clientes fijos", systemImage: "person.crop.circle.badge.xmark")
                    }
                    .buttonStyle(ShalevTonalButtonStyle())
                    Text("Las compras permanecen en Registros, pero dejarán de aparecer en esta ficha.")
                        .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText).multilineTextAlignment(.center)
                }
                .padding(16)
            }
            .background(Color.shalevBackground)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Cerrar") { dismiss() } } }
            .alert("Quitar cliente fijo", isPresented: $confirmingRemoval) {
                Button("Cancelar", role: .cancel) {}
                Button("Quitar", role: .destructive) {
                    do { try store.removeFixedCustomer(customerID); dismiss() }
                    catch { errorMessage = error.localizedDescription }
                }
            } message: { Text("No se borrarán las compras registradas.") }
            .alert("No se pudieron guardar los cambios", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Entendido", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
    }

    private func save() {
        guard var customer = store.data.customers.first(where: { $0.id == customerID }) else { return }
        customer.name = name
        customer.identification = identification
        customer.phone = phone
        do { try store.updateCustomer(customer) }
        catch { errorMessage = error.localizedDescription }
    }
}

@MainActor
private struct ShiftRecordRow: View {
    let shift: Shift
    @ObservedObject var store: ShalevStore
    var body: some View {
        HStack(spacing: 11) {
            Image(systemName: shift.closedAt == nil ? "lock.open.fill" : "checkmark.circle.fill")
                .font(.system(size: 16, weight: .semibold)).foregroundStyle(shift.closedAt == nil ? Color.shalevSecondary : Color.shalevSuccess)
                .frame(width: 42, height: 42).background(Color.shalevSurfaceVariant, in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text("Turno del \(shift.openedAt.shalevDate)").font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
                Text(shift.closedAt.map { "Cerrado \($0.shalevDateTime)" } ?? "En curso · abierto \(shift.openedAt.shalevDateTime)")
                    .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText).lineLimit(1)
            }
            Spacer()
            Text(Cup(cents: store.expectedCashCents(for: shift.id)).formatted)
                .font(.system(size: 11, weight: .semibold)).foregroundStyle(Color.shalevText).lineLimit(1).minimumScaleFactor(0.75)
        }
        .padding(12).background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 15, style: .continuous))
    }
}

@MainActor
private struct SaleDetailView: View {
    let sale: Sale
    @ObservedObject var store: ShalevStore
    let allowModifications: Bool
    let onDone: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var showCancelDialog = false
    @State private var showEditSheet = false
    @State private var cancellationReason = ""
    @State private var actionError: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    ShalevScreenTitle(title: sale.isService ? "Detalle del servicio" : "Detalle de la venta",
                                     subtitle: sale.date.shalevDateTime)
                    ShalevCard {
                        VStack(spacing: 11) {
                            ForEach(sale.lines) { line in
                                HStack(alignment: .top) {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(line.name).font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
                                        Text("\(line.quantity) × \(Cup(cents: line.unitPriceCents).formatted)")
                                            .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                                    }
                                    Spacer()
                                    Text(Cup(cents: line.subtotalCents).formatted).font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText)
                                }
                            }
                            Divider()
                            HStack { Text("Total").font(.system(size: 15, weight: .semibold)); Spacer(); Text(Cup(cents: sale.totalCents).formatted).font(.system(size: 17, weight: .bold)) }
                                .foregroundStyle(Color.shalevText)
                        }
                    }
                    ShalevCard {
                        VStack(spacing: 9) {
                            detailRow("Pago", sale.paymentMethod.rawValue)
                            detailRow("Costo", Cup(cents: sale.costCents).formatted)
                            detailRow("Ganancia", Cup(cents: sale.profitCents).formatted)
                            if !sale.transactionNumber.isEmpty { detailRow("N.º de transacción", sale.transactionNumber) }
                            if let customer = sale.customer {
                                detailRow("Cliente", customer.name)
                                detailRow("Carné", customer.identification)
                                detailRow("Teléfono", customer.phone)
                            }
                            if let card = sale.paymentCard { detailRow("Cobrado en la tarjeta", card) }
                            if let phone = sale.paymentPhone { detailRow("Teléfono de cobro", phone) }
                            if let seller = sale.sellerName, !seller.isEmpty { detailRow("Vendedor", seller) }
                            if let correction = sale.correctionOfSaleID { detailRow("Corrige", "Venta \(correction.uuidString.prefix(8))") }
                            if sale.customerIsFixed { ShalevStatusPill(title: "Cliente fijo", symbol: "person.crop.circle.fill") }
                            if sale.isCancelled {
                                ShalevStatusPill(title: "Anulada", color: .shalevError, symbol: "xmark.circle.fill")
                                if let date = sale.cancelledAt { detailRow("Anulada", date.shalevDateTime) }
                                if let reason = sale.cancellationReason { detailRow("Motivo", reason) }
                            }
                        }
                        .font(.system(size: 13)).foregroundStyle(Color.shalevText)
                    }
                    if canModify {
                        ShalevCard {
                            VStack(spacing: 10) {
                                Button { showEditSheet = true } label: { Label("Modificar venta", systemImage: "pencil") }
                                    .buttonStyle(ShalevTonalButtonStyle())
                                Button(role: .destructive) { showCancelDialog = true } label: {
                                    Label("Anular venta", systemImage: "xmark.circle.fill")
                                }
                                .buttonStyle(ShalevTonalButtonStyle())
                                if sale.paymentMethod == .transfer {
                                    Text("La transferencia debe devolverse por separado; Shalev no mueve dinero.")
                                        .font(.system(size: 11)).foregroundStyle(Color.shalevError)
                                }
                            }
                        }
                    }
                }
                .padding(18)
            }
            .background(Color.shalevBackground)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Cerrar") { dismiss() }.foregroundStyle(Color.shalevPrimary) } }
            .sheet(isPresented: $showEditSheet) {
                SaleEditView(sale: sale, store: store) {
                    showEditSheet = false
                    dismiss()
                    onDone()
                }
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
            }
            .alert("Anular venta", isPresented: $showCancelDialog) {
                TextField("Motivo (3–60 caracteres)", text: $cancellationReason)
                Button("Anular venta", role: .destructive) { cancelSale() }
                    .disabled(!(3...60).contains(cancellationReason.trimmingCharacters(in: .whitespacesAndNewlines).count))
                Button("Cancelar", role: .cancel) { cancellationReason = "" }
            } message: {
                Text(sale.paymentMethod == .transfer
                     ? "La venta se marcará como anulada y se devolverán las existencias. Devuelve el dinero por Transfermóvil."
                     : "La venta se marcará como anulada y se devolverán las existencias.")
            }
            .alert("No se pudo completar", isPresented: Binding(get: { actionError != nil }, set: { if !$0 { actionError = nil } })) {
                Button("Entendido", role: .cancel) { actionError = nil }
            } message: { Text(actionError ?? "") }
        }
    }

    private var canModify: Bool {
        allowModifications && !sale.isCancelled && store.data.shifts.contains(where: { $0.id == sale.shiftID && $0.closedAt == nil })
    }

    private func cancelSale() {
        do {
            try store.cancelSale(sale.id, reason: cancellationReason)
            cancellationReason = ""
            dismiss()
            onDone()
        } catch {
            actionError = error.localizedDescription
        }
    }

    private func detailRow(_ title: String, _ value: String) -> some View {
        HStack { Text(title).foregroundStyle(Color.shalevSecondaryText); Spacer(); Text(value).fontWeight(.semibold).multilineTextAlignment(.trailing) }
    }
}

@MainActor
private struct SaleEditView: View {
    let sale: Sale
    @ObservedObject var store: ShalevStore
    let onSaved: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var lines: [SaleLine]
    @State private var payment: PaymentMethod
    @State private var transactionNumber: String
    @State private var customerName: String
    @State private var customerID: String
    @State private var customerPhone: String
    @State private var customerIsFixed: Bool
    @State private var reason = ""
    @State private var errorMessage: String?

    init(sale: Sale, store: ShalevStore, onSaved: @escaping () -> Void) {
        self.sale = sale
        self.store = store
        self.onSaved = onSaved
        _lines = State(initialValue: sale.lines)
        _payment = State(initialValue: sale.paymentMethod)
        _transactionNumber = State(initialValue: sale.transactionNumber)
        _customerName = State(initialValue: sale.customer?.name ?? "")
        _customerID = State(initialValue: sale.customer?.identification ?? "")
        _customerPhone = State(initialValue: sale.customer?.phone ?? "")
        _customerIsFixed = State(initialValue: sale.customerIsFixed)
    }

    private var editedCustomer: Customer? {
        guard payment == .transfer, !customerName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return Customer(id: sale.customer?.id ?? UUID(), name: customerName, identification: customerID,
                        phone: customerPhone, updatedAt: sale.customer?.updatedAt ?? Date())
    }

    private var cart: [CartDraft] {
        lines.filter { $0.quantity > 0 }.map { CartDraft(sourceID: $0.sourceID, kind: $0.kind, quantity: $0.quantity) }
    }

    private var preview: SalePreview? { try? store.previewSale(cart, payment: payment, returningSaleID: sale.id) }
    private var editedTotal: Int64 {
        guard let preview else { return 0 }
        let oldLines = lines.filter { original in sale.lines.contains(where: { $0.id == original.id }) }
        let oldPrices = oldLines.reduce(into: [String: Int64]()) { result, line in
            result["\(line.kind.rawValue)-\(line.sourceID.uuidString)"] = line.unitPriceCents
        }
        return preview.lines.reduce(0) { sum, line in
            sum + (oldPrices["\(line.kind.rawValue)-\(line.sourceID.uuidString)"] ?? line.unitPriceCents) * Int64(line.quantity)
        }
    }
    private var validReason: Bool { (3...60).contains(reason.trimmingCharacters(in: .whitespacesAndNewlines).count) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 13) {
                    ShalevScreenTitle(title: "Modificar venta", subtitle: "La corrección quedará en el mismo turno; el registro original se conserva como anulado.")
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 11) {
                            ShalevSectionHeader(title: "Artículos")
                            ForEach(lines) { line in
                                HStack(spacing: 8) {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(line.name).font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText).lineLimit(2)
                                        Text("\(Cup(cents: line.unitPriceCents).formatted) por unidad")
                                            .font(.system(size: 10)).foregroundStyle(Color.shalevSecondaryText)
                                    }
                                    Spacer(minLength: 3)
                                    Stepper(value: quantityBinding(for: line), in: 0...100_000) {
                                        Text("\(line.quantity)").font(.system(size: 13, weight: .semibold)).monospacedDigit()
                                    }
                                    .labelsHidden()
                                    .frame(width: 108)
                                }
                                .padding(.vertical, 4)
                            }
                            Menu {
                                Menu("Agregar producto") {
                                    ForEach(store.activeProducts.filter { $0.priceCents > 0 }) { product in
                                        Button(product.name) { add(product) }
                                    }
                                }
                                Menu("Agregar servicio") {
                                    ForEach(store.activeServices.filter { $0.priceCents > 0 }) { service in
                                        Button(service.name) { add(service) }
                                    }
                                }
                            } label: {
                                Label("Agregar artículo o servicio", systemImage: "plus.circle.fill")
                                    .font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                                    .frame(maxWidth: .infinity, minHeight: 40)
                            }
                        }
                    }
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 11) {
                            Picker("Método de pago", selection: $payment) {
                                ForEach(PaymentMethod.allCases) { method in Text(method.rawValue).tag(method) }
                            }
                            .pickerStyle(.segmented)
                            if payment == .transfer {
                                TextField("N.º de transacción", text: $transactionNumber)
                                    .textInputAutocapitalization(.characters)
                                    .textFieldStyle(SaleEditFieldStyle())
                                Text("Datos del cliente").font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.shalevText)
                                TextField("Nombre y apellidos", text: $customerName)
                                    .textFieldStyle(SaleEditFieldStyle())
                                TextField("Carné cubano de 11 dígitos", text: $customerID)
                                    .keyboardType(.numberPad)
                                    .textFieldStyle(SaleEditFieldStyle())
                                TextField("Teléfono", text: $customerPhone)
                                    .keyboardType(.phonePad)
                                    .textFieldStyle(SaleEditFieldStyle())
                                Toggle("Cliente fijo", isOn: $customerIsFixed).tint(Color.shalevPrimary)
                            }
                            HStack {
                                Text("Total corregido").font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                                Spacer()
                                Text(Cup(cents: editedTotal).formatted).font(.system(size: 17, weight: .bold)).foregroundStyle(Color.shalevText)
                            }
                        }
                    }
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 7) {
                            Text("Motivo de la corrección (3–60 caracteres)")
                                .font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                            TextField("Ej. Cantidad incorrecta", text: $reason)
                                .textFieldStyle(SaleEditFieldStyle())
                                .onChange(of: reason) { _, value in if value.count > 60 { reason = String(value.prefix(60)) } }
                        }
                    }
                    if let errorMessage {
                        Text(errorMessage).font(.system(size: 12)).foregroundStyle(Color.shalevError)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    Button(action: save) { Label("Guardar corrección", systemImage: "checkmark.circle.fill") }
                        .buttonStyle(ShalevPrimaryButtonStyle())
                        .disabled(cart.isEmpty || preview == nil || !validReason)
                        .padding(.bottom, 20)
                }
                .padding(18)
            }
            .background(Color.shalevBackground)
            .navigationTitle("Editar venta")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Cancelar") { dismiss() }.foregroundStyle(Color.shalevSecondaryText) }
            }
            .alert("No se pudo modificar", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Entendido", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
    }

    private func quantityBinding(for line: SaleLine) -> Binding<Int> {
        Binding(get: { lines.first(where: { $0.id == line.id })?.quantity ?? 0 }, set: { quantity in
            guard let index = lines.firstIndex(where: { $0.id == line.id }) else { return }
            lines[index].quantity = quantity
        })
    }

    private func add(_ product: Product) {
        if let index = lines.firstIndex(where: { $0.kind == .product && $0.sourceID == product.id }) {
            lines[index].quantity += 1
        } else {
            lines.append(SaleLine(sourceID: product.id, kind: .product, name: product.name, category: product.category,
                                  quantity: 1, unitPriceCents: product.priceCents, unitCostCents: store.estimatedCostCents(for: product)))
        }
    }

    private func add(_ service: ServiceItem) {
        if let index = lines.firstIndex(where: { $0.kind == .service && $0.sourceID == service.id }) {
            lines[index].quantity += 1
        } else {
            lines.append(SaleLine(sourceID: service.id, kind: .service, name: service.name, category: service.type,
                                  quantity: 1, unitPriceCents: service.priceCents, unitCostCents: store.estimatedCostCents(for: service)))
        }
    }

    private func save() {
        guard preview != nil else { errorMessage = "Revisa las existencias y los artículos activos."; return }
        do {
            _ = try store.modifySale(sale.id, cart: cart, payment: payment, transactionNumber: transactionNumber,
                                     reason: reason, customer: editedCustomer,
                                     customerIsFixed: payment == .transfer && customerIsFixed)
            onSaved()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private struct SaleEditFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration.font(.system(size: 14)).padding(11)
            .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
    }
}

@MainActor
private struct ShiftDetailView: View {
    let shift: Shift
    @ObservedObject var store: ShalevStore
    @Environment(\.dismiss) private var dismiss

    private var shiftSales: [Sale] { store.data.sales.filter { $0.shiftID == shift.id && !$0.isCancelled } }
    private var cashSales: Int64 { shiftSales.filter { $0.paymentMethod == .cash }.reduce(0) { $0 + $1.totalCents } }
    private var cashMovementsTotal: Int64 {
        store.data.cashMovements.filter { $0.shiftID == shift.id }.reduce(Int64(0)) { total, movement in
            total + (movement.kind == .income ? movement.amountCents : -movement.amountCents)
        }
    }
    private var displayedExpectedCash: Int64 {
        guard let summary = shift.summary else { return store.expectedCashCents(for: shift.id) }
        return shift.openingCashCents + summary.cashCents + cashMovementsTotal
    }
    private var cashDifference: Int64? { shift.countedCashCents.map { $0 - displayedExpectedCash } }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    ShalevScreenTitle(title: "Detalle del turno", subtitle: "Abierto \(shift.openedAt.shalevDateTime)")
                    ShalevCard {
                        VStack(spacing: 9) {
                            detailRow("Estado", shift.closedAt == nil ? "Abierto" : "Cerrado")
                            detailRow("Fondo inicial", Cup(cents: shift.openingCashCents).formatted)
                            detailRow("Ventas en efectivo", Cup(cents: shift.summary?.cashCents ?? cashSales).formatted)
                            if let summary = shift.summary {
                                detailRow("Transferencias", Cup(cents: summary.transferCents).formatted)
                                detailRow("Total de ventas", "\(summary.saleCount) · \(Cup(cents: summary.totalCents).formatted)")
                                detailRow("Ganancia", Cup(cents: summary.profitCents).formatted)
                            }
                            detailRow("Efectivo esperado", Cup(cents: displayedExpectedCash).formatted)
                            if let counted = shift.countedCashCents { detailRow("Efectivo contado", Cup(cents: counted).formatted) }
                            if let difference = cashDifference { detailRow("Diferencia de caja", Cup(cents: difference).formatted) }
                            if let openedBy = shift.openedBy { detailRow("Abierto por", openedBy) }
                            if let closedBy = shift.closedBy { detailRow("Cerrado por", closedBy) }
                            if let closed = shift.closedAt { detailRow("Cerrado", closed.shalevDateTime) }
                        }
                        .font(.system(size: 13)).foregroundStyle(Color.shalevText)
                    }
                    if let count = shift.closingCount {
                        ShalevCard {
                            VStack(alignment: .leading, spacing: 8) {
                                ShalevSectionHeader(title: "Conciliación de inventario")
                                if count.inventoryCountSkipped == true {
                                    Text("Conteo omitido explícitamente; no se ajustaron existencias.")
                                        .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                                } else if count.inventory.allSatisfy({ $0.differenceMilli == 0 }) {
                                    Text("Sin diferencias de inventario.").font(.system(size: 12)).foregroundStyle(Color.shalevSuccess)
                                } else {
                                    ForEach(count.inventory.filter { $0.differenceMilli != 0 }) { discrepancy in
                                        Text("\(discrepancy.productName): \(FixedNumber.formatMilli(discrepancy.expectedMilli)) → \(FixedNumber.formatMilli(discrepancy.countedMilli))")
                                            .font(.system(size: 12)).foregroundStyle(Color.shalevText)
                                    }
                                    if !count.reason.isEmpty { Text("Motivo: \(count.reason)").font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText) }
                                }
                            }
                        }
                    }
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 10) {
                            ShalevSectionHeader(title: "Operaciones", trailing: "\(shiftSales.count)")
                            ForEach(shiftSales) { sale in
                                HStack {
                                    Text(sale.isService ? "Servicio" : "Venta").font(.system(size: 13)).foregroundStyle(Color.shalevSecondaryText)
                                    Spacer()
                                    Text(Cup(cents: sale.totalCents).formatted).font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText)
                                }
                            }
                        }
                    }
                }
                .padding(18)
            }
            .background(Color.shalevBackground)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Cerrar") { dismiss() }.foregroundStyle(Color.shalevPrimary) } }
        }
    }

    private func detailRow(_ title: String, _ value: String) -> some View {
        HStack { Text(title).foregroundStyle(Color.shalevSecondaryText); Spacer(); Text(value).fontWeight(.semibold).multilineTextAlignment(.trailing) }
    }
}
