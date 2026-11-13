package cu.shalev.domain.model

import cu.shalev.core.money.Cup
import cu.shalev.core.money.sumOfCup
import cu.shalev.core.quantity.Cantidad
import java.time.Instant

enum class MetodoPago { EFECTIVO, TRANSFERENCIA }

/**
 * P29: qué se vendió en una línea. [DetalleVenta.productoId] es el id DENTRO de su tabla (producto, insumo o
 * servicio), así que dos líneas de clase distinta pueden tener el mismo número.
 */
enum class ClaseArticulo { PRODUCTO, INSUMO, SERVICIO }

/**
 * Línea de venta con precio y costo CONGELADOS en el momento de vender: editar el producto después
 * no altera el historial ni la ganancia. [precioBase] = precio antes de preajustes.
 */
data class DetalleVenta(
    val id: Long = 0,
    val ventaId: Long = 0,
    val productoId: Long,
    val nombre: String,
    val categoria: String,
    val cantidad: Long,
    val precioBase: Cup,
    val precioUnitario: Cup,
    val costoUnitario: Cup,
    val clase: ClaseArticulo = ClaseArticulo.PRODUCTO,
) {
    val subtotal: Cup get() = precioUnitario * cantidad
    val costo: Cup get() = costoUnitario * cantidad
    val ganancia: Cup get() = subtotal - costo
}

data class Venta(
    val id: Long = 0,
    val turnoId: Long,
    val fecha: Instant,
    val metodoPago: MetodoPago,
    val detalles: List<DetalleVenta>,
    val transaccion: Transaccion? = null,
    /** 0.20.0 (H1): quién vendió = quien abrió el turno (nombre congelado). Solo lectura: no se guarda aparte. */
    val vendedor: String = "",
    /** 0.25.0: anulada en la app principal (sigue en Registros, tachada; no cuenta en totales ni en caja). */
    val anulacion: Anulacion? = null,
    /** 0.25.0: «Modificar» = anular + esta venta corregida; id de la venta original a la que sustituye. */
    val corrigeVentaId: Long? = null,
) {
    val anulada: Boolean get() = anulacion != null
    val total: Cup get() = detalles.sumOfCup { it.subtotal }
    val costoTotal: Cup get() = detalles.sumOfCup { it.costo }
    val ganancia: Cup get() = total - costoTotal
    val unidades: Long get() = detalles.sumOf { it.cantidad }
    /** P29: los servicios se venden aparte (una venta tiene solo productos o solo servicios). */
    val esServicio: Boolean get() = detalles.isNotEmpty() && detalles.all { it.clase == ClaseArticulo.SERVICIO }
}

/** 0.25.0: quién, cuándo y por qué se anuló una venta. [motivo] 3–60 caracteres. */
data class Anulacion(val en: Instant, val motivo: String, val por: String) {
    companion object {
        const val MOTIVO_MIN = 3
        const val MOTIVO_MAX = 60
        /** Motivo automático de la venta original al modificarla («Modificada → venta #N»). */
        fun motivoModificada(nueva: Long, motivo: String) = "Modificada → venta #$nueva · $motivo".take(120)
    }
}

/** Ventas válidas (no anuladas): lo único que cuenta en totales, gráficos, ganancia y caja. */
fun List<Venta>.validas(): List<Venta> = filterNot { it.anulada }

/** Transferencia recibida (Registros → "Transferencias recibidas"). */
data class Transaccion(
    val id: Long = 0,
    val ventaId: Long = 0,
    val fecha: Instant,
    val importe: Cup,
    val numero: String,
    val cliente: DatosCliente,
    /** Cuenta/teléfono de cobro usados (texto congelado, por si luego se borran del Perfil). */
    val tarjetaCobro: String? = null,
    val telefonoCobro: String? = null,
    /** 0.20.0 (H1): quién cobró (el que abrió el turno de la venta). Solo lectura. */
    val vendedor: String = "",
    /** 0.27.0 (N2): se marcó «Cliente fijo» al vender: al registrar la venta, el cliente se guarda (o actualiza) por carné. */
    val clienteFijo: Boolean = false,
    /** Conflicto de cobro detectado al reunir ventas offline. Requiere revisar en Registros. */
    val referenciaDuplicada: Boolean = false,
)

data class DatosCliente(val nombreApellidos: String, val ci: String, val telefono: String)

/**
 * Elaborado vendido (redefinición P26): no tiene existencias propias ni pasa por un proceso de producción.
 * Vender [unidades] descuenta de cada insumo su [consumo] (insumoId → cantidad) en la MISMA transacción de la
 * venta (movimientos CONSUMO ligados a la venta). El costo por receta va en [DetalleVenta.costoUnitario].
 */
data class ElaboradoEnVenta(
    val productoId: Long,
    val nombre: String,
    val unidades: Long,
    val consumo: Map<Long, Cantidad>,
    /** P29: PRODUCTO (Elaborado) o SERVICIO (servicio que consume insumos). */
    val clase: ClaseArticulo = ClaseArticulo.PRODUCTO,
)
