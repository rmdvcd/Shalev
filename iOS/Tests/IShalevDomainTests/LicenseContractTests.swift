import XCTest
@testable import IShalevDomain

final class LicenseContractTests: XCTestCase {
    func testPinnedGLKeysParseAndMatchTheirFingerprints() {
        XCTAssertTrue(GLTrust.canValidate)
        XCTAssertTrue(GLTrust.canRequest)
        XCTAssertEqual(GLTrust.signingPublicKeys.count, 5)
        XCTAssertNotNil(GLTrust.ecdhPublicKey)
    }

    func testLicensePricesMatchAndroidContract() {
        XCTAssertEqual(GLLicenseType.monthly.totalPriceCents(secondaries: 0), 600_000)
        XCTAssertEqual(GLLicenseType.monthly.totalPriceCents(secondaries: 2), 800_000)
        XCTAssertEqual(GLLicenseType.semiannual.totalPriceCents(secondaries: 1), 3_500_000)
        XCTAssertEqual(GLLicenseType.annual.totalPriceCents(secondaries: 2), 6_800_000)
        XCTAssertEqual(GLLicenseType.perpetual.totalPriceCents(secondaries: 1), 10_700_000)
        XCTAssertEqual(GLLicenseType.monthly.totalPriceCents(secondaries: -3), 600_000)
        XCTAssertEqual(GLLicenseType.monthly.extensionPriceCents(addedSecondaries: 3), 300_000)
        XCTAssertEqual(GLLicenseType.semiannual.extensionPriceCents(addedSecondaries: 2), 1_000_000)
        XCTAssertEqual(GLLicenseType.annual.extensionPriceCents(addedSecondaries: 4), 3_600_000)
        XCTAssertEqual(GLLicenseType.perpetual.extensionPriceCents(addedSecondaries: 2), 3_400_000)
    }

    func testLicenseProfileValidationAndPhoneNormalization() {
        XCTAssertTrue(validName("María"))
        XCTAssertTrue(validName("Pérez-González"))
        XCTAssertFalse(validName("A"))
        XCTAssertFalse(validName("María1"))
        XCTAssertTrue(validCI("85010112345"))
        XCTAssertFalse(validCI("85 0101"))
        XCTAssertEqual(normalizePhone("52123456"), "+5352123456")
        XCTAssertEqual(normalizePhone("005352123456"), "+5352123456")
        XCTAssertEqual(normalizePhone("+53 5212 3456"), "+5352123456")
        XCTAssertNil(normalizePhone("123"))
    }

    func testAvisoFrescuraIsSevenDaysGSM7() {
        let expected = "Vale 7 dias: enviala y paga cuanto antes; si pasa ese tiempo, genera otra solicitud."
        XCTAssertEqual(GLLicenseMessage.AVISO_FRESCURA, expected)
        let allowed = Set("@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u{001b}ÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà")
        for ch in GLLicenseMessage.AVISO_FRESCURA {
            XCTAssertTrue(allowed.contains(ch), "Character '\(ch)' is not in GSM-7 basic charset")
        }
    }

    func testTransferenciaValidationAndTxFlag() {
        XCTAssertFalse(GLLicenseManager.ENVIAR_TX_EN_SOLICITUD)
        let pattern = "^[A-Za-z0-9-]{4,30}$"
        XCTAssertNotNil("BR601ADLM8997".range(of: pattern, options: .regularExpression))
        XCTAssertNotNil("TX-123456".range(of: pattern, options: .regularExpression))
        XCTAssertNil("123".range(of: pattern, options: .regularExpression))
        XCTAssertNil(String(repeating: "A", count: 31).range(of: pattern, options: .regularExpression))
        XCTAssertNil("TX@123".range(of: pattern, options: .regularExpression))
    }

    func testRedeemContractNormalizationAndTranscriptVector() {
        XCTAssertEqual(RedeemContract.normalizeCode("4K7N-9X2M-B8PQ-3T5V"), "4K7N9X2MB8PQ3T5V")
        XCTAssertEqual(RedeemContract.normalizeCode(" 4k7n 9x2m b8pq 3t5v "), "4K7N9X2MB8PQ3T5V")
        XCTAssertEqual(RedeemContract.normalizeCode("4K7N9X2MB8PQ3T5V"), "4K7N9X2MB8PQ3T5V")

        XCTAssertNil(RedeemContract.normalizeCode("4K7N-9X2M-B8PQ-3T5")) // 15
        XCTAssertNil(RedeemContract.normalizeCode("4K7N-9X2M-B8PQ-3T5VV")) // 17
        XCTAssertNil(RedeemContract.normalizeCode("ABCD-EFGH-JKLM-NPQI")) // I
        XCTAssertNil(RedeemContract.normalizeCode("ABCD-EFGH-JKLM-NPQO")) // O

        let proofBytes = RedeemContract.redeemProofBytes(
            appId: "DSHALEV",
            code: "4K7N9X2MB8PQ3T5V",
            deviceKeyB64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEsg/+aDd+3cH8ZhZK5n77JMi/+seJn1aVw+l7XmJsOAbQRsvlmYx5suxdvhkhIbte6N8pFn196CH/jv0I9ipxog==",
            requestId: "11111111-1111-4111-8111-111111111111",
            requestedAt: "2026-11-10T12:00:00.000Z",
            nonceB64: "AAECAwQFBgcICQoLDA0ODw=="
        )
        let expectedB64 = "AAAAEkdMLVJFREVFTS1SRVFVRVNUMQAAAAExAAAAB0RTSEFMRVYAAAAQNEs3TjlYMk1COFBRM1Q1VgAAAHxNRmt3RXdZSEtvWkl6ajBDQVFZSUtvWkl6ajBEQVFjRFFnQUVzZy8rYURkKzNjSDhaaFpLNW43N0pNaS8rc2VKbjFhVncrbDdYbUpzT0FiUVJzdmxtWXg1c3V4ZHZoa2hJYnRlNk44cEZuMTk2Q0gvanYwSTlpcHhvZz09AAAAJDExMTExMTExLTExMTEtNDExMS04MTExLTExMTExMTExMTExMQAAABgyMDI2LTExLTEwVDEyOjAwOjAwLjAwMFoAAAAYQUFFQ0F3UUZCZ2NJQ1FvTERBME9Edz09"
        XCTAssertEqual(proofBytes.base64EncodedString(), expectedB64)

        XCTAssertEqual(
            RedeemContract.mensajeCodigoInvalidoOUsado,
            "Código no válido para esta aplicación o ya utilizado"
        )
        XCTAssertEqual(
            RedeemContract.motivoAunNoDisponible,
            "Aún no disponible en esta edición"
        )
    }
}
