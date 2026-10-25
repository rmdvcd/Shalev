import Foundation
import CryptoKit
import Security
import CommonCrypto
import zlib

/// Compatibilidad con «Shalev-BACKUP» v3/v4 de Android (`SPVIBAK`).
/// Los importes permanecen en centavos y las cantidades se convierten explícitamente entre unidades y milésimas.
enum AndroidBackupCodec {
    private static let magic = Data("SPVIBAK".utf8)
    private static let maximumClearBytes = 128 * 1024 * 1024
    private static let defaultIterations = 310_000

    static func isAndroidBackup(_ data: Data) -> Bool {
        data.count > magic.count && data.prefix(magic.count) == magic
    }

    /// Solo lee el indicador público de la cabecera; no descifra ni valida todavía el contenido.
    static func requiresPassword(_ data: Data) -> Bool {
        let bytes = [UInt8](data)
        guard isAndroidBackup(data), bytes.count > magic.count + 1 else { return true }
        return bytes[magic.count] == 4 ? bytes[magic.count + 1] == 1 : true
    }

    static func encode(_ app: AppData, password: String, createdAt: Date = Date()) throws -> Data {
        let hasPassword = !password.isEmpty
        guard !hasPassword || password.count >= 8 else { throw BusinessError.backupPassword }
        let clear = try jsonForExport(app)
        let compressed = try GZIP.encode(clear)
        let salt = try randomBytes(16)
        let iv = try randomBytes(12)
        let iterations = hasPassword ? defaultIterations : 10_000
        let encryptedLength = Int64(compressed.count + 16)
        var aad = Data()
        aad.append(magic)
        aad.append(4)
        aad.append(hasPassword ? 1 : 0)
        appendBigEndian(Int64(milliseconds(createdAt)), to: &aad)
        appendBigEndian(Int32(iterations), to: &aad)
        aad.append(salt)
        aad.append(iv)
        appendBigEndian(encryptedLength, to: &aad)
        let key = try deriveKey(password: hasPassword ? password : internalPassword(), salt: salt, iterations: iterations)
        let nonce = try AES.GCM.Nonce(data: iv)
        let box = try AES.GCM.seal(compressed, using: key, nonce: nonce, authenticating: aad)
        let encrypted = box.ciphertext + box.tag
        var result = aad
        result.append(Data(SHA256.hash(data: encrypted)))
        result.append(encrypted)
        return result
    }

    static func decode(_ backup: Data, password: String) throws -> AppData {
        let bytes = [UInt8](backup)
        guard bytes.count > magic.count + 1, Data(bytes.prefix(magic.count)) == magic else {
            throw BusinessError.backupInvalid
        }
        let version = Int(bytes[magic.count])
        guard version == 3 || version == 4 else { throw BusinessError.unsupportedBackup }
        let aadLength = version == 4 ? 57 : 56
        let headerLength = aadLength + 32
        guard bytes.count >= headerLength + 16 else { throw BusinessError.backupInvalid }
        var cursor = magic.count + 1
        let hasPassword: Bool
        if version == 4 {
            guard bytes[cursor] == 0 || bytes[cursor] == 1 else { throw BusinessError.backupInvalid }
            hasPassword = bytes[cursor] == 1
            cursor += 1
        } else { hasPassword = true }
        cursor += 8 // creadoEn, legible sin descifrar
        guard cursor + 4 <= bytes.count else { throw BusinessError.backupInvalid }
        let iterations = Int(bigEndian32(bytes, at: cursor))
        guard (10_000...5_000_000).contains(iterations) else { throw BusinessError.backupInvalid }
        cursor += 4
        let salt = Data(bytes[cursor..<(cursor + 16)])
        cursor += 16
        let iv = Data(bytes[cursor..<(cursor + 12)])
        cursor += 12
        let encryptedLength64 = bigEndian64(bytes, at: cursor)
        guard encryptedLength64 >= 16 && encryptedLength64 <= Int64(maximumClearBytes) else { throw BusinessError.backupInvalid }
        let encryptedLength = Int(encryptedLength64)
        guard encryptedLength == bytes.count - headerLength else { throw BusinessError.backupInvalid }
        let expectedHash = Data(bytes[aadLength..<headerLength])
        let encrypted = Data(bytes[headerLength..<bytes.count])
        guard constantTimeEqual(expectedHash, Data(SHA256.hash(data: encrypted))) else { throw BusinessError.backupInvalid }
        let effectivePassword = hasPassword ? password : internalPassword()
        guard !hasPassword || !effectivePassword.isEmpty else { throw BusinessError.backupPassword }
        do {
            let key = try deriveKey(password: effectivePassword, salt: salt, iterations: iterations)
            let nonce = try AES.GCM.Nonce(data: iv)
            let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: encrypted.dropLast(16), tag: encrypted.suffix(16))
            let aad = Data(bytes.prefix(aadLength))
            let compressed = try AES.GCM.open(box, using: key, authenticating: aad)
            let clear = try GZIP.decode(compressed, maximumBytes: maximumClearBytes)
            return try appFromJSON(clear)
        } catch let error as BusinessError {
            throw error
        } catch {
            throw BusinessError.backupInvalid
        }
    }

    private static func deriveKey(password: String, salt: Data, iterations: Int) throws -> SymmetricKey {
        var derived = Data(count: 32)
        let passwordBytes = Array(password.utf8)
        let status = passwordBytes.withUnsafeBytes { passwordBuffer in
            salt.withUnsafeBytes { saltBuffer in
                derived.withUnsafeMutableBytes { keyBuffer in
                    CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2),
                        passwordBuffer.bindMemory(to: Int8.self).baseAddress!, passwordBytes.count,
                        saltBuffer.bindMemory(to: UInt8.self).baseAddress!, salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), UInt32(iterations),
                        keyBuffer.bindMemory(to: UInt8.self).baseAddress!, 32)
                }
            }
        }
        guard status == kCCSuccess else { throw BusinessError.backupInvalid }
        return SymmetricKey(data: derived)
    }

    private static func randomBytes(_ count: Int) throws -> Data {
        var data = Data(count: count)
        let status = data.withUnsafeMutableBytes {
            SecRandomCopyBytes(kSecRandomDefault, count, $0.baseAddress!)
        }
        guard status == errSecSuccess else { throw BusinessError.backupInvalid }
        return data
    }

    private static func internalPassword() -> String {
        let mask = [0x5A, 0x13, 0x7C, 0x29, 0x44, 0x61, 0x0E, 0x38]
        let secret = [0x09, 0x43, 0x2A, 0x60, 0x69, 0x23, 0x4F, 0x7B, 0x11, 0x3E, 0x13, 0x47, 0x23, 0x0E, 0x6A, 0x5D,
                      0x74, 0x21, 0x4C, 0x1B, 0x73, 0x4C, 0x3C, 0x0F, 0x6B, 0x5F, 0x3D, 0x7D, 0x1D, 0x24, 0x3F, 0x7E]
        return String(decoding: secret.enumerated().map { UInt8($0.element ^ mask[$0.offset % mask.count]) }, as: UTF8.self)
    }

    private static func constantTimeEqual(_ lhs: Data, _ rhs: Data) -> Bool {
        guard lhs.count == rhs.count else { return false }
        var difference: UInt8 = 0
        for (a, b) in zip(lhs, rhs) { difference |= a ^ b }
        return difference == 0
    }

    private static func bigEndian32(_ bytes: [UInt8], at offset: Int) -> UInt32 {
        bytes[offset..<(offset + 4)].reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
    }

    private static func bigEndian64(_ bytes: [UInt8], at offset: Int) -> Int64 {
        let value = bytes[offset..<(offset + 8)].reduce(UInt64(0)) { ($0 << 8) | UInt64($1) }
        return Int64(bitPattern: value)
    }

    private static func appendBigEndian(_ value: Int32, to data: inout Data) {
        for shift in stride(from: 24, through: 0, by: -8) { data.append(UInt8(truncatingIfNeeded: value >> shift)) }
    }

    private static func appendBigEndian(_ value: Int64, to data: inout Data) {
        for shift in stride(from: 56, through: 0, by: -8) { data.append(UInt8(truncatingIfNeeded: value >> shift)) }
    }

    private static func milliseconds(_ date: Date) -> Int64 { Int64(date.timeIntervalSince1970 * 1_000) }
    private static func date(_ milliseconds: Int64) -> Date { Date(timeIntervalSince1970: Double(milliseconds) / 1_000) }
}

enum GZIP {
    static func encode(_ input: Data) throws -> Data {
        var stream = z_stream()
        guard deflateInit2_(&stream, Z_DEFAULT_COMPRESSION, Z_DEFLATED, 16 + MAX_WBITS, 8,
                            Z_DEFAULT_STRATEGY, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size)) == Z_OK else {
            throw BusinessError.backupInvalid
        }
        defer { deflateEnd(&stream) }
        var result = Data()
        var status = Z_OK
        try input.withUnsafeBytes { raw in
            stream.next_in = UnsafeMutablePointer<Bytef>(mutating: raw.bindMemory(to: Bytef.self).baseAddress)
            stream.avail_in = uInt(input.count)
            while status != Z_STREAM_END {
                var buffer = [UInt8](repeating: 0, count: 32_768)
                let capacity = buffer.count
                let produced = buffer.withUnsafeMutableBytes { output -> Int in
                    stream.next_out = output.bindMemory(to: Bytef.self).baseAddress
                    stream.avail_out = uInt(capacity)
                    status = deflate(&stream, Z_FINISH)
                    return capacity - Int(stream.avail_out)
                }
                result.append(buffer, count: produced)
                guard status == Z_OK || status == Z_STREAM_END else { throw BusinessError.backupInvalid }
            }
        }
        return result
    }

    static func decode(_ input: Data, maximumBytes: Int) throws -> Data {
        guard !input.isEmpty else { throw BusinessError.backupInvalid }
        var stream = z_stream()
        guard inflateInit2_(&stream, 16 + MAX_WBITS, ZLIB_VERSION,
                            Int32(MemoryLayout<z_stream>.size)) == Z_OK else { throw BusinessError.backupInvalid }
        defer { inflateEnd(&stream) }
        var result = Data()
        var status = Z_OK
        try input.withUnsafeBytes { raw in
            stream.next_in = UnsafeMutablePointer<Bytef>(mutating: raw.bindMemory(to: Bytef.self).baseAddress)
            stream.avail_in = uInt(input.count)
            while status != Z_STREAM_END {
                var buffer = [UInt8](repeating: 0, count: 32_768)
                let capacity = buffer.count
                let produced = buffer.withUnsafeMutableBytes { output -> Int in
                    stream.next_out = output.bindMemory(to: Bytef.self).baseAddress
                    stream.avail_out = uInt(capacity)
                    status = inflate(&stream, Z_NO_FLUSH)
                    return capacity - Int(stream.avail_out)
                }
                result.append(buffer, count: produced)
                guard result.count <= maximumBytes,
                      status == Z_OK || status == Z_STREAM_END,
                      produced > 0 || stream.avail_in > 0 else { throw BusinessError.backupInvalid }
            }
        }
        return result
    }
}

enum AndroidID {
    private static let prefixes = [
        "product": "10000001", "ingredient": "10000002", "service": "10000003", "shift": "10000004",
        "sale": "10000005", "movement": "10000006", "cash": "10000007", "adjustment": "10000008",
        "card": "10000009", "phone": "1000000a", "employee": "1000000b", "customer": "1000000c"
    ]

    static func uuid(_ domain: String, _ id: Int64) -> UUID {
        // 8 hex de dominio + 16 hex del Long + 8 hex de relleno: reversible para todo Int64 positivo.
        if id > 0, let prefix = prefixes[domain] {
            let hex = String(format: "%016llx", id)
            let text = "\(prefix)-\(hex.prefix(4))-\(hex.dropFirst(4).prefix(4))-\(hex.dropFirst(8).prefix(4))-\(hex.dropFirst(12).prefix(4))00000000"
            if let value = UUID(uuidString: text) { return value }
        }
        return digestUUID(domain, "\(id)")
    }

    static func uuid(_ domain: String, _ text: String) -> UUID {
        digestUUID(domain, text)
    }

    static func int64(_ domain: String, _ id: UUID) -> Int64 {
        let text = id.uuidString.lowercased()
        let compact = text.replacingOccurrences(of: "-", with: "")
        if let prefix = prefixes[domain], compact.hasPrefix(prefix), compact.suffix(8) == "00000000",
           let value = Int64(compact.dropFirst(prefix.count).prefix(16), radix: 16), value > 0 { return value }
        let digest = SHA256.hash(data: Data("\(domain)|\(text)".utf8))
        let value = digest.prefix(8).reduce(UInt64(0)) { ($0 << 8) | UInt64($1) } & UInt64(Int64.max)
        return Int64(max(1, value))
    }

    private static func digestUUID(_ domain: String, _ value: String) -> UUID {
        let digest = Array(SHA256.hash(data: Data("\(domain)|\(value)".utf8)).prefix(16))
        let hex = digest.map { String(format: "%02x", $0) }.joined()
        let formatted = "\(hex.prefix(8))-\(hex.dropFirst(8).prefix(4))-4\(hex.dropFirst(13).prefix(3))-8\(hex.dropFirst(17).prefix(3))-\(hex.suffix(12))"
        return UUID(uuidString: formatted) ?? UUID()
    }
}

// MARK: - JSON portable de Android

extension AndroidBackupCodec {
    static func jsonForExport(_ app: AppData) throws -> Data {
        try validateReferences(app)
        let products = app.products.filter { $0.kind != .ingredient }
        let ingredients = app.products.filter { $0.kind == .ingredient }
        let productDTOs = try products.map { try exportProduct($0) }
        let recipeDTOs = products.filter { $0.kind == .prepared }.map { product in
            ["productoId": AndroidID.int64("product", product.id),
             "lineas": product.uses.map { ["insumoId": AndroidID.int64("ingredient", $0.ingredientID), "cantidadMil": $0.milliUnits] }] as [String: Any]
        }
        let shiftDTOs = try app.shifts.map { try exportShift($0, app: app) }
        let saleDTOs = app.sales.map { exportSale($0, app: app) }
        let reconstructed = reconstructedStocks(app)
        let movementDTOs = try app.inventoryMovements.map { try exportMovement($0, app: app, reconstructed: reconstructed) }
        let cashDTOs = app.cashMovements.map { movement in
            ["id": AndroidID.int64("cash", movement.id), "turnoId": AndroidID.int64("shift", movement.shiftID),
             "fecha": milliseconds(movement.date), "tipo": movement.kind == .income ? "ENTRADA" : "SALIDA",
             "importeCent": movement.amountCents, "motivo": movement.reason, "hechoPor": movement.actorName ?? "",
             "uuid": movement.id.uuidString.lowercased(), "sincronizado": movement.synced] as [String: Any]
        }
        let serviceDTOs = app.services.map(exportService)
        let customerDTOs = app.customers.map { customer in
            ["nombreApellidos": customer.name, "ci": customer.identification, "telefono": customer.phone,
             "creadoEn": milliseconds(customer.updatedAt), "actualizadoEn": milliseconds(customer.updatedAt)]
        }
        let employeeDTOs = (app.employees ?? []).map(exportEmployee)
        let adjustments = try app.settings.priceAdjustments.map { try exportAdjustment($0, app: app) }
        let root: [String: Any] = [
            "formato": "spvi-respaldo", "version": 4, "creadoEn": milliseconds(Date()), "appVersion": "IShalev-1.0",
            "productos": productDTOs, "recetas": recipeDTOs, "insumos": try ingredients.map(exportIngredient),
            "turnos": shiftDTOs, "ventas": saleDTOs, "movimientos": movementDTOs,
            "perfil": exportProfile(app.settings.profile), "preajustes": adjustments,
            "servicios": serviceDTOs, "empleados": employeeDTOs, "caja": cashDTOs, "clientesFijos": customerDTOs
        ]
        return try JSONSerialization.data(withJSONObject: root, options: [.sortedKeys])
    }

    static func exportProduct(_ product: Product) throws -> [String: Any] {
        let prepared = product.kind == .prepared
        var dto: [String: Any] = [
            "id": AndroidID.int64("product", product.id), "categoria": product.category, "nombre": product.name,
            "descripcion": product.description, "precioCostoCent": prepared ? 0 : product.costCents,
            "precioVentaCent": product.priceCents, "cantidad": prepared ? 0 : try wholeUnits(product.stockMilli, product.name),
            "creadoEn": milliseconds(product.createdAt), "actualizadoEn": milliseconds(product.updatedAt ?? product.createdAt),
            "eliminado": !product.isActive
        ]
        if !prepared {
            dto["nivelBajo"] = try wholeUnits(product.lowStockMilli, product.name)
            dto["nivelCritico"] = try wholeUnits(product.criticalStockMilli, product.name)
            if let expiry = product.expiresAt { dto["fechaCaducidad"] = calendarDate(expiry) }
        }
        if let image = product.imageData { dto["fotoData"] = image.base64EncodedString() }
        return dto
    }

    static func exportIngredient(_ product: Product) throws -> [String: Any] {
        var dto: [String: Any] = [
            "id": AndroidID.int64("ingredient", product.id), "nombre": product.name, "unidad": product.unit,
            "precioCent": product.costCents, "cantidadMil": product.stockMilli,
            "nivelBajoMil": product.lowStockMilli, "nivelCriticoMil": product.criticalStockMilli,
            "creadoEn": milliseconds(product.createdAt), "actualizadoEn": milliseconds(product.updatedAt ?? product.createdAt)
        ]
        if product.priceCents > 0 { dto["precioVentaCent"] = product.priceCents }
        if let image = product.imageData { dto["fotoData"] = image.base64EncodedString() }
        return dto
    }

    static func exportService(_ service: ServiceItem) -> [String: Any] {
        var dto: [String: Any] = [
            "id": AndroidID.int64("service", service.id), "nombre": service.name, "tipo": service.type,
            "importeCent": service.priceCents, "descripcion": service.description,
            "creadoEn": milliseconds(service.createdAt), "actualizadoEn": milliseconds(service.createdAt),
            "eliminado": !service.isActive,
            "insumos": service.uses.map { ["insumoId": AndroidID.int64("ingredient", $0.ingredientID), "cantidadMil": $0.milliUnits] }
        ]
        if let image = service.imageData { dto["fotoData"] = image.base64EncodedString() }
        return dto
    }

    static func exportProfile(_ profile: StoreProfile) -> [String: Any] {
        let cards = profile.availableCards
        let phones = profile.availablePhones
        let ownerParts = profile.ownerName.split(whereSeparator: \.isWhitespace)
        var dto: [String: Any] = [
            "nombre": profile.ownerFirstName ?? ownerParts.first.map(String.init) ?? "",
            "apellidos": profile.ownerLastName ?? ownerParts.dropFirst().joined(separator: " "),
            "ci": profile.identification, "negocio": profile.businessName,
            "tarjetas": cards.map { ["id": AndroidID.int64("card", $0.id), "numero": $0.number, "alias": $0.alias] },
            "telefonos": phones.map { ["id": AndroidID.int64("phone", $0.id), "numero": $0.number, "alias": $0.alias] }
        ]
        let selectedCard = profile.paymentCardID ?? (profile.transferCards == nil ? cards.first?.id : nil)
        let selectedPhone = profile.paymentPhoneID ?? (profile.transferPhones == nil ? phones.first?.id : nil)
        if let id = selectedCard { dto["pagoTarjetaId"] = AndroidID.int64("card", id) }
        if let id = selectedPhone { dto["pagoTelefonoId"] = AndroidID.int64("phone", id) }
        return dto
    }

    static func exportPermissions(_ permissions: Set<EmployeePermission>) -> [String] {
        var names = Set<String>()
        if permissions.contains(.sellProducts) { names.insert("VENDER_PRODUCTOS") }
        if permissions.contains(.sellServices) { names.insert("VENDER_SERVICIOS") }
        if permissions.contains(.editInventory) { names.insert("EDITAR_INVENTARIO") }
        if permissions.contains(.changePrices) { names.insert("CAMBIAR_PRECIOS") }
        if permissions.contains(.exportReports) { names.insert("EXPORTAR") }
        return names.sorted()
    }

    static func exportEmployee(_ employee: LocalEmployee) -> [String: Any] {
        var dto: [String: Any] = [
            "id": employee.syncID ?? AndroidID.int64("employee", employee.id), "nombre": employee.name,
            "permisos": exportPermissions(employee.permissions),
            "creadoEn": milliseconds(employee.linkedAt ?? employee.createdAt), "activo": employee.syncActive
        ]
        if let date = employee.lastSyncAt { dto["ultimaSync"] = milliseconds(date) }
        if let date = employee.codeExpiresAt { dto["codigoExpiraEn"] = milliseconds(date) }
        if let id = employee.paymentCardID { dto["pagoTarjetaId"] = AndroidID.int64("card", id) }
        if let id = employee.paymentPhoneID { dto["pagoTelefonoId"] = AndroidID.int64("phone", id) }
        if let phone = employee.phone { dto["telefono"] = phone }
        if let id = employee.deviceID { dto["deviceId"] = id }
        if let date = employee.closeRequestedAt { dto["cierreSolicitadoEn"] = milliseconds(date) }
        if let date = employee.employeeCloseRequestAt { dto["cierreSolicitadoEmpleadoEn"] = milliseconds(date) }
        return dto
    }

    static func exportAdjustment(_ adjustment: PriceAdjustment, app: AppData) throws -> [String: Any] {
        let ids = adjustment.productIDs ?? Set(app.products.filter { $0.kind != .ingredient }.map(\.id))
        guard ids.allSatisfy({ id in app.products.contains(where: { $0.id == id && $0.kind != .ingredient }) }) else {
            throw BusinessError.androidBackupUnsupported("un preajuste apunta a un insumo o a un artículo que ya no existe")
        }
        var dto: [String: Any] = ["id": AndroidID.int64("adjustment", adjustment.id), "nombre": adjustment.name,
            "puntosBasicos": adjustment.basisPoints, "productoIds": ids.map { AndroidID.int64("product", $0) },
            "activo": adjustment.active]
        if let method = adjustment.paymentMethod { dto["metodoPago"] = method == .cash ? "EFECTIVO" : "TRANSFERENCIA" }
        if let minimum = adjustment.minimumAmountCents { dto["importeMinimoCent"] = minimum }
        return dto
    }

    static func exportShift(_ shift: Shift, app: AppData) throws -> [String: Any] {
        var dto: [String: Any] = [
            "id": AndroidID.int64("shift", shift.id), "abiertoEn": milliseconds(shift.openedAt),
            "abiertoPor": shift.openedBy ?? "", "uuid": shift.id.uuidString.lowercased(),
            "sincronizado": shift.synced, "fondoCent": shift.openingCashCents
        ]
        if let employeeID = shift.employeeID { dto["empleadoId"] = employeeID }
        if let closed = shift.closedAt { dto["cerradoEn"] = milliseconds(closed) }
        if let closedBy = shift.closedBy { dto["cerradoPor"] = closedBy }
        if let counted = shift.countedCashCents { dto["contadoCent"] = counted }
        if let summary = shift.summary {
            let movements = app.inventoryMovements.filter { $0.shiftID == shift.id }
            let productMovements = movements.filter { movement in
                app.products.first(where: { $0.id == movement.productID })?.kind != .ingredient
            }.count
            dto["resumen"] = [
                "numVentas": summary.saleCount, "unidades": summary.units, "totalCent": summary.totalCents,
                "efectivoCent": summary.cashCents, "transferenciaCent": summary.transferCents,
                "costoCent": summary.costCents, "numMovimientos": movements.count,
                "ventasEfectivo": app.sales.filter { $0.shiftID == shift.id && !$0.isCancelled && $0.paymentMethod == .cash }.count,
                "ventasTransferencia": app.sales.filter { $0.shiftID == shift.id && !$0.isCancelled && $0.paymentMethod == .transfer }.count,
                "movimientosProducto": productMovements, "movimientosInsumo": movements.count - productMovements,
                "entradasCent": app.cashMovements.filter { $0.shiftID == shift.id && $0.kind == .income }.reduce(0) { $0 + $1.amountCents },
                "salidasCent": app.cashMovements.filter { $0.shiftID == shift.id && $0.kind == .expense }.reduce(0) { $0 + $1.amountCents },
                "numAnuladas": summary.cancelledCount
            ]
        }
        if let count = shift.closingCount {
            let bills = Dictionary(uniqueKeysWithValues: count.bills.map { (String($0.key), $0.value) })
            let lines = try count.inventory.map { line -> [String: Any] in
                let ingredient = app.products.first(where: { $0.id == line.productID })?.kind == .ingredient
                return ["entidad": ingredient ? "INSUMO" : "PRODUCTO",
                        "id": AndroidID.int64(ingredient ? "ingredient" : "product", line.productID),
                        "nombre": line.productName, "unidad": line.unit,
                        "esperado": ingredient ? line.expectedMilli : try wholeUnits(line.expectedMilli, line.productName),
                        "contado": ingredient ? line.countedMilli : try wholeUnits(line.countedMilli, line.productName),
                        "actualizadoEn": milliseconds(line.updatedAt), "categoria": line.category]
            }
            var closing: [String: Any] = ["billetes": bills, "otroCent": count.otherCashCents,
                "inventario": lines, "motivo": count.reason, "inventarioOmitido": count.inventoryCountSkipped ?? false]
            if let approvedBy = count.approvedBy { closing["aprobadoPor"] = approvedBy }
            if let approvedAt = count.approvedAt { closing["aprobadoEn"] = milliseconds(approvedAt) }
            dto["conteoCierre"] = closing
        }
        return dto
    }

    static func exportSale(_ sale: Sale, app: AppData) -> [String: Any] {
        let details = sale.lines.map { line -> [String: Any] in
            let isService = line.kind == .service
            let ingredient = !isService && app.products.first(where: { $0.id == line.sourceID })?.kind == .ingredient
            return ["id": AndroidID.int64("line", line.id),
                "productoId": AndroidID.int64(isService ? "service" : ingredient ? "ingredient" : "product", line.sourceID),
                "nombre": line.name, "categoria": line.category, "cantidad": line.quantity,
                "precioBaseCent": line.basePriceCents ?? line.unitPriceCents,
                "precioUnitarioCent": line.unitPriceCents, "costoUnitarioCent": line.unitCostCents,
                "clase": isService ? "SERVICIO" : ingredient ? "INSUMO" : "PRODUCTO"]
        }
        var dto: [String: Any] = [
            "id": AndroidID.int64("sale", sale.id), "turnoId": AndroidID.int64("shift", sale.shiftID),
            "fecha": milliseconds(sale.date), "metodoPago": sale.paymentMethod == .cash ? "EFECTIVO" : "TRANSFERENCIA",
            "detalles": details, "uuid": sale.id.uuidString.lowercased(), "sincronizado": sale.synced,
            "vendidoPor": sale.sellerName ?? ""
        ]
        if let employeeID = sale.employeeID { dto["empleadoId"] = employeeID }
        if sale.paymentMethod == .transfer {
            let customer = sale.customer
            dto["transaccion"] = [
                "id": AndroidID.int64("transaction", sale.id), "fecha": milliseconds(sale.date),
                "importeCent": sale.totalCents, "numero": sale.transactionNumber,
                "clienteNombre": customer?.name ?? "", "clienteCi": customer?.identification ?? "",
                "clienteTelefono": customer?.phone ?? "", "tarjetaCobro": sale.paymentCard ?? "",
                "telefonoCobro": sale.paymentPhone ?? "", "clienteFijo": sale.customerIsFixed
            ]
        }
        if let cancelled = sale.cancelledAt { dto["anuladaEn"] = milliseconds(cancelled) }
        if let reason = sale.cancellationReason { dto["motivoAnulacion"] = reason }
        if let correction = sale.correctionOfSaleID {
            dto["corrigeVentaId"] = AndroidID.int64("sale", correction)
            dto["corrigeUuid"] = correction.uuidString.lowercased()
        }
        return dto
    }

    static func exportMovement(_ movement: InventoryMovement, app: AppData,
                               reconstructed: [UUID: Int64]) throws -> [String: Any] {
        let ingredient = app.products.first(where: { $0.id == movement.productID })?.kind == .ingredient
        guard let resulting = movement.resultingStockMilli ?? reconstructed[movement.id] else {
            throw BusinessError.androidBackupUnsupported("no se pudo reconstruir la existencia de \(movement.productName)")
        }
        let reason = movement.reason
        let type: String
        if reason.hasPrefix("Anulación") || reason.hasPrefix("Corrección · devolución") { type = "ANULACION" }
        else if reason.hasPrefix("Alta") { type = "ALTA" }
        else if reason.hasPrefix("Eliminación") { type = "BAJA" }
        else if reason.hasPrefix("Venta") || reason.hasPrefix("Corrección de venta") { type = ingredient ? "CONSUMO" : "VENTA" }
        else { type = "AJUSTE" }
        return ["id": AndroidID.int64("movement", movement.id), "fecha": milliseconds(movement.date),
            "tipo": type, "entidad": ingredient ? "INSUMO" : "PRODUCTO",
            "entidadId": AndroidID.int64(ingredient ? "ingredient" : "product", movement.productID),
            "nombre": movement.productName, "delta": ingredient ? movement.deltaMilli : try wholeUnits(movement.deltaMilli, movement.productName),
            "existencia": ingredient ? resulting : try wholeUnits(resulting, movement.productName),
            "turnoId": optionalJSON(movement.shiftID.map { AndroidID.int64("shift", $0) }),
            "ventaId": optionalJSON(movement.saleID.map { AndroidID.int64("sale", $0) }),
            "nota": reason, "hechoPor": movement.actorName ?? ""]
    }

    static func reconstructedStocks(_ app: AppData) -> [UUID: Int64] {
        var result: [UUID: Int64] = [:]
        for product in app.products {
            let movements = app.inventoryMovements.filter { $0.productID == product.id }.sorted { $0.date > $1.date }
            var stock = product.stockMilli
            for (index, movement) in movements.enumerated() {
                result[movement.id] = stock
                if index + 1 < movements.count { stock -= movements[index].deltaMilli }
            }
        }
        return result
    }

    static func appFromJSON(_ data: Data) throws -> AppData {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              string(root["formato"]) == "spvi-respaldo" else { throw BusinessError.backupInvalid }
        let version = int(root["version"], default: 0)
        guard (3...4).contains(version) else { throw BusinessError.unsupportedBackup }
        let recipeRows = dictionaries(root["recetas"])
        var recipes: [Int64: [[String: Any]]] = [:]
        for recipe in recipeRows { recipes[int64(recipe["productoId"])] = dictionaries(recipe["lineas"]) }
        let regular = try dictionaries(root["productos"]).map { try importProduct($0, recipe: recipes[int64($0["id"])]) }
        let ingredients = try dictionaries(root["insumos"]).map(importIngredient)
        let services = dictionaries(root["servicios"]).map(importService)
        let shifts = try dictionaries(root["turnos"]).map(importShift)
        let openShifts = shifts.filter { $0.closedAt == nil }
        guard Set(openShifts.map { $0.employeeID }).count == openShifts.count else {
            throw BusinessError.androidBackupUnsupported("hay más de un turno abierto para la misma app o empleado")
        }
        let sales = dictionaries(root["ventas"]).map(importSale)
        let shiftIDs = Set(shifts.map(\.id))
        guard sales.allSatisfy({ shiftIDs.contains($0.shiftID) }) else { throw BusinessError.backupInvalid }
        let movements = try dictionaries(root["movimientos"]).map(importMovement)
        let cash = dictionaries(root["caja"]).map(importCash)
        guard cash.allSatisfy({ shiftIDs.contains($0.shiftID) }) else { throw BusinessError.backupInvalid }
        let profile = (root["perfil"] as? [String: Any]).map(importProfile) ?? StoreProfile()
        let adjustments = dictionaries(root["preajustes"]).map(importAdjustment)
        let employees = dictionaries(root["empleados"]).map(importEmployee)
        let customers = dictionaries(root["clientesFijos"]).map { row in
            let ci = string(row["ci"])
            return Customer(id: AndroidID.uuid("customer", ci), name: string(row["nombreApellidos"]),
                identification: ci, phone: string(row["telefono"]), updatedAt: date(int64(row["actualizadoEn"])))
        }
        var result = AppData.empty
        result.settings.profile = profile
        result.settings.priceAdjustments = adjustments
        result.products = regular + ingredients
        result.services = services
        result.sales = sales
        result.shifts = shifts
        result.inventoryMovements = movements
        result.cashMovements = cash
        result.customers = customers
        result.employees = employees.isEmpty ? nil : employees
        try validateReferences(result)
        return result
    }

    static func validateReferences(_ app: AppData) throws {
        let productIDs = Set(app.products.map(\.id))
        let serviceIDs = Set(app.services.map(\.id))
        let ingredientIDs = Set(app.products.filter { $0.kind == .ingredient }.map(\.id))
        let saleIDs = Set(app.sales.map(\.id))
        let shiftIDs = Set(app.shifts.map(\.id))
        let validRecipes = (app.products.map(\.uses) + app.services.map(\.uses)).joined()
            .allSatisfy { ingredientIDs.contains($0.ingredientID) && $0.milliUnits > 0 }
        let validLines = app.sales.flatMap(\.lines).allSatisfy {
            $0.kind == .service ? serviceIDs.contains($0.sourceID) : productIDs.contains($0.sourceID)
        }
        let validMovements = app.inventoryMovements.allSatisfy {
            productIDs.contains($0.productID) && ($0.saleID == nil || saleIDs.contains($0.saleID!))
                && ($0.shiftID == nil || shiftIDs.contains($0.shiftID!))
        }
        let validClosingCounts = app.shifts.compactMap(\.closingCount).flatMap(\.inventory)
            .allSatisfy { productIDs.contains($0.productID) }
        guard validRecipes && validLines && validMovements && validClosingCounts else { throw BusinessError.backupInvalid }
    }

    static func importProduct(_ row: [String: Any], recipe: [[String: Any]]?) throws -> Product {
        let id64 = int64(row["id"])
        let id = AndroidID.uuid("product", id64)
        let prepared = recipe != nil
        let uses = (recipe ?? []).map { line in
            let ingredientID = int64(line["insumoId"])
            return IngredientUse(id: AndroidID.uuid("recipe", "\(id64)-\(ingredientID)"),
                ingredientID: AndroidID.uuid("ingredient", ingredientID), milliUnits: int64(line["cantidadMil"]))
        }
        return Product(id: id, name: string(row["nombre"]), category: string(row["categoria"], default: "Otros"),
            kind: prepared ? .prepared : .product, description: string(row["descripcion"]),
            imageData: image(row["fotoData"]), expiresAt: prepared ? nil : calendarDate(string(row["fechaCaducidad"])),
            stockMilli: prepared ? 0 : try milli(int64(row["cantidad"])), unit: "u",
            lowStockMilli: prepared ? 0 : try milli(int64(row["nivelBajo"])),
            criticalStockMilli: prepared ? 0 : try milli(int64(row["nivelCritico"])),
            priceCents: int64(row["precioVentaCent"]), costCents: int64(row["precioCostoCent"]), uses: uses,
            isActive: !bool(row["eliminado"]), createdAt: date(int64(row["creadoEn"])),
            updatedAt: date(int64(row["actualizadoEn"])), syncPhotoName: optionalString(row["fotoUri"]))
    }

    static func importIngredient(_ row: [String: Any]) throws -> Product {
        let id = int64(row["id"])
        return Product(id: AndroidID.uuid("ingredient", id), name: string(row["nombre"]), category: "Insumos",
            kind: .ingredient, imageData: image(row["fotoData"]), stockMilli: int64(row["cantidadMil"]),
            unit: string(row["unidad"], default: "u"), lowStockMilli: int64(row["nivelBajoMil"]),
            criticalStockMilli: int64(row["nivelCriticoMil"]), priceCents: int64(row["precioVentaCent"]),
            costCents: int64(row["precioCent"]), isActive: true, createdAt: date(int64(row["creadoEn"])),
            updatedAt: date(int64(row["actualizadoEn"])), syncPhotoName: optionalString(row["fotoUri"]))
    }

    static func importService(_ row: [String: Any]) -> ServiceItem {
        let id = int64(row["id"])
        let uses = dictionaries(row["insumos"]).map { line in
            let ingredientID = int64(line["insumoId"])
            return IngredientUse(id: AndroidID.uuid("serviceRecipe", "\(id)-\(ingredientID)"),
                ingredientID: AndroidID.uuid("ingredient", ingredientID), milliUnits: int64(line["cantidadMil"]))
        }
        return ServiceItem(id: AndroidID.uuid("service", id), name: string(row["nombre"]),
            type: string(row["tipo"], default: "General"), description: string(row["descripcion"]),
            imageData: image(row["fotoData"]), priceCents: int64(row["importeCent"]), uses: uses,
            isActive: !bool(row["eliminado"]), createdAt: date(int64(row["creadoEn"])),
            syncPhotoName: optionalString(row["fotoUri"]))
    }

    static func importProfile(_ row: [String: Any]) -> StoreProfile {
        var profile = StoreProfile()
        let first = string(row["nombre"])
        let last = string(row["apellidos"])
        profile.businessName = string(row["negocio"], default: "Mi negocio")
        profile.ownerFirstName = first
        profile.ownerLastName = last
        profile.ownerName = "\(first) \(last)".trimmingCharacters(in: .whitespacesAndNewlines)
        profile.identification = string(row["ci"])
        let cards = dictionaries(row["tarjetas"]).map { card in
            TransferCard(id: AndroidID.uuid("card", int64(card["id"])), number: string(card["numero"]), alias: string(card["alias"]))
        }
        let phones = dictionaries(row["telefonos"]).map { phone in
            TransferPhone(id: AndroidID.uuid("phone", int64(phone["id"])), number: string(phone["numero"]), alias: string(phone["alias"]))
        }
        profile.transferCards = cards
        profile.transferPhones = phones
        if let id = optionalInt64(row["pagoTarjetaId"]) { profile.paymentCardID = AndroidID.uuid("card", id) }
        if let id = optionalInt64(row["pagoTelefonoId"]) { profile.paymentPhoneID = AndroidID.uuid("phone", id) }
        profile.transferCard = profile.paymentCard?.number ?? ""
        profile.transferPhone = profile.paymentPhone?.number ?? ""
        return profile
    }

    static func importAdjustment(_ row: [String: Any]) -> PriceAdjustment {
        let method = string(row["metodoPago"])
        let ids = (row["productoIds"] as? [Any] ?? []).map { AndroidID.uuid("product", int64($0)) }
        return PriceAdjustment(id: AndroidID.uuid("adjustment", int64(row["id"])), name: string(row["nombre"]),
            basisPoints: int(row["puntosBasicos"]), productIDs: Set(ids),
            paymentMethod: method == "EFECTIVO" ? .cash : method == "TRANSFERENCIA" ? .transfer : nil,
            minimumAmountCents: optionalInt64(row["importeMinimoCent"]), active: bool(row["activo"], default: true))
    }

    static func importShift(_ row: [String: Any]) throws -> Shift {
        var summary: ShiftSummary?
        if let value = row["resumen"] as? [String: Any] {
            summary = ShiftSummary(saleCount: int(value["numVentas"]), units: int(value["unidades"]),
                totalCents: int64(value["totalCent"]), cashCents: int64(value["efectivoCent"]),
                transferCents: int64(value["transferenciaCent"]), costCents: int64(value["costoCent"]),
                cancelledCount: int(value["numAnuladas"]))
        }
        var closing: ShiftClosingCount?
        if let value = row["conteoCierre"] as? [String: Any] {
            let billRows = value["billetes"] as? [String: Any] ?? [:]
            var bills: [Int: Int] = [:]
            for (key, amount) in billRows { if let denomination = Int(key) { bills[denomination] = int(amount) } }
            let lines = try dictionaries(value["inventario"]).map { line -> ShiftInventoryCount in
                let ingredient = string(line["entidad"]) == "INSUMO"
                let expected = int64(line["esperado"])
                let counted = int64(line["contado"])
                return ShiftInventoryCount(productID: AndroidID.uuid(ingredient ? "ingredient" : "product", int64(line["id"])),
                    productName: string(line["nombre"]), category: string(line["categoria"]),
                    unit: string(line["unidad"]), expectedMilli: ingredient ? expected : try milli(expected),
                    countedMilli: ingredient ? counted : try milli(counted), updatedAt: date(int64(line["actualizadoEn"])))
            }
            closing = ShiftClosingCount(bills: bills, otherCashCents: int64(value["otroCent"]), inventory: lines,
                inventoryCountSkipped: optionalBool(value["inventarioOmitido"]), reason: string(value["motivo"]),
                approvedBy: optionalString(value["aprobadoPor"]), approvedAt: optionalInt64(value["aprobadoEn"]).map(date))
        }
        let id = optionalString(row["uuid"]).flatMap(UUID.init(uuidString:)) ?? AndroidID.uuid("shift", int64(row["id"]))
        return Shift(id: id, openedAt: date(int64(row["abiertoEn"])),
            closedAt: optionalInt64(row["cerradoEn"]).map(date), openingCashCents: int64(row["fondoCent"]),
            countedCashCents: optionalInt64(row["contadoCent"]), openedBy: optionalString(row["abiertoPor"]),
            closedBy: optionalString(row["cerradoPor"]), closingCount: closing, summary: summary,
            employeeID: optionalInt64(row["empleadoId"]), synced: bool(row["sincronizado"]))
    }

    static func importSale(_ row: [String: Any]) -> Sale {
        let id = int64(row["id"])
        let globalID = optionalString(row["uuid"]).flatMap(UUID.init(uuidString:)) ?? AndroidID.uuid("sale", id)
        let details = dictionaries(row["detalles"]).map { line -> SaleLine in
            let service = string(line["clase"], default: "PRODUCTO") == "SERVICIO"
            let ingredient = string(line["clase"], default: "PRODUCTO") == "INSUMO"
            let domain = service ? "service" : ingredient ? "ingredient" : "product"
            return SaleLine(id: AndroidID.uuid("line", int64(line["id"])),
                sourceID: AndroidID.uuid(domain, int64(line["productoId"])), kind: service ? .service : .product,
                name: string(line["nombre"]), category: string(line["categoria"]), quantity: int(line["cantidad"]),
                basePriceCents: int64(line["precioBaseCent"]), unitPriceCents: int64(line["precioUnitarioCent"]),
                unitCostCents: int64(line["costoUnitarioCent"]))
        }
        var customer: Customer?
        var fixed = false
        var transaction = ""
        var card: String?
        var phone: String?
        if let value = row["transaccion"] as? [String: Any] {
            transaction = string(value["numero"])
            let ci = string(value["clienteCi"])
            customer = Customer(id: AndroidID.uuid("customer", ci.isEmpty ? "transaction-\(id)" : ci),
                name: string(value["clienteNombre"]), identification: ci, phone: string(value["clienteTelefono"]),
                updatedAt: date(int64(value["fecha"])))
            fixed = bool(value["clienteFijo"])
            card = optionalString(value["tarjetaCobro"])
            phone = optionalString(value["telefonoCobro"])
        }
        let correction = optionalString(row["corrigeUuid"]).flatMap(UUID.init(uuidString:))
            ?? optionalInt64(row["corrigeVentaId"]).map { AndroidID.uuid("sale", $0) }
        return Sale(id: globalID, shiftID: AndroidID.uuid("shift", int64(row["turnoId"])),
            date: date(int64(row["fecha"])), paymentMethod: string(row["metodoPago"]) == "TRANSFERENCIA" ? .transfer : .cash,
            lines: details, transactionNumber: transaction, customer: customer, customerIsFixed: fixed,
            paymentCard: card, paymentPhone: phone, isCancelled: optionalInt64(row["anuladaEn"]) != nil,
            cancelledAt: optionalInt64(row["anuladaEn"]).map(date), cancellationReason: optionalString(row["motivoAnulacion"]),
            correctionOfSaleID: correction, sellerName: optionalString(row["vendidoPor"]),
            employeeID: optionalInt64(row["empleadoId"]), synced: bool(row["sincronizado"]))
    }

    static func importMovement(_ row: [String: Any]) throws -> InventoryMovement {
        let ingredient = string(row["entidad"]) == "INSUMO"
        let delta = int64(row["delta"])
        let resulting = int64(row["existencia"])
        return InventoryMovement(id: AndroidID.uuid("movement", int64(row["id"])), date: date(int64(row["fecha"])),
            productID: AndroidID.uuid(ingredient ? "ingredient" : "product", int64(row["entidadId"])),
            productName: string(row["nombre"]), deltaMilli: ingredient ? delta : try milli(delta),
            reason: string(row["nota"], default: string(row["tipo"])),
            saleID: optionalInt64(row["ventaId"]).map { AndroidID.uuid("sale", $0) },
            shiftID: optionalInt64(row["turnoId"]).map { AndroidID.uuid("shift", $0) },
            resultingStockMilli: ingredient ? resulting : try milli(resulting), actorName: optionalString(row["hechoPor"]))
    }

    static func importCash(_ row: [String: Any]) -> CashMovement {
        let fallback = AndroidID.uuid("cash", int64(row["id"]))
        let id = optionalString(row["uuid"]).flatMap(UUID.init(uuidString:)) ?? fallback
        return CashMovement(id: id, shiftID: AndroidID.uuid("shift", int64(row["turnoId"])),
            date: date(int64(row["fecha"])), kind: string(row["tipo"]) == "SALIDA" ? .expense : .income,
            amountCents: int64(row["importeCent"]), reason: string(row["motivo"]), actorName: optionalString(row["hechoPor"]),
            synced: bool(row["sincronizado"]))
    }

    static func importEmployee(_ row: [String: Any]) -> LocalEmployee {
        let names = Set((row["permisos"] as? [String] ?? []))
        var permissions = Set<EmployeePermission>()
        if names.contains("VENDER_PRODUCTOS") { permissions.formUnion([.sellProducts, .viewInventory, .viewRecords, .manageCash]) }
        if names.contains("VENDER_SERVICIOS") { permissions.formUnion([.sellServices, .viewRecords, .manageCash]) }
        if names.contains("EDITAR_INVENTARIO") { permissions.formUnion([.editInventory, .viewInventory]) }
        if names.contains("CAMBIAR_PRECIOS") { permissions.insert(.changePrices) }
        if names.contains("EXPORTAR") { permissions.insert(.exportReports) }
        let syncID = int64(row["id"])
        return LocalEmployee(id: AndroidID.uuid("employee", syncID), name: string(row["nombre"]),
            permissions: permissions, createdAt: date(int64(row["creadoEn"])), syncID: syncID,
            syncActive: bool(row["activo"], default: true), linkedAt: date(int64(row["creadoEn"])),
            lastSyncAt: optionalInt64(row["ultimaSync"]).map(date),
            codeExpiresAt: optionalInt64(row["codigoExpiraEn"]).map(date),
            paymentCardID: optionalInt64(row["pagoTarjetaId"]).map { AndroidID.uuid("card", $0) },
            paymentPhoneID: optionalInt64(row["pagoTelefonoId"]).map { AndroidID.uuid("phone", $0) },
            phone: optionalString(row["telefono"]), deviceID: optionalString(row["deviceId"]),
            closeRequestedAt: optionalInt64(row["cierreSolicitadoEn"]).map(date),
            employeeCloseRequestAt: optionalInt64(row["cierreSolicitadoEmpleadoEn"]).map(date))
    }

    static func wholeUnits(_ milli: Int64, _ name: String) throws -> Int64 {
        guard milli % 1_000 == 0 else {
            throw BusinessError.androidBackupUnsupported("\(name) tiene una cantidad fraccionaria que Android solo puede guardar como unidades enteras")
        }
        return milli / 1_000
    }

    static func milli(_ units: Int64) throws -> Int64 {
        let result = units.multipliedReportingOverflow(by: 1_000)
        guard !result.overflow else { throw BusinessError.backupInvalid }
        return result.partialValue
    }

    static func calendarDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    static func calendarDate(_ value: String) -> Date? {
        guard !value.isEmpty else { return nil }
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: value)
    }

    static func image(_ value: Any?) -> Data? {
        guard let text = value as? String, !text.isEmpty else { return nil }
        return Data(base64Encoded: text)
    }

    static func dictionaries(_ value: Any?) -> [[String: Any]] {
        guard let array = value as? [Any] else { return [] }
        return array.compactMap { $0 as? [String: Any] }
    }

    static func string(_ value: Any?, default fallback: String = "") -> String {
        guard !(value is NSNull) else { return fallback }
        return value as? String ?? fallback
    }

    static func optionalString(_ value: Any?) -> String? {
        let result = string(value)
        return result.isEmpty ? nil : result
    }

    static func int64(_ value: Any?) -> Int64 {
        guard !(value is NSNull) else { return 0 }
        if let number = value as? NSNumber { return number.int64Value }
        if let text = value as? String { return Int64(text) ?? 0 }
        return 0
    }

    static func optionalInt64(_ value: Any?) -> Int64? {
        guard value != nil && !(value is NSNull) else { return nil }
        return int64(value)
    }

    static func optionalJSON(_ value: Int64?) -> Any {
        if let value { return value }
        return NSNull()
    }

    static func int(_ value: Any?, default fallback: Int = 0) -> Int {
        guard value != nil && !(value is NSNull) else { return fallback }
        return Int(int64(value))
    }

    static func bool(_ value: Any?, default fallback: Bool = false) -> Bool {
        guard value != nil && !(value is NSNull) else { return fallback }
        if let boolean = value as? Bool { return boolean }
        return int64(value) != 0
    }

    static func optionalBool(_ value: Any?) -> Bool? {
        guard value != nil && !(value is NSNull) else { return nil }
        return bool(value)
    }
}
