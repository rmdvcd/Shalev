package cu.shalev.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.ajustes.AjustesContent
import cu.shalev.app.ajustes.AjustesTags
import cu.shalev.app.ajustes.AjustesUiState
import cu.shalev.app.navigation.Route
import cu.shalev.app.onboarding.ModoWizard
import cu.shalev.app.onboarding.NivelesForm
import cu.shalev.app.onboarding.OnboardingAcciones
import cu.shalev.app.onboarding.OnboardingContent
import cu.shalev.app.onboarding.OnboardingTags
import cu.shalev.app.onboarding.OnboardingUiState
import cu.shalev.app.onboarding.PasoWizard
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.model.ResumenConfiguracion
import cu.shalev.licencia.LicenseState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pantallas sin estado del asistente, el escáner y Ajustes (sin Hilt). */
@RunWith(AndroidJUnit4::class)
class OnboardingUiTest {
    @get:Rule val rule = createComposeRule()

    private val plan = listOf(PasoWizard.BIENVENIDA, PasoWizard.DATOS, PasoWizard.ALERTAS, PasoWizard.PRUEBA)
    private val lic = Licencia(LicenseState.Trial(7), "SPVI:x", false, null)
    private val clics = mutableListOf<String>()

    private fun acciones() = OnboardingAcciones(
        onSiguiente = { clics += "siguiente" }, onAtras = { clics += "atras" },
        onOmitirPaso = { clics += "omitirPaso" }, onOmitirTodo = { clics += "omitirTodo" },
        onDatos = {}, onNivel = { _, _ -> }, onSumarNivel = { _, _ -> clics += "sumar" }, onRestablecerNiveles = {},
    )

    private fun wizard(indice: Int, modo: ModoWizard = ModoWizard.PRIMERA_VEZ, niveles: NivelesForm = NivelesForm(), errores: Boolean = false) =
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                OnboardingContent(
                    OnboardingUiState(cargando = false, modo = modo, pasos = plan, indice = indice, licencia = lic, niveles = niveles, mostrarErroresNiveles = errores),
                    acciones(),
                )
            }
        }

    @Test fun bienvenidaPermiteSaltarTodo() {
        wizard(0)
        rule.onNodeWithTag(OnboardingTags.paso(PasoWizard.BIENVENIDA)).assertIsDisplayed()
        rule.onNodeWithText("Bienvenido a Shalev").assertIsDisplayed()
        rule.onNodeWithTag(OnboardingTags.OMITIR_TODO).performClick()
        assertEquals(listOf("omitirTodo"), clics)
    }

    @Test fun progresoYOmitirPaso() {
        wizard(1)
        rule.onNodeWithContentDescription("Paso 1 de 3", substring = true).assertExists() // P18 (L9): ShalevStepper
        rule.onNodeWithTag(OnboardingTags.OMITIR_PASO).performClick()
        rule.onNodeWithContentDescription("Atrás").performClick()
        assertEquals(listOf("omitirPaso", "atras"), clics)
    }

    @Test fun alertasMuestranValoresRecomendadosYBotonesAccesibles() {
        wizard(2)
        rule.onNodeWithTag(OnboardingTags.paso(PasoWizard.ALERTAS)).assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Sumar 1 a Bajo").assertCountEquals(2) // producto e insumo
        rule.onAllNodesWithContentDescription("Sumar 1 a Bajo")[0].performClick()
        rule.onNodeWithTag(OnboardingTags.SIGUIENTE).performClick()
        assertEquals(listOf("sumar", "siguiente"), clics)
    }

    @Test fun alertasInvalidasMuestranElMotivo() {
        wizard(2, niveles = NivelesForm(productoBajo = "1", productoCritico = "3"), errores = true)
        rule.onAllNodesWithText(NivelesForm.CRITICO_MAYOR).onFirst().assertExists()
    }

    @Test fun pruebaExplicaDiasYQueNoSePierdenDatos() {
        wizard(3)
        rule.onAllNodesWithText("Tienes 7 días de prueba gratis").onFirst().assertIsDisplayed()
        rule.onNodeWithText("Tus datos NO se borran", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(OnboardingTags.SIGUIENTE).assertContentDescriptionEquals("Empezar a usar Shalev")
    }

    // ---------- Ajustes ----------

    @Test fun ajustesMuestraPendientesYAbreElAsistente() {
        var destino: Route? = null
        val resumen = ResumenConfiguracion(PasoConfiguracion.entries.toList(), listOf(PasoConfiguracion.ALERTAS, PasoConfiguracion.PRUEBA))
        rule.setContent {
            ShalevTheme(darkTheme = false) { AjustesContent(AjustesUiState(resumen), { destino = it }, {}) }
        }
        rule.onNodeWithText("Faltan 2 pasos").assertIsDisplayed()
        rule.onNodeWithTag(AjustesTags.CONFIGURACION).performClick()
        assertEquals(Route.ConfiguracionInicial(), destino)
    }
}
