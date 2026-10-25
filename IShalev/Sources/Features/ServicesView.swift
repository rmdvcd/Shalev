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
    let onSell: () -> Void
    var canEdit = true
    var canSell = true
    @State private var search = ""
    @State private var typeFilter: String?
    @State private var sheet: ServiceSheet?

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
            HStack(spacing: 8) {
                ShalevScreenTitle(title: "Servicios")
                if canSell {
                    Button(action: onSell) {
                        Image(systemName: "cart.fill").font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(Color.shalevPrimary).frame(width: 42, height: 42)
                    }
                    .accessibilityLabel("Vender servicio")
                }
            }
            .padding(.horizontal, 17).padding(.top, 7).padding(.bottom, 10)
            ShalevSearchField(text: $search, placeholder: "Buscar servicios")
                .padding(.horizontal, 17)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ShalevFilterChip(title: "Todos", selected: typeFilter == nil) { typeFilter = nil }
                    ForEach(types, id: \.self) { type in
                        ShalevFilterChip(title: type, selected: typeFilter == type) { typeFilter = type }
                    }
                }
                .padding(.horizontal, 17).padding(.vertical, 11)
            }
            Text("\(services.count) \(services.count == 1 ? "servicio" : "servicios")")
                .font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText).padding(.bottom, 5)

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
        .sheet(item: $sheet) { route in
            ServiceEditorView(store: store, service: route.service)
                .presentationDetents([.large]).presentationDragIndicator(.visible)
        }
    }
}

@MainActor
private struct ServiceRow: View {
    let service: ServiceItem
    @ObservedObject var store: ShalevStore

    var body: some View {
        HStack(spacing: 12) {
            if let data = service.imageData, let photo = UIImage(data: data) {
                Image(uiImage: photo).resizable().scaledToFill()
                    .frame(width: 48, height: 48).clipped().clipShape(RoundedRectangle(cornerRadius: 14))
            } else {
                Image(systemName: "scissors")
                    .font(.system(size: 18, weight: .semibold)).foregroundStyle(Color.shalevTertiary)
                    .frame(width: 48, height: 48).background(Color.shalevTertiaryContainer, in: RoundedRectangle(cornerRadius: 14))
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(service.name).font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText).lineLimit(1)
                let capacity = store.availableQuantity(kind: .service, sourceID: service.id) ?? 0
                Text(service.uses.isEmpty ? service.type : "Alcanza para \(capacity) · \(service.type)")
                    .font(.system(size: 12)).foregroundStyle(capacity == 0 && !service.uses.isEmpty ? Color.shalevError : Color.shalevSecondaryText)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            Text(Cup(cents: service.priceCents).formatted)
                .font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText).lineLimit(1).minimumScaleFactor(0.78)
        }
        .padding(13)
        .background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.shalevOutlineVariant.opacity(0.55), lineWidth: 0.7))
    }
}

@MainActor
private struct ServiceEditorView: View {
    @ObservedObject var store: ShalevStore
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

    init(store: ShalevStore, service: ServiceItem?) {
        self.store = store
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
                                    .font(.system(size: 30)).foregroundStyle(Color.shalevTertiary)
                                    .frame(maxWidth: .infinity).frame(height: 95)
                                    .background(Color.shalevSurfaceVariant, in: RoundedRectangle(cornerRadius: 14))
                            }
                            HStack(spacing: 10) {
                                PhotosPicker(selection: $selectedPhoto, matching: .images) {
                                    Label(imageData == nil ? "Añadir foto" : "Cambiar foto", systemImage: "photo")
                                }
                                .buttonStyle(ShalevTonalButtonStyle())
                                if imageData != nil {
                                    Button(role: .destructive) { imageData = nil; selectedPhoto = nil } label: { Label("Quitar", systemImage: "trash") }
                                        .buttonStyle(ShalevTonalButtonStyle())
                                }
                            }
                        }
                    }
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 13) {
                            input("Nombre", text: $name, placeholder: "Ej. Corte de cabello")
                            input("Tipo", text: $type, placeholder: "Ej. Peluquería")
                            input("Descripción (opcional, máximo 40)", text: $description, placeholder: "Detalles del servicio")
                                .onChange(of: description) { _, value in if value.count > 40 { description = String(value.prefix(40)) } }
                            Divider()
                            recipeEditor
                        }
                    }
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Importe (CUP)").font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                            TextField("0.00", text: $price).keyboardType(.decimalPad).textFieldStyle(ServiceInputStyle())
                            Button { save() } label: { Label("Guardar", systemImage: "checkmark") }
                                .buttonStyle(ShalevPrimaryButtonStyle())
                        }
                    }
                    if service != nil {
                        Button(role: .destructive) { confirmDelete = true } label: {
                            Label("Eliminar servicio", systemImage: "trash")
                                .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevError)
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
                ToolbarItem(placement: .topBarTrailing) { Button("Guardar") { save() }.fontWeight(.semibold).foregroundStyle(Color.shalevPrimary) }
            }
            .alert("Revisa los datos", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Entendido", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
            .confirmationDialog("¿Eliminar este servicio?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Eliminar", role: .destructive) { delete() }
                Button("Cancelar", role: .cancel) { }
            } message: { Text("Las operaciones ya registradas conservarán su historial.") }
        }
    }

    private var recipeEditor: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Insumos por servicio").font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
            Text("Si no añades insumos, el servicio no tiene límite de disponibilidad.")
                .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
            if ingredients.isEmpty {
                Text("No hay insumos creados todavía.").font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
            } else {
                ForEach(recipe) { use in
                    HStack {
                        Text(ingredients.first(where: { $0.id == use.ingredientID })?.name ?? "Insumo eliminado")
                            .font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevText)
                        Spacer()
                        Text(FixedNumber.formatMilli(use.milliUnits)).font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
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

    private func input(_ title: String, text: Binding<String>, placeholder: String, multiline: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
            if multiline {
                TextField(placeholder, text: text, axis: .vertical).lineLimit(2...4).textFieldStyle(ServiceInputStyle())
            } else {
                TextField(placeholder, text: text).textFieldStyle(ServiceInputStyle())
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
                imageData = renderer.jpegData(withCompressionQuality: 0.82) { _ in
                    image.draw(in: CGRect(origin: .zero, size: size))
                }
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
                                  createdAt: service?.createdAt ?? Date())
        do { try store.saveService(updated); dismiss() }
        catch { errorMessage = error.localizedDescription }
    }

    private func delete() {
        guard let service else { return }
        do { try store.deleteService(service.id); dismiss() }
        catch { errorMessage = error.localizedDescription }
    }
}

private struct ServiceInputStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration.font(.system(size: 14)).foregroundStyle(Color.shalevText)
            .padding(11).background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 11).stroke(Color.shalevOutlineVariant, lineWidth: 1))
    }
}
