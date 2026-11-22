import Foundation
import CryptoKit

/// Protocolo de canje GL v1 (Anexo B / S7 / Fase 4).
enum RedeemContract {
    static let versionService = 1
    static let versionLicense = 1
    static let versionStatus = 1

    static let domainRedeemRequest = "GL-REDEEM-REQUEST1"
    static let domainStatusRequest = "GL-REDEEM-STATUS-REQUEST1"
    static let domainLicense = "GL-REDEEM-LICENSE1"
    static let domainStatus = "GL-REDEEM-STATUS1"

    static let defaultAppID = "SHALEV-IOS"
    static let mensajeCodigoInvalidoOUsado = "Código no válido para esta aplicación o ya utilizado"
    static let motivoAunNoDisponible = "Aún no disponible en esta edición"

    private static let codigoRegex = try? NSRegularExpression(pattern: "^[0-9A-HJ-NP-Z]{16}$")

    static func normalizeCode(_ raw: String) -> String? {
        let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            .uppercased()
            .replacingOccurrences(of: "-", with: "")
            .replacingOccurrences(of: " ", with: "")
        guard clean.count == 16 else { return nil }
        let range = NSRange(location: 0, length: clean.utf16.count)
        guard codigoRegex?.firstMatch(in: clean, range: range) != nil else { return nil }
        return clean
    }

    static func canonical(_ parts: [String]) -> Data {
        var result = Data()
        for part in parts {
            let partData = Data(part.utf8)
            var length = UInt32(partData.count).bigEndian
            result.append(Data(bytes: &length, count: 4))
            result.append(partData)
        }
        return result
    }

    static func deviceThumbprint(_ spkiDER: Data) -> String {
        let digest = SHA256.hash(data: spkiDER)
        return Base64URL.encode(Data(digest))
    }

    static func redeemProofBytes(
        appID: String,
        code: String,
        deviceKeyBase64: String,
        requestID: String,
        requestedAt: String,
        nonceBase64: String,
        v: Int = versionService
    ) -> Data {
        canonical([
            domainRedeemRequest,
            String(v),
            appID,
            code,
            deviceKeyBase64,
            requestID,
            requestedAt,
            nonceBase64
        ])
    }

    static func statusProofBytes(
        appID: String,
        licenseID: String,
        deviceKeyBase64: String,
        requestID: String,
        requestedAt: String,
        nonceBase64: String,
        v: Int = versionService
    ) -> Data {
        canonical([
            domainStatusRequest,
            String(v),
            appID,
            licenseID,
            deviceKeyBase64,
            requestID,
            requestedAt,
            nonceBase64
        ])
    }

    static func licenseSigningBytes(
        v: Int,
        licenseID: String,
        appID: String,
        type: String,
        status: String,
        issuedAt: String,
        expiresAt: String?,
        deviceThumbprint: String,
        redemptionID: String,
        transferredFrom: String?,
        secondaries: Int,
        devicePublicKey: String?
    ) -> Data {
        canonical([
            domainLicense,
            String(v),
            licenseID,
            appID,
            type,
            status,
            issuedAt,
            expiresAt ?? "",
            deviceThumbprint,
            redemptionID,
            transferredFrom ?? "",
            String(secondaries),
            devicePublicKey ?? ""
        ])
    }

    static func statusSigningBytes(
        v: Int,
        licenseID: String,
        appID: String,
        type: String,
        status: String,
        issuedAt: String,
        expiresAt: String?,
        deviceThumbprint: String,
        redemptionID: String,
        transferredFrom: String?,
        secondaries: Int,
        devicePublicKey: String?,
        checkedAt: String
    ) -> Data {
        canonical([
            domainStatus,
            String(v),
            licenseID,
            appID,
            type,
            status,
            issuedAt,
            expiresAt ?? "",
            deviceThumbprint,
            redemptionID,
            transferredFrom ?? "",
            String(secondaries),
            devicePublicKey ?? "",
            checkedAt
        ])
    }
}
