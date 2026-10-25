import SwiftUI
import UIKit
import PhotosUI

private enum InventoryFilter: String, CaseIterable, Identifiable, Equatable {
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

private enum ProductSheet: Identifiable {
    case create
    case edit(Product)
    var id: String {
        switch self {
        case .create: "create"
        case .edit(let product): "edit-\(product.id.uuidString)"
        }
    }
    var product: Product? {
        if case .edit(let product) = self { return product }
        return nil
    }
}

@MainActor
struct InventoryView: View {
    @ObservedObject var store: ShalevStore
    @ObservedObject var sync: SyncManager
    let onSell: () -> Void
    var canEdit = true
    var canSell = true
    @State private var search = ""
    @State private var filter: InventoryFilter = .all
    @State private var sheet: ProductSheet?

    private var filteredProducts: [Product] {
        store.activeProducts.filter { product in
            filter.includes(product) && (search.isEmpty || product.name.localizedCaseInsensitiveContains(search)
                || product.category.localizedCaseInsensitiveContains(search))
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                ShalevScreenTitle(title: "Inventario")
                if canSell {
                    Button(action: onSell) {
                        Image(systemName: "cart.fill")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(Color.shalevPrimary)
                            .frame(width: 42, height: 42)
                    }
                    .accessibilityLabel("Nueva venta")
                }
            }
            .padding(.horizontal, 17)
            .padding(.top, 7)
            .padding(.bottom, 10)

            ShalevSearchField(text: $search)
                .padding(.horizontal, 17)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(InventoryFilter.allCases) { option in
                        ShalevFilterChip(title: option.rawValue, selected: filter == option) { filter = option }
                    }
                }
                .padding(.horizontal, 17)
                .padding(.vertical, 11)
            }

            Text("\(filteredProducts.count) \(filteredProducts.count == 1 ? "artículo" : "artículos")")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Color.shalevSecondaryText)
                .padding(.bottom, 5)

            if filteredProducts.isEmpty {
                ScrollView {
                    ShalevEmptyState(symbol: search.isEmpty ? "shippingbox" : "magnifyingglass",
                                     title: search.isEmpty ? "Tu inventario está vacío" : "No hay resultados",
                                     detail: search.isEmpty ? (canEdit ? "Agrega productos, insumos o elaborados para empezar." : "Pide al administrador que agregue artículos al inventario.") : "Prueba con otro nombre o categoría.",
                                     actionTitle: search.isEmpty ? (canEdit ? "Agregar artículo" : nil) : "Limpiar búsqueda") {
                        if search.isEmpty { if canEdit { sheet = .create } } else { search = ""; filter = .all }
                    }
                    .padding(.horizontal, 20)
                }
            } else {
                ScrollView {
                    LazyVStack(spacing: 1) {
                        ForEach(filteredProducts) { product in
                            if canEdit {
                                Button { sheet = .edit(product) } label: { ProductRow(product: product, store: store) }
                                    .buttonStyle(.plain)
                            } else {
                                ProductRow(product: product, store: store)
                            }
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.bottom, 84)
                }
            }
        }
        .background(Color.shalevBackground)
        .overlay(alignment: .bottomTrailing) {
            if canEdit {
                ShalevFloatingButton(title: "Agregar artículo") { sheet = .create }
                    .padding(.trailing, 18)
                    .padding(.bottom, 15)
            }
        }
        .sheet(item: $sheet) { route in
            ProductEditorView(store: store, sync: sync, product: route.product)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
    }
}

@MainActor
private struct ProductRow: View {
    let product: Product
    @ObservedObject var store: ShalevStore

    private var available: Int? { product.kind == .prepared ? store.availableQuantity(kind: .product, sourceID: product.id) : nil }
    private var statusColor: Color {
        if let available { return available == 0 ? .shalevError : .shalevPrimary }
        if product.criticalStockMilli > 0 && product.stockMilli <= product.criticalStockMilli { return .shalevError }
        if product.lowStockMilli > 0 && product.stockMilli <= product.lowStockMilli { return .shalevSecondary }
        return .shalevPrimary
    }

    var body: some View {
        HStack(spacing: 12) {
            Capsule().fill(statusColor).frame(width: 4, height: 56)
            if let data = product.imageData, let photo = UIImage(data: data) {
                Image(uiImage: photo)
                    .resizable().scaledToFill().frame(width: 46, height: 46).clipShape(RoundedRectangle(cornerRadius: 13))
            } else {
                Image(systemName: product.kind.symbol)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color.shalevPrimary)
                    .frame(width: 46, height: 46)
                    .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(product.name)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.shalevText)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                Text(product.kind == .prepared ? "Alcanza para \(available ?? 0)" : product.displayStock)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(statusColor)
                Text(product.kind == .prepared ? "Elaborado" : product.category)
                    .font(.system(size: 11))
                    .foregroundStyle(Color.shalevSecondaryText)
                    .lineLimit(1)
            }
            Spacer(minLength: 4)
            VStack(alignment: .trailing, spacing: 4) {
                Text(Cup(cents: product.priceCents).formatted)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.shalevText)
                    .lineLimit(1).minimumScaleFactor(0.75)
                Text(Cup(cents: store.estimatedCostCents(for: product)).formatted)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Color.shalevSecondaryText)
                    .lineLimit(1).minimumScaleFactor(0.75)
            }
        }
        .padding(.horizontal, 9)
        .padding(.vertical, 10)
        .contentShape(Rectangle())
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
                    if kind != .prepared && !sync.isSecondary {
                    ShalevCard {
                        VStack(spacing: 10) {
                            if let imageData, let photo = UIImage(data: imageData) {
                                Image(uiImage: photo).resizable().scaledToFill()
                                    .frame(maxWidth: .infinity).frame(height: 190).clipped()
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                            } else {
                                Image(systemName: "photo.on.rectangle.angled")
                                    .font(.system(size: 30)).foregroundStyle(Color.shalevTertiary)
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
                                formField("Categoría", text: $category, placeholder: "Ej. Alimentos")
                            }
                            formField("Descripción (opcional, máximo 40)", text: $description, placeholder: "Detalles para identificarlo")
                                .onChange(of: description) { _, value in if value.count > 40 { description = String(value.prefix(40)) } }

                            if kind != .prepared {
                                Divider()
                                formField(kind == .ingredient ? "Existencias" : "Cantidad", text: $stock,
                                          placeholder: "0", keyboard: .decimalPad)
                                if kind == .ingredient {
                                    formField("Unidad de medida", text: $unit, placeholder: "kg, L, u…")
                                }
                                formField("Aviso de stock bajo", text: $lowStock, placeholder: "0", keyboard: .decimalPad)
                                formField("Aviso de stock crítico", text: $criticalStock, placeholder: "0", keyboard: .decimalPad)
                                Toggle("Tiene fecha de caducidad", isOn: $hasExpiry).tint(Color.shalevPrimary)
                                if hasExpiry {
                                    DatePicker("Caduca el", selection: $expiry, displayedComponents: .date)
                                        .font(.system(size: 14))
                                }
                            } else {
                                Divider()
                                recipeEditor
                            }
                        }
                    }

                    ShalevCard {
                        VStack(alignment: .leading, spacing: 13) {
                            fieldLabel(kind == .ingredient ? "Precio de venta opcional (CUP)" : "Precio de venta (CUP)")
                            TextField("0.00", text: $price).keyboardType(.decimalPad).textFieldStyle(ShalevTextFieldStyle())
                            if kind != .prepared {
                                fieldLabel("Costo por unidad (CUP)")
                                TextField("0.00", text: $cost).keyboardType(.decimalPad).textFieldStyle(ShalevTextFieldStyle())
                            } else {
                                Text("El costo se calcula a partir de los insumos de la receta.")
                                    .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                            }
                            Button {
                                save()
                            } label: {
                                Label("Guardar", systemImage: "checkmark")
                            }
                            .buttonStyle(ShalevPrimaryButtonStyle())
                            .disabled(working)
                            if working { ProgressView().frame(maxWidth: .infinity) }
                        }
                    }

                    if product != nil {
                        Button(role: .destructive) { confirmDelete = true } label: {
                            Label("Eliminar del inventario", systemImage: "trash")
                                .font(.system(size: 14, weight: .semibold))
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
                .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
            Text("Al vender este artículo, se descuentan los insumos automáticamente.")
                .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
            if ingredients.isEmpty {
                Text("Crea primero los artículos de tipo Insumo.")
                    .font(.system(size: 13)).foregroundStyle(Color.shalevSecondaryText)
            } else {
                ForEach(recipe) { use in
                    HStack {
                        Text(ingredients.first(where: { $0.id == use.ingredientID })?.name ?? "Insumo eliminado")
                            .font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevText)
                        Spacer()
                        Text(FixedNumber.formatMilli(use.milliUnits))
                            .font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.shalevSecondaryText)
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
                           multiline: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            fieldLabel(label)
            if multiline {
                TextField(placeholder, text: text, axis: .vertical).lineLimit(2...4).textFieldStyle(ShalevTextFieldStyle())
            } else {
                TextField(placeholder, text: text).keyboardType(keyboard).textFieldStyle(ShalevTextFieldStyle())
            }
        }
    }

    private func fieldLabel(_ text: String) -> some View {
        Text(text).font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
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
            var action = SyncJSON.message("insumo_guardar", ["insumo": try AndroidBackupCodec.exportIngredient(saved)])
            if let product { action["cantidadLeidaMil"] = product.stockMilli }
            return action
        }
        var fields: [String: Any] = ["producto": try AndroidBackupCodec.exportProduct(saved)]
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
            .font(.system(size: 15))
            .foregroundStyle(Color.shalevText)
            .padding(12)
            .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.shalevOutlineVariant, lineWidth: 1))
    }
}
