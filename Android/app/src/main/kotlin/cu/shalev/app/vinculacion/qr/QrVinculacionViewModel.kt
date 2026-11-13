package cu.shalev.app.vinculacion.qr

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Lector QR mínimo (solo vinculación): lo leído se devuelve tal cual; el vacío se ignora. */
sealed interface EventoQr {
    data class QrLeido(val texto: String) : EventoQr
}

@HiltViewModel
class QrVinculacionViewModel @Inject constructor() : ViewModel() {
    private val eventosCh = Channel<EventoQr>(Channel.BUFFERED)
    val eventos: Flow<EventoQr> = eventosCh.receiveAsFlow()

    /** Llamado por el analizador al leer un QR. El vacío se ignora (sigue escaneando). */
    fun qrDetectado(texto: String) {
        if (texto.isBlank()) return
        eventosCh.trySend(EventoQr.QrLeido(texto))
    }
}
