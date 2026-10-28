package cu.shalev.app.venta

import kotlinx.coroutines.flow.distinctUntilChanged
import cu.shalev.domain.repository.SecundariaRepository
import cu.shalev.domain.service.SesionVenta
import cu.shalev.domain.usecase.ObservarElaborados
import cu.shalev.domain.usecase.ObservarServicios
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.model.comoProducto
import kotlinx.coroutines.flow.map
import cu.shalev.domain.repository.ProductoRepository
import cu.shalev.domain.repository.PerfilRepository
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.app.common.Entrada
import cu.shalev.app.common.EntradaCompartida
import cu.shalev.app.inicio.TipoVenta
import cu.shalev.app.registros.TextosTurno
import cu.shalev.core.money.Cup
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.Producto
import cu.shalev.domain.service.Cotizacion
import cu.shalev.domain.service.PagoQr
import cu.shalev.domain.usecase.AbrirTurno
import cu.shalev.domain.usecase.CotizarVenta
import cu.shalev.domain.usecase.DatosTransferencia
import cu.shalev.domain.usecase.ExtraerNumeroTransaccion
import cu.shalev.domain.usecase.ObservarPermisoVenta
import cu.shalev.domain.usecase.ObservarPermisoVenta.Permiso
import cu.shalev.domain.usecase.RegistrarVenta
import cu.shalev.domain.usecase.SmsPago
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [permiso] null = aún cargando. */
data class VentaUiState(
    val permiso: Permiso? = null,
    val abriendo: Boolean = false,
    val tipo: TipoVenta = TipoVenta.VENTA,
    val lineas: List<LineaCarrito> = emptyList(),
    val metodo: MetodoPago = MetodoPago.EFECTIVO,
    val paso: PasoVenta = PasoVenta.CARRITO,
    /** Precios definitivos (con preajustes) del comprobante/QR; null en el carrito. */
    val cotizacion: Cotizacion? = null,
    /** Derivado del Perfil (Pago electrónico); null = aún cargando o método efectivo. */
    val qr: PagoQr.Resultado? = null,
    val cliente: FormCliente = FormCliente(),
    /** Aviso no bloqueante: el importe del SMS no coincide con el total. */
    val avisoSms: String? = null,
    /** Error del paso actual (stock, receta…), visible junto al botón. */
    val error: String? = null,
    val trabajando: Boolean = false,
    val confirmarSalir: Boolean = false,
    /** 0.20.0 (H5): el encargado pidió cerrar el turno; se cerrará al terminar esta venta (P43). */
    val cierrePedido: Boolean = false,
    /** 0.27.0 (N2): clientes fijos para autocompletar. */
    val clientesFijos: List<cu.shalev.domain.model.ClienteFijo> = emptyList(),
) {
    /** 0.27.0 (N2): hasta 3 clientes fijos cuyo nombre contiene lo escrito. */
    val sugerencias: List<cu.shalev.domain.model.ClienteFijo>
        get() = cu.shalev.domain.model.ClienteFijo.sugerencias(clientesFijos, cliente.nombre)
    val bloqueada: Boolean get() = permiso == Permiso.SinTurno
    val totalEstimado: Cup get() = Carrito.totalEstimado(lineas)
    val total: Cup get() = cotizacion?.total ?: totalEstimado
    val puedeContinuar: Boolean get() = lineas.isNotEmpty() && !trabajando && permiso is Permiso.Permitido
}

sealed interface EventoVenta {
    data class Mensaje(val texto: String) : EventoVenta
    /** Abrir el Inventario en modo venta con [preseleccion] marcada. */
    data class ElegirProductos(val tipo: TipoVenta, val preseleccion: List<Long>) : EventoVenta
    data class Terminada(val mensaje: String) : EventoVenta
    data object Salir : EventoVenta
    data object ConfigurarPago : EventoVenta
}

/**
 * Venta completa (Prompt 13). Reglas:
 * - **Sin turno abierto no se vende:** con el turno cerrado la pantalla se bloquea en CUALQUIER paso (el carrito se
 *   conserva) y los botones de confirmar se desactivan; [RegistrarVenta] y la transacción de :data lo verifican otra vez.
 * - Precios definitivos al pasar al comprobante/QR ([CotizarVenta], preajustes por método); el registro vuelve a cotizar.
 * - SMS: pega/compartir siempre disponibles; captura de notificación opcional con acceso explícito, sin READ_SMS.
 */
@HiltViewModel
class VentaViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    observarPermiso: ObservarPermisoVenta,
    private val productosRepo: ProductoRepository,
    observarElaborados: ObservarElaborados,
    insumosRepo: InsumoRepository,
    observarServicios: ObservarServicios,
    private val perfilRepo: PerfilRepository,
    private val abrirTurno: AbrirTurno,
    private val cotizar: CotizarVenta,
    private val registrar: RegistrarVenta,
    private val extraer: ExtraerNumeroTransaccion,
    private val entrada: EntradaCompartida,
    private val sesion: SesionVenta,
    secundaria: SecundariaRepository,
    clientesFijos: cu.shalev.domain.repository.ClienteFijoRepository,
) : ViewModel() {

    /**
     * 0.20.0 (H5, P43): mientras esta pantalla vive hay una venta en curso y el cierre de turno pedido por el
     * encargado espera. Se suelta al registrar, al descartar o al salir.
     */
    private var ficha: Any? = sesion.iniciar()

    private fun soltarVenta() {
        ficha?.let(sesion::terminar)
        ficha = null
    }

    override fun onCleared() {
        entrada.habilitarCapturaSmsAutomatica(false)
        soltarVenta()
        super.onCleared()
    }

    private val tipo: TipoVenta = saved.get<String>(KEY_TIPO)?.let { t -> TipoVenta.entries.firstOrNull { it.name == t } } ?: TipoVenta.VENTA
    private val carrito = MutableStateFlow(Carrito.restaurar(saved.get<LongArray>(KEY_CARRITO)))
    private val local = MutableStateFlow(VentaUiState(tipo = tipo))
    private val eventosCh = Channel<EventoVenta>(Channel.BUFFERED)
    val eventos: Flow<EventoVenta> = eventosCh.receiveAsFlow()

    private val permiso = observarPermiso().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val esServicio = tipo == TipoVenta.SERVICIO

    /**
     * Artículos vivos + tope por insumos:
     * - Productos: productos + insumos vendibles (id negativo) + «Alcanza para N» de cada Elaborado (P26).
     * - Servicios (P29): cada servicio adaptado a fila de carrito; tope = lo que alcanzan sus insumos (sin insumos, ninguno).
     */
    private val productos: StateFlow<CatalogoVenta> =
        (if (esServicio) {
            observarServicios.disponibles().map(CatalogoVenta::deServicios)
        } else {
            combine(productosRepo.observarTodos(), observarElaborados(), insumosRepo.observarTodos()) { l, es, xs ->
                CatalogoVenta.deProductos(l, es, xs)
            }
        })
            .catch { emit(CatalogoVenta(servicios = esServicio)) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, CatalogoVenta(servicios = esServicio))
    private val perfil: StateFlow<Perfil?> = perfilRepo.perfil.catch { emit(Perfil()) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** El QR se deriva del Perfil VIVO: al volver de Pago electrónico ya refleja la tarjeta/móvil nuevos. */
    val state: StateFlow<VentaUiState> = combine(local, permiso, carrito, productos, perfil) { l, p, c, cat, pf ->
        val ajustado = Carrito.recortarACapacidad(c, cat)
        val topes = Carrito.topesCompartidos(ajustado, cat)
        l.copy(
            permiso = p, lineas = Carrito.lineas(ajustado, cat.articulos, topes, servicios = esServicio),
            qr = if (l.metodo == MetodoPago.TRANSFERENCIA && pf != null) PagoQr.transfermovil(pf) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VentaUiState(tipo = tipo))

    init {
        viewModelScope.launch {
            combine(local, perfil) { s, p ->
                s.metodo == MetodoPago.TRANSFERENCIA &&
                    (s.paso == PasoVenta.QR || s.paso == PasoVenta.CLIENTE) &&
                    p?.let { PagoQr.transfermovil(it) is PagoQr.Resultado.Ok } == true
            }.distinctUntilChanged().collect(entrada::habilitarCapturaSmsAutomatica)
        }
        // Primera vez con turno abierto y carrito vacío → directo al Inventario (Shalev.txt: «se navega hasta Inventario»).
        viewModelScope.launch {
            permiso.filterIsInstance<Permiso.Permitido>().first()
            if (carrito.value.isEmpty() && saved.get<Boolean>(KEY_AUTO) != true) {
                saved[KEY_AUTO] = true
                emitir(EventoVenta.ElegirProductos(tipo, emptyList()))
            }
        }
        viewModelScope.launch {
            clientesFijos.observar().catch { emit(emptyList()) }.collect { c -> local.update { it.copy(clientesFijos = c) } }
        }
        viewModelScope.launch {
            secundaria.estado.map { it.cierrePendiente }.distinctUntilChanged().collect { c -> local.update { it.copy(cierrePedido = c) } }
        }
        // SMS compartido o reconocido desde una notificación mientras se cobra una transferencia.
        viewModelScope.launch {
            entrada.entrada.filterNotNull().collect { e ->
                val paso = local.value.paso
                val esperandoTransferencia = local.value.metodo == MetodoPago.TRANSFERENCIA &&
                    (paso == PasoVenta.QR || paso == PasoVenta.CLIENTE)
                when (e) {
                    is Entrada.SmsPagoAutomatico -> when {
                        !esperandoTransferencia -> entrada.consumir(e) // descarta notificaciones obsoletas
                        entrada.consumir(e) -> {
                            if (paso == PasoVenta.QR) local.update { it.copy(paso = PasoVenta.CLIENTE) }
                            aplicarSms(e.pago)
                        }
                    }
                    is Entrada.Texto -> if (esperandoTransferencia && entrada.consumir(e)) {
                        if (paso == PasoVenta.QR) local.update { it.copy(paso = PasoVenta.CLIENTE) }
                        pegarSms(e.texto)
                    }
                    is Entrada.Archivo -> Unit
                }
            }
        }
    }

    // ---------------- Turno ----------------

    /** 0.25.0 (§5.2): [fondo] = efectivo inicial de la caja (obligatorio, puede ser 0). */
    fun abrirTurno(fondo: Cup) {
        if (local.value.abriendo) return
        viewModelScope.launch {
            local.update { it.copy(abriendo = true) }
            val r = abrirTurno.invoke(fondo)
            local.update { it.copy(abriendo = false) }
            when {
                r is AppResult.Ok -> emitir(EventoVenta.Mensaje(TextosTurno.TURNO_ABIERTO))
                r is AppResult.Err && r.error == AppError.TurnoYaAbierto -> Unit // otro lo abrió: el flujo ya lo refleja
                else -> emitir(EventoVenta.Mensaje(TextosTurno.ERROR_ABRIR))
            }
        }
    }

    // ---------------- Carrito ----------------

    fun elegirProductos() = emitir(EventoVenta.ElegirProductos(tipo, carrito.value.keys.toList()))

    /** Resultado del Inventario en modo venta. */
    fun recibirSeleccion(ids: List<Long>) {
        val cat = productos.value
        val deseado = Carrito.desdeSeleccion(ids, carrito.value)
        val ajustado = Carrito.recortarACapacidad(deseado, cat)
        guardar(ajustado)
        local.update { it.copy(paso = PasoVenta.CARRITO, cotizacion = null, error = null) }
        if (ajustado.size < deseado.size) {
            emitir(EventoVenta.Mensaje("Algunos artículos seleccionados ya no tienen insumos o existencias suficientes."))
        }
    }

    fun cambiarCantidad(id: Long, cantidad: Long) {
        val cat = productos.value
        val base = Carrito.recortarACapacidad(carrito.value, cat)
        val topes = Carrito.topesCompartidos(base, cat)
        val max = topes[id] ?: cat.articulos[id]?.let { if (esServicio) Carrito.MAX_CANTIDAD else it.cantidad } ?: state.value.lineas.firstOrNull { it.producto.id == id }?.maximo
        guardar(Carrito.recortarACapacidad(Carrito.cambiar(base, id, cantidad, max), cat))
        local.update { it.copy(error = null) }
    }

    fun quitar(id: Long) {
        guardar(Carrito.quitar(carrito.value, id))
        local.update { it.copy(error = null) }
    }

    /** Artículos o servicios activos con sus topes reales disponibles para el asistente OCR de Ventas. */
    fun candidatosOcr(): List<cu.shalev.app.ocr.CandidatoVentaOcr> {
        val cat = productos.value
        val base = Carrito.recortarACapacidad(carrito.value, cat)
        val topes = Carrito.topesCompartidos(base, cat)
        return cat.articulos.values.map { p ->
            cu.shalev.app.ocr.CandidatoVentaOcr(
                id = p.id,
                nombre = p.nombre,
                esServicio = esServicio,
                topeDisponible = topes[p.id],
            )
        }
    }

    /** Incorpora líneas y datos de cliente leídos desde OCR / Excel / Word / PDF respetando las existencias compartidas. */
    fun importarDesdeOcr(resultado: cu.shalev.app.ocr.ResultadoVentaOcr) {
        val cat = productos.value
        val nuevo = LinkedHashMap(carrito.value)
        for (l in resultado.lineas) {
            if (cat.articulos.containsKey(l.articuloId) && l.cantidad > 0L) {
                nuevo[l.articuloId] = ((nuevo[l.articuloId] ?: 0L) + l.cantidad).coerceAtMost(Carrito.MAX_CANTIDAD)
            }
        }
        val ajustado = Carrito.recortarACapacidad(nuevo, cat)
        guardar(ajustado)
        local.update { s ->
            var cli = s.cliente
            resultado.clienteNombre?.takeIf { it.isNotBlank() }?.let { cli = cli.con(CampoCliente.NOMBRE, it) }
            resultado.clienteTelefono?.takeIf { it.isNotBlank() }?.let { cli = cli.con(CampoCliente.TELEFONO, it) }
            resultado.clienteCi?.takeIf { it.isNotBlank() }?.let { cli = cli.con(CampoCliente.CI, it) }
            s.copy(
                paso = PasoVenta.CARRITO,
                cotizacion = null,
                error = null,
                cliente = cli,
            )
        }
        emitir(EventoVenta.Mensaje("Líneas importadas al carrito (${ajustado.size})."))
    }

    fun elegirMetodo(m: MetodoPago) = local.update { it.copy(metodo = m, error = null) }

    /** CARRITO → COMPROBANTE (efectivo) o QR (transferencia), con los precios definitivos. */
    fun continuar() {
        val s = state.value
        if (!s.puedeContinuar) return
        viewModelScope.launch {
            local.update { it.copy(trabajando = true, error = null) }
            val r = seguro { cotizar(Carrito.solicitud(s.lineas), s.metodo) }
            when (r) {
                is AppResult.Ok -> local.update {
                    it.copy(
                        trabajando = false, cotizacion = r.value,
                        paso = if (s.metodo == MetodoPago.EFECTIVO) PasoVenta.COMPROBANTE else PasoVenta.QR,
                    )
                }
                is AppResult.Err -> local.update { it.copy(trabajando = false, error = TextosVenta.mensaje(r.error)) }
            }
        }
    }

    // ---------------- Navegación entre pasos ----------------

    /** Atrás del sistema o de la barra: retrocede un paso; en el carrito pregunta antes de descartar. */
    fun atras() {
        val s = local.value
        when (s.paso) {
            PasoVenta.CLIENTE -> local.update { it.copy(paso = PasoVenta.QR, avisoSms = null) }
            PasoVenta.QR, PasoVenta.COMPROBANTE -> local.update { it.copy(paso = PasoVenta.CARRITO, cotizacion = null, error = null) }
            PasoVenta.CARRITO -> if (carrito.value.isEmpty()) emitir(EventoVenta.Salir) else local.update { it.copy(confirmarSalir = true) }
        }
    }

    fun cancelarSalir() = local.update { it.copy(confirmarSalir = false) }

    /** Cancelar la venta (botón del comprobante o «Descartar»): no se registra nada. */
    fun descartar() {
        soltarVenta()
        guardar(linkedMapOf())
        local.update { VentaUiState(tipo = tipo) }
        emitir(EventoVenta.Salir)
    }

    // ---------------- Efectivo ----------------

    fun confirmarEfectivo() = registrarVenta(null)

    // ---------------- Transferencia ----------------

    fun pagoRecibido() {
        if (local.value.paso == PasoVenta.QR) local.update { it.copy(paso = PasoVenta.CLIENTE) }
    }

    fun configurarPago() = emitir(EventoVenta.ConfigurarPago)

    fun editarCliente(campo: CampoCliente, valor: String) = local.update { it.copy(cliente = it.cliente.con(campo, valor)) }

    /** 0.27.0 (N2). */
    fun elegirCliente(c: cu.shalev.domain.model.ClienteFijo) = local.update { it.copy(cliente = it.cliente.conCliente(c)) }
    fun clienteFijo(v: Boolean) = local.update { it.copy(cliente = it.cliente.copy(fijo = v)) }

    /** Texto pegado (botón) o compartido: extrae el nº y avisa si el importe del SMS no cuadra con el total. */
    fun pegarSms(texto: String?) {
        if (texto.isNullOrBlank()) { emitir(EventoVenta.Mensaje(TextosVenta.PORTAPAPELES_VACIO)); return }
        val pago = extraer.detalle(texto.take(EntradaCompartida.MAX_TEXTO))
        if (pago == null) { emitir(EventoVenta.Mensaje(TextosVenta.SMS_SIN_NUMERO)); return }
        aplicarSms(pago)
    }

    /** El listener solo publica estos datos reconocidos, nunca el cuerpo de la notificación. */
    private fun aplicarSms(pago: SmsPago) {
        val total = state.value.total
        local.update {
            it.copy(
                cliente = it.cliente.con(CampoCliente.NUMERO, pago.numero),
                avisoSms = pago.importe?.takeIf { imp -> imp != total }?.let { imp -> TextosVenta.smsOtroImporte(imp, total) },
            )
        }
    }

    fun confirmarTransferencia() {
        val f = local.value.cliente
        if (f.errores().isNotEmpty()) { local.update { it.copy(cliente = f.copy(intento = true)) }; return }
        registrarVenta(DatosTransferencia(f.datos(), f.numero, clienteFijo = f.fijo))
    }

    // ---------------- Registro ----------------

    private fun registrarVenta(t: DatosTransferencia?) {
        val s = state.value
        if (s.trabajando || s.lineas.isEmpty()) return
        if (s.bloqueada) { emitir(EventoVenta.Mensaje(TextosVenta.mensaje(AppError.TurnoCerrado))); return }
        viewModelScope.launch {
            local.update { it.copy(trabajando = true, error = null) }
            val r = seguro { registrar(Carrito.solicitud(s.lineas), s.metodo, t) }
            when (r) {
                is AppResult.Ok -> {
                    val total = s.total
                    guardar(linkedMapOf())
                    soltarVenta()
                    local.update { VentaUiState(tipo = tipo, cierrePedido = it.cierrePedido, clientesFijos = it.clientesFijos) }
                    emitir(EventoVenta.Terminada(TextosVenta.registrada(total, s.metodo)))
                }
                is AppResult.Err -> {
                    val msg = TextosVenta.mensaje(r.error)
                    // Stock/receta: hay que cambiar el carrito → se vuelve a él con el error a la vista.
                    val alCarrito = r.error is AppError.StockInsuficiente || r.error == AppError.NoEncontrado ||
                        (r.error as? AppError.Validacion)?.campo == "receta"
                    local.update {
                        it.copy(
                            trabajando = false, error = msg,
                            paso = if (alCarrito) PasoVenta.CARRITO else it.paso,
                            cotizacion = if (alCarrito) null else it.cotizacion,
                        )
                    }
                    if (!alCarrito) emitir(EventoVenta.Mensaje(msg))
                }
            }
        }
    }

    private fun guardar(c: Map<Long, Long>) {
        carrito.value = c
        saved[KEY_CARRITO] = Carrito.aplanar(c)
    }

    /** Ningún fallo de E/S llega a la UI como excepción ni con detalles internos. */
    private suspend fun <T> seguro(bloque: suspend () -> AppResult<T>): AppResult<T> = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppResult.Err(AppError.Desconocido())
    }

    private fun emitir(e: EventoVenta) { eventosCh.trySend(e) }

    companion object {
        const val KEY_TIPO = "tipo"
        const val KEY_CARRITO = "venta.carrito"
        const val KEY_AUTO = "venta.auto"
    }
}
