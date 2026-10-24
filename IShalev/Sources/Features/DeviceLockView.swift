import SwiftUI
import LocalAuthentication

@MainActor
struct DeviceLockView: View {
    let onUnlock: () -> Void
    @State private var message = "Usa Face ID, Touch ID o el código de tu dispositivo para continuar."
    @State private var isAuthenticating = false

    var body: some View {
        VStack(spacing: 18) {
            Spacer()
            Image(systemName: "lock.shield.fill")
                .font(.system(size: 54, weight: .regular))
                .foregroundStyle(Color.shalevPrimary)
                .frame(width: 100, height: 100)
                .background(Color.shalevPrimaryContainer, in: RoundedRectangle(cornerRadius: 30))
            Text("Shalev está protegido")
                .font(.system(size: 23, weight: .bold)).foregroundStyle(Color.shalevText)
            Text(message)
                .font(.system(size: 14)).foregroundStyle(Color.shalevSecondaryText)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 340)
            Button(action: authenticate) {
                Label(isAuthenticating ? "Verificando…" : "Desbloquear", systemImage: "faceid")
            }
            .buttonStyle(ShalevPrimaryButtonStyle())
            .disabled(isAuthenticating)
            Spacer()
            Text("La autenticación se realiza localmente en iOS.")
                .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                .padding(.bottom, 20)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.shalevBackground.ignoresSafeArea())
        .task { authenticate() }
    }

    private func authenticate() {
        guard !isAuthenticating else { return }
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            message = error?.localizedDescription ?? "Configura un código de acceso en el dispositivo para habilitar el bloqueo."
            return
        }
        isAuthenticating = true
        message = "Confirma tu identidad para abrir Shalev."
        context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Desbloquear Shalev") { success, authError in
            Task { @MainActor in
                isAuthenticating = false
                if success {
                    onUnlock()
                } else {
                    message = authError?.localizedDescription ?? "No se pudo verificar la identidad. Inténtalo de nuevo."
                }
            }
        }
    }
}
