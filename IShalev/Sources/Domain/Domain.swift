import Foundation
import Combine
import CryptoKit
import Security
import CommonCrypto

// MARK: - Dinero y cantidades

/// Importe exacto en centavos. La app no usa `Double` para cálculos de dinero.
struct Cup: Codable, Hashable, Comparable, Sendable {
    let cents: Int64

    init(cents: Int64) { self.cents = cents }

    static let zero = Cup(cents: 0)
    static func < (lhs: Cup, rhs: Cup) -> Bool { lhs.cents < rhs.cents }

    var formatted: String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.groupingSeparator = ","
        formatter.decimalSeparator = "."
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        let amount = Decimal(cents) / Decimal(100)
        return "\(formatter.string(from: NSDecimalNumber(decimal: amount)) ?? "0.00") CUP"
    }

    var inputValue: String {
        let amount = Decimal(cents) / Decimal(100)
        return NSDecimalNumber(decimal: amount).stringValue
    }

    static func parse(_ text: String) -> Cup? {
        guard let cents = FixedNumber.parse(text, multiplier: 100) else { return nil }
        return Cup(cents: cents)
    }
}

/// Conversión decimal fija usada para cantidades de insumos: 1 unidad = 1 000 milésimas.
enum FixedNumber {
    static func parse(_ text: String, multiplier: Int64) -> Int64? {
        var value = text.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "CUP", with: "", options: .caseInsensitive)
            .replacingOccurrences(of: "$", with: "")
            .replacingOccurrences(of: " ", with: "")
        guard !value.isEmpty else { return nil }

        let comma = value.lastIndex(of: ",")
        let dot = value.lastIndex(of: ".")
        if let comma, let dot {
            // El separador más a la derecha es el decimal; el otro se trata como agrupador.
            if comma > dot {
                value = value.replacingOccurrences(of: ".", with: "")
                value = value.replacingOccurrences(of: ",", with: ".")
            } else {
                value = value.replacingOccurrences(of: ",", with: "")
            }
        } else if comma != nil {
            let locale = Locale.current
            let decimalSeparator = locale.decimalSeparator ?? "."
            let groupingSeparator = locale.groupingSeparator ?? ","
            if groupingSeparator == ",", decimalSeparator != ",", Self.isGrouped(value, separator: ",") {
                value = value.replacingOccurrences(of: ",", with: "")
            } else {
                value = value.replacingOccurrences(of: ",", with: ".")
            }
        }

        guard let decimal = Decimal(string: value, locale: Locale(identifier: "en_US_POSIX")), decimal >= 0 else {
            return nil
        }
        var scaled = decimal * Decimal(multiplier)
        var rounded = Decimal()
        NSDecimalRound(&rounded, &scaled, 0, .plain)
        let number = NSDecimalNumber(decimal: rounded)
        guard number != .notANumber, number.int64Value >= 0 else { return nil }
        return number.int64Value
    }

    private static func isGrouped(_ value: String, separator: String) -> Bool {
        let groups = value.components(separatedBy: separator)
        guard groups.count > 1,
              (1...3).contains(groups[0].count),
              groups[0].allSatisfy(\.isNumber) else { return false }
        return groups.dropFirst().allSatisfy { $0.count == 3 && $0.allSatisfy(\.isNumber) }
    }

    static func formatMilli(_ value: Int64, unit: String = "") -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.locale = Locale(identifier: "es_US")
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 3
        let amount = Decimal(value) / Decimal(1_000)
        let number = formatter.string(from: NSDecimalNumber(decimal: amount)) ?? "0"
        return unit.isEmpty ? number : "\(number) \(unit)"
    }
}

// MARK: - Modelos de negocio

enum InventoryKind: String, Codable, CaseIterable, Identifiable, Equatable, Hashable {
    case product = "Producto"
    case ingredient = "Insumo"
    case prepared = "Elaborado"

    var id: String { rawValue }
    var symbol: String {
        switch self {
        case .product: "shippingbox.fill"
        case .ingredient: "leaf.fill"
        case .prepared: "takeoutbag.and.cup.and.straw.fill"
        }
    }
}

enum PaymentMethod: String, Codable, CaseIterable, Identifiable, Equatable, Hashable {
    case cash = "Efectivo"
    case transfer = "Transferencia"
    var id: String { rawValue }
    var symbol: String { self == .cash ? "banknote.fill" : "qrcode" }
}

struct ParsedTransferMessage: Equatable {
    var transactionNumber: String
    var amountCents: Int64?
}

enum TransferMessageParser {
    private static let transaction = try? NSRegularExpression(
        pattern: "transacci[oó]n\\s*(?:no\\.?|nro\\.?|n[º°]|#)?\\s*[:.]?\\s*([A-Z0-9]{6,30})\\b",
        options: [.caseInsensitive]
    )
    private static let alternative = try? NSRegularExpression(
        pattern: "\\b(?:nro|no|n[º°])\\.?\\s*[:.]\\s*([A-Z0-9]{6,30})\\b",
        options: [.caseInsensitive]
    )
    private static let amount = try? NSRegularExpression(
        pattern: "(?:monto|importe(?:\\s+pagado)?)\\s*:\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*CUP",
        options: [.caseInsensitive]
    )

    static func parse(_ text: String) -> ParsedTransferMessage? {
        let candidates = matches(transaction, in: text) + matches(alternative, in: text)
        guard let match = candidates.max(by: { $0.range.location < $1.range.location }), match.number.contains(where: \.isLetter),
              match.number.contains(where: \.isNumber) else { return nil }
        let prefix = (text as NSString).substring(to: match.range.location)
        let blockStart = prefix.range(of: "Banco ", options: [.backwards, .caseInsensitive])?.lowerBound ?? prefix.startIndex
        let relevant = String(prefix[blockStart...])
        let amountMatches = matches(amount, in: relevant)
        let cents: Int64?
        if let amountText = amountMatches.last?.number { cents = Cup.parse(amountText)?.cents }
        else { cents = nil }
        return ParsedTransferMessage(transactionNumber: match.number.uppercased(), amountCents: cents)
    }

    private struct Match { let number: String; let range: NSRange }
    private static func matches(_ regex: NSRegularExpression?, in text: String) -> [Match] {
        guard let regex else { return [] }
        let ns = text as NSString
        return regex.matches(in: text, range: NSRange(location: 0, length: ns.length)).compactMap { result in
            guard result.numberOfRanges > 1, result.range(at: 1).location != NSNotFound else { return nil }
            return Match(number: ns.substring(with: result.range(at: 1)), range: result.range(at: 1))
        }
    }
}

enum SaleItemKind: String, Codable {
    case product
    case service
}

enum CashMovementKind: String, Codable, CaseIterable, Identifiable, Equatable, Hashable {
    case income = "Entrada"
    case expense = "Salida"
    var id: String { rawValue }
}

struct IngredientUse: Codable, Identifiable, Equatable, Hashable {
    var id: UUID = UUID()
    var ingredientID: UUID
    /// Consumo por unidad del elaborado o del servicio, en milésimas.
    var milliUnits: Int64
}

struct Product: Codable, Identifiable, Equatable {
    var id: UUID = UUID()
    var name: String
    var category: String = "Otros"
    var kind: InventoryKind = .product
    var description: String = ""
    var imageData: Data? = nil
    var expiresAt: Date? = nil
    /// Los productos enteros también se guardan como milésimas (1 unidad = 1 000).
    var stockMilli: Int64 = 0
    var unit: String = "u"
    var lowStockMilli: Int64 = 0
    var criticalStockMilli: Int64 = 0
    var priceCents: Int64 = 0
    var costCents: Int64 = 0
    var uses: [IngredientUse] = []
    var isActive: Bool = true
    var createdAt: Date = Date()

    var displayStock: String {
        if kind == .prepared { return "Elaborado" }
        if kind == .ingredient { return FixedNumber.formatMilli(stockMilli, unit: unit) }
        return "\(stockMilli / 1_000) \(unit)"
    }
}

struct ServiceItem: Codable, Identifiable, Equatable {
    var id: UUID = UUID()
    var name: String
    var type: String = "General"
    var description: String = ""
    var priceCents: Int64 = 0
    var uses: [IngredientUse] = []
    var isActive: Bool = true
    var createdAt: Date = Date()
}

struct Customer: Codable, Identifiable, Equatable {
    var id: UUID = UUID()
    var name: String
    var identification: String = ""
    var phone: String = ""
    var updatedAt: Date = Date()
}

struct SaleLine: Codable, Identifiable, Equatable {
    var id: UUID = UUID()
    var sourceID: UUID
    var kind: SaleItemKind
    var name: String
    var category: String
    var quantity: Int
    var unitPriceCents: Int64
    var unitCostCents: Int64

    var subtotalCents: Int64 { unitPriceCents * Int64(quantity) }
    var costCents: Int64 { unitCostCents * Int64(quantity) }
}

struct Sale: Codable, Identifiable, Equatable {
    var id: UUID = UUID()
    var shiftID: UUID
    var date: Date = Date()
    var paymentMethod: PaymentMethod
    var lines: [SaleLine]
    var transactionNumber: String = ""
    var customer: Customer? = nil
    var customerIsFixed: Bool = false
    var isCancelled: Bool = false
    var cancelledAt: Date? = nil
    var cancellationReason: String? = nil
    var correctionOfSaleID: UUID? = nil

    var isService: Bool { !lines.isEmpty && lines.allSatisfy { $0.kind == .service } }
    var totalCents: Int64 { lines.reduce(0) { $0 + $1.subtotalCents } }
    var costCents: Int64 { lines.reduce(0) { $0 + $1.costCents } }
    var profitCents: Int64 { totalCents - costCents }
    var units: Int { lines.reduce(0) { $0 + $1.quantity } }
}

struct InventoryMovement: Codable, Identifiable, Equatable {
    var id: UUID = UUID()
    var date: Date = Date()
    var productID: UUID
    var productName: String
    /// Variación en milésimas; negativa = salida de existencias.
    var deltaMilli: Int64
    var reason: String
    var saleID: UUID? = nil
}

struct CashMovement: Codable, Identifiable, Equatable {
    var id: UUID = UUID()
    var shiftID: UUID
    var date: Date = Date()
    var kind: CashMovementKind
    var amountCents: Int64
    var reason: String
}

struct Shift: Codable, Identifiable, Equatable {
    var id: UUID = UUID()
    var openedAt: Date = Date()
    var closedAt: Date? = nil
    var openingCashCents: Int64
    var countedCashCents: Int64? = nil
}

struct PriceAdjustment: Codable, Identifiable, Equatable {
    var id: UUID = UUID()
    var name: String
    /// Puntos básicos: 1 000 = 10 %. Positivo = recargo; negativo = descuento.
    var basisPoints: Int
    var paymentMethod: PaymentMethod? = nil
    var minimumAmountCents: Int64? = nil
    var active: Bool = true
}

struct StoreProfile: Codable, Equatable {
    var businessName: String = "Mi negocio"
    var ownerName: String = ""
    var ownerFirstName: String? = nil
    var ownerLastName: String? = nil
    var identification: String = ""
    var licensePhone: String? = nil
    var transferPhone: String = ""
    var transferCard: String = ""
}

enum EmployeePermission: String, Codable, CaseIterable, Identifiable, Hashable {
    case sellProducts = "Vender productos"
    case sellServices = "Vender servicios"
    case viewInventory = "Ver inventario"
    case editInventory = "Editar inventario y servicios"
    case viewRecords = "Ver registros"
    case manageCash = "Abrir/cerrar turno y caja"
    case changePrices = "Cambiar precios"
    case exportReports = "Exportar PDF y Excel"
    case correctSales = "Modificar/anular ventas"
    case manageSettings = "Acceder a Ajustes"

    var id: String { rawValue }
    var symbol: String {
        switch self {
        case .sellProducts: "cart.fill"
        case .sellServices: "scissors"
        case .viewInventory: "archivebox.fill"
        case .editInventory: "pencil.and.list.clipboard"
        case .viewRecords: "clock.arrow.circlepath"
        case .manageCash: "banknote.fill"
        case .changePrices: "dollarsign.circle.fill"
        case .exportReports: "square.and.arrow.up.fill"
        case .correctSales: "pencil.and.list.clipboard"
        case .manageSettings: "gearshape.fill"
        }
    }
}

struct LocalEmployee: Codable, Identifiable, Equatable {
    var id: UUID = UUID()
    var name: String
    var permissions: Set<EmployeePermission>
    var createdAt: Date = Date()
}

struct AppSettings: Codable, Equatable {
    var profile: StoreProfile = StoreProfile()
    var priceAdjustments: [PriceAdjustment] = []
}

struct AppData: Codable, Equatable {
    var schemaVersion: Int = 1
    var settings: AppSettings = AppSettings()
    var products: [Product] = []
    var services: [ServiceItem] = []
    var sales: [Sale] = []
    var shifts: [Shift] = []
    var inventoryMovements: [InventoryMovement] = []
    var cashMovements: [CashMovement] = []
    var customers: [Customer] = []
    var employees: [LocalEmployee]? = nil

    static let empty = AppData()
}

struct CartDraft: Identifiable, Equatable {
    var sourceID: UUID
    var kind: SaleItemKind
    var quantity: Int
    var id: UUID { sourceID }
}

struct SalePreview: Equatable {
    var lines: [SaleLine]
    var baseTotalCents: Int64
    var totalCents: Int64 { lines.reduce(0) { $0 + $1.subtotalCents } }
}

enum StatsPeriod: String, CaseIterable, Identifiable, Equatable {
    case today = "Hoy"
    case week = "Esta semana"
    case month = "Este mes"
    case year = "Este año"
    case all = "Todo"
    var id: String { rawValue }
}

enum RecordsTab: String, CaseIterable, Identifiable, Equatable {
    case sales = "Ventas"
    case services = "Servicios"
    case transfers = "Transferencias"
    case movements = "Movimientos"
    case customers = "Clientes"
    case shifts = "Turnos"
    var id: String { rawValue }
}

enum BusinessError: LocalizedError {
    case invalidName
    case invalidAmount
    case shiftAlreadyOpen
    case shiftRequired
    case noOpenShift
    case turnClosed
    case saleAlreadyCancelled
    case emptyCart
    case unavailableItem(String)
    case insufficientStock(String)
    case invalidReason
    case invalidPaymentDetails
    case invalidRecipe
    case storageUnavailable
    case backupPassword
    case backupInvalid
    case unsupportedBackup

    var errorDescription: String? {
        switch self {
        case .invalidName: "Escribe un nombre válido."
        case .invalidAmount: "El importe debe ser mayor que cero."
        case .shiftAlreadyOpen: "Ya hay un turno abierto."
        case .shiftRequired: "Abre un turno para realizar esta operación."
        case .noOpenShift: "No hay un turno abierto."
        case .turnClosed: "Solo se pueden editar o anular ventas de turnos abiertos."
        case .saleAlreadyCancelled: "Esta venta ya está anulada."
        case .emptyCart: "Agrega al menos un artículo al carrito."
        case .unavailableItem(let name): "No se pudo encontrar «\(name)» en el catálogo."
        case .insufficientStock(let name): "No hay existencias suficientes de «\(name)»."
        case .invalidReason: "Escribe un motivo de entre 3 y 60 caracteres."
        case .invalidPaymentDetails: "Configura una tarjeta válida en Ajustes → Pago electrónico."
        case .invalidRecipe: "La receta debe contener insumos activos de tipo Insumo y cantidades válidas."
        case .storageUnavailable: "El almacenamiento cifrado no está disponible. No se guardaron cambios."
        case .backupPassword: "La contraseña debe tener al menos 8 caracteres."
        case .backupInvalid: "No se pudo abrir el respaldo. Comprueba la contraseña y que el archivo sea válido."
        case .unsupportedBackup: "Este respaldo pertenece a una versión no compatible de Shalev para iOS."
        }
    }
}

// MARK: - Persistencia cifrada y operaciones de negocio

@MainActor
final class ShalevStore: ObservableObject {
    @Published private(set) var data: AppData
    @Published private(set) var loadError: String?

    private let storage: EncryptedLocalStore
    private var readOnlyAfterLoadFailure = false

    init(storage: EncryptedLocalStore = EncryptedLocalStore()) {
        self.storage = storage
        var initial = AppData.empty
        var loadMessage: String?
        var readOnly = false
        do {
            if let saved = try storage.load() { initial = saved }
        } catch {
            loadMessage = "No se pudieron abrir los datos cifrados. La app no los reemplazará automáticamente."
            readOnly = true
        }
        self.data = initial
        self.loadError = loadMessage
        self.readOnlyAfterLoadFailure = readOnly
    }

    var activeProducts: [Product] { data.products.filter(\.isActive).sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending } }
    var activeServices: [ServiceItem] { data.services.filter(\.isActive).sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending } }
    var employees: [LocalEmployee] { data.employees ?? [] }
    var openShift: Shift? { data.shifts.first(where: { $0.closedAt == nil }) }

    func saveEmployee(_ incoming: LocalEmployee) throws {
        let name = incoming.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard (2...40).contains(name.count),
              !employees.contains(where: { $0.id != incoming.id && $0.name.localizedCaseInsensitiveCompare(name) == .orderedSame }) else {
            throw BusinessError.invalidName
        }
        var next = data
        var employee = incoming
        employee.name = name
        var list = next.employees ?? []
        if let index = list.firstIndex(where: { $0.id == employee.id }) { list[index] = employee }
        else { list.append(employee) }
        next.employees = list
        try commit(next)
    }

    func deleteEmployee(_ id: UUID) throws {
        var next = data
        next.employees = (next.employees ?? []).filter { $0.id != id }
        try commit(next)
    }

    func saveProduct(_ incoming: Product) throws {
        guard !incoming.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw BusinessError.invalidName }
        guard (0...9_000_000_000_000).contains(incoming.priceCents),
              (0...9_000_000_000_000).contains(incoming.costCents),
              (0...9_000_000_000_000).contains(incoming.stockMilli),
              (0...9_000_000_000_000).contains(incoming.lowStockMilli),
              (0...9_000_000_000_000).contains(incoming.criticalStockMilli),
              incoming.uses.allSatisfy({ (1...1_000_000_000).contains($0.milliUnits) }) else { throw BusinessError.invalidAmount }
        if incoming.kind == .prepared {
            let ingredientIDs = Set(data.products.filter { $0.isActive && $0.kind == .ingredient }.map(\.id))
            guard !incoming.uses.isEmpty,
                  incoming.uses.allSatisfy({ ingredientIDs.contains($0.ingredientID) }) else { throw BusinessError.invalidRecipe }
        }
        var next = data
        var product = incoming
        product.name = product.name.trimmingCharacters(in: .whitespacesAndNewlines)
        product.category = product.kind == .ingredient ? "Insumos" : product.kind == .prepared ? "Elaborado" : product.category.trimmingCharacters(in: .whitespacesAndNewlines)
        if product.category.isEmpty { product.category = "Otros" }
        if product.kind != .prepared { product.uses = [] }
        if let previous = next.products.first(where: { $0.id == product.id }) {
            let delta = (product.kind == .prepared ? 0 : product.stockMilli) - (previous.kind == .prepared ? 0 : previous.stockMilli)
            if delta != 0 {
                next.inventoryMovements.insert(InventoryMovement(productID: product.id, productName: product.name,
                    deltaMilli: delta, reason: "Ajuste manual"), at: 0)
            }
        } else if product.kind != .prepared && product.stockMilli > 0 {
            next.inventoryMovements.insert(InventoryMovement(productID: product.id, productName: product.name,
                deltaMilli: product.stockMilli, reason: "Alta inicial"), at: 0)
        }
        try upsert(product, in: &next.products)
        try commit(next)
    }

    func deleteProduct(_ id: UUID) throws {
        var next = data
        guard let index = next.products.firstIndex(where: { $0.id == id }) else { return }
        if next.products[index].kind != .prepared && next.products[index].stockMilli > 0 {
            next.inventoryMovements.insert(InventoryMovement(productID: id, productName: next.products[index].name,
                deltaMilli: -next.products[index].stockMilli, reason: "Baja de inventario"), at: 0)
        }
        next.products[index].isActive = false
        try commit(next)
    }

    func adjustStock(productID: UUID, newStockMilli: Int64, reason: String) throws {
        guard let current = data.products.first(where: { $0.id == productID && $0.isActive }) else { throw BusinessError.invalidName }
        guard current.kind != .prepared, (0...9_000_000_000_000).contains(newStockMilli) else { throw BusinessError.invalidAmount }
        let note = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        guard note.count >= 3, note.count <= 60 else { throw BusinessError.invalidReason }
        var next = data
        guard let index = next.products.firstIndex(where: { $0.id == productID }) else { throw BusinessError.invalidName }
        let delta = newStockMilli - current.stockMilli
        next.products[index].stockMilli = newStockMilli
        next.inventoryMovements.append(InventoryMovement(productID: productID, productName: current.name, deltaMilli: delta, reason: note))
        try commit(next)
    }

    func saveService(_ incoming: ServiceItem) throws {
        guard !incoming.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw BusinessError.invalidName }
        guard (0...9_000_000_000_000).contains(incoming.priceCents),
              incoming.uses.allSatisfy({ (1...1_000_000_000).contains($0.milliUnits) }) else { throw BusinessError.invalidAmount }
        let ingredientIDs = Set(data.products.filter { $0.isActive && $0.kind == .ingredient }.map(\.id))
        guard incoming.uses.allSatisfy({ ingredientIDs.contains($0.ingredientID) }) else { throw BusinessError.invalidRecipe }
        var service = incoming
        service.name = service.name.trimmingCharacters(in: .whitespacesAndNewlines)
        service.type = service.type.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "General" : service.type.trimmingCharacters(in: .whitespacesAndNewlines)
        var next = data
        try upsert(service, in: &next.services)
        try commit(next)
    }

    func deleteService(_ id: UUID) throws {
        var next = data
        guard let index = next.services.firstIndex(where: { $0.id == id }) else { return }
        next.services[index].isActive = false
        try commit(next)
    }

    func saveProfile(_ profile: StoreProfile) throws {
        var next = data
        next.settings.profile = profile
        try commit(next)
    }

    func savePriceAdjustment(_ adjustment: PriceAdjustment) throws {
        let name = adjustment.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, name.count <= 60,
              (-9_000...100_000).contains(adjustment.basisPoints),
              adjustment.minimumAmountCents.map({ (0...9_000_000_000_000).contains($0) }) ?? true else { throw BusinessError.invalidAmount }
        var next = data
        var normalized = adjustment
        normalized.name = name
        try upsert(normalized, in: &next.settings.priceAdjustments)
        try commit(next)
    }

    func deletePriceAdjustment(_ id: UUID) throws {
        var next = data
        next.settings.priceAdjustments.removeAll { $0.id == id }
        try commit(next)
    }

    func openShift(openingCashCents: Int64) throws {
        guard openShift == nil else { throw BusinessError.shiftAlreadyOpen }
        guard (0...9_000_000_000_000).contains(openingCashCents) else { throw BusinessError.invalidAmount }
        var next = data
        next.shifts.insert(Shift(openingCashCents: openingCashCents), at: 0)
        try commit(next)
    }

    func closeShift(countedCashCents: Int64) throws {
        guard let current = openShift else { throw BusinessError.noOpenShift }
        guard (0...9_000_000_000_000).contains(countedCashCents) else { throw BusinessError.invalidAmount }
        var next = data
        guard let index = next.shifts.firstIndex(where: { $0.id == current.id }) else { throw BusinessError.noOpenShift }
        next.shifts[index].countedCashCents = countedCashCents
        next.shifts[index].closedAt = Date()
        try commit(next)
    }

    func expectedCashCents(for shiftID: UUID) -> Int64 {
        let shift = data.shifts.first(where: { $0.id == shiftID })
        let cashSales = data.sales.filter { $0.shiftID == shiftID && !$0.isCancelled && $0.paymentMethod == .cash }.reduce(Int64(0)) { $0 + $1.totalCents }
        let movements = data.cashMovements.filter { $0.shiftID == shiftID }.reduce(Int64(0)) { total, movement in
            total + (movement.kind == .income ? movement.amountCents : -movement.amountCents)
        }
        return (shift?.openingCashCents ?? 0) + cashSales + movements
    }

    func recordCashMovement(kind: CashMovementKind, amountCents: Int64, reason: String) throws {
        guard let shift = openShift else { throw BusinessError.shiftRequired }
        let note = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        guard (1...9_000_000_000_000).contains(amountCents) else { throw BusinessError.invalidAmount }
        guard (3...60).contains(note.count) else { throw BusinessError.invalidReason }
        var next = data
        next.cashMovements.insert(CashMovement(shiftID: shift.id, kind: kind, amountCents: amountCents, reason: note), at: 0)
        try commit(next)
    }

    func estimatedCostCents(for product: Product) -> Int64 { unitCost(for: product) }
    func estimatedCostCents(for service: ServiceItem) -> Int64 { unitCost(for: service.uses) }

    func availableQuantity(kind: SaleItemKind, sourceID: UUID, returningSaleID: UUID? = nil) -> Int? {
        let credits = returningSaleID.map(inventoryCredits(for:)) ?? [:]
        switch kind {
        case .product:
            guard let product = data.products.first(where: { $0.id == sourceID && $0.isActive }) else { return nil }
            if product.kind == .prepared { return availableForUses(product.uses, credits: credits) }
            let units = safeAvailable(stockMilli: product.stockMilli, creditMilli: credits[sourceID] ?? 0) / 1_000
            return units > Int64(Int.max) ? Int.max : Int(units)
        case .service:
            guard let service = data.services.first(where: { $0.id == sourceID && $0.isActive }) else { return nil }
            return service.uses.isEmpty ? Int.max : availableForUses(service.uses, credits: credits)
        }
    }

    func previewSale(_ cart: [CartDraft], payment: PaymentMethod, returningSaleID: UUID? = nil) throws -> SalePreview {
        guard !cart.isEmpty else { throw BusinessError.emptyCart }
        let baseLines = try cart.map { draft -> SaleLine in
            guard (1...100_000).contains(draft.quantity) else { throw BusinessError.invalidAmount }
            switch draft.kind {
            case .product:
                guard let product = data.products.first(where: { $0.id == draft.sourceID && $0.isActive }) else {
                    throw BusinessError.unavailableItem("Artículo")
                }
                guard (availableQuantity(kind: .product, sourceID: product.id, returningSaleID: returningSaleID) ?? 0) >= draft.quantity else {
                    throw BusinessError.insufficientStock(product.name)
                }
                let cost = unitCost(for: product)
                return SaleLine(sourceID: product.id, kind: .product, name: product.name, category: product.category,
                                quantity: draft.quantity, unitPriceCents: product.priceCents, unitCostCents: cost)
            case .service:
                guard let service = data.services.first(where: { $0.id == draft.sourceID && $0.isActive }) else {
                    throw BusinessError.unavailableItem("Servicio")
                }
                guard (availableQuantity(kind: .service, sourceID: service.id, returningSaleID: returningSaleID) ?? Int.max) >= draft.quantity else {
                    throw BusinessError.insufficientStock(service.name)
                }
                return SaleLine(sourceID: service.id, kind: .service, name: service.name, category: service.type,
                                quantity: draft.quantity, unitPriceCents: service.priceCents, unitCostCents: unitCost(for: service.uses))
            }
        }
        let baseTotals = try checkedTotals(for: baseLines)
        let baseTotal = baseTotals.revenue
        let applicable = data.settings.priceAdjustments.filter { adjustment in
            adjustment.active && (adjustment.paymentMethod == nil || adjustment.paymentMethod == payment)
                && (adjustment.minimumAmountCents.map { baseTotal >= $0 } ?? true)
        }
        let adjusted = baseLines.map { line in
            let percent = applicable.reduce(0) { $0 + $1.basisPoints }
            let result = Self.applyBasisPoints(line.unitPriceCents, percent)
            return SaleLine(sourceID: line.sourceID, kind: line.kind, name: line.name, category: line.category,
                            quantity: line.quantity, unitPriceCents: result, unitCostCents: line.unitCostCents)
        }
        _ = try checkedTotals(for: adjusted)
        return SalePreview(lines: adjusted, baseTotalCents: baseTotal)
    }

    @discardableResult
    func recordSale(_ cart: [CartDraft], payment: PaymentMethod, transactionNumber: String = "",
                    customer: Customer? = nil, customerIsFixed: Bool = false) throws -> Sale {
        guard let shift = openShift else { throw BusinessError.shiftRequired }
        let transaction = transactionNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        guard transaction.count <= 100 else { throw BusinessError.invalidAmount }
        let preview = try previewSale(cart, payment: payment)
        let sale = Sale(shiftID: shift.id, paymentMethod: payment, lines: preview.lines,
                        transactionNumber: transaction, customer: customer, customerIsFixed: customerIsFixed)
        var next = data

        try applyInventory(lines: preview.lines, to: &next, saleID: sale.id, reason: "Venta")
        next.sales.insert(sale, at: 0)
        if customerIsFixed, let customer, !customer.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            if let existing = next.customers.firstIndex(where: {
                (!customer.identification.isEmpty && $0.identification == customer.identification)
                    || (!customer.phone.isEmpty && $0.phone == customer.phone)
            }) {
                next.customers[existing] = Customer(id: next.customers[existing].id, name: customer.name,
                    identification: customer.identification, phone: customer.phone, updatedAt: Date())
            } else {
                next.customers.insert(customer, at: 0)
            }
        }
        try commit(next)
        return sale
    }

    func cancelSale(_ saleID: UUID, reason: String) throws {
        guard let original = data.sales.first(where: { $0.id == saleID }) else { throw BusinessError.unavailableItem("venta") }
        guard !original.isCancelled else { throw BusinessError.saleAlreadyCancelled }
        guard let shift = data.shifts.first(where: { $0.id == original.shiftID }) else { throw BusinessError.turnClosed }
        guard shift.closedAt == nil, openShift?.id == shift.id else { throw BusinessError.turnClosed }
        let note = reason.trimmingCharacters(in: .whitespacesAndNewlines).split(whereSeparator: \.isWhitespace).joined(separator: " ")
        guard (3...60).contains(note.count) else { throw BusinessError.invalidReason }

        var next = data
        try restoreInventory(for: original, in: &next, reason: "Anulación · \(note)")
        guard let index = next.sales.firstIndex(where: { $0.id == saleID }) else { throw BusinessError.unavailableItem("venta") }
        next.sales[index].isCancelled = true
        next.sales[index].cancelledAt = Date()
        next.sales[index].cancellationReason = note
        try commit(next)
    }

    /// Modifica las cantidades de una venta abierta de forma atómica: repone los movimientos originales, registra la
    /// corrección en el mismo turno y conserva los precios/costos históricos de los artículos que ya estaban.
    func modifySale(_ saleID: UUID, cart: [CartDraft], payment: PaymentMethod, transactionNumber: String,
                    reason: String) throws -> Sale {
        guard let original = data.sales.first(where: { $0.id == saleID }) else { throw BusinessError.unavailableItem("venta") }
        guard !original.isCancelled else { throw BusinessError.saleAlreadyCancelled }
        guard let shift = data.shifts.first(where: { $0.id == original.shiftID }) else { throw BusinessError.turnClosed }
        guard shift.closedAt == nil, openShift?.id == shift.id else { throw BusinessError.turnClosed }
        guard !cart.isEmpty else { throw BusinessError.emptyCart }
        let note = reason.trimmingCharacters(in: .whitespacesAndNewlines).split(whereSeparator: \.isWhitespace).joined(separator: " ")
        guard (3...60).contains(note.count) else { throw BusinessError.invalidReason }
        let transaction = transactionNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        guard transaction.count <= 100 else { throw BusinessError.invalidAmount }
        let preview = try previewSale(cart, payment: payment, returningSaleID: saleID)
        let oldPrices = original.lines.reduce(into: [String: SaleLine]()) { result, line in
            result["\(line.kind.rawValue)-\(line.sourceID.uuidString)"] = line
        }
        let adjustedLines = preview.lines.map { line -> SaleLine in
            guard let old = oldPrices["\(line.kind.rawValue)-\(line.sourceID.uuidString)"] else { return line }
            return SaleLine(id: line.id, sourceID: line.sourceID, kind: line.kind, name: line.name, category: line.category,
                            quantity: line.quantity, unitPriceCents: old.unitPriceCents, unitCostCents: old.unitCostCents)
        }
        _ = try checkedTotals(for: adjustedLines)
        let replacement = Sale(shiftID: original.shiftID, paymentMethod: payment, lines: adjustedLines,
                               transactionNumber: transaction, customer: original.customer,
                               customerIsFixed: original.customerIsFixed, correctionOfSaleID: original.id)
        var next = data
        try restoreInventory(for: original, in: &next, reason: "Corrección · devolución · \(note)")
        try applyInventory(lines: replacement.lines, to: &next, saleID: replacement.id, reason: "Corrección de venta")
        guard let originalIndex = next.sales.firstIndex(where: { $0.id == original.id }) else { throw BusinessError.unavailableItem("venta") }
        next.sales[originalIndex].isCancelled = true
        next.sales[originalIndex].cancelledAt = Date()
        next.sales[originalIndex].cancellationReason = "Modificada · \(note)"
        next.sales.insert(replacement, at: 0)
        try commit(next)
        return replacement
    }

    func sales(for period: StatsPeriod, now: Date = Date()) -> [Sale] {
        let calendar = Calendar.current
        return data.sales.filter { sale in
            guard !sale.isCancelled else { return false }
            switch period {
            case .today: return calendar.isDate(sale.date, inSameDayAs: now)
            case .week: return calendar.isDate(sale.date, equalTo: now, toGranularity: .weekOfYear)
            case .month: return calendar.isDate(sale.date, equalTo: now, toGranularity: .month)
            case .year: return calendar.isDate(sale.date, equalTo: now, toGranularity: .year)
            case .all: return true
            }
        }
    }

    func monthlySales(last months: Int = 12, now: Date = Date()) -> [(label: String, cents: Int64)] {
        let calendar = Calendar.current
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "es_ES")
        formatter.dateFormat = "MMM"
        return (0..<months).reversed().compactMap { offset in
            guard let month = calendar.date(byAdding: .month, value: -offset, to: calendar.date(from: calendar.dateComponents([.year, .month], from: now)) ?? now),
                  let interval = calendar.dateInterval(of: .month, for: month) else { return nil }
            let total = data.sales.filter { !$0.isCancelled && !$0.isService && $0.date >= interval.start && $0.date < interval.end }
                .reduce(Int64(0)) { $0 + $1.totalCents }
            return (formatter.string(from: month).lowercased(), total)
        }
    }

    func makeEncryptedBackup(password: String) throws -> Data {
        guard !readOnlyAfterLoadFailure else { throw BusinessError.storageUnavailable }
        return try BackupCodec.encode(data, password: password)
    }

    func restoreEncryptedBackup(_ backup: Data, password: String) throws {
        guard !readOnlyAfterLoadFailure else { throw BusinessError.storageUnavailable }
        let restored = try BackupCodec.decode(backup, password: password)
        guard restored.schemaVersion == AppData.empty.schemaVersion else { throw BusinessError.unsupportedBackup }
        try commit(restored)
    }

    func demoData() throws {
        let flour = Product(name: "Harina de trigo", category: "Insumos", kind: .ingredient, stockMilli: 8_500,
                            unit: "kg", lowStockMilli: 3_000, criticalStockMilli: 1_000, costCents: 250_00)
        let coffee = Product(name: "Café molido", category: "Insumos", kind: .ingredient, stockMilli: 2_400,
                             unit: "kg", lowStockMilli: 1_000, criticalStockMilli: 500, costCents: 600_00)
        let sandwich = Product(name: "Sándwich de jamón", category: "Elaborado", kind: .prepared, priceCents: 220_00,
                               costCents: 85_00, uses: [IngredientUse(ingredientID: flour.id, milliUnits: 100)])
        let bottled = Product(name: "Agua mineral 500 ml", category: "Bebidas", kind: .product, stockMilli: 26_000,
                              lowStockMilli: 10_000, criticalStockMilli: 3_000, priceCents: 75_00, costCents: 45_00)
        let shampoo = Product(name: "Champú 250 ml", category: "Aseo personal", kind: .product, stockMilli: 12_000,
                              lowStockMilli: 5_000, criticalStockMilli: 2_000, priceCents: 340_00, costCents: 240_00)
        let service = ServiceItem(name: "Café para llevar", type: "Cafetería", priceCents: 120_00,
                                  uses: [IngredientUse(ingredientID: coffee.id, milliUnits: 20)])
        let profile = StoreProfile(businessName: "La Esquina", ownerName: "María", transferPhone: "+5352123456", transferCard: "9200123456789012")
        var next = AppData.empty
        next.settings.profile = profile
        next.products = [flour, coffee, sandwich, bottled, shampoo]
        next.services = [service]
        next.settings.priceAdjustments = []
        next.shifts = [Shift(openingCashCents: 5_000_00)]
        try commit(next)
    }

    // MARK: Ayudantes de dominio

    private func checkedTotals(for lines: [SaleLine]) throws -> (revenue: Int64, cost: Int64) {
        var revenue: Int64 = 0
        var cost: Int64 = 0
        for line in lines {
            guard line.quantity > 0, line.unitPriceCents >= 0, line.unitCostCents >= 0 else { throw BusinessError.invalidAmount }
            let quantity = Int64(line.quantity)
            let lineRevenue = line.unitPriceCents.multipliedReportingOverflow(by: quantity)
            let lineCost = line.unitCostCents.multipliedReportingOverflow(by: quantity)
            guard !lineRevenue.overflow, !lineCost.overflow else { throw BusinessError.invalidAmount }
            let nextRevenue = revenue.addingReportingOverflow(lineRevenue.partialValue)
            let nextCost = cost.addingReportingOverflow(lineCost.partialValue)
            guard !nextRevenue.overflow, !nextCost.overflow else { throw BusinessError.invalidAmount }
            revenue = nextRevenue.partialValue
            cost = nextCost.partialValue
        }
        return (revenue, cost)
    }

    private func availableForUses(_ uses: [IngredientUse], credits: [UUID: Int64] = [:]) -> Int {
        guard !uses.isEmpty else { return 0 }
        let grouped = Dictionary(grouping: uses, by: \.ingredientID)
        let counts = grouped.compactMap { ingredientID, ingredientUses -> Int? in
            let required = ingredientUses.reduce(Int64(0)) { total, use in
                let sum = total.addingReportingOverflow(use.milliUnits)
                return sum.overflow ? Int64.max : sum.partialValue
            }
            guard required > 0,
                  let ingredient = data.products.first(where: { $0.id == ingredientID && $0.isActive && $0.kind == .ingredient }) else { return nil }
            let count = safeAvailable(stockMilli: ingredient.stockMilli, creditMilli: credits[ingredientID] ?? 0) / required
            return count > Int64(Int.max) ? Int.max : Int(count)
        }
        return counts.count == grouped.count ? (counts.min() ?? 0) : 0
    }

    private func inventoryCredits(for saleID: UUID) -> [UUID: Int64] {
        var result: [UUID: Int64] = [:]
        for movement in data.inventoryMovements where movement.saleID == saleID && movement.deltaMilli < 0 {
            let amount = movement.deltaMilli == Int64.min ? Int64.max : -movement.deltaMilli
            let sum = (result[movement.productID] ?? 0).addingReportingOverflow(amount)
            result[movement.productID] = sum.overflow ? Int64.max : sum.partialValue
        }
        return result
    }

    private func safeAvailable(stockMilli: Int64, creditMilli: Int64) -> Int64 {
        let sum = max(0, stockMilli).addingReportingOverflow(max(0, creditMilli))
        return sum.overflow ? Int64.max : sum.partialValue
    }

    private func unitCost(for product: Product) -> Int64 {
        guard product.kind == .prepared else { return product.costCents }
        return unitCost(for: product.uses)
    }

    private func unitCost(for uses: [IngredientUse]) -> Int64 {
        uses.reduce(Int64(0)) { total, use in
            guard let ingredient = data.products.first(where: { $0.id == use.ingredientID && $0.isActive }) else { return total }
            let value = Decimal(ingredient.costCents) * Decimal(use.milliUnits) / Decimal(1_000)
            var rounded = Decimal()
            var source = value
            NSDecimalRound(&rounded, &source, 0, .plain)
            let number = NSDecimalNumber(decimal: rounded)
            guard number.compare(NSDecimalNumber(value: Int64.max)) != .orderedDescending else { return Int64.max }
            let sum = total.addingReportingOverflow(number.int64Value)
            return sum.overflow ? Int64.max : sum.partialValue
        }
    }

    private func restoreInventory(for sale: Sale, in next: inout AppData, reason: String) throws {
        let outbound = next.inventoryMovements.filter { $0.saleID == sale.id && $0.deltaMilli < 0 }
        for movement in outbound {
            guard let index = next.products.firstIndex(where: { $0.id == movement.productID && $0.isActive && $0.kind != .prepared }) else { continue }
            let amount = movement.deltaMilli == Int64.min ? Int64.max : -movement.deltaMilli
            let restored = next.products[index].stockMilli.addingReportingOverflow(amount)
            guard !restored.overflow, (0...9_000_000_000_000).contains(restored.partialValue) else { throw BusinessError.invalidAmount }
            next.products[index].stockMilli = restored.partialValue
            next.inventoryMovements.insert(InventoryMovement(productID: movement.productID, productName: movement.productName,
                deltaMilli: amount, reason: reason, saleID: sale.id), at: 0)
        }
    }

    private func applyInventory(lines: [SaleLine], to next: inout AppData, saleID: UUID, reason: String) throws {
        for line in lines {
            switch line.kind {
            case .product:
                guard let product = next.products.first(where: { $0.id == line.sourceID && $0.isActive }) else {
                    throw BusinessError.unavailableItem(line.name)
                }
                if product.kind == .prepared {
                    try consume(product.uses, quantity: line.quantity, from: &next, saleID: saleID)
                } else {
                    let amount = Int64(line.quantity).multipliedReportingOverflow(by: 1_000)
                    guard !amount.overflow,
                          let index = next.products.firstIndex(where: { $0.id == product.id }),
                          next.products[index].stockMilli >= amount.partialValue else {
                        throw BusinessError.insufficientStock(product.name)
                    }
                    next.products[index].stockMilli -= amount.partialValue
                    next.inventoryMovements.insert(InventoryMovement(productID: product.id, productName: product.name,
                        deltaMilli: -amount.partialValue, reason: reason, saleID: saleID), at: 0)
                }
            case .service:
                guard let service = next.services.first(where: { $0.id == line.sourceID && $0.isActive }) else {
                    throw BusinessError.unavailableItem(line.name)
                }
                if !service.uses.isEmpty {
                    try consume(service.uses, quantity: line.quantity, from: &next, saleID: saleID)
                }
            }
        }
    }

    private func consume(_ uses: [IngredientUse], quantity: Int, from next: inout AppData, saleID: UUID) throws {
        guard !uses.isEmpty else { throw BusinessError.insufficientStock("receta o insumos") }
        let grouped = Dictionary(grouping: uses, by: \.ingredientID)
        for (ingredientID, ingredientUses) in grouped {
            guard let index = next.products.firstIndex(where: { $0.id == ingredientID && $0.isActive && $0.kind == .ingredient }) else {
                throw BusinessError.insufficientStock("insumo de la receta")
            }
            let perUnit = ingredientUses.reduce(Int64(0)) { $0 + $1.milliUnits }
            let amount = perUnit * Int64(quantity)
            guard amount > 0, next.products[index].stockMilli >= amount else { throw BusinessError.insufficientStock(next.products[index].name) }
            next.products[index].stockMilli -= amount
            next.inventoryMovements.insert(InventoryMovement(productID: ingredientID, productName: next.products[index].name,
                deltaMilli: -amount, reason: "Consumo por venta", saleID: saleID), at: 0)
        }
    }

    private static func applyBasisPoints(_ cents: Int64, _ basisPoints: Int) -> Int64 {
        let factor = Decimal(10_000 + basisPoints)
        var amount = Decimal(cents) * factor / Decimal(10_000)
        var rounded = Decimal()
        NSDecimalRound(&rounded, &amount, 0, .plain)
        return max(0, NSDecimalNumber(decimal: rounded).int64Value)
    }

    private func upsert<T: Identifiable>(_ value: T, in items: inout [T]) throws where T.ID == UUID {
        if let index = items.firstIndex(where: { $0.id == value.id }) { items[index] = value }
        else { items.append(value) }
    }

    private func commit(_ next: AppData) throws {
        guard !readOnlyAfterLoadFailure else { throw BusinessError.storageUnavailable }
        do {
            try storage.save(next)
            data = next
        } catch {
            throw BusinessError.storageUnavailable
        }
    }
}

// MARK: - Archivo local cifrado con AES-GCM y clave de Keychain

final class EncryptedLocalStore {
    private let fileURL: URL
    private let keychainService: String
    private let keychainAccount = "database-key-v1"

    init(fileManager: FileManager = .default, bundleIdentifier: String = Bundle.main.bundleIdentifier ?? "cu.shalev.ishalev") {
        let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let directory = base.appendingPathComponent("Shalev", isDirectory: true)
        self.fileURL = directory.appendingPathComponent("store.aesgcm")
        self.keychainService = bundleIdentifier
    }

    func load() throws -> AppData? {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { _ = try encryptionKey(); return nil }
        let encrypted = try Data(contentsOf: fileURL)
        let box = try AES.GCM.SealedBox(combined: encrypted)
        let clear = try AES.GCM.open(box, using: encryptionKey())
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(AppData.self, from: clear)
    }

    func save(_ data: AppData) throws {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        let clear = try encoder.encode(data)
        let box = try AES.GCM.seal(clear, using: encryptionKey())
        guard let encrypted = box.combined else { throw CryptoKitError.authenticationFailure }
        let directory = fileURL.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try encrypted.write(to: fileURL, options: [.atomic, .completeFileProtection])
        try FileManager.default.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: fileURL.path)
    }

    private func encryptionKey() throws -> SymmetricKey {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecSuccess, let bytes = result as? Data { return SymmetricKey(data: bytes) }
        guard status == errSecItemNotFound else { throw KeychainError.status(status) }

        let bytes = SymmetricKey(size: .bits256).withUnsafeBytes { Data($0) }
        let attributes: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: keychainService,
            kSecAttrAccount as String: keychainAccount,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecValueData as String: bytes,
        ]
        let addStatus = SecItemAdd(attributes as CFDictionary, nil)
        guard addStatus == errSecSuccess else { throw KeychainError.status(addStatus) }
        return SymmetricKey(data: bytes)
    }

    private enum KeychainError: Error { case status(OSStatus) }
}

// MARK: - Respaldo cifrado portable (formato iOS, no compatible con el .shalev de Android)

enum BackupCodec {
    private static let magic = Data("SHLV-IOS-BACKUP-1\n".utf8)
    private static let saltLength = 16
    private static let keyLength = 32
    private static let rounds: UInt32 = 250_000

    static func encode(_ data: AppData, password: String) throws -> Data {
        guard password.count >= 8 else { throw BusinessError.backupPassword }
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.sortedKeys]
        let clear = try encoder.encode(data)
        let salt = try randomBytes(count: saltLength)
        let key = try deriveKey(password: password, salt: salt)
        let box = try AES.GCM.seal(clear, using: key, authenticating: magic + salt)
        guard let combined = box.combined else { throw BusinessError.backupInvalid }
        return magic + salt + combined
    }

    static func decode(_ backup: Data, password: String) throws -> AppData {
        guard password.count >= 8 else { throw BusinessError.backupPassword }
        guard backup.count > magic.count + saltLength + 28,
              backup.prefix(magic.count) == magic else { throw BusinessError.backupInvalid }
        let saltStart = magic.count
        let salt = backup.subdata(in: saltStart..<(saltStart + saltLength))
        let sealed = backup.subdata(in: (saltStart + saltLength)..<backup.count)
        do {
            let key = try deriveKey(password: password, salt: salt)
            let box = try AES.GCM.SealedBox(combined: sealed)
            let clear = try AES.GCM.open(box, using: key, authenticating: magic + salt)
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            return try decoder.decode(AppData.self, from: clear)
        } catch let error as BusinessError {
            throw error
        } catch {
            throw BusinessError.backupInvalid
        }
    }

    private static func randomBytes(count: Int) throws -> Data {
        var bytes = Data(count: count)
        let status = bytes.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, count, $0.baseAddress!) }
        guard status == errSecSuccess else { throw BusinessError.backupInvalid }
        return bytes
    }

    private static func deriveKey(password: String, salt: Data) throws -> SymmetricKey {
        var derived = Data(count: keyLength)
        let passwordBytes = Array(password.utf8)
        let status = passwordBytes.withUnsafeBytes { passwordBuffer in
            salt.withUnsafeBytes { saltBuffer in
                derived.withUnsafeMutableBytes { keyBuffer in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passwordBuffer.bindMemory(to: Int8.self).baseAddress!,
                        passwordBytes.count,
                        saltBuffer.bindMemory(to: UInt8.self).baseAddress!,
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        rounds,
                        keyBuffer.bindMemory(to: UInt8.self).baseAddress!,
                        keyLength
                    )
                }
            }
        }
        guard status == kCCSuccess else { throw BusinessError.backupInvalid }
        return SymmetricKey(data: derived)
    }
}
