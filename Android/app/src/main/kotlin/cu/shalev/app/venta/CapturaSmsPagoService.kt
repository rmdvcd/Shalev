package cu.shalev.app.venta

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import cu.shalev.app.common.EntradaCompartida
import cu.shalev.domain.usecase.ExtraerNumeroTransaccion
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Captura opcional del SMS de PAGOxMOVIL desde su notificación, solo mientras una venta por transferencia está
 * esperando el pago. No consulta el buzón, no pide READ_SMS y no guarda el cuerpo del mensaje.
 *
 * Android concede acceso a todas las notificaciones del teléfono, por eso el usuario debe activarlo manualmente en
 * Ajustes. Este servicio descarta las demás y solo publica texto de una notificación de tipo mensaje que contiene un
 * encabezado de banco cubano y un número de transacción reconocido por [ExtraerNumeroTransaccion].
 */
@AndroidEntryPoint
class CapturaSmsPagoService : NotificationListenerService() {

    @Inject lateinit var entrada: EntradaCompartida
    @Inject lateinit var extraer: ExtraerNumeroTransaccion

    private var conectadoDesde = Long.MAX_VALUE

    override fun onListenerConnected() {
        conectadoDesde = System.currentTimeMillis()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.postTime < conectadoDesde || !entrada.capturaSmsAutomaticaActiva()) return
        val notificacion = sbn.notification ?: return
        if (
            (notificacion.flags and Notification.FLAG_GROUP_SUMMARY) != 0 ||
            notificacion.category != Notification.CATEGORY_MESSAGE
        ) return

        val pago = CapturaSmsLogica.extraer(
            listOf(
                notificacion.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
                notificacion.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            ),
            extraer,
        ) ?: return
        entrada.publicarSmsAutomatico(pago)
    }
}
