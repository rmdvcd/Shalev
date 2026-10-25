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
}
