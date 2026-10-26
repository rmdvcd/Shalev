import SwiftUI
import UniformTypeIdentifiers
import UIKit
import LocalAuthentication

private enum SettingsSheet: String, Identifiable, Equatable {
    case license, profile, payment, prices, backup, export, employees, sync, updates, help, support, about
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
        case .sync: "Apps vinculadas"
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
    @ObservedObject var sync: SyncManager
    @Binding var selectedOperatorID: String
    let isAdministrator: Bool
    let canChangePrices: Bool
    let canExport: Bool
    let canExportInventory: Bool
    let canExportServices: Bool
    let canExportSales: Bool
    @AppStorage("shalev.lock.enabled") private var appLockEnabled = false
    @State private var sheet: SettingsSheet?
    @State private var showSetupWizard = false
    @State private var canUseDeviceAuthentication = false

    var body: some View {
        ScrollView {
            VStack(spacing: 15) {
                ShalevScreenTitle(title: "Ajustes")
                    .padding(.top, 8)
                ShalevCard(padding: 5) {
                    VStack(spacing: 0) {
                        // Mismo orden que Android. «Completar configuración» solo aparece si falta algo.
                        if isAdministrator && !pendingSetup.isEmpty {
                            Button { showSetupWizard = true } label: {
                                settingsRowLabel(title: "Completar configuración",
                                                 subtitle: pendingSetupText,
                                                 symbol: "checklist",
                                                 badge: "\(pendingSetup.count)")
                            }
                            .buttonStyle(.plain)
                            settingsDivider
                        }
                        settingsRow("Perfil", subtitle: "Nombre, apellidos y carné", symbol: "person.crop.circle", sheet: .profile)
                        if isAdministrator {
                            settingsDivider
                            settingsRow("Licencia", subtitle: "Estado, solicitud y activación", symbol: "key.fill", sheet: .license)
                        }
                        settingsDivider
                        settingsRow("Apps vinculadas",
                                    subtitle: sync.isSecondary ? "Secundaria · Principal" : "Principal · Vincula las apps de tus empleados",
                                    symbol: "point.3.connected.trianglepath", sheet: .sync)
                        if isAdministrator {
                            settingsDivider
                            settingsRow("Pago electrónico", subtitle: "Teléfonos y cuentas para cobrar por transferencia", symbol: "creditcard.fill", sheet: .payment)
                        }
                        if canChangePrices {
                            settingsDivider
                            settingsRow("Precios", subtitle: "Por pago o por importe", symbol: "dollarsign.circle.fill", sheet: .prices)
                        }
                        if isAdministrator {
                            settingsDivider
                            settingsRow("Permisos", subtitle: "Perfiles locales y qué puede hacer cada empleado", symbol: "lock.fill", sheet: .employees)
                            settingsDivider
                            accessLockRow
                        }
                        if isAdministrator {
                            settingsDivider
                            settingsRow("Respaldo", subtitle: "Copia cifrada de todo e importar", symbol: "externaldrive.fill", sheet: .backup)
                        }
                        if canExport {
                            settingsDivider
                            settingsRow("Exportar", subtitle: "Excel (XLSX), CSV, PDF e imágenes", symbol: "square.and.arrow.up.fill", sheet: .export)
                        }
                        if isAdministrator {
                            settingsDivider
                            settingsRow("Actualizar", subtitle: "Consultar nuevas publicaciones de IShalev", symbol: "arrow.triangle.2.circlepath", sheet: .updates)
                        }
                        settingsDivider
                        settingsRow("Ayuda", subtitle: "Manual breve de uso", symbol: "questionmark.circle.fill", sheet: .help)
                        settingsDivider
                        settingsRow("Soporte", subtitle: "Datos de contacto del desarrollador", symbol: "message.fill", sheet: .support)
                    }
                }
                ShalevCard {
                    VStack(alignment: .leading, spacing: 7) {
                        Label("Privacidad", systemImage: "lock.shield.fill")
                            .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                        Text("Shalev guarda los datos en este teléfono y no usa una cuenta, servidor, analítica ni anuncios. El almacenamiento local se cifra con AES-GCM y una clave protegida por el llavero de iOS.")
                            .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                    }
                }
                ShalevCard(padding: 5) {
                    settingsRow("Acerca de Shalev", subtitle: "Aplicación nativa para iOS · Versión 1.0", symbol: "info.circle.fill", sheet: .about)
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
        .sheet(isPresented: $showSetupWizard) {
            OnboardingView(store: store, startAtData: true) { showSetupWizard = false }
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
                        case .prices: PriceAdjustmentsView(store: store, sync: sync)
                        case .backup: BackupSettingsView(store: store)
                        case .export: ExportCenterView(store: store, canExportInventory: canExportInventory,
                                                       canExportServices: canExportServices, canExportSales: canExportSales)
                        case .employees: EmployeeSettingsView(store: store, selectedOperatorID: $selectedOperatorID)
                        case .sync: SyncView(sync: sync)
                        case .updates: UpdatesView()
                        case .help: HelpView()
                        case .support: SupportView()
                        case .about: AboutView()
                        }
                    }
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            if route != .profile && route != .payment {
                                Button("Cerrar") { sheet = nil }.foregroundStyle(Color.shalevPrimary)
                            }
                        }
                    }
                }
                .presentationDetents(route == .help || route == .about ? [.medium, .large] : [.large])
                .presentationDragIndicator(.visible)
            }
        }
    }

    // MARK: - Filas

    private var accessLockRow: some View {
        HStack(spacing: 12) {
            Image(systemName: "faceid")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(Color.shalevPrimary)
                .frame(width: 42, height: 42)
                .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 3) {
                Text("Acceso con clave")
                    .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
                Text(lockSubtitle)
                    .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 5)
            Toggle("", isOn: $appLockEnabled)
                .labelsHidden()
                .tint(Color.shalevPrimary)
                .disabled(!canUseDeviceAuthentication)
                .accessibilityLabel("Bloquear Shalev al salir")
        }
        .padding(12)
    }

    private var lockSubtitle: String {
        if !canUseDeviceAuthentication {
            return "Configura Face ID, Touch ID o un código de acceso en el teléfono para usar esta opción."
        }
        return appLockEnabled ? "Shalev se bloquea al salir; se abre con Face ID o el código del teléfono."
            : "Pide Face ID o el código del teléfono al volver a Shalev."
    }

    /// Pasos que faltan para tener la configuración completa (los mismos datos del asistente inicial).
    private var pendingSetup: [String] {
        var pending: [String] = []
        let profile = store.data.settings.profile
        let name = profile.businessName.trimmingCharacters(in: .whitespacesAndNewlines)
        if name.isEmpty || name == "Mi negocio" { pending.append("negocio") }
        if (profile.ownerName).trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { pending.append("titular") }
        if profile.paymentCard == nil && profile.paymentPhone == nil { pending.append("cobro") }
        return pending
    }

    private var pendingSetupText: String {
        pendingSetup.isEmpty ? "Todo listo" : "Faltan \(pendingSetup.count) pasos: \(pendingSetup.joined(separator: ", "))"
    }

    private var settingsDivider: some View { Color.shalevOutlineVariant.opacity(0.65).frame(height: 0.6).padding(.leading, 58) }

    private func settingsRow(_ title: String, subtitle: String, symbol: String, sheet route: SettingsSheet) -> some View {
        Button { sheet = route } label: {
            settingsRowLabel(title: title, subtitle: subtitle, symbol: symbol, badge: nil)
        }
        .buttonStyle(.plain)
    }

    private func settingsRowLabel(title: String, subtitle: String, symbol: String, badge: String?) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).font(.system(size: 18, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                .frame(width: 42, height: 42).background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
                Text(subtitle).font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText).lineLimit(2)
            }
            Spacer(minLength: 5)
            if let badge {
                Text(badge)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(minWidth: 20, minHeight: 20)
                    .background(Color.shalevError, in: Circle())
                    .accessibilityLabel("\(badge) pasos pendientes")
            }
            Image(systemName: "chevron.right").font(.system(size: 11, weight: .semibold)).foregroundStyle(Color.shalevSecondaryText)
        }
        .padding(12)
        .contentShape(Rectangle())
    }
}

@MainActor
private struct ProfileSettingsView: View {
    @ObservedObject var store: ShalevStore
    @Environment(\.dismiss) private var dismiss
    @AppStorage("shalev.module.sales") private var salesEnabled = true
    @AppStorage("shalev.module.inventory") private var inventoryEnabled = true
    @AppStorage("shalev.module.services") private var servicesEnabled = true

    @State private var firstName: String
    @State private var lastName: String
    @State private var identification: String
    @State private var showErrors = false
    @State private var confirmDiscard = false
    @State private var message: String?

    init(store: ShalevStore) {
        self.store = store
        let saved = ProfileSettingsView.savedValues(store.data.settings.profile)
        _firstName = State(initialValue: saved.first)
        _lastName = State(initialValue: saved.last)
        _identification = State(initialValue: saved.identification)
    }

    /// Nombre, apellidos y CI tal como los tiene guardados el perfil (compatibles con el nombre completo antiguo).
    static func savedValues(_ profile: StoreProfile) -> (first: String, last: String, identification: String) {
        let parts = profile.ownerName.split(whereSeparator: \.isWhitespace)
        let first = profile.ownerFirstName ?? parts.first.map(String.init) ?? ""
        let last = profile.ownerLastName ?? (parts.count > 1 ? parts.dropFirst().joined(separator: " ") : "")
        return (first, last, profile.identification)
    }

    private var saved: (first: String, last: String, identification: String) {
        ProfileSettingsView.savedValues(store.data.settings.profile)
    }

    private var hasChanges: Bool {
        trimmed(firstName) != trimmed(saved.first)
            || trimmed(lastName) != trimmed(saved.last)
            || trimmed(identification) != trimmed(saved.identification)
    }

    private var nameError: String? {
        let value = trimmed(firstName)
        return value.isEmpty || validName(value) ? nil : "Solo letras, de 2 a 80"
    }

    private var lastNameError: String? {
        let value = trimmed(lastName)
        return value.isEmpty || validName(value) ? nil : "Solo letras, de 2 a 80"
    }

    private var identificationError: String? {
        let value = trimmed(identification)
        return value.isEmpty || validCI(value) ? nil : "Entre 5 y 20 letras o números"
    }

    private var hasErrors: Bool { nameError != nil || lastNameError != nil || identificationError != nil }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                Text("Tus datos se usan para pedir la licencia. Solo se guardan en este teléfono, protegidos.")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.shalevText)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(14)
                    .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                ShalevCard {
                    VStack(alignment: .leading, spacing: 12) {
                        profileField("Nombre", text: $firstName, placeholder: "Nombre",
                                     error: showErrors ? nameError : nil, capitalization: .words)
                        profileField("Apellidos", text: $lastName, placeholder: "Apellidos",
                                     error: showErrors ? lastNameError : nil, capitalization: .words)
                        profileField("Carnet de identidad (CI)", text: $identification, placeholder: "85010112345",
                                     error: showErrors ? identificationError : nil, capitalization: .characters)
                        Text("Solo se envía al pedir la licencia")
                            .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                        Button { save() } label: {
                            Label("Guardar", systemImage: "checkmark")
                        }
                        .buttonStyle(ShalevPrimaryButtonStyle())
                        .disabled(!hasChanges)
                    }
                }

                ShalevCard {
                    VStack(alignment: .leading, spacing: 8) {
                        ShalevSectionHeader(title: "Tu negocio")
                        Text("Lo que no marques desaparece de la barra inferior, de Inicio y de Registros. No se borra nada.")
                            .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                        moduleRow("Ventas", detail: "Vender productos del inventario", isOn: salesEnabled,
                                  symbol: "cart.fill") { toggleSales() }
                        moduleRow("Inventario", detail: "Controlar existencias de productos e insumos", isOn: inventoryEnabled,
                                  symbol: "archivebox.fill") { toggleInventory() }
                        moduleRow("Servicios", detail: "Vender servicios", isOn: servicesEnabled,
                                  symbol: "storefront.fill") { toggleServices() }
                    }
                }
            }
            .padding(18)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .background(Color.shalevBackground)
        .navigationTitle("Perfil")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Cerrar") { requestClose() }.foregroundStyle(Color.shalevPrimary)
            }
        }
        .alert("¿Salir sin guardar?", isPresented: $confirmDiscard) {
            Button("Seguir editando", role: .cancel) {}
            Button("Descartar", role: .destructive) { dismiss() }
        } message: { Text("Los cambios en tus datos personales se perderán.") }
        .alert("Perfil", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("Entendido", role: .cancel) { message = nil }
        } message: { Text(message ?? "") }
    }

    private func profileField(_ title: String, text: Binding<String>, placeholder: String,
                              error: String?, capitalization: TextInputAutocapitalization = .never) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
            TextField(placeholder, text: text)
                .textInputAutocapitalization(capitalization)
                .autocorrectionDisabled()
                .font(.system(size: 14))
                .padding(11)
                .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
                .overlay {
                    RoundedRectangle(cornerRadius: 11)
                        .stroke(error == nil ? Color.shalevOutlineVariant : Color.shalevError, lineWidth: 1)
                }
            if let error {
                Text(error).font(.system(size: 11)).foregroundStyle(Color.shalevError)
            }
        }
    }

    private func moduleRow(_ title: String, detail: String, isOn: Bool, symbol: String,
                           action: @escaping () -> Void) -> some View {
        let onlyOne = isOn && activeModules == 1
        return Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: isOn ? "checkmark.square.fill" : "square")
                    .font(.system(size: 19))
                    .foregroundStyle(isOn ? Color.shalevPrimary : Color.shalevSecondaryText)
                Image(systemName: symbol)
                    .font(.system(size: 15))
                    .foregroundStyle(Color.shalevSecondaryText)
                    .frame(width: 22)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.system(size: 14, weight: .medium)).foregroundStyle(Color.shalevText)
                    Text(detail).font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                }
                Spacer(minLength: 4)
            }
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(onlyOne)
        .opacity(onlyOne ? 0.55 : 1)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }

    private var activeModules: Int {
        [salesEnabled, inventoryEnabled, servicesEnabled].filter { $0 }.count
    }

    private func toggleSales() {
        salesEnabled.toggle()
        if salesEnabled { inventoryEnabled = true }
    }

    private func toggleInventory() {
        if inventoryEnabled {
            inventoryEnabled = false
            salesEnabled = false
        } else {
            inventoryEnabled = true
        }
    }

    private func toggleServices() {
        servicesEnabled.toggle()
    }

    private func requestClose() {
        if hasChanges { confirmDiscard = true } else { dismiss() }
    }

    private func save() {
        showErrors = true
        guard !hasErrors else { return }
        var profile = store.data.settings.profile
        profile.ownerFirstName = trimmed(firstName)
        profile.ownerLastName = trimmed(lastName)
        profile.ownerName = "\(profile.ownerFirstName ?? "") \(profile.ownerLastName ?? "")"
            .trimmingCharacters(in: .whitespacesAndNewlines)
        profile.identification = trimmed(identification).uppercased()
        do {
            try store.saveProfile(profile)
            message = "Datos guardados"
        } catch {
            message = "No se pudo guardar. Inténtalo de nuevo."
        }
    }

    private func trimmed(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

private enum PaymentKind: String, Identifiable {
    case phone, card
    var id: String { rawValue }
    var symbol: String { self == .phone ? "phone.fill" : "creditcard.fill" }
}

private struct PaymentDraft: Identifiable {
    let id = UUID()
    var kind: PaymentKind
    var editingID: UUID? = nil
    var number = ""
    var alias = ""
}

private struct PaymentTarget: Identifiable {
    let id = UUID()
    var kind: PaymentKind
    var itemID: UUID
    var title: String
}

/// Identificación visual orientativa por prefijo, igual que en Android (no verifica el banco).
private enum BancoCubano: String, CaseIterable {
    case bpa, bandec, banmet

    var nombre: String {
        switch self {
        case .bpa: "Banco Popular de Ahorro"
        case .bandec: "Banco de Crédito y Comercio"
        case .banmet: "Banco Metropolitano"
        }
    }

    var sigla: String {
        switch self {
        case .bpa: "BPA"
        case .bandec: "BANDEC"
        case .banmet: "BANMET"
        }
    }

    var prefijos: [String] {
        switch self {
        case .bpa: ["9205"]
        case .bandec: ["9225"]
        case .banmet: ["9226"]
        }
    }
}

private func bancoCubano(_ number: String) -> BancoCubano? {
    let digits = number.filter(\.isNumber)
    guard digits.count >= 4 else { return nil }
    return BancoCubano.allCases.first { banco in banco.prefijos.contains { digits.hasPrefix($0) } }
}

@MainActor
private struct PaymentSettingsView: View {
    @ObservedObject var store: ShalevStore
    @Environment(\.dismiss) private var dismiss
    @State private var phones: [TransferPhone]
    @State private var cards: [TransferCard]
    @State private var paymentPhoneID: UUID?
    @State private var paymentCardID: UUID?
    @State private var editing: PaymentDraft?
    @State private var deleting: PaymentTarget?
    @State private var expanded = false
    @State private var message: String?

    init(store: ShalevStore) {
        self.store = store
        let profile = store.data.settings.profile
        _phones = State(initialValue: profile.availablePhones)
        _cards = State(initialValue: profile.availableCards)
        _paymentPhoneID = State(initialValue: profile.paymentPhone?.id)
        _paymentCardID = State(initialValue: profile.paymentCard?.id)
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    sectionHeader("Teléfonos")
                    if phones.isEmpty {
                        emptyLine("La lista está vacía. Toca + para agregar.")
                    } else {
                        ForEach(phones) { phone in
                            paymentRow(kind: .phone, id: phone.id,
                                       title: phone.number,
                                       subtitle: phone.alias.isEmpty ? nil : phone.alias,
                                       selected: paymentPhoneID == phone.id)
                        }
                    }

                    sectionHeader("Tarjetas y cuentas")
                    if cards.isEmpty {
                        emptyLine("La lista está vacía. Toca + para agregar.")
                    } else {
                        ForEach(cards) { card in
                            let bank = bancoCubano(card.number)
                            let subtitle = [card.alias.isEmpty ? nil : card.alias, bank?.sigla]
                                .compactMap { $0 }.joined(separator: " · ")
                            paymentRow(kind: .card, id: card.id,
                                       title: maskedCard(card.number),
                                       subtitle: subtitle.isEmpty ? nil : subtitle,
                                       selected: paymentCardID == card.id)
                        }
                    }

                    Text("Los logos se estiman por el prefijo; no verifican el banco ni la titularidad.")
                        .font(.system(size: 11))
                        .foregroundStyle(Color.shalevSecondaryText)
                        .padding(.horizontal, 18)
                        .padding(.top, 18)
                }
                .padding(.bottom, 120)
                .frame(maxWidth: 680)
                .frame(maxWidth: .infinity)
            }
            floatingAdd
        }
        .background(Color.shalevBackground)
        .navigationTitle("Pago electrónico")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Cerrar") { dismiss() }.foregroundStyle(Color.shalevPrimary)
            }
        }
        .sheet(item: $editing) { draft in
            PaymentEditorSheet(kind: draft.kind,
                               title: editorTitle(draft),
                               number: draft.number,
                               alias: draft.alias,
                               existingNumbers: existingNumbers(for: draft)) { number, alias in
                save(kind: draft.kind, editingID: draft.editingID, number: number, alias: alias)
            }
        }
        .alert(deleting?.kind == .phone ? "¿Eliminar este teléfono?" : "¿Eliminar esta tarjeta o cuenta?",
               isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })) {
            Button("Cancelar", role: .cancel) { deleting = nil }
            Button("Eliminar", role: .destructive) { confirmDelete() }
        } message: {
            Text("Se quita de la lista. Las ventas ya registradas no cambian.")
        }
        .alert("Pago electrónico", isPresented: Binding(get: { message != nil }, set: { if !$0 { message = nil } })) {
            Button("Entendido", role: .cancel) { message = nil }
        } message: { Text(message ?? "") }
    }

    private func sectionHeader(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(Color.shalevText)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 18)
            .padding(.top, 16)
            .padding(.bottom, 6)
            .accessibilityAddTraits(.isHeader)
    }

    private func emptyLine(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 12))
            .foregroundStyle(Color.shalevSecondaryText)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 18)
            .padding(.vertical, 10)
    }

    private func paymentRow(kind: PaymentKind, id: UUID, title: String, subtitle: String?, selected: Bool) -> some View {
        HStack(spacing: 10) {
            Button { select(kind, id) } label: {
                HStack(spacing: 10) {
                    Image(systemName: selected ? "checkmark.square.fill" : "square")
                        .font(.system(size: 19))
                        .foregroundStyle(selected ? Color.shalevPrimary : Color.shalevSecondaryText)
                    Image(systemName: kind.symbol)
                        .font(.system(size: 17))
                        .foregroundStyle(Color.shalevText)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title).font(.system(size: 15)).foregroundStyle(Color.shalevText)
                        if let subtitle {
                            Text(subtitle).font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                        }
                    }
                    Spacer(minLength: 6)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Usar \(title)")
            .accessibilityAddTraits(selected ? .isSelected : [])

            Button { edit(kind, id) } label: {
                Image(systemName: "pencil").font(.system(size: 16)).foregroundStyle(Color.shalevText)
                    .frame(width: 34, height: 34)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Editar \(title)")

            Button { askDelete(kind, id, title) } label: {
                Image(systemName: "trash").font(.system(size: 16)).foregroundStyle(Color.shalevText)
                    .frame(width: 34, height: 34)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Eliminar \(title)")
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(selected ? Color.shalevPrimaryContainer : Color.shalevSurface)
    }

    private var floatingAdd: some View {
        VStack(alignment: .trailing, spacing: 10) {
            if expanded {
                addPill("Tarjeta o cuenta", symbol: "creditcard.fill") {
                    expanded = false
                    editing = PaymentDraft(kind: .card)
                }
                addPill("Teléfono", symbol: "phone.fill") {
                    expanded = false
                    editing = PaymentDraft(kind: .phone)
                }
            }
            ShalevFloatingButton(title: expanded ? "Cerrar acciones" : "Agregar",
                                 symbol: expanded ? "xmark" : "plus") {
                withAnimation(.easeInOut(duration: 0.18)) { expanded.toggle() }
            }
        }
        .padding(.trailing, 18)
        .padding(.bottom, 22)
    }

    private func addPill(_ title: String, symbol: String, action: @escaping () -> Void) -> some View {
        HStack(spacing: 8) {
            Text(title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Color.shalevText)
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            Button(action: action) {
                Image(systemName: symbol)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.shalevPrimary)
                    .frame(width: 40, height: 40)
                    .background(Color.shalevPrimaryContainer, in: Circle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(title)
        }
    }

    private func maskedCard(_ number: String) -> String {
        let digits = number.filter(\.isNumber)
        return digits.count >= 4 ? "•••• \(digits.suffix(4))" : "••••"
    }

    private func select(_ kind: PaymentKind, _ id: UUID) {
        switch kind {
        case .phone: paymentPhoneID = paymentPhoneID == id ? nil : id
        case .card: paymentCardID = paymentCardID == id ? nil : id
        }
        persist()
    }

    private func edit(_ kind: PaymentKind, _ id: UUID) {
        switch kind {
        case .phone:
            guard let phone = phones.first(where: { $0.id == id }) else { return }
            editing = PaymentDraft(kind: .phone, editingID: id, number: phone.number, alias: phone.alias)
        case .card:
            guard let card = cards.first(where: { $0.id == id }) else { return }
            editing = PaymentDraft(kind: .card, editingID: id, number: card.number, alias: card.alias)
        }
    }

    private func askDelete(_ kind: PaymentKind, _ id: UUID, _ title: String) {
        deleting = PaymentTarget(kind: kind, itemID: id, title: title)
    }

    private func confirmDelete() {
        guard let target = deleting else { return }
        switch target.kind {
        case .phone:
            phones.removeAll { $0.id == target.itemID }
            if paymentPhoneID == target.itemID { paymentPhoneID = nil }
        case .card:
            cards.removeAll { $0.id == target.itemID }
            if paymentCardID == target.itemID { paymentCardID = nil }
        }
        deleting = nil
        persist()
    }

    private func editorTitle(_ draft: PaymentDraft) -> String {
        switch (draft.kind, draft.editingID) {
        case (.phone, nil): "Nuevo teléfono"
        case (.phone, _): "Editar teléfono"
        case (.card, nil): "Nueva tarjeta o cuenta"
        case (.card, _): "Editar tarjeta o cuenta"
        }
    }

    private func existingNumbers(for draft: PaymentDraft) -> [String] {
        switch draft.kind {
        case .phone:
            return phones.filter { $0.id != draft.editingID }
                .compactMap { CustomerValidator.normalizedPhone($0.number) }
        case .card:
            return cards.filter { $0.id != draft.editingID }
                .map { $0.number.filter(\.isNumber) }
        }
    }

    private func save(kind: PaymentKind, editingID: UUID?, number: String, alias: String) {
        let cleanAlias = alias.trimmingCharacters(in: .whitespacesAndNewlines)
        switch kind {
        case .phone:
            let normalized = CustomerValidator.normalizedPhone(number)
                ?? number.trimmingCharacters(in: .whitespacesAndNewlines)
            if let editingID, let index = phones.firstIndex(where: { $0.id == editingID }) {
                phones[index].number = normalized
                phones[index].alias = cleanAlias
            } else {
                phones.append(TransferPhone(number: normalized, alias: cleanAlias))
            }
        case .card:
            let digits = number.filter(\.isNumber)
            if let editingID, let index = cards.firstIndex(where: { $0.id == editingID }) {
                cards[index].number = digits
                cards[index].alias = cleanAlias
            } else {
                cards.append(TransferCard(number: digits, alias: cleanAlias))
            }
        }
        persist()
    }

    private func persist() {
        var profile = store.data.settings.profile
        profile.transferPhones = phones
        profile.transferCards = cards
        profile.paymentPhoneID = paymentPhoneID
        profile.paymentCardID = paymentCardID
        do {
            try store.saveProfile(profile)
            message = "Pago electrónico guardado"
        } catch {
            message = "No se pudo guardar. Inténtalo de nuevo."
        }
    }
}

@MainActor
private struct PaymentEditorSheet: View {
    @Environment(\.dismiss) private var dismiss
    let kind: PaymentKind
    let title: String
    let existingNumbers: [String]
    let onSave: (String, String) -> Void

    @State private var number: String
    @State private var alias: String
    @State private var touched = false

    init(kind: PaymentKind, title: String, number: String, alias: String,
         existingNumbers: [String], onSave: @escaping (String, String) -> Void) {
        self.kind = kind
        self.title = title
        self.existingNumbers = existingNumbers
        self.onSave = onSave
        _number = State(initialValue: number)
        _alias = State(initialValue: alias)
    }

    private var error: String? {
        let value = number.trimmingCharacters(in: .whitespacesAndNewlines)
        if kind == .phone {
            guard let normalized = CustomerValidator.normalizedPhone(value) else {
                return "Escribe un teléfono válido, por ejemplo 5123 4567"
            }
            return existingNumbers.contains(normalized) ? "Ese número ya está en la lista" : nil
        }
        let digits = value.filter(\.isNumber)
        let invalid = value.contains { !$0.isNumber && !$0.isWhitespace && $0 != "-" }
        if invalid || !(12...20).contains(digits.count) {
            return "Escribe solo los números de la tarjeta o cuenta (12 a 20 dígitos)"
        }
        return existingNumbers.contains(digits) ? "Ese número ya está en la lista" : nil
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(kind == .phone ? "Número de teléfono" : "Número de tarjeta o cuenta")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Color.shalevSecondaryText)
                        TextField(kind == .phone ? "5123 4567" : "9205 0000 0000 0000", text: $number)
                            .keyboardType(kind == .phone ? .phonePad : .numberPad)
                            .autocorrectionDisabled()
                            .font(.system(size: 14))
                            .padding(11)
                            .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
                            .overlay {
                                RoundedRectangle(cornerRadius: 11)
                                    .stroke(shownError == nil ? Color.shalevOutlineVariant : Color.shalevError, lineWidth: 1)
                            }
                            .onChange(of: number) { _, _ in touched = true }
                        if let shownError {
                            Text(shownError).font(.system(size: 11)).foregroundStyle(Color.shalevError)
                        }
                    }

                    if kind == .card, let bank = bancoCubano(number) {
                        HStack(spacing: 10) {
                            Image(systemName: "creditcard.fill")
                                .font(.system(size: 18))
                                .foregroundStyle(Color.shalevSecondaryText)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(bank.nombre)
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(Color.shalevText)
                                Text("Identificación orientativa por prefijo")
                                    .font(.system(size: 11))
                                    .foregroundStyle(Color.shalevSecondaryText)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(10)
                        .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }

                    VStack(alignment: .leading, spacing: 5) {
                        Text("Nombre para reconocerlo (opcional)")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(Color.shalevSecondaryText)
                        TextField(kind == .phone ? "Mi móvil" : "Tarjeta del banco", text: $alias)
                            .autocorrectionDisabled()
                            .font(.system(size: 14))
                            .padding(11)
                            .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
                            .overlay {
                                RoundedRectangle(cornerRadius: 11).stroke(Color.shalevOutlineVariant, lineWidth: 1)
                            }
                    }
                }
                .padding(18)
            }
            .background(Color.shalevBackground)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Guardar") {
                        guard error == nil else { touched = true; return }
                        onSave(number, alias)
                        dismiss()
                    }
                    .disabled(error != nil)
                }
            }
        }
    }

    private var shownError: String? {
        (touched || !number.isEmpty) ? error : nil
    }
}

@MainActor
private struct PriceAdjustmentsView: View {
    @ObservedObject var store: ShalevStore
    @ObservedObject var sync: SyncManager
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
                                    if sync.isSecondary {
                                        Task {
                                            do {
                                                let row = try AndroidBackupCodec.exportAdjustment(changed, app: store.data)
                                                _ = try await sync.executeRemote(SyncJSON.message("preajuste_guardar", ["preajuste": row]))
                                            } catch { errorMessage = error.localizedDescription }
                                        }
                                    } else {
                                        do { try store.savePriceAdjustment(changed) } catch { errorMessage = error.localizedDescription }
                                    }
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
            PriceAdjustmentEditor(store: store, sync: sync, adjustment: nil)
                .presentationDetents([.large]).presentationDragIndicator(.visible)
        }
        .sheet(item: $editor) { adjustment in
            PriceAdjustmentEditor(store: store, sync: sync, adjustment: adjustment)
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
    @ObservedObject var sync: SyncManager
    let adjustment: PriceAdjustment?
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var percent: String
    @State private var method: PaymentMethod?
    @State private var minimum: String
    @State private var productIDs: Set<UUID>
    @State private var errorMessage: String?
    @State private var confirmDelete = false
    @State private var working = false

    init(store: ShalevStore, sync: SyncManager, adjustment: PriceAdjustment?) {
        self.store = store
        self.sync = sync
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
                                .buttonStyle(ShalevPrimaryButtonStyle()).disabled(working)
                            if working { ProgressView().frame(maxWidth: .infinity) }
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
        if sync.isSecondary {
            working = true
            Task {
                do {
                    let row = try AndroidBackupCodec.exportAdjustment(result, app: store.data)
                    _ = try await sync.executeRemote(SyncJSON.message("preajuste_guardar", ["preajuste": row]))
                    dismiss()
                } catch { errorMessage = error.localizedDescription; working = false }
            }
        } else {
            do { try store.savePriceAdjustment(result); dismiss() }
            catch { errorMessage = error.localizedDescription }
        }
    }

    private func delete() {
        guard let adjustment else { return }
        if sync.isSecondary {
            working = true
            Task {
                do {
                    _ = try await sync.executeRemote(SyncJSON.message("preajuste_eliminar", ["id": AndroidID.int64("adjustment", adjustment.id)]))
                    dismiss()
                } catch { errorMessage = error.localizedDescription; working = false }
            }
        } else {
            do { try store.deletePriceAdjustment(adjustment.id); dismiss() }
            catch { errorMessage = error.localizedDescription }
        }
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
    @State private var exportFormat: PortableBackupFormat = .ios
    @State private var exportContentType: UTType = .shalevBackup
    @State private var showExporter = false
    @State private var showImporter = false
    @State private var pendingBackup: Data?
    @State private var showRestoreConfirmation = false
    @State private var alertMessage: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                ShalevScreenTitle(title: "Respaldo", subtitle: "Copias cifradas para iOS y Android")
                ShalevCard {
                    VStack(alignment: .leading, spacing: 11) {
                        Label("Protege tu copia con una contraseña", systemImage: "lock.fill")
                            .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                        Picker("Formato de exportación", selection: $exportFormat) {
                            ForEach(PortableBackupFormat.allCases) { format in
                                Text(format.title).tag(format)
                            }
                        }
                        .font(.system(size: 13, weight: .medium))
                        Text(exportFormat == .ios
                             ? "El formato iOS conserva todos los datos propios de IShalev. La importación detecta automáticamente respaldos iOS y Android."
                             : "El formato .shalev puede abrirse en Shalev para Android. Las fotos se conservan para IShalev como datos adicionales; Android no muestra esos datos adicionales ni los PIN locales.")
                            .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText).fixedSize(horizontal: false, vertical: true)
                        Text("La exportación usa AES-GCM y PBKDF2. Guarda la contraseña: no se puede recuperar.")
                            .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText).fixedSize(horizontal: false, vertical: true)
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
        .fileExporter(isPresented: $showExporter, document: exportDocument, contentType: exportContentType,
                      defaultFilename: exportFormat == .ios ? "respaldo-shalev-ios" : "respaldo-shalev-android") { result in
            if case .failure(let error) = result { alertMessage = error.localizedDescription }
            password = ""; confirmPassword = ""
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.shalevBackup, .shalevAndroidBackup], allowsMultipleSelection: false) { result in
            do {
                guard let url = try result.get().first else { return }
                let hasAccess = url.startAccessingSecurityScopedResource()
                defer { if hasAccess { url.stopAccessingSecurityScopedResource() } }
                let backup = try Data(contentsOf: url)
                pendingBackup = backup
                password = ""; confirmPassword = ""
                if AndroidBackupCodec.isAndroidBackup(backup) && !AndroidBackupCodec.requiresPassword(backup) {
                    showRestoreConfirmation = true
                } else {
                    passwordSheet = .restore
                }
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
                exportDocument = ShalevBackupDocument(data: try store.makeEncryptedBackup(password: password, format: exportFormat))
                exportContentType = exportFormat == .ios ? .shalevBackup : .shalevAndroidBackup
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
        if mode == .export {
            guard password.count >= 8 else { errorMessage = "Usa una contraseña de al menos 8 caracteres."; return }
            guard password == confirmPassword else { errorMessage = "Las contraseñas no coinciden."; return }
        } else {
            guard !password.isEmpty else { errorMessage = "Escribe la contraseña del respaldo."; return }
        }
        onConfirm()
        dismiss()
    }
}

private struct ShalevBackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.shalevBackup, .shalevAndroidBackup] }
    static var writableContentTypes: [UTType] { [.shalevBackup, .shalevAndroidBackup] }
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
    static let shalevAndroidBackup = UTType(exportedAs: "cu.shalev.android-backup", conformingTo: .data)
}

private struct HelpView: View {
    private let topics: [(String, String, String)] = [
        ("Abrir un turno", "Desde Inicio, abre un turno e indica el fondo de caja. Sin turno abierto no se habilitan las ventas.", "storefront.fill"),
        ("Vender y cobrar", "Agrega productos o servicios, elige efectivo o transferencia y confirma la operación. Puedes pegar el SMS de Transfermóvil para leer el importe y el número; Shalev no confirma el pago con el banco.", "cart.fill"),
        ("Inventario y servicios", "Busca y filtra artículos. En cada ficha puedes editar existencias, precios, mínimos y recetas; también puedes añadir una foto desde la fototeca.", "archivebox.fill"),
        ("Corregir una venta", "Desde Registros, modifica o anula una venta del turno abierto. Escribe un motivo; la operación original queda en el historial.", "pencil.and.list.clipboard"),
        ("Empleados", "En Ajustes → Empleados crea perfiles y elige permisos. Los perfiles son locales a este iPhone; volver al administrador exige autenticación del dispositivo.", "person.2.fill"),
        ("Cerrar caja", "Al cerrar el turno, cuenta el efectivo y compara el resultado con el efectivo esperado.", "checkmark.circle.fill"),
        ("Respaldos", "Exporta una copia iOS o un archivo .shalev compatible con Android. Al importar, el formato se detecta automáticamente; los respaldos Android sin contraseña también se pueden abrir.", "externaldrive.fill"),
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
                    Text("IShalev usa una interfaz y almacenamiento nativos de Apple y valida licencias bajo el contrato GL. Puede intercambiar respaldos .shalev con Android; la sincronización por red local entre dispositivos todavía no está disponible.")
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
