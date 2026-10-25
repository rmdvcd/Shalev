import SwiftUI
import AVFoundation
import CoreImage
import UIKit

struct SyncView: View {
    @ObservedObject var sync: SyncManager
    @State private var adding = false
    @State private var linking = false
    @State private var confirmLink = false
    @State private var confirmUnlink = false
    @State private var errorMessage: String?

    var body: some View {
        List {
            Section(sync.isSecondary ? "App secundaria" : "App principal") {
                Label(sync.isSecondary ? (sync.link?.businessName ?? "Negocio") : "Este dispositivo guarda la base general",
                      systemImage: sync.isSecondary ? "point.3.connected.trianglepath" : "server.rack")
                if sync.isSecondary {
                    Label(sync.link?.employeeName ?? "Empleado", systemImage: "person")
                } else {
                    Label(sync.isListening ? "Disponible en \(sync.addresses.joined(separator: ", ")) : \(sync.port)" : sync.status,
                          systemImage: "wifi")
                }
                Text(sync.status).font(.caption).foregroundStyle(.secondary)
                if let error = sync.lastError { Text(error).font(.caption).foregroundStyle(.red) }
            }

            if sync.isSecondary { secondarySections } else { principalSections }
        }
        .navigationTitle("Apps vinculadas")
        .sheet(isPresented: $adding) {
            SyncEmployeeEditor(sync: sync, employee: nil)
        }
        .sheet(isPresented: $linking) {
            SyncLinkScanner { text in
                do { try await sync.linkSecondary(qr: text, phone: nil); linking = false }
                catch { errorMessage = error.localizedDescription }
            }
        }
        .alert("¿Convertir esta app en secundaria?", isPresented: $confirmLink) {
            Button("Cancelar", role: .cancel) {}
            Button("Borrar datos y continuar", role: .destructive) { linking = true }
        } message: {
            Text("Después de escanear el QR se borrarán los datos locales y esta app conservará solo el catálogo recibido y sus propias operaciones.")
        }
        .alert("¿Desvincular esta app?", isPresented: $confirmUnlink) {
            Button("Cancelar", role: .cancel) {}
            Button("Borrar datos y desvincular", role: .destructive) {
                Task { do { try await sync.unlinkSecondary() } catch { errorMessage = error.localizedDescription } }
            }
        } message: { Text("Se borrarán el catálogo y las operaciones locales de esta secundaria.") }
        .alert("Sincronización", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Entendido", role: .cancel) {}
        } message: { Text(errorMessage ?? "") }
        .onAppear { sync.start() }
    }

    @ViewBuilder private var principalSections: some View {
        Section("Secundarias") {
            ForEach(sync.linkedApps.filter(\.syncActive)) { employee in
                SyncLinkedAppRow(sync: sync, employee: employee)
            }
            Button { adding = true } label: { Label("Agregar app secundaria", systemImage: "person.badge.plus") }
        }
        Section {
            Text("La principal puede vender, editar el catálogo y administrar las licencias. Cada QR caduca en 10 minutos, se usa una sola vez y sustituye la clave anterior.")
                .font(.caption).foregroundStyle(.secondary)
            if sync.linkedApps.filter(\.syncActive).isEmpty {
                Button("Usar esta app como secundaria…") { confirmLink = true }
            }
        }
    }

    @ViewBuilder private var secondarySections: some View {
        Section("Estado") {
            Label(sync.secondaryLicenseState?.title ?? "Licencia de la principal no disponible", systemImage: "key")
            Label("Pendientes de enviar: \(sync.pendingCount)", systemImage: "arrow.triangle.2.circlepath")
            if let last = sync.link?.lastSync { Label("Última: \(last.shalevDateTime)", systemImage: "clock") }
            if sync.catalogChanged { Label("El catálogo cambió en la principal", systemImage: "exclamationmark.circle") }
            Button {
                Task { do { _ = try await sync.synchronize() } catch { errorMessage = error.localizedDescription } }
            } label: {
                if sync.isBusy { ProgressView().frame(maxWidth: .infinity) } else { Text("Sincronizar ahora") }
            }
            .disabled(sync.isBusy)
        }
        if sync.closeRequestedByPrincipal {
            Section { Label("La principal pidió cerrar tu turno. Cuéntalo y ciérralo desde Inicio.", systemImage: "lock.rotation") }
        }
        if sync.link?.closeNotice == true {
            Section {
                Label("El turno se cerró por una petición de la principal.", systemImage: "checkmark.circle")
                Button("Entendido") { sync.dismissCloseNotices() }
            }
        }
        if sync.closeRejectedByPrincipal {
            Section {
                Label("La principal no aprobó la solicitud de cierre. Puedes seguir vendiendo.", systemImage: "xmark.circle")
                Button("Entendido") { sync.dismissCloseNotices() }
            }
        }
        Section("Caja") {
            if sync.link?.closeRequested == true {
                Label("Solicitud de cierre pendiente de aprobación", systemImage: "clock")
            } else if sync.store.openShift != nil {
                Button("Solicitar cierre a la principal") {
                    Task { do { try await sync.requestSecondaryClose() } catch { errorMessage = error.localizedDescription } }
                }
            }
        }
        Section {
            Text("Vender y cerrar siguen disponibles sin red. Los turnos, ventas y movimientos se reenvían hasta que la principal confirme que los guardó.")
                .font(.caption).foregroundStyle(.secondary)
            Button("Desvincular y borrar datos…", role: .destructive) { confirmUnlink = true }
                .disabled(sync.isBusy)
        }
    }
}

private struct SyncLinkedAppRow: View {
    @ObservedObject var sync: SyncManager
    let employee: LocalEmployee
    @State private var editing = false
    @State private var showingQR = false
    @State private var confirmRemove = false
    @State private var errorMessage: String?

    private var remoteShift: Shift? { sync.store.data.shifts.first(where: { $0.employeeID == employee.syncID && $0.closedAt == nil }) }

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            HStack {
                Image(systemName: sync.connectedEmployeeIDs.contains(employee.syncID ?? 0) ? "circle.fill" : "circle")
                    .foregroundStyle(sync.connectedEmployeeIDs.contains(employee.syncID ?? 0) ? .green : .secondary)
                Text(employee.name).font(.headline)
                Spacer()
                if employee.linkedAt == nil { Text("Pendiente").font(.caption).foregroundStyle(.orange) }
            }
            Text(employee.lastSyncAt.map { "Última sync \($0.shalevDateTime)" } ?? "Aún no sincroniza")
                .font(.caption).foregroundStyle(.secondary)
            if let shift = remoteShift {
                Text("Turno abierto \(shift.openedAt.shalevDateTime)").font(.caption)
                if let count = shift.closingCount {
                    Text(count.approvedAt == nil ? "Conteo pendiente de aprobación" : "Conteo aprobado; falta recibir el cierre")
                        .font(.caption).foregroundStyle(count.approvedAt == nil ? .orange : .green)
                    if count.approvedAt == nil {
                        HStack {
                            Button("Aprobar conteo") { do { try sync.approveClosing(employee.syncID ?? 0) } catch { errorMessage = error.localizedDescription } }
                            Button("Rechazar", role: .destructive) { do { try sync.rejectClosing(employee.syncID ?? 0) } catch { errorMessage = error.localizedDescription } }
                        }
                    }
                } else if employee.closeRequestedAt == nil {
                    Button("Pedir cierre") {
                        var updated = employee; updated.closeRequestedAt = Date()
                        do { try sync.updateLinkedApp(updated) } catch { errorMessage = error.localizedDescription }
                    }
                }
            }
            if employee.employeeCloseRequestAt != nil && employee.employeeCloseRequestAt?.timeIntervalSince1970 != 0 {
                Text("El empleado solicita cerrar").font(.caption).foregroundStyle(.orange)
            }
            HStack {
                Button("QR") { showingQR = true }
                Button("Permisos y cobro") { editing = true }
                Spacer()
                Button("Quitar", role: .destructive) { confirmRemove = true }
            }.font(.subheadline)
        }
        .padding(.vertical, 5)
        .sheet(isPresented: $editing) { SyncEmployeeEditor(sync: sync, employee: employee) }
        .sheet(isPresented: $showingQR) { SyncPairingQRView(sync: sync, employee: employee) }
        .alert("¿Quitar \(employee.name)?", isPresented: $confirmRemove) {
            Button("Cancelar", role: .cancel) {}
            Button("Quitar", role: .destructive) {
                Task { do { try await sync.removeLinkedApp(employee.syncID ?? 0) } catch { errorMessage = error.localizedDescription } }
            }
        } message: { Text("La próxima conexión autenticada borrará los datos de esa secundaria.") }
        .alert("Sincronización", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Entendido", role: .cancel) {}
        } message: { Text(errorMessage ?? "") }
    }
}

private struct SyncEmployeeEditor: View {
    @Environment(\.dismiss) private var dismiss
    let sync: SyncManager
    let employee: LocalEmployee?
    @State private var name = ""
    @State private var sellProducts = true
    @State private var sellServices = true
    @State private var editInventory = false
    @State private var changePrices = false
    @State private var exportReports = true
    @State private var cardID: UUID?
    @State private var phoneID: UUID?
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Empleado") { TextField("Nombre", text: $name) }
                Section("Permisos") {
                    Toggle("Vender productos", isOn: $sellProducts)
                    Toggle("Vender servicios", isOn: $sellServices)
                    Toggle("Editar inventario y servicios", isOn: $editInventory)
                    Toggle("Cambiar precios y preajustes", isOn: $changePrices)
                    Toggle("Exportar y compartir", isOn: $exportReports)
                }
                Section("Cobro por transferencia") {
                    Picker("Tarjeta", selection: $cardID) {
                        Text("Predeterminada").tag(UUID?.none)
                        ForEach(sync.store.data.settings.profile.availableCards) { card in
                            Text("\(card.alias) · \(card.number)").tag(UUID?.some(card.id))
                        }
                    }
                    Picker("Teléfono", selection: $phoneID) {
                        Text("Predeterminado").tag(UUID?.none)
                        ForEach(sync.store.data.settings.profile.availablePhones) { phone in
                            Text("\(phone.alias) · \(phone.number)").tag(UUID?.some(phone.id))
                        }
                    }
                }
            }
            .navigationTitle(employee == nil ? "Agregar secundaria" : "Permisos")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Guardar", action: save) }
            }
            .onAppear {
                name = employee?.name ?? ""
                sellProducts = employee?.permissions.contains(.sellProducts) ?? true
                sellServices = employee?.permissions.contains(.sellServices) ?? true
                editInventory = employee?.permissions.contains(.editInventory) ?? false
                changePrices = employee?.permissions.contains(.changePrices) ?? false
                exportReports = employee?.permissions.contains(.exportReports) ?? true
                cardID = employee?.paymentCardID; phoneID = employee?.paymentPhoneID
            }
            .alert("No se pudo guardar", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Entendido", role: .cancel) {}
            } message: { Text(errorMessage ?? "") }
        }
    }

    private func save() {
        var permissions = Set<EmployeePermission>()
        if sellProducts { permissions.formUnion([.sellProducts, .viewInventory, .viewRecords, .manageCash]) }
        if sellServices { permissions.formUnion([.sellServices, .viewRecords, .manageCash]) }
        if editInventory { permissions.formUnion([.editInventory, .viewInventory]) }
        if changePrices { permissions.insert(.changePrices) }
        if exportReports { permissions.insert(.exportReports) }
        do {
            if var employee {
                employee.name = name; employee.permissions = permissions
                employee.paymentCardID = cardID; employee.paymentPhoneID = phoneID
                try sync.updateLinkedApp(employee)
            } else { _ = try sync.addLinkedApp(name: name, permissions: permissions) }
            dismiss()
        } catch { errorMessage = error.localizedDescription }
    }
}

private struct SyncPairingQRView: View {
    @Environment(\.dismiss) private var dismiss
    let sync: SyncManager
    let employee: LocalEmployee
    @State private var code: SyncPairingCode?
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 18) {
                if let code, let text = try? code.encoded() {
                    QRCodeImage(text: text)
                    Text("Vincular a \(employee.name)").font(.headline)
                    Text("Vence \(code.expiresAt.shalevDateTime)").font(.caption).foregroundStyle(.secondary)
                    ShareLink(item: text) { Label("Compartir código", systemImage: "square.and.arrow.up") }
                    Text("El QR contiene el token inicial, no la clave definitiva del empleado. La clave se acuerda después con ECDH P-256.")
                        .font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
                } else if let errorMessage { Text(errorMessage).foregroundStyle(.red) }
                else { ProgressView("Generando código…") }
                Button("Generar uno nuevo") { Task { await generate() } }
            }
            .padding()
            .navigationTitle("Código de vinculación")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Listo") { dismiss() } } }
            .task { if code == nil { await generate() } }
        }
    }

    private func generate() async {
        do { code = try await sync.pairingCode(for: employee.syncID ?? 0); errorMessage = nil }
        catch { errorMessage = error.localizedDescription }
    }
}

private struct QRCodeImage: View {
    let text: String
    var body: some View {
        if let image = Self.make(text) {
            Image(uiImage: image).interpolation(.none).resizable().scaledToFit()
                .frame(maxWidth: 280, maxHeight: 280).padding().background(.white).clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }
    private static func make(_ text: String) -> UIImage? {
        guard let filter = CIFilter(name: "CIQRCodeGenerator") else { return nil }
        filter.setValue(Data(text.utf8), forKey: "inputMessage")
        filter.setValue("M", forKey: "inputCorrectionLevel")
        guard let output = filter.outputImage else { return nil }
        let scale = UIScreen.main.scale * 8
        let image = output.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        return UIImage(ciImage: image)
    }
}

private struct SyncLinkScanner: View {
    @Environment(\.dismiss) private var dismiss
    let onCode: (String) async throws -> Void
    @State private var manual = ""
    @State private var cameraAllowed = false
    @State private var working = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    if cameraAllowed {
                        QRScannerView { text in submit(text) }
                            .frame(height: 320).clipShape(RoundedRectangle(cornerRadius: 16))
                    } else {
                        Label("Escanea el QR o pega aquí el texto compartido por la principal.", systemImage: "qrcode.viewfinder")
                    }
                    TextEditor(text: $manual).frame(minHeight: 100).padding(6)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(.secondary))
                    Button("Vincular con este código") { submit(manual) }
                        .buttonStyle(.borderedProminent).disabled(working || manual.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    if working { ProgressView() }
                }.padding()
            }
            .navigationTitle("Vincular secundaria")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancelar") { dismiss() } } }
            .task {
                if AVCaptureDevice.authorizationStatus(for: .video) == .authorized { cameraAllowed = true }
                else if AVCaptureDevice.authorizationStatus(for: .video) == .notDetermined {
                    cameraAllowed = await AVCaptureDevice.requestAccess(for: .video)
                }
            }
            .alert("Vinculación", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Entendido", role: .cancel) {}
            } message: { Text(errorMessage ?? "") }
        }
    }

    private func submit(_ text: String) {
        guard !working else { return }
        working = true
        Task {
            do { try await onCode(text); dismiss() }
            catch { errorMessage = error.localizedDescription; working = false }
        }
    }
}

private struct QRScannerView: UIViewRepresentable {
    let onCode: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(onCode: onCode) }

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        let session = context.coordinator.session
        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.videoGravity = .resizeAspectFill
        view.layer.addSublayer(preview)
        context.coordinator.preview = preview
        DispatchQueue.main.async { preview.frame = view.bounds }
        guard let device = AVCaptureDevice.default(for: .video), let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else { return view }
        session.addInput(input)
        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else { return view }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(context.coordinator, queue: .main)
        output.metadataObjectTypes = [.qr]
        context.coordinator.queue.async { session.startRunning() }
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) { context.coordinator.preview?.frame = uiView.bounds }
    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) { coordinator.session.stopRunning() }

    final class Coordinator: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        let session = AVCaptureSession()
        let queue = DispatchQueue(label: "cu.shalev.ishalev.qr")
        let onCode: (String) -> Void
        var preview: AVCaptureVideoPreviewLayer?
        var found = false
        init(onCode: @escaping (String) -> Void) { self.onCode = onCode }
        func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
            guard !found, let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
                  let text = object.stringValue, text.hasPrefix(SyncPairingCode.prefix) else { return }
            found = true; onCode(text)
        }
    }
}
