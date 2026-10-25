import XCTest
import CryptoKit
@testable import IShalevDomain

final class SyncProtocolTests: XCTestCase {
    private func data(_ hex: String) -> Data {
        var result = Data()
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            result.append(UInt8(hex[index..<next], radix: 16)!)
            index = next
        }
        return result
    }

    func testDecodesQRProducedByAndroidAndRoundTrips() throws {
        // JSON compacto de CodigoQr.kt; token 00…0f, Base64url sin relleno.
        let android = "SPVI-VINC1:eyJuIjoiYTFiMmMzZDRlNWY2YTFiMmMzZDRlNWY2Iiwibm4iOiJNYXLDrWEgUMOpcmV6IiwiaCI6WyIxOTIuMTY4LjQzLjEiLCIxMC4wLjAuNSJdLCJwIjo0NzgxMSwiZSI6MywiZW4iOiJMdWlzIiwidCI6IkFBRUNBd1FGQmdjSUNRb0xEQTBPRHciLCJ4IjoxNzkwOTUzODAwMDAwfQ"
        let code = try SyncPairingCode.decode(android)
        XCTAssertEqual(code.businessID, "a1b2c3d4e5f6a1b2c3d4e5f6")
        XCTAssertEqual(code.businessName, "María Pérez")
        XCTAssertEqual(code.hosts, ["192.168.43.1", "10.0.0.5"])
        XCTAssertEqual(code.port, 47_811)
        XCTAssertEqual(code.employeeID, 3)
        XCTAssertEqual(code.token, Data(0..<16))
        let copy = try SyncPairingCode.decode(code.encoded())
        XCTAssertEqual(copy, code)
        XCTAssertThrowsError(try SyncPairingCode.decode("SPVI-VINC1:e30"))
        XCTAssertThrowsError(try SyncPairingCode.decode(android, now: code.expiresAt, requireCurrent: true))
    }

    func testPairingHMACMatchesIndependentAndroidVector() {
        let token = Data(0..<16)
        let publicKey = Data(0..<65)
        XCTAssertEqual(SyncCrypto.secondaryPairingMAC(token: token, publicKeySPKI: publicKey).map { String(format: "%02x", $0) }.joined(),
            "c05a477af00e0a166916ab513285ed1936acbce691893a9aca74d4367d3f88d2")
    }

    func testSessionHKDFMatchesIndependentAndroidVector() throws {
        let keys = try SyncCrypto.sessionKeys(employeeKey: Data(0..<32), secondaryNonce: Data(16..<32), principalNonce: Data(32..<48))
        XCTAssertEqual(keys.c2s.map { String(format: "%02x", $0) }.joined(),
            "e6ef47190a1d19ac4736c3061c0fa68509b1582aa13af8c23f594e561bfe6775")
        XCTAssertEqual(keys.s2c.map { String(format: "%02x", $0) }.joined(),
            "03e29c2acaa4f252dcb9d779f879f4637c465614a69c1d257c82bae91e04bd0f")
    }

    func testECDHBothSidesDeriveSameEmployeeKey() throws {
        let secondary = P256.KeyAgreement.PrivateKey()
        let principal = P256.KeyAgreement.PrivateKey()
        let token = try SyncCrypto.random(16)
        let a = try SyncCrypto.employeeKey(privateKey: secondary, peerSPKI: principal.publicKey.derRepresentation, token: token)
        let b = try SyncCrypto.employeeKey(privateKey: principal, peerSPKI: secondary.publicKey.derRepresentation, token: token)
        XCTAssertEqual(a, b)
        XCTAssertEqual(a.count, 32)
        let other = try SyncCrypto.employeeKey(privateKey: secondary, peerSPKI: principal.publicKey.derRepresentation,
            token: Data(token.reversed()))
        XCTAssertNotEqual(a, other)
    }

    func testCipherDeliversOrderAndRejectsTamperReplayWrongKeyAndReflection() throws {
        let employee = Data(0..<32), nonceC = Data(32..<48), nonceS = Data(48..<64)
        var secondary = try SyncChannelCipher(side: .secondary, employeeKey: employee, secondaryNonce: nonceC, principalNonce: nonceS)
        var principal = try SyncChannelCipher(side: .principal, employeeKey: employee, secondaryNonce: nonceC, principalNonce: nonceS)
        let first = try secondary.seal(SyncJSON.message("ping", ["id": 1]))
        let second = try secondary.seal(SyncJSON.message("ping", ["id": 2]))
        XCTAssertEqual(try SyncJSON.type(principal.open(first)), "ping")
        let decodedSecond = try principal.open(second)
        XCTAssertEqual(AndroidBackupCodec.int64(decodedSecond["id"]), 2)

        var replayReceiver = try SyncChannelCipher(side: .principal, employeeKey: employee, secondaryNonce: nonceC, principalNonce: nonceS)
        _ = try replayReceiver.open(first)
        XCTAssertThrowsError(try replayReceiver.open(first))

        var altered = first
        altered[altered.count - 1] ^= 1
        var tamperReceiver = try SyncChannelCipher(side: .principal, employeeKey: employee, secondaryNonce: nonceC, principalNonce: nonceS)
        XCTAssertThrowsError(try tamperReceiver.open(altered))

        var wrongKey = try SyncChannelCipher(side: .principal, employeeKey: Data(repeating: 9, count: 32), secondaryNonce: nonceC, principalNonce: nonceS)
        XCTAssertThrowsError(try wrongKey.open(first))

        var principalSender = try SyncChannelCipher(side: .principal, employeeKey: employee, secondaryNonce: nonceC, principalNonce: nonceS)
        let reflected = try principalSender.seal(SyncJSON.message("aviso"))
        var reflectedReceiver = try SyncChannelCipher(side: .principal, employeeKey: employee, secondaryNonce: nonceC, principalNonce: nonceS)
        XCTAssertThrowsError(try reflectedReceiver.open(reflected))
    }

    func testFrameLimits() throws {
        XCTAssertEqual(try SyncFrames.lengthPrefix(0x0102_0304), Data([1, 2, 3, 4]))
        XCTAssertEqual(try SyncFrames.decodeLength(Data([1, 2, 3, 4])), 0x0102_0304)
        XCTAssertThrowsError(try SyncFrames.lengthPrefix(SyncFrames.maximum + 1))
    }
}
