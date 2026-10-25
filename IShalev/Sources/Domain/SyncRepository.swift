import Foundation
import Security
import Combine

// MARK: - Configuración secreta (fuera de AppData y de los respaldos)

enum SyncRole: String, Codable { case principal, secondary }

struct SyncLicense: Codable, Equatable {
    var clase: String
    var tipo: String? = nil
    var venceEn: Int64? = nil
    var subToken: String? = nil
    var firmaPub: String? = nil
    var ranura: Int? = nil
    var permitidas: Int? = nil

    static func from(_ state: GLLicenseState, now: Date = Date()) -> SyncLicense {
        switch state {
        case .trial(let days): SyncLicense(clase: "PRUEBA", venceEn: Int64(now.addingTimeInterval(TimeInterval(days * 86_400)).timeIntervalSince1970 * 1_000))
        case .active(let type, let expiry, _, _): SyncLicense(clase: "ACTIVA", tipo: type.rawValue, venceEn: Int64(expiry.timeIntervalSince1970 * 1_000))
        case .perpetual: SyncLicense(clase: "PERPETUA", tipo: "PERPETUA")
        default: SyncLicense(clase: "BLOQUEADA")
        }
    }

    func dictionary() -> [String: Any] {
        var result: [String: Any] = ["clase": clase]
        if let tipo { result["tipo"] = tipo }
        if let venceEn { result["venceEn"] = venceEn }
        if let subToken { result["subToken"] = subToken }
        if let firmaPub { result["firmaPub"] = firmaPub }
        if let ranura { result["ranura"] = ranura }
        if let permitidas { result["permitidas"] = permitidas }
        return result
    }

    static func from(_ row: [String: Any]?) -> SyncLicense? {
        guard let row, let clase = row["clase"] as? String else { return nil }
        return SyncLicense(clase: clase, tipo: AndroidBackupCodec.optionalString(row["tipo"]),
            venceEn: AndroidBackupCodec.optionalInt64(row["venceEn"]), subToken: AndroidBackupCodec.optionalString(row["subToken"]),
            firmaPub: AndroidBackupCodec.optionalString(row["firmaPub"]), ranura: row["ranura"].map { AndroidBackupCodec.int($0) },
            permitidas: row["permitidas"].map { AndroidBackupCodec.int($0) })
    }

    /// Una principal antigua puede omitir el permiso; si lo envía, cualquier inconsistencia bloquea la secundaria.
    func validated(deviceID: String, now: Date = Date()) -> SyncLicense {
        if clase == "BLOQUEADA" { return self }
        guard let subToken, !subToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return self }
        guard let firmaPub, !firmaPub.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let permission = SyncSubToken.verify(subToken, publicKeyBase64: firmaPub, deviceID: deviceID, now: now),
              ranura == nil || ranura == permission.slot,
              permitidas == nil || permitidas == permission.allowed else { return SyncLicense(clase: "BLOQUEADA") }
        var copy = self
        copy.ranura = permission.slot; copy.permitidas = permission.allowed
        return copy
    }

    func state(now: Date = Date()) -> GLLicenseState {
        switch clase {
        case "PERPETUA": return .perpetual(id: "principal", secondaries: permitidas ?? 0)
        case "PRUEBA":
            guard let venceEn else { return .trialExpired }
            let hours = Int((Double(venceEn) / 1_000 - now.timeIntervalSince1970) / 3_600)
            return hours <= 0 ? .trialExpired : .trial(daysLeft: (hours + 23) / 24)
        case "ACTIVA":
            let type = GLLicenseType(rawValue: tipo ?? "") ?? .monthly
            guard let venceEn else { return .expired(type: type) }
            let expiry = Date(timeIntervalSince1970: Double(venceEn) / 1_000)
            return now < expiry ? .active(type: type, expiresAt: expiry, id: "principal", secondaries: permitidas ?? 0) : .expired(type: type)
        default: return .revoked
        }
    }
}

struct SyncPhotoRef: Codable, Equatable {
    var entity: String
    var id: Int64
    var name: String
    var bytes: Int64
}

struct SyncLink: Codable, Equatable {
    var businessID: String
    var businessName: String
    var employeeID: Int64
    var employeeName: String
    var key: Data
    var hosts: [String]
    var port: Int
    var permissions: [String] = []
    var lastSync: Date? = nil
    var license: SyncLicense? = nil
    var catalogHash: String? = nil
    var modules: [String]? = nil
    var photos: [SyncPhotoRef] = []
    var changesAcknowledged: [String] = []
    var phone: String? = nil
    var closePending = false
    var closeNotice = false
    var closeRequested = false
    var closeRejectedNotice = false
}

struct SyncSecureConfiguration: Codable {
    var role: SyncRole = .principal
    var businessID: String
    var link: SyncLink? = nil
    /// Claves simétricas por id de empleado; jamás se copian al respaldo del negocio.
    var employeeKeys: [String: Data] = [:]
}

protocol SyncSecureStoring: AnyObject {
    func load() throws -> SyncSecureConfiguration?
    func save(_ configuration: SyncSecureConfiguration) throws
}

final class SyncKeychainStore: SyncSecureStoring {
    private let service: String
    private let account = "sync-configuration-v1"

    init(bundleIdentifier: String = Bundle.main.bundleIdentifier ?? "cu.shalev.ishalev") {
        service = bundleIdentifier + ".sync"
    }

    func load() throws -> SyncSecureConfiguration? {
        var query = baseQuery()
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw SyncError.storage }
        return try JSONDecoder().decode(SyncSecureConfiguration.self, from: data)
    }

    func save(_ configuration: SyncSecureConfiguration) throws {
        let data = try JSONEncoder().encode(configuration)
        var query = baseQuery()
        let attributes: [String: Any] = [kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
        let status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecSuccess { return }
        if status == errSecItemNotFound {
            query[kSecValueData as String] = data
            query[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            guard SecItemAdd(query as CFDictionary, nil) == errSecSuccess else { throw SyncError.storage }
            return
        }
        throw SyncError.storage
    }

    private func baseQuery() -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
         kSecAttrAccount as String: account]
    }
}

final class SyncMemorySecureStore: SyncSecureStoring {
    var value: SyncSecureConfiguration?
    init(value: SyncSecureConfiguration? = nil) { self.value = value }
    func load() throws -> SyncSecureConfiguration? { value }
    func save(_ configuration: SyncSecureConfiguration) throws { value = configuration }
}

// MARK: - Instantánea y lotes del negocio

enum SyncCatalog {
    static let negativeStockNote = "Vendido en otra app sin existencias suficientes: revisa el conteo"

    static func snapshot(app: AppData, employee: LocalEmployee, license: SyncLicense, modules: [String]) throws -> [String: Any] {
        let regular = app.products.filter { $0.kind != .ingredient }
        let ingredients = app.products.filter { $0.kind == .ingredient }
        let products = try regular.map { product -> [String: Any] in
            var row = try AndroidBackupCodec.exportProduct(product)
            row.removeValue(forKey: "fotoData")
            if product.imageData != nil { row["fotoUri"] = photoName(for: product) }
            return row
        }
        let ingredientRows = try ingredients.map { product -> [String: Any] in
            var row = try AndroidBackupCodec.exportIngredient(product)
            row.removeValue(forKey: "fotoData")
            return row
        }
        let recipes = regular.filter { $0.kind == .prepared }.map { product in
            ["productoId": AndroidID.int64("product", product.id), "lineas": product.uses.map {
                ["insumoId": AndroidID.int64("ingredient", $0.ingredientID), "cantidadMil": $0.milliUnits]
            }] as [String: Any]
        }
        let services = app.services.map { service -> [String: Any] in
            var row = AndroidBackupCodec.exportService(service)
            row.removeValue(forKey: "fotoData")
            if service.imageData != nil { row["fotoUri"] = photoName(for: service) }
            return row
        }
        let photos = regular.filter { $0.isActive && $0.imageData != nil }.map {
            ["entidad": "producto", "id": AndroidID.int64("product", $0.id), "nombre": photoName(for: $0), "bytes": Int64($0.imageData!.count)] as [String: Any]
        } + app.services.filter { $0.isActive && $0.imageData != nil }.map {
            ["entidad": "servicio", "id": AndroidID.int64("service", $0.id), "nombre": photoName(for: $0), "bytes": Int64($0.imageData!.count)] as [String: Any]
        }
        return [
            "nombreNegocio": app.settings.profile.businessName.isEmpty ? app.settings.profile.ownerName : app.settings.profile.businessName,
            "empleado": ["nombre": employee.name, "permisos": AndroidBackupCodec.exportPermissions(employee.permissions)],
            "licencia": license.dictionary(), "productos": products, "recetas": recipes, "insumos": ingredientRows,
            "servicios": services, "preajustes": try app.settings.priceAdjustments.map { try AndroidBackupCodec.exportAdjustment($0, app: app) },
            "perfil": limitedProfile(app.settings.profile, employee: employee),
            "preferencias": ["productoBajo": 5, "productoCritico": 1, "insumoBajoMil": 5_000, "insumoCriticoMil": 1_000,
                              "modulos": modules.sorted(), "empleadosPrevistos": 0],
            "fotos": photos
        ]
    }

    static func hash(_ snapshot: [String: Any]) throws -> String {
        SyncCrypto.sha256Hex(try SyncJSON.data(snapshot))
    }

    static func normalizedPhotoName(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return nil }
        let name = value.split(separator: "/").last.map(String.init) ?? value
        return name.isEmpty ? nil : name
    }

    static func photoName(for product: Product) -> String {
        normalizedPhotoName(product.syncPhotoName) ?? product.id.uuidString.lowercased() + ".jpg"
    }
    static func photoName(for service: ServiceItem) -> String {
        normalizedPhotoName(service.syncPhotoName) ?? service.id.uuidString.lowercased() + ".jpg"
    }

    static func photo(named name: String, app: AppData) -> (entity: String, id: Int64, data: Data)? {
        for product in app.products where product.imageData != nil && photoName(for: product) == name {
            return (product.kind == .ingredient ? "insumo" : "producto", AndroidID.int64(product.kind == .ingredient ? "ingredient" : "product", product.id), product.imageData!)
        }
        for service in app.services where service.imageData != nil && photoName(for: service) == name {
            return ("servicio", AndroidID.int64("service", service.id), service.imageData!)
        }
        return nil
    }

    static func applySnapshot(_ snapshot: [String: Any], to current: AppData) throws -> AppData {
        guard let employee = snapshot["empleado"] as? [String: Any] else { throw SyncError.invalidMessage }
        var recipes: [Int64: [[String: Any]]] = [:]
        for recipe in AndroidBackupCodec.dictionaries(snapshot["recetas"]) {
            recipes[AndroidBackupCodec.int64(recipe["productoId"])] = AndroidBackupCodec.dictionaries(recipe["lineas"])
        }
        var products = try AndroidBackupCodec.dictionaries(snapshot["productos"]).map { row -> Product in
            var product = try AndroidBackupCodec.importProduct(row, recipe: recipes[AndroidBackupCodec.int64(row["id"])])
            if product.imageData == nil, let old = current.products.first(where: { $0.id == product.id }),
               normalizedPhotoName(old.syncPhotoName) == normalizedPhotoName(product.syncPhotoName) { product.imageData = old.imageData }
            return product
        }
        products += try AndroidBackupCodec.dictionaries(snapshot["insumos"]).map(AndroidBackupCodec.importIngredient)
        let services = AndroidBackupCodec.dictionaries(snapshot["servicios"]).map { row -> ServiceItem in
            var service = AndroidBackupCodec.importService(row)
            if service.imageData == nil, let old = current.services.first(where: { $0.id == service.id }),
               normalizedPhotoName(old.syncPhotoName) == normalizedPhotoName(service.syncPhotoName) { service.imageData = old.imageData }
            return service
        }
        var next = current
        next.products = products
        next.services = services
        next.settings.priceAdjustments = AndroidBackupCodec.dictionaries(snapshot["preajustes"]).map(AndroidBackupCodec.importAdjustment)
        let activeProductIDs = Set(products.filter { $0.isActive && $0.kind != .ingredient }.map(\.id))
        for index in next.settings.priceAdjustments.indices {
            if let ids = next.settings.priceAdjustments[index].productIDs {
                next.settings.priceAdjustments[index].productIDs = ids.intersection(activeProductIDs)
            }
        }
        var profile = (snapshot["perfil"] as? [String: Any]).map(AndroidBackupCodec.importProfile) ?? StoreProfile()
        profile.ownerName = AndroidBackupCodec.string(employee["nombre"])
        profile.ownerFirstName = profile.ownerName
        profile.ownerLastName = ""
        profile.identification = ""
        profile.businessName = AndroidBackupCodec.string(snapshot["nombreNegocio"], default: profile.businessName)
        next.settings.profile = profile

        // El catálogo de la principal ya incluye lo confirmado. Solo se repiten los descuentos de ventas aún pendientes.
        let pendingSaleIDs = Set(next.sales.filter { !$0.synced }.map(\.id))
        let pending = next.inventoryMovements.filter { $0.saleID.map { pendingSaleIDs.contains($0) } ?? false }
        for movement in pending {
            guard let index = next.products.firstIndex(where: { $0.id == movement.productID }) else { continue }
            let sum = next.products[index].stockMilli.addingReportingOverflow(movement.deltaMilli)
            next.products[index].stockMilli = sum.overflow ? (movement.deltaMilli < 0 ? Int64.min : Int64.max) : sum.partialValue
            next.products[index].updatedAt = Date()
        }
        return next
    }

    static func pendingBatch(app: AppData) throws -> [String: Any] {
        let shifts = try app.shifts.filter { !$0.synced && $0.employeeID == nil }.map { shift -> [String: Any] in
            var outgoing = shift
            // En una secundaria el cierre se envía primero como conteo de un turno abierto; se cierra tras aprobarlo la principal.
            if outgoing.closedAt != nil && outgoing.closingCount != nil && outgoing.closingCount?.approvedAt == nil {
                outgoing.closedAt = nil; outgoing.closedBy = nil; outgoing.summary = nil
            }
            return ["uuid": shift.id.uuidString.lowercased(), "turno": try AndroidBackupCodec.exportShift(outgoing, app: app)]
        }
        let reconstructed = AndroidBackupCodec.reconstructedStocks(app)
        let sales = try app.sales.filter { !$0.synced && $0.employeeID == nil }.map { sale -> [String: Any] in
            let movements = try app.inventoryMovements.filter { $0.saleID == sale.id }.map {
                try AndroidBackupCodec.exportMovement($0, app: app, reconstructed: reconstructed)
            }
            return ["uuid": sale.id.uuidString.lowercased(), "turnoUuid": sale.shiftID.uuidString.lowercased(),
                    "venta": AndroidBackupCodec.exportSale(sale, app: app), "movimientos": movements]
        }
        let cash = app.cashMovements.filter { !$0.synced }.map { movement -> [String: Any] in
            ["uuid": movement.id.uuidString.lowercased(), "turnoUuid": movement.shiftID.uuidString.lowercased(),
             "mov": ["id": AndroidID.int64("cash", movement.id), "turnoId": AndroidID.int64("shift", movement.shiftID),
                     "fecha": Int64(movement.date.timeIntervalSince1970 * 1_000), "tipo": movement.kind == .income ? "ENTRADA" : "SALIDA",
                     "importeCent": movement.amountCents, "motivo": movement.reason, "hechoPor": movement.actorName ?? "",
                     "uuid": movement.id.uuidString.lowercased(), "sincronizado": movement.synced] as [String: Any]]
        }
        return ["turnos": shifts, "ventas": sales, "caja": cash]
    }

    /// Aplica en la principal. Cada confirmación corresponde a una fila realmente insertada/actualizada o a un duplicado.
    static func applyBatch(_ batch: [String: Any], employeeID: Int64, to current: AppData) throws -> (app: AppData, received: [String: Any]) {
        var next = current
        var receivedShifts: [[String: Any]] = []
        var newlyClosed = Set<UUID>()
        for wrapper in AndroidBackupCodec.dictionaries(batch["turnos"]) {
            guard let id = UUID(uuidString: AndroidBackupCodec.string(wrapper["uuid"])),
                  let row = wrapper["turno"] as? [String: Any] else { throw SyncError.invalidMessage }
            var incoming = try AndroidBackupCodec.importShift(row)
            incoming.id = id; incoming.employeeID = employeeID; incoming.synced = true
            incoming.closingCount?.approvedBy = nil; incoming.closingCount?.approvedAt = nil
            if let count = incoming.closingCount, (!count.isValid() || incoming.countedCashCents != count.cashCents) { continue }
            let existing = next.shifts.first(where: { $0.id == id })
            let authorized = existing?.closingCount?.approvedAt != nil ? existing?.closingCount : nil
            if let authorized, let count = incoming.closingCount,
               (withoutApproval(count) != withoutApproval(authorized) || incoming.countedCashCents != authorized.cashCents) {
                throw SyncError.remote("El conteo de cierre cambió después de ser aprobado.")
            }
            if incoming.closedAt != nil && incoming.closingCount != nil && authorized == nil {
                throw SyncError.remote("El cierre necesita la aprobación del conteo en la principal.")
            }
            if let index = next.shifts.firstIndex(where: { $0.id == id }) {
                guard next.shifts[index].employeeID == employeeID else { continue }
                if next.shifts[index].closedAt == nil && incoming.closedAt != nil {
                    incoming.closingCount = next.shifts[index].closingCount ?? incoming.closingCount
                    next.shifts[index] = incoming; newlyClosed.insert(id)
                } else if next.shifts[index].closedAt == nil {
                    next.shifts[index].openingCashCents = incoming.openingCashCents
                    if authorized == nil {
                        next.shifts[index].countedCashCents = incoming.countedCashCents
                        next.shifts[index].closingCount = incoming.closingCount
                    }
                }
            } else {
                next.shifts.append(incoming)
                if incoming.closedAt != nil { newlyClosed.insert(id) }
            }
            receivedShifts.append(["uuid": id.uuidString.lowercased(), "cerrado": incoming.closedAt != nil])
        }

        var receivedSales: [String] = []
        for wrapper in AndroidBackupCodec.dictionaries(batch["ventas"]) {
            guard let id = UUID(uuidString: AndroidBackupCodec.string(wrapper["uuid"])),
                  let shiftID = UUID(uuidString: AndroidBackupCodec.string(wrapper["turnoUuid"])),
                  let row = wrapper["venta"] as? [String: Any] else { throw SyncError.invalidMessage }
            if next.sales.contains(where: { $0.id == id }) { receivedSales.append(id.uuidString.lowercased()); continue }
            guard let shift = next.shifts.first(where: { $0.id == shiftID && $0.employeeID == employeeID }) else { continue }
            var sale = AndroidBackupCodec.importSale(row)
            sale.id = id; sale.shiftID = shift.id; sale.employeeID = employeeID; sale.synced = true; sale.changePending = false
            next.sales.insert(sale, at: 0)
            if sale.customerIsFixed, let customer = sale.customer { upsertCustomer(customer, in: &next, at: sale.date) }
            for row in AndroidBackupCodec.dictionaries(wrapper["movimientos"]) {
                var movement = try AndroidBackupCodec.importMovement(row)
                movement.id = UUID(); movement.saleID = id; movement.shiftID = shiftID
                guard let productIndex = next.products.firstIndex(where: { $0.id == movement.productID }) else { continue }
                let sum = next.products[productIndex].stockMilli.addingReportingOverflow(movement.deltaMilli)
                let stock = sum.overflow ? (movement.deltaMilli < 0 ? Int64.min : Int64.max) : sum.partialValue
                next.products[productIndex].stockMilli = stock
                next.products[productIndex].updatedAt = Date()
                movement.resultingStockMilli = stock
                if stock < 0 && !movement.reason.contains(Self.negativeStockNote) {
                    movement.reason = [movement.reason, Self.negativeStockNote].filter { !$0.isEmpty }.joined(separator: " · ")
                }
                next.inventoryMovements.insert(movement, at: 0)
            }
            receivedSales.append(id.uuidString.lowercased())
        }

        var receivedCash: [String] = []
        for wrapper in AndroidBackupCodec.dictionaries(batch["caja"]) {
            guard let id = UUID(uuidString: AndroidBackupCodec.string(wrapper["uuid"])),
                  let shiftID = UUID(uuidString: AndroidBackupCodec.string(wrapper["turnoUuid"])),
                  let row = wrapper["mov"] as? [String: Any] else { throw SyncError.invalidMessage }
            if next.cashMovements.contains(where: { $0.id == id }) { receivedCash.append(id.uuidString.lowercased()); continue }
            guard next.shifts.contains(where: { $0.id == shiftID && $0.employeeID == employeeID }) else { continue }
            var movement = AndroidBackupCodec.importCash(row)
            movement.id = id; movement.shiftID = shiftID; movement.synced = true
            if movement.amountCents <= 0 { receivedCash.append(id.uuidString.lowercased()); continue }
            movement.reason = String(movement.reason.prefix(160))
            next.cashMovements.insert(movement, at: 0)
            receivedCash.append(id.uuidString.lowercased())
        }
        for id in newlyClosed { recalculateClosing(shiftID: id, in: &next) }
        return (next, ["turnos": receivedShifts, "ventas": receivedSales, "caja": receivedCash])
    }

    static func applyReceipts(_ received: [String: Any], to current: AppData) -> AppData {
        var next = current
        for row in AndroidBackupCodec.dictionaries(received["turnos"]) {
            guard let id = UUID(uuidString: AndroidBackupCodec.string(row["uuid"])),
                  let index = next.shifts.firstIndex(where: { $0.id == id }) else { continue }
            next.shifts[index].synced = true
        }
        let saleIDs = Set((received["ventas"] as? [Any] ?? []).compactMap { UUID(uuidString: AndroidBackupCodec.string($0)) })
        for index in next.sales.indices where saleIDs.contains(next.sales[index].id) { next.sales[index].synced = true }
        let cashIDs = Set((received["caja"] as? [Any] ?? []).compactMap { UUID(uuidString: AndroidBackupCodec.string($0)) })
        for index in next.cashMovements.indices where cashIDs.contains(next.cashMovements[index].id) { next.cashMovements[index].synced = true }
        return next
    }

    static func changes(for employeeID: Int64, app: AppData) -> [[String: Any]] {
        app.sales.filter { $0.employeeID == employeeID && $0.changePending }.compactMap { sale in
            var row: [String: Any] = ["uuid": sale.id.uuidString.lowercased(), "turnoUuid": sale.shiftID.uuidString.lowercased()]
            if let cancelled = sale.cancelledAt { row["anuladaEn"] = Int64(cancelled.timeIntervalSince1970 * 1_000) }
            if let reason = sale.cancellationReason { row["motivo"] = reason }
            if sale.correctionOfSaleID != nil { row["nueva"] = AndroidBackupCodec.exportSale(sale, app: app) }
            if let original = sale.correctionOfSaleID { row["corrigeUuid"] = original.uuidString.lowercased() }
            return row
        }
    }

    static func applyChanges(_ changes: [[String: Any]], to current: AppData) throws -> (app: AppData, applied: [String]) {
        var next = current
        var applied: [String] = []
        for change in changes {
            guard let id = UUID(uuidString: AndroidBackupCodec.string(change["uuid"])),
                  let shiftID = UUID(uuidString: AndroidBackupCodec.string(change["turnoUuid"])) else { throw SyncError.invalidMessage }
            if let index = next.sales.firstIndex(where: { $0.id == id }) {
                if !(change["nueva"] is [String: Any]), let cancelled = AndroidBackupCodec.optionalInt64(change["anuladaEn"]), next.sales[index].cancelledAt == nil {
                    next.sales[index].isCancelled = true
                    next.sales[index].cancelledAt = Date(timeIntervalSince1970: Double(cancelled) / 1_000)
                    next.sales[index].cancellationReason = AndroidBackupCodec.optionalString(change["motivo"])
                }
            } else if let row = change["nueva"] as? [String: Any] {
                guard next.shifts.contains(where: { $0.id == shiftID }) else { continue }
                var sale = AndroidBackupCodec.importSale(row)
                sale.id = id; sale.shiftID = shiftID; sale.employeeID = nil; sale.synced = true; sale.changePending = false
                if let original = AndroidBackupCodec.optionalString(change["corrigeUuid"]).flatMap(UUID.init(uuidString:)) {
                    sale.correctionOfSaleID = original
                }
                next.sales.insert(sale, at: 0)
            }
            applied.append(id.uuidString.lowercased())
        }
        return (next, applied)
    }

    static func acknowledgeChanges(_ ids: [String], employeeID: Int64, in current: AppData) -> AppData {
        let uuids = Set(ids.compactMap { UUID(uuidString: $0) })
        var next = current
        for index in next.sales.indices where next.sales[index].employeeID == employeeID && uuids.contains(next.sales[index].id) {
            next.sales[index].changePending = false
        }
        return next
    }

    static func applyApprovedCount(_ countRow: [String: Any]?, shiftUUID: String?, to current: AppData) throws -> AppData {
        guard let countRow, let shiftUUID, let id = UUID(uuidString: shiftUUID),
              let index = current.shifts.firstIndex(where: { $0.id == id }) else { return current }
        let wrapper: [String: Any] = ["id": 0, "abiertoEn": 0, "fondoCent": 0, "conteoCierre": countRow]
        let approved = try AndroidBackupCodec.importShift(wrapper).closingCount
        guard let approved, let existing = current.shifts[index].closingCount,
              withoutApproval(existing) == withoutApproval(approved) else { return current }
        var next = current
        next.shifts[index].closingCount = approved
        next.shifts[index].synced = false
        return next
    }

    static func approveClosing(employeeID: Int64, by actor: String, in current: AppData, now: Date = Date()) -> AppData {
        var next = current
        guard let index = next.shifts.firstIndex(where: { $0.employeeID == employeeID && $0.closedAt == nil && $0.closingCount != nil }) else { return next }
        next.shifts[index].closingCount?.approvedBy = actor
        next.shifts[index].closingCount?.approvedAt = now
        if let employeeIndex = (next.employees ?? []).firstIndex(where: { $0.syncID == employeeID }) {
            next.employees?[employeeIndex].closeRequestedAt = now
            next.employees?[employeeIndex].employeeCloseRequestAt = nil
        }
        return next
    }

    private static func withoutApproval(_ count: ShiftClosingCount) -> ShiftClosingCount {
        var copy = count; copy.approvedBy = nil; copy.approvedAt = nil; return copy
    }

    private static func limitedProfile(_ profile: StoreProfile, employee: LocalEmployee) -> [String: Any] {
        var row = AndroidBackupCodec.exportProfile(profile)
        row["nombre"] = ""; row["apellidos"] = ""; row["ci"] = ""
        let cards = AndroidBackupCodec.dictionaries(row["tarjetas"])
        let phones = AndroidBackupCodec.dictionaries(row["telefonos"])
        let cardID = employee.paymentCardID ?? profile.paymentCardID ?? profile.availableCards.first?.id
        let phoneID = employee.paymentPhoneID ?? profile.paymentPhoneID ?? profile.availablePhones.first?.id
        row["tarjetas"] = cards.filter { AndroidBackupCodec.int64($0["id"]) == cardID.map { AndroidID.int64("card", $0) } }
        row["telefonos"] = phones.filter { AndroidBackupCodec.int64($0["id"]) == phoneID.map { AndroidID.int64("phone", $0) } }
        if let cardID { row["pagoTarjetaId"] = AndroidID.int64("card", cardID) } else { row.removeValue(forKey: "pagoTarjetaId") }
        if let phoneID { row["pagoTelefonoId"] = AndroidID.int64("phone", phoneID) } else { row.removeValue(forKey: "pagoTelefonoId") }
        return row
    }

    private static func upsertCustomer(_ customer: Customer, in app: inout AppData, at date: Date) {
        if let index = app.customers.firstIndex(where: { !$0.identification.isEmpty && $0.identification == customer.identification }) {
            app.customers[index].name = customer.name; app.customers[index].phone = customer.phone; app.customers[index].updatedAt = date
        } else { app.customers.append(customer) }
    }

    private static func recalculateClosing(shiftID: UUID, in app: inout AppData) {
        guard let index = app.shifts.firstIndex(where: { $0.id == shiftID }) else { return }
        let sales = app.sales.filter { $0.shiftID == shiftID }
        let valid = sales.filter { !$0.isCancelled }
        app.shifts[index].summary = ShiftSummary(saleCount: valid.count, units: valid.flatMap(\.lines).reduce(0) { $0 + $1.quantity },
            totalCents: valid.reduce(0) { $0 + $1.totalCents }, cashCents: valid.filter { $0.paymentMethod == .cash }.reduce(0) { $0 + $1.totalCents },
            transferCents: valid.filter { $0.paymentMethod == .transfer }.reduce(0) { $0 + $1.totalCents },
            costCents: valid.reduce(0) { $0 + $1.costCents }, cancelledCount: sales.filter(\.isCancelled).count)
    }
}
