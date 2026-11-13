package cu.shalev.app.inicio

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import cu.shalev.core.money.Cup
import cu.shalev.designsystem.component.BannerTone
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.Porcion
import cu.shalev.domain.model.ResumenGeneral
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.Top3
import cu.shalev.domain.model.TopItem
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 0.31.0 (P1): en Inicio, el turno y, debajo, el selector de período; con el banner de licencia forman el bloque de
 * arriba, que **no se desplaza** con la lista.
 *
 * Antes el `Modifier.weight(1f)` no llegaba a los hijos directos del `Row` (el turno lo ignoraba y el combobox lo
 * aplicaba a un descendiente): el turno medía todo el ancho y el selector quedaba con ancho 0 y sin texto.
 *
 * Además, Inicio se abre siempre por arriba: no restaura la posición guardada ni se desplaza solo cuando los datos
 * llegan después del primer dibujo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class InicioPeriodoTest {
    @get:Rule val rule = createComposeRule()

    private val acciones = AccionesInicio(
        onNavigate = {}, onTurno = {}, onNuevaVenta = {}, onElegirVenta = {}, onConfirmarCierre = {},
        onCerrarDialogo = {}, onPeriodo = {}, onPago = {}, onReintentar = {},
    )

    @Test
    fun selectorDebajoDelTurnoYBloqueFijoAlDesplazar() {
        rule.setContent {
            ShalevTheme {
                InicioContent(
                    InicioUiState(
                        banner = "Licencia mensual: 3 días restantes",
                        bannerTono = BannerTone.Aviso,
                        resumen = EstadoCarga.Exito(resumenDePrueba()),
                    ),
                    acciones, zona = ZoneId.of("America/Havana"),
                )
            }
        }
        val turno = bounds(InicioTags.TURNO)
        val periodo = bounds(InicioTags.PERIODO)
        val banner = bounds(InicioTags.BANNER)
        assertTrue("el selector de período quedó sin ancho: $periodo", periodo.width > 100f)
        assertTrue("el selector va debajo del turno: $periodo / $turno", periodo.top >= turno.bottom)
        assertTrue("el banner de licencia va debajo del selector: $banner / $periodo", banner.top >= periodo.bottom)
        // El bloque de arriba no se mueve al desplazar la lista.
        rule.onNode(hasTestTag(InicioTags.LISTA)).performScrollToNode(hasTestTag(InicioTags.top(TipoTop.MAS_VENDIDO)))
        assertEquals("el turno se movió al desplazar", turno, bounds(InicioTags.TURNO))
        assertEquals("el selector se movió al desplazar", periodo, bounds(InicioTags.PERIODO))
        assertEquals("el banner se movió al desplazar", banner, bounds(InicioTags.BANNER))
    }

    /** 0.31.0 (P1): Inicio se abre siempre por arriba, aunque Android haya guardado la posición anterior. */
    @Test
    fun inicioAbreArribaAunqueSeGuardeLaPosicion() {
        val restaurador = StateRestorationTester(rule)
        restaurador.setContent {
            ShalevTheme {
                InicioContent(
                    // Suficiente contenido para que la prueba necesite desplazar incluso con cambios de medida
                    // de Compose. Ir a un nodo ya visible no prueba que se haya guardado una posición distinta.
                    InicioUiState(
                        alertas = TipoAlerta.entries.map { AlertaUi(it, 2) },
                        resumen = EstadoCarga.Exito(resumenDePrueba()),
                    ),
                    acciones, zona = ZoneId.of("America/Havana"),
                )
            }
        }
        rule.onNode(hasTestTag(InicioTags.LISTA)).performScrollToNode(hasTestTag(InicioTags.top(TipoTop.RENTABILIDAD)))
        assertTrue("la lista no se desplazó: ${desplazamiento()}", desplazamiento() > 0f)
        restaurador.emulateSavedInstanceStateRestore()
        assertEquals("al reabrir, Inicio aparece a media lista", 0f, desplazamiento())
    }

    /**
     * 0.31.0 (P1): los datos llegan después del primer dibujo. Las alertas se insertan por encima de lo que ya se
     * veía; Compose mantiene en su sitio el primer elemento visible y la lista se desplazaba sola (Inicio aparecía
     * a media lista, sin el título de las alertas).
     */
    @Test
    fun alLlegarLasAlertasLaListaSigueArriba() {
        val estado = mutableStateOf(InicioUiState())
        rule.setContent { ShalevTheme { InicioContent(estado.value, acciones, zona = ZoneId.of("America/Havana")) } }
        assertEquals(0f, desplazamiento())
        rule.runOnIdle {
            estado.value = InicioUiState(
                alertas = listOf(AlertaUi(TipoAlerta.STOCK_BAJO, 2), AlertaUi(TipoAlerta.STOCK_CRITICO, 1)),
                resumen = EstadoCarga.Exito(resumenDePrueba()),
            )
        }
        rule.waitForIdle()
        assertEquals("la lista se desplazó sola al llegar las alertas", 0f, desplazamiento())
    }

    private fun resumenDePrueba(): ResumenGeneral {
        val item = TopItem(1, "Refresco", 5, Cup.ofPesos(500), Cup.ofPesos(200))
        return ResumenGeneral(
            listOf(Porcion("Bebidas", 10, 1.0)), emptyList(), Top3(listOf(item), emptyList(), listOf(item)), 30,
        )
    }

    /** Posición de la lista en el eje de desplazamiento: 0 = arriba del todo. */
    private fun desplazamiento() = rule.onNodeWithTag(InicioTags.LISTA)
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}
