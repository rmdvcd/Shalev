package cu.shalev.app

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.app.ayuda.AyudaContenido
import cu.shalev.app.ayuda.AyudaScreen
import cu.shalev.app.ayuda.AyudaTags
import cu.shalev.app.pagos.AccionesListaPago
import cu.shalev.app.pagos.PagoElectronicoContent
import cu.shalev.app.pagos.PagoTags
import cu.shalev.app.pagos.PagoUiState
import cu.shalev.app.pagos.TipoCuentaPago
import cu.shalev.app.perfil.AccionesPerfil
import cu.shalev.app.perfil.DatosPerfilForm
import cu.shalev.app.perfil.PerfilContent
import cu.shalev.app.perfil.PerfilTags
import cu.shalev.app.perfil.PerfilUiState
import cu.shalev.app.perfil.TextosPerfil
import cu.shalev.app.respaldo.AccionesRespaldo
import cu.shalev.app.respaldo.ExportForm
import cu.shalev.app.respaldo.ImportForm
import cu.shalev.app.respaldo.PasosRespaldo
import cu.shalev.app.respaldo.Progreso
import cu.shalev.app.respaldo.RespaldoContent
import cu.shalev.app.respaldo.RespaldoTags
import cu.shalev.app.respaldo.RespaldoUiState
import cu.shalev.app.respaldo.ResultadoRespaldo
import cu.shalev.app.respaldo.TextosRespaldo
import cu.shalev.app.soporte.SoporteContent
import cu.shalev.app.soporte.SoporteInfo
import cu.shalev.app.soporte.SoporteTags
import cu.shalev.designsystem.component.ShalevStepperTextos
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.repository.InfoRespaldo
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Contenidos sin estado de Ajustes (Prompt 12): no requieren Hilt. */
@RunWith(AndroidJUnit4::class)
class AjustesPantallasUiTest {
    @get:Rule val rule = createComposeRule()

    // ---------- Perfil

    private val perfil = Perfil(
        "Ana", "Díaz", "85010112345",
        tarjetas = listOf(TarjetaBancaria(3, "9205000000001234")), telefonos = listOf(Telefono(4, "+5352345678")),
    )
    private var elegidas = mutableListOf<Pair<TipoCuentaPago, Long>>()
    private val accionesLista = AccionesListaPago(
        onElegir = { t, id -> elegidas += t to id }, onNuevo = {}, onEditar = { _, _ -> }, onBorrar = { _, _ -> },
        onCambiarEdicion = {}, onGuardarEdicion = {}, onCerrarEdicion = {}, onConfirmarBorrado = {}, onCancelarBorrado = {},
    )

    /** P28: la lista de Pago electrónico (única) muestra la tarjeta enmascarada y tocarla la deja en uso. */
    @Test fun pagoElectronicoTocarEligeLaTarjeta() {
        rule.setContent { ShalevTheme(darkTheme = false) { PagoElectronicoContent(PagoUiState(true, perfil), {}, accionesLista) } }
        rule.onNodeWithText("•••• 1234").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(PagoTags.opcion(TipoCuentaPago.TARJETA, 3)).performClick()
        assertEquals(listOf(TipoCuentaPago.TARJETA to 3L), elegidas)
    }

    /** P28: Perfil solo tiene nombre, apellidos y carné; las tarjetas viven en Pago electrónico. */
    @Test fun perfilMuestraSoloDatosPersonales() {
        var guardar = 0
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                PerfilContent(PerfilUiState(true, perfil, DatosPerfilForm.desde(perfil)), {}, AccionesPerfil({}, { guardar++ }, {}))
            }
        }
        rule.onNodeWithTag(PerfilTags.GUARDAR).assertIsNotEnabled() // sin cambios
        rule.onNodeWithText("•••• 1234").assertDoesNotExist()
        assertEquals(0, guardar)
    }

    @Test fun perfilConCambiosHabilitaGuardar() {
        val form = DatosPerfilForm.desde(perfil).copy(nombre = "Ana María")
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                PerfilContent(PerfilUiState(true, perfil, form), {}, AccionesPerfil({}, {}, {}))
            }
        }
        rule.onNodeWithTag(PerfilTags.GUARDAR).assertIsEnabled()
    }

    @Test fun perfilMuestraErrorDelNombreMientrasSeEdita() {
        rule.setContent {
            var form by remember { mutableStateOf(DatosPerfilForm.desde(perfil)) }
            ShalevTheme(darkTheme = false) {
                PerfilContent(
                    PerfilUiState(true, perfil, form), {},
                    AccionesPerfil({ actualizar -> form = actualizar(form) }, {}, {}),
                )
            }
        }
        rule.onNodeWithTag(PerfilTags.NOMBRE).performTextReplacement("12345!!!")
        rule.onNodeWithText(TextosPerfil.ERROR_NOMBRE).performScrollTo().assertIsDisplayed()
    }

    // ---------- Respaldo

    private fun accionesRespaldo(onGuardar: () -> Unit = {}, onImportar: () -> Unit = {}) = AccionesRespaldo(
        onEditarExport = {}, onGuardar = onGuardar, onElegirArchivo = onImportar,
        onContrasenaImport = {}, onConfirmarImport = {}, onCancelarImport = {},
    )

    @Test fun respaldoProtegePorDefectoYApagarLaClaveEsExplicito() {
        rule.setContent {
            var state by remember { mutableStateOf(RespaldoUiState()) }
            ShalevTheme(darkTheme = false) {
                RespaldoContent(state, {}, AccionesRespaldo(
                    onEditarExport = { editar -> state = state.copy(export = editar(state.export)) },
                    onGuardar = {}, onElegirArchivo = {}, onContrasenaImport = {}, onConfirmarImport = {}, onCancelarImport = {},
                ))
            }
        }
        rule.onNodeWithTag(RespaldoTags.CONTRASENA).assertExists()
        rule.onNodeWithTag(RespaldoTags.REPETIR).assertExists()
        rule.onNodeWithTag(RespaldoTags.SIN_CONTRASENA).assertDoesNotExist()
        rule.onNodeWithTag(RespaldoTags.PROTEGER).performScrollTo().performClick()
        rule.onNodeWithTag(RespaldoTags.CONTRASENA).assertDoesNotExist()
        rule.onNodeWithTag(RespaldoTags.REPETIR).assertDoesNotExist()
        rule.onNodeWithTag(RespaldoTags.SIN_CONTRASENA).performScrollTo().assertIsDisplayed()
    }

    @Test fun respaldoMuestraErroresDeContrasena() {
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                RespaldoContent(RespaldoUiState(export = ExportForm(true, "corta", "", mostrarErrores = true)), {}, accionesRespaldo())
            }
        }
        rule.onNodeWithText(TextosRespaldo.ERROR_CONTRASENA).assertIsDisplayed()
    }

    @Test fun respaldoBotones() {
        var guardar = 0; var importar = 0
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                RespaldoContent(RespaldoUiState(ExportForm(true, "clave-segura", "clave-segura")), {}, accionesRespaldo({ guardar++ }, { importar++ }))
            }
        }
        // P18 (A09): Guardar está en el paso 2 del asistente.
        rule.onNodeWithTag(RespaldoTags.GUARDAR).assertDoesNotExist()
        rule.onNodeWithContentDescription(ShalevStepperTextos.SIGUIENTE).performScrollTo().performClick()
        rule.onNodeWithTag(RespaldoTags.GUARDAR).performScrollTo().performClick()
        rule.onNodeWithTag(RespaldoTags.IMPORTAR).performScrollTo().performClick()
        assertEquals(1, guardar); assertEquals(1, importar)
    }

    @Test fun respaldoProgresoDeshabilitaYResultadoSeCierra() {
        var cerrar = 0
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                RespaldoContent(
                    RespaldoUiState(
                        progreso = Progreso(2, 3, "Cifrando con tu contraseña…"),
                        resultado = ResultadoRespaldo(true, "Respaldo guardado", "Shalev_respaldo_2026-09-30.shalev"),
                    ),
                    {}, AccionesRespaldo(
                        onEditarExport = {}, onGuardar = {}, onElegirArchivo = {},
                        onContrasenaImport = {}, onConfirmarImport = {}, onCancelarImport = {}, onCerrarResultado = { cerrar++ },
                    ),
                )
            }
        }
        rule.onNodeWithText("Etapa 2 de 3 · Cifrando con tu contraseña…").assertExists()
        rule.onNodeWithText("Respaldo guardado").assertIsDisplayed()
        rule.onNodeWithContentDescription(TextosRespaldo.ACEPTAR).performClick()
        assertEquals(1, cerrar)
    }

    @Test fun respaldoImportarMuestraQueArchivoEsYQueSeReemplaza() {
        val info = InfoRespaldo(3, Instant.parse("2026-09-29T15:00:00Z"), 7)
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                RespaldoContent(
                    RespaldoUiState(importacion = ImportForm("content://r", info, nombre = "Shalev_respaldo_2026-09-29.shalev")),
                    {}, accionesRespaldo(), zona = ZoneId.of("America/Havana"),
                )
            }
        }
        rule.onNodeWithTag(RespaldoTags.ARCHIVO).assertTextContains("Respaldo del 29/09/2026", substring = true)
        rule.onNodeWithText(TextosRespaldo.AVISO_IMPORTAR).assertIsDisplayed()
        rule.onNodeWithTag(RespaldoTags.CONTRASENA_IMPORTAR).assertIsDisplayed()
    }

    @Test fun respaldoPaso3HechoYHacerOtro() {
        var otro = 0
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                RespaldoContent(
                    RespaldoUiState(exportado = PasosRespaldo.textoHecho("Shalev_respaldo_2026-09-30.shalev", enviado = false)),
                    {}, AccionesRespaldo(
                        onEditarExport = {}, onGuardar = {}, onElegirArchivo = {},
                        onContrasenaImport = {}, onConfirmarImport = {}, onCancelarImport = {}, onNuevoRespaldo = { otro++ },
                    ),
                )
            }
        }
        rule.onNodeWithContentDescription("Paso 3 de 3: Respaldo listo").assertExists()
        rule.onNodeWithContentDescription("Paso 1 de 4: Elige el archivo .shalev").assertExists()
        rule.onNodeWithTag(RespaldoTags.HECHO).assertTextContains("Shalev_respaldo_2026-09-30.shalev", substring = true)
        rule.onNodeWithTag(RespaldoTags.OTRO_RESPALDO).performScrollTo().performClick()
        assertEquals(1, otro)
    }

    /** 0.26.0 (§1): el respaldo solo se exporta como .shalev; ya no hay «Documento PDF». */
    @Test fun respaldoSinDocumentoPdf() {
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                RespaldoContent(
                    RespaldoUiState(), {}, AccionesRespaldo(
                        onEditarExport = {}, onGuardar = {}, onElegirArchivo = {},
                        onContrasenaImport = {}, onConfirmarImport = {}, onCancelarImport = {},
                    ),
                )
            }
        }
        rule.onAllNodesWithText("Documento PDF").assertCountEquals(0)
    }

    // ---------- Ayuda y Soporte ----------

    @Test fun ayudaDespliegaUnTema() {
        rule.setContent { ShalevTheme(darkTheme = false) { AyudaScreen(onBack = {}) } }
        val tema = AyudaContenido.temasPara(cu.shalev.domain.model.Modulo.TODOS)[2]
        rule.onNodeWithTag(AyudaTags.tema(2)).performScrollTo().performClick()
        rule.onNodeWithText("1. ${tema.pasos[0]}").assertIsDisplayed()
    }

    @Test fun ayudaContextualSinTemasMuestraOrientacion() {
        rule.setContent {
            ShalevTheme(darkTheme = false) {
                cu.shalev.app.ayuda.DialogoAyudaPestana(
                    pestana = cu.shalev.app.ayuda.PestanaAyuda.INVENTARIO,
                    modulos = setOf(cu.shalev.domain.model.Modulo.SERVICIOS),
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithText("Consulta el manual de usuario en Ajustes.").assertIsDisplayed()
    }

    @Test fun soporteMuestraFichaYContacto() {
        var wa = 0
        rule.setContent { ShalevTheme(darkTheme = false) { SoporteContent(onBack = {}, version = "0.12.0 (12)", onWhatsapp = { wa++ }, onSms = {}) } }
        rule.onNodeWithTag(SoporteTags.FICHA).assertIsDisplayed()
        // Las filas seleccionables contienen «Etiqueta: valor», no dos nodos de texto separados.
        SoporteInfo.filas.forEach { (etiqueta, valor) ->
            rule.onNodeWithText("$etiqueta: $valor").performScrollTo().assertIsDisplayed()
        }
        rule.onNodeWithContentDescription("Escribir por WhatsApp").performScrollTo().performClick()
        assertEquals(1, wa)
    }
}
