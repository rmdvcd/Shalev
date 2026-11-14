package cu.shalev.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.common.TextosGuardia
import cu.shalev.app.common.rememberSalidaProtegida
import cu.shalev.designsystem.component.BannerTone
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevStatusBanner
import cu.shalev.designsystem.component.ShalevStepper
import cu.shalev.designsystem.component.ShalevStepperTextos
import cu.shalev.designsystem.component.ShalevTab
import cu.shalev.designsystem.component.ShalevTabs
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.ShalevTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** P18: componentes nuevos del design system (pestañas, asistente, banner, guardia de cambios y letra grande). */
@RunWith(AndroidJUnit4::class)
class DisenoP18UiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    // ---------- A11: pestañas ----------

    @Test fun pestanasTienenRolTabYEstadoSeleccionado() {
        rule.setContent {
            var i by remember { mutableIntStateOf(0) }
            ShalevTheme(darkTheme = false) {
                ShalevTabs(listOf(ShalevTab("Insumos", testTag = "t0"), ShalevTab("Elaborados", 3, testTag = "t1")), i, { i = it })
            }
        }
        val esTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        rule.onNodeWithTag("t0").assert(esTab).assertIsSelected()
        rule.onNodeWithTag("t1").assert(esTab).assertIsNotSelected().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText("Elaborados (3)").assertIsDisplayed()
        rule.onNodeWithTag("t1").performClick()
        rule.onNodeWithTag("t1").assertIsSelected()
        rule.onNodeWithTag("t0").assertIsNotSelected()
    }

    // ---------- A08/A09: asistente ----------

    @Test fun asistenteAnunciaPasoYOcultaFlechasQueNoAplican() {
        var siguiente = 0
        rule.setContent {
            ShalevTheme(darkTheme = false) { ShalevStepper(actual = 1, total = 3, titulo = "Revisa tus datos", onSiguiente = { siguiente++ }) }
        }
        rule.onNodeWithContentDescription("Paso 1 de 3: Revisa tus datos").assertExists()
        rule.onNodeWithContentDescription(ShalevStepperTextos.ATRAS).assertDoesNotExist()
        rule.onNodeWithContentDescription(ShalevStepperTextos.SIGUIENTE).performClick()
        assertEquals(1, siguiente)
    }

    // ---------- A10: banner ----------

    @Test fun bannerCriticoMuestraTextoYDetalle() {
        var toques = 0
        rule.setContent {
            ShalevTheme(darkTheme = true) {
                ShalevStatusBanner("Licencia mensual: 1 día restante", BannerTone.Critico, detail = "Vence hoy.", onClick = { toques++ })
            }
        }
        rule.onNodeWithText("Licencia mensual: 1 día restante").assertIsDisplayed().performClick()
        rule.onNodeWithText("Vence hoy.").assertIsDisplayed()
        assertEquals(1, toques)
    }

    // ---------- A03: guardia de cambios ----------

    @Test fun conCambiosAtrasPreguntaYSeguirEditandoNoSale() {
        var salidas = 0
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                val atras = rememberSalidaProtegida(hayCambios = true, onSalir = { salidas++ })
                ShalevPrimaryButton("Atrás", icon = ShalevIcons.Atras, onClick = atras, modifier = Modifier.testTag("atras"))
            }
        }
        rule.onNodeWithTag("atras").performClick()
        rule.onNodeWithText(TextosGuardia.TITULO).assertIsDisplayed()
        // El diálogo no tiene Cancelar en el pie: Atrás lo descarta y conserva la edición.
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithText(TextosGuardia.TITULO).assertDoesNotExist()
        assertEquals(0, salidas)
        // Botón atrás del sistema: también pregunta.
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText(TextosGuardia.TITULO).assertIsDisplayed()
        rule.onNodeWithContentDescription(TextosGuardia.SALIR).performClick()
        assertEquals(1, salidas)
    }

    @Test fun sinCambiosSaleDirectamente() {
        var salidas = 0
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                var cambios by remember { mutableStateOf(false) }
                val atras = rememberSalidaProtegida(hayCambios = cambios, onSalir = { salidas++ })
                ShalevPrimaryButton("Atrás", icon = ShalevIcons.Atras, onClick = atras, modifier = Modifier.testTag("atras"))
            }
        }
        rule.onNodeWithTag("atras").performClick()
        rule.onNodeWithText(TextosGuardia.TITULO).assertDoesNotExist()
        assertEquals(1, salidas)
    }

    // ---------- A01/A02: letra grande (escala 2.0) ----------

    @Test fun conLetraGrandeElBotonCreceYElValorVaDebajoDelTitulo() {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                ShalevTheme(darkTheme = false) {
                    Column(Modifier.width(320.dp)) {
                        ShalevPrimaryButton("Guardar en el teléfono", icon = ShalevIcons.Exportar, onClick = {}, modifier = Modifier.testTag("boton"))
                        ShalevListItem("Refresco de cola de dos litros", value = "1\u202F450 CUP", modifier = Modifier.testTag("fila"))
                    }
                }
            }
        }
        rule.onNodeWithTag("boton").assertHeightIsAtLeast(48.dp)
        rule.onNodeWithContentDescription("Guardar en el teléfono").assertIsDisplayed()
        val titulo = rule.onNodeWithText("Refresco de cola de dos litros", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val valor = rule.onNodeWithText("1\u202F450 CUP", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("El importe debe ir debajo del nombre con letra grande", valor.top >= titulo.bottom)
    }

    // ---------- A16: regla común de validación (al salir del campo o al guardar) ----------

    @Test fun elErrorApareceAlSalirDelCampoEditadoYSeCorrigeAlMomento() {
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                var ci by remember { mutableStateOf("") }
                var otro by remember { mutableStateOf("") }
                Column {
                    ShalevTextField(
                        value = ci, onValueChange = { ci = it }, label = "Carné",
                        isError = ci.length != 11, errorText = "El carné tiene 11 dígitos",
                        validarAlSalir = true, modifier = Modifier.testTag("ci"),
                    )
                    ShalevTextField(value = otro, onValueChange = { otro = it }, label = "Otro", modifier = Modifier.testTag("otro"))
                }
            }
        }
        // Mientras se escribe, sin error.
        rule.onNodeWithTag("ci").performTextInput("850")
        rule.onAllNodesWithText("El carné tiene 11 dígitos").assertCountEquals(0)
        // Al salir del campo, aparece.
        rule.onNodeWithTag("otro").performClick()
        rule.onNodeWithText("El carné tiene 11 dígitos").assertIsDisplayed()
        // Se corrige en tiempo real.
        rule.onNodeWithTag("ci").performTextInput("10112345")
        rule.onAllNodesWithText("El carné tiene 11 dígitos").assertCountEquals(0)
    }

    @Test fun campoNoTocadoSoloMuestraErrorAlForzar() {
        var forzar by mutableStateOf(false)
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                ShalevTextField(
                    value = "", onValueChange = {}, label = "Nombre", isError = true, errorText = "Escribe el nombre",
                    validarAlSalir = true, forzarError = forzar,
                )
            }
        }
        rule.onAllNodesWithText("Escribe el nombre").assertCountEquals(0)
        forzar = true
        rule.onNodeWithText("Escribe el nombre").assertIsDisplayed()
    }
}
