package cu.shalev.app.herramientas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.common.LocalPermisosApp
import cu.shalev.core.money.Money
import cu.shalev.core.quantity.Cantidad
import cu.shalev.designsystem.component.*
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.ArticuloGestion
import cu.shalev.domain.model.MotivoMovimiento
import cu.shalev.domain.model.TipoEntidad
import java.time.LocalDate

/** Recepción en tres pasos; el registro completo se confirma una sola vez, en una transacción. */
@Composable
fun GestionInventarioScreen(
    onBack: () -> Unit,
    tipoInicial: String? = null,
    articuloIdInicial: Long? = null,
    cantidadInicial: String? = null,
    costoInicial: String? = null,
    vm: GestionInventarioViewModel = hiltViewModel(),
) {
    val articulos by vm.articulos.collectAsStateWithLifecycle()
    val recepciones by vm.recepciones.collectAsStateWithLifecycle()
    val lotes by vm.lotes.collectAsStateWithLifecycle()
    var recepcionInicialPreparada by rememberSaveable { mutableStateOf(false) }
    val catalogoCargado = vm.catalogoCargado
    LaunchedEffect(articulos, tipoInicial, articuloIdInicial, catalogoCargado) {
        val entidad = tipoInicial?.let { runCatching { TipoEntidad.valueOf(it) }.getOrNull() }
        if (articuloIdInicial == null) {
            vm.actualizarCatalogo(articulos)
        } else if (catalogoCargado) {
            val inicial = articulos.firstOrNull { it.entidad == entidad && it.id == articuloIdInicial }
            if (inicial != null && !recepcionInicialPreparada && vm.articulo == null) {
                vm.prepararRecepcionInicial(inicial, cantidadInicial, costoInicial)
                recepcionInicialPreparada = true
            } else if (recepcionInicialPreparada) {
                vm.actualizarCatalogo(articulos)
            }
        }
    }
    var seccion by rememberSaveable { mutableStateOf("Recepción") }
    var salir by rememberSaveable { mutableStateOf(false) }
    val volver = { if (!vm.ocupado) { if (vm.tieneDatos) salir = true else onBack() } }
    BackHandler { volver() }
    val principal = !LocalPermisosApp.current.esSecundaria
    Scaffold(topBar = { ShalevTopBar("Gestión de inventario", onBack = volver) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(ShalevSpacing.md), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md)) {
            if (vm.hayErrorCarga) item { ShalevSecondaryButton("Reintentar carga", vm::reintentarCarga, ShalevIcons.Reintentar, mostrarTexto = true) }
            item { ShalevComboBox("Ver", listOf("Recepción", "Ajuste con motivo", "Lotes y caducidades", "Recepciones registradas"), seccion, { it }, { seccion = it }) }
            if (articuloIdInicial != null && seccion == "Recepción") item {
                val pendiente = articulos.firstOrNull { it.id == articuloIdInicial && it.entidad.name == tipoInicial }
                ShalevCard(tone = CardTone.Tonal) {
                    Text("Recepción inicial")
                    ShalevSecondaryText(
                        when {
                            pendiente != null -> "${pendiente.nombre} se guardó con existencia cero. Completa y confirma la recepción para registrar la cantidad, el costo y el lote."
                            !catalogoCargado && !vm.hayErrorCarga -> "Buscando el artículo recién guardado en el catálogo del local…"
                            else -> "No se encontró el artículo en este local. Revisa el local seleccionado antes de registrar la compra."
                        },
                    )
                }
            }
            if (!principal) item { ShalevSecondaryText("Las recepciones, lotes y ajustes con motivo se gestionan en la app principal.") }
            else when (seccion) {
                "Recepción" -> {
                    item { ShalevStepper(vm.paso, 3, when (vm.paso) { 1 -> "Origen de la compra"; 2 -> "Artículos y lotes"; else -> "Revisar recepción" },
                        onAtras = if (vm.paso > 1) ({ vm.paso-- }) else null,
                        onSiguiente = if (vm.paso < 3) ({ vm.paso++ }) else null) }
                    if (vm.paso == 1) {
                        item { ShalevTextField(vm.proveedor, { vm.proveedor = it }, "Proveedor") }
                        item { ShalevTextField(vm.comprobante, { vm.comprobante = it }, "Referencia del comprobante") }
                        item { ShalevTextField(vm.motivo, { vm.motivo = it }, "Motivo (3–120 caracteres)") }
                        item { ShalevSecondaryText("El comprobante se registra como referencia de texto; no se fotografía ni se adjunta un archivo.") }
                    }
                    if (vm.paso == 2) {
                        item {
                            vm.articulo?.let { seleccionado -> ShalevComboBox("Artículo del local", articulos, seleccionado, { "${it.nombre} · ${it.unidad.simbolo}" }, vm::elegir) }
                                ?: ShalevSecondaryText(
                                    when {
                                        articuloIdInicial != null && !catalogoCargado && !vm.hayErrorCarga -> "Buscando el artículo recién guardado en el catálogo del local…"
                                        articuloIdInicial != null -> "No se encontró el artículo de la compra inicial en este local. Revisa el local antes de continuar."
                                        articulos.isEmpty() -> "Crea primero un producto o un insumo en este local."
                                        else -> "Elige un artículo del catálogo del local."
                                    },
                                )
                        }
                        item { ShalevTextField(vm.cantidad, { vm.cantidad = it }, "Cantidad (${vm.articulo?.unidad?.simbolo.orEmpty()})", filtro = FiltroEntrada.DECIMAL) }
                        item { ShalevTextField(vm.costo, { vm.costo = it }, "Costo por ${vm.articulo?.unidad?.simbolo.orEmpty()} (CUP)", filtro = FiltroEntrada.DECIMAL) }
                        item { ShalevTextField(vm.codigoLote, { vm.codigoLote = it }, "Código de lote") }
                        item { ShalevTextField(vm.caducidad, { vm.caducidad = it }, "Caducidad opcional (AAAA-MM-DD)") }
                        item { ShalevSecondaryButton("Agregar línea", vm::agregarLinea, ShalevIcons.Agregar, enabled = !vm.ocupado, mostrarTexto = true) }
                    }
                    if (vm.paso >= 2) itemsIndexed(vm.lineas) { indice, linea ->
                        ShalevCard {
                            Text("${articulos.firstOrNull { it.entidad == linea.entidad && it.id == linea.id }?.nombre ?: "Artículo no disponible"} · ${Cantidad.format(linea.cantidad)} · ${Money.format(linea.costoUnitario)} por unidad de medida")
                            ShalevSecondaryText("Lote ${linea.lote} · Caducidad: ${linea.caducidad ?: "sin fecha"}")
                            if (vm.paso == 2) ShalevIconAction(ShalevIcons.Eliminar, "Quitar línea ${indice + 1}", { vm.quitarLinea(indice) })
                        }
                    }
                    if (vm.paso == 3) {
                        item { ShalevSecondaryText("${vm.proveedor} · ${vm.comprobante}\n${vm.motivo}\nEl costo se actualiza por promedio ponderado. Las ventas anteriores conservan su costo original.") }
                        item { ShalevPrimaryButton("Confirmar recepción", vm::confirmarRecepcion, ShalevIcons.Guardar, Modifier.fillMaxWidth(), enabled = !vm.ocupado && vm.lineas.isNotEmpty(), mostrarTexto = true) }
                    }
                }
                "Ajuste con motivo" -> {
                    item {
                            vm.articulo?.let { seleccionado -> ShalevComboBox("Artículo del local", articulos, seleccionado, { "${it.nombre} · ${it.unidad.simbolo}" }, vm::elegir) }
                                ?: ShalevSecondaryText("Crea primero un producto o un insumo en este local.")
                        }
                    item { ShalevComboBox("Tipo de cambio", listOf(true, false), vm.esSalida, { if (it) "Salida" else "Entrada" }, { vm.esSalida = it }) }
                    item { ShalevComboBox("Motivo", MotivoMovimiento.entries.filter { it != MotivoMovimiento.COMPRA }, vm.motivoAjuste, { it.titulo }, { vm.motivoAjuste = it }) }
                    item { ShalevTextField(vm.cantidad, { vm.cantidad = it }, "Cantidad del cambio", filtro = FiltroEntrada.DECIMAL) }
                    item { ShalevTextField(vm.motivo, { vm.motivo = it }, "Explicación (3–120 caracteres)") }
                    item { ShalevPrimaryButton("Registrar ajuste", vm::confirmarAjuste, ShalevIcons.Guardar, enabled = !vm.ocupado, mostrarTexto = true) }
                }
                "Lotes y caducidades" -> {
                    item { ShalevSecondaryText("Asignación contable FEFO: primero los lotes con caducidad más cercana. El saldo previo o aumentado sin recepción puede no tener lote. En ventas offline se asignan los lotes disponibles al sincronizar; verifica físicamente qué lote entregas.") }
                    if (lotes.isEmpty()) item { Text("Todavía no hay lotes registrados en este local.") }
                    items(lotes, key = { it.id }) { l -> ShalevCard {
                        Text("${l.nombre} · Lote ${l.codigo}")
                        Text("Disponible: ${Cantidad.format(l.disponible)} ${l.unidad} de ${Cantidad.format(l.inicial)} ${l.unidad}")
                        ShalevSecondaryText("Caducidad: ${l.caducidad ?: "sin fecha"}${if (l.caducidad?.isBefore(LocalDate.now()) == true && l.disponible.milesimas > 0) " · VENCIDO: revisar" else ""}")
                    } }
                }
                else -> {
                    if (recepciones.isEmpty()) item { Text("Todavía no hay recepciones en este local.") }
                    items(recepciones, key = { it.uuid }) { r -> ShalevCard {
                        Text("${r.proveedor} · ${Money.format(r.total)}")
                        ShalevSecondaryText("${r.comprobante} · ${r.fecha}\n${r.autor} · ${r.motivo}")
                    } }
                }
            }
        }
    }
    vm.aviso?.let { ShalevDialog("Gestión de inventario", onDismiss = { vm.aviso = null }, onConfirm = { vm.aviso = null }) { Text(it) } }
    if (salir) ShalevDialog("¿Salir sin guardar?", onDismiss = { salir = false }, onConfirm = onBack, confirmDescription = "Confirmar", dismissDescription = "Cancelar") { Text("Se descartará la recepción o el ajuste que todavía no confirmaste.") }
}
