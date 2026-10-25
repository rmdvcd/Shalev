import XCTest
@testable import IShalevDomain

private final class SyncMemoryStorage: LocalStorePersisting {
    var value: AppData?
    func load() throws -> AppData? { value }
    func save(_ data: AppData) throws { value = data }
}

@MainActor
final class SyncCatalogTests: XCTestCase {
    private func fixture() -> (app: AppData, product: Product, shift: Shift, sale: Sale) {
        let product = Product(name: "Agua", kind: .product, stockMilli: 10_000, lowStockMilli: 1_000,
                              criticalStockMilli: 500, priceCents: 100, costCents: 50)
        let shift = Shift(openingCashCents: 1_000, openedBy: "Luis")
        let line = SaleLine(sourceID: product.id, kind: .product, name: product.name, category: product.category,
                            quantity: 2, unitPriceCents: 100, unitCostCents: 50)
        let sale = Sale(shiftID: shift.id, paymentMethod: .cash, lines: [line])
        var app = AppData.empty
        app.settings.profile.businessName = "La Esquina"
        app.products = [product]
        app.shifts = [shift]
        app.sales = [sale]
        app.inventoryMovements = [InventoryMovement(productID: product.id, productName: product.name, deltaMilli: -2_000,
            reason: "Venta", saleID: sale.id, shiftID: shift.id, resultingStockMilli: 8_000, actorName: "Luis")]
        app.cashMovements = [CashMovement(shiftID: shift.id, kind: .income, amountCents: 50, reason: "Propina")]
        app.products[0].stockMilli = 8_000
        return (app, product, shift, sale)
    }

    func testBatchIsIdempotentByUUIDAndOnlyConfirmsSavedRows() throws {
        let fixture = self.fixture()
        let batch = try SyncCatalog.pendingBatch(app: fixture.app)
        var principal = AppData.empty
        principal.products = fixture.app.products
        principal.products[0].stockMilli = 10_000
        let first = try SyncCatalog.applyBatch(batch, employeeID: 7, to: principal)
        XCTAssertEqual(first.app.sales.map(\.id), [fixture.sale.id])
        XCTAssertEqual(first.app.shifts.first?.employeeID, 7)
        XCTAssertEqual(first.app.products[0].stockMilli, 8_000)
        XCTAssertEqual(AndroidBackupCodec.dictionaries(first.received["ventas"]).count, 0)
        XCTAssertEqual((first.received["ventas"] as? [String])?.count, 1)
        XCTAssertEqual((first.received["caja"] as? [String])?.count, 1)

        let second = try SyncCatalog.applyBatch(batch, employeeID: 7, to: first.app)
        XCTAssertEqual(second.app.sales.count, 1)
        XCTAssertEqual(second.app.shifts.count, 1)
        XCTAssertEqual(second.app.cashMovements.count, 1)
        XCTAssertEqual(second.app.inventoryMovements.count, 1)
        XCTAssertEqual(second.app.products[0].stockMilli, 8_000)

        let acknowledged = SyncCatalog.applyReceipts(first.received, to: fixture.app)
        XCTAssertTrue(acknowledged.sales[0].synced)
        XCTAssertTrue(acknowledged.shifts[0].synced)
        XCTAssertTrue(acknowledged.cashMovements[0].synced)
        XCTAssertEqual(AndroidBackupCodec.dictionaries(try SyncCatalog.pendingBatch(app: acknowledged)["ventas"]).count, 0)
    }

    func testSnapshotReappliesOnlyUnconfirmedSaleMovements() throws {
        let fixture = self.fixture()
        let employee = LocalEmployee(name: "Luis", syncID: 7)
        let snapshot = try SyncCatalog.snapshot(app: fixture.app, employee: employee,
            license: SyncLicense(clase: "PERPETUA"), modules: ["VENTAS", "INVENTARIO"])
        let withPending = try SyncCatalog.applySnapshot(snapshot, to: fixture.app)
        XCTAssertEqual(withPending.products[0].stockMilli, 6_000)
        XCTAssertEqual(withPending.settings.profile.ownerName, "Luis")
        XCTAssertTrue(withPending.sales.map(\.id) == [fixture.sale.id])

        var principal = fixture.app
        principal.products[0].stockMilli = 8_000
        let currentSnapshot = try SyncCatalog.snapshot(app: principal, employee: employee,
            license: SyncLicense(clase: "PERPETUA"), modules: ["VENTAS"])
        var confirmed = fixture.app
        confirmed.sales[0].synced = true
        let applied = try SyncCatalog.applySnapshot(currentSnapshot, to: confirmed)
        XCTAssertEqual(applied.products[0].stockMilli, 8_000)
    }

    func testPhotosHaveMetadataAndSeparateBytes() throws {
        var fixture = self.fixture()
        fixture.app.products[0].imageData = Data([1, 2, 3, 4])
        let snapshot = try SyncCatalog.snapshot(app: fixture.app, employee: LocalEmployee(name: "Luis", syncID: 7),
            license: SyncLicense(clase: "PRUEBA", venceEn: 1), modules: [])
        let product = AndroidBackupCodec.dictionaries(snapshot["productos"])[0]
        XCTAssertNil(product["fotoData"])
        let photo = AndroidBackupCodec.dictionaries(snapshot["fotos"])[0]
        let name = AndroidBackupCodec.string(photo["nombre"])
        XCTAssertEqual(SyncCatalog.photo(named: name, app: fixture.app)?.data, Data([1, 2, 3, 4]))
    }

    func testRemoteOpenShiftDoesNotBlockLocalShift() throws {
        let fixture = self.fixture()
        let batch = try SyncCatalog.pendingBatch(app: fixture.app)
        var principal = fixture.app
        principal.shifts = [Shift(openingCashCents: 2_000, openedBy: "Dueña")]
        principal.sales = []
        principal.cashMovements = []
        principal.inventoryMovements = []
        let applied = try SyncCatalog.applyBatch(batch, employeeID: 9, to: principal).app
        let storage = SyncMemoryStorage()
        storage.value = applied
        let store = ShalevStore(storage: storage)
        XCTAssertEqual(store.openShift?.employeeID, nil)
        XCTAssertEqual(applied.shifts.filter { $0.closedAt == nil }.count, 2)
    }
}
