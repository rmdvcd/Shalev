import SwiftUI
import UIKit

// MARK: - Miniaturas de bancos cubanos
// Código de banco en los dígitos 5–8: BPA 1299, BANDEC 0699, BANMET 9598.
enum BancoCubanoThumbnail {
    static func imagen(para banco: BancoCubano) -> String? {
        switch banco {
        case .bpa: "bpa_miniatura"
        case .bandec: "bandec_miniatura"
        case .banmet: "banmet_miniatura"
        }
    }
}

struct ShalevBankThumbnail: View {
    let banco: BancoCubano
    var width: CGFloat = 40
    var height: CGFloat = 24

    var body: some View {
        Group {
            if let name = BancoCubanoThumbnail.imagen(para: banco), UIImage(named: name) != nil {
                Image(name)
                    .resizable()
                    .scaledToFit()
            } else {
                Image(systemName: "creditcard.fill")
                    .font(.shalevIcono(17, weight: .semibold))
                    .foregroundStyle(Color.shalevSecondaryText)
            }
        }
        .frame(width: width, height: height)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Logo de \(banco.nombre)")
    }
}
