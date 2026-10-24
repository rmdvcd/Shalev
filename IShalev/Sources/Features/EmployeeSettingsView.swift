import SwiftUI

private struct EmployeeEditorRoute: Identifiable {
    var employee: LocalEmployee?
    var id: String { employee?.id.uuidString ?? "new-employee" }
}

@MainActor
struct EmployeeSettingsView: View {
    @ObservedObject var store: ShalevStore
    @Binding var selectedOperatorID: String
    @State private var editor: EmployeeEditorRoute?
    @State private var pendingDelete: LocalEmployee?
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                ShalevScreenTitle(title: "Empleados", subtitle: "Permisos por persona en este iPhone")
                ShalevCard {
                    VStack(alignment: .leading, spacing: 10) {
                        ShalevSectionHeader(title: "Operador actual")
                        Picker("Operador", selection: $selectedOperatorID) {
                            Text("Dueño / administrador").tag("")
                            ForEach(store.employees) { employee in
                                Text(employee.name).tag(employee.id.uuidString)
                            }
                        }
                        .pickerStyle(.menu)
                        Text("El operador seleccionado limita los módulos visibles y acciones disponibles en este teléfono.")
                            .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                    }
                }
                ShalevCard {
                    VStack(alignment: .leading, spacing: 11) {
                        HStack {
                            ShalevSectionHeader(title: "Perfiles de empleado", trailing: "\(store.employees.count)")
                            Spacer(minLength: 5)
                            Button { editor = EmployeeEditorRoute(employee: nil) } label: {
                                Image(systemName: "plus.circle.fill").font(.system(size: 22)).foregroundStyle(Color.shalevPrimary)
                            }
                            .accessibilityLabel("Agregar empleado")
                        }
                        if store.employees.isEmpty {
                            Text("Aún no hay perfiles. Crea uno y asígnale los módulos que puede usar.")
                                .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                        } else {
                            ForEach(store.employees) { employee in
                                HStack(spacing: 9) {
                                    Image(systemName: "person.crop.circle.fill").font(.system(size: 26)).foregroundStyle(Color.shalevPrimary)
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(employee.name).font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText)
                                        Text(employee.permissions.map(\.rawValue).sorted().joined(separator: " · "))
                                            .font(.system(size: 10)).foregroundStyle(Color.shalevSecondaryText).lineLimit(2)
                                    }
                                    Spacer(minLength: 3)
                                    Button { editor = EmployeeEditorRoute(employee: employee) } label: {
                                        Image(systemName: "pencil").foregroundStyle(Color.shalevPrimary)
                                    }
                                    .accessibilityLabel("Editar \(employee.name)")
                                    Button(role: .destructive) { pendingDelete = employee } label: {
                                        Image(systemName: "trash").foregroundStyle(Color.shalevError)
                                    }
                                    .accessibilityLabel("Eliminar \(employee.name)")
                                }
                                .padding(.vertical, 5)
                                if employee.id != store.employees.last?.id { Divider() }
                            }
                        }
                    }
                }
                ShalevCard {
                    VStack(alignment: .leading, spacing: 7) {
                        Label("Alcance en iOS", systemImage: "info.circle.fill")
                            .font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                        Text("Estos perfiles y permisos se guardan cifrados en este teléfono y no se sincronizan con Android. El perfil activo se selecciona manualmente y no tiene PIN propio; volver desde un perfil de empleado al administrador exige Face ID o el código del dispositivo. Activa el bloqueo de Shalev para proteger el acceso local.")
                            .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                    }
                }
                .padding(.bottom, 20)
            }
            .padding(18)
        }
        .background(Color.shalevBackground)
        .sheet(item: $editor) { route in
            EmployeeEditorView(store: store, employee: route.employee)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        .confirmationDialog("¿Eliminar este perfil?", isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }), titleVisibility: .visible) {
            Button("Eliminar perfil", role: .destructive) {
                guard let employee = pendingDelete else { return }
                do {
                    try store.deleteEmployee(employee.id)
                    if selectedOperatorID == employee.id.uuidString { selectedOperatorID = "" }
                } catch { errorMessage = error.localizedDescription }
                pendingDelete = nil
            }
            Button("Cancelar", role: .cancel) { pendingDelete = nil }
        }
        .alert("No se pudo guardar", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Entendido", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }
}

@MainActor
private struct EmployeeEditorView: View {
    @ObservedObject var store: ShalevStore
    let employee: LocalEmployee?
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var permissions: Set<EmployeePermission>
    @State private var errorMessage: String?

    init(store: ShalevStore, employee: LocalEmployee?) {
        self.store = store
        self.employee = employee
        _name = State(initialValue: employee?.name ?? "")
        _permissions = State(initialValue: employee?.permissions ?? Set([.sellProducts, .sellServices, .viewInventory, .viewRecords, .manageCash, .exportReports]))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    ShalevScreenTitle(title: employee == nil ? "Agregar empleado" : "Editar empleado",
                                     subtitle: "Selecciona las acciones permitidas.")
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 11) {
                            Text("Nombre").font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                            TextField("Nombre del empleado", text: $name)
                                .textInputAutocapitalization(.words)
                                .padding(12)
                                .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
                            Divider()
                            ForEach(EmployeePermission.allCases) { permission in
                                Toggle(isOn: Binding(get: { permissions.contains(permission) }, set: { enabled in
                                    if enabled { permissions.insert(permission) } else { permissions.remove(permission) }
                                })) {
                                    Label(permission.rawValue, systemImage: permission.symbol)
                                        .font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevText)
                                }
                            }
                        }
                        .tint(Color.shalevPrimary)
                    }
                    if let errorMessage {
                        Text(errorMessage).font(.system(size: 12)).foregroundStyle(Color.shalevError)
                    }
                    Button(action: save) { Label("Guardar permisos", systemImage: "checkmark.circle.fill") }
                        .buttonStyle(ShalevPrimaryButtonStyle()).padding(.bottom, 20)
                }
                .padding(18)
            }
            .background(Color.shalevBackground)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Cancelar") { dismiss() }.foregroundStyle(Color.shalevSecondaryText) }
            }
            .alert("Revisa el nombre", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Entendido", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
    }

    private func save() {
        do {
            try store.saveEmployee(LocalEmployee(id: employee?.id ?? UUID(), name: name, permissions: permissions,
                                                 createdAt: employee?.createdAt ?? Date()))
            dismiss()
        } catch { errorMessage = error.localizedDescription }
    }
}
