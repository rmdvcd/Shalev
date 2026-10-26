import SwiftUI
import UIKit

/// Panel de Licencia en tres pasos, como en Android:
/// 1. Revisa tus datos · 2. Pide la licencia · 3. Activa la licencia.
/// Siguiendo `LicenciaScreen.kt`, los precios y el estado quedan fijos arriba y solo cambia el paso.
@MainActor
struct LicenseView: View {
    @ObservedObject var store: ShalevStore
    @ObservedObject var license: GLLicenseManager
    let blocking: Bool
    @Environment(\.dismiss) private var dismiss

    private enum Step: Int, CaseIterable {
        case data, request, activate

        var title: String {
            switch self {
            case .data: "Revisa tus datos"
            case .request: "Pide la licencia"
            case .activate: "Activa la licencia"
            }
        }
    }

    @State private var step: Step = .data
    @State private var firstName = ""
    @State private var lastName = ""
    @State private var identification = ""
    @State private var phone = ""
    @State private var type: GLLicenseType = .monthly
    @State private var secondaries = 0
    @State private var channel: GLRequestChannel = .whatsapp
    @State private var recoveryID = ""
    @State private var activationText = ""
    @State private var editingData = false
    @State private var showErrors = false
    @State private var requestMessage: String?
    @State private var alertMessage: String?
    @State private var didLoadProfile = false
    @State private var isBusy = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if blocking { blockingCard }
                    statusCard
                    if !license.canValidate { validationCard }
                    if showsRenewal { renewalButton }
                    pricesCard
                    stepper
                    stepContent
                }
                .padding(18)
                .padding(.bottom, 24)
                .frame(maxWidth: 680)
                .frame(maxWidth: .infinity)
            }
            .background(Color.shalevBackground)
            .navigationTitle("Licencia")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    if !blocking {
                        Button("Cerrar") { dismiss() }.foregroundStyle(Color.shalevPrimary)
                    }
                }
            }
            .alert("Licencia", isPresented: Binding(get: { alertMessage != nil }, set: { if !$0 { alertMessage = nil } })) {
                Button("Entendido", role: .cancel) { alertMessage = nil }
            } message: { Text(alertMessage ?? "") }
            .task {
                await license.refresh()
                loadProfileIfNeeded()
            }
        }
    }

    // MARK: - Bloqueo y estado

    private var blockingCard: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 8) {
                Label("Shalev está bloqueado hasta que actives una licencia válida.", systemImage: "lock.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.shalevText)
                Text("Tus datos siguen guardados.")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.shalevSecondaryText)
                Link(destination: URL(string: "https://wa.me/5351815604")!) {
                    Label("Contactar soporte", systemImage: "message.fill")
                }
                .buttonStyle(ShalevTonalButtonStyle())
            }
        }
    }

    private var statusCard: some View {
        Group {
            switch license.state {
            case .trial(let days):
                statusLayout(symbol: "hourglass", title: "Periodo de prueba",
                             detail: days == 1 ? "Queda 1 día de prueba." : "Quedan \(days) días de prueba.")
            case .trialExpired:
                statusLayout(symbol: "xmark.seal.fill", title: "Prueba finalizada",
                             detail: "Solicita una licencia para seguir usando Shalev.")
            case .active(let activeType, let expires, _, let slots):
                statusLayout(symbol: "checkmark.seal.fill", title: "Licencia \(activeType.title)",
                             detail: "Vence el \(expires.shalevDate).", extra: "Apps secundarias: \(slots)")
            case .perpetual(_, let slots):
                statusLayout(symbol: "checkmark.seal.fill", title: "Licencia Perpetua",
                             detail: "Sin vencimiento.", extra: "Apps secundarias: \(slots)")
            case .expired(let expiredType):
                statusLayout(symbol: "xmark.seal.fill", title: "Licencia \(expiredType.title) vencida",
                             detail: "Solicita una renovación para seguir usando Shalev.")
            case .revoked:
                statusLayout(symbol: "person.fill.xmark", title: "Licencia transferida",
                             detail: "Se recuperó en otro teléfono. Contacta al desarrollador si no fuiste tú.")
            case .clockTampered:
                statusLayout(symbol: "clock.badge.exclamationmark", title: "Fecha del sistema incorrecta",
                             detail: "La fecha del teléfono es anterior a la última registrada. Corrígela en los ajustes del sistema.")
            case .loading, .unavailable:
                statusLayout(symbol: "questionmark.circle.fill", title: license.state.title,
                             detail: "Comprueba tu conexión o vuelve a abrir la pantalla.")
            }
        }
    }

    private func statusLayout(symbol: String, title: String, detail: String, extra: String? = nil) -> some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: symbol)
                        .font(.system(size: 23, weight: .medium))
                        .foregroundStyle(license.state.unlocked ? Color.shalevPrimary : Color.shalevSecondary)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(title)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Color.shalevText)
                            .accessibilityAddTraits(.isHeader)
                        Text(detail)
                            .font(.system(size: 13))
                            .foregroundStyle(Color.shalevSecondaryText)
                    }
                    Spacer(minLength: 0)
                }
                if let extra {
                    Text(extra)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color.shalevText)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var validationCard: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 6) {
                Label("Esta versión no puede validar licencias", systemImage: "exclamationmark.shield.fill")
                    .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevError)
                Text("Contacta al desarrollador para obtener una versión actualizada. Las claves públicas fijadas en la app no se pueden cambiar desde Ajustes.")
                    .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
            }
        }
    }

    private var showsRenewal: Bool {
        license.installedType != nil && !license.state.isPerpetual && step != .request
    }

    private var renewalButton: some View {
        VStack(spacing: 6) {
            Button {
                if let installedType = license.installedType {
                    type = installedType
                    secondaries = min(10, max(0, license.installedSecondaries ?? 0))
                }
                step = .request
                requestMessage = nil
            } label: {
                Label("Renovar igual", systemImage: "arrow.clockwise")
            }
            .buttonStyle(ShalevTonalButtonStyle())

            if let installedType = license.installedType {
                let installedSlots = min(10, max(0, license.installedSecondaries ?? 0))
                Text("\(installedType.title) · \(slotsLabel(installedSlots)) · \(Cup(cents: installedType.totalPriceCents(secondaries: installedSlots)).formatted)")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.shalevSecondaryText)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Precios

    private var pricesCard: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 10) {
                ShalevSectionHeader(title: "Precios de la licencia")
                Text("Cada licencia cubre tu app principal. Cada app secundaria (empleado, hasta 10) suma un importe fijo.")
                    .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                VStack(spacing: 0) {
                    ForEach(GLLicenseType.allCases) { option in
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(option.title)
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundStyle(Color.shalevText)
                                Text("Cada app secundaria: +\(Cup(cents: option.secondaryPriceCUP * 100).formatted)")
                                    .font(.system(size: 12))
                                    .foregroundStyle(Color.shalevSecondaryText)
                            }
                            Spacer(minLength: 10)
                            Text(Cup(cents: option.basePriceCUP * 100).formatted)
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Color.shalevText)
                        }
                        .padding(.vertical, 7)
                        if option != .perpetual {
                            Divider().overlay(Color.shalevOutlineVariant.opacity(0.5))
                        }
                    }
                }
            }
        }
    }

    // MARK: - Avance por pasos

    private var stepper: some View {
        VStack(spacing: 8) {
            HStack(spacing: 12) {
                Button {
                    if let previous = Step(rawValue: step.rawValue - 1) { step = previous; requestMessage = nil }
                } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(step == .data ? Color.shalevOutline : Color.shalevText)
                        .frame(width: 38, height: 38)
                        .background((step == .data ? Color.clear : Color.shalevSurfaceVariant), in: Circle())
                }
                .buttonStyle(.plain)
                .disabled(step == .data)
                .accessibilityLabel("Paso anterior")

                VStack(spacing: 2) {
                    Text("Paso \(step.rawValue + 1) de \(Step.allCases.count)")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Color.shalevSecondaryText)
                    Text(step.title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Color.shalevText)
                }
                .frame(maxWidth: .infinity)

                Button {
                    if let next = Step(rawValue: step.rawValue + 1) {
                        if step == .data && !validForm {
                            showErrors = true
                            editingData = true
                        } else {
                            if step == .data { editingData = false }
                            step = next
                        }
                    }
                } label: {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Color.shalevOnPrimary)
                        .frame(width: 42, height: 42)
                        .background(step == .activate ? Color.shalevOutlineVariant : Color.shalevPrimary, in: Circle())
                }
                .buttonStyle(.plain)
                .disabled(step == .activate)
                .accessibilityLabel("Paso siguiente")
            }

            GeometryReader { geometry in
                HStack(spacing: 6) {
                    Capsule()
                        .fill(Color.shalevOutlineVariant.opacity(0.6))
                        .frame(height: 4)
                        .overlay(alignment: .leading) {
                            Capsule()
                                .fill(Color.shalevPrimary)
                                .frame(width: geometry.size.width * progressFraction, height: 4)
                        }
                        .clipShape(Capsule())
                    Circle()
                        .fill(step == .activate ? Color.shalevPrimary : Color.shalevOutlineVariant.opacity(0.6))
                        .frame(width: 5, height: 5)
                }
            }
            .frame(height: 5)
        }
        .padding(.top, 2)
    }

    private var progressFraction: CGFloat {
        switch step {
        case .data: 0.34
        case .request: 0.68
        case .activate: 1
        }
    }

    @ViewBuilder
    private var stepContent: some View {
        switch step {
        case .data: dataCard
        case .request: requestCard
        case .activate: activationCard
        }
    }

    // MARK: - Paso 1 · datos

    private var dataCard: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 10) {
                ShalevSectionHeader(title: "Tus datos")
                if editingData {
                    dataForm
                } else {
                    VStack(spacing: 5) {
                        Text("\(firstName) \(lastName)".trimmingCharacters(in: .whitespaces))
                            .font(.system(size: 16))
                            .foregroundStyle(Color.shalevText)
                        Text("CI: \(identification)")
                            .font(.system(size: 13)).foregroundStyle(Color.shalevSecondaryText)
                        Text("Teléfono: \(phone)")
                            .font(.system(size: 13)).foregroundStyle(Color.shalevSecondaryText)
                        Text("De tu Perfil y de Pago electrónico.")
                            .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                        Button {
                            editingData = true
                        } label: {
                            Label("Editar", systemImage: "pencil")
                        }
                        .buttonStyle(ShalevTonalButtonStyle())
                        .padding(.top, 4)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }

    private var dataForm: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Tu Perfil está vacío: estos datos se guardarán también en él al solicitar.")
                .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
            inputField("Nombre", text: $firstName, placeholder: "Nombre",
                       isError: showErrors && !validName(firstName), errorText: "Solo letras, 2 a 80",
                       capitalization: .words)
            inputField("Apellidos", text: $lastName, placeholder: "Apellidos",
                       isError: showErrors && !validName(lastName), errorText: "Solo letras, 2 a 80",
                       capitalization: .words)
            inputField("Carné de identidad", text: $identification, placeholder: "5–20 letras o dígitos",
                       isError: showErrors && !validCI(identification), errorText: "5 a 20 letras o dígitos",
                       keyboard: .asciiCapable, capitalization: .characters)
            inputField("Teléfono", text: $phone, placeholder: "5XXXXXXX",
                       isError: showErrors && normalizePhone(phone) == nil, errorText: "Número no válido",
                       keyboard: .phonePad)
            if !profilePhones.isEmpty {
                Text("Tus teléfonos")
                    .font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                HStack(spacing: 8) {
                    ForEach(profilePhones, id: \.self) { candidate in
                        ShalevFilterChip(title: candidate, selected: phone == candidate) { phone = candidate }
                    }
                }
            }
            Button {
                editingData = false
            } label: {
                Label("Listo", systemImage: "checkmark")
            }
            .buttonStyle(ShalevTonalButtonStyle())
        }
    }

    // MARK: - Paso 2 · solicitud

    private var requestCard: some View {
        VStack(spacing: 12) {
            ShalevCard {
                VStack(alignment: .leading, spacing: 12) {
                    ShalevSectionHeader(title: requestTitle)
                    if license.state.isPerpetual {
                        Text("Tu licencia no vence. No necesitas renovarla.")
                            .font(.system(size: 13)).foregroundStyle(Color.shalevSecondaryText)
                    } else {
                        Text("Tipo de licencia")
                            .font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                        VStack(spacing: 8) {
                            ForEach(GLLicenseType.allCases) { option in
                                typeChip(option)
                            }
                        }

                        Text("Apps secundarias (empleados)")
                            .font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                        HStack(spacing: 16) {
                            quantityButton("minus", enabled: secondaries > 0, label: "Una secundaria menos") {
                                secondaries = max(0, secondaries - 1)
                                requestMessage = nil
                            }
                            Text("\(secondaries)")
                                .font(.system(size: 22, weight: .semibold))
                                .foregroundStyle(Color.shalevText)
                                .frame(minWidth: 34)
                            quantityButton("plus", enabled: secondaries < 10, label: "Una secundaria más") {
                                secondaries = min(10, secondaries + 1)
                                requestMessage = nil
                            }
                        }
                        .frame(maxWidth: .infinity)

                        Text(totalText)
                            .font(.system(size: 12))
                            .foregroundStyle(Color.shalevSecondaryText)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)

                        if license.canRecover {
                            Text("¿Ya tenías licencia en otro teléfono?")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Color.shalevText)
                            inputField("ID de la licencia anterior (opcional)", text: $recoveryID,
                                       placeholder: "UUID de la licencia",
                                       isError: !recoveryID.isBlank && normalizedRecoveryID == nil,
                                       errorText: "El ID de la licencia anterior no es válido. Cópialo del mensaje de licencia (ID: …).")
                            Text("Escribe o pega el ID de tu licencia anterior: el desarrollador la pasa a este teléfono gratis, con el mismo vencimiento y secundarias. El teléfono anterior se bloquea, borra sus datos y pide desinstalar Shalev.")
                                .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                        }

                        Text("Enviar por")
                            .font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                        HStack(spacing: 14) {
                            channelButton(.whatsapp, symbol: "message.fill", tint: Color(red: 0.14, green: 0.62, blue: 0.36))
                            channelButton(.sms, symbol: "text.bubble.fill", tint: Color.shalevPrimary)
                        }
                        .frame(maxWidth: .infinity)

                        Text(channel == .whatsapp ? "Se compartirá tu solicitud de licencia por WhatsApp o Mensajes."
                                                  : "Se compartirá tu solicitud de licencia por Mensajes o WhatsApp.")
                            .font(.system(size: 12))
                            .foregroundStyle(Color.shalevSecondaryText)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)

                        if license.installedType != nil {
                            Text("Si renuevas antes de que venza no pierdes días: la nueva licencia empieza cuando termine la actual.")
                                .font(.system(size: 12))
                                .foregroundStyle(Color.shalevSecondaryText)
                                .multilineTextAlignment(.center)
                                .frame(maxWidth: .infinity)
                        }

                        Button {
                            createRequest()
                        } label: {
                            Label(isBusy ? "Preparando…" : requestButtonTitle, systemImage: "paperplane.fill")
                        }
                        .buttonStyle(ShalevPrimaryButtonStyle())
                        .disabled(isBusy || !license.canRequest || !validForm)
                        .frame(maxWidth: .infinity)

                        if let requestMessage {
                            Text("Solicitud lista para compartir")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(Color.shalevSuccess)
                            ShareLink(item: requestMessage) {
                                Label("Compartir solicitud", systemImage: "square.and.arrow.up")
                            }
                            .buttonStyle(ShalevTonalButtonStyle())
                            .frame(maxWidth: .infinity)
                            Text("Envía el texto completo; el código SPVIR1 es la parte cifrada que GL procesa.")
                                .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                        }
                    }
                }
            }
            if !license.state.isPerpetual {
                Text("Cuando recibas la respuesta, actívala en el paso siguiente.")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.shalevSecondaryText)
                    .multilineTextAlignment(.center)
            }
        }
    }

    private func typeChip(_ option: GLLicenseType) -> some View {
        Button {
            type = option
            requestMessage = nil
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(option.title)
                        .font(.system(size: 14, weight: .semibold))
                    Text(Cup(cents: option.basePriceCUP * 100).formatted)
                        .font(.system(size: 12))
                }
                Spacer(minLength: 8)
                if type == option {
                    Image(systemName: "checkmark")
                        .font(.system(size: 13, weight: .bold))
                }
            }
            .foregroundStyle(type == option ? Color.shalevOnPrimary : Color.shalevText)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(type == option ? Color.shalevPrimary : Color.shalevSurfaceVariant,
                        in: RoundedRectangle(cornerRadius: 13, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(type == option ? .isSelected : [])
    }

    private func quantityButton(_ symbol: String, enabled: Bool, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(enabled ? Color.shalevText : Color.shalevOutline)
                .frame(width: 40, height: 40)
                .background(enabled ? Color.shalevSurfaceVariant : Color.shalevSurfaceVariant.opacity(0.45), in: Circle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }

    private func channelButton(_ option: GLRequestChannel, symbol: String, tint: Color) -> some View {
        Button {
            channel = option
        } label: {
            Image(systemName: symbol)
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(channel == option ? Color.shalevOnPrimary : tint)
                .frame(width: 46, height: 46)
                .background(channel == option ? Color.shalevPrimary : Color.shalevSurfaceVariant, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Enviar por \(option.title)")
        .accessibilityAddTraits(channel == option ? .isSelected : [])
    }

    // MARK: - Paso 3 · activación

    private var activationCard: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 11) {
                ShalevSectionHeader(title: "Activar licencia")
                Text("Pega el mensaje de activación que recibiste.")
                    .font(.system(size: 12))
                    .foregroundStyle(Color.shalevSecondaryText)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
                TextField("Mensaje de licencia", text: $activationText, axis: .vertical)
                    .lineLimit(4...8)
                    .font(.system(size: 13, design: .monospaced))
                    .padding(12)
                    .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.shalevOutlineVariant, lineWidth: 1))
                HStack(spacing: 9) {
                    Button {
                        activationText = UIPasteboard.general.string ?? ""
                    } label: {
                        Label("Pegar", systemImage: "doc.on.clipboard")
                    }
                    .buttonStyle(ShalevTonalButtonStyle())
                    Button {
                        Task { await activate() }
                    } label: {
                        Label(isBusy ? "Comprobando…" : "Activar", systemImage: "checkmark.seal.fill")
                    }
                    .buttonStyle(ShalevPrimaryButtonStyle())
                    .disabled(isBusy || activationText.isBlank || !license.canValidate)
                }
            }
        }
    }

    // MARK: - Textos

    private var requestTitle: String {
        if license.state.isPerpetual { return "Licencia perpetua" }
        return license.installedType == nil ? "Solicitar licencia" : "Renovar o cambiar licencia"
    }

    private var requestButtonTitle: String {
        license.installedType == nil ? "Solicitar" : "Solicitar renovación"
    }

    private func slotsLabel(_ count: Int) -> String {
        switch count {
        case 0: "sin apps secundarias"
        case 1: "1 app secundaria"
        default: "\(count) apps secundarias"
        }
    }

    private var totalText: String {
        let base = Cup(cents: type.basePriceCUP * 100).formatted
        let secondary = Cup(cents: type.secondaryPriceCUP * 100).formatted
        if secondaries == 0 { return "Total: \(base)" }
        let total = Cup(cents: type.totalPriceCents(secondaries: secondaries)).formatted
        return "Total: \(total) (\(base) + \(secondaries) × \(secondary))"
    }

    private var normalizedRecoveryID: String? {
        let trimmed = recoveryID.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, UUID(uuidString: trimmed) != nil else { return nil }
        return trimmed
    }

    // MARK: - Datos del perfil

    private var profilePhones: [String] {
        let candidates = [store.data.settings.profile.licensePhone,
                          store.data.settings.profile.paymentPhone?.number,
                          store.data.settings.profile.transferPhone]
        var seen = Set<String>()
        return candidates.compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && seen.insert($0).inserted }
    }

    private var validForm: Bool {
        validName(firstName) && validName(lastName) && validCI(identification) && normalizePhone(phone) != nil
            && (recoveryID.isBlank || normalizedRecoveryID != nil)
    }

    private func inputField(_ title: String, text: Binding<String>, placeholder: String,
                            isError: Bool = false, errorText: String? = nil,
                            keyboard: UIKeyboardType = .default,
                            capitalization: TextInputAutocapitalization = .never) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
            TextField(placeholder, text: text)
                .keyboardType(keyboard)
                .textInputAutocapitalization(capitalization)
                .autocorrectionDisabled()
                .font(.system(size: 14))
                .padding(11)
                .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
                .overlay {
                    RoundedRectangle(cornerRadius: 11)
                        .stroke(isError ? Color.shalevError : Color.shalevOutlineVariant, lineWidth: 1)
                }
            if isError, let errorText {
                Text(errorText).font(.system(size: 11)).foregroundStyle(Color.shalevError)
            }
        }
    }

    // MARK: - Acciones

    private func loadProfileIfNeeded() {
        guard !didLoadProfile else { return }
        didLoadProfile = true
        let profile = store.data.settings.profile
        if license.requestProfile.firstName.isEmpty && license.requestProfile.lastName.isEmpty {
            let owner = profile.ownerName.split(whereSeparator: \.isWhitespace)
            license.requestProfile = GLLicenseProfile(
                firstName: profile.ownerFirstName ?? owner.first.map(String.init) ?? "",
                lastName: profile.ownerLastName ?? (owner.count > 1 ? owner.dropFirst().joined(separator: " ") : ""),
                identification: profile.identification,
                phone: profile.licensePhone ?? profile.paymentPhone?.number ?? profile.transferPhone
            )
        }
        firstName = license.requestProfile.firstName
        lastName = license.requestProfile.lastName
        identification = license.requestProfile.identification
        phone = license.requestProfile.phone
        if let installedType = license.installedType { type = installedType }
        if let count = license.installedSecondaries { secondaries = min(10, max(0, count)) }
        editingData = firstName.isBlank || lastName.isBlank || identification.isBlank || phone.isBlank
    }

    private func createRequest() {
        isBusy = true
        defer { isBusy = false }
        do {
            var requestProfile = GLLicenseProfile(firstName: firstName, lastName: lastName,
                                                  identification: identification.uppercased(), phone: phone)
            license.requestProfile = requestProfile
            license.saveRequestProfile()
            var profile = store.data.settings.profile
            profile.ownerFirstName = firstName.trimmingCharacters(in: .whitespacesAndNewlines)
            profile.ownerLastName = lastName.trimmingCharacters(in: .whitespacesAndNewlines)
            profile.ownerName = "\(profile.ownerFirstName ?? "") \(profile.ownerLastName ?? "")".trimmingCharacters(in: .whitespacesAndNewlines)
            profile.identification = identification.uppercased().trimmingCharacters(in: .whitespacesAndNewlines)
            profile.licensePhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
            try store.saveProfile(profile)
            requestProfile.firstName = firstName.trimmingCharacters(in: .whitespacesAndNewlines)
            requestProfile.lastName = lastName.trimmingCharacters(in: .whitespacesAndNewlines)
            requestMessage = try license.buildRequest(type: type, secondaries: secondaries, channel: channel,
                                                       recoveryID: normalizedRecoveryID)
        } catch {
            alertMessage = "Revisa los datos y vuelve a intentarlo."
        }
    }

    private func activate() async {
        isBusy = true
        let result = await license.activate(message: activationText)
        isBusy = false
        activationText = ""
        await license.refresh()
        switch result {
        case .accepted: alertMessage = "Licencia activada"
        case .notFound: alertMessage = "No se encontró una licencia en el texto pegado"
        case .rejected: alertMessage = "La licencia no es válida para este dispositivo"
        case .outdated: alertMessage = "Ya tienes instalada una licencia más reciente"
        }
    }
}

private extension GLLicenseState {
    var isPerpetual: Bool {
        if case .perpetual = self { return true }
        return false
    }
}

private extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}
