package cu.shalev.app.registros

import cu.shalev.core.money.Money
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.time.Dates
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.Turno
import cu.shalev.domain.model.Venta
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Textos del turno de venta (en un solo sitio para la UI y los tests). */
object TextosTurno {
    const val SIN_TURNO_TITULO = "No hay un turno abierto"
    const val SIN_TURNO_DETALLE =
        "Para vender, primero abre un turno."
    const val ABRIR = "Abrir turno"
    /** 0.25.1: PDF o Excel del turno. */
    const val COMPARTIR = "Compartir turno"
    fun alcanceExportar(d: cu.shalev.domain.model.DetalleTurno): String =
        "Resumen, arqueo de caja, entradas y salidas de efectivo, ${d.ventas.size} " + (if (d.ventas.size == 1) "venta" else "ventas") +
            " e inventario" + if (d.provisional) ". El turno sigue abierto: datos provisionales." else "."
    const val ABRIENDO = "Abriendo turno…"
    const val TURNO_ABIERTO = "Turno abierto. Ya puedes vender."
    const val PROVISIONAL = "Turno abierto: los totales cambian hasta cerrarlo."
    const val ERROR_CARGA = "No se pudo cargar el registro de turnos."
    const val NO_ENCONTRADO = "Este turno no existe."
    const val SIN_TURNOS_TITULO = "Aún no hay turnos"
    const val SIN_TURNOS_DETALLE = "Abre un turno desde Inicio."
    const val SIN_ACTIVIDAD = "Sin ventas ni movimientos."
    const val SIN_VENTAS = "Sin ventas en este turno"
    const val SIN_MOVIMIENTOS = "Sin movimientos en este turno"
    const val ERROR_ABRIR = "No se pudo abrir el turno. Inténtalo de nuevo."
}


/** P25: un turno se identifica por su fecha de apertura («30/09/2026»), no por su número interno. */
fun tituloTurno(t: Turno, zona: ZoneId): String = Dates.day(t.abiertoEn, zona)

/** Título del detalle: «Turno del 30/09/2026». */
fun tituloDetalleTurno(t: Turno, zona: ZoneId): String = "Turno del ${tituloTurno(t, zona)}"

/** Horas del turno sin repetir la fecha del título: «08:30 – 17:00», «22:00 – 01/10 02:00» o «Desde las 08:30». */
fun horasTurno(t: Turno, zona: ZoneId): String {
    val a = t.abiertoEn
    val c = t.cerradoEn ?: return "Desde las ${Dates.time(a, zona)}"
    val mismoDia = c.atZone(zona).toLocalDate() == a.atZone(zona).toLocalDate()
    return "${Dates.time(a, zona)} – ${if (mismoDia) Dates.time(c, zona) else Dates.dayMonthTime(c, zona)}"
}

fun hora(i: Instant, zona: ZoneId): String = Dates.time(i, zona)

fun fechaHora(i: Instant, zona: ZoneId): String = Dates.dayTime(i, zona)

/** "30/09 08:30 – 17:00", "30/09 22:00 – 01/10 02:00" o, si sigue abierto, "30/09 desde las 08:30". */
fun horarioTurno(t: Turno, zona: ZoneId): String {
    val a = t.abiertoEn
    val c = t.cerradoEn ?: return "${Dates.dayMonth(a, zona)} desde las ${Dates.time(a, zona)}"
    val mismoDia = c.atZone(zona).toLocalDate() == a.atZone(zona).toLocalDate()
    val fin = if (mismoDia) Dates.time(c, zona) else Dates.dayMonthTime(c, zona)
    return "${Dates.dayMonthTime(a, zona)} – $fin"
}

/** "8 h 30 min", "45 min", "0 min". */
fun duracionTexto(d: Duration): String {
    val min = d.toMinutes().coerceAtLeast(0)
    val h = min / 60
    val m = min % 60
    return if (h == 0L) "$m min" else if (m == 0L) "$h h" else "$h h $m min"
}

/** Subtítulo en la lista del Registro: horas y usuario (la fecha ya está en el título). */
fun subtituloTurno(t: Turno, zona: ZoneId): String =
    listOfNotNull(horasTurno(t, zona), t.abiertoPor.ifBlank { null }).joinToString(" · ")

/** Valor a la derecha en la lista: total congelado o "Abierto". */
fun valorTurno(t: Turno): String = t.resumen?.let { Money.format(it.total) } ?: "Abierto"

fun ventasTexto(n: Int): String = if (n == 1) "1 venta" else "$n ventas"

fun unidadesTexto(n: Long): String = if (n == 1L) "1 unidad" else "$n unidades"

fun etiqueta(m: MetodoPago): String = cu.shalev.domain.service.Estadisticas.etiqueta(m)

fun etiqueta(t: TipoMovimiento): String = cu.shalev.domain.service.Estadisticas.etiqueta(t)

/** Cantidad con signo: productos en unidades ("+3", "-2"); insumos en su unidad ("-1.25 kg"). */
fun cantidadConSigno(delta: Long, entidad: TipoEntidad, simbolo: String = ""): String {
    val signo = if (delta < 0) "-" else "+"
    return when (entidad) {
        TipoEntidad.PRODUCTO -> "$signo${abs(delta)}"
        TipoEntidad.INSUMO -> "$signo${Cantidad.format(Cantidad(abs(delta)))}${if (simbolo.isBlank()) "" else " $simbolo"}"
    }
}

fun cantidadInsumo(c: Cantidad, simbolo: String): String = cantidadConSigno(c.milesimas, TipoEntidad.INSUMO, simbolo)

/** Subtítulo de un movimiento: "08:35 · Venta · nota". */
fun subtituloMovimiento(m: MovimientoInventario, zona: ZoneId): String =
    listOfNotNull(hora(m.fecha, zona), etiqueta(m.tipo), m.nota?.trim()?.ifEmpty { null }).joinToString(" · ")

/** Título de una venta en el detalle: "08:35 · Efectivo". */
fun tituloVenta(v: Venta, zona: ZoneId): String = "${hora(v.fecha, zona)} · ${etiqueta(v.metodoPago)}"

/** Subtítulo de una venta: artículos resumidos ("2 × Refresco, 1 × Pan"), recortado si son muchos. */
fun subtituloVenta(v: Venta, max: Int = 3): String {
    val partes = v.detalles.map { "${it.cantidad} × ${it.nombre}" }
    return if (partes.size <= max) partes.joinToString(", ") else partes.take(max).joinToString(", ") + " y ${partes.size - max} más"
}
