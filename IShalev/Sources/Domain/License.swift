import Foundation
import Combine
import CryptoKit
import Security

// MARK: - Contrato GL v1 / SPVI2

/// Anclas públicas copiadas literalmente de `licencia/LicenseTrust.kt` (Android).
/// No se regeneran ni se sustituyen: cambiar cualquiera rompería solicitudes o licencias emitidas por GL.
/// La clave ECDH actual cifra solicitudes; las cinco claves de firma permiten validar licencias de todas las rotaciones conocidas.
enum GLTrust {
    struct Pin {
        let spkiBase64: String
        let sha256: String
    }

    static let ecdh = Pin(
        spkiBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE4UHhdTzS3X/2pk5wmEAibv760EBcnxDjERo8km4OCdIhVDc5D+N0yPQlq6UpiXV1exXVmMfD6vqezlNsYFEIYg==",
        sha256: "sha256:fb3a:f7fc:e8a2:e86d:4e83:5351:d677:6aec:a9b6:5071:21d7:a417:9cd7:4b1b:0a53:078d"
    )

    static let signatures: [Pin] = [
        Pin(spkiBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8TwMudgF0ioPFGp+VOQDU+xBDEQQQRHErwPyKQupkBGS8WL/dQVAFN4t3PIddOBl3Kg60ucThoOicZFctdtjIQ==",
            sha256: "sha256:113f:a5f6:af7f:f43e:a87e:5f16:c578:36bb:58d1:d73a:c7a8:bb65:568a:b0bf:6d1a:4b1c"),
        Pin(spkiBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1LFKFD7Af1xhbza5CrD7ArwOwq5h4qG70edEp54zozXoUa/r7P7xRQwwV82rXcy0mPznM3TNMk8wGxYCwR7ubg==",
            sha256: "sha256:58d4:3aac:6f1b:099a:e86c:4375:b7c7:ffb5:8631:2399:246c:1f26:f861:e302:9c83:8c4e"),
        Pin(spkiBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPjc7tw29A0TaynecwlYDphDAtavpfNQ6njeGIX59hIDO+ID9KxTLo4ZaxlPJ/enkCzlBo+5JEMn5AUN8bneC7Q==",
            sha256: "sha256:e693:6c20:2461:57af:0f91:2313:94a4:80a5:0d8c:d150:34a1:091b:08af:b121:2828:0a6e"),
        Pin(spkiBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE/Kx1wh2WpM/JcPeNs7e7a5QvRMARHAPreFZ9YauzFguI85mT7/wC/jmKxR1ml5SGrGWCfA88Qo+PQ4efSUSsdg==",
            sha256: "sha256:d9c4:d821:1880:bc79:dda6:f92a:0c0d:f6a4:dc0d:a00a:191f:9c05:229d:4c1f:3be7:998e"),
        Pin(spkiBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+UpBZNnTcsbL5yR5LQRD1qATSub6tV7dgaVKNgXjFUbJFbxWmUQWOQvqsOBZmt7bKDdu3iLW02Z/RtiPfa3OWQ==",
            sha256: "sha256:8a91:bcfb:19dc:af1b:af6e:0bb7:c82a:9410:e9b5:aa1d:fea0:cab8:241c:52d1:bcbd:6888")
    ]

    static let ecdhPublicKey: P256.KeyAgreement.PublicKey? = validatedECDH()
    static let signingPublicKeys: [P256.Signing.PublicKey] = signatures.compactMap(validatedSigningKey)
    static let canValidate = !signingPublicKeys.isEmpty
    static let canRequest = ecdhPublicKey != nil && canValidate

    private static func validatedECDH() -> P256.KeyAgreement.PublicKey? {
        guard let der = Data(base64Encoded: ecdh.spkiBase64), fingerprint(der) == normalizedFingerprint(ecdh.sha256) else { return nil }
        return try? P256.KeyAgreement.PublicKey(derRepresentation: der)
    }

    private static func validatedSigningKey(_ pin: Pin) -> P256.Signing.PublicKey? {
        guard let der = Data(base64Encoded: pin.spkiBase64), fingerprint(der) == normalizedFingerprint(pin.sha256) else { return nil }
        return try? P256.Signing.PublicKey(derRepresentation: der)
    }

    private static func fingerprint(_ der: Data) -> String { Data(SHA256.hash(data: der)).hexLowercase }
    private static func normalizedFingerprint(_ text: String) -> String {
        text.lowercased().replacingOccurrences(of: "sha256:", with: "").filter { $0.isHexDigit }
    }
}

private extension Data {
    var hexLowercase: String { map { String(format: "%02x", $0) }.joined() }
}

enum GLLicenseType: String, CaseIterable, Codable, Identifiable, Hashable {
    case monthly = "MENSUAL"
    case semiannual = "SEMESTRAL"
    case annual = "ANUAL"
    case perpetual = "PERPETUA"

    var id: String { rawValue }
    var title: String {
        switch self {
        case .monthly: "Mensual"
        case .semiannual: "Semestral"
        case .annual: "Anual"
        case .perpetual: "Perpetua"
        }
    }
    var basePriceCUP: Int64 {
        switch self {
        case .monthly: 6_000
        case .semiannual: 30_000
        case .annual: 50_000
        case .perpetual: 90_000
        }
    }
    var secondaryPriceCUP: Int64 {
        switch self {
        case .monthly: 1_000
        case .semiannual: 5_000
        case .annual: 9_000
        case .perpetual: 17_000
        }
    }
    func totalPriceCents(secondaries: Int) -> Int64 { (basePriceCUP + Int64(max(0, secondaries)) * secondaryPriceCUP) * 100 }
}

enum GLRequestChannel: String, CaseIterable, Identifiable, Codable, Hashable {
    case whatsapp = "WHATSAPP"
    case sms = "SMS"
    var id: String { rawValue }
    var title: String { self == .whatsapp ? "WhatsApp" : "SMS" }
}

enum GLActivationResult: Equatable {
    case accepted
    case notFound
    case rejected
    case outdated
}

enum GLLicenseState: Equatable {
    case loading
    case trial(daysLeft: Int)
    case trialExpired
    case active(type: GLLicenseType, expiresAt: Date, id: String, secondaries: Int)
    case perpetual(id: String, secondaries: Int)
    case expired(type: GLLicenseType)
    case revoked
    case clockTampered
    case unavailable

    var unlocked: Bool {
        switch self {
        case .trial, .active, .perpetual: true
        default: false
        }
    }

    var installedID: String? {
        switch self {
        case .active(_, _, let id, _), .perpetual(let id, _): id
        default: nil
        }
    }

    var installedType: GLLicenseType? {
        switch self {
        case .active(let type, _, _, _), .expired(let type): type
        case .perpetual: .perpetual
        default: nil
        }
    }

    var installedSecondaries: Int? {
        switch self {
        case .active(_, _, _, let count), .perpetual(_, let count): count
        default: nil
        }
    }

    var expiresAt: Date? {
        if case .active(_, let date, _, _) = self { return date }
        if case .expired = self { return nil }
        return nil
    }

    var title: String {
        switch self {
        case .loading: "Comprobando licencia…"
        case .trial(let days): "Prueba gratuita · \(days) \(days == 1 ? "día" : "días")"
        case .trialExpired: "Periodo de prueba vencido"
        case .active(let type, let date, _, _): "Licencia \(type.title) · vence el \(date.shalevDate)"
        case .perpetual: "Licencia perpetua"
        case .expired(let type): "Licencia \(type.title) vencida"
        case .revoked: "Licencia no válida"
        case .clockTampered: "Revisa la fecha y hora del dispositivo"
        case .unavailable: "No se pudo comprobar la licencia"
        }
    }
}

struct GLLicenseProfile: Codable, Equatable {
    var firstName = ""
    var lastName = ""
    var identification = ""
    var phone = ""
}

private struct GLVault: Codable {
    var licenseCode: String? = nil
    var licenseID: String? = nil
    var issuedAt: Date? = nil
    var type: GLLicenseType? = nil
    var expiresAt: Date? = nil
    var secondaries: Int? = nil
    var trialStart: Date? = nil
    var lastSeen: Date? = nil
    var lastRevocationCheck: Date? = nil
    var migratedAt: Date? = nil
    var revokedAt: Date? = nil
    var profile = GLLicenseProfile()
}

private enum GLKeychain {
    private static var service: String { Bundle.main.bundleIdentifier ?? "cu.shalev.ishalev" }
    static let deviceKeyAccount = "spvi-device-p256-v1"
    static let deviceIDAccount = "spvi-device-id-v1"
    static let vaultAccount = "spvi-license-v1"

    static func read(_ account: String) throws -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw GLKeychainError(status) }
        return data
    }

    static func write(_ data: Data, account: String) throws {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
        var attributes = query
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        attributes[kSecValueData as String] = data
        let status = SecItemAdd(attributes as CFDictionary, nil)
        guard status == errSecSuccess else { throw GLKeychainError(status) }
    }

    private struct GLKeychainError: Error { let status: OSStatus; init(_ status: OSStatus) { self.status = status } }
}

private final class GLDeviceIdentity {
    let privateKey: P256.KeyAgreement.PrivateKey
    let deviceID: String

    init() throws {
        if let stored = try GLKeychain.read(GLKeychain.deviceKeyAccount) {
            privateKey = try P256.KeyAgreement.PrivateKey(rawRepresentation: stored)
        } else {
            let generated = P256.KeyAgreement.PrivateKey()
            try GLKeychain.write(generated.rawRepresentation, account: GLKeychain.deviceKeyAccount)
            privateKey = generated
        }
        if let storedID = try GLKeychain.read(GLKeychain.deviceIDAccount), let id = String(data: storedID, encoding: .utf8), !id.isEmpty {
            deviceID = id
        } else {
            let id = "SPVI:" + UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
            try GLKeychain.write(Data(id.utf8), account: GLKeychain.deviceIDAccount)
            deviceID = id
        }
    }

    var compressedPublicKey: Data { P256CompressedPoint.compress(privateKey.publicKey.rawRepresentation) }
    func sharedSecret(with peer: P256.KeyAgreement.PublicKey) throws -> Data {
        let secret = try privateKey.sharedSecretFromKeyAgreement(with: peer)
        return secret.withUnsafeBytes { Data($0) }
    }
}

private enum GLVaultStore {
    static func load() -> GLVault {
        guard let data = try? GLKeychain.read(GLKeychain.vaultAccount),
              let vault = try? JSONDecoder().decode(GLVault.self, from: data) else { return GLVault() }
        return vault
    }
    static func save(_ vault: GLVault) throws {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        try GLKeychain.write(encoder.encode(vault), account: GLKeychain.vaultAccount)
    }
}

private struct GLRequestPayload: Encodable {
    var v = 2
    var nombre: String
    var apellidos: String
    var ci: String
    var via: GLRequestChannel
    var telefono: String
    var deviceId: String
    var tipo: GLLicenseType
    var solicitadaEn: String
    var nonce: String
    var devicePub: String
    var secundarias: Int
    var renueva: String?
    var recupera: String?

    enum CodingKeys: String, CodingKey {
        case v, nombre, apellidos, ci, via, telefono, deviceId, tipo, solicitadaEn, nonce, devicePub, secundarias, renueva, recupera
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(v, forKey: .v)
        try container.encode(nombre, forKey: .nombre)
        try container.encode(apellidos, forKey: .apellidos)
        try container.encode(ci, forKey: .ci)
        try container.encode(via, forKey: .via)
        try container.encode(telefono, forKey: .telefono)
        try container.encode(deviceId, forKey: .deviceId)
        try container.encode(tipo, forKey: .tipo)
        try container.encode(solicitadaEn, forKey: .solicitadaEn)
        try container.encode(nonce, forKey: .nonce)
        try container.encode(devicePub, forKey: .devicePub)
        try container.encode(secundarias, forKey: .secundarias)
        try container.encodeIfPresent(renueva, forKey: .renueva)
        try container.encodeIfPresent(recupera, forKey: .recupera)
    }
}

// MARK: - Motor de licencias

@MainActor
final class GLLicenseManager: ObservableObject {
    @Published private(set) var state: GLLicenseState = .loading
    @Published var requestProfile = GLLicenseProfile()
    @Published private(set) var lastError: String?

    private var identity: GLDeviceIdentity?
    private var vault = GLVaultStore.load()
    private var isCheckingRevocations = false
    private(set) var configurationError: Bool

    var canValidate: Bool { GLTrust.canValidate }
    var canRequest: Bool { GLTrust.canRequest && identity != nil }
    var deviceID: String { identity?.deviceID ?? "SPVI:0000000000000000" }
    var savedLicenseID: String? { verifiedInstalled?.id }
    var installedType: GLLicenseType? { verifiedInstalled?.type }
    var installedSecondaries: Int? { verifiedInstalled?.secondaries }
    var renewableLicenseID: String? {
        guard let verified = verifiedInstalled, verified.expiresAt != nil else { return nil }
        return verified.id
    }
    var canRecover: Bool {
        guard verifiedInstalled != nil else { return true }
        if case .expired = state { return true }
        return false
    }
    private var verifiedInstalled: VerifiedLicense? {
        guard let identity, let code = vault.licenseCode,
              let verified = verify(code: code, identity: identity),
              vault.licenseID == verified.id, vault.issuedAt == verified.issuedAt,
              vault.type == verified.type, vault.expiresAt == verified.expiresAt,
              vault.secondaries == verified.secondaries,
              vault.migratedAt == nil || verified.issuedAt > (vault.migratedAt ?? .distantPast) else { return nil }
        return verified
    }

    init() {
        do {
            identity = try GLDeviceIdentity()
            configurationError = false
            requestProfile = vault.profile
        } catch {
            configurationError = true
            lastError = "No se pudo inicializar la identidad protegida del dispositivo."
        }
    }

    func refresh(now: Date = Date()) async {
        guard let identity else { state = .unavailable; return }
        vault = GLVaultStore.load()
        if let last = vault.lastSeen, now < last.addingTimeInterval(-2 * 60 * 60) {
            state = .clockTampered
            return
        }
        if vault.lastSeen == nil || now > vault.lastSeen! {
            vault.lastSeen = now
            try? GLVaultStore.save(vault)
        }

        if let code = vault.licenseCode,
           let verified = verify(code: code, identity: identity),
           vault.licenseID == verified.id,
           vault.issuedAt == verified.issuedAt,
           vault.type == verified.type,
           vault.expiresAt == verified.expiresAt,
           vault.secondaries == verified.secondaries,
           (vault.migratedAt == nil || verified.issuedAt > (vault.migratedAt ?? .distantPast)) {
            state = verified.state(at: now)
            return
        }
        if vault.migratedAt != nil {
            state = vault.revokedAt == nil ? .trialExpired : .revoked
            return
        }
        if vault.trialStart == nil {
            vault.trialStart = now
            try? GLVaultStore.save(vault)
        }
        let days = Int(floor(max(0, now.timeIntervalSince(vault.trialStart!) / 86_400)))
        let remaining = 7 - days
        state = remaining > 0 ? .trial(daysLeft: remaining) : .trialExpired
    }

    func buildRequest(type: GLLicenseType, secondaries: Int, channel: GLRequestChannel, recoveryID: String? = nil) throws -> String {
        guard let identity, let glPublic = GLTrust.ecdhPublicKey, GLTrust.canValidate else {
            throw GLLicenseError.configuration
        }
        guard (0...10).contains(secondaries), validName(requestProfile.firstName), validName(requestProfile.lastName),
              validCI(requestProfile.identification), let phone = normalizePhone(requestProfile.phone) else {
            throw GLLicenseError.invalidRequest
        }
        let recovery = recoveryID?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let recovery, UUID(uuidString: recovery) == nil { throw GLLicenseError.invalidRequest }
        let nonce = Base64URL.encode(try secureRandom(16))
        let now = Date()
        let payload = GLRequestPayload(
            nombre: requestProfile.firstName.trimmingCharacters(in: .whitespacesAndNewlines),
            apellidos: requestProfile.lastName.trimmingCharacters(in: .whitespacesAndNewlines),
            ci: requestProfile.identification.trimmingCharacters(in: .whitespacesAndNewlines).uppercased(),
            via: channel,
            telefono: phone,
            deviceId: identity.deviceID,
            tipo: type,
            solicitadaEn: iso8601Seconds(now),
            nonce: nonce,
            devicePub: Base64URL.encode(identity.compressedPublicKey),
            secundarias: secondaries,
            renueva: recovery == nil ? renewableLicenseID : nil,
            recupera: recovery?.lowercased()
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = []
        let plain = try encoder.encode(payload)
        let ephemeral = P256.KeyAgreement.PrivateKey()
        let epk = P256CompressedPoint.compress(ephemeral.publicKey.rawRepresentation)
        let shared = try ephemeral.sharedSecretFromKeyAgreement(with: glPublic).withUnsafeBytes { Data($0) }
        let (key, iv) = derive(shared, salt: epk, info: Data("SPVI-R1".utf8))
        let nonceValue = try AES.GCM.Nonce(data: iv)
        let sealed = try AES.GCM.seal(plain, using: key, nonce: nonceValue, authenticating: Data("SPVI-R1|".utf8) + epk)
        let code = "SPVIR1:" + Base64URL.encode(epk + sealed.ciphertext + sealed.tag)
        return requestMessage(payload: payload, code: code)
    }

    func activate(message: String, now: Date = Date()) async -> GLActivationResult {
        guard let identity, GLTrust.canValidate else { return .rejected }
        guard let code = extractShortLicense(message) else { return .notFound }
        guard let verified = verify(code: code, identity: identity) else { return .rejected }
        if let currentIssued = vault.issuedAt, verified.issuedAt < currentIssued { return .outdated }
        if let migrated = vault.migratedAt, verified.issuedAt <= migrated { return .outdated }

        vault.licenseCode = code
        vault.licenseID = verified.id
        vault.issuedAt = verified.issuedAt
        vault.type = verified.type
        vault.expiresAt = verified.expiresAt
        vault.secondaries = verified.secondaries
        vault.revokedAt = nil
        vault.migratedAt = nil
        vault.lastSeen = max(vault.lastSeen ?? now, now)
        try? GLVaultStore.save(vault)
        state = verified.state(at: now)
        return .accepted
    }

    func saveRequestProfile() {
        vault.profile = requestProfile
        try? GLVaultStore.save(vault)
    }

    func applyRevocationList(_ data: Data, now: Date = Date()) {
        guard let id = vault.licenseID, isRevoked(id: id, in: data) else { return }
        vault.revokedAt = now
        vault.migratedAt = now
        vault.licenseCode = nil
        try? GLVaultStore.save(vault)
        state = .revoked
    }

    /// Descarga semanal la lista pública firmada por GL; solo se envía una petición idéntica para todos los teléfonos.
    /// Sin red, 404, formato inválido o firma desconocida, se falla abierto igual que el cliente Android.
    func checkRevocationsIfDue(now: Date = Date()) async {
        guard !isCheckingRevocations, vault.licenseID != nil else { return }
        if let last = vault.lastRevocationCheck, now.timeIntervalSince(last) < 7 * 86_400 { return }
        guard let url = URL(string: "https://github.com/rmdvcd/Shalev/releases/download/revocaciones/revocadas.json") else { return }
        isCheckingRevocations = true
        defer { isCheckingRevocations = false }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 12
        configuration.timeoutIntervalForResource = 16
        configuration.waitsForConnectivity = false
        do {
            let (data, response) = try await URLSession(configuration: configuration).data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200, data.count <= 512 * 1024 else { return }
            applyRevocationList(data, now: now)
            vault.lastRevocationCheck = now
            try? GLVaultStore.save(vault)
        } catch {
            // La ausencia de conexión no debe bloquear ventas ni desactivar licencias válidas.
        }
    }

    private func verify(code: String, identity: GLDeviceIdentity) -> VerifiedLicense? {
        guard code.hasPrefix("SPVI2:"),
              let binary = Base64URL.decode(String(code.dropFirst("SPVI2:".count))), binary.count == 157 else { return nil }
        let epk = Data(binary.prefix(33))
        let ciphertextAndTag = Data(binary[33..<93])
        let rawSignature = Data(binary[93..<157])
        let signed = Data("SPVI-L2|".utf8) + epk + ciphertextAndTag
        guard let derSignature = SignatureFormat.der(fromRaw: rawSignature),
              let signature = try? P256.Signing.ECDSASignature(derRepresentation: derSignature),
              GLTrust.signingPublicKeys.contains(where: { $0.isValidSignature(signature, for: signed) }) else {
            return nil
        }
        guard ciphertextAndTag.count >= 16,
              let uncompressed = P256CompressedPoint.uncompress(epk),
              let peer = try? P256.KeyAgreement.PublicKey(x963Representation: uncompressed),
              let shared = try? identity.sharedSecret(with: peer) else { return nil }
        let (key, iv) = derive(shared, salt: epk, info: Data("SPVI-L2".utf8))
        let ciphertext = Data(ciphertextAndTag.dropLast(16))
        let tag = Data(ciphertextAndTag.suffix(16))
        guard let nonce = try? AES.GCM.Nonce(data: iv),
              let box = try? AES.GCM.SealedBox(nonce: nonce, ciphertext: ciphertext, tag: tag),
              let clear = try? AES.GCM.open(box, using: key, authenticating: Data("SPVI-L2|".utf8) + epk),
              let body = parseLicenseBody(clear, identity: identity) else { return nil }
        return body
    }

    private func parseLicenseBody(_ body: Data, identity: GLDeviceIdentity) -> VerifiedLicense? {
        guard body.count == 44, body[0] == 2 else { return nil }
        let expectedFingerprint = Data(Data(SHA256.hash(data: Data("SPVI-L2|\(identity.deviceID)|".utf8) + identity.compressedPublicKey)).prefix(16))
        let idBytes = Data(body[1..<17])
        let fingerprint = Data(body[17..<33])
        guard constantTimeEqual(fingerprint, expectedFingerprint),
              let id = uuidString(from: idBytes),
              let type = GLLicenseType.contractValue(body[33]),
              body[35] <= 10 else { return nil }
        let stateByte = body[34]
        let issuedSeconds = uint32(Data(body[36..<40]))
        let expiresSeconds = uint32(Data(body[40..<44]))
        guard issuedSeconds > 0 else { return nil }
        let issued = Date(timeIntervalSince1970: TimeInterval(issuedSeconds))
        let expiry = expiresSeconds == 0 ? nil : Date(timeIntervalSince1970: TimeInterval(expiresSeconds))
        if stateByte == 3 {
            guard type == .perpetual, expiry == nil else { return nil }
        } else if stateByte == 0 {
            if type == .perpetual {
                guard expiry == nil else { return nil }
            } else {
                guard let expiry, expiry > issued else { return nil }
            }
        } else if stateByte > 3 {
            return nil
        }
        return VerifiedLicense(id: id, type: type, secondaries: Int(body[35]), issuedAt: issued, expiresAt: expiry, stateCode: stateByte)
    }

    private func requestMessage(payload: GLRequestPayload, code: String) -> String {
        var lines = [
            payload.recupera == nil ? "Solicitud de licencia Shalev" : "Recuperar licencia Shalev",
            "Nombre: \(payload.nombre) \(payload.apellidos)",
            "Carné de identidad: \(payload.ci)",
            "Teléfono: \(payload.telefono)"
        ]
        if let recovery = payload.recupera {
            lines.append("Licencia anterior: \(recovery)")
        } else {
            lines.append("Tipo: \(payload.tipo.title)")
            lines.append("Apps secundarias: \(payload.secundarias)")
            lines.append("Precio: \(Cup(cents: payload.tipo.totalPriceCents(secondaries: payload.secundarias)).formatted)")
            if let renewal = payload.renueva { lines.append("Renueva: \(renewal)") }
        }
        lines.append("Vale 72 horas: envíala y paga cuanto antes; si pasa ese tiempo, genera otra solicitud.")
        return lines.joined(separator: "\n") + "\n\n" + code
    }
}

private struct VerifiedLicense {
    let id: String
    let type: GLLicenseType
    let secondaries: Int
    let issuedAt: Date
    let expiresAt: Date?
    let stateCode: UInt8

    func state(at now: Date) -> GLLicenseState {
        switch stateCode {
        case 1: return .expired(type: type)
        case 2: return .revoked
        case 3: return .perpetual(id: id, secondaries: secondaries)
        default:
            if type == .perpetual { return .perpetual(id: id, secondaries: secondaries) }
            guard let expiresAt else { return .unavailable }
            return now < expiresAt ? .active(type: type, expiresAt: expiresAt, id: id, secondaries: secondaries) : .expired(type: type)
        }
    }
}

private enum GLLicenseError: Error { case configuration, invalidRequest }

private extension GLLicenseType {
    static func contractValue(_ byte: UInt8) -> GLLicenseType? {
        switch byte {
        case 0: .monthly
        case 1: .semiannual
        case 2: .annual
        case 3: .perpetual
        default: nil
        }
    }
}

private enum Base64URL {
    static func encode(_ data: Data) -> String {
        data.base64EncodedString().replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }
    static func decode(_ value: String) -> Data? {
        var base = value.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        let remainder = base.count % 4
        if remainder == 1 { return nil }
        if remainder > 0 { base += String(repeating: "=", count: 4 - remainder) }
        return Data(base64Encoded: base)
    }
}

private func derive(_ shared: Data, salt: Data, info: Data) -> (SymmetricKey, Data) {
    let material = HKDF<SHA256>.deriveKey(inputKeyMaterial: SymmetricKey(data: shared), salt: salt, info: info, outputByteCount: 44)
    let bytes = material.withUnsafeBytes { Data($0) }
    return (SymmetricKey(data: Data(bytes.prefix(32))), Data(bytes.suffix(12)))
}

private func iso8601Seconds(_ date: Date) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime]
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return formatter.string(from: date)
}

func validName(_ source: String) -> Bool {
    let value = source.trimmingCharacters(in: .whitespacesAndNewlines)
    guard value.count >= 2, value.count <= 80,
          let first = value.first, let last = value.last,
          first.isLetter, last.isLetter else { return false }
    return value.allSatisfy { $0.isLetter || $0 == " " || $0 == "'" || $0 == "-" }
}

func validCI(_ source: String) -> Bool {
    let value = source.trimmingCharacters(in: .whitespacesAndNewlines)
    return (5...20).contains(value.count) && value.unicodeScalars.allSatisfy {
        (65...90).contains(Int($0.value)) || (97...122).contains(Int($0.value)) || (48...57).contains(Int($0.value))
    }
}

func normalizePhone(_ source: String) -> String? {
    let trimmed = source.trimmingCharacters(in: .whitespacesAndNewlines)
    let digits = String(trimmed.filter { $0 >= "0" && $0 <= "9" })
    let value: String
    if trimmed.hasPrefix("+") { value = "+" + digits }
    else if trimmed.hasPrefix("00"), digits.count > 2 { value = "+" + String(digits.dropFirst(2)) }
    else if digits.count == 8 { value = "+53" + digits }
    else if digits.count == 10, digits.hasPrefix("53") { value = "+" + digits }
    else if (11...15).contains(digits.count) { value = "+" + digits }
    else { return nil }
    let content = value.dropFirst()
    return value.hasPrefix("+") && (8...15).contains(content.count) && content.allSatisfy(\.isNumber) ? value : nil
}

private func secureRandom(_ count: Int) throws -> Data {
    var data = Data(count: count)
    let status = data.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, count, $0.baseAddress!) }
    guard status == errSecSuccess else { throw GLLicenseError.configuration }
    return data
}

private func extractShortLicense(_ text: String) -> String? {
    let prefix = "SPVI2:"
    var searchRange = text.startIndex..<text.endIndex
    while let found = text.range(of: prefix, range: searchRange) {
        var code = ""
        var index = found.upperBound
        while index < text.endIndex && code.count < 210 {
            let character = text[index]
            if character.isASCII && (character.isLetter || character.isNumber || character == "-" || character == "_") {
                code.append(character)
            } else if character.isWhitespace {
                // Las apps de mensajería pueden insertar espacios o saltos en el Base64url.
            } else {
                break
            }
            index = text.index(after: index)
        }
        if code.count == 210, Base64URL.decode(code)?.count == 157 { return prefix + code }
        searchRange = found.upperBound..<text.endIndex
    }
    return nil
}

private func constantTimeEqual(_ lhs: Data, _ rhs: Data) -> Bool {
    guard lhs.count == rhs.count else { return false }
    var difference: UInt8 = 0
    for (a, b) in zip(lhs, rhs) { difference |= a ^ b }
    return difference == 0
}

private func uuidString(from data: Data) -> String? {
    guard data.count == 16 else { return nil }
    let hex = data.hexLowercase
    let parts = [String(hex.prefix(8)), String(hex.dropFirst(8).prefix(4)), String(hex.dropFirst(12).prefix(4)),
                 String(hex.dropFirst(16).prefix(4)), String(hex.suffix(12))]
    return parts.joined(separator: "-")
}

private func uint32(_ data: Data) -> UInt32 {
    data.reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
}

private func isRevoked(id: String, in data: Data) -> Bool {
    guard data.count <= 512 * 1024,
          let envelope = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let payload = envelope["datos"] as? String,
          let signatureText = envelope["firma"] as? String,
          let signatureRaw = Base64URL.decode(signatureText.trimmingCharacters(in: .whitespacesAndNewlines)),
          let signatureDER = SignatureFormat.der(fromRaw: signatureRaw),
          let signature = try? P256.Signing.ECDSASignature(derRepresentation: signatureDER) else { return false }
    let signed = Data("SPVI-REV1|\(payload)".utf8)
    guard GLTrust.signingPublicKeys.contains(where: { $0.isValidSignature(signature, for: signed) }),
          let inner = payload.data(using: .utf8),
          let values = try? JSONSerialization.jsonObject(with: inner) as? [String: Any],
          (values["v"] as? Int) == 1,
          let revoked = values["revocadas"] as? [String] else { return false }
    let fingerprint = Data(SHA256.hash(data: Data("SPVI-REV|\(id.lowercased())".utf8))).prefix(16).hexLowercase
    return revoked.contains(fingerprint)
}

private enum SignatureFormat {
    static func der(fromRaw raw: Data) -> Data? {
        guard raw.count == 64 else { return nil }
        func integer(_ source: Data) -> Data {
            var bytes = Array(source)
            while bytes.count > 1 && bytes.first == 0 { bytes.removeFirst() }
            if bytes[0] & 0x80 != 0 { bytes.insert(0, at: 0) }
            return Data([0x02, UInt8(bytes.count)]) + Data(bytes)
        }
        let r = integer(Data(raw.prefix(32)))
        let s = integer(Data(raw.suffix(32)))
        let body = r + s
        return Data([0x30, UInt8(body.count)]) + body
    }
}

private extension Data {
    subscript(safe range: Range<Int>) -> Data? { range.lowerBound >= 0 && range.upperBound <= count ? Data(self[range]) : nil }
}

private func P256CompressedPoint_dummy() {}

/// P-256 SEC1 compressed-point conversion. CryptoKit exposes X9.63 uncompressed points; GL's compact
/// `SPVIR1`/`SPVI2` contract uses 33-byte compressed SEC1 points. The finite-field code only handles decoding.
private enum P256CompressedPoint {
    static func compress(_ x963: Data) -> Data {
        guard x963.count == 65, x963[0] == 0x04 else { return Data() }
        let x = Data(x963[1..<33])
        let y = Data(x963[33..<65])
        return Data([0x02 | (y.last! & 1)]) + x
    }

    static func uncompress(_ compressed: Data) -> Data? {
        guard compressed.count == 33, compressed[0] == 2 || compressed[0] == 3,
              let x = Field256(bigEndian: Data(compressed.dropFirst())), x < .prime else { return nil }
        let x2 = Field256.multiplyMod(x, x)
        let x3 = Field256.multiplyMod(x2, x)
        let threeX = Field256.addMod(Field256.addMod(x, x), x)
        let rhs = Field256.addMod(Field256.subtractMod(x3, threeX), .curveB)
        var y = Field256.powerMod(rhs, .sqrtExponent)
        guard Field256.multiplyMod(y, y) == rhs else { return nil }
        if (y.words[0] & 1) != UInt32(compressed[0] & 1) { y = Field256.subtractMod(.prime, y) }
        return Data([0x04]) + x.bigEndianData + y.bigEndianData
    }
}

private struct Field256: Equatable, Comparable {
    var words: [UInt32] // little endian, eight 32-bit limbs
    private init(words: [UInt32]) { self.words = words }

    init?(bigEndian data: Data) {
        guard data.count == 32 else { return nil }
        var words = Array(repeating: UInt32(0), count: 8)
        for (index, byte) in data.enumerated() {
            let reverse = 31 - index
            words[reverse / 4] |= UInt32(byte) << UInt32((reverse % 4) * 8)
        }
        self.words = words
    }

    init(hex: String) {
        var source = hex
        if source.count % 2 == 1 { source = "0" + source }
        let bytes = stride(from: 0, to: source.count, by: 2).compactMap { offset -> UInt8? in
            let start = source.index(source.startIndex, offsetBy: offset)
            let end = source.index(start, offsetBy: 2)
            return UInt8(source[start..<end], radix: 16)
        }
        self = Field256(bigEndian: Data(bytes)) ?? Field256(words: Array(repeating: 0, count: 8))
    }

    static let prime = Field256(hex: "FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF")
    static let curveB = Field256(hex: "5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B")
    static let sqrtExponent = Field256(hex: "3FFFFFFFC0000000400000000000000000000000400000000000000000000000")
    static let one = Field256(words: [1, 0, 0, 0, 0, 0, 0, 0])

    var bigEndianData: Data {
        var bytes = [UInt8](repeating: 0, count: 32)
        for index in 0..<32 {
            let reverse = 31 - index
            bytes[index] = UInt8(truncatingIfNeeded: words[reverse / 4] >> UInt32((reverse % 4) * 8))
        }
        return Data(bytes)
    }

    static func < (lhs: Field256, rhs: Field256) -> Bool {
        for index in stride(from: 7, through: 0, by: -1) {
            if lhs.words[index] != rhs.words[index] { return lhs.words[index] < rhs.words[index] }
        }
        return false
    }

    private static func subtract(_ lhs: Field256, _ rhs: Field256) -> Field256 {
        var out = Array(repeating: UInt32(0), count: 8)
        var borrow: Int64 = 0
        let radix = Int64(1) << 32
        for index in 0..<8 {
            var value = Int64(lhs.words[index]) - Int64(rhs.words[index]) - borrow
            if value < 0 { value += radix; borrow = 1 } else { borrow = 0 }
            out[index] = UInt32(value)
        }
        return Field256(words: out)
    }

    private static func addNoOverflow(_ lhs: Field256, _ rhs: Field256) -> Field256 {
        var out = Array(repeating: UInt32(0), count: 8)
        var carry: UInt64 = 0
        for index in 0..<8 {
            let sum = UInt64(lhs.words[index]) + UInt64(rhs.words[index]) + carry
            out[index] = UInt32(truncatingIfNeeded: sum)
            carry = sum >> 32
        }
        return Field256(words: out)
    }

    static func addMod(_ lhs: Field256, _ rhs: Field256) -> Field256 {
        let threshold = subtract(prime, rhs)
        return lhs >= threshold ? subtract(lhs, threshold) : addNoOverflow(lhs, rhs)
    }

    static func subtractMod(_ lhs: Field256, _ rhs: Field256) -> Field256 {
        lhs >= rhs ? subtract(lhs, rhs) : subtract(prime, subtract(rhs, lhs))
    }

    static func multiplyMod(_ lhs: Field256, _ rhs: Field256) -> Field256 {
        var result = Field256(words: Array(repeating: 0, count: 8))
        var addend = lhs
        for bit in 0..<256 {
            if (rhs.words[bit / 32] & (UInt32(1) << UInt32(bit % 32))) != 0 { result = addMod(result, addend) }
            addend = addMod(addend, addend)
        }
        return result
    }

    static func powerMod(_ base: Field256, _ exponent: Field256) -> Field256 {
        var result = one
        var factor = base
        for bit in 0..<256 {
            if (exponent.words[bit / 32] & (UInt32(1) << UInt32(bit % 32))) != 0 { result = multiplyMod(result, factor) }
            factor = multiplyMod(factor, factor)
        }
        return result
    }
}
