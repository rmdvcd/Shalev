package cu.shalev.app.servicios

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
import cu.shalev.domain.model.Insumo

object ServicioFormTags {
    const val NOMBRE = "servicio_nombre"
    const val TIPO = "servicio_tipo"
    const val IMPORTE = "servicio_importe"
    const val DESCRIPCION = "servicio_descripcion"
    const val FOTO = "servicio_foto"
    const val INSUMOS = "servicio_insumos"
    const val AGREGAR_INSUMO = "servicio_agregar_insumo"
    const val GUARDAR = "servicio_guardar"
    fun cantidadInsumo(id: Long) = "servicio_insumo_$id"
}

class AccionesServicioForm(
    val onNombre: (String) -> Unit = {},
    val onTipo: (String) -> Unit = {},
    val onImporte: (String) -> Unit = {},
    val onDescripcion: (String) -> Unit = {},
    val onElegirFoto: () -> Unit = {},
    val onQuitarFoto: () -> Unit = {},
    /** 0.27.0 (T13): foto hecha con la cámara o elegida en la galería (content://). */
    val onFoto: (String) -> Unit = {},
    val onMostrarInsumos: (Boolean) -> Unit = {},
    val onAgregarInsumo: (Insumo) -> Unit = {},
    val onCantidadInsumo: (Long, String) -> Unit = { _, _ -> },
    val onQuitarInsumo: (Long) -> Unit = {},
    val onGuardar: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun ServicioFormScreen(onBack: () -> Unit, onGuardado: (String) -> Unit, viewModel: ServicioFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Selector de fotos del sistema: no necesita permisos de almacenamiento.
    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.fotoElegida(it?.toString()) }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoServicioForm.Guardado -> onGuardado(e.mensaje)
                is EventoServicioForm.Mensaje -> snackbar.showSnackbar(e.texto)
            }
        }
    }
    ServicioFormContent(
        state = state,
        snackbar = snackbar,
        acciones = AccionesServicioForm(
            onNombre = viewModel::nombre, onTipo = viewModel::tipo, onImporte = viewModel::importe, onDescripcion = viewModel::descripcion,
            onElegirFoto = { elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onQuitarFoto = viewModel::quitarFoto, onFoto = { viewModel.fotoElegida(it) }, onMostrarInsumos = viewModel::mostrarInsumos, onAgregarInsumo = viewModel::agregarInsumo,
            onCantidadInsumo = viewModel::cantidadInsumo, onQuitarInsumo = viewModel::quitarInsumo, onGuardar = viewModel::guardar, onBack = onBack,
        ),
    )
}

@Composable
fun ServicioFormContent(state: ServicioFormUiState, acciones: AccionesServicioForm, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    BackHandler(enabled = state.hojaInsumos) { acciones.onMostrarInsumos(false) }
    val atras = rememberSalidaProtegida(state.sinGuardar && !state.hojaInsumos, acciones.onBack)
    Scaffold(
        topBar = { ShalevTopBar(title = if (state.esNuevo) "Nuevo servicio" else "Editar servicio", onBack = atras) },
        bottomBar = {
            if (!state.noEncontrado && !state.cargando) {
                ShalevBarraAcciones {
                    ShalevPrimaryButton(
                        if (state.esNuevo) "Guardar servicio" else "Guardar cambios", onClick = acciones.onGuardar, loading = state.guardando,
                        icon = ShalevIcons.Guardar, modifier = Modifier.testTag(ServicioFormTags.GUARDAR),
                    )
                }
            }
        },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.cargando -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { ShalevLoading() }
            state.noEncontrado -> Box(Modifier.padding(padding)) {
                ShalevEmptyState(title = "Este servicio ya no existe", detail = "Puede que se haya eliminado.", ilustracion = ShalevIlustracion.NoExiste) {
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
private fun Formulario(state: ServicioFormUiState, a: AccionesServicioForm, modifier: Modifier) {
    val f = state.form
    val err = state.errores
    Column(
        modifier.fillMaxSize().shalevContentWidth().imePadding().verticalScroll(rememberScrollState()).padding(ShalevSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
    ) {
        // 0.27.0 (T13): la foto es lo primero.
        Foto(f.fotoUri, f.nombre, state.importandoFoto, a)
        ShalevSecondaryText("* Obligatorio")
        ShalevTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = f.nombre, onValueChange = a.onNombre, label = "Nombre *", placeholder = "Corte de pelo",
            isError = CamposServicio.NOMBRE in err, errorText = err[CamposServicio.NOMBRE], validarAlSalir = true, forzarError = state.intentado,
            imeAction = ImeAction.Next, modifier = Modifier.testTag(ServicioFormTags.NOMBRE),
        )
        CampoTipo(f.tipo, state.tipos, err[CamposServicio.TIPO], state.intentado, a.onTipo)
        ShalevTextField(
            filtro = FiltroEntrada.DINERO,
            value = f.importe, onValueChange = a.onImporte, label = "Importe (CUP) *", placeholder = "0.00",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = CamposServicio.IMPORTE in err, errorText = err[CamposServicio.IMPORTE], validarAlSalir = true, forzarError = state.intentado,
            supportingText = "Lo que cobras cada vez", imeAction = ImeAction.Next,
            modifier = Modifier.testTag(ServicioFormTags.IMPORTE),
        )
        ShalevTextField(
            filtro = FiltroEntrada.TEXTO,
            // 0.24.0: una línea; obligatoria solo si ya hay otro servicio con el mismo nombre.
            value = f.descripcion, onValueChange = a.onDescripcion, label = "Descripción", placeholder = "A domicilio, 30 minutos",
            supportingText = cu.shalev.app.producto.ProductoFormLogic.ayudaDescripcion(f.descripcion),
            isError = CamposServicio.DESCRIPCION in err, errorText = err[CamposServicio.DESCRIPCION], validarAlSalir = true, forzarError = state.intentado,
            modifier = Modifier.testTag(ServicioFormTags.DESCRIPCION),
        )
        Insumos(state, a)
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
private fun CampoTipo(valor: String, tipos: List<String>, error: String?, forzarError: Boolean, onValor: (String) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    val opciones = tipos.filter { valor.isBlank() || it.contains(valor.trim(), ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = abierto && opciones.isNotEmpty(), onExpandedChange = { abierto = it }) {
        ShalevTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = valor, onValueChange = { onValor(it); abierto = true }, label = "Tipo *", placeholder = "Peluquería",
            isError = error != null, errorText = error, validarAlSalir = true, forzarError = forzarError, supportingText = "Elige o escribe uno nuevo",
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).testTag(ServicioFormTags.TIPO),
        )
        ExposedDropdownMenu(expanded = abierto && opciones.isNotEmpty(), onDismissRequest = { abierto = false }) {
            opciones.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { onValor(t); abierto = false }) }
        }
    }
}

@Composable
private fun Foto(uri: String?, nombre: String, importando: Boolean, a: AccionesServicioForm) {
    // 0.27.0 (T13): miniatura centrada + Cámara / Galería / Quitar (solo icono).
    cu.shalev.app.common.SelectorFoto(
        uri = uri, descripcion = nombre.ifBlank { "servicio" }, importando = importando,
        onFoto = a.onFoto, onQuitar = a.onQuitarFoto, modifier = Modifier.testTag(ServicioFormTags.FOTO),
    )
}

@Composable
private fun Insumos(state: ServicioFormUiState, a: AccionesServicioForm) {
    val err = state.errores[CamposServicio.INSUMOS]
    Column(Modifier.testTag(ServicioFormTags.INSUMOS), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        Text("Insumos que gasta (opcional)", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
        ShalevSecondaryText("Por cada vez que lo prestas. Se descuentan al venderlo.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        state.form.insumos.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                ShalevTextField(
                    filtro = FiltroEntrada.DECIMAL,
                    value = l.cantidad, onValueChange = { a.onCantidadInsumo(l.insumoId, it) }, label = "${l.nombre} (${l.simbolo})",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), clearable = false,
                    modifier = Modifier.weight(1f).testTag(ServicioFormTags.cantidadInsumo(l.insumoId)),
                )
                ShalevIconAction(ShalevIcons.Quitar, "Quitar ${l.nombre}", onClick = { a.onQuitarInsumo(l.insumoId) })
            }
        }
        if (err != null) Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (state.form.insumos.isNotEmpty()) {
            ShalevCard(tone = CardTone.Tonal) {
                Text(
                    "Costo de insumos: ${state.costo?.let(Money::format) ?: "—"}", style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        ShalevButtonRow {
            ShalevSecondaryButton("Agregar insumo", onClick = { a.onMostrarInsumos(true) }, icon = ShalevIcons.AgregarALista, modifier = Modifier.testTag(ServicioFormTags.AGREGAR_INSUMO))
        }
    }
}
