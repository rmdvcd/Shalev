import Foundation

// Fuente única de instrucciones y datos de cobro en iOS (T2).
//
// AVISO DE SEGURIDAD: si el repositorio es público, los datos de cobro no deben comitearse con información real.
// Se dejan vacíos con marcadores para que el propietario decida su despliegue y alojamiento.

/// Un método de cobro mostrado en la tarjeta de pago.
///
/// Se llama `MetodoCobro` y no `PaymentMethod` porque `Domain.swift` ya declara `PaymentMethod` para el método de
/// pago de una venta (efectivo o transferencia). Redeclararlo impedía compilar todo el módulo.
public struct MetodoCobro: Equatable, Hashable {
    public let nombre: String
    public let destino: String
    public let nota: String?

    public init(nombre: String, destino: String, nota: String? = nil) {
        self.nombre = nombre
        self.destino = destino
        self.nota = nota
    }
}

/// Textos únicos de la tarjeta «Cómo pagar», equivalentes a `PaymentInstructions.kt` en Android y `pago.py` en DShalev.
public enum PaymentInstructions {
    public static let titulo = "Cómo pagar"
    public static let frasePagaSolo = "Paga solo a los datos que muestra esta pantalla"
    public static let avisoSinMetodos = "Te enviaremos los datos de pago al recibir tu solicitud."

    /// Métodos configurados de cobro. Vacíos por defecto si el repo es público.
    public static let metodos: [MetodoCobro] = []
}
