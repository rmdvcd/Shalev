package cu.shalev.app.inventario

import cu.shalev.core.time.Dates
import cu.shalev.domain.model.nombreCompleto
import cu.shalev.core.result.runCatchingCancelable
import cu.shalev.domain.usecase.EliminarInsumos
import cu.shalev.domain.usecase.ObtenerFichaInsumo
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.model.comoProducto
import cu.shalev.domain.model.IdArticulo
import cu.shalev.domain.model.FichaInsumo
import cu.shalev.domain.repository.ProductoRepository
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.app.common.ArchivosApp
import cu.shalev.app.common.ExportadorArchivos
import cu.shalev.app.common.lanzarExportacion
import cu.shalev.app.common.ImagenTablaAndroid
import cu.shalev.app.inicio.AlertaPendiente
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.inicio.TipoVenta
import cu.shalev.app.navigation.Route
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.FichaProducto
import cu.shalev.domain.model.FiltroInventario
import cu.shalev.domain.model.ItemInventario
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.TipoArticulo
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.VistaInventario
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.service.TablasExport
import cu.shalev.domain.usecase.EliminarProductos
import cu.shalev.domain.usecase.ExportarFicha
import cu.shalev.domain.usecase.ExportarInventario
import cu.shalev.domain.usecase.LimpiarFotos
import cu.shalev.domain.usecase.ObservarInventario
import cu.shalev.domain.usecase.ObtenerFichaProducto
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HojaInventario { FILTRO, EXPORTAR, COMPARTIR_FICHA }

sealed interface ConfirmarEliminar {
    data class Uno(val id: Long, val nombre: String) : ConfirmarEliminar
    data class Seleccion(val cantidad: Int) : ConfirmarEliminar
}

data class InventarioUiState(
    val comentarioPromocion: String = "",
    val vista: EstadoCarga<VistaInventario> = EstadoCarga.Cargando,
    val filtro: FiltroInventario = FiltroInventario(),
    val seleccion: Set<Long> = emptySet(),
    val ficha: FichaProducto? = null,
    /** P29: ficha de un insumo (fila con id negativo). */
    val fichaInsumo: FichaInsumo? = null,
    val hoja: HojaInventario? = null,
    val confirmar: ConfirmarEliminar? = null,
    val trabajando: Boolean = false,
    /** No null = modo «elegir qué vender» (Prompt 13). */
    val modoVenta: TipoVenta? = null,
) {
    val items: List<ItemInventario> get() = (vista as? EstadoCarga.Exito)?.datos?.items.orEmpty()
    val total: Int get() = when (vista) { is EstadoCarga.Exito -> vista.datos.total; else -> 0 }
    val categorias: List<String> get() = (vista as? EstadoCarga.Exito)?.datos?.categorias.orEmpty()
    /** Filas seleccionadas, en orden de marcado, aunque no coincidan con el texto o filtro actual. */
    val elementosFijados: List<ItemInventario>
        get() {
            val datos = when (val carga = vista) {
                is EstadoCarga.Exito -> carga.datos
                is EstadoCarga.Vacio -> carga.datos
                else -> null
            } ?: return emptyList()
            val porId = datos.elementosCompletos.associateBy { it.producto.id }
            return seleccion.mapNotNull(porId::get)
        }
    val todosVisiblesSeleccionados: Boolean get() = items.isNotEmpty() && items.all { it.producto.id in seleccion }
}

sealed interface EventoInventario {
    data class Compartir(val archivos: List<File>, val mime: String, val asunto: String) : EventoInventario
    /** La pantalla abre "Guardar como" (SAF) y devuelve la URI a [InventarioViewModel.guardarEn]. */
    data class GuardarComo(val nombre: String, val mime: String) : EventoInventario
    data class Navegar(val route: Route) : EventoInventario
    data class Mensaje(val texto: String) : EventoInventario
    /** Modo venta: el usuario confirmó; vuelve a la venta con los ids elegidos (en orden de marcado). */
    data class SeleccionVenta(val ids: List<Long>) : EventoInventario
}

/**
 * Inventario: tabla cronológica con buscador, filtro, selección por checkbox, ficha, borrado y exportación.
 * El filtro llega preseleccionado desde un contador de alertas de Inicio (argumento de ruta "alerta").
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InventarioViewModel @Inject constructor(
    saved: SavedStateHandle,
    observarInventario: ObservarInventario,
    private val productosRepo: ProductoRepository,
    private val obtenerFicha: ObtenerFichaProducto,
    private val eliminarProductos: EliminarProductos,
    insumosRepo: InsumoRepository,
    private val obtenerFichaInsumo: ObtenerFichaInsumo,
    private val eliminarInsumos: EliminarInsumos,
    private val exportarInventario: ExportarInventario,
    private val exportarFicha: ExportarFicha,
    private val tarjetas: TarjetasAndroid,
    private val imagenTabla: ImagenTablaAndroid,
    private val archivos: ArchivosApp,
    private val clock: Clock,
    limpiarFotos: LimpiarFotos,
) : ViewModel() {

    private val modoVenta: TipoVenta? = saved.get<String>(KEY_VENTA)?.let { v -> TipoVenta.entries.firstOrNull { it.name == v } }
    // 0.31.0: la alerta de Inicio (si la hay) entra como filtro inicial; se consume aquí, una sola vez.
    private val filtro = MutableStateFlow(
        FiltroInventario(alerta = AlertaPendiente.consumir(), tipo = SeleccionVenta.filtroInicial(modoVenta)),
    )
    private val intento = MutableStateFlow(0)

    /** Retardo del buscador (0.30.0, F1). Los tests lo bajan a 0 para no depender del reloj virtual. */
    internal var debounceBusqueda: Long = 250
    private val seleccion = MutableStateFlow<Set<Long>>(if (modoVenta != null) SeleccionVenta.parse(saved.get<String>(KEY_SELECCION)) else emptySet())
    private val local = MutableStateFlow(InventarioUiState(modoVenta = modoVenta))
    private val eventosCh = Channel<EventoInventario>(Channel.BUFFERED)
    val eventos: Flow<EventoInventario> = eventosCh.receiveAsFlow()

    /**
     * 0.30.0 (F1): el filtro que alimenta la consulta. Lo que no es texto (alertas, tipo, «quitar filtros») pasa al
     * instante; el texto espera [debounceBusqueda] para que escribir «refresco» no dispare una consulta por tecla
     * (8 pulsaciones = 1 consulta). El filtro que ve la pantalla (`state.filtro`) sigue siendo inmediato: el campo
     * de texto no se retrasa. Con el texto vacío el retardo es 0, así que abrir la pantalla no espera nada.
     */
    private val filtroConsultado: Flow<FiltroInventario> = filtro.debounce { f -> if (f.texto.isEmpty()) 0L else debounceBusqueda }

    /**
     * 0.30.0 (F1): [filtroConsultado] + [intento] (Reintentar) disparan la consulta. Cada cambio de filtro
     * vuelve a suscribir a `observarInventario` (por flatMapLatest), por eso el debounce funciona como se espera:
     * escribiendo rápido solo llega el último valor. El estado inicial es Cargando, igual que antes.
     */
    private val consulta = combine(intento, filtroConsultado) { _, f -> f }.distinctUntilChanged()

    private val vista: StateFlow<EstadoCarga<VistaInventario>> = consulta.flatMapLatest { f ->
        observarInventario(flowOf(f))
            .map<VistaInventario, EstadoCarga<VistaInventario>> { if (it.total == 0) EstadoCarga.Vacio(it) else EstadoCarga.Exito(it) }
            .onStart { emit(EstadoCarga.Cargando) }
            .catch { emit(EstadoCarga.Error(TextosInventario.ERROR_CARGA)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoCarga.Cargando)

    /**
     * Todos los productos activos + los insumos (id negativo, P29): la selección sobrevive a búsquedas/filtros y se
     * poda si se borran.
     */
    private val todos: StateFlow<Map<Long, Producto>> = combine(productosRepo.observarTodos(), insumosRepo.observarTodos()) { l, xs ->
        l.filterNot { it.eliminado }.associateBy { it.id } + xs.map { it.comoProducto() }.associateBy { it.id }
    }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val state: StateFlow<InventarioUiState> = combine(vista, filtro, seleccion, todos, local) { v, f, s, t, l ->
        l.copy(vista = v, filtro = f, seleccion = if (t.isEmpty()) emptySet() else s.filterTo(mutableSetOf()) { it in t })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventarioUiState(filtro = filtro.value))

    /** «Enviar» y «Guardar como» (P17 S5): el formato pendiente espera la URI del selector del sistema. */
    private val exportador = ExportadorArchivos<FormatoSalida>(archivos)

    init {
        viewModelScope.launch { runCatchingCancelable { limpiarFotos() } }
    }

    // ---------------- Buscador y filtro ----------------

    fun aplicarAlertaPendiente() {
        AlertaPendiente.consumir()?.let { alerta ->
            filtro.value = FiltroInventario(alerta = alerta)
            seleccion.value = emptySet()
        }
    }

    fun buscar(texto: String) = filtro.update { it.copy(texto = texto.take(80)) }
    fun aplicarFiltro(f: FiltroInventario) { filtro.update { f.copy(texto = it.texto) }; cerrarHoja() }
    fun quitarFiltros() = filtro.update { FiltroInventario() }
    fun reintentar() = intento.update { it + 1 }

    // ---------------- Selección (checkbox) ----------------

    fun alternar(id: Long) {
        val item = state.value.items.firstOrNull { it.producto.id == id }
        if (modoVenta != null && id !in seleccion.value && item != null && !SeleccionVenta.vendible(item)) {
            emitir(EventoInventario.Mensaje(SeleccionVenta.motivo(item))); return
        }
        seleccion.update { if (id in it) it - id else it + id }
    }

    /** Marca todo lo visible; si ya estaba todo lo visible marcado, lo desmarca. En modo venta, solo lo vendible. */
    fun seleccionarTodo() {
        val visibles = state.value.items
            .filter { modoVenta == null || SeleccionVenta.vendible(it) }.map { it.producto.id }.toSet()
        seleccion.update { if (visibles.isNotEmpty() && it.containsAll(visibles)) it - visibles else it + visibles }
    }

    fun limpiarSeleccion() { seleccion.value = emptySet() }

    /** Modo venta: Continuar → la venta recibe los ids (MainScaffold los devuelve por el back stack). */
    fun confirmarSeleccionVenta() {
        val ids = state.value.seleccion.toList()
        if (modoVenta == null || ids.isEmpty()) return
        emitir(EventoInventario.SeleccionVenta(ids))
    }

    // ---------------- Ficha ----------------

    fun abrirFicha(id: Long) {
        viewModelScope.launch {
            if (IdArticulo.esInsumo(id)) {
                when (val r = obtenerFichaInsumo(IdArticulo.insumoId(id))) {
                    is AppResult.Ok -> local.update { it.copy(fichaInsumo = r.value) }
                    is AppResult.Err -> emitir(EventoInventario.Mensaje(TextosInventario.FICHA_NO_DISPONIBLE))
                }
                return@launch
            }
            when (val r = obtenerFicha(id)) {
                is AppResult.Ok -> local.update { it.copy(ficha = r.value) }
                is AppResult.Err -> emitir(EventoInventario.Mensaje(TextosInventario.FICHA_NO_DISPONIBLE))
            }
        }
    }

    fun cerrarFicha() = local.update { it.copy(ficha = null, fichaInsumo = null, hoja = if (it.hoja == HojaInventario.COMPARTIR_FICHA) null else it.hoja) }

    fun editar(id: Long) {
        local.update { it.copy(ficha = null, fichaInsumo = null, hoja = null) }
        emitir(EventoInventario.Navegar(if (IdArticulo.esInsumo(id)) Route.InsumoForm(IdArticulo.insumoId(id)) else Route.ProductoForm(id = id)))
    }

    // ---------------- Agregar ----------------

    fun mostrarHoja(h: HojaInventario) = local.update { it.copy(hoja = h) }
    fun cerrarHoja() = local.update { it.copy(hoja = null) }

    /**
     * P31: «Insumos» es una categoría, no un botón. El + abre el formulario de producto (donde se puede
     * elegir la categoría «Insumos»), o directamente el de insumo si la tabla está filtrada por «Insumos».
     */
    fun agregar() {
        cerrarHoja()
        val destino = when {
            filtro.value.tipo == TipoArticulo.INSUMOS -> Route.InsumoForm()
            else -> Route.ProductoForm()
        }
        emitir(EventoInventario.Navegar(destino))
    }

    // ---------------- Eliminar ----------------

    fun pedirEliminarFicha() {
        val s = state.value
        val c = s.fichaInsumo?.let { ConfirmarEliminar.Uno(IdArticulo.deInsumo(it.insumo.id), it.insumo.nombre) }
            ?: s.ficha?.let { ConfirmarEliminar.Uno(it.producto.id, it.producto.nombreCompleto) } ?: return
        local.update { it.copy(confirmar = c) }
    }

    fun pedirEliminarSeleccion() {
        val n = state.value.seleccion.size
        if (n > 0) local.update { it.copy(confirmar = ConfirmarEliminar.Seleccion(n)) }
    }

    fun cancelarEliminar() = local.update { it.copy(confirmar = null) }

    fun confirmarEliminar() {
        val c = local.value.confirmar ?: return
        local.update { it.copy(confirmar = null, trabajando = true) }
        viewModelScope.launch {
            when (c) {
                is ConfirmarEliminar.Uno -> {
                    val mensaje = if (IdArticulo.esInsumo(c.id)) {
                        val r = eliminarInsumos(setOf(IdArticulo.insumoId(c.id)))
                        when {
                            r.eliminados == 1 -> null
                            r.enUso.isNotEmpty() -> TextosInventario.EN_USO
                            else -> TextosInventario.ERROR_ELIMINAR
                        }
                    } else {
                        if (productosRepo.eliminar(c.id) is AppResult.Ok) null else TextosInventario.ERROR_ELIMINAR
                    }
                    if (mensaje == null) { local.update { it.copy(ficha = null, fichaInsumo = null) }; seleccion.update { it - c.id } }
                    emitir(EventoInventario.Mensaje(mensaje ?: TextosInventario.ELIMINADO))
                }
                is ConfirmarEliminar.Seleccion -> {
                    val (insumos, productos) = state.value.seleccion.partition(IdArticulo::esInsumo)
                    val rp = if (productos.isEmpty()) null else eliminarProductos(productos.toSet())
                    val ri = if (insumos.isEmpty()) null else eliminarInsumos(insumos.map(IdArticulo::insumoId).toSet())
                    seleccion.value = emptySet()
                    emitir(
                        EventoInventario.Mensaje(
                            TextosInventario.eliminados(
                                (rp?.eliminados ?: 0) + (ri?.eliminados ?: 0),
                                (rp?.fallidos ?: 0) + (ri?.fallidos ?: 0),
                                ri?.enUso.orEmpty(),
                            ),
                        ),
                    )
                }
            }
            local.update { it.copy(trabajando = false) }
        }
    }

    // ---------------- Exportar (lista) ----------------

    /** Lo que se exporta: la selección; sin selección, lo que se ve (búsqueda + filtro aplicados). */
    fun objetivoExport(): List<Producto> {
        val s = state.value
        return if (s.seleccion.isNotEmpty()) s.seleccion.mapNotNull { todos.value[it] }.sortedByDescending { it.creadoEn }
        else s.items.map { it.producto }
    }

    fun comentarioPromocion(texto: String) = local.update { it.copy(comentarioPromocion = texto.take(160)) }

    fun exportar(formato: FormatoSalida) {
        val ps = objetivoExport().filter { formato.interno || !it.esInsumo }
        if (ps.isEmpty()) return
        val comentario = state.value.comentarioPromocion
        cerrarHoja()
        trabajar {
            when (formato) {
                FormatoSalida.IMAGEN -> {
                    val fs = if (ps.size == 1) tarjetas.renderizar(ps, comentario)
                    else imagenTabla.renderizar(TablasExport.listaPrecios(ps), "Shalev_precios_${Dates.localDate(clock.now(), ZoneId.systemDefault())}")
                    emitir(EventoInventario.Compartir(fs, "image/png", TextosInventario.TITULO))
                }
                FormatoSalida.TARJETAS -> {
                    val fs = tarjetas.renderizar(ps, comentario)
                    emitir(EventoInventario.Compartir(fs, "image/png", TextosInventario.TITULO))
                }
                FormatoSalida.PDF, FormatoSalida.EXCEL -> {
                    val fx = formato.aExport()
                    when (val r = exportador.aTemporal(nombreInventario(fx)) { exportarInventario(ps, fx, it) }) {
                        is AppResult.Ok -> emitir(EventoInventario.Compartir(listOf(r.value.archivo), fx.mime, TextosInventario.TITULO))
                        is AppResult.Err -> emitir(EventoInventario.Mensaje(TextosInventario.ERROR_EXPORTAR))
                    }
                }
            }
        }
    }

    /** "Guardar en el dispositivo" (PDF/Excel): primero se pide el destino al sistema. */
    fun pedirGuardar(formato: FormatoSalida) {
        if (!formato.guardable || objetivoExport().isEmpty()) return
        exportador.recordar(formato)
        cerrarHoja()
        val fx = formato.aExport()
        emitir(EventoInventario.GuardarComo(nombreInventario(fx), fx.mime))
    }

    fun guardarEn(uri: String?) {
        val formato = exportador.tomar(uri) ?: return // nada pendiente o el usuario canceló el selector
        val ps = objetivoExport()
        trabajar {
            val r = exportador.aDestino(checkNotNull(uri)) { out -> exportarInventario(ps, formato.aExport(), out) }
            emitir(EventoInventario.Mensaje(if (r is AppResult.Ok) TextosInventario.GUARDADO_EN_DISPOSITIVO else TextosInventario.ERROR_EXPORTAR))
        }
    }

    // ---------------- Compartir (ficha) ----------------

    fun compartirFicha(formato: FormatoSalida) {
        val f = state.value.ficha ?: return
        if (formato.interno || f.producto.esInsumo) return
        val comentario = state.value.comentarioPromocion
        local.update { it.copy(hoja = null) }
        trabajar {
            when (formato) {
                FormatoSalida.IMAGEN -> emitir(EventoInventario.Compartir(imagenTabla.renderizar(TablasExport.listaPrecios(listOf(f.producto)), "Shalev_precios"), "image/png", f.producto.nombreCompleto))
                FormatoSalida.TARJETAS -> emitir(EventoInventario.Compartir(tarjetas.renderizar(listOf(f.producto), comentario), "image/png", f.producto.nombreCompleto))
                FormatoSalida.PDF, FormatoSalida.EXCEL -> {
                    when (val r = exportador.aTemporal("Ficha ${f.producto.nombreCompleto}.pdf") { exportarFicha(f, it) }) {
                        is AppResult.Ok -> emitir(EventoInventario.Compartir(listOf(r.value.archivo), FormatoExport.PDF.mime, f.producto.nombreCompleto))
                        is AppResult.Err -> emitir(EventoInventario.Mensaje(TextosInventario.ERROR_EXPORTAR))
                    }
                }
            }
        }
    }

    // ---------------- Internos ----------------

    private fun trabajar(block: suspend () -> Unit) = lanzarExportacion(
        ocupado = { b -> local.update { it.copy(trabajando = b) } },
        alFallar = { emitir(EventoInventario.Mensaje(TextosInventario.ERROR_EXPORTAR)) },
        bloque = block,
    )

    private fun nombreInventario(f: FormatoExport) =
        "Shalev_inventario_${Dates.localDate(clock.now(), ZoneId.systemDefault())}.${f.extension}"

    private fun FormatoSalida.aExport() = if (this == FormatoSalida.EXCEL) FormatoExport.XLSX else FormatoExport.PDF

    private fun emitir(e: EventoInventario) { eventosCh.trySend(e) }

    companion object {
        const val KEY_VENTA = "venta"
        const val KEY_SELECCION = "seleccion"
    }
}
