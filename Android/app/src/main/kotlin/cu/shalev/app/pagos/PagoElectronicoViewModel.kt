package cu.shalev.app.pagos

import cu.shalev.domain.repository.PerfilRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.core.result.AppResult
import cu.shalev.core.validation.Phone
import cu.shalev.domain.validation.Validadores
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.usecase.AgregarTarjeta
import cu.shalev.domain.usecase.AgregarTelefono
import cu.shalev.domain.usecase.EditarTarjeta
import cu.shalev.domain.usecase.EditarTelefono
import cu.shalev.domain.usecase.EliminarTarjeta
import cu.shalev.domain.usecase.EliminarTelefono
import cu.shalev.domain.usecase.SeleccionarPagoElectronico
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PagoUiState(
    val cargado: Boolean = false,
    val perfil: Perfil = Perfil(),
    val edicion: EdicionPago? = null,
    val borrar: Pair<TipoCuentaPago, Long>? = null,
    val guardando: Boolean = false,
)

sealed interface EventoPago {
    data class Mensaje(val texto: String) : EventoPago
}

/**
 * Pago electrónico (Shalev.txt): el usuario introduce o selecciona el teléfono de confirmación y la
 * tarjeta/cuenta que recibe transferencias. P28: una sola pantalla (se abre desde Inicio y desde Ajustes);
 * ya no hay ventana aparte en Inicio ni listas repetidas en Perfil.
 */
@HiltViewModel
class PagoElectronicoViewModel @Inject constructor(
    private val perfilRepo: PerfilRepository,
    private val seleccionar: SeleccionarPagoElectronico,
    private val agregarTelefono: AgregarTelefono,
    private val agregarTarjeta: AgregarTarjeta,
    private val editarTelefono: EditarTelefono,
    private val editarTarjeta: EditarTarjeta,
    private val eliminarTelefono: EliminarTelefono,
    private val eliminarTarjeta: EliminarTarjeta,
) : ViewModel() {

    private val perfil = perfilRepo.perfil
    private val local = MutableStateFlow(PagoUiState())
    private val eventosCh = Channel<EventoPago>(Channel.BUFFERED)
    val eventos: Flow<EventoPago> = eventosCh.receiveAsFlow()

    val state: StateFlow<PagoUiState> = combine(perfil, local) { p, l -> l.copy(cargado = true, perfil = p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PagoUiState())

    // ---------------- Listas ----------------

    fun elegir(tipo: TipoCuentaPago, id: Long) = ejecutar {
        val p = perfil.first()
        val r = if (tipo == TipoCuentaPago.TELEFONO) seleccionar(tarjetaId = p.pagoTarjetaId, telefonoId = id.takeUnless { it == p.pagoTelefonoId }) else seleccionar(tarjetaId = id.takeUnless { it == p.pagoTarjetaId }, telefonoId = p.pagoTelefonoId)
        if (r is AppResult.Err) eventosCh.trySend(EventoPago.Mensaje(mensajePago(r.error)))
    }

    fun nuevo(tipo: TipoCuentaPago) = local.update { it.copy(edicion = EdicionPago(tipo)) }

    fun editar(tipo: TipoCuentaPago, id: Long) {
        viewModelScope.launch {
            val p = perfil.first()
            val e = when (tipo) {
                TipoCuentaPago.TELEFONO -> p.telefonos.firstOrNull { it.id == id }?.let { EdicionPago(tipo, id, it.numero, it.alias.orEmpty()) }
                TipoCuentaPago.TARJETA -> p.tarjetas.firstOrNull { it.id == id }?.let { EdicionPago(tipo, id, formatoCuenta(it.numero), it.alias.orEmpty()) }
            }
            local.update { it.copy(edicion = e) }
        }
    }

    fun editarEdicion(f: (EdicionPago) -> EdicionPago) =
        local.update { s -> s.copy(edicion = s.edicion?.let { f(it).copy(errorServidor = null) }) }

    fun cerrarEdicion() = local.update { it.copy(edicion = null) }

    fun guardarEdicion() {
        val e = local.value.edicion ?: return
        if (e.error != null) { local.update { it.copy(edicion = e.copy(mostrarErrores = true)) }; return }
        val alias = e.alias.trim().ifEmpty { null }
        ejecutar {
            if (e.id == null && yaExiste(e)) {
                local.update { it.copy(edicion = e.copy(mostrarErrores = true, errorServidor = TextosPago.DUPLICADO)) }
                return@ejecutar
            }
            val r = when (e.tipo) {
                TipoCuentaPago.TELEFONO -> if (e.id == null) agregarTelefono(e.numero, alias) else editarTelefono(e.id, e.numero, alias)
                TipoCuentaPago.TARJETA -> if (e.id == null) agregarTarjeta(e.numero, alias) else editarTarjeta(e.id, e.numero, alias)
            }
            when (r) {
                is AppResult.Ok -> local.update { it.copy(edicion = null) }
                is AppResult.Err -> local.update { it.copy(edicion = e.copy(mostrarErrores = true, errorServidor = mensajePago(r.error))) }
            }
        }
    }

    private suspend fun yaExiste(e: EdicionPago): Boolean {
        val p = perfil.first()
        return when (e.tipo) {
            TipoCuentaPago.TELEFONO -> Phone.normalize(e.numero)?.let { n -> p.telefonos.any { it.numero == n } } == true
            TipoCuentaPago.TARJETA -> Validadores.normalizarCuenta(e.numero)?.let { n -> p.tarjetas.any { it.numero == n } } == true
        }
    }

    fun pedirBorrado(tipo: TipoCuentaPago, id: Long) = local.update { it.copy(borrar = tipo to id) }

    fun cancelarBorrado() = local.update { it.copy(borrar = null) }

    fun confirmarBorrado() {
        val (tipo, id) = local.value.borrar ?: return
        local.update { it.copy(borrar = null) }
        ejecutar {
            val r = if (tipo == TipoCuentaPago.TELEFONO) eliminarTelefono(id) else eliminarTarjeta(id)
            if (r is AppResult.Err) eventosCh.trySend(EventoPago.Mensaje(mensajePago(r.error)))
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
