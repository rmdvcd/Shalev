import SwiftUI

// MARK: - Miniaturas de Bancos Cubanos
// Generadas para los prefijos: BPA (9205), BANDEC (9225), BANMET (9226)

enum BancoCubanoThumbnail: String {
    case bpa = "bpa_miniatura"
    case bandec = "bandec_miniatura"
    case banmet = "banmet_miniatura"
    
    static func imagen(para banco: BancoCubano) -> String? {
        switch banco {
        case .bpa: return BancoCubanoThumbnail.bpa.rawValue
        case .bandec: return BancoCubanoThumbnail.bandec.rawValue
        case .banmet: return BancoCubanoThumbnail.banmet.rawValue
        }
    }
}

// MARK: - Función interna para asignar imagen según banco
// Basado en el modelo ShalevBanknoteThumbnail de DesignSystem.swift

struct ShalevBankThumbnail: View {
    let banco: BancoCubano
    var width: CGFloat = 40
    var height: CGFloat = 24
    
    var body: some View {
        if let nombreImagen = BancoCubanoThumbnail.imagen(para: banco),
           let uiImage = UIImage(named: nombreImagen) {
            Image(uiImage: uiImage)
                .resizable()
                .scaledToFit()
                .frame(width: width, height: height)
                .accessibilityLabel("Logo de \(banco.nombre)")
        } else {
            // Fallback a SF Symbol si la imagen no existe
            Image(systemName: "creditcard.fill")
                .font(.shalevIcono(17, weight: .semibold))
                .foregroundStyle(Color.shalevSecondaryText)
                .frame(width: width, height: height)
                .accessibilityLabel("Logo de \(banco.nombre)")
        }
    }
}
