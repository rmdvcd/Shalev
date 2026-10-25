import Foundation
import Combine
import CryptoKit
import Network

private struct SyncPairingOffer {
    var code: SyncPairingCode
    var privateKey: P256.KeyAgreement.PrivateKey
}

@MainActor
final class SyncManager: ObservableObject {
    @Published private(set) var role: SyncRole
    @Published private(set) var isBusy = false
    @Published private(set) var status = "Sin sincronizar"
    @Published private(set) var isListening = false
    @Published private(set) var addresses: [String] = []
    @Published private(set) var port = 0
    @Published private(set) var connectedEmployeeIDs = Set<Int64>()
    @Published var catalogChanged = false
    @Published var lastError: String?

    let store: ShalevStore
    private let licenseManager: GLLicenseManager
    private let secureStore: SyncSecureStoring
    private let modulesProvider: () -> [String]
    private var configuration: SyncSecureConfiguration
    private var listener: SyncListener?
    private var pairingOffers: [Int64: SyncPairingOffer] = [:]
    private var serverSessions: [Int64: SyncEncryptedSession] = [:]
    private var clientSession: SyncEncryptedSession?
    private var clientCommandsUnique = false
    private var syncTask: Task<Bool, Error>?
    private var maintenanceTask: Task<Void, Never>?
    private var requestInFlight = false
    private var requestID: Int64 = 0
    private var commandResults: [Int64: [String: (value: Int64?, error: String?)]] = [:]
    private var startingListener = false
    private var incomingConnections = 0

    var link: SyncLink? { configuration.link }
    var businessID: String { configuration.businessID }
    var isSecondary: Bool { role == .secondary && configuration.link != nil }
    var linkedApps: [LocalEmployee] { store.linkedApps }
    var pendingCount: Int {
        store.data.shifts.filter { !$0.synced && $0.employeeID == nil }.count
            + store.data.sales.filter { !$0.synced && $0.employeeID == nil }.count
            + store.data.cashMovements.filter { !$0.synced }.count
    }
    var secondaryLicenseState: GLLicenseState? { isSecondary ? (link?.license?.state() ?? .revoked) : nil }
    var closeRequestedByPrincipal: Bool { link?.closePending ?? false }
    var closeRejectedByPrincipal: Bool { link?.closeRejectedNotice ?? false }

    init(store: ShalevStore, licenseManager: GLLicenseManager, secureStore: SyncSecureStoring? = nil,
         modulesProvider: @escaping () -> [String] = {
            let defaults = UserDefaults.standard
            func enabled(_ key: String) -> Bool { defaults.object(forKey: key) == nil ? true : defaults.bool(forKey: key) }
            var modules: [String] = []
            if enabled("shalev.module.sales") { modules.append("VENTAS") }
            if enabled("shalev.module.inventory") { modules.append("INVENTARIO") }
            if enabled("shalev.module.services") { modules.append("SERVICIOS") }
            return modules
         }) {
        self.store = store
        self.licenseManager = licenseManager
        let storage = secureStore ?? SyncKeychainStore()
        self.secureStore = storage
        self.modulesProvider = modulesProvider
        if let loaded = try? storage.load() {
            configuration = loaded
        } else {
            let random = (try? SyncCrypto.random(12)) ?? Data(UUID().uuidString.utf8).prefix(12)
            configuration = SyncSecureConfiguration(businessID: random.map { String(format: "%02x", $0) }.joined())
        }
        if configuration.link != nil { configuration.role = .secondary }
        role = configuration.role
        store.requiresClosingApproval = role == .secondary
        store.allowsRemoteSaleCorrections = role == .principal
        store.changeHandler = { [weak self] old, new in
            Task { @MainActor in self?.dataChanged(old: old, new: new) }
        }
        if isSecondary {
            status = link?.lastSync == nil ? "Vinculada; falta sincronizar" : "Lista para sincronizar"
        }
    }

    func start() {
        store.requiresClosingApproval = isSecondary
        store.allowsRemoteSaleCorrections = !isSecondary
        if isSecondary { startMaintenance() }
        else { Task { [weak self] in try? await self?.startPrincipal() } }
    }

    func stop() {
        maintenanceTask?.cancel(); maintenanceTask = nil
        listener?.cancel(); listener = nil; isListening = false
        for session in serverSessions.values { Task { await session.close() } }
        serverSessions.removeAll(); connectedEmployeeIDs.removeAll()
        if let clientSession { Task { await clientSession.close() } }
        self.clientSession = nil
    }

    func permits(_ permission: String) -> Bool {
        guard isSecondary else { return true }
        return link?.permissions.contains(permission) ?? false
    }

    func moduleEnabled(_ module: String) -> Bool {
        guard isSecondary, let modules = link?.modules else { return true }
        return modules.contains(module)
    }

    func permissions(_ names: [String]) -> Set<EmployeePermission> {
        var result = Set<EmployeePermission>()
        if names.contains("VENDER_PRODUCTOS") { result.formUnion([.sellProducts, .viewInventory, .viewRecords, .manageCash]) }
        if names.contains("VENDER_SERVICIOS") { result.formUnion([.sellServices, .viewRecords, .manageCash]) }
        if names.contains("EDITAR_INVENTARIO") { result.formUnion([.editInventory, .viewInventory]) }
        if names.contains("CAMBIAR_PRECIOS") { result.insert(.changePrices) }
        if names.contains("EXPORTAR") { result.insert(.exportReports) }
        return result
    }

    private func persist() throws {
        try secureStore.save(configuration)
        objectWillChange.send()
    }

    private func nextID() -> Int64 {
        requestID += 1
        if requestID <= 0 { requestID = 1 }
        return requestID
    }

    // MARK: - Altas, códigos y permisos en la principal

    func addLinkedApp(name: String, permissions: Set<EmployeePermission>) throws -> LocalEmployee {
        guard !isSecondary else { throw SyncError.permission }
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard (2...40).contains(trimmed.count),
              !store.linkedApps.contains(where: { $0.syncActive && $0.name.localizedCaseInsensitiveCompare(trimmed) == .orderedSame }) else {
            throw BusinessError.invalidName
        }
        let activeCount = store.linkedApps.filter { $0.syncActive }.count
        let limit = licenseManager.state.installedSecondaries ?? 5
        guard activeCount < limit else { throw SyncError.permission }
        let id = (store.linkedApps.compactMap(\.syncID).max() ?? 0) + 1
        let now = Date()
        let employee = LocalEmployee(name: trimmed, permissions: permissions, createdAt: now, syncID: id,
            syncActive: true, linkedAt: nil)
        try store.saveLinkedApp(employee)
        return employee
    }

    func pairingCode(for employeeID: Int64) async throws -> SyncPairingCode {
        guard !isSecondary, licenseManager.state.unlocked,
              let employee = store.linkedApps.first(where: { $0.syncID == employeeID && $0.syncActive }) else {
            throw SyncError.permission
        }
        if !isListening { try await startPrincipal() }
        guard isListening, !addresses.isEmpty else { throw SyncError.disconnected }
        let token = try SyncCrypto.random(16)
        let privateKey = P256.KeyAgreement.PrivateKey()
        let expires = Date().addingTimeInterval(SyncPairingCode.validity)
        let code = SyncPairingCode(businessID: businessID,
            businessName: store.data.settings.profile.businessName.isEmpty ? store.actorName : store.data.settings.profile.businessName,
            hosts: addresses, port: port, employeeID: employeeID, employeeName: employee.name, token: token, expiresAt: expires)
        pairingOffers[employeeID] = SyncPairingOffer(code: code, privateKey: privateKey)
        configuration.employeeKeys.removeValue(forKey: String(employeeID))
        try persist()
        var updated = employee
        updated.linkedAt = nil; updated.codeExpiresAt = expires
        try store.saveLinkedApp(updated)
        if let old = serverSessions.removeValue(forKey: employeeID) { await old.close() }
        return code
    }

    func updateLinkedApp(_ employee: LocalEmployee) throws {
        guard !isSecondary, employee.syncID != nil else { throw SyncError.permission }
        try store.saveLinkedApp(employee)
        notifyCatalogChanged()
    }

    func removeLinkedApp(_ employeeID: Int64) async throws {
        guard var employee = store.linkedApps.first(where: { $0.syncID == employeeID }) else { return }
        employee.syncActive = false; employee.codeExpiresAt = nil
        pairingOffers.removeValue(forKey: employeeID)
        try store.saveLinkedApp(employee)
        // Se conserva la clave para que el próximo saludo reciba un «quitada» autenticado.
        if let session = serverSessions.removeValue(forKey: employeeID) {
            try? await session.send(SyncJSON.message("quitada")); await session.close()
        }
    }

    func approveClosing(_ employeeID: Int64) throws {
        let next = SyncCatalog.approveClosing(employeeID: employeeID, by: store.actorName, in: store.data)
        try store.replaceDataForSync(next)
        notifyCatalogChanged()
    }

    func rejectClosing(_ employeeID: Int64) throws {
        guard var employee = store.linkedApps.first(where: { $0.syncID == employeeID }) else { return }
        employee.closeRequestedAt = nil
        employee.employeeCloseRequestAt = Date(timeIntervalSince1970: 0)
        try store.saveLinkedApp(employee)
        notifyCatalogChanged()
    }

    func requestSecondaryClose() async throws {
        guard isSecondary, var link else { throw SyncError.permission }
        link.closeRequested = true
        configuration.link = link
        try persist()
        _ = try await synchronize()
    }

    func dismissCloseNotices() {
        guard var link else { return }
        link.closeNotice = false; link.closeRejectedNotice = false
        configuration.link = link
        try? persist()
    }

    // MARK: - Vinculación de esta app como secundaria

    func linkSecondary(qr: String, phone: String?) async throws {
        guard !isSecondary else { throw SyncError.permission }
        let code = try SyncPairingCode.decode(qr, requireCurrent: true)
        isBusy = true; status = "Vinculando…"; lastError = nil
        defer { isBusy = false }
        do {
            let privateKey = P256.KeyAgreement.PrivateKey()
            let publicKey = privateKey.publicKey.derRepresentation
            let socket = try await SyncConnector.connect(hosts: code.hosts, port: code.port, businessID: code.businessID)
            let greeting = SyncJSON.message("vincular", ["v": 1, "negocio": code.businessID, "empleado": code.employeeID,
                "pub": publicKey.base64EncodedString(),
                "mac": SyncCrypto.secondaryPairingMAC(token: code.token, publicKeySPKI: publicKey).base64EncodedString(),
                "device": licenseManager.deviceID])
            try await SyncTiming.void(12, socket: socket) { try await socket.writeFrame(SyncJSON.data(greeting)) }
            let responseData = try await SyncTiming.data(15, socket: socket) { try await socket.readFrame() }
            guard responseData.count <= SyncFrames.maximumGreeting else { throw SyncError.invalidMessage }
            let response = try SyncJSON.object(responseData)
            let type = try SyncJSON.type(response)
            if type == "rechazo" { throw SyncError.rejected(AndroidBackupCodec.string(response["motivo"])) }
            guard type == "vincular_ok",
                  let principalPublic = Data(base64Encoded: AndroidBackupCodec.string(response["pub"])),
                  let mac = Data(base64Encoded: AndroidBackupCodec.string(response["mac"])) else { throw SyncError.invalidMessage }
            let expected = SyncCrypto.principalPairingMAC(token: code.token, principalSPKI: principalPublic, secondarySPKI: publicKey)
            guard SyncCrypto.equal(mac, expected) else { throw SyncError.authentication }
            let key = try SyncCrypto.employeeKey(privateKey: privateKey, peerSPKI: principalPublic, token: code.token)
            await socket.cancel()
            listener?.cancel(); listener = nil; isListening = false
            configuration.role = .secondary
            configuration.employeeKeys.removeAll()
            configuration.link = SyncLink(businessID: code.businessID,
                businessName: AndroidBackupCodec.string(response["nombreNegocio"], default: code.businessName),
                employeeID: code.employeeID, employeeName: code.employeeName, key: key, hosts: code.hosts, port: code.port,
                phone: phone?.trimmingCharacters(in: .whitespacesAndNewlines))
            role = .secondary
            try persist()
            try store.resetForSecondary(employeeName: code.employeeName)
            store.requiresClosingApproval = true; store.allowsRemoteSaleCorrections = false
            status = "Vinculada; recibiendo catálogo…"
            _ = try await synchronize()
        } catch {
            status = "No se pudo vincular"
            lastError = error.localizedDescription
            throw error
        }
    }

    func unlinkSecondary() async throws {
        guard isSecondary else { return }
        syncTask?.cancel()
        if let clientSession { await clientSession.close() }
        clientSession = nil
        maintenanceTask?.cancel(); maintenanceTask = nil
        configuration.role = .principal; configuration.link = nil; configuration.employeeKeys.removeAll()
        role = .principal
        try persist()
        try store.resetAfterUnlink()
        store.requiresClosingApproval = false; store.allowsRemoteSaleCorrections = true
        status = "App principal"; catalogChanged = false
        try await startPrincipal()
    }

    private func removedByPrincipal() async throws {
        if isSecondary { try await unlinkSecondary() }
        throw SyncError.rejected("quitada")
    }

    // MARK: - Cliente secundario

    @discardableResult
    func synchronize() async throws -> Bool {
        guard isSecondary else { return false }
        if let syncTask { return try await syncTask.value }
        var result = false
        for _ in 0..<4 {
            let task = Task<Bool, Error> { [weak self] in
                guard let self else { return false }
                return try await self.performSynchronization()
            }
            syncTask = task
            do {
                result = try await task.value
                syncTask = nil
            } catch {
                syncTask = nil
                throw error
            }
            let approvedClosePending = store.data.shifts.contains {
                !$0.synced && $0.closedAt != nil && $0.closingCount?.approvedAt != nil
            }
            if (link?.changesAcknowledged.isEmpty ?? true) && !approvedClosePending { break }
        }
        return result
    }

    /// Toda apertura de turno de una secundaria pasa por aquí; la venta y el cierre no llaman a esta función.
    func prepareToOpenShift() async throws {
        guard isSecondary else { return }
        _ = try await synchronize()
    }

    private func performSynchronization() async throws -> Bool {
        guard var link else { throw SyncError.disconnected }
        guard !requestInFlight else { throw SyncError.remote("Ya hay una comunicación en curso.") }
        isBusy = true; status = "Sincronizando…"; lastError = nil
        defer { isBusy = false }
        do {
            let id = nextID()
            let batch = try SyncCatalog.pendingBatch(app: store.data)
            var message = SyncJSON.message("sync", ["id": id, "lote": batch, "cambiosAplicados": link.changesAcknowledged,
                "solicitaCierre": link.closeRequested, "versionCode": 1])
            if let hash = link.catalogHash { message["hash"] = hash }
            if let phone = link.phone { message["telefono"] = phone }
            let response = try await clientRequest(message, expected: "sync_ok")
            guard AndroidBackupCodec.int64(response["id"]) == id,
                  let received = response["recibidos"] as? [String: Any] else { throw SyncError.invalidMessage }
            let changed = try SyncCatalog.applyChanges(AndroidBackupCodec.dictionaries(response["cambiosVentas"]), to: store.data)
            link.changesAcknowledged = changed.applied
            var next = SyncCatalog.applyReceipts(received, to: changed.app)
            next = try SyncCatalog.applyApprovedCount(response["conteoAprobado"] as? [String: Any],
                shiftUUID: AndroidBackupCodec.optionalString(response["conteoTurnoUuid"]), to: next)
            if let snapshot = response["instantanea"] as? [String: Any] {
                next = try SyncCatalog.applySnapshot(snapshot, to: next)
                if let employee = snapshot["empleado"] as? [String: Any] {
                    link.employeeName = AndroidBackupCodec.string(employee["nombre"], default: link.employeeName)
                    link.permissions = (employee["permisos"] as? [String] ?? []).sorted()
                }
                link.license = SyncLicense.from(snapshot["licencia"] as? [String: Any])?.validated(deviceID: licenseManager.deviceID)
                link.businessName = AndroidBackupCodec.string(snapshot["nombreNegocio"], default: link.businessName)
                if let preferences = snapshot["preferencias"] as? [String: Any] {
                    link.modules = preferences["modulos"] as? [String]
                }
                link.photos = AndroidBackupCodec.dictionaries(snapshot["fotos"]).map {
                    SyncPhotoRef(entity: AndroidBackupCodec.string($0["entidad"]), id: AndroidBackupCodec.int64($0["id"]),
                        name: AndroidBackupCodec.string($0["nombre"]), bytes: AndroidBackupCodec.int64($0["bytes"]))
                }
            }
            link.catalogHash = AndroidBackupCodec.optionalString(response["hash"]) ?? link.catalogHash
            link.lastSync = Date()
            let oldClosePending = link.closePending
            link.closePending = AndroidBackupCodec.bool(response["cerrarTurno"])
            if link.closePending { link.closeRequested = false }
            if oldClosePending && !link.closePending { link.closeNotice = true }
            if AndroidBackupCodec.bool(response["cierreRechazado"]) {
                link.closeRejectedNotice = true; link.closeRequested = false
            }
            configuration.link = link
            try store.replaceDataForSync(next)
            try persist()
            try await downloadPendingPhotos()
            catalogChanged = false
            status = "Sincronizado · \(Date().shalevDateTime)"
            startMaintenance()
            // Si llegó la aprobación, el siguiente envío ya lleva el turno cerrado.
            if store.data.shifts.contains(where: { !$0.synced && $0.closedAt != nil && $0.closingCount?.approvedAt != nil }) {
                status = "Conteo aprobado; confirma el cierre"
            }
            return true
        } catch {
            status = "Sin conexión; los cambios siguen en este dispositivo"
            lastError = error.localizedDescription
            throw error
        }
    }

    private func establishClient() async throws -> SyncEncryptedSession {
        guard let link else { throw SyncError.disconnected }
        status = "Conectando con la principal…"
        let socket = try await SyncConnector.connect(hosts: link.hosts, port: link.port, businessID: link.businessID)
        let nonce = try SyncCrypto.random(16)
        let hello = SyncJSON.message("hola", ["v": 1, "negocio": link.businessID, "empleado": link.employeeID,
            "nonce": nonce.base64EncodedString(), "device": licenseManager.deviceID])
        try await SyncTiming.void(10, socket: socket) { try await socket.writeFrame(SyncJSON.data(hello)) }
        let responseData = try await SyncTiming.data(12, socket: socket) { try await socket.readFrame() }
        guard responseData.count <= SyncFrames.maximumGreeting else { throw SyncError.invalidMessage }
        let response = try SyncJSON.object(responseData)
        let type = try SyncJSON.type(response)
        if type == "rechazo" {
            let reason = AndroidBackupCodec.string(response["motivo"])
            if reason == "quitada", let mac = Data(base64Encoded: AndroidBackupCodec.string(response["mac"])),
               SyncCrypto.equal(mac, SyncCrypto.rejectionMAC(key: link.key, nonce: nonce, reason: reason)) {
                await socket.cancel(); try await removedByPrincipal()
            }
            throw SyncError.rejected(reason)
        }
        guard type == "hola_ok", let principalNonce = Data(base64Encoded: AndroidBackupCodec.string(response["nonce"])),
              principalNonce.count == 16 else { throw SyncError.invalidMessage }
        clientCommandsUnique = AndroidBackupCodec.bool(response["comandosUnicos"])
        return try SyncEncryptedSession(socket: socket, side: .secondary, employeeKey: link.key,
            secondaryNonce: nonce, principalNonce: principalNonce)
    }

    private func clientRequest(_ message: [String: Any], expected: String) async throws -> [String: Any] {
        guard !requestInFlight else { throw SyncError.remote("Ya hay una comunicación en curso.") }
        requestInFlight = true
        defer { requestInFlight = false }
        if clientSession == nil { clientSession = try await establishClient() }
        guard let session = clientSession else { throw SyncError.disconnected }
        do {
            try await session.send(message)
            while true {
                let response = try await session.receive()
                let type = try SyncJSON.type(response)
                if type == expected { return response }
                if type == "aviso" { catalogChanged = true; status = "La principal cambió el catálogo"; continue }
                if type == "quitada" { try await removedByPrincipal() }
                if type == "pong" { continue }
                throw SyncError.invalidMessage
            }
        } catch {
            await session.close()
            if clientSession === session { clientSession = nil }
            throw error
        }
    }

    private func startMaintenance() {
        guard isSecondary, maintenanceTask == nil else { return }
        maintenanceTask = Task { [weak self] in
            var backoff: UInt64 = 2
            while !Task.isCancelled {
                guard let self, self.isSecondary else { return }
                try? await Task.sleep(nanoseconds: 30 * 1_000_000_000)
                if Task.isCancelled || self.isBusy || self.requestInFlight { continue }
                do {
                    let id = self.nextID()
                    _ = try await self.clientRequest(SyncJSON.message("ping", ["id": id]), expected: "pong")
                    backoff = 2
                } catch {
                    if !self.isSecondary { return }
                    if let session = self.clientSession { await session.close() }
                    self.clientSession = nil
                    self.status = "Reconectando…"
                    try? await Task.sleep(nanoseconds: backoff * 1_000_000_000)
                    backoff = min(60, backoff * 2)
                    if !self.isBusy, !self.requestInFlight, self.clientSession == nil {
                        self.clientSession = try? await self.establishClient()
                    }
                }
            }
        }
    }

    private func downloadPendingPhotos() async throws {
        guard let link else { return }
        for photo in link.photos {
            let uuid = AndroidID.uuid(photo.entity == "servicio" ? "service" : "product", photo.id)
            let existing = photo.entity == "servicio"
                ? store.data.services.first(where: { $0.id == uuid })?.imageData
                : store.data.products.first(where: { $0.id == uuid })?.imageData
            let existingName = photo.entity == "servicio"
                ? store.data.services.first(where: { $0.id == uuid })?.syncPhotoName
                : store.data.products.first(where: { $0.id == uuid })?.syncPhotoName
            if existing != nil && SyncCatalog.normalizedPhotoName(existingName) == photo.name { continue }
            guard photo.bytes >= 0 && photo.bytes <= Int64(SyncFrames.maximumClear) else { continue }
            var data = Data()
            var hash = ""
            while Int64(data.count) < photo.bytes {
                let id = nextID()
                let response = try await clientRequest(SyncJSON.message("foto_pedir", ["id": id, "nombre": photo.name, "desde": data.count]), expected: "foto_bloque")
                guard AndroidBackupCodec.int64(response["id"]) == id else { throw SyncError.invalidMessage }
                if let error = AndroidBackupCodec.optionalString(response["error"]) { throw SyncError.remote(error) }
                guard AndroidBackupCodec.int64(response["desde"]) == Int64(data.count),
                      let block = Data(base64Encoded: AndroidBackupCodec.string(response["datos"])) else { throw SyncError.invalidMessage }
                data.append(block)
                hash = AndroidBackupCodec.string(response["sha256"])
                guard Int64(data.count) <= photo.bytes else { throw SyncError.invalidMessage }
                if block.isEmpty && data.count < photo.bytes { throw SyncError.disconnected }
            }
            guard data.count == photo.bytes, SyncCrypto.sha256Hex(data) == hash else { throw SyncError.authentication }
            var next = store.data
            if photo.entity == "servicio", let index = next.services.firstIndex(where: { $0.id == uuid }) {
                next.services[index].imageData = data; next.services[index].syncPhotoName = photo.name
            } else if let index = next.products.firstIndex(where: { $0.id == uuid }) {
                next.products[index].imageData = data; next.products[index].syncPhotoName = photo.name
            } else { continue }
            try store.replaceDataForSync(next)
        }
    }

    // MARK: - Servidor principal

    func startPrincipal() async throws {
        guard !isSecondary, !isListening, !startingListener else { return }
        guard licenseManager.state.unlocked else { status = "La sincronización requiere una licencia activa"; return }
        startingListener = true
        defer { startingListener = false }
        for candidate in 47_811...47_820 {
            do {
                let newListener = try SyncListener(port: candidate, businessID: businessID) { [weak self] connection in
                    Task { @MainActor in await self?.accept(connection) }
                }
                try await newListener.start()
                listener = newListener; port = candidate; addresses = SyncLocalAddresses.ipv4()
                isListening = true; status = "Principal disponible en la red local"
                return
            } catch { continue }
        }
        status = "No se pudo abrir el puerto de sincronización"
        throw SyncError.disconnected
    }

    private func accept(_ connection: NWConnection) async {
        guard incomingConnections < 24 else { connection.cancel(); return }
        incomingConnections += 1
        defer { incomingConnections -= 1 }
        let socket = SyncSocket(connection: connection)
        do {
            try await socket.start()
            let data = try await SyncTiming.data(15, socket: socket) { try await socket.readFrame() }
            guard data.count <= SyncFrames.maximumGreeting else { throw SyncError.invalidMessage }
            let greeting = try SyncJSON.object(data)
            switch try SyncJSON.type(greeting) {
            case "vincular": try await acceptPairing(greeting, socket: socket)
            case "hola": try await acceptSession(greeting, socket: socket)
            default: await socket.cancel()
            }
        } catch { await socket.cancel() }
    }

    private func acceptPairing(_ greeting: [String: Any], socket: SyncSocket) async throws {
        let employeeID = AndroidBackupCodec.int64(greeting["empleado"])
        guard AndroidBackupCodec.int(greeting["v"], default: 1) == 1 else {
            try await sendRejection("version", socket: socket); return
        }
        guard AndroidBackupCodec.string(greeting["negocio"]) == businessID else {
            try await sendRejection("otro_negocio", socket: socket); return
        }
        guard let offer = pairingOffers[employeeID],
              let employee = store.linkedApps.first(where: { $0.syncID == employeeID && $0.syncActive }) else {
            try await sendRejection("codigo_invalido", socket: socket); return
        }
        guard Date() < offer.code.expiresAt else {
            try await sendRejection("codigo_vencido", socket: socket); return
        }
        guard let secondaryPublic = Data(base64Encoded: AndroidBackupCodec.string(greeting["pub"])),
              let mac = Data(base64Encoded: AndroidBackupCodec.string(greeting["mac"])) else {
            try await sendRejection("codigo_invalido", socket: socket); return
        }
        guard SyncCrypto.equal(mac, SyncCrypto.secondaryPairingMAC(token: offer.code.token, publicKeySPKI: secondaryPublic)) else {
            try await sendRejection("codigo_invalido", socket: socket); return
        }
        let key = try SyncCrypto.employeeKey(privateKey: offer.privateKey, peerSPKI: secondaryPublic, token: offer.code.token)
        // El token se gasta antes de contestar; repetir el mismo QR no vuelve a vincular.
        configuration.employeeKeys[String(employeeID)] = key
        pairingOffers.removeValue(forKey: employeeID)
        try persist()
        var linked = employee
        linked.linkedAt = Date(); linked.codeExpiresAt = nil
        linked.deviceID = AndroidBackupCodec.optionalString(greeting["device"])
        try store.saveLinkedApp(linked)
        let principalPublic = offer.privateKey.publicKey.derRepresentation
        let response = SyncJSON.message("vincular_ok", ["pub": principalPublic.base64EncodedString(),
            "mac": SyncCrypto.principalPairingMAC(token: offer.code.token, principalSPKI: principalPublic,
                secondarySPKI: secondaryPublic).base64EncodedString(),
            "nombreNegocio": offer.code.businessName])
        try await socket.writeFrame(SyncJSON.data(response))
        await socket.cancel()
    }

    private func sendRejection(_ reason: String, socket: SyncSocket, key: Data? = nil, nonce: Data? = nil) async throws {
        var response = SyncJSON.message("rechazo", ["motivo": reason])
        if let key, let nonce { response["mac"] = SyncCrypto.rejectionMAC(key: key, nonce: nonce, reason: reason).base64EncodedString() }
        try await socket.writeFrame(SyncJSON.data(response))
        await socket.cancel()
    }

    private func acceptSession(_ hello: [String: Any], socket: SyncSocket) async throws {
        let employeeID = AndroidBackupCodec.int64(hello["empleado"])
        guard AndroidBackupCodec.int(hello["v"], default: 1) == 1 else {
            try await sendRejection("version", socket: socket); return
        }
        guard AndroidBackupCodec.string(hello["negocio"]) == businessID else {
            try await sendRejection("otro_negocio", socket: socket); return
        }
        guard let key = configuration.employeeKeys[String(employeeID)],
              let employee = store.linkedApps.first(where: { $0.syncID == employeeID }),
              let secondaryNonce = Data(base64Encoded: AndroidBackupCodec.string(hello["nonce"])), secondaryNonce.count == 16 else {
            try await sendRejection("desconocida", socket: socket); return
        }
        guard employee.syncActive else {
            try await sendRejection("quitada", socket: socket, key: key, nonce: secondaryNonce); return
        }
        let principalNonce = try SyncCrypto.random(16)
        try await socket.writeFrame(SyncJSON.data(SyncJSON.message("hola_ok", ["nonce": principalNonce.base64EncodedString(), "comandosUnicos": true])))
        let session = try SyncEncryptedSession(socket: socket, side: .principal, employeeKey: key,
            secondaryNonce: secondaryNonce, principalNonce: principalNonce)
        do {
            var pending = try await session.receive()
            // Solo un mensaje cifrado prueba la clave y registra/desplaza la sesión anterior.
            if let rawDevice = AndroidBackupCodec.optionalString(hello["device"]),
               let device = SyncSubToken.validDeviceID(rawDevice), var updated = store.linkedApps.first(where: { $0.syncID == employeeID }) {
                updated.deviceID = device; try? store.saveLinkedApp(updated)
            }
            if let old = serverSessions[employeeID] { await old.close() }
            serverSessions[employeeID] = session; connectedEmployeeIDs.insert(employeeID)
            while true {
                if store.linkedApps.first(where: { $0.syncID == employeeID })?.syncActive != true {
                    try await session.send(SyncJSON.message("quitada")); break
                }
                let type = try SyncJSON.type(pending)
                switch type {
                case "sync": try await session.send(try respondToSync(pending, employeeID: employeeID))
                case "ping": try await session.send(SyncJSON.message("pong", ["id": AndroidBackupCodec.int64(pending["id"])]))
                case "cmd": try await session.send(try respondToCommand(pending, employeeID: employeeID))
                case "foto_pedir": try await session.send(photoBlock(pending))
                case "apk_pedir": try await session.send(SyncJSON.message("apk_bloque", ["id": AndroidBackupCodec.int64(pending["id"]),
                    "desde": AndroidBackupCodec.int64(pending["desde"]), "datos": "", "total": 0, "error": "no_encontrado"]))
                default: throw SyncError.invalidMessage
                }
                pending = try await session.receive()
            }
            await session.close()
            if serverSessions[employeeID] === session { serverSessions.removeValue(forKey: employeeID) }
            connectedEmployeeIDs.remove(employeeID)
        } catch {
            await session.close()
            if serverSessions[employeeID] === session { serverSessions.removeValue(forKey: employeeID) }
            connectedEmployeeIDs.remove(employeeID)
        }
    }

    private func respondToSync(_ message: [String: Any], employeeID: Int64) throws -> [String: Any] {
        guard let employee = store.linkedApps.first(where: { $0.syncID == employeeID }),
              let batch = message["lote"] as? [String: Any] else { throw SyncError.invalidMessage }
        let acknowledged = (message["cambiosAplicados"] as? [String] ?? [])
        let rejectionPending = store.linkedApps.first(where: { $0.syncID == employeeID })?.employeeCloseRequestAt?.timeIntervalSince1970 == 0
        var next = SyncCatalog.acknowledgeChanges(acknowledged, employeeID: employeeID, in: store.data)
        let applied = try SyncCatalog.applyBatch(batch, employeeID: employeeID, to: next)
        next = applied.app
        if let index = (next.employees ?? []).firstIndex(where: { $0.syncID == employeeID }) {
            next.employees?[index].lastSyncAt = Date()
            if let phone = AndroidBackupCodec.optionalString(message["telefono"]) { next.employees?[index].phone = phone }
            if AndroidBackupCodec.bool(message["solicitaCierre"]) {
                if next.employees?[index].employeeCloseRequestAt == nil { next.employees?[index].employeeCloseRequestAt = Date() }
            } else if next.employees?[index].employeeCloseRequestAt?.timeIntervalSince1970 != 0 {
                next.employees?[index].employeeCloseRequestAt = nil
            }
            if next.shifts.contains(where: { $0.employeeID == employeeID && $0.closedAt != nil }) {
                next.employees?[index].closeRequestedAt = nil
            }
            if rejectionPending { next.employees?[index].employeeCloseRequestAt = nil }
        }
        try store.replaceDataForSync(next)
        guard let current = store.linkedApps.first(where: { $0.syncID == employeeID }) else { throw SyncError.invalidMessage }
        var license = SyncLicense.from(licenseManager.state)
        let activeApps = store.linkedApps.filter(\.syncActive).sorted {
            (($0.linkedAt ?? .distantPast), $0.syncID ?? 0) < (($1.linkedAt ?? .distantPast), $1.syncID ?? 0)
        }
        if let device = current.deviceID, let slot = activeApps.firstIndex(where: { $0.syncID == employeeID }).map({ $0 + 1 }),
           let permission = licenseManager.issueSecondaryPermission(deviceID: device, slot: slot) {
            license.subToken = permission.token; license.firmaPub = permission.publicKey
            license.ranura = permission.slot; license.permitidas = permission.allowed
        }
        let snapshot = try SyncCatalog.snapshot(app: store.data, employee: current,
            license: license, modules: modulesProvider())
        let hash = try SyncCatalog.hash(snapshot)
        let includeSnapshot = AndroidBackupCodec.optionalString(message["hash"]) != hash
        var response = SyncJSON.message("sync_ok", ["id": AndroidBackupCodec.int64(message["id"]),
            "recibidos": applied.received, "hash": hash, "cambiosVentas": SyncCatalog.changes(for: employeeID, app: store.data)])
        if includeSnapshot { response["instantanea"] = snapshot }
        let open = store.data.shifts.first(where: { $0.employeeID == employeeID && $0.closedAt == nil })
        response["cerrarTurno"] = current.closeRequestedAt != nil && open != nil
        response["cierreRechazado"] = rejectionPending
        if let open, let count = open.closingCount, count.approvedAt != nil {
            let row = try AndroidBackupCodec.exportShift(open, app: store.data)
            response["conteoAprobado"] = row["conteoCierre"]
            response["conteoTurnoUuid"] = open.id.uuidString.lowercased()
        }
        return response
    }

    private func photoBlock(_ request: [String: Any]) -> [String: Any] {
        let id = AndroidBackupCodec.int64(request["id"])
        let from = AndroidBackupCodec.int64(request["desde"])
        let name = AndroidBackupCodec.string(request["nombre"])
        guard from >= 0, let photo = SyncCatalog.photo(named: name, app: store.data), from <= Int64(photo.data.count) else {
            return SyncJSON.message("foto_bloque", ["id": id, "desde": max(0, from), "datos": "", "total": 0, "error": "no_encontrado"])
        }
        let start = Int(from), end = min(photo.data.count, start + 256 * 1_024)
        let block = photo.data.subdata(in: start..<end)
        return SyncJSON.message("foto_bloque", ["id": id, "desde": from, "datos": block.base64EncodedString(),
            "total": photo.data.count, "sha256": SyncCrypto.sha256Hex(photo.data)])
    }

    private func dataChanged(old: AppData, new: AppData) {
        guard !isSecondary else { return }
        if old.products != new.products || old.services != new.services
            || old.settings.priceAdjustments != new.settings.priceAdjustments || old.settings.profile != new.settings.profile {
            notifyCatalogChanged()
        }
    }

    func modulesChanged() { notifyCatalogChanged() }

    private func notifyCatalogChanged() {
        guard !isSecondary else { return }
        for (id, session) in serverSessions {
            Task { @MainActor [weak self] in
                do { try await session.send(SyncJSON.message("aviso")) }
                catch {
                    await session.close()
                    self?.serverSessions.removeValue(forKey: id)
                    self?.connectedEmployeeIDs.remove(id)
                }
            }
        }
    }

    // MARK: - Comandos de catálogo (implementados abajo)

    func executeRemote(_ action: [String: Any]) async throws -> Int64? {
        guard isSecondary else { throw SyncError.permission }
        guard !isBusy, !requestInFlight else { throw SyncError.remote("Espera a que termine la sincronización.") }
        isBusy = true; status = "Aplicando cambio en la principal…"
        defer { isBusy = false }
        let commandKey = UUID().uuidString.lowercased()
        var response: [String: Any]
        var retries = 0
        while true {
            let id = nextID()
            do {
                response = try await clientRequest(SyncJSON.message("cmd", ["id": id, "accion": action, "clave": commandKey]), expected: "cmd_ok")
                guard AndroidBackupCodec.int64(response["id"]) == id else { throw SyncError.invalidMessage }
                break
            } catch {
                // Como Android 0.21.7: una sola repetición con la misma clave y solo si la principal promete idempotencia.
                if clientCommandsUnique && retries < 1 { retries += 1; continue }
                throw error
            }
        }
        if let error = AndroidBackupCodec.optionalString(response["error"]) {
            if error == "permiso" { throw SyncError.permission }
            throw SyncError.remote(error)
        }
        _ = try await synchronize()
        return AndroidBackupCodec.optionalInt64(response["valor"])
    }

    private func respondToCommand(_ message: [String: Any], employeeID: Int64) throws -> [String: Any] {
        guard let action = message["accion"] as? [String: Any],
              let employee = store.linkedApps.first(where: { $0.syncID == employeeID }) else { throw SyncError.invalidMessage }
        let id = AndroidBackupCodec.int64(message["id"])
        let key = AndroidBackupCodec.optionalString(message["clave"])
        if let key, let cached = commandResults[employeeID]?[key] {
            var response = SyncJSON.message("cmd_ok", ["id": id])
            if let value = cached.value { response["valor"] = value }
            if let error = cached.error { response["error"] = error }
            return response
        }
        let type = try SyncJSON.type(action)
        let needed = type == "producto_precios" || type == "preajuste_guardar" || type == "preajuste_eliminar"
            ? "CAMBIAR_PRECIOS" : "EDITAR_INVENTARIO"
        var result: (value: Int64?, error: String?) = (nil, nil)
        guard AndroidBackupCodec.exportPermissions(employee.permissions).contains(needed) else {
            result.error = "permiso"
            if let key { commandResults[employeeID, default: [:]][key] = result }
            return SyncJSON.message("cmd_ok", ["id": id, "error": "permiso"])
        }
        do { result.value = try SyncCommands.apply(action, store: store) }
        catch { result.error = SyncCommands.code(for: error) }
        if let key {
            var cache = commandResults[employeeID, default: [:]]
            cache[key] = result
            if cache.count > 200 { cache = Dictionary(uniqueKeysWithValues: cache.suffix(200).map { ($0.key, $0.value) }) }
            commandResults[employeeID] = cache
        }
        var response = SyncJSON.message("cmd_ok", ["id": id])
        if let value = result.value { response["valor"] = value }
        if let error = result.error { response["error"] = error }
        return response
    }
}

// MARK: - Acciones de catálogo ejecutadas por la principal

@MainActor
enum SyncCommands {
    static func apply(_ action: [String: Any], store: ShalevStore) throws -> Int64? {
        let type = try SyncJSON.type(action)
        switch type {
        case "producto_guardar":
            guard let row = action["producto"] as? [String: Any] else { throw SyncError.invalidMessage }
            let recipes = (action["receta"] as? [String: Any]).map { AndroidBackupCodec.dictionaries($0["lineas"]) }
            var product = try AndroidBackupCodec.importProduct(row, recipe: recipes)
            if let existing = store.data.products.first(where: { $0.id == product.id }) {
                if product.imageData == nil { product.imageData = existing.imageData; product.syncPhotoName = existing.syncPhotoName }
                if let read = AndroidBackupCodec.optionalInt64(action["cantidadLeida"]) {
                    let delta = product.stockMilli - read * 1_000
                    product.stockMilli = existing.stockMilli + delta
                }
            }
            try store.saveProduct(product)
            return AndroidID.int64("product", product.id)
        case "producto_eliminar":
            let id = AndroidID.uuid("product", AndroidBackupCodec.int64(action["id"]))
            try store.deleteProduct(id); return nil
        case "producto_stock":
            let id = AndroidID.uuid("product", AndroidBackupCodec.int64(action["id"]))
            let delta = AndroidBackupCodec.int64(action["delta"]) * 1_000
            try store.adjustStock(productID: id, newStockMilli: (store.data.products.first(where: { $0.id == id })?.stockMilli ?? 0) + delta,
                reason: AndroidBackupCodec.string(action["nota"], default: "Ajuste desde app secundaria"))
            return (store.data.products.first(where: { $0.id == id })?.stockMilli ?? 0) / 1_000
        case "producto_precios":
            guard let prices = action["precios"] as? [String: Any] else { throw SyncError.invalidMessage }
            for (key, value) in prices {
                let id = AndroidID.uuid("product", Int64(key) ?? 0)
                guard var product = store.data.products.first(where: { $0.id == id && $0.kind != .ingredient }) else { continue }
                product.priceCents = AndroidBackupCodec.int64(value)
                try store.saveProduct(product)
            }
            return nil
        case "insumo_guardar":
            guard let row = action["insumo"] as? [String: Any] else { throw SyncError.invalidMessage }
            var product = try AndroidBackupCodec.importIngredient(row)
            if let existing = store.data.products.first(where: { $0.id == product.id }) {
                if product.imageData == nil { product.imageData = existing.imageData; product.syncPhotoName = existing.syncPhotoName }
                if let read = AndroidBackupCodec.optionalInt64(action["cantidadLeidaMil"]) {
                    product.stockMilli = existing.stockMilli + product.stockMilli - read
                }
            }
            try store.saveProduct(product)
            return AndroidID.int64("ingredient", product.id)
        case "insumo_eliminar":
            try store.deleteProduct(AndroidID.uuid("ingredient", AndroidBackupCodec.int64(action["id"]))); return nil
        case "insumo_stock":
            let id = AndroidID.uuid("ingredient", AndroidBackupCodec.int64(action["id"]))
            let current = store.data.products.first(where: { $0.id == id })?.stockMilli ?? 0
            try store.adjustStock(productID: id, newStockMilli: current + AndroidBackupCodec.int64(action["deltaMil"]),
                reason: AndroidBackupCodec.string(action["nota"], default: "Ajuste desde app secundaria"))
            return store.data.products.first(where: { $0.id == id })?.stockMilli
        case "servicio_guardar":
            guard let row = action["servicio"] as? [String: Any] else { throw SyncError.invalidMessage }
            var service = AndroidBackupCodec.importService(row)
            if service.imageData == nil, let old = store.data.services.first(where: { $0.id == service.id }) {
                service.imageData = old.imageData; service.syncPhotoName = old.syncPhotoName
            }
            try store.saveService(service)
            return AndroidID.int64("service", service.id)
        case "servicio_eliminar":
            try store.deleteService(AndroidID.uuid("service", AndroidBackupCodec.int64(action["id"]))); return nil
        case "preajuste_guardar":
            guard let row = action["preajuste"] as? [String: Any] else { throw SyncError.invalidMessage }
            let adjustment = AndroidBackupCodec.importAdjustment(row)
            try store.savePriceAdjustment(adjustment)
            return AndroidID.int64("adjustment", adjustment.id)
        case "preajuste_eliminar":
            try store.deletePriceAdjustment(AndroidID.uuid("adjustment", AndroidBackupCodec.int64(action["id"]))); return nil
        default: throw SyncError.invalidMessage
        }
    }

    static func code(for error: Error) -> String {
        if let sync = error as? SyncError {
            if case .permission = sync { return "permiso" }
            return "otro"
        }
        guard let business = error as? BusinessError else { return "otro" }
        switch business {
        case .unavailableItem: return "no_encontrado"
        case .insufficientStock: return "stock"
        case .duplicateCustomer: return "duplicado:cliente"
        case .invalidName: return "validacion:nombre"
        case .invalidRecipe: return "validacion:receta"
        case .invalidPrice: return "validacion:precio"
        case .invalidPriceAdjustment: return "validacion:preajuste"
        default: return "otro"
        }
    }
}
