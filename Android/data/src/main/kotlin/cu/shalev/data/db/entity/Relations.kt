package cu.shalev.data.db.entity

import androidx.room.Embedded
import androidx.room.Relation

data class VentaCompleta(
    @Embedded val venta: VentaEntity,
    @Relation(parentColumn = "id", entityColumn = "ventaId") val detalles: List<DetalleVentaEntity>,
    @Relation(parentColumn = "id", entityColumn = "ventaId") val transaccion: TransaccionEntity?,
)

data class PreajusteCompleto(
    @Embedded val preajuste: PreajusteEntity,
    @Relation(parentColumn = "id", entityColumn = "preajusteId") val productos: List<PreajusteProductoEntity>,
    @Relation(parentColumn = "id", entityColumn = "preajusteId") val servicios: List<PreajusteServicioEntity>,
)

/** Agregados de un turno calculados en SQL al cerrarlo. */
data class ResumenTurnoRow(
    val numVentas: Int,
    val unidades: Long,
    val totalCent: Long,
    val efectivoCent: Long,
    val transferenciaCent: Long,
    val costoCent: Long,
    val ventasEfectivo: Int,
    val ventasTransferencia: Int,
    /** 0.25.0: ventas anuladas (excluidas de los demás totales). */
    val numAnuladas: Int = 0,
)

/** 0.25.0: arqueo en vivo del turno activo (fondo + efectivo de ventas válidas + entradas − salidas). */
data class ArqueoRow(
    val fondoCent: Long?,
    val contadoCent: Long?,
    val efectivoCent: Long,
    val entradasCent: Long,
    val salidasCent: Long,
)

/** Movimientos del turno por tipo de entidad (PRODUCTO / INSUMO). */
data class MovimientosTurnoRow(
    val producto: Int,
    val insumo: Int,
)
