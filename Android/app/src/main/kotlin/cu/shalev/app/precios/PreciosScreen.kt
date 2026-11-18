package cu.shalev.app.precios

import cu.shalev.app.recorrido.recorridoVentana
import cu.shalev.app.recorrido.ObjetivosRecorrido
import cu.shalev.designsystem.component.coachMark
import cu.shalev.app.recorrido.RecorridoModulo
import cu.shalev.app.recorrido.Recorrido
import cu.shalev.designsystem.component.ShalevCheckbox
import cu.shalev.designsystem.theme.ShalevTextos
import cu.shalev.domain.model.nombreCompleto
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import cu.shalev.designsystem.component.shalevIndicadorDesplazamiento
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.core.money.Money
import cu.shalev.core.money.Percent
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.ShalevBottomSheet
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevChip
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevEmptyState
import cu.shalev.designsystem.component.ShalevFab
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevLoading
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.ShalevTextButton
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.component.shalevAnimateItem
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.ilustracion.ShalevIlustracion
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.CondicionPreajuste
import cu.shalev.domain.model.TipoClientePreajuste
import cu.shalev.domain.model.PreajustePrecios
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Servicio
import cu.shalev.designsystem.component.ShalevAvisosState
import cu.shalev.designsystem.component.rememberAvisos

object PreciosTags {
    const val NUEVO = "precios_nuevo"
    const val ELEGIR_MOSTRADOS = "precios_elegir_mostrados"
    const val FIJADOS = "precios_productos_fijados"
    const val BUSCAR = "precios_buscar_producto"
    const val PORCENTAJE = "precios_porcentaje"
    const val IMPORTE = "precios_importe"
    const val GUARDAR = "precios_guardar"
    const val VISTA_PREVIA = "precios_vista_previa"
    fun item(id: Long) = "precios_item_$id"
    fun producto(id: Long) = "precios_producto_$id"
}

@Composable
fun PreciosScreen(onBack: () -> Unit, viewModel: PreciosViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberAvisos()
    LaunchedEffect(viewModel) { viewModel.mensajes.collect { snackbar.showSnackbar(it) } }
    RecorridoModulo(Recorrido.PRECIOS, listo = state.form == null && state.borrar == null) {
        PreciosContent(
            state = state,
            onBack = onBack,
            acciones = AccionesPrecios(
                onNuevo = viewModel::nuevo, onEditar = viewModel::editar, onActivar = viewModel::activar,
                onCambiar = viewModel::cambiar, onProducto = viewModel::alternarProducto, onServicio = viewModel::alternarServicio, onElegirTodos = viewModel::elegirTodos,
                onGuardar = viewModel::guardar, onCerrar = viewModel::cerrar, onBorrar = viewModel::pedirBorrado,
                onConfirmarBorrado = viewModel::confirmarBorrado, onCancelarBorrado = viewModel::cancelarBorrado,
            ),
            snackbar = snackbar,
        )
    }
}

class AccionesPrecios(
    val onNuevo: () -> Unit,
    val onEditar: (PreajustePrecios) -> Unit,
    val onActivar: (PreajustePrecios, Boolean) -> Unit,
    val onCambiar: ((PreajusteForm) -> PreajusteForm) -> Unit,
    val onProducto: (Long) -> Unit,
    val onElegirVisibles: (Boolean) -> Unit = {},
    val onGuardar: () -> Unit,
    val onServicio: (Long) -> Unit = {},
    val onElegirTodos: () -> Unit = {},

    val onCerrar: () -> Unit,
    val onBorrar: () -> Unit,
    val onConfirmarBorrado: () -> Unit,
    val onCancelarBorrado: () -> Unit,
)

@Composable
fun PreciosContent(
    state: PreciosUiState,
    onBack: () -> Unit,
    acciones: AccionesPrecios,
    snackbar: ShalevAvisosState = rememberAvisos(),
) {
    Scaffold(
        topBar = {
            ShalevTopBar(title = TextosPrecios.TITULO, onBack = onBack)
        },
        // P24 (Jakob/Fitts): crear = botón flotante abajo, igual que Inventario y Elaboración; antes estaba arriba
        // y repetido en el estado vacío.
        // 0.26.0 (P73): el «+» va en la esquina inferior (estándar M3); la lista deja ShalevSize.fabClearance abajo
        // para que la última fila (importes a la derecha) no quede tapada.
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            if (state.cargado && (state.productos.isNotEmpty() || state.servicios.isNotEmpty())) {
                ShalevFab(ShalevIcons.Agregar, "Nuevo ajuste de precios", onClick = acciones.onNuevo, etiqueta = "Nuevo", modifier = Modifier.testTag(PreciosTags.NUEVO).coachMark(ObjetivosRecorrido.PRECIOS_NUEVO))
            }
        },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        when {
            !state.cargado -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { ShalevLoading() }
            else -> {
                val desplazamiento = rememberLazyListState()
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding).imePadding()
                        .shalevIndicadorDesplazamiento(hayContenidoArriba = { desplazamiento.canScrollBackward }, hayContenidoAbajo = { desplazamiento.canScrollForward }),
                    state = desplazamiento,
                    contentPadding = PaddingValues(top = ShalevSpacing.md, bottom = ShalevSize.fabClearance),
                    verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
                ) {
                item {
                    ShalevCard(tone = CardTone.Tonal, modifier = Modifier.padding(horizontal = ShalevSpacing.md).coachMark(ObjetivosRecorrido.PRECIOS_EXPLICACION)) {
                        Text(TextosPrecios.EXPLICACION, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (state.preajustes.isEmpty()) {
                    item {
                        ShalevEmptyState(
                            title = TextosPrecios.VACIO_TITULO,
                            detail = if (state.productos.isEmpty() && state.servicios.isEmpty()) "Primero agrega productos o servicios." else TextosPrecios.VACIO_DETALLE,
                            modifier = Modifier.padding(ShalevSpacing.md),
                            ilustracion = ShalevIlustracion.Precios,
                        )
                    }
                }
                items(state.preajustes, key = { it.id }) { p ->
                    ShalevListItem(
                        title = p.nombre,
                        value = Percent.format(p.puntosBasicos),
                        indicatorColor = if (p.activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        onClick = { acciones.onEditar(p) },
                        // El resumen (condición y nº de artículos) no se ve en la fila; TalkBack lo sigue leyendo.
                        modifier = Modifier.testTag(PreciosTags.item(p.id)).then(shalevAnimateItem())
                            .then(if (p == state.preajustes.first()) Modifier.coachMark(ObjetivosRecorrido.PRECIOS_LISTA) else Modifier)
                            .semantics { stateDescription = resumenPreajuste(p, state.existentes(p)) },
                        trailing = {
                            Switch(
                                checked = p.activo,
                                onCheckedChange = { acciones.onActivar(p, it) },
                                modifier = Modifier.semantics { contentDescription = if (p.activo) "Activo: ${p.nombre}" else "Pausado: ${p.nombre}" },
                            )
                        },
                    )
                }
            }
        }
    }

    state.form?.let { f -> FormularioPreajuste(state, f, acciones) }

    state.borrar?.let { p ->
        ShalevDialog(
            title = "¿Eliminar este ajuste?",
            text = "\"${p.nombre}\". Las ventas registradas no cambian.",
            onDismiss = acciones.onCancelarBorrado,
            onConfirm = acciones.onConfirmarBorrado,
            confirmDescription = "Eliminar",
            destructive = true,
        )
    }
}

@Composable
private fun FormularioPreajuste(state: PreciosUiState, f: PreajusteForm, acciones: AccionesPrecios) {
    val err = state.errores          // tras pulsar Guardar (incluye «elige productos»)
    val errCampos = validar(f)       // P18 (A16): regla común, al salir del campo
    val inicial = remember { f }
    ShalevBottomSheet(
        onDismiss = acciones.onCerrar,
        sinGuardar = f.copy(busqueda = inicial.busqueda, mostrarErrores = inicial.mostrarErrores) != inicial,
        title = if (f.nuevo) "Nuevo ajuste de precios" else "Editar ajuste",
        recorrido = recorridoVentana(Recorrido.VENTANA_PRECIO),
        footer = {
            ShalevSecondaryButton("Cancelar", icon = ShalevIcons.Cancelar, onClick = acciones.onCerrar, enabled = !state.guardando)
            if (!f.nuevo) ShalevIconAction(
                ShalevIcons.Eliminar, "Eliminar ajuste", etiqueta = "Eliminar", onClick = acciones.onBorrar,
                modifier = Modifier.coachMark(ObjetivosRecorrido.VENTANA_ELIMINAR),
            )
            ShalevPrimaryButton("Guardar", icon = ShalevIcons.Guardar, onClick = acciones.onGuardar, loading = state.guardando, modifier = Modifier.testTag(PreciosTags.GUARDAR).coachMark(ObjetivosRecorrido.VENTANA_GUARDAR))
        },
    ) {
        ShalevTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = f.nombre,
            onValueChange = { v -> acciones.onCambiar { it.copy(nombre = v) } },
            label = "Nombre del ajuste",
            placeholder = nombreSugerido(f),
            leadingIcon = ShalevIcons.Precios,
            modifier = Modifier.coachMark(ObjetivosRecorrido.VENTANA_NOMBRE),
            isError = CampoPrecio.NOMBRE in errCampos,
            errorText = errCampos[CampoPrecio.NOMBRE], validarAlSalir = true, forzarError = f.mostrarErrores,
        )
        Titulo("1. ¿Subir o bajar el precio?")
        ShalevChip(
            label = if (f.sube) "Subir precio" else "Bajar precio",
            selected = f.sube,
            onClick = { acciones.onCambiar { it.copy(sube = !it.sube) } },
            icon = if (f.sube) ShalevIcons.Agregar else ShalevIcons.Quitar,
        )
        ShalevTextField(
            filtro = FiltroEntrada.PORCENTAJE,
            value = f.porcentaje,
            onValueChange = { v -> acciones.onCambiar { it.copy(porcentaje = v) } },
            label = "Porcentaje",
            placeholder = "10",
            leadingIcon = ShalevIcons.Porcentaje,
            isError = CampoPrecio.PORCENTAJE in errCampos,
            errorText = errCampos[CampoPrecio.PORCENTAJE], validarAlSalir = true, forzarError = f.mostrarErrores,
            supportingText = if (f.sube) "Hasta 100 %" else "Hasta 90 %",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.testTag(PreciosTags.PORCENTAJE).coachMark(ObjetivosRecorrido.PRECIO_CAMBIO),
        )

        Titulo("2. ¿Cuándo se aplica?")
        GrupoCondicionPreajuste(f, acciones, errCampos[CampoPrecio.VOLUMEN])
        Titulo("3. ¿A qué artículos?")
        val permisos = cu.shalev.app.common.LocalPermisosApp.current
        val tipos = buildList {
            if (permisos.verInventario) add(TipoSeleccionPrecio.PRODUCTOS)
            if (permisos.verServicios) add(TipoSeleccionPrecio.SERVICIOS)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs, Alignment.CenterHorizontally)) {
            tipos.forEach { tipo ->
                ShalevChip(tipo.titulo, selected = f.tipoSeleccion == tipo, onClick = { acciones.onCambiar { it.copy(tipoSeleccion = tipo, busqueda = "") } })
            }
        }
        val totalElegidos = f.productoIds.size + f.servicioIds.size
        ShalevSecondaryText(if (totalElegidos == 1) "1 artículo elegido" else "$totalElegidos artículos elegidos", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        ShalevSecondaryButton(
            text = "Añadir todos los ${f.tipoSeleccion.titulo.lowercase()}",
            etiqueta = "Todos",
            icon = ShalevIcons.AgregarALista,
            onClick = acciones.onElegirTodos,
            modifier = Modifier.fillMaxWidth().testTag(PreciosTags.ELEGIR_MOSTRADOS).coachMark(ObjetivosRecorrido.PRECIO_ARTICULOS),
            mostrarTexto = true,
            enabled = if (f.tipoSeleccion == TipoSeleccionPrecio.PRODUCTOS) state.productos.isNotEmpty() else state.servicios.isNotEmpty(),
        )
        ShalevTextField(
            filtro = FiltroEntrada.BUSQUEDA,
            value = f.busqueda,
            onValueChange = { v -> acciones.onCambiar { it.copy(busqueda = v) } },
            label = if (f.tipoSeleccion == TipoSeleccionPrecio.PRODUCTOS) "Buscar producto" else "Buscar servicio",
            leadingIcon = ShalevIcons.Buscar,
            modifier = Modifier.testTag(PreciosTags.BUSCAR),
        )
        Column {
            if (f.tipoSeleccion == TipoSeleccionPrecio.PRODUCTOS) {
                state.productosVisibles.forEach { p -> ProductoPreajuste(p, p.id in f.productoIds, acciones) }
                if (state.productosVisibles.size == TextosPrecios.MAX_LISTA) ShalevSecondaryText("Busca para ver más.")
            } else {
                state.serviciosVisibles.forEach { servicio -> ServicioPreajuste(servicio, servicio.id in f.servicioIds, acciones) }
                if (state.serviciosVisibles.size == TextosPrecios.MAX_LISTA) ShalevSecondaryText("Busca para ver más.")
            }
        }
        err[CampoPrecio.PRODUCTOS]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        state.vistaPrevia?.let {
            ShalevCard(tone = CardTone.Highlight, modifier = Modifier.testTag(PreciosTags.VISTA_PREVIA).coachMark(ObjetivosRecorrido.PRECIO_VISTA)) {
                ShalevSecondaryText("Así quedaría al cobrar")
                Text(it, style = ShalevTextos.dato)
            }
        }
    }
}

/** Grupo exclusivo de casillas: una regla se activa por pago, volumen o tipo de cliente, nunca por varias causas. */
@Composable
private fun GrupoCondicionPreajuste(
    f: PreajusteForm,
    acciones: AccionesPrecios,
    errorVolumen: String?,
) {
    Column(
        Modifier.fillMaxWidth().selectableGroup().coachMark(ObjetivosRecorrido.PRECIO_CUANDO),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
    ) {
        CondicionPreajuste.entries.forEach { condicion ->
            val seleccionada = f.condicion == condicion
            Row(
                Modifier.fillMaxWidth()
                    .selectable(
                        selected = seleccionada,
                        role = Role.RadioButton,
                        onClick = { acciones.onCambiar { it.copy(condicion = condicion) } },
                    )
                    .padding(vertical = ShalevSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
            ) {
                ShalevCheckbox(checked = seleccionada, onCheckedChange = null)
                Text(tituloCondicion(condicion), style = MaterialTheme.typography.bodyLarge)
            }
            if (seleccionada) when (condicion) {
                CondicionPreajuste.METODO_PAGO -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs, Alignment.CenterHorizontally),
                    ) {
                        listOf<MetodoPago?>(MetodoPago.EFECTIVO, MetodoPago.TRANSFERENCIA, null).forEach { metodo ->
                            ShalevChip(
                                label = metodo?.let(::etiqueta) ?: "Todos",
                                selected = f.metodo == metodo,
                                onClick = { acciones.onCambiar { it.copy(metodo = metodo) } },
                                icon = when (metodo) {
                                    MetodoPago.EFECTIVO -> ShalevIcons.Efectivo
                                    MetodoPago.TRANSFERENCIA -> ShalevIcons.Transferencia
                                    null -> ShalevIcons.Precios
                                },
                            )
                        }
                    }
                }
                CondicionPreajuste.VOLUMEN_VENTA -> ShalevTextField(
                    filtro = FiltroEntrada.ENTERO,
                    value = f.volumenMinimo,
                    onValueChange = { valor -> acciones.onCambiar { it.copy(volumenMinimo = valor) } },
                    label = "Unidades desde",
                    placeholder = "12",
                    leadingIcon = ShalevIcons.Inventario,
                    supportingText = "A partir de esta cantidad se considera venta mayorista.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = errorVolumen != null,
                    errorText = errorVolumen,
                    validarAlSalir = true,
                    forzarError = f.mostrarErrores,
                )
                CondicionPreajuste.CLIENTE -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs, Alignment.CenterHorizontally),
                ) {
                    TipoClientePreajuste.entries.forEach { cliente ->
                        ShalevChip(
                            label = etiqueta(cliente),
                            selected = f.tipoCliente == cliente,
                            onClick = { acciones.onCambiar { it.copy(tipoCliente = cliente) } },
                            icon = if (cliente == TipoClientePreajuste.PARTICULAR) ShalevIcons.Perfil else ShalevIcons.Local,
                        )
                    }
                }
            }
        }
    }
}

private fun tituloCondicion(condicion: CondicionPreajuste): String = when (condicion) {
    CondicionPreajuste.METODO_PAGO -> "Método de pago"
    CondicionPreajuste.VOLUMEN_VENTA -> "Volumen de venta"
    CondicionPreajuste.CLIENTE -> "Cliente"
}

@Composable
private fun ProductoPreajuste(producto: Producto, elegido: Boolean, acciones: AccionesPrecios) {
    ShalevListItem(
        title = producto.nombreCompleto,
        value = Money.format(producto.precioVenta),
        indicatorColor = null,
        leading = { ShalevCheckbox(checked = elegido, onCheckedChange = null) },
        modifier = Modifier.testTag(PreciosTags.producto(producto.id))
            .toggleable(value = elegido, role = Role.Checkbox, onValueChange = { acciones.onProducto(producto.id) }),
    )
}

@Composable
private fun ServicioPreajuste(servicio: Servicio, elegido: Boolean, acciones: AccionesPrecios) {
    ShalevListItem(
        title = servicio.nombreCompleto,
        value = Money.format(servicio.importe),
        indicatorColor = null,
        leading = { ShalevCheckbox(checked = elegido, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = elegido, role = Role.Checkbox, onValueChange = { acciones.onServicio(servicio.id) }),
    )
}

@Composable
private fun Titulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
}
