package cu.shalev.app.herramientas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevBottomSheet
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevComboBox
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevFab
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.fichacosto.CalculoFichaCosto
import cu.shalev.domain.fichacosto.CategoriaCosto
import cu.shalev.domain.fichacosto.MonedaCosto
import cu.shalev.domain.fichacosto.PartidaCosto
import cu.shalev.domain.fichacosto.UnidadMargen

@Composable
fun HerramientasScreen(onAbrirFicha: () -> Unit, onVerManual: () -> Unit) {
    var ayuda by remember { mutableStateOf(false) }
    var cambioAbierto by rememberSaveable { mutableStateOf(false) }
    val modulos = cu.shalev.app.common.LocalPermisosApp.current.modulos
    Scaffold(topBar = { ShalevTopBar(title = "Herramientas", onAyuda = { ayuda = true }, ayudaDescripcion = "Ayuda de Herramientas") }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(ShalevSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        ) {
            ShalevCard(onClick = onAbrirFicha) {
                ShalevListItem(
                    title = "Ficha de costo",
                    subtitle = "Calculadora temporal de costos",
                    indicatorColor = null,
                )
            }
            ShalevCard(onClick = { cambioAbierto = true }) {
                ShalevListItem(
                    title = "Calculadora de cambio",
                    subtitle = "Convertir CUP y USD o calcular una tasa",
                    indicatorColor = null,
                )
            }
        }
    }
    if (cambioAbierto) CalculadoraCambio(onDismiss = { cambioAbierto = false })
    if (ayuda) cu.shalev.app.ayuda.DialogoAyudaPestana(
        pestana = cu.shalev.app.ayuda.PestanaAyuda.HERRAMIENTAS,
        modulos = modulos,
        onDismiss = { ayuda = false },
        onVerManual = { ayuda = false; onVerManual() },
    )
}

/** El borrador solo vive mientras está abierta esta pantalla; no forma parte del respaldo. */
class FichaCostoViewModel : ViewModel() {
    var nombre by mutableStateOf("")
    var margen by mutableStateOf("")
    var unidadMargen by mutableStateOf(UnidadMargen.PORCENTAJE)
    var partidas by mutableStateOf<List<PartidaCosto>>(emptyList())
    val tieneDatos: Boolean get() = nombre.isNotBlank() || margen.isNotBlank() || partidas.isNotEmpty()

    fun guardarPartida(indice: Int?, partida: PartidaCosto) {
        partidas = if (indice == null) partidas + partida else partidas.mapIndexed { i, anterior -> if (i == indice) partida else anterior }
    }
    fun eliminarPartida(indice: Int) { partidas = partidas.filterIndexed { i, _ -> i != indice } }
}

@Composable
fun FichaCostoScreen(onBack: () -> Unit, vm: FichaCostoViewModel = viewModel()) {
    var editorAbierto by rememberSaveable { mutableStateOf(false) }
    var indiceEdicion by rememberSaveable { mutableStateOf<Int?>(null) }
    var indiceEliminar by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmarSalida by rememberSaveable { mutableStateOf(false) }
    var cambioAbierto by rememberSaveable { mutableStateOf(false) }
    val salir = { if (vm.tieneDatos) confirmarSalida = true else onBack() }
    BackHandler(enabled = !editorAbierto && indiceEliminar == null && !confirmarSalida && !cambioAbierto) { salir() }
    val margen = CalculoFichaCosto.numero(vm.margen)
    val totales = CalculoFichaCosto.calcular(vm.partidas, margen, vm.unidadMargen)
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            ShalevTopBar(title = "Ficha de costo", onBack = salir, actions = {
                ShalevIconAction(ShalevIcons.Transferencia, "Calculadora de cambio", onClick = { cambioAbierto = true })
            })
        },
        floatingActionButton = {
            ShalevFab(ShalevIcons.Agregar, "Agregar partida", onClick = { indiceEdicion = null; editorAbierto = true })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(ShalevSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        ) {
            item {
                ShalevCard(tone = CardTone.Tonal) {
                    Text("Cálculo temporal: no se guarda ni se incluye en el respaldo. Al salir se pierde.")
                }
            }
            item { ShalevTextField(vm.nombre, { vm.nombre = it }, label = "Producto o servicio", filtro = FiltroEntrada.NOMBRE) }
            item { Text("Partidas", style = MaterialTheme.typography.titleMedium) }
            if (vm.partidas.isEmpty()) item { Text("Agrega materias, trabajo u otros gastos con el botón +.") }
            itemsIndexed(vm.partidas) { indice, partida ->
                ShalevCard {
                    ShalevListItem(
                        title = partida.descripcion,
                        subtitle = "${partida.categoria.titulo} · ${partida.cantidad.toPlainString()} ${partida.unidad} × ${partida.costoUnitario.toPlainString()} ${partida.moneda.name}",
                        value = "${partida.subtotal.toPlainString()} ${partida.moneda.name}",
                        subtitleMaxLines = 2,
                        indicatorColor = null,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        ShalevIconAction(ShalevIcons.Editar, "Editar ${partida.descripcion}", onClick = { indiceEdicion = indice; editorAbierto = true })
                        ShalevIconAction(ShalevIcons.Eliminar, "Eliminar ${partida.descripcion}", onClick = { indiceEliminar = indice })
                    }
                }
            }
            item {
                ShalevCard(title = "Totales por categoría") {
                    CategoriaCosto.entries.forEach { categoria ->
                        val valores = totales.porCategoria.getValue(categoria)
                        Text("${categoria.titulo}: ${valores.getValue(MonedaCosto.CUP)} CUP · ${valores.getValue(MonedaCosto.USD)} USD", modifier = Modifier.fillMaxWidth())
                    }
                    Text("Costo total: ${totales.costoTotal.getValue(MonedaCosto.CUP)} CUP · ${totales.costoTotal.getValue(MonedaCosto.USD)} USD", style = MaterialTheme.typography.titleMedium)
                }
            }
            item {
                ShalevTextField(
                    vm.margen, { vm.margen = it }, label = "Margen de ganancia (opcional)", filtro = FiltroEntrada.DECIMAL,
                    isError = vm.margen.isNotBlank() && margen == null,
                    errorText = "Escribe un número válido.",
                )
            }
            item {
                ShalevComboBox("Interpretar margen como", UnidadMargen.entries, vm.unidadMargen, { it.titulo }, { vm.unidadMargen = it })
            }
            item {
                val precio = totales.precioEstimado
                if (precio != null) Text("Precio estimado: ${precio.getValue(MonedaCosto.CUP)} CUP · ${precio.getValue(MonedaCosto.USD)} USD")
                Text("Estimación configurable, no fórmula normativa. Las monedas no se convierten.", style = MaterialTheme.typography.bodySmall)
            }
            // Espacio para que el botón flotante no tape el último texto.
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(ShalevSpacing.lg)) }
        }
    }
    if (cambioAbierto) CalculadoraCambio(onDismiss = { cambioAbierto = false })
    if (editorAbierto) {
        EditorPartida(
            partida = indiceEdicion?.let { vm.partidas.getOrNull(it) },
            onDismiss = { editorAbierto = false },
            onGuardar = { partida -> vm.guardarPartida(indiceEdicion, partida); editorAbierto = false },
        )
    }
    indiceEliminar?.let { indice ->
        ShalevDialog(
            title = "¿Eliminar partida?",
            text = "Se quitará del cálculo temporal.",
            onDismiss = { indiceEliminar = null },
            onConfirm = { vm.eliminarPartida(indice); indiceEliminar = null },
            confirmDescription = "Eliminar",
            destructive = true,
        )
    }
    if (confirmarSalida) ShalevDialog(
        title = "¿Salir del cálculo?",
        text = "La ficha temporal y sus partidas se perderán.",
        onDismiss = { confirmarSalida = false },
        onConfirm = onBack,
        confirmDescription = "Salir sin guardar",
        confirmIcon = ShalevIcons.Salir,
        destructive = true,
    )
}

@Composable
private fun EditorPartida(partida: PartidaCosto?, onDismiss: () -> Unit, onGuardar: (PartidaCosto) -> Unit) {
    var descripcion by rememberSaveable { mutableStateOf(partida?.descripcion.orEmpty()) }
    var cantidad by rememberSaveable { mutableStateOf(partida?.cantidad?.toPlainString() ?: "1") }
    var unidad by rememberSaveable { mutableStateOf(partida?.unidad ?: "unidad") }
    var costo by rememberSaveable { mutableStateOf(partida?.costoUnitario?.toPlainString() ?: "0") }
    var categoria by rememberSaveable { mutableStateOf(partida?.categoria ?: CategoriaCosto.MATERIAS) }
    var moneda by rememberSaveable { mutableStateOf(partida?.moneda ?: MonedaCosto.CUP) }
    var intento by rememberSaveable { mutableStateOf(false) }
    val cantidadDecimal = CalculoFichaCosto.numero(cantidad)
    val costoDecimal = CalculoFichaCosto.numero(costo)
    ShalevBottomSheet(
        title = if (partida == null) "Nueva partida" else "Editar partida",
        onDismiss = onDismiss,
        sinGuardar = true,
        footer = {
            ShalevIconAction(ShalevIcons.Guardar, "Confirmar partida", onClick = {
                intento = true
                if (descripcion.isNotBlank() && unidad.isNotBlank() && cantidadDecimal != null && costoDecimal != null) {
                    onGuardar(PartidaCosto(descripcion.trim(), categoria, cantidadDecimal, unidad.trim(), costoDecimal, moneda))
                }
            }, style = IconActionStyle.Filled)
        },
    ) {
        ShalevComboBox("Categoría", CategoriaCosto.entries, categoria, { it.titulo }, { categoria = it })
        ShalevTextField(descripcion, { descripcion = it }, label = "Descripción", filtro = FiltroEntrada.NOMBRE,
            isError = intento && descripcion.isBlank(), errorText = "Escribe una descripción.")
        ShalevTextField(cantidad, { cantidad = it }, label = "Cantidad", filtro = FiltroEntrada.DECIMAL,
            isError = intento && cantidadDecimal == null, errorText = "Escribe una cantidad válida.")
        ShalevTextField(unidad, { unidad = it }, label = "Unidad de medida", filtro = FiltroEntrada.NOMBRE,
            isError = intento && unidad.isBlank(), errorText = "Escribe una unidad.")
        ShalevTextField(costo, { costo = it }, label = "Costo unitario", filtro = FiltroEntrada.DECIMAL,
            isError = intento && costoDecimal == null, errorText = "Escribe un costo válido.")
        ShalevComboBox("Moneda", MonedaCosto.entries, moneda, { it.name }, { moneda = it })
    }
}
