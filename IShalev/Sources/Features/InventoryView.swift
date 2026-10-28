import SwiftUI
import UIKit
import PhotosUI

/// Filtro de la lista. El tipo (`kind`) y la alerta se combinan, como la hoja de filtros de Android.
private enum InventoryKindFilter: String, CaseIterable, Identifiable, Equatable {
    case all = "Todos"
    case products = "Productos"
    case ingredients = "Insumos"
    case prepared = "Elaborados"
    var id: String { rawValue }

    func includes(_ product: Product) -> Bool {
        switch self {
        case .all: true
        case .products: product.kind == .product
        case .ingredients: product.kind == .ingredient
        case .prepared: product.kind == .prepared
        }
    }
}

private enum InventoryAlertFilter: String, CaseIterable, Identifiable, Equatable {
    case none = "Sin filtro"
    case critical = "Stock crítico"
    case low = "Stock bajo"
    case expiring = "Próximo a caducar"
    case zero = "Sin existencia"
    var id: String { rawValue }
}

private struct InventoryFilterState: Equatable {
    var kind: InventoryKindFilter = .all
    var alert: InventoryAlertFilter = .none

    var isActive: Bool { kind != .all || alert != .none }

    var summary: String {
        [kind == .all ? nil : kind.rawValue, alert == .none ? nil : alert.rawValue.lowercased()]
            .compactMap { $0 }
            .joined(separator: " · ")
    }
}

private enum InventorySheet: Identifiable {
    case create
    case edit(Product)
    case detail(Product)

    var id: String {
        switch self {
        case .create: "create"
        case .edit(let product): "edit-\(product.id.uuidString)"
        case .detail(let product): "detail-\(product.id.uuidString)"
        }
    }

    var product: Product? {
        switch self {
        case .create: nil
        case .edit(let product), .detail(let product): product
        }
    }
}

@MainActor
struct InventoryView: View {
    @ObservedObject var store: ShalevStore
    @ObservedObject var sync: SyncManager
    let onSell: () -> Void
    var canEdit = true
    var canSell = true
    var canExport = true

    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento
    @State private var search = ""
    @State private var filter = InventoryFilterState()
    @State private var showFilterSheet = false
    @State private var showExportSheet = false
    @State private var selection: Set<UUID> = []
    @State private var sheet: InventorySheet?
    @State private var confirmDelete: DeleteTarget?
    @State private var shareFileURL: URL?
    @State private var showShareSheet = false
    @State private var message: String?

    private var filteredProducts: [Product] {
        let today = Calendar.current.startOfDay(for: Date())
        let limit = Calendar.current.date(byAdding: .day, value: 7, to: today) ?? today
        return store.activeProducts.filter { product in
            guard filter.kind.includes(product) else { return false }
            switch filter.alert {
            case .none: break
            case .critical:
                guard product.kind != .prepared, product.criticalStockMilli > 0,
                      product.stockMilli <= product.criticalStockMilli else { return false }
            case .low:
                guard product.kind != .prepared, product.lowStockMilli > 0,
                      product.stockMilli <= product.lowStockMilli,
                      !(product.criticalStockMilli > 0 && product.stockMilli <= product.criticalStockMilli) else { return false }
            case .zero:
                guard product.kind != .prepared, product.stockMilli <= 0 else { return false }
            case .expiring:
                guard product.stockMilli > 0, let expiresAt = product.expiresAt,
                      Calendar.current.startOfDay(for: expiresAt) <= limit else { return false }
            }
            return search.isEmpty || product.name.localizedCaseInsensitiveContains(search)
                || product.category.localizedCaseInsensitiveContains(search)
                || product.description.localizedCaseInsensitiveContains(search)
        }
    }

    private var visibleSelection: [Product] { filteredProducts.filter { selection.contains($0.id) } }

    var body: some View {
        VStack(spacing: 0) {
            header
            ShalevSearchField(text: $search, placeholder: "Buscar por nombre, categoría o código")
                .padding(.horizontal, 16)
            counterRow
            if canEdit && selection.isEmpty && !filteredProducts.isEmpty {
                selectionHint
            }
            if !selection.isEmpty { selectionBar }
            list
        }
        .background(Color.shalevBackground)
        .overlay(alignment: .bottomTrailing) {
            if canEdit && selection.isEmpty {
                ShalevFloatingButton(title: "Agregar producto") { sheet = .create }
                    .padding(.trailing, 18)
                    .padding(.bottom, 15)
            }
        }
        .sheet(isPresented: $showFilterSheet) {
            InventoryFilterSheet(filter: $filter)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showExportSheet) {
            InventoryExportSheet(store: store, products: inventoryExportProducts,
                                 subtitle: exportSubtitle) { url in
                shareFileURL = url
                showShareSheet = true
            }
            .presentationDetents([.medium])
            .presentationDragIndicator(.visible)
        }
        .sheet(item: $sheet) { route in
            switch route {
            case .detail(let product):
                ProductDetailSheet(store: store, productID: product.id, canEdit: canEdit, canExport: canExport,
                                   onEdit: { sheet = .edit(product) },
                                   onDelete: { confirmDelete = .one(product) },
                                   onShare: { share(product) })
            case .create, .edit:
                ProductEditorView(store: store, sync: sync, product: route.product)
                    .presentationDetents([.large])
                    .presentationDragIndicator(.visible)
            }
        }
        .sheet(isPresented: $showShareSheet) {
            if let shareFileURL { ShareSheet(items: [shareFileURL]) }
        }
        .confirmationDialog(deleteTitle, isPresented: Binding(get: { confirmDelete != nil },
                                                              set: { if !$0 { confirmDelete = nil } }),
                            titleVisibility: .visible) {
            Button("Eliminar", role: .destructive) { performDelete() }
            Button("Cancelar", role: .cancel) { confirmDelete = nil }
        } message: {
            Text("Las ventas registradas no cambian.")
        }
        .alert("Inventario", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("Entendido", role: .cancel) { message = nil }
        } message: {
            Text(message ?? "")
        }
    }

    // MARK: - Cabecera

    private var header: some View {
        ZStack {
            ShalevScreenTitle(title: "Inventario")
            HStack(spacing: 2) {
                Spacer()
                Button { showFilterSheet = true } label: {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "line.3.horizontal.decrease")
                            .font(.shalev(17, weight: .semibold))
                            .foregroundStyle(filter.isActive ? Color.shalevOnPrimary : Color.shalevText)
                            .frame(width: 42, height: 42)
                            .background(filter.isActive ? Color.shalevPrimary : .clear, in: Circle())
                            .shalevToque()
                        if filter.isActive {
                            Circle().fill(Color.shalevSecondary).frame(width: 9, height: 9)
                                .offset(x: -4, y: 5)
                        }
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel(filter.isActive ? "Filtrar inventario. Filtros activos: \(filter.summary)" : "Filtrar inventario")
                if canExport {
                    Button {
                        showExportSheet = true
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                            .font(.shalev(17, weight: .semibold))
                            .foregroundStyle(Color.shalevText)
                            .frame(width: 42, height: 42)
                            .shalevToque()
                    }
                    .buttonStyle(.plain)
                    .disabled(filteredProducts.isEmpty && selection.isEmpty)
                    .opacity(filteredProducts.isEmpty && selection.isEmpty ? 0.4 : 1)
                    .accessibilityLabel("Exportar inventario")
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 6)
        .padding(.bottom, 8)
    }

    private var counterRow: some View {
        HStack(spacing: 8) {
            Text(counterText)
                .font(.shalev(12, weight: .medium))
                .foregroundStyle(Color.shalevSecondaryText)
            if filter.isActive {
                Button { withAnimation(ShalevMotion.cambio(reducirMovimiento, duracion: ShalevMotion.muyCorta)) { filter = InventoryFilterState() } } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "xmark").font(.shalev(9, weight: .bold))
                        Text(filter.summary).font(.shalev(11, weight: .semibold)).lineLimit(1)
                    }
                    .foregroundStyle(Color.shalevOnPrimary)
                    .padding(.horizontal, 9)
                    .frame(minHeight: 26)
                    .background(Color.shalevPrimary, in: Capsule())
                    .shalevToque()
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Quitar el filtro \(filter.summary)")
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 9)
        .padding(.bottom, 6)
    }

    private var counterText: String {
        let visible = filteredProducts.count
        let total = store.activeProducts.count
        return visible == total ? productos(visible) : "\(visible) de \(productos(total))"
    }

    private func productos(_ count: Int) -> String {
        count == 1 ? "1 producto" : "\(count) productos"
    }

    private var selectionHint: some View {
        Text("Mantén pulsado para seleccionar varios")
            .font(.shalev(11))
            .foregroundStyle(Color.shalevSecondaryText)
            .frame(maxWidth: .infinity, alignment: .center)
            .padding(.bottom, 4)
    }

    // MARK: - Selección

    private var selectionBar: some View {
        HStack(spacing: 10) {
            Text(selection.count == 1 ? "1 seleccionado" : "\(selection.count) seleccionados")
                .font(.shalev(13, weight: .semibold))
                .foregroundStyle(Color.shalevText)
            Spacer(minLength: 4)
            Button(allVisibleSelected ? "Quitar todo" : "Marcar todo") {
                if allVisibleSelected { selection.removeAll() } else { selection = Set(filteredProducts.map(\.id)) }
            }
            .font(.shalev(12, weight: .semibold))
            .foregroundStyle(Color.shalevPrimary)
            if canExport {
                Button { showExportSheet = true } label: {
                    Image(systemName: "square.and.arrow.up").font(.shalev(15, weight: .semibold))
                }
                .accessibilityLabel("Exportar la selección")
            }
            if canEdit {
                Button { confirmDelete = .selection(visibleSelection) } label: {
                    Image(systemName: "trash").font(.shalev(15, weight: .semibold))
                }
                .foregroundStyle(Color.shalevError)
                .accessibilityLabel("Eliminar la selección")
            }
            Button { selection.removeAll() } label: {
                Image(systemName: "xmark").font(.shalev(14, weight: .semibold))
            }
            .foregroundStyle(Color.shalevSecondaryText)
            .accessibilityLabel("Limpiar la selección")
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 9)
        .background(Color.shalevPrimaryContainer)
        .overlay(alignment: .bottom) { Color.shalevOutlineVariant.opacity(0.6).frame(height: 0.5) }
    }

    private var allVisibleSelected: Bool {
        !filteredProducts.isEmpty && filteredProducts.allSatisfy { selection.contains($0.id) }
    }

    // MARK: - Lista

    @ViewBuilder
    private var list: some View {
        if filteredProducts.isEmpty {
            ScrollView {
                if store.activeProducts.isEmpty {
                    ShalevEmptyState(symbol: "shippingbox", title: "Tu inventario está vacío",
                                     detail: canEdit ? "Toca + para agregar tu primer producto." : "Pide al administrador que agregue artículos al inventario.",
                                     actionTitle: canEdit ? "Agregar producto" : nil) {
                        if canEdit { sheet = .create }
                    }
                    .padding(.horizontal, 20)
                } else {
                    ShalevEmptyState(symbol: "magnifyingglass", title: "Sin resultados",
                                     detail: "Prueba otra búsqueda o quita los filtros.",
                                     actionTitle: "Quitar filtros") {
                        search = ""
                        filter = InventoryFilterState()
                    }
                    .padding(.horizontal, 20)
                }
            }
        } else {
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(filteredProducts) { product in
                        InventoryRow(product: product,
                                     store: store,
                                     marked: selection.contains(product.id),
                                     canSelect: canEdit,
                                     alertText: alertText(for: product),
                                     onToggle: { toggleSelection(product) },
                                     onOpen: { sheet = .detail(product) })
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 4)
                .padding(.bottom, canEdit && selection.isEmpty ? 92 : 20)
            }
        }
    }

    private func toggleSelection(_ product: Product) {
        if selection.contains(product.id) { selection.remove(product.id) }
        else { selection.insert(product.id) }
    }

    private func alertText(for product: Product) -> String? {
        guard product.kind != .prepared else { return nil }
        if product.stockMilli <= 0 { return "Sin existencia" }
        if product.criticalStockMilli > 0 && product.stockMilli <= product.criticalStockMilli { return "Crítico" }
        if product.lowStockMilli > 0 && product.stockMilli <= product.lowStockMilli { return "Bajo" }
        if let expiresAt = product.expiresAt, product.stockMilli > 0 {
            let today = Calendar.current.startOfDay(for: Date())
            let limit = Calendar.current.date(byAdding: .day, value: 7, to: today) ?? today
            let day = Calendar.current.startOfDay(for: expiresAt)
            if day < today { return "Vencido" }
            if day <= limit { return "Vence \(expiresAt.shalevDate)" }
        }
        return nil
    }

    // MARK: - Exportar y eliminar

    private var inventoryExportProducts: [Product] {
        selection.isEmpty ? filteredProducts : visibleSelection
    }

    private var exportSubtitle: String {
        let business = store.data.settings.profile.businessName
        let scope = selection.isEmpty ? (filter.isActive || !search.isEmpty ? "Filtro aplicado" : "Todo el inventario")
                                     : "\(selection.count) seleccionados"
        return "\(business) · \(Date().shalevDate) · \(scope)"
    }

    private func share(_ product: Product) {
        let report = ExportReport(
            title: "Ficha de \(product.name)",
            subtitle: store.data.settings.profile.businessName,
            headers: ["Artículo", "Categoría", "Existencias", "Precio CUP"],
            rows: [[product.name, product.category,
                    product.kind == .prepared ? "Elaborado" : "\(product.displayStock) \(product.unit)",
                    Cup(cents: product.priceCents).inputValue]])
        if let url = try? ShalevExporter.write(report, format: .pdf) {
            shareFileURL = url
            showShareSheet = true
        } else {
            message = "No se pudo generar el archivo. Inténtalo de nuevo."
        }
    }

    private var deleteTitle: String {
        guard let target = confirmDelete else { return "¿Eliminar?" }
        switch target {
        case .one(let product): return "¿Eliminar «\(product.name)»?"
        case .selection(let products): return "¿Eliminar \(products.count) \(products.count == 1 ? "producto" : "productos")?"
        }
    }

    private func performDelete() {
        guard let target = confirmDelete else { return }
        let products = target.products
        var deleted = 0
        var blocked: [String] = []
        for product in products {
            do {
                try store.deleteProduct(product.id)
                deleted += 1
            } catch {
                blocked.append(product.name)
            }
        }
        confirmDelete = nil
        sheet = nil
        selection.subtract(products.map(\.id))
        var text = deleted == 1 ? "1 eliminado." : "\(deleted) eliminados."
        if !blocked.isEmpty { text += " En uso (no se borran): \(blocked.joined(separator: ", "))." }
        message = text
    }
}

private enum DeleteTarget {
    case one(Product)
    case selection([Product])

    var products: [Product] {
        switch self {
        case .one(let product): [product]
        case .selection(let products): products
        }
    }
}

/// Fila del inventario: barra de estado, casilla (o foto), nombre, aviso, existencias, categoría y, a la
/// derecha, el precio de venta con el costo en un tono más suave. Pulsar abre la ficha; la casilla marca.
@MainActor
private struct InventoryRow: View {
    let product: Product
    @ObservedObject var store: ShalevStore
    let marked: Bool
    let canSelect: Bool
    let alertText: String?
    let onToggle: () -> Void
    let onOpen: () -> Void

    private var available: Int? {
        product.kind == .prepared ? store.availableQuantity(kind: .product, sourceID: product.id) : nil
    }
    private var statusColor: Color {
        if product.kind == .prepared {
            return (available ?? 0) == 0 ? .shalevError : .shalevPrimary
        }
        if product.stockMilli <= 0 { return .shalevError }
        if product.criticalStockMilli > 0 && product.stockMilli <= product.criticalStockMilli { return .shalevError }
        if product.lowStockMilli > 0 && product.stockMilli <= product.lowStockMilli { return .shalevSecondary }
        if alertText?.hasPrefix("Vence") == true || alertText == "Vencido" { return .shalevTertiary }
        return .shalevPrimary
    }

    var body: some View {
        HStack(spacing: 11) {
            Capsule().fill(statusColor).frame(width: 4, height: 54)
            if canSelect {
                Button(action: onToggle) {
                    Image(systemName: marked ? "checkmark.square.fill" : "square")
                        .font(.shalev(21, weight: .regular))
                        .foregroundStyle(marked ? Color.shalevPrimary : Color.shalevOutline)
                        .frame(minWidth: 44, minHeight: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(marked ? "Deseleccionar \(product.name)" : "Seleccionar \(product.name)")
            }
            Button(action: onOpen) {
                HStack(spacing: 11) {
                    if let data = product.imageData, let photo = UIImage(data: data) {
                        Image(uiImage: photo)
                            .resizable().scaledToFill()
                            .frame(width: 40, height: 40)
                            .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
                    }
                    VStack(alignment: .leading, spacing: 3) {
                        Text(product.name)
                            .font(.shalev(15, weight: .medium))
                            .foregroundStyle(Color.shalevText)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                        if let alertText {
                            Text(alertText)
                                .font(.shalev(12, weight: .semibold))
                                .foregroundStyle(statusColor)
                                .lineLimit(2)
                        }
                        Text(product.kind == .prepared
                             ? "Alcanza para \(available ?? 0)"
                             : "\(product.displayStock) \(product.unit)")
                            .font(.shalev(12))
                            .foregroundStyle(Color.shalevSecondaryText)
                        Text(product.kind == .prepared ? "Elaborado" : product.category)
                            .font(.shalev(11))
                            .foregroundStyle(Color.shalevSecondaryText)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 6)
                    VStack(alignment: .trailing, spacing: 3) {
                        Text(Cup(cents: product.priceCents).formatted)
                            .font(.shalev(14, weight: .semibold))
                            .foregroundStyle(Color.shalevText)
                            .lineLimit(1).minimumScaleFactor(0.75)
                        Text(Cup(cents: store.estimatedCostCents(for: product)).formatted)
                            .font(.shalev(11, weight: .medium))
                            .foregroundStyle(Color.shalevSecondaryText)
                            .lineLimit(1).minimumScaleFactor(0.75)
                    }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(product.name). \(alertText ?? product.displayStock). Abrir ficha")
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 6)
        .background(marked ? Color.shalevPrimaryContainer.opacity(0.65) : Color.shalevSurface,
                    in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.shalevOutlineVariant.opacity(marked ? 0 : 0.45), lineWidth: 0.6))
    }
}

/// Ficha del artículo: foto, estado, datos y acciones Editar / Compartir / Eliminar.
@MainActor
private struct ProductDetailSheet: View {
    @ObservedObject var store: ShalevStore
    let productID: UUID
    let canEdit: Bool
    let canExport: Bool
    let onEdit: () -> Void
    let onDelete: () -> Void
    let onShare: () -> Void
    @Environment(\.dismiss) private var dismiss

    private var product: Product? { store.data.products.first { $0.id == productID && $0.isActive } }

    var body: some View {
        NavigationStack {
            ScrollView {
                if let product {
                    VStack(spacing: 14) {
                        if let data = product.imageData, let photo = UIImage(data: data) {
                            Image(uiImage: photo)
                                .resizable().scaledToFill()
                                .frame(maxWidth: .infinity).frame(height: 190).clipped()
                                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }
                        ShalevCard {
                            VStack(alignment: .leading, spacing: 10) {
                                if !product.description.isEmpty {
                                    detailRow("Descripción", product.description)
                                }
                                detailRow("Categoría", product.kind == .prepared ? "Elaborado" : product.category)
                                detailRow("Tipo", product.kind.rawValue)
                                if product.kind != .prepared {
                                    detailRow("Existencias", "\(product.displayStock) \(product.unit)")
                                    detailRow("Aviso de stock bajo", FixedNumber.formatMilli(product.lowStockMilli))
                                    detailRow("Aviso de stock crítico", FixedNumber.formatMilli(product.criticalStockMilli))
                                    if let expiresAt = product.expiresAt {
                                        detailRow("Caduca el", expiresAt.shalevDate)
                                    }
                                } else {
                                    detailRow("Alcanza para", "\(store.availableQuantity(kind: .product, sourceID: product.id) ?? 0)")
                                    if !product.uses.isEmpty {
                                        detailRow("Receta", product.uses.map { use in
                                            let name = store.data.products.first { $0.id == use.ingredientID }?.name ?? "Insumo"
                                            return "\(name) \(FixedNumber.formatMilli(use.milliUnits))"
                                        }.joined(separator: ", "))
                                    }
                                }
                                detailRow("Costo", Cup(cents: store.estimatedCostCents(for: product)).formatted)
                                detailRow("Precio de venta", Cup(cents: product.priceCents).formatted)
                                let unitCost = store.estimatedCostCents(for: product)
                                detailRow("Ganancia por unidad", Cup(cents: product.priceCents - unitCost).formatted,
                                          tint: product.priceCents < unitCost ? .shalevError : .shalevText)
                            }
                        }
                        HStack(spacing: 10) {
                            if canEdit {
                                Button(action: onEdit) { Label("Editar", systemImage: "pencil") }
                                    .buttonStyle(ShalevTonalButtonStyle())
                            }
                            if canExport {
                                Button(action: onShare) { Label("Compartir", systemImage: "square.and.arrow.up") }
                                    .buttonStyle(ShalevTonalButtonStyle())
                            }
                            if canEdit {
                                Button(action: onDelete) { Label("Eliminar", systemImage: "trash") }
                                    .buttonStyle(ShalevTonalButtonStyle())
                            }
                        }
                        .padding(.bottom, 10)
                    }
                    .padding(18)
                } else {
                    ShalevEmptyState(symbol: "shippingbox", title: "Ya no existe",
                                     detail: "Este artículo se eliminó o se desactivó en otra app vinculada.")
                        .padding(20)
                }
            }
            .background(Color.shalevBackground)
            .navigationTitle(product?.name ?? "Artículo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Color.shalevPrimary)
                }
            }
        }
    }

    private func detailRow(_ title: String, _ value: String, tint: Color = .shalevText) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(title).font(.shalev(13)).foregroundStyle(Color.shalevSecondaryText)
            Spacer(minLength: 6)
            Text(value)
                .font(.shalev(13, weight: .semibold))
                .foregroundStyle(tint)
                .multilineTextAlignment(.trailing)
        }
    }
}

/// Hoja de filtros: tipo de artículo y alerta, con «Quitar filtros».
private struct InventoryFilterSheet: View {
    @Binding var filter: InventoryFilterState
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    ShalevSectionHeader(title: "Tipo")
                    ShalevCard(padding: 5) {
                        VStack(spacing: 0) {
                            ForEach(InventoryKindFilter.allCases) { option in
                                Button { filter.kind = option } label: {
                                    HStack {
                                        Image(systemName: option.symbol)
                                            .font(.shalev(15, weight: .semibold))
                                            .foregroundStyle(Color.shalevPrimary)
                                            .frame(width: 26)
                                        Text(option.rawValue)
                                            .font(.shalev(14, weight: .medium))
                                            .foregroundStyle(Color.shalevText)
                                        Spacer()
                                        if filter.kind == option {
                                            Image(systemName: "checkmark").foregroundStyle(Color.shalevPrimary)
                                        }
                                    }
                                    .padding(.horizontal, 12).padding(.vertical, 11)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    ShalevSectionHeader(title: "Alerta")
                    ShalevCard(padding: 5) {
                        VStack(spacing: 0) {
                            ForEach(InventoryAlertFilter.allCases) { option in
                                Button { filter.alert = option } label: {
                                    HStack {
                                        Text(option.rawValue)
                                            .font(.shalev(14, weight: .medium))
                                            .foregroundStyle(Color.shalevText)
                                        Spacer()
                                        if filter.alert == option {
                                            Image(systemName: "checkmark").foregroundStyle(Color.shalevPrimary)
                                        }
                                    }
                                    .padding(.horizontal, 12).padding(.vertical, 11)
                                    .contentShape(Rectangle())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(18)
            }
            .background(Color.shalevBackground)
            .navigationTitle("Filtrar inventario")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Quitar filtros") { filter = InventoryFilterState() }
                        .foregroundStyle(Color.shalevPrimary)
                        .disabled(!filter.isActive)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Aplicar") { dismiss() }.foregroundStyle(Color.shalevPrimary)
                }
            }
        }
    }

}

/// Hoja de exportación del inventario: elige formato y comparte el archivo generado.
private struct InventoryExportSheet: View {
    @ObservedObject var store: ShalevStore
    let products: [Product]
    let subtitle: String
    let onFile: (URL) -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var error: String?

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text("\(products.count) \(products.count == 1 ? "artículo" : "artículos")")
                    .font(.shalev(13, weight: .semibold))
                    .foregroundStyle(Color.shalevSecondaryText)
                ForEach([ExportFormat.xlsx, .csv, .pdf], id: \.fileExtension) { format in
                    Button {
                        export(format)
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: format.symbol)
                                .font(.shalev(18, weight: .semibold))
                                .foregroundStyle(Color.shalevPrimary)
                                .frame(width: 42, height: 42)
                                .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 12))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(format.title).font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevText)
                                Text(format.detail).font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                            }
                            Spacer()
                            Image(systemName: "chevron.right").font(.shalev(11, weight: .semibold))
                                .foregroundStyle(Color.shalevSecondaryText)
                        }
                        .padding(12)
                        .background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
            }
            .padding(18)
            .background(Color.shalevBackground)
            .navigationTitle("Exportar inventario")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Cerrar") { dismiss() }.foregroundStyle(Color.shalevPrimary)
                }
            }
            .alert("No se pudo exportar", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
                Button("Entendido", role: .cancel) { error = nil }
            } message: { Text(error ?? "") }
        }
    }

    private func export(_ format: ExportFormat) {
        let report = ExportReport(
            title: "Inventario",
            subtitle: subtitle,
            headers: ["Artículo", "Tipo", "Categoría", "Existencias", "Unidad", "Costo CUP", "Precio CUP", "Caducidad"],
            rows: products.map { product in
                [product.name, product.kind.rawValue, product.category,
                 product.kind == .prepared ? "Elaborado" : FixedNumber.formatMilli(product.stockMilli), product.unit,
                 Cup(cents: store.estimatedCostCents(for: product)).inputValue,
                 Cup(cents: product.priceCents).inputValue, product.expiresAt?.shalevDate ?? ""]
            })
        do {
            let url = try ShalevExporter.write(report, format: format)
            dismiss()
            onFile(url)
        } catch {
            self.error = "No se pudo generar el archivo. Inténtalo de nuevo."
        }
    }
}

private extension InventoryKindFilter {
    var symbol: String {
        switch self {
        case .all: "square.grid.2x2"
        case .products: "shippingbox"
        case .ingredients: "drop"
        case .prepared: "fork.knife"
        }
    }
}

private extension ExportFormat {
    var title: String {
        switch self {
        case .xlsx: "Excel (XLSX)"
        case .csv: "CSV"
        case .pdf: "PDF"
        }
    }
    var detail: String {
        switch self {
        case .xlsx: "Libro con hojas y columnas"
        case .csv: "Texto separado por punto y coma"
        case .pdf: "Documento para imprimir o enviar"
        }
    }
    var symbol: String {
        switch self {
        case .xlsx: "tablecells"
        case .csv: "text.alignleft"
        case .pdf: "doc.richtext"
        }
    }
}

@MainActor
private struct ProductEditorView: View {
    @ObservedObject var store: ShalevStore
    @ObservedObject var sync: SyncManager
    let product: Product?
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var category: String
    @State private var kind: InventoryKind
    @State private var description: String
    @State private var stock: String
    @State private var unit: String
    @State private var lowStock: String
    @State private var criticalStock: String
    @State private var price: String
    @State private var cost: String
    @State private var hasExpiry: Bool
    @State private var expiry: Date
    @State private var recipe: [IngredientUse]
    @State private var imageData: Data?
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var selectedIngredientID: UUID?
    @State private var recipeAmount = "0.100"
    @State private var errorMessage: String?
    @State private var confirmDelete = false
    @State private var working = false

    init(store: ShalevStore, sync: SyncManager, product: Product?) {
        self.store = store
        self.sync = sync
        self.product = product
        _name = State(initialValue: product?.name ?? "")
        _category = State(initialValue: product?.category ?? "Otros")
        _kind = State(initialValue: product?.kind ?? .product)
        _description = State(initialValue: product?.description ?? "")
        _stock = State(initialValue: product.map { FixedNumber.formatMilli($0.stockMilli) } ?? "0")
        _unit = State(initialValue: product?.unit ?? "u")
        _lowStock = State(initialValue: product.map { FixedNumber.formatMilli($0.lowStockMilli) } ?? "0")
        _criticalStock = State(initialValue: product.map { FixedNumber.formatMilli($0.criticalStockMilli) } ?? "0")
        _price = State(initialValue: product.map { Cup(cents: $0.priceCents).inputValue } ?? "")
        _cost = State(initialValue: product.map { Cup(cents: $0.costCents).inputValue } ?? "")
        _hasExpiry = State(initialValue: product?.expiresAt != nil)
        _expiry = State(initialValue: product?.expiresAt ?? Date())
        _recipe = State(initialValue: product?.uses ?? [])
        _imageData = State(initialValue: product?.imageData)
        _selectedPhoto = State(initialValue: nil)
        _selectedIngredientID = State(initialValue: store.activeProducts.first(where: { $0.kind == .ingredient })?.id)
    }

    private var ingredients: [Product] { store.activeProducts.filter { $0.kind == .ingredient } }

    var body: some View {
        let photoActionTitle = imageData == nil ? "Añadir foto" : "Cambiar foto"
        return NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    ShalevScreenTitle(title: product == nil ? "Agregar artículo" : "Editar artículo",
                                     subtitle: "Los datos se guardan cifrados en este teléfono.")
                    Text("* Obligatorio")
                        .font(.shalev(11))
                        .foregroundStyle(Color.shalevSecondaryText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    if kind != .prepared && !sync.isSecondary {
                    ShalevCard {
                        VStack(spacing: 10) {
                            if let imageData, let photo = UIImage(data: imageData) {
                                Image(uiImage: photo).resizable().scaledToFill()
                                    .frame(maxWidth: .infinity).frame(height: 190).clipped()
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                            } else {
                                Image(systemName: "photo.on.rectangle.angled")
                                    .font(.shalev(30)).foregroundStyle(Color.shalevTertiary)
                                    .frame(maxWidth: .infinity).frame(height: 95)
                                    .background(Color.shalevSurfaceVariant, in: RoundedRectangle(cornerRadius: 14))
                            }
                            HStack(spacing: 10) {
                                PhotosPicker(selection: $selectedPhoto, matching: .images) {
                                    Label(photoActionTitle, systemImage: "photo")
                                }
                                .buttonStyle(ShalevTonalButtonStyle())
                                if imageData != nil {
                                    Button(role: .destructive) { imageData = nil; selectedPhoto = nil } label: {
                                        Label("Quitar", systemImage: "trash")
                                    }
                                    .buttonStyle(ShalevTonalButtonStyle())
                                }
                            }
                        }
                    }
                    }
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 13) {
                            formField("Nombre", text: $name, placeholder: "Ej. Agua mineral")
                            VStack(alignment: .leading, spacing: 6) {
                                fieldLabel("Tipo")
                                Picker("Tipo", selection: $kind) {
                                    ForEach(InventoryKind.allCases) { option in Text(option.rawValue).tag(option) }
                                }
                                .pickerStyle(.segmented)
                                .onChange(of: kind) { _, newValue in
                                    if newValue == .ingredient { category = "Insumos" }
                                    else if newValue == .prepared { category = "Elaborado"; imageData = nil; selectedPhoto = nil }
                                    else if category == "Insumos" || category == "Elaborado" { category = "Otros" }
                                }
                            }
                            if kind == .product {
                                VStack(alignment: .leading, spacing: 7) {
                                    formField("Categoría *", text: $category, placeholder: "Elige o escribe una nueva")
                                    if !categorySuggestions.isEmpty {
                                        ScrollView(.horizontal, showsIndicators: false) {
                                            HStack(spacing: 7) {
                                                ForEach(categorySuggestions, id: \.self) { option in
                                                    ShalevFilterChip(title: option, selected: category == option) {
                                                        category = option
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            formField("Descripción (opcional, máximo 40)", text: $description, placeholder: "Detalles para identificarlo")
                                .onChange(of: description) { _, value in if value.count > 40 { description = String(value.prefix(40)) } }

                            if kind != .prepared {
                                Divider()
                                formField(kind == .ingredient ? "Existencias *" : "Cantidad *", text: $stock,
                                          placeholder: "0", keyboard: .decimalPad,
                                          help: kind == .ingredient ? "Unidades que tienes" : "Unidades que tienes")
                                if kind == .ingredient {
                                    formField("Unidad de medida", text: $unit, placeholder: "kg, L, u…")
                                }
                                formField("Aviso de stock bajo", text: $lowStock, placeholder: "0", keyboard: .decimalPad, help: "Opcional")
                                formField("Aviso de stock crítico", text: $criticalStock, placeholder: "0", keyboard: .decimalPad, help: "Opcional")
                                Text("Vacíos: se usan los niveles de Ajustes.")
                                    .font(.shalev(11))
                                    .foregroundStyle(Color.shalevSecondaryText)
                                Toggle("Tiene fecha de caducidad", isOn: $hasExpiry).tint(Color.shalevPrimary)
                                if hasExpiry {
                                    DatePicker("Caduca el", selection: $expiry, displayedComponents: .date)
                                        .font(.shalev(14))
                                }
                            } else {
                                Divider()
                                recipeEditor
                            }
                        }
                    }

                    ShalevCard {
                        VStack(alignment: .leading, spacing: 13) {
                            fieldLabel(kind == .ingredient ? "Precio de venta opcional (CUP)" : "Precio de venta (CUP) *")
                            TextField("0.00", text: $price).keyboardType(.decimalPad).textFieldStyle(ShalevTextFieldStyle())
                            Text("Por unidad").font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                            if kind != .prepared {
                                fieldLabel("Precio de costo (CUP) *")
                                TextField("0.00", text: $cost).keyboardType(.decimalPad).textFieldStyle(ShalevTextFieldStyle())
                                Text("Por unidad").font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                            } else {
                                Text("El costo se calcula a partir de los insumos de la receta.")
                                    .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
                            }
                            Button {
                                save()
                            } label: {
                                Label(product == nil ? "Guardar producto" : "Guardar cambios", systemImage: "checkmark")
                            }
                            .buttonStyle(ShalevPrimaryButtonStyle())
                            .disabled(working)
                            if working { ProgressView().accessibilityLabel("Cargando").frame(maxWidth: .infinity) }
                        }
                    }

                    if product != nil {
                        Button(role: .destructive) { confirmDelete = true } label: {
                            Label("Eliminar del inventario", systemImage: "trash")
                                .font(.shalev(14, weight: .semibold))
                                .foregroundStyle(Color.shalevError)
                                .frame(maxWidth: .infinity, minHeight: 46)
                        }
                        .padding(.horizontal, 20)
                        .padding(.bottom, 20)
                    }
                }
                .padding(18)
            }
            .background(Color.shalevBackground)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Cancelar") { dismiss() }.foregroundStyle(Color.shalevSecondaryText) }
                ToolbarItem(placement: .topBarTrailing) { Button("Guardar") { save() }.fontWeight(.semibold).foregroundStyle(Color.shalevPrimary).disabled(working) }
            }
            .alert("Revisa los datos", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Entendido", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
            .confirmationDialog("¿Eliminar este artículo?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Eliminar", role: .destructive) { delete() }
                Button("Cancelar", role: .cancel) { }
            } message: {
                Text("Las ventas ya registradas conservarán sus datos.")
            }
        }
        .onChange(of: selectedPhoto) { _, item in
            Task { await loadPhoto(item) }
        }
    }

    private var recipeEditor: some View {
        VStack(alignment: .leading, spacing: 11) {
            Text("Insumos por unidad elaborada")
                .font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevText)
            Text("Al vender este artículo, se descuentan los insumos automáticamente.")
                .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
            if ingredients.isEmpty {
                Text("Crea primero los artículos de tipo Insumo.")
                    .font(.shalev(13)).foregroundStyle(Color.shalevSecondaryText)
            } else {
                ForEach(recipe) { use in
                    HStack {
                        Text(ingredients.first(where: { $0.id == use.ingredientID })?.name ?? "Insumo eliminado")
                            .font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevText)
                        Spacer()
                        Text(FixedNumber.formatMilli(use.milliUnits))
                            .font(.shalev(12, weight: .semibold)).foregroundStyle(Color.shalevSecondaryText)
                        Button { recipe.removeAll { $0.id == use.id } } label: {
                            Image(systemName: "minus.circle.fill").foregroundStyle(Color.shalevError)
                        }
                        .accessibilityLabel("Quitar insumo")
                    }
                }
                Picker("Insumo", selection: Binding(get: { selectedIngredientID ?? ingredients.first!.id }, set: { selectedIngredientID = $0 })) {
                    ForEach(ingredients) { ingredient in Text(ingredient.name).tag(ingredient.id) }
                }
                .pickerStyle(.menu)
                HStack(spacing: 10) {
                    TextField("Cantidad por unidad", text: $recipeAmount)
                        .keyboardType(.decimalPad)
                        .textFieldStyle(ShalevTextFieldStyle())
                    Button("Agregar") { addRecipeIngredient() }
                        .buttonStyle(ShalevTonalButtonStyle())
                        .disabled(selectedIngredientID == nil)
                }
            }
        }
    }

    private func formField(_ label: String, text: Binding<String>, placeholder: String = "", keyboard: UIKeyboardType = .default,
                           multiline: Bool = false, help: String? = nil) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            fieldLabel(label)
            if multiline {
                TextField(placeholder, text: text, axis: .vertical).lineLimit(2...4).textFieldStyle(ShalevTextFieldStyle())
            } else {
                TextField(placeholder, text: text).keyboardType(keyboard).textFieldStyle(ShalevTextFieldStyle())
                    .submitLabel(.done)
                    .textContentType(keyboard == .phonePad ? .telephoneNumber : nil)
            }
            if let help {
                Text(help).font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
            }
        }
    }

    /// Categorías ya usadas en el inventario, sin repetir y sin las reservadas de insumos y elaborados.
    private var categorySuggestions: [String] {
        var seen = Set<String>()
        let reserved: Set<String> = ["Insumos", "Elaborado"]
        return store.activeProducts
            .map { $0.category.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !reserved.contains($0) && seen.insert($0).inserted }
            .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }

    private func fieldLabel(_ text: String) -> some View {
        Text(text).font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
    }

    private func addRecipeIngredient() {
        guard let id = selectedIngredientID, let amount = FixedNumber.parse(recipeAmount, multiplier: 1_000), amount > 0 else {
            errorMessage = "Escribe una cantidad de insumo mayor que cero."
            return
        }
        if let index = recipe.firstIndex(where: { $0.ingredientID == id }) { recipe[index].milliUnits = amount }
        else { recipe.append(IngredientUse(ingredientID: id, milliUnits: amount)) }
        recipeAmount = "0.100"
    }

    private func loadPhoto(_ item: PhotosPickerItem?) async {
        guard let item else { return }
        do {
            guard let source = try await item.loadTransferable(type: Data.self), let image = UIImage(data: source) else {
                errorMessage = "No se pudo abrir esa imagen."
                return
            }
            let longestSide = max(image.size.width, image.size.height)
            guard longestSide > 0 else { errorMessage = "La imagen no es válida."; return }
            let scale = min(1, 1_200 / longestSide)
            let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
            let renderer = UIGraphicsImageRenderer(size: size)
            let resized = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
            guard let jpeg = resized.jpegData(compressionQuality: 0.76), jpeg.count <= 1_500_000 else {
                errorMessage = "La imagen supera el tamaño permitido. Elige una foto más pequeña."
                return
            }
            imageData = jpeg
        } catch {
            errorMessage = "No se pudo importar la foto."
        }
    }

    private func save() {
        guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { errorMessage = "Escribe el nombre del artículo."; return }
        let priceCents: Int64
        if kind == .ingredient && price.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { priceCents = 0 }
        else {
            guard let parsedPrice = Cup.parse(price)?.cents else { errorMessage = "Escribe un precio válido en CUP."; return }
            priceCents = parsedPrice
        }
        let costCents = Cup.parse(cost)?.cents ?? 0
        let stockMilli: Int64
        let low: Int64
        let critical: Int64
        if kind == .prepared {
            stockMilli = 0; low = 0; critical = 0
        } else {
            guard let parsedStock = FixedNumber.parse(stock, multiplier: 1_000) else { errorMessage = "Escribe una cantidad válida."; return }
            guard let parsedLow = lowStock.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0 : FixedNumber.parse(lowStock, multiplier: 1_000),
                  let parsedCritical = criticalStock.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0 : FixedNumber.parse(criticalStock, multiplier: 1_000) else {
                errorMessage = "Revisa los niveles de aviso de existencias."; return
            }
            stockMilli = parsedStock; low = parsedLow; critical = parsedCritical
        }
        if kind == .prepared && recipe.isEmpty { errorMessage = "Añade al menos un insumo a la receta."; return }
        let saved = Product(id: product?.id ?? UUID(), name: name, category: category, kind: kind, description: description,
                            imageData: imageData, expiresAt: hasExpiry && kind != .prepared ? expiry : nil,
                            stockMilli: stockMilli, unit: unit.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "u" : unit,
                            lowStockMilli: low, criticalStockMilli: critical, priceCents: priceCents,
                            costCents: kind == .prepared ? 0 : costCents, uses: recipe,
                            isActive: product?.isActive ?? true, createdAt: product?.createdAt ?? Date(),
                            updatedAt: product?.updatedAt, syncPhotoName: product?.syncPhotoName)
        if sync.isSecondary {
            working = true
            Task {
                do {
                    let action = try remoteSaveAction(saved)
                    _ = try await sync.executeRemote(action)
                    dismiss()
                } catch { errorMessage = error.localizedDescription; working = false }
            }
        } else {
            do { try store.saveProduct(saved); dismiss() }
            catch { errorMessage = error.localizedDescription }
        }
    }

    private func remoteSaveAction(_ saved: Product) throws -> [String: Any] {
        if saved.kind == .ingredient {
            var row = try AndroidBackupCodec.exportIngredient(saved)
            if let name = saved.syncPhotoName { row["fotoUri"] = name }
            var action = SyncJSON.message("insumo_guardar", ["insumo": row])
            if let product { action["cantidadLeidaMil"] = product.stockMilli }
            return action
        }
        var row = try AndroidBackupCodec.exportProduct(saved)
        if let name = saved.syncPhotoName { row["fotoUri"] = name }
        var fields: [String: Any] = ["producto": row]
        if saved.kind == .prepared {
            fields["receta"] = ["productoId": AndroidID.int64("product", saved.id), "lineas": saved.uses.map {
                ["insumoId": AndroidID.int64("ingredient", $0.ingredientID), "cantidadMil": $0.milliUnits]
            }]
        }
        if let product { fields["cantidadLeida"] = product.stockMilli / 1_000 }
        return SyncJSON.message("producto_guardar", fields)
    }

    private func delete() {
        guard let product else { return }
        if sync.isSecondary {
            working = true
            Task {
                do {
                    let domain = product.kind == .ingredient ? "insumo" : "producto"
                    let id = AndroidID.int64(product.kind == .ingredient ? "ingredient" : "product", product.id)
                    _ = try await sync.executeRemote(SyncJSON.message("\(domain)_eliminar", ["id": id]))
                    dismiss()
                } catch { errorMessage = error.localizedDescription; working = false }
            }
        } else {
            do { try store.deleteProduct(product.id); dismiss() }
            catch { errorMessage = error.localizedDescription }
        }
    }
}

private struct ShalevTextFieldStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .font(.shalev(15))
            .foregroundStyle(Color.shalevText)
            .padding(12)
            .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.shalevOutlineVariant, lineWidth: 1))
    }
}
