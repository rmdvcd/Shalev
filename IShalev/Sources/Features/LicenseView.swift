import SwiftUI
import UIKit

@MainActor
struct LicenseView: View {
    @ObservedObject var store: ShalevStore
    @ObservedObject var license: GLLicenseManager
    let blocking: Bool
    @Environment(\.dismiss) private var dismiss

    @State private var firstName = ""
    @State private var lastName = ""
    @State private var identification = ""
    @State private var phone = ""
    @State private var type: GLLicenseType = .monthly
    @State private var secondaries = 0
    @State private var channel: GLRequestChannel = .whatsapp
    @State private var recoveryID = ""
    @State private var activationText = ""
    @State private var requestMessage: String?
    @State private var alertMessage: String?
    @State private var didLoadProfile = false
    @State private var isBusy = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    ShalevScreenTitle(title: blocking ? "Activar Shalev" : "Licencia", subtitle: "Licencias offline emitidas por GL")
                    statusCard
                    if !license.canValidate {
                        ShalevCard {
                            Label("Esta versión no puede validar licencias", systemImage: "exclamationmark.shield.fill")
                                .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevError)
                            Text("Contacta al desarrollador para obtener una versión actualizada. Las claves públicas fijadas en la app no se pueden cambiar desde Ajustes.")
                                .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                        }
                    }
                    pricesCard
                    requestCard
                    activationCard
                    if blocking { supportCard }
                }
                .padding(18)
                .padding(.bottom, 20)
            }
            .background(Color.shalevBackground)
            .toolbar {
                if !blocking {
                    ToolbarItem(placement: .topBarTrailing) {
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

    private var statusCard: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 9) {
                Label(license.state.title, systemImage: license.state.unlocked ? "checkmark.seal.fill" : "lock.fill")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(license.state.unlocked ? Color.shalevSuccess : Color.shalevSecondary)
                if case .trial = license.state {
                    Text("La prueba dura siete días desde la primera apertura. La fecha se conserva en el llavero del dispositivo.")
                        .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                } else if case .active(_, let expires, let id, let slots) = license.state {
                    detailRow("Vencimiento", expires.shalevDate)
                    detailRow("Apps secundarias", "\(slots)")
                    detailRow("ID de licencia", id)
                } else if case .perpetual(let id, let slots) = license.state {
                    detailRow("Vencimiento", "Nunca")
                    detailRow("Apps secundarias", "\(slots)")
                    detailRow("ID de licencia", id)
                }
                if !license.deviceID.isEmpty { detailRow("ID del dispositivo", license.deviceID) }
            }
        }
    }

    private var pricesCard: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 9) {
                ShalevSectionHeader(title: "Precios de licencia")
                Text("Precio base + importe por cada app secundaria. El total se calcula en la solicitud.")
                    .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                ForEach(GLLicenseType.allCases) { option in
                    HStack {
                        Text(option.title).font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevText)
                        Spacer()
                        Text(Cup(cents: option.totalPriceCents(secondaries: secondaries)).formatted)
                            .font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                    }
                }
            }
        }
    }

    private var requestCard: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 12) {
                ShalevSectionHeader(title: "Solicitar o renovar")
                Text("La solicitud se cifra para GL. No cambies las claves del emisor: la app usa las mismas anclas públicas que Android.")
                    .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                input("Nombre", text: $firstName, placeholder: "Nombre", capitalization: .words)
                input("Apellidos", text: $lastName, placeholder: "Apellidos", capitalization: .words)
                input("Carné de identidad", text: $identification, placeholder: "5–20 letras o dígitos", capitalization: .characters)
                input("Teléfono", text: $phone, placeholder: "+53 5XXX XXXX", keyboard: .phonePad)
                Picker("Tipo de licencia", selection: $type) {
                    ForEach(GLLicenseType.allCases) { option in
                        Text("\(option.title) · \(Cup(cents: option.basePriceCUP * 100).formatted)").tag(option)
                    }
                }
                .pickerStyle(.menu)
                Stepper("Apps secundarias: \(secondaries)", value: $secondaries, in: 0...10)
                    .font(.system(size: 13, weight: .medium)).tint(Color.shalevPrimary)
                Picker("Enviar solicitud por", selection: $channel) {
                    ForEach(GLRequestChannel.allCases) { option in Text(option.title).tag(option) }
                }
                .pickerStyle(.segmented)
                if license.canRecover {
                    input("ID de licencia anterior (opcional)", text: $recoveryID, placeholder: "UUID de la licencia")
                    Text("Si recuperas una licencia, GL conserva su tipo, vencimiento y cupo; la anterior queda revocada.")
                        .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                }
                Button { createRequest() } label: {
                    Label(isBusy ? "Preparando…" : "Crear solicitud cifrada", systemImage: "lock.doc.fill")
                }
                .buttonStyle(ShalevPrimaryButtonStyle())
                .disabled(isBusy || !license.canRequest || !validForm)
                if let requestMessage {
                    Text("Solicitud lista para compartir")
                        .font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevSuccess)
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

    private var activationCard: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 11) {
                ShalevSectionHeader(title: "Activar licencia")
                Text("Pega el mensaje de GL o solo el código SPVI2. La app comprueba la firma y descifra la licencia en este dispositivo.")
                    .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
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
                    .disabled(isBusy || activationText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !license.canValidate)
                }
            }
        }
    }

    private var supportCard: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 8) {
                Label("¿Necesitas ayuda?", systemImage: "questionmark.circle.fill")
                    .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                Text("Si perdiste el teléfono o reinstalaste Shalev, contacta al desarrollador para recuperar la licencia. La recuperación revoca el dispositivo anterior.")
                    .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                Link(destination: URL(string: "https://wa.me/5351815604")!) {
                    Label("Contactar soporte", systemImage: "message.fill")
                }
                .buttonStyle(ShalevTonalButtonStyle())
            }
        }
    }

    private var validForm: Bool {
        validName(firstName) && validName(lastName) && validCI(identification) && normalizePhone(phone) != nil
            && (recoveryID.isBlank || UUID(uuidString: recoveryID.trimmingCharacters(in: .whitespacesAndNewlines)) != nil)
    }

    private func detailRow(_ title: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(title).foregroundStyle(Color.shalevSecondaryText)
            Spacer(minLength: 10)
            Text(value).fontWeight(.medium).multilineTextAlignment(.trailing)
                .textSelection(.enabled)
        }
        .font(.system(size: 12))
    }

    private func input(_ title: String, text: Binding<String>, placeholder: String,
                       keyboard: UIKeyboardType = .default, capitalization: TextInputAutocapitalization = .never) -> some View {
        VStack(alignment: .leading, spacing: 5) {
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
                phone: profile.licensePhone ?? profile.transferPhone
            )
        }
        firstName = license.requestProfile.firstName
        lastName = license.requestProfile.lastName
        identification = license.requestProfile.identification
        phone = license.requestProfile.phone
        if let installedType = license.installedType { type = installedType }
        if let count = license.installedSecondaries { secondaries = min(10, max(0, count)) }
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
                                                       recoveryID: recoveryID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : recoveryID)
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
        case .accepted: alertMessage = "Licencia activada."
        case .notFound: alertMessage = "No se encontró una licencia SPVI2 en el texto."
        case .rejected: alertMessage = "La licencia no es válida para este dispositivo."
        case .outdated: alertMessage = "Ya tienes instalada una licencia más reciente."
        }
    }
}

private extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}
