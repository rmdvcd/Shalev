package cu.shalev.app.perfil

import cu.shalev.app.recorrido.ObjetivosRecorrido
import cu.shalev.designsystem.component.coachMark
import cu.shalev.app.recorrido.RecorridoModulo
import cu.shalev.app.recorrido.Recorrido
import cu.shalev.designsystem.component.ShalevCheckbox
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevListItem
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.common.SecureWindow
import cu.shalev.app.common.rememberSalidaProtegida
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.launch
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.component.shalevContentWidth
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing

object PerfilTags {
    const val MODULOS = "perfil.modulos"
    fun modulo(m: cu.shalev.domain.model.Modulo) = "perfil.modulo.${m.name}"
    const val NOMBRE = "perfil.nombre"
    const val APELLIDOS = "perfil.apellidos"
    const val CI = "perfil.ci"
    const val GUARDAR = "perfil.guardar"
    const val ID_MOSTRAR = "perfil.id_licencia.mostrar"
    const val ID_DIALOGO = "perfil.id_licencia.dialogo"
    const val ID_TEXTO = "perfil.id_licencia.texto"
    const val ID_COPIAR = "perfil.id_licencia.copiar"
}

class AccionesPerfil(
    val onEditar: ((DatosPerfilForm) -> DatosPerfilForm) -> Unit,
    val onGuardar: () -> Unit,
    val onDescartar: () -> Unit,
    /** 0.21.0 (C12). */
    val onModulo: (cu.shalev.domain.model.Modulo) -> Unit = {},
)

/**
 * Ajustes → Perfil: Nombre, Apellidos y CI. FLAG_SECURE: ni capturas ni miniatura en Recientes.
 * P28: los teléfonos y tarjetas ya no se repiten aquí; viven solo en Pago electrónico (decisión del usuario,
 * se aparta a propósito de Shalev.txt 119–123).
 */
@Composable
fun PerfilScreen(
    onBack: () -> Unit,
    viewModel: PerfilViewModel = hiltViewModel(),
) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.mensajes.collect { snackbar.showSnackbar(it) } }
    RecorridoModulo(Recorrido.PERFIL, listo = true) {
        PerfilContent(
            state = state,
            onBack = onBack,
            acciones = AccionesPerfil(viewModel::editar, viewModel::guardar, viewModel::descartar, viewModel::alternarModulo),
            snackbar = snackbar,
        )
    }
}

@Composable
fun PerfilContent(
    state: PerfilUiState,
    onBack: () -> Unit,
    acciones: AccionesPerfil,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val atras = rememberSalidaProtegida(state.sinGuardar, { acciones.onDescartar(); onBack() }, TextosPerfil.DESCARTAR_TEXTO)
    var verId by rememberSaveable { mutableStateOf(false) }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    Scaffold(
        topBar = { ShalevTopBar(title = TextosPerfil.TITULO, onBack = atras) },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().shalevContentWidth().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(vertical = ShalevSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            item(key = "intro") {
                ShalevCard(tone = CardTone.Tonal, modifier = Modifier.padding(horizontal = ShalevSpacing.md)) {
                    Text(TextosPerfil.EXPLICACION, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            item(key = "datos") { DatosPersonales(state, acciones) }
            state.idLicencia?.let { item(key = "id_licencia") { TarjetaIdLicencia(onMostrar = { verId = true }) } }
            state.modulos?.let { m -> item(key = "modulos") { ModulosNegocio(m, acciones) } }
        }
    }
    val idLicencia = state.idLicencia
    if (verId && idLicencia != null) {
        DialogoIdLicencia(
            id = idLicencia.id,
            onCerrar = { verId = false },
            onCopiar = { id ->
                copiarIdLicencia(contexto, id)
                verId = false
                alcance.launch { snackbar.showSnackbar(TextosIdLicencia.COPIADO) }
            },
        )
    }
}

/** Tarjeta de Perfil con el botón que muestra el ID de la licencia. */
@Composable
private fun TarjetaIdLicencia(onMostrar: () -> Unit) {
    ShalevCard(title = TextosIdLicencia.TITULO, modifier = Modifier.padding(horizontal = ShalevSpacing.md).coachMark(ObjetivosRecorrido.PERFIL_ID_LICENCIA)) {
        ShalevSecondaryText(TextosIdLicencia.EXPLICACION)
        ShalevButtonRow {
            ShalevSecondaryButton(
                TextosIdLicencia.BOTON, onClick = onMostrar, icon = ShalevIcons.Licencia,
                modifier = Modifier.testTag(PerfilTags.ID_MOSTRAR),
            )
        }
    }
}

/**
 * Ventana con el ID de la licencia (seleccionable, en monoespaciada para leerlo sin confundir caracteres) y el botón
 * Copiar. Sin licencia activada solo explica cuándo aparece. La pantalla tiene FLAG_SECURE (sin capturas).
 */
@Composable
internal fun DialogoIdLicencia(id: String?, onCerrar: () -> Unit, onCopiar: (String) -> Unit) {
    ShalevDialog(
        title = TextosIdLicencia.DIALOGO,
        onDismiss = onCerrar,
        onConfirm = id?.let { { onCopiar(it) } },
        confirmDescription = TextosIdLicencia.COPIAR_DESCRIPCION,
        confirmEtiqueta = TextosIdLicencia.COPIAR,
        confirmIcon = ShalevIcons.Copiar,
        confirmTag = PerfilTags.ID_COPIAR,
        modifier = Modifier.testTag(PerfilTags.ID_DIALOGO),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            if (id == null) {
                Text(TextosIdLicencia.SIN_LICENCIA, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            } else {
                Text(TextosIdLicencia.DETALLE, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                ShalevCard(tone = CardTone.Tonal) {
                    SelectionContainer {
                        Text(
                            id, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag(PerfilTags.ID_TEXTO),
                        )
                    }
                }
                ShalevSecondaryText(TextosIdLicencia.AVISO)
            }
        }
    }
}

/**
 * Copia el ID al portapapeles marcado como dato sensible: Android 13+ no lo muestra en la vista previa del
 * portapapeles (se usa el nombre literal del extra para no depender de la API 33).
 */
internal fun copiarIdLicencia(contexto: Context, id: String) {
    val portapapeles = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText(TextosIdLicencia.TITULO, id)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    portapapeles.setPrimaryClip(clip)
}

@Composable
private fun DatosPersonales(state: PerfilUiState, acciones: AccionesPerfil) {
    val f = state.form
    ShalevCard(modifier = Modifier.padding(horizontal = ShalevSpacing.md)) {
        ShalevTextField(
            filtro = FiltroEntrada.PERSONA,
            value = f.nombre, onValueChange = { v -> acciones.onEditar { it.copy(nombre = v) } },
            label = "Nombre", leadingIcon = ShalevIcons.Perfil,
            isError = f.errorNombre != null, errorText = f.errorNombre, validarAlSalir = false, forzarError = f.mostrarErrores,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(PerfilTags.NOMBRE).coachMark(ObjetivosRecorrido.PERFIL_DATOS),
        )
        ShalevTextField(
            filtro = FiltroEntrada.PERSONA,
            value = f.apellidos, onValueChange = { v -> acciones.onEditar { it.copy(apellidos = v) } },
            label = "Apellidos",
            isError = f.errorApellidos != null, errorText = f.errorApellidos, validarAlSalir = false, forzarError = f.mostrarErrores,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(PerfilTags.APELLIDOS),
        )
        ShalevTextField(
            filtro = FiltroEntrada.CARNE,
            value = f.ci, onValueChange = { v -> acciones.onEditar { it.copy(ci = v) } },
            label = "Carnet de identidad (CI)", placeholder = "85010112345",
            isError = f.errorCi != null, errorText = f.errorCi, validarAlSalir = true, forzarError = f.mostrarErrores,
            supportingText = "Solo se envía al pedir la licencia",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            imeAction = ImeAction.Done, onImeAction = { if (state.sinGuardar) acciones.onGuardar() },
            modifier = Modifier.testTag(PerfilTags.CI),
        )
        ShalevButtonRow {
            ShalevPrimaryButton(
                "Guardar", onClick = acciones.onGuardar, enabled = state.sinGuardar, loading = state.guardando,
                icon = ShalevIcons.Guardar, modifier = Modifier.testTag(PerfilTags.GUARDAR).coachMark(ObjetivosRecorrido.PERFIL_GUARDAR),
            )
        }
    }
}

/** 0.21.0 (C12): «Tu negocio»: qué secciones usa Shalev. Lo desmarcado desaparece de la barra inferior, Inicio y Registros. */
@Composable
private fun ModulosNegocio(modulos: Set<cu.shalev.domain.model.Modulo>, acciones: AccionesPerfil) {
    ShalevCard(title = TextosPerfil.MODULOS_TITULO, modifier = Modifier.padding(horizontal = ShalevSpacing.md).testTag(PerfilTags.MODULOS).coachMark(ObjetivosRecorrido.PERFIL_MODULOS)) {
        ShalevSecondaryText(TextosPerfil.MODULOS_EXPLICACION)
        cu.shalev.domain.model.Modulo.entries.forEach { m ->
            val marcado = m in modulos
            // No se puede desmarcar el último (la app nunca se queda sin secciones).
            val bloqueado = marcado && cu.shalev.domain.model.Modulo.alternar(modulos, m).isEmpty()
            ShalevListItem(
                title = m.etiqueta, subtitle = m.detalle, indicatorColor = null, selected = marcado,
                leading = { androidx.compose.material3.Checkbox(checked = marcado, onCheckedChange = null, enabled = !bloqueado) },
                onClick = if (bloqueado) null else ({ acciones.onModulo(m) }),
                modifier = Modifier.testTag(PerfilTags.modulo(m)),
            )
        }
    }
}
