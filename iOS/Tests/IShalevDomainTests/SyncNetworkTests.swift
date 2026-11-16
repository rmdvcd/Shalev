import XCTest
@testable import IShalevDomain

final class SyncNetworkTests: XCTestCase {
    func testWiFiAndHotspotAddressesPrecedePrivateCellularAddresses() {
        let result = SyncLocalAddresses.ordered([
            (interface: "pdp_ip0", address: "10.12.0.4"),
            (interface: "en0", address: "192.168.43.1"),
            (interface: "pdp_ip0", address: "100.80.20.5"),
            (interface: "en0", address: "189.30.20.15")
        ])
        XCTAssertEqual(result, ["192.168.43.1", "189.30.20.15", "10.12.0.4", "100.80.20.5"])
    }

    func testFiltersUnusableAndDuplicateIPv4AddressesAndCapsQRHosts() {
        let candidates = [
            (interface: "en0", address: "127.0.0.1"),
            (interface: "en0", address: "169.254.1.2"),
            (interface: "en0", address: "224.0.0.1"),
            (interface: "en0", address: "192.168.1.2"),
            (interface: "en0", address: "192.168.1.2")
        ] + (1...SyncLocalAddresses.maximumHosts + 5).map {
            (interface: "en0", address: "10.0.0.\($0)")
        }
        let result = SyncLocalAddresses.ordered(candidates)
        XCTAssertFalse(result.contains("127.0.0.1"))
        XCTAssertFalse(result.contains("169.254.1.2"))
        XCTAssertFalse(result.contains("224.0.0.1"))
        XCTAssertEqual(result.count, SyncLocalAddresses.maximumHosts)
        XCTAssertEqual(result.count, Set(result).count)
        XCTAssertEqual(result.first, "192.168.1.2")
    }

    func testOnlyNumericReachableIPv4AddressesAreQrCandidates() {
        ["10.0.0.1", "192.168.43.1", "100.64.0.1", "189.30.20.15"].forEach {
            XCTAssertTrue(SyncLocalAddresses.isUsableIPv4($0), "Expected valid candidate: \($0)")
        }
        ["localhost", "192.168.1", "192.168.1.999", "192.168.01.2", "0.0.0.0",
         "127.0.0.1", "169.254.1.2", "224.0.0.1", "240.0.0.1"].forEach {
            XCTAssertFalse(SyncLocalAddresses.isUsableIPv4($0), "Expected invalid candidate: \($0)")
        }
    }
}
