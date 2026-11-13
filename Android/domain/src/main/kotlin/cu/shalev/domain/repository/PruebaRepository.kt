package cu.shalev.domain.repository

import cu.shalev.domain.model.InfoRegistroPrueba
import kotlinx.coroutines.flow.StateFlow

/** 0.26.0 (P74): registro de la prueba que sobrevive a desinstalar (copias en Imágenes, Download y Documents). */
interface PruebaRepository {
    /** null hasta la primera evaluación de la licencia. */
    val info: StateFlow<InfoRegistroPrueba?>
    /** Permiso de Android que deja leer las copias de instalaciones anteriores (fotos en 13+, almacenamiento antes). */
    val permisoLectura: String
    fun lecturaPermitida(): Boolean
    suspend fun permisoPedido(): Boolean
    suspend fun marcarPermisoPedido()
}
