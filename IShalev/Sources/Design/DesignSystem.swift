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

// MARK: - Dynamic Type

/// Escala de Dynamic Type activa en la app.
///
/// Los tamaños del design system (9–23 pt) son los de la categoría **Large**, la que trae el teléfono por
/// defecto. Para que respeten la configuración del usuario sin reescribir las ~480 llamadas de tipografía,
/// la raíz (`ShalevRootView`) instala aquí la categoría que lee del entorno de SwiftUI y
/// `Font.shalev(_:weight:design:)` multiplica el tamaño base por el factor de Apple para esa categoría.
/// Solo se lee y se escribe en el hilo principal (al evaluar `body` y en `onChange`).
final class ShalevDynamicType {
    static let shared = ShalevDynamicType()

    /// Tope de escala; equivale a «Accesibilidad 1». Por encima, las filas, tarjetas y barras de altura
    /// fija se rompen y en iOS todavía no hay disposiciones alternativas como el `letraGrande()` de
    /// Android. Quitar el tope es el pendiente IX-013 de la auditoría UI/UX/IX.
    static let escalaMaxima: CGFloat = 1.6

    /// Factor de la categoría Large (por defecto, hasta que la raíz instala la del usuario).
    private(set) var factor: CGFloat = 1

    private init() {}

    func actualizar(_ categoria: DynamicTypeSize) {
        factor = min(Self.escalaMaxima, Self.factorDe(categoria))
    }

    /// Factores de Apple para el estilo `.body` en cada categoría de contenido.
    static func factorDe(_ categoria: DynamicTypeSize) -> CGFloat {
        switch categoria {
        case .xSmall: 0.82
        case .small: 0.88
        case .medium: 0.94
        case .large: 1
        case .xLarge: 1.12
        case .xxLarge: 1.24
        case .xxxLarge: 1.35
        case .accessibility1: 1.6
        case .accessibility2: 1.9
        case .accessibility3: 2.35
        case .accessibility4: 2.75
        case .accessibility5: 3.2
        @unknown default: 1
        }
    }

    /// Tamaño del design system escalado a la categoría activa, redondeado a medio punto para que los
    /// textos vecinos no queden desalineados por el redondeo.
    static func escalado(_ size: CGFloat) -> CGFloat {
        (size * shared.factor * 2).rounded() / 2
    }
}

extension Font {
    /// Tipografía de Shalev: el tamaño es el del design system (categoría Large) y **escala con Dynamic
    /// Type**, incluidos los tamaños de accesibilidad. Sustituye a `.system(size:)`, que es fijo.
    static func shalev(_ size: CGFloat, weight: Font.Weight = .regular, design: Font.Design = .default) -> Font {
        .system(size: ShalevDynamicType.escalado(size), weight: weight, design: design)
    }

    /// Fuente de marca (Orbitron) escalada con Dynamic Type.
    static func shalevMarca(_ size: CGFloat) -> Font {
        .custom("Orbitron-Bold", size: ShalevDynamicType.escalado(size))
    }
}

// MARK: - Movimiento, transparencia, área táctil y vibración

/// Movimiento de Shalev en iOS: duraciones cortas y respeto a «Reducir movimiento» (MUST de la guía).
/// Equivalente de `ShalevMotion` de Android, donde «Quitar animaciones» del sistema pone
/// `MotionDurationScale` a 0 y el cambio se resuelve en el fotograma siguiente.
enum ShalevMotion {
    /// Cambio de estado corriente: pestañas, acordeones, acciones flotantes.
    static let corta: TimeInterval = 0.18
    /// Cambios menores dentro de una fila o un contador.
    static let muyCorta: TimeInterval = 0.15

    /// Animación de un cambio de estado, o `nil` con «Reducir movimiento» activo: el cambio se aplica de
    /// inmediato, sin desplazar ni fundir nada.
    static func cambio(_ reducirMovimiento: Bool, duracion: TimeInterval = ShalevMotion.corta) -> Animation? {
        reducirMovimiento ? nil : .easeInOut(duration: duracion)
    }
}

/// Fondo de las barras flotantes (pestañas, pie del asistente y de la venta): material translúcido, u
/// opaco cuando el usuario activa «Reducir transparencia» (MUST de la guía). El color opaco es una
/// superficie del sistema, así el contraste del texto de encima no cambia.
struct ShalevBarBackground: View {
    @Environment(\.accessibilityReduceTransparency) private var reducirTransparencia

    var body: some View {
        if reducirTransparencia {
            Color.shalevSurface
        } else {
            Rectangle().fill(.ultraThinMaterial)
        }
    }
}

/// Vibración breve de Shalev, equivalente a `ShalevHaptics` de Android (vibra al leer un código, al
/// terminar una venta y con cada mensaje). No necesita ningún permiso: iOS decide si vibra según el
/// ajuste de hápticos del usuario, igual que Android respeta el suyo.
enum ShalevHaptics {
    /// Operación completada: código QR leído, venta registrada, licencia activada, respaldo exportado.
    static func exito() { UINotificationFeedbackGenerator().notificationOccurred(.success) }
    /// Algo destructivo o un error: eliminar, anular, descartar, contraseña incorrecta.
    static func error() { UINotificationFeedbackGenerator().notificationOccurred(.error) }
    /// Selección: pestañas, casillas y filtros.
    static func seleccion() { UISelectionFeedbackGenerator().selectionChanged() }
}

extension View {
    /// Área táctil mínima de 44×44 pt (MUST de la guía) **sin cambiar el dibujo**: el mínimo se aplica
    /// fuera del fondo del control, así el tamaño visible sigue siendo el del design system y lo que
    /// crece es la zona que responde al dedo.
    func shalevToque(_ lado: CGFloat = 44) -> some View {
        frame(minWidth: lado, minHeight: lado)
            .contentShape(Rectangle())
    }
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
                .font(isBrand ? .shalevMarca(23) : .shalev(21, weight: .semibold, design: .rounded))
                .foregroundStyle(Color.shalevText)
                .lineLimit(2)
                .minimumScaleFactor(0.82)
            if let subtitle, !subtitle.isEmpty {
                Text(subtitle)
                    .font(.shalev(13, weight: .regular))
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
                .font(.shalev(16, weight: .semibold))
                .foregroundStyle(Color.shalevText)
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 8)
            if let trailing {
                Text(trailing)
                    .font(.shalev(12, weight: .medium))
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
                .font(.shalev(17, weight: .semibold))
                .foregroundStyle(isSelected ? Color.shalevOnPrimary : Color.shalevText)
                .frame(width: 42, height: 42)
                .background(isSelected ? Color.shalevPrimary : Color.clear, in: Circle())
                .shalevToque()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }
}

/// «Paso N de M» con título, flechas de avance y barra de progreso, como `ShalevStepper` de Android.
struct ShalevStepper: View {
    let current: Int
    let total: Int
    let title: String
    var onBack: (() -> Void)? = nil
    var onNext: (() -> Void)? = nil

    private var fraction: CGFloat {
        total <= 0 ? 0 : min(1, max(0, CGFloat(current) / CGFloat(total)))
    }

    var body: some View {
        VStack(spacing: 9) {
            HStack(spacing: 10) {
                Button { onBack?() } label: {
                    Image(systemName: "chevron.left")
                        .font(.shalev(14, weight: .semibold))
                        .foregroundStyle(onBack == nil ? Color.shalevOutline : Color.shalevText)
                        .frame(width: 36, height: 36)
                        .background(onBack == nil ? Color.clear : Color.shalevSurfaceVariant, in: Circle())
                        .shalevToque()
                }
                .buttonStyle(.plain)
                .disabled(onBack == nil)
                .accessibilityLabel("Paso anterior")

                VStack(spacing: 2) {
                    Text("Paso \(current) de \(total)")
                        .font(.shalev(11, weight: .medium))
                        .foregroundStyle(Color.shalevSecondaryText)
                    Text(title)
                        .font(.shalev(15, weight: .semibold))
                        .foregroundStyle(Color.shalevText)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Progreso del asistente")
                .accessibilityValue("Paso \(current) de \(total): \(title)")

                Button { onNext?() } label: {
                    Image(systemName: "chevron.right")
                        .font(.shalev(14, weight: .semibold))
                        .foregroundStyle(onNext == nil ? Color.shalevSecondaryText : Color.shalevOnPrimary)
                        .frame(width: 38, height: 38)
                        .background(onNext == nil ? Color.shalevSurfaceVariant : Color.shalevPrimary, in: Circle())
                        .shalevToque()
                }
                .buttonStyle(.plain)
                .disabled(onNext == nil)
                .accessibilityLabel("Paso siguiente")
            }

            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.shalevOutlineVariant.opacity(0.6))
                    Capsule()
                        .fill(Color.shalevPrimary)
                        .frame(width: geometry.size.width * fraction)
                }
            }
            .frame(height: 4)
            .accessibilityHidden(true)
        }
        .padding(.vertical, 2)
    }
}

struct ShalevPrimaryButtonStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento
    var destructive = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.shalev(15, weight: .semibold))
            .foregroundStyle(Color.shalevOnPrimary)
            .padding(.horizontal, 18)
            .frame(minHeight: 48)
            .frame(maxWidth: .infinity)
            .background(destructive ? Color.shalevError : Color.shalevPrimary, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .opacity(configuration.isPressed ? 0.82 : 1)
            .scaleEffect(configuration.isPressed && !reducirMovimiento ? 0.985 : 1)
    }
}

struct ShalevTonalButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.shalev(14, weight: .semibold))
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
            if let symbol { Image(systemName: symbol).font(.shalev(10, weight: .bold)) }
            Text(title).font(.shalev(11, weight: .semibold))
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
                .font(.shalev(15))
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
        .frame(minHeight: 50)
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
                .font(.shalev(12, weight: .semibold))
                .foregroundStyle(selected ? Color.shalevOnPrimary : Color.shalevText)
                .padding(.horizontal, 14)
                .frame(minHeight: 36)
                .background(selected ? Color.shalevPrimary : Color.shalevSurfaceVariant, in: Capsule())
                .shalevToque()
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
                .font(.shalev(29, weight: .regular))
                .foregroundStyle(Color.shalevPrimary)
                .frame(width: 70, height: 70)
                .background(Color.shalevPrimaryContainer, in: Circle())
            Text(title)
                .font(.shalev(17, weight: .semibold))
                .foregroundStyle(Color.shalevText)
                .multilineTextAlignment(.center)
            Text(detail)
                .font(.shalev(14))
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
                .font(.shalev(19, weight: .semibold))
                .foregroundStyle(Color.shalevOnPrimary)
                .frame(minWidth: 58, minHeight: 58)
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
                .font(.shalev(21, weight: .semibold))
                .foregroundStyle(tint)
            Text(title).font(.shalev(13, weight: .semibold)).foregroundStyle(Color.shalevText)
            Text(value)
                .font(.shalev(13, weight: .medium))
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
