package cu.shalev.app.producto

import cu.shalev.domain.model.identidad
import cu.shalev.core.result.runCatchingCancelable
import cu.shalev.app.common.mensajeSync
import cu.shalev.app.common.RecepcionInicialAlta
import cu.shalev.domain.repository.FotoRepository
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.core.result.AppResult
import cu.shalev.core.money.Money
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.validation.Validadores
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.repository.ProductoRepository
import cu.shalev.domain.usecase.GuardarProducto
import cu.shalev.domain.usecase.ObtenerCategorias
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
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

data class ProductoFormUiState(
    val form: ProductoForm = ProductoForm(),
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    val importandoFoto: Boolean = false,
    /** Campos que el usuario ya tocó: su error se muestra al momento (validación en tiempo real). */
    val tocados: Set<String> = emptySet(),
    /** Tras pulsar Guardar se muestran todos los errores. */
    val intentado: Boolean = false,
    /** Errores que solo se conocen al guardar (p. ej. código duplicado). */
    val erroresGuardado: Map<String, String> = emptyMap(),
    val categorias: List<String> = Categorias.PREDEFINIDAS,
    val insumos: List<Insumo> = emptyList(),
    val hojaInsumos: Boolean = false,
    /** El producto solicitado por id no existe o está archivado. */
    val noEncontrado: Boolean = false,
    /** P18 (A03): el usuario cambió algo desde que se abrió: salir sin guardar pide confirmación. */
    val cambios: Boolean = false,
    /** 0.21.6: existencia al abrir la ficha; si no se toca, al guardar se conserva la actual (ventas mientras tanto). */
    val cantidadLeida: Long? = null,
    /** 0.24.0: los demás productos activos (id, nombre, descripción) para avisar en vivo de un nombre repetido. */
    val otros: List<Triple<Long, String, String?>> = emptyList(),
) {
    val sinGuardar: Boolean get() = cambios && !guardando
    val esNuevo: Boolean get() = form.id == 0L
    val errores: Map<String, String> get() {
        val todos = ProductoFormLogic.validar(form) + erroresGuardado
        val visibles = if (intentado) todos else todos.filterKeys { it in tocados }
        // 0.24.0: el nombre repetido se avisa en la Descripción en cuanto se toca el nombre o la descripción.
        if (Campos.DESCRIPCION in visibles || !(intentado || Campos.NOMBRE in tocados || Campos.DESCRIPCION in tocados)) return visibles
        val id = ProductoFormLogic.errorIdentidad(form.id, form.nombre, form.descripcion, otros, "producto") ?: return visibles
        return visibles + (Campos.DESCRIPCION to id)
    }
    val costoReceta get() = ProductoFormLogic.costoElaborado(form)
    /** Insumos que aún no están en la receta (para la hoja "Agregar insumo"). */
    val insumosDisponibles: List<Insumo> get() = insumos.filter { i -> form.receta.none { it.insumoId == i.id } }
}

sealed interface EventoForm {
    data class Guardado(val mensaje: String, val recepcionInicial: RecepcionInicialAlta? = null) : EventoForm
    data class Mensaje(val texto: String) : EventoForm
    /** P31: se eligió la categoría «Insumos» en un artículo nuevo: seguir en el formulario de insumo con [nombre]. */
    data class EsInsumo(val nombre: String) : EventoForm
}

/**
 * Crear / editar producto. Argumentos de ruta: `id` (0 = nuevo) y prellenado opcional
 * (`nombre`, `foto`, `categoria`). El usuario completa el resto (Shalev.txt).
 */
@HiltViewModel
class ProductoFormViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val productos: ProductoRepository,
    private val insumosRepo: InsumoRepository,
    private val obtenerCategorias: ObtenerCategorias,
    private val guardarProducto: GuardarProducto,
    private val fotos: FotoRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(ProductoFormUiState())
    val state: StateFlow<ProductoFormUiState> = _state.asStateFlow()
    private val eventosCh = Channel<EventoForm>(Channel.BUFFERED)
    val eventos: Flow<EventoForm> = eventosCh.receiveAsFlow()

    init {
        val id = saved.get<Long>("id") ?: 0L
        viewModelScope.launch { runCatchingCancelable { obtenerCategorias() }.onSuccess { c -> _state.update { it.copy(categorias = c) } } }
        viewModelScope.launch { insumosRepo.observarTodos().catch { emit(emptyList()) }.collect { xs -> _state.update { it.copy(insumos = xs) } } }
        viewModelScope.launch {
            productos.observarTodos().catch { emit(emptyList()) }
                .collect { ps -> _state.update { it.copy(otros = ps.map { p -> p.identidad }) } }
        }
        if (id > 0) {
            cargar(id)
        } else {
            _state.update {
                it.copy(
                    form = it.form.copy(
                        categoria = saved.get<String>("categoria")?.trim()?.take(40).orEmpty(),
                        nombre = saved.get<String>("nombre")?.let { n -> ProductoFormLogic.limpiarNombre(n, Validadores.MAX_NOMBRE) }.orEmpty(),
                        fotoUri = saved.get<String>("foto"),
                        precioCosto = saved.get<String>("costo").orEmpty(),
                        precioVenta = saved.get<String>("precio").orEmpty(),
                    ),
                )
            }
        }
    }

    private fun cargar(id: Long) {
        _state.update { it.copy(cargando = true) }
        viewModelScope.launch {
            val p = productos.obtener(id)?.takeUnless { it.eliminado }
            if (p == null) { _state.update { it.copy(cargando = false, noEncontrado = true) }; return@launch }
            val receta = if (p.esElaborado) productos.receta(id) else null
            val xs = receta?.let { r -> insumosRepo.obtenerVarios(r.lineas.map { it.insumoId }).associateBy { it.id } }.orEmpty()
            _state.update { it.copy(cargando = false, form = ProductoFormLogic.desde(p, receta, xs), cantidadLeida = p.cantidad) }
        }
    }

    // ---------------- Edición de campos ----------------

    private fun editar(campo: String?, f: (ProductoForm) -> ProductoForm) = _state.update {
        it.copy(form = f(it.form), cambios = true, tocados = if (campo != null) it.tocados + campo else it.tocados, erroresGuardado = it.erroresGuardado - (campo ?: ""))
    }

    /**
     * P31: «Insumos» es una categoría del Inventario, no un botón aparte. En un artículo nuevo, elegirla (o
     * escribirla) lleva al formulario de insumo, que tiene sus propios campos (unidad, decimales). Al editar
     * un producto existente sigue siendo una categoría reservada (la validación del dominio la rechaza).
     */
    fun categoria(v: String) {
        val s = _state.value
        if (s.esNuevo && Categorias.esInsumos(v)) {
            eventosCh.trySend(EventoForm.EsInsumo(s.form.nombre.trim()))
            return
        }
        editar(Campos.CATEGORIA) { it.copy(categoria = v.take(40)) }
    }
    fun nombre(v: String) = editar(Campos.NOMBRE) { it.copy(nombre = v.take(80)) }
    fun descripcion(v: String) = editar(Campos.DESCRIPCION) { it.copy(descripcion = ProductoFormLogic.unaLinea(v).take(500)) }
    fun fecha(v: LocalDate?) = editar(null) { it.copy(fechaCaducidad = v) }
    fun costoAdicional(v: String) = editar(Campos.COSTO_ADICIONAL) { it.copy(costoAdicional = v.take(16)) }
    fun precioCosto(v: String) = editar(Campos.PRECIO_COSTO) { it.copy(precioCosto = v.take(16)) }
    fun precioVenta(v: String) = editar(Campos.PRECIO_VENTA) { it.copy(precioVenta = v.take(16)) }
    fun cantidad(v: String) = editar(Campos.CANTIDAD) { it.copy(cantidad = v.take(9)) }
    fun nivelBajo(v: String) = editar(Campos.NIVEL_BAJO) { it.copy(nivelBajo = v.take(9)) }
    fun nivelCritico(v: String) = editar(Campos.NIVEL_CRITICO) { it.copy(nivelCritico = v.take(9)) }

    // ---------------- Foto ----------------

    fun fotoElegida(uri: String?) {
        if (uri == null) return
        _state.update { it.copy(importandoFoto = true) }
        viewModelScope.launch {
            when (val r = fotos.importar(uri)) {
                is AppResult.Ok -> _state.update { it.copy(importandoFoto = false, cambios = true, form = it.form.copy(fotoUri = r.value)) }
                is AppResult.Err -> { _state.update { it.copy(importandoFoto = false) }; eventosCh.trySend(EventoForm.Mensaje(ERROR_FOTO)) }
            }
        }
    }

    fun quitarFoto() = editar(null) { it.copy(fotoUri = null) }

    // ---------------- Receta (solo Elaborado) ----------------

    fun mostrarInsumos(v: Boolean) = _state.update { it.copy(hojaInsumos = v) }

    fun agregarInsumo(i: Insumo) = _state.update {
        if (it.form.receta.any { l -> l.insumoId == i.id }) it.copy(hojaInsumos = false)
        else it.copy(
            hojaInsumos = false, cambios = true, tocados = it.tocados + Campos.RECETA,
            form = it.form.copy(receta = it.form.receta + LineaRecetaForm(i.id, i.nombre, i.unidad.simbolo, i.precio)),
        )
    }

    fun cantidadInsumo(insumoId: Long, v: String) = editar(Campos.RECETA) { f ->
        f.copy(receta = f.receta.map { if (it.insumoId == insumoId) it.copy(cantidad = v.take(12)) else it })
    }

    fun quitarInsumo(insumoId: Long) = editar(Campos.RECETA) { f -> f.copy(receta = f.receta.filterNot { it.insumoId == insumoId }) }

    // ---------------- Guardar ----------------

    fun guardar(abrirRecepcion: Boolean = false) {
        val s = _state.value
        if (s.guardando) return
        val listo = ProductoFormLogic.aProducto(s.form, clock.now())
        if (listo == null) { _state.update { it.copy(intentado = true) }; return }
        val (productoListo, receta) = listo
        val registrarRecepcion = abrirRecepcion && s.esNuevo && !productoListo.esElaborado
        if (registrarRecepcion && productoListo.cantidad <= 0) {
            _state.update {
                it.copy(
                    intentado = true,
                    erroresGuardado = it.erroresGuardado + (Campos.CANTIDAD to "Escribe una cantidad mayor que cero para la recepción inicial."),
                )
            }
            return
        }
        _state.update { it.copy(intentado = true, guardando = true) }
        viewModelScope.launch {
            val productoAGuardar = if (registrarRecepcion) productoListo.copy(cantidad = 0) else productoListo
            when (val r = guardarProducto(productoAGuardar, receta, s.cantidadLeida)) {
                is AppResult.Ok -> {
                    _state.update { it.copy(guardando = false) }
                    val inicial = if (registrarRecepcion) RecepcionInicialAlta(
                        tipo = TipoEntidad.PRODUCTO,
                        articuloId = r.value,
                        cantidad = productoListo.cantidad.toString(),
                        costo = Money.editar(productoListo.precioCosto),
                    ) else null
                    eventosCh.trySend(EventoForm.Guardado(
                        if (s.esNuevo) "Producto agregado." else "Cambios guardados.",
                        recepcionInicial = inicial,
                    ))
                }
                is AppResult.Err -> {
                    val campo = (r.error as? cu.shalev.core.result.AppError.Validacion)?.campo ?: (r.error as? cu.shalev.core.result.AppError.Duplicado)?.campo
                    val msg = ProductoFormLogic.mensaje(r.error)
                    _state.update { it.copy(guardando = false, erroresGuardado = if (campo != null && msg != null) mapOf(campo to msg) else emptyMap()) }
                    if (campo == null || msg == null) eventosCh.trySend(EventoForm.Mensaje(mensajeSync(r.error) ?: ERROR_GUARDAR))
                }
            }
        }
    }

    companion object {
        const val ERROR_GUARDAR = "No se pudo guardar. Inténtalo de nuevo."
        const val ERROR_FOTO = "No se pudo usar esa foto. Prueba con otra."
    }
}
