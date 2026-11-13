import SwiftUI

private struct IShalevRelease: Decodable, Identifiable, Equatable {
    let tagName: String
    let name: String?
    let body: String?
    let htmlUrl: String
    let publishedAt: String?
    let prerelease: Bool

    var id: String { tagName }
    var title: String { name?.isEmpty == false ? name! : tagName }
    var url: URL? { URL(string: htmlUrl) }

    enum CodingKeys: String, CodingKey {
        case tagName = "tag_name"
        case name
        case body
        case htmlUrl = "html_url"
        case publishedAt = "published_at"
        case prerelease
    }
}

@MainActor
private final class IShalevUpdateChecker: ObservableObject {
    enum Status {
        case idle
        case checking
        case available(IShalevRelease)
        case current
        case noIOSRelease
        case failed(String)
    }

    @Published var status: Status = .idle
    var isChecking: Bool { if case .checking = status { true } else { false } }

    func check(currentVersion: String) async {
        status = .checking
        guard let url = URL(string: "https://api.github.com/repos/rmdvcd/Shalev/releases?per_page=100") else {
            status = .failed("La dirección de comprobación no es válida.")
            return
        }
        do {
            var request = URLRequest(url: url)
            request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
            request.setValue("IShalev-iOS/1.0", forHTTPHeaderField: "User-Agent")
            request.timeoutInterval = 20
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
                status = .failed("GitHub no respondió con una lista de publicaciones. Inténtalo de nuevo más tarde.")
                return
            }
            let releases = try JSONDecoder().decode([IShalevRelease].self, from: data)
            guard let release = releases.first(where: {
                $0.tagName.lowercased().hasPrefix("ios-v") && !$0.prerelease
            }) else {
                status = .noIOSRelease
                return
            }
            status = Self.isNewer(release.tagName, than: currentVersion) ? .available(release) : .current
        } catch {
            status = .failed("No se pudo comprobar la conexión con GitHub. Revisa Internet e inténtalo de nuevo.")
        }
    }

    private static func isNewer(_ tag: String, than current: String) -> Bool {
        func numbers(_ value: String, removingIOSPrefix: Bool) -> [Int]? {
            var text = value.trimmingCharacters(in: .whitespacesAndNewlines)
            if removingIOSPrefix, text.lowercased().hasPrefix("ios-v") { text = String(text.dropFirst(5)) }
            else if text.lowercased().hasPrefix("v") { text = String(text.dropFirst()) }
            let components = text.split(separator: ".", omittingEmptySubsequences: false)
            guard !components.isEmpty, components.allSatisfy({ Int($0) != nil }) else { return nil }
            return components.map { Int($0) ?? 0 }
        }
        guard let latest = numbers(tag, removingIOSPrefix: true),
              let installed = numbers(current, removingIOSPrefix: false) else { return false }
        for index in 0..<max(latest.count, installed.count) {
            let lhs = index < latest.count ? latest[index] : 0
            let rhs = index < installed.count ? installed[index] : 0
            if lhs != rhs { return lhs > rhs }
        }
        return false
    }
}

@MainActor
struct UpdatesView: View {
    @StateObject private var checker = IShalevUpdateChecker()
    private let releasePage = URL(string: "https://github.com/rmdvcd/Shalev/releases")!

    private var currentVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                ShalevScreenTitle(title: "Actualizaciones", subtitle: "Comprobar publicaciones de IShalev")
                ShalevCard {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("Versión instalada: \(currentVersion)", systemImage: "iphone")
                            .font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevText)
                        statusContent
                        Button {
                            Task { await checker.check(currentVersion: currentVersion) }
                        } label: {
                            Label(checker.isChecking ? "Comprobando…" : "Comprobar ahora", systemImage: "arrow.clockwise")
                        }
                        .buttonStyle(ShalevPrimaryButtonStyle())
                        .disabled(checker.isChecking)
                    }
                }
                ShalevCard {
                    VStack(alignment: .leading, spacing: 8) {
                        Label("Instalación en iOS", systemImage: "info.circle.fill")
                            .font(.shalev(13, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                        Text("Shalev no puede reemplazar ni instalar su propia app en iOS. Las versiones de iPhone se distribuyen mediante TestFlight, App Store o un canal de administración autorizado. Esta pantalla solo avisa de publicaciones con etiqueta ios-v y abre sus notas; no descarga APK ni cambia la instalación.")
                            .font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText)
                        Link("Ver publicaciones de GitHub", destination: releasePage)
                            .font(.shalev(12, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                    }
                }
                .padding(.bottom, 20)
            }
            .padding(18)
        }
        .background(Color.shalevBackground)
    }

    @ViewBuilder private var statusContent: some View {
        switch checker.status {
        case .idle:
            Text("Pulsa «Comprobar ahora» para consultar la última publicación iOS.")
                .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
        case .checking:
            HStack(spacing: 8) {
                ProgressView().tint(Color.shalevPrimary)
                Text("Consultando las publicaciones…").font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
            }
        case .available(let release):
            VStack(alignment: .leading, spacing: 7) {
                Label("Hay una versión más reciente: \(release.title)", systemImage: "arrow.down.circle.fill")
                    .font(.shalev(13, weight: .semibold)).foregroundStyle(Color.shalevSuccess)
                if let date = release.publishedAt { Text("Publicada: \(date)").font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText) }
                if let notes = release.body, !notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Text(String(notes.prefix(700))).font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText).lineLimit(8)
                }
                if let url = release.url { Link("Abrir notas de la versión", destination: url).font(.shalev(12, weight: .semibold)) }
            }
        case .current:
            Label("IShalev está al día según la última publicación iOS.", systemImage: "checkmark.circle.fill")
                .font(.shalev(12, weight: .medium)).foregroundStyle(Color.shalevSuccess)
        case .noIOSRelease:
            Text("Aún no hay una publicación con etiqueta ios-v. La app instalada no se modificó.")
                .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
        case .failed(let message):
            Label(message, systemImage: "wifi.exclamationmark")
                .font(.shalev(12)).foregroundStyle(Color.shalevError)
        }
    }
}
