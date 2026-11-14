package cu.shalev.app.herramientas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.app.common.mensajeParaUsuario
import cu.shalev.core.money.Money
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.*
import cu.shalev.domain.repository.GestionInventarioRepository
import cu.shalev.domain.usecase.RegistrarAjusteInventario
import cu.shalev.domain.usecase.RegistrarRecepcion
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GestionInventarioViewModel @Inject constructor(
    repo: GestionInventarioRepository,
    private val recibir: RegistrarRecepcion,
    private val ajustar: RegistrarAjusteInventario,
) : ViewModel() {
    var aviso by mutableStateOf<String?>(null)
    var ocupado by mutableStateOf(false)
        private set
    private val intento = MutableStateFlow(0)
    var hayErrorCarga by mutableStateOf(false)
        private set
    var catalogoCargado by mutableStateOf(false)
        private set
    private fun <T> recuperable(mensaje: String, observar: () -> Flow<List<T>>, alEmitir: () -> Unit = {}) = intento.flatMapLatest {
        observar().onEach { alEmitir() }.catch { hayErrorCarga = true; aviso = mensaje }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val articulos = recuperable("No se pudo cargar el catálogo.", repo::articulos) { catalogoCargado = true }
    val recepciones = recuperable("No se pudieron cargar las recepciones.", repo::recepciones)
    val lotes = recuperable("No se pudieron cargar los lotes.", repo::lotes)
    fun reintentarCarga() { hayErrorCarga = false; catalogoCargado = false; intento.update { it + 1 } }
    var articulo by mutableStateOf<ArticuloGestion?>(null)
    var proveedor by mutableStateOf("")
    var comprobante by mutableStateOf("")
    var motivo by mutableStateOf("")
    var cantidad by mutableStateOf("")
    var costo by mutableStateOf("")
    var codigoLote by mutableStateOf("")
    var caducidad by mutableStateOf("")
    var lineas by mutableStateOf<List<LineaRecepcion>>(emptyList())
        private set
    var paso by mutableStateOf(1)
    var esSalida by mutableStateOf(true)
    var motivoAjuste by mutableStateOf(MotivoMovimiento.CORRECCION)
    private var uuid = UUID.randomUUID().toString()
    val tieneDatos get() = lineas.isNotEmpty() || proveedor.isNotBlank() || comprobante.isNotBlank() || motivo.isNotBlank() ||
        cantidad.isNotBlank() || codigoLote.isNotBlank() || caducidad.isNotBlank() ||
        (costo.isNotBlank() && (articulo == null || costo != Money.editar(articulo!!.costo)))

    /** Un cambio de existencias/costos no debe sustituir el costo de compra que se está escribiendo. */
    fun actualizarCatalogo(actuales: List<ArticuloGestion>) {
        val seleccionado = articulo
        val mismo = actuales.firstOrNull { it.id == seleccionado?.id && it.entidad == seleccionado.entidad }
        if (mismo != null) articulo = mismo
        else actuales.firstOrNull()?.let(::elegir) ?: run { articulo = null }
    }

    fun elegir(a: ArticuloGestion) { articulo = a; costo = Money.editar(a.costo) }

    /** Al volver desde un alta, deja la recepción inicial pendiente preparada para el artículo recién creado. */
    fun prepararRecepcionInicial(articulo: ArticuloGestion, cantidadInicial: String?, costoInicial: String?) {
        elegir(articulo)
        if (!cantidadInicial.isNullOrBlank()) cantidad = cantidadInicial
        if (!costoInicial.isNullOrBlank()) costo = costoInicial
        paso = 1
    }

    fun agregarLinea() {
        val a = articulo ?: return informar("Elige un artículo del local.")
        val q = Cantidad.parse(cantidad)?.takeIf { it.milesimas > 0 } ?: return informar("Escribe una cantidad mayor que cero, con hasta tres decimales.")
        val c = Money.parse(costo)?.takeIf { it.centavos >= 0 } ?: return informar("Escribe un costo válido, con hasta dos decimales.")
        val fecha = caducidad.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
        if (caducidad.isNotBlank() && fecha == null) return informar("Usa una fecha como 2026-12-31 o deja la caducidad vacía.")
        if (a.entidad == TipoEntidad.PRODUCTO && q.milesimas % 1000 != 0L) return informar("Los productos se reciben en unidades enteras.")
        if (codigoLote.trim().length !in 1..80) return informar("Identifica el lote; si el proveedor no lo indica, escribe una referencia propia.")
        lineas = lineas + LineaRecepcion(a.entidad, a.id, q, c, codigoLote.trim(), fecha)
        cantidad = ""; codigoLote = ""; caducidad = ""
    }
    fun quitarLinea(indice: Int) { lineas = lineas.filterIndexed { i, _ -> i != indice } }
    fun confirmarRecepcion() = ejecutar {
        when (val r = recibir(NuevaRecepcion(uuid, proveedor, comprobante, motivo, lineas))) {
            is AppResult.Err -> informar(if (r.error is cu.shalev.core.result.AppError.Validacion) "Revisa proveedor, comprobante, motivo y líneas. No se guardó ningún cambio." else r.error.mensajeParaUsuario())
            is AppResult.Ok -> {
                informar("Recepción guardada con sus lotes. Se actualizó el costo promedio; revisa los precios de venta si subió el costo.")
                lineas = emptyList(); proveedor = ""; comprobante = ""; motivo = ""; paso = 1; uuid = UUID.randomUUID().toString()
            }
        }
    }
    fun confirmarAjuste() = ejecutar {
        val a = articulo ?: return@ejecutar informar("Elige un artículo.")
        val q = Cantidad.parse(cantidad)?.takeIf { it.milesimas > 0 } ?: return@ejecutar informar("Escribe la cantidad del cambio, mayor que cero.")
        val delta = if (esSalida) Cantidad(-q.milesimas) else q
        when (val r = ajustar(a.entidad, a.id, delta, motivoAjuste, motivo)) {
            is AppResult.Err -> informar(if (r.error is cu.shalev.core.result.AppError.Validacion) "Escribe un motivo de 3 a 120 caracteres. Merma y consumo interno solo permiten salidas." else r.error.mensajeParaUsuario())
            is AppResult.Ok -> { informar("Ajuste registrado con motivo y autor."); cantidad = ""; motivo = "" }
        }
    }
    private fun informar(texto: String) { aviso = texto }
    private fun ejecutar(bloque: suspend () -> Unit) {
        if (ocupado) return
        ocupado = true
        viewModelScope.launch {
            try { bloque() } catch (e: CancellationException) { throw e }
            catch (_: Exception) { informar("No se pudo guardar. Revisa el estado antes de repetir la operación.") }
            finally { ocupado = false }
        }
    }
}
