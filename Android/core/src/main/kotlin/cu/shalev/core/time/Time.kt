package cu.shalev.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Reloj inyectable (tests deterministas). */
fun interface Clock {
    fun now(): Instant

    companion object {
        val System = Clock { Instant.now() }
    }
}

/**
 * Patrones de fecha y hora con los que se muestra todo en la app (pantallas, PDF, Excel y CSV).
 *
 * P24: salen del **dispositivo**. La app los fija al arrancar y al volver a primer plano (`FormatoFechaDispositivo`):
 * orden día/mes según el idioma y reloj de 12 o 24 h según Ajustes del sistema. Sin configurar (tests JVM, herramientas)
 * se usan los de siempre: `dd/MM/yyyy` y `HH:mm`.
 */
data class FormatoFecha(
    val dia: String = "dd/MM/yyyy",
    val diaMes: String = "dd/MM",
    val hora: String = "HH:mm",
    val mes: String = "MMM",
) {
    internal val fDia: DateTimeFormatter = DateTimeFormatter.ofPattern(dia)
    internal val fDiaMes: DateTimeFormatter = DateTimeFormatter.ofPattern(diaMes)
    internal val fHora: DateTimeFormatter = DateTimeFormatter.ofPattern(hora)
    internal val fMes: DateTimeFormatter = DateTimeFormatter.ofPattern(mes)
    internal val fDiaHora: DateTimeFormatter = DateTimeFormatter.ofPattern("$dia $hora")
    internal val fDiaMesHora: DateTimeFormatter = DateTimeFormatter.ofPattern("$diaMes $hora")

    companion object {
        val PREDETERMINADO = FormatoFecha()

        /** Formato del dispositivo; si algún patrón no es válido para java.time, se queda el predeterminado. */
        fun crear(dia: String, diaMes: String, hora: String, mes: String = "MMM"): FormatoFecha =
            runCatching { FormatoFecha(dia, diaMes, hora, mes) }.getOrDefault(PREDETERMINADO)
    }
}

/** Persistencia en UTC ([Instant]); presentación en la zona y con el formato del dispositivo. */
object Dates {
    /**
     * Día local de un instante. Sustituye a `LocalDate.ofInstant`, que solo existe desde Android 14 (API 34):
     * en Android 8–13 provocaba un cierre de la app (lint NewApi, 0.27.0).
     */
    fun localDate(i: Instant, zone: ZoneId = ZoneId.systemDefault()): LocalDate = i.atZone(zone).toLocalDate()

    @Volatile
    var formato: FormatoFecha = FormatoFecha.PREDETERMINADO

    fun day(i: Instant, zone: ZoneId = ZoneId.systemDefault()): String = formato.fDia.format(i.atZone(zone))
    fun day(d: LocalDate): String = formato.fDia.format(d)
    fun dayTime(i: Instant, zone: ZoneId = ZoneId.systemDefault()): String = formato.fDiaHora.format(i.atZone(zone))
    fun time(i: Instant, zone: ZoneId = ZoneId.systemDefault()): String = formato.fHora.format(i.atZone(zone))
    fun month(i: Instant, zone: ZoneId = ZoneId.systemDefault()): String = formato.fMes.format(i.atZone(zone))
    fun dayMonth(i: Instant, zone: ZoneId = ZoneId.systemDefault()): String = formato.fDiaMes.format(i.atZone(zone))
    fun dayMonthTime(i: Instant, zone: ZoneId = ZoneId.systemDefault()): String = formato.fDiaMesHora.format(i.atZone(zone))
}
