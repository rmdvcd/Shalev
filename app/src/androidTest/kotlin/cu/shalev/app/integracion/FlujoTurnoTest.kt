package cu.shalev.app.integracion

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.caja.CajaTags
import cu.shalev.app.inicio.AccionesInicio
import cu.shalev.app.inicio.InicioContent
import cu.shalev.app.inicio.InicioTags
import cu.shalev.core.result.AppResult
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Venta
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Apertura y cierre desde InicioContent + ViewModels/casos de uso + Room reales.
 * Se dejan fuera los bordes de InicioScreen (actualizaciones por red, notificaciones y navegación Hilt).
 * El fondo, el efectivo y el inventario recorren el asistente real, sin confirmar el cierre por un atajo.
 */
@RunWith(AndroidJUnit4::class)
class FlujoTurnoTest {
    @get:Rule val rule = createComposeRule()
    private val env = EntornoIntegracion()
    private lateinit var inicio: cu.shalev.app.inicio.InicioViewModel

    @After fun fin() = env.cerrar()

    private fun interruptor() = rule.onNode(isToggleable() and hasAnyAncestor(hasTestTag(InicioTags.TURNO)))

    private fun mostrar() {
        val vm = env.inicioVm().also { inicio = it }
        val cajaVm = env.cajaVm()
        val conteoVm = env.conteoVm()
        rule.setContent {
            ShalevTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                val caja by cajaVm.state.collectAsStateWithLifecycle()
                InicioContent(
                    state, AccionesInicio(
                        onNavigate = {}, onTurno = vm::cambiarTurno, onNuevaVenta = vm::nuevaVenta,
                        onElegirVenta = vm::elegirVenta, onConfirmarCierre = vm::confirmarCierreTurno,
                        onCerrarDialogo = vm::cerrarDialogo, onPeriodo = vm::elegirPeriodo,
                        onPago = {}, onReintentar = vm::refrescar, onAbrirTurno = vm::abrirTurnoCon,
                    ),
                    caja = caja, conteoVm = conteoVm, zona = ZoneId.of("America/Havana"),
                )
            }
        }
        rule.onNodeWithTag(InicioTags.TURNO).assertIsDisplayed() // bloque fijo, no es un hijo desplazable
    }

    @Test fun abrirYCerrarTurnoCongelaElResumen() {
        mostrar()
        assertNull(runBlocking { env.turnos.activo() })

        interruptor().performClick()
        rule.onNodeWithTag(CajaTags.FONDO).performTextInput("0")
        rule.onNodeWithTag(CajaTags.CONFIRMAR).performClick()
        rule.waitUntil(5_000) { runBlocking { env.turnos.activo() } != null }
        val abierto = runBlocking { env.turnos.activo()!! }
        assertEquals("Ana", abierto.abiertoPor)

        // Una venta dentro del turno (por el repositorio: la UI de venta tiene su propio flujo).
        runBlocking {
            val id = (env.productos.crear(Producto(categoria = "Bebidas", nombre = "Refresco", precioCosto = cup(60), precioVenta = cup(100), cantidad = 5, creadoEn = env.ahora), null) as AppResult.Ok).value
            val r = env.ventas.registrar(Venta(turnoId = abierto.id, fecha = env.ahora, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(
                DetalleVenta(productoId = id, nombre = "Refresco", categoria = "Bebidas", cantidad = 3, precioBase = cup(100), precioUnitario = cup(100), costoUnitario = cup(60)))))
            assertTrue(r is AppResult.Ok)
        }

        rule.waitUntil(5_000) { inicio.state.value.arqueo?.esperado == cup(300) }
        interruptor().performClick()
        rule.onNodeWithTag(CajaTags.OMITIR).performClick() // no se cuentan billetes: se da el total
        rule.onNodeWithTag(CajaTags.CONTADO).performScrollTo().performTextReplacement("300")
        rule.onNodeWithTag(CajaTags.CONFIRMAR).performClick()
        rule.onNode(hasSetTextAction() and hasText("Refresco", substring = true)).performScrollTo().performTextInput("2")
        rule.onNodeWithContentDescription("Revisar diferencias").performClick()
        // El mismo botón de confirmación conserva la protección contra el doble toque entre pasos.
        val desde = android.os.SystemClock.elapsedRealtime()
        rule.waitUntil(2_000) { android.os.SystemClock.elapsedRealtime() - desde >= cu.shalev.designsystem.component.GuardiaDobleToque.VENTANA_MS }
        rule.onNodeWithContentDescription("Cerrar turno").performClick()
        rule.waitUntil(5_000) { runBlocking { env.turnos.activo() } == null }
        val cerrado = runBlocking { env.turnos.ultimoCerrado()!! }
        assertEquals(abierto.id, cerrado.id)
        assertEquals(1, cerrado.resumen!!.numVentas)
        assertEquals(cup(300), cerrado.resumen!!.total)
        assertEquals(cup(300), cerrado.contado)
        assertTrue(cerrado.conteoCierre!!.inventario.all { it.diferencia == 0L })
    }

    @Test fun cancelarElDialogoNoCierraElTurno() {
        runBlocking { env.turnos.abrir(env.ahora, "Ana") }
        mostrar()
        rule.waitUntil(5_000) { inicio.state.value.turnoAbierto }
        interruptor().performClick()
        rule.onNodeWithContentDescription("Continuar al inventario").assertExists()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithContentDescription("Continuar al inventario").assertDoesNotExist()
        assertTrue(runBlocking { env.turnos.activo() } != null)
    }
}
