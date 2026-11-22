import Foundation
import CryptoKit

enum GLDateParser {
    private static let withFraction: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private static let standard: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    static func parse(_ string: String) -> Date? {
        withFraction.date(from: string) ?? standard.date(from: string)
    }

    static func formatUTC(_ date: Date) -> String {
        withFraction.string(from: date)
    }
}

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
        case .disabled: RedeemContract.motivoAunNoDisponible
        case .invalidCode: "El código debe tener 16 caracteres alfanuméricos válidos."
        case .clockSkew: "El reloj del dispositivo no coincide con el servidor de canje."
        case .wrongDevice: "La licencia canjeada es para otro dispositivo."
        case .wrongApp: RedeemContract.mensajeCodigoInvalidoOUsado
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

    var canCheckStatus: Bool {
        endpointStatus != nil && endpointStatus?.scheme == "https" && !appID.isEmpty
    }

    func redeem(code: String, identity: GLIdentity, manager: GLLicenseManager, now: Date = Date()) async throws -> GLActivationResult {
        guard canRedeem, let url = endpointRedeem else {
            throw RedeemError.disabled
        }
        guard let codeNorm = RedeemContract.normalizeCode(code) else {
            throw RedeemError.invalidCode
        }

        let requestID = UUID().uuidString
        var requestedAt = GLDateParser.formatUTC(now)
        var nonce = (0..<16).map { _ in UInt8.random(in: 0...255) }
        var nonceB64 = Data(nonce).base64EncodedString()
        let deviceKeyB64 = identity.signingPublicKeyDER.base64EncodedString()

        var proofBytes = RedeemContract.redeemProofBytes(
            appID: appID,
            code: codeNorm,
            deviceKeyBase64: deviceKeyB64,
            requestID: requestID,
            requestedAt: requestedAt,
            nonceBase64: nonceB64
        )
        var signature = try identity.signingKey.signature(for: proofBytes).derRepresentation.base64EncodedString()

        var payload: [String: Any] = [
            "v": 1,
            "appId": appID,
            "code": codeNorm,
            "deviceKey": deviceKeyB64,
            "requestId": requestID,
            "requestedAt": requestedAt,
            "nonce": nonceB64,
            "proof": signature
        ]

        var (data, statusCode) = try await sendPost(url: url, payload: payload)

        // Reintento automático ante CLOCK_SKEW (máximo 1 reintento con hora del servidor)
        if statusCode == 400 {
            if let serverTime = parseServerTime(from: data) ?? (await fetchTime(from: url)) {
                manager.recoverClock(serverTime: serverTime)
                requestedAt = GLDateParser.formatUTC(serverTime)
                nonce = (0..<16).map { _ in UInt8.random(in: 0...255) }
                nonceB64 = Data(nonce).base64EncodedString()

                proofBytes = RedeemContract.redeemProofBytes(
                    appID: appID,
                    code: codeNorm,
                    deviceKeyBase64: deviceKeyB64,
                    requestID: requestID,
                    requestedAt: requestedAt,
                    nonceBase64: nonceB64
                )
                signature = try identity.signingKey.signature(for: proofBytes).derRepresentation.base64EncodedString()

                payload = [
                    "v": 1,
                    "appId": appID,
                    "code": codeNorm,
                    "deviceKey": deviceKeyB64,
                    "requestId": requestID,
                    "requestedAt": requestedAt,
                    "nonce": nonceB64,
                    "proof": signature
                ]

                let retryResult = try await sendPost(url: url, payload: payload)
                data = retryResult.0
                statusCode = retryResult.1
            } else {
                throw RedeemError.clockSkew(now)
            }
        }

        guard statusCode == 200 else {
            let codigosOcultos: Set<String> = [
                "CODE_ALREADY_USED", "CODE_NOT_FOUND", "LICENSE_WRONG_APP", "CODE_FOR_OTHER_APP",
                "ALREADY_REDEEMED", "INVALID_CODE", "CODE_UNAVAILABLE", "CODE_EXPIRED"
            ]
            if let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
               let err = json["error"] as? [String: Any] {
                let code = (err["code"] as? String) ?? "HTTP_\(statusCode)"
                let rawMsg = (err["message"] as? String) ?? "Error en el canje"
                let msg = (codigosOcultos.contains(code) || [404, 409, 422].contains(statusCode))
                    ? RedeemContract.mensajeCodigoInvalidoOUsado
                    : rawMsg
                throw RedeemError.serverError(code: code, message: msg)
            }
            let msg = [404, 409, 422].contains(statusCode) ? RedeemContract.mensajeCodigoInvalidoOUsado : "Error \(statusCode)"
            throw RedeemError.serverError(code: "HTTP_\(statusCode)", message: msg)
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
        guard let licenseID = lic["licenseId"] as? String, !licenseID.isEmpty,
              let typeStr = lic["type"] as? String, !typeStr.isEmpty,
              let statusStr = lic["status"] as? String, !statusStr.isEmpty,
              let issuedAtStr = lic["issuedAt"] as? String, !issuedAtStr.isEmpty,
              let issuedDate = GLDateParser.parse(issuedAtStr) else {
            throw RedeemError.serverError(code: "INVALID_RESPONSE", message: "Campos requeridos de la licencia inválidos o faltantes")
        }

        let expiresAtStr = lic["expiresAt"] as? String
        let expiresDate: Date? = {
            guard let str = expiresAtStr, !str.isEmpty else { return nil }
            return GLDateParser.parse(str)
        }()

        let redemptionID = (lic["redemptionId"] as? String) ?? ""
        let transferredFrom = lic["transferredFrom"] as? String
        let secondaries = (lic["secundarias"] as? Int) ?? 0
        guard secondaries == 0 else {
            throw RedeemError.invalidCode
        }
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

        // Verificar firma contra claves de uso 'canje' del directorio (por kid exacto)
        let kid = (licenseObj["kid"] as? String) ?? "gl-redeem-v1"
        guard let redeemKey = TrustDirectory.effectiveDirectory?.redeemKey(kid: kid) else {
            throw RedeemError.serverError(code: "UNKNOWN_KID", message: "Clave pública del servicio no autorizada para kid: \(kid)")
        }

        guard let ecdsaSignature = try? P256.Signing.ECDSASignature(derRepresentation: sigDER),
              redeemKey.isValidSignature(ecdsaSignature, for: signingBytes) else {
            throw RedeemError.invalidSignature
        }

        let licType: GLLicenseType
        switch typeStr {
        case "PERPETUA": licType = .perpetual
        case "ANUAL": licType = .annual
        case "SEMESTRAL": licType = .semiannual
        case "MENSUAL": licType = .monthly
        default:
            throw RedeemError.serverError(code: "INVALID_TYPE", message: "Tipo de licencia no admitido: \(typeStr)")
        }

        if licType != .perpetual && expiresDate == nil {
            throw RedeemError.serverError(code: "INVALID_EXPIRES", message: "Fecha de vencimiento requerida para licencia temporal")
        }

        let envelopeData = try JSONSerialization.data(withJSONObject: licenseObj, options: [.sortedKeys])
        let envelopeString = String(data: envelopeData, encoding: .utf8) ?? ""

        return try manager.installDirectLicense(
            licenseID: licenseID,
            type: licType,
            issuedAt: issuedDate,
            expiresAt: expiresDate,
            secondaries: secondaries,
            envelope: envelopeString,
            now: now
        )
    }

    func status(identity: GLIdentity, manager: GLLicenseManager, licenseIDManual: String? = nil, now: Date = Date()) async throws -> GLActivationResult {
        guard canCheckStatus, let url = endpointStatus else {
            throw RedeemError.disabled
        }

        guard let targetLicenseID = licenseIDManual ?? manager.savedLicenseID ?? manager.renewableLicenseID else {
            throw RedeemError.serverError(code: "SIN_LICENCIA", message: "No hay una licencia guardada que consultar.")
        }

        let requestID = UUID().uuidString
        var requestedAt = GLDateParser.formatUTC(now)
        var nonce = (0..<16).map { _ in UInt8.random(in: 0...255) }
        var nonceB64 = Data(nonce).base64EncodedString()
        let deviceKeyB64 = identity.signingPublicKeyDER.base64EncodedString()

        var proofBytes = RedeemContract.statusProofBytes(
            appID: appID,
            licenseID: targetLicenseID,
            deviceKeyBase64: deviceKeyB64,
            requestID: requestID,
            requestedAt: requestedAt,
            nonceBase64: nonceB64
        )
        var signature = try identity.signingKey.signature(for: proofBytes).derRepresentation.base64EncodedString()

        var payload: [String: Any] = [
            "v": 1,
            "appId": appID,
            "licenseId": targetLicenseID,
            "deviceKey": deviceKeyB64,
            "requestId": requestID,
            "requestedAt": requestedAt,
            "nonce": nonceB64,
            "proof": signature
        ]

        var (data, statusCode) = try await sendPost(url: url, payload: payload)

        // Reintento automático ante CLOCK_SKEW
        if statusCode == 400 {
            if let serverTime = parseServerTime(from: data) ?? (await fetchTime(from: url)) {
                manager.recoverClock(serverTime: serverTime)
                requestedAt = GLDateParser.formatUTC(serverTime)
                nonce = (0..<16).map { _ in UInt8.random(in: 0...255) }
                nonceB64 = Data(nonce).base64EncodedString()

                proofBytes = RedeemContract.statusProofBytes(
                    appID: appID,
                    licenseID: targetLicenseID,
                    deviceKeyBase64: deviceKeyB64,
                    requestID: requestID,
                    requestedAt: requestedAt,
                    nonceBase64: nonceB64
                )
                signature = try identity.signingKey.signature(for: proofBytes).derRepresentation.base64EncodedString()

                payload = [
                    "v": 1,
                    "appId": appID,
                    "licenseId": targetLicenseID,
                    "deviceKey": deviceKeyB64,
                    "requestId": requestID,
                    "requestedAt": requestedAt,
                    "nonce": nonceB64,
                    "proof": signature
                ]

                let retryResult = try await sendPost(url: url, payload: payload)
                data = retryResult.0
                statusCode = retryResult.1
            } else {
                throw RedeemError.clockSkew(now)
            }
        }

        guard statusCode == 200 else {
            if let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
               let err = json["error"] as? [String: Any] {
                let code = (err["code"] as? String) ?? "HTTP_\(statusCode)"
                let msg = (err["message"] as? String) ?? "Error al consultar estado"
                throw RedeemError.serverError(code: code, message: msg)
            }
            throw RedeemError.serverError(code: "HTTP_\(statusCode)", message: "Error \(statusCode)")
        }

        guard let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let statusObj = json["status"] as? [String: Any],
              let st = statusObj["status"] as? [String: Any],
              let sigB64 = statusObj["sig"] as? String,
              let sigDER = Data(base64Encoded: sigB64) else {
            throw RedeemError.serverError(code: "INVALID_RESPONSE", message: "Respuesta ilegible")
        }

        let thumbprint = (st["deviceThumbprint"] as? String) ?? ""
        let expectedThumbprint = RedeemContract.deviceThumbprint(identity.signingPublicKeyDER)
        guard thumbprint == expectedThumbprint else {
            throw RedeemError.wrongDevice
        }

        let stApp = (st["appId"] as? String) ?? ""
        guard stApp == appID else {
            throw RedeemError.wrongApp
        }

        let v = (st["v"] as? Int) ?? 1
        let licenseID = (st["licenseId"] as? String) ?? ""
        let typeStr = (st["type"] as? String) ?? ""
        let statusStr = (st["status"] as? String) ?? ""
        let issuedAtStr = (st["issuedAt"] as? String) ?? ""
        let expiresAtStr = st["expiresAt"] as? String
        let redemptionID = (st["redemptionId"] as? String) ?? ""
        let transferredFrom = st["transferredFrom"] as? String
        let secondaries = (st["secundarias"] as? Int) ?? 0
        let devicePublicKey = st["devicePublicKey"] as? String
        let checkedAtStr = (st["checkedAt"] as? String) ?? requestedAt

        let signingBytes = RedeemContract.statusSigningBytes(
            v: v,
            licenseID: licenseID,
            appID: stApp,
            type: typeStr,
            status: statusStr,
            issuedAt: issuedAtStr,
            expiresAt: expiresAtStr,
            deviceThumbprint: thumbprint,
            redemptionID: redemptionID,
            transferredFrom: transferredFrom,
            secondaries: secondaries,
            devicePublicKey: devicePublicKey,
            checkedAt: checkedAtStr
        )

        let kid = (statusObj["kid"] as? String) ?? "gl-redeem-v1"
        let redeemKey = TrustDirectory.effectiveDirectory?.redeemKey(kid: kid)
            ?? TrustDirectory.effectiveDirectory?.canjeKeys().first

        guard let redeemKey else {
            throw RedeemError.serverError(code: "UNKNOWN_KID", message: "Clave pública del servicio no autorizada para kid: \(kid)")
        }

        guard let ecdsaSignature = try? P256.Signing.ECDSASignature(derRepresentation: sigDER),
              redeemKey.isValidSignature(ecdsaSignature, for: signingBytes) else {
            throw RedeemError.invalidSignature
        }

        if statusStr == "REVOCADA" {
            try manager.markRevoked(now: now)
        }

        return .accepted
    }

    private func sendPost(url: URL, payload: [String: Any]) async throws -> (Data, Int) {
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
        return (data, statusCode)
    }

    private func parseServerTime(from data: Data) -> Date? {
        guard let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return nil }
        let err = json["error"] as? [String: Any]
        guard let stRaw = (err?["serverTime"] as? String) ?? (json["serverTime"] as? String) else { return nil }
        return GLDateParser.parse(stRaw)
    }

    func fetchTime(from serviceURL: URL) async -> Date? {
        let base = serviceURL.deletingLastPathComponent().deletingLastPathComponent()
        let timeURL = base.appendingPathComponent("v1").appendingPathComponent("time")
        var request = URLRequest(url: timeURL, timeoutInterval: 5.0)
        request.httpMethod = "GET"
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              let http = response as? HTTPURLResponse, http.statusCode == 200,
              let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let nowStr = json["now"] as? String else {
            return nil
        }
        return GLDateParser.parse(nowStr)
    }
}
