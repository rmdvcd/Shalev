package cu.shalev.app.insumos

import cu.shalev.core.quantity.Cantidad
import cu.shalev.app.common.mensajeSync
import cu.shalev.app.common.RecepcionInicialAlta
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.money.Money
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.UnidadMedida
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.usecase.GuardarInsumo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InsumoFormUiState(
    val form: InsumoForm = InsumoForm(),
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    /** Campos que el usuario ya tocó: su error se muestra al momento (validación en tiempo real). */
    val tocados: Set<String> = emptySet(),
    /** Tras pulsar Guardar se muestran todos los errores. */
    val intentado: Boolean = false,
    val erroresGuardado: Map<String, String> = emptyMap(),
    val noEncontrado: Boolean = false,
    /** P18 (A03): el usuario cambió algo desde que se abrió: salir sin guardar pide confirmación. */
    val cambios: Boolean = false,
    /** 0.21.6: existencia al abrir la ficha; si no se toca, al guardar se conserva la actual. */
    val cantidadLeida: Cantidad? = null,
) {
    val sinGuardar: Boolean get() = cambios && !guardando
    val esNuevo: Boolean get() = form.id == 0L
    val errores: Map<String, String> get() {
        val todos = InsumoFormLogic.validar(form) + erroresGuardado
        return if (intentado) todos else todos.filterKeys { it in tocados }
    }
}

sealed interface EventoInsumoForm {
    data class Guardado(val mensaje: String, val recepcionInicial: RecepcionInicialAlta? = null) : EventoInsumoForm
    data class Mensaje(val texto: String) : EventoInsumoForm
}

/**
 * Crear / editar insumo (argumento de ruta `id`, 0 = nuevo). Guarda con [GuardarInsumo], que además
 * recalcula el costo de los Elaborados cuyo precio depende de este insumo. Cambiar la cantidad al editar
 * queda registrado como movimiento AJUSTE (en :data).
 */
@HiltViewModel
class InsumoFormViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val insumos: InsumoRepository,
    private val guardarInsumo: GuardarInsumo,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(InsumoFormUiState())
    val state: StateFlow<InsumoFormUiState> = _state.asStateFlow()
    private val eventosCh = Channel<EventoInsumoForm>(Channel.BUFFERED)
    val eventos: Flow<EventoInsumoForm> = eventosCh.receiveAsFlow()

    init {
        val id = saved.get<Long>("id") ?: 0L
        if (id > 0) {
            cargar(id)
        } else {
            // P31: llega desde el formulario de producto al elegir «Insumos»: se conserva lo ya escrito.
            saved.get<String>("nombre")?.takeIf { it.isNotBlank() }?.let(::nombre)
        }
    }

    private fun cargar(id: Long) {
        _state.update { it.copy(cargando = true) }
        viewModelScope.launch {
            val i = insumos.obtener(id)
            _state.update {
                if (i == null) it.copy(cargando = false, noEncontrado = true)
                else it.copy(cargando = false, form = InsumoFormLogic.desde(i), cantidadLeida = i.cantidad)
            }
        }
    }

    private fun editar(campo: String?, f: (InsumoForm) -> InsumoForm) = _state.update {
        it.copy(form = f(it.form), cambios = true, tocados = if (campo != null) it.tocados + campo else it.tocados, erroresGuardado = it.erroresGuardado - (campo ?: ""))
    }

    fun nombre(v: String) = editar(CamposInsumo.NOMBRE) { it.copy(nombre = v.take(80)) }
    fun precio(v: String) = editar(CamposInsumo.PRECIO) { it.copy(precio = v.take(16)) }
    fun cantidad(v: String) = editar(CamposInsumo.CANTIDAD) { it.copy(cantidad = v.take(12)) }
    fun unidad(u: UnidadMedida) = editar(CamposInsumo.UNIDAD) { it.copy(unidad = u) }
    fun precioVenta(v: String) = editar(CamposInsumo.PRECIO_VENTA) { it.copy(precioVenta = v.take(16)) }
    fun nivelBajo(v: String) = editar(CamposInsumo.NIVEL_BAJO) { it.copy(nivelBajo = v.take(12)) }
    fun nivelCritico(v: String) = editar(CamposInsumo.NIVEL_CRITICO) { it.copy(nivelCritico = v.take(12)) }

    fun guardar(abrirRecepcion: Boolean = false) {
        val s = _state.value
        if (s.guardando) return
        _state.update { it.copy(intentado = true) }
        val insumoListo = InsumoFormLogic.aInsumo(s.form, clock.now()) ?: return
        val registrarRecepcion = abrirRecepcion && s.esNuevo
        if (registrarRecepcion && insumoListo.cantidad.milesimas <= 0) {
            _state.update {
                it.copy(erroresGuardado = it.erroresGuardado + (CamposInsumo.CANTIDAD to "Escribe una cantidad mayor que cero para la recepción inicial."))
            }
            return
        }
        val insumoAGuardar = if (registrarRecepcion) insumoListo.copy(cantidad = Cantidad.ZERO) else insumoListo
        _state.update { it.copy(guardando = true) }
        viewModelScope.launch {
            when (val r = guardarInsumo(insumoAGuardar, s.cantidadLeida)) {
                is AppResult.Ok -> {
                    val inicial = if (registrarRecepcion) RecepcionInicialAlta(
                        tipo = TipoEntidad.INSUMO,
                        articuloId = r.value,
                        cantidad = Cantidad.editable(insumoListo.cantidad),
                        costo = Money.editar(insumoListo.precio),
                    ) else null
                    eventosCh.trySend(EventoInsumoForm.Guardado(
                        if (s.esNuevo) "Insumo guardado." else "Cambios guardados.",
                        recepcionInicial = inicial,
                    ))
                }
                is AppResult.Err -> {
                    val e = r.error
                    val campo = (e as? AppError.Validacion)?.campo
                    val msg = InsumoFormLogic.mensaje(e)
                    if (campo != null && msg != null) _state.update { it.copy(erroresGuardado = it.erroresGuardado + (campo to msg)) }
                    else eventosCh.trySend(EventoInsumoForm.Mensaje(if (e is AppError.NoEncontrado) "Este insumo ya no existe." else mensajeSync(e) ?: "No se pudo guardar. Inténtalo de nuevo."))
                }
            }
            _state.update { it.copy(guardando = false) }
        }
    }
}
