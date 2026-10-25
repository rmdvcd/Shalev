import Foundation
import CryptoKit
import Security

// MARK: - Protocolo LAN v1 compartido con Shalev Android

/// Errores sin datos sensibles: nunca incluyen claves, token, ventas ni contenido de tramas.
enum SyncError: LocalizedError, Equatable {
    case invalidQR
    case expiredQR
    case invalidMessage
    case frameTooLarge
    case authentication
    case timeout
    case disconnected
    case rejected(String)
    case permission
    case syncRequired
    case remote(String)
    case storage

    var errorDescription: String? {
        switch self {
        case .invalidQR: "El código no es una vinculación válida de Shalev."
        case .expiredQR: "El código de vinculación venció. Genera uno nuevo en la principal."
        case .invalidMessage: "La otra app envió un mensaje que no se pudo interpretar."
        case .frameTooLarge: "El mensaje de sincronización supera el límite del protocolo."
        case .authentication: "No se pudo autenticar la sesión cifrada."
        case .timeout: "La principal no respondió a tiempo. Revisa que ambas apps estén en la misma red."
        case .disconnected: "Se perdió la conexión con la otra app. Los cambios locales siguen guardados."
        case .rejected("codigo_vencido"): "El código de vinculación venció."
        case .rejected("codigo_invalido"): "El código ya fue utilizado o fue sustituido."
        case .rejected("otro_negocio"): "El código pertenece a otro negocio."
        case .rejected("quitada"): "La principal quitó esta app secundaria."
        case .rejected("desconocida"): "La principal ya no reconoce esta vinculación."
        case .rejected("version"): "Las versiones del protocolo de sincronización no son compatibles."
        case .rejected(let reason): "La principal rechazó la conexión (\(reason))."
        case .permission: "La principal no concedió permiso para esta acción."
        case .syncRequired: "Sincroniza con la principal antes de abrir el turno."
        case .remote(let message): message
        case .storage: "No se pudo guardar la configuración segura de sincronización."
        }
    }
}

struct SyncPairingCode: Equatable {
    static let prefix = "SPVI-VINC1:"
    static let validity: TimeInterval = 10 * 60

    var businessID: String
    var businessName: String
    var hosts: [String]
    var port: Int
    var employeeID: Int64
    var employeeName: String
    var token: Data
    var expiresAt: Date

    private struct Content: Codable {
        var n: String
        var nn: String
        var h: [String]
        var p: Int
        var e: Int64
        var en: String
        var t: String
        var x: Int64
    }

    func encoded() throws -> String {
        guard !businessID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !hosts.isEmpty, (1...65_535).contains(port), employeeID > 0, token.count == 16 else {
            throw SyncError.invalidQR
        }
        let content = Content(n: businessID, nn: businessName, h: hosts, p: port, e: employeeID,
            en: employeeName, t: SyncBase64.url(token), x: Int64(expiresAt.timeIntervalSince1970 * 1_000))
        let json = try JSONEncoder().encode(content)
        return Self.prefix + SyncBase64.url(json)
    }

    static func decode(_ text: String, now: Date = Date(), requireCurrent: Bool = false) throws -> SyncPairingCode {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count <= 2_000 + prefix.count, trimmed.hasPrefix(prefix),
              let json = SyncBase64.decodeURL(String(trimmed.dropFirst(prefix.count))),
              let content = try? JSONDecoder().decode(Content.self, from: json),
              let token = SyncBase64.decodeURL(content.t), token.count == 16,
              !content.n.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !content.h.isEmpty, (1...65_535).contains(content.p), content.e > 0 else {
            throw SyncError.invalidQR
        }
        let result = SyncPairingCode(businessID: content.n, businessName: content.nn, hosts: content.h,
            port: content.p, employeeID: content.e, employeeName: content.en, token: token,
            expiresAt: Date(timeIntervalSince1970: Double(content.x) / 1_000))
        if requireCurrent && now >= result.expiresAt { throw SyncError.expiredQR }
        return result
    }
}

enum SyncBase64 {
    static func url(_ data: Data) -> String {
        data.base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }

    static func decodeURL(_ text: String) -> Data? {
        var value = text.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        value += String(repeating: "=", count: (4 - value.count % 4) % 4)
        return Data(base64Encoded: value)
    }
}

enum SyncJSON {
    static func data(_ object: [String: Any]) throws -> Data {
        guard JSONSerialization.isValidJSONObject(object) else { throw SyncError.invalidMessage }
        return try JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
    }

    static func object(_ data: Data) throws -> [String: Any] {
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw SyncError.invalidMessage
        }
        return object
    }

    static func message(_ type: String, _ fields: [String: Any] = [:]) -> [String: Any] {
        var result = fields
        result["t"] = type
        return result
    }

    static func type(_ message: [String: Any]) throws -> String {
        guard let type = message["t"] as? String, !type.isEmpty else { throw SyncError.invalidMessage }
        return type
    }
}

enum SyncFrames {
    static let maximum = 16 * 1_024 * 1_024
    static let maximumGreeting = 64 * 1_024
    static let maximumClear = 4 * maximum

    static func lengthPrefix(_ count: Int) throws -> Data {
        guard (0...maximum).contains(count) else { throw SyncError.frameTooLarge }
        let value = UInt32(count)
        return Data([UInt8(truncatingIfNeeded: value >> 24), UInt8(truncatingIfNeeded: value >> 16),
                     UInt8(truncatingIfNeeded: value >> 8), UInt8(truncatingIfNeeded: value)])
    }

    static func decodeLength(_ prefix: Data) throws -> Int {
        guard prefix.count == 4 else { throw SyncError.invalidMessage }
        let value = prefix.reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
        guard value <= UInt32(maximum) else { throw SyncError.frameTooLarge }
        return Int(value)
    }
}

enum SyncCrypto {
    static let keyBytes = 32
    static let nonceBytes = 16

    static func random(_ count: Int) throws -> Data {
        var result = Data(count: count)
        let status = result.withUnsafeMutableBytes { buffer in
            SecRandomCopyBytes(kSecRandomDefault, count, buffer.baseAddress!)
        }
        guard status == errSecSuccess else { throw SyncError.authentication }
        return result
    }

    static func hmac(_ key: Data, _ parts: Data...) -> Data {
        let hmac = HMAC<SHA256>(key: SymmetricKey(data: key))
        var authenticator = hmac
        for part in parts { authenticator.update(data: part) }
        return Data(authenticator.finalize())
    }

    static func equal(_ lhs: Data, _ rhs: Data) -> Bool {
        guard lhs.count == rhs.count else { return false }
        var difference: UInt8 = 0
        for (a, b) in zip(lhs, rhs) { difference |= a ^ b }
        return difference == 0
    }

    static func secondaryPairingMAC(token: Data, publicKeySPKI: Data) -> Data {
        hmac(token, Data("spvi-vinc-c".utf8), publicKeySPKI)
    }

    static func principalPairingMAC(token: Data, principalSPKI: Data, secondarySPKI: Data) -> Data {
        hmac(token, Data("spvi-vinc-s".utf8), principalSPKI, secondarySPKI)
    }

    static func rejectionMAC(key: Data, nonce: Data, reason: String) -> Data {
        hmac(key, Data("spvi-rechazo".utf8), nonce, Data(reason.utf8))
    }

    static func employeeKey(privateKey: P256.KeyAgreement.PrivateKey, peerSPKI: Data, token: Data) throws -> Data {
        let peer = try P256.KeyAgreement.PublicKey(derRepresentation: peerSPKI)
        let secret = try privateKey.sharedSecretFromKeyAgreement(with: peer)
        let key = secret.hkdfDerivedSymmetricKey(using: SHA256.self, salt: token,
            sharedInfo: Data("spvi-clave-empleado-v1".utf8), outputByteCount: keyBytes)
        return key.withUnsafeBytes { Data($0) }
    }

    /// Primero cliente→servidor, después servidor→cliente, exactamente como `CriptoSync.clavesSesion`.
    static func sessionKeys(employeeKey: Data, secondaryNonce: Data, principalNonce: Data) throws -> (c2s: Data, s2c: Data) {
        guard employeeKey.count == keyBytes, secondaryNonce.count == nonceBytes, principalNonce.count == nonceBytes else {
            throw SyncError.authentication
        }
        let salt = secondaryNonce + principalNonce
        func derive(_ label: String) -> SymmetricKey {
            HKDF<SHA256>.deriveKey(inputKeyMaterial: SymmetricKey(data: employeeKey), salt: salt,
                info: Data(label.utf8), outputByteCount: keyBytes)
        }
        let c2s = derive("spvi-c2s-v1").withUnsafeBytes { Data($0) }
        let s2c = derive("spvi-s2c-v1").withUnsafeBytes { Data($0) }
        return (c2s, s2c)
    }

    static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}

/// AES-GCM + gzip + número de secuencia autenticado. Una instancia pertenece a una sola conexión.
struct SyncSubTokenData: Equatable {
    let licenseID: String
    let deviceID: String
    let slot: Int
    let allowed: Int
    let expiresAt: Date?
}

/// Permiso de secundaria 0.31+: firma ECDSA de la identidad de la principal, no una cuota autodeclarada.
enum SyncSubToken {
    static func validDeviceID(_ raw: String) -> String? {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard (8...128).contains(value.utf8.count), value.utf8.allSatisfy({
            ($0 >= 48 && $0 <= 57) || ($0 >= 65 && $0 <= 90) || ($0 >= 97 && $0 <= 122) || $0 == 58 || $0 == 95 || $0 == 45
        }) else { return nil }
        return value
    }

    static func issue(licenseID: String, deviceID: String, slot: Int, allowed: Int,
                      expiresAt: Date?, signer: P256.Signing.PrivateKey) throws -> (token: String, publicKey: String) {
        guard !licenseID.isEmpty, licenseID.count <= 64, !deviceID.isEmpty, deviceID.count <= 128,
              !licenseID.contains("|"), !licenseID.contains("."), !deviceID.contains("|"), !deviceID.contains("."),
              (0...10).contains(allowed), slot >= 1, slot <= allowed else { throw SyncError.permission }
        let text = [licenseID, deviceID, String(slot), String(allowed), expiresAt.map(dateString) ?? "PERPETUA"].joined(separator: "|")
        let signature = try signer.signature(for: Data(text.utf8)).derRepresentation
        return (text + "." + signature.base64EncodedString(), signer.publicKey.derRepresentation.base64EncodedString())
    }

    static func verify(_ token: String, publicKeyBase64: String, deviceID: String, now: Date) -> SyncSubTokenData? {
        guard !token.isEmpty, token.count <= 4_096, let cut = token.lastIndex(of: "."), cut > token.startIndex else { return nil }
        let text = String(token[..<cut]), encoded = String(token[token.index(after: cut)...])
        guard !encoded.isEmpty, let der = Data(base64Encoded: encoded),
              let keyDER = Data(base64Encoded: publicKeyBase64),
              let key = try? P256.Signing.PublicKey(derRepresentation: keyDER),
              let signature = try? P256.Signing.ECDSASignature(derRepresentation: der),
              key.isValidSignature(signature, for: Data(text.utf8)) else { return nil }
        let fields = text.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        guard fields.count == 5, let slot = Int(fields[2]), let allowed = Int(fields[3]),
              (0...10).contains(allowed), slot >= 1, slot <= allowed, fields[1] == deviceID else { return nil }
        let expiry: Date?
        if fields[4] == "PERPETUA" { expiry = nil }
        else if let date = parseDate(fields[4]) { expiry = date }
        else { return nil }
        guard expiry == nil || now < expiry! else { return nil }
        return SyncSubTokenData(licenseID: fields[0], deviceID: fields[1], slot: slot, allowed: allowed, expiresAt: expiry)
    }

    private static func dateString(_ date: Date) -> String {
        let format = ISO8601DateFormatter(); format.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return format.string(from: date)
    }
    private static func parseDate(_ text: String) -> Date? {
        let format = ISO8601DateFormatter(); format.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = format.date(from: text) { return date }
        format.formatOptions = [.withInternetDateTime]
        return format.date(from: text)
    }
}

struct SyncChannelCipher {
    enum Side { case principal, secondary }

    private let sendKey: SymmetricKey
    private let receiveKey: SymmetricKey
    private let sendLabel: UInt8
    private let receiveLabel: UInt8
    private var sendSequence: Int64 = 0
    private var receiveSequence: Int64 = 0

    init(side: Side, employeeKey: Data, secondaryNonce: Data, principalNonce: Data) throws {
        let keys = try SyncCrypto.sessionKeys(employeeKey: employeeKey, secondaryNonce: secondaryNonce, principalNonce: principalNonce)
        switch side {
        case .secondary:
            sendKey = SymmetricKey(data: keys.c2s); receiveKey = SymmetricKey(data: keys.s2c)
            sendLabel = 1; receiveLabel = 2
        case .principal:
            sendKey = SymmetricKey(data: keys.s2c); receiveKey = SymmetricKey(data: keys.c2s)
            sendLabel = 2; receiveLabel = 1
        }
    }

    mutating func seal(_ message: [String: Any]) throws -> Data {
        _ = try SyncJSON.type(message)
        let compressed = try GZIP.encode(SyncJSON.data(message))
        let sequence = sendSequence
        guard sequence < Int64.max else { throw SyncError.authentication }
        sendSequence += 1
        let iv = try SyncCrypto.random(12)
        let box = try AES.GCM.seal(compressed, using: sendKey, nonce: AES.GCM.Nonce(data: iv),
            authenticating: Self.aad(label: sendLabel, sequence: sequence))
        return iv + box.ciphertext + box.tag
    }

    mutating func open(_ frame: Data) throws -> [String: Any] {
        guard frame.count >= 28, frame.count <= SyncFrames.maximum else { throw SyncError.invalidMessage }
        let sequence = receiveSequence
        guard sequence < Int64.max else { throw SyncError.authentication }
        receiveSequence += 1
        do {
            let nonce = try AES.GCM.Nonce(data: frame.prefix(12))
            let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: frame.dropFirst(12).dropLast(16), tag: frame.suffix(16))
            let compressed = try AES.GCM.open(box, using: receiveKey, authenticating: Self.aad(label: receiveLabel, sequence: sequence))
            let clear = try GZIP.decode(compressed, maximumBytes: SyncFrames.maximumClear)
            return try SyncJSON.object(clear)
        } catch {
            throw SyncError.authentication
        }
    }

    private static func aad(label: UInt8, sequence: Int64) -> Data {
        var result = Data("SPVI-S1\0".utf8)
        result.append(label)
        let value = UInt64(bitPattern: sequence)
        for shift in stride(from: 56, through: 0, by: -8) { result.append(UInt8(truncatingIfNeeded: value >> shift)) }
        return result
    }
}
