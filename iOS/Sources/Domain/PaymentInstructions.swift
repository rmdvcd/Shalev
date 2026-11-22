import Foundation

public struct PaymentMethod: Equatable, Hashable {
    public let nombre: String
    public let destino: String
    public let nota: String?

    public init(nombre: String, destino: String, nota: String? = nil) {
        self.nombre = nombre
        self.destino = destino
        self.nota = nota
    }
}

/// Fuente única de instrucciones y datos de cobro en iOS (T2).
///
/// AVISO DE SEGURIDAD:
/// Si el repositorio es público, los datos de cobro no deben comitearse con información real.
/// Se dejan vacíos con marcadores para que el propietario decida su despliegue y alojamiento.
public enum PaymentInstructions {
    public static let titulo = "Cómo pagar"
    public static let frasePagaSolo = "Paga solo a los datos que muestra esta pantalla"
    public static let avisoSinMetodos = "Te enviaremos los datos de pago al recibir tu solicitud."

    /// Métodos configurados de cobro. Vacíos por defecto si el repo es público.
    public static let metodos: [PaymentMethod] = []
}
