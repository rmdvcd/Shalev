package cu.shalev.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.inicio.TipoVenta
import cu.shalev.app.registros.AccionesRegistros
import cu.shalev.app.registros.PestanaRegistros
import cu.shalev.app.registros.PestanaTurno
import cu.shalev.app.registros.RegistrosContent
import cu.shalev.app.registros.RegistrosTags
import cu.shalev.app.registros.RegistrosUiState
import cu.shalev.app.registros.TextosTurno
import cu.shalev.app.registros.TurnoDetalleContent
import cu.shalev.app.registros.TurnoDetalleTags
import cu.shalev.app.venta.AccionesVenta
import cu.shalev.app.venta.VentaContent
import cu.shalev.app.venta.VentaTags
import cu.shalev.app.venta.VentaUiState
import cu.shalev.core.money.Cup
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.DetalleTurno
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.ResumenTurno
import cu.shalev.domain.model.Turno
import cu.shalev.domain.model.Venta
import cu.shalev.domain.usecase.ObservarPermisoVenta.Permiso
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Prompt 7 sin Hilt: bloqueo de venta, registro de turnos y detalle. */
@RunWith(AndroidJUnit4::class)
class TurnoUiTest {
    @get:Rule val rule = createComposeRule()
    private val zona = ZoneId.of("America/Havana")
    private val t0 = Instant.parse("2026-09-30T12:30:00Z")

    @Test fun sinTurnoLaVentaMuestraBloqueoYBotonAbrir() {
        var abrir = 0
        rule.setContent { ShalevTheme { VentaContent(TipoVenta.VENTA, VentaUiState(Permiso.SinTurno), acciones = AccionesVenta(onAbrirTurno = { abrir++ }), zona = zona) } }
        rule.onNodeWithTag(VentaTags.BLOQUEO).assertIsDisplayed()
        rule.onNodeWithText(TextosTurno.SIN_TURNO_TITULO).assertIsDisplayed()
        rule.onNodeWithTag(VentaTags.ABRIR_TURNO).performClick()
        assertEquals(1, abrir)
        rule.onNodeWithTag(VentaTags.TURNO).assertDoesNotExist()
    }

    @Test fun conTurnoSeMuestraDesdeCuandoYQuien() {
        val t = Turno(1, t0, abiertoPor = "Ana Pérez")
        rule.setContent { ShalevTheme { VentaContent(TipoVenta.VENTA, VentaUiState(Permiso.Permitido(t)), acciones = AccionesVenta(), zona = zona) } }
        rule.onNodeWithTag(VentaTags.BLOQUEO).assertDoesNotExist()
        rule.onNodeWithText("Turno abierto · Desde las 08:30 · Ana Pérez").assertIsDisplayed()
    }

    @Test fun registroVacioYListaNavegable() {
        var abierto: Long? = null
        val lista = listOf(Turno(2, t0), Turno(1, t0.minusSeconds(86_400), t0.minusSeconds(80_000), resumen = ResumenTurno.VACIO.copy(total = Cup.ofPesos(1450))))
        rule.setContent {
            ShalevTheme {
                RegistrosContent(
                    state = RegistrosUiState(pestana = PestanaRegistros.TURNOS), turnos = EstadoCarga.Exito(lista),
                    acciones = AccionesRegistros(onTurno = { abierto = it }), zona = zona,
                )
            }
        }
        rule.onNodeWithText("1\u202F450 CUP").assertIsDisplayed()
        rule.onNodeWithTag(RegistrosTags.turno(1)).performClick()
        assertEquals(1L, abierto)
    }

    @Test fun detalleProvisionalMuestraAvisoYVentas() {
        val v = Venta(id = 1, turnoId = 1, fecha = t0, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(
            DetalleVenta(productoId = 1, nombre = "Refresco", categoria = "Bebidas", cantidad = 2, precioBase = Cup.ofPesos(100), precioUnitario = Cup.ofPesos(100), costoUnitario = Cup.ofPesos(60))))
        val d = DetalleTurno(Turno(1, t0, abiertoPor = "Ana"), listOf(v), emptyList(), emptyList())
        rule.setContent { ShalevTheme { TurnoDetalleContent(EstadoCarga.Exito(d), onBack = {}, onReintentar = {}, zona = zona, ahora = t0.plusSeconds(3600)) } }
        rule.onNodeWithTag(TurnoDetalleTags.PROVISIONAL).assertIsDisplayed()
        // P24: las ventas están en su propia pestaña.
        rule.onNodeWithTag(TurnoDetalleTags.VENTAS).assertDoesNotExist()
        rule.onNodeWithTag(TurnoDetalleTags.pestana(PestanaTurno.VENTAS)).performClick()
        rule.onNodeWithTag(TurnoDetalleTags.VENTAS).assertIsDisplayed()
        rule.onNodeWithTag(TurnoDetalleTags.VENDIDOS).assertIsDisplayed()
        assertTrue(d.provisional)
    }
}
