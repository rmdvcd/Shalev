package cu.shalev.app.insumos

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.common.rememberSalidaProtegida
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.ShalevBarraAcciones
import cu.shalev.designsystem.component.ShalevChip
import cu.shalev.designsystem.component.ShalevEmptyState
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
import cu.shalev.domain.model.UnidadMedida

object InsumoFormTags {
    const val PRECIO_VENTA = "insumo_precio_venta"
    const val NOMBRE = "insumo_nombre"
    const val PRECIO = "insumo_precio"
    const val CANTIDAD = "insumo_cantidad"
    const val BAJO = "insumo_bajo"
    const val CRITICO = "insumo_critico"
    const val GUARDAR = "insumo_guardar"
    fun unidad(u: UnidadMedida) = "insumo_unidad_${u.name}"
}

class AccionesInsumoForm(
    val onNombre: (String) -> Unit = {},
    val onPrecio: (String) -> Unit = {},
    val onPrecioVenta: (String) -> Unit = {},
    val onCantidad: (String) -> Unit = {},
    val onUnidad: (UnidadMedida) -> Unit = {},
    val onBajo: (String) -> Unit = {},
    val onCritico: (String) -> Unit = {},
    val onGuardar: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun InsumoFormScreen(onBack: () -> Unit, onGuardado: (String) -> Unit, viewModel: InsumoFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoInsumoForm.Guardado -> onGuardado(e.mensaje)
                is EventoInsumoForm.Mensaje -> snackbar.showSnackbar(e.texto)
            }
        }
    }
    InsumoFormContent(
        state = state,
        snackbar = snackbar,
        acciones = AccionesInsumoForm(
            onNombre = viewModel::nombre, onPrecio = viewModel::precio, onPrecioVenta = viewModel::precioVenta, onCantidad = viewModel::cantidad, onUnidad = viewModel::unidad,
            onBajo = viewModel::nivelBajo, onCritico = viewModel::nivelCritico, onGuardar = viewModel::guardar, onBack = onBack,
        ),
    )
}

@Composable
fun InsumoFormContent(state: InsumoFormUiState, acciones: AccionesInsumoForm, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    val atras = rememberSalidaProtegida(state.sinGuardar, acciones.onBack)
    Scaffold(
        topBar = {
            ShalevTopBar(
                title = if (state.esNuevo) "Nuevo insumo" else "Editar insumo",
                onBack = atras,
            )
        },
        // P24: un único Guardar, fijo abajo y centrado (antes estaba arriba y al final del formulario).
        bottomBar = {
            if (!state.noEncontrado && !state.cargando) {
                ShalevBarraAcciones {
                    ShalevPrimaryButton(
                        if (state.esNuevo) "Guardar insumo" else "Guardar cambios", onClick = acciones.onGuardar, loading = state.guardando,
                        icon = ShalevIcons.Guardar, modifier = Modifier.testTag(InsumoFormTags.GUARDAR),
                    )
                }
            }
        },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.cargando -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { ShalevLoading() }
            state.noEncontrado -> Box(Modifier.padding(padding)) {
                ShalevEmptyState(title = "Este insumo ya no existe", detail = "Puede que se haya eliminado.", ilustracion = ShalevIlustracion.NoExiste) {
                    ShalevSecondaryButton("Volver", icon = ShalevIcons.Atras, onClick = acciones.onBack)
                }
            }
            else -> Formulario(state, acciones, Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Formulario(state: InsumoFormUiState, a: AccionesInsumoForm, modifier: Modifier) {
    val f = state.form
    val err = state.errores
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val simbolo = f.unidad.simbolo
    Column(
        modifier.fillMaxSize().shalevContentWidth().imePadding().verticalScroll(rememberScrollState()).padding(ShalevSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
    ) {
        ShalevSecondaryText("* Obligatorio")
        ShalevTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = f.nombre, onValueChange = a.onNombre, label = "Nombre *", placeholder = "Harina de trigo",
            isError = CamposInsumo.NOMBRE in err, errorText = err[CamposInsumo.NOMBRE], validarAlSalir = true, forzarError = state.intentado,
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(InsumoFormTags.NOMBRE),
        )

        // Cantidad + unidad de medida (la unidad define también cómo se leen el precio y los niveles).
        ShalevTextField(
            filtro = FiltroEntrada.DECIMAL,
            value = f.cantidad, onValueChange = a.onCantidad, label = "Cantidad ($simbolo) *", placeholder = "0", keyboardOptions = decimal,
            isError = CamposInsumo.CANTIDAD in err, errorText = err[CamposInsumo.CANTIDAD], validarAlSalir = true, forzarError = state.intentado, supportingText = "Admite decimales: 2.5",
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(InsumoFormTags.CANTIDAD),
        )
        Text("Se mide en", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs, Alignment.CenterHorizontally)) {
            UnidadMedida.entries.forEach { u ->
                ShalevChip(
                    InsumoFormLogic.etiqueta(u), selected = f.unidad == u, onClick = { a.onUnidad(u) },
                    modifier = Modifier.testTag(InsumoFormTags.unidad(u)),
                )
            }
        }

        ShalevTextField(
            filtro = FiltroEntrada.DINERO,
            value = f.precio, onValueChange = a.onPrecio, label = "Precio de costo (CUP por $simbolo) *", placeholder = "0.00", keyboardOptions = decimal,
            isError = CamposInsumo.PRECIO in err, errorText = err[CamposInsumo.PRECIO], validarAlSalir = true, forzarError = state.intentado, supportingText = "Lo que te cuesta 1 $simbolo",
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(InsumoFormTags.PRECIO),
        )
        // P29: los insumos también se pueden vender (unidades enteras de su medida) si tienen precio de venta.
        ShalevTextField(
            filtro = FiltroEntrada.DINERO,
            value = f.precioVenta, onValueChange = a.onPrecioVenta, label = "Precio de venta (CUP por $simbolo)", placeholder = "0.00", keyboardOptions = decimal,
            isError = CamposInsumo.PRECIO_VENTA in err, errorText = err[CamposInsumo.PRECIO_VENTA], validarAlSalir = true, forzarError = state.intentado,
            supportingText = "Opcional: para venderlo por $simbolo enteros",
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(InsumoFormTags.PRECIO_VENTA),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md)) {
            ShalevTextField(
                filtro = FiltroEntrada.DECIMAL,
                value = f.nivelBajo, onValueChange = a.onBajo, label = "Nivel bajo ($simbolo)", placeholder = "5", keyboardOptions = decimal,
                isError = CamposInsumo.NIVEL_BAJO in err, errorText = err[CamposInsumo.NIVEL_BAJO], validarAlSalir = true, forzarError = state.intentado, supportingText = "Opcional",
                imeAction = ImeAction.Next,
            modifier = Modifier.weight(1f).testTag(InsumoFormTags.BAJO),
            )
            ShalevTextField(
                filtro = FiltroEntrada.DECIMAL,
                value = f.nivelCritico, onValueChange = a.onCritico, label = "Nivel crítico ($simbolo)", placeholder = "1", keyboardOptions = decimal,
                isError = CamposInsumo.NIVEL_CRITICO in err, errorText = err[CamposInsumo.NIVEL_CRITICO], validarAlSalir = true, forzarError = state.intentado, supportingText = "Opcional",
                imeAction = ImeAction.Done, onImeAction = a.onGuardar,
            modifier = Modifier.weight(1f).testTag(InsumoFormTags.CRITICO),
            )
        }
        ShalevSecondaryText("Vacíos: se usan los niveles de Ajustes.")

        if (state.intentado && err.isNotEmpty()) {
            Text(
                "Revisa ${if (err.size == 1) "el campo marcado" else "los ${err.size} campos marcados"} en rojo.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
