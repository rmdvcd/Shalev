import SwiftUI
import UIKit

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
                        Picker("Operador", selection: operatorSelection) {
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
                                        Text("\(employee.hasPIN ? "PIN configurado" : "Falta PIN") · \(employee.permissions.map(\.rawValue).sorted().joined(separator: " · "))")
                                            .font(.system(size: 10)).foregroundStyle(employee.hasPIN ? Color.shalevSecondaryText : Color.shalevError).lineLimit(2)
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
                        Text("Cada empleado usa un PIN propio de 6 a 12 dígitos; se guarda solo un verificador PBKDF2 dentro de los datos cifrados. Cinco intentos fallidos bloquean el perfil durante 15 minutos. Al volver de segundo plano se solicita otra vez. Cambiar al administrador exige Face ID o el código del dispositivo. Los perfiles siguen siendo locales y no se sincronizan con Android.")
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
        .alert("No se pudo completar la acción", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Entendido", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

    private var operatorSelection: Binding<String> {
        Binding(get: { selectedOperatorID }, set: { proposedID in
            guard !proposedID.isEmpty else {
                selectedOperatorID = ""
                return
            }
            guard let employee = store.employees.first(where: { $0.id.uuidString == proposedID }) else { return }
            guard employee.hasPIN else {
                errorMessage = "Configura un PIN de 6 a 12 dígitos en Ajustes → Empleados antes de activar este perfil."
                return
            }
            selectedOperatorID = proposedID
        })
    }
}

@MainActor
private struct EmployeeEditorView: View {
    @ObservedObject var store: ShalevStore
    let employee: LocalEmployee?
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var permissions: Set<EmployeePermission>
    @State private var pin = ""
    @State private var confirmPIN = ""
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
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 10) {
                            ShalevSectionHeader(title: "PIN del empleado")
                            Text(employee?.hasPIN == true
                                 ? "El PIN actual está protegido. Deja ambos campos vacíos para conservarlo o escribe uno nuevo para reemplazarlo."
                                 : "Obligatorio para activar este perfil. Usa de 6 a 12 dígitos.")
                                .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                            SecureField("PIN de 6 a 12 dígitos", text: $pin)
                                .keyboardType(.numberPad)
                                .padding(12)
                                .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
                            SecureField("Repite el PIN", text: $confirmPIN)
                                .keyboardType(.numberPad)
                                .padding(12)
                                .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11))
                        }
                    }
                    if let errorMessage {
                        Text(errorMessage).font(.system(size: 12)).foregroundStyle(Color.shalevError)
                    }
                    Button(action: save) { Label("Guardar perfil", systemImage: "checkmark.circle.fill") }
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
        let pinToSave: String?
        if !pin.isEmpty || !confirmPIN.isEmpty {
            guard EmployeePIN.isValid(pin) else { errorMessage = BusinessError.invalidEmployeePIN.localizedDescription; return }
            guard pin == confirmPIN else { errorMessage = "Los PIN no coinciden."; return }
            pinToSave = pin
        } else if employee?.hasPIN == true {
            pinToSave = nil
        } else {
            errorMessage = BusinessError.employeePINRequired.localizedDescription
            return
        }

        var incoming = employee ?? LocalEmployee(name: name, permissions: permissions)
        incoming.name = name
        incoming.permissions = permissions
        do {
            try store.saveEmployee(incoming, pin: pinToSave)
            dismiss()
        } catch { errorMessage = error.localizedDescription }
    }
}

@MainActor
struct EmployeeSessionLockView: View {
    @ObservedObject var store: ShalevStore
    let employee: LocalEmployee
    let onUnlock: () -> Void
    let onSwitchToOwner: () -> Void
    @State private var pin = ""
    @State private var message = "Introduce el PIN del empleado para continuar."

    var body: some View {
        VStack(spacing: 18) {
            Spacer()
            Image(systemName: employee.hasPIN ? "person.badge.key.fill" : "person.crop.circle.badge.exclamationmark")
                .font(.system(size: 50, weight: .regular))
                .foregroundStyle(Color.shalevPrimary)
                .frame(width: 96, height: 96)
                .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 28))
            Text("Perfil de \(employee.name)")
                .font(.system(size: 23, weight: .bold)).foregroundStyle(Color.shalevText)
            if employee.hasPIN {
                Text(message)
                    .font(.system(size: 14)).foregroundStyle(Color.shalevSecondaryText)
                    .multilineTextAlignment(.center).frame(maxWidth: 340)
                SecureField("PIN de 6 a 12 dígitos", text: $pin)
                    .keyboardType(.numberPad)
                    .padding(14)
                    .background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 13))
                    .overlay(RoundedRectangle(cornerRadius: 13).stroke(Color.shalevOutlineVariant, lineWidth: 1))
                    .frame(maxWidth: 340)
                Button(action: unlock) { Label("Desbloquear perfil", systemImage: "lock.open.fill") }
                    .buttonStyle(ShalevPrimaryButtonStyle())
                    .disabled(pin.isEmpty)
            } else {
                Text("Este perfil todavía no tiene PIN. Vuelve al administrador para configurarlo en Ajustes → Empleados.")
                    .font(.system(size: 14)).foregroundStyle(Color.shalevSecondaryText)
                    .multilineTextAlignment(.center).frame(maxWidth: 340)
            }
            Button(action: onSwitchToOwner) {
                Label("Volver al administrador", systemImage: "person.crop.circle.badge.checkmark")
                    .font(.system(size: 13, weight: .semibold))
            }
            .buttonStyle(.plain).foregroundStyle(Color.shalevPrimary)
            Spacer()
            Text("La verificación del PIN se realiza localmente en este dispositivo.")
                .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                .padding(.bottom, 20)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.shalevBackground.ignoresSafeArea())
    }

    private func unlock() {
        do {
            switch try store.verifyEmployeePIN(pin, employeeID: employee.id) {
            case .accepted:
                pin = ""
                onUnlock()
            case .rejected(let attemptsRemaining):
                pin = ""
                message = "PIN incorrecto. Quedan \(attemptsRemaining) intentos antes del bloqueo."
            case .locked(let until):
                pin = ""
                let time = DateFormatter.localizedString(from: until, dateStyle: .none, timeStyle: .short)
                message = "Demasiados intentos. Vuelve a probar después de las \(time)."
            case .notConfigured:
                message = "Este perfil no tiene un PIN válido. Vuelve al administrador para configurarlo."
            }
        } catch {
            message = error.localizedDescription
        }
    }
}
