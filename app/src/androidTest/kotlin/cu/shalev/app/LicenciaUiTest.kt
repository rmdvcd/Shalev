package cu.shalev.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.licencia.LicenciaAcciones
import cu.shalev.app.licencia.LicenciaContent
import cu.shalev.app.licencia.LicenciaForm
import cu.shalev.app.licencia.LicenciaTags
import cu.shalev.app.licencia.LicenciaUiState
import cu.shalev.designsystem.component.ShalevStepperTextos
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.Telefono
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pantallas sin estado: no requieren Hilt ni Keystore. */
@RunWith(AndroidJUnit4::class)
class LicenciaUiTest {
    @get:Rule val rule = createComposeRule()

    private val ahora = Instant.parse("2026-09-30T12:00:00Z")
    private val huella = "ab".repeat(32)
    private val vencida = Licencia(LicenseState.Expired(TipoLicencia.MENSUAL), "SPVI:abc", true, huella)
    private val perfil = Perfil("María", "Pérez González", "85010112345", telefonos = listOf(Telefono(1, "52345678")))

    private var solicitudes = 0
    private fun acciones() = LicenciaAcciones(
        onBack = {}, onEditar = {}, onAbrirEdicion = {}, onCerrarEdicion = {},
        onSolicitar = { solicitudes++ }, onActivar = {}, onPegar = {},
    )

    private fun licencia(state: LicenciaUiState, bloqueada: Boolean = false) = rule.setContent {
        ShalevTheme(darkTheme = false) { LicenciaContent(state, ahora, bloqueada, acciones()) }
    }

    private fun siguiente() = rule.onNodeWithContentDescription(ShalevStepperTextos.SIGUIENTE).performScrollTo().performClick()

    // ---------- Bloqueo ----------

    @Test fun bloqueoAbreLicenciaConMotivoYSoporte() {
        var soporte = 0
        val acc = LicenciaAcciones(
            onBack = null, onEditar = {}, onAbrirEdicion = {}, onCerrarEdicion = {},
            onSolicitar = {}, onActivar = {}, onPegar = {}, onSoporte = { soporte++ },
        )
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                LicenciaContent(LicenciaUiState(vencida, LicenciaForm().desdePerfil(perfil), perfilVacio = false), ahora, true, acc)
            }
        }
        rule.onNodeWithTag(LicenciaTags.AVISO_BLOQUEO).assertIsDisplayed()
        rule.onNodeWithText("Licencia Mensual vencida").assertIsDisplayed()
        rule.onNodeWithTag(LicenciaTags.SOPORTE).performScrollTo().performClick()
        assertEquals(1, soporte)
    }

    // ---------- Panel de Licencia ----------

    @Test fun panelEnBloqueoMuestraAviso() {
        licencia(LicenciaUiState(vencida, LicenciaForm().desdePerfil(perfil), perfilVacio = false), bloqueada = true)
        rule.onNodeWithTag(LicenciaTags.AVISO_BLOQUEO).assertIsDisplayed()
    }

    @Test fun perfilVacioMuestraFormulario() {
        licencia(LicenciaUiState(vencida, LicenciaForm().desdePerfil(Perfil()), perfilVacio = true))
        rule.onNodeWithTag(LicenciaTags.FORM_DATOS).performScrollTo().assertIsDisplayed()
    }

    @Test fun perfilCompletoNoRepideLosDatosYMuestraResumenAlVolver() {
        licencia(LicenciaUiState(vencida, LicenciaForm().desdePerfil(perfil), listOf("52345678"), perfilVacio = false))
        // Los datos ya están en el Perfil: el asistente entra directo en «Pide la licencia», sin re-pedirlos.
        rule.onNodeWithContentDescription("Paso 2 de 3: Pide la licencia").assertExists()
        rule.onNodeWithText("Solicitar renovación").assertExists()
        rule.onNodeWithContentDescription(ShalevStepperTextos.ATRAS).performScrollTo().performClick()
        rule.onNodeWithTag(LicenciaTags.RESUMEN_DATOS).performScrollTo().assertIsDisplayed()
    }

    @Test fun buildSinClaveDelEmisorRemiteAlDesarrolladorYDeshabilitaSolicitar() {
        val sinClave = vencida.copy(huellaEmisor = null, puedeSolicitar = false)
        licencia(LicenciaUiState(sinClave, LicenciaForm().desdePerfil(perfil), perfilVacio = false))
        rule.onNodeWithText("Esta versión no puede pedir licencias").assertIsDisplayed()
        // Con el Perfil completo ya se está en «Pide la licencia»: el botón está ahí, deshabilitado.
        rule.onNodeWithTag(LicenciaTags.BOTON_SOLICITAR).performScrollTo().assertIsNotEnabled()
    }

    @Test fun buildSinValidacionSeDistingueDeFaltaDeClave() {
        licencia(LicenciaUiState(vencida.copy(validacionDisponible = false), LicenciaForm().desdePerfil(perfil), perfilVacio = false))
        rule.onNodeWithText("Esta versión no puede validar licencias").assertIsDisplayed()
        rule.onNodeWithText("Esta versión no puede pedir licencias").assertDoesNotExist()
    }

    @Test fun solicitarInvocaLaAccion() {
        licencia(LicenciaUiState(vencida, LicenciaForm().desdePerfil(perfil), perfilVacio = false))
        // Arranque directo en «Pide la licencia» (los datos ya vienen del Perfil).
        rule.onNodeWithTag(LicenciaTags.BOTON_SOLICITAR).performScrollTo().assertIsEnabled().performClick()
        assertTrue(solicitudes == 1)
    }

    // ---------- P18 (A08): asistente en 3 pasos ----------

    @Test fun asistenteAnunciaElPasoYAvanzaHastaActivar() {
        licencia(LicenciaUiState(vencida, LicenciaForm().desdePerfil(perfil), perfilVacio = false))
        // Con los datos ya dados se entra en el paso 2; Atrás permite revisarlos (paso 1).
        rule.onNodeWithContentDescription("Paso 2 de 3: Pide la licencia").assertExists()
        rule.onNodeWithTag(LicenciaTags.BOTON_ACTIVAR).assertDoesNotExist()
        rule.onNodeWithContentDescription(ShalevStepperTextos.ATRAS).performScrollTo().performClick()
        rule.onNodeWithContentDescription("Paso 1 de 3: Revisa tus datos").assertExists()
        siguiente()
        rule.onNodeWithContentDescription("Paso 2 de 3: Pide la licencia").assertExists()
        siguiente()
        rule.onNodeWithContentDescription("Paso 3 de 3: Activa la licencia").assertExists()
        rule.onNodeWithTag(LicenciaTags.BOTON_ACTIVAR).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription(ShalevStepperTextos.SIGUIENTE).assertDoesNotExist()
        rule.onNodeWithContentDescription(ShalevStepperTextos.ATRAS).performClick()
        rule.onNodeWithContentDescription("Paso 2 de 3: Pide la licencia").assertExists()
    }

    @Test fun datosInvalidosNoAvanzanYMarcanErrores() {
        var editado: LicenciaForm? = null
        val form = LicenciaForm().desdePerfil(Perfil())
        val acc = LicenciaAcciones(
            onBack = {}, onEditar = { editado = it(form) }, onAbrirEdicion = {}, onCerrarEdicion = {},
            onSolicitar = {}, onActivar = {}, onPegar = {},
        )
        rule.setContent { ShalevTheme(darkTheme = false) { LicenciaContent(LicenciaUiState(vencida, form, perfilVacio = true), ahora, false, acc) } }
        siguiente()
        rule.onNodeWithContentDescription("Paso 1 de 3: Revisa tus datos").assertExists()
        assertTrue(editado?.mostrarErrores == true)
    }

    // ---------- Paneles informativos (acordeones) ----------

    @Test fun preciosEsUnPanelColapsable() {
        licencia(LicenciaUiState(vencida, LicenciaForm().desdePerfil(Perfil()), perfilVacio = true))
        // Los precios son información de referencia: el panel empieza cerrado y se abre tocando su título.
        rule.onNodeWithTag(LicenciaTags.PRECIOS).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(LicenciaTags.precio(TipoLicencia.MENSUAL)).assertDoesNotExist()
        rule.onNodeWithText("Precios de la licencia").performScrollTo().performClick()
        rule.onNodeWithTag(LicenciaTags.precio(TipoLicencia.MENSUAL)).performScrollTo().assertIsDisplayed()
    }

    /** 0.31.0 (P4): el paso «Activa la licencia» se alcanza con «Paso siguiente», sin botón de llave. */
    @Test fun elPasoSiguienteLlevaAActivar() {
        licencia(LicenciaUiState(vencida, LicenciaForm().desdePerfil(perfil), perfilVacio = false))
        rule.onNodeWithContentDescription(ShalevStepperTextos.ATRAS).performScrollTo().performClick() // a «Revisa tus datos»
        siguiente()
        siguiente()
        rule.onNodeWithTag(LicenciaTags.BOTON_ACTIVAR).performScrollTo().assertIsDisplayed()
    }

}
