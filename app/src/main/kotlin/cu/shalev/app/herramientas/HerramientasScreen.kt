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
import androidx.compose.material3.Icon
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
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevAccordionCard
import cu.shalev.designsystem.component.ShalevBottomSheet
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevComboBox
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.core.money.Cup
import cu.shalev.domain.fichacosto.CalculoFichaCosto
import cu.shalev.domain.fichacosto.CategoriaCosto
import cu.shalev.domain.fichacosto.MonedaCosto
import cu.shalev.domain.fichacosto.PartidaCosto
import cu.shalev.domain.fichacosto.UnidadMargen
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.launch

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
                ShalevListItem("Fichas de costo", subtitle = "Crea, guarda y exporta varias fichas", leading = { Icon(ShalevIcons.FichaCosto, null) }, trailing = { Icon(ShalevIcons.Abrir, null) }, indicatorColor = null)
            }
            ShalevCard(onClick = { cambioAbierto = true }) {
                ShalevListItem("Calculadora de cambio", subtitle = "Convertir CUP y USD o calcular una tasa", leading = { Icon(ShalevIcons.CalculadoraCambio, null) }, trailing = { Icon(ShalevIcons.Abrir, null) }, indicatorColor = null)
            }
        }
    }
    if (cambioAbierto) CalculadoraCambio({ cambioAbierto = false })
    if (ayuda) cu.shalev.app.ayuda.DialogoAyudaPestana(cu.shalev.app.ayuda.PestanaAyuda.HERRAMIENTAS, modulos, { ayuda = false }) { ayuda = false; onVerManual() }
}

private fun enteroDinero(valor: BigDecimal): String = valor.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
private fun totalCup(ficha: FichaCostoGuardada): BigDecimal? {
    val totales = CalculoFichaCosto.calcular(ficha.partidas, CalculoFichaCosto.numero(ficha.margen), ficha.unidadMargen)
    val tasa = CalculoFichaCosto.numero(ficha.tasaCupPorUsd)
    val cup = totales.precioEstimado?.get(MonedaCosto.CUP) ?: totales.costoTotal.getValue(MonedaCosto.CUP)
    val usd = totales.precioEstimado?.get(MonedaCosto.USD) ?: totales.costoTotal.getValue(MonedaCosto.USD)
    return if (usd.signum() == 0) cup else tasa?.let { cup + usd.multiply(it) }
}

@Composable
fun FichaCostoScreen(
    onBack: () -> Unit,
    onExportarInventario: (nombre: String, costoCup: String, precioCup: String) -> Unit = { _, _, _ -> },
    onExportarServicio: (nombre: String, importeCup: String) -> Unit = { _, _ -> },
    vm: FichaCostoViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    var editorAbierto by rememberSaveable { mutableStateOf(false) }
    var indiceEdicion by rememberSaveable { mutableStateOf<Int?>(null) }
    var indiceEliminar by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmarSalida by rememberSaveable { mutableStateOf(false) }
    var cambioAbierto by rememberSaveable { mutableStateOf(false) }
    var busqueda by rememberSaveable { mutableStateOf("") }
    var aviso by rememberSaveable { mutableStateOf<String?>(null) }
    var fichasExpandidas by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var totalesExpandidos by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(vm.error) { vm.error?.let { aviso = it; vm.descartarError() } }
    val margen = CalculoFichaCosto.numero(vm.margen)
    val totales = CalculoFichaCosto.calcular(vm.partidas, margen, vm.unidadMargen)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val salir = { if (vm.tieneDatos && vm.idEdicion == null) confirmarSalida = true else onBack() }
    BackHandler(enabled = !editorAbierto && indiceEliminar == null && !confirmarSalida && !cambioAbierto) { salir() }

    Scaffold(modifier = Modifier.imePadding(), topBar = {
        ShalevTopBar("Fichas de costo", onBack = salir, actions = { ShalevIconAction(ShalevIcons.CalculadoraCambio, "Calculadora de cambio", onClick = { cambioAbierto = true }) })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(ShalevSpacing.md), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md)) {
            item { ShalevTextField(busqueda, { busqueda = it }, label = "Buscar ficha", leadingIcon = ShalevIcons.Buscar, anchoCompleto = true) }
            val visibles = vm.fichas.filter { busqueda.isBlank() || it.nombre.contains(busqueda, ignoreCase = true) }
            if (visibles.isNotEmpty()) item { Text("Fichas guardadas", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(visibles, key = { _, f -> f.id }) { _, ficha ->
                val total = totalCup(ficha)
                ShalevAccordionCard(
                    title = ficha.nombre,
                    subtitle = total?.let { "${enteroDinero(it)} CUP" } ?: "Falta la tasa CUP por USD",
                    expanded = ficha.id in fichasExpandidas,
                    onToggle = { fichasExpandidas = if (ficha.id in fichasExpandidas) fichasExpandidas - ficha.id else fichasExpandidas + ficha.id },
                ) {
                    Text("${ficha.partidas.size} partidas")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        ShalevIconAction(ShalevIcons.Editar, "Abrir ${ficha.nombre}", onClick = { vm.abrir(ficha) })
                        ShalevIconAction(ShalevIcons.Inventario, "Exportar al Inventario", onClick = {
                            scope.launch {
                                val r = vm.exportarAInventario(ficha)
                                aviso = r.getOrNull() ?: r.exceptionOrNull()?.message ?: "Error al exportar"
                            }
                        }, enabled = total != null && !vm.trabajando)
                        ShalevIconAction(ShalevIcons.Servicios, "Exportar a Servicios", onClick = {
                            scope.launch {
                                val r = vm.exportarAServicio(ficha)
                                aviso = r.getOrNull() ?: r.exceptionOrNull()?.message ?: "Error al exportar"
                            }
                        }, enabled = total != null && !vm.trabajando)
                        ShalevIconAction(ShalevIcons.Eliminar, "Eliminar ${ficha.nombre}", onClick = { scope.launch { aviso = vm.eliminarFicha(ficha.id).exceptionOrNull()?.message } })
                    }
                }
            }
            item {
                ShalevAccordionCard(
                    title = "Totales por categoría",
                    subtitle = "Costo total: ${enteroDinero(totales.costoTotal.getValue(MonedaCosto.CUP))} CUP · ${enteroDinero(totales.costoTotal.getValue(MonedaCosto.USD))} USD",
                    expanded = totalesExpandidos,
                    onToggle = { totalesExpandidos = !totalesExpandidos },
                ) {
                    CategoriaCosto.entries.forEach { c -> val v = totales.porCategoria.getValue(c); Text("${c.titulo}: ${enteroDinero(v.getValue(MonedaCosto.CUP))} CUP · ${enteroDinero(v.getValue(MonedaCosto.USD))} USD") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (vm.idEdicion == null) "Nueva ficha" else "Editando ficha", style = MaterialTheme.typography.titleMedium)
                    ShalevIconAction(ShalevIcons.Agregar, "Crear otra ficha", onClick = { vm.nueva() })
                }
            }
            item { ShalevTextField(vm.nombre, { vm.nombre = it }, label = "Producto o servicio", filtro = FiltroEntrada.NOMBRE) }
            item { Text("Partidas", style = MaterialTheme.typography.titleMedium) }
            if (vm.partidas.isEmpty()) item { Text("Todavía no hay partidas.") }
            itemsIndexed(vm.partidas) { indice, partida ->
                ShalevCard {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                        Text(partida.descripcion, style = MaterialTheme.typography.titleMedium)
                        Text("${partida.categoria.titulo} · ${partida.cantidad.toPlainString()} ${partida.unidad} × ${enteroDinero(partida.costoUnitario)} ${partida.moneda.name}")
                        Text("Importe: ${enteroDinero(partida.subtotal)} ${partida.moneda.name}", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        ShalevIconAction(ShalevIcons.Editar, "Editar ${partida.descripcion}", onClick = { indiceEdicion = indice; editorAbierto = true })
                        ShalevIconAction(ShalevIcons.Eliminar, "Eliminar ${partida.descripcion}", onClick = { indiceEliminar = indice })
                    }
                }
            }
            item {
                ShalevSecondaryButton("Agregar partida", { indiceEdicion = null; editorAbierto = true }, ShalevIcons.Agregar, Modifier.fillMaxWidth(), mostrarTexto = true)
            }
            item { ShalevTextField(vm.margen, { vm.margen = it }, label = "Margen de ganancia (opcional)", filtro = FiltroEntrada.DECIMAL, isError = vm.margen.isNotBlank() && margen == null, errorText = "Escribe un número válido.") }
            item { ShalevComboBox("Interpretar margen como", UnidadMargen.entries, vm.unidadMargen, { it.titulo }, { vm.unidadMargen = it }) }
            item { ShalevTextField(vm.tasaCupPorUsd, { vm.tasaCupPorUsd = it }, label = "Tasa CUP por 1 USD (para exportar)", filtro = FiltroEntrada.DECIMAL) }
            item {
                val precio = totales.precioEstimado
                if (precio != null) Text("Precio estimado: ${enteroDinero(precio.getValue(MonedaCosto.CUP))} CUP · ${enteroDinero(precio.getValue(MonedaCosto.USD))} USD")
            }
            item { ShalevPrimaryButton("Guardar ficha", { scope.launch { val r = vm.guardarFicha(); aviso = r.getOrNull() ?: r.exceptionOrNull()?.message } }, ShalevIcons.Guardar, Modifier.fillMaxWidth(), mostrarTexto = true) }
            aviso?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        }
    }
    if (cambioAbierto) CalculadoraCambio({ cambioAbierto = false })
    if (editorAbierto) EditorPartida(indiceEdicion?.let(vm.partidas::getOrNull), { editorAbierto = false }) { vm.guardarPartida(indiceEdicion, it); editorAbierto = false }
    indiceEliminar?.let { i -> ShalevDialog(title = "¿Eliminar partida?", text = "Se quitará de esta ficha.", onDismiss = { indiceEliminar = null }, onConfirm = { vm.eliminarPartida(i); indiceEliminar = null }, confirmDescription = "Eliminar", destructive = true) }
    if (confirmarSalida) ShalevDialog(title = "¿Salir sin guardar?", text = "La ficha nueva y sus partidas se perderán.", onDismiss = { confirmarSalida = false }, onConfirm = onBack, confirmDescription = "Salir sin guardar", confirmIcon = ShalevIcons.Salir, destructive = true)
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
    val cantidadDecimal = CalculoFichaCosto.numero(cantidad); val costoDecimal = CalculoFichaCosto.numero(costo)
    ShalevBottomSheet(onDismiss = onDismiss, title = if (partida == null) "Nueva partida" else "Editar partida", sinGuardar = true, footer = {
        ShalevIconAction(ShalevIcons.Guardar, "Confirmar partida", { intento = true; if (descripcion.isNotBlank() && unidad.isNotBlank() && cantidadDecimal != null && costoDecimal != null) onGuardar(PartidaCosto(descripcion.trim(), categoria, cantidadDecimal, unidad.trim(), costoDecimal, moneda)) }, style = IconActionStyle.Filled)
    }) {
        ShalevComboBox("Categoría", CategoriaCosto.entries, categoria, { it.titulo }, { categoria = it })
        ShalevTextField(descripcion, { descripcion = it }, label = "Descripción", filtro = FiltroEntrada.NOMBRE, isError = intento && descripcion.isBlank(), errorText = "Escribe una descripción.")
        ShalevTextField(cantidad, { cantidad = it }, label = "Cantidad", filtro = FiltroEntrada.DECIMAL, isError = intento && cantidadDecimal == null, errorText = "Escribe una cantidad válida.")
        ShalevTextField(unidad, { unidad = it }, label = "Unidad de medida", filtro = FiltroEntrada.NOMBRE, isError = intento && unidad.isBlank(), errorText = "Escribe una unidad.")
        ShalevTextField(costo, { costo = it }, label = "Costo unitario", filtro = FiltroEntrada.DECIMAL, isError = intento && costoDecimal == null, errorText = "Escribe un costo válido.")
        ShalevComboBox("Moneda", MonedaCosto.entries, moneda, { it.name }, { moneda = it })
    }
}
