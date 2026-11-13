package cu.shalev.app.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import cu.shalev.core.contact.DeveloperContact

/**
 * Canales hacia el desarrollador (Soporte y Licencia). Sin permisos: WhatsApp por enlace wa.me y SMS
 * por intent `smsto:` (el usuario pulsa Enviar en su app de mensajes). Devuelve false si no hay app.
 */
object Contacto {

    fun whatsapp(context: Context, texto: String? = null): Boolean {
        val url = DeveloperContact.WHATSAPP_URL + (texto?.let { "?text=" + Uri.encode(it) } ?: "")
        return start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun sms(context: Context, texto: String? = null): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(DeveloperContact.SMS_URI))
        texto?.let { intent.putExtra("sms_body", it) }
        return start(context, intent)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
