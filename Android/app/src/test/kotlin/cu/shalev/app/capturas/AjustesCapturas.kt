package cu.shalev.app.capturas

import androidx.compose.foundation.layout.padding
import android.app.Application
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cu.shalev.app.ajustes.AjustesContent
import cu.shalev.app.ajustes.AjustesUiState
import cu.shalev.app.ayuda.AyudaScreen
import cu.shalev.app.licencia.LicenciaAcciones
import cu.shalev.app.licencia.LicenciaContent
import cu.shalev.app.licencia.LicenciaForm
import cu.shalev.app.licencia.LicenciaUiState
import cu.shalev.app.onboarding.ModoWizard
import cu.shalev.app.onboarding.NivelesForm
import cu.shalev.app.onboarding.OnboardingAcciones
import cu.shalev.app.onboarding.OnboardingContent
import cu.shalev.app.onboarding.OnboardingUiState
import cu.shalev.app.onboarding.PasoWizard
import cu.shalev.app.perfil.AccionesPerfil
import cu.shalev.app.perfil.DatosPerfilForm
import cu.shalev.app.perfil.PerfilContent
import cu.shalev.app.perfil.PerfilUiState
import cu.shalev.app.perfil.TextosPerfil
import cu.shalev.app.respaldo.AccionesRespaldo
import cu.shalev.app.respaldo.EtapasRespaldo
import cu.shalev.app.respaldo.ExportForm
import cu.shalev.app.respaldo.ImportForm
import cu.shalev.app.respaldo.PasosRespaldo
import cu.shalev.app.respaldo.Progreso
import cu.shalev.app.respaldo.RespaldoContent
import cu.shalev.app.respaldo.RespaldoUiState
import cu.shalev.app.respaldo.ResultadoRespaldo
import cu.shalev.app.respaldo.TextosRespaldo
import cu.shalev.app.soporte.SoporteContent
import cu.shalev.designsystem.component.ShalevStepperTextos
import cu.shalev.domain.model.DatosIniciales
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.ResumenConfiguracion
import cu.shalev.domain.model.TarjetaBancaria
import cu.shalev.domain.model.Telefono
import cu.shalev.domain.repository.InfoRespaldo
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ajustes, Perfil, Licencia (y bloqueo), Respaldo, Ayuda, Soporte y el asistente de configuración. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = Captura.TELEFONO, application = Application::class)
class AjustesCapturas {
    @get:Rule val rule = createComposeRule()

    private val perfil = Perfil(
        "María", "González Pérez", "85010112345",
        tarjetas = listOf(TarjetaBancaria(3, "9205129970876454")), telefonos = listOf(Telefono(1, "52345678")),
        pagoTarjetaId = 3, pagoTelefonoId = 1,
    )

    // ---------- Ajustes ----------
    private fun ajustes(id: String, s: AjustesUiState) = rule.capturar(id) { AjustesContent(s, {}, {}) }
    @Test fun ajustesCompleto() = ajustes(
        "07a2_ajustes_configuracion_completa",
        AjustesUiState(ResumenConfiguracion(PasoConfiguracion.entries.toList(), emptyList())),
    )
    @Test fun ajustesPendientes() = ajustes(
        "07a_ajustes",
        AjustesUiState(ResumenConfiguracion(PasoConfiguracion.entries.toList(), listOf(PasoConfiguracion.ALERTAS, PasoConfiguracion.PRUEBA))),
    )

    // ---------- Perfil ----------
    private fun perfilCap(id: String, form: DatosPerfilForm) = rule.capturar(id) {
        PerfilContent(PerfilUiState(true, perfil, form), {}, AccionesPerfil({}, {}, {}))
    }
    @Test @Config(qualifiers = Captura.LARGA) fun perfil() = perfilCap("07c_perfil", DatosPerfilForm.desde(perfil))
    @Test fun perfilCambios() = perfilCap("07d2_perfil_con_cambios", DatosPerfilForm.desde(perfil).copy(nombre = "María José"))
    @Test fun perfilErrores() = perfilCap("07d_perfil_errores", DatosPerfilForm.desde(perfil).copy(nombre = "M", ci = "123", mostrarErrores = true))

    @Test fun perfilDialogoSalir() = rule.capturar("07e_perfil_dialogo_salir", antes = { onNodeWithContentDescription("Atrás").performClick() }) {
        PerfilContent(PerfilUiState(true, perfil, DatosPerfilForm.desde(perfil).copy(nombre = "María José")), {}, AccionesPerfil({}, {}, {}))
    }
    @Test fun perfilSnackbar() = rule.capturar("07f_perfil_snackbar") {
        PerfilContent(PerfilUiState(true, perfil, DatosPerfilForm.desde(perfil)), {}, AccionesPerfil({}, {}, {}), snackbarCon(TextosPerfil.GUARDADO))
    }

    // ---------- Licencia ----------
    private val ahora = Captura.AHORA
    private val huella = "ab".repeat(32)
    private fun lic(estado: LicenseState) = Licencia(estado, "SPVI:7F3A-92C1-D04E-11B8", true, huella)
    private val acciones = LicenciaAcciones(onBack = {}, onEditar = {}, onAbrirEdicion = {}, onCerrarEdicion = {}, onSolicitar = {}, onActivar = {}, onPegar = {})
    private val siguiente: ComposeContentTestRule.() -> Unit =
        { onNodeWithContentDescription(ShalevStepperTextos.SIGUIENTE).performScrollTo().performClick(); waitForIdle() }
    private fun licencia(id: String, s: LicenciaUiState, bloqueada: Boolean = false, pasos: Int = 0) = rule.capturar(id, antes = { repeat(pasos) { siguiente() } }) {
        LicenciaContent(
            s, ahora, bloqueada,
            if (bloqueada) LicenciaAcciones(onBack = null, onEditar = {}, onAbrirEdicion = {}, onCerrarEdicion = {}, onSolicitar = {}, onActivar = {}, onPegar = {}, onSoporte = {}) else acciones,
        )
    }
    private val formOk = LicenciaForm().desdePerfil(perfil)
    /** 0.27.0 (T2). */
    @Test fun licenciaLetraGrande() = rule.capturar("07z_licencia_letra_200") {
        LetraGrande { LicenciaContent(LicenciaUiState(lic(LicenseState.Trial(5)), formOk, listOf("52345678"), perfilVacio = false), ahora, false, acciones) }
    }
    @Test fun cajaArqueoLetraGrande() = rule.capturar("07z2_caja_arqueo_letra_200") {
        LetraGrande {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(cu.shalev.designsystem.token.ShalevSpacing.md)) {
                cu.shalev.designsystem.component.ShalevCard {
                    cu.shalev.app.caja.TablaArqueo(
                        cu.shalev.domain.model.Arqueo(
                            cu.shalev.core.money.Cup.ofPesos(500), cu.shalev.core.money.Cup.ofPesos(12_350), cu.shalev.core.money.Cup.ofPesos(200),
                            cu.shalev.core.money.Cup.ofPesos(150), cu.shalev.core.money.Cup.ofPesos(12_850),
                        ),
                    )
                }
            }
        }
    }
    @Test fun licenciaPrueba() = licencia("07i_licencia_prueba_paso1", LicenciaUiState(lic(LicenseState.Trial(5)), formOk, listOf("52345678"), perfilVacio = false))
    @Test fun licenciaPerfilVacio() = licencia("07j2_licencia_paso1_perfil_vacio", LicenciaUiState(lic(LicenseState.Trial(5)), LicenciaForm().desdePerfil(Perfil()), perfilVacio = true))
    @Test @Config(qualifiers = Captura.LARGA) fun licenciaPaso2() = licencia("07k_licencia_paso2_solicitar", LicenciaUiState(lic(LicenseState.Trial(5)), formOk, listOf("52345678"), perfilVacio = false), pasos = 1)
    @Test fun licenciaPaso3() = licencia("07l_licencia_paso3_activar", LicenciaUiState(lic(LicenseState.Trial(5)), formOk, listOf("52345678"), perfilVacio = false), pasos = 2)
    @Test fun licenciaErrores() = licencia(
        "07j_licencia_paso1_errores",
        LicenciaUiState(lic(LicenseState.Trial(5)), LicenciaForm(nombre = "M", apellidos = "González Pérez", ci = "8501", telefono = "5234", mostrarErrores = true), perfilVacio = true),
    )
    @Test fun licenciaActiva() = licencia(
        "07n_licencia_activa",
        LicenciaUiState(lic(LicenseState.Active(TipoLicencia.ANUAL, Instant.parse("2027-09-30T00:00:00Z"), "L-2026-0042")), formOk, perfilVacio = false),
    )
    @Test fun licenciaPerpetua() = licencia("07n2_licencia_perpetua", LicenciaUiState(lic(LicenseState.Perpetual("L-2026-0043")), formOk, perfilVacio = false))
    @Test fun licenciaVencidaBloqueo() = licencia("07o_licencia_vencida_bloqueo", LicenciaUiState(lic(LicenseState.Expired(TipoLicencia.MENSUAL)), formOk, perfilVacio = false), bloqueada = true)
    @Test fun licenciaPruebaTerminada() = licencia("07o2_licencia_prueba_terminada_bloqueo", LicenciaUiState(lic(LicenseState.TrialExpired), formOk, perfilVacio = false), bloqueada = true)
    @Test fun licenciaFechaIncorrecta() = licencia("07p_licencia_fecha_incorrecta", LicenciaUiState(lic(LicenseState.ClockTampered), formOk, perfilVacio = false), bloqueada = true)
    @Test fun licenciaSinClave() = licencia(
        "07q_licencia_sin_clave_emisor",
        LicenciaUiState(lic(LicenseState.Trial(5)).copy(huellaEmisor = null, puedeSolicitar = false), formOk, perfilVacio = false), pasos = 1,
    )

    // ---------- Respaldo ----------
    private val accionesRespaldo = AccionesRespaldo(
        onEditarExport = {}, onGuardar = {}, onElegirArchivo = {}, onContrasenaImport = {}, onConfirmarImport = {}, onCancelarImport = {},
    )
    private fun respaldo(id: String, s: RespaldoUiState) = rule.capturar(id) { RespaldoContent(s, {}, accionesRespaldo, zona = Captura.ZONA) }
    private val info = InfoRespaldo(3, Instant.parse("2026-09-28T15:00:00Z"), 48_213)
    private val importacion = ImportForm("content://respaldo", info, nombre = "Shalev_respaldo_2026-09-28.shalev")

    @Test @Config(qualifiers = Captura.LARGA) fun respaldoPaso1() = respaldo("09a_respaldo_paso1", RespaldoUiState())
    @Test fun respaldoErrores() = respaldo("09b_respaldo_paso1_errores", RespaldoUiState(export = ExportForm(true, "abc12", "abc", mostrarErrores = true)))
    @Test fun respaldoPaso2() = rule.capturar(
        "09c_respaldo_paso2",
        antes = { onNodeWithContentDescription(ShalevStepperTextos.SIGUIENTE).performScrollTo().performClick() },
    ) { RespaldoContent(RespaldoUiState(ExportForm(true, "clave-segura", "clave-segura")), {}, accionesRespaldo, zona = Captura.ZONA) }
    @Test fun respaldoProgreso() = respaldo("09d_respaldo_progreso", RespaldoUiState(progreso = Progreso(2, 3, "Cifrando con tu contraseña…")))
    @Test fun respaldoListo() = respaldo(
        "09e_respaldo_listo",
        RespaldoUiState(
            exportado = PasosRespaldo.textoHecho("Shalev_respaldo_2026-10-01.shalev", enviado = false),
            resultado = ResultadoRespaldo(true, "Respaldo guardado", "Shalev_respaldo_2026-10-01.shalev\nRespaldo guardado: 86 productos, 12 insumos, 1204 ventas.\nSin la contraseña no se puede abrir: guárdala en un lugar seguro."),
        ),
    )
    @Test fun respaldoImportar() = respaldo("09g_respaldo_dialogo_importar", RespaldoUiState(importacion = importacion))
    @Test fun respaldoImportarError() = respaldo("09h_respaldo_importar_error", RespaldoUiState(importacion = importacion.copy(contrasena = "miclave2025", error = "Contraseña incorrecta")))
    @Test fun respaldoImportando() = respaldo(
        "09i_respaldo_importando",
        RespaldoUiState(importacion = importacion.copy(contrasena = "miclave2026"), progreso = Progreso(2, 3, "Comprobando la contraseña…")),
    )
    @Test fun respaldoComprobando() = respaldo("09f_respaldo_comprobando", RespaldoUiState(progreso = Progreso(1, 3, EtapasRespaldo.COMPROBANDO)))
    @Test fun respaldoImportado() = respaldo(
        "09j_respaldo_importado", RespaldoUiState(resultado = ResultadoRespaldo(true, "Respaldo importado", "Respaldo importado: 86 productos, 12 insumos, 1204 ventas.")),
    )
    @Test fun respaldoVersionNueva() = respaldo(
        "09l_respaldo_version_nueva",
        RespaldoUiState(resultado = ResultadoRespaldo(false, "Respaldo de una versión más nueva", "Se creó con una versión de Shalev más reciente. Actualiza la app e inténtalo de nuevo. Tus datos no se han tocado.")),
    )
    @Test fun respaldoIncompleto() = respaldo(
        "09k_respaldo_incompleto",
        RespaldoUiState(resultado = ResultadoRespaldo(false, "El archivo está incompleto", "Se cortó al descargarlo o enviarlo (por ejemplo, un envío por Bluetooth interrumpido). Pide que te lo envíen de nuevo o vuelve a copiarlo. Tus datos no se han tocado.")),
    )

    // ---------- Ayuda y Soporte ----------
    @Test fun ayuda() = rule.capturar("07g_ayuda") { AyudaScreen(onBack = {}) }
    @Test fun soporte() = rule.capturar("07h_soporte") { SoporteContent(onBack = {}, version = "0.27.1 (50)", onWhatsapp = {}, onSms = {}) }

    // ---------- Asistente de configuración ----------
    private val plan = listOf(PasoWizard.BIENVENIDA, PasoWizard.DATOS, PasoWizard.ALERTAS, PasoWizard.PRUEBA)
    private val accionesWizard = OnboardingAcciones(
        onSiguiente = {}, onAtras = {}, onOmitirPaso = {}, onOmitirTodo = {}, onDatos = {}, onNivel = { _, _ -> }, onSumarNivel = { _, _ -> }, onRestablecerNiveles = {},
    )
    private fun wizard(id: String, s: OnboardingUiState) = rule.capturar(id) { OnboardingContent(s, accionesWizard) }
    private fun paso(i: Int) = OnboardingUiState(cargando = false, pasos = plan, indice = i, licencia = lic(LicenseState.Trial(7)))

    @Test fun cargaInicial() = wizard("10a_carga_inicial", OnboardingUiState(cargando = true))
    @Test fun bienvenida() = wizard("10b_onboarding_bienvenida", paso(0))
    @Test fun datos() = wizard("10c_onboarding_datos", paso(1).copy(datos = DatosIniciales(nombre = "María", apellidos = "González Pérez")))
    @Test fun datosErrores() = wizard(
        "10d_onboarding_datos_errores",
        paso(1).copy(datos = DatosIniciales(nombre = "M", apellidos = "González Pérez", ci = "85-0412", telefono = "5234"), mostrarErroresDatos = true),
    )
    @Test fun alertas() = wizard("10e_onboarding_alertas", paso(2))
    @Test fun alertasErrores() = wizard(
        "10f_onboarding_alertas_errores",
        paso(2).copy(niveles = NivelesForm(productoBajo = "3", productoCritico = "6", insumoBajo = "2.5", insumoCritico = "x"), mostrarErroresNiveles = true),
    )
    @Test fun prueba() = wizard("10g_onboarding_prueba", paso(3))
    @Test fun completar() = wizard(
        "10h_onboarding_completar",
        OnboardingUiState(cargando = false, modo = ModoWizard.RETOMAR, pasos = listOf(PasoWizard.ALERTAS, PasoWizard.PRUEBA), indice = 1, licencia = lic(LicenseState.Trial(1))),
    )
}
