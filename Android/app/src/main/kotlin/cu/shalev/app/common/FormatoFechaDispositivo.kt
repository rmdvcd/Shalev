package cu.shalev.app.common

import android.content.Context
import android.text.format.DateFormat
import cu.shalev.core.time.Dates
import cu.shalev.core.time.FormatoFecha
import java.util.Locale

/**
 * P24: fecha y hora con el formato del dispositivo.
 * - Orden de día, mes y año según el idioma del sistema (`getBestDateTimePattern`), año con 4 cifras.
 * - Reloj de 12 o 24 horas según Ajustes del sistema → Fecha y hora.
 * Se aplica al arrancar y cada vez que la app vuelve a primer plano (el usuario pudo cambiarlo mientras tanto).
 */
object FormatoFechaDispositivo {
    fun aplicar(context: Context) {
        val locale = Locale.getDefault()
        val h24 = DateFormat.is24HourFormat(context)
        Dates.formato = FormatoFecha.crear(
            dia = DateFormat.getBestDateTimePattern(locale, "ddMMyyyy"),
            diaMes = DateFormat.getBestDateTimePattern(locale, "ddMM"),
            hora = DateFormat.getBestDateTimePattern(locale, if (h24) "HHmm" else "hhmma"),
            mes = DateFormat.getBestDateTimePattern(locale, "MMM"),
        )
    }
}
