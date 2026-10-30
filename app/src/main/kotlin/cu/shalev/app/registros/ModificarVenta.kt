package cu.shalev.app.registros

import cu.shalev.app.common.SecureDialogWindow
import cu.shalev.designsystem.theme.ShalevTextos
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cu.shalev.app.venta.CampoCliente
import cu.shalev.app.venta.Carrito
import cu.shalev.app.venta.CatalogoVenta
import cu.shalev.app.venta.FormCliente
import cu.shalev.app.venta.LineaCarrito
import cu.shalev.app.venta.LineaEditable
import cu.shalev.app.venta.SelectorMetodo
import cu.shalev.app.venta.TextosVenta
import cu.shalev.app.venta.camposCliente
import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
import cu.shalev.core.money.sumOfCup
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.Anulacion
import cu.shalev.domain.model.ClaseArticulo
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.IdArticulo
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Venta
import cu.shalev.domain.model.comoProducto
import cu.shalev.domain.model.nombreCompleto
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.repository.ProductoRepository
import cu.shalev.domain.service.InventarioFiltro
import cu.shalev.domain.usecase.DatosTransferencia
import cu.shalev.domain.usecase.ModificarVenta
import cu.shalev.domain.usecase.ObservarElaborados
import cu.shalev.domain.usecase.ObservarServicios
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 0.25.1 (B): textos de la pantalla Modificar venta. */
object TextosModificar {
    const val TEXTO = "Cambia cantidades, quita o añade artículos y elige el método de pago. Lo que ya estaba conserva su precio de " +
        "entonces; lo nuevo, el precio actual."
    const val ANADIR = "Añadir artículo"
    const val BUSCAR = "Buscar por nombre o descripción"
    const val SIN_RESULTADOS = "No hay artículos disponibles con ese nombre."
    const val CERRAR_BUSQUEDA = "Terminar de añadir"
    const val CLIENTE = "Datos de la transferencia"
    const val TRANSFERENCIA_INCOMPLETA = "Completa los datos de la transferencia (el número de transacción es obligatorio)."
    const val SIN_CAMBIOS = "No hay cambios que guardar."
    const val NUEVO = "Nuevo"
    fun total(t: Cup) = "Total estimado: ${Money.format(t)}"
}

object ModificarTags {
    const val PANTALLA = "modificar_pantalla"
    const val ANADIR = "modificar_anadir"
    const val BUSCAR = "modificar_buscar"
    const val GUARDAR = "modificar_guardar"
    const val CERRAR = "modificar_cerrar"
    const val TOTAL = "modificar_total"
    const val ERROR = "modificar_error"
    fun resultado(id: Long) = "modificar_resultado_$id"
}

/**
 * 0.25.1 (B): lógica pura de la edición. Las claves son las del carrito de Nueva venta: producto o Elaborado = su id,
 * insumo vendible = id negativo ([IdArticulo]), servicio = su id (una venta es de productos o de servicios, no de ambos).
 */
object EdicionVenta {
    fun clave(d: DetalleVenta): Long = if (d.clase == ClaseArticulo.INSUMO) IdArticulo.deInsumo(d.productoId) else d.productoId

    fun esDeServicios(v: Venta): Boolean = v.detalles.any { it.clase == ClaseArticulo.SERVICIO }

    /** Cantidades de la venta original, en su orden. */
    fun inicial(v: Venta): Map<Long, Long> = v.detalles.associate { clave(it) to it.cantidad }

    /**
     * Filas para [LineaEditable]. Las originales muestran su precio de entonces y su tope suma lo que ya se había vendido
     * (vuelve al inventario al guardar); un artículo que ya no existe se muestra con los datos de la venta.
     */
    fun lineas(
        v: Venta,
        cantidades: Map<Long, Long>,
        catalogo: Map<Long, Producto>,
        alcanza: Map<Long, Long>,
    ): List<LineaCarrito> {
        val servicios = esDeServicios(v)
        val originales = v.detalles.associateBy { clave(it) }
        return cantidades.mapNotNull { (k, n) ->
            val d = originales[k]
            val base = catalogo[k]?.takeUnless { it.eliminado } ?: d?.let { provisional(k, it) } ?: return@mapNotNull null
            val p = if (d != null) base.copy(precioVenta = d.precioUnitario, cantidad = base.cantidad.coerceAtLeast(0) + d.cantidad) else base
            val tope = alcanza[k]
            when {
                servicios -> LineaCarrito(p, n, tope, ClaseArticulo.SERVICIO)
                p.esElaborado -> LineaCarrito(p, n, tope ?: (d?.cantidad ?: 0))
                else -> LineaCarrito(p, n, tope)
            }
        }
    }

    private fun provisional(k: Long, d: DetalleVenta) = Producto(
        id = k, categoria = d.categoria, nombre = d.nombre, precioCosto = d.costoUnitario, precioVenta = d.precioUnitario,
        cantidad = 0, creadoEn = Instant.EPOCH,
    )

    fun total(lineas: List<LineaCarrito>): Cup = lineas.sumOfCup { it.subtotalEstimado }

    /** Añadir desde el buscador: +1 si ya está (sin pasar del tope), si no una línea nueva con 1. */
    fun anadir(cantidades: Map<Long, Long>, k: Long, maximo: Long): Map<Long, Long> {
        val actual = cantidades[k] ?: 0
        if (actual >= maximo) return cantidades
        return cantidades + (k to actual + 1)
    }

    /** Lo que se puede añadir: disponible (con existencias o lo que alcanza) y que coincide con [q] en nombre o descripción. */
    fun resultados(catalogo: Map<Long, Producto>, alcanza: Map<Long, Long>, servicios: Boolean, q: String, max: Int = 30): List<Producto> {
        val plano = InventarioFiltro.plano(q)
        return catalogo.values.asSequence()
            .filterNot { it.eliminado }
            .filter { p ->
                when {
                    servicios -> (alcanza[p.id] ?: Carrito.MAX_CANTIDAD) > 0
                    p.esElaborado -> (alcanza[p.id] ?: 0) > 0
                    else -> p.cantidad > 0
                }
            }
            .filter { p -> plano.isBlank() || InventarioFiltro.plano(p.nombre).contains(plano) || p.descripcion?.let { InventarioFiltro.plano(it).contains(plano) } == true }
            .sortedBy { InventarioFiltro.plano(it.nombreCompleto) }
            .take(max)
            .toList()
    }

    fun clienteInicial(v: Venta): FormCliente = v.transaccion?.let { t ->
        FormCliente(nombre = t.cliente.nombreApellidos, ci = t.cliente.ci, telefono = t.cliente.telefono, numero = t.numero)
    } ?: FormCliente()

    fun cambiada(v: Venta, cantidades: Map<Long, Long>, metodo: MetodoPago, cliente: FormCliente): Boolean =
        cantidades != inicial(v) || metodo != v.metodoPago ||
            (metodo == MetodoPago.TRANSFERENCIA && cliente.copy(intento = false, tocados = emptySet()) != clienteInicial(v))
}

data class ModificarVentaUiState(
    val venta: Venta? = null,
    val cantidades: Map<Long, Long> = emptyMap(),
    val lineas: List<LineaCarrito> = emptyList(),
    val metodo: MetodoPago = MetodoPago.EFECTIVO,
    val cliente: FormCliente = FormCliente(),
    val motivo: String = "",
    /** Buscador de «Añadir artículo» abierto y su texto. */
    val buscando: Boolean = false,
    val busqueda: String = "",
    /** Lo que se puede añadir, como fila de carrito con 1 unidad (para saber su tope). */
    val resultados: List<LineaCarrito> = emptyList(),
    val error: String? = null,
    val trabajando: Boolean = false,
) {
    val total: Cup get() = EdicionVenta.total(lineas)
    val cambiada: Boolean get() = venta != null && EdicionVenta.cambiada(venta, cantidades, metodo, cliente)
    val puedeGuardar: Boolean get() = cambiada && lineas.isNotEmpty() && ModificacionLogic.motivoValido(motivo) && !trabajando
}

/**
 * 0.25.1 (B): Modificar venta a pantalla completa. Reutiliza [ModificarVenta] (que ya aceptaba líneas nuevas, otro método
 * y los datos de la transferencia) y las piezas de Nueva venta. Solo principal y turno abierto (lo comprueba
 * [VentaAccionesViewModel] antes de abrirla y el caso de uso al guardar).
 */
@HiltViewModel
class ModificarVentaViewModel @Inject constructor(
    productosRepo: ProductoRepository,
    observarElaborados: ObservarElaborados,
    insumosRepo: InsumoRepository,
    observarServicios: ObservarServicios,
    private val modificarVenta: ModificarVenta,
) : ViewModel() {

    private val local = MutableStateFlow(ModificarVentaUiState())
    private val mensajes = Channel<String>(Channel.BUFFERED)
    /** Mensaje final tras guardar (la pantalla se cierra). */
    val hecho: Flow<String> = mensajes.receiveAsFlow()

    private val productos: Flow<CatalogoVenta> =
        combine(productosRepo.observarTodos(), observarElaborados(), insumosRepo.observarTodos()) { l, es, xs ->
            CatalogoVenta.deProductos(l, es, xs)
        }.catch { emit(CatalogoVenta(servicios = false)) }

    private val servicios: Flow<CatalogoVenta> =
        observarServicios.disponibles().map(CatalogoVenta::deServicios).catch { emit(CatalogoVenta(servicios = true)) }

    val state: StateFlow<ModificarVentaUiState> = combine(local, productos, servicios) { l, ps, ss ->
        val v = l.venta ?: return@combine l
        val esServ = EdicionVenta.esDeServicios(v)
        val cat = if (esServ) ss else ps
        val devueltos = EdicionVenta.inicial(v)
        val topes = Carrito.topesCompartidos(l.cantidades, cat, devueltos)
        val topesParaAnadir = topes.mapValues { (k, topeTotal) -> (topeTotal - (l.cantidades[k] ?: 0L)).coerceAtLeast(0L) }
        l.copy(
            lineas = EdicionVenta.lineas(v, l.cantidades, cat.articulos, topes),
            resultados = if (!l.buscando) emptyList() else EdicionVenta.resultados(cat.articulos, topesParaAnadir, esServ, l.busqueda)
                .mapNotNull { p -> EdicionVenta.lineas(v, mapOf(p.id to 1L), cat.articulos, topes).firstOrNull() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModificarVentaUiState())

    fun iniciar(v: Venta) {
        if (local.value.venta?.id == v.id) return
        local.value = ModificarVentaUiState(
            venta = v, cantidades = EdicionVenta.inicial(v), metodo = v.metodoPago, cliente = EdicionVenta.clienteInicial(v),
        )
    }

    fun cambiarCantidad(k: Long, n: Long) {
        val max = state.value.lineas.firstOrNull { it.producto.id == k }?.maximo ?: Carrito.MAX_CANTIDAD
        local.update { it.copy(cantidades = it.cantidades + (k to n.coerceIn(1, max.coerceAtLeast(1))), error = null) }
    }

    fun quitar(k: Long) = local.update { it.copy(cantidades = it.cantidades - k, error = null) }

    fun abrirBusqueda() = local.update { it.copy(buscando = true, busqueda = "") }
    fun cerrarBusqueda() = local.update { it.copy(buscando = false, busqueda = "") }
    fun buscar(q: String) = local.update { it.copy(busqueda = q.take(40)) }

    fun anadir(r: LineaCarrito) {
        val max = state.value.lineas.firstOrNull { it.producto.id == r.producto.id }?.maximo ?: r.maximo
        local.update { it.copy(cantidades = EdicionVenta.anadir(it.cantidades, r.producto.id, max), error = null) }
    }

    fun elegirMetodo(m: MetodoPago) = local.update { it.copy(metodo = m, error = null) }
    fun editarCliente(c: CampoCliente, valor: String) = local.update { it.copy(cliente = it.cliente.con(c, valor), error = null) }
    fun editarMotivo(m: String) = local.update { it.copy(motivo = m.take(Anulacion.MOTIVO_MAX)) }

    fun guardar() {
        val s = state.value
        val v = s.venta ?: return
        if (s.trabajando) return
        if (!s.cambiada) { local.update { it.copy(error = TextosModificar.SIN_CAMBIOS) }; return }
        if (s.lineas.isEmpty()) { local.update { it.copy(error = TextosAnulacion.SIN_LINEAS) }; return }
        val transferencia = if (s.metodo == MetodoPago.TRANSFERENCIA) {
            if (s.cliente.errores().isNotEmpty()) {
                local.update { it.copy(cliente = it.cliente.copy(intento = true), error = TextosModificar.TRANSFERENCIA_INCOMPLETA) }
                return
            }
            DatosTransferencia(s.cliente.datos(), s.cliente.numero)
        } else null
        val lineas = Carrito.solicitud(s.lineas)
        viewModelScope.launch {
            local.update { it.copy(trabajando = true, error = null) }
            val r = try {
                modificarVenta(v.id, lineas, s.metodo, transferencia, s.motivo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppResult.Err(AppError.Desconocido())
            }
            local.update { it.copy(trabajando = false) }
            when (r) {
                is AppResult.Ok -> mensajes.trySend(TextosAnulacion.modificada(Money.format(s.total)))
                is AppResult.Err -> local.update { it.copy(error = mensajeError(r.error)) }
            }
        }
    }

    companion object {
        fun mensajeError(e: AppError): String = when {
            e == AppError.TurnoCerrado -> TextosAnulacion.TURNO_CERRADO
            (e as? AppError.Validacion)?.campo == "lineas" -> TextosAnulacion.SIN_LINEAS
            (e as? AppError.Validacion)?.campo == "transferencia" -> TextosModificar.TRANSFERENCIA_INCOMPLETA
            e is AppError.StockInsuficiente -> TextosVenta.mensaje(e)
            else -> TextosAnulacion.ERROR
        }
    }
}

/** 0.25.1 (B): pantalla completa sobre la ficha. [onHecho] recibe el mensaje final (la ficha se cierra y se recarga). */
@Composable
fun ModificarVentaPantalla(v: Venta, onCerrar: () -> Unit, onHecho: (String) -> Unit, vm: ModificarVentaViewModel = hiltViewModel()) {
    LaunchedEffect(v.id) { vm.iniciar(v) }
    LaunchedEffect(vm) { vm.hecho.collect(onHecho) }
    val s by vm.state.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        SecureDialogWindow()
        Surface(Modifier.fillMaxSize()) {
            ModificarVentaContent(
                s,
                AccionesModificar(
                    onCerrar = onCerrar, onGuardar = vm::guardar, onCantidad = vm::cambiarCantidad, onQuitar = vm::quitar,
                    onAbrirBusqueda = vm::abrirBusqueda, onCerrarBusqueda = vm::cerrarBusqueda, onBuscar = vm::buscar, onAnadir = vm::anadir,
                    onMetodo = vm::elegirMetodo, onCampo = vm::editarCliente, onMotivo = vm::editarMotivo,
                ),
            )
        }
    }
}

class AccionesModificar(
    val onCerrar: () -> Unit = {},
    val onGuardar: () -> Unit = {},
    val onCantidad: (Long, Long) -> Unit = { _, _ -> },
    val onQuitar: (Long) -> Unit = {},
    val onAbrirBusqueda: () -> Unit = {},
    val onCerrarBusqueda: () -> Unit = {},
    val onBuscar: (String) -> Unit = {},
    val onAnadir: (LineaCarrito) -> Unit = {},
    val onMetodo: (MetodoPago) -> Unit = {},
    val onCampo: (CampoCliente, String) -> Unit = { _, _ -> },
    val onMotivo: (String) -> Unit = {},
)

@Composable
fun ModificarVentaContent(s: ModificarVentaUiState, a: AccionesModificar) {
    Scaffold(
        // 0.32.0 (A11): con el teclado abierto (datos del cliente, motivo) la lista se encoge y los campos
        // quedan por encima del teclado; edge-to-edge no redimensiona la ventana por sí solo.
        modifier = Modifier.testTag(ModificarTags.PANTALLA).imePadding(),
        topBar = {
            ShalevTopBar(title = TextosAnulacion.MODIFICAR, onBack = a.onCerrar, actions = {
                ShalevIconAction(
                    ShalevIcons.Confirmar, "Guardar", onClick = a.onGuardar, enabled = s.puedeGuardar,
                    style = IconActionStyle.Filled, modifier = Modifier.testTag(ModificarTags.GUARDAR),
                )
            })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(ShalevSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            item { ShalevSecondaryText(TextosModificar.TEXTO, maxLines = 4) }
            items(s.lineas, key = { "l${it.producto.id}" }) { l -> LineaEditable(l, a.onCantidad, a.onQuitar) }
            if (s.lineas.isEmpty()) item { ShalevSecondaryText(TextosAnulacion.SIN_LINEAS, maxLines = 2) }
            if (s.buscando) {
                item {
                    ShalevTextField(
                        value = s.busqueda, onValueChange = a.onBuscar, label = TextosModificar.BUSCAR,
                        anchoCompleto = true,
            modifier = Modifier.fillMaxWidth().testTag(ModificarTags.BUSCAR),
                    )
                }
                items(s.resultados, key = { "r${it.producto.id}" }) { r ->
                    val p = r.producto
                    ShalevListItem(
                        title = p.nombreCompleto,
                        subtitleResaltado = ShalevTextos.datoTexto(Money.format(p.precioVenta)),
                        indicatorColor = null,
                        onClick = { a.onAnadir(r) },
                        trailing = { ShalevIconAction(ShalevIcons.Agregar, "Añadir ${p.nombreCompleto}", etiqueta = "Añadir", onClick = { a.onAnadir(r) }) },
                        modifier = Modifier.testTag(ModificarTags.resultado(p.id)),
                    )
                }
                if (s.resultados.isEmpty()) item { ShalevSecondaryText(TextosModificar.SIN_RESULTADOS) }
                item {
                    ShalevButtonRow { ShalevSecondaryButton(TextosModificar.CERRAR_BUSQUEDA, onClick = a.onCerrarBusqueda, icon = ShalevIcons.Listo) }
                }
            } else {
                item {
                    ShalevButtonRow {
                        ShalevSecondaryButton(
                            TextosModificar.ANADIR, onClick = a.onAbrirBusqueda, icon = ShalevIcons.AgregarCarrito,
                            modifier = Modifier.testTag(ModificarTags.ANADIR),
                        )
                    }
                }
            }
            item {
                Text(
                    // 0.28.0: solo el importe («Total estimado: 1 250.00 CUP») va en seminegrita.
                    ShalevTextos.resaltar(TextosModificar.total(s.total), Money.format(s.total)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = ShalevSpacing.xs).testTag(ModificarTags.TOTAL),
                )
            }
            item { SelectorMetodo(s.metodo, a.onMetodo) }
            if (s.metodo == MetodoPago.TRANSFERENCIA) {
                item { Text(TextosModificar.CLIENTE, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = ShalevSpacing.md).semantics { heading() }) }
                camposCliente(s.cliente, a.onCampo)
            }
            item {
                ShalevTextField(
                    value = s.motivo, onValueChange = a.onMotivo, label = "${TextosAnulacion.MOTIVO} *",
                    supportingText = TextosAnulacion.MOTIVO_AYUDA, modifier = Modifier.padding(top = ShalevSpacing.md).testTag(VentaAccionesTags.MOTIVO),
                )
            }
            s.error?.let { e ->
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Text(
                            e, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag(ModificarTags.ERROR).semantics { liveRegion = LiveRegionMode.Assertive },
                        )
                    }
                }
            }
        }
    }
}
