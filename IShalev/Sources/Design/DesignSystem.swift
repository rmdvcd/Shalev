import SwiftUI
import CoreImage
import CoreImage.CIFilterBuiltins
import UIKit

// MARK: - Paleta Shalev (los valores coinciden con el Design System Android)

extension Color {
    private static func adaptive(light: UInt32, dark: UInt32) -> Color {
        Color(uiColor: UIColor { traits in
            let value = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat((value >> 16) & 0xff) / 255,
                           green: CGFloat((value >> 8) & 0xff) / 255,
                           blue: CGFloat(value & 0xff) / 255,
                           alpha: 1)
        })
    }

    static let shalevPrimary = adaptive(light: 0x2B4FA3, dark: 0x66D1EF)
    static let shalevOnPrimary = adaptive(light: 0xFFFFFF, dark: 0x00212C)
    static let shalevPrimaryContainer = adaptive(light: 0xDCE4FA, dark: 0x1F3A4A)
    static let shalevOnPrimaryContainer = adaptive(light: 0x0F2660, dark: 0xCDEFFB)
    static let shalevBackground = adaptive(light: 0xF8F9FA, dark: 0x0F1318)
    static let shalevSurface = adaptive(light: 0xFFFFFF, dark: 0x1A1F26)
    static let shalevSurfaceVariant = adaptive(light: 0xEEF1F4, dark: 0x262C35)
    static let shalevSurfaceHigh = adaptive(light: 0xEBEDF0, dark: 0x2A313B)
    static let shalevText = adaptive(light: 0x1A1C1E, dark: 0xE6E6E6)
    static let shalevSecondaryText = adaptive(light: 0x5F6368, dark: 0xB3B9C2)
    static let shalevOutline = adaptive(light: 0x767B82, dark: 0x8C939E)
    static let shalevOutlineVariant = adaptive(light: 0xE0E3E7, dark: 0x363E4A)
    static let shalevTertiary = adaptive(light: 0x2F6F80, dark: 0x7FC4D4)
    static let shalevTertiaryContainer = adaptive(light: 0xCDEBF3, dark: 0x12434F)
    static let shalevOnTertiaryContainer = adaptive(light: 0x0B3440, dark: 0xCDEBF3)
    static let shalevSecondary = adaptive(light: 0xB4541A, dark: 0xF6A15C)
    static let shalevSecondaryContainer = adaptive(light: 0xFFE0CC, dark: 0x5A2F10)
    static let shalevOnSecondaryContainer = adaptive(light: 0x3A1A00, dark: 0xFFDCC2)
    static let shalevError = adaptive(light: 0xC62828, dark: 0xEF9A9A)
    static let shalevErrorContainer = adaptive(light: 0xFDE7E7, dark: 0x5C1A1A)
    static let shalevOnErrorContainer = adaptive(light: 0x5F1111, dark: 0xFFDAD6)
    static let shalevSuccess = adaptive(light: 0x2E7D32, dark: 0x81C784)
    static let shalevChartBlue = adaptive(light: 0x2B4FA3, dark: 0x66D1EF)
    static let shalevChartOrange = adaptive(light: 0xB4541A, dark: 0xF6A15C)
}

// MARK: - Componentes reutilizables

struct ShalevCard<Content: View>: View {
    var padding: CGFloat = 16
    var content: Content

    init(padding: CGFloat = 16, @ViewBuilder content: () -> Content) {
        self.padding = padding
        self.content = content()
    }

    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.shalevSurface)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .stroke(Color.shalevOutlineVariant.opacity(0.55), lineWidth: 0.7)
            }
            .shadow(color: .black.opacity(0.045), radius: 4, x: 0, y: 2)
    }
}

struct ShalevScreenTitle: View {
    let title: String
    var subtitle: String? = nil
    var isBrand: Bool = false

    var body: some View {
        VStack(spacing: 4) {
            Text(title)
                .font(isBrand ? .custom("Orbitron-Bold", size: 23) : .system(size: 21, weight: .semibold, design: .rounded))
                .foregroundStyle(Color.shalevText)
                .lineLimit(2)
                .minimumScaleFactor(0.82)
            if let subtitle, !subtitle.isEmpty {
                Text(subtitle)
                    .font(.system(size: 13, weight: .regular))
                    .foregroundStyle(Color.shalevSecondaryText)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

struct ShalevSectionHeader: View {
    let title: String
    var trailing: String? = nil
    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Color.shalevText)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 8)
            if let trailing {
                Text(trailing)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.shalevSecondaryText)
            }
        }
    }
}

struct ShalevIconButton: View {
    let symbol: String
    let title: String
    var isSelected: Bool = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(isSelected ? Color.shalevOnPrimary : Color.shalevText)
                .frame(width: 42, height: 42)
                .background(isSelected ? Color.shalevPrimary : Color.clear, in: Circle())
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }
}

struct ShalevPrimaryButtonStyle: ButtonStyle {
    var destructive = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(Color.shalevOnPrimary)
            .padding(.horizontal, 18)
            .frame(minHeight: 48)
            .frame(maxWidth: .infinity)
            .background(destructive ? Color.shalevError : Color.shalevPrimary, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .opacity(configuration.isPressed ? 0.82 : 1)
            .scaleEffect(configuration.isPressed ? 0.985 : 1)
    }
}

struct ShalevTonalButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(Color.shalevOnTertiaryContainer)
            .padding(.horizontal, 15)
            .frame(minHeight: 44)
            .background(Color.shalevTertiaryContainer, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}

struct ShalevStatusPill: View {
    let title: String
    var color: Color = .shalevPrimary
    var symbol: String? = nil

    var body: some View {
        HStack(spacing: 5) {
            if let symbol { Image(systemName: symbol).font(.system(size: 10, weight: .bold)) }
            Text(title).font(.system(size: 11, weight: .semibold))
        }
        .foregroundStyle(color)
        .padding(.horizontal, 9)
        .padding(.vertical, 6)
        .background(color.opacity(0.12), in: Capsule())
        .accessibilityElement(children: .combine)
    }
}

struct ShalevSearchField: View {
    @Binding var text: String
    var placeholder: String = "Buscar"

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(Color.shalevSecondaryText)
            TextField(placeholder, text: $text)
                .font(.system(size: 15))
                .foregroundStyle(Color.shalevText)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(Color.shalevSecondaryText)
                }
                .accessibilityLabel("Borrar búsqueda")
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 50)
        .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 13).stroke(Color.shalevOutline, lineWidth: 1))
    }
}

struct ShalevFilterChip: View {
    let title: String
    var selected = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(selected ? Color.shalevOnPrimary : Color.shalevText)
                .padding(.horizontal, 14)
                .frame(height: 36)
                .background(selected ? Color.shalevPrimary : Color.shalevSurfaceVariant, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

struct ShalevEmptyState: View {
    let symbol: String
    let title: String
    let detail: String
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.system(size: 29, weight: .regular))
                .foregroundStyle(Color.shalevPrimary)
                .frame(width: 70, height: 70)
                .background(Color.shalevPrimaryContainer, in: Circle())
            Text(title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Color.shalevText)
                .multilineTextAlignment(.center)
            Text(detail)
                .font(.system(size: 14))
                .foregroundStyle(Color.shalevSecondaryText)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .buttonStyle(ShalevTonalButtonStyle())
                    .padding(.top, 4)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 34)
        .accessibilityElement(children: .contain)
    }
}

struct ShalevFloatingButton: View {
    let title: String
    var symbol = "plus"
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(Color.shalevOnPrimary)
                .frame(width: 58, height: 58)
                .background(Color.shalevPrimary, in: RoundedRectangle(cornerRadius: 19, style: .continuous))
                .shadow(color: Color.shalevPrimary.opacity(0.28), radius: 8, x: 0, y: 4)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }
}

struct ShalevStatTile: View {
    let title: String
    let value: String
    let symbol: String
    var tint: Color = .shalevPrimary

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: symbol)
                .font(.system(size: 21, weight: .semibold))
                .foregroundStyle(tint)
            Text(title).font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText)
            Text(value)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Color.shalevSecondaryText)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .minimumScaleFactor(0.82)
        }
        .frame(maxWidth: .infinity, minHeight: 116)
        .padding(.horizontal, 10)
        .background(Color.shalevSurfaceVariant, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

// MARK: - QR Transfermóvil

struct ShalevQRCode: View {
    let contents: String
    private static let context = CIContext()

    private var image: UIImage? {
        guard !contents.isEmpty else { return nil }
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(contents.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 9, y: 9)),
              let cgImage = Self.context.createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .interpolation(.none)
                    .scaledToFit()
                    .padding(12)
                    .background(.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .accessibilityLabel("Código QR de Transfermóvil")
            } else {
                Image(systemName: "qrcode")
                    .resizable()
                    .scaledToFit()
                    .padding(12)
                    .foregroundStyle(Color.shalevSecondaryText)
                    .background(.white, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
        }
        .frame(width: 228, height: 228)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Código QR de Transfermóvil. El importe no está incluido; aparece debajo.")
    }
}

// El formato de fechas compartido vive en Domain.swift para que también lo usen las pruebas del paquete.
