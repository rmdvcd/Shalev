package cu.shalev.app.ocr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.core.money.Money
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Servicio
import cu.shalev.domain.model.UnidadMedida
import cu.shalev.domain.usecase.GuardarInsumo
import cu.shalev.domain.usecase.GuardarProducto
import cu.shalev.domain.usecase.GuardarServicio
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportarOcrEstado(
    val guardando: Boolean = false,
    val error: String? = null,
)

sealed interface EventoImportarOcr {
    data class Importado(val mensaje: String) : EventoImportarOcr
}

/**
 * Importa lotes de inventario (productos e insumos) o servicios verificando todos los validadores de dominio
 * sin alterar los constructores de los ViewModels existentes.
 */
@HiltViewModel
class ImportarOcrViewModel @Inject constructor(
    private val guardarProducto: GuardarProducto,
    private val guardarInsumo: GuardarInsumo,
    private val guardarServicio: GuardarServicio,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(ImportarOcrEstado())
    val state: StateFlow<ImportarOcrEstado> = _state.asStateFlow()

    private val _eventos = Channel<EventoImportarOcr>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    fun limpiarError() {
        _state.update { it.copy(error = null) }
    }

    fun importarInventario(borradores: List<BorradorImportacionOcr>) {
        val activos = borradores.filter { it.seleccionada }
        if (activos.isEmpty()) {
            _state.update { it.copy(error = "Marca al menos una fila válida para importar.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(guardando = true, error = null) }
            val ahora = clock.now()
            var ok = 0
            var fallidos = 0
            var primerError: String? = null
            for (fila in activos) {
                val nombre = fila[CampoOcr.NOMBRE].trim()
                val catRaw = fila[CampoOcr.CATEGORIA].trim()
                val unidadTxt = MapeadorOcr.extraerUnidad(fila[CampoOcr.UNIDAD])
                val esInsumo = catRaw.equals(Categorias.INSUMOS, ignoreCase = true) ||
                    (unidadTxt.isNotEmpty() && unidadTxt != "u")
                if (esInsumo) {
                    val costo = Money.parse(MapeadorOcr.extraerMoneda(fila[CampoOcr.PRECIO_COSTO]).ifEmpty {
                        MapeadorOcr.extraerMoneda(fila[CampoOcr.PRECIO_VENTA])
                    })
                    val pvTxt = MapeadorOcr.extraerMoneda(fila[CampoOcr.PRECIO_VENTA])
                    val pv = pvTxt.takeIf { it.isNotEmpty() }?.let(Money::parse)?.takeIf { it.centavos > 0 }
                    val cant = Cantidad.parse(MapeadorOcr.extraerCantidad(fila[CampoOcr.CANTIDAD], permitirDecimales = true).ifEmpty { "0" })
                    val um = when (unidadTxt) {
                        "kg" -> UnidadMedida.KILOGRAMO
                        "g" -> UnidadMedida.GRAMO
                        "L" -> UnidadMedida.LITRO
                        "ml" -> UnidadMedida.MILILITRO
                        else -> UnidadMedida.UNIDAD
                    }
                    if (nombre.isEmpty() || costo == null || cant == null) {
                        fallidos++
                        if (primerError == null) primerError = "Revisa nombre, costo y cantidad en «${nombre.ifEmpty { "fila sin nombre" }}»."
                        continue
                    }
                    when (guardarInsumo(
                        Insumo(
                            nombre = nombre,
                            unidad = um,
                            precio = costo,
                            cantidad = cant,
                            creadoEn = ahora,
                            precioVenta = pv,
                        ),
                    )) {
                        is AppResult.Ok -> ok++
                        is AppResult.Err -> {
                            fallidos++
                            if (primerError == null) primerError = "No se pudo guardar «$nombre»."
                        }
                    }
                } else {
                    val pv = Money.parse(MapeadorOcr.extraerMoneda(fila[CampoOcr.PRECIO_VENTA]))
                    val pc = Money.parse(MapeadorOcr.extraerMoneda(fila[CampoOcr.PRECIO_COSTO]).ifEmpty { "0" })
                    val cant = MapeadorOcr.extraerCantidad(fila[CampoOcr.CANTIDAD], permitirDecimales = false)
                        .ifEmpty { "0" }
                        .toLongOrNull()
                    val cat = catRaw.ifEmpty { "General" }.let {
                        if (Categorias.esElaborado(it)) "General" else it
                    }
                    val desc = fila[CampoOcr.DESCRIPCION].trim().take(40).ifEmpty { null }
                    if (nombre.isEmpty() || pv == null || pv.centavos <= 0 || pc == null || cant == null || cant < 0) {
                        fallidos++
                        if (primerError == null) primerError = "Revisa nombre y precio de venta (> 0 CUP) en «${nombre.ifEmpty { "fila sin nombre" }}»."
                        continue
                    }
                    when (guardarProducto(
                        Producto(
                            categoria = cat,
                            nombre = nombre,
                            descripcion = desc,
                            precioCosto = pc,
                            precioVenta = pv,
                            cantidad = cant,
                            creadoEn = ahora,
                        ),
                        receta = null,
                    )) {
                        is AppResult.Ok -> ok++
                        is AppResult.Err -> {
                            fallidos++
                            if (primerError == null) primerError = "No se pudo guardar «$nombre» (puede requerir descripción distinta)."
                        }
                    }
                }
            }
            _state.update { it.copy(guardando = false) }
            if (ok > 0) {
                val resumen = if (fallidos == 0) {
                    if (ok == 1) "1 artículo importado al inventario." else "$ok artículos importados al inventario."
                } else {
                    "$ok importados · $fallidos omitidos."
                }
                _eventos.send(EventoImportarOcr.Importado(resumen))
            } else {
                _state.update { it.copy(error = primerError ?: "Ninguna fila cumplió las validaciones del inventario.") }
            }
        }
    }

    fun importarServicios(borradores: List<BorradorImportacionOcr>) {
        val activos = borradores.filter { it.seleccionada }
        if (activos.isEmpty()) {
            _state.update { it.copy(error = "Marca al menos un servicio válido para importar.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(guardando = true, error = null) }
            val ahora = clock.now()
            var ok = 0
            var fallidos = 0
            var primerError: String? = null
            for (fila in activos) {
                val nombre = fila[CampoOcr.NOMBRE].trim()
                val tipo = fila[CampoOcr.CATEGORIA].trim().ifEmpty { "General" }
                val imp = Money.parse(MapeadorOcr.extraerMoneda(fila[CampoOcr.IMPORTE]))
                val desc = fila[CampoOcr.DESCRIPCION].trim().take(40).ifEmpty { null }
                if (nombre.isEmpty() || imp == null || imp.centavos <= 0) {
                    fallidos++
                    if (primerError == null) primerError = "Revisa nombre e importe (> 0 CUP) en «${nombre.ifEmpty { "fila sin nombre" }}»."
                    continue
                }
                when (guardarServicio(
                    Servicio(
                        nombre = nombre,
                        tipo = tipo,
                        importe = imp,
                        descripcion = desc,
                        creadoEn = ahora,
                    ),
                    insumos = emptyList(),
                )) {
                    is AppResult.Ok -> ok++
                    is AppResult.Err -> {
                        fallidos++
                        if (primerError == null) primerError = "No se pudo guardar el servicio «$nombre»."
                    }
                }
            }
            _state.update { it.copy(guardando = false) }
            if (ok > 0) {
                val resumen = if (fallidos == 0) {
                    if (ok == 1) "1 servicio importado." else "$ok servicios importados."
                } else {
                    "$ok servicios importados · $fallidos omitidos."
                }
                _eventos.send(EventoImportarOcr.Importado(resumen))
            } else {
                _state.update { it.copy(error = primerError ?: "Ninguna fila cumplió las validaciones de servicios.") }
            }
        }
    }
}
