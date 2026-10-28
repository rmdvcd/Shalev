import SwiftUI
import UIKit

/// Pasos de la venta, como en Android: carrito → comprobante (efectivo) o QR y datos del cliente (transferencia).
private enum SaleStep: Equatable {
    case cart, receipt, qr, customer
}

@MainActor
struct SaleComposerView: View {
    @ObservedObject var store: ShalevStore
    let mode: SaleMode
    let onOpenHome: () -> Void
    let onOpenSettings: () -> Void
    var canOpenSettings = true
    @Environment(\.dismiss) private var dismiss

    @State private var step: SaleStep = .cart
    @State private var cart: [UUID: Int] = [:]
    @State private var payment: PaymentMethod = .cash
    @State private var showCatalog = false
    @State private var catalogSearch = ""
    @State private var transactionNumber = ""
    @State private var smsAmountCents: Int64?
    @State private var customerName = ""
    @State private var customerID = ""
    @State private var customerPhone = ""
    @State private var customerIsFixed = false
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
        let card = store.data.settings.profile.paymentCard?.number.filter(\.isNumber) ?? ""
        guard (12...20).contains(card.count) else { return nil }
        let phoneDigits = store.data.settings.profile.paymentPhone?.number.filter(\.isNumber) ?? ""
        let mobile = phoneDigits.hasPrefix("53") ? String(phoneDigits.dropFirst(2)) : phoneDigits
        let validMobile = mobile.count == 8 ? mobile : ""
        return "TRANSFERMOVIL_ETECSA,TRANSFERENCIA,\(card),\(validMobile),"
    }

    private var products: [Product] {
        store.activeProducts.filter { $0.priceCents > 0 && $0.name.localizedCaseInsensitiveContains(catalogSearch) }
    }
    private var services: [ServiceItem] {
        store.activeServices.filter { $0.priceCents > 0 && $0.name.localizedCaseInsensitiveContains(catalogSearch) }
    }

    private var title: String {
        switch step {
        case .cart: mode.title
        case .receipt: "Comprobante"
        case .qr: "Cobro por transferencia"
        case .customer: "Datos del cliente"
        }
    }

    var body: some View {
        NavigationStack {
            Group {
                if store.openShift == nil {
                    shiftRequiredView
                } else {
                    VStack(spacing: 0) {
                        if step != .qr, !(step == .cart && cart.isEmpty) { totalHeader }
                        ScrollView { stepContent.padding(.horizontal, 16).padding(.bottom, 18) }
                    }
                }
            }
            .background(Color.shalevBackground)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    if step == .cart {
                        Button("Cancelar") { dismiss() }.foregroundStyle(Color.shalevSecondaryText)
                    } else {
                        Button {
                            switch step {
                            case .receipt, .qr: step = .cart
                            case .customer: step = .qr
                            case .cart: break
                            }
                        } label: {
                            Label("Atrás", systemImage: "chevron.left")
                        }
                        .foregroundStyle(Color.shalevPrimary)
                    }
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if store.openShift != nil && requiresActionBar { actionBar }
            }
            .alert("No se pudo registrar", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Entendido", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
            .sheet(isPresented: $showCatalog) {
                SaleCatalogSheet(store: store, mode: mode, cart: $cart)
                    .presentationDetents([.large])
                    .presentationDragIndicator(.visible)
            }
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

    private var requiresActionBar: Bool {
        switch step {
        case .cart: !cart.isEmpty
        case .receipt, .qr, .customer: true
        }
    }

    // MARK: - Cabecera del total

    private var totalHeader: some View {
        VStack(spacing: 4) {
            Text(totalLabel)
                .font(.shalev(13, weight: .medium))
                .foregroundStyle(Color.shalevSecondaryText)
            Text(Cup(cents: totalCents).formatted)
                .font(.shalev(24, weight: .bold))
                .foregroundStyle(Color.shalevText)
                .lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 4)
        .padding(.bottom, 8)
        .accessibilityElement(children: .combine)
    }

    private var totalLabel: String {
        switch step {
        case .qr: "Importe a transferir"
        case .receipt: "Total de la venta"
        case .customer: "Total de la venta"
        case .cart: "Total estimado"
        }
    }

    @ViewBuilder
    private var stepContent: some View {
        switch step {
        case .cart: cartStep
        case .receipt: receiptStep
        case .qr: qrStep
        case .customer: customerStep
        }
    }

    // MARK: - Carrito

    private var cartStep: some View {
        VStack(spacing: 12) {
            if let shift = store.openShift {
                let who = store.actorName
                Text("Turno abierto · Desde las \(shift.openedAt.shalevTime)\(who.isEmpty ? "" : " · \(who)")")
                    .font(.shalev(12))
                    .foregroundStyle(Color.shalevSecondaryText)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
            if cart.isEmpty {
                ShalevEmptyState(symbol: "cart", title: "El carrito está vacío",
                                 detail: "Elige artículos\(mode == .products ? " del inventario" : " de tus servicios") para empezar la venta.",
                                 actionTitle: "Elegir artículos") { showCatalog = true }
            } else {
                ForEach(drafts) { draft in cartLine(draft) }
                Button { showCatalog = true } label: {
                    Label("Agregar artículos", systemImage: "plus")
                }
                .buttonStyle(ShalevTonalButtonStyle())
                paymentSelector
                if let warnings = preview?.warnings, !warnings.isEmpty {
                    Label("Revisa el precio de \(warnings.joined(separator: ", ")): queda al costo o por debajo después de los ajustes.",
                          systemImage: "exclamationmark.triangle.fill")
                        .font(.shalev(11, weight: .medium))
                        .foregroundStyle(Color.shalevSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
        .padding(.top, 4)
    }

    private func cartLine(_ draft: CartDraft) -> some View {
        ShalevCard(padding: 14) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 10) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(lineName(for: draft))
                            .font(.shalev(15, weight: .semibold))
                            .foregroundStyle(Color.shalevText)
                            .lineLimit(2)
                        Text("\(Cup(cents: basePrice(for: draft)).formatted) c/u" + availabilityText(draft))
                            .font(.shalev(12))
                            .foregroundStyle(Color.shalevSecondaryText)
                            .lineLimit(2)
                    }
                    Spacer(minLength: 6)
                    Text(Cup(cents: lineSubtotal(draft)).formatted)
                        .font(.shalev(14, weight: .semibold))
                        .foregroundStyle(Color.shalevText)
                        .lineLimit(1).minimumScaleFactor(0.75)
                }
                HStack(spacing: 9) {
                    circleButton(symbol: "minus", label: "Una menos de \(lineName(for: draft))",
                                 enabled: draft.quantity > 1) { adjust(draft.sourceID, by: -1) }
                    Text("\(draft.quantity)")
                        .font(.shalev(15, weight: .semibold))
                        .foregroundStyle(Color.shalevText)
                        .frame(width: 76, height: 44)
                        .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 11).stroke(Color.shalevOutline, lineWidth: 1))
                        .accessibilityLabel("Cantidad de \(lineName(for: draft)): \(draft.quantity)")
                    circleButton(symbol: "plus", label: "Una más de \(lineName(for: draft))",
                                 enabled: canIncrease(draft)) { adjust(draft.sourceID, by: 1) }
                    Spacer(minLength: 0)
                    Button { cart.removeValue(forKey: draft.sourceID) } label: {
                        Image(systemName: "trash")
                            .font(.shalev(17, weight: .semibold))
                            .foregroundStyle(Color.shalevSecondaryText)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Quitar \(lineName(for: draft)) de la venta")
                }
            }
        }
    }

    private func circleButton(symbol: String, label: String, enabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.shalev(16, weight: .bold))
                .foregroundStyle(enabled ? Color.shalevPrimary : Color.shalevSecondaryText.opacity(0.45))
                .frame(width: 44, height: 44)
                .background(Color.shalevPrimaryContainer.opacity(enabled ? 1 : 0.45), in: Circle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .accessibilityLabel(label)
    }

    private func availabilityText(_ draft: CartDraft) -> String {
        if itemKind == .product {
            let available = store.availableQuantity(kind: .product, sourceID: draft.sourceID) ?? 0
            let kind = store.data.products.first(where: { $0.id == draft.sourceID })?.kind
            return kind == .prepared ? " · Alcanza para \(available)" : ""
        }
        let available = store.availableQuantity(kind: .service, sourceID: draft.sourceID) ?? 0
        return available == Int.max ? "" : " · Alcanza para \(available)"
    }

    private func canIncrease(_ draft: CartDraft) -> Bool {
        let available = store.availableQuantity(kind: itemKind, sourceID: draft.sourceID) ?? 0
        return draft.quantity < available
    }

    private var paymentSelector: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Método de pago")
                .font(.shalev(14, weight: .semibold))
                .foregroundStyle(Color.shalevText)
                .frame(maxWidth: .infinity, alignment: .leading)
            HStack(spacing: 10) {
                paymentPill(.cash, title: "Efectivo", symbol: "banknote")
                paymentPill(.transfer, title: "Transferencia", symbol: "arrow.left.arrow.right")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 4)
    }

    private func paymentPill(_ method: PaymentMethod, title: String, symbol: String) -> some View {
        let selected = payment == method
        return Button { payment = method } label: {
            Label(title, systemImage: symbol)
                .font(.shalev(13, weight: .semibold))
                .foregroundStyle(selected ? Color.shalevOnPrimary : Color.shalevText)
                .padding(.horizontal, 14)
                .frame(height: 44)
                .frame(maxWidth: .infinity)
                .background(selected ? Color.shalevPrimary : Color.clear,
                            in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 12)
                    .stroke(selected ? Color.clear : Color.shalevOutline, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    // MARK: - Comprobante (efectivo)

    private var receiptStep: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 11) {
                ForEach(drafts) { draft in
                    HStack(alignment: .top, spacing: 10) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(lineName(for: draft))
                                .font(.shalev(14, weight: .medium))
                                .foregroundStyle(Color.shalevText)
                                .lineLimit(2)
                            Text("\(draft.quantity) × \(Cup(cents: unitPrice(draft)).formatted)")
                                .font(.shalev(12))
                                .foregroundStyle(Color.shalevSecondaryText)
                        }
                        Spacer(minLength: 6)
                        Text(Cup(cents: lineSubtotal(draft)).formatted)
                            .font(.shalev(13, weight: .semibold))
                            .foregroundStyle(Color.shalevText)
                            .lineLimit(1).minimumScaleFactor(0.8)
                    }
                }
                Divider()
                Text("Pago: Efectivo")
                    .font(.shalev(12))
                    .foregroundStyle(Color.shalevSecondaryText)
            }
        }
        .padding(.top, 4)
    }

    // MARK: - QR (transferencia)

    private var qrStep: some View {
        VStack(spacing: 14) {
            if let qrPayload {
                ShalevQRCode(contents: qrPayload)
                ShalevCard {
                    VStack(spacing: 10) {
                        HStack { Text("Importe a transferir"); Spacer(); Text(Cup(cents: totalCents).formatted).fontWeight(.bold) }
                        Divider()
                        HStack { Text("Tarjeta"); Spacer(); Text(maskedCard).fontWeight(.semibold) }
                        if let phone = store.data.settings.profile.paymentPhone?.number {
                            HStack { Text("Móvil"); Spacer(); Text(phone).fontWeight(.semibold) }
                        }
                    }
                    .font(.shalev(14)).foregroundStyle(Color.shalevText)
                }
                ShalevCard {
                    VStack(alignment: .leading, spacing: 7) {
                        Label("El QR no incluye el importe", systemImage: "info.circle.fill")
                            .font(.shalev(13, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
                        Text("Comprueba que el cliente complete la transferencia por \(Cup(cents: totalCents).formatted). La app no verifica el pago con el banco.")
                            .font(.shalev(12)).foregroundStyle(Color.shalevSecondaryText)
                    }
                }
                transferTransactionFields
            } else {
                ShalevEmptyState(symbol: "creditcard", title: "Configura una tarjeta de cobro",
                                 detail: canOpenSettings ? "Añade tu tarjeta en Ajustes → Pago electrónico para generar el QR." : "Pide al administrador que configure la tarjeta de cobro.",
                                 actionTitle: canOpenSettings ? "Ir a Ajustes" : "Cerrar") {
                    if canOpenSettings {
                        dismiss()
                        onOpenSettings()
                    } else {
                        dismiss()
                    }
                }
            }
        }
        .padding(.top, 4)
    }

    private var maskedCard: String {
        let digits = store.data.settings.profile.paymentCard?.number.filter(\.isNumber) ?? ""
        guard digits.count > 4 else { return "••••" }
        return "•••• •••• •••• \(digits.suffix(4))"
    }

    private var transferTransactionFields: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 10) {
                Text("N.º de transacción").font(.shalev(13, weight: .semibold)).foregroundStyle(Color.shalevText)
                TextField("6–30 letras o dígitos", text: $transactionNumber)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .textFieldStyle(SaleInputStyle())
                    .submitLabel(.done)
                    .onChange(of: transactionNumber) { _, value in
                        if value.count > 30 { transactionNumber = String(value.prefix(30)) }
                    }
                Button(action: pasteTransferSMS) {
                    Label("Pegar SMS de Transfermóvil", systemImage: "doc.on.clipboard")
                        .font(.shalev(12, weight: .semibold))
                }
                .buttonStyle(ShalevTonalButtonStyle())
                if let smsAmountCents {
                    let differs = smsAmountCents != totalCents
                    Text(differs
                         ? "El SMS indica \(Cup(cents: smsAmountCents).formatted), pero la venta suma \(Cup(cents: totalCents).formatted). Revisa que sea el pago correcto."
                         : "Importe del SMS: \(Cup(cents: smsAmountCents).formatted)")
                        .font(.shalev(11, weight: differs ? .semibold : .regular))
                        .foregroundStyle(differs ? Color.shalevError : Color.shalevSecondaryText)
                }
            }
        }
    }

    // MARK: - Datos del cliente

    private var customerStep: some View {
        VStack(spacing: 14) {
            customerFields
            if let warnings = preview?.warnings, !warnings.isEmpty {
                Label("Revisa \(warnings.joined(separator: ", ")): el precio final queda al costo o por debajo.",
                      systemImage: "exclamationmark.triangle.fill")
                    .font(.shalev(11, weight: .medium)).foregroundStyle(Color.shalevSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(.top, 4)
    }

    private var customerFields: some View {
        ShalevCard {
            VStack(alignment: .leading, spacing: 10) {
                Label("Datos del cliente", systemImage: "person.crop.circle")
                    .font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevText)
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
                    .textContentType(.telephoneNumber)
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
                            .font(.shalev(13)).foregroundStyle(Color.shalevPrimary)
                    }
                    .accessibilityLabel("Usar cliente \(match.name)")
                }
            }
        }
    }

    // MARK: - Barra de acciones

    @ViewBuilder
    private var actionBar: some View {
        VStack(spacing: 0) {
            Divider()
            HStack(spacing: 14) {
                switch step {
                case .cart:
                    Button(action: continueFromCart) {
                        Label("Continuar", systemImage: "arrow.right")
                            .font(.shalev(15, weight: .semibold))
                            .foregroundStyle(Color.shalevOnPrimary)
                            .frame(maxWidth: .infinity, minHeight: 50)
                            .background(Color.shalevPrimary, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(cart.isEmpty)
                    .opacity(cart.isEmpty ? 0.45 : 1)
                case .receipt:
                    roundAction("xmark", label: "Cancelar la venta", filled: false) { dismiss() }
                    Spacer(minLength: 0)
                    roundAction("checkmark", label: "Confirmar la venta", filled: true) { completeSale() }
                case .qr:
                    roundAction("xmark", label: "Cancelar la venta", filled: false) { dismiss() }
                    Spacer(minLength: 0)
                    roundAction("checkmark", label: "Pago recibido", filled: true) { step = .customer }
                case .customer:
                    roundAction("chevron.left", label: "Volver al código QR", filled: false) { step = .qr }
                    Spacer(minLength: 0)
                    roundAction("checkmark", label: "Registrar la venta", filled: true) { completeSale() }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .background(ShalevBarBackground())
    }

    private func roundAction(_ symbol: String, label: String, filled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.shalev(19, weight: .bold))
                .foregroundStyle(filled ? Color.shalevOnPrimary : Color.shalevOnTertiaryContainer)
                .frame(width: 54, height: 54)
                .background(filled ? Color.shalevPrimary : Color.shalevTertiaryContainer, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }

    private func continueFromCart() {
        guard !cart.isEmpty else { return }
        if payment == .transfer && qrPayload == nil {
            errorMessage = BusinessError.invalidPaymentDetails.localizedDescription
            return
        }
        step = payment == .cash ? .receipt : .qr
    }

    // MARK: - Datos de las líneas

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

    /// Precio unitario con los ajustes aplicados (descuentos/recargos), si la vista previa lo calculó.
    private func unitPrice(_ draft: CartDraft) -> Int64 {
        preview?.lines.first(where: { $0.sourceID == draft.sourceID })?.unitPriceCents ?? basePrice(for: draft)
    }

    private func lineSubtotal(_ draft: CartDraft) -> Int64 {
        preview?.lines.first(where: { $0.sourceID == draft.sourceID })?.subtotalCents
            ?? basePrice(for: draft) * Int64(draft.quantity)
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
            ShalevHaptics.exito()   // igual que Android: vibra al terminar la venta
            dismiss()
        } catch {
            ShalevHaptics.error()
            errorMessage = error.localizedDescription
        }
    }

    private var shiftRequiredView: some View {
        VStack(spacing: 16) {
            Spacer()
            ShalevEmptyState(symbol: "lock.fill", title: "Abre un turno para vender",
                             detail: "Las ventas y los movimientos de caja siempre pertenecen a un turno.",
                             actionTitle: "Ir a Inicio") {
                dismiss()
                onOpenHome()
            }
            Spacer()
        }
        .padding(20)
    }
}

/// Selector de artículos de la venta: búsqueda y lista con cantidades. Se abre desde el carrito.
@MainActor
private struct SaleCatalogSheet: View {
    @ObservedObject var store: ShalevStore
    let mode: SaleMode
    @Binding var cart: [UUID: Int]
    @Environment(\.dismiss) private var dismiss
    @State private var search = ""

    private var kind: SaleItemKind { mode == .products ? .product : .service }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ShalevSearchField(text: $search, placeholder: "Buscar")
                    .padding(.horizontal, 16)
                    .padding(.bottom, 10)
                ScrollView {
                    VStack(spacing: 1) {
                        if mode == .products {
                            if products.isEmpty {
                                ShalevEmptyState(symbol: "shippingbox", title: "No hay artículos vendibles",
                                                 detail: search.isEmpty ? "Agrega artículos con precio y existencias en Inventario." : "Prueba con otra búsqueda.")
                            } else {
                                ForEach(products) { product in
                                    let available = store.availableQuantity(kind: .product, sourceID: product.id) ?? 0
                                    let quantity = cart[product.id, default: 0]
                                    SaleCatalogRow(name: product.name,
                                                   detail: product.kind == .prepared ? "Alcanza para \(available) · Elaborado" : "\(product.displayStock) · \(product.category)",
                                                   price: Cup(cents: product.priceCents).formatted,
                                                   quantity: quantity, canAdd: available > quantity,
                                                   onMinus: { adjust(product.id, by: -1) }, onPlus: { adjust(product.id, by: 1) })
                                }
                            }
                        } else {
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
                    .padding(.bottom, 20)
                }
            }
            .background(Color.shalevBackground)
            .navigationTitle(mode == .products ? "Elige los artículos" : "Elige los servicios")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Listo") { dismiss() }.foregroundStyle(Color.shalevPrimary)
                }
            }
        }
    }

    private var products: [Product] {
        store.activeProducts.filter { $0.priceCents > 0 && $0.name.localizedCaseInsensitiveContains(search) }
    }

    private var services: [ServiceItem] {
        store.activeServices.filter { $0.priceCents > 0 && $0.name.localizedCaseInsensitiveContains(search) }
    }

    private func adjust(_ id: UUID, by delta: Int) {
        let next = max(0, cart[id, default: 0] + delta)
        if next == 0 {
            cart.removeValue(forKey: id)
        } else {
            let available = store.availableQuantity(kind: kind, sourceID: id) ?? 0
            guard next <= available else { return }
            cart[id] = next
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
                Text(name).font(.shalev(14, weight: .semibold)).foregroundStyle(Color.shalevText).lineLimit(2)
                Text(detail).font(.shalev(11)).foregroundStyle(Color.shalevSecondaryText).lineLimit(1)
                Text(price).font(.shalev(12, weight: .semibold)).foregroundStyle(Color.shalevPrimary)
            }
            Spacer(minLength: 8)
            HStack(spacing: 9) {
                if quantity > 0 {
                    Button(action: onMinus) { Image(systemName: "minus.circle.fill") }
                        .accessibilityLabel("Quitar una unidad de \(name)")
                    Text("\(quantity)").font(.shalev(13, weight: .semibold)).monospacedDigit()
                }
                Button(action: onPlus) {
                    Image(systemName: quantity == 0 ? "plus.circle.fill" : "plus.circle")
                        .font(.shalev(22, weight: .semibold))
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
            .font(.shalev(14))
            .padding(11)
            .background(Color.shalevBackground, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
    }
}
