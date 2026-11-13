package cu.shalev.data.repository

import cu.shalev.data.db.dao.TurnoDao
import cu.shalev.data.db.dao.localActivo
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

/**
 * 0.19.3 · La principal es un punto de venta completo, con su propio turno. Cuando ejecuta un cambio de stock que pidió
 * una SECUNDARIA (EjecutorComandos), el movimiento no debe caer en el turno de la principal: va al turno abierto de esa
 * secundaria, o a ninguno si no tiene. Así el cierre de turno de la principal solo cuenta lo suyo.
 *
 * Se pasa como elemento del contexto de corrutinas para no cambiar las interfaces de dominio. Room conserva los
 * elementos del contexto dentro de `withTransaction`.
 */
internal class OrigenMovimiento(val empleadoId: Long, val nombre: String = "", val local: String? = null) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<OrigenMovimiento>
}

/** Turno al que se asocia un movimiento de inventario hecho ahora (debe llamarse dentro de la transacción). */
internal suspend fun TurnoDao.turnoParaMovimiento(): Long? {
    val origen = currentCoroutineContext()[OrigenMovimiento]
    return if (origen == null) activo()?.id else activoDeEmpleado(origen.empleadoId)?.id
}

/** 0.21.0 (C7): quién hace el movimiento: el empleado de la app secundaria que lo pidió; vacío = el dueño (o el turno). */
internal suspend fun hechoPorActual(): String = currentCoroutineContext()[OrigenMovimiento]?.nombre.orEmpty()

/** Contexto autenticado por la principal; jamás se cambia el selector visible para atender a un empleado. */
internal suspend fun cu.shalev.data.db.dao.AlmacenLocalDao.localOperacion(): String =
    kotlinx.coroutines.currentCoroutineContext()[OrigenMovimiento]?.local ?: localActivo()
