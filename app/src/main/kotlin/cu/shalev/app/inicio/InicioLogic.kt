package cu.shalev.app.inicio

import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
import cu.shalev.core.money.Percent
import cu.shalev.core.time.Dates
import cu.shalev.designsystem.component.BannerTone
import cu.shalev.designsystem.component.ChartSlice
import cu.shalev.designsystem.theme.AlertTone
import cu.shalev.domain.model.ConteoAlertas
import cu.shalev.domain.model.GraficosPeriodo
import cu.shalev.domain.model.Granularidad
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.OpcionPeriodo
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.Porcion
import cu.shalev.domain.model.ResumenGeneral
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.TopItem
import cu.shalev.domain.model.TopPersona
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.bannerText
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Estado de una sección que se carga: idle → cargando → éxito | vacío | error.
 * [Vacio] conserva los datos (p. ej. qué turno se está mostrando) para poder explicar POR QUÉ está vacío.
 */
sealed interface EstadoCarga<out T> {
    data object Idle : EstadoCarga<Nothing>
    data object Cargando : EstadoCarga<Nothing>
    data class Exito<T>(val datos: T) : EstadoCarga<T>
    data class Vacio<T>(val datos: T? = null) : EstadoCarga<T>
    data class Error(val mensaje: String) : EstadoCarga<Nothing>
}

val EstadoCarga<*>.tieneDatos: Boolean get() = this is EstadoCarga.Exito || this is EstadoCarga.Vacio

/** Tipos del diálogo "Nueva venta" (Shalev.txt: Venta / Elaborado). */
/** P29: la venta es de productos (inventario, incluidos Elaborados e insumos con precio) o de servicios, nunca mezclada. */
enum class TipoVenta(val etiqueta: String, val detalle: String) {
    VENTA("Productos", "Del inventario"),
    SERVICIO("Servicios", "Lo que ofreces"),
}

/** 0.21.0 (C12): tipos de venta según los módulos del negocio y los permisos de esta app. */
fun tiposVenta(p: cu.shalev.domain.model.PermisosApp): List<TipoVenta> = buildList {
    if (p.venderProductos) add(TipoVenta.VENTA)
    if (p.venderServicios) add(TipoVenta.SERVICIO)
}

object TextosInicio {
    /** 0.27.0 (N1): subtítulo de la dona Inventario (productos e insumos, cada uno en su medida). */
    const val EXISTENCIAS_POR_CATEGORIA = "Valor al costo por categoría (CUP)"
    /** 0.20.0 (H5): cierre de turno pedido desde la app principal. */
    const val CIERRE_PEDIDO = "El encargado pidió cerrar tu turno. Se cierra en cuanto no haya una venta en curso."
    /** 0.25.1 (D). */
    const val AHORA_NO = "Ahora no"
    const val CONTAR = "Contar"
    const val CONTAR_PENDIENTE = "Falta contar el efectivo. Toca para contarlo; sin conteo no se cierra."
    const val CIERRE_HECHO = "El encargado cerró tu turno desde la app principal."

    const val ERROR_CARGA = "No se pudieron cargar los datos."
    const val SIN_VENTAS = "Todavía no hay ventas en este período."
    const val SIN_INVENTARIO = "Aún no hay productos con existencias."
    const val SIN_PAGOS = "Sin ventas en los últimos días."
    const val TURNO_ABIERTO = "Turno abierto"
    const val TURNO_CERRADO = "Turno cerrado"
    const val AVISO_TURNO_CERRADO = "Turno cerrado: ábrelo para vender."
    const val CERRAR_TURNO_TITULO = "¿Cerrar el turno?"
    const val CERRAR_TURNO_TEXTO = "Cuenta el efectivo de la caja. Se guarda un resumen del turno con el arqueo; no podrás vender hasta abrir otro."
    /** 0.25.0 (§5.3): conteo del cierre pedido por el encargado (secundaria). */
    const val CONTAR_TITULO = "Cuenta la caja para cerrar"
    const val CONTAR_TEXTO = "El encargado pidió cerrar tu turno. Escribe el efectivo que hay; el turno se cierra al enviarlo."
    /** 0.25.0 (§4): recordatorio mensual de respaldo. */
    fun avisoRespaldo(dias: Long?) = if (dias == null) "Aún no has hecho ningún respaldo. Toca para hacerlo." else "Último respaldo hace $dias días. Toca para hacer otro."
    /** 0.21.0 (C6): en la secundaria el empleado no cierra su turno: lo solicita al encargado. */
    const val SOLICITAR_CIERRE_TITULO = "¿Solicitar el cierre del turno?"
    const val SOLICITAR_CIERRE_TEXTO = "Cuenta el efectivo de la caja. Mientras tanto puedes seguir vendiendo."
    const val CIERRE_SOLICITADO = "Pediste cerrar el turno. Se cerrará cuando el encargado lo apruebe; mientras, puedes seguir vendiendo."
    const val CIERRE_RECHAZADO = "El encargado no aprobó cerrar el turno. Sigue abierto."
    const val SOLICITUD_ENVIADA = "Solicitud de cierre guardada. Se envía al encargado al sincronizar."
    /** 0.21.0 (C6): en la principal, empleados que piden cerrar su turno. */
    fun solicitudesCierre(n: Int) = if (n == 1) "Un empleado pide cerrar su turno" else "$n empleados piden cerrar su turno"
    const val PAGO_SIN_CONFIGURAR = "Sin configurar"
}

// ---------------- Banner ----------------

/** Banner de licencia (reglas de Shalev.txt, en `bannerText`): prueba y Mensual en días, Semestral/Anual en meses, Perpetua oculto. */
fun bannerInicio(licencia: Licencia?, ahora: Instant, zone: ZoneId): String? = licencia?.estado?.bannerText(ahora, zone)

/**
 * P18 (A10): tono del banner según lo que falta. Solo presentación: no cambia cuándo se bloquea la app.
 * - Último día (quedan menos de 24 h; `bannerText` redondea hacia arriba y muestra «1 día») → [BannerTone.Critico].
 * - 7 días o menos → [BannerTone.Aviso]. La prueba dura 7 días, así que siempre se avisa.
 * - Más de 7 días → [BannerTone.Info].
 */
fun tonoBanner(licencia: Licencia?, ahora: Instant): BannerTone = when (val e = licencia?.estado) {
    is LicenseState.Trial -> tonoPorDias(e.daysLeft.toLong())
    is LicenseState.Active -> {
        val horas = Duration.between(ahora, e.venceEn).toHours()
        tonoPorDias(if (horas <= 0) 0 else (horas + 23) / 24)
    }
    else -> BannerTone.Info
}

private fun tonoPorDias(dias: Long): BannerTone = when {
    dias <= 1 -> BannerTone.Critico
    dias <= 7 -> BannerTone.Aviso
    else -> BannerTone.Info
}

/** Segunda línea del banner: qué hacer. Info no la lleva para no distraer. */
fun detalleBanner(tono: BannerTone): String? = when (tono) {
    BannerTone.Info -> null
    BannerTone.Aviso -> TEXTO_BANNER_AVISO
    BannerTone.Critico -> TEXTO_BANNER_CRITICO
}

const val TEXTO_BANNER_AVISO = "Toca para renovar."
const val TEXTO_BANNER_CRITICO = "Vence hoy. Renueva para seguir vendiendo."

// ---------------- Alertas ----------------

data class AlertaUi(val tipo: TipoAlerta, val cantidad: Int) {
    val etiqueta: String get() = tipo.etiqueta()
    val tono: AlertTone get() = tipo.tono()
}

/**
 * 0.31.0: alerta de Inicio pulsada, pendiente de que la pestaña Inventario aplique el filtro. Se consume una vez.
 *
 * No sirve el `SavedStateHandle` de la entrada de navegación: el que Hilt inyecta en el ViewModel es **otro** (se crea
 * a partir de los argumentos de la ruta), así que lo escrito en la entrada no llegaba al filtro. Como `TourPendiente`,
 * es una señal de proceso: si el proceso muere entre medias la alerta se pierde y la lista abre sin filtro.
 */
object AlertaPendiente {
    private val pendiente = java.util.concurrent.atomic.AtomicReference<TipoAlerta?>(null)
    fun pedir(tipo: TipoAlerta) = pendiente.set(tipo)
    fun consumir(): TipoAlerta? = pendiente.getAndSet(null)
}

/** Orden de Shalev.txt. Un contador en 0 se oculta. */
fun alertasVisibles(c: ConteoAlertas): List<AlertaUi> =
    ORDEN_ALERTAS.map { AlertaUi(it, c.de(it)) }.filter { it.cantidad > 0 }

/** 0.20.0 (H6): «Sin existencia» (antes «Existencias negativas») va primero: es un error de inventario que hay que revisar antes que nada. */
private val ORDEN_ALERTAS = listOf(
    TipoAlerta.SIN_EXISTENCIA,
    TipoAlerta.STOCK_BAJO, TipoAlerta.STOCK_CRITICO, TipoAlerta.INSUMO_BAJO, TipoAlerta.INSUMO_CRITICO, TipoAlerta.PROXIMO_A_CADUCAR,
    TipoAlerta.NOMBRE_REPETIDO,
)

fun TipoAlerta.etiqueta(): String = when (this) {
    TipoAlerta.STOCK_BAJO -> "Stock inventario bajo"
    TipoAlerta.STOCK_CRITICO -> "Stock inventario crítico"
    TipoAlerta.INSUMO_BAJO -> "Stock insumo bajo"
    TipoAlerta.INSUMO_CRITICO -> "Stock insumo crítico"
    TipoAlerta.PROXIMO_A_CADUCAR -> "Próximo a caducar"
    TipoAlerta.SIN_EXISTENCIA -> "Sin existencia"
    TipoAlerta.NOMBRE_REPETIDO -> "Nombre repetido: añade descripción"
}

/** Colores de Shalev.txt: amarillo, rojo, verde claro (0.21.2, H6: petróleo, a petición del usuario), naranja, púrpura claro (variantes AA en el design system). */
fun TipoAlerta.tono(): AlertTone = when (this) {
    TipoAlerta.STOCK_BAJO -> AlertTone.StockBajo
    TipoAlerta.STOCK_CRITICO -> AlertTone.StockCritico
    TipoAlerta.INSUMO_BAJO -> AlertTone.InsumoBajo
    TipoAlerta.INSUMO_CRITICO -> AlertTone.InsumoCritico
    TipoAlerta.PROXIMO_A_CADUCAR -> AlertTone.Caducidad
    // Sin token nuevo: el rojo de crítico (AA ya verificado); la etiqueta lo distingue.
    TipoAlerta.SIN_EXISTENCIA -> AlertTone.SinExistencia // 0.21.1 (H4): contador relleno
    TipoAlerta.NOMBRE_REPETIDO -> AlertTone.Revisar // 0.24.0: dato por completar, no de existencias
}

val TipoAlerta.esInsumo: Boolean get() = this == TipoAlerta.INSUMO_BAJO || this == TipoAlerta.INSUMO_CRITICO

// ---------------- Pago electrónico ----------------

data class PagoResumen(val telefono: String?, val cuenta: String?) {
    val configurado: Boolean get() = telefono != null || cuenta != null
    val texto: String get() = if (!configurado) TextosInicio.PAGO_SIN_CONFIGURAR
    else listOfNotNull(telefono?.let { "Tel. $it" }, cuenta?.let { "Cuenta $it" }).joinToString("\n")
}

fun Perfil.pagoResumen() = PagoResumen(telefonoPago?.numero?.let(::formatoTelefono), tarjetaPago?.enmascarado)

/** "+5351234567" → "+53 5123 4567". Otros formatos se dejan tal cual. */
fun formatoTelefono(e164: String): String =
    if (e164.startsWith("+53") && e164.length == 11) "+53 ${e164.substring(3, 7)} ${e164.substring(7)}" else e164

// ---------------- Precios ----------------

fun textoPrecios(activos: Int, total: Int): String = when {
    total == 0 -> "Sin ajustes"
    activos == 0 -> "Ningún ajuste activo"
    activos == 1 -> "1 ajuste activo"
    else -> "$activos ajustes activos"
}

// ---------------- Período y gráficos ----------------

fun OpcionPeriodo.etiqueta(): String = when (this) {
    OpcionPeriodo.TURNO -> "Turno"
    OpcionPeriodo.HOY -> "Hoy"
    OpcionPeriodo.SEMANA -> "7 días"
    OpcionPeriodo.MES -> "Este mes"
    OpcionPeriodo.ANIO -> "Este año"
}

/** Qué se está mostrando, en palabras simples. */
fun descripcionPeriodo(g: GraficosPeriodo, opcion: OpcionPeriodo, zone: ZoneId): String {
    val t = g.turno
    return when {
        g.sinTurnos -> "Aún no hay turnos: se muestran las ventas de hoy"
        t != null && t.abierto -> "Turno actual, desde las ${Dates.time(t.abiertoEn, zone)}"
        t != null -> "Último turno: ${Dates.dayMonthTime(t.abiertoEn, zone)} a ${Dates.time(t.cerradoEn!!, zone)}"
        else -> opcion.etiqueta()
    }
}

/** Etiqueta del eje X según la agrupación, usando los formatos de fecha, hora y mes del dispositivo. */
fun etiquetaPunto(inicio: Instant, g: Granularidad, zone: ZoneId): String = when (g) {
    Granularidad.HORA -> Dates.time(inicio, zone)
    Granularidad.DIA -> Dates.dayMonth(inicio, zone)
    Granularidad.MES -> Dates.month(inicio, zone)
}

fun Cup.enPesos(): Double = centavos / 100.0

/** Resumen hablado del gráfico de Ventas (TalkBack). */
fun descripcionVentas(g: GraficosPeriodo, zone: ZoneId): String {
    val mejor = g.serie.puntos.maxByOrNull { it.ventas }?.takeIf { it.ventas > Cup.ZERO }
    return buildString {
        append("Gráfico de barras de ventas. Total ${Money.format(g.totalVentas)}.")
        mejor?.let { append(" Mayor venta en ${etiquetaPunto(it.inicio, g.serie.granularidad, zone)}: ${Money.format(it.ventas)}.") }
    }
}

fun descripcionGanancia(g: GraficosPeriodo): String =
    "Gráfico de área. Ventas ${Money.format(g.totalVentas)}, costo ${Money.format(g.totalCosto)}, " +
        "ganancia bruta ${Money.format(g.ganancia)}" + (g.margen?.let { ", margen ${Percent.format(it).removePrefix("+")}." } ?: ".")

/**
 * Porciones para la dona: las [max]-1 mayores y el resto sumado en "Otras" (la leyenda debe ser legible).
 * [valor] formatea el valor absoluto; se muestra junto al porcentaje.
 */
fun porcionesUi(porciones: List<Porcion>, max: Int = 6, valor: (Long) -> String? = { null }): List<ChartSlice> {
    val lista = if (porciones.size <= max) porciones else {
        val resto = porciones.drop(max - 1)
        porciones.take(max - 1) + Porcion("Otras", resto.sumOf { it.valor }, resto.sumOf { it.fraccion })
    }
    return lista.map { p ->
        val pct = "${Math.round(p.fraccion * 100)} %"
        ChartSlice(p.etiqueta, p.fraccion, valor(p.valor)?.let { "$it · $pct" } ?: pct)
    }
}

/**
 * 0.27.0 (N1): cantidad de la dona Inventario. Llega en milésimas (los insumos cuentan en su propia medida) y se
 * muestra como número neutro, sin «u» ni otra unidad: 12500 → «12.5», 3000 → «3».
 */
fun cantidadInventario(milesimas: Long): String = Cantidad.format(Cantidad(milesimas))

fun descripcionDona(titulo: String, slices: List<ChartSlice>): String =
    "Gráfico de anillo, $titulo. " + slices.joinToString(". ") { "${it.label}: ${it.valueText}" } + "."

/**
 * 0.27.0 (T14): cuántas alertas van en cada fila de Inicio para que el reparto sea equitativo:
 * 1 → [1]; 2 → [2]; 3 → [3]; 4 → [2, 2]; 5 → [3, 2]; 6 → [3, 3]; con más, filas de 3 como mucho y las más llenas arriba.
 */
fun repartoAlertas(n: Int, maxPorFila: Int = MAX_ALERTAS_POR_FILA): List<Int> {
    if (n <= 0) return emptyList()
    val max = maxPorFila.coerceIn(1, MAX_ALERTAS_POR_FILA)
    val filas = (n + max - 1) / max
    val base = n / filas
    val extra = n % filas
    return List(filas) { i -> if (i < extra) base + 1 else base }
}

/** Máximo de alertas en una fila de Inicio. */
const val MAX_ALERTAS_POR_FILA = 3

/**
 * Ancho mínimo de una tarjeta de alerta con letra al 100 %: lo que ocupa «inventario» (la palabra más larga de las
 * etiquetas) en bodyMedium más el relleno. Con letra mayor se multiplica por la escala.
 */
const val ANCHO_MIN_ALERTA_DP = 96f

/**
 * 0.27.0 (T2, hallado con las capturas al 200 %): cuántas alertas caben por fila sin partir palabras. Con letra
 * normal en un teléfono de 360 dp caben 3; al 130 %, 2; al 200 %, 1.
 */
fun alertasPorFila(anchoDisponibleDp: Float, escalaLetra: Float): Int {
    val porTarjeta = ANCHO_MIN_ALERTA_DP * escalaLetra.coerceAtLeast(1f) + 16f // 16 = separación entre tarjetas
    return ((anchoDisponibleDp + 16f) / porTarjeta).toInt().coerceIn(1, MAX_ALERTAS_POR_FILA)
}

/** 0.27.0 (T3): número de la medalla de cada puesto del Top 3 (índice 0 = primero). */
fun puestoTop(indice: Int): Int? = (indice + 1).takeIf { it in 1..3 }

// ---------------- Top 3 ----------------

enum class TipoTop(val titulo: String) {
    MAS_VENDIDO("Más vendido"),
    LENTO("Lento movimiento"),
    RENTABILIDAD("Rentabilidad"),
    /** P29: servicios (unidades = veces que se prestó). */
    SERVICIO_TOP("Servicios: top ventas"),
    SERVICIO_MENOS("Servicios: menos vendidos"),
    /** Empleados con mayor importe vendido y clientes por transferencia con mayor importe comprado. */
    EMPLEADO("Empleado"),
    CLIENTE("Cliente"),
}

fun valorTop(tipo: TipoTop, item: TopItem): String = when (tipo) {
    TipoTop.MAS_VENDIDO, TipoTop.LENTO -> if (item.unidades == 1L) "1 unidad" else "${item.unidades} unidades"
    TipoTop.RENTABILIDAD -> Money.format(item.ganancia)
    TipoTop.SERVICIO_TOP, TipoTop.SERVICIO_MENOS -> if (item.unidades == 1L) "1 vez" else "${item.unidades} veces"
    TipoTop.EMPLEADO, TipoTop.CLIENTE -> Money.format(item.ingresos)
}

fun subtituloTop(tipo: TipoTop, item: TopItem): String? = when (tipo) {
    TipoTop.MAS_VENDIDO, TipoTop.SERVICIO_TOP -> Money.format(item.ingresos)
    TipoTop.LENTO, TipoTop.SERVICIO_MENOS -> null
    TipoTop.RENTABILIDAD -> if (item.unidades == 1L) "1 unidad vendida" else "${item.unidades} unidades vendidas"
    TipoTop.EMPLEADO -> if (item.unidades == 1L) "1 venta" else "${item.unidades} ventas"
    TipoTop.CLIENTE -> if (item.unidades == 1L) "1 compra" else "${item.unidades} compras"
}

/** Subtítulos de los acordeones de Inicio (de un vistazo, sin abrir): null = sin datos. */
fun resumenVentas(g: GraficosPeriodo): String = Money.format(g.totalVentas)

fun resumenGanancia(g: GraficosPeriodo): String = Money.format(g.ganancia)

fun resumenInventario(r: ResumenGeneral): String =
    if (r.categorias.size == 1) "1 categoría" else "${r.categorias.size} categorías"

fun resumenMetodos(r: ResumenGeneral): String? = r.metodosPago.firstOrNull()?.etiqueta

fun resumenTop(tipo: TipoTop, items: List<TopItem>): String? =
    items.firstOrNull()?.let { "${it.nombre} · ${valorTop(tipo, it)}" }

fun resumenTopPersonas(personas: List<TopPersona>): String? =
    personas.firstOrNull()?.let { "${it.nombre} · ${Money.format(it.importe)}" }
