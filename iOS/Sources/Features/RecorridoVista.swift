import SwiftUI

/// Repetición de los recorridos guiados desde Ayuda, equivalente a `PestanaAyuda` de Android y a
/// «Recorridos de las pantallas» de DShalev.
///
/// Cada fila abre su recorrido sobre la app real: la lista se cierra y el `RecorridosModelo` arranca el recorrido con
/// los objetivos que haya en pantalla en ese momento (los pasos sin objetivo se saltan, como en Android). Un recorrido
/// ya visto no vuelve a salir solo; desde aquí se puede repetir siempre.
struct RecorridosAyudaView: View {
    @ObservedObject var modelo: RecorridosModelo
    @Environment(\.dismiss) private var dismiss
    @State private var reiniciados = false

    /// Recorridos que se ofrecen en Ayuda, en el orden de la barra y con una frase de qué enseñan. Son los mismos
    /// de las tres plataformas y los mismos que se abren solos la primera vez.
    private static let ofrecidos: [(RecorridoPantalla, String)] = [
        (.inicio, "Resumen del período, gráficos, rankings y el botón de vender."),
        (.inventario, "Lista, búsqueda, orden, exportación y alta de artículos."),
        (.servicios, "Lista, búsqueda y alta de servicios."),
        (.registros, "Detalle de ventas, filtros, turnos y exportación."),
        (.herramientas, "Ajustes de precio, fichas de costo e importación."),
        (.ajustes, "Módulos, empleados, licencia, respaldo y ayuda."),
        (.venta, "Carrito, total, forma de pago y cobro."),
        (.producto, "Nombre, precio, existencia y guardado de un artículo."),
        (.servicio, "Nombre, precio y guardado de un servicio."),
        (.turno, "Fondo inicial, movimientos y cierre de caja."),
        (.ajustePrecio, "Condición, artículos y activación de un ajuste."),
        (.fichaCliente, "Historial y edición de un cliente."),
        (.exportar, "Qué se exporta y en qué formato."),
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text("La primera vez que abres una pantalla, Shalev resalta lo importante. Repite el recorrido cuando quieras: los pasos cuyo control no esté en pantalla se saltan solos.")
                        .font(.shalev(13))
                        .foregroundStyle(Color.shalevText)
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.shalevSurfaceVariant, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                    ForEach(Self.ofrecidos, id: \.0) { recorrido, detalle in
                        ShalevCard {
                            HStack(alignment: .top, spacing: 10) {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(recorrido.titulo)
                                        .font(.shalev(16, weight: .semibold))
                                        .foregroundStyle(Color.shalevText)
                                    Text(detalle)
                                        .font(.shalev(12))
                                        .foregroundStyle(Color.shalevSecondaryText)
                                    Text("\(recorrido.pasos.count) pasos")
                                        .font(.shalev(11))
                                        .foregroundStyle(Color.shalevSecondaryText)
                                }
                                Spacer(minLength: 8)
                                Button(modelo.almacen.visto(recorrido) ? "Repetir" : "Ver") {
                                    // Se cierra la hoja y se abre el recorrido sobre la pantalla de fondo.
                                    dismiss()
                                    DispatchQueue.main.async {
                                        _ = modelo.abrir(recorrido)
                                    }
                                }
                                .buttonStyle(ShalevTonalButtonStyle())
                                .accessibilityLabel("Ver el recorrido: \(recorrido.titulo)")
                            }
                        }
                    }

                    Button {
                        modelo.almacen.reiniciar()
                        reiniciados = true
                    } label: {
                        Label("Marcar todos como no vistos", systemImage: "arrow.counterclockwise")
                    }
                    .buttonStyle(ShalevTonalButtonStyle())
                    .padding(.top, 4)
                    .accessibilityHint("Vuelven a salir solos la próxima vez que abras cada pantalla")

                    if reiniciados {
                        Text("Listo: los recorridos volverán a salir solos la próxima vez que abras cada pantalla.")
                            .font(.shalev(12))
                            .foregroundStyle(Color.shalevSuccess)
                            .accessibilityAddTraits(.updatesFrequently)
                    }
                }
                .padding(18)
                .frame(maxWidth: 680)
                .frame(maxWidth: .infinity)
            }
            .background(Color.shalevBackground)
            .navigationTitle("Recorridos")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Cerrar") { dismiss() }
                }
            }
        }
    }
}
