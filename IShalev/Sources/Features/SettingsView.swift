import SwiftUI
import UniformTypeIdentifiers
import UIKit
import LocalAuthentication

private enum SettingsSheet: String, Identifiable, Equatable {
    case license, profile, payment, prices, backup, export, employees, updates, help, support, about
    var id: String { rawValue }
    var title: String {
        switch self {
        case .license: "Licencia"
        case .profile: "Perfil"
        case .payment: "Pago electrónico"
        case .prices: "Precios"
        case .backup: "Respaldo"
        case .export: "Exportar"
        case .employees: "Empleados"
        case .updates: "Actualizaciones"
        case .help: "Ayuda"
        case .support: "Soporte"
        case .about: "Acerca de Shalev"
        }
    }
}

@MainActor
struct SettingsView: View {
    @ObservedObject var store: ShalevStore
    @ObservedObject var licenseManager: GLLicenseManager
    @Binding var selectedOperatorID: String
    let isAdministrator: Bool
    let canChangePrices: Bool
    let canExport: Bool
    let canExportInventory: Bool
    let canExportServices: Bool
    let canExportSales: Bool
    @AppStorage("shalev.lock.enabled") private var appLockEnabled = false
    @AppStorage("shalev.module.sales") private var salesEnabled = true
    @AppStorage("shalev.module.inventory") private var inventoryEnabled = true
    @AppStorage("shalev.module.services") private var servicesEnabled = true
    @State private var sheet: SettingsSheet?
    @State private var canUseDeviceAuthentication = false

    var body: some View {
        ScrollView {
            VStack(spacing: 15) {
                ShalevScreenTitle(title: "Ajustes", subtitle: store.data.settings.profile.businessName)
                    .padding(.top, 8)
                ShalevCard(padding: 5) {
                    VStack(spacing: 0) {
                        if isAdministrator {
                            settingsRow("Licencia", subtitle: licenseManager.state.title, symbol: "checkmark.seal.fill", sheet: .license)
                            settingsDivider
                            settingsRow("Perfil", subtitle: "Datos del negocio y del titular", symbol: "person.crop.circle", sheet: .profile)
                            settingsDivider
                            settingsRow("Pago electrónico", subtitle: "Tarjetas y teléfonos para Transfermóvil", symbol: "creditcard.fill", sheet: .payment)
                        }
                        if canChangePrices {
                            if isAdministrator { settingsDivider }
                            settingsRow("Precios", subtitle: "Descuentos y recargos automáticos", symbol: "dollarsign.circle.fill", sheet: .prices)
                        }
                        if isAdministrator {
                            settingsDivider
                            settingsRow("Empleados", subtitle: "Permisos de módulos en este teléfono", symbol: "person.2.fill", sheet: .employees)
                            settingsDivider
                            settingsRow("Actualizaciones", subtitle: "Comprobar nuevas publicaciones de IShalev", symbol: "arrow.down.circle.fill", sheet: .updates)
                        }
                    }
                }
                if isAdministrator {
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 12) {
                            ShalevSectionHeader(title: "Módulos del negocio")
                            Toggle(isOn: $salesEnabled) {
                                Label("Ventas de productos", systemImage: "cart.fill")
                                    .font(.system(size: 13, weight: .medium))
                            }
                            Toggle(isOn: $inventoryEnabled) {
                                Label("Inventario", systemImage: "archivebox.fill")
                                    .font(.system(size: 13, weight: .medium))
                            }
                            .disabled(inventoryEnabled && !salesEnabled && !servicesEnabled)
                            Toggle(isOn: $servicesEnabled) {
                                Label("Servicios", systemImage: "storefront.fill")
                                    .font(.system(size: 13, weight: .medium))
                            }
                            .disabled(servicesEnabled && !salesEnabled && !inventoryEnabled)
                            Text("Ventas de productos incluye el acceso al inventario. Debe quedar al menos un módulo activo; los cambios afectan las pestañas y los registros visibles.")
                                .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                        }
                        .tint(Color.shalevPrimary)
                    }
                }
                ShalevCard(padding: 5) {
                    VStack(spacing: 0) {
                        if isAdministrator {
                            settingsRow("Respaldo", subtitle: "Exportar o importar una copia cifrada", symbol: "externaldrive.fill", sheet: .backup)
                            settingsDivider
                        }
                        if canExport {
                            settingsRow("Exportar", subtitle: "PDF y tablas compatibles con Excel", symbol: "square.and.arrow.up.fill", sheet: .export)
                            settingsDivider
                        }
                        settingsRow("Ayuda", subtitle: "Guía rápida de los flujos principales", symbol: "questionmark.circle.fill", sheet: .help)
                        settingsDivider
                        settingsRow("Soporte", subtitle: "Contacto del desarrollador por WhatsApp o SMS", symbol: "message.fill", sheet: .support)
                    }
                }
                ShalevCard(padding: 5) {
                    settingsRow("Acerca de Shalev", subtitle: "Aplicación nativa para iOS · Versión 1.0", symbol: "info.circle.fill", sheet: .about)
                }
                ShalevCard {
                    VStack(alignment: .leading, spacing: 7) {
                        Label("Privacidad", systemImage: "lock.shield.fill")
                            .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                        Text("Shalev guarda los datos en este teléfono y no usa una cuenta, servidor, analítica ni anuncios. El almacenamiento local se cifra con AES-GCM y una clave protegida por el llavero de iOS.")
                            .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                        Toggle("Bloquear al salir de Shalev", isOn: $appLockEnabled)
                            .font(.system(size: 13, weight: .medium))
                            .disabled(!canUseDeviceAuthentication || !isAdministrator)
                        if !canUseDeviceAuthentication {
                            Text("Configura Face ID, Touch ID o un código de acceso en el dispositivo para activar el bloqueo.")
                                .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                        }
                    }
                    .tint(Color.shalevPrimary)
                }
                .padding(.bottom, 18)
            }
            .padding(.horizontal, 17)
        }
        .background(Color.shalevBackground)
        .task {
            let context = LAContext()
            var error: NSError?
            canUseDeviceAuthentication = context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error)
        }
        .sheet(item: $sheet) { route in
            if route == .license {
                LicenseView(store: store, license: licenseManager, blocking: false)
                    .presentationDetents([.large])
                    .presentationDragIndicator(.visible)
            } else {
                NavigationStack {
                    Group {
                        switch route {
                        case .license: EmptyView()
                        case .profile: ProfileSettingsView(store: store)
                        case .payment: PaymentSettingsView(store: store)
                        case .prices: PriceAdjustmentsView(store: store)
                        case .backup: BackupSettingsView(store: store)
                        case .export: ExportCenterView(store: store, canExportInventory: canExportInventory,
                                                       canExportServices: canExportServices, canExportSales: canExportSales)
                        case .employees: EmployeeSettingsView(store: store, selectedOperatorID: $selectedOperatorID)
                        case .updates: UpdatesView()
                        case .help: HelpView()
                        case .support: SupportView()
                        case .about: AboutView()
                        }
                    }
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("Cerrar") { sheet = nil }.foregroundStyle(Color.shalevPrimary)
                        }
                    }
                }
                .presentationDetents(route == .help || route == .about ? [.medium, .large] : [.large])
                .presentationDragIndicator(.visible)
            }
        }
    }

    private var settingsDivider: some View { Color.shalevOutlineVariant.opacity(0.65).frame(height: 0.6).padding(.leading, 58) }

    private func settingsRow(_ title: String, subtitle: String, symbol: String, sheet route: SettingsSheet) -> some View {
        Button { sheet = route } label: {
            HStack(spacing: 12) {
                Image(systemName: symbol).font(.system(size: 18, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                    .frame(width: 42, height: 42).background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 13))
                VStack(alignment: .leading, spacing: 3) {
                    Text(title).font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
                    Text(subtitle).font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText).lineLimit(2)
                }
                Spacer(minLength: 5)
                Image(systemName: "chevron.right").font(.system(size: 11, weight: .semibold)).foregroundStyle(Color.shalevSecondaryText)
            }
            .padding(12).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

@MainActor
private struct ProfileSettingsView: View {
    @ObservedObject var store: ShalevStore
    @Environment(\.dismiss) private var dismiss
    @State private var business: String
    @State private var owner: String
    @State private var identification: String
    @State private var errorMessage: String?

    init(store: ShalevStore) {
        self.store = store
        _business = State(initialValue: store.data.settings.profile.businessName)
        _owner = State(initialValue: store.data.settings.profile.ownerName)
        _identification = State(initialValue: store.data.settings.profile.identification)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 15) {
                ShalevScreenTitle(title: "Perfil", subtitle: "Datos básicos del negocio")
                ShalevCard {
                    VStack(alignment: .leading, spacing: 12) {
                        labeledField("Nombre del negocio", text: $business, placeholder: "Mi negocio")
                        labeledField("Nombre del titular", text: $owner, placeholder: "Nombre y apellidos")
                        labeledField("Carné de identidad (opcional)", text: $identification, placeholder: "Número de identidad", keyboard: .numberPad)
                        Text("Estos datos se conservan únicamente en el dispositivo.")
                            .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                    }
                }
                Button { save() } label: { Label("Guardar perfil", systemImage: "checkmark") }
                    .buttonStyle(ShalevPrimaryButtonStyle())
            }
            .padding(18)
        }
        .background(Color.shalevBackground)
        .alert("No se pudo guardar", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Entendido", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

    private func save() {
        guard !business.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { errorMessage = "Escribe el nombre del negocio."; return }
        var profile = store.data.settings.profile
        profile.businessName = business.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.ownerName = owner.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.identification = identification.trimmingCharacters(in: .whitespacesAndNewlines)
        do { try store.saveProfile(profile); dismiss() }
        catch { errorMessage = error.localizedDescription }
    }
}

@MainActor
private struct PaymentSettingsView: View {
    @ObservedObject var store: ShalevStore
    @Environment(\.dismiss) private var dismiss
    @State private var phones: [TransferPhone]
    @State private var cards: [TransferCard]
    @State private var paymentPhoneID: UUID?
    @State private var paymentCardID: UUID?
    @State private var errorMessage: String?

    init(store: ShalevStore) {
        self.store = store
        let profile = store.data.settings.profile
        _phones = State(initialValue: profile.availablePhones)
        _cards = State(initialValue: profile.availableCards)
        _paymentPhoneID = State(initialValue: profile.paymentPhone?.id)
        _paymentCardID = State(initialValue: profile.paymentCard?.id)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 15) {
                ShalevScreenTitle(title: "Pago electrónico", subtitle: "Elige la tarjeta y el teléfono que recibirán las transferencias")
                ShalevCard {
                    VStack(alignment: .leading, spacing: 12) {
                        ShalevSectionHeader(title: "Tarjetas y cuentas")
                        ForEach($cards) { $card in
                            VStack(spacing: 8) {
                                labeledField("Alias (opcional)", text: $card.alias, placeholder: "Ej. Cuenta principal")
                                labeledField("Tarjeta o cuenta", text: $card.number, placeholder: "12–20 dígitos", keyboard: .numberPad)
                                Button(role: .destructive) { removeCard(card.id) } label: {
                                    Label("Eliminar tarjeta", systemImage: "trash")
                                }
                                .buttonStyle(ShalevTonalButtonStyle())
                            }
                            if card.id != cards.last?.id { Divider() }
                        }
                        Button { cards.append(TransferCard(number: "")) } label: { Label("Agregar tarjeta", systemImage: "plus") }
                            .buttonStyle(ShalevTonalButtonStyle())
                        if !cards.isEmpty {
                            Picker("Tarjeta para cobros", selection: $paymentCardID) {
                                Text("Ninguna").tag(UUID?.none)
                                ForEach(cards) { card in Text(cardLabel(card)).tag(UUID?.some(card.id)) }
                            }
                            .font(.system(size: 13, weight: .medium))
                        }
                    }
                }
                ShalevCard {
                    VStack(alignment: .leading, spacing: 12) {
                        ShalevSectionHeader(title: "Teléfonos")
                        ForEach($phones) { $phone in
                            VStack(spacing: 8) {
                                labeledField("Alias (opcional)", text: $phone.alias, placeholder: "Ej. Móvil del negocio")
                                labeledField("Teléfono", text: $phone.number, placeholder: "+53 5212 3456", keyboard: .phonePad)
                                Button(role: .destructive) { removePhone(phone.id) } label: {
                                    Label("Eliminar teléfono", systemImage: "trash")
                                }
                                .buttonStyle(ShalevTonalButtonStyle())
                            }
                            if phone.id != phones.last?.id { Divider() }
                        }
                        Button { phones.append(TransferPhone(number: "")) } label: { Label("Agregar teléfono", systemImage: "plus") }
                            .buttonStyle(ShalevTonalButtonStyle())
                        if !phones.isEmpty {
                            Picker("Teléfono para cobros", selection: $paymentPhoneID) {
                                Text("Ninguno").tag(UUID?.none)
                                ForEach(phones) { phone in Text(phoneLabel(phone)).tag(UUID?.some(phone.id)) }
                            }
                            .font(.system(size: 13, weight: .medium))
                        }
                    }
                }
                Text("La tarjeta seleccionada es obligatoria para cobrar por transferencia. El teléfono se incorpora al QR cuando existe; el importe se muestra por separado.")
                    .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                Button { save() } label: { Label("Guardar datos de cobro", systemImage: "checkmark") }
                    .buttonStyle(ShalevPrimaryButtonStyle())
            }
            .padding(18)
        }
        .background(Color.shalevBackground)
        .alert("No se pudo guardar", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Entendido", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

    private func cardLabel(_ card: TransferCard) -> String {
        let masked = card.number.count >= 4 ? "•••• \(card.number.suffix(4))" : "Nueva tarjeta"
        return card.alias.isEmpty ? masked : "\(card.alias) · \(masked)"
    }

    private func phoneLabel(_ phone: TransferPhone) -> String {
        phone.alias.isEmpty ? phone.number : "\(phone.alias) · \(phone.number)"
    }

    private func removeCard(_ id: UUID) {
        cards.removeAll { $0.id == id }
        if paymentCardID == id { paymentCardID = nil }
    }

    private func removePhone(_ id: UUID) {
        phones.removeAll { $0.id == id }
        if paymentPhoneID == id { paymentPhoneID = nil }
    }

    private func save() {
        var profile = store.data.settings.profile
        profile.transferCards = cards
        profile.transferPhones = phones
        profile.paymentCardID = paymentCardID
        profile.paymentPhoneID = paymentPhoneID
        do { try store.saveProfile(profile); dismiss() }
        catch { errorMessage = error.localizedDescription }
    }
}

@MainActor
private struct PriceAdjustmentsView: View {
    @ObservedObject var store: ShalevStore
    @State private var editor: PriceAdjustment?
    @State private var showCreate = false
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                ShalevScreenTitle(title: "Precios", subtitle: "Descuentos y recargos automáticos")
                ShalevCard {
                    VStack(alignment: .leading, spacing: 9) {
                        Text("Los ajustes activos se aplican al carrito antes de cobrar. Se pueden limitar por método de pago o por un importe mínimo.")
                            .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                        if store.data.settings.priceAdjustments.isEmpty {
                            Text("No hay ajustes creados.").font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevText).padding(.top, 7)
                        }
                        ForEach(store.data.settings.priceAdjustments) { adjustment in
                            HStack(spacing: 10) {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(adjustment.name).font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
                                    Text(adjustmentSummary(adjustment)).font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                                }
                                Spacer()
                                Text(percentText(adjustment.basisPoints)).font(.system(size: 13, weight: .bold))
                                    .foregroundStyle(adjustment.basisPoints >= 0 ? Color.shalevSecondary : Color.shalevSuccess)
                                Toggle(adjustment.name, isOn: Binding(get: { adjustment.active }, set: { value in
                                    var changed = adjustment; changed.active = value
                                    do { try store.savePriceAdjustment(changed) } catch { errorMessage = error.localizedDescription }
                                }))
                                .labelsHidden().tint(Color.shalevPrimary)
                                Button { editor = adjustment } label: { Image(systemName: "pencil.circle.fill").foregroundStyle(Color.shalevPrimary) }
                                    .accessibilityLabel("Editar ajuste \(adjustment.name)")
                            }
                            .padding(.vertical, 7)
                            if adjustment.id != store.data.settings.priceAdjustments.last?.id { Divider() }
                        }
                    }
                }
                Button { showCreate = true } label: { Label("Agregar ajuste", systemImage: "plus") }
                    .buttonStyle(ShalevPrimaryButtonStyle())
            }
            .padding(18)
        }
        .background(Color.shalevBackground)
        .sheet(isPresented: $showCreate) {
            PriceAdjustmentEditor(store: store, adjustment: nil)
                .presentationDetents([.large]).presentationDragIndicator(.visible)
        }
        .sheet(item: $editor) { adjustment in
            PriceAdjustmentEditor(store: store, adjustment: adjustment)
                .presentationDetents([.large]).presentationDragIndicator(.visible)
        }
        .alert("No se pudo actualizar", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Entendido", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

    private func adjustmentSummary(_ adjustment: PriceAdjustment) -> String {
        let payment = adjustment.paymentMethod?.rawValue ?? "Todos los pagos"
        let minimum = adjustment.minimumAmountCents.map { " · desde \(Cup(cents: $0).formatted)" } ?? ""
        let articles = adjustment.productIDs.map { " · \($0.count) artículo\($0.count == 1 ? "" : "s")" }
            ?? " · todos los artículos (anterior)"
        return "\(payment)\(minimum)\(articles)"
    }
    private func percentText(_ basisPoints: Int) -> String {
        let value = Decimal(basisPoints) / Decimal(100)
        return "\(NSDecimalNumber(decimal: value).stringValue)%"
    }
}

@MainActor
private struct PriceAdjustmentEditor: View {
    @ObservedObject var store: ShalevStore
    let adjustment: PriceAdjustment?
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var percent: String
    @State private var method: PaymentMethod?
    @State private var minimum: String
    @State private var productIDs: Set<UUID>
    @State private var errorMessage: String?
    @State private var confirmDelete = false

    init(store: ShalevStore, adjustment: PriceAdjustment?) {
        self.store = store
        self.adjustment = adjustment
        _name = State(initialValue: adjustment?.name ?? "")
        _percent = State(initialValue: adjustment.map { NSDecimalNumber(decimal: Decimal($0.basisPoints) / Decimal(100)).stringValue } ?? "10")
        _method = State(initialValue: adjustment?.paymentMethod)
        _minimum = State(initialValue: adjustment?.minimumAmountCents.map { Cup(cents: $0).inputValue } ?? "")
        _productIDs = State(initialValue: adjustment?.productIDs ?? Set(store.activeProducts.map(\.id)))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    ShalevScreenTitle(title: adjustment == nil ? "Nuevo ajuste" : "Editar ajuste")
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 12) {
                            labeledField("Nombre", text: $name, placeholder: "Ej. Descuento en efectivo")
                            labeledField("Porcentaje", text: $percent, placeholder: "10 o -5", keyboard: .decimalPad)
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Método de pago").font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                                Picker("Método", selection: Binding(get: { method?.rawValue ?? "Todos" }, set: { value in
                                    method = PaymentMethod(rawValue: value)
                                })) {
                                    Text("Todos").tag("Todos")
                                    ForEach(PaymentMethod.allCases) { Text($0.rawValue).tag($0.rawValue) }
                                }
                                .pickerStyle(.segmented)
                            }
                            labeledField("Importe mínimo (opcional, CUP)", text: $minimum, placeholder: "Sin mínimo", keyboard: .decimalPad)
                            Divider()
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Artículos incluidos").font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText)
                                Text("El ajuste solo se aplica a los artículos seleccionados. Si hay varios ajustes para un mismo artículo, se suman.")
                                    .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                                ForEach(store.activeProducts) { product in
                                    Toggle(isOn: Binding(get: { productIDs.contains(product.id) }, set: { selected in
                                        if selected { productIDs.insert(product.id) } else { productIDs.remove(product.id) }
                                    })) {
                                        HStack {
                                            Text(product.name).font(.system(size: 13)).foregroundStyle(Color.shalevText)
                                            Spacer()
                                            Text(Cup(cents: product.priceCents).formatted)
                                                .font(.system(size: 11, weight: .semibold)).foregroundStyle(Color.shalevSecondaryText)
                                        }
                                    }
                                    .tint(Color.shalevPrimary)
                                }
                                if store.activeProducts.isEmpty {
                                    Text("Crea primero un artículo en Inventario.").font(.system(size: 12)).foregroundStyle(Color.shalevError)
                                }
                            }
                            Text("Usa porcentaje negativo para un descuento y positivo para un recargo.")
                                .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                            Button { save() } label: { Label("Guardar ajuste", systemImage: "checkmark") }
                                .buttonStyle(ShalevPrimaryButtonStyle())
                        }
                    }
                    if adjustment != nil {
                        Button(role: .destructive) { confirmDelete = true } label: {
                            Label("Eliminar ajuste", systemImage: "trash").foregroundStyle(Color.shalevError)
                                .frame(maxWidth: .infinity, minHeight: 44)
                        }
                    }
                }
                .padding(18)
            }
            .background(Color.shalevBackground)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Cerrar") { dismiss() }.foregroundStyle(Color.shalevPrimary) } }
            .alert("Revisa los datos", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Entendido", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
            .confirmationDialog("¿Eliminar este ajuste?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Eliminar", role: .destructive) { delete() }
                Button("Cancelar", role: .cancel) { }
            }
        }
    }

    private func save() {
        guard let percentValue = Decimal(string: percent.replacingOccurrences(of: ",", with: "."), locale: Locale(identifier: "en_US_POSIX")),
              percentValue >= Decimal(-90), percentValue <= Decimal(100), percentValue != 0 else {
            errorMessage = "El porcentaje debe ser distinto de cero y estar entre −90 % y +100 %."; return
        }
        var basis = percentValue * Decimal(100)
        var rounded = Decimal()
        NSDecimalRound(&rounded, &basis, 0, .plain)
        let minimumCents = minimum.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : Cup.parse(minimum)?.cents
        if !minimum.isEmpty && minimumCents == nil { errorMessage = "Escribe un importe mínimo válido."; return }
        guard !productIDs.isEmpty else { errorMessage = "Selecciona al menos un artículo."; return }
        let result = PriceAdjustment(id: adjustment?.id ?? UUID(), name: name,
                                     basisPoints: NSDecimalNumber(decimal: rounded).intValue,
                                     productIDs: productIDs, paymentMethod: method, minimumAmountCents: minimumCents,
                                     active: adjustment?.active ?? true)
        do { try store.savePriceAdjustment(result); dismiss() }
        catch { errorMessage = error.localizedDescription }
    }

    private func delete() {
        guard let adjustment else { return }
        do { try store.deletePriceAdjustment(adjustment.id); dismiss() }
        catch { errorMessage = error.localizedDescription }
    }
}

private enum BackupPasswordMode: String, Identifiable, Equatable {
    case export, restore
    var id: String { rawValue }
}

@MainActor
private struct BackupSettingsView: View {
    @ObservedObject var store: ShalevStore
    @State private var passwordSheet: BackupPasswordMode?
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var exportDocument = ShalevBackupDocument(data: Data())
    @State private var showExporter = false
    @State private var showImporter = false
    @State private var pendingBackup: Data?
    @State private var showRestoreConfirmation = false
    @State private var alertMessage: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                ShalevScreenTitle(title: "Respaldo", subtitle: "Copia cifrada de los datos de Shalev para iOS")
                ShalevCard {
                    VStack(alignment: .leading, spacing: 9) {
                        Label("Protege tu copia con una contraseña", systemImage: "lock.fill")
                            .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                        Text("La exportación usa AES-GCM y PBKDF2. Guarda la contraseña: no se puede recuperar y el respaldo iOS no es compatible con el formato Android .shalev.")
                            .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText).fixedSize(horizontal: false, vertical: true)
                    }
                }
                Button { password = ""; confirmPassword = ""; passwordSheet = .export } label: {
                    Label("Exportar respaldo cifrado", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(ShalevPrimaryButtonStyle())
                Button { showImporter = true } label: {
                    Label("Importar respaldo", systemImage: "square.and.arrow.down")
                }
                .buttonStyle(ShalevTonalButtonStyle())
                    .frame(maxWidth: .infinity)
                Text("Importar reemplaza los datos actuales del dispositivo. Haz primero una copia si quieres conservarlos.")
                    .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText).multilineTextAlignment(.center)
            }
            .padding(18)
        }
        .background(Color.shalevBackground)
        .sheet(item: $passwordSheet) { mode in
            BackupPasswordSheet(mode: mode, password: $password, confirmPassword: $confirmPassword) {
                passwordSheet = nil
                handlePassword(mode)
            }
            .presentationDetents([.medium, .large]).presentationDragIndicator(.visible)
        }
        .fileExporter(isPresented: $showExporter, document: exportDocument, contentType: .shalevBackup,
                      defaultFilename: "respaldo-shalev-ios") { result in
            if case .failure(let error) = result { alertMessage = error.localizedDescription }
            password = ""; confirmPassword = ""
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.shalevBackup], allowsMultipleSelection: false) { result in
            do {
                guard let url = try result.get().first else { return }
                let hasAccess = url.startAccessingSecurityScopedResource()
                defer { if hasAccess { url.stopAccessingSecurityScopedResource() } }
                pendingBackup = try Data(contentsOf: url)
                password = ""; confirmPassword = ""
                passwordSheet = .restore
            } catch {
                alertMessage = error.localizedDescription
            }
        }
        .confirmationDialog("¿Reemplazar los datos del teléfono?", isPresented: $showRestoreConfirmation, titleVisibility: .visible) {
            Button("Reemplazar datos", role: .destructive) { restore() }
            Button("Cancelar", role: .cancel) { pendingBackup = nil; password = "" }
        } message: { Text("La copia restaurada sustituirá el inventario, las ventas, los turnos y los ajustes actuales.") }
        .alert("Respaldo", isPresented: Binding(get: { alertMessage != nil }, set: { if !$0 { alertMessage = nil } })) {
            Button("Entendido", role: .cancel) { alertMessage = nil }
        } message: { Text(alertMessage ?? "") }
    }

    private func handlePassword(_ mode: BackupPasswordMode) {
        if mode == .export {
            do {
                exportDocument = ShalevBackupDocument(data: try store.makeEncryptedBackup(password: password))
                showExporter = true
            } catch { alertMessage = error.localizedDescription }
        } else if pendingBackup != nil {
            showRestoreConfirmation = true
        }
    }

    private func restore() {
        guard let pendingBackup else { return }
        do {
            try store.restoreEncryptedBackup(pendingBackup, password: password)
            alertMessage = "Respaldo restaurado correctamente."
        } catch { alertMessage = error.localizedDescription }
        self.pendingBackup = nil
        password = ""; confirmPassword = ""
    }
}

private struct BackupPasswordSheet: View {
    let mode: BackupPasswordMode
    @Binding var password: String
    @Binding var confirmPassword: String
    let onConfirm: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                ShalevScreenTitle(title: mode == .export ? "Cifrar respaldo" : "Abrir respaldo",
                                  subtitle: mode == .export ? "Elige una contraseña de al menos 8 caracteres." : "Escribe la contraseña del archivo.")
                ShalevCard {
                    VStack(spacing: 11) {
                        SecureField("Contraseña", text: $password).textFieldStyle(BackupInputStyle())
                        if mode == .export {
                            SecureField("Repite la contraseña", text: $confirmPassword).textFieldStyle(BackupInputStyle())
                        }
                    }
                }
                Button(mode == .export ? "Crear copia cifrada" : "Continuar") { validate() }
                    .buttonStyle(ShalevPrimaryButtonStyle())
                Spacer(minLength: 0)
            }
            .padding(18)
            .background(Color.shalevBackground)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Cancelar") { dismiss() }.foregroundStyle(Color.shalevSecondaryText) } }
            .alert("Contraseña", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Entendido", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
    }

    private func validate() {
        guard password.count >= 8 else { errorMessage = "Usa una contraseña de al menos 8 caracteres."; return }
        if mode == .export && password != confirmPassword { errorMessage = "Las contraseñas no coinciden."; return }
        onConfirm()
        dismiss()
    }
}

private struct ShalevBackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.shalevBackup] }
    var data: Data

    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents else { throw BusinessError.backupInvalid }
        self.data = data
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

extension UTType {
    static let shalevBackup = UTType(exportedAs: "cu.shalev.ios-backup", conformingTo: .data)
}

private struct HelpView: View {
    private let topics: [(String, String, String)] = [
        ("Abrir un turno", "Desde Inicio, abre un turno e indica el fondo de caja. Sin turno abierto no se habilitan las ventas.", "storefront.fill"),
        ("Vender y cobrar", "Agrega productos o servicios, elige efectivo o transferencia y confirma la operación. Puedes pegar el SMS de Transfermóvil para leer el importe y el número; Shalev no confirma el pago con el banco.", "cart.fill"),
        ("Inventario y servicios", "Busca y filtra artículos. En cada ficha puedes editar existencias, precios, mínimos y recetas; también puedes añadir una foto desde la fototeca.", "archivebox.fill"),
        ("Corregir una venta", "Desde Registros, modifica o anula una venta del turno abierto. Escribe un motivo; la operación original queda en el historial.", "pencil.and.list.clipboard"),
        ("Empleados", "En Ajustes → Empleados crea perfiles y elige permisos. Los perfiles son locales a este iPhone; volver al administrador exige autenticación del dispositivo.", "person.2.fill"),
        ("Cerrar caja", "Al cerrar el turno, cuenta el efectivo y compara el resultado con el efectivo esperado.", "checkmark.circle.fill"),
        ("Respaldos", "Exporta una copia con contraseña. El respaldo iOS es independiente del formato Android.", "externaldrive.fill"),
        ("Actualizaciones", "Comprobar ahora consulta publicaciones iOS en GitHub. La instalación se gestiona por TestFlight, App Store o un canal autorizado.", "arrow.down.circle.fill"),
    ]

    var body: some View {
        ScrollView {
            VStack(spacing: 13) {
                ShalevScreenTitle(title: "Ayuda", subtitle: "Guía rápida de uso")
                ForEach(topics, id: \.0) { topic in
                    ShalevCard(padding: 14) {
                        HStack(alignment: .top, spacing: 11) {
                            Image(systemName: topic.2).foregroundStyle(Color.shalevPrimary).frame(width: 25)
                            VStack(alignment: .leading, spacing: 5) {
                                Text(topic.0).font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
                                Text(topic.1).font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText).fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                }
            }
            .padding(18)
        }
        .background(Color.shalevBackground)
    }
}

private struct AboutView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 13) {
                Image("ShalevLogo").resizable().scaledToFit().frame(width: 112, height: 112)
                    .accessibilityLabel("Logo de Shalev")
                ShalevScreenTitle(title: "Shalev para iOS", subtitle: "Punto de venta e inventario nativo")
                ShalevCard {
                    VStack(alignment: .leading, spacing: 8) {
                        info("Versión", "1.0 · iOS")
                        info("Modo", "Local y sin conexión")
                        info("Almacenamiento", "AES-GCM · Keychain")
                        info("Tecnología", "SwiftUI · CryptoKit · Charts")
                    }
                }
                ShalevCard {
                    Text("IShalev usa una interfaz y almacenamiento nativos de Apple y valida licencias bajo el contrato GL. La sincronización de datos con teléfonos Android todavía no está disponible; el respaldo iOS es un formato cifrado independiente.")
                        .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText).fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(18)
        }
        .background(Color.shalevBackground)
    }

    private func info(_ key: String, _ value: String) -> some View {
        HStack { Text(key).foregroundStyle(Color.shalevSecondaryText); Spacer(); Text(value).fontWeight(.semibold).foregroundStyle(Color.shalevText) }
            .font(.system(size: 13))
    }
}

private func labeledField(_ title: String, text: Binding<String>, placeholder: String, keyboard: UIKeyboardType = .default) -> some View {
    VStack(alignment: .leading, spacing: 6) {
        Text(title).font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
        TextField(placeholder, text: text).keyboardType(keyboard).textFieldStyle(SettingsInputStyle())
    }
}

private struct SettingsInputStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration.font(.system(size: 14)).foregroundStyle(Color.shalevText)
            .padding(11).background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 11).stroke(Color.shalevOutlineVariant, lineWidth: 1))
    }
}

private struct BackupInputStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration.font(.system(size: 15)).padding(12)
            .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
    }
}
