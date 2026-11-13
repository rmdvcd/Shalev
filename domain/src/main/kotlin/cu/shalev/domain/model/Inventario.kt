package cu.shalev.domain.model

import java.time.Instant

enum class TipoEntidad { PRODUCTO, INSUMO }

enum class TipoMovimiento {
    ALTA,        // creación con existencias iniciales
    AJUSTE,      // corrección manual (edición de cantidad o ajuste explícito)
    VENTA,       // salida por venta
    PRODUCCION,  // histórico: producción previa de Elaborados (eliminada en P26; solo respaldos/registros antiguos)
    CONSUMO,     // salida de insumo al vender un Elaborado (P26)
    BAJA,        // eliminación
    ANULACION,   // 0.25.0: devolución de existencias al anular (o modificar) una venta en la app principal
}

/**
 * Movimiento de existencias. [delta] y [existenciaResultante] van en la unidad nativa de la entidad:
 * unidades para PRODUCTO, milésimas para INSUMO. [nombre] se congela.
 */
data class MovimientoInventario(
    val id: Long = 0,
    val fecha: Instant,
    val tipo: TipoMovimiento,
    val entidad: TipoEntidad,
    val entidadId: Long,
    val nombre: String,
    val delta: Long,
    val existenciaResultante: Long,
    val turnoId: Long? = null,
    val ventaId: Long? = null,
    val nota: String? = null,
    /** 0.21.0 (C7): quién lo hizo (vendedor del turno, empleado que ajustó desde su app o el dueño). */
    val hechoPor: String = "",
    val revisiones: List<RevisionMovimiento> = emptyList(),
)

/** Historial inmutable, además del valor vigente del movimiento. */
data class RevisionMovimiento(val fecha: Instant, val anterior: Long, val nuevo: Long, val existenciaAnterior: Long, val existenciaNueva: Long, val autor: String, val motivo: String)
