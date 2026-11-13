package cu.shalev.domain.model

import cu.shalev.core.money.Cup
import cu.shalev.core.money.sumOfCup
import cu.shalev.core.quantity.Cantidad
import java.time.Duration
import java.time.Instant

/**
 * Turno de venta. Solo se vende con un turno abierto; al cerrar se congela [resumen].
 * [abiertoPor]/[cerradoPor] = nombre del usuario (Perfil) en ese momento; texto congelado.
 */
data class Turno(
    val id: Long = 0,
    val abiertoEn: Instant,
    val cerradoEn: Instant? = null,
    val resumen: ResumenTurno? = null,
    val abiertoPor: String = "",
    val cerradoPor: String? = null,
    /** P37: en la principal, la app secundaria que lo abrió (null = turno de esta app). */
    val empleadoId: Long? = null,
    /** 0.25.0: efectivo con el que empezó (obligatorio al abrir; null = turno anterior a 0.25.0, sin arqueo). */
    val fondo: Cup? = null,
    /**
     * 0.25.0: efectivo contado al cerrar. En una secundaria con el turno abierto es el conteo ya declarado al solicitar el
     * cierre (viaja a la principal para que el dueño vea la diferencia antes de aprobar).
     */
    val contado: Cup? = null,
    val conteoCierre: ConteoCierre? = null,
) {
    val abierto: Boolean get() = cerradoEn == null
    val deSecundaria: Boolean get() = empleadoId != null

    /** Duración hasta el cierre (o hasta [ahora] si sigue abierto). */
    fun duracion(ahora: Instant): Duration = Duration.between(abiertoEn, cerradoEn ?: ahora).coerceAtLeast(Duration.ZERO)
}

/**
 * Totales congelados al cerrar. [numMovimientos] = [movimientosProducto] + [movimientosInsumo]
 * (incluye las salidas por venta).
 */
data class ResumenTurno(
    val numVentas: Int,
    val unidades: Long,
    val total: Cup,
    val totalEfectivo: Cup,
    val totalTransferencia: Cup,
    val costo: Cup,
    val numMovimientos: Int,
    val ventasEfectivo: Int = 0,
    val ventasTransferencia: Int = 0,
    val movimientosProducto: Int = 0,
    val movimientosInsumo: Int = 0,
    /** 0.25.0: entradas y salidas de efectivo del turno (no son ventas). */
    val entradasCaja: Cup = Cup.ZERO,
    val salidasCaja: Cup = Cup.ZERO,
    /** 0.25.0: ventas anuladas del turno (ya excluidas de los demás totales). */
    val numAnuladas: Int = 0,
) {
    val ganancia: Cup get() = total - costo

    companion object {
        val VACIO = ResumenTurno(0, 0, Cup.ZERO, Cup.ZERO, Cup.ZERO, Cup.ZERO, 0)

        /** Mismo cálculo que hace :data en SQL al cerrar; sirve para el turno abierto (datos provisionales). */
        fun calcular(todas: List<Venta>, movimientos: List<MovimientoInventario>, caja: List<MovimientoCaja> = emptyList()): ResumenTurno {
            val ventas = todas.validas()
            val efectivo = ventas.filter { it.metodoPago == MetodoPago.EFECTIVO }
            val transferencia = ventas.filter { it.metodoPago == MetodoPago.TRANSFERENCIA }
            val prod = movimientos.count { it.entidad == TipoEntidad.PRODUCTO }
            val ins = movimientos.count { it.entidad == TipoEntidad.INSUMO }
            return ResumenTurno(
                numVentas = ventas.size,
                unidades = ventas.sumOf { it.unidades },
                total = ventas.sumOfCup { it.total },
                totalEfectivo = efectivo.sumOfCup { it.total },
                totalTransferencia = transferencia.sumOfCup { it.total },
                costo = ventas.sumOfCup { it.costoTotal },
                numMovimientos = prod + ins,
                ventasEfectivo = efectivo.size,
                ventasTransferencia = transferencia.size,
                movimientosProducto = prod,
                movimientosInsumo = ins,
                entradasCaja = caja.filter { it.tipo == TipoMovimientoCaja.ENTRADA }.sumOfCup { it.importe },
                salidasCaja = caja.filter { it.tipo == TipoMovimientoCaja.SALIDA }.sumOfCup { it.importe },
                numAnuladas = todas.size - ventas.size,
            )
        }
    }
}

/** Artículo vendido en el turno (agrupado por producto; el nombre es el congelado en la venta). */
data class VendidoEnTurno(val productoId: Long, val nombre: String, val unidades: Long, val total: Cup)

/** Variación neta de un insumo en el turno ([cantidad] negativa = consumo). [simbolo] vacío si el insumo ya no existe. */
data class InsumoEnTurno(val insumoId: Long, val nombre: String, val cantidad: Cantidad, val simbolo: String)

/**
 * Registro completo de un turno: cabecera + ventas + movimientos de inventario e insumos.
 * Las ventas y movimientos están ligados por turnoId y no cambian una vez cerrado (ni se puede vender ni
 * se asocian movimientos a un turno cerrado), así que el registro es inmutable.
 */
data class DetalleTurno(
    val turno: Turno,
    val ventas: List<Venta>,
    val movimientos: List<MovimientoInventario>,
    val insumos: List<InsumoEnTurno>,
    /** 0.25.0: entradas y salidas de efectivo, en orden cronológico. */
    val caja: List<MovimientoCaja> = emptyList(),
) {
    /** Congelado si está cerrado; calculado en vivo si sigue abierto. */
    val resumen: ResumenTurno get() = turno.resumen ?: ResumenTurno.calcular(ventas, movimientos, caja)

    /** 0.25.0: arqueo (null en turnos anteriores a 0.25.0, sin fondo). */
    val arqueo: Arqueo? get() = Arqueo.de(turno, resumen)
    val provisional: Boolean get() = turno.abierto

    val movimientosProducto: List<MovimientoInventario> get() = movimientos.filter { it.entidad == TipoEntidad.PRODUCTO }
    val movimientosInsumo: List<MovimientoInventario> get() = movimientos.filter { it.entidad == TipoEntidad.INSUMO }

    /** Más vendidos primero (unidades, luego importe, luego nombre). */
    val vendidos: List<VendidoEnTurno>
        get() = ventas.validas().flatMap { it.detalles }.groupBy { it.productoId }.map { (id, ds) ->
            VendidoEnTurno(id, ds.last().nombre, ds.sumOf { it.cantidad }, ds.sumOfCup { it.subtotal })
        }.sortedWith(compareByDescending<VendidoEnTurno> { it.unidades }.thenByDescending { it.total.centavos }.thenBy { it.nombre })

    val vacio: Boolean get() = ventas.isEmpty() && movimientos.isEmpty()
}

/** 0.25.0: entrada o salida de efectivo durante el turno (no es una venta). */
enum class TipoMovimientoCaja { ENTRADA, SALIDA }

/**
 * 0.25.0: movimiento de efectivo con motivo obligatorio. No se borra ni se edita: un error se corrige con el movimiento
 * contrario. [uuid] = id global (las secundarias lo envían a la principal sin duplicar).
 */
data class MovimientoCaja(
    val id: Long = 0,
    val turnoId: Long,
    val fecha: Instant,
    val tipo: TipoMovimientoCaja,
    val importe: Cup,
    val motivo: String,
    val hechoPor: String = "",
    val uuid: String? = null,
) {
    companion object {
        const val MOTIVO_MIN = 3
        const val MOTIVO_MAX = 60
    }
}

/**
 * 0.25.0: arqueo de caja. Esperado = fondo + ventas en efectivo + entradas − salidas (las transferencias no cuentan).
 * Diferencia = contado − esperado (> 0 sobrante, < 0 faltante, 0 cuadra). [contado] null = aún sin contar.
 */
data class Arqueo(
    val fondo: Cup,
    val ventasEfectivo: Cup,
    val entradas: Cup,
    val salidas: Cup,
    val contado: Cup?,
) {
    val esperado: Cup get() = fondo + ventasEfectivo + entradas - salidas
    val diferencia: Cup? get() = contado?.let { it - esperado }
    val estado: EstadoArqueo? get() = diferencia?.let {
        when {
            it.centavos > 0 -> EstadoArqueo.SOBRANTE
            it.centavos < 0 -> EstadoArqueo.FALTANTE
            else -> EstadoArqueo.CUADRA
        }
    }

    companion object {
        fun de(turno: Turno, resumen: ResumenTurno): Arqueo? = turno.fondo?.let { f ->
            Arqueo(f, resumen.totalEfectivo, resumen.entradasCaja, resumen.salidasCaja, turno.contado)
        }
    }
}

enum class EstadoArqueo { CUADRA, SOBRANTE, FALTANTE }
