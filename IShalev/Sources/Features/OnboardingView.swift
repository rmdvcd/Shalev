import SwiftUI
import UIKit

@MainActor
struct OnboardingView: View {
    @ObservedObject var store: ShalevStore
    let onComplete: () -> Void

    @AppStorage("shalev.module.sales") private var salesEnabled = true
    @AppStorage("shalev.module.inventory") private var inventoryEnabled = true
    @AppStorage("shalev.module.services") private var servicesEnabled = true
    @State private var businessName = ""
    @State private var ownerFirstName = ""
    @State private var ownerLastName = ""
    @State private var identification = ""
    @State private var phone = ""
    @State private var transferPhone = ""
    @State private var transferCard = ""
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 17) {
                HStack(spacing: 12) {
                    Image(systemName: "storefront.fill")
                        .font(.system(size: 25, weight: .semibold))
                        .foregroundStyle(Color.shalevOnPrimary)
                        .frame(width: 58, height: 58)
                        .background(Color.shalevPrimary, in: RoundedRectangle(cornerRadius: 16))
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Bienvenido a Shalev").font(.system(size: 23, weight: .bold)).foregroundStyle(Color.shalevText)
                        Text("Configura tu negocio para empezar").font(.system(size: 13)).foregroundStyle(Color.shalevSecondaryText)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.top, 20)

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

                ShalevCard {
                    VStack(alignment: .leading, spacing: 11) {
                        ShalevSectionHeader(title: "Módulos del negocio")
                        Toggle(isOn: $salesEnabled) {
                            Label("Ventas de productos", systemImage: "cart.fill")
                                .font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevText)
                        }
                        Toggle(isOn: $inventoryEnabled) {
                            Label("Inventario", systemImage: "archivebox.fill")
                                .font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevText)
                        }
                        Toggle(isOn: $servicesEnabled) {
                            Label("Servicios", systemImage: "storefront.fill")
                                .font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevText)
                        }
                        Text("Ventas de productos incluye el acceso al inventario. Debe quedar al menos un módulo activo. Los perfiles por empleado se administran en Ajustes → Empleados y son locales a este iPhone; aún no se sincronizan con Android.")
                            .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                    }
                    .tint(Color.shalevPrimary)
                }

                if let errorMessage {
                    Text(errorMessage).font(.system(size: 12)).foregroundStyle(Color.shalevError)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                Button(action: finish) {
                    Label("Guardar y comenzar", systemImage: "arrow.right.circle.fill")
                }
                .buttonStyle(ShalevPrimaryButtonStyle())
                .padding(.bottom, 24)
            }
            .padding(.horizontal, 18)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .background(Color.shalevBackground.ignoresSafeArea())
        .task { loadProfile() }
        .alert("No se pudo guardar el perfil", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Entendido", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

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
