import Foundation
import XCTest
@testable import IShalevDomain

final class EmployeePINTests: XCTestCase {
    func testPolicyRequiresSixToTwelveAsciiDigits() {
        XCTAssertTrue(EmployeePIN.isValid("123456"))
        XCTAssertTrue(EmployeePIN.isValid("123456789012"))
        XCTAssertFalse(EmployeePIN.isValid("12345"))
        XCTAssertFalse(EmployeePIN.isValid("1234567890123"))
        XCTAssertFalse(EmployeePIN.isValid("12345a"))
        XCTAssertFalse(EmployeePIN.isValid("１２３４５６"))
    }

    func testSaltedVerifierAcceptsOnlyTheConfiguredPIN() throws {
        let first = try EmployeePIN.credentials(for: "731904")
        let second = try EmployeePIN.credentials(for: "731904")

        XCTAssertEqual(first.salt.count, EmployeePIN.saltLength)
        XCTAssertEqual(first.verifier.count, EmployeePIN.keyLength)
        XCTAssertNotEqual(first.salt, second.salt)
        XCTAssertNotEqual(first.verifier, second.verifier)
        XCTAssertTrue(try EmployeePIN.matches("731904", salt: first.salt, verifier: first.verifier))
        XCTAssertFalse(try EmployeePIN.matches("731905", salt: first.salt, verifier: first.verifier))
    }

    func testFifthFailureLocksForFifteenMinutes() {
        let employee = LocalEmployee(name: "Ana", permissions: [.sellProducts])
        let start = Date(timeIntervalSince1970: 1_800_000_000)
        var current = employee

        for remaining in stride(from: 4, through: 1, by: -1) {
            let result = EmployeePIN.registeringFailure(for: current, at: start)
            current = result.employee
            XCTAssertEqual(result.result, .rejected(attemptsRemaining: remaining))
        }

        let locked = EmployeePIN.registeringFailure(for: current, at: start)
        XCTAssertEqual(locked.result, .locked(until: start.addingTimeInterval(15 * 60)))
        XCTAssertEqual(locked.employee.pinFailedAttempts, 0)
        XCTAssertEqual(locked.employee.pinLockedUntil, start.addingTimeInterval(15 * 60))
    }

    func testPreviousEmployeeJSONDecodesWithoutNewPINFields() throws {
        let legacyJSON = """
        {
          "id": "00000000-0000-0000-0000-000000000001",
          "name": "Ana",
          "permissions": ["Vender productos"],
          "createdAt": "2026-10-24T00:00:00Z"
        }
        """
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let employee = try decoder.decode(LocalEmployee.self, from: Data(legacyJSON.utf8))

        XCTAssertEqual(employee.name, "Ana")
        XCTAssertFalse(employee.hasPIN)
        XCTAssertNil(employee.pinSalt)
        XCTAssertNil(employee.pinVerifier)
        XCTAssertNil(employee.pinFailedAttempts)
        XCTAssertNil(employee.pinLockedUntil)
    }
}
