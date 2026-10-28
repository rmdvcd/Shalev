import SwiftUI
import UIKit
import PhotosUI

private enum ServiceSheet: Identifiable {
    case create
    case edit(ServiceItem)
    var id: String {
        switch self {
        case .create: "create-service"
        case .edit(let service): "service-\(service.id.uuidString)"
        }
    }
    var service: ServiceItem? {
        if case .edit(let service) = self { return service }
        return nil
    }
}

@MainActor
struct ServicesView: View {
    @ObservedObject var store: ShalevStore
    @ObservedObject var sync: SyncManager
    let onSell: () -> Void
    var canEdit = true
    var canSell = true
    @State private var search = ""
    @State private var typeFilter: String?
    @State private var sheet: ServiceSheet?
    @State private var showFilterSheet = false

    private var types: [String] { Array(Set(store.activeServices.map(\.type))).sorted() }
    private var services: [ServiceItem] {
        store.activeServices.filter { service in
            (typeFilter == nil || service.type == typeFilter)
                && (search.isEmpty || service.name.localizedCaseInsensitiveContains(search)
                    || service.type.localizedCaseInsensitiveContains(search))
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                ShalevScreenTitle(title: "Servicios")
                HStack(spacing: 2) {
                    Spacer()
                    Button { showFilterSheet = true } label: {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "line.3.horizontal.decrease")
                                .font(.shalev(17, weight: .semibold))
                                .foregroundStyle(typeFilter == nil ? Color.shalevText : Color.shalevOnPrimary)
                                .frame(width: 42, height: 42)
                                .background(typeFilter == nil ? .clear : Color.shalevPrimary, in: Circle())
                                .shalevToque()
                            if typeFilter != nil {
                                Circle().fill(Color.shalevSecondary).frame(width: 9, height: 9).offset(x: -4, y: 5)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .disabled(types.isEmpty)
                    .opacity(types.isEmpty ? 0.4 : 1)
                    .accessibilityLabel(typeFilter.map { "Filtrar servicios. Filtro activo: \($0)" } ?? "Filtrar servicios")
                }
            }
            .padding(.horizontal, 12).padding(.top, 6).padding(.bottom, 8)
            ShalevSearchField(text: $search, placeholder: "Buscar servicios")
                .padding(.horizontal, 16)

            HStack(spacing: 8) {
                Text("\(services.count) \(services.count == 1 ? "servicio" : "servicios")")
                    .font(.shalev(12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                if let typeFilter {
                    Button { self.typeFilter = nil } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "xmark").font(.shalev(9, weight: .bold))
                            Text(typeFilter).font(.shalev(11, weight: .semibold)).lineLimit(1)
                        }
                        .foregroundStyle(Color.shalevOnPrimary)
                        .padding(.horizontal, 9).frame(minHeight: 26)
                        .background(Color.shalevPrimary, in: Capsule())
                        .shalevToque()
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Quitar el filtro \(typeFilter)")
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 9).padding(.bottom, 5)

            if services.isEmpty {
                ScrollView {
                    ShalevEmptyState(symbol: "storefront", title: search.isEmpty ? "Aún no hay servicios" : "No hay resultados",
                                     detail: search.isEmpty ? (canEdit ? "Crea servicios para venderlos por separado de los productos." : "Pide al administrador que agregue servicios.") : "Prueba con otra búsqueda.",
                                     actionTitle: search.isEmpty ? (canEdit ? "Agregar servicio" : nil) : "Limpiar búsqueda") {
                        if search.isEmpty { if canEdit { sheet = .create } } else { search = ""; typeFilter = nil }
                    }
                    .padding(.horizontal, 20)
                }
            } else {
                ScrollView {
                    LazyVStack(spacing: 9) {
                        ForEach(services) { service in
                            if canEdit {
                                Button { sheet = .edit(service) } label: { ServiceRow(service: service, store: store) }
                                    .buttonStyle(.plain)
                            } else {
                                ServiceRow(service: service, store: store)
                            }
                        }
                    }
                    .padding(.horizontal, 16).padding(.bottom, 84).padding(.top, 4)
                }
            }
        }
        .background(Color.shalevBackground)
        .overlay(alignment: .bottomTrailing) {
            if canEdit {
                ShalevFloatingButton(title: "Agregar servicio") { sheet = .create }
                    .padding(.trailing, 18).padding(.bottom, 15)
            }
        }
        .sheet(isPresented: $showFilterSheet) {
            ServiceFilterSheet(types: types, selected: $typeFilter)
                .presentationDetents([.medium])
                .presentationDragIndicator(.visible)
        }
        .sheet(item: $sheet) { route in
            ServiceEditorView(store: store, sync: sync, service: route.service)
                .presentationDetents([.large]).presentationDragIndicator(.visible)
        }
    }
}

@MainActor
private struct ServiceRow: View {
    let service: ServiceItem
    @ObservedObject var store: ShalevStore
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let apilar = dynamicTypeSize >= .xxxLarge
        HStack(spacing: 12) {
            if let data = service.imageData, let photo = UIImage(data: data) {
                Image(uiImage: photo).resizable().scaledToFill()
                    .frame(width: 48, height: 48).clipped().clipShape(RoundedRectangle(cornerRadius: 14))
            } else {
                Image(systemName: "scissors")
                    .font(.shalevIcono(18, weight: .semibold)).foregroundStyle(Color.shalevTertiary)
                    .frame(width: 48, height: 48).background(Color.shalevTertiaryContainer, in: RoundedRectangle(cornerRadius: 14))
            }
            ShalevFilaAdaptable(espaciadoHorizontal: 8, espaciadoVertical: 4) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(service.name).font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevText).lineLimit(apilar ? nil : 1)
                    let capacity = store.availableQuantity(kind: .service, sourceID: service.id) ?? 0
                    Text(service.uses.isEmpty ? service.type : "Alcanza para \(capacity) · \(service.type)")
                        .font(.shalev(12)).foregroundStyle(capacity == 0 && !service.uses.isEmpty ? Color.shalevError : Color.shalevSecondaryText)
                        .lineLimit(apilar ? nil : 1)
                }
            } valor: {
                Text(Cup(cents: service.priceCents).formatted)
                    .font(.shalev(13, weight: .semibold)).foregroundStyle(Color.shalevText).lineLimit(apilar ? nil : 1).minimumScaleFactor(0.78)
            }
        }
        .padding(13)
        .background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.shalevOutlineVariant.opacity(0.55), lineWidth: 0.7))
    }
}

@MainActor
private struct ServiceEditorView: View {
    @ObservedObject var store: ShalevStore
    @ObservedObject var sync: SyncManager
    let service: ServiceItem?
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var type: String
    @State private var description: String
    @State private var price: String
    @State private var recipe: [IngredientUse]
    @State private var imageData: Data?
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var selectedIngredientID: UUID?
    @State private var recipeAmount = "0.100"
    @State private var errorMessage: String?
    @State private var confirmDelete = false
    @State private var working = false
    private enum ServiceFocus: Hashable { case name, description, type }
    @FocusState private var focusedField: ServiceFocus?

    init(store: ShalevStore, sync: SyncManager, service: ServiceItem?) {
        self.store = store
        self.sync = sync
        self.service = service
        _name = State(initialValue: service?.name ?? "")
        _type = State(initialValue: service?.type ?? "General")
        _description = State(initialValue: service?.description ?? "")
        _price = State(initialValue: service.map { Cup(cents: $0.priceCents).inputValue } ?? "")
        _recipe = State(initialValue: service?.uses ?? [])
        _imageData = State(initialValue: service?.imageData)
        _selectedPhoto = State(initialValue: nil)
        _selectedIngredientID = State(initialValue: store.activeProducts.first(where: { $0.kind == .ingredient })?.id)
    }

    private var ingredients: [Product] { store.activeProducts.filter { $0.kind == .ingredient } }

    /// Tipos ya usados en el catálogo, para no reescribirlos a mano.
    private var typeSuggestions: [String] {
        var seen = Set<String>()
        let current = type.trimmingCharacters(in: .whitespacesAndNewlines)
        return store.data.services
            .map { $0.type.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && $0 != current && seen.insert($0).inserted }
            .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    ShalevScreenTitle(title: service == nil ? "Agregar servicio" : "Editar servicio",
                                     subtitle: "Un servicio puede descontar insumos al registrarse.")
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
                                    Label(imageData == nil ? "Añadir foto" : "Cambiar foto", systemImage: "photo")
                                }
                                .buttonStyle(ShalevTonalButtonStyle())
                                .disabled(sync.isSecondary)
                                if imageData != nil {
                                    Button(role: .destructive) { imageData = nil; selectedPhoto = nil } label: { Label("Quitar", systemImage: "trash") }
                                        .buttonStyle(ShalevTonalButtonStyle()).disabled(sync.isSecondary)
                                }
                            }
                        }
                    }
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 13) {
                            Text("* Obligatorio")
                                .font(.shalev(11))
                                .foregroundStyle(Color.shalevSecondaryText)
                            input("Nombre *", text: $name, placeholder: "Corte de pelo",
                                  focus: .name, submit: .next) { focusedField = .description }
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Importe (CUP) *")
                                    .font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                                TextField("0.00", text: $price).keyboardType(.decimalPad).textFieldStyle(ServiceInputStyle())
                            }
                            input("Descripción (máximo 40)", text: $description, placeholder: "A domicilio, 30 minutos",
                                  focus: .description, submit: .next) { focusedField = .type }
                                .onChange(of: description) { _, value in if value.count > 40 { description = String(value.prefix(40)) } }
                            VStack(alignment: .leading, spacing: 7) {
                                input("Tipo *", text: $type, placeholder: "Peluquería",
                                      focus: .type, submit: .done) { focusedField = nil }
                                if !typeSuggestions.isEmpty {
                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack(spacing: 7) {
                                            ForEach(typeSuggestions, id: \.self) { option in
                                                ShalevFilterChip(title: option, selected: type == option) { type = option }
                                            }
                                        }
                                    }
                                }
                            }
                            Divider()
                            recipeEditor
                        }
                    }
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 12) {
                            Button { save() } label: {
                                Label(service == nil ? "Guardar servicio" : "Guardar cambios", systemImage: "checkmark")
                            }
                            .buttonStyle(ShalevPrimaryButtonStyle()).disabled(working)
                            if working { ProgressView().accessibilityLabel("Cargando").frame(maxWidth: .infinity) }
                        }
                    }
                    if service != nil {
                        Button(role: .destructive) { confirmDelete = true } label: {
                            Label("Eliminar servicio", systemImage: "trash")
                                .font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevError)
                                .frame(maxWidth: .infinity, minHeight: 46)
                        }
                        .padding(.horizontal, 20).padding(.bottom, 18)
                    }
                }
                .padding(18)
            }
            .background(Color.shalevBackground)
            .onChange(of: selectedPhoto) { _, _ in loadPhoto() }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Cancelar") { dismiss() }.foregroundStyle(Color.shalevSecondaryText) }
                ToolbarItem(placement: .topBarTrailing) { Button("Guardar") { save() }.fontWeight(.semibold).foregroundStyle(Color.shalevPrimary).disabled(working) }
            }
            .alert("Revisa los datos", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Entendido", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
            .confirmationDialog("¿Eliminar este servicio?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Eliminar", role: .destructive) { delete() }
                Button("Cancelar", role: .cancel) { }
            } message: { Text("Desaparece de Servicios. Las ventas ya registradas no cambian.") }
        }
    }

    private var recipeEditor: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Insumos por servicio").font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevText)
            Text("Si no añades insumos, el servicio no tiene límite de disponibilidad.")
                .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
            if ingredients.isEmpty {
                Text("No hay insumos creados todavía.").font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
            } else {
                ForEach(recipe) { use in
                    HStack {
                        Text(ingredients.first(where: { $0.id == use.ingredientID })?.name ?? "Insumo eliminado")
                            .font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevText)
                        Spacer()
                        Text(FixedNumber.formatMilli(use.milliUnits)).font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
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
                HStack(spacing: 9) {
                    TextField("Cantidad por servicio", text: $recipeAmount)
                        .keyboardType(.decimalPad).textFieldStyle(ServiceInputStyle())
                    Button("Agregar") { addIngredient() }.buttonStyle(ShalevTonalButtonStyle())
                }
            }
        }
    }

    private func input(_ title: String, text: Binding<String>, placeholder: String, multiline: Bool = false,
                       focus: ServiceFocus? = nil, submit: SubmitLabel = .done,
                       onSubmit: (() -> Void)? = nil) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
            if multiline {
                TextField(placeholder, text: text, axis: .vertical).lineLimit(2...4).textFieldStyle(ServiceInputStyle())
            } else if let focus {
                TextField(placeholder, text: text).textFieldStyle(ServiceInputStyle())
                    .focused($focusedField, equals: focus)
                    .submitLabel(submit)
                    .onSubmit { onSubmit?() }
            } else {
                TextField(placeholder, text: text).textFieldStyle(ServiceInputStyle())
                    .submitLabel(submit)
            }
        }
    }

    private func loadPhoto() {
        guard let selectedPhoto else { return }
        Task { @MainActor in
            do {
                guard let data = try await selectedPhoto.loadTransferable(type: Data.self),
                      let image = UIImage(data: data), image.size.width > 0, image.size.height > 0 else {
                    errorMessage = "No se pudo leer esa imagen."; return
                }
                let scale = min(1, 1_200 / max(image.size.width, image.size.height))
                let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
                let renderer = UIGraphicsImageRenderer(size: size)
                let compressed = renderer.jpegData(withCompressionQuality: 0.82) { _ in
                    image.draw(in: CGRect(origin: .zero, size: size))
                }
                imageData = compressed
            } catch { errorMessage = "No se pudo cargar la foto." }
        }
    }

    private func addIngredient() {
        guard let id = selectedIngredientID, let amount = FixedNumber.parse(recipeAmount, multiplier: 1_000), amount > 0 else {
            errorMessage = "Escribe una cantidad mayor que cero."; return
        }
        if let index = recipe.firstIndex(where: { $0.ingredientID == id }) { recipe[index].milliUnits = amount }
        else { recipe.append(IngredientUse(ingredientID: id, milliUnits: amount)) }
        recipeAmount = "0.100"
    }

    private func save() {
        guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { errorMessage = "Escribe el nombre del servicio."; return }
        guard let cents = Cup.parse(price)?.cents else { errorMessage = "Escribe un importe válido en CUP."; return }
        let updated = ServiceItem(id: service?.id ?? UUID(), name: name, type: type, description: description,
                                  imageData: imageData, priceCents: cents, uses: recipe, isActive: service?.isActive ?? true,
                                  createdAt: service?.createdAt ?? Date(), syncPhotoName: service?.syncPhotoName)
        if sync.isSecondary {
            working = true
            Task {
                do {
                    var row = AndroidBackupCodec.exportService(updated)
                    if let name = updated.syncPhotoName { row["fotoUri"] = name }
                    _ = try await sync.executeRemote(SyncJSON.message("servicio_guardar", ["servicio": row]))
                    dismiss()
                } catch { errorMessage = error.localizedDescription; working = false }
            }
        } else {
            do { try store.saveService(updated); dismiss() }
            catch { errorMessage = error.localizedDescription }
        }
    }

    private func delete() {
        guard let service else { return }
        if sync.isSecondary {
            working = true
            Task {
                do {
                    _ = try await sync.executeRemote(SyncJSON.message("servicio_eliminar", ["id": AndroidID.int64("service", service.id)]))
                    dismiss()
                } catch { errorMessage = error.localizedDescription; working = false }
            }
        } else {
            do { try store.deleteService(service.id); dismiss() }
            catch { errorMessage = error.localizedDescription }
        }
    }
}

private struct ServiceInputStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration.font(.shalev(14)).foregroundStyle(Color.shalevText)
            .padding(11).background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 11).stroke(Color.shalevOutlineVariant, lineWidth: 1))
    }
}

/// Filtro por tipo de servicio, como la hoja de Android.
private struct ServiceFilterSheet: View {
    let types: [String]
    @Binding var selected: String?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                ShalevCard(padding: 5) {
                    VStack(spacing: 0) {
                        option("Todos", isSelected: selected == nil) { selected = nil }
                        ForEach(types, id: \.self) { type in
                            option(type, isSelected: selected == type) { selected = type }
                        }
                    }
                }
                .padding(18)
            }
            .background(Color.shalevBackground)
            .navigationTitle("Filtrar servicios")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Quitar filtro") { selected = nil }
                        .foregroundStyle(Color.shalevPrimary)
                        .disabled(selected == nil)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Aplicar") { dismiss() }.foregroundStyle(Color.shalevPrimary)
                }
            }
        }
    }

    private func option(_ title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(title).font(.shalev(14, weight: .medium)).foregroundStyle(Color.shalevText)
                Spacer()
                if isSelected { Image(systemName: "checkmark").foregroundStyle(Color.shalevPrimary) }
            }
            .padding(.horizontal, 12).padding(.vertical, 11)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
