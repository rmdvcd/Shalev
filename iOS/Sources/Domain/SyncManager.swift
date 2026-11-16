import Foundation
import Combine
import CryptoKit
import Network

private struct SyncPairingOffer {
    var code: SyncPairingCode
    var privateKey: P256.KeyAgreement.PrivateKey
}

private struct SyncBaseFileRecord {
    var url: URL
    var sha256: String
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
    private var outgoingBaseFiles: [String: SyncBaseFileRecord] = [:]
    private var lastOutgoingBaseCleanup: Date?
    private var importingBase = false
    private var startingListener = false
    private var incomingConnections = 0

    var link: SyncLink? { configuration.link }
    var businessID: String { configuration.businessID }
    var isSecondary: Bool { role == .secondary && configuration.link != nil }
    var linkedApps: [LocalEmployee] { store.linkedApps }
    var pendingCount: Int {
        store.activeShifts.filter { !$0.synced && $0.employeeID == nil }.count
            + store.activeSales.filter { !$0.synced && $0.employeeID == nil }.count
            + store.activeCashMovements.filter { !$0.synced }.count
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
        store.allowsSaleCorrections = role == .principal
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
        store.allowsSaleCorrections = !isSecondary
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
        if let requestID = employee.importRequestID { discardOutgoingBase(employeeID: employeeID, requestID: requestID) }
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

    func resolveImportRequest(employeeID: Int64, requestID: String, authorize: Bool) throws {
        guard !isSecondary, Self.validImportRequestID(requestID),
              var employee = store.linkedApps.first(where: { $0.syncID == employeeID && $0.syncActive }),
              employee.importRequestID == requestID,
              SyncImportRequestState(serverValue: employee.importRequestState) == .pending else {
            throw SyncError.permission
        }
        employee.importRequestState = authorize ? SyncImportRequestState.authorized.rawValue : SyncImportRequestState.rejected.rawValue
        try store.saveLinkedApp(employee)
        if !authorize { discardOutgoingBase(employeeID: employeeID, requestID: requestID) }
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

    func linkSecondary(qr: String, phone: String?, initialOptIn: Bool = false,
                       backupExportedAt: Date? = nil) async throws {
        guard role == .principal, !isSecondary else { throw SyncError.permission }
        let eligibleInitialLink = initialOptIn && linkedApps.isEmpty
        guard backupExportedAt != nil || eligibleInitialLink else { throw SyncError.backupRequired }
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
            for session in serverSessions.values { await session.close() }
            serverSessions.removeAll(); connectedEmployeeIDs.removeAll()
            try store.resetForSecondary(employeeName: code.employeeName)
            var next = configuration
            next.role = .secondary
            next.employeeKeys.removeAll()
            next.link = SyncLink(businessID: code.businessID,
                businessName: AndroidBackupCodec.string(response["nombreNegocio"], default: code.businessName),
                employeeID: code.employeeID, employeeName: code.employeeName, key: key, hosts: code.hosts, port: code.port,
                phone: phone?.trimmingCharacters(in: .whitespacesAndNewlines))
            try secureStore.save(next)
            configuration = next
            role = .secondary
            objectWillChange.send()
            discardAllOutgoingBaseFiles()
            store.requiresClosingApproval = true; store.allowsSaleCorrections = false; store.allowsRemoteSaleCorrections = false
            status = "Vinculada; recibiendo catálogo…"
            // Si falla la primera descarga, iniciar el reintento cuando termine esa petición para no competir con ella.
            do {
                _ = try await synchronize()
            } catch {
                startMaintenance()
                throw error
            }
            startMaintenance()
        } catch {
            status = isSecondary ? "Vinculada; reconectando…" : "No se pudo vincular"
            lastError = error.localizedDescription
            throw error
        }
    }

    func unlinkSecondary() async throws {
        guard isSecondary else { return }
        let oldRequestID = configuration.link?.importRequestID
        try await becomePrincipal(resetData: true, discardedRequestID: oldRequestID)
    }

    private func becomePrincipal(resetData: Bool, discardedRequestID: String? = nil) async throws {
        syncTask?.cancel()
        if let clientSession { await clientSession.close() }
        clientSession = nil
        maintenanceTask?.cancel(); maintenanceTask = nil
        if resetData { try store.resetAfterUnlink() }

        var next = configuration
        next.businessID = try Self.newBusinessID()
        next.role = .principal
        next.link = nil
        next.employeeKeys.removeAll()
        try secureStore.save(next)
        configuration = next
        role = .principal
        objectWillChange.send()
        if let discardedRequestID { discardIncomingBase(requestID: discardedRequestID) }
        store.requiresClosingApproval = false; store.allowsSaleCorrections = true; store.allowsRemoteSaleCorrections = true
        status = "App principal"; lastError = nil; catalogChanged = false
        try await startPrincipal()
    }

    func requestBaseImport() async throws {
        guard isSecondary, var link else { throw SyncError.permission }
        let state = link.importState
        guard state != .incompatible, state != .authorized, state != .consumed else {
            throw SyncError.remote(state.userMessage ?? "No se puede crear otra solicitud ahora.")
        }
        let previousRequestID = link.importRequestID
        let requestID: String
        if state == .pending || state == .pendingSend {
            guard let existing = link.importRequestID, Self.validImportRequestID(existing) else { throw SyncError.invalidMessage }
            requestID = existing
        } else {
            requestID = UUID().uuidString.lowercased()
            link.baseImportedLocally = false
        }
        link.importRequestID = requestID
        link.importRequestState = SyncImportRequestState.pendingSend.rawValue
        configuration.link = link
        try persist()
        if let previousRequestID, previousRequestID != requestID { discardIncomingBase(requestID: previousRequestID) }
        _ = try await synchronize()
    }

    func importPrincipalBase() async throws {
        guard !importingBase else { throw SyncError.remote("Ya hay una importación en curso.") }
        importingBase = true
        defer { importingBase = false }
        guard isSecondary, var link, let requestID = link.importRequestID,
              Self.validImportRequestID(requestID) else { throw SyncError.permission }
        var state = link.importState
        guard state == .authorized || state == .consumed else {
            throw SyncError.remote(state.userMessage ?? "La importación no está autorizada.")
        }

        if !link.hasImportedBaseLocally {
            let downloaded = isCompleteIncomingBase(requestID: requestID)
            let hasPendingOperations = pendingCount > 0
            if hasPendingOperations || (state == .authorized && !downloaded) {
                _ = try await synchronize()
                if hasPendingOperations { discardIncomingBase(requestID: requestID) }
                guard pendingCount == 0 else {
                    throw SyncError.remote("Quedan operaciones locales por enviar. Sincroniza con la principal antes de reemplazar esta base.")
                }
                guard let refreshed = configuration.link,
                      refreshed.importRequestID == requestID else { throw SyncError.permission }
                link = refreshed
                state = link.importState
                guard state == .authorized || state == .consumed else {
                    throw SyncError.remote(state.userMessage ?? "La autorización ya no está disponible.")
                }
            }
        }

        isBusy = true
        status = link.hasImportedBaseLocally ? "Finalizando cambio a principal…" : "Descargando la base autorizada…"
        defer { isBusy = false }

        if !link.hasImportedBaseLocally {
            if state == .consumed && !isCompleteIncomingBase(requestID: requestID) {
                throw SyncError.permission
            }
            let backupURL = try await downloadIncomingBase(requestID: requestID)
            let backup = try Data(contentsOf: backupURL, options: [.mappedIfSafe])
            try store.restoreEncryptedBackup(backup, password: "")
            guard var updated = configuration.link, updated.importRequestID == requestID else { throw SyncError.permission }
            updated.baseImportedLocally = true
            configuration.link = updated
            try persist()
            link = updated
        }

        state = link.importState
        if state == .authorized {
            guard try await confirmBaseImport(requestID: requestID) else {
                throw SyncError.remote("La autorización ya no está disponible. Solicita otra autorización o empieza con una base nueva.")
            }
        } else if state != .consumed {
            throw SyncError.permission
        }
        try await becomePrincipal(resetData: false, discardedRequestID: requestID)
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
            let approvedClosePending = store.activeShifts.contains {
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
        let synchronizationLocation = store.activeLocationName
        do {
            let id = nextID()
            let batch = try SyncCatalog.pendingBatch(app: store.data)
            var message = SyncJSON.message("sync", ["id": id, "lote": batch, "cambiosAplicados": link.changesAcknowledged,
                "solicitaCierre": link.closeRequested, "versionCode": 1])
            if let hash = link.catalogHash { message["hash"] = hash }
            if let phone = link.phone { message["telefono"] = phone }
            if let requestID = link.importRequestID { message["solicitudImportacionId"] = requestID }
            let response = try await clientRequest(message, expected: "sync_ok")
            guard store.activeLocationName == synchronizationLocation else {
                throw SyncError.remote("Se cambió el local durante la sincronización. Inténtalo de nuevo.")
            }
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
            let answeredRequestID = AndroidBackupCodec.optionalString(response["solicitudImportacionId"])
            if let requestID = link.importRequestID {
                link.importRequestState = answeredRequestID == requestID
                    ? (AndroidBackupCodec.optionalString(response["estadoSolicitudImportacion"])
                        ?? SyncImportRequestState.incompatible.rawValue)
                    : SyncImportRequestState.incompatible.rawValue
            } else {
                link.importRequestState = SyncImportRequestState.none.rawValue
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
            if store.activeShifts.contains(where: { !$0.synced && $0.closedAt != nil && $0.closingCount?.approvedAt != nil }) {
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

    private func serveBaseBlock(_ request: [String: Any], employeeID: Int64) async -> [String: Any] {
        let messageID = AndroidBackupCodec.int64(request["id"])
        let requestID = AndroidBackupCodec.string(request["solicitudId"])
        let offset = AndroidBackupCodec.int64(request["desde"])
        guard Self.validImportRequestID(requestID), offset >= 0,
              let employee = store.linkedApps.first(where: { $0.syncID == employeeID && $0.syncActive }),
              employee.importRequestID == requestID,
              SyncImportRequestState(serverValue: employee.importRequestState) == .authorized else {
            return baseBlockError(messageID, requestID, offset, "no_autorizada")
        }
        do {
            let record = try prepareOutgoingBase(employeeID: employeeID, requestID: requestID)
            let total = try fileSize(record.url)
            guard total > 0, total <= Self.maximumBaseBytes, offset <= total else {
                return baseBlockError(messageID, requestID, offset, "no_disponible")
            }
            let bytes: Data
            if offset == total {
                bytes = Data()
            } else {
                let file = try FileHandle(forReadingFrom: record.url)
                defer { try? file.close() }
                try file.seek(toOffset: UInt64(offset))
                bytes = try file.read(upToCount: Int(min(Int64(Self.baseChunkBytes), total - offset))) ?? Data()
                guard !bytes.isEmpty else { return baseBlockError(messageID, requestID, offset, "no_disponible") }
            }
            return SyncJSON.message("base_bloque", ["id": messageID, "solicitudId": requestID, "desde": offset,
                "datos": bytes.base64EncodedString(), "total": total, "sha256": record.sha256])
        } catch {
            return baseBlockError(messageID, requestID, offset, "no_disponible")
        }
    }

    private func confirmBaseOnPrincipal(_ request: [String: Any], employeeID: Int64) throws -> [String: Any] {
        let messageID = AndroidBackupCodec.int64(request["id"])
        let requestID = AndroidBackupCodec.string(request["solicitudId"])
        guard Self.validImportRequestID(requestID),
              var employee = store.linkedApps.first(where: { $0.syncID == employeeID && $0.syncActive }),
              employee.importRequestID == requestID else {
            return SyncJSON.message("base_confirmada", ["id": messageID, "confirmada": false])
        }
        let state = SyncImportRequestState(serverValue: employee.importRequestState)
        if state == .authorized {
            employee.importRequestState = SyncImportRequestState.consumed.rawValue
            try store.saveLinkedApp(employee)
            discardOutgoingBase(employeeID: employeeID, requestID: requestID)
        } else if state != .consumed {
            return SyncJSON.message("base_confirmada", ["id": messageID, "confirmada": false])
        }
        return SyncJSON.message("base_confirmada", ["id": messageID, "confirmada": true])
    }

    private func confirmBaseImport(requestID: String) async throws -> Bool {
        let id = nextID()
        let response = try await clientRequest(SyncJSON.message("base_confirmar", ["id": id, "solicitudId": requestID]),
            expected: "base_confirmada")
        guard AndroidBackupCodec.int64(response["id"]) == id else { throw SyncError.invalidMessage }
        return AndroidBackupCodec.bool(response["confirmada"])
    }

    private func downloadIncomingBase(requestID: String) async throws -> URL {
        guard Self.validImportRequestID(requestID) else { throw SyncError.invalidMessage }
        let (destination, marker) = try baseFileURLs(folder: Self.incomingBaseFolder, requestID: requestID)
        if isCompleteIncomingBase(requestID: requestID) { return destination }
        let fileManager = FileManager.default
        if !fileManager.fileExists(atPath: destination.path) {
            guard fileManager.createFile(atPath: destination.path, contents: nil) else { throw SyncError.storage }
        }
        var offset = (try? fileSize(destination)) ?? 0
        if offset < 0 || offset > Self.maximumBaseBytes {
            try discardBaseFiles(destination: destination, marker: marker)
            guard fileManager.createFile(atPath: destination.path, contents: nil) else { throw SyncError.storage }
            offset = 0
        }
        var expectedTotal: Int64 = -1
        var expectedSHA: String?
        var response = try await requestBaseBlock(requestID: requestID, offset: offset)

        while true {
            try validateBaseBlock(response, requestID: requestID, expectedOffset: offset)
            var total = AndroidBackupCodec.int64(response["total"])
            var sha = AndroidBackupCodec.optionalString(response["sha256"])?.lowercased() ?? ""
            guard total > 0, total <= Self.maximumBaseBytes, Self.validBaseHash(sha) else {
                throw SyncError.remote("La copia recibida no tiene un tamaño o una huella válidos.")
            }
            if expectedTotal < 0 {
                expectedTotal = total
                expectedSHA = sha
                let previous = readBaseMetadata(marker)
                let metadataMatches = previous?.requestID == requestID && previous?.total == total && previous?.sha256 == sha
                if offset > total || (offset > 0 && !metadataMatches) {
                    let file = try FileHandle(forUpdating: destination)
                    try file.truncate(atOffset: 0)
                    try file.close()
                    offset = 0
                    try writeBaseMetadata(marker, requestID: requestID, total: total, sha256: sha)
                    response = try await requestBaseBlock(requestID: requestID, offset: 0)
                    try validateBaseBlock(response, requestID: requestID, expectedOffset: 0)
                    let restartedTotal = AndroidBackupCodec.int64(response["total"])
                    let restartedSHA = AndroidBackupCodec.optionalString(response["sha256"])?.lowercased() ?? ""
                    guard restartedTotal == total, restartedSHA == sha else {
                        throw SyncError.remote("La copia cambió durante la descarga.")
                    }
                } else {
                    try writeBaseMetadata(marker, requestID: requestID, total: total, sha256: sha)
                }
            } else if total != expectedTotal || sha != expectedSHA {
                throw SyncError.remote("La copia cambió durante la descarga.")
            }

            if offset == expectedTotal { break }
            let encoded = AndroidBackupCodec.string(response["datos"])
            guard let bytes = Data(base64Encoded: encoded), !bytes.isEmpty,
                  bytes.count <= Self.baseChunkBytes,
                  offset + Int64(bytes.count) <= expectedTotal else {
                throw SyncError.remote("La copia recibida contiene un bloque inválido.")
            }
            let file = try FileHandle(forUpdating: destination)
            try file.seek(toOffset: UInt64(offset))
            try file.write(contentsOf: bytes)
            try file.close()
            offset += Int64(bytes.count)
            response = try await requestBaseBlock(requestID: requestID, offset: offset)
        }

        guard try fileSize(destination) == expectedTotal,
              try sha256File(destination) == expectedSHA else {
            try discardBaseFiles(destination: destination, marker: marker)
            throw SyncError.remote("La copia no pasó la verificación SHA-256.")
        }
        return destination
    }

    private func requestBaseBlock(requestID: String, offset: Int64) async throws -> [String: Any] {
        guard let link, link.importRequestID == requestID,
              link.importState == .authorized else { throw SyncError.permission }
        let id = nextID()
        let response = try await clientRequest(SyncJSON.message("base_pedir", [
            "id": id, "solicitudId": requestID, "desde": offset
        ]), expected: "base_bloque")
        guard AndroidBackupCodec.int64(response["id"]) == id else { throw SyncError.invalidMessage }
        return response
    }

    private func validateBaseBlock(_ response: [String: Any], requestID: String, expectedOffset: Int64) throws {
        guard AndroidBackupCodec.string(response["solicitudId"]) == requestID,
              AndroidBackupCodec.int64(response["desde"]) == expectedOffset else { throw SyncError.invalidMessage }
        if let error = AndroidBackupCodec.optionalString(response["error"]) {
            if error == "no_autorizada" {
                throw SyncError.remote("La autorización ya no está disponible. Solicita otra autorización o empieza con una base nueva.")
            }
            throw SyncError.remote("La copia autorizada ya no está disponible. Pide una nueva autorización.")
        }
    }

    private func isCompleteIncomingBase(requestID: String) -> Bool {
        guard let (file, marker) = try? baseFileURLs(folder: Self.incomingBaseFolder, requestID: requestID),
              let metadata = readBaseMetadata(marker), metadata.requestID == requestID,
              let size = try? fileSize(file), size == metadata.total, size > 0, size <= Self.maximumBaseBytes,
              let hash = try? sha256File(file) else { return false }
        return hash == metadata.sha256
    }

    private func prepareOutgoingBase(employeeID: Int64, requestID: String) throws -> SyncBaseFileRecord {
        guard Self.validImportRequestID(requestID),
              let employee = store.linkedApps.first(where: { $0.syncID == employeeID && $0.syncActive }),
              employee.importRequestID == requestID,
              SyncImportRequestState(serverValue: employee.importRequestState) == .authorized else {
            throw SyncError.permission
        }
        cleanExpiredOutgoingBaseFiles()
        let key = "\(employeeID):\(requestID)"
        let (destination, marker) = try baseFileURLs(folder: Self.outgoingBaseFolder, requestID: requestID, employeeID: employeeID)
        if let cached = outgoingBaseFiles[key],
           let size = try? fileSize(cached.url), size > 0, size <= Self.maximumBaseBytes,
           let modification = try? FileManager.default.attributesOfItem(atPath: cached.url.path)[.modificationDate] as? Date,
           modification >= Date().addingTimeInterval(-Self.baseCacheLifetime) {
            return cached
        }
        outgoingBaseFiles.removeValue(forKey: key)
        if let metadata = readBaseMetadata(marker), metadata.requestID == requestID,
           let size = try? fileSize(destination), size == metadata.total, size > 0, size <= Self.maximumBaseBytes,
           let modification = try? FileManager.default.attributesOfItem(atPath: destination.path)[.modificationDate] as? Date,
           modification >= Date().addingTimeInterval(-Self.baseCacheLifetime),
           let actualHash = try? sha256File(destination), actualHash == metadata.sha256 {
            let record = SyncBaseFileRecord(url: destination, sha256: actualHash)
            outgoingBaseFiles[key] = record
            return record
        }

        let fileManager = FileManager.default
        try? fileManager.removeItem(at: destination)
        try? fileManager.removeItem(at: marker)
        let temporary = destination.appendingPathExtension("tmp")
        try? fileManager.removeItem(at: temporary)
        // El respaldo interno omite la licencia. La clave integrada del formato passwordless está ofuscada, no es
        // confidencial frente a quien inspeccione la app; la sesión autenticada AES-GCM protege el transporte.
        var backup = try AndroidBackupCodec.encode(store.data, password: "")
        defer { if !backup.isEmpty { backup.resetBytes(in: 0..<backup.count) } }
        guard !backup.isEmpty, backup.count <= Self.maximumBaseBytes else { throw SyncError.storage }
        try backup.write(to: temporary, options: .atomic)
        let hash = try sha256File(temporary)
        try fileManager.moveItem(at: temporary, to: destination)
        try writeBaseMetadata(marker, requestID: requestID, total: Int64(backup.count), sha256: hash)
        let record = SyncBaseFileRecord(url: destination, sha256: hash)
        outgoingBaseFiles[key] = record
        return record
    }

    private func baseBlockError(_ id: Int64, _ requestID: String, _ offset: Int64, _ error: String) -> [String: Any] {
        SyncJSON.message("base_bloque", ["id": id, "solicitudId": requestID, "desde": max(0, offset), "error": error])
    }

    private func discardOutgoingBase(employeeID: Int64, requestID: String) {
        let key = "\(employeeID):\(requestID)"
        outgoingBaseFiles.removeValue(forKey: key)
        guard let (file, marker) = try? baseFileURLs(folder: Self.outgoingBaseFolder, requestID: requestID, employeeID: employeeID) else { return }
        try? discardBaseFiles(destination: file, marker: marker)
    }

    private func discardAllOutgoingBaseFiles() {
        outgoingBaseFiles.removeAll()
        guard let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else { return }
        try? FileManager.default.removeItem(at: caches.appendingPathComponent(Self.outgoingBaseFolder, isDirectory: true))
    }

    private func cleanExpiredOutgoingBaseFiles(now: Date = Date()) {
        if let lastOutgoingBaseCleanup, now.timeIntervalSince(lastOutgoingBaseCleanup) < 60 * 60 { return }
        guard let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else { return }
        lastOutgoingBaseCleanup = now
        let directory = caches.appendingPathComponent(Self.outgoingBaseFolder, isDirectory: true)
        guard let files = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.contentModificationDateKey]) else { return }
        let expiration = now.addingTimeInterval(-Self.baseCacheLifetime)
        for file in files {
            guard let attributes = try? FileManager.default.attributesOfItem(atPath: file.path),
                  let modified = attributes[.modificationDate] as? Date else { continue }
            if modified < expiration { try? FileManager.default.removeItem(at: file) }
        }
        outgoingBaseFiles = outgoingBaseFiles.filter { entry in
            guard let attributes = try? FileManager.default.attributesOfItem(atPath: entry.value.url.path),
                  let modified = attributes[.modificationDate] as? Date else { return false }
            return modified >= expiration
        }
    }

    private func discardIncomingBase(requestID: String) {
        guard let (file, marker) = try? baseFileURLs(folder: Self.incomingBaseFolder, requestID: requestID) else { return }
        try? discardBaseFiles(destination: file, marker: marker)
    }

    private func baseFileURLs(folder: String, requestID: String, employeeID: Int64? = nil) throws -> (URL, URL) {
        guard Self.validImportRequestID(requestID),
              let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first else { throw SyncError.storage }
        let directory = caches.appendingPathComponent(folder, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let digest = SHA256.hash(data: Data(requestID.utf8)).map { String(format: "%02x", $0) }.joined()
        let stem = (employeeID.map { "\($0)_" } ?? "") + String(digest.prefix(24))
        return (directory.appendingPathComponent("\(stem).shalev"), directory.appendingPathComponent("\(stem).meta"))
    }

    private func discardBaseFiles(destination: URL, marker: URL) throws {
        try? FileManager.default.removeItem(at: destination)
        try? FileManager.default.removeItem(at: marker)
        try? FileManager.default.removeItem(at: destination.appendingPathExtension("tmp"))
    }

    private func fileSize(_ file: URL) throws -> Int64 {
        let attributes = try FileManager.default.attributesOfItem(atPath: file.path)
        guard let size = attributes[.size] as? NSNumber else { throw SyncError.storage }
        return size.int64Value
    }

    private func readBaseMetadata(_ file: URL) -> (requestID: String, total: Int64, sha256: String)? {
        guard let raw = try? String(contentsOf: file, encoding: .utf8) else { return nil }
        let fields = raw.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        guard fields.count == 3, Self.validImportRequestID(fields[0]),
              let total = Int64(fields[1]), Self.validBaseHash(fields[2]) else { return nil }
        return (fields[0], total, fields[2])
    }

    private func writeBaseMetadata(_ file: URL, requestID: String, total: Int64, sha256: String) throws {
        try "\(requestID)\n\(total)\n\(sha256)".write(to: file, atomically: true, encoding: .utf8)
    }

    private func sha256File(_ file: URL) throws -> String {
        guard let stream = InputStream(url: file) else { throw SyncError.storage }
        stream.open()
        defer { stream.close() }
        var hasher = SHA256()
        var buffer = [UInt8](repeating: 0, count: 64 * 1_024)
        while true {
            let count = buffer.withUnsafeMutableBufferPointer { pointer in
                stream.read(pointer.baseAddress!, maxLength: pointer.count)
            }
            if count < 0 { throw SyncError.storage }
            if count == 0 { break }
            hasher.update(data: Data(buffer[0..<count]))
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }

    private static func validBaseHash(_ value: String) -> Bool {
        value.count == 64 && value.allSatisfy { $0.isHexDigit }
    }

    private static func validImportRequestID(_ value: String) -> Bool {
        importRequestIDPattern.firstMatch(in: value, range: NSRange(value.startIndex..., in: value)) != nil
    }

    private static func newBusinessID() throws -> String {
        try SyncCrypto.random(12).map { String(format: "%02x", $0) }.joined()
    }

    private static let importRequestIDPattern = try! NSRegularExpression(
        pattern: "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
    )
    private static let baseChunkBytes = 48 * 1_024
    private static let maximumBaseBytes: Int64 = 128 * 1_024 * 1_024 + 256
    private static let baseCacheLifetime: TimeInterval = 24 * 60 * 60
    private static let incomingBaseFolder = "importacion_principal_entrante"
    private static let outgoingBaseFolder = "importacion_principal_saliente"

    private func startMaintenance() {
        guard isSecondary, maintenanceTask == nil else { return }
        maintenanceTask = Task { [weak self] in
            var backoff: UInt64 = 2
            while !Task.isCancelled {
                guard let self, self.isSecondary else { return }
                if self.isBusy || self.requestInFlight || self.syncTask != nil {
                    try? await Task.sleep(nanoseconds: 5 * 1_000_000_000)
                    continue
                }
                do {
                    // Primer ping inmediato: también abre la sesión tras un fallo inicial de vinculación/arranque.
                    let id = self.nextID()
                    _ = try await self.clientRequest(SyncJSON.message("ping", ["id": id]), expected: "pong")
                    backoff = 2
                    // Completar el catálogo inicial tras un corte, sin competir con otra acción iniciada por el usuario.
                    if self.link?.lastSync == nil, !self.isBusy, !self.requestInFlight, self.syncTask == nil {
                        _ = try await self.synchronize()
                    }
                    if ["Reconectando…", "Conectando con la principal…", "Vinculada; reconectando…"].contains(self.status) {
                        self.status = "Conectada con la principal"
                    }
                    try? await Task.sleep(nanoseconds: 30 * 1_000_000_000)
                } catch {
                    if !self.isSecondary { return }
                    if let session = self.clientSession { await session.close() }
                    self.clientSession = nil
                    self.status = "Reconectando…"
                    try? await Task.sleep(nanoseconds: backoff * 1_000_000_000)
                    backoff = min(60, backoff * 2)
                }
            }
        }
    }

    private func downloadPendingPhotos() async throws {
        guard let link else { return }
        for photo in link.photos {
            let domain = photo.entity == "servicio" ? "service" : photo.entity == "insumo" ? "ingredient" : "product"
            let existingProduct = photo.entity == "servicio" ? nil : store.data.products.first(where: { AndroidID.int64(domain, $0.id) == photo.id })
            let existingService = photo.entity == "servicio" ? store.data.services.first(where: { AndroidID.int64(domain, $0.id) == photo.id }) : nil
            let existing = existingProduct?.imageData ?? existingService?.imageData
            let existingName = existingProduct?.syncPhotoName ?? existingService?.syncPhotoName
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
            if photo.entity == "servicio", let index = next.services.firstIndex(where: { AndroidID.int64("service", $0.id) == photo.id }) {
                next.services[index].imageData = data; next.services[index].syncPhotoName = photo.name
            } else if photo.entity != "servicio", let index = next.products.firstIndex(where: { AndroidID.int64(domain, $0.id) == photo.id }) {
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
                case "base_pedir": try await session.send(await serveBaseBlock(pending, employeeID: employeeID))
                case "base_confirmar": try await session.send(try confirmBaseOnPrincipal(pending, employeeID: employeeID))
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
        var obsoleteImportRequestID: String?
        if let index = (next.employees ?? []).firstIndex(where: { $0.syncID == employeeID }) {
            next.employees?[index].lastSyncAt = Date()
            if let incoming = AndroidBackupCodec.optionalString(message["solicitudImportacionId"]),
               Self.validImportRequestID(incoming), next.employees?[index].importRequestID != incoming {
                obsoleteImportRequestID = next.employees?[index].importRequestID
                next.employees?[index].importRequestID = incoming
                next.employees?[index].importRequestState = SyncImportRequestState.pending.rawValue
            }
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
        if let obsoleteImportRequestID, Self.validImportRequestID(obsoleteImportRequestID) {
            discardOutgoingBase(employeeID: employeeID, requestID: obsoleteImportRequestID)
        }
        guard let current = store.linkedApps.first(where: { $0.syncID == employeeID }) else { throw SyncError.invalidMessage }
        var license = SyncLicense.from(licenseManager.state, trialExpiresAt: licenseManager.trialExpirationForSync)
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
        let open = store.activeShifts.first(where: { $0.employeeID == employeeID && $0.closedAt == nil })
        response["cerrarTurno"] = current.closeRequestedAt != nil && open != nil
        response["cierreRechazado"] = rejectionPending
        if let requestID = AndroidBackupCodec.optionalString(message["solicitudImportacionId"]),
           Self.validImportRequestID(requestID), current.importRequestID == requestID {
            response["solicitudImportacionId"] = requestID
            response["estadoSolicitudImportacion"] = current.importRequestState ?? SyncImportRequestState.pending.rawValue
        }
        if let approvedOpen = store.activeShifts.first(where: {
            $0.employeeID == employeeID && $0.closedAt == nil && $0.closingCount?.approvedAt != nil
        }) {
            let row = try AndroidBackupCodec.exportShift(approvedOpen, app: store.data)
            response["conteoAprobado"] = row["conteoCierre"]
            response["conteoTurnoUuid"] = approvedOpen.id.uuidString.lowercased()
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
            || old.settings.priceAdjustments != new.settings.priceAdjustments || old.settings.profile != new.settings.profile
            || old.settings.effectiveInventoryAlertDefaults != new.settings.effectiveInventoryAlertDefaults
            || old.settings.effectivePlannedEmployees != new.settings.effectivePlannedEmployees {
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
                if clientSession == nil { clientSession = try await establishClient() }
                var command = SyncJSON.message("cmd", ["id": id, "accion": action])
                if clientCommandsUnique { command["clave"] = commandKey }
                response = try await clientRequest(command, expected: "cmd_ok")
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
        let rawKey = AndroidBackupCodec.optionalString(message["clave"])
        let key = rawKey.flatMap { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && $0.count <= 64 ? $0 : nil }
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
            for index in product.uses.indices { product.uses[index].ingredientID = SyncCatalog.canonicalIngredientID(product.uses[index].ingredientID, in: store.data.products) }
            if let existingID = SyncCatalog.productID(androidID: AndroidBackupCodec.int64(row["id"]), ingredient: false, in: store.data.products),
               let existing = store.data.products.first(where: { $0.id == existingID }) {
                product.id = existing.id
                if product.imageData == nil {
                    product.imageData = existing.imageData
                    if product.syncPhotoName == nil { product.syncPhotoName = existing.syncPhotoName }
                }
                if let read = AndroidBackupCodec.optionalInt64(action["cantidadLeida"]) {
                    let delta = product.stockMilli - read * 1_000
                    product.stockMilli = existing.stockMilli + delta
                }
            }
            try store.saveProduct(product)
            return AndroidID.int64("product", product.id)
        case "producto_eliminar":
            let androidID = AndroidBackupCodec.int64(action["id"])
            guard let id = SyncCatalog.productID(androidID: androidID, ingredient: false, in: store.data.products) else { throw BusinessError.unavailableItem("producto") }
            try store.deleteProduct(id); return nil
        case "producto_stock":
            let androidID = AndroidBackupCodec.int64(action["id"])
            guard let id = SyncCatalog.productID(androidID: androidID, ingredient: false, in: store.data.products) else { throw BusinessError.unavailableItem("producto") }
            let delta = AndroidBackupCodec.int64(action["delta"]) * 1_000
            try store.adjustStock(productID: id, newStockMilli: (store.data.products.first(where: { $0.id == id })?.stockMilli ?? 0) + delta,
                reason: AndroidBackupCodec.string(action["nota"], default: "Ajuste desde app secundaria"))
            return (store.data.products.first(where: { $0.id == id })?.stockMilli ?? 0) / 1_000
        case "producto_precios":
            guard let prices = action["precios"] as? [String: Any] else { throw SyncError.invalidMessage }
            for (key, value) in prices {
                guard let id = SyncCatalog.productID(androidID: Int64(key) ?? 0, ingredient: false, in: store.data.products),
                      var product = store.data.products.first(where: { $0.id == id && $0.kind != .ingredient }) else { continue }
                product.priceCents = AndroidBackupCodec.int64(value)
                try store.saveProduct(product)
            }
            return nil
        case "insumo_guardar":
            guard let row = action["insumo"] as? [String: Any] else { throw SyncError.invalidMessage }
            var product = try AndroidBackupCodec.importIngredient(row)
            if let existingID = SyncCatalog.productID(androidID: AndroidBackupCodec.int64(row["id"]), ingredient: true, in: store.data.products),
               let existing = store.data.products.first(where: { $0.id == existingID }) {
                product.id = existing.id
                if product.imageData == nil {
                    product.imageData = existing.imageData
                    if product.syncPhotoName == nil { product.syncPhotoName = existing.syncPhotoName }
                }
                if let read = AndroidBackupCodec.optionalInt64(action["cantidadLeidaMil"]) {
                    product.stockMilli = existing.stockMilli + product.stockMilli - read
                }
            }
            try store.saveProduct(product)
            return AndroidID.int64("ingredient", product.id)
        case "insumo_eliminar":
            let androidID = AndroidBackupCodec.int64(action["id"])
            guard let id = SyncCatalog.productID(androidID: androidID, ingredient: true, in: store.data.products) else { throw BusinessError.unavailableItem("insumo") }
            try store.deleteProduct(id); return nil
        case "insumo_stock":
            let androidID = AndroidBackupCodec.int64(action["id"])
            guard let id = SyncCatalog.productID(androidID: androidID, ingredient: true, in: store.data.products) else { throw BusinessError.unavailableItem("insumo") }
            let current = store.data.products.first(where: { $0.id == id })?.stockMilli ?? 0
            try store.adjustStock(productID: id, newStockMilli: current + AndroidBackupCodec.int64(action["deltaMil"]),
                reason: AndroidBackupCodec.string(action["nota"], default: "Ajuste desde app secundaria"))
            return store.data.products.first(where: { $0.id == id })?.stockMilli
        case "servicio_guardar":
            guard let row = action["servicio"] as? [String: Any] else { throw SyncError.invalidMessage }
            var service = AndroidBackupCodec.importService(row)
            for index in service.uses.indices { service.uses[index].ingredientID = SyncCatalog.canonicalIngredientID(service.uses[index].ingredientID, in: store.data.products) }
            if let existingID = SyncCatalog.serviceID(androidID: AndroidBackupCodec.int64(row["id"]), in: store.data.services),
               let old = store.data.services.first(where: { $0.id == existingID }) {
                service.id = old.id
                if service.imageData == nil {
                    service.imageData = old.imageData
                    if service.syncPhotoName == nil { service.syncPhotoName = old.syncPhotoName }
                }
            }
            try store.saveService(service)
            return AndroidID.int64("service", service.id)
        case "servicio_eliminar":
            let androidID = AndroidBackupCodec.int64(action["id"])
            guard let id = SyncCatalog.serviceID(androidID: androidID, in: store.data.services) else { throw BusinessError.unavailableItem("servicio") }
            try store.deleteService(id); return nil
        case "preajuste_guardar":
            guard let row = action["preajuste"] as? [String: Any] else { throw SyncError.invalidMessage }
            var adjustment = AndroidBackupCodec.importAdjustment(row)
            if let id = SyncCatalog.adjustmentID(androidID: AndroidBackupCodec.int64(row["id"]), in: store.data.settings.priceAdjustments) { adjustment.id = id }
            if let ids = adjustment.productIDs {
                let localProducts = store.data.products.filter { $0.localName == store.activeLocationName && $0.kind != .ingredient }
                let localServices = store.data.services.filter { $0.localName == store.activeLocationName }
                adjustment.productIDs = Set(ids.compactMap { sourceID in
                    SyncCatalog.productID(androidID: AndroidID.int64("product", sourceID), ingredient: false, in: localProducts)
                        ?? SyncCatalog.serviceID(androidID: AndroidID.int64("service", sourceID), in: localServices)
                })
            }
            try store.savePriceAdjustment(adjustment)
            return AndroidID.int64("adjustment", adjustment.id)
        case "preajuste_eliminar":
            let androidID = AndroidBackupCodec.int64(action["id"])
            guard let id = SyncCatalog.adjustmentID(androidID: androidID, in: store.data.settings.priceAdjustments) else { throw BusinessError.unavailableItem("preajuste") }
            try store.deletePriceAdjustment(id); return nil
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
