import SwiftUI
import UIKit
import LocalAuthentication

/// Asistente de primera ejecución: rol, módulos/empleados, datos, alertas, licencia y resumen.
/// Desde Ajustes → «Completar configuración» empieza directamente en los datos.
@MainActor
struct OnboardingView: View {
    @ObservedObject var store: ShalevStore
    @ObservedObject var licenseManager: GLLicenseManager
    /// `true` cuando se abre desde Ajustes para completar la configuración: se salta la bienvenida.
    var startAtData = false
    let onComplete: () -> Void
    var onLinkSecondary: () -> Void = { }
    var onAccessLockEnabled: () -> Void = { }

    @AppStorage("shalev.module.sales") private var salesEnabled = true
    @AppStorage("shalev.module.inventory") private var inventoryEnabled = true
    @AppStorage("shalev.module.services") private var servicesEnabled = true
    @AppStorage("shalev.lock.enabled") private var appLockEnabled = false

    @State private var canUseDeviceAuthentication = false
    @State private var step = 0
    @State private var installingAsSecondary = false
    @State private var businessName = ""
    @State private var ownerFirstName = ""
    @State private var ownerLastName = ""
    @State private var identification = ""
    @State private var phone = ""
    @State private var transferPhone = ""
    @State private var transferCard = ""
    @State private var productLow = "5"
    @State private var productCritical = "1"
    @State private var ingredientLow = "5"
    @State private var ingredientCritical = "1"
    @State private var plannedEmployees = 0
    @State private var errorMessage: String?
    private enum DataFocus: Hashable { case business, firstName, lastName, ci }
    @FocusState private var focusedField: DataFocus?

    init(store: ShalevStore, licenseManager: GLLicenseManager, startAtData: Bool = false,
         onComplete: @escaping () -> Void, onLinkSecondary: @escaping () -> Void = { },
         onAccessLockEnabled: @escaping () -> Void = { }) {
        self.store = store
        self.licenseManager = licenseManager
        self.startAtData = startAtData
        self.onComplete = onComplete
        self.onLinkSecondary = onLinkSecondary
        self.onAccessLockEnabled = onAccessLockEnabled
        _step = State(initialValue: startAtData ? 3 : 0)
    }

    private var steps: [Int] { startAtData ? [3, 4, 5, 6] : [0, 1, 2, 3, 4, 5, 6] }
    private var stepIndex: Int { steps.firstIndex(of: step) ?? 0 }
    private var position: Int { max(0, stepIndex - (startAtData ? 0 : 1)) }
    private var totalSteps: Int { max(1, steps.count - (startAtData ? 0 : 1)) }
    private var canGoBack: Bool { stepIndex > 0 }
    private var maximumPlannedEmployees: Int {
        switch licenseManager.state {
        case .trial: 5
        case .active(_, _, _, let secondaries), .perpetual(_, let secondaries): min(10, max(0, secondaries))
        default: 0
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(spacing: 15) {
                    switch step {
                    case 0: welcomeStep
                    case 1: appTypeStep
                    case 2: modulesStep
                    case 3: dataStep
                    case 4: alertsStep
                    case 5: trialStep
                    default: summaryStep
                    }
                    if let errorMessage {
                        Text(errorMessage)
                            .font(.shalev(12, weight: .medium))
                            .foregroundStyle(Color.shalevError)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.horizontal, 18)
                .padding(.bottom, 24)
                .frame(maxWidth: 680)
                .frame(maxWidth: .infinity)
            }
            actionBar
        }
        .background(Color.shalevBackground.ignoresSafeArea())
        .task {
            loadProfile()
            let context = LAContext()
            var error: NSError?
            canUseDeviceAuthentication = context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error)
        }
    }

    // MARK: - Cabecera con progreso

    private var header: some View {
        VStack(spacing: 8) {
            if step != 0 {
                HStack {
                    Text(title)
                        .font(.shalev(21, weight: .semibold))
                        .foregroundStyle(Color.shalevText)
                    Spacer()
                    Text("Paso \(position + 1) de \(totalSteps)")
                        .font(.shalev(12, weight: .medium))
                        .foregroundStyle(Color.shalevSecondaryText)
                }
                ProgressView(value: Double(position + 1), total: Double(totalSteps))
                    .tint(Color.shalevPrimary)
                    .accessibilityLabel("Progreso del asistente")
                    .accessibilityValue("Paso \(position + 1) de \(totalSteps)")
                Text(subtitle)
                    .font(.shalev(12))
                    .foregroundStyle(Color.shalevSecondaryText)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 16)
        .padding(.bottom, 12)
    }

    private var title: String {
        switch step {
        case 1: "Tipo de app"
        case 2: "Tu negocio"
        case 3: "Tus datos"
        case 4: "Avisos de inventario"
        case 5: "Periodo de prueba"
        default: "Todo listo"
        }
    }

    private var subtitle: String {
        switch step {
        case 1: "Indica si este teléfono será el principal o el de un empleado."
        case 2: "Elige qué harás con Shalev. Lo que no marques no aparecerá en la app."
        case 3: "Se guardan solo en este teléfono, cifrados. Puedes cambiarlos después en Ajustes → Perfil."
        case 4: "Elige cuándo quieres recibir avisos de existencias bajas o críticas. Puedes ajustarlos luego en Ajustes."
        case 5: "Conoce qué incluye tu prueba o licencia y qué pasa al terminar."
        default: "Revisa lo elegido y empieza a trabajar."
        }
    }

    // MARK: - Pasos

    private var welcomeStep: some View {
        VStack(spacing: 16) {
            Image(systemName: "storefront.fill")
                .font(.shalev(40, weight: .semibold))
                .foregroundStyle(Color.shalevOnPrimary)
                .frame(width: 88, height: 88)
                .background(Color.shalevPrimary, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                .padding(.top, 30)
            Text("Bienvenido a Shalev")
                .font(.shalev(26, weight: .bold))
                .foregroundStyle(Color.shalevText)
                .multilineTextAlignment(.center)
            Text("Punto de venta e inventario que funciona sin conexión y guarda todo en este teléfono.")
                .font(.shalev(14))
                .foregroundStyle(Color.shalevSecondaryText)
                .multilineTextAlignment(.center)
            ShalevCard {
                VStack(alignment: .leading, spacing: 13) {
                    bullet("cart.fill", "Vende y controla el turno", "Efectivo o transferencia con QR de Transfermóvil.")
                    bullet("shippingbox.fill", "Ordena tu inventario", "Productos, insumos y elaborados con recetas.")
                    bullet("chart.bar.fill", "Mira cómo va el negocio", "Ventas, ganancia y alertas de existencias.")
                    bullet("lock.shield.fill", "Sin cuenta ni internet", "Los datos se cifran y se quedan en el teléfono.")
                }
            }
            Text("Completa la configuración para empezar a trabajar.")
                .font(.shalev(12))
                .foregroundStyle(Color.shalevSecondaryText)
            Button(action: onLinkSecondary) {
                Label("Este teléfono es de un empleado · Vincular con la principal", systemImage: "arrow.left.arrow.right")
                    .font(.shalev(13, weight: .semibold))
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(ShalevTonalButtonStyle())
        }
    }

    private var appTypeStep: some View {
        VStack(spacing: 12) {
            Button { installingAsSecondary = false } label: {
                roleOption(title: "Principal (dueño)",
                           detail: "Este teléfono administra el negocio, el inventario y las apps de los empleados.",
                           selected: !installingAsSecondary)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(!installingAsSecondary ? .isSelected : [])

            Button { installingAsSecondary = true } label: {
                roleOption(title: "Secundaria (empleado)",
                           detail: "Este teléfono se vincula con la app principal; el dueño comparte el catálogo y los permisos.",
                           selected: installingAsSecondary)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(installingAsSecondary ? .isSelected : [])

            if installingAsSecondary {
                Text("Al continuar abrirás Apps vinculadas para escanear el código QR del dueño. No se borrarán datos hasta que confirmes la vinculación.")
                    .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                Text("Solo puede haber una app principal por negocio. Cada empleado usa su propia secundaria.")
                    .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func roleOption(title: String, detail: String, selected: Bool) -> some View {
        ShalevCard(padding: 15) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.shalev(20, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).font(.shalev(15, weight: .semibold)).foregroundStyle(Color.shalevText)
                    Text(detail).font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(selected ? Color.shalevPrimary : Color.clear, lineWidth: 2)
        }
    }

    private func bullet(_ symbol: String, _ title: String, _ detail: String) -> some View {
        HStack(alignment: .top, spacing: 11) {
            Image(systemName: symbol)
                .font(.shalev(15, weight: .semibold))
                .foregroundStyle(Color.shalevPrimary)
                .frame(width: 34, height: 34)
                .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevText)
                Text(detail).font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
            }
            Spacer(minLength: 0)
        }
    }

    private var modulesStep: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 12) {
                ShalevSectionHeader(title: "Módulos del negocio")
                Toggle(isOn: $salesEnabled) {
                    Label("Ventas de productos", systemImage: "cart.fill")
                        .font(.shalev(14, weight: .medium)).foregroundStyle(Color.shalevText)
                }
                Toggle(isOn: $inventoryEnabled) {
                    Label("Inventario", systemImage: "archivebox.fill")
                        .font(.shalev(14, weight: .medium)).foregroundStyle(Color.shalevText)
                }
                Toggle(isOn: $servicesEnabled) {
                    Label("Servicios", systemImage: "storefront.fill")
                        .font(.shalev(14, weight: .medium)).foregroundStyle(Color.shalevText)
                }
                Divider()
                Stepper(value: $plannedEmployees, in: 0...maximumPlannedEmployees) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Apps secundarias previstas").font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevText)
                        Text(plannedEmployees == 0 ? "Sin empleados" : "\(plannedEmployees) empleado\(plannedEmployees == 1 ? "" : "s")")
                            .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                    }
                }
                Text("Vender productos necesita el inventario: si activas Ventas, Inventario se activa también. Debe quedar al menos un módulo.")
                    .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                Text("Cada empleado usa una app secundaria. Puedes elegir hasta el límite de tu prueba o licencia (ahora: \(maximumPlannedEmployees)); para aumentarlo, consulta Ajustes → Licencia.")
                    .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                Text("Los perfiles por empleado se administran en Ajustes → Permisos y son locales a este iPhone; aún no se sincronizan con Android.")
                    .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
            }
            .tint(Color.shalevPrimary)
        }
        .onChange(of: salesEnabled) { _, enabled in
            if enabled && !inventoryEnabled { inventoryEnabled = true }
        }
    }

    private var dataStep: some View {
        VStack(spacing: 14) {
            ShalevCard {
                VStack(alignment: .leading, spacing: 12) {
                    ShalevSectionHeader(title: "Tu negocio")
                    field("Nombre del negocio", text: $businessName, placeholder: "Mi negocio", capitalization: .words,
                          focus: .business, submit: .next) { focusedField = .firstName }
                    field("Nombre del titular", text: $ownerFirstName, placeholder: "Nombre", capitalization: .words,
                          focus: .firstName, submit: .next) { focusedField = .lastName }
                    field("Apellidos", text: $ownerLastName, placeholder: "Apellidos", capitalization: .words,
                          focus: .lastName, submit: .next) { focusedField = .ci }
                    field("Carné de identidad (opcional)", text: $identification, placeholder: "CI", capitalization: .characters,
                          focus: .ci, submit: .done) { focusedField = nil }
                    field("Teléfono de contacto (opcional)", text: $phone, placeholder: "+53 5XXX XXXX", keyboard: .phonePad)
                }
            }
            ShalevCard {
                VStack(alignment: .leading, spacing: 11) {
                    ShalevSectionHeader(title: "Transfermóvil (opcional)")
                    Text("Estos datos solo se usan para crear el QR de cobro; Shalev no verifica ni confirma pagos.")
                        .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
                    field("Teléfono asociado", text: $transferPhone, placeholder: "5XXXXXXX", keyboard: .phonePad)
                    field("Tarjeta", text: $transferCard, placeholder: "16 dígitos", keyboard: .numberPad)
                }
            }
        }
    }

    private var alertsStep: some View {
        VStack(spacing: 14) {
            ShalevCard {
                VStack(alignment: .leading, spacing: 12) {
                    ShalevSectionHeader(title: "Productos")
                    field("Aviso de nivel bajo (unidades)", text: $productLow, placeholder: "5", keyboard: .numberPad)
                    field("Aviso de nivel crítico (unidades)", text: $productCritical, placeholder: "1", keyboard: .numberPad)
                }
            }
            ShalevCard {
                VStack(alignment: .leading, spacing: 12) {
                    ShalevSectionHeader(title: "Insumos")
                    field("Aviso de nivel bajo (cantidad)", text: $ingredientLow, placeholder: "5", keyboard: .decimalPad)
                    field("Aviso de nivel crítico (cantidad)", text: $ingredientCritical, placeholder: "1", keyboard: .decimalPad)
                }
            }
            ShalevCard {
                Toggle(isOn: Binding(get: { appLockEnabled }, set: { requestAccessLockChange($0) })) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Acceso con clave (opcional)").font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevText)
                        Text(canUseDeviceAuthentication
                             ? "Solicita Face ID, Touch ID o el código del iPhone al abrir Shalev y al volver después de 10 minutos."
                             : "Configura primero un código de bloqueo en Ajustes del iPhone.")
                            .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                    }
                }
                .tint(Color.shalevPrimary)
                .disabled(!canUseDeviceAuthentication)
            }
            Text("Valores recomendados: productos 5 y 1; insumos 5 y 1. El nivel crítico debe ser menor o igual que el nivel bajo.")
                .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var trialStep: some View {
        VStack(spacing: 14) {
            ShalevCard {
                VStack(alignment: .leading, spacing: 11) {
                    ShalevSectionHeader(title: trialStepTitle)
                    Text(trialStepMessage)
                        .font(.shalev(13)).foregroundStyle(Color.shalevSecondaryText)
                    bullet("checkmark.seal.fill", "Tus datos se conservan", "Si termina el periodo, la app puede quedar bloqueada hasta activar una licencia; los datos no se borran.")
                    bullet("key.fill", "Puedes pedir una licencia cuando quieras", "Ve a Ajustes → Licencia para consultar opciones o continuar el proceso.")
                }
            }
            ShalevCard {
                VStack(alignment: .leading, spacing: 8) {
                    ShalevSectionHeader(title: "Planes de referencia")
                    ForEach(GLLicenseType.allCases) { option in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(option.title).font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevText)
                                Text("Cada app secundaria: +\(Cup(cents: option.secondaryPriceCUP * 100).formatted)")
                                    .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                            }
                            Spacer()
                            Text(Cup(cents: option.basePriceCUP * 100).formatted)
                                .font(.shalev(12, weight: .semibold)).foregroundStyle(Color.shalevText)
                        }
                        if option != .perpetual { Divider() }
                    }
                }
            }
        }
    }

    private var trialStepTitle: String {
        switch licenseManager.state {
        case .trial(let days): days == 1 ? "Te queda 1 día de prueba gratis" : "Tienes \(days) días de prueba gratis"
        case .active, .perpetual: "Tu licencia está activa"
        default: "Periodo de prueba y licencia"
        }
    }

    private var trialStepMessage: String {
        switch licenseManager.state {
        case .trial: "Durante la prueba puedes usar todas las funciones."
        case .active, .perpetual: licenseManager.state.title
        default: "Consulta el estado y las opciones de licencia desde Ajustes → Licencia."
        }
    }

    private var summaryStep: some View {
        VStack(spacing: 14) {
            ShalevCard {
                VStack(alignment: .leading, spacing: 11) {
                    ShalevSectionHeader(title: "Resumen")
                    summaryRow("Negocio", businessName.isEmpty ? "Mi negocio" : businessName)
                    let modules = [salesEnabled ? "Ventas" : nil, inventoryEnabled ? "Inventario" : nil, servicesEnabled ? "Servicios" : nil]
                        .compactMap { $0 }
                    summaryRow("Módulos", modules.isEmpty ? "Ninguno" : modules.joined(separator: ", "))
                    summaryRow("Apps secundarias previstas", "\(plannedEmployees)")
                    summaryRow("Titular", "\(ownerFirstName) \(ownerLastName)".trimmingCharacters(in: .whitespaces).isEmpty
                               ? "Sin indicar" : "\(ownerFirstName) \(ownerLastName)")
                    summaryRow("Cobro por transferencia", transferCard.isEmpty ? "Pendiente de configurar" : "Configurado")
                    summaryRow("Avisos de inventario", "Productos \(productCritical) / \(productLow) · Insumos \(ingredientCritical) / \(ingredientLow)")
                }
            }
            ShalevCard {
                VStack(alignment: .leading, spacing: 8) {
                    Label("Siguiente paso: abre el turno", systemImage: "lock.open.fill")
                        .font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                    Text("En Inicio toca el interruptor del turno o «Nueva venta» para abrir la caja con su fondo. Sin turno abierto no se vende.")
                        .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
                }
            }
        }
    }

    private func summaryRow(_ title: String, _ value: String) -> some View {
        ShalevFilaAdaptable(espaciadoHorizontal: 10, espaciadoVertical: 3) {
            Text(title).font(.shalev(13)).foregroundStyle(Color.shalevSecondaryText)
        } valor: {
            Text(value)
                .font(.shalev(13, weight: .semibold))
                .foregroundStyle(Color.shalevText)
        }
    }

    // MARK: - Barra de acciones

    private var actionBar: some View {
        VStack(spacing: 0) {
            Divider()
            HStack(spacing: 10) {
                if canGoBack {
                    Button { goBack() } label: {
                        Label("Atrás", systemImage: "chevron.left")
                    }
                    .buttonStyle(ShalevTonalButtonStyle())
                    .frame(maxWidth: 130)
                }
                Button { advance() } label: {
                    Label(step == 6 ? "Guardar y comenzar" : installingAsSecondary && step == 1 ? "Vincular ahora" : "Continuar",
                          systemImage: step == 6 ? "checkmark.circle.fill" : "arrow.right")
                }
                .buttonStyle(ShalevPrimaryButtonStyle())
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
        }
        .background(ShalevBarBackground())
    }

    private func advance() {
        errorMessage = nil
        switch step {
        case 0:
            step = 1
        case 1:
            if installingAsSecondary { onLinkSecondary() }
            else { step = 2 }
        case 2:
            if !salesEnabled && !inventoryEnabled && !servicesEnabled {
                errorMessage = "Activa al menos un módulo para continuar."
                return
            }
            step = 3
        case 3:
            step = 4
        case 4:
            guard parsedInventoryAlertDefaults() != nil else {
                errorMessage = "Escribe niveles válidos; el nivel crítico no puede superar el nivel bajo."
                return
            }
            step = 5
        case 5:
            step = 6
        default:
            finish()
        }
    }

    private func goBack() {
        errorMessage = nil
        guard canGoBack else { return }
        step = steps[stepIndex - 1]
    }

    // MARK: - Datos

    private func field(_ title: String, text: Binding<String>, placeholder: String,
                       keyboard: UIKeyboardType = .default,
                       capitalization: TextInputAutocapitalization = .never,
                       focus: DataFocus? = nil, submit: SubmitLabel = .done,
                       onSubmit: (() -> Void)? = nil) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.shalev(12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
            if let focus {
                TextField(placeholder, text: text)
                    .keyboardType(keyboard)
                    .textInputAutocapitalization(capitalization)
                    .autocorrectionDisabled()
                    .focused($focusedField, equals: focus)
                    .submitLabel(submit)
                    .onSubmit { onSubmit?() }
                    .textContentType(keyboard == .phonePad ? .telephoneNumber : nil)
                    .font(.shalev(14))
                    .padding(11)
                    .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
            } else {
                TextField(placeholder, text: text)
                    .keyboardType(keyboard)
                    .textInputAutocapitalization(capitalization)
                    .autocorrectionDisabled()
                    .submitLabel(submit)
                    .textContentType(keyboard == .phonePad ? .telephoneNumber : nil)
                    .font(.shalev(14))
                    .padding(11)
                    .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
            }
        }
    }

    private func loadProfile() {
        let profile = store.data.settings.profile
        businessName = profile.businessName
        ownerFirstName = profile.ownerFirstName ?? ""
        ownerLastName = profile.ownerLastName ?? ""
        if ownerFirstName.isEmpty && ownerLastName.isEmpty {
            let names = profile.ownerName.split(whereSeparator: \.isWhitespace)
            ownerFirstName = names.first.map(String.init) ?? ""
            ownerLastName = names.dropFirst().joined(separator: " ")
        }
        identification = profile.identification
        phone = profile.licensePhone ?? ""
        transferPhone = profile.paymentPhone?.number ?? profile.transferPhone
        transferCard = profile.paymentCard?.number ?? profile.transferCard
        let defaults = store.data.settings.effectiveInventoryAlertDefaults
        productLow = String(defaults.productLowMilli / 1_000)
        productCritical = String(defaults.productCriticalMilli / 1_000)
        ingredientLow = FixedNumber.formatMilli(defaults.ingredientLowMilli)
        ingredientCritical = FixedNumber.formatMilli(defaults.ingredientCriticalMilli)
        plannedEmployees = min(maximumPlannedEmployees, store.data.settings.effectivePlannedEmployees)
    }

    private func requestAccessLockChange(_ enabled: Bool) {
        guard canUseDeviceAuthentication else {
            errorMessage = "Configura un bloqueo de pantalla en Ajustes del iPhone para usar esta opción."
            return
        }
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            errorMessage = "Configura un bloqueo de pantalla en Ajustes del iPhone para usar esta opción."
            return
        }
        context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Confirma tu identidad para configurar el acceso") { success, _ in
            Task { @MainActor in
                if success {
                    appLockEnabled = enabled
                    if enabled { onAccessLockEnabled() }
                    errorMessage = nil
                } else {
                    errorMessage = "No se pudo confirmar. Inténtalo de nuevo."
                }
            }
        }
    }

    private func parsedInventoryAlertDefaults() -> InventoryAlertDefaults? {
        guard let productLowValue = Int64(productLow), (0...999_999).contains(productLowValue),
              let productCriticalValue = Int64(productCritical), (0...999_999).contains(productCriticalValue),
              let ingredientLowValue = FixedNumber.parse(ingredientLow, multiplier: 1_000),
              let ingredientCriticalValue = FixedNumber.parse(ingredientCritical, multiplier: 1_000),
              productCriticalValue <= productLowValue,
              ingredientCriticalValue <= ingredientLowValue else { return nil }
        return InventoryAlertDefaults(productLowMilli: productLowValue * 1_000,
            productCriticalMilli: productCriticalValue * 1_000,
            ingredientLowMilli: ingredientLowValue, ingredientCriticalMilli: ingredientCriticalValue)
    }

    private func finish() {
        guard salesEnabled || inventoryEnabled || servicesEnabled else {
            errorMessage = "Activa al menos un módulo para continuar."
            return
        }
        if salesEnabled { inventoryEnabled = true }
        guard let alertDefaults = parsedInventoryAlertDefaults() else {
            errorMessage = "Revisa los niveles de inventario antes de guardar."
            step = 4
            return
        }
        var profile = store.data.settings.profile
        profile.businessName = businessName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Mi negocio" : businessName.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.ownerFirstName = ownerFirstName.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.ownerLastName = ownerLastName.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.ownerName = "\(profile.ownerFirstName ?? "") \(profile.ownerLastName ?? "")".trimmingCharacters(in: .whitespacesAndNewlines)
        profile.identification = identification.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        profile.licensePhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedTransferPhone = transferPhone.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedTransferCard = transferCard.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedTransferPhone.isEmpty {
            let entry = TransferPhone(number: trimmedTransferPhone)
            profile.transferPhones = [entry]
            profile.paymentPhoneID = entry.id
        } else {
            profile.transferPhones = []
            profile.paymentPhoneID = nil
        }
        if !trimmedTransferCard.isEmpty {
            let entry = TransferCard(number: trimmedTransferCard)
            profile.transferCards = [entry]
            profile.paymentCardID = entry.id
        } else {
            profile.transferCards = []
            profile.paymentCardID = nil
        }
        do {
            try store.saveProfile(profile, inventoryAlertDefaults: alertDefaults, plannedEmployees: plannedEmployees)
            onComplete()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
