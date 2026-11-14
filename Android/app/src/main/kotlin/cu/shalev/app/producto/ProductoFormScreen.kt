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
import androidx.compose.runtime.saveable.rememberSaveable
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
import cu.shalev.app.common.AccionesAsistenteAlta
import cu.shalev.app.common.EncabezadoAsistenteAlta
import cu.shalev.app.common.NavegacionAsistenteAlta
import cu.shalev.app.common.rememberSalidaProtegida
import cu.shalev.core.money.Money
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevEmptyState
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevLoading
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
    const val GESTION_INVENTARIO = "producto.gestion_inventario"
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
    val onCostoAdicional: (String) -> Unit = {},
    val onVenta: (String) -> Unit = {},
    val onCantidad: (String) -> Unit = {},
    val onBajo: (String) -> Unit = {},
    val onCritico: (String) -> Unit = {},
    val onMostrarInsumos: (Boolean) -> Unit = {},
    val onAgregarInsumo: (Insumo) -> Unit = {},
    val onCantidadInsumo: (Long, String) -> Unit = { _, _ -> },
    val onQuitarInsumo: (Long) -> Unit = {},
    val onGuardar: () -> Unit = {},
    val onGuardarYRegistrar: () -> Unit = {},
    val onFichasCosto: () -> Unit = {},
    val onGestionInventario: () -> Unit = {},
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
    onFichasCosto: () -> Unit = {},
    onGestionInventario: () -> Unit = {},
    onRecepcionInicial: (id: Long, cantidad: String, costo: String) -> Unit = { _, _, _ -> },
    viewModel: ProductoFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Selector de fotos del sistema: no necesita permisos de almacenamiento.
    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.fotoElegida(it?.toString()) }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoForm.Guardado -> e.recepcionInicial?.let { onRecepcionInicial(it.articuloId, it.cantidad, it.costo) } ?: onGuardado(e.mensaje)
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
            onCritico = viewModel::nivelCritico, onCostoAdicional = viewModel::costoAdicional,
            onMostrarInsumos = viewModel::mostrarInsumos, onAgregarInsumo = viewModel::agregarInsumo,
            onCantidadInsumo = viewModel::cantidadInsumo, onQuitarInsumo = viewModel::quitarInsumo,
            onGuardar = { viewModel.guardar() }, onGuardarYRegistrar = { viewModel.guardar(abrirRecepcion = true) },
            onFichasCosto = onFichasCosto, onGestionInventario = onGestionInventario, onBack = onBack,
        ),
    )
}

@Composable
fun ProductoFormContent(state: ProductoFormUiState, acciones: AccionesForm, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    BackHandler(enabled = state.hojaInsumos) { acciones.onMostrarInsumos(false) }
    val atras = rememberSalidaProtegida(state.sinGuardar && !state.hojaInsumos, acciones.onBack)
    var paso by rememberSaveable(state.form.id) { mutableStateOf(1) }
    LaunchedEffect(state.intentado, state.errores) {
        if (state.intentado) NavegacionAsistenteAlta.pasoConError(
            state.errores.keys,
            listOf(
                setOf(Campos.CATEGORIA, Campos.NOMBRE, Campos.DESCRIPCION),
                setOf(Campos.RECETA, Campos.COSTO_ADICIONAL, Campos.PRECIO_COSTO),
                setOf(Campos.PRECIO_VENTA, Campos.CANTIDAD, Campos.NIVEL_BAJO, Campos.NIVEL_CRITICO),
            ),
        )?.let { paso = it }
    }
    Scaffold(
        topBar = { ShalevTopBar(title = if (state.esNuevo) "Nuevo producto" else "Editar producto", onBack = atras) },
        bottomBar = {
            if (!state.noEncontrado && !state.cargando) AccionesAsistenteAlta(
                actual = paso, total = 3,
                etiquetaGuardar = if (state.esNuevo) "Guardar producto" else "Guardar cambios",
                guardando = state.guardando,
                onSiguiente = { paso = NavegacionAsistenteAlta.siguiente(paso, 3) },
                onGuardar = acciones.onGuardar,
                onGuardarYRegistrar = if (state.esNuevo && !state.form.esElaborado && !cu.shalev.app.common.LocalPermisosApp.current.esSecundaria && cu.shalev.app.common.LocalPermisosApp.current.verInventario) acciones.onGuardarYRegistrar else null,
            )
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
            else -> Formulario(
                state, acciones, paso,
                onAnterior = if (paso > 1) ({ paso = NavegacionAsistenteAlta.anterior(paso, 3) }) else null,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            )
        }
    }
    if (state.hojaInsumos) HojaAgregarInsumo(
        state.insumos, state.insumosDisponibles,
        onCerrar = { acciones.onMostrarInsumos(false) }, onAgregar = acciones.onAgregarInsumo,
    )
}

@Composable
private fun Formulario(state: ProductoFormUiState, a: AccionesForm, paso: Int, onAnterior: (() -> Unit)?, modifier: Modifier) {
    val f = state.form
    val err = state.errores
    val numero = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val entero = KeyboardOptions(keyboardType = KeyboardType.Number)
    val titulo = when (paso) { 1 -> "Datos del producto"; 2 -> "Costos y receta"; else -> "Precio e inventario" }
    Column(
        modifier.fillMaxSize().shalevContentWidth().imePadding().verticalScroll(rememberScrollState()).padding(ShalevSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
    ) {
        EncabezadoAsistenteAlta(
            paso, 3, titulo,
            onAnterior = onAnterior,
        )
        when (paso) {
            1 -> {
                if (!f.esElaborado) Foto(f.fotoUri, f.nombre, state.importandoFoto, a)
                ShalevSecondaryText("* Obligatorio")
                CampoCategoria(f.categoria, state.categorias, err[Campos.CATEGORIA], state.intentado, a.onCategoria, ofrecerInsumos = state.esNuevo)
                ShalevTextField(
                    filtro = FiltroEntrada.NOMBRE,
                    value = f.nombre, onValueChange = a.onNombre, label = "Nombre *", isError = Campos.NOMBRE in err, errorText = err[Campos.NOMBRE], validarAlSalir = true, forzarError = state.intentado,
                    imeAction = ImeAction.Next, modifier = Modifier.testTag(ProductoFormTags.NOMBRE),
                )
                ShalevTextField(
                    filtro = FiltroEntrada.TEXTO,
                    value = f.descripcion, onValueChange = a.onDescripcion, label = "Descripción", placeholder = ProductoFormLogic.EJEMPLO_DESCRIPCION,
                    supportingText = ProductoFormLogic.ayudaDescripcion(f.descripcion), imeAction = ImeAction.Next,
                    isError = Campos.DESCRIPCION in err, errorText = err[Campos.DESCRIPCION], validarAlSalir = true, forzarError = state.intentado,
                    modifier = Modifier.testTag(ProductoFormTags.DESCRIPCION),
                )
                if (!f.esElaborado) CampoFecha(
                    fecha = f.fechaCaducidad, onFecha = a.onFecha, label = "Fecha de caducidad (opcional)",
                    modifier = Modifier.fillMaxWidth(), tag = ProductoFormTags.FECHA,
                )
            }
            2 -> {
                if (f.esElaborado) {
                    Receta(state, a)
                    ShalevTextField(
                        f.costoAdicional, a.onCostoAdicional, label = "Mano de obra y otros costos (CUP)", filtro = FiltroEntrada.DINERO,
                        isError = Campos.COSTO_ADICIONAL in err, errorText = err[Campos.COSTO_ADICIONAL],
                        supportingText = "Costo adicional de UNA unidad, además de los insumos",
                    )
                    ShalevCard(tone = CardTone.Tonal) {
                        Text("Precio de costo: ${state.costoReceta?.let(Money::format) ?: "—"}", style = MaterialTheme.typography.bodyLarge)
                        ShalevSecondaryText("Receta + mano de obra y otros costos")
                    }
                } else {
                    ShalevSecondaryText("* Obligatorio")
                    ShalevTextField(
                        filtro = FiltroEntrada.DINERO,
                        value = f.precioCosto, onValueChange = a.onCosto, label = "Precio de costo (CUP) *", placeholder = "0.00", keyboardOptions = numero,
                        isError = Campos.PRECIO_COSTO in err, errorText = err[Campos.PRECIO_COSTO], validarAlSalir = true, forzarError = state.intentado, supportingText = "Por unidad",
                        imeAction = ImeAction.Next, modifier = Modifier.testTag(ProductoFormTags.COSTO),
                    )
                }
                if (state.esNuevo && !cu.shalev.app.common.LocalPermisosApp.current.esSecundaria) {
                    ShalevSecondaryButton(
                        "Usar una ficha de costo", a.onFichasCosto, ShalevIcons.FichaCosto,
                        Modifier.fillMaxWidth(), mostrarTexto = true,
                    )
                    ShalevSecondaryText("Exportar una ficha crea un elaborado con receta y finaliza este asistente.")
                }
            }
            else -> {
                ShalevSecondaryText("* Obligatorio")
                ShalevTextField(
                    filtro = FiltroEntrada.DINERO,
                    value = f.precioVenta, onValueChange = a.onVenta, label = "Precio de venta (CUP) *", placeholder = "0.00", keyboardOptions = numero,
                    isError = Campos.PRECIO_VENTA in err, errorText = err[Campos.PRECIO_VENTA], validarAlSalir = true, forzarError = state.intentado, supportingText = "Por unidad",
                    imeAction = ImeAction.Done, onImeAction = a.onGuardar, modifier = Modifier.testTag(ProductoFormTags.VENTA),
                )
                if (!f.esElaborado) {
                    ShalevTextField(
                        filtro = FiltroEntrada.ENTERO,
                        value = f.cantidad, onValueChange = a.onCantidad, label = "Cantidad *", placeholder = "0", keyboardOptions = entero,
                        isError = Campos.CANTIDAD in err, errorText = err[Campos.CANTIDAD], validarAlSalir = true, forzarError = state.intentado, supportingText = "Unidades que tienes",
                        imeAction = ImeAction.Next, modifier = Modifier.testTag(ProductoFormTags.CANTIDAD),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md)) {
                        ShalevTextField(
                            filtro = FiltroEntrada.ENTERO,
                            value = f.nivelBajo, onValueChange = a.onBajo, label = "Nivel bajo", placeholder = "5", keyboardOptions = entero,
                            isError = Campos.NIVEL_BAJO in err, errorText = err[Campos.NIVEL_BAJO], validarAlSalir = true, forzarError = state.intentado, supportingText = "Opcional",
                            imeAction = ImeAction.Next, modifier = Modifier.weight(1f).testTag(ProductoFormTags.BAJO),
                        )
                        ShalevTextField(
                            filtro = FiltroEntrada.ENTERO,
                            value = f.nivelCritico, onValueChange = a.onCritico, label = "Nivel crítico", placeholder = "1", keyboardOptions = entero,
                            isError = Campos.NIVEL_CRITICO in err, errorText = err[Campos.NIVEL_CRITICO], validarAlSalir = true, forzarError = state.intentado, supportingText = "Opcional",
                            imeAction = ImeAction.Next, modifier = Modifier.weight(1f).testTag(ProductoFormTags.CRITICO),
                        )
                    }
                    ShalevSecondaryText("Vacíos: se usan los niveles de Ajustes.")
                    if (!state.esNuevo && !cu.shalev.app.common.LocalPermisosApp.current.esSecundaria && cu.shalev.app.common.LocalPermisosApp.current.verInventario) {
                        ShalevSecondaryButton(
                            "Gestión de inventario", a.onGestionInventario, ShalevIcons.Inventario,
                            Modifier.fillMaxWidth().testTag(ProductoFormTags.GESTION_INVENTARIO), mostrarTexto = true,
                        )
                        ShalevSecondaryText("Gestiona compras, lotes y ajustes; el formulario conserva tus cambios al volver.")
                    }
                } else {
                    ShalevCard(tone = CardTone.Tonal) {
                        Text(f.nombre.ifBlank { "Producto elaborado" }, style = MaterialTheme.typography.titleMedium)
                        ShalevSecondaryText("Costo completo: ${state.costoReceta?.let(Money::format) ?: "por definir"} · ${f.receta.size} insumos")
                        ShalevSecondaryText("Elaborado: las existencias dependen de los insumos de la receta.")
                    }
                    if (!cu.shalev.app.common.LocalPermisosApp.current.esSecundaria && cu.shalev.app.common.LocalPermisosApp.current.verInventario) {
                        ShalevSecondaryButton(
                            "Gestionar existencias de insumos", a.onGestionInventario, ShalevIcons.Inventario,
                            Modifier.fillMaxWidth().testTag(ProductoFormTags.GESTION_INVENTARIO), mostrarTexto = true,
                        )
                    }
                }
            }
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
