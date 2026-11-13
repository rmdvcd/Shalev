package cu.shalev.app.registros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.app.common.SecureDialogWindow
import cu.shalev.app.common.SecureWindow
import cu.shalev.app.venta.CampoCliente
import cu.shalev.app.venta.CampoClienteField
import cu.shalev.app.venta.FormCliente
import cu.shalev.core.money.Money
import cu.shalev.core.time.Dates
import cu.shalev.designsystem.component.ShalevBottomSheet
import cu.shalev.designsystem.component.ShalevCheckbox
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevEmptyState
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevLoading
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.theme.ShalevTextos
import cu.shalev.designsystem.component.shalevAnimateItem
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.ilustracion.ShalevIlustracion
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.ClienteFijo
import cu.shalev.domain.repository.ClienteFijoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 0.27.0 (N2): pestaña «Clientes» de Registros. Lista de clientes fijos (se guardan al vender por transferencia con
 * «Cliente fijo» marcado), ficha con sus compras, «Editar» (0.31.0) y «Quitar de clientes fijos». Sin exportar;
 * con FLAG_SECURE.
 */
data class ClientesUiState(
    val cargando: Boolean = true,
    val clientes: List<ClienteFijo> = emptyList(),
    val fichaCi: String? = null,
    val confirmarQuitar: Boolean = false,
    /** 0.31.0: datos en corrección (nombre, carné y teléfono); null = sin ventana de corrección. */
    val edicion: FormCliente? = null,
    /** 0.31.0: motivo por el que no se pudo guardar la corrección (ese carné ya es de otro cliente). */
    val errorEdicion: String? = null,
) {
    val ficha: ClienteFijo? get() = fichaCi?.let { ci -> clientes.firstOrNull { it.ci == ci } }
}

private data class LocalClientes(
    val fichaCi: String? = null,
    val confirmarQuitar: Boolean = false,
    val edicion: FormCliente? = null,
    val errorEdicion: String? = null,
)

@HiltViewModel
class ClientesFijosViewModel @Inject constructor(private val repo: ClienteFijoRepository) : ViewModel() {
    private val local = MutableStateFlow(LocalClientes())

    val state: StateFlow<ClientesUiState> =
        combine(repo.observar().map<List<ClienteFijo>, List<ClienteFijo>?> { it }.catch { emit(emptyList()) }, local) { cs, l ->
            ClientesUiState(
                cargando = cs == null, clientes = cs.orEmpty(), fichaCi = l.fichaCi, confirmarQuitar = l.confirmarQuitar,
                edicion = l.edicion, errorEdicion = l.errorEdicion,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientesUiState())

    fun abrir(ci: String) = local.update { it.copy(fichaCi = ci, confirmarQuitar = false) }
    fun cerrar() = local.update { LocalClientes() }
    fun pedirQuitar() = local.update { it.copy(confirmarQuitar = true) }
    fun cancelarQuitar() = local.update { it.copy(confirmarQuitar = false) }
    fun quitar() {
        val ci = local.value.fichaCi ?: return
        viewModelScope.launch {
            runCatching { repo.quitar(ci) }
            local.update { LocalClientes() }
        }
    }

    /** 0.31.0: «Editar» de la ficha: el formulario llega con los datos actuales del cliente. */
    fun editar(c: ClienteFijo) = local.update {
        it.copy(edicion = FormCliente(nombre = c.nombreApellidos, ci = c.ci, telefono = c.telefono), errorEdicion = null)
    }

    fun campoEdicion(campo: CampoCliente, valor: String) =
        local.update { it.copy(edicion = it.edicion?.con(campo, valor), errorEdicion = null) }

    fun cancelarEdicion() = local.update { it.copy(edicion = null, errorEdicion = null) }

    fun guardarEdicion() {
        val l = local.value
        val ci = l.fichaCi ?: return
        val f = l.edicion ?: return
        if (f.erroresCliente().isNotEmpty()) {
            local.update { it.copy(edicion = f.copy(intento = true)) }
            return
        }
        viewModelScope.launch {
            val guardado = runCatching { repo.actualizar(ci, f.nombre, f.ci, f.telefono) }.getOrDefault(false)
            local.update {
                when {
                    !guardado -> it.copy(errorEdicion = TextosClientes.CARNE_OCUPADO)
                    // Con otro carné, la ficha sigue abierta con el nuevo y sus compras lo siguen (las reasigna el repositorio).
                    else -> it.copy(fichaCi = f.ci, edicion = null, errorEdicion = null)
                }
            }
        }
    }
}

object TextosClientes {
    const val VACIO_TITULO = "Aún no hay clientes fijos"
    const val VACIO_DETALLE = "En una venta por transferencia, marca «Cliente fijo» para guardar sus datos."
    /** 0.31.0: corrección del nombre, el carné y el teléfono desde la ficha. */
    const val EDITAR = "Editar"
    const val EDITAR_TITULO = "Editar cliente"
    const val GUARDAR = "Guardar"
    const val EDITAR_AYUDA = "Al guardar, sus compras anteriores quedan con estos datos."
    const val CARNE_OCUPADO = "Ese carné ya es de otro cliente."
    const val QUITAR = "Quitar de clientes fijos"
    const val QUITAR_TITULO = "¿Quitar de clientes fijos?"
    const val QUITAR_TEXTO = "Sus datos ya no se sugerirán en las ventas. Sus compras siguen en Registros."
    fun resumen(c: ClienteFijo): String =
        if (c.compras == 0) "Sin compras" else "${c.compras} ${if (c.compras == 1) "compra" else "compras"} · ${Money.format(c.total)}"
    fun campos(c: ClienteFijo, zona: ZoneId = ZoneId.systemDefault()): List<Pair<String, String>> = buildList {
        add("Carné de identidad" to c.ci)
        add("Teléfono" to c.telefono)
        add("Compras" to c.compras.toString())
        add("Total comprado" to Money.format(c.total))
        c.ultimaCompra?.let { add("Última compra" to Dates.dayTime(it, zona)) }
        add("Cliente fijo desde" to Dates.day(c.creadoEn, zona))
    }
}

object ClientesTags {
    const val LISTA = "clientes_lista"
    const val FICHA = "clientes_ficha"
    const val EDITAR = "clientes_editar"
    const val QUITAR = "clientes_quitar"
    const val CONFIRMAR_EDITAR = "clientes_confirmar_editar"
    const val CONFIRMAR_QUITAR = "clientes_confirmar_quitar"
    const val ERROR_EDITAR = "clientes_error_editar"
    fun cliente(ci: String) = "clientes_$ci"
    /** 0.31.0 (P3): casilla de selección de la fila. */
    fun check(ci: String) = "clientes_check_$ci"
    /** 0.31.0: campo del formulario de corrección. */
    fun campoEditar(c: CampoCliente) = "clientes_campo_${c.name.lowercase()}"
}

class AccionesClientes(
    val onAbrir: (String) -> Unit = {},
    val onCerrar: () -> Unit = {},
    val onEditar: (ClienteFijo) -> Unit = {},
    val onCampoEdicion: (CampoCliente, String) -> Unit = { _, _ -> },
    val onCancelarEdicion: () -> Unit = {},
    val onGuardarEdicion: () -> Unit = {},
    val onPedirQuitar: () -> Unit = {},
    val onCancelarQuitar: () -> Unit = {},
    val onQuitar: () -> Unit = {},
)

@Composable
fun ClientesFijosTab(state: ClientesUiState, acciones: AccionesClientes, zona: ZoneId = ZoneId.systemDefault()) {
    // Nombre, carné y teléfono: sin capturas ni miniatura en Recientes.
    SecureWindow()
    when {
        state.cargando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ShalevLoading() }
        state.clientes.isEmpty() -> ShalevEmptyState(
            title = TextosClientes.VACIO_TITULO, detail = TextosClientes.VACIO_DETALLE, ilustracion = ShalevIlustracion.Registros,
        )
        else -> {
            // 0.31.0 (P3): los clientes también se marcan con casilla (no se exportan: la selección es local).
            var marcados by rememberSaveable { mutableStateOf(emptySet<String>()) }
            Column(Modifier.fillMaxSize()) {
                if (marcados.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.xs).testTag(RegistrosTags.SELECCION),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShalevIconAction(
                            ShalevIcons.Cancelar, "Quitar selección", onClick = { marcados = emptySet() },
                            modifier = Modifier.testTag(RegistrosTags.LIMPIAR_SELECCION),
                        )
                        Text(
                            "${marcados.size} seleccionados", style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize().testTag(ClientesTags.LISTA),
                    contentPadding = PaddingValues(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
                ) {
                    items(state.clientes, key = { it.ci }) { c ->
                        ShalevListItem(
                            title = c.nombreApellidos,
                            // 0.28.0: el importe («3 compras · 1 250.00 CUP») va en seminegrita.
                            subtitleResaltado = ShalevTextos.resaltar(TextosClientes.resumen(c), Money.format(c.total)),
                            indicatorColor = null,
                            leading = {
                                ShalevCheckbox(
                                    checked = c.ci in marcados,
                                    onCheckedChange = { marcados = if (c.ci in marcados) marcados - c.ci else marcados + c.ci },
                                    modifier = Modifier.testTag(ClientesTags.check(c.ci))
                                        .semantics { contentDescription = "Seleccionar ${c.nombreApellidos}" },
                                )
                            },
                            onClick = { acciones.onAbrir(c.ci) }, modifier = shalevAnimateItem().testTag(ClientesTags.cliente(c.ci)),
                        )
                    }
                }
            }
        }
    }
    state.ficha?.let { c ->
        ShalevBottomSheet(
            onDismiss = acciones.onCerrar, title = c.nombreApellidos,
            // 0.31.0: «Editar» y «Quitar», centrados en el pie y siempre a la vista (la ficha puede ser más larga que la ventana).
            footer = {
                ShalevSecondaryButton(
                    TextosClientes.EDITAR, icon = ShalevIcons.Editar, onClick = { acciones.onEditar(c) },
                    modifier = Modifier.testTag(ClientesTags.EDITAR),
                )
                ShalevSecondaryButton(
                    TextosClientes.QUITAR, icon = ShalevIcons.Eliminar, onClick = acciones.onPedirQuitar,
                    modifier = Modifier.testTag(ClientesTags.QUITAR),
                )
            },
        ) {
            // Datos del cliente: la ventana de la hoja no hereda FLAG_SECURE de la pantalla.
            SecureDialogWindow()
            Column(Modifier.fillMaxWidth().testTag(ClientesTags.FICHA), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                TextosClientes.campos(c, zona).forEach { (k, v) -> ShalevListItem(title = k, value = v, indicatorColor = null) }
            }
        }
        if (state.confirmarQuitar) {
            ShalevDialog(
                title = TextosClientes.QUITAR_TITULO, text = TextosClientes.QUITAR_TEXTO,
                onDismiss = acciones.onCancelarQuitar, onConfirm = acciones.onQuitar,
                confirmDescription = TextosClientes.QUITAR, destructive = true, confirmTag = ClientesTags.CONFIRMAR_QUITAR,
            )
        }
    }
    state.edicion?.let { f ->
        ShalevDialog(
            title = TextosClientes.EDITAR_TITULO, onDismiss = acciones.onCancelarEdicion, onConfirm = acciones.onGuardarEdicion,
            confirmDescription = TextosClientes.GUARDAR, confirmTag = ClientesTags.CONFIRMAR_EDITAR,
        ) {
            SecureDialogWindow()
            // Nombre, carné y teléfono: aquí no hay número de transacción.
            CampoCliente.entries.filter { it != CampoCliente.NUMERO }.forEach { campo ->
                CampoClienteField(f, campo, acciones.onCampoEdicion, Modifier.testTag(ClientesTags.campoEditar(campo)))
            }
            ShalevSecondaryText(TextosClientes.EDITAR_AYUDA, maxLines = 3)
            state.errorEdicion?.let { e ->
                Text(
                    e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(ClientesTags.ERROR_EDITAR).semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }
    }
}
