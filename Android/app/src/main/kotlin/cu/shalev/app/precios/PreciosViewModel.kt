package cu.shalev.app.precios

import cu.shalev.app.common.mensajeSync
import cu.shalev.domain.repository.ProductoRepository
import cu.shalev.domain.repository.ServicioRepository
import cu.shalev.domain.repository.PreciosRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.PreajustePrecios
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Servicio
import cu.shalev.domain.usecase.ActivarPreajuste
import cu.shalev.domain.usecase.GuardarPreajuste
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreciosUiState(
    val cargado: Boolean = false,
    val preajustes: List<PreajustePrecios> = emptyList(),
    val productos: List<Producto> = emptyList(),
    val servicios: List<Servicio> = emptyList(),
    val form: PreajusteForm? = null,
    val borrar: PreajustePrecios? = null,
    val guardando: Boolean = false,
) {
    val errores: Map<CampoPrecio, String> get() = form?.takeIf { it.mostrarErrores }?.let(::validar).orEmpty()
    val productosVisibles: List<Producto> get() = form?.let { filtrar(productos, it.busqueda) }.orEmpty()
    val serviciosVisibles: List<Servicio> get() {
        val f = form ?: return emptyList()
        val q = f.busqueda.trim().lowercase()
        return servicios.filter { q.isEmpty() || it.nombre.lowercase().contains(q) || it.tipo.lowercase().contains(q) }.take(TextosPrecios.MAX_LISTA)
    }
    /** Productos marcados, en orden de selección, aunque no coincidan con la búsqueda. */
    val seleccionados: List<Producto>
        get() {
            val ids = form?.productoIds ?: return emptyList()
            val porId = productos.associateBy { it.id }
            return ids.mapNotNull(porId::get)
        }
    val serviciosSeleccionados: List<Servicio>
        get() {
            val ids = form?.servicioIds ?: return emptyList()
            val porId = servicios.associateBy { it.id }
            return ids.mapNotNull(porId::get)
        }
    val vistaPrevia: String?
        get() {
            val f = form ?: return null
            vistaPrevia(f, productos)?.let { return it }
            val pb = f.puntosBasicos ?: return null
            val s = servicios.firstOrNull { it.id in f.servicioIds } ?: return null
            val nuevo = s.importe.ajustar(pb)
            return "${s.nombre}: ${cu.shalev.core.money.Money.format(s.importe)} → ${cu.shalev.core.money.Money.format(nuevo)}"
        }
    fun existentes(p: PreajustePrecios): Int = productos.count { it.id in p.productoIds } + servicios.count { it.id in p.servicioIds }
}

/**
 * "Precios" de Inicio: preajustes que suben/bajan el precio de los productos elegidos según el método de
 * pago. Se aplican a productos o servicios al cotizar y no cambian el precio guardado.
 */
@HiltViewModel
class PreciosViewModel @Inject constructor(
    private val preciosRepo: PreciosRepository,
    private val productosRepo: ProductoRepository,
    private val serviciosRepo: ServicioRepository,
    private val guardarPreajuste: GuardarPreajuste,
    private val activarPreajuste: ActivarPreajuste,
) : ViewModel() {

    private val local = MutableStateFlow(PreciosUiState())
    private val preajustes = preciosRepo.observarPreajustes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val productos = productosRepo.observarTodos().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val servicios = serviciosRepo.observarTodos().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val mensajesCh = Channel<String>(Channel.BUFFERED)
    val mensajes: Flow<String> = mensajesCh.receiveAsFlow()

    val state: StateFlow<PreciosUiState> = combine(preajustes, productos, servicios, local) { pa, ps, ss, l ->
        l.copy(cargado = true, preajustes = pa, productos = ps, servicios = ss)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreciosUiState())

    fun nuevo() = local.update { it.copy(form = PreajusteForm()) }

    fun editar(p: PreajustePrecios) = local.update { it.copy(form = desde(p)) }

    fun cambiar(f: (PreajusteForm) -> PreajusteForm) = local.update { s -> s.copy(form = s.form?.let(f)) }

    fun alternarProducto(id: Long) = cambiar { it.copy(productoIds = if (id in it.productoIds) it.productoIds - id else it.productoIds + id) }

    /** Compatibilidad con llamadas anteriores: selecciona los productos visibles. */
    fun elegirVisibles(elegir: Boolean) = cambiar { f ->
        val ids = filtrar(productos.value, f.busqueda).map { it.id }.toSet()
        f.copy(productoIds = if (elegir) f.productoIds + ids else f.productoIds - ids)
    }

    fun alternarServicio(id: Long) = cambiar { it.copy(servicioIds = if (id in it.servicioIds) it.servicioIds - id else it.servicioIds + id) }

    /** Añade todos los artículos del tipo elegido, no solo los que coinciden con la búsqueda. */
    fun elegirTodos() = cambiar { f ->
        when (f.tipoSeleccion) {
            TipoSeleccionPrecio.PRODUCTOS -> f.copy(productoIds = f.productoIds + productos.value.filterNot { it.eliminado }.map { it.id })
            TipoSeleccionPrecio.SERVICIOS -> f.copy(servicioIds = f.servicioIds + servicios.value.filterNot { it.eliminado }.map { it.id })
        }
    }

    fun cerrar() = local.update { it.copy(form = null) }

    fun guardar() {
        val f = local.value.form ?: return
        if (validar(f).isNotEmpty()) { cambiar { it.copy(mostrarErrores = true) }; return }
        ejecutar {
            when (val r = guardarPreajuste(aPreajuste(f))) {
                is AppResult.Ok -> {
                    local.update { it.copy(form = null) }
                    mensajesCh.trySend(if (f.nuevo) "Ajuste creado" else "Ajuste guardado")
                }
                is AppResult.Err -> mensajesCh.trySend(mensajeSync(r.error) ?: TextosPrecios.ERROR_GENERICO)
            }
        }
    }

    fun activar(p: PreajustePrecios, activo: Boolean) = ejecutar {
        val r = activarPreajuste(p.id, activo)
        if (r is AppResult.Err) mensajesCh.trySend(mensajeSync(r.error) ?: TextosPrecios.ERROR_GENERICO)
    }

    fun pedirBorrado() = local.update { s -> s.copy(borrar = preajustes.value.firstOrNull { it.id == s.form?.id }) }

    fun cancelarBorrado() = local.update { it.copy(borrar = null) }

    fun confirmarBorrado() {
        val p = local.value.borrar ?: return
        local.update { it.copy(borrar = null, form = null) }
        ejecutar {
            when (val r = preciosRepo.eliminarPreajuste(p.id)) {
                is AppResult.Ok -> mensajesCh.trySend("Ajuste eliminado")
                is AppResult.Err -> mensajesCh.trySend(mensajeSync(r.error) ?: TextosPrecios.ERROR_GENERICO)
            }
        }
    }

    private fun ejecutar(bloque: suspend () -> Unit) {
        if (local.value.guardando) return
        viewModelScope.launch {
            local.update { it.copy(guardando = true) }
            try { bloque() } finally { local.update { it.copy(guardando = false) } }
        }
    }
}
