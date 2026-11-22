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
}
