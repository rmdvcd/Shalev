package cu.shalev.app.herramientas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.app.common.mensajeParaUsuario
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.fichacosto.DestinoFicha
import cu.shalev.domain.fichacosto.FichaCostoRepository
import cu.shalev.domain.fichacosto.PartidaCosto
import cu.shalev.domain.fichacosto.UnidadMargen
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

typealias FichaCostoGuardada = cu.shalev.domain.fichacosto.FichaCostoGuardada

/** Solo estado de edición; almacenamiento, conversión y transacciones pertenecen a data/domain. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FichaCostoViewModel @Inject constructor(private val repo: FichaCostoRepository) : ViewModel() {
    var nombre by mutableStateOf("")
    var margen by mutableStateOf("")
    var unidadMargen by mutableStateOf(UnidadMargen.PORCENTAJE)
    var tasaCupPorUsd by mutableStateOf("")
    var partidas by mutableStateOf<List<PartidaCosto>>(emptyList())
    var fichas by mutableStateOf<List<FichaCostoGuardada>>(emptyList())
        private set
    var idEdicion by mutableStateOf<Long?>(null)
        private set
    var trabajando by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val tieneDatos: Boolean get() = nombre.isNotBlank() || margen.isNotBlank() || partidas.isNotEmpty()

    private val intento = MutableStateFlow(0)
    private var original: FichaCostoGuardada? = null
    val sinGuardar: Boolean get() = original?.let { it != fichaActual(it.id) } ?: tieneDatos
    private fun fichaActual(id: Long) = FichaCostoGuardada(id, nombre.trim(), margen, unidadMargen, tasaCupPorUsd, partidas)

    init {
        viewModelScope.launch {
            intento.flatMapLatest {
                repo.observar().onEach { error = null }
                    .catch { error = "No se pudieron cargar las fichas. No se borró la copia anterior." }
            }.collect { fichas = it }
        }
    }
    fun reintentarCarga() { error = null; intento.update { it + 1 } }
    fun descartarError() { error = null }
    fun guardarPartida(indice: Int?, partida: PartidaCosto) { partidas = if (indice == null) partidas + partida else partidas.mapIndexed { i, p -> if (i == indice) partida else p } }
    fun eliminarPartida(indice: Int) { partidas = partidas.filterIndexed { i, _ -> i != indice } }

    suspend fun guardarFicha(): Result<String> = ejecutar {
        val id = idEdicion ?: (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).coerceAtLeast(1)
        val ficha = fichaActual(id)
        when (val r = repo.guardar(ficha)) {
            is AppResult.Err -> fallo(r.error)
            is AppResult.Ok -> { idEdicion = id; original = ficha; Result.success("Ficha guardada.") }
        }
    }
    fun abrir(f: FichaCostoGuardada) { original = f; idEdicion = f.id; nombre = f.nombre; margen = f.margen; unidadMargen = f.unidadMargen; tasaCupPorUsd = f.tasaCupPorUsd; partidas = f.partidas }
    fun nueva() { original = null; idEdicion = null; nombre = ""; margen = ""; unidadMargen = UnidadMargen.PORCENTAJE; tasaCupPorUsd = ""; partidas = emptyList() }
    suspend fun eliminarFicha(id: Long): Result<String> = ejecutar {
        when (val r = repo.eliminar(id)) {
            is AppResult.Err -> fallo(r.error)
            is AppResult.Ok -> { if (idEdicion == id) nueva(); Result.success("Ficha eliminada.") }
        }
    }
    suspend fun exportarAInventario(ficha: FichaCostoGuardada) = exportar(ficha, DestinoFicha.INVENTARIO)
    suspend fun exportarAServicio(ficha: FichaCostoGuardada) = exportar(ficha, DestinoFicha.SERVICIO)
    private suspend fun exportar(ficha: FichaCostoGuardada, destino: DestinoFicha): Result<String> = ejecutar {
        when (val r = repo.exportar(ficha, destino)) {
            is AppResult.Err -> fallo(r.error)
            is AppResult.Ok -> Result.success("Ficha exportada con su receta y costo completo. Los materiales usan los costos vigentes del local.")
        }
    }
    private fun fallo(e: AppError): Result<String> = Result.failure(IllegalStateException(when {
        e is AppError.Validacion && e.campo == "unidad" -> "Revisa las unidades de las materias: u, kg, g, L o ml. No se mezclan masa, volumen y unidades; máximo tres decimales en la medida del insumo."
        e is AppError.Validacion && e.campo == "tasa" -> "Escribe una tasa CUP por USD mayor que cero."
        e is AppError.Validacion && e.campo == "precioVenta" -> "El precio estimado no supera el costo completo vigente. Revisa el margen; no se creó ningún insumo ni artículo."
        e is AppError.Validacion && e.campo == "ficha" -> "Escribe un nombre, partidas con cantidad mayor que cero y un margen y tasa válidos."
        e is AppError.SinPermiso -> "La exportación de fichas se hace en la app principal, dentro del local seleccionado."
        else -> e.mensajeParaUsuario()
    }))
    private suspend fun ejecutar(bloque: suspend () -> Result<String>): Result<String> {
        if (trabajando) return Result.failure(IllegalStateException("Espera a que termine la operación actual."))
        trabajando = true
        return try { bloque() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { fallo(AppError.Almacenamiento) }
        finally { trabajando = false }
    }
}
