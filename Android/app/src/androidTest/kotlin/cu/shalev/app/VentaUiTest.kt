package cu.shalev.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.inicio.TipoVenta
import cu.shalev.app.venta.AccionesVenta
import cu.shalev.app.venta.CampoCliente
import cu.shalev.app.venta.FormCliente
import cu.shalev.app.venta.LineaCarrito
import cu.shalev.app.venta.PasoVenta
import cu.shalev.app.venta.TextosVenta
import cu.shalev.app.venta.VentaContent
import cu.shalev.app.venta.VentaTags
import cu.shalev.app.venta.VentaUiState
import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Turno
import cu.shalev.domain.service.Cotizacion
import cu.shalev.domain.service.PagoQr
import cu.shalev.domain.usecase.ObservarPermisoVenta.Permiso
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Prompt 13 sin Hilt: carrito, comprobante (iconos Confirmar/Cancelar), QR y datos del cliente. */
@RunWith(AndroidJUnit4::class)
class VentaUiTest {
    @get:Rule val rule = createComposeRule()
    private val zona = ZoneId.of("America/Havana")
    private val t0 = Instant.parse("2026-09-30T12:30:00Z")
    private val abierto = Permiso.Permitido(Turno(1, t0, abiertoPor = "Ana"))
    private val refresco = Producto(
        id = 1, categoria = "Bebidas", nombre = "Refresco", precioCosto = Cup.ofPesos(60), precioVenta = Cup.ofPesos(100),
        cantidad = 5, creadoEn = t0,
    )
    private val detalle = DetalleVenta(
        productoId = 1, nombre = "Refresco", categoria = "Bebidas", cantidad = 2,
        precioBase = Cup.ofPesos(100), precioUnitario = Cup.ofPesos(100), costoUnitario = Cup.ofPesos(60),
    )

    @Test fun carritoVacioOfreceElegirProductos() {
        var elegir = 0
        rule.setContent { ShalevTheme { VentaContent(TipoVenta.VENTA, VentaUiState(abierto), AccionesVenta(onElegir = { elegir++ }), zona = zona) } }
        rule.onNodeWithText(TextosVenta.CARRITO_VACIO_TITULO).assertIsDisplayed()
        rule.onNodeWithContentDescription(TextosVenta.ELEGIR).performClick()
        assertEquals(1, elegir)
    }

    @Test fun carritoAjustaCantidadesYMetodo() {
        val cambios = mutableListOf<Pair<Long, Long>>(); var metodo: MetodoPago? = null
        val s = VentaUiState(abierto, lineas = listOf(LineaCarrito(refresco, 5)))
        rule.setContent {
            ShalevTheme {
                VentaContent(TipoVenta.VENTA, s, AccionesVenta(onCantidad = { id, n -> cambios += id to n }, onMetodo = { metodo = it }), zona = zona)
            }
        }
        rule.onNodeWithTag(VentaTags.mas(1)).assertIsNotEnabled()          // 5 de 5: no se puede más
        rule.onNodeWithTag(VentaTags.menos(1)).performClick()
        assertEquals(listOf(1L to 4L), cambios)
        rule.onNodeWithTag(VentaTags.metodo(MetodoPago.TRANSFERENCIA)).performClick()
        assertEquals(MetodoPago.TRANSFERENCIA, metodo)
        rule.onNodeWithTag(VentaTags.TOTAL).assertTextContains("500 CUP", substring = true)
    }

    @Test fun agregarArticuloPermaneceEnLaBarraDelCarrito() {
        var elegir = 0
        val s = VentaUiState(abierto, lineas = listOf(LineaCarrito(refresco, 2)))
        rule.setContent { ShalevTheme { VentaContent(TipoVenta.VENTA, s, AccionesVenta(onElegir = { elegir++ }), zona = zona) } }
        rule.onNodeWithContentDescription(TextosVenta.AGREGAR).assertIsDisplayed().performClick()
        assertEquals(1, elegir)
    }

    @Test fun comprobanteConConfirmarYCancelar() {
        var confirmar = 0; var cancelar = 0
        val s = VentaUiState(
            abierto, lineas = listOf(LineaCarrito(refresco, 2)), paso = PasoVenta.COMPROBANTE,
            cotizacion = Cotizacion(MetodoPago.EFECTIVO, listOf(detalle), emptyMap()),
        )
        rule.setContent {
            ShalevTheme { VentaContent(TipoVenta.VENTA, s, AccionesVenta(onConfirmarEfectivo = { confirmar++ }, onSolicitarDescartar = { cancelar++ }), zona = zona) }
        }
        rule.onNodeWithText(TextosVenta.linea(2, Cup.ofPesos(100))).assertIsDisplayed()
        rule.onNodeWithTag(VentaTags.CONFIRMAR).performClick()
        rule.onNodeWithTag(VentaTags.CANCELAR).performClick()
        assertEquals(1, confirmar); assertEquals(1, cancelar)
    }

    @Test fun turnoCerradoEnElComprobanteMuestraElBloqueo() {
        val s = VentaUiState(
            Permiso.SinTurno, lineas = listOf(LineaCarrito(refresco, 2)), paso = PasoVenta.COMPROBANTE,
            cotizacion = Cotizacion(MetodoPago.EFECTIVO, listOf(detalle), emptyMap()),
        )
        rule.setContent { ShalevTheme { VentaContent(TipoVenta.VENTA, s, AccionesVenta(), zona = zona) } }
        rule.onNodeWithTag(VentaTags.BLOQUEO).assertIsDisplayed()
        rule.onNodeWithTag(VentaTags.CONFIRMAR).assertDoesNotExist()
    }

    @Test fun qrMuestraElImporteYPasaADatos() {
        var recibido = 0
        val s = VentaUiState(
            abierto, lineas = listOf(LineaCarrito(refresco, 2)), metodo = MetodoPago.TRANSFERENCIA, paso = PasoVenta.QR,
            cotizacion = Cotizacion(MetodoPago.TRANSFERENCIA, listOf(detalle), emptyMap()),
            qr = PagoQr.Resultado.Ok("TRANSFERMOVIL_ETECSA,TRANSFERENCIA,9248129970876454,51815604,", "9248129970876454", "51815604"),
        )
        rule.setContent { ShalevTheme { VentaContent(TipoVenta.VENTA, s, AccionesVenta(onPagoRecibido = { recibido++ }), zona = zona) } }
        rule.onNodeWithTag(VentaTags.QR).assertIsDisplayed()
        // P28: el importe va una sola vez, centrado arriba, con la etiqueta «Importe a transferir».
        rule.onNodeWithText(TextosVenta.IMPORTE).assertIsDisplayed()
        rule.onNodeWithText(Money.format(Cup.ofPesos(200))).assertIsDisplayed()
        val qrBottom = rule.onAllNodesWithTag(VentaTags.QR).fetchSemanticsNodes().single().boundsInRoot.bottom
        val importeTop = rule.onAllNodesWithTag(VentaTags.TOTAL).fetchSemanticsNodes().single().boundsInRoot.top
        assertTrue("el importe de transferencia debe quedar debajo del QR", importeTop >= qrBottom)
        rule.onNodeWithTag(VentaTags.PAGO_RECIBIDO).performClick()
        assertEquals(1, recibido)
    }

    @Test fun datosDelClienteMuestranErroresYPegarSms() {
        var pegar = 0
        val s = VentaUiState(
            abierto, lineas = listOf(LineaCarrito(refresco, 2)), metodo = MetodoPago.TRANSFERENCIA, paso = PasoVenta.CLIENTE,
            cliente = FormCliente(intento = true),
        )
        rule.setContent { ShalevTheme { VentaContent(TipoVenta.VENTA, s, AccionesVenta(onPegarSms = { pegar++ }), zona = zona) } }
        rule.onNodeWithTag(VentaTags.campo(CampoCliente.NUMERO)).assertExists()
        rule.onNodeWithText("Escribe o pega el nº de transacción.").assertExists()
        rule.onNodeWithTag(VentaTags.PEGAR_SMS).performClick()
        assertEquals(1, pegar)
    }
}
