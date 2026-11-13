package cu.shalev.app.common

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.domain.usecase.SmsPago
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Entradas que llegan a Shalev:
 * - [Texto]: contenido compartido por el usuario, como el SMS de PAGOxMOVIL o una licencia.
 * - [SmsPagoAutomatico]: SMS nuevo reconocido en una notificación, solo con acceso especial autorizado y una venta por
 *   transferencia activa; no consulta el buzón ni guarda el cuerpo.
 * - [Archivo]: un respaldo `.shalev` abierto desde otra app. Shalev lo ignora: el .shalev solo entra con el
 *   gestor de archivos desde Respaldo (almacenamiento interno).
 *
 * Sin permisos: la URI llega con permiso de lectura temporal concedido por la app que comparte.
 */
sealed interface Entrada {
    data class Texto(val texto: String) : Entrada
    /** Datos mínimos extraídos de un SMS nuevo; el cuerpo completo nunca sale del listener. */
    data class SmsPagoAutomatico(val pago: SmsPago) : Entrada
    data class Archivo(val uri: String, val nombre: String?) : Entrada
}

@Singleton
class EntradaCompartida @Inject constructor() {
    private val estado = MutableStateFlow<Entrada?>(null)
    private val capturaSmsActiva = AtomicBoolean(false)
    val entrada: StateFlow<Entrada?> = estado.asStateFlow()

    fun publicar(e: Entrada) { estado.value = e }

    /** VentaViewModel habilita la lectura efímera solo en QR/Cliente durante una transferencia activa. */
    fun habilitarCapturaSmsAutomatica(activa: Boolean) { capturaSmsActiva.set(activa) }

    fun capturaSmsAutomaticaActiva(): Boolean = capturaSmsActiva.get()

    /** Publica solo los campos reconocidos si el usuario dio acceso y la venta lo espera. */
    fun publicarSmsAutomatico(pago: SmsPago): Boolean {
        if (!capturaSmsActiva.get() || pago.numero.isBlank()) return false
        return estado.compareAndSet(null, Entrada.SmsPagoAutomatico(pago))
    }

    /** Quien la usa la consume (una sola vez). Devuelve false si ya no era la actual. */
    fun consumir(e: Entrada): Boolean = estado.compareAndSet(e, null)

    companion object {
        /** Tope del texto compartido: un SMS o una conversación corta, no un documento. */
        const val MAX_TEXTO = 8_000

        /** Traduce el Intent de «Compartir» / «Abrir con». Null si no es para Shalev. Pura salvo la lectura del Intent. */
        fun desdeIntent(intent: Intent?): Entrada? {
            intent ?: return null
            return when (intent.action) {
                Intent.ACTION_SEND -> {
                    // Solo content:// (file:// expondría rutas y exigiría permisos de almacenamiento).
                    val uri = intent.streamUri()?.takeIf { it.scheme == "content" }
                    val texto = intent.getStringExtra(Intent.EXTRA_TEXT)
                    when {
                        uri != null -> Entrada.Archivo(uri.toString(), null)
                        !texto.isNullOrBlank() -> Entrada.Texto(texto.take(MAX_TEXTO))
                        else -> null
                    }
                }
                Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == "content" }?.let { Entrada.Archivo(it.toString(), null) }
                else -> null
            }
        }

        @Suppress("DEPRECATION")
        private fun Intent.streamUri(): Uri? =
            if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    }
}

/**
 * Prompt 14: la navegación principal observa si hay un texto de licencia compartido. (Los .shalev ya no entran
 * por aquí: solo se importan con el gestor de archivos desde Respaldo.)
 */
@HiltViewModel
class EntradaViewModel @Inject constructor(private val entrada: EntradaCompartida) : ViewModel() {
    /**
     * 0.25.0: el mensaje de licencia de GL compartido con Shalev (WhatsApp → Compartir → Shalev) se activa sin pegarlo a mano.
     * Solo lo que el usuario comparte: nunca se lee el portapapeles ni los mensajes en segundo plano.
     */
    val licenciaPendiente: StateFlow<Boolean> =
        entrada.entrada.map { it is Entrada.Texto && EntradaLicencia.esLicencia(it.texto) }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Toma (una sola vez) el texto de licencia compartido; null si no hay o ya lo tomó otro. */
    fun tomarLicencia(): String? {
        val e = entrada.entrada.value as? Entrada.Texto ?: return null
        if (!EntradaLicencia.esLicencia(e.texto)) return null
        return if (entrada.consumir(e)) e.texto else null
    }
}

/** 0.25.0: reconoce un mensaje de licencia corta (prefijo `SPVI2:`) dentro de un texto compartido. */
object EntradaLicencia {
    const val PREFIJO = "SPVI2:"
    fun esLicencia(texto: String): Boolean = texto.contains(PREFIJO)
}
