package cu.shalev.app.servicios

import cu.shalev.domain.model.identidad
import cu.shalev.core.result.runCatchingCancelable
import cu.shalev.app.common.mensajeSync
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.app.producto.LineaRecetaForm
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.repository.FotoRepository
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.repository.ServicioRepository
import cu.shalev.domain.usecase.GuardarServicio
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServicioFormUiState(
    val form: ServicioForm = ServicioForm(),
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    val importandoFoto: Boolean = false,
    val tocados: Set<String> = emptySet(),
    val intentado: Boolean = false,
    val erroresGuardado: Map<String, String> = emptyMap(),
    /** Tipos ya usados (sugerencias del campo Tipo). */
    val tipos: List<String> = emptyList(),
    val insumos: List<Insumo> = emptyList(),
    val hojaInsumos: Boolean = false,
    val noEncontrado: Boolean = false,
    val cambios: Boolean = false,
    /** 0.24.0: los demás servicios activos (id, nombre, descripción) para avisar en vivo de un nombre repetido. */
    val otros: List<Triple<Long, String, String?>> = emptyList(),
) {
    val sinGuardar: Boolean get() = cambios && !guardando
    val esNuevo: Boolean get() = form.id == 0L
    val errores: Map<String, String> get() {
        val todos = ServicioFormLogic.validar(form) + erroresGuardado
        val visibles = if (intentado) todos else todos.filterKeys { it in tocados }
        val d = CamposServicio.DESCRIPCION
        if (d in visibles || !(intentado || CamposServicio.NOMBRE in tocados || d in tocados)) return visibles
        val id = cu.shalev.app.producto.ProductoFormLogic.errorIdentidad(form.id, form.nombre, form.descripcion, otros, "servicio") ?: return visibles
        return visibles + (d to id)
    }
    val costo get() = ServicioFormLogic.costo(form)
    val insumosDisponibles: List<Insumo> get() = insumos.filter { i -> form.insumos.none { it.insumoId == i.id } }
}

sealed interface EventoServicioForm {
    data class Guardado(val mensaje: String) : EventoServicioForm
    data class Mensaje(val texto: String) : EventoServicioForm
}

/** P29: crear / editar servicio (argumento de ruta `id`, 0 = nuevo). */
@HiltViewModel
class ServicioFormViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val servicios: ServicioRepository,
    private val insumosRepo: InsumoRepository,
    private val guardarServicio: GuardarServicio,
    private val fotos: FotoRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(ServicioFormUiState())
    val state: StateFlow<ServicioFormUiState> = _state.asStateFlow()
    private val eventosCh = Channel<EventoServicioForm>(Channel.BUFFERED)
    val eventos: Flow<EventoServicioForm> = eventosCh.receiveAsFlow()

    init {
        val id = saved.get<Long>("id") ?: 0L
        viewModelScope.launch { runCatchingCancelable { servicios.tiposEnUso() }.onSuccess { t -> _state.update { it.copy(tipos = t) } } }
        viewModelScope.launch { insumosRepo.observarTodos().catch { emit(emptyList()) }.collect { xs -> _state.update { it.copy(insumos = xs) } } }
        viewModelScope.launch {
            servicios.observarTodos().catch { emit(emptyList()) }
                .collect { ss -> _state.update { it.copy(otros = ss.map { s -> s.identidad }) } }
        }
        if (id > 0) cargar(id)
    }

    private fun cargar(id: Long) {
        _state.update { it.copy(cargando = true) }
        viewModelScope.launch {
            val s = servicios.obtener(id)?.takeUnless { it.eliminado }
            if (s == null) { _state.update { it.copy(cargando = false, noEncontrado = true) }; return@launch }
            val lineas = servicios.insumos(id)
            val xs = insumosRepo.obtenerVarios(lineas.map { it.insumoId }).associateBy { it.id }
            _state.update { it.copy(cargando = false, form = ServicioFormLogic.desde(s, lineas, xs)) }
        }
    }

    private fun editar(campo: String?, f: (ServicioForm) -> ServicioForm) = _state.update {
        it.copy(form = f(it.form), cambios = true, tocados = if (campo != null) it.tocados + campo else it.tocados, erroresGuardado = it.erroresGuardado - (campo ?: ""))
    }

    fun nombre(v: String) = editar(CamposServicio.NOMBRE) { it.copy(nombre = v.take(80)) }
    fun tipo(v: String) = editar(CamposServicio.TIPO) { it.copy(tipo = v.take(40)) }
    fun costoAdicional(v: String) = editar(CamposServicio.COSTO_ADICIONAL) { it.copy(costoAdicional = v.take(16)) }
    fun importe(v: String) = editar(CamposServicio.IMPORTE) { it.copy(importe = v.take(16)) }
    fun descripcion(v: String) = editar(CamposServicio.DESCRIPCION) { it.copy(descripcion = cu.shalev.app.producto.ProductoFormLogic.unaLinea(v).take(500)) }

    fun fotoElegida(uri: String?) {
        if (uri == null) return
        _state.update { it.copy(importandoFoto = true) }
        viewModelScope.launch {
            when (val r = fotos.importar(uri)) {
                is AppResult.Ok -> _state.update { it.copy(importandoFoto = false, cambios = true, form = it.form.copy(fotoUri = r.value)) }
                is AppResult.Err -> { _state.update { it.copy(importandoFoto = false) }; eventosCh.trySend(EventoServicioForm.Mensaje(ERROR_FOTO)) }
            }
        }
    }

    fun quitarFoto() = editar(null) { it.copy(fotoUri = null) }

    fun mostrarInsumos(v: Boolean) = _state.update { it.copy(hojaInsumos = v) }

    fun agregarInsumo(i: Insumo) = _state.update {
        if (it.form.insumos.any { l -> l.insumoId == i.id }) it.copy(hojaInsumos = false)
        else it.copy(
            hojaInsumos = false, cambios = true, tocados = it.tocados + CamposServicio.INSUMOS,
            form = it.form.copy(insumos = it.form.insumos + LineaRecetaForm(i.id, i.nombre, i.unidad.simbolo, i.precio)),
        )
    }

    fun cantidadInsumo(insumoId: Long, v: String) = editar(CamposServicio.INSUMOS) { f ->
        f.copy(insumos = f.insumos.map { if (it.insumoId == insumoId) it.copy(cantidad = v.take(12)) else it })
    }

    fun quitarInsumo(insumoId: Long) = editar(CamposServicio.INSUMOS) { f -> f.copy(insumos = f.insumos.filterNot { it.insumoId == insumoId }) }

    fun guardar() {
        val s = _state.value
        if (s.guardando) return
        _state.update { it.copy(intentado = true) }
        val (servicio, lineas) = ServicioFormLogic.aServicio(s.form, clock.now()) ?: return
        _state.update { it.copy(guardando = true) }
        viewModelScope.launch {
            when (val r = guardarServicio(servicio, lineas)) {
                is AppResult.Ok -> eventosCh.trySend(EventoServicioForm.Guardado(if (s.esNuevo) "Servicio guardado." else "Cambios guardados."))
                is AppResult.Err -> {
                    val e = r.error
                    val campo = (e as? AppError.Validacion)?.campo
                    val msg = ServicioFormLogic.mensaje(e)
                    if (campo != null && msg != null) _state.update { it.copy(erroresGuardado = it.erroresGuardado + (campo to msg)) }
                    else eventosCh.trySend(EventoServicioForm.Mensaje(if (e is AppError.NoEncontrado) "Este servicio ya no existe." else mensajeSync(e) ?: "No se pudo guardar. Inténtalo de nuevo."))
                }
            }
            _state.update { it.copy(guardando = false) }
        }
    }

    private companion object {
        const val ERROR_FOTO = "No se pudo usar esa foto. Prueba con otra."
    }
}
