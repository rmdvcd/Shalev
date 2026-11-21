import Foundation
import CryptoKit

enum RedeemError: LocalizedError, Equatable {
    case disabled
    case invalidCode
    case clockSkew(Date)
    case wrongDevice
    case wrongApp
    case invalidSignature
    case serverError(code: String, message: String)
    case network(String)

    var errorDescription: String? {
        switch self {
        case .disabled: "La activación comercial directa no está configurada en este build."
        case .invalidCode: "El código debe tener 16 caracteres alfanuméricos válidos."
        case .clockSkew: "El reloj del dispositivo no coincide con el servidor de canje."
        case .wrongDevice: "La licencia canjeada es para otro dispositivo."
        case .wrongApp: "La licencia canjeada es para otra aplicación."
        case .invalidSignature: "La firma de la licencia no pudo ser verificada."
        case .serverError(let code, let msg): "[\(code)] \(msg)"
        case .network(let detail): "Error de conexión: \(detail)"
        }
    }
}

final class RedeemClient {
    let appID: String
    let endpointRedeem: URL?
    let endpointStatus: URL?

    init(
        appID: String = RedeemContract.defaultAppID,
        endpointRedeem: URL? = nil,
        endpointStatus: URL? = nil
    ) {
        self.appID = appID
        self.endpointRedeem = endpointRedeem
        self.endpointStatus = endpointStatus
    }

    var canRedeem: Bool {
        endpointRedeem != nil && endpointRedeem?.scheme == "https" && !appID.isEmpty
    }

    func redeem(code: String, identity: GLIdentity, manager: GLLicenseManager, now: Date = Date()) async throws -> GLActivationResult {
        guard canRedeem, let url = endpointRedeem else {
            throw RedeemError.disabled
        }
        guard let codeNorm = RedeemContract.normalizeCode(code) else {
            throw RedeemError.invalidCode
        }

        let requestID = UUID().uuidString
        let requestedAt = ISO8601DateFormatter().string(from: now)
        let nonce = (0..<16).map { _ in UInt8.random(in: 0...255) }
        let nonceB64 = Data(nonce).base64EncodedString()
        let deviceKeyB64 = identity.signingPublicKeyDER.base64EncodedString()

        let proofBytes = RedeemContract.redeemProofBytes(
            appID: appID,
            code: codeNorm,
            deviceKeyBase64: deviceKeyB64,
            requestID: requestID,
            requestedAt: requestedAt,
            nonceBase64: nonceB64
        )
        let signature = try identity.signingKey.signature(for: proofBytes).derRepresentation.base64EncodedString()

        let payload: [String: Any] = [
            "v": 1,
            "appId": appID,
            "code": codeNorm,
            "deviceKey": deviceKeyB64,
            "requestId": requestID,
            "requestedAt": requestedAt,
            "nonce": nonceB64,
            "proof": signature
        ]

        var request = URLRequest(url: url, timeoutInterval: 10.0)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (data, response): (Data, URLResponse)
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            throw RedeemError.network(error.localizedDescription)
        }

        let http = response as? HTTPURLResponse
        let statusCode = http?.statusCode ?? 500

        if statusCode == 400 {
            if let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
               let err = json["error"] as? [String: Any],
               (err["code"] as? String) == "CLOCK_SKEW",
               let stRaw = (err["serverTime"] as? String) ?? (json["serverTime"] as? String),
               let serverTime = ISO8601DateFormatter().date(from: stRaw) {
                manager.recoverClock(serverTime: serverTime)
                throw RedeemError.clockSkew(serverTime)
            }
        }

        guard statusCode == 200 else {
            if let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
               let err = json["error"] as? [String: Any] {
                let code = (err["code"] as? String) ?? "HTTP_\(statusCode)"
                let msg = (err["message"] as? String) ?? "Error en el canje"
                throw RedeemError.serverError(code: code, message: msg)
            }
            throw RedeemError.serverError(code: "HTTP_\(statusCode)", message: "Error \(statusCode)")
        }

        guard let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let licenseObj = json["license"] as? [String: Any],
              let lic = licenseObj["license"] as? [String: Any],
              let sigB64 = licenseObj["sig"] as? String,
              let sigDER = Data(base64Encoded: sigB64) else {
            throw RedeemError.serverError(code: "INVALID_RESPONSE", message: "Respuesta ilegible")
        }

        // Verificar deviceThumbprint
        let thumbprint = (lic["deviceThumbprint"] as? String) ?? ""
        let expectedThumbprint = RedeemContract.deviceThumbprint(identity.signingPublicKeyDER)
        guard thumbprint == expectedThumbprint else {
            throw RedeemError.wrongDevice
        }

        // Verificar appId
        let licApp = (lic["appId"] as? String) ?? ""
        guard licApp == appID else {
            throw RedeemError.wrongApp
        }

        let v = (lic["v"] as? Int) ?? 1
        let licenseID = (lic["licenseId"] as? String) ?? ""
        let typeStr = (lic["type"] as? String) ?? ""
        let statusStr = (lic["status"] as? String) ?? ""
        let issuedAtStr = (lic["issuedAt"] as? String) ?? ""
        let expiresAtStr = lic["expiresAt"] as? String
        let redemptionID = (lic["redemptionId"] as? String) ?? ""
        let transferredFrom = lic["transferredFrom"] as? String
        let secondaries = (lic["secundarias"] as? Int) ?? 0
        let devicePublicKey = lic["devicePublicKey"] as? String

        let signingBytes = RedeemContract.licenseSigningBytes(
            v: v,
            licenseID: licenseID,
            appID: licApp,
            type: typeStr,
            status: statusStr,
            issuedAt: issuedAtStr,
            expiresAt: expiresAtStr,
            deviceThumbprint: thumbprint,
            redemptionID: redemptionID,
            transferredFrom: transferredFrom,
            secondaries: secondaries,
            devicePublicKey: devicePublicKey
        )

        let issuedDate = ISO8601DateFormatter().date(from: issuedAtStr) ?? now
        let signingKeys = TrustDirectory.effectiveDirectory?.signKeys(at: issuedDate) ?? GLTrust.signingPublicKeys

        guard let ecdsaSignature = try? P256.Signing.ECDSASignature(derRepresentation: sigDER),
              signingKeys.contains(where: { $0.isValidSignature(ecdsaSignature, for: signingBytes) }) else {
            throw RedeemError.invalidSignature
        }

        // Instalar o absorber licencia
        let expiresDate = expiresAtStr.flatMap { ISO8601DateFormatter().date(from: $0) }
        let licType: GLLicenseType = typeStr == "PERPETUA" ? .perpetual : (typeStr == "ANUAL" ? .annual : .monthly)

        // S3: Renovación dominada
        if let currentID = manager.vault.licenseID, manager.vault.revokedAt == nil {
            if manager.vault.type == .perpetual {
                return .absorbed(message: "Tu licencia ya cubría este periodo de forma perpetua. La nueva clave ha sido registrada como absorbida.")
            }
            if let currentExpires = manager.vault.expiresAt, let newExpires = expiresDate, newExpires <= currentExpires {
                let dateStr = ISO8601DateFormatter().string(from: currentExpires)
                return .absorbed(message: "Tu licencia ya cubría este periodo hasta \(dateStr). La nueva clave ha sido registrada como absorbida.")
            }
        }

        manager.vault.licenseID = licenseID
        manager.vault.type = licType
        manager.vault.issuedAt = issuedDate
        manager.vault.expiresAt = expiresDate
        manager.vault.secondaries = secondaries
        manager.vault.revokedAt = nil
        manager.vault.migratedAt = nil
        manager.vault.lastSeen = max(manager.vault.lastSeen ?? now, now)
        try? GLVaultStore.save(manager.vault)

        if licType == .perpetual {
            manager.state = .perpetual(id: licenseID, secondaries: secondaries)
        } else if let expiresDate {
            let days = max(0, Int(expiresDate.timeIntervalSince(now) / 86400))
            manager.state = .active(id: licenseID, daysLeft: days, type: licType, secondaries: secondaries)
        }
        return .accepted
    }
}
