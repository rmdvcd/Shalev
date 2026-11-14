package cu.shalev.app.capturas

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import cu.shalev.app.inicio.TipoVenta
import cu.shalev.app.pagos.AccionesListaPago
import cu.shalev.app.pagos.EdicionPago
import cu.shalev.app.pagos.PagoElectronicoContent
import cu.shalev.app.pagos.PagoUiState
import cu.shalev.app.pagos.TextosPago
import cu.shalev.app.pagos.TipoCuentaPago
import cu.shalev.app.precios.AccionesPrecios
import cu.shalev.app.precios.PreajusteForm
import cu.shalev.app.precios.PreciosContent
import cu.shalev.app.precios.PreciosUiState
import cu.shalev.app.venta.AccionesVenta
import cu.shalev.app.venta.FormCliente
import cu.shalev.app.venta.LineaCarrito
import cu.shalev.app.venta.PasoVenta
import cu.shalev.app.venta.VentaContent
import cu.shalev.app.venta.VentaUiState
import cu.shalev.core.money.Cup
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.PreajustePrecios
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.model.Turno
import cu.shalev.domain.service.Cotizacion
import cu.shalev.domain.service.PagoQr
import cu.shalev.domain.usecase.ObservarPermisoVenta.Permiso
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Venta (carrito, comprobante, QR, datos del cliente, bloqueo sin turno), Pago electrónico y Precios. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = Captura.TELEFONO, application = Application::class)
class VentaCapturas {
    @get:Rule val rule = createComposeRule()

    private val t0 = Captura.AHORA.minusSeconds(30L * 86_400)
    private val abierto = Permiso.Permitido(Turno(7, Captura.AHORA.minusSeconds(8 * 3600), abiertoPor = "Ana Pérez"))
    private fun p(id: Long, cat: String, nombre: String, venta: Long, cantidad: Long) = Producto(
        id = id, categoria = cat, nombre = nombre, precioCosto = Cup.ofPesos(venta * 6 / 10), precioVenta = Cup.ofPesos(venta), cantidad = cantidad, creadoEn = t0,
    )
    private val refresco = p(1, "Bebidas", "Refresco de lata 355 ml", 150, 48)
    private val pizza = p(4, "Elaborado", "Pizza napolitana", 400, 0)
    private val lineas = listOf(LineaCarrito(pizza, 2, alcanza = 12), LineaCarrito(refresco, 2)) // P26: «Alcanza para 12»
    private fun d(pr: Producto, n: Long) = DetalleVenta(
        productoId = pr.id, nombre = pr.nombre, categoria = pr.categoria, cantidad = n,
        precioBase = pr.precioVenta, precioUnitario = pr.precioVenta, costoUnitario = pr.precioCosto,
    )
    private val detalles = listOf(d(pizza, 2), d(refresco, 2))
    private val qr = PagoQr.Resultado.Ok("TRANSFERMOVIL_ETECSA,TRANSFERENCIA,9205129970876454,52345678,", "9205129970876454", "52345678")

    private fun venta(id: String, s: VentaUiState, tipo: TipoVenta = TipoVenta.VENTA) =
        rule.capturar(id) { VentaContent(tipo, s, AccionesVenta(), zona = Captura.ZONA) }

    @Test fun sinTurno() = venta("05c_venta_sin_turno", VentaUiState(Permiso.SinTurno))
    @Test fun carritoVacio() = venta("05b_venta_carrito_vacio", VentaUiState(abierto))
    @Test fun carrito() = venta("05a_venta_carrito", VentaUiState(abierto, lineas = lineas))
    @Test fun carritoTransferencia() = venta("05a2_venta_carrito_transferencia", VentaUiState(abierto, lineas = lineas, metodo = MetodoPago.TRANSFERENCIA))
    @Test fun carritoServicios() = venta("05d2_venta_servicios", VentaUiState(abierto, tipo = TipoVenta.SERVICIO, lineas = lineas.take(1)), TipoVenta.SERVICIO)
    @Test fun carritoError() = venta(
        "05d_venta_elaborados_error_stock",
        VentaUiState(abierto, lineas = listOf(LineaCarrito(refresco, 60)), error = "Solo quedan 48 unidades de Refresco de lata 355 ml."),
    )
    @Test fun comprobante() = venta(
        "05e_venta_comprobante",
        VentaUiState(abierto, lineas = lineas, paso = PasoVenta.COMPROBANTE, cotizacion = Cotizacion(MetodoPago.EFECTIVO, detalles, emptyMap())),
    )
    @Test fun qr() = venta(
        "05f_venta_qr",
        VentaUiState(abierto, lineas = lineas, metodo = MetodoPago.TRANSFERENCIA, paso = PasoVenta.QR, cotizacion = Cotizacion(MetodoPago.TRANSFERENCIA, detalles, emptyMap()), qr = qr),
    )
    @Test fun qrSinTarjeta() = venta(
        "05g_venta_qr_sin_tarjeta",
        VentaUiState(abierto, lineas = lineas, metodo = MetodoPago.TRANSFERENCIA, paso = PasoVenta.QR, cotizacion = Cotizacion(MetodoPago.TRANSFERENCIA, detalles, emptyMap()), qr = PagoQr.Resultado.SinTarjeta),
    )
    @Test fun clienteErrores() = venta(
        "05i_venta_datos_cliente_errores",
        VentaUiState(abierto, lineas = lineas, metodo = MetodoPago.TRANSFERENCIA, paso = PasoVenta.CLIENTE, cotizacion = Cotizacion(MetodoPago.TRANSFERENCIA, detalles, emptyMap()), cliente = FormCliente(intento = true)),
    )
    @Test fun clienteCompleto() = venta(
        "05h_venta_datos_cliente",
        VentaUiState(
            abierto, lineas = lineas, metodo = MetodoPago.TRANSFERENCIA, paso = PasoVenta.CLIENTE, cotizacion = Cotizacion(MetodoPago.TRANSFERENCIA, detalles, emptyMap()),
            cliente = FormCliente("Ana Díaz Rodríguez", "90020212345", "53123456", "MM10040FEJ987"), avisoSms = "El importe del SMS (1\u202F000 CUP) no coincide con el total (1\u202F100 CUP).",
        ),
    )
    @Test fun confirmarSalir() = venta("05j_venta_dialogo_descartar", VentaUiState(abierto, lineas = lineas, confirmarSalir = true))

    // ---------- Pago electrónico ----------
    private val perfil = Perfil(
        "Ana", "Díaz Rodríguez", "85010112345",
        tarjetas = listOf(TarjetaBancaria(3, "9205129970876454"), TarjetaBancaria(4, "9227069995328054")),
        telefonos = listOf(Telefono(1, "52345678"), Telefono(2, "53123456")),
        pagoTarjetaId = 3, pagoTelefonoId = 1,
    )
    private val accionesLista = AccionesListaPago(
        onElegir = { _, _ -> }, onNuevo = {}, onEditar = { _, _ -> }, onBorrar = { _, _ -> },
        onCambiarEdicion = {}, onGuardarEdicion = {}, onCerrarEdicion = {}, onConfirmarBorrado = {}, onCancelarBorrado = {},
    )
    private fun pago(id: String, s: PagoUiState) = rule.capturar(id) { PagoElectronicoContent(s, {}, accionesLista) }

    @Test fun pagoLista() = pago("06c_pago_lista", PagoUiState(true, perfil))
    @Test fun pagoVacio() = pago("06d_pago_lista_vacia", PagoUiState(true, Perfil()))
    @Test fun pagoEditar() = pago("06f_pago_hoja_editar_tarjeta", PagoUiState(true, perfil, edicion = EdicionPago(TipoCuentaPago.TARJETA, 3, "9205129970876454")))
    @Test fun pagoNuevoError() = pago("06e_pago_hoja_nuevo_telefono", PagoUiState(true, perfil, edicion = EdicionPago(TipoCuentaPago.TELEFONO, null, "5234", mostrarErrores = true)))
    @Test fun pagoBorrar() = pago("06g_pago_dialogo_eliminar", PagoUiState(true, perfil, borrar = TipoCuentaPago.TARJETA to 4L))
    @Test fun pagoSnackbar() = rule.capturar("06h_pago_snackbar_guardado") {
        PagoElectronicoContent(PagoUiState(true, perfil), {}, accionesLista, snackbarCon(TextosPago.GUARDADO))
    }

    // ---------- Precios ----------
    private val productos = listOf(refresco, pizza, p(2, "Confituras", "Galletas de chocolate", 150, 1), p(6, "Bodega", "Arroz 1 kg", 320, 30))
    private val preajustes = listOf(
        PreajustePrecios(1, "Transferencia +5 %", 500, setOf(1, 4), metodoPago = MetodoPago.TRANSFERENCIA),
        PreajustePrecios(2, "Por mayor −10 %", -1000, setOf(6), importeMinimo = Cup.ofPesos(5000), activo = false),
    )
    private val accionesPrecios = AccionesPrecios(
        onNuevo = {}, onEditar = {}, onActivar = { _, _ -> }, onCambiar = {}, onProducto = {}, onElegirVisibles = {},
        onGuardar = {}, onCerrar = {}, onBorrar = {}, onConfirmarBorrado = {}, onCancelarBorrado = {},
    )
    private fun precios(id: String, s: PreciosUiState) = rule.capturar(id) { PreciosContent(s, {}, accionesPrecios) }

    @Test fun preciosLista() = precios("06i_precios_lista", PreciosUiState(true, preajustes, productos))
    @Test fun preciosVacio() = precios("06j_precios_vacio", PreciosUiState(true, emptyList(), productos))
    @Test fun preciosSinProductos() = precios("06k_precios_sin_productos", PreciosUiState(true, emptyList(), emptyList()))
    @Test fun preciosForm() = precios(
        "06l_precios_hoja_formulario",
        PreciosUiState(true, preajustes, productos, form = PreajusteForm(nombre = "Transferencia +5 %", porcentaje = "5", metodo = MetodoPago.TRANSFERENCIA, productoIds = setOf(1, 4))),
    )
    @Test fun preciosFormErrores() = precios("06m_precios_hoja_errores", PreciosUiState(true, preajustes, productos, form = PreajusteForm(mostrarErrores = true)))
    @Test fun preciosBorrar() = precios("06n_precios_dialogo_eliminar", PreciosUiState(true, preajustes, productos, borrar = preajustes[1]))
}
