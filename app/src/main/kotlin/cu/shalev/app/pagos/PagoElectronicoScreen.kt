package cu.shalev.app.pagos

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.common.SecureWindow
import cu.shalev.app.inicio.formatoTelefono
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.ShalevBottomSheet
import cu.shalev.designsystem.component.ShalevCheckbox
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevFab
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.component.shalevAnimateItem
import cu.shalev.designsystem.component.shalevContentWidth
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevElevation
import cu.shalev.designsystem.token.ShalevMotion
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

object PagoTags {
    fun opcion(tipo: TipoCuentaPago, id: Long?) = "pago_${tipo.name}_${id ?: "ninguno"}"
    fun bancoPreview(banco: BancoCubano) = "pago_banco_preview_${banco.name}"
}

private data class ElementoPago(val id: Long, val texto: String, val alias: String?, val banco: BancoCubano? = null)

// =====================================================================
// Pago electrónico (desde Inicio y desde Ajustes): listas editables.
// =====================================================================

@Composable
fun PagoElectronicoScreen(onBack: () -> Unit, viewModel: PagoElectronicoViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e -> if (e is EventoPago.Mensaje) snackbar.showSnackbar(e.texto) }
    }
    SecureWindow() // números de tarjeta y teléfonos
    PagoElectronicoContent(
        state = state,
        onBack = onBack,
        acciones = AccionesListaPago(
            onElegir = viewModel::elegir, onNuevo = viewModel::nuevo, onEditar = viewModel::editar,
            onBorrar = viewModel::pedirBorrado, onCambiarEdicion = viewModel::editarEdicion,
            onGuardarEdicion = viewModel::guardarEdicion, onCerrarEdicion = viewModel::cerrarEdicion,
            onConfirmarBorrado = viewModel::confirmarBorrado, onCancelarBorrado = viewModel::cancelarBorrado,
        ),
        snackbar = snackbar,
    )
}

class AccionesListaPago(
    val onElegir: (TipoCuentaPago, Long) -> Unit,
    val onNuevo: (TipoCuentaPago) -> Unit,
    val onEditar: (TipoCuentaPago, Long) -> Unit,
    val onBorrar: (TipoCuentaPago, Long) -> Unit,
    val onCambiarEdicion: ((EdicionPago) -> EdicionPago) -> Unit,
    val onGuardarEdicion: () -> Unit,
    val onCerrarEdicion: () -> Unit,
    val onConfirmarBorrado: () -> Unit,
    val onCancelarBorrado: () -> Unit,
)

@Composable
fun PagoElectronicoContent(
    state: PagoUiState,
    onBack: () -> Unit,
    acciones: AccionesListaPago,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    // Un solo «+» flotante abajo a la derecha con las 2 opciones (en vez de un + por lista).
    var expandido by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { ShalevTopBar(title = TextosPago.TITULO, onBack = onBack) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md)) {
                AnimatedVisibility(visible = expandido, enter = ShalevMotion.enterVertical, exit = ShalevMotion.exitVertical) {
                    Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md)) {
                        FilaPago("Teléfono", ShalevIcons.Telefono) { expandido = false; acciones.onNuevo(TipoCuentaPago.TELEFONO) }
                        FilaPago("Tarjeta o cuenta", ShalevIcons.PagoElectronico) { expandido = false; acciones.onNuevo(TipoCuentaPago.TARJETA) }
                    }
                }
                ShalevFab(
                    ShalevIcons.Agregar,
                    if (expandido) "Cerrar acciones" else "Agregar",
                    onClick = { expandido = !expandido },
                )
            }
        },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().shalevContentWidth().padding(padding),
            contentPadding = PaddingValues(
                top = ShalevSpacing.md,
                // Hueco para que el flotante no tape la última fila.
                bottom = ShalevSpacing.md + ShalevSize.fabClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            lista(
                tipo = TipoCuentaPago.TELEFONO, titulo = "Teléfonos",
                items = state.perfil.telefonos.map { ElementoPago(it.id, formatoTelefono(it.numero), it.alias) },
                seleccionado = state.perfil.pagoTelefonoId, acciones = acciones,
            )
            lista(
                tipo = TipoCuentaPago.TARJETA, titulo = "Tarjetas y cuentas",
                items = state.perfil.tarjetas.map { ElementoPago(it.id, it.enmascarado, it.alias, bancoPorTarjeta(it.numero)) },
                seleccionado = state.perfil.pagoTarjetaId, acciones = acciones,
            )
        }
    }

    DialogosListaPago(state, acciones)
}

/** Ventana de alta/edición + confirmación de borrado. */
@Composable
private fun DialogosListaPago(state: PagoUiState, acciones: AccionesListaPago) {
    state.edicion?.let { e -> EdicionSheet(e, state.guardando, acciones) }

    state.borrar?.let { (tipo, _) ->
        ShalevDialog(
            title = if (tipo == TipoCuentaPago.TELEFONO) "¿Eliminar este teléfono?" else "¿Eliminar esta tarjeta o cuenta?",
            text = "Se quita de la lista. Las ventas ya registradas no cambian.",
            onDismiss = acciones.onCancelarBorrado,
            onConfirm = acciones.onConfirmarBorrado,
            confirmDescription = "Eliminar",
            destructive = true,
        )
    }
}

/**
 * Lista editable de teléfonos o tarjetas. P28: es el ÚNICO sitio donde se ven y se eligen (antes también en
 * Perfil y en una ventana de Inicio). Tocar un elemento alterna su selección; solo se guarda un id por tipo.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.lista(
    tipo: TipoCuentaPago,
    titulo: String,
    items: List<ElementoPago>,
    seleccionado: Long?,
    acciones: AccionesListaPago,
) {
    item(key = "h_$tipo") {
        Text(titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth().padding(start = ShalevSpacing.md, end = ShalevSpacing.md, top = ShalevSpacing.md).semantics { heading() })
    }
    if (items.isEmpty()) {
        item(key = "v_$tipo") {
            ShalevSecondaryText("La lista está vacía. Toca + para agregar.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md))
        }
    }
    items(items, key = { "${tipo}_${it.id}" }) { item ->
        val enUso = item.id == seleccionado
        val subtitulo = listOfNotNull(item.alias, item.banco?.sigla).joinToString(" · ").ifBlank { null }
        ShalevListItem(
            title = item.texto,
            subtitle = subtitulo,
            selected = enUso,
            indicatorColor = null, // La casilla indica el elemento elegido.
            leading = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShalevCheckbox(
                        checked = enUso,
                        modifier = Modifier.semantics { contentDescription = "Usar ${item.texto}" },
                        onCheckedChange = { acciones.onElegir(tipo, item.id) },
                    )
                    Icon(
                        if (tipo == TipoCuentaPago.TELEFONO) ShalevIcons.Telefono else ShalevIcons.PagoElectronico,
                        contentDescription = null,
                    )
                }
            },
            onClick = { acciones.onElegir(tipo, item.id) },
            modifier = shalevAnimateItem().testTag(PagoTags.opcion(tipo, item.id)), // P18 (A18)
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShalevIconAction(ShalevIcons.Editar, "Editar ${item.texto}", etiqueta = "Editar", onClick = { acciones.onEditar(tipo, item.id) })
                    ShalevIconAction(ShalevIcons.Eliminar, "Eliminar ${item.texto}", etiqueta = "Eliminar", onClick = { acciones.onBorrar(tipo, item.id) })
                }
            },
        )
    }
}

/** Icono monocromático común para todas las tarjetas. */
@Composable
private fun IconoTarjeta(banco: BancoCubano, modifier: Modifier = Modifier) {
    Icon(
        ShalevIcons.PagoElectronico, contentDescription = banco.nombre,
        modifier = modifier.size(ShalevSize.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EdicionSheet(e: EdicionPago, guardando: Boolean, acciones: AccionesListaPago) {
    val telefono = e.tipo == TipoCuentaPago.TELEFONO
    val inicial = remember { e }
    ShalevBottomSheet(
        onDismiss = acciones.onCerrarEdicion,
        sinGuardar = e.numero != inicial.numero || e.alias != inicial.alias,
        title = when {
            e.id == null && telefono -> "Nuevo teléfono"
            e.id == null -> "Nueva tarjeta o cuenta"
            telefono -> "Editar teléfono"
            else -> "Editar tarjeta o cuenta"
        },
        footer = {
            ShalevSecondaryButton("Cancelar", icon = ShalevIcons.Cancelar, onClick = acciones.onCerrarEdicion)
            ShalevPrimaryButton("Guardar", icon = ShalevIcons.Guardar, onClick = acciones.onGuardarEdicion, loading = guardando, enabled = e.error == null)
        },
    ) {
        ShalevTextField(
            filtro = if (telefono) FiltroEntrada.TELEFONO else FiltroEntrada.TARJETA,
            value = e.numero,
            onValueChange = { v -> acciones.onCambiarEdicion { it.copy(numero = v) } },
            label = if (telefono) "Número de teléfono" else "Número de tarjeta o cuenta",
            placeholder = if (telefono) "5123 4567" else "9205 0000 0000 0000",
            leadingIcon = if (telefono) ShalevIcons.Telefono else ShalevIcons.PagoElectronico,
            isError = e.error != null, validarAlSalir = true, forzarError = e.mostrarErrores,
            errorText = e.error,
            keyboardOptions = KeyboardOptions(keyboardType = if (telefono) KeyboardType.Phone else KeyboardType.Number),
        )
        if (!telefono) {
            bancoPorTarjeta(e.numero)?.let { banco ->
                Row(
                    Modifier.fillMaxWidth().testTag(PagoTags.bancoPreview(banco)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconoTarjeta(banco)
                    Column(Modifier.padding(start = ShalevSpacing.xs)) {
                        Text(banco.nombre, style = MaterialTheme.typography.titleSmall)
                        ShalevSecondaryText(TextosPago.BANCO_ESTIMADO)
                    }
                }
            }
        }
        ShalevTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = e.alias,
            onValueChange = { v -> acciones.onCambiarEdicion { it.copy(alias = v) } },
            label = "Nombre para reconocerlo (opcional)",
            placeholder = if (telefono) "Mi móvil" else "Tarjeta del banco",
        )
    }
}

/** Acción del «+» de pago electrónico: etiqueta visible a la izquierda y mini botón M3 a la derecha. */
@Composable
private fun FilaPago(label: String, icon: ImageVector, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        Surface(
            shape = RoundedCornerShape(ShalevRadius.sm),
            color = cs.surfaceContainerHigh,
            contentColor = cs.onSurface,
            shadowElevation = ShalevElevation.low,
            modifier = Modifier.clearAndSetSemantics { }, // TalkBack ya lee la descripción del botón
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = ShalevSpacing.xs, vertical = ShalevSpacing.xs),
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = cs.tertiaryContainer, // como el botón tonal; el naranja es solo para avisos
            contentColor = cs.onTertiaryContainer,
        ) { Icon(icon, contentDescription = label) }
    }
}
