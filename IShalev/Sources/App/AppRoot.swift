import SwiftUI
import LocalAuthentication

@main
@MainActor
struct IShalevApp: App {
    @StateObject private var store: ShalevStore
    @StateObject private var licenseManager: GLLicenseManager
    @StateObject private var sync: SyncManager

    init() {
        let store = ShalevStore()
        let license = GLLicenseManager()
        _store = StateObject(wrappedValue: store)
        _licenseManager = StateObject(wrappedValue: license)
        _sync = StateObject(wrappedValue: SyncManager(store: store, licenseManager: license))
    }

    var body: some Scene {
        WindowGroup {
            ShalevRootView(store: store, licenseManager: licenseManager, sync: sync)
                .preferredColorScheme(nil)
        }
    }
}

enum MainSection: String, CaseIterable, Identifiable, Equatable {
    case home = "Inicio"
    case inventory = "Inventario"
    case services = "Servicios"
    case records = "Registros"
    case settings = "Ajustes"

    var id: String { rawValue }
    var symbol: String {
        switch self {
        case .home: "house.fill"
        case .inventory: "archivebox.fill"
        case .services: "storefront.fill"
        case .records: "clock.arrow.circlepath"
        case .settings: "gearshape.fill"
        }
    }
}

enum SaleMode: String, Identifiable, Equatable {
    case products
    case services
    var id: String { rawValue }
    var title: String { self == .products ? "Nueva venta" : "Vender servicio" }
}

@MainActor
struct ShalevRootView: View {
    @ObservedObject var store: ShalevStore
    @ObservedObject var licenseManager: GLLicenseManager
    @ObservedObject var sync: SyncManager
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("shalev.onboarding.completed") private var onboardingCompleted = false
    @AppStorage("shalev.lock.enabled") private var appLockEnabled = false
    @AppStorage("shalev.module.sales") private var salesEnabled = true
    @AppStorage("shalev.module.inventory") private var inventoryEnabled = true
    @AppStorage("shalev.module.services") private var servicesEnabled = true
    @AppStorage("shalev.operator.active") private var selectedOperatorID = ""
    @State private var authenticatedOperatorID = ""
    @State private var selectedSection: MainSection = .home
    @State private var saleMode: SaleMode?
    @State private var showStorageAlert = false
    @State private var appUnlocked = false
    @State private var operatorAuthError: String?

    private var activeEmployee: LocalEmployee? {
        guard !selectedOperatorID.isEmpty else { return nil }
        return store.employees.first { $0.id.uuidString == selectedOperatorID }
    }
    private var isAdministrator: Bool { !sync.isSecondary && activeEmployee == nil }
    private var effectiveLicenseState: GLLicenseState { sync.secondaryLicenseState ?? licenseManager.state }
    private var effectiveSales: Bool { salesEnabled && sync.moduleEnabled("VENTAS") }
    private var effectiveInventory: Bool { inventoryEnabled && sync.moduleEnabled("INVENTARIO") }
    private var effectiveServices: Bool { servicesEnabled && sync.moduleEnabled("SERVICIOS") }
    private func permits(_ permission: EmployeePermission) -> Bool {
        if sync.isSecondary { return sync.permissions(sync.link?.permissions ?? []).contains(permission) }
        return activeEmployee?.permissions.contains(permission) ?? true
    }
    private var canSellProducts: Bool { effectiveSales && permits(.sellProducts) }
    private var canSellServices: Bool { effectiveServices && permits(.sellServices) }
    private var canViewInventory: Bool {
        (effectiveSales || effectiveInventory) && (permits(.viewInventory) || (effectiveSales && permits(.sellProducts)))
    }
    private var canEditInventory: Bool { permits(.editInventory) }
    private var canViewRecords: Bool { permits(.viewRecords) || permits(.correctSales) }
    private var canManageCash: Bool { permits(.manageCash) }
    private var canChangePrices: Bool { permits(.changePrices) }
    private var canExport: Bool { permits(.exportReports) }
    private var canCorrectSales: Bool { !sync.isSecondary && permits(.correctSales) }
    private var canOpenSettings: Bool {
        sync.isSecondary || isAdministrator || permits(.manageSettings) || canChangePrices || canExport
    }

    var body: some View {
        Group {
            if !sync.isSecondary && licenseManager.state == .loading {
                ProgressView("Comprobando licencia…")
                    .tint(Color.shalevPrimary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.shalevBackground.ignoresSafeArea())
            } else if !effectiveLicenseState.unlocked {
                if sync.isSecondary {
                    NavigationStack { SyncView(sync: sync) }
                } else {
                    LicenseView(store: store, license: licenseManager, blocking: true)
                }
            } else if !onboardingCompleted && !sync.isSecondary {
                OnboardingView(store: store) { onboardingCompleted = true }
            } else if appLockEnabled && !appUnlocked {
                DeviceLockView { appUnlocked = true }
            } else if let employee = activeEmployee, authenticatedOperatorID != employee.id.uuidString {
                EmployeeSessionLockView(store: store, employee: employee,
                                        onUnlock: { authenticatedOperatorID = employee.id.uuidString },
                                        onSwitchToOwner: switchToOwner)
            } else {
                appShell
            }
        }
        .task {
            store.currentOperatorName = activeEmployee?.name
            repairSelection()
            if salesEnabled && !inventoryEnabled { inventoryEnabled = true }
            ensureOneModule()
            sync.start()
            await licenseManager.refresh()
            Task { await licenseManager.checkRevocationsIfDue() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background || phase == .inactive {
                if appLockEnabled { appUnlocked = false }
                if !selectedOperatorID.isEmpty { authenticatedOperatorID = "" }
            } else if phase == .active {
                sync.start()
                Task {
                    await licenseManager.refresh()
                    await licenseManager.checkRevocationsIfDue()
                }
            }
        }
        .onChange(of: salesEnabled) { _, enabled in
            sync.modulesChanged()
            if enabled && !inventoryEnabled { inventoryEnabled = true }
            ensureOneModule()
            repairSelection()
        }
        .onChange(of: inventoryEnabled) { _, enabled in
            sync.modulesChanged()
            if !enabled && salesEnabled { salesEnabled = false }
            ensureOneModule()
            repairSelection()
        }
        .onChange(of: servicesEnabled) { _, _ in sync.modulesChanged(); ensureOneModule(); repairSelection() }
        .onChange(of: selectedOperatorID) { oldID, newID in
            if oldID != newID { authenticatedOperatorID = "" }
            store.currentOperatorName = activeEmployee?.name
            repairSelection()
        }
        .onChange(of: store.data.employees) { _, _ in
            store.currentOperatorName = activeEmployee?.name
            repairSelection()
        }
        .onChange(of: sync.role) { _, _ in
            selectedOperatorID = ""; authenticatedOperatorID = ""
            store.currentOperatorName = nil
            repairSelection()
        }
        .alert("No se pudo cambiar de operador", isPresented: Binding(get: { operatorAuthError != nil }, set: { if !$0 { operatorAuthError = nil } })) {
            Button("Entendido", role: .cancel) { operatorAuthError = nil }
        } message: { Text(operatorAuthError ?? "") }
    }

    private var appShell: some View {
        Group {
            switch selectedSection {
            case .home:
                HomeView(store: store, sync: sync, onNewSale: { saleMode = .products }, onSellService: { saleMode = .services },
                         onShowInventory: { selectedSection = .inventory }, onShowSettings: { selectedSection = .settings },
                         canSellProducts: canSellProducts, canSellServices: canSellServices,
                         canManageCash: canManageCash, canViewInventory: canViewInventory,
                         canViewRecords: canViewRecords, canViewSales: effectiveSales, canViewServices: effectiveServices,
                         canManageBusinessSettings: isAdministrator, canChangePrices: canChangePrices,
                         isEmployeeSession: !sync.isSecondary && !isAdministrator, onSwitchToOwner: switchToOwner)
            case .inventory:
                InventoryView(store: store, sync: sync, onSell: { saleMode = .products }, canEdit: canEditInventory, canSell: canSellProducts)
            case .services:
                ServicesView(store: store, sync: sync, onSell: { saleMode = .services }, canEdit: canEditInventory, canSell: canSellServices)
            case .records:
                RecordsView(store: store, canExport: canExport, canCorrectSales: canCorrectSales,
                            canManageCash: canManageCash, canViewInventory: canViewInventory,
                            canViewSales: effectiveSales, canViewServices: effectiveServices)
            case .settings:
                SettingsView(store: store, licenseManager: licenseManager, sync: sync, selectedOperatorID: $selectedOperatorID,
                             isAdministrator: isAdministrator, canChangePrices: canChangePrices, canExport: canExport,
                             canExportInventory: effectiveSales || effectiveInventory,
                             canExportServices: effectiveServices, canExportSales: effectiveSales)
            }
        }
        .frame(maxWidth: 700)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.shalevBackground.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            ShalevTabBar(selection: $selectedSection, visibleSections: visibleSections)
        }
        .sheet(item: $saleMode) { mode in
            SaleComposerView(store: store, mode: mode, onOpenHome: { selectedSection = .home },
                             onOpenSettings: { selectedSection = .settings }, canOpenSettings: canOpenSettings)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        .onAppear {
            if store.loadError != nil { showStorageAlert = true }
        }
        .alert("Datos protegidos", isPresented: $showStorageAlert) {
            Button("Entendido", role: .cancel) { }
        } message: {
            Text(store.loadError ?? "")
        }
    }

    private var visibleSections: [MainSection] {
        MainSection.allCases.filter { section in
            switch section {
            case .inventory: (effectiveSales || effectiveInventory) && (canViewInventory || canEditInventory)
            case .services: effectiveServices && (canSellServices || canEditInventory)
            case .records: canViewRecords && (effectiveSales || effectiveServices || effectiveInventory)
            case .settings: canOpenSettings
            case .home: true
            }
        }
    }

    private func repairSelection() {
        if !selectedOperatorID.isEmpty && activeEmployee == nil {
            selectedOperatorID = ""
            authenticatedOperatorID = ""
        }
        if selectedOperatorID.isEmpty { authenticatedOperatorID = "" }
        if !visibleSections.contains(selectedSection) { selectedSection = .home }
    }

    private func ensureOneModule() {
        if !salesEnabled && !inventoryEnabled && !servicesEnabled { servicesEnabled = true }
    }

    private func switchToOwner() {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            operatorAuthError = error?.localizedDescription ?? "Configura Face ID o un código del dispositivo antes de cambiar al administrador."
            return
        }
        context.evaluatePolicy(.deviceOwnerAuthentication,
                               localizedReason: "Confirma tu identidad para volver al perfil administrador de Shalev") { success, authError in
            Task { @MainActor in
                if success {
                    selectedOperatorID = ""
                    authenticatedOperatorID = ""
                    selectedSection = .home
                } else {
                    operatorAuthError = authError?.localizedDescription ?? "No se pudo confirmar tu identidad."
                }
            }
        }
    }
}

private struct ShalevTabBar: View {
    @Binding var selection: MainSection
    let visibleSections: [MainSection]

    var body: some View {
        HStack(spacing: 4) {
            ForEach(visibleSections) { section in
                let selected = selection == section
                Button {
                    withAnimation(.easeInOut(duration: 0.18)) { selection = section }
                } label: {
                    Image(systemName: section.symbol)
                        .font(.system(size: 19, weight: .semibold))
                        .foregroundStyle(selected ? Color.shalevOnPrimary : Color.shalevSecondaryText)
                        .frame(width: selected ? 56 : 46, height: 40)
                        .background(selected ? Color.shalevPrimary : .clear, in: Capsule())
                        .frame(maxWidth: .infinity)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(section.rawValue)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(.horizontal, 13)
        .padding(.top, 9)
        .padding(.bottom, 5)
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) { Color.shalevOutlineVariant.opacity(0.7).frame(height: 0.5) }
        .background(Color.shalevBackground.opacity(0.9).ignoresSafeArea(edges: .bottom))
    }
}
