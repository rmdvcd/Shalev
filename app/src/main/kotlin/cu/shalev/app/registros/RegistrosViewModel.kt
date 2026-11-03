package cu.shalev.app.registros

import cu.shalev.core.time.Dates
import cu.shalev.domain.repository.VentaRepository
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.app.common.ArchivosApp
import cu.shalev.app.common.ExportadorArchivos
import cu.shalev.app.common.lanzarExportacion
import cu.shalev.app.inicio.EstadoCarga
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.FiltroRegistros
import cu.shalev.domain.model.ItemMovimiento
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Venta
import cu.shalev.domain.model.VistaRegistro
import cu.shalev.domain.service.FormatoExport
import cu.shalev.domain.service.TablaExport
import cu.shalev.domain.service.TablasExport
import cu.shalev.domain.usecase.ExportarTablas
import cu.shalev.domain.usecase.ObservarRegistro
import cu.shalev.domain.usecase.TipoRegistro
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Elemento abierto en la ventana (hoja inferior) al tocar una fila. */
sealed interface FichaRegistro {
    data class DeVenta(val venta: Venta) : FichaRegistro
    data class DeTransferencia(val transaccion: Transaccion, val venta: Venta? = null) : FichaRegistro
    data class DeMovimiento(val item: ItemMovimiento) : FichaRegistro
}

data class RegistrosUiState(
    val pestana: PestanaRegistros = PestanaRegistros.VENTAS,
    /** Un filtro por tabla: cambiar de pestaña no pierde la búsqueda ni el filtro de las otras. */
    val filtros: Map<TipoRegistro, FiltroRegistros> = emptyMap(),
    val vista: EstadoCarga<VistaRegistro> = EstadoCarga.Cargando,
    val ficha: FichaRegistro? = null,
    val hojaFiltro: Boolean = false,
    /** Prompt 14: hoja «Exportar PDF o Excel» abierta. */
    val hojaExportar: Boolean = false,
    /** Creando el PDF/Excel: barra de progreso y acciones deshabilitadas. */
    val exportando: Boolean = false,
    /** 0.20.0 (H1): nombres de quienes abrieron turnos; el filtro «Vendedor» sale solo con 2 o más. */
    val vendedores: List<String> = emptyList(),
    /** 0.31.0 (P3): filas marcadas con la casilla; si hay alguna, exportar saca solo esas. */
    val seleccion: Set<Long> = emptySet(),
) {
    val tipo: TipoRegistro? get() = pestana.tipo
    val filtro: FiltroRegistros get() = tipo?.let { filtros[it] } ?: FiltroRegistros()
    val datos: VistaRegistro? get() = (vista as? EstadoCarga.Exito)?.datos
    val puedeCompartir: Boolean get() = (datos?.cantidad ?: 0) > 0
    /** 0.31.0 (P3): todas las filas que se ven están marcadas (el botón pasa a «Desmarcar todo»). */
    val todosSeleccionados: Boolean
        get() = seleccion.isNotEmpty() && datos?.let { d -> idsVista(d).all { it in seleccion } } == true
}

sealed interface EventoRegistros {
    /** Enviar el PDF/Excel creado (selector del sistema: WhatsApp, Telegram, Bluetooth, Drive, OneDrive…). */
    data class CompartirArchivo(val archivo: File, val mime: String, val asunto: String) : EventoRegistros
    /** La pantalla abre «Guardar como» (SAF) y devuelve la URI a [RegistrosViewModel.guardarEn]. */
    data class GuardarComo(val nombre: String, val mime: String) : EventoRegistros
    data class Mensaje(val texto: String) : EventoRegistros
}

/**
 * Registros: Ventas, Transferencias recibidas y Movimientos de inventario e insumos (más la pestaña Turnos,
 * que sigue usando [TurnosViewModel]). Cada tabla tiene buscador y filtro por fecha; Ventas y Transferencias
 * también por importe. Tocar una fila abre su ventana; tablas en PDF o Excel (0.26.0: sin texto).
 * Prompt 14: la tabla visible (búsqueda y filtro aplicados) también se exporta en PDF o Excel, para guardar en el
 * teléfono (SAF) o enviar a otra app (FileProvider). Los elementos sueltos solo se ven en su ventana.
 * Solo lectura: reutiliza [ObservarRegistro] (repositorio de Registros), [VentaRepository.obtener] y [ExportarTablas].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RegistrosViewModel @Inject constructor(
    private val observarRegistro: ObservarRegistro,
    private val ventasRepo: VentaRepository,
    private val exportarTablas: ExportarTablas,
    private val archivos: ArchivosApp,
    private val clock: Clock,
    private val saved: SavedStateHandle,
    private val estadoPantallas: cu.shalev.domain.repository.EstadoPantallasRepository,
) : ViewModel() {

    /** Zona para fechas y días del filtro (tests: fija). */
    internal var zona: ZoneId = ZoneId.systemDefault()

    /** Retardo del buscador (0.30.0, F1). Los tests lo bajan a 0 para no depender del reloj virtual. */
    internal var debounceBusqueda: Long = 250

    /**
     * P18 (A12): la pestaña, la búsqueda y los filtros sobreviven a que el sistema cierre el proceso
     * ([SavedStateHandle]). Solo se guarda esto (texto corto, fechas e importes del filtro); nunca filas ni datos de la tabla.
     */
    private val local = MutableStateFlow(
        RegistrosUiState(
            pestana = FiltrosGuardados.pestana(saved.get<String>(FiltrosGuardados.CLAVE_PESTANA)),
            filtros = FiltrosGuardados.leer { saved.get<ArrayList<String>>(it) },
        ),
    )

    /** Última cadena escrita por clave en el ajuste permanente (no se reescribe lo mismo en cada tecla). */
    private val persistido = mutableMapOf<String, String>()

    init {
        // Permanencia al cerrar la app: solo si el SavedStateHandle viene vacío (arranque en frío, no muerte del
        // proceso) se restaura lo guardado la última vez. Si la persona ya tocó algo mientras se leía, no se pisa.
        val deProceso = saved.get<String>(FiltrosGuardados.CLAVE_PESTANA) != null ||
            TipoRegistro.entries.any { saved.get<ArrayList<String>>(FiltrosGuardados.clave(it)) != null }
        if (!deProceso) {
            val inicial = local.value
            viewModelScope.launch {
                val pestana = FiltrosGuardados.pestana(estadoPantallas.observar(FiltrosGuardados.CLAVE_PESTANA_PERSISTENTE).first())
                val filtros = TipoRegistro.entries.mapNotNull { t ->
                    val guardado = estadoPantallas.observar(FiltrosGuardados.clavePersistente(t)).first()
                    guardado?.also { persistido[FiltrosGuardados.clavePersistente(t)] = it }
                    FiltrosGuardados.decodificarTexto(guardado)?.let { t to it }
                }.toMap()
                if (local.value == inicial && (filtros.isNotEmpty() || pestana != inicial.pestana)) {
                    local.value = inicial.copy(pestana = pestana, filtros = filtros)
                    // Espejo en el SavedStateHandle: una muerte del proceso en esta sesión restaura lo mismo.
                    saved[FiltrosGuardados.CLAVE_PESTANA] = pestana.name
                    filtros.forEach { (t, f) -> saved[FiltrosGuardados.clave(t)] = FiltrosGuardados.codificar(f) }
                }
            }
        }
    }

    private fun persistir(clave: String, valor: String) {
        if (persistido[clave] == valor) return
        persistido[clave] = valor
        viewModelScope.launch { estadoPantallas.guardar(clave, valor) }
    }
    private val intento = MutableStateFlow(0)
    private val eventosCh = Channel<EventoRegistros>(Channel.BUFFERED)
    val eventos: Flow<EventoRegistros> = eventosCh.receiveAsFlow()

    private data class Consulta(val tipo: TipoRegistro?, val filtro: FiltroRegistros, val intento: Int)

    /**
     * La vista recuerda de qué tabla es: al cambiar de pestaña se muestra «Cargando» (nunca filas de otra
     * tabla) y al escribir en el buscador la lista anterior sigue visible hasta que llega la nueva (sin parpadeo).
     */
    private val vista: StateFlow<Pair<TipoRegistro?, EstadoCarga<VistaRegistro>>> =
        combine(local, intento) { l, i -> Consulta(l.tipo, l.filtro, i) }
            .distinctUntilChanged()
            // 0.30.0 (F1): escribir en el buscador no dispara una consulta por tecla (8 pulsaciones = 1);
            // pestañas, tipo y «Reintentar» (texto vacío) no esperan. El texto que ve la pantalla es inmediato.
            .debounce { c -> if (c.filtro.texto.isEmpty()) 0L else debounceBusqueda }
            .flatMapLatest { c ->
                val tipo = c.tipo ?: return@flatMapLatest flowOf<Pair<TipoRegistro?, EstadoCarga<VistaRegistro>>>(null to EstadoCarga.Idle)
                val sinFiltro = c.filtro.texto.isBlank() && c.filtro.activos == 0
                observarRegistro(tipo, c.filtro, zona)
                    .map<VistaRegistro, Pair<TipoRegistro?, EstadoCarga<VistaRegistro>>> { v ->
                        tipo to (if (v.cantidad == 0 && sinFiltro) EstadoCarga.Vacio(v) else EstadoCarga.Exito(v))
                    }
                    .catch { e -> if (e is CancellationException) throw e else emit(tipo to EstadoCarga.Error(TextosRegistros.ERROR_CARGA)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null to EstadoCarga.Cargando)

    private val vendedores = observarRegistro.vendedores().catch { e -> if (e is CancellationException) throw e else emit(emptyList()) }

    val state: StateFlow<RegistrosUiState> = combine(local, vista, vendedores) { l, (tipo, v), vs ->
        // 0.31.0 (P3): lo marcado se recorta a lo que se ve (un filtro nuevo puede dejar fuera filas marcadas).
        val datos = (v as? EstadoCarga.Exito)?.datos
        l.copy(
            vista = if (tipo == l.tipo) v else EstadoCarga.Cargando,
            vendedores = vs,
            seleccion = if (datos != null && tipo == l.tipo) seleccionVigente(datos, l.seleccion) else emptySet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegistrosUiState())

    // ---------------- Pestañas, buscador y filtro ----------------

    fun pestana(p: PestanaRegistros) {
        local.update { it.copy(pestana = p, ficha = null, hojaFiltro = false, hojaExportar = false, seleccion = emptySet()) }
        saved[FiltrosGuardados.CLAVE_PESTANA] = p.name
        persistir(FiltrosGuardados.CLAVE_PESTANA_PERSISTENTE, p.name) // sobrevive al cierre de la app
    }

    // ---------------- Selección con casilla (0.31.0, P3) ----------------

    /** Marca o desmarca una fila (la casilla de la lista, igual que en Inventario). */
    fun alternar(id: Long) = local.update { s ->
        val marcadas = if (id in s.seleccion) s.seleccion - id else s.seleccion + id
        s.copy(seleccion = marcadas, ficha = null)
    }

    /** Marca o desmarca todo lo que se ve en la pestaña actual. */
    fun seleccionarTodo() = local.update { s ->
        val ids = (s.datos ?: return@update s).let(::idsVista).toSet()
        s.copy(seleccion = if (s.seleccion.containsAll(ids)) emptySet() else ids, ficha = null)
    }

    fun limpiarSeleccion() = local.update { it.copy(seleccion = emptySet()) }

    fun buscar(texto: String) = editarFiltro { it.copy(texto = texto.take(80)) }

    fun abrirFiltro() { if (local.value.tipo != null) local.update { it.copy(hojaFiltro = true) } }
    fun cerrarFiltro() = local.update { it.copy(hojaFiltro = false) }

    /** El filtro ya validado ([FiltroRegistrosLogic.aplicar]); conserva lo escrito en el buscador. */
    fun aplicarFiltro(f: FiltroRegistros) {
        editarFiltro { actual -> f.copy(texto = actual.texto) }
        cerrarFiltro()
    }

    /** Quita fecha e importe; con [tambienTexto] también vacía el buscador («Sin resultados → Quitar filtros»). */
    fun quitarFiltros(tambienTexto: Boolean = false) = editarFiltro { if (tambienTexto) FiltroRegistros() else it.sinFiltros() }

    fun reintentar() = intento.update { it + 1 }

    private fun editarFiltro(cambio: (FiltroRegistros) -> FiltroRegistros) {
        local.update { s ->
            val tipo = s.tipo ?: return@update s
            s.copy(filtros = s.filtros + (tipo to cambio(s.filtro)))
        }
        val s = local.value
        s.tipo?.let {
            saved[FiltrosGuardados.clave(it)] = FiltrosGuardados.codificar(s.filtro)
            // Permanente (sin el texto del buscador): buscar() pasa por aquí en cada tecla, pero persistir()
            // no reescribe la misma cadena, así que solo hay escritura cuando cambian fecha/importe/vendedor.
            persistir(FiltrosGuardados.clavePersistente(it), FiltrosGuardados.codificarTexto(s.filtro))
        }
    }

    // ---------------- Ventana del elemento ----------------

    fun abrirVenta(id: Long) = abrir { v -> (v as? VistaRegistro.Ventas)?.items?.firstOrNull { it.id == id }?.let(FichaRegistro::DeVenta) }
    fun abrirTransferencia(id: Long) {
        abrir { v -> (v as? VistaRegistro.Transferencias)?.items?.firstOrNull { it.id == id }?.let(FichaRegistro::DeTransferencia) }
        cargarVentaDeTransferencia()
    }
    fun abrirMovimiento(id: Long) =
        abrir { v -> (v as? VistaRegistro.Movimientos)?.items?.firstOrNull { it.movimiento.id == id }?.let(FichaRegistro::DeMovimiento) }

    /**
     * 0.31.0: trae la venta que pagó la transferencia abierta. Así su ficha puede ofrecer corregir el importe y el nº
     * de transacción (que son de la venta) sin salir de ella.
     */
    private fun cargarVentaDeTransferencia() {
        val f = local.value.ficha as? FichaRegistro.DeTransferencia ?: return
        if (f.venta != null) return
        viewModelScope.launch {
            val v = try { ventasRepo.obtener(f.transaccion.ventaId) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (v != null) local.update { s ->
                if ((s.ficha as? FichaRegistro.DeTransferencia)?.transaccion?.id == f.transaccion.id) s.copy(ficha = f.copy(venta = v)) else s
            }
        }
    }

    private fun abrir(buscar: (VistaRegistro) -> FichaRegistro?) {
        val f = state.value.datos?.let(buscar) ?: return
        local.update { it.copy(ficha = f) }
    }

    fun cerrarFicha() = local.update { it.copy(ficha = null) }

    /** Desde una transferencia: abre la venta que pagó (artículos, total). */
    fun verVentaDeTransferencia() {
        val t = (local.value.ficha as? FichaRegistro.DeTransferencia)?.transaccion ?: return
        viewModelScope.launch {
            val v = try { ventasRepo.obtener(t.ventaId) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            if (v != null) local.update { it.copy(ficha = FichaRegistro.DeVenta(v)) }
            else emitir(EventoRegistros.Mensaje(TextosRegistros.VENTA_NO_DISPONIBLE))
        }
    }

    /** La tabla que se ve, con el filtro en el título. Los documentos llevan el CI completo (registro del negocio). */
    private fun tablaVisible(ocultarCi: Boolean): TablaExport? {
        val s = state.value
        val datos = s.datos?.takeIf { it.cantidad > 0 } ?: return null
        // 0.31.0 (P3): con algo marcado, el documento sale solo de esas filas.
        val soloMarcadas = s.seleccion.isNotEmpty()
        val tabla = when (datos) {
            is VistaRegistro.Ventas -> TablasExport.ventas(datos.items.filter { it.id in s.seleccion || !soloMarcadas }, zona)
            is VistaRegistro.Transferencias -> TablasExport.transacciones(
                datos.items.filter { it.id in s.seleccion || !soloMarcadas }, zona, ocultarCi = ocultarCi,
            )
            is VistaRegistro.Movimientos -> TablasExport.movimientosItems(
                datos.items.filter { it.movimiento.id in s.seleccion || !soloMarcadas }, zona,
            )
        }
        val titulo = buildList {
            if (soloMarcadas) add("${s.seleccion.size} seleccionadas")
            detalleFiltro(s)?.let { add(it) }
        }.takeIf { it.isNotEmpty() }?.joinToString(" · ")
        return titulo?.let { tabla.copy(titulo = "${tabla.titulo} ($it)") } ?: tabla
    }

    // ---------------- Exportar PDF / Excel (Prompt 14) ----------------

    fun abrirExportar() { if (state.value.puedeCompartir) local.update { it.copy(hojaExportar = true) } }
    fun cerrarExportar() = local.update { it.copy(hojaExportar = false) }

    /** Formato pendiente de «Guardar como» (la URI llega después, desde el selector del sistema). */
    private data class Pendiente(val formato: FormatoExport, val tabla: TablaExport, val nombre: String)
    private val exportador = ExportadorArchivos<Pendiente>(archivos)

    private fun nombre(tipo: TipoRegistro, f: FormatoExport) =
        TextosRegistros.nombreArchivo(tipo, Dates.localDate(clock.now(), zona), f.extension)

    /** «Enviar»: crea el archivo en la carpeta privada de compartir y abre el selector del sistema. */
    fun enviar(formato: FormatoExport) {
        val tipo = state.value.tipo ?: return
        val tabla = tablaVisible(ocultarCi = false) ?: return
        cerrarExportar()
        trabajar {
            when (val r = exportador.aTemporal(nombre(tipo, formato)) { exportarTablas(listOf(tabla), formato, it) }) {
                is AppResult.Ok -> emitir(EventoRegistros.CompartirArchivo(r.value.archivo, formato.mime, TextosRegistros.asunto(tipo)))
                is AppResult.Err -> emitir(EventoRegistros.Mensaje(TextosRegistros.ERROR_EXPORTAR))
            }
        }
    }

    /** «Guardar en el teléfono»: congela la tabla visible y pide el destino al sistema. */
    fun pedirGuardar(formato: FormatoExport) {
        val tipo = state.value.tipo ?: return
        val tabla = tablaVisible(ocultarCi = false) ?: return
        val sugerido = nombre(tipo, formato)
        exportador.recordar(Pendiente(formato, tabla, sugerido))
        cerrarExportar()
        emitir(EventoRegistros.GuardarComo(sugerido, formato.mime))
    }

    fun guardarEn(uri: String?) {
        val (formato, tabla, nombre) = exportador.tomar(uri) ?: return // nada pendiente o el usuario canceló el selector
        trabajar {
            val r = exportador.aDestino(checkNotNull(uri)) { exportarTablas(listOf(tabla), formato, it) }
            emitir(EventoRegistros.Mensaje(if (r is AppResult.Ok) TextosRegistros.guardado(nombre) else TextosRegistros.ERROR_EXPORTAR))
        }
    }

    private fun trabajar(bloque: suspend () -> Unit) {
        if (local.value.exportando) return
        lanzarExportacion(
            ocupado = { b -> local.update { it.copy(exportando = b) } },
            alFallar = { emitir(EventoRegistros.Mensaje(TextosRegistros.ERROR_EXPORTAR)) },
            bloque = bloque,
        )
    }

    private fun emitir(e: EventoRegistros) { eventosCh.trySend(e) }
}
