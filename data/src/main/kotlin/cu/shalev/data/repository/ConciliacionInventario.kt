package cu.shalev.data.repository

import cu.shalev.core.result.AppError
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.abortar
import cu.shalev.data.db.entity.MovimientoEntity
import cu.shalev.data.dto.aJson
import cu.shalev.data.dto.conteoDeJson
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.ConteoCierre
import cu.shalev.domain.model.LineaConteo
import cu.shalev.domain.model.TipoEntidad

/**
 * 0.32.0: el conteo de inventario es **parcial**: solo se cuentan los artículos que cambiaron durante el turno (los
 * que el empleado movió), productos e insumos en una misma lista (cada línea conserva su categoría para los
 * exportados). Lo que no se movió se da por bueno.
 *
 * Se ejecuta siempre dentro de la transacción del cierre o de la aprobación.
 */
internal suspend fun ShalevDatabase.instantaneaConteo(turnoId: Long): List<LineaConteo> {
    val movidos = movimientoDao().deTurno(turnoId).map { it.entidad to it.entidadId }.toSet()
    val productos = productoDao().todos()
        .filter { !it.eliminado && !Categorias.esElaborado(it.categoria) && (TipoEntidad.PRODUCTO.name to it.id) in movidos }
        .map {
            LineaConteo(TipoEntidad.PRODUCTO, it.id, it.nombre, "u", it.cantidad, it.cantidad, it.actualizadoEn, it.categoria)
        }
    val insumos = insumoDao().todos()
        .filter { (TipoEntidad.INSUMO.name to it.id) in movidos }
        .map {
            LineaConteo(
                TipoEntidad.INSUMO, it.id, it.nombre, it.unidad.let { u -> cu.shalev.domain.model.UnidadMedida.valueOf(u).simbolo },
                it.cantidadMil, it.cantidadMil, it.actualizadoEn, Categorias.INSUMOS,
            )
        }
    return productos + insumos
}

/**
 * 0.32.0: comprueba el conteo.
 * - En la SECUNDARIA ([compararFecha] = true): tiene que ser exactamente el que se le ofrecio (mismos artículos y
 *   mismos valores): si el inventario cambió mientras contaba, se avisa y se vuelve a cargar.
 * - En la PRINCIPAL ([compararFecha] = false): solo se comprueba que los artículos contados sigan existiendo. La
 *   diferencia se aplica sobre el valor actual de la principal (que puede haber cambiado tras el snapshot).
 */
internal suspend fun ShalevDatabase.comprobarConteo(c: ConteoCierre, turnoId: Long, compararFecha: Boolean) {
    if (!c.valido()) abortar(AppError.Validacion("conteo", AppError.Regla.RANGO))
    if (compararFecha) {
        val actuales = instantaneaConteo(turnoId).associateBy { it.entidad to it.id }
        if (actuales.keys != c.inventario.map { it.entidad to it.id }.toSet())
            abortar(AppError.Validacion("inventarioCambió", AppError.Regla.RANGO))
        for (l in c.inventario) {
            val actual = actuales[l.entidad to l.id]!!
            if (actual.esperado != l.esperado || actual.actualizadoEn != l.actualizadoEn)
                abortar(AppError.Validacion("inventarioCambió", AppError.Regla.RANGO))
        }
    } else {
        for (l in c.inventario) {
            val existe = when (l.entidad) {
                TipoEntidad.PRODUCTO -> productoDao().obtener(l.id)?.let { !it.eliminado && !Categorias.esElaborado(it.categoria) } == true
                TipoEntidad.INSUMO -> insumoDao().obtener(l.id) != null
            }
            if (!existe) abortar(AppError.Validacion("inventarioCambió", AppError.Regla.RANGO))
        }
    }
}

internal suspend fun ShalevDatabase.aplicarConteo(turnoId: Long, usuario: String, ahora: Long) {
    val turno = turnoDao().obtener(turnoId) ?: abortar(AppError.NoEncontrado)
    val c = turno.conteoCierreJson?.conteoDeJson() ?: return // compatibilidad con turnos anteriores
    if (c.aprobadoEn != null) return // aprobación idempotente: nunca repetir un ajuste
    comprobarConteo(c, turnoId, compararFecha = turno.empleadoId == null)
    for (l in c.inventario.filter { it.contado != it.esperado }) {
        val delta = l.diferencia
        val n = if (l.entidad == TipoEntidad.PRODUCTO) productoDao().sumarStock(l.id, delta, ahora)
            else insumoDao().sumarStock(l.id, delta, ahora)
        if (n != 1) abortar(AppError.NoEncontrado)
        // La existencia es la actual tras el ajuste (puede no ser la contada: la principal pudo moverla después).
        val existencia = if (l.entidad == TipoEntidad.PRODUCTO) productoDao().cantidad(l.id) ?: 0
            else insumoDao().cantidad(l.id) ?: 0
        movimientoDao().insertar(MovimientoEntity(
            fecha = ahora, tipo = "AJUSTE", entidad = l.entidad.name, entidadId = l.id, nombre = l.nombre,
            delta = delta, existencia = existencia, turnoId = turnoId, ventaId = null,
            nota = "Conciliación de cierre · Esperado ${cantidadConteo(l.esperado, l)} · Contado ${cantidadConteo(l.contado, l)} · ${c.motivo.trim()}", hechoPor = usuario,
        ))
    }
    syncDao().actualizarTurno(turno.copy(conteoCierreJson = c.copy(aprobadoPor = usuario, aprobadoEn = ahora).aJson(), sincronizado = false))
}

private fun cantidadConteo(n: Long, l: LineaConteo): String =
    (if (l.entidad == TipoEntidad.INSUMO) cu.shalev.core.quantity.Cantidad.format(cu.shalev.core.quantity.Cantidad(n)) else n.toString()) + " ${l.unidad}"
