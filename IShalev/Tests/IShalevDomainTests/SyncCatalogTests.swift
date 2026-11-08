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
                              criticalStockMilli: 1_000, priceCents: 100, costCents: 50)
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
        app.normalizeLocations()
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
        XCTAssertEqual(first.app.sales[0].lines[0].sourceID, fixture.product.id)
        XCTAssertEqual(first.app.inventoryMovements[0].productID, fixture.product.id)
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
        var fixture = self.fixture()
        let alertDefaults = InventoryAlertDefaults(productLowMilli: 7_000, productCriticalMilli: 2_000,
            ingredientLowMilli: 9_500, ingredientCriticalMilli: 1_500)
        fixture.app.settings.inventoryAlertDefaults = alertDefaults
        fixture.app.settings.plannedEmployees = 3
        let employee = LocalEmployee(name: "Luis", permissions: [], syncID: 7)
        let snapshot = try SyncCatalog.snapshot(app: fixture.app, employee: employee,
            license: SyncLicense(clase: "PERPETUA"), modules: ["VENTAS", "INVENTARIO"])
        let preferences = try XCTUnwrap(snapshot["preferencias"] as? [String: Any])
        XCTAssertEqual(AndroidBackupCodec.int64(preferences["productoBajo"]), 7)
        var replica = fixture.app
        replica.settings.inventoryAlertDefaults = .recommended
        let withPending = try SyncCatalog.applySnapshot(snapshot, to: replica)
        XCTAssertEqual(withPending.settings.effectiveInventoryAlertDefaults, alertDefaults)
        XCTAssertEqual(withPending.settings.effectivePlannedEmployees, 3)
        XCTAssertEqual(withPending.products[0].stockMilli, 6_000)
        XCTAssertEqual(withPending.products[0].id, fixture.product.id)
        XCTAssertEqual(withPending.settings.profile.ownerName, "Luis")
        XCTAssertTrue(withPending.sales.map { $0.id } == [fixture.sale.id])

        var principal = fixture.app
        principal.products[0].stockMilli = 8_000
        let currentSnapshot = try SyncCatalog.snapshot(app: principal, employee: employee,
            license: SyncLicense(clase: "PERPETUA"), modules: ["VENTAS"])
        var confirmed = fixture.app
        confirmed.sales[0].synced = true
        let applied = try SyncCatalog.applySnapshot(currentSnapshot, to: confirmed)
        XCTAssertEqual(applied.products[0].stockMilli, 8_000)
    }

    func testSyncIsScopedToActiveLocationAndSnapshotKeepsOtherCatalogs() throws {
        var fixture = self.fixture()
        let other = Product(name: "Café Norte", localName: "Norte", stockMilli: 3_000, priceCents: 200)
        fixture.app.locations = [BusinessLocation(name: "Local"), BusinessLocation(name: "Norte")]
        fixture.app.selectedLocation = "Local"
        fixture.app.products.append(other)
        fixture.app.normalizeLocations()

        let employee = LocalEmployee(name: "Luis", permissions: [], syncID: 7)
        let snapshot = try SyncCatalog.snapshot(app: fixture.app, employee: employee,
            license: SyncLicense(clase: "PERPETUA"), modules: ["VENTAS"])
        XCTAssertEqual(AndroidBackupCodec.dictionaries(snapshot["productos"]).count, 1)
        let updated = try SyncCatalog.applySnapshot(snapshot, to: fixture.app)
        XCTAssertEqual(updated.products.count, 2)
        XCTAssertEqual(updated.products.first(where: { $0.id == other.id })?.localName, "Norte")
        XCTAssertEqual(AndroidBackupCodec.dictionaries(try SyncCatalog.pendingBatch(app: updated)["ventas"]).count, 1)

        var northReplica = fixture.app
        northReplica.selectedLocation = "Norte"
        let mapped = try SyncCatalog.applySnapshot(snapshot, to: northReplica)
        XCTAssertEqual(mapped.selectedLocation, "Norte")
        XCTAssertEqual(mapped.products.first(where: { $0.name == "Agua" })?.localName, "Local")
        var olderSnapshot = snapshot
        olderSnapshot.removeValue(forKey: "local")
        let backwardCompatible = try SyncCatalog.applySnapshot(olderSnapshot, to: northReplica)
        XCTAssertNotNil(backwardCompatible.products.first(where: { $0.name == "Agua" && $0.localName == "Norte" }))

        var north = updated
        north.selectedLocation = "Norte"
        XCTAssertEqual(AndroidBackupCodec.dictionaries(try SyncCatalog.pendingBatch(app: north)["ventas"]).count, 0)
    }

    func testSnapshotAndApplyAdjustmentsAreScopedWithoutLosingOtherLocations() throws {
        let localProduct = Product(name: "Agua Centro", localName: "Centro", priceCents: 100)
        let northProduct = Product(name: "Agua Norte", localName: "Norte", priceCents: 200)
        var app = AppData.empty
        app.locations = [BusinessLocation(name: "Centro"), BusinessLocation(name: "Norte")]
        app.selectedLocation = "Centro"
        app.products = [localProduct, northProduct]
        let shared = PriceAdjustment(name: "Efectivo", basisPoints: -500,
            productIDs: [localProduct.id, northProduct.id])
        let northOnly = PriceAdjustment(name: "Solo Norte", basisPoints: 1_000, productIDs: [northProduct.id])
        app.settings.priceAdjustments = [shared, northOnly]

        let snapshot = try SyncCatalog.snapshot(app: app,
            employee: LocalEmployee(name: "Luis", permissions: [], syncID: 7),
            license: SyncLicense(clase: "PERPETUA"), modules: ["VENTAS"])
        let rows = AndroidBackupCodec.dictionaries(snapshot["preajustes"])
        XCTAssertEqual(rows.count, 1)
        XCTAssertEqual((rows[0]["productoIds"] as? [Int64])?.count, 1)

        let received = try SyncCatalog.applySnapshot(snapshot, to: app)
        XCTAssertEqual(received.settings.priceAdjustments.count, 2)
        XCTAssertEqual(received.settings.priceAdjustments.first(where: { $0.id == shared.id })?.productIDs,
                       Set([localProduct.id, northProduct.id]))
        XCTAssertEqual(received.settings.priceAdjustments.first(where: { $0.id == northOnly.id })?.productIDs,
                       Set([northProduct.id]))
    }

    func testApprovedCountSurvivesAndroidIDsAndMillisecondDates() throws {
        var fixture = self.fixture()
        let product = fixture.app.products[0]
        let line = ShiftInventoryCount(productID: product.id, productName: product.name, category: product.category,
            unit: product.unit, expectedMilli: 10_000, countedMilli: 10_000,
            updatedAt: product.updatedAt ?? product.createdAt)
        fixture.app.shifts[0].closingCount = ShiftClosingCount(inventory: [line])
        fixture.app.shifts[0].countedCashCents = 0
        var approvedShift = fixture.app.shifts[0]
        approvedShift.closingCount?.approvedBy = "Dueña"
        approvedShift.closingCount?.approvedAt = Date(timeIntervalSince1970: 1_800_000_000)
        let row = try AndroidBackupCodec.exportShift(approvedShift, app: fixture.app)
        let result = try SyncCatalog.applyApprovedCount(row["conteoCierre"] as? [String: Any],
            shiftUUID: fixture.shift.id.uuidString.lowercased(), to: fixture.app)
        XCTAssertEqual(result.shifts[0].closingCount?.approvedBy, "Dueña")
        XCTAssertEqual(result.shifts[0].closingCount?.inventory[0].productID, product.id)
        XCTAssertEqual(result.shifts[0].closingCount?.inventory[0].updatedAt, line.updatedAt)
        XCTAssertFalse(result.shifts[0].synced)
    }

    func testPhotosHaveMetadataAndSeparateBytes() throws {
        var fixture = self.fixture()
        fixture.app.products[0].imageData = Data([1, 2, 3, 4])
        let snapshot = try SyncCatalog.snapshot(app: fixture.app, employee: LocalEmployee(name: "Luis", permissions: [], syncID: 7),
            license: SyncLicense(clase: "PRUEBA", venceEn: 1), modules: [])
        let product = AndroidBackupCodec.dictionaries(snapshot["productos"])[0]
        XCTAssertNil(product["fotoData"])
        let photo = AndroidBackupCodec.dictionaries(snapshot["fotos"])[0]
        let name = AndroidBackupCodec.string(photo["nombre"])
        XCTAssertEqual(SyncCatalog.photo(named: name, app: fixture.app)?.data, Data([1, 2, 3, 4]))
    }

    func testRemoteCommandsResolveRandomLocalUUIDs() throws {
        var fixture = self.fixture()
        fixture.app.products[0].imageData = Data([1, 2, 3])
        fixture.app.products[0].syncPhotoName = "agua.jpg"
        fixture.app.settings.priceAdjustments = [PriceAdjustment(name: "Descuento", basisPoints: -1_000, productIDs: [fixture.product.id])]
        let storage = SyncMemoryStorage(); storage.value = fixture.app
        let store = ShalevStore(storage: storage)
        var productRow = try AndroidBackupCodec.exportProduct(fixture.app.products[0])
        productRow["precioVentaCent"] = 125
        _ = try SyncCommands.apply(SyncJSON.message("producto_guardar", ["producto": productRow]), store: store)
        XCTAssertEqual(store.data.products.count, 1)
        XCTAssertEqual(store.data.products[0].id, fixture.product.id)
        XCTAssertEqual(store.data.products[0].priceCents, 125)
        XCTAssertEqual(store.data.products[0].imageData, Data([1, 2, 3]))
        _ = try SyncCommands.apply(SyncJSON.message("producto_stock", ["id": AndroidID.int64("product", fixture.product.id), "delta": -1]), store: store)
        XCTAssertEqual(store.data.products[0].stockMilli, 7_000)

        let adjustmentRow = try AndroidBackupCodec.exportAdjustment(fixture.app.settings.priceAdjustments[0], app: fixture.app)
        _ = try SyncCommands.apply(SyncJSON.message("preajuste_guardar", ["preajuste": adjustmentRow]), store: store)
        XCTAssertEqual(store.data.settings.priceAdjustments.count, 1)
        XCTAssertEqual(store.data.settings.priceAdjustments[0].productIDs, [fixture.product.id])
    }

    func testRemoteSaleChangesCannotMutateOrAcknowledgeAnotherLocation() throws {
        var fixture = self.fixture()
        var app = fixture.app
        app.locations = [BusinessLocation(name: "Centro"), BusinessLocation(name: "Norte")]
        app.selectedLocation = "Norte"
        app.shifts[0].localName = "Centro"
        app.sales[0].localName = "Centro"
        app.sales[0].employeeID = 7
        app.sales[0].changePending = true

        var northShift = Shift(openingCashCents: 0, openedBy: "Ana")
        northShift.localName = "Norte"
        let northLine = SaleLine(sourceID: fixture.product.id, kind: .product, name: fixture.product.name,
            category: fixture.product.category, quantity: 1, unitPriceCents: 100, unitCostCents: 50)
        var northSale = Sale(shiftID: northShift.id, paymentMethod: .cash, lines: [northLine])
        northSale.localName = "Norte"
        northSale.employeeID = 7
        northSale.changePending = true
        app.shifts.append(northShift)
        app.sales.append(northSale)

        let changeCentro: [String: Any] = ["uuid": fixture.sale.id.uuidString, "turnoUuid": fixture.shift.id.uuidString,
            "anuladaEn": Int64(1_800_000_000_000), "motivo": "Fuera de local"]
        let changeNorte: [String: Any] = ["uuid": northSale.id.uuidString, "turnoUuid": northShift.id.uuidString,
            "anuladaEn": Int64(1_800_000_001_000), "motivo": "Ajuste autorizado"]

        XCTAssertEqual(SyncCatalog.changes(for: 7, app: app).compactMap { $0["uuid"] as? String },
            [northSale.id.uuidString.lowercased()])
        let result = try SyncCatalog.applyChanges([changeCentro, changeNorte], to: app)
        XCTAssertEqual(result.applied, [northSale.id.uuidString.lowercased()])
        XCTAssertNil(result.app.sales.first(where: { $0.id == fixture.sale.id })?.cancelledAt)
        XCTAssertNotNil(result.app.sales.first(where: { $0.id == northSale.id })?.cancelledAt)

        let acknowledged = SyncCatalog.acknowledgeChanges(
            [fixture.sale.id.uuidString, northSale.id.uuidString], employeeID: 7, in: app)
        XCTAssertTrue(acknowledged.sales.first(where: { $0.id == fixture.sale.id })?.changePending == true)
        XCTAssertFalse(acknowledged.sales.first(where: { $0.id == northSale.id })?.changePending ?? true)
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
