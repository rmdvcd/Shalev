import SwiftUI

@MainActor
struct SupportView: View {
    private let whatsapp = URL(string: "https://wa.me/5351815604")!
    private let sms = URL(string: "sms:+5351815604")!
    private let phone = URL(string: "tel:+5351815604")!

    private var version: String {
        let info = Bundle.main.infoDictionary
        let name = info?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = info?["CFBundleVersion"] as? String
        return build.map { "\(name) (\($0))" } ?? name
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                ShalevScreenTitle(title: "Soporte", subtitle: "Contacto del desarrollador")
                ShalevCard {
                    VStack(spacing: 12) {
                        Image(systemName: "person.crop.circle.badge.questionmark")
                            .font(.shalev(43)).foregroundStyle(Color.shalevPrimary)
                            .frame(maxWidth: .infinity).padding(.bottom, 4)
                        contactRow("Desarrollador", "Ing. Ronnie Montero Duarte")
                        Divider()
                        contactRow("CI", "91040922502")
                        Divider()
                        HStack {
                            Text("Teléfono").foregroundStyle(Color.shalevSecondaryText)
                            Spacer(minLength: 10)
                            Link("+53 5 181 5604", destination: phone)
                                .fontWeight(.semibold).foregroundStyle(Color.shalevText)
                        }
                        Divider()
                        contactRow("Especialidad", "Desarrollo de sistemas y aplicaciones multiplataforma")
                    }
                    .font(.shalev(13))
                    .textSelection(.enabled)
                }
                ShalevCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("¿Tienes dudas o problemas? Escribe al desarrollador.")
                            .font(.shalev(13, weight: .medium)).foregroundStyle(Color.shalevText)
                        Link(destination: whatsapp) {
                            Label("Escribir por WhatsApp", systemImage: "message.fill")
                        }
                        .buttonStyle(ShalevPrimaryButtonStyle())
                        Link(destination: sms) {
                            Label("Enviar SMS", systemImage: "text.bubble.fill")
                        }
                        .buttonStyle(ShalevTonalButtonStyle())
                        Text("Nunca te pediremos tus contraseñas ni los datos de tu tarjeta.\nIShalev · \(version)")
                            .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                            .frame(maxWidth: .infinity).multilineTextAlignment(.center).padding(.top, 3)
                    }
                }
                .padding(.bottom, 20)
            }
            .padding(18)
        }
        .background(Color.shalevBackground)
    }

    private func contactRow(_ title: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text(title).foregroundStyle(Color.shalevSecondaryText)
            Spacer(minLength: 10)
            Text(value).fontWeight(.semibold).foregroundStyle(Color.shalevText).multilineTextAlignment(.trailing)
        }
        .accessibilityElement(children: .combine)
    }
}
