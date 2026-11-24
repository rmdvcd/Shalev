import Foundation
import CryptoKit

// MARK: - Directorio de Confianza v1 (Anexo A de IMPLEMENTACION_SHALEV.md)

/// Directorio de claves públicas dinámicas para MIPVentaX / Shalev en iOS.
/// Autenticado por las claves raíz P-256 fijadas en el build (`rootASPKI`, `rootBSPKI`).
enum TrustDirectory {
    static let domainPrefix = "SPVI-DIR1|"
    static let maxSizeBytes = 64 * 1024
    static let version = 1

    // Marcadores para las raíces en compilación (se sustituyen con las raíces reales de producción).
    // Si permanecen como cadenas vacías, las funciones de producción fallan cerrado.
    static let rootASPKI: String = ""
    static let rootBSPKI: String = ""

    struct Envelope: Codable {
        let datos: String
        let firma: String
        let raiz: String?
    }

    struct KeyDTO: Codable {
        let kid: String
        let uso: String
        let spki: String
        let huella: String
        let desde: Int64
        let hasta: Int64?
        let estado: String
    }

    /// Bloque opcional de canje (Anexo A / B). Solo datos públicos: base HTTPS autorizada y `appId` por producto.
    struct CanjeDTO: Codable {
        let endpoint_base: String?
        let app_ids: [String: String]?
    }

    struct DataDTO: Codable {
        let v: Int
        let seq: Int
        let emitidaEn: Int64
        let claves: [KeyDTO]
        let canje: CanjeDTO?
    }

    struct Key: Equatable {
        let kid: String
        let usage: String
        let spkiDER: Data
        let fingerprint: String
        let since: Int64
        let until: Int64?
        let status: String

        var isActive: Bool { status != "comprometida" }

        func covers(seconds: Int64) -> Bool {
            if status == "comprometida" { return false }
            if seconds < since { return false }
            if let until, seconds > until { return false }
            return true
        }
    }

    struct Directory: Equatable {
        let v: Int
        let seq: Int
        let issuedAt: Int64
        let keys: [Key]
        /// Base HTTPS autorizada del servicio de canje; vacía = canje cerrado.
        var endpointBase: String = ""
        /// `appId` autorizados por producto (`canje.app_ids`). Vacío = usar el del build.
        var appIds: [String: String] = [:]

        func signKeys(issuedAt: Date? = nil) -> [P256.Signing.PublicKey] {
            let seconds = Int64((issuedAt ?? Date()).timeIntervalSince1970)
            return keys
                .filter { $0.usage == "firma" && $0.covers(seconds: seconds) }
                .compactMap { try? P256.Signing.PublicKey(derRepresentation: $0.spkiDER) }
        }

        func activeECDH() -> P256.KeyAgreement.PublicKey? {
            guard let key = keys.first(where: { $0.usage == "ecdh" && $0.status == "vigente" }) else { return nil }
            return try? P256.KeyAgreement.PublicKey(derRepresentation: key.spkiDER)
        }

        func redeemKey(kid: String) -> P256.Signing.PublicKey? {
            guard let key = keys.first(where: { $0.usage == "canje" && $0.kid == kid && $0.isActive }) else { return nil }
            return try? P256.Signing.PublicKey(derRepresentation: key.spkiDER)
        }

        /// true si hay al menos una clave de canje utilizable (no comprometida).
        var hasActiveRedeemKey: Bool {
            keys.contains { $0.usage == "canje" && $0.isActive }
        }

        func canjeKeys() -> [P256.Signing.PublicKey] {
            keys.filter { $0.usage == "canje" && $0.isActive }
                .compactMap { try? P256.Signing.PublicKey(derRepresentation: $0.spkiDER) }
        }

        func isCompromised(der: Data) -> Bool {
            keys.contains(where: { $0.spkiDER == der && $0.status == "comprometida" })
        }
    }

    private static var _effective: Directory? = nil
    private static let lock = NSLock()

    static var effectiveDirectory: Directory? {
        get {
            lock.lock()
            defer { lock.unlock() }
            return _effective
        }
        set {
            lock.lock()
            defer { lock.unlock() }
            _effective = newValue
        }
    }

    static func compileTimeRoots() -> [Data] {
        var roots: [Data] = []
        for b64 in [rootASPKI, rootBSPKI] where !b64.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            if let data = Data(base64Encoded: b64.trimmingCharacters(in: .whitespacesAndNewlines)),
               (try? P256.Signing.PublicKey(derRepresentation: data)) != nil {
                roots.append(data)
            }
        }
        return roots
    }

    enum DirectoryError: Error, Equatable {
        case oversized
        case invalidEnvelope
        case missingRoots
        case invalidSignature
        case invalidData
        case unsupportedVersion(Int)
        case invalidSequence
        case keyMismatch(String)
        case multipleActiveECDH
        case rollback(current: Int, next: Int)
    }

    static func parseAndVerify(_ data: Data, rootsDER: [Data]? = nil) throws -> Directory {
        guard let text = String(data: data, encoding: .utf8) else { throw DirectoryError.invalidData }
        return try parse(text: text, rootsDER: rootsDER)
    }

    static func parseAndVerify(_ text: String, rootsDER: [Data]? = nil) throws -> Directory {
        return try parse(text: text, rootsDER: rootsDER)
    }

    static func parse(text: String, rootsDER: [Data]? = nil) throws -> Directory {
        guard let utf8 = text.data(using: .utf8), utf8.count <= maxSizeBytes else {
            throw DirectoryError.oversized
        }
        guard let envelope = try? JSONDecoder().decode(Envelope.self, from: utf8),
              !envelope.datos.isEmpty, !envelope.firma.isEmpty else {
            throw DirectoryError.invalidEnvelope
        }

        let roots = rootsDER ?? compileTimeRoots()
        guard !roots.isEmpty else { throw DirectoryError.missingRoots }

        guard let rawSig = Base64URL.decode(envelope.firma.trimmingCharacters(in: .whitespacesAndNewlines)),
              rawSig.count == 64,
              let derSig = SignatureFormat.der(fromRaw: rawSig),
              let signature = try? P256.Signing.ECDSASignature(derRepresentation: derSig) else {
            throw DirectoryError.invalidSignature
        }

        let signedMessage = Data((domainPrefix + envelope.datos).utf8)
        let verified = roots.contains { der in
            guard let pub = try? P256.Signing.PublicKey(derRepresentation: der) else { return false }
            return pub.isValidSignature(signature, for: signedMessage)
        }
        guard verified else { throw DirectoryError.invalidSignature }

        guard let dataJSON = envelope.datos.data(using: .utf8),
              let dto = try? JSONDecoder().decode(DataDTO.self, from: dataJSON) else {
            throw DirectoryError.invalidData
        }

        guard dto.v == version else { throw DirectoryError.unsupportedVersion(dto.v) }
        guard dto.seq >= 0 else { throw DirectoryError.invalidSequence }

        var activeECDHCount = 0
        var keys: [Key] = []

        for k in dto.claves {
            guard ["firma", "ecdh", "canje"].contains(k.uso) else { throw DirectoryError.invalidData }
            guard ["vigente", "retirada", "comprometida"].contains(k.estado) else { throw DirectoryError.invalidData }
            guard let spkiDER = Data(base64Encoded: k.spki.trimmingCharacters(in: .whitespacesAndNewlines)) else {
                throw DirectoryError.invalidData
            }
            guard (try? P256.Signing.PublicKey(derRepresentation: spkiDER)) != nil else {
                throw DirectoryError.invalidData
            }

            let calculated = Data(SHA256.hash(data: spkiDER)).map { String(format: "%02x", $0) }.joined()
            let normalizedExpected = k.huella.lowercased().replacingOccurrences(of: "sha256:", with: "").filter { $0.isHexDigit }
            guard calculated == normalizedExpected else {
                throw DirectoryError.keyMismatch(k.kid)
            }

            if k.uso == "ecdh" && k.estado == "vigente" {
                activeECDHCount += 1
                if activeECDHCount > 1 { throw DirectoryError.multipleActiveECDH }
            }

            keys.append(Key(
                kid: k.kid,
                usage: k.uso,
                spkiDER: spkiDER,
                fingerprint: k.huella,
                since: k.desde,
                until: k.hasta,
                status: k.estado
            ))
        }

        return Directory(
            v: dto.v,
            seq: dto.seq,
            issuedAt: dto.emitidaEn,
            keys: keys,
            endpointBase: ((dto.canje?.endpoint_base ?? "").trimmingCharacters(in: .whitespacesAndNewlines))
                .replacingOccurrences(of: "/+$", with: "", options: .regularExpression),
            appIds: dto.canje?.app_ids ?? [:]
        )
    }

    static func applyAntiRollback(current: Directory?, next: Directory) throws -> Directory {
        guard let current else { return next }
        if next.seq < current.seq {
            throw DirectoryError.rollback(current: current.seq, next: next.seq)
        }
        return next
    }

    static let officialURL = URL(string: "https://github.com/rmdvcd/Shalev/releases/download/revocaciones/directorio.json")!
    static let allowedHost = "github.com"
}

/// Gestor de descarga y caché de directorio en iOS (Fase 1.3).
final class TrustDirectoryManager {
    static let shared = TrustDirectoryManager()
    var cacheURL: URL?

    func loadEffective(cache: URL? = nil) -> TrustDirectory.Directory? {
        let urlToUse = cache ?? cacheURL
        let bootstrap: TrustDirectory.Directory? = {
            let bundleURL = Bundle.main.url(forResource: "directorio-bootstrap", withExtension: "json")
                ?? Bundle(for: TrustDirectoryManager.self).url(forResource: "directorio-bootstrap", withExtension: "json")
            if let bundleURL, let data = try? Data(contentsOf: bundleURL) {
                return try? TrustDirectory.parseAndVerify(data)
            }
            return nil
        }()

        let cached: TrustDirectory.Directory? = {
            guard let urlToUse, FileManager.default.fileExists(atPath: urlToUse.path),
                  let data = try? Data(contentsOf: urlToUse) else { return nil }
            return try? TrustDirectory.parseAndVerify(data)
        }()

        var effective = bootstrap
        if let cached {
            if let updated = try? TrustDirectory.applyAntiRollback(current: bootstrap, next: cached) {
                effective = updated
            }
        }
        TrustDirectory.effectiveDirectory = effective
        return effective
    }

    func updateFromNetwork(cache: URL? = nil) async -> Bool {
        let urlToUse = cache ?? cacheURL
        guard TrustDirectory.officialURL.scheme == "https",
              TrustDirectory.officialURL.host == TrustDirectory.allowedHost else { return false }
        do {
            let request = URLRequest(url: TrustDirectory.officialURL, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 10.0)
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200, data.count <= TrustDirectory.maxSizeBytes else {
                return false
            }
            let next = try TrustDirectory.parseAndVerify(data)
            let current = TrustDirectory.effectiveDirectory ?? loadEffective(cache: urlToUse)
            let updated = try TrustDirectory.applyAntiRollback(current: current, next: next)
            if let urlToUse {
                try data.write(to: urlToUse, options: .atomic)
            }
            TrustDirectory.effectiveDirectory = updated
            return true
        } catch {
            return false
        }
    }
}
