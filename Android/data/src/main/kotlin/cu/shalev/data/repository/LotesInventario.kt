package cu.shalev.data.repository

import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.entity.LoteConsumoEntity

/**
 * FEFO contable dentro de la transacción de stock (sin inventar lotes para saldos antiguos).
 * La parte que no tiene lote registrado sigue siendo existencia no trazada, nunca se pierde una venta por eso.
 */
internal suspend fun ShalevDatabase.consumirLotes(local: String, entidad: String, entidadId: Long, cantidadMil: Long, ventaId: Long? = null, movimientoId: Long? = null) {
    val localId = almacenLocalDao().porNombre(local)?.id ?: return
    var pendiente = cantidadMil
    for (lote in gestionDao().disponibles(localId, entidad, entidadId)) {
        if (pendiente <= 0) break
        val consumo = minOf(pendiente, lote.disponibleMil)
        gestionDao().actualizarLote(lote.copy(disponibleMil = lote.disponibleMil - consumo))
        if (ventaId != null || movimientoId != null) gestionDao().insertarConsumo(LoteConsumoEntity(ventaId = ventaId, movimientoId = movimientoId, loteId = lote.id, cantidadMil = consumo))
        pendiente -= consumo
    }
}

/** Restituye los lotes realmente asignados a esa venta; anular dos veces se rechaza antes de llegar aquí. */
internal suspend fun ShalevDatabase.devolverLotes(ventaId: Long) {
    for (consumo in gestionDao().consumosDe(ventaId)) {
        val lote = gestionDao().lote(consumo.loteId) ?: continue
        gestionDao().actualizarLote(lote.copy(disponibleMil = Math.addExact(lote.disponibleMil, consumo.cantidadMil).coerceAtMost(lote.cantidadInicialMil)))
    }
    gestionDao().borrarConsumosDe(ventaId)
}

/** Devuelve primero la parte no trazada y después los últimos lotes de ESTE ajuste (no los de otra salida). */
internal suspend fun ShalevDatabase.corregirLotesDeMovimiento(movimientoId: Long, salidaAnteriorMil: Long, salidaNuevaMil: Long) {
    val m = movimientoDao().obtener(movimientoId) ?: return
    if (salidaNuevaMil > salidaAnteriorMil) {
        consumirLotes(m.local, m.entidad, m.entidadId, salidaNuevaMil - salidaAnteriorMil, movimientoId = movimientoId)
        return
    }
    val asignaciones = gestionDao().consumosDeMovimiento(movimientoId)
    val trazado = asignaciones.fold(0L) { acc, a -> Math.addExact(acc, a.cantidadMil) }
    var devolver = (trazado - salidaNuevaMil).coerceAtLeast(0)
    for (a in asignaciones) {
        if (devolver == 0L) break
        val lote = gestionDao().lote(a.loteId) ?: continue
        val n = minOf(devolver, a.cantidadMil)
        gestionDao().actualizarLote(lote.copy(disponibleMil = Math.addExact(lote.disponibleMil, n)))
        if (n == a.cantidadMil) gestionDao().borrarConsumo(a.id)
        else gestionDao().actualizarConsumo(a.copy(cantidadMil = a.cantidadMil - n))
        devolver -= n
    }
}
