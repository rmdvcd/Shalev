package cu.shalev.domain.usecase

import cu.shalev.domain.model.nombreCompleto
import cu.shalev.domain.repository.ServicioRepository
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.comoProducto
import cu.shalev.domain.model.IdArticulo
import cu.shalev.domain.model.ClaseArticulo
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.result.map
import cu.shalev.core.result.toResult
import cu.shalev.core.time.Clock
import cu.shalev.core.validation.Phone
import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.ElaboradoEnVenta
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.repository.PerfilRepository
import cu.shalev.domain.repository.PreciosRepository
import cu.shalev.domain.repository.ProductoRepository
import cu.shalev.domain.repository.TurnoRepository
import cu.shalev.domain.repository.VentaRepository
import cu.shalev.domain.service.Cotizacion
import cu.shalev.domain.service.LineaSolicitada
import cu.shalev.domain.service.PlanificadorVenta
import cu.shalev.domain.service.Recetas
import cu.shalev.domain.validation.Validadores
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Comprobante / QR: totales con preajustes aplicados, sin persistir. P26: para cada Elaborado calcula cuántas
 * unidades alcanzan con sus insumos y su costo por receta (no tiene existencias propias).
 */
class CotizarVenta @Inject constructor(
    private val productos: ProductoRepository,
    private val precios: PreciosRepository,
    private val insumos: InsumoRepository,
    private val servicios: ServicioRepository,
) {
    suspend operator fun invoke(lineas: List<LineaSolicitada>, metodo: MetodoPago): AppResult<Cotizacion> =
        planificar(lineas, metodo).map { it.cotizacion }

    /** Cotización + recetas/insumos leídos, para que [RegistrarVenta] arme el consumo sin volver a leer. */
    internal suspend fun planificar(
        solicitadas: List<LineaSolicitada>, metodo: MetodoPago, devueltos: Devueltos = Devueltos.NINGUNO,
    ): AppResult<Plan> {
        val deServicios = solicitadas.count { it.clase == ClaseArticulo.SERVICIO }
        if (deServicios > 0 && deServicios < solicitadas.size) return AppResult.Err(AppError.Validacion("lineas", AppError.Regla.NO_PERMITIDO))
        if (deServicios > 0) return planificarServicios(solicitadas, metodo, devueltos)
        // P29: los insumos vendibles entran como «producto» con id negativo (IdArticulo) y precio por unidad de medida.
        val idsInsumo = solicitadas.filter { it.clase == ClaseArticulo.INSUMO }.map { it.productoId }.toSet()
        val vendidos = (if (idsInsumo.isEmpty()) emptyList() else insumos.obtenerVarios(idsInsumo)).map(devueltos::aplicar)
        if (vendidos.any { it.precioVenta == null }) return AppResult.Err(AppError.Validacion("precioVenta", AppError.Regla.REQUERIDO))
        val lineas = solicitadas.map { if (it.clase == ClaseArticulo.INSUMO) it.copy(productoId = IdArticulo.deInsumo(it.productoId)) else it }
        val mapa = productos.obtenerVarios(lineas.filter { it.productoId > 0 }.map { it.productoId }.toSet()).map(devueltos::aplicar).associateBy { it.id } +
            vendidos.map { it.comoProducto() }.associateBy { it.id }
        val pedidas = lineas.groupBy { it.productoId }.mapValues { (_, l) -> l.sumOf { it.cantidad } }
        val recetas = mutableMapOf<Long, Receta>()
        val alcanza = mutableMapOf<Long, Long>()
        val costos = mutableMapOf<Long, Cup>()
        var insumosLeidos: Map<Long, Insumo> = emptyMap()
        val elaborados = pedidas.keys.filter { id -> mapa[id]?.esElaborado == true }
        if (elaborados.isNotEmpty()) {
            elaborados.forEach { id -> productos.receta(id)?.let { recetas[id] = it } }
            insumosLeidos = insumos.obtenerVarios(recetas.values.flatMap { r -> r.lineas.map { it.insumoId } }.toSet()).map(devueltos::aplicar).associateBy { it.id }
            recetas.forEach { (id, r) ->
                alcanza[id] = Recetas.maxUnidades(r, insumosLeidos)
                (Recetas.costo(r, insumosLeidos) as? AppResult.Ok)?.let { costos[id] = it.value }
            }
        }
        return PlanificadorVenta.cotizar(lineas, mapa, metodo, precios.preajustesActivos(), alcanza, costos)
            .map { c ->
                val detalles = c.detalles.map { d ->
                    if (IdArticulo.esInsumo(d.productoId)) d.copy(productoId = IdArticulo.insumoId(d.productoId), clase = ClaseArticulo.INSUMO) else d
                }
                Plan(c.copy(detalles = detalles), recetas, insumosLeidos)
            }
    }

    /**
     * P29: venta de servicios (aparte de los productos). Sin preajustes de precio; el tope es lo que permiten los
     * insumos que consume cada servicio (sin insumos, sin tope) y el costo, el de esos insumos.
     */
    private suspend fun planificarServicios(lineas: List<LineaSolicitada>, metodo: MetodoPago, devueltos: Devueltos): AppResult<Plan> {
        val ss = servicios.obtenerVarios(lineas.map { it.productoId }.toSet()).filterNot { it.eliminado }
        val recetas = ss.associate { it.id to servicios.insumos(it.id) }.filterValues { it.isNotEmpty() }
            .mapValues { (id, ls) -> Receta(id, ls) }
        val insumosLeidos = insumos.obtenerVarios(recetas.values.flatMap { r -> r.lineas.map { it.insumoId } }.toSet()).map(devueltos::aplicar).associateBy { it.id }
        val alcanza = mutableMapOf<Long, Long>()
        val costos = mutableMapOf<Long, Cup>()
        val mapa = ss.associate { s ->
            val r = recetas[s.id]
            val tope = r?.let { Recetas.maxUnidades(it, insumosLeidos) } ?: SIN_TOPE
            val costo = r?.let { (Recetas.costo(it, insumosLeidos) as? AppResult.Ok)?.value } ?: Cup.ZERO
            alcanza[s.id] = tope
            costos[s.id] = costo
            s.id to Producto(
                id = s.id, categoria = s.tipo, nombre = s.nombreCompleto, precioCosto = costo, precioVenta = s.importe,
                cantidad = tope, creadoEn = s.creadoEn,
            )
        }
        // El planificador trabaja con artículos genéricos; para servicios usa sus ids seleccionados como ids de producto.
        val ajustesServicios = precios.preajustesActivos().map { it.copy(productoIds = it.servicioIds) }
        return PlanificadorVenta.cotizar(lineas.map { it.copy(clase = ClaseArticulo.PRODUCTO) }, mapa, metodo, ajustesServicios, alcanza, costos)
            .map { c -> Plan(c.copy(detalles = c.detalles.map { it.copy(clase = ClaseArticulo.SERVICIO) }, elaborados = emptyMap()), recetas, insumosLeidos, servicios = true) }
    }

    internal data class Plan(
        val cotizacion: Cotizacion,
        val recetas: Map<Long, Receta>,
        val insumos: Map<Long, Insumo>,
        val servicios: Boolean = false,
    ) {
        /**
         * Nota: dos Elaborados que comparten insumo se validan por separado en la cotización; si juntos no
         * alcanzan, la transacción de :data lo detecta (StockInsuficiente con el insumo) y no registra nada.
         */
        fun elaborados(): AppResult<List<ElaboradoEnVenta>> = if (servicios) AppResult.Ok(
            // P29: cada servicio que consume insumos los descuenta al venderse (como un Elaborado).
            cotizacion.detalles.mapNotNull { d ->
                recetas[d.productoId]?.let { r ->
                    ElaboradoEnVenta(d.productoId, d.nombre, d.cantidad, Recetas.consumo(r, d.cantidad), ClaseArticulo.SERVICIO)
                }
            },
        ) else AppResult.Ok(
            cotizacion.elaborados.map { (id, unidades) ->
                val receta = recetas[id] ?: return AppResult.Err(AppError.Validacion("receta", AppError.Regla.REQUERIDO))
                ElaboradoEnVenta(
                    productoId = id,
                    nombre = cotizacion.detalles.first { it.productoId == id }.nombre,
                    unidades = unidades,
                    consumo = Recetas.consumo(receta, unidades),
                )
            },
        )
    }
}

/**
 * 0.25.0: existencias que DEVOLVERÍA anular la venta que se modifica (productos en unidades, insumos en milésimas). Se
 * suman a las actuales al cotizar la venta corregida, porque en la transacción la anulación va antes que la venta nueva.
 */
data class Devueltos(val productos: Map<Long, Long> = emptyMap(), val insumosMil: Map<Long, Long> = emptyMap()) {
    fun aplicar(p: Producto): Producto = productos[p.id]?.let { p.copy(cantidad = p.cantidad + it) } ?: p
    fun aplicar(i: Insumo): Insumo = insumosMil[i.id]?.let { i.copy(cantidad = cu.shalev.core.quantity.Cantidad(i.cantidad.milesimas + it)) } ?: i

    companion object {
        val NINGUNO = Devueltos()

        /** A partir de las salidas de la venta (deltas negativos): lo devuelto es el contrario. */
        fun de(movimientos: List<cu.shalev.domain.model.MovimientoInventario>): Devueltos = Devueltos(
            productos = movimientos.filter { it.entidad == cu.shalev.domain.model.TipoEntidad.PRODUCTO }
                .groupBy { it.entidadId }.mapValues { (_, l) -> -l.sumOf { it.delta } },
            insumosMil = movimientos.filter { it.entidad == cu.shalev.domain.model.TipoEntidad.INSUMO }
                .groupBy { it.entidadId }.mapValues { (_, l) -> -l.sumOf { it.delta } },
        )
    }
}

/** Tope «sin límite» de un servicio que no consume insumos. */
private const val SIN_TOPE = Long.MAX_VALUE / 4

/** [clienteFijo] (0.27.0, N2): al registrar la venta, el cliente se guarda o actualiza como cliente fijo. */
data class DatosTransferencia(val cliente: DatosCliente, val numeroTransaccion: String, val clienteFijo: Boolean = false)

/**
 * Registra la venta: exige turno abierto, datos del cliente si es transferencia, recalcula la cotización
 * (precios/stock pueden haber cambiado desde el comprobante) y persiste de forma atómica: artículos → VENTA y
 * Elaborados → CONSUMO de sus insumos (P26: sin producción previa ni existencias del Elaborado).
 */
class RegistrarVenta @Inject constructor(
    private val cotizar: CotizarVenta,
    private val turnos: TurnoRepository,
    private val ventas: VentaRepository,
    private val perfil: PerfilRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(lineas: List<LineaSolicitada>, metodo: MetodoPago, transferencia: DatosTransferencia? = null): AppResult<Long> {
        val turno = turnos.activo() ?: return AppResult.Err(AppError.TurnoCerrado)

        val cliente = when (val r = validarTransferencia(metodo, transferencia)) {
            is AppResult.Err -> return r
            is AppResult.Ok -> r.value
        }

        val plan = when (val r = cotizar.planificar(lineas, metodo)) {
            is AppResult.Err -> return r
            is AppResult.Ok -> r.value
        }
        val cot = plan.cotizacion
        val elaborados = when (val r = plan.elaborados()) {
            is AppResult.Err -> return r
            is AppResult.Ok -> r.value
        }
        val now = clock.now()
        val transaccion = cliente?.let { t ->
            val p = perfil.perfil.first()
            transaccion(t, now, cot.total, p.tarjetaPago?.numero, p.telefonoPago?.numero)
        }
        return ventas.registrar(
            Venta(turnoId = turno.id, fecha = now, metodoPago = metodo, detalles = cot.detalles, transaccion = transaccion),
            elaborados,
        )
    }
}

/** Datos del cliente validados (null si es en efectivo). Compartido por [RegistrarVenta] y [ModificarVenta]. */
internal fun validarTransferencia(metodo: MetodoPago, transferencia: DatosTransferencia?): AppResult<DatosTransferencia?> =
    if (metodo == MetodoPago.TRANSFERENCIA) {
        val t = transferencia ?: return AppResult.Err(AppError.Validacion("transferencia", AppError.Regla.REQUERIDO))
        when (val r = (Validadores.cliente(t.cliente) + listOfNotNull(Validadores.numeroTransaccion(t.numeroTransaccion))).toResult()) {
            is AppResult.Err -> r
            is AppResult.Ok -> AppResult.Ok(t)
        }
    } else AppResult.Ok(null)

internal fun transaccion(t: DatosTransferencia, fecha: java.time.Instant, importe: Cup, tarjeta: String?, telefono: String?) = Transaccion(
    fecha = fecha,
    importe = importe,
    numero = t.numeroTransaccion.trim().uppercase(),
    cliente = DatosCliente(
        nombreApellidos = t.cliente.nombreApellidos.trim().replace(Regex("\\s+"), " "),
        ci = t.cliente.ci.trim(),
        telefono = Phone.normalize(t.cliente.telefono)!!,
    ),
    tarjetaCobro = tarjeta,
    telefonoCobro = telefono,
    clienteFijo = t.clienteFijo,
)

/** Lo que se reconoce de un SMS de PAGOxMOVIL. [importe] null si el texto no lo trae. */
data class SmsPago(val numero: String, val importe: Cup?)

/**
 * C2 (sin READ_SMS ni acceso al buzón): Shalev recibe el texto cuando el usuario lo pega/comparta o cuando el servicio
 * opcional de notificaciones lo entrega durante una transferencia activa. Este parser no accede a Android ni a SMS.
 * Formato verificado con SMS reales (BPA, 30/9/2026):
 * "Nro. Transaccion: BR601ADLM8997" (transferencia) y "No. Transaccion: BR601AG4M9997" (pago).
 * Si se pega la conversación entera se toma la ÚLTIMA transacción (la más reciente queda abajo).
 * Si no reconoce el texto devuelve null y el usuario lo teclea.
 */
class ExtraerNumeroTransaccion @Inject constructor() {
    private val principal = Regex(
        "transacci[oó]n\\s*(?:no\\.?|nro\\.?|n[º°]|#)?\\s*[:.]?\\s*([A-Z0-9]{6,30})\\b", RegexOption.IGNORE_CASE,
    )
    private val alternativo = Regex("\\b(?:nro|no|n[º°])\\.?\\s*[:.]\\s*([A-Z0-9]{6,30})\\b", RegexOption.IGNORE_CASE)
    private val importe = Regex(
        "(?:monto|importe(?:\\s+pagado)?)\\s*:\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*CUP", RegexOption.IGNORE_CASE,
    )

    operator fun invoke(sms: String): String? = detalle(sms)?.numero

    fun detalle(sms: String): SmsPago? {
        val m = principal.findAll(sms).lastOrNull { valido(it.groupValues[1]) }
            ?: alternativo.findAll(sms).lastOrNull { valido(it.groupValues[1]) }
            ?: return null
        // El importe que acompaña a ESA transacción: el último "Monto/Importe" escrito antes del número.
        val previo = sms.substring(0, m.range.first).let { t -> bloque(t) }
        val monto = importe.findAll(previo).lastOrNull()?.groupValues?.get(1)?.let(Money::parse)
        return SmsPago(m.groupValues[1].uppercase(), monto)
    }

    private fun valido(n: String) = n.any(Char::isDigit) && n.any(Char::isLetterOrDigit)

    /** Texto del mismo SMS: desde el último encabezado de banco ("Banco …:") hasta el número. */
    private fun bloque(antes: String): String {
        val i = antes.lastIndexOf("Banco ", ignoreCase = true)
        return if (i >= 0) antes.substring(i) else antes.takeLast(400)
    }
}

