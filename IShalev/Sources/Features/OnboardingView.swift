import SwiftUI
import UIKit

/// Asistente de primera ejecución, con la misma idea que el de Android: bienvenida, módulos del negocio,
/// datos y resumen final. Desde Ajustes → «Completar configuración» empieza directamente en los datos.
@MainActor
struct OnboardingView: View {
    @ObservedObject var store: ShalevStore
    /// `true` cuando se abre desde Ajustes para completar la configuración: se salta la bienvenida.
    var startAtData = false
    let onComplete: () -> Void

    @AppStorage("shalev.module.sales") private var salesEnabled = true
    @AppStorage("shalev.module.inventory") private var inventoryEnabled = true
    @AppStorage("shalev.module.services") private var servicesEnabled = true

    @State private var step = 0
    @State private var businessName = ""
    @State private var ownerFirstName = ""
    @State private var ownerLastName = ""
    @State private var identification = ""
    @State private var phone = ""
    @State private var transferPhone = ""
    @State private var transferCard = ""
    @State private var errorMessage: String?

    init(store: ShalevStore, startAtData: Bool = false, onComplete: @escaping () -> Void) {
        self.store = store
        self.startAtData = startAtData
        self.onComplete = onComplete
        _step = State(initialValue: startAtData ? 2 : 0)
    }

    private var steps: [Int] { startAtData ? [2, 3] : [0, 1, 2, 3] }
    private var position: Int { steps.firstIndex(of: step) ?? 0 }
    private var totalSteps: Int { steps.count }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(spacing: 15) {
                    switch step {
                    case 0: welcomeStep
                    case 1: modulesStep
                    case 2: dataStep
                    default: summaryStep
                    }
                    if let errorMessage {
                        Text(errorMessage)
                            .font(.system(size: 12, weight: .medium))
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
        .task { loadProfile() }
    }

    // MARK: - Cabecera con progreso

    private var header: some View {
        VStack(spacing: 8) {
            if step != 0 {
                HStack {
                    Text(title)
                        .font(.system(size: 21, weight: .semibold))
                        .foregroundStyle(Color.shalevText)
                    Spacer()
                    Text("Paso \(position + 1) de \(totalSteps)")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color.shalevSecondaryText)
                }
                ProgressView(value: Double(position + 1), total: Double(totalSteps))
                    .tint(Color.shalevPrimary)
                Text(subtitle)
                    .font(.system(size: 12))
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
        case 1: "Tu negocio"
        case 2: "Tus datos"
        default: "Todo listo"
        }
    }

    private var subtitle: String {
        switch step {
        case 1: "Elige qué harás con Shalev. Lo que no marques no aparecerá en la app."
        case 2: "Se guardan solo en este teléfono, cifrados. Puedes cambiarlos después en Ajustes → Perfil."
        default: "Revisa lo elegido y empieza a trabajar."
        }
    }

    // MARK: - Pasos

    private var welcomeStep: some View {
        VStack(spacing: 16) {
            Image(systemName: "storefront.fill")
                .font(.system(size: 40, weight: .semibold))
                .foregroundStyle(Color.shalevOnPrimary)
                .frame(width: 88, height: 88)
                .background(Color.shalevPrimary, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                .padding(.top, 30)
            Text("Bienvenido a Shalev")
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(Color.shalevText)
                .multilineTextAlignment(.center)
            Text("Punto de venta e inventario que funciona sin conexión y guarda todo en este teléfono.")
                .font(.system(size: 14))
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
            Text("Tres pasos y ya puedes empezar.")
                .font(.system(size: 12))
                .foregroundStyle(Color.shalevSecondaryText)
        }
    }

    private func bullet(_ symbol: String, _ title: String, _ detail: String) -> some View {
        HStack(alignment: .top, spacing: 11) {
            Image(systemName: symbol)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Color.shalevPrimary)
                .frame(width: 34, height: 34)
                .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
                Text(detail).font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
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
                        .font(.system(size: 14, weight: .medium)).foregroundStyle(Color.shalevText)
                }
                Toggle(isOn: $inventoryEnabled) {
                    Label("Inventario", systemImage: "archivebox.fill")
                        .font(.system(size: 14, weight: .medium)).foregroundStyle(Color.shalevText)
                }
                Toggle(isOn: $servicesEnabled) {
                    Label("Servicios", systemImage: "storefront.fill")
                        .font(.system(size: 14, weight: .medium)).foregroundStyle(Color.shalevText)
                }
                Text("Vender productos necesita el inventario: si activas Ventas, Inventario se activa también. Debe quedar al menos un módulo.")
                    .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                Text("Los perfiles por empleado se administran en Ajustes → Permisos y son locales a este iPhone; aún no se sincronizan con Android.")
                    .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
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
                    field("Nombre del negocio", text: $businessName, placeholder: "Mi negocio", capitalization: .words)
                    field("Nombre del titular", text: $ownerFirstName, placeholder: "Nombre", capitalization: .words)
                    field("Apellidos", text: $ownerLastName, placeholder: "Apellidos", capitalization: .words)
                    field("Carné de identidad (opcional)", text: $identification, placeholder: "CI", capitalization: .characters)
                    field("Teléfono de contacto (opcional)", text: $phone, placeholder: "+53 5XXX XXXX", keyboard: .phonePad)
                }
            }
            ShalevCard {
                VStack(alignment: .leading, spacing: 11) {
                    ShalevSectionHeader(title: "Transfermóvil (opcional)")
                    Text("Estos datos solo se usan para crear el QR de cobro; Shalev no verifica ni confirma pagos.")
                        .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                    field("Teléfono asociado", text: $transferPhone, placeholder: "5XXXXXXX", keyboard: .phonePad)
                    field("Tarjeta", text: $transferCard, placeholder: "16 dígitos", keyboard: .numberPad)
                }
            }
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
                    summaryRow("Titular", "\(ownerFirstName) \(ownerLastName)".trimmingCharacters(in: .whitespaces).isEmpty
                               ? "Sin indicar" : "\(ownerFirstName) \(ownerLastName)")
                    summaryRow("Cobro por transferencia", transferCard.isEmpty ? "Pendiente de configurar" : "Configurado")
                }
            }
            ShalevCard {
                VStack(alignment: .leading, spacing: 8) {
                    Label("Siguiente paso: abre el turno", systemImage: "lock.open.fill")
                        .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                    Text("En Inicio toca el interruptor del turno o «Nueva venta» para abrir la caja con su fondo. Sin turno abierto no se vende.")
                        .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                }
            }
        }
    }

    private func summaryRow(_ title: String, _ value: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text(title).font(.system(size: 13)).foregroundStyle(Color.shalevSecondaryText)
            Spacer(minLength: 6)
            Text(value)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.shalevText)
                .multilineTextAlignment(.trailing)
        }
    }

    // MARK: - Barra de acciones

    private var actionBar: some View {
        VStack(spacing: 0) {
            Divider()
            HStack(spacing: 10) {
                if position > 0 {
                    Button { goBack() } label: {
                        Label("Atrás", systemImage: "chevron.left")
                    }
                    .buttonStyle(ShalevTonalButtonStyle())
                    .frame(maxWidth: 130)
                }
                Button { advance() } label: {
                    Label(step == 3 ? "Guardar y comenzar" : "Continuar",
                          systemImage: step == 3 ? "checkmark.circle.fill" : "arrow.right")
                }
                .buttonStyle(ShalevPrimaryButtonStyle())
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 12)
        }
        .background(.ultraThinMaterial)
    }

    private func advance() {
        errorMessage = nil
        switch step {
        case 0:
            step = 1
        case 1:
            if !salesEnabled && !inventoryEnabled && !servicesEnabled {
                errorMessage = "Activa al menos un módulo para continuar."
                return
            }
            step = 2
        case 2:
            step = 3
        default:
            finish()
        }
    }

    private func goBack() {
        errorMessage = nil
        step = max(0, step - 1)
    }

    // MARK: - Datos

    private func field(_ title: String, text: Binding<String>, placeholder: String,
                       keyboard: UIKeyboardType = .default,
                       capitalization: TextInputAutocapitalization = .never) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
            TextField(placeholder, text: text)
                .keyboardType(keyboard)
                .textInputAutocapitalization(capitalization)
                .autocorrectionDisabled()
                .font(.system(size: 14))
                .padding(11)
                .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
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
    }

    private func finish() {
        guard salesEnabled || inventoryEnabled || servicesEnabled else {
            errorMessage = "Activa al menos un módulo para continuar."
            return
        }
        if salesEnabled { inventoryEnabled = true }
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
            try store.saveProfile(profile)
            onComplete()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
