import SwiftUI
import UIKit

@MainActor
struct SaleComposerView: View {
    @ObservedObject var store: ShalevStore
    let mode: SaleMode
    let onOpenHome: () -> Void
    let onOpenSettings: () -> Void
    var canOpenSettings = true
    @Environment(\.dismiss) private var dismiss

    @State private var search = ""
    @State private var cart: [UUID: Int] = [:]
    @State private var payment: PaymentMethod = .cash
    @State private var transactionNumber = ""
    @State private var smsAmountCents: Int64?
    @State private var customerName = ""
    @State private var customerID = ""
    @State private var customerPhone = ""
    @State private var customerIsFixed = false
    @State private var showTransferPage = false
    @State private var errorMessage: String?

    private var itemKind: SaleItemKind { mode == .products ? .product : .service }
    private var drafts: [CartDraft] {
        cart.map { CartDraft(sourceID: $0.key, kind: itemKind, quantity: $0.value) }
    }
    private var preview: SalePreview? { try? store.previewSale(drafts, payment: payment) }
    private var totalCents: Int64 { preview?.totalCents ?? 0 }
    private var customer: Customer? {
        guard !customerName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return Customer(name: customerName.trimmingCharacters(in: .whitespacesAndNewlines),
                        identification: customerID.trimmingCharacters(in: .whitespacesAndNewlines),
                        phone: customerPhone.trimmingCharacters(in: .whitespacesAndNewlines))
    }
    private var qrPayload: String? {
        let card = store.data.settings.profile.transferCard.filter(\.isNumber)
        guard (12...20).contains(card.count) else { return nil }
        let phoneDigits = store.data.settings.profile.transferPhone.filter(\.isNumber)
        let mobile = phoneDigits.hasPrefix("53") ? String(phoneDigits.dropFirst(2)) : phoneDigits
        let validMobile = mobile.count == 8 ? mobile : ""
        return "TRANSFERMOVIL_ETECSA,TRANSFERENCIA,\(card),\(validMobile),"
    }

    private var products: [Product] {
        store.activeProducts.filter { $0.priceCents > 0 && $0.name.localizedCaseInsensitiveContains(search) }
    }
    private var services: [ServiceItem] {
        store.activeServices.filter { $0.priceCents > 0 && $0.name.localizedCaseInsensitiveContains(search) }
    }

    var body: some View {
        NavigationStack {
            Group {
                if store.openShift == nil {
                    shiftRequiredView
                } else if showTransferPage {
                    transferPaymentView
                } else {
                    selectionView
                }
            }
            .background(Color.shalevBackground)
            .navigationTitle(showTransferPage ? "Cobrar por transferencia" : mode.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if showTransferPage {
                        Button { showTransferPage = false } label: {
                            Label("Atrás", systemImage: "chevron.left")
                        }
                        .foregroundStyle(Color.shalevPrimary)
                    } else {
                        Button("Cancelar") { dismiss() }.foregroundStyle(Color.shalevSecondaryText)
                    }
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if store.openShift != nil && !showTransferPage { checkoutBar }
            }
            .alert("No se pudo registrar", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Entendido", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
            .onChange(of: payment) { _, method in
                if method == .cash {
                    transactionNumber = ""
                    smsAmountCents = nil
                    customerName = ""
                    customerID = ""
                    customerPhone = ""
                    customerIsFixed = false
                }
            }
        }
    }

    private var shiftRequiredView: some View {
        VStack(spacing: 16) {
            Spacer()
            ShalevEmptyState(symbol: "lock.fill", title: "Abre un turno para vender",
                             detail: "Las ventas y los movimientos de caja siempre pertenecen a un turno.",
                             actionTitle: "Ir a Inicio") {
                onOpenHome()
                dismiss()
            }
            Spacer()
        }
        .padding(20)
    }

    private var selectionView: some View {
        ScrollView {
            VStack(spacing: 14) {
                HStack {
                    ShalevScreenTitle(title: mode == .products ? "Elige los artículos" : "Elige los servicios",
                                     subtitle: "Agrega cantidades y revisa el total antes de cobrar.")
                }
                ShalevSearchField(text: $search, placeholder: "Buscar en el catálogo")

                if mode == .products {
                    productList
                } else {
                    serviceList
                }

                if !cart.isEmpty { cartSummary }
            }
            .padding(17)
            .padding(.bottom, 14)
        }
    }

    private var productList: some View {
        VStack(spacing: 1) {
            if products.isEmpty {
                ShalevEmptyState(symbol: "shippingbox", title: "No hay artículos vendibles",
                                 detail: search.isEmpty ? "Agrega artículos con precio y existencias en Inventario." : "Prueba con otra búsqueda.")
            } else {
                ForEach(products) { product in
                    let available = store.availableQuantity(kind: .product, sourceID: product.id) ?? 0
                    let quantity = cart[product.id, default: 0]
                    SaleCatalogRow(name: product.name, detail: product.kind == .prepared ? "Alcanza para \(available) · Elaborado" : "\(product.displayStock) · \(product.category)",
                                   price: Cup(cents: product.priceCents).formatted,
                                   quantity: quantity, canAdd: available > quantity,
                                   onMinus: { adjust(product.id, by: -1) }, onPlus: { adjust(product.id, by: 1) })
                }
            }
        }
    }

    private var serviceList: some View {
        VStack(spacing: 1) {
            if services.isEmpty {
                ShalevEmptyState(symbol: "storefront", title: "No hay servicios vendibles",
                                 detail: search.isEmpty ? "Agrega servicios con precio en la pestaña Servicios." : "Prueba con otra búsqueda.")
            } else {
                ForEach(services) { service in
                    let available = store.availableQuantity(kind: .service, sourceID: service.id) ?? 0
                    let quantity = cart[service.id, default: 0]
                    let stockText = available == Int.max ? service.type : "Alcanza para \(available) · \(service.type)"
                    SaleCatalogRow(name: service.name, detail: stockText,
                                   price: Cup(cents: service.priceCents).formatted,
                                   quantity: quantity, canAdd: available > quantity,
                                   onMinus: { adjust(service.id, by: -1) }, onPlus: { adjust(service.id, by: 1) })
                }
            }
        }
    }

    private var cartSummary: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 11) {
                ShalevSectionHeader(title: "Carrito", trailing: "\(cart.values.reduce(0, +)) artículos")
                ForEach(drafts) { draft in
                    HStack(spacing: 10) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(lineName(for: draft)).font(.system(size: 13, weight: .medium)).foregroundStyle(Color.shalevText).lineLimit(1)
                            Text("\(draft.quantity) × \(Cup(cents: basePrice(for: draft)).formatted)")
                                .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                        }
                        Spacer()
                        HStack(spacing: 10) {
                            Button { adjust(draft.sourceID, by: -1) } label: { Image(systemName: "minus.circle.fill") }
                                .accessibilityLabel("Quitar una unidad de \(lineName(for: draft))")
                            Text("\(draft.quantity)").font(.system(size: 13, weight: .semibold)).monospacedDigit()
                            Button { adjust(draft.sourceID, by: 1) } label: { Image(systemName: "plus.circle.fill") }
                                .accessibilityLabel("Añadir una unidad de \(lineName(for: draft))")
                        }
                        .font(.system(size: 17))
                        .foregroundStyle(Color.shalevPrimary)
                    }
                }
                Divider()
                Picker("Método de pago", selection: $payment) {
                    ForEach(PaymentMethod.allCases) { method in
                        Label(method.rawValue, systemImage: method.symbol).tag(method)
                    }
                }
                .pickerStyle(.segmented)
                if payment == .transfer {
                    Text("Después del QR completarás el número de transacción y los datos del cliente.")
                        .font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText)
                }
                if let warnings = preview?.warnings, !warnings.isEmpty {
                    Label("Revisa el precio de \(warnings.joined(separator: ", ")): queda al costo o por debajo después de los ajustes.", systemImage: "exclamationmark.triangle.fill")
                        .font(.system(size: 11, weight: .medium)).foregroundStyle(Color.shalevSecondary)
                }
                HStack {
                    Text("Total").font(.system(size: 14, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                    Spacer()
                    Text(Cup(cents: totalCents).formatted).font(.system(size: 19, weight: .bold)).foregroundStyle(Color.shalevText)
                }
            }
        }
    }

    private var transferTransactionFields: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("N.º de transacción").font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevText)
                TextField("6–30 letras o dígitos", text: $transactionNumber)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .textFieldStyle(SaleInputStyle())
                    .onChange(of: transactionNumber) { _, value in
                        if value.count > 30 { transactionNumber = String(value.prefix(30)) }
                    }
                Button(action: pasteTransferSMS) {
                    Label("Pegar SMS de Transfermóvil", systemImage: "doc.on.clipboard")
                        .font(.system(size: 12, weight: .semibold))
                }
                .buttonStyle(ShalevTonalButtonStyle())
                if let smsAmountCents {
                    let differs = smsAmountCents != totalCents
                    Text(differs
                         ? "El SMS indica \(Cup(cents: smsAmountCents).formatted), pero la venta suma \(Cup(cents: totalCents).formatted). Revisa que sea el pago correcto."
                         : "Importe del SMS: \(Cup(cents: smsAmountCents).formatted)")
                        .font(.system(size: 11, weight: differs ? .semibold : .regular))
                        .foregroundStyle(differs ? Color.shalevError : Color.shalevSecondaryText)
                }
            }
        }
    }

    private var customerFields: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 10) {
                Label("Datos del cliente", systemImage: "person.crop.circle")
                    .font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText)
                TextField("Nombre y apellidos", text: $customerName)
                    .textContentType(.name).textFieldStyle(SaleInputStyle())
                TextField("Carné cubano de 11 dígitos", text: $customerID)
                    .keyboardType(.numberPad)
                    .textInputAutocapitalization(.never).textFieldStyle(SaleInputStyle())
                    .onChange(of: customerID) { _, value in
                        customerID = String(value.filter(\.isNumber).prefix(11))
                    }
                TextField("Teléfono", text: $customerPhone)
                    .keyboardType(.phonePad).textFieldStyle(SaleInputStyle())
                Toggle("Guardar como cliente fijo", isOn: $customerIsFixed).tint(Color.shalevPrimary)
                let matches = CustomerValidator.suggestions(store.data.customers, containing: customerName)
                ForEach(matches) { match in
                    Button {
                        customerName = match.name
                        customerID = match.identification
                        customerPhone = match.phone
                        customerIsFixed = true
                    } label: {
                        HStack { Image(systemName: "person.crop.circle.fill"); Text(match.name); Spacer(); Image(systemName: "arrow.up.left") }
                            .font(.system(size: 13)).foregroundStyle(Color.shalevPrimary)
                    }
                    .accessibilityLabel("Usar cliente \(match.name)")
                }
            }
        }
    }

    private var checkoutBar: some View {
        VStack(spacing: 9) {
            HStack {
                Text("Total de la venta").font(.system(size: 12, weight: .medium)).foregroundStyle(Color.shalevSecondaryText)
                Spacer()
                Text(Cup(cents: totalCents).formatted).font(.system(size: 18, weight: .bold)).foregroundStyle(Color.shalevText)
            }
            Button {
                if payment == .transfer {
                    guard qrPayload != nil else { errorMessage = BusinessError.invalidPaymentDetails.localizedDescription; return }
                    showTransferPage = true
                } else {
                    completeSale()
                }
            } label: {
                Label(payment == .transfer ? "Mostrar QR de cobro" : "Registrar venta en efectivo",
                      systemImage: payment == .transfer ? "qrcode" : "checkmark.circle.fill")
            }
            .buttonStyle(ShalevPrimaryButtonStyle())
            .disabled(cart.isEmpty)
        }
        .padding(.horizontal, 17)
        .padding(.top, 11)
        .padding(.bottom, 8)
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) { Color.shalevOutlineVariant.opacity(0.6).frame(height: 0.5) }
    }

    private var transferPaymentView: some View {
        ScrollView {
            VStack(spacing: 17) {
                ShalevScreenTitle(title: "Transfermóvil", subtitle: "Escanea el QR desde Transfermóvil y escribe el importe manualmente.")
                if let qrPayload {
                    ShalevQRCode(contents: qrPayload)
                    ShalevCard {
                        VStack(spacing: 10) {
                            HStack { Text("Importe a transferir"); Spacer(); Text(Cup(cents: totalCents).formatted).fontWeight(.bold) }
                            Divider()
                            HStack { Text("Tarjeta"); Spacer(); Text(maskedCard).fontWeight(.semibold) }
                            if !store.data.settings.profile.transferPhone.isEmpty {
                                HStack { Text("Móvil"); Spacer(); Text(store.data.settings.profile.transferPhone).fontWeight(.semibold) }
                            }
                        }
                        .font(.system(size: 14)).foregroundStyle(Color.shalevText)
                    }
                    ShalevCard {
                        VStack(alignment: .leading, spacing: 7) {
                            Label("El QR no incluye el importe", systemImage: "info.circle.fill")
                                .font(.system(size: 13, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                            Text("Comprueba que el cliente complete la transferencia por \(Cup(cents: totalCents).formatted). La app no verifica el pago con el banco.")
                                .font(.system(size: 12)).foregroundStyle(Color.shalevSecondaryText)
                        }
                    }
                    transferTransactionFields
                    customerFields
                    if let warnings = preview?.warnings, !warnings.isEmpty {
                        Label("Revisa \(warnings.joined(separator: ", ")): el precio final queda al costo o por debajo.", systemImage: "exclamationmark.triangle.fill")
                            .font(.system(size: 11, weight: .medium)).foregroundStyle(Color.shalevSecondary)
                    }
                    Button { completeSale() } label: {
                        Label("Confirmar pago recibido y registrar", systemImage: "checkmark.circle.fill")
                    }
                    .buttonStyle(ShalevPrimaryButtonStyle())
                    .padding(.top, 2)
                } else {
                    ShalevEmptyState(symbol: "creditcard", title: "Configura una tarjeta de cobro",
                                     detail: canOpenSettings ? "Añade tu tarjeta en Ajustes → Pago electrónico para generar el QR." : "Pide al administrador que configure la tarjeta de cobro.",
                                     actionTitle: canOpenSettings ? "Volver a Ajustes" : "Cerrar") {
                        showTransferPage = false
                        if canOpenSettings { onOpenSettings() }
                        dismiss()
                    }
                }
            }
            .padding(20)
            .padding(.bottom, 25)
        }
    }

    private var maskedCard: String {
        let digits = store.data.settings.profile.transferCard.filter(\.isNumber)
        guard digits.count > 4 else { return "••••" }
        return "•••• •••• •••• \(digits.suffix(4))"
    }

    private func adjust(_ id: UUID, by delta: Int) {
        let next = max(0, cart[id, default: 0] + delta)
        if next == 0 { cart.removeValue(forKey: id) }
        else {
            let available = store.availableQuantity(kind: itemKind, sourceID: id) ?? 0
            guard next <= available else { return }
            cart[id] = next
        }
    }

    private func lineName(for draft: CartDraft) -> String {
        switch draft.kind {
        case .product: store.data.products.first(where: { $0.id == draft.sourceID })?.name ?? "Artículo"
        case .service: store.data.services.first(where: { $0.id == draft.sourceID })?.name ?? "Servicio"
        }
    }

    private func basePrice(for draft: CartDraft) -> Int64 {
        switch draft.kind {
        case .product: store.data.products.first(where: { $0.id == draft.sourceID })?.priceCents ?? 0
        case .service: store.data.services.first(where: { $0.id == draft.sourceID })?.priceCents ?? 0
        }
    }

    private func pasteTransferSMS() {
        guard let text = UIPasteboard.general.string, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            errorMessage = "Copia el SMS de PAGOxMOVIL y vuelve a tocar «Pegar SMS»."
            return
        }
        guard let parsed = TransferMessageParser.parse(text) else {
            errorMessage = "No se encontró un número de transacción reconocible. Puedes escribirlo manualmente."
            return
        }
        transactionNumber = parsed.transactionNumber
        smsAmountCents = parsed.amountCents
    }

    private func completeSale() {
        do {
            _ = try store.recordSale(drafts, payment: payment, transactionNumber: transactionNumber,
                                     customer: payment == .transfer ? customer : nil,
                                     customerIsFixed: payment == .transfer && customerIsFixed)
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private struct SaleCatalogRow: View {
    let name: String
    let detail: String
    let price: String
    let quantity: Int
    let canAdd: Bool
    let onMinus: () -> Void
    let onPlus: () -> Void

    var body: some View {
        HStack(spacing: 11) {
            VStack(alignment: .leading, spacing: 4) {
                Text(name).font(.system(size: 14, weight: .semibold)).foregroundStyle(Color.shalevText).lineLimit(2)
                Text(detail).font(.system(size: 11)).foregroundStyle(Color.shalevSecondaryText).lineLimit(1)
                Text(price).font(.system(size: 12, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
            }
            Spacer(minLength: 8)
            HStack(spacing: 9) {
                if quantity > 0 {
                    Button(action: onMinus) { Image(systemName: "minus.circle.fill") }
                        .accessibilityLabel("Quitar una unidad de \(name)")
                    Text("\(quantity)").font(.system(size: 13, weight: .semibold)).monospacedDigit()
                }
                Button(action: onPlus) {
                    Image(systemName: quantity == 0 ? "plus.circle.fill" : "plus.circle")
                        .font(.system(size: 22, weight: .semibold))
                }
                .disabled(!canAdd)
                .opacity(canAdd ? 1 : 0.38)
                .accessibilityLabel("Añadir \(name)")
            }
            .foregroundStyle(Color.shalevPrimary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .background(Color.shalevSurface)
        .overlay(alignment: .bottom) { Color.shalevOutlineVariant.opacity(0.5).frame(height: 0.5) }
    }
}

private struct SaleInputStyle: TextFieldStyle {
    func _body(configuration: TextField<Self._Label>) -> some View {
        configuration
            .font(.system(size: 14))
            .padding(11)
            .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
    }
}
