package cu.shalev.app.perfil

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
    PerfilContent(
        state = state,
        onBack = onBack,
        acciones = AccionesPerfil(viewModel::editar, viewModel::guardar, viewModel::descartar, viewModel::alternarModulo),
        snackbar = snackbar,
    )
}

@Composable
fun PerfilContent(
    state: PerfilUiState,
    onBack: () -> Unit,
    acciones: AccionesPerfil,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val atras = rememberSalidaProtegida(state.sinGuardar, { acciones.onDescartar(); onBack() }, TextosPerfil.DESCARTAR_TEXTO)

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
            state.modulos?.let { m -> item(key = "modulos") { ModulosNegocio(m, acciones) } }
        }
    }
}

@Composable
private fun DatosPersonales(state: PerfilUiState, acciones: AccionesPerfil) {
    val f = state.form
    ShalevCard(modifier = Modifier.padding(horizontal = ShalevSpacing.md)) {
        ShalevTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = f.nombre, onValueChange = { v -> acciones.onEditar { it.copy(nombre = v) } },
            label = "Nombre", leadingIcon = ShalevIcons.Perfil,
            isError = f.errorNombre != null, errorText = f.errorNombre, validarAlSalir = false, forzarError = f.mostrarErrores,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(PerfilTags.NOMBRE),
        )
        ShalevTextField(
            filtro = FiltroEntrada.NOMBRE,
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
                icon = ShalevIcons.Guardar, modifier = Modifier.testTag(PerfilTags.GUARDAR),
            )
        }
    }
}

/** 0.21.0 (C12): «Tu negocio»: qué secciones usa Shalev. Lo desmarcado desaparece de la barra inferior, Inicio y Registros. */
@Composable
private fun ModulosNegocio(modulos: Set<cu.shalev.domain.model.Modulo>, acciones: AccionesPerfil) {
    ShalevCard(title = TextosPerfil.MODULOS_TITULO, modifier = Modifier.padding(horizontal = ShalevSpacing.md).testTag(PerfilTags.MODULOS)) {
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
