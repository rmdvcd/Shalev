import Foundation
import XCTest
@testable import IShalevDomain

private final class MemoryStorage: LocalStorePersisting {
    var value: AppData?
    init(_ value: AppData? = nil) { self.value = value }
    func load() throws -> AppData? { value }
    func save(_ data: AppData) throws { value = data }
}

@MainActor
final class DomainParityTests: XCTestCase {
    private func product(name: String = "Agua", price: Int64 = 10_000, cost: Int64 = 5_000,
                         stock: Int64 = 20_000, kind: InventoryKind = .product) -> Product {
        Product(name: name, category: kind == .ingredient ? "Insumos" : kind == .prepared ? "Elaborado" : "Bebidas",
                kind: kind, stockMilli: stock, priceCents: price, costCents: cost)
    }

    private func makeStore(products: [Product]? = nil) -> ShalevStore {
        var initial = AppData.empty
        initial.settings.profile.ownerName = "María Pérez"
        initial.settings.profile.transferCard = "9200123456789012"
        initial.settings.profile.transferPhone = "+5352123456"
        initial.products = products ?? [product()]
        return ShalevStore(storage: MemoryStorage(initial))
    }

    private func customer(id: UUID = UUID(), identification: String = "00110112345") -> Customer {
        Customer(id: id, name: "  Ana   Pérez ", identification: identification, phone: "52123456")
    }

    func testSecondaryRoleCannotCancelOrModifySales() throws {
        let item = product(), store = makeStore(products: [item])
        try store.openShift(openingCashCents: 0)
        let cart = [CartDraft(sourceID: item.id, kind: .product, quantity: 1)]
        let sale = try store.recordSale(cart, payment: .cash)
        store.allowsSaleCorrections = false
        XCTAssertThrowsError(try store.cancelSale(sale.id, reason: "Venta duplicada")) {
            XCTAssertEqual($0 as? BusinessError, .saleCorrectionRequiresPrincipal)
        }
        XCTAssertThrowsError(try store.modifySale(sale.id, cart: cart, payment: .cash, transactionNumber: "", reason: "Cantidad incorrecta")) {
            XCTAssertEqual($0 as? BusinessError, .saleCorrectionRequiresPrincipal)
        }
        XCTAssertEqual(store.data.sales.count, 1)
        XCTAssertFalse(store.data.sales[0].isCancelled)
        store.allowsSaleCorrections = true
        try store.cancelSale(sale.id, reason: "Venta duplicada")
        XCTAssertTrue(store.data.sales[0].isCancelled)
    }

    func testPriceAdjustmentsOnlyAffectSelectedArticlesAndAccumulate() throws {
        let first = product(name: "Agua")
        let second = product(name: "Refresco")
        let store = makeStore(products: [first, second])
        try store.openShift(openingCashCents: 0)
        try store.savePriceAdjustment(PriceAdjustment(name: "Efectivo", basisPoints: -1_000, productIDs: [first.id]))
        try store.savePriceAdjustment(PriceAdjustment(name: "Adicional", basisPoints: -500, productIDs: [first.id]))

        let preview = try store.previewSale([
            CartDraft(sourceID: first.id, kind: .product, quantity: 1),
            CartDraft(sourceID: second.id, kind: .product, quantity: 1),
        ], payment: .cash)

        XCTAssertEqual(preview.lines.first(where: { $0.sourceID == first.id })?.unitPriceCents, 8_500)
        XCTAssertEqual(preview.lines.first(where: { $0.sourceID == second.id })?.unitPriceCents, 10_000)
        XCTAssertEqual(preview.lines.first(where: { $0.sourceID == first.id })?.basePriceCents, 10_000)
    }

    func testLegacyGlobalAdjustmentStillAppliesAfterReadingOldData() throws {
        let item = product()
        var initial = AppData.empty
        initial.products = [item]
        initial.settings.priceAdjustments = [PriceAdjustment(name: "Anterior", basisPoints: -1_000, productIDs: nil)]
        let store = ShalevStore(storage: MemoryStorage(initial))
        try store.openShift(openingCashCents: 0)

        let preview = try store.previewSale([CartDraft(sourceID: item.id, kind: .product, quantity: 1)], payment: .cash)
        XCTAssertEqual(preview.totalCents, 9_000)
    }

    func testTransferRequiresAndroidCustomerAndTransactionRules() throws {
        let item = product()
        let store = makeStore(products: [item])
        try store.openShift(openingCashCents: 0)
        let cart = [CartDraft(sourceID: item.id, kind: .product, quantity: 1)]

        XCTAssertThrowsError(try store.recordSale(cart, payment: .transfer, customer: customer())) { error in
            XCTAssertEqual(error as? BusinessError, .invalidTransactionNumber)
        }
        XCTAssertThrowsError(try store.recordSale(cart, payment: .transfer, transactionNumber: "BR601ADLM8997",
                                                   customer: Customer(name: "Ana", identification: "123", phone: "52123456"))) { error in
            XCTAssertEqual(error as? BusinessError, .invalidCustomer)
        }

        let sale = try store.recordSale(cart, payment: .transfer, transactionNumber: "br601adlm8997",
                                        customer: customer(), customerIsFixed: true)
        XCTAssertEqual(sale.transactionNumber, "BR601ADLM8997")
        XCTAssertEqual(sale.customer?.name, "Ana Pérez")
        XCTAssertEqual(sale.customer?.phone, "+5352123456")
        XCTAssertEqual(sale.sellerName, "María Pérez")
        XCTAssertEqual(store.data.customers.count, 1)
        XCTAssertEqual(store.purchases(for: store.data.customers[0]).map(\.id), [sale.id])
    }

    func testEditingFixedCustomerUpdatesPurchasesAndRejectsDuplicateIdentification() throws {
        let item = product()
        var other = customer(identification: "90121254321")
        other.name = "Otro Cliente"
        var initial = AppData.empty
        initial.settings.profile.ownerName = "María Pérez"
        initial.settings.profile.transferCard = "9200123456789012"
        initial.settings.profile.transferPhone = "+5352123456"
        initial.products = [item]
        initial.customers = [other]
        let store = ShalevStore(storage: MemoryStorage(initial))
        try store.openShift(openingCashCents: 0)
        let cart = [CartDraft(sourceID: item.id, kind: .product, quantity: 1)]
        let sale = try store.recordSale(cart, payment: .transfer, transactionNumber: "BR601ADLM8997",
                                        customer: customer(), customerIsFixed: true)

        var edited = store.data.customers.first(where: { $0.identification == "00110112345" })!
        edited.name = "Ana María Pérez"
        edited.identification = "00110199999"
        edited.phone = "0013055551234"
        try store.updateCustomer(edited)

        XCTAssertEqual(store.data.customers[0].name, "Ana María Pérez")
        XCTAssertEqual(store.data.sales.first(where: { $0.id == sale.id })?.customer?.identification, "00110199999")
        XCTAssertEqual(store.data.sales.first(where: { $0.id == sale.id })?.customer?.phone, "+13055551234")

        var duplicate = store.data.customers[0]
        duplicate.identification = other.identification
        XCTAssertThrowsError(try store.updateCustomer(duplicate)) { error in
            XCTAssertEqual(error as? BusinessError, .duplicateCustomer)
        }
        try store.removeFixedCustomer(edited.id)
        XCTAssertEqual(store.data.customers.map(\.id), [other.id])
        XCTAssertNotNil(store.data.sales.first(where: { $0.id == sale.id })?.customer)
    }

    func testClosingCountReconcilesOnlyChangedInventoryAndFreezesSummary() throws {
        let item = product(stock: 20_000)
        let store = makeStore(products: [item])
        try store.openShift(openingCashCents: 1_000)
        _ = try store.recordSale([CartDraft(sourceID: item.id, kind: .product, quantity: 2)], payment: .cash)
        XCTAssertEqual(store.data.products[0].stockMilli, 18_000)

        var count = try store.prepareClosingCount()
        XCTAssertEqual(count.inventory.map(\.productID), [item.id])
        XCTAssertEqual(count.inventory[0].expectedMilli, 18_000)
        count.inventory[0].countedMilli = 17_250
        count.otherCashCents = 21_000
        count.bills = [100: 1, 500: 2]
        count.otherCashCents = 0
        count.reason = "Merma comprobada al cerrar"
        try store.closeShift(count: count)

        XCTAssertNil(store.openShift)
        XCTAssertEqual(store.data.products[0].stockMilli, 17_250)
        XCTAssertEqual(store.data.shifts[0].countedCashCents, 110_000)
        XCTAssertEqual(store.data.shifts[0].summary?.saleCount, 1)
        XCTAssertEqual(store.data.shifts[0].summary?.units, 2)
        XCTAssertEqual(store.data.shifts[0].closingCount?.approvedBy, "María Pérez")
        let adjustment = store.data.inventoryMovements.first { $0.reason.hasPrefix("Conciliación de cierre") }
        XCTAssertEqual(adjustment?.deltaMilli, -750)
        XCTAssertEqual(adjustment?.shiftID, store.data.shifts[0].id)
    }

    func testClosingRejectsSnapshotWhenInventoryChangedWhileCounting() throws {
        let item = product()
        let store = makeStore(products: [item])
        try store.openShift(openingCashCents: 0)
        try store.adjustStock(productID: item.id, newStockMilli: 19_000, reason: "Venta manual")
        var count = try store.prepareClosingCount()
        count.otherCashCents = 0
        try store.adjustStock(productID: item.id, newStockMilli: 18_000, reason: "Nuevo ajuste")

        XCTAssertThrowsError(try store.closeShift(count: count)) { error in
            XCTAssertEqual(error as? BusinessError, .closingCountChanged)
        }
        XCTAssertNotNil(store.openShift)
    }

    func testClosingCanExplicitlySkipInventoryWithoutAdjustingStock() throws {
        let item = product()
        let store = makeStore(products: [item])
        try store.openShift(openingCashCents: 0)
        try store.adjustStock(productID: item.id, newStockMilli: 19_000, reason: "Venta manual")
        var count = try store.prepareClosingCount()
        count.inventoryCountSkipped = true
        count.inventory = []
        try store.closeShift(count: count)

        XCTAssertEqual(store.data.products[0].stockMilli, 19_000)
        XCTAssertEqual(store.data.shifts[0].closingCount?.inventoryCountSkipped, true)
        XCTAssertFalse(store.data.inventoryMovements.contains { $0.reason.hasPrefix("Conciliación de cierre") })
    }

    func testNumericInputRejectsExcessPrecision() {
        XCTAssertNil(Cup.parse("1.234"))
        XCTAssertNil(FixedNumber.parse("1.2345", multiplier: 1_000))
        XCTAssertEqual(Cup.parse("1,450.00")?.cents, 145_000)
        XCTAssertEqual(FixedNumber.parse("1.234", multiplier: 1_000), 1_234)
    }

    func testProductAndAdjustmentValidationMatchesBusinessLimits() throws {
        let store = makeStore()
        XCTAssertThrowsError(try store.saveProduct(product(price: 4_000, cost: 5_000))) { error in
            XCTAssertEqual(error as? BusinessError, .invalidPrice)
        }
        var invalidLevels = product()
        invalidLevels.lowStockMilli = 1_000
        invalidLevels.criticalStockMilli = 2_000
        XCTAssertThrowsError(try store.saveProduct(invalidLevels))
        XCTAssertThrowsError(try store.savePriceAdjustment(PriceAdjustment(name: "Global nuevo", basisPoints: 1_000,
                                                                            productIDs: []))) { error in
            XCTAssertEqual(error as? BusinessError, .invalidPriceAdjustment)
        }
    }

    func testMultiplePaymentAccountsAndSaleSnapshot() throws {
        let item = product()
        let store = makeStore(products: [item])
        try store.openShift(openingCashCents: 0)
        let firstCard = TransferCard(number: "9200111111111111", alias: "Principal")
        let secondCard = TransferCard(number: "9200222222222222", alias: "Secundaria")
        let firstPhone = TransferPhone(number: "52123456", alias: "Tienda")
        let secondPhone = TransferPhone(number: "+5358765432", alias: "Cobros")
        var profile = store.data.settings.profile
        profile.transferCards = [firstCard, secondCard]
        profile.transferPhones = [firstPhone, secondPhone]
        profile.paymentCardID = secondCard.id
        profile.paymentPhoneID = secondPhone.id
        try store.saveProfile(profile)
        XCTAssertEqual(store.data.settings.profile.paymentPhone?.number, "+5358765432")
        XCTAssertEqual(store.data.settings.profile.paymentCard?.id, secondCard.id)

        let sale = try store.recordSale([CartDraft(sourceID: item.id, kind: .product, quantity: 1)],
                                        payment: .transfer, transactionNumber: "abc123",
                                        customer: customer())
        XCTAssertEqual(sale.paymentCard, secondCard.number)
        XCTAssertEqual(sale.paymentPhone, "+5358765432")
        XCTAssertEqual(sale.transactionNumber, "ABC123")

        profile = store.data.settings.profile
        profile.paymentCardID = firstCard.id
        try store.saveProfile(profile)
        XCTAssertEqual(store.data.sales.first?.paymentCard, secondCard.number)

        profile.transferPhones = [firstPhone, TransferPhone(id: UUID(), number: firstPhone.number)]
        XCTAssertThrowsError(try store.saveProfile(profile)) {
            XCTAssertEqual($0 as? BusinessError, .invalidPaymentConfiguration)
        }
    }

    func testClosingCountSurvivesPortableBackup() throws {
        var initial = AppData.empty
        let shiftID = UUID()
        initial.shifts = [Shift(id: shiftID, closedAt: Date(), openingCashCents: 0, countedCashCents: 12_300,
                                closingCount: ShiftClosingCount(bills: [100: 1], otherCashCents: 2_300))]
        let decoded = try BackupCodec.decode(BackupCodec.encode(initial, password: "clave-segura"), password: "clave-segura")
        XCTAssertEqual(decoded.shifts[0].closingCount?.cashCents, 12_300)
        XCTAssertEqual(decoded.shifts[0].closingCount?.bills[100], 1)
    }

    func testSharedIngredientCapacityAndCashOutflowGuards() throws {
        let store = ShalevStore(storage: MemoryStorage())
        try store.openShift(openingCashCents: 20_000) // 200 CUP

        // Salida de efectivo mayor al disponible en caja es rechazada.
        XCTAssertThrowsError(try store.recordCashMovement(kind: .expense, amountCents: 25_000, reason: "Pago proveedor")) {
            XCTAssertEqual($0 as? BusinessError, .insufficientCash)
        }

        // 1.000 kg de queso compartido entre dos pizzas elaboradas y venta directa.
        let cheese = Product(name: "Queso", category: "Insumos", kind: .ingredient,
                             stockMilli: 1_000, unit: "kg", priceCents: 30_000, costCents: 20_000)
        try store.saveProduct(cheese)
        let pizzaA = Product(name: "Pizza Napolitana", category: "Elaborado", kind: .prepared,
                             stockMilli: 0, priceCents: 15_000, costCents: 0,
                             uses: [IngredientUse(ingredientID: cheese.id, milliUnits: 200)])
        let pizzaB = Product(name: "Pizza Jamón", category: "Elaborado", kind: .prepared,
                             stockMilli: 0, priceCents: 18_000, costCents: 0,
                             uses: [IngredientUse(ingredientID: cheese.id, milliUnits: 250)])
        try store.saveProduct(pizzaA)
        try store.saveProduct(pizzaB)

        let cart: [UUID: Int] = [pizzaA.id: 3] // usa 600 g, quedan 400 g
        XCTAssertEqual(store.maxAllowedQuantity(kind: .product, sourceID: pizzaA.id, in: cart), 5)
        XCTAssertEqual(store.maxAllowedQuantity(kind: .product, sourceID: pizzaB.id, in: cart), 1)
        XCTAssertEqual(store.maxAllowedQuantity(kind: .product, sourceID: cheese.id, in: cart), 0)

        XCTAssertThrowsError(try store.previewSale([
            CartDraft(sourceID: pizzaA.id, kind: .product, quantity: 3),
            CartDraft(sourceID: pizzaB.id, kind: .product, quantity: 2),
        ], payment: .cash))
    }
}
