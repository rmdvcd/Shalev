package cu.shalev.app.producto

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.common.CampoFecha
import cu.shalev.app.common.HojaAgregarInsumo
import cu.shalev.app.common.rememberSalidaProtegida
import cu.shalev.core.money.Money
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.ShalevBarraAcciones
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevEmptyState
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevLoading
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.component.shalevContentWidth
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.ilustracion.ShalevIlustracion
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.Insumo

object ProductoFormTags {
    const val GUARDAR = "producto.guardar"
    const val CATEGORIA = "producto.categoria"
    const val NOMBRE = "producto.nombre"
    const val DESCRIPCION = "producto.descripcion"
    const val FOTO = "producto.foto"
    const val RECETA = "producto.receta"
    const val AGREGAR_INSUMO = "producto.agregar_insumo"
    const val FECHA = "producto.fecha"
    const val COSTO = "producto.costo"
    const val VENTA = "producto.venta"
    const val CANTIDAD = "producto.cantidad"
    const val BAJO = "producto.bajo"
    const val CRITICO = "producto.critico"
    fun cantidadInsumo(id: Long) = "producto.insumo.$id"
}

class AccionesForm(
    val onCategoria: (String) -> Unit = {},
    val onNombre: (String) -> Unit = {},
    val onDescripcion: (String) -> Unit = {},
    val onElegirFoto: () -> Unit = {},
    val onQuitarFoto: () -> Unit = {},
    /** 0.27.0 (T13): foto hecha con la cámara o elegida en la galería (content://). */
    val onFoto: (String) -> Unit = {},
    val onFecha: (java.time.LocalDate?) -> Unit = {},
    val onCosto: (String) -> Unit = {},
    val onVenta: (String) -> Unit = {},
    val onCantidad: (String) -> Unit = {},
    val onBajo: (String) -> Unit = {},
    val onCritico: (String) -> Unit = {},
    val onMostrarInsumos: (Boolean) -> Unit = {},
    val onAgregarInsumo: (Insumo) -> Unit = {},
    val onCantidadInsumo: (Long, String) -> Unit = { _, _ -> },
    val onQuitarInsumo: (Long) -> Unit = {},
    val onGuardar: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * @param onGuardado vuelve a Inventario con el mensaje para el snackbar.
 */
@Composable
fun ProductoFormScreen(
    onBack: () -> Unit,
    onGuardado: (String) -> Unit,
    onCrearInsumo: (String) -> Unit = {},
    viewModel: ProductoFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Selector de fotos del sistema: no necesita permisos de almacenamiento.
    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.fotoElegida(it?.toString()) }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoForm.Guardado -> onGuardado(e.mensaje)
                is EventoForm.Mensaje -> snackbar.showSnackbar(e.texto)
                is EventoForm.EsInsumo -> onCrearInsumo(e.nombre)
            }
        }
    }
    ProductoFormContent(
        state = state,
        snackbar = snackbar,
        acciones = AccionesForm(
            onCategoria = viewModel::categoria, onNombre = viewModel::nombre, onDescripcion = viewModel::descripcion,
            onElegirFoto = { elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onQuitarFoto = viewModel::quitarFoto, onFoto = { viewModel.fotoElegida(it) }, onFecha = viewModel::fecha, onCosto = viewModel::precioCosto,
            onVenta = viewModel::precioVenta, onCantidad = viewModel::cantidad, onBajo = viewModel::nivelBajo,
            onCritico = viewModel::nivelCritico,
            onMostrarInsumos = viewModel::mostrarInsumos, onAgregarInsumo = viewModel::agregarInsumo,
            onCantidadInsumo = viewModel::cantidadInsumo, onQuitarInsumo = viewModel::quitarInsumo,
            onGuardar = viewModel::guardar, onBack = onBack,
        ),
    )
}

@Composable
fun ProductoFormContent(state: ProductoFormUiState, acciones: AccionesForm, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    BackHandler(enabled = state.hojaInsumos) { acciones.onMostrarInsumos(false) }
    val atras = rememberSalidaProtegida(state.sinGuardar && !state.hojaInsumos, acciones.onBack)
    Scaffold(
        topBar = {
            ShalevTopBar(
                title = if (state.esNuevo) "Nuevo producto" else "Editar producto",
                onBack = atras,
            )
        },
        // P24: un único Guardar, fijo abajo y centrado (antes estaba arriba y al final del formulario).
        bottomBar = {
            if (!state.noEncontrado && !state.cargando) {
                ShalevBarraAcciones {
                    ShalevPrimaryButton(
                        if (state.esNuevo) "Guardar producto" else "Guardar cambios", onClick = acciones.onGuardar, loading = state.guardando,
                        icon = ShalevIcons.Guardar, modifier = Modifier.testTag(ProductoFormTags.GUARDAR),
                    )
                }
            }
        },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.cargando -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { ShalevLoading() }
            state.noEncontrado -> Box(Modifier.padding(padding)) {
                ShalevEmptyState(title = "Este producto ya no existe", detail = "Puede que se haya eliminado.", ilustracion = ShalevIlustracion.NoExiste) {
                    ShalevSecondaryButton("Volver", icon = ShalevIcons.Atras, onClick = acciones.onBack)
                }
            }
            else -> Formulario(state, acciones, Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }
    if (state.hojaInsumos) HojaAgregarInsumo(
        state.insumos, state.insumosDisponibles,
        onCerrar = { acciones.onMostrarInsumos(false) }, onAgregar = acciones.onAgregarInsumo,
    )
}

@Composable
private fun Formulario(state: ProductoFormUiState, a: AccionesForm, modifier: Modifier) {
    val f = state.form
    val err = state.errores
    val numero = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val entero = KeyboardOptions(keyboardType = KeyboardType.Number)
    Column(
        modifier.fillMaxSize().shalevContentWidth().imePadding().verticalScroll(rememberScrollState()).padding(ShalevSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
    ) {
        // 0.27.0 (T13): la foto es lo primero (los Elaborados no llevan foto).
        if (!f.esElaborado) Foto(f.fotoUri, f.nombre, state.importandoFoto, a)
        ShalevSecondaryText("* Obligatorio")
        CampoCategoria(f.categoria, state.categorias, err[Campos.CATEGORIA], state.intentado, a.onCategoria, ofrecerInsumos = state.esNuevo)

        ShalevTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = f.nombre, onValueChange = a.onNombre, label = "Nombre *", isError = Campos.NOMBRE in err, errorText = err[Campos.NOMBRE], validarAlSalir = true, forzarError = state.intentado,
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(ProductoFormTags.NOMBRE),
        )
        ShalevTextField(
            filtro = FiltroEntrada.TEXTO,
            // 0.24.0: una línea; obligatoria solo si ya hay otro producto con el mismo nombre.
            value = f.descripcion, onValueChange = a.onDescripcion, label = "Descripción", placeholder = ProductoFormLogic.EJEMPLO_DESCRIPCION,
            supportingText = ProductoFormLogic.ayudaDescripcion(f.descripcion), imeAction = ImeAction.Next,
            isError = Campos.DESCRIPCION in err, errorText = err[Campos.DESCRIPCION], validarAlSalir = true, forzarError = state.intentado,
            modifier = Modifier.testTag(ProductoFormTags.DESCRIPCION),
        )

        if (f.esElaborado) {
            Receta(state, a)
        } else {
            CampoFecha(
                fecha = f.fechaCaducidad, onFecha = a.onFecha, label = "Fecha de caducidad (opcional)",
                modifier = Modifier.fillMaxWidth(), tag = ProductoFormTags.FECHA,
            )
        }

        if (f.esElaborado) {
            ShalevCard(tone = CardTone.Tonal) {
                Text("Precio de costo: ${state.costoReceta?.let(Money::format) ?: "—"}", style = MaterialTheme.typography.bodyLarge)
                ShalevSecondaryText("Sale de la receta")
            }
        } else {
            ShalevTextField(
                filtro = FiltroEntrada.DINERO,
                value = f.precioCosto, onValueChange = a.onCosto, label = "Precio de costo (CUP) *", placeholder = "0.00", keyboardOptions = numero,
                isError = Campos.PRECIO_COSTO in err, errorText = err[Campos.PRECIO_COSTO], validarAlSalir = true, forzarError = state.intentado, supportingText = "Por unidad",
                imeAction = ImeAction.Next,
            modifier = Modifier.testTag(ProductoFormTags.COSTO),
            )
        }
        ShalevTextField(
            filtro = FiltroEntrada.DINERO,
            value = f.precioVenta, onValueChange = a.onVenta, label = "Precio de venta (CUP) *", placeholder = "0.00", keyboardOptions = numero,
            isError = f.precioVenta.isNotBlank() && Campos.PRECIO_VENTA in err, errorText = err[Campos.PRECIO_VENTA], validarAlSalir = false, forzarError = state.intentado, supportingText = "Por unidad",
            imeAction = if (f.esElaborado) ImeAction.Done else ImeAction.Next, onImeAction = if (f.esElaborado) a.onGuardar else null,
            modifier = Modifier.testTag(ProductoFormTags.VENTA),
        )
        // P26: un Elaborado no tiene existencias ni niveles propios (alcanza según sus insumos).
        if (!f.esElaborado) {
            ShalevTextField(
                filtro = FiltroEntrada.ENTERO,
                value = f.cantidad, onValueChange = a.onCantidad, label = "Cantidad *", placeholder = "0", keyboardOptions = entero,
                isError = Campos.CANTIDAD in err, errorText = err[Campos.CANTIDAD], validarAlSalir = true, forzarError = state.intentado, supportingText = "Unidades que tienes",
                imeAction = ImeAction.Next,
                modifier = Modifier.testTag(ProductoFormTags.CANTIDAD),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md)) {
                ShalevTextField(
                    filtro = FiltroEntrada.ENTERO,
                    value = f.nivelBajo, onValueChange = a.onBajo, label = "Nivel bajo", placeholder = "5", keyboardOptions = entero,
                    isError = Campos.NIVEL_BAJO in err, errorText = err[Campos.NIVEL_BAJO], validarAlSalir = true, forzarError = state.intentado, supportingText = "Opcional",
                    imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f).testTag(ProductoFormTags.BAJO),
                )
                ShalevTextField(
                    filtro = FiltroEntrada.ENTERO,
                    value = f.nivelCritico, onValueChange = a.onCritico, label = "Nivel crítico", placeholder = "1", keyboardOptions = entero,
                    isError = Campos.NIVEL_CRITICO in err, errorText = err[Campos.NIVEL_CRITICO], validarAlSalir = true, forzarError = state.intentado, supportingText = "Opcional",
                    imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f).testTag(ProductoFormTags.CRITICO),
                )
            }
            ShalevSecondaryText("Vacíos: se usan los niveles de Ajustes.")
        }
        if (state.intentado && err.isNotEmpty()) {
            Text(
                "Revisa ${if (err.size == 1) "el campo marcado" else "los ${err.size} campos marcados"} en rojo.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoCategoria(
    valor: String,
    categorias: List<String>,
    error: String?,
    forzarError: Boolean,
    onValor: (String) -> Unit,
    ofrecerInsumos: Boolean = false,
) {
    var abierto by remember { mutableStateOf(false) }
    // P31: en un artículo nuevo, «Insumos» es la primera opción (lleva al formulario de insumo).
    val todas = if (ofrecerInsumos) listOf(Categorias.INSUMOS) + categorias.filterNot(Categorias::esInsumos) else categorias
    val opciones = todas.filter { valor.isBlank() || it.contains(valor.trim(), ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = abierto && opciones.isNotEmpty(), onExpandedChange = { abierto = it }) {
        ShalevTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = valor, onValueChange = { onValor(it); abierto = true }, label = "Categoría *",
            isError = error != null, errorText = error, validarAlSalir = true, forzarError = forzarError, supportingText = "Elige o escribe una nueva",
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).testTag(ProductoFormTags.CATEGORIA),
        )
        ExposedDropdownMenu(expanded = abierto && opciones.isNotEmpty(), onDismissRequest = { abierto = false }) {
            opciones.forEach { c ->
                DropdownMenuItem(text = { Text(c) }, onClick = { onValor(c); abierto = false })
            }
        }
    }
}

@Composable
private fun Foto(uri: String?, nombre: String, importando: Boolean, a: AccionesForm) {
    // 0.27.0 (T13): miniatura centrada + Cámara / Galería / Quitar (solo icono).
    cu.shalev.app.common.SelectorFoto(
        uri = uri, descripcion = nombre.ifBlank { "producto" }, importando = importando,
        onFoto = a.onFoto, onQuitar = a.onQuitarFoto, modifier = Modifier.testTag(ProductoFormTags.FOTO),
    )
}

@Composable
private fun Receta(state: ProductoFormUiState, a: AccionesForm) {
    val err = state.errores[Campos.RECETA]
    Column(Modifier.testTag(ProductoFormTags.RECETA), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        Text("Receta *", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
        ShalevSecondaryText("Insumos de UNA unidad", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        state.form.receta.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                ShalevTextField(
                    filtro = FiltroEntrada.DECIMAL,
                    value = l.cantidad, onValueChange = { a.onCantidadInsumo(l.insumoId, it) }, label = "${l.nombre} (${l.simbolo})",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), clearable = false,
                    modifier = Modifier.weight(1f).testTag(ProductoFormTags.cantidadInsumo(l.insumoId)),
                )
                ShalevIconAction(ShalevIcons.Quitar, "Quitar ${l.nombre}", onClick = { a.onQuitarInsumo(l.insumoId) })
            }
        }
        if (err != null) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        ShalevButtonRow {
            ShalevSecondaryButton("Agregar insumo", onClick = { a.onMostrarInsumos(true) }, icon = ShalevIcons.AgregarALista, modifier = Modifier.testTag(ProductoFormTags.AGREGAR_INSUMO))
        }
    }
}
