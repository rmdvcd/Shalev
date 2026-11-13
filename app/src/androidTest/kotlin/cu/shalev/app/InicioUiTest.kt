package cu.shalev.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.inicio.AccionesInicio
import cu.shalev.app.inicio.AlertaUi
import cu.shalev.app.inicio.DialogoInicio
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.inicio.InicioContent
import cu.shalev.app.inicio.InicioTags
import cu.shalev.app.inicio.InicioUiState
import cu.shalev.app.inicio.TipoTop
import cu.shalev.app.inicio.TipoVenta
import cu.shalev.app.navigation.Route
import cu.shalev.core.money.Cup
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.Porcion
import cu.shalev.domain.model.ResumenGeneral
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.Top3
import cu.shalev.domain.model.TopItem
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Inicio sin Hilt: reglas visibles del Prompt 6 (contador 0 oculto, Top 3 oculto sin datos, banner, diálogo de venta). */
@RunWith(AndroidJUnit4::class)
class InicioUiTest {
    @get:Rule val rule = createComposeRule()

    private val eventos = mutableListOf<Any>()
    private val acciones = AccionesInicio(
        onNavigate = { eventos += it }, onVerAlerta = { eventos += it },
        onTurno = { eventos += "turno:$it" }, onNuevaVenta = { eventos += "nuevaVenta" },
        onElegirVenta = { eventos += it }, onConfirmarCierre = {}, onCerrarDialogo = {}, onPeriodo = { eventos += it },
        onPago = { eventos += "pago" }, onReintentar = { eventos += "reintentar" },
    )

    private fun mostrar(s: InicioUiState) = rule.setContent { ShalevTheme { InicioContent(s, acciones, zona = ZoneId.of("America/Havana")) } }


    @Test fun bannerYAlertasVisiblesNavegan() {
        mostrar(InicioUiState(banner = "Periodo de prueba restante: 5 días", alertas = listOf(AlertaUi(TipoAlerta.STOCK_CRITICO, 2))))
        rule.onNodeWithTag(InicioTags.BANNER).assertIsDisplayed()
        rule.onNodeWithTag(InicioTags.alerta(TipoAlerta.STOCK_CRITICO)).performClick()
        assertEquals(TipoAlerta.STOCK_CRITICO, eventos.last())
        rule.onNodeWithTag(InicioTags.alerta(TipoAlerta.STOCK_BAJO)).assertDoesNotExist()
    }

    @Test fun sinBannerParaPerpetua() {
        mostrar(InicioUiState(banner = null))
        rule.onNodeWithTag(InicioTags.BANNER).assertDoesNotExist()
    }

    @Test fun top3OcultoSinDatosYVisibleConDatos() {
        val sinTop = ResumenGeneral(listOf(Porcion("Bebidas", 10, 1.0)), emptyList(), Top3.VACIO, 30)
        mostrar(InicioUiState(resumen = EstadoCarga.Exito(sinTop)))
        TipoTop.entries.forEach { rule.onNodeWithTag(InicioTags.top(it)).assertDoesNotExist() }
    }

    @Test fun top3ConDatos() {
        val item = TopItem(1, "Refresco", 5, Cup.ofPesos(500), Cup.ofPesos(200))
        val r = ResumenGeneral(listOf(Porcion("Bebidas", 10, 1.0)), emptyList(), Top3(listOf(item), emptyList(), listOf(item)), 30)
        mostrar(InicioUiState(resumen = EstadoCarga.Exito(r)))
        rule.onNode(hasTestTag(InicioTags.LISTA)).performScrollToNode(hasTestTag(InicioTags.top(TipoTop.MAS_VENDIDO)))
        rule.onNodeWithTag(InicioTags.top(TipoTop.MAS_VENDIDO)).assertIsDisplayed()
        rule.onNodeWithTag(InicioTags.top(TipoTop.LENTO)).assertDoesNotExist()
    }

    @Test fun losAcordeonesEmpiezanCerradosYSeAbrenAlTocar() {
        val item = TopItem(1, "Refresco", 5, Cup.ofPesos(500), Cup.ofPesos(200))
        val r = ResumenGeneral(listOf(Porcion("Bebidas", 10, 1.0)), emptyList(), Top3(listOf(item), emptyList(), emptyList()), 30)
        mostrar(InicioUiState(resumen = EstadoCarga.Exito(r)))
        rule.onNode(hasTestTag(InicioTags.LISTA)).performScrollToNode(hasTestTag(InicioTags.top(TipoTop.MAS_VENDIDO)))
        rule.onNodeWithTag(InicioTags.top(TipoTop.MAS_VENDIDO)).assertIsDisplayed()
        // La cabecera contraída sí resume «Refresco · 5 unidades». La fila completa solo vive dentro.
        rule.onNodeWithText("Refresco").assertDoesNotExist()
        // La etiqueta abarca toda la tarjeta; al abrir crece y su centro ya no es la cabecera pulsable.
        val cabecera = rule.onNode(hasText(TipoTop.MAS_VENDIDO.titulo) and hasClickAction())
        cabecera.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Contraído"))
        cabecera.performClick()
        cabecera.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expandido"))
        rule.onNodeWithText("Refresco").assertIsDisplayed()
        // Son dos decisiones distintas, no un doble toque mecánico (guardia de 600 ms).
        val desde = android.os.SystemClock.elapsedRealtime()
        rule.waitUntil(2_000) {
            android.os.SystemClock.elapsedRealtime() - desde >= cu.shalev.designsystem.component.GuardiaDobleToque.VENTANA_MS
        }
        cabecera.performClick()
        cabecera.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Contraído"))
        rule.waitUntil(2_000) { rule.onAllNodesWithText("Refresco").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun dialogoNuevaVentaOfreceProductosYServicios() {
        mostrar(InicioUiState(dialogo = DialogoInicio.NuevaVenta))
        rule.onNodeWithTag(InicioTags.tipoVenta(TipoVenta.SERVICIO)).performClick()
        assertEquals(TipoVenta.SERVICIO, eventos.last())
    }

    @Test fun turnoCerradoSePuedeAbrir() {
        mostrar(InicioUiState())
        rule.onNodeWithText("Turno cerrado", substring = true).assertIsDisplayed().performClick()
        assertEquals("turno:true", eventos.last())
    }
}
