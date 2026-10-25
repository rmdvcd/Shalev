import Foundation
import XCTest
@testable import IShalevDomain

private final class AndroidBackupMemoryStorage: LocalStorePersisting {
    var value: AppData?
    init(_ value: AppData? = nil) { self.value = value }
    func load() throws -> AppData? { value }
    func save(_ data: AppData) throws { value = data }
}

@MainActor
final class AndroidBackupTests: XCTestCase {
    // Vectores independientes generados según BackupCipher.kt; no fueron producidos por el codec Swift.
    private let androidV4 = Data(base64Encoded: "U1BWSUJBSwQBAAABoMRQbAAABLrwAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGwAAAAAAAAT7mOG3fOhN04kqeyFL2i49eAJoWddrqLGO/GJKO9FkaLp7XLqOccRVGGsczgjN8Xy2PEverxoKQbjm5yHnXgWvruaWIPRqv+H6Qr3o2qAjsSL5/JqZjlMcy/Xz0GSrtx2cZE81jqLLfNUYRyF0Fktanhcvz4CGO+hJSutQL0Qu/+r9dgIf4fuk9OmxgRqxIl/RTIny8VrqNRzl6IrUEZhyPPQMCSXeuA6uYR8ikI6maKhOnya/cPn66WQ/GE8Ub/jkQvAymIBLsNm3mmLin32XxGI1Gti4CD+woyi0BzRrLEonR/yckiQRpun05O4rzWmJULGpGPh4RDuV9zqGNHFhLJac0+vYwy+uMYpX2w+BXBY2fDOun90uRW/20WATowEGbbNpwA9HwD8f2mPxWG/IHxYw4CZXEGCTGa736D37QniYCI+nivdm1qmKJXHyz3Ykvaw22ry1Q+41GJUeRp2WBtFAAZdJt4mgKdGvyeEEoUaMgBc7xf7VOB6x22panKCIY1K+HC3UyvkgOowTOa6JYK8rXhNbGQQlHZrheeiKAGImJCnNC9rIARJmX6e173VVyPq6doQhcw9s7DOlWgBf5YcvJb+38mDTIc9zdM5VPCHh08/ZS6L/BpVTcfvs8P7gt0YZwuOjXPg+mOqroDpVGwutRt8dMZ6UsOMvJWtwaDZtzphVwjSk2q4yhrjoKbeGWgH1f05gbj7NTqSeRgH9YtgGUq/ZahCPaNz6nLkQmMG9ParhAvGP7LuT31JsX6tumb1m4GmhK+xIjiQPyIaU7WnoYoKBvfoF4nXg9OUH+e9s2rWTRuATsXOTIvKaArRfGp+ck1GYab4SxQfNjRJhYUp22mZliAb7HdC9GEl3ilD44U0meB+Jnnvyo7etRN5tv1KwP4BbcCKm9DlB9a1XVXFJrZZaLiZr+3Qf18MI6yIlNJ/pLW5m4z+r0FeJby0L8RcKD0eUlqr66N1UeFEd4QtyXclIgZz23v+ujvrtltyAwy8pHetnTnNNuT54IylPCiRj8vu5uarE2cui+Ftzm0EwJgG3v12w3UiBBXh/VWJHhufc+4pwcbyl8KFh449iWcgweCZmXCTV0f0nDdKEHC5Hk0M2Wk010NZqx8AjPWvyWFbeUQI5KME5CwQlhbhbGp9UP7eSJw/61COJC4B+3GLRhBga84giPSGTYDIq1QURJA6zgquZv+SvDwizrlPi9lVbzTrWx8FY2bpT5QxdiZPJ2lahjF0cWnD5iWTvo5aTbZuBh5X7TgspW5MRqkXlypslE7iTofFvvo92saA2z3oRG7anTrae1iPswJvM4yVFvou622fUSWwtAh1QofOP15McSgWSPLTuxzJXDXmRx4rAJAUUoIRN3vVzQv11ITKR3scCjyeeTkQfRqs9onBbPJBh0KxKgLwGCwmUlrbiJFWO1dTewUEMKNtDem+0ahEdAky/8BoO0WE638a9G6CjVTn7wIZdeG2ep6XJ5lfVPzzWtKPAc+fL6+DXOTBq34nBzkycJl9iKBMKhEQJBblh6dejwwM5e2PHxcBdLIWQNwkmy47zDlQO+K22R+cKLwYVM/FoKF2mJF9xjlFtIokNeUNHvAEgkmPvVi2mhHt/JRrHaRpwdvx/Oio996VnK1F9qrI8NoirnYjlTmlPSb/53wi3ERCdCFQ4fBn/TrkhQa1q4Z6KFUKU3xAjXF+o6IXocTzgnSYYavqW6+l+GvxomYGRdJ+L/M11a54=")!
    private let androidV3 = Data(base64Encoded: "U1BWSUJBSwMAAAGgxFBsAAAEuvAAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobAAAAAAAAAFsWCsHn7BpQmVeRA6U9ob41BNLfHcorI4TN6pJl7TSBFntcuo5xxFUYaxzwCcyw1LNgfILK5nP/PFjyRaLDyIrEw0JtMEwXjNL9G8f7CtoIWyZ1woIrlR2Y86DP6ZkT6+ew0xMfPkHvefKtW9F98l0R/41VH7RgG2yqJ6g=")!
    private let androidWithoutPassword = Data(base64Encoded: "U1BWSUJBSwQAAAABoMRQbAAAACcQAAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGwAAAAAAAABbybPCLze62eDyFogM8cJ4ZQUMIelvSe54F8j1uxUaM5lrCH5alZEGhT7/q9yH+SJLk0yk7DBQig+w536sJsDuRLkSVSQBA0/hHM2WFcH07rUsOK09KGSGyCbDlh1jvtXAHdzOlIpO2aJPYIHoaZPmgNOThOxZxUCGj2AN")!

    func testReadsAndroidV4AndPreservesUnitsMoneyRecipesAndClosing() throws {
        XCTAssertTrue(AndroidBackupCodec.isAndroidBackup(androidV4))
        XCTAssertTrue(AndroidBackupCodec.requiresPassword(androidV4))
        let app = try AndroidBackupCodec.decode(androidV4, password: "secreta1")

        XCTAssertEqual(app.products.count, 3)
        let water = try XCTUnwrap(app.products.first { $0.name == "Agua" })
        let bread = try XCTUnwrap(app.products.first { $0.name == "Sándwich" })
        let flour = try XCTUnwrap(app.products.first { $0.name == "Harina" })
        XCTAssertEqual(water.stockMilli, 26_000)
        XCTAssertEqual(water.lowStockMilli, 10_000)
        XCTAssertEqual(water.priceCents, 7_500)
        XCTAssertEqual(water.costCents, 4_500)
        XCTAssertEqual(bread.kind, .prepared)
        XCTAssertEqual(bread.uses.first?.ingredientID, flour.id)
        XCTAssertEqual(bread.uses.first?.milliUnits, 100)
        XCTAssertEqual(flour.stockMilli, 8_500)
        XCTAssertEqual(flour.priceCents, 0, "Un insumo Android sin precioVentaCent no se vende suelto")

        let service = try XCTUnwrap(app.services.first)
        XCTAssertEqual(service.priceCents, 12_000)
        XCTAssertEqual(service.uses.first?.ingredientID, flour.id)
        XCTAssertEqual(service.uses.first?.milliUnits, 20)

        let sale = try XCTUnwrap(app.sales.first)
        XCTAssertEqual(sale.totalCents, 7_500)
        XCTAssertEqual(sale.transactionNumber, "BR601ADLM8997")
        XCTAssertEqual(sale.customer?.identification, "90121254321")
        XCTAssertEqual(sale.paymentCard, "9200123456789012")
        XCTAssertEqual(sale.lines.first?.sourceID, water.id)
        XCTAssertEqual(sale.lines.first?.basePriceCents, 7_500)
        XCTAssertEqual(app.customers.first?.identification, sale.customer?.identification)

        let shift = try XCTUnwrap(app.shifts.first)
        XCTAssertEqual(shift.summary?.totalCents, 7_500)
        XCTAssertEqual(shift.closingCount?.bills[100], 2)
        XCTAssertEqual(shift.closingCount?.otherCashCents, 5_000)
        let waterCount = try XCTUnwrap(shift.closingCount?.inventory.first { $0.productID == water.id })
        let flourCount = try XCTUnwrap(shift.closingCount?.inventory.first { $0.productID == flour.id })
        XCTAssertEqual(waterCount.expectedMilli, 27_000)
        XCTAssertEqual(waterCount.countedMilli, 26_000)
        XCTAssertEqual(flourCount.expectedMilli, 8_600)
        XCTAssertEqual(flourCount.countedMilli, 8_500)

        XCTAssertEqual(app.inventoryMovements.first?.deltaMilli, -1_000)
        XCTAssertEqual(app.inventoryMovements.first?.resultingStockMilli, 26_000)
        XCTAssertEqual(app.cashMovements.first?.amountCents, 5_000)
        XCTAssertEqual(app.settings.priceAdjustments.first?.productIDs, Set([water.id]))
        XCTAssertEqual(app.settings.profile.paymentCard?.number, "9200123456789012")
        XCTAssertEqual(app.settings.profile.paymentPhone?.number, "+5352123456")
        XCTAssertEqual(app.employees?.first?.hasPIN, false, "Los PIN locales nunca viajan en un respaldo Android")
        XCTAssertEqual(app.employees?.first?.permissions.contains(.sellProducts), true)

        // La exportación usa los mismos Long estables y conserva los valores al volver a importar.
        let exported = try AndroidBackupCodec.encode(app, password: "otra-clave-9")
        let restored = try AndroidBackupCodec.decode(exported, password: "otra-clave-9")
        XCTAssertEqual(restored.products.first { $0.name == "Agua" }?.id, water.id)
        XCTAssertEqual(restored.products.first { $0.name == "Agua" }?.stockMilli, 26_000)
        XCTAssertEqual(restored.sales.first?.id, sale.id)
        XCTAssertEqual(restored.sales.first?.totalCents, 7_500)
        XCTAssertEqual(restored.shifts.first?.closingCount?.inventory.count, 2)
    }

    func testReadsLegacyV3AndAndroidFilesWithoutUserPassword() throws {
        let legacy = try AndroidBackupCodec.decode(androidV3, password: "secreta1")
        XCTAssertEqual(legacy, .empty)

        XCTAssertFalse(AndroidBackupCodec.requiresPassword(androidWithoutPassword))
        // Android ignora la contraseña recibida cuando el indicador público es 0.
        let open = try AndroidBackupCodec.decode(androidWithoutPassword, password: "no-se-usa")
        XCTAssertEqual(open.products, [])

        let encoded = try AndroidBackupCodec.encode(.empty, password: "")
        XCTAssertFalse(AndroidBackupCodec.requiresPassword(encoded))
        let roundTrip = try AndroidBackupCodec.decode(encoded, password: "")
        XCTAssertEqual(roundTrip.products, [])
        XCTAssertEqual(roundTrip.sales, [])
        XCTAssertEqual(roundTrip.shifts, [])
        XCTAssertEqual(roundTrip.settings.profile.businessName, AppData.empty.settings.profile.businessName)
    }

    func testRejectsWrongPasswordCorruptionAndLossyProductQuantities() throws {
        XCTAssertThrowsError(try AndroidBackupCodec.decode(androidV4, password: "incorrecta")) {
            XCTAssertEqual($0 as? BusinessError, .backupInvalid)
        }
        var damaged = androidV4
        damaged[damaged.count - 1] ^= 1
        XCTAssertThrowsError(try AndroidBackupCodec.decode(damaged, password: "secreta1")) {
            XCTAssertEqual($0 as? BusinessError, .backupInvalid)
        }

        var fractional = AppData.empty
        fractional.products = [Product(name: "Fracción", stockMilli: 1_500, priceCents: 100)]
        XCTAssertThrowsError(try AndroidBackupCodec.encode(fractional, password: "secreta1")) { error in
            guard let businessError = error as? BusinessError,
                  case .androidBackupUnsupported = businessError else {
                return XCTFail("Se esperaba un error de conversión explícito, no \(error)")
            }
        }
    }

    func testReconstructsMissingResultingStockForLegacyMovements() throws {
        let item = Product(name: "Agua", stockMilli: 9_000, priceCents: 100)
        let first = InventoryMovement(date: Date(timeIntervalSince1970: 100), productID: item.id,
            productName: item.name, deltaMilli: -1_000, reason: "Ajuste antiguo")
        let second = InventoryMovement(date: Date(timeIntervalSince1970: 200), productID: item.id,
            productName: item.name, deltaMilli: -2_000, reason: "Ajuste reciente")
        var app = AppData.empty
        app.products = [item]
        app.inventoryMovements = [first, second]

        let decoded = try AndroidBackupCodec.decode(try AndroidBackupCodec.encode(app, password: "secreta1"), password: "secreta1")
        XCTAssertEqual(decoded.inventoryMovements.first { $0.reason == first.reason }?.resultingStockMilli, 11_000)
        XCTAssertEqual(decoded.inventoryMovements.first { $0.reason == second.reason }?.resultingStockMilli, 9_000)
        let twice = try AndroidBackupCodec.decode(try AndroidBackupCodec.encode(decoded, password: "secreta1"), password: "secreta1")
        XCTAssertEqual(twice.products.first?.id, decoded.products.first?.id)
        XCTAssertEqual(Set(twice.inventoryMovements.map(\.id)), Set(decoded.inventoryMovements.map(\.id)))
    }

    func testStoreDetectsAndroidFormatWhenRestoring() throws {
        var initial = AppData.empty
        initial.products = [Product(name: "Agua", stockMilli: 2_000, priceCents: 100)]
        initial.settings.profile.transferCard = "9200123456789012"
        initial.settings.profile.transferPhone = "+5352123456"
        let source = ShalevStore(storage: AndroidBackupMemoryStorage(initial))
        let backup = try source.makeEncryptedBackup(password: "secreta1", format: .android)
        let destinationStorage = AndroidBackupMemoryStorage(AppData.empty)
        let destination = ShalevStore(storage: destinationStorage)

        try destination.restoreEncryptedBackup(backup, password: "secreta1")

        XCTAssertEqual(destination.data.products.first?.name, "Agua")
        XCTAssertEqual(destination.data.products.first?.stockMilli, 2_000)
        XCTAssertEqual(destination.data.settings.profile.paymentCard?.number, "9200123456789012")
        XCTAssertEqual(destination.data.settings.profile.paymentPhone?.number, "+5352123456")
        XCTAssertEqual(destinationStorage.value?.products.count, 1)
    }
}
