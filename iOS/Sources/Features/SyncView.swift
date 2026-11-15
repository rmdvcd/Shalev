import SwiftUI
import AVFoundation
import CoreImage
import UIKit

/// Apps vinculadas, con la misma estructura que Android: descripción, red local,
/// lista de secundarias con su estado y botón flotante para agregar.
struct SyncView: View {
    @ObservedObject var sync: SyncManager
    var allowInitialSecondary = false
    var lockedMode = false
    var onInitialSecondaryLinked: () -> Void = {}
    @AppStorage("shalev.backup.exportedAt") private var backupExportedAt = 0.0
    @State private var adding = false
    @State private var linking = false
    @State private var showBackup = false
    @State private var showBackupRequired = false
    @State private var confirmLink = false
    @State private var confirmUnlink = false
    @State private var showRoleOptions = false
    @State private var confirmImport = false
    @State private var errorMessage: String?

    private var activeEmployees: [LocalEmployee] {
        sync.linkedApps.filter(\.syncActive)
    }

    private var allowedPermissions: [EmployeePermission] {
        let allowed = sync.permissions(sync.link?.permissions ?? [])
        return EmployeePermission.allCases.filter { allowed.contains($0) }
    }

    private var initialSecondaryEligible: Bool {
        allowInitialSecondary && sync.linkedApps.isEmpty
    }

    private var currentImportState: SyncImportRequestState { sync.link?.importState ?? .none }

    private var importActionTitle: String {
        switch sync.link?.importState ?? .none {
        case .authorized, .consumed: "Importar base autorizada"
        case .pending, .pendingSend: "Consultar autorización"
        case .rejected, .none: "Solicitar importar la base"
        case .incompatible: "Actualizar la app principal"
        }
    }

    private func requestSecondaryConversion() {
        guard backupExportedAt > 0 || initialSecondaryEligible else {
            showBackupRequired = true
            return
        }
        confirmLink = true
    }

    private func runImportAction() {
        guard !sync.isBusy else { return }
        let state = currentImportState
        if state == .authorized || state == .consumed {
            confirmImport = true
            return
        }
        performImportAction(for: state)
    }

    private func performImportAction(for state: SyncImportRequestState) {
        guard !sync.isBusy else { return }
        Task {
            do {
                switch state {
                case .authorized, .consumed: try await sync.importPrincipalBase()
                case .pending, .pendingSend: _ = try await sync.synchronize()
                case .none, .rejected: try await sync.requestBaseImport()
                case .incompatible: errorMessage = state.userMessage
                }
            } catch { errorMessage = error.localizedDescription }
        }
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                if sync.isSecondary { secondaryContent } else { principalContent }
            }
            .padding(18)
            .padding(.bottom, 90)
            .frame(maxWidth: 680)
            .frame(maxWidth: .infinity)
        }
        .refreshable {
            if sync.isSecondary {
                do { _ = try await sync.synchronize() }
                catch { errorMessage = error.localizedDescription }
            } else {
                sync.start()
            }
        }
        .background(Color.shalevBackground.ignoresSafeArea())
        .navigationTitle("Apps vinculadas")
        .navigationBarTitleDisplayMode(.inline)
        .overlay(alignment: .bottomTrailing) {
            if !sync.isSecondary {
                ShalevFloatingButton(title: "Agregar app secundaria", symbol: "person.badge.plus") { adding = true }
                    .padding(.trailing, 20)
                    .padding(.bottom, 24)
            }
        }
        .sheet(isPresented: $adding) {
            SyncEmployeeEditor(sync: sync, employee: nil)
        }
        .sheet(isPresented: $linking) {
            SyncLinkScanner { text in
                do {
                    let exportedAt = backupExportedAt > 0 ? Date(timeIntervalSince1970: backupExportedAt) : nil
                    try await sync.linkSecondary(qr: text, phone: nil, initialOptIn: allowInitialSecondary,
                        backupExportedAt: exportedAt)
                    linking = false
                    onInitialSecondaryLinked()
                } catch { errorMessage = error.localizedDescription }
            }
        }
        .sheet(isPresented: $showBackup) {
            NavigationStack { BackupSettingsView(store: sync.store) }
        }
        .alert("¿Convertir esta app en secundaria?", isPresented: $confirmLink) {
            Button("Cancelar", role: .cancel) {}
            Button("Borrar datos y continuar", role: .destructive) { linking = true }
        } message: {
            Text("Después de escanear el QR se borrarán los datos locales y esta app conservará solo el catálogo recibido y sus propias operaciones.")
        }
        .alert("Antes de cambiar a secundaria", isPresented: $showBackupRequired) {
            Button("Cancelar", role: .cancel) {}
            Button("Crear respaldo") { showBackup = true }
        } message: {
            Text("Exporta un respaldo de este teléfono antes de vincularlo como secundaria. El primer vínculo sin respaldo solo se permite desde la configuración inicial, en una instalación sin apps vinculadas.")
        }
        .alert(lockedMode ? "¿Desvincular y empezar desde cero?" : "¿Empezar con una base nueva?", isPresented: $confirmUnlink) {
            Button("Cancelar", role: .cancel) {}
            Button("Borrar datos y empezar", role: .destructive) {
                Task { do { try await sync.unlinkSecondary() } catch { errorMessage = error.localizedDescription } }
            }
        } message: {
            Text("Se borrarán el catálogo y las operaciones locales de esta secundaria. No se puede deshacer.")
        }
        .confirmationDialog("Cambiar a app principal", isPresented: $showRoleOptions, titleVisibility: .visible) {
            Button("Empezar con una base nueva", role: .destructive) { confirmUnlink = true }
            Button(importActionTitle) { runImportAction() }.disabled(currentImportState == .incompatible)
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("Puedes empezar desde cero o solicitar permiso a la app principal para importar una copia completa. La importación reemplaza todos los datos de este teléfono.")
        }
        .confirmationDialog("¿Importar la base autorizada?", isPresented: $confirmImport, titleVisibility: .visible) {
            Button("Reemplazar datos e importar", role: .destructive) { performImportAction(for: currentImportState) }
            Button("Cancelar", role: .cancel) {}
        } message: {
            Text("Se reemplazarán todos los datos locales por la copia de la app principal y esta app cambiará a principal. No se puede deshacer.")
        }
        .alert("Sincronización", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Entendido", role: .cancel) {}
        } message: { Text(errorMessage ?? "") }
        .onAppear { sync.start() }
    }

    // MARK: - App principal

    @ViewBuilder private var principalContent: some View {
        Text("Esta es la app principal: guarda todos los datos y la licencia. Tus empleados se vinculan con un código QR.")
            .font(.shalev(13))
            .foregroundStyle(Color.shalevText)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)

        networkCard(title: sync.isListening ? "Red local activa" : "Red local inactiva",
                    detail: sync.isListening ? "Este teléfono: \(sync.addresses.joined(separator: ", "))" : sync.status,
                    symbol: sync.isListening ? "key.horizontal.fill" : "wifi.slash")

        Text("Secundarias (\(activeEmployees.count) de 10)")
            .font(.shalev(15, weight: .semibold))
            .foregroundStyle(Color.shalevText)
            .frame(maxWidth: .infinity)

        if activeEmployees.isEmpty {
            ShalevEmptyState(symbol: "iphone",
                             title: "Aún no hay apps secundarias",
                             detail: "Agrega una con el botón + y muestra el QR en el otro teléfono.",
                             actionTitle: "Agregar app secundaria") { adding = true }
        } else {
            VStack(spacing: 0) {
                ForEach(Array(activeEmployees.enumerated()), id: \.element.id) { index, employee in
                    SyncLinkedAppRow(sync: sync, employee: employee)
                    if index < activeEmployees.count - 1 {
                        Divider().overlay(Color.shalevOutlineVariant.opacity(0.6))
                    }
                }
            }
            .padding(.horizontal, 16)
            .background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(Color.shalevOutlineVariant.opacity(0.55), lineWidth: 0.7)
            }
        }

        Text("Cada QR caduca en 10 minutos, se usa una sola vez y sustituye la clave anterior.")
            .font(.shalev(12))
            .foregroundStyle(Color.shalevSecondaryText)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)

        Text(initialSecondaryEligible
            ? "Vinculación inicial: puedes continuar sin respaldo porque elegiste ser secundaria y aún no hay apps vinculadas."
            : backupExportedAt > 0
                ? "Antes de cambiar esta app a secundaria, conserva el respaldo exportado."
                : "Antes de cambiar esta app a secundaria, exporta un respaldo de sus datos.")
            .font(.shalev(12))
            .foregroundStyle(Color.shalevSecondaryText)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)

        Button(action: requestSecondaryConversion) {
            Label("Usar esta app como secundaria…", systemImage: "arrow.left.arrow.right")
        }
        .buttonStyle(ShalevTonalButtonStyle())
    }

    // MARK: - App secundaria

    @ViewBuilder private var secondaryContent: some View {
        if lockedMode { lockedSecondaryContent }
        else { normalSecondaryContent }
    }

    @ViewBuilder private var lockedSecondaryContent: some View {
        ShalevCard {
            VStack(spacing: 8) {
                Image(systemName: "lock.slash")
                    .font(.shalev(24, weight: .semibold))
                    .foregroundStyle(Color.shalevError)
                Text("Esta secundaria está bloqueada por la licencia de la app principal.")
                    .font(.shalev(15, weight: .semibold))
                    .foregroundStyle(Color.shalevText)
                    .multilineTextAlignment(.center)
                Text("Puedes volver a intentarlo más tarde o cambiar esta app a principal. Conserva la base autorizada o empieza desde cero.")
                    .font(.shalev(13))
                    .foregroundStyle(Color.shalevSecondaryText)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
        }
        networkCard(title: sync.status, detail: sync.lastError ?? "La licencia no impide conservar tus datos locales.",
                    symbol: "wifi.slash")
        if currentImportState != .none {
            if currentImportState == .incompatible {
                noticeCard(symbol: "externaldrive.badge.person.crop", title: "Importación de la base",
                           detail: currentImportState.userMessage ?? "")
            } else {
                noticeCard(symbol: "externaldrive.badge.person.crop", title: "Importación de la base",
                           detail: currentImportState.userMessage ?? "", actionTitle: importActionTitle) { runImportAction() }
            }
        }
        Button { showRoleOptions = true } label: {
            Label("Cambiar a app principal…", systemImage: "arrow.left.arrow.right")
        }
        .buttonStyle(ShalevTonalButtonStyle())
        .disabled(sync.isBusy)
    }

    @ViewBuilder private var normalSecondaryContent: some View {
        ShalevCard {
            VStack(spacing: 4) {
                Text("Secundaria de \(sync.link?.businessName ?? "tu negocio")")
                    .font(.shalev(17, weight: .semibold))
                    .foregroundStyle(Color.shalevText)
                    .multilineTextAlignment(.center)
                Text("Empleado: \(sync.link?.employeeName ?? "sin vincular")")
                    .font(.shalev(14))
                    .foregroundStyle(Color.shalevText)
                Text(secondarySummary)
                    .font(.shalev(12))
                    .foregroundStyle(Color.shalevSecondaryText)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
        }

        networkCard(title: sync.link == nil ? sync.status : "Conectada con la app principal",
                    detail: sync.lastError ?? "Vender y cerrar funcionan sin red.",
                    symbol: "key.horizontal.fill")

        Button {
            Task { do { _ = try await sync.synchronize() } catch { errorMessage = error.localizedDescription } }
        } label: {
            Group {
                if sync.isBusy {
                    ProgressView().tint(Color.shalevOnPrimary)
                } else {
                    Image(systemName: "arrow.triangle.2.circlepath")
                        .font(.shalev(21, weight: .semibold))
                }
            }
            .foregroundStyle(Color.shalevOnPrimary)
            .frame(width: 58, height: 58)
            .background(Color.shalevPrimary, in: Circle())
            .shadow(color: Color.shalevPrimary.opacity(0.25), radius: 7, x: 0, y: 3)
        }
        .buttonStyle(.plain)
        .disabled(sync.isBusy)
        .accessibilityLabel(sync.isBusy ? "Sincronizando…" : "Sincronizar ahora")

        if !allowedPermissions.isEmpty {
            ShalevCard {
                VStack(alignment: .leading, spacing: 6) {
                    ShalevSectionHeader(title: "Lo que puedes hacer")
                    ForEach(allowedPermissions) { permission in
                        Text("• \(permission.rawValue)")
                            .font(.shalev(13))
                            .foregroundStyle(Color.shalevText)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }

        if sync.closeRequestedByPrincipal {
            noticeCard(symbol: "lock.rotation", title: "La principal pidió cerrar tu turno",
                       detail: "Cuéntalo y ciérralo desde Inicio.")
        }
        if sync.link?.closeNotice == true {
            noticeCard(symbol: "checkmark.circle", title: "El turno se cerró por una petición de la principal",
                       detail: "No tienes que hacer nada más.", actionTitle: "Entendido") { sync.dismissCloseNotices() }
        }
        if sync.closeRejectedByPrincipal {
            noticeCard(symbol: "xmark.circle", title: "La principal no aprobó la solicitud de cierre",
                       detail: "Puedes seguir vendiendo.", actionTitle: "Entendido") { sync.dismissCloseNotices() }
        }
        let importState = sync.link?.importState ?? .none
        if !lockedMode, importState != .none {
            if importState == .incompatible {
                noticeCard(symbol: "externaldrive.badge.person.crop", title: "Importación de la base",
                           detail: importState.userMessage ?? "")
            } else {
                noticeCard(symbol: "externaldrive.badge.person.crop", title: "Importación de la base",
                           detail: importState.userMessage ?? "", actionTitle: importActionTitle) { runImportAction() }
            }
        }

        if sync.link?.closeRequested == true {
            noticeCard(symbol: "clock", title: "Solicitud de cierre pendiente de aprobación",
                       detail: "La principal la verá al conectarse.")
        } else if sync.store.openShift != nil {
            Button {
                Task { do { try await sync.requestSecondaryClose() } catch { errorMessage = error.localizedDescription } }
            } label: {
                Label("Solicitar cierre a la principal", systemImage: "lock.rotation")
            }
            .buttonStyle(ShalevTonalButtonStyle())
        }

        Text("Vender y cerrar siguen disponibles sin red. Los turnos, ventas y movimientos se reenvían hasta que la principal confirme que los guardó.")
            .font(.shalev(12))
            .foregroundStyle(Color.shalevSecondaryText)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)

        Button {
            if lockedMode { confirmUnlink = true }
            else { showRoleOptions = true }
        } label: {
            Label(lockedMode ? "Desvincular y borrar datos" : "Cambiar a app principal…",
                  systemImage: lockedMode ? "iphone.slash" : "arrow.left.arrow.right")
        }
        .buttonStyle(ShalevTonalButtonStyle())
        .disabled(sync.isBusy)
    }

    private var secondarySummary: String {
        let sent = sync.pendingCount == 0 ? "Todo enviado" : "\(sync.pendingCount) por enviar"
        guard let last = sync.link?.lastSync else { return sent }
        return "\(sent) · Última vez: \(last.shalevDateTime)"
    }

    private func networkCard(title: String, detail: String, symbol: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.shalev(20, weight: .medium))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.shalev(14, weight: .semibold))
                    .foregroundStyle(Color.shalevText)
                Text(detail)
                    .font(.shalev(12))
                    .foregroundStyle(Color.shalevSecondaryText)
            }
            Spacer(minLength: 8)
            Image(systemName: "wifi")
                .font(.shalev(18, weight: .medium))
        }
        .foregroundStyle(Color.shalevPrimary)
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func noticeCard(symbol: String, title: String, detail: String,
                            actionTitle: String? = nil, action: (() -> Void)? = nil) -> some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 6) {
                Label(title, systemImage: symbol)
                    .font(.shalev(14, weight: .semibold))
                    .foregroundStyle(Color.shalevText)
                Text(detail)
                    .font(.shalev(12))
                    .foregroundStyle(Color.shalevSecondaryText)
                if let actionTitle, let action {
                    Button(actionTitle, action: action)
                        .buttonStyle(ShalevTonalButtonStyle())
                }
            }
        }
    }
}

private struct SyncLinkedAppRow: View {
    @ObservedObject var sync: SyncManager
    let employee: LocalEmployee
    @State private var editing = false
    @State private var showingQR = false
    @State private var confirmRemove = false
    @State private var importDecision: Bool?
    @State private var errorMessage: String?

    private var importState: SyncImportRequestState { SyncImportRequestState(serverValue: employee.importRequestState) }
    private var remoteShift: Shift? { sync.store.activeShifts.first(where: { $0.employeeID == employee.syncID && $0.closedAt == nil }) }
    private var isConnected: Bool { sync.connectedEmployeeIDs.contains(employee.syncID ?? 0) }

    private var statusLine: String {
        if isConnected { return "Conectada ahora" }
        if employee.closeRequestedAt != nil { return "Cierre pedido · se cerrará al conectar" }
        if employee.lastSyncAt != nil { return "Última vez" }
        return employee.linkedAt == nil ? "Pendiente de vincular" : "Sin conexión"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: "iphone")
                    .font(.shalev(19))
                    .foregroundStyle(Color.shalevPrimary)
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(employee.name)
                        .font(.shalev(15, weight: .semibold))
                        .foregroundStyle(Color.shalevText)
                    if let last = employee.lastSyncAt, employee.closeRequestedAt != nil || isConnected {
                        Text(statusLine == "Última vez" ? "Última vez: \(last.shalevDateTime)" : "\(statusLine) · Última vez: \(last.shalevDateTime)")
                            .font(.shalev(12))
                            .foregroundStyle(isConnected ? Color.shalevSuccess : Color.shalevSecondaryText)
                    } else {
                        Text(statusLine)
                            .font(.shalev(12))
                            .foregroundStyle(isConnected ? Color.shalevSuccess : Color.shalevSecondaryText)
                    }
                }
                Spacer(minLength: 8)
                if employee.linkedAt == nil {
                    Text("Pendiente")
                        .font(.shalev(11, weight: .semibold))
                        .foregroundStyle(Color.shalevError)
                }
            }

            if let shift = remoteShift {
                Text("Turno abierto \(shift.openedAt.shalevDateTime)")
                    .font(.shalev(12))
                    .foregroundStyle(Color.shalevSecondaryText)
                if let count = shift.closingCount {
                    Text(count.approvedAt == nil ? "Conteo pendiente de aprobación" : "Conteo aprobado; falta recibir el cierre")
                        .font(.shalev(12))
                        .foregroundStyle(count.approvedAt == nil ? Color.shalevError : Color.shalevSuccess)
                    ShiftCountSummaryView(count: count, expectedCashCents: sync.store.expectedCashCents(for: shift.id))
                    if count.approvedAt == nil {
                        HStack(spacing: 14) {
                            Button("Aprobar cierre") { do { try sync.approveClosing(employee.syncID ?? 0) } catch { errorMessage = error.localizedDescription } }
                            Button("Rechazar cierre", role: .destructive) { do { try sync.rejectClosing(employee.syncID ?? 0) } catch { errorMessage = error.localizedDescription } }
                        }
                        .font(.shalev(12, weight: .semibold))
                    }
                } else if employee.closeRequestedAt == nil {
                    Button("Pedir cierre") {
                        var updated = employee; updated.closeRequestedAt = Date()
                        do { try sync.updateLinkedApp(updated) } catch { errorMessage = error.localizedDescription }
                    }
                    .font(.shalev(12, weight: .semibold))
                }
            }
            if employee.employeeCloseRequestAt != nil && employee.employeeCloseRequestAt?.timeIntervalSince1970 != 0 {
                Text("El empleado solicita cerrar")
                    .font(.shalev(12))
                    .foregroundStyle(Color.shalevError)
            }

            if importState != .none {
                VStack(alignment: .leading, spacing: 6) {
                    Text(importState.userMessage ?? "Solicitud de importación")
                        .font(.shalev(12))
                        .foregroundStyle(importState == .rejected || importState == .incompatible ? Color.shalevError : Color.shalevSecondaryText)
                    if importState == .pending, employee.importRequestID != nil {
                        HStack(spacing: 14) {
                            Button("Autorizar importación…") { importDecision = true }
                            Button("Rechazar", role: .destructive) { importDecision = false }
                        }
                        .font(.shalev(12, weight: .semibold))
                    }
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.shalevPrimaryContainer.opacity(0.55), in: RoundedRectangle(cornerRadius: 12))
            }

            HStack(spacing: 14) {
                Button("QR") { showingQR = true }
                Button("Permisos y cobro") { editing = true }
                Spacer()
                Button("Quitar", role: .destructive) { confirmRemove = true }
            }
            .font(.shalev(12, weight: .semibold))
        }
        .padding(.vertical, 11)
        .sheet(isPresented: $editing) { SyncEmployeeEditor(sync: sync, employee: employee) }
        .sheet(isPresented: $showingQR) { SyncPairingQRView(sync: sync, employee: employee) }
        .alert("¿Quitar \(employee.name)?", isPresented: $confirmRemove) {
            Button("Cancelar", role: .cancel) {}
            Button("Quitar", role: .destructive) {
                Task { do { try await sync.removeLinkedApp(employee.syncID ?? 0) } catch { errorMessage = error.localizedDescription } }
            }
        } message: { Text("La próxima conexión autenticada borrará los datos de esa secundaria.") }
        .alert(importDecision == true ? "¿Autorizar la importación?" : "¿Rechazar la solicitud?",
               isPresented: Binding(get: { importDecision != nil }, set: { if !$0 { importDecision = nil } })) {
            Button("Cancelar", role: .cancel) { importDecision = nil }
            Button(importDecision == true ? "Autorizar" : "Rechazar", role: importDecision == false ? .destructive : nil) {
                guard let authorize = importDecision,
                      let requestID = employee.importRequestID,
                      let employeeID = employee.syncID else { importDecision = nil; return }
                do { try sync.resolveImportRequest(employeeID: employeeID, requestID: requestID, authorize: authorize) }
                catch { errorMessage = error.localizedDescription }
                importDecision = nil
            }
        } message: {
            Text(importDecision == true
                ? "La app de \(employee.name) recibirá una copia cifrada de la base completa por la sesión autenticada. Sus datos locales serán reemplazados; las claves de acceso no se transfieren."
                : "La app de \(employee.name) no podrá importar esta copia. Podrá volver a solicitar autorización.")
        }
        .alert("Sincronización", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Entendido", role: .cancel) {}
        } message: { Text(errorMessage ?? "") }
    }
}

/// El mismo desglose que se guarda y se exporta, visible antes de aprobar el conteo de un empleado.
/// Port de `caja/ResumenConteo.kt`: billetes, otro efectivo, conciliación de inventario, estado y motivo.
private struct ShiftCountSummaryView: View {
    let count: ShiftClosingCount
    let expectedCashCents: Int64

    private struct BillLine: Identifiable {
        var denomination: Int
        var quantity: Int
        var id: Int { denomination }
    }

    private var billLines: [BillLine] {
        count.bills.filter { $0.value != 0 }
            .map { BillLine(denomination: $0.key, quantity: $0.value) }
            .sorted { $0.denomination < $1.denomination }
    }

    private var countedCashCents: Int64 { count.cashCents }

    private var differenceText: String {
        let difference = countedCashCents - expectedCashCents
        if difference == 0 { return "Cuadra" }
        return "\(difference > 0 ? "Sobrante" : "Faltante") \(Cup(cents: abs(difference)).formatted)"
    }

    private func fixed(_ milli: Int64) -> String { FixedNumber.formatMilli(milli) }

    private func signedDifference(_ milli: Int64) -> String {
        if milli == 0 { return "0" }
        return "\(milli > 0 ? "+" : "−")\(fixed(abs(milli)))"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text("Conteo de cierre")
                .font(.shalev(13, weight: .semibold))
                .foregroundStyle(Color.shalevText)
            HStack(spacing: 4) {
                Text("Esperado \(Cup(cents: expectedCashCents).formatted)")
                Text("·")
                Text("Contado \(Cup(cents: countedCashCents).formatted)")
                Text("·")
                Text(differenceText)
                    .foregroundStyle(countedCashCents == expectedCashCents ? Color.shalevSuccess : Color.shalevSecondary)
            }
            .font(.shalev(11))
            .foregroundStyle(Color.shalevSecondaryText)
            ForEach(billLines) { line in
                HStack(spacing: 8) {
                    ShalevBanknoteThumbnail(denomination: line.denomination, width: 62, height: 28)
                    Text("× \(line.quantity) = \(Cup(cents: Int64(line.denomination) * 100 * Int64(line.quantity)).formatted)")
                        .font(.shalev(11, design: .monospaced))
                        .foregroundStyle(Color.shalevText)
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel("\(line.denomination) CUP × \(line.quantity) = \(Cup(cents: Int64(line.denomination) * 100 * Int64(line.quantity)).formatted)")
            }
            Text("Otro efectivo: \(Cup(cents: count.otherCashCents).formatted)")
                .font(.shalev(11))
                .foregroundStyle(Color.shalevText)
            if count.inventory.isEmpty {
                Text("Sin conteo de inventario.")
                    .font(.shalev(11))
                    .foregroundStyle(Color.shalevSecondaryText)
            } else {
                ForEach(count.inventory) { line in
                    VStack(alignment: .leading, spacing: 1) {
                        Text("\(line.productName) (\(line.unit))")
                            .font(.shalev(11, weight: .medium))
                            .foregroundStyle(Color.shalevText)
                        Text("esperado \(fixed(line.expectedMilli)), contado \(fixed(line.countedMilli)), diferencia \(signedDifference(line.differenceMilli))")
                            .font(.shalev(10))
                            .foregroundStyle(line.differenceMilli == 0 ? Color.shalevSecondaryText : Color.shalevSecondary)
                    }
                }
            }
            Text(count.approvedAt == nil ? "Pendiente de aprobación de inventario" : "Inventario aprobado por \(count.approvedBy ?? "el dueño")")
                .font(.shalev(11, weight: .medium))
                .foregroundStyle(count.approvedAt == nil ? Color.shalevError : Color.shalevSuccess)
            if !count.reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                Text("Motivo: \(count.reason)")
                    .font(.shalev(11))
                    .foregroundStyle(Color.shalevSecondaryText)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(11)
        .background(Color.shalevSurfaceVariant.opacity(0.55), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
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

    private var cards: [TransferCard] { sync.store.data.settings.profile.availableCards }
    private var phones: [TransferPhone] { sync.store.data.settings.profile.availablePhones }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    ShalevScreenTitle(title: employee == nil ? "Agregar app de un empleado" : (employee?.name ?? ""),
                                      subtitle: "Su app se vincula con un código QR y solo puede hacer lo que marques aquí.")
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 11) {
                            if employee == nil {
                                Text("* Obligatorio")
                                    .font(.shalev(11))
                                    .foregroundStyle(Color.shalevSecondaryText)
                                Text("Nombre del empleado *")
                                    .font(.shalev(12, weight: .medium))
                                    .foregroundStyle(Color.shalevSecondaryText)
                                TextField("Nombre del empleado", text: $name)
                                    .textInputAutocapitalization(.words)
                                    .submitLabel(.done)
                                    .padding(12)
                                    .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
                            }
                            Text("Permisos")
                                .font(.shalev(14, weight: .semibold))
                                .foregroundStyle(Color.shalevText)
                            EmployeePermissionRow(permission: .sellProducts, isOn: sellProducts) { sellProducts = $0 }
                            Divider().opacity(0.5)
                            EmployeePermissionRow(permission: .sellServices, isOn: sellServices) { sellServices = $0 }
                            Divider().opacity(0.5)
                            EmployeePermissionRow(permission: .editInventory, isOn: editInventory) { editInventory = $0 }
                            Divider().opacity(0.5)
                            EmployeePermissionRow(permission: .changePrices, isOn: changePrices) { changePrices = $0 }
                            Divider().opacity(0.5)
                            EmployeePermissionRow(permission: .exportReports, isOn: exportReports) { exportReports = $0 }
                        }
                    }
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("Cobro por transferencia")
                                .font(.shalev(14, weight: .semibold))
                                .foregroundStyle(Color.shalevText)
                            Picker("Cobrar en la tarjeta", selection: $cardID) {
                                Text("La predeterminada").tag(UUID?.none)
                                ForEach(cards) { card in
                                    Text("\(card.alias) · \(NumberPresentation.card(card.number))").tag(UUID?.some(card.id))
                                }
                            }
                            .pickerStyle(.menu)
                            .tint(Color.shalevPrimary)
                            .disabled(cards.isEmpty)
                            if cards.count > 1 {
                                Divider().opacity(0.5)
                                Picker("Teléfono del empleado", selection: $phoneID) {
                                    Text("Lo escribe el empleado").tag(UUID?.none)
                                    ForEach(phones) { phone in
                                        Text("\(phone.alias) · \(NumberPresentation.phone(phone.number))").tag(UUID?.some(phone.id))
                                    }
                                }
                                .pickerStyle(.menu)
                                .tint(Color.shalevPrimary)
                            }
                            Text("Su app solo recibe esta tarjeta para el QR de cobro. Si no eliges un teléfono, el empleado escribe el suyo al vincular su app.")
                                .font(.shalev(11))
                                .foregroundStyle(Color.shalevSecondaryText)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    Button(action: save) {
                        Label(employee == nil ? "Crear código" : "Guardar",
                              systemImage: employee == nil ? "qrcode" : "checkmark.circle.fill")
                    }
                    .buttonStyle(ShalevPrimaryButtonStyle())
                    .disabled(employee == nil && name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    if let errorMessage {
                        Text(errorMessage).font(.shalev(12)).foregroundStyle(Color.shalevError)
                            .multilineTextAlignment(.center).frame(maxWidth: .infinity)
                    }
                }
                .padding(18)
                .padding(.bottom, 16)
            }
            .background(Color.shalevBackground)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Cancelar") { dismiss() }.foregroundStyle(Color.shalevSecondaryText) }
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
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanName.isEmpty else { errorMessage = "Escribe el nombre del empleado."; return }
        var permissions = Set<EmployeePermission>()
        if sellProducts { permissions.formUnion([.sellProducts, .viewInventory, .viewRecords, .manageCash]) }
        if sellServices { permissions.formUnion([.sellServices, .viewRecords, .manageCash]) }
        if editInventory { permissions.formUnion([.editInventory, .viewInventory]) }
        if changePrices { permissions.insert(.changePrices) }
        if exportReports { permissions.insert(.exportReports) }
        do {
            if var employee {
                employee.name = cleanName; employee.permissions = permissions
                employee.paymentCardID = cardID; employee.paymentPhoneID = phoneID
                try sync.updateLinkedApp(employee)
            } else { _ = try sync.addLinkedApp(name: cleanName, permissions: permissions) }
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
                    Text("Vincular a \(employee.name)").font(.shalev(17, weight: .semibold))
                    Text("Vence \(code.expiresAt.shalevDateTime)").font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
                    ShareLink(item: text) { Label("Compartir código", systemImage: "square.and.arrow.up") }
                    Text("El QR contiene el token inicial, no la clave definitiva del empleado. La clave se acuerda después con ECDH P-256.")
                        .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText).multilineTextAlignment(.center)
                } else if let errorMessage { Text(errorMessage).foregroundStyle(Color.shalevError) }
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
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.shalevOutline))
                    Button("Vincular con este código") { submit(manual) }
                        .buttonStyle(ShalevPrimaryButtonStyle()).disabled(working || manual.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    if working { ProgressView().accessibilityLabel("Vinculando…") }
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
            do { try await onCode(text); ShalevHaptics.exito(); dismiss() }   // vibra al leer el código, como Android
            catch { ShalevHaptics.error(); errorMessage = error.localizedDescription; working = false }
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
