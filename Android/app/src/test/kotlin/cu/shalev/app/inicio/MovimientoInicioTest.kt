package cu.shalev.app.inicio

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import cu.shalev.designsystem.component.ShalevBarChart
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.TipoAlerta
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class MovimientoInicioTest {
    @get:Rule val rule = createComposeRule()

    @Test fun alertaDesapareceYLaNuevaSigueOperativa() {
        val estado = mutableStateOf(InicioUiState(alertas = listOf(AlertaUi(TipoAlerta.STOCK_BAJO, 2))))
        var elegida: TipoAlerta? = null
        val acciones = AccionesInicio(
            onNavigate = {}, onVerAlerta = { elegida = it }, onTurno = {}, onNuevaVenta = {},
            onElegirVenta = {}, onConfirmarCierre = {}, onCerrarDialogo = {}, onPeriodo = {}, onPago = {}, onReintentar = {},
        )
        rule.setContent { ShalevTheme { InicioContent(estado.value, acciones) } }
        rule.onNodeWithTag(InicioTags.alerta(TipoAlerta.STOCK_BAJO)).assertExists()
        rule.runOnIdle { estado.value = estado.value.copy(alertas = listOf(AlertaUi(TipoAlerta.SIN_EXISTENCIA, 1))) }
        rule.onNodeWithTag(InicioTags.alerta(TipoAlerta.SIN_EXISTENCIA)).performClick()
        rule.runOnIdle { assertEquals(TipoAlerta.SIN_EXISTENCIA, elegida) }
        rule.onNodeWithTag(InicioTags.alerta(TipoAlerta.STOCK_BAJO)).assertDoesNotExist()
        rule.runOnIdle { estado.value = estado.value.copy(alertas = emptyList()) }
        rule.onNodeWithTag(InicioTags.alerta(TipoAlerta.SIN_EXISTENCIA)).assertDoesNotExist()
    }

    @Test fun graficoActualizaDescripcionSinDuplicarSemantica() {
        val datos = mutableStateOf(listOf(1.0, 2.0))
        rule.setContent {
            ShalevTheme {
                ShalevBarChart(values = datos.value, labels = listOf("A", "B"), description = "Ventas ${datos.value.sum()}")
            }
        }
        rule.onNodeWithContentDescription("Ventas 3.0").assertExists()
        rule.runOnIdle { datos.value = listOf(2.0, 3.0) }
        rule.onNodeWithContentDescription("Ventas 5.0").assertExists()
        rule.onNodeWithContentDescription("Ventas 3.0").assertDoesNotExist()
    }
}
