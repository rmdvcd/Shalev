package cu.shalev.app.servicios

import cu.shalev.core.time.Dates
import cu.shalev.domain.model.nombreCompleto
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.app.common.ArchivosApp
import cu.shalev.app.common.ExportadorArchivos
import cu.shalev.app.common.lanzarExportacion
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.app.inventario.FormatoSalida
import cu.shalev.app.inventario.SeleccionVenta
import cu.shalev.app.navigation.Route
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.FiltroServicios
import cu.shalev.domain.model.ServicioDisponible
import cu.shalev.domain.model.VistaServicios
import cu.shalev.domain.repository.ServicioRepository
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.service.TablasExport
import cu.shalev.domain.usecase.EliminarServicios
import cu.shalev.domain.usecase.ExportarTablas
import cu.shalev.domain.usecase.ObservarServicios
import cu.shalev.domain.usecase.ObtenerFichaServicio
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HojaServicios { FILTRO, EXPORTAR }

sealed interface ConfirmarEliminarServicio {
    data class Uno(val id: Long, val nombre: String) : ConfirmarEliminarServicio
    data class Seleccion(val cantidad: Int) : ConfirmarEliminarServicio
}

data class ServiciosUiState(
    val comentarioPromocion: String = "",
    val vista: EstadoCarga<VistaServicios> = EstadoCarga.Cargando,
    val filtro: FiltroServicios = FiltroServicios(),
    val seleccion: Set<Long> = emptySet(),
    val ficha: ServicioDisponible? = null,
    val hoja: HojaServicios? = null,
    val confirmar: ConfirmarEliminarServicio? = null,
    val trabajando: Boolean = false,
    /** P29: modo «elegir qué vender» de una venta de servicios (checkbox + Continuar, sin exportar ni borrar). */
    val modoVenta: Boolean = false,
    /** Filas seleccionadas, en orden de marcado, incluso fuera de la búsqueda actual. */
    val elementosFijados: List<ServicioDisponible> = emptyList(),
) {
    val datos: VistaServicios? get() = (vista as? EstadoCarga.Exito)?.datos ?: (vista as? EstadoCarga.Vacio)?.datos
    val items: List<ServicioDisponible> get() = (vista as? EstadoCarga.Exito)?.datos?.items.orEmpty()
    val total: Int get() = datos?.total ?: 0
    val tipos: List<String> get() = datos?.tipos.orEmpty()
    val todosVisiblesSeleccionados: Boolean get() = items.isNotEmpty() && items.all { it.servicio.id in seleccion }
}

sealed interface EventoServicios {
    data class Compartir(val archivo: java.io.File, val mime: String, val asunto: String) : EventoServicios
    data class CompartirImagenes(val archivos: List<java.io.File>) : EventoServicios
    data class GuardarComo(val nombre: String, val mime: String) : EventoServicios
    data class Navegar(val route: Route) : EventoServicios
    data class Mensaje(val texto: String) : EventoServicios
    /** Modo venta: vuelve a la venta con los ids elegidos (en orden de marcado). */
    data class SeleccionVenta(val ids: List<Long>) : EventoServicios
}

/**
 * P29 · Servicios (sustituye a Elaboración): lista cronológica con buscador, filtro por Tipo, selección, ficha
 * (Editar, Eliminar) y exportación. En modo venta solo se eligen servicios.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ServiciosViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val observarServicios: ObservarServicios,
    private val serviciosRepo: ServicioRepository,
    private val obtenerFicha: ObtenerFichaServicio,
    private val eliminarServicios: EliminarServicios,
    private val exportarTablas: ExportarTablas,
    private val archivos: ArchivosApp,
    private val clock: Clock,
    private val imagenTabla: cu.shalev.app.common.ImagenTablaAndroid,
    private val tarjetas: cu.shalev.app.inventario.TarjetasAndroid,
    private val estadoPantallas: cu.shalev.domain.repository.EstadoPantallasRepository,
) : ViewModel() {

    private val modoVenta: Boolean = saved.get<Boolean>(KEY_VENTA) == true
    private val filtro = MutableStateFlow(FiltroServicios())
    private val intento = MutableStateFlow(0)
    private val seleccion = MutableStateFlow<Set<Long>>(if (modoVenta) SeleccionVenta.parse(saved.get<String>(KEY_SELECCION)) else emptySet())
    private val local = MutableStateFlow(ServiciosUiState(modoVenta = modoVenta))
    private val eventosCh = Channel<EventoServicios>(Channel.BUFFERED)
    val eventos: Flow<EventoServicios> = eventosCh.receiveAsFlow()

    /** Retardo del buscador (0.30.0, F1). Los tests lo bajan a 0 para no depender del reloj virtual. */
    internal var debounceBusqueda: Long = 250

    /** 0.30.0 (F1, ver `InventarioViewModel`): el texto del buscador espera [debounceBusqueda]; el resto, al instante. */
    private val filtroConsultado: Flow<FiltroServicios> = filtro.debounce { f -> if (f.texto.isEmpty()) 0L else debounceBusqueda }

    /** 0.30.0 (F1, ver `InventarioViewModel`): filtro + intento (Reintentar) disparan la consulta. */
    private val consulta = combine(intento, filtroConsultado) { _, f -> f }.distinctUntilChanged()

    private val vista: StateFlow<EstadoCarga<VistaServicios>> = consulta.flatMapLatest { f ->
        observarServicios(flowOf(f))
            .map<VistaServicios, EstadoCarga<VistaServicios>> { if (it.total == 0) EstadoCarga.Vacio(it) else EstadoCarga.Exito(it) }
            .onStart { emit(EstadoCarga.Cargando) }
            .catch { emit(EstadoCarga.Error(TextosServicios.ERROR_CARGA)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoCarga.Cargando)

    /** Todos los activos: la selección sobrevive a búsquedas/filtros y se poda si se borran. */
    private val todos: StateFlow<Map<Long, ServicioDisponible>> = observarServicios.disponibles()
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val state: StateFlow<ServiciosUiState> = combine(vista, filtro, seleccion, todos, local) { v, f, s, t, l ->
        val seleccionValida = if (t.isEmpty()) emptySet() else s.filterTo(linkedSetOf()) { it in t }
        l.copy(
            vista = v,
            filtro = f,
            seleccion = seleccionValida,
            elementosFijados = seleccionValida.mapNotNull(t::get),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiciosUiState(modoVenta = modoVenta))

    private val exportador = ExportadorArchivos<FormatoSalida>(archivos)

    init {
        // Permanencia del filtro al cerrar la app (fuera del modo venta): se restaura el último tipo marcado,
        // salvo que la persona ya esté buscando o filtrando cuando llega el valor.
        if (!modoVenta) viewModelScope.launch {
            val guardado = FiltroServiciosGuardado.decodificar(
                estadoPantallas.observar(FiltroServiciosGuardado.CLAVE).first(),
            )
            if (guardado != null && filtro.value == FiltroServicios()) filtro.value = guardado
        }
    }

    private fun persistirFiltro() {
        if (modoVenta) return // la selección para vender no toca el filtro recordado de la tienda
        val valor = FiltroServiciosGuardado.codificar(filtro.value)
        viewModelScope.launch { estadoPantallas.guardar(FiltroServiciosGuardado.CLAVE, valor) }
    }

    // ---------------- Buscador y filtro ----------------

    fun buscar(texto: String) = filtro.update { it.copy(texto = texto.take(80)) }
    fun aplicarFiltro(f: FiltroServicios) { filtro.update { f.copy(texto = it.texto) }; cerrarHoja(); persistirFiltro() }
    fun quitarFiltros() { filtro.update { FiltroServicios() }; persistirFiltro() }
    fun reintentar() = intento.update { it + 1 }

    // ---------------- Selección ----------------

    fun alternar(id: Long) {
        val s = todos.value[id]
        if (modoVenta && id !in seleccion.value && s != null && !s.vendible) {
            emitir(EventoServicios.Mensaje(TextosServicios.NO_VENDIBLE)); return
        }
        seleccion.update { if (id in it) it - id else it + id }
    }

    fun seleccionarTodo() {
        val visibles = state.value.items.filter { !modoVenta || it.vendible }.map { it.servicio.id }.toSet()
        seleccion.update { if (visibles.isNotEmpty() && it.containsAll(visibles)) it - visibles else it + visibles }
    }

    fun limpiarSeleccion() { seleccion.value = emptySet() }

    fun confirmarSeleccionVenta() {
        val ids = state.value.seleccion.toList()
        if (!modoVenta || ids.isEmpty()) return
        emitir(EventoServicios.SeleccionVenta(ids))
    }

    // ---------------- Ficha ----------------

    fun abrirFicha(id: Long) {
        viewModelScope.launch {
            when (val r = obtenerFicha(id)) {
                is AppResult.Ok -> local.update { it.copy(ficha = r.value) }
                is AppResult.Err -> emitir(EventoServicios.Mensaje(TextosServicios.FICHA_NO_DISPONIBLE))
            }
        }
    }

    fun cerrarFicha() = local.update { it.copy(ficha = null) }

    fun editar(id: Long) {
        local.update { it.copy(ficha = null, hoja = null) }
        emitir(EventoServicios.Navegar(Route.ServicioForm(id = id)))
    }

    fun agregar() = emitir(EventoServicios.Navegar(Route.ServicioForm()))

    // ---------------- Hojas ----------------

    fun mostrarHoja(h: HojaServicios) = local.update { it.copy(hoja = h) }
    fun cerrarHoja() = local.update { it.copy(hoja = null) }

    // ---------------- Eliminar ----------------

    fun pedirEliminarFicha() {
        val f = state.value.ficha ?: return
        local.update { it.copy(confirmar = ConfirmarEliminarServicio.Uno(f.servicio.id, f.servicio.nombreCompleto)) }
    }

    fun pedirEliminarSeleccion() {
        val n = state.value.seleccion.size
        if (n > 0) local.update { it.copy(confirmar = ConfirmarEliminarServicio.Seleccion(n)) }
    }

    fun cancelarEliminar() = local.update { it.copy(confirmar = null) }

    fun confirmarEliminar() {
        val c = local.value.confirmar ?: return
        local.update { it.copy(confirmar = null, trabajando = true) }
        viewModelScope.launch {
            when (c) {
                is ConfirmarEliminarServicio.Uno -> {
                    val ok = serviciosRepo.eliminar(c.id) is AppResult.Ok
                    if (ok) { local.update { it.copy(ficha = null) }; seleccion.update { it - c.id } }
                    emitir(EventoServicios.Mensaje(if (ok) TextosServicios.ELIMINADO else TextosServicios.ERROR_ELIMINAR))
                }
                is ConfirmarEliminarServicio.Seleccion -> {
                    val r = eliminarServicios(state.value.seleccion)
                    seleccion.value = emptySet()
                    emitir(EventoServicios.Mensaje(TextosServicios.eliminados(r.eliminados, r.fallidos)))
                }
            }
            local.update { it.copy(trabajando = false) }
        }
    }

    // ---------------- Exportar ----------------

    /** La selección; sin selección, lo que se ve (búsqueda + filtro aplicados). */
    fun objetivoExport(): List<ServicioDisponible> {
        val s = state.value
        return if (s.seleccion.isNotEmpty()) s.seleccion.mapNotNull { todos.value[it] }.sortedByDescending { it.servicio.creadoEn }
        else s.items
    }

    fun comentarioPromocion(texto: String) = local.update { it.copy(comentarioPromocion = texto.take(160)) }

    fun exportar(formato: FormatoSalida) {
        val xs = objetivoExport()
        if (xs.isEmpty()) return
        val comentario = state.value.comentarioPromocion
        cerrarHoja()
        trabajar {
            if (!formato.interno) {
                val productos = xs.map { ServiciosLogic.productoPromocional(it.servicio) }
                val fs = if (formato == FormatoSalida.TARJETAS || productos.size == 1) tarjetas.renderizar(productos, comentario)
                    else imagenTabla.renderizar(TablasExport.listaPrecios(productos), "Shalev_servicios")
                emitir(EventoServicios.CompartirImagenes(fs))
                return@trabajar
            }
            val tabla = ServiciosLogic.tabla(xs)
            val fx = formato.aExport()
            when (val r = exportador.aTemporal(nombreArchivo(fx)) { exportarTablas(listOf(tabla), fx, it) }) {
                is AppResult.Ok -> emitir(EventoServicios.Compartir(r.value.archivo, fx.mime, TextosServicios.TITULO))
                is AppResult.Err -> emitir(EventoServicios.Mensaje(TextosServicios.ERROR_EXPORTAR))
            }
        }
    }

    fun pedirGuardar(formato: FormatoSalida) {
        if (formato != FormatoSalida.PDF && formato != FormatoSalida.EXCEL || objetivoExport().isEmpty()) return
        exportador.recordar(formato)
        cerrarHoja()
        val fx = formato.aExport()
        emitir(EventoServicios.GuardarComo(nombreArchivo(fx), fx.mime))
    }

    fun guardarEn(uri: String?) {
        val formato = exportador.tomar(uri) ?: return
        val tabla = ServiciosLogic.tabla(objetivoExport())
        trabajar {
            val r = exportador.aDestino(checkNotNull(uri)) { out -> exportarTablas(listOf(tabla), formato.aExport(), out) }
            emitir(EventoServicios.Mensaje(if (r is AppResult.Ok) TextosServicios.GUARDADO_EN_DISPOSITIVO else TextosServicios.ERROR_EXPORTAR))
        }
    }

    // ---------------- Internos ----------------

    private fun trabajar(block: suspend () -> Unit) {
        if (local.value.trabajando) return // ya hay una exportación en curso: no se lanza otra encima
        lanzarExportacion(
            ocupado = { b -> local.update { it.copy(trabajando = b) } },
            alFallar = { emitir(EventoServicios.Mensaje(TextosServicios.ERROR_EXPORTAR)) },
            bloque = block,
        )
    }

    private fun nombreArchivo(f: FormatoExport) =
        "Shalev_servicios_${Dates.localDate(clock.now(), ZoneId.systemDefault())}.${f.extension}"

    private fun FormatoSalida.aExport() = if (this == FormatoSalida.EXCEL) FormatoExport.XLSX else FormatoExport.PDF

    private fun emitir(e: EventoServicios) { eventosCh.trySend(e) }

    companion object {
        const val KEY_VENTA = "venta"
        const val KEY_SELECCION = "seleccion"
    }
}
