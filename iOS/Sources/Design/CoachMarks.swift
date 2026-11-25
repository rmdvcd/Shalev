import SwiftUI

/// Recorrido guiado (coach marks con efecto spotlight) para iOS, equivalente a `CoachMarks.kt` de Android.
///
/// - La pantalla marca sus controles con `.recorridoObjetivo("clave")`; sin un `CoachMarkHost` alrededor, el
///   modificador no publica nada y la pantalla se comporta igual (las capturas y las pruebas no cambian).
/// - El host dibuja un velo negro al 50 % con un hueco redondeado sobre el objetivo y, debajo, el globo con título,
///   frase, progreso y «Omitir» / «Anterior» / «Siguiente» (o «Finalizar»).
/// - **El velo no intercepta toques** (salvo el globo): la pantalla se puede seguir usando con el recorrido abierto,
///   igual que en Android, donde solo el globo recibe toques.
/// - El globo nunca se sale de la pantalla y, si el objetivo no está visible, se centra sin hueco.

/// Geometría del globo, aislada de SwiftUI para poder probarla con `swift test`.
public enum GeometriaRecorrido {
    /// Posición del globo (origen) dentro del área disponible.
    /// - Objetivo en la mitad superior: globo debajo, centrado sobre el objetivo y pegado a los márgenes.
    /// - Objetivo en la mitad inferior: globo encima.
    /// - Sin objetivo: centrado.
    public static func posicionGlobo(tamano: CGSize, globo: CGSize, foco: CGRect?,
                                     margen: CGFloat = 16, separacion: CGFloat = 12) -> CGPoint {
        let ancho = min(globo.width, max(0, tamano.width - margen * 2))
        let alto = min(globo.height, max(0, tamano.height - margen * 2))
        guard let foco else {
            return CGPoint(x: (tamano.width - ancho) / 2, y: (tamano.height - alto) / 2)
        }
        let debajo = foco.midY <= tamano.height / 2
        let y = debajo ? foco.maxY + separacion : foco.minY - separacion - alto
        let xDeseada = foco.midX - ancho / 2
        return CGPoint(
            x: limitar(xDeseada, minimo: margen, maximo: tamano.width - ancho - margen),
            y: limitar(y, minimo: margen, maximo: tamano.height - alto - margen)
        )
    }

    /// `true` si el globo cabe debajo sin salirse; se usa para decidir el orden de lectura de VoiceOver.
    static func limitar(_ valor: CGFloat, minimo: CGFloat, maximo: CGFloat) -> CGFloat {
        guard maximo >= minimo else { return minimo }
        return min(max(valor, minimo), maximo)
    }
}

/// Estado vivo de los recorridos: qué objetivos hay en pantalla y cuál está abierto.
@MainActor
final class RecorridosModelo: ObservableObject {
    @Published private(set) var estado: EstadoRecorrido?
    /// Rectángulos de los objetivos visibles, en coordenadas globales de la ventana.
    @Published private(set) var marcos: [String: CGRect] = [:]

    let almacen: AlmacenRecorridos
    /// Lo actualiza el host desde el entorno (`accessibilityReduceMotion`).
    var reducirMovimiento = false

    init(almacen: AlmacenRecorridos = AlmacenRecorridosUsuario()) {
        self.almacen = almacen
    }

    var activo: Bool { estado?.activo ?? false }
    var marcoActual: CGRect? { estado?.pasoActual.flatMap { marcos[$0.objetivo] } }

    /// Registra (o retira) un objetivo. Lo llaman las vistas al aparecer y al desaparecer.
    func registrar(_ objetivo: String, marco: CGRect?) {
        if let marco, marco.width > 0, marco.height > 0 {
            marcos[objetivo] = marco
        } else {
            marcos.removeValue(forKey: objetivo)
        }
    }

    /// Abre el recorrido si toca (primera vez y con algún objetivo disponible). Devuelve si lo abrió.
    @discardableResult
    func abrirSiToca(_ recorrido: RecorridoPantalla) -> Bool {
        guard estado == nil, PoliticaRecorridos.debeMostrar(recorrido, almacen: almacen, objetivos: Set(marcos.keys)) else {
            return false
        }
        return abrir(recorrido)
    }

    /// Abre un recorrido aunque ya se haya visto (Ayuda → Recorridos). Es idempotente si ya está abierto.
    @discardableResult
    func abrir(_ recorrido: RecorridoPantalla) -> Bool {
        guard estado == nil, let nuevo = EstadoRecorrido.iniciar(recorrido, objetivos: Set(marcos.keys)) else {
            return false
        }
        withAnimation(animacion) { estado = nuevo }
        return true
    }

    func siguiente() {
        guard var actual = estado else { return }
        if actual.esUltimo {
            finalizar()
            return
        }
        withAnimation(animacion) { actual.siguiente() }
        estado = actual
    }

    func anterior() {
        guard var actual = estado else { return }
        withAnimation(animacion) { actual.anterior() }
        estado = actual
    }

    /// Cierra y guarda que se vio: no vuelve a salir solo.
    func finalizar() {
        guard let actual = estado else { return }
        // Se busca el recorrido por sus pasos: el estado guarda solo la vuelta en curso (los pasos disponibles).
        if let recorrido = RecorridoPantalla.allCases.first(where: { candidato in
            !actual.pasos.isEmpty && actual.pasos.allSatisfy { paso in
                candidato.pasos.contains(where: { $0.objetivo == paso.objetivo })
            }
        }) {
            almacen.marcarVisto(recorrido)
        }
        withAnimation(animacion) { estado = nil }
    }

    /// Respeta «Reducir movimiento» del sistema, como el resto de la app (`ShalevMotion.cambio`).
    private var animacion: Animation? {
        reducirMovimiento ? nil : .easeInOut(duration: ShalevMotion.corta)
    }
}

/// Host que pinta el velo, el hueco y el globo. Se coloca una sola vez, envolviendo la app.
struct CoachMarkHost<Content: View>: View {
    @ObservedObject var modelo: RecorridosModelo
    @ViewBuilder var content: Content
    @Environment(\.accessibilityReduceMotion) private var reducirMovimiento
    /// Alto real del globo, medido para que la geometría no dependa de una estimación.
    @State private var altoGlobo: CGFloat = 200

    var body: some View {
        content
            .overlayPreferenceValue(RecorridoAncla.self) { anclas in
                GeometryReader { proxy in
                    if let estado = modelo.estado, let paso = estado.pasoActual {
                        let marco = anclas[paso.objetivo]?.resolver(en: proxy)
                        ZStack(alignment: .topLeading) {
                            VeloRecorrido(marco: marco)
                                // El velo no bloquea: con el recorrido abierto la pantalla se sigue pudiendo tocar.
                                .allowsHitTesting(false)
                                .accessibilityHidden(true)
                            GloboRecorrido(estado: estado, paso: paso, modelo: modelo)
                                .frame(width: anchoGlobo(en: proxy))
                                .background(
                                    GeometryReader { interior in
                                        Color.clear.preference(key: AltoGloboClave.self, value: interior.size.height)
                                    }
                                )
                                .position(centro(en: proxy, marco: marco))
                        }
                        .transition(.opacity)
                    }
                }
                .onPreferenceChange(AltoGloboClave.self) { altoGlobo = $0 }
            }
            .onAppear { modelo.reducirMovimiento = reducirMovimiento }
            .onChange(of: reducirMovimiento) { _, nuevo in modelo.reducirMovimiento = nuevo }
    }

    private func anchoGlobo(en proxy: GeometryProxy) -> CGFloat {
        min(420, max(0, proxy.size.width - 32))
    }

    private func centro(en proxy: GeometryProxy, marco: CGRect?) -> CGPoint {
        let globo = CGSize(width: anchoGlobo(en: proxy), height: max(altoGlobo, 1))
        let origen = GeometriaRecorrido.posicionGlobo(tamano: proxy.size, globo: globo, foco: marco)
        return CGPoint(x: origen.x + globo.width / 2, y: origen.y + globo.height / 2)
    }
}

/// Alto medido del globo.
struct AltoGloboClave: PreferenceKey {
    static var defaultValue: CGFloat = 200
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

/// Velo oscuro con hueco redondeado sobre el objetivo.
private struct VeloRecorrido: View {
    let marco: CGRect?

    var body: some View {
        Canvas { contexto, tamano in
            var camino = Path(CGRect(origin: .zero, size: tamano))
            if let marco {
                let hueco = CGRect(x: marco.minX - 6, y: marco.minY - 6,
                                   width: marco.width + 12, height: marco.height + 12)
                camino.addRoundedRect(in: hueco, cornerSize: CGSize(width: 12, height: 12))
            }
            contexto.fill(camino, with: .color(.black.opacity(0.5)), style: FillStyle(eoFill: true))
        }
        .ignoresSafeArea()
    }
}

/// Globo con el paso actual. Solo esta pieza recibe toques.
private struct GloboRecorrido: View {
    let estado: EstadoRecorrido
    let paso: PasoRecorrido
    @ObservedObject var modelo: RecorridosModelo

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text(paso.titulo)
                    .font(.shalev(16, weight: .semibold))
                    .foregroundStyle(Color.shalevText)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 8)
                Text(estado.progreso)
                    .font(.shalev(12))
                    .foregroundStyle(Color.shalevSecondaryText)
            }
            Text(paso.frase)
                .font(.shalev(13))
                .foregroundStyle(Color.shalevSecondaryText)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 8) {
                Button("Omitir") { modelo.finalizar() }
                    .buttonStyle(ShalevTonalButtonStyle())
                    .accessibilityLabel("Omitir el recorrido")
                Spacer(minLength: 0)
                if !estado.esPrimero {
                    Button("Anterior") { modelo.anterior() }
                        .buttonStyle(ShalevTonalButtonStyle())
                }
                Button(estado.esUltimo ? "Finalizar" : "Siguiente") {
                    if estado.esUltimo {
                        modelo.finalizar()
                    } else {
                        modelo.siguiente()
                    }
                }
                .buttonStyle(ShalevPrimaryButtonStyle())
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.shalevSurface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: .black.opacity(0.18), radius: 18, y: 8)
        // El globo es su propia región: VoiceOver lee título, frase y progreso, y no recorre la pantalla de fondo.
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Recorrido, \(paso.titulo), \(estado.progreso)")
        // Dos dedos hacia abajo (como los modales del sistema) y la acción de VoiceOver cierran el recorrido.
        // `onExitCommand` (Escape) no existe en iOS: aquí no hay teclado que garantice Escape.
        .simultaneousGesture(
            DragGesture(minimumDistance: 30).onEnded { gesto in
                if gesto.translation.height > 60 && abs(gesto.translation.width) < 80 {
                    modelo.finalizar()
                }
            }
        )
        .accessibilityAction(named: "Cerrar recorrido") { modelo.finalizar() }
    }
}

/// Ancla de un objetivo: su marco, resuelto contra el `GeometryProxy` del host.
struct RecorridoAncla: PreferenceKey {
    static var defaultValue: [String: Anchor<CGRect>] = [:]

    static func reduce(value: inout [String: Anchor<CGRect>], nextValue: () -> [String: Anchor<CGRect>]) {
        value.merge(nextValue()) { _, nuevo in nuevo }
    }
}

private extension Anchor<CGRect> {
    func resolver(en proxy: GeometryProxy) -> CGRect { proxy[self] }
}

extension View {
    /// Abre el recorrido de esta pantalla cuando aparece, pero solo la primera vez y solo si ya publicó algún
    /// objetivo. Se usa en las pantallas que no dependen de que el usuario cambie de pestaña (una hoja, un formulario).
    func recorridoAlAparecer(_ recorrido: RecorridoPantalla, modelo: RecorridosModelo?) -> some View {
        modifier(AperturaRecorrido(recorrido: recorrido, modelo: modelo))
    }

    /// Marca esta vista como objetivo de un recorrido. Sin `CoachMarkHost` alrededor no hace nada visible.
    func recorridoObjetivo(_ clave: String) -> some View {
        anchorPreference(key: RecorridoAncla.self, value: .bounds) { [clave: $0] }
    }
}

/// Abre el recorrido al aparecer la pantalla. El pequeño retardo deja que las preferencias de los objetivos lleguen
/// al modelo: sin él, el recorrido se abriría sin pasos y no se mostraría nada.
private struct AperturaRecorrido: ViewModifier {
    let recorrido: RecorridoPantalla
    let modelo: RecorridosModelo?
    @State private var intentado = false

    func body(content: Content) -> some View {
        content.task {
            guard !intentado, let modelo else { return }
            intentado = true
            try? await Task.sleep(for: .milliseconds(400))
            modelo.abrirSiToca(recorrido)
        }
    }
}
