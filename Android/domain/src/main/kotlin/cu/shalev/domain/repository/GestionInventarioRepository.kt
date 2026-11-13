package cu.shalev.domain.repository

import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.ArticuloGestion
import cu.shalev.domain.model.LoteInventario
import cu.shalev.domain.model.MotivoMovimiento
import cu.shalev.domain.model.NuevaRecepcion
import cu.shalev.domain.model.RecepcionInventario
import cu.shalev.domain.model.TipoEntidad
import kotlinx.coroutines.flow.Flow

/** Operaciones de la principal, transaccionales y separadas por el local seleccionado. */
interface GestionInventarioRepository {
    fun articulos(): Flow<List<ArticuloGestion>>
    fun recepciones(): Flow<List<RecepcionInventario>>
    fun lotes(): Flow<List<LoteInventario>>
    suspend fun recibir(recepcion: NuevaRecepcion, autor: String): AppResult<String>
    suspend fun ajustar(entidad: TipoEntidad, id: Long, delta: Cantidad, motivo: MotivoMovimiento, detalle: String, autor: String): AppResult<Unit>
}
