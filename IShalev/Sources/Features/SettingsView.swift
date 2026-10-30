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
    @State private var lockMessage: String?
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

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
                            .font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                        Text("Shalev guarda los datos en este teléfono y no usa una cuenta, servidor, analítica ni anuncios. El almacenamiento local se cifra con AES-GCM y una clave protegida por el llavero de iOS.")
                            .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
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
        .alert("Acceso con clave", isPresented: Binding(get: { lockMessage != nil }, set: { if !$0 { lockMessage = nil } })) {
            Button("Entendido", role: .cancel) { lockMessage = nil }
        } message: { Text(lockMessage ?? "") }
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
                .font(.shalev(18, weight: .semibold))
                .foregroundStyle(Color.shalevPrimary)
                .frame(width: 42, height: 42)
                .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 3) {
                Text("Acceso con clave")
                    .font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevText)
                Text(lockSubtitle)
                    .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 5)
            Toggle("", isOn: Binding(get: { appLockEnabled }, set: { requestLockChange($0) }))
                .labelsHidden()
                .tint(Color.shalevPrimary)
                .disabled(!canUseDeviceAuthentication)
                .accessibilityLabel("Acceso con clave")
        }
        .padding(12)
    }

    private var lockSubtitle: String {
        if !canUseDeviceAuthentication {
            return "Configura un bloqueo de pantalla en Ajustes del teléfono para usar esta opción."
        }
        return appLockEnabled
            ? "Activado: se pide al abrir Shalev y al volver tras 10 minutos."
            : "Pide tu huella, tu cara o el PIN del teléfono al abrir Shalev y al volver tras 10 minutos."
    }

    /// Activar o desactivar exige confirmar la identidad primero, como en Android.
    private func requestLockChange(_ enabled: Bool) {
        guard canUseDeviceAuthentication else {
            lockMessage = "Configura un bloqueo de pantalla en Ajustes del teléfono para usar esta opción."
            return
        }
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            lockMessage = "Configura un bloqueo de pantalla en Ajustes del teléfono para usar esta opción."
            return
        }
        context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Confirma que eres tú") { success, _ in
            Task { @MainActor in
                if success {
                    appLockEnabled = enabled
                    lockMessage = enabled ? "Acceso con clave activado" : "Acceso con clave desactivado"
                } else {
                    lockMessage = "No se pudo confirmar. Inténtalo de nuevo."
                }
            }
        }
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
        let apilar = dynamicTypeSize >= .xxxLarge
        return HStack(spacing: 12) {
            Image(systemName: symbol).font(.shalevIcono(18, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                .frame(width: 42, height: 42).background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevText)
                Text(subtitle).font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText).lineLimit(apilar ? nil : 2)
            }
            Spacer(minLength: 5)
            if let badge {
                Text(badge)
                    .font(.shalevIcono(11, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(minWidth: 20, minHeight: 20)
                    .background(Color.shalevError, in: Circle())
                    .accessibilityLabel("\(badge) pasos pendientes")
            }
            Image(systemName: "chevron.right").font(.shalevIcono(11, weight: .semibold)).foregroundStyle(Color.shalevSecondaryText)
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
    private enum ProfileFocus: Hashable { case firstName, lastName, ci }
    @FocusState private var focusedField: ProfileFocus?

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
                    .font(.shalev(13))
                    .foregroundStyle(Color.shalevText)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(14)
                    .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                ShalevCard {
                    VStack(alignment: .leading, spacing: 12) {
                        profileField("Nombre", text: $firstName, placeholder: "Nombre",
                                     error: showErrors ? nameError : nil, capitalization: .words,
                                     focus: .firstName, submit: .next) { focusedField = .lastName }
                        profileField("Apellidos", text: $lastName, placeholder: "Apellidos",
                                     error: showErrors ? lastNameError : nil, capitalization: .words,
                                     focus: .lastName, submit: .next) { focusedField = .ci }
                        profileField("Carnet de identidad (CI)", text: $identification, placeholder: "85010112345",
                                     error: showErrors ? identificationError : nil, capitalization: .characters,
                                     focus: .ci, submit: .done) { focusedField = nil }
                        Text("Solo se envía al pedir la licencia")
                            .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
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
                            .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
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
                              error: String?, capitalization: TextInputAutocapitalization = .never,
                              focus: ProfileFocus, submit: SubmitLabel,
                              onSubmit: @escaping () -> Void) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).font(.shalev(12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
            TextField(placeholder, text: text)
                .textInputAutocapitalization(capitalization)
                .autocorrectionDisabled()
                .focused($focusedField, equals: focus)
                .submitLabel(submit)
                .onSubmit(onSubmit)
                .font(.shalev(14))
                .padding(11)
                .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
                .overlay {
                    RoundedRectangle(cornerRadius: 11)
                        .stroke(error == nil ? Color.shalevOutlineVariant : Color.shalevError, lineWidth: 1)
                }
            if let error {
                Text(error).font(.shalev(11)).foregroundStyle(Color.shalevError)
            }
        }
    }

    private func moduleRow(_ title: String, detail: String, isOn: Bool, symbol: String,
                           action: @escaping () -> Void) -> some View {
        let onlyOne = isOn && activeModules == 1
        return Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: isOn ? "checkmark.square.fill" : "square")
                    .font(.shalev(19))
                    .foregroundStyle(isOn ? Color.shalevPrimary : Color.shalevSecondaryText)
                Image(systemName: symbol)
                    .font(.shalev(15))
                    .foregroundStyle(Color.shalevSecondaryText)
                    .frame(width: 22)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.shalev(14, weight: .medium)).foregroundStyle(Color.shalevText)
                    Text(detail).font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
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
    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento
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
                        .font(.shalev(11))
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
            .font(.shalev(16, weight: .semibold))
            .foregroundStyle(Color.shalevText)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 18)
            .padding(.top, 16)
            .padding(.bottom, 6)
            .accessibilityAddTraits(.isHeader)
    }

    private func emptyLine(_ text: String) -> some View {
        Text(text)
            .font(.shalev(12))
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
                        .font(.shalev(19))
                        .foregroundStyle(selected ? Color.shalevPrimary : Color.shalevSecondaryText)
                    
                    // Miniatura del banco si es tarjeta (basado en el prefijo)
                    if kind == .card, let bank = bancoCubano(title) {
                        ShalevBankThumbnail(banco: bank)
                            .padding(.leading, 4)
                    } else {
                        Image(systemName: kind.symbol)
                            .font(.shalev(17))
                            .foregroundStyle(Color.shalevText)
                    }
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title).font(.shalev(15)).foregroundStyle(Color.shalevText)
                        if let subtitle {
                            Text(subtitle).font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
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
                Image(systemName: "pencil").font(.shalev(16)).foregroundStyle(Color.shalevText)
                    .frame(width: 34, height: 34)
                    .shalevToque()
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Editar \(title)")

            Button { askDelete(kind, id, title) } label: {
                Image(systemName: "trash").font(.shalev(16)).foregroundStyle(Color.shalevText)
                    .frame(width: 34, height: 34)
                    .shalevToque()
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Eliminar \(title)")
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 11)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(selected ? Color.shalevPrimaryContainer : Color.shalevSurface)
    }

    private func floatingAdd: some View {
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
            ShalevFloatingButton(title: expanded ? "Cerrar" : "Agregar",
                                 symbol: expanded ? "xmark" : "plus") {
                withAnimation(ShalevMotion.cambio(reducirMovimiento)) { expanded.toggle() }
            }
        }
        .padding(.trailing, 18)
        .padding(.bottom, 22)
    }

    private func addPill(_ title: String, symbol: String, action: @escaping () -> Void) -> some View {
        HStack(spacing: 8) {
            Text(title)
                .font(.shalev(12, weight: .semibold))
                .foregroundStyle(Color.shalevText)
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            Button(action: action) {
                Image(systemName: symbol)
                    .font(.shalev(15, weight: .semibold))
                    .foregroundStyle(Color.shalevPrimary)
                    .frame(width: 40, height: 40)
                    .background(Color.shalevPrimaryContainer, in: Circle())
                    .shalevToque()
            }
            .buttonStyle(.plain)
            .accessibilityLabel(title)
        }
    }

    private func maskedCard(_ number: String) -> String {
        let digits = number.filter(\.isNumber)
        return digits.count >= 4 ? "•••• \(digits.suffix(4))" : "••••"
    }    private func addPill(_ title: String, symbol: String, action: @escaping () -> Void) -> some View {
        HStack(spacing: 8) {
            Text(title)
                .font(.shalev(12, weight: .semibold))
                .foregroundStyle(Color.shalevText)
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            Button(action: action) {
                Image(systemName: symbol)
                    .font(.shalev(15, weight: .semibold))
                    .foregroundStyle(Color.shalevPrimary)
                    .frame(width: 40, height: 40)
                    .background(Color.shalevPrimaryContainer, in: Circle())
                    .shalevToque()
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
                            .font(.shalev(12, weight: .medium))
                            .foregroundStyle(Color.shalevSecondaryText)
                        TextField(kind == .phone ? "5123 4567" : "9205 0000 0000 0000", text: $number)
                            .keyboardType(kind == .phone ? .phonePad : .numberPad)
                            .textContentType(kind == .phone ? .telephoneNumber : nil)
                            .autocorrectionDisabled()
                            .font(.shalev(14))
                            .padding(11)
                            .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
                            .overlay {
                                RoundedRectangle(cornerRadius: 11)
                                    .stroke(shownError == nil ? Color.shalevOutlineVariant : Color.shalevError, lineWidth: 1)
                            }
                            .onChange(of: number) { _, _ in touched = true }
                        if let shownError {
                            Text(shownError).font(.shalev(11)).foregroundStyle(Color.shalevError)
                        }
                    }

                    if kind == .card, let bank = bancoCubano(number) {
                        HStack(spacing: 10) {
                            Image(systemName: "creditcard.fill")
                                .font(.shalev(18))
                                .foregroundStyle(Color.shalevSecondaryText)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(bank.nombre)
                                    .font(.shalev(14, weight: .medium))
                                    .foregroundStyle(Color.shalevText)
                                Text("Identificación orientativa por prefijo")
                                    .font(.shalev(11))
                                    .foregroundStyle(Color.shalevSecondaryText)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(10)
                        .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }

                    VStack(alignment: .leading, spacing: 5) {
                        Text("Nombre para reconocerlo (opcional)")
                            .font(.shalev(12, weight: .medium))
                            .foregroundStyle(Color.shalevSecondaryText)
                        TextField(kind == .phone ? "Mi móvil" : "Tarjeta del banco", text: $alias)
                            .autocorrectionDisabled()
                            .submitLabel(.done)
                            .font(.shalev(14))
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

    private var adjustments: [PriceAdjustment] { store.data.settings.priceAdjustments }
    private var hasProducts: Bool { !store.activeProducts.isEmpty }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            ScrollView {
                VStack(spacing: 12) {
                    Text("Sube o baja precios al cobrar, según el pago o el importe. El precio guardado no cambia.")
                        .font(.shalev(13))
                        .foregroundStyle(Color.shalevText)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(14)
                        .background(Color.shalevSurfaceVariant, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                    if adjustments.isEmpty {
                        ShalevEmptyState(symbol: "percent",
                                         title: "Aún no hay ajustes de precio",
                                         detail: hasProducts ? "Ej.: +5 % con transferencia."
                                                             : "Primero agrega productos en Inventario.")
                            .padding(.top, 20)
                    } else {
                        VStack(spacing: 0) {
                            ForEach(adjustments) { adjustment in
                                adjustmentRow(adjustment)
                                if adjustment.id != adjustments.last?.id {
                                    Divider().padding(.leading, 16)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 10)
                .padding(.bottom, 110)
                .frame(maxWidth: 680)
                .frame(maxWidth: .infinity)
            }

            if hasProducts {
                ShalevFloatingButton(title: "Nuevo ajuste de precios", symbol: "plus") { showCreate = true }
                    .padding(.trailing, 18)
                    .padding(.bottom, 22)
            }
        }
        .background(Color.shalevBackground)
        .navigationTitle("Precios")
        .navigationBarTitleDisplayMode(.inline)
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

    private func adjustmentRow(_ adjustment: PriceAdjustment) -> some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 2)
                .fill(adjustment.active ? Color.shalevPrimary : Color.shalevOutlineVariant)
                .frame(width: 4, height: 38)

            Button { editor = adjustment } label: {
                HStack {
                    Text(adjustment.name)
                        .font(.shalev(15))
                        .foregroundStyle(Color.shalevText)
                    Spacer(minLength: 8)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(adjustment.name)
            .accessibilityHint(adjustmentSummary(adjustment))

            Text(percentText(adjustment.basisPoints))
                .font(.shalev(13, weight: .semibold))
                .foregroundStyle(Color.shalevText)
                .accessibilityLabel("Ajuste de \(percentText(adjustment.basisPoints))")

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
            .labelsHidden()
            .tint(Color.shalevPrimary)
            .accessibilityLabel(adjustment.active ? "Activo: \(adjustment.name)" : "Pausado: \(adjustment.name)")
        }
        .padding(.vertical, 9)
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
        let text = NSDecimalNumber(decimal: value).stringValue
        return "\(basisPoints > 0 ? "+" : "")\(text) %"
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
                                Text("Método de pago").font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
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
                                Text("Artículos incluidos").font(.shalev(13, weight: .semibold)).foregroundStyle(Color.shalevText)
                                Text("El ajuste solo se aplica a los artículos seleccionados. Si hay varios ajustes para un mismo artículo, se suman.")
                                    .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                                ForEach(store.activeProducts) { product in
                                    Toggle(isOn: Binding(get: { productIDs.contains(product.id) }, set: { selected in
                                        if selected { productIDs.insert(product.id) } else { productIDs.remove(product.id) }
                                    })) {
                                        HStack {
                                            Text(product.name).font(.shalev(13)).foregroundStyle(Color.shalevText)
                                            Spacer()
                                            Text(Cup(cents: product.priceCents).formatted)
                                                .font(.shalev(11, weight: .semibold)).foregroundStyle(Color.shalevSecondaryText)
                                        }
                                    }
                                    .tint(Color.shalevPrimary)
                                }
                                if store.activeProducts.isEmpty {
                                    Text("Crea primero un artículo en Inventario.").font(.shalev(12)).foregroundStyle(Color.shalevError)
                                }
                            }
                            Text("Usa porcentaje negativo para un descuento y positivo para un recargo.")
                                .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
                            Button { save() } label: { Label("Guardar ajuste", systemImage: "checkmark") }
                                .buttonStyle(ShalevPrimaryButtonStyle()).disabled(working)
                            if working { ProgressView().accessibilityLabel("Cargando").frame(maxWidth: .infinity) }
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

@MainActor
private struct BackupSettingsView: View {
    @ObservedObject var store: ShalevStore
    @Environment(\.dismiss) private var dismiss

    // Exportar
    @State private var exportStep = 1
    @State private var protect = true
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var showExportErrors = false
    @State private var exportFormat: PortableBackupFormat = .ios
    @State private var exportDocument = ShalevBackupDocument(data: Data())
    @State private var exportContentType: UTType = .shalevBackup
    @State private var showExporter = false
    @State private var exportDone: String?

    // Importar
    @State private var importStep = 1
    @State private var showImporter = false
    @State private var pendingBackup: Data?
    @State private var importNeedsPassword = true
    @State private var importPassword = ""
    @State private var importPasswordError: String?
    @State private var showPasswordSheet = false
    @State private var showRestoreConfirmation = false
    @State private var result: (title: String, detail: String)?
    private enum BackupFocus: Hashable { case password, confirm }
    @FocusState private var focusedField: BackupFocus?

    private var passwordError: String? {
        guard protect else { return nil }
        return password.count < 8 ? "Mínimo 8 caracteres" : nil
    }

    private var repeatError: String? {
        guard protect, passwordError == nil else { return nil }
        return confirmPassword == password ? nil : "Las contraseñas no coinciden"
    }

    private var exportValid: Bool { passwordError == nil && repeatError == nil }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                VStack(spacing: 5) {
                    Text("Guarda una copia protegida de todo el negocio.")
                        .font(.shalev(13))
                        .foregroundStyle(Color.shalevText)
                        .multilineTextAlignment(.center)
                    Text("La licencia no viaja en el respaldo: en otro teléfono hace falta una licencia nueva.")
                        .font(.shalev(12))
                        .foregroundStyle(Color.shalevSecondaryText)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(15)
                .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                exportCard
                importCard
            }
            .padding(18)
            .padding(.bottom, 10)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .background(Color.shalevBackground)
        .navigationTitle("Respaldo")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Cerrar") { dismiss() }.foregroundStyle(Color.shalevPrimary)
            }
        }
        .fileExporter(isPresented: $showExporter, document: exportDocument, contentType: exportContentType,
                      defaultFilename: exportFormat == .ios ? "respaldo-shalev-ios" : "respaldo-shalev-android") { exportResult in
            switch exportResult {
            case .success:
                let name = exportFormat == .ios ? "respaldo-shalev-ios.shalev" : "respaldo-shalev-android.shalev"
                exportDone = "\(name) guardado.\n\(summaryText).\n\(protect ? "Guárdalo junto con la contraseña: sin ella no se puede abrir." : "Sin contraseña, quien consiga el archivo podrá leer tus ventas y los datos de tus clientes.")"
                exportStep = 3
                password = ""
                confirmPassword = ""
            case .failure(let error):
                result = ("No se pudo guardar el respaldo", error.localizedDescription)
            }
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.shalevBackup, .shalevAndroidBackup],
                      allowsMultipleSelection: false) { picked in
            handleImport(picked)
        }
        .sheet(isPresented: $showPasswordSheet) {
            ImportPasswordSheet(password: $importPassword, error: $importPasswordError) {
                showPasswordSheet = false
                importStep = 4
                showRestoreConfirmation = true
            }
            .presentationDetents([.medium]).presentationDragIndicator(.visible)
        }
        .confirmationDialog("¿Reemplazar los datos del teléfono?", isPresented: $showRestoreConfirmation, titleVisibility: .visible) {
            Button("Importar", role: .destructive) { restore() }
            Button("Cancelar", role: .cancel) { cancelImport() }
        } message: {
            Text("Se reemplazarán TODOS los datos de este teléfono: productos, insumos, inventario, turnos, ventas y configuración. No se puede deshacer.")
        }
        .alert(result?.title ?? "", isPresented: Binding(get: { result != nil }, set: { if !$0 { result = nil } })) {
            Button("Aceptar", role: .cancel) { result = nil }
        } message: { Text(result?.detail ?? "") }
    }

    // MARK: - Exportar

    private var exportCard: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 12) {
                ShalevSectionHeader(title: "Exportar")
                Text("Se guarda todo lo del negocio.")
                    .font(.shalev(12))
                    .foregroundStyle(Color.shalevSecondaryText)

                ShalevStepper(current: exportStep, total: 3, title: exportTitle,
                              onBack: exportBackAction, onNext: exportNextAction)

                if exportStep == 1 {
                    Toggle(isOn: Binding(get: { protect }, set: { protect = $0; showExportErrors = false })) {
                        Label("Proteger con contraseña", systemImage: "lock.fill")
                            .font(.shalev(14, weight: .medium))
                            .foregroundStyle(Color.shalevText)
                    }
                    .tint(Color.shalevPrimary)

                    if protect {
                        backupField("Contraseña", text: $password, secure: true,
                                    error: showExportErrors ? passwordError : nil,
                                    help: "Anótala en un lugar seguro: sin ella no se puede recuperar",
                                    focus: .password, submit: .next) { focusedField = .confirm }
                        backupField("Repite la contraseña", text: $confirmPassword, secure: true,
                                    error: showExportErrors ? repeatError : nil,
                                    focus: .confirm, submit: .done) { focusedField = nil }
                    } else {
                        Text("Sin contraseña, quien consiga este archivo podrá leer tus ventas y los datos de tus clientes sin ninguna clave.")
                            .font(.shalev(12))
                            .foregroundStyle(Color.shalevError)
                            .fixedSize(horizontal: false, vertical: true)
                        Text("Sin contraseña la copia se guarda en formato .shalev, que también abre Android.")
                            .font(.shalev(11))
                            .foregroundStyle(Color.shalevSecondaryText)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                } else if exportStep == 2 {
                    Text(protect ? "Contraseña lista." : "Ahora elige dónde guardar el respaldo.")
                        .font(.shalev(13))
                        .foregroundStyle(Color.shalevText)

                    if protect {
                        VStack(alignment: .leading, spacing: 5) {
                            Text("Formato")
                                .font(.shalev(12, weight: .medium))
                                .foregroundStyle(Color.shalevSecondaryText)
                            Picker("Formato", selection: $exportFormat) {
                                ForEach(PortableBackupFormat.allCases) { format in
                                    Text(format.title).tag(format)
                                }
                            }
                            .pickerStyle(.segmented)
                            Text(exportFormat == .ios
                                 ? "El formato IShalev conserva todos los datos propios de la app."
                                 : "El formato .shalev se puede abrir en Shalev para Android.")
                                .font(.shalev(11))
                                .foregroundStyle(Color.shalevSecondaryText)
                        }
                    }

                    Button { saveExport() } label: {
                        Label("Guardar en el teléfono", systemImage: "square.and.arrow.down")
                    }
                    .buttonStyle(ShalevPrimaryButtonStyle())
                } else {
                    Text(exportDone ?? "")
                        .font(.shalev(13))
                        .foregroundStyle(Color.shalevText)
                        .fixedSize(horizontal: false, vertical: true)
                    Button {
                        exportStep = 1
                        exportDone = nil
                        protect = true
                        exportFormat = .ios
                    } label: {
                        Label("Hacer otro respaldo", systemImage: "arrow.clockwise")
                    }
                    .buttonStyle(ShalevTonalButtonStyle())
                }
            }
        }
    }

    private var exportBackAction: (() -> Void)? {
        exportStep == 2 ? { exportStep = 1 } : nil
    }

    private var exportNextAction: (() -> Void)? {
        exportStep == 1 ? { advanceExport() } : nil
    }

    private var exportTitle: String {
        switch exportStep {
        case 2: "Elige dónde guardarlo"
        case 3: "Respaldo listo"
        default: "Protégelo (opcional)"
        }
    }

    private func advanceExport() {
        showExportErrors = true
        guard exportValid else { return }
        if !protect { exportFormat = .android }
        exportStep = 2
    }

    private func saveExport() {
        let finalPassword = protect ? password : ""
        do {
            exportDocument = ShalevBackupDocument(data: try store.makeEncryptedBackup(password: finalPassword, format: exportFormat))
            exportContentType = exportFormat == .ios ? .shalevBackup : .shalevAndroidBackup
            showExporter = true
        } catch {
            result = ("No se pudo guardar el respaldo", "No se pudo completar. Inténtalo de nuevo.")
        }
    }

    // MARK: - Importar

    private var importCard: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 12) {
                ShalevSectionHeader(title: "Importar")
                ShalevStepper(current: importStep, total: 4, title: importTitle)
                Text("Elige un archivo .shalev.")
                    .font(.shalev(12))
                    .foregroundStyle(Color.shalevSecondaryText)
                Button { showImporter = true } label: {
                    Label("Elegir archivo", systemImage: "doc.badge.plus")
                }
                .buttonStyle(ShalevTonalButtonStyle())
                if importStep >= 3 {
                    Text("Paso \(importStep) de 4: \(importTitle.prefix(1).lowercased() + String(importTitle.dropFirst()))")
                        .font(.shalev(11))
                        .foregroundStyle(Color.shalevSecondaryText)
                }
            }
        }
    }

    private var importTitle: String {
        switch importStep {
        case 2: "Comprobando el archivo"
        case 3: "Escribe la contraseña del respaldo"
        case 4: "Pulsa Importar para reemplazar tus datos"
        default: "Elige el archivo .shalev"
        }
    }

    private func handleImport(_ picked: Result<[URL], Error>) {
        switch picked {
        case .failure:
            result = ("No se pudo abrir el archivo", "Vuelve a elegir el archivo o cópialo al teléfono primero. Tus datos no se han tocado.")
        case .success(let urls):
            guard let url = urls.first else { return }
            importStep = 2
            let hasAccess = url.startAccessingSecurityScopedResource()
            defer { if hasAccess { url.stopAccessingSecurityScopedResource() } }
            do {
                let backup = try Data(contentsOf: url)
                pendingBackup = backup
                importPassword = ""
                importNeedsPassword = !AndroidBackupCodec.isAndroidBackup(backup) || AndroidBackupCodec.requiresPassword(backup)
                if importNeedsPassword {
                    importStep = 3
                    showPasswordSheet = true
                } else {
                    importStep = 4
                    showRestoreConfirmation = true
                }
            } catch {
                importStep = 1
                result = ("No se pudo abrir el archivo", "Vuelve a elegir el archivo o cópialo al teléfono primero. Tus datos no se han tocado.")
            }
        }
    }

    private func restore() {
        guard let backup = pendingBackup else { cancelImport(); return }
        do {
            try store.restoreEncryptedBackup(backup, password: importPassword)
            result = ("Respaldo importado", "\(summaryText).")
            importStep = 1
        } catch {
            let message = error.localizedDescription
            if message.localizedCaseInsensitiveContains("contraseña") {
                importStep = 3
                importPasswordError = "Contraseña incorrecta"
                importPassword = ""
                showPasswordSheet = true
            } else {
                importStep = 1
                result = ("No se pudo importar", "El archivo no es un respaldo de Shalev o está dañado. Tus datos no se han tocado.")
            }
        }
        pendingBackup = nil
        importPassword = ""
    }

    private func cancelImport() {
        pendingBackup = nil
        importPassword = ""
        importPasswordError = nil
        importStep = 1
    }

    private var summaryText: String {
        let data = store.data
        func plural(_ count: Int, _ one: String, _ many: String) -> String { "\(count) \(count == 1 ? one : many)" }
        return "Respaldo con " + [
            plural(data.products.count, "producto", "productos"),
            plural(data.services.count, "servicio", "servicios"),
            plural(data.sales.count, "venta", "ventas"),
        ].joined(separator: ", ")
    }

    private func backupField(_ title: String, text: Binding<String>, secure: Bool,
                             error: String?, help: String? = nil,
                             focus: BackupFocus, submit: SubmitLabel,
                             onSubmit: @escaping () -> Void) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title)
                .font(.shalev(12, weight: .medium))
                .foregroundStyle(Color.shalevSecondaryText)
            Group {
                if secure {
                    SecureField(title, text: text)
                } else {
                    TextField(title, text: text)
                }
            }
            .focused($focusedField, equals: focus)
            .submitLabel(submit)
            .onSubmit(onSubmit)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .font(.shalev(14))
            .padding(11)
            .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
            .overlay {
                RoundedRectangle(cornerRadius: 11)
                    .stroke(error == nil ? Color.shalevOutlineVariant : Color.shalevError, lineWidth: 1)
            }
            if let error {
                Text(error).font(.shalev(11)).foregroundStyle(Color.shalevError)
            } else if let help {
                Text(help).font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
            }
        }
    }
}

@MainActor
private struct ImportPasswordSheet: View {
    @Binding var password: String
    @Binding var error: String?
    let onConfirm: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 14) {
                Text("Escribe la contraseña con la que se guardó el respaldo.")
                    .font(.shalev(13))
                    .foregroundStyle(Color.shalevSecondaryText)
                    .multilineTextAlignment(.center)
                SecureField("Contraseña del respaldo", text: $password)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.done)
                    .font(.shalev(14))
                    .padding(11)
                    .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
                    .overlay {
                        RoundedRectangle(cornerRadius: 11)
                            .stroke(error == nil ? Color.shalevOutlineVariant : Color.shalevError, lineWidth: 1)
                    }
                if let error {
                    Text(error).font(.shalev(11)).foregroundStyle(Color.shalevError)
                }
                Button("Continuar") {
                    guard !password.isEmpty else { error = "Escribe la contraseña del respaldo."; return }
                    error = nil
                    onConfirm()
                }
                .buttonStyle(ShalevPrimaryButtonStyle())
                Spacer(minLength: 0)
            }
            .padding(18)
            .background(Color.shalevBackground)
            .navigationTitle("Contraseña del respaldo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
            }
        }
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

private enum HelpCategory: String, CaseIterable, Identifiable {
    case start, sell, inventory, data, employees, help

    var id: String { rawValue }

    var title: String {
        switch self {
        case .start: "Empezar"
        case .sell: "Vender y cobrar"
        case .inventory: "Inventario"
        case .data: "Tus datos y tu licencia"
        case .employees: "Empleados"
        case .help: "Ayuda"
        }
    }

    var detail: String {
        switch self {
        case .start: "Qué es cada botón y cómo dejar Shalev listo"
        case .sell: "El turno, la venta y el cobro por transferencia"
        case .inventory: "Productos, insumos y elaborados"
        case .data: "Respaldo y licencia"
        case .employees: "Las apps de tus empleados"
        case .help: "A quién escribir cuando algo no sale"
        }
    }

    var symbol: String {
        switch self {
        case .start: "checklist"
        case .sell: "cart.fill"
        case .inventory: "archivebox.fill"
        case .data: "externaldrive.fill"
        case .employees: "iphone"
        case .help: "questionmark.circle.fill"
        }
    }
}

private enum HelpDiagram {
    case bar, shift, sale, backup
}

/// Qué módulos deben estar activos para que el tema se muestre (igual que Android).
private enum HelpScope {
    case always
    case salesOrInventory
    case salesOrServices
    case servicesOnly
}

private struct HelpTopic: Identifiable {
    let title: String
    let steps: [String]
    let category: HelpCategory
    var diagram: HelpDiagram? = nil
    var scope: HelpScope = .always

    var id: String { title }
}

private struct HelpView: View {
    @AppStorage("shalev.module.sales") private var salesEnabled = true
    @AppStorage("shalev.module.inventory") private var inventoryEnabled = true
    @AppStorage("shalev.module.services") private var servicesEnabled = true
    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento
    @State private var openTopic: String?

    private static let intro = "Shalev te ayuda a vender, controlar tu inventario y ver cuánto ganas. Elige una sección y toca un tema para ver los pasos."

    private static let topics: [HelpTopic] = [
        HelpTopic(title: "Primeros pasos", steps: [
            "Abajo tienes 5 botones: Inicio, Inventario, Servicios, Registros y Ajustes.",
            "Toca una sección de la barra para cambiar de pantalla.",
            "En Ajustes → Completar configuración escribe tus datos y elige los avisos.",
            "Tienes 7 días de prueba gratis. Después hace falta una licencia.",
            "Opcional: Ajustes → Acceso con clave pide tu huella o el PIN del teléfono al abrir Shalev.",
        ], category: .start, diagram: .bar),

        HelpTopic(title: "Abrir y cerrar el turno", steps: [
            "El turno es la jornada de trabajo: sin turno abierto no se puede vender.",
            "En Inicio toca Abrir turno. Arriba verás el tiempo que lleva abierto.",
            "Al terminar el día toca Cerrar turno: Shalev guarda un resumen con todo lo vendido.",
            "Si sales de Shalev con el turno abierto, verás un aviso arriba en la barra del teléfono.",
        ], category: .sell, diagram: .shift, scope: .salesOrServices),

        HelpTopic(title: "Vender", steps: [
            "Toca Nueva venta, elige Productos o Servicios (van por separado) y la cantidad.",
            "Elige cómo te pagan: efectivo o transferencia.",
            "Revisa el total y confirma. La venta queda en Registros.",
        ], category: .sell, diagram: .sale, scope: .salesOrServices),

        HelpTopic(title: "Cobrar por transferencia", steps: [
            "En Ajustes → Pago electrónico agrega tu teléfono y tu tarjeta.",
            "Toca el que quieras usar para cobrar: queda marcado \"En uso\".",
            "En la venta por transferencia anota los datos del cliente y el nº de transacción.",
        ], category: .sell, scope: .salesOrServices),

        HelpTopic(title: "Ver lo vendido", steps: [
            "En Registros ves ventas, servicios, transferencias, movimientos y turnos.",
            "Toca Filtrar para elegir las fechas: hoy, 7 días, este mes…",
            "En ventas, transferencias y servicios también puedes filtrar por importe.",
            "En Movimientos solo se filtra por fecha: un movimiento no tiene importe.",
            "Marca con la casilla lo que quieras exportar y toca Compartir.",
        ], category: .sell, scope: .salesOrServices),

        HelpTopic(title: "Agregar productos", steps: [
            "Ve a Inventario y toca +.",
            "Escribe el nombre, el precio de compra, el precio de venta y la cantidad.",
            "Si ya hay otro con el mismo nombre, escribe en Descripción qué lo distingue (p. ej. Lata 350 ml).",
            "Toca el botón ✓ (Guardar) arriba a la derecha.",
        ], category: .inventory, scope: .salesOrInventory),

        HelpTopic(title: "Buscar y filtrar el inventario", steps: [
            "El buscador encuentra por nombre, categoría o código.",
            "Toca Filtrar y elige el Estado: Todos, Stock bajo, Stock crítico, Próximos a caducar o Sin existencia.",
            "También puedes filtrar por Tipo (artículos, elaborados o insumos) y por Categoría.",
            "El chip bajo el buscador resume el filtro activo; Quitar filtros lo borra.",
        ], category: .inventory, scope: .salesOrInventory),

        HelpTopic(title: "Insumos y elaborados", steps: [
            "Los insumos (harina, azúcar…) están en Inventario, en la categoría Insumos.",
            "Para crear uno: Inventario → + → Escribir y elige la categoría Insumos.",
            "Si le pones precio de venta a un insumo, también lo puedes vender.",
            "Crea el producto elaborado con su receta. Al venderlo, sus insumos se descuentan solos.",
            "Verás para cuántos alcanzan los insumos; si no alcanzan, no se puede vender.",
        ], category: .inventory, scope: .salesOrInventory),

        HelpTopic(title: "Servicios", steps: [
            "Sirve para lo que ofreces que no es un producto (cortes de pelo, arreglos, entregas…).",
            "Ve a Servicios y toca +. Escribe el nombre, el tipo y el importe.",
            "Si el servicio gasta insumos, agrégalos: se descuentan al venderlo.",
            "Para venderlo: Nueva venta → Servicios. En Inicio verás los más y menos vendidos.",
        ], category: .inventory, scope: .servicesOnly),

        HelpTopic(title: "Guardar una copia", steps: [
            "Ajustes → Respaldo → Exportar y toca Siguiente.",
            "Opcional: activa Proteger con contraseña, escríbela dos veces y anótala.",
            "Toca Guardar en el teléfono o Enviar a otra app (Drive, Telegram…).",
            "Para recuperarla: Respaldo → Elegir archivo (y la contraseña, si la tiene).",
        ], category: .data, diagram: .backup),

        HelpTopic(title: "Licencia", steps: [
            "Ajustes → Licencia muestra cuántos días te quedan. Sigue los 3 pasos.",
            "Paso 1: revisa tus datos. Paso 2: elige el tipo, WhatsApp o SMS, y toca Solicitar. Solo pulsa Enviar.",
            "Paso 3: copia todo el mensaje que te respondan, toca Pegar y luego Activar.",
            "Si la activación llega en varios SMS, copia y pega todos.",
            "Para renovar con lo mismo, toca Renovar igual. Si renuevas antes de que venza no pierdes días.",
        ], category: .data),

        HelpTopic(title: "Apps vinculadas", steps: [
            "En tu teléfono: Ajustes → Apps vinculadas → +. Tu licencia dice cuántas secundarias puedes tener.",
            "En el del empleado: Usar esta app como secundaria, escribe su teléfono y escanea tu código (misma wifi).",
            "Su teléfono va en su QR de cobro: el SMS de la transferencia le llega a él.",
            "El empleado no cierra su turno: lo solicita y tú lo apruebas o rechazas. Sin red sigue vendiendo.",
            "Toca su app para sus permisos, su tarjeta de cobro o «Pedir cierre» de su turno abierto.",
        ], category: .employees),

        HelpTopic(title: "¿Necesitas ayuda?", steps: [
            "Ajustes → Soporte: escribe al desarrollador por WhatsApp o SMS.",
        ], category: .help),
    ]

    private var visibleTopics: [HelpTopic] {
        HelpView.topics.filter { topic -> Bool in
            switch topic.scope {
            case .always: return true
            case .salesOrInventory: return salesEnabled || inventoryEnabled
            case .salesOrServices: return salesEnabled || servicesEnabled
            case .servicesOnly: return servicesEnabled
            }
        }
    }

    private var sections: [(HelpCategory, [HelpTopic])] {
        HelpCategory.allCases.compactMap { category in
            let topics = visibleTopics.filter { $0.category == category }
            return topics.isEmpty ? nil : (category, topics)
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Text(HelpView.intro)
                    .font(.shalev(13))
                    .foregroundStyle(Color.shalevText)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                    .padding(14)
                    .background(Color.shalevSurfaceVariant, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                ForEach(Array(sections.enumerated()), id: \.offset) { _, section in
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: section.0.symbol)
                            .font(.shalev(18))
                            .foregroundStyle(Color.shalevPrimary)
                            .frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(section.0.title)
                                .font(.shalev(16, weight: .semibold))
                                .foregroundStyle(Color.shalevText)
                            Text(section.0.detail)
                                .font(.shalev(12))
                                .foregroundStyle(Color.shalevSecondaryText)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.top, 8)
                    .accessibilityElement(children: .combine)
                    .accessibilityAddTraits(.isHeader)

                    ForEach(section.1) { topic in
                        topicCard(topic)
                    }
                }
            }
            .padding(18)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .background(Color.shalevBackground)
        .navigationTitle("Ayuda")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func topicCard(_ topic: HelpTopic) -> some View {
        let expanded = openTopic == topic.id
        return ShalevCard {
            VStack(alignment: .leading, spacing: 8) {
                Button {
                    withAnimation(ShalevMotion.cambio(reducirMovimiento)) {
                        openTopic = expanded ? nil : topic.id
                    }
                } label: {
                    HStack(spacing: 9) {
                        Text(topic.title)
                            .font(.shalev(15, weight: .medium))
                            .foregroundStyle(Color.shalevText)
                        Spacer(minLength: 8)
                        Image(systemName: expanded ? "minus" : "plus")
                            .font(.shalev(13, weight: .semibold))
                            .foregroundStyle(Color.shalevSecondaryText)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(topic.title)
                .accessibilityValue(expanded ? "Abierto" : "Cerrado")

                if expanded {
                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(Array(topic.steps.enumerated()), id: \.offset) { index, step in
                            Text("\(index + 1). \(step)")
                                .font(.shalev(13))
                                .foregroundStyle(Color.shalevText)
                                .fixedSize(horizontal: false, vertical: true)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                    if let diagram = topic.diagram {
                        diagramView(diagram).padding(.top, 4)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func diagramView(_ diagram: HelpDiagram) -> some View {
        switch diagram {
        case .bar:
            HStack(spacing: 5) {
                ForEach(["Inicio", "Inventario", "Servicios", "Registros", "Ajustes"], id: \.self) { label in
                    Text(label)
                        .font(.shalev(9, weight: .medium))
                        .foregroundStyle(label == "Inicio" ? Color.shalevOnPrimaryContainer : Color.shalevSecondaryText)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                        .background(label == "Inicio" ? Color.shalevPrimaryContainer : Color.shalevSurfaceVariant,
                                    in: RoundedRectangle(cornerRadius: 7, style: .continuous))
                }
            }
        case .shift:
            flow(["Turno cerrado", "Abrir turno", "Vender", "Cerrar turno"])
        case .sale:
            flow(["Nueva venta", "Productos o Servicios", "Efectivo o transferencia", "Confirmar"])
        case .backup:
            flow(["Este teléfono", "Archivo .shalev cifrado", "El teléfono nuevo"])
        }
    }

    private func flow(_ steps: [String]) -> some View {
        VStack(spacing: 4) {
            ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                Text(step)
                    .font(.shalev(12, weight: .medium))
                    .foregroundStyle(Color.shalevText)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.shalevSurfaceVariant, in: RoundedRectangle(cornerRadius: 7, style: .continuous))
                if index < steps.count - 1 {
                    Text("↓")
                        .font(.shalev(14, weight: .semibold))
                        .foregroundStyle(Color.shalevPrimary)
                }
            }
        }
        .frame(maxWidth: .infinity)
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
                        .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText).fixedSize(horizontal: false, vertical: true)
                }
            }
            .padding(18)
        }
        .background(Color.shalevBackground)
    }

    private func info(_ key: String, _ value: String) -> some View {
        HStack { Text(key).foregroundStyle(Color.shalevSecondaryText); Spacer(); Text(value).fontWeight(.semibold).foregroundStyle(Color.shalevText) }
            .font(.shalev(13))
    }
}

private func labeledField(_ title: String, text: Binding<String>, placeholder: String, keyboard: UIKeyboardType = .default) -> some View {
    VStack(alignment: .leading, spacing: 6) {
        Text(title).font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
        TextField(placeholder, text: text).keyboardType(keyboard).textFieldStyle(SettingsInputStyle())
            .submitLabel(.done)
            .textContentType(keyboard == .phonePad ? .telephoneNumber : nil)
    }
}

private struct SettingsInputStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration.font(.shalev(14)).foregroundStyle(Color.shalevText)
            .padding(11).background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 11).stroke(Color.shalevOutlineVariant, lineWidth: 1))
    }
}

private struct BackupInputStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration.font(.shalev(15)).padding(12)
            .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
    }
}
