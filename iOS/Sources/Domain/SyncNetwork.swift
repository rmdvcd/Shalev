import Foundation
import Network
import Darwin

/// Los callbacks de Network pueden llegar más de una vez; la continuación se consume bajo lock.
private final class SyncOnce<Value>: @unchecked Sendable {
    private var continuation: CheckedContinuation<Value, Error>?
    private let lock = NSLock()
    init(_ continuation: CheckedContinuation<Value, Error>) { self.continuation = continuation }
    func resume(_ result: Result<Value, Error>) {
        lock.lock()
        let current = continuation
        continuation = nil
        lock.unlock()
        guard let current else { return }
        switch result {
        case .success(let value): current.resume(returning: value)
        case .failure(let error): current.resume(throwing: error)
        }
    }
}

/// Una conexión TCP con tramas de longitud fija. Hay un solo lector y las escrituras se serializan en el actor.
actor SyncSocket {
    private let connection: NWConnection
    private let queue = DispatchQueue(label: "cu.shalev.ishalev.sync.socket")
    private var pending = Data()
    private var finished = false

    init(host: String, port: Int) {
        let tcp = NWProtocolTCP.Options()
        tcp.noDelay = true
        let parameters = NWParameters(tls: nil, tcp: tcp)
        connection = NWConnection(host: NWEndpoint.Host(host), port: NWEndpoint.Port(rawValue: UInt16(port))!, using: parameters)
    }

    init(endpoint: NWEndpoint) {
        let tcp = NWProtocolTCP.Options()
        tcp.noDelay = true
        let parameters = NWParameters(tls: nil, tcp: tcp)
        connection = NWConnection(to: endpoint, using: parameters)
    }

    init(connection: NWConnection) { self.connection = connection }

    func start() async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let once = SyncOnce(continuation)
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready: once.resume(.success(()))
                case .failed(let error): once.resume(.failure(error))
                case .cancelled: once.resume(.failure(SyncError.disconnected))
                default: break
                }
            }
            connection.start(queue: queue)
        }
    }

    func cancel() {
        finished = true
        connection.stateUpdateHandler = nil
        connection.cancel()
    }

    func readFrame() async throws -> Data {
        let prefix = try await readExact(4)
        let count = try SyncFrames.decodeLength(prefix)
        return try await readExact(count)
    }

    func writeFrame(_ data: Data) async throws {
        guard !finished else { throw SyncError.disconnected }
        var framed = try SyncFrames.lengthPrefix(data.count)
        framed.append(data)
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            connection.send(content: framed, completion: .contentProcessed { error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume() }
            })
        }
    }

    private func readExact(_ count: Int) async throws -> Data {
        guard count >= 0 else { throw SyncError.invalidMessage }
        while pending.count < count {
            if finished { throw SyncError.disconnected }
            let chunk = try await receive()
            if chunk.isEmpty { throw SyncError.disconnected }
            pending.append(chunk)
        }
        let result = pending.prefix(count)
        pending.removeFirst(count)
        return Data(result)
    }

    private func receive() async throws -> Data {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            connection.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1_024) { content, _, complete, error in
                if let error { continuation.resume(throwing: error); return }
                if let content, !content.isEmpty { continuation.resume(returning: content); return }
                if complete { continuation.resume(throwing: SyncError.disconnected); return }
                continuation.resume(throwing: SyncError.disconnected)
            }
        }
    }
}

actor SyncEncryptedSession {
    let socket: SyncSocket
    private var cipher: SyncChannelCipher

    init(socket: SyncSocket, side: SyncChannelCipher.Side, employeeKey: Data, secondaryNonce: Data, principalNonce: Data) throws {
        self.socket = socket
        cipher = try SyncChannelCipher(side: side, employeeKey: employeeKey, secondaryNonce: secondaryNonce, principalNonce: principalNonce)
    }

    func send(_ message: [String: Any]) async throws {
        let frame = try cipher.seal(message)
        try await socket.writeFrame(frame)
    }

    func receive() async throws -> [String: Any] {
        let frame = try await SyncTiming.data(95, socket: self.socket) { try await self.socket.readFrame() }
        return try cipher.open(frame)
    }

    func close() async { await socket.cancel() }
}

enum SyncTiming {
    static func data(_ seconds: UInt64, socket: SyncSocket,
                     operation: @escaping @Sendable () async throws -> Data) async throws -> Data {
        try await withThrowingTaskGroup(of: Data.self) { group in
            group.addTask { try await operation() }
            group.addTask {
                try await Task.sleep(nanoseconds: seconds * 1_000_000_000)
                throw SyncError.timeout
            }
            do {
                let result = try await group.next()!
                group.cancelAll()
                return result
            } catch {
                group.cancelAll()
                await socket.cancel()
                throw error
            }
        }
    }

    static func void(_ seconds: UInt64, socket: SyncSocket,
                     operation: @escaping @Sendable () async throws -> Void) async throws {
        _ = try await data(seconds, socket: socket) {
            try await operation()
            return Data()
        }
    }
}

enum SyncConnector {
    static func connect(hosts: [String], port: Int, businessID: String) async throws -> SyncSocket {
        var lastError: Error = SyncError.disconnected
        for host in hosts.prefix(SyncLocalAddresses.maximumHosts) where SyncLocalAddresses.isUsableIPv4(host) {
            let socket = SyncSocket(host: host, port: port)
            do {
                try await SyncTiming.void(4, socket: socket) { try await socket.start() }
                return socket
            } catch { lastError = error }
        }
        if let endpoint = await SyncBonjour.find(businessID: businessID, timeout: 5) {
            let socket = SyncSocket(endpoint: endpoint)
            do {
                try await SyncTiming.void(4, socket: socket) { try await socket.start() }
                return socket
            } catch { lastError = error }
        }
        throw lastError
    }
}

enum SyncBonjour {
    static let serviceType = "_shalev._tcp"
    static func serviceName(businessID: String) -> String { "Shalev-" + businessID.prefix(12) }

    static func find(businessID: String, timeout: UInt64) async -> NWEndpoint? {
        try? await withCheckedThrowingContinuation { (continuation: CheckedContinuation<NWEndpoint?, Error>) in
            let queue = DispatchQueue(label: "cu.shalev.ishalev.sync.bonjour")
            let browser = NWBrowser(for: .bonjour(type: serviceType, domain: nil), using: .tcp)
            let once = SyncOnce<NWEndpoint?>(continuation)
            let expected = serviceName(businessID: businessID)
            func finish(_ endpoint: NWEndpoint?) {
                browser.cancel()
                once.resume(.success(endpoint))
            }
            browser.browseResultsChangedHandler = { results, _ in
                for result in results {
                    guard case .service(let name, _, _, _) = result.endpoint, name == expected else { continue }
                    finish(result.endpoint)
                    return
                }
            }
            browser.stateUpdateHandler = { state in
                if case .failed = state { finish(nil) }
                if case .cancelled = state { finish(nil) }
            }
            browser.start(queue: queue)
            queue.asyncAfter(deadline: .now() + .seconds(Int(timeout))) { finish(nil) }
        }
    }
}

/// Listener principal con anuncio Bonjour. El llamador prueba puertos consecutivos si ya están ocupados.
final class SyncListener: @unchecked Sendable {
    let listener: NWListener
    private let queue = DispatchQueue(label: "cu.shalev.ishalev.sync.listener")

    init(port: Int, businessID: String, onConnection: @escaping @Sendable (NWConnection) -> Void) throws {
        let tcp = NWProtocolTCP.Options()
        tcp.noDelay = true
        let parameters = NWParameters(tls: nil, tcp: tcp)
        listener = try NWListener(using: parameters, on: NWEndpoint.Port(rawValue: UInt16(port))!)
        listener.service = NWListener.Service(name: SyncBonjour.serviceName(businessID: businessID), type: SyncBonjour.serviceType)
        listener.newConnectionHandler = onConnection
    }

    func start() async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let once = SyncOnce(continuation)
            listener.stateUpdateHandler = { state in
                switch state {
                case .ready: once.resume(.success(()))
                case .failed(let error): once.resume(.failure(error))
                case .cancelled: once.resume(.failure(SyncError.disconnected))
                default: break
                }
            }
            listener.start(queue: queue)
        }
    }

    func cancel() {
        listener.stateUpdateHandler = nil
        listener.newConnectionHandler = nil
        listener.cancel()
    }
}

enum SyncLocalAddresses {
    static let maximumHosts = 32

    static func ipv4() -> [String] {
        var pointer: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&pointer) == 0 else { return [] }
        defer { freeifaddrs(pointer) }
        var candidates: [(interface: String, address: String)] = []
        var current = pointer
        while let item = current {
            defer { current = item.pointee.ifa_next }
            let flags = item.pointee.ifa_flags
            guard flags & UInt32(IFF_UP) != 0, flags & UInt32(IFF_LOOPBACK) == 0,
                  let socketAddress = item.pointee.ifa_addr,
                  socketAddress.pointee.sa_family == UInt8(AF_INET) else { continue }
            var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            let length = socklen_t(MemoryLayout<sockaddr_in>.size)
            guard getnameinfo(socketAddress, length, &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST) == 0 else { continue }
            let address = String(cString: host)
            guard isUsableIPv4(address), let interfaceName = item.pointee.ifa_name else { continue }
            candidates.append((String(cString: interfaceName), address))
        }
        return ordered(candidates)
    }

    /// El código QR solo transporta IPv4 numéricas utilizables, nunca hostnames ni loopback/link-local/multicast.
    static func isUsableIPv4(_ address: String) -> Bool {
        let parts = address.split(separator: ".", omittingEmptySubsequences: false)
        guard parts.count == 4 else { return false }
        var octets: [Int] = []
        for part in parts {
            guard (1...3).contains(part.count), part.utf8.allSatisfy({ $0 >= 48 && $0 <= 57 }),
                  !(part.count > 1 && part.first == "0"), let value = Int(part), (0...255).contains(value) else {
                return false
            }
            octets.append(value)
        }
        let first = octets[0], second = octets[1]
        return first != 0 && first != 127 && !(first == 169 && second == 254) && first < 224
    }

    /// LAN/Zona Wi-Fi primero; IP móvil privada/CGNAT al final para evitar que tape una ruta local que sí funciona.
    static func ordered(_ candidates: [(interface: String, address: String)]) -> [String] {
        let sorted = candidates.enumerated().filter { isUsableIPv4($0.element.address) }.sorted { lhs, rhs in
            let a = priority(interface: lhs.element.interface, address: lhs.element.address)
            let b = priority(interface: rhs.element.interface, address: rhs.element.address)
            return a == b ? lhs.offset < rhs.offset : a < b
        }
        var seen = Set<String>()
        var result: [String] = []
        for candidate in sorted where seen.insert(candidate.element.address).inserted {
            result.append(candidate.element.address)
            if result.count == maximumHosts { break }
        }
        return result
    }

    private static func priority(interface: String, address: String) -> Int {
        let name = interface.lowercased()
        let localInterface = ["wlan", "wifi", "ap", "softap", "swlan", "p2p", "en", "eth"].contains { name.hasPrefix($0) }
        let cellular = ["rmnet", "ccmni", "ccemni", "cc2mni", "pdp", "wwan", "cell"].contains { name.hasPrefix($0) }
        let privateOrCGNAT = isPrivate(address) || isCGNAT(address)
        switch (localInterface, cellular, privateOrCGNAT) {
        case (true, _, true): return 0
        case (true, _, false): return 1
        case (false, false, true): return 2
        case (false, false, false): return 3
        case (false, true, false): return 4 // Una IPv4 pública celular puede aceptar conexiones entrantes.
        case (false, true, true): return 5 // La IP móvil privada/CGNAT depende del operador y suele ser inalcanzable.
        }
    }

    private static func isPrivate(_ address: String) -> Bool {
        let parts = address.split(separator: ".").compactMap { Int($0) }
        guard parts.count == 4 else { return false }
        return parts[0] == 10 || (parts[0] == 192 && parts[1] == 168)
            || (parts[0] == 172 && (16...31).contains(parts[1]))
    }

    private static func isCGNAT(_ address: String) -> Bool {
        let parts = address.split(separator: ".").compactMap { Int($0) }
        return parts.count == 4 && parts[0] == 100 && (64...127).contains(parts[1])
    }
}
