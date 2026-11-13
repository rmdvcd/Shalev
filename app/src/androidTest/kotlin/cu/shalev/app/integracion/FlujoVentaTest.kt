package cu.shalev.app.integracion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.inicio.TipoVenta
import cu.shalev.app.venta.CampoCliente
import cu.shalev.app.venta.VentaScreen
import cu.shalev.app.venta.VentaTags
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Producto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prompt 15 — venta de punta a punta: pantalla real + VentaViewModel + casos de uso + Room en memoria.
 * El Inventario (selector de productos) se sustituye por la selección que devuelve a la Venta.
 */
@RunWith(AndroidJUnit4::class)
class FlujoVentaTest {
    @get:Rule val rule = createComposeRule()
    @org.junit.Before fun permisos() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val i = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            i.uiAutomation.grantRuntimePermission(i.targetContext.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    private val env = EntornoIntegracion()

    @After fun fin() = env.cerrar()

    private fun crearRefresco(cantidad: Long = 10): Long = runBlocking {
        val r = env.productos.crear(Producto(categoria = "Bebidas", nombre = "Refresco", precioCosto = cup(60), precioVenta = cup(100), cantidad = cantidad, creadoEn = env.ahora), null)
        (r as cu.shalev.core.result.AppResult.Ok).value
    }

    private fun abrirTurno() = runBlocking { env.turnos.abrir(env.ahora, "Ana") }

    private fun mostrar(seleccion: List<Long>?, onTerminada: (String) -> Unit = {}) {
        val vm = env.ventaVm()
        val caja = env.cajaVm()
        rule.setContent {
            ShalevTheme {
                VentaScreen(
                    tipo = TipoVenta.VENTA, onBack = {}, onElegirProductos = { _, _ -> }, onTerminada = onTerminada,
                    onConfigurarPago = {}, seleccion = seleccion, onSeleccionConsumida = {}, viewModel = vm, cajaVm = caja,
                )
            }
        }
    }

    private fun esperarVentas(n: Int) = rule.waitUntil(5_000) { runBlocking { env.ventas.deTurno(1).size == n } }

    @Test fun ventaEnEfectivoSeRegistraYDescuentaElStock() {
        val id = crearRefresco()
        abrirTurno()
        var terminada: String? = null
        mostrar(listOf(id)) { terminada = it }

        rule.waitUntil(5_000) { rule.onAllNodesWithTag(VentaTags.mas(id)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(VentaTags.mas(id)).performClick()                          // 2 unidades
        rule.onNodeWithTag(VentaTags.metodo(MetodoPago.EFECTIVO)).performClick()
        rule.onNodeWithTag(VentaTags.CONTINUAR).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(VentaTags.CONFIRMAR).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("200 CUP", substring = true).onFirst().assertExists()
        rule.onNodeWithTag(VentaTags.CONFIRMAR).performClick()

        esperarVentas(1)
        val v = runBlocking { env.ventas.deTurno(1).single() }
        assertEquals(MetodoPago.EFECTIVO, v.metodoPago)
        assertEquals(cup(200), v.total)
        assertEquals(8L, runBlocking { env.productos.obtener(id)!!.cantidad })
        rule.waitUntil(5_000) { terminada != null }
        assertTrue(terminada!!.contains("200 CUP"))
    }

    @Test fun ventaPorTransferenciaConQrYDatosDelCliente() {
        val id = crearRefresco()
        abrirTurno()
        mostrar(listOf(id))

        rule.waitUntil(5_000) { rule.onAllNodesWithTag(VentaTags.metodo(MetodoPago.TRANSFERENCIA)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(VentaTags.metodo(MetodoPago.TRANSFERENCIA)).performClick()
        rule.onNodeWithTag(VentaTags.CONTINUAR).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(VentaTags.QR).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(VentaTags.QR).assertIsDisplayed()
        rule.onNodeWithTag(VentaTags.PAGO_RECIBIDO).performScrollTo().performClick()

        rule.onNodeWithTag(VentaTags.campo(CampoCliente.NOMBRE)).performScrollTo().performTextInput("María Pérez")
        rule.onNodeWithTag(VentaTags.campo(CampoCliente.CI)).performScrollTo().performTextInput("85010112345")
        rule.onNodeWithTag(VentaTags.campo(CampoCliente.TELEFONO)).performScrollTo().performTextInput("51234567")
        rule.onNodeWithTag(VentaTags.campo(CampoCliente.NUMERO)).performScrollTo().performTextInput("BR601ADLM8997")
        rule.onNodeWithTag(VentaTags.CONFIRMAR).performClick()

        esperarVentas(1)
        val v = runBlocking { env.ventas.deTurno(1).single() }
        assertEquals(MetodoPago.TRANSFERENCIA, v.metodoPago)
        assertEquals("BR601ADLM8997", v.transaccion!!.numero)
        assertEquals("+5351234567", v.transaccion!!.cliente.telefono)
        assertEquals("9248129970876454", v.transaccion!!.tarjetaCobro)
        assertEquals(9L, runBlocking { env.productos.obtener(id)!!.cantidad })
    }

    @Test fun sinTurnoSeBloqueaYAbrirloDesdeLaVentaPermiteVender() {
        val id = crearRefresco()
        mostrar(listOf(id))
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(VentaTags.BLOQUEO).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(VentaTags.CONFIRMAR).assertDoesNotExist()
        assertTrue(runBlocking { env.turnos.activo() } == null)

        rule.onNodeWithTag(VentaTags.ABRIR_TURNO).performClick()
        rule.onNodeWithTag(cu.shalev.app.caja.CajaTags.FONDO).performTextInput("0")
        rule.onNodeWithTag(cu.shalev.app.caja.CajaTags.CONFIRMAR).performClick()
        rule.waitUntil(5_000) { runBlocking { env.turnos.activo() } != null }
        rule.waitUntil(5_000) { rule.onAllNodesWithTag(VentaTags.BLOQUEO).fetchSemanticsNodes().isEmpty() }
        assertEquals("Ana", runBlocking { env.turnos.activo()!!.abiertoPor })
    }
}
