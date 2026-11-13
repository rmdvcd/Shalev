package cu.shalev.domain.repository

import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.Servicio
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Receta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Productos (incluye Elaborados y sus recetas). Toda escritura que cambia existencias registra su
 * [cu.shalev.domain.model.MovimientoInventario] en la MISMA transacción (asociado al turno abierto, si hay).
 */
interface ProductoRepository {
    /** Activos, más recientes primero (orden cronológico de Shalev.txt). */
    fun observarTodos(): Flow<List<Producto>>
    suspend fun fotosEnUso(): Set<String> = observarTodos().first().mapNotNull { it.fotoUri }.toSet()
    fun observar(id: Long): Flow<Producto?>
    suspend fun obtener(id: Long): Producto?
    suspend fun obtenerVarios(ids: Collection<Long>): List<Producto>
    suspend fun categoriasEnUso(): List<String>

    suspend fun crear(producto: Producto, receta: Receta?): AppResult<Long>
    suspend fun actualizar(producto: Producto, receta: Receta?): AppResult<Unit>
    /**
     * 0.21.6: como [actualizar], pero con la existencia leída al abrir la ficha: si no cambió, se conserva la actual
     * ([cu.shalev.domain.service.Stock.cantidadAlGuardar]). Las implementaciones reales lo deciden dentro de la transacción.
     */
    suspend fun actualizar(producto: Producto, receta: Receta?, cantidadLeida: Long?): AppResult<Unit> =
        actualizar(producto, receta)
    /** Borrado lógico + movimiento BAJA por las existencias restantes. */
    suspend fun eliminar(id: Long): AppResult<Unit>
    suspend fun ajustarStock(id: Long, delta: Long, nota: String?): AppResult<Long>
    /** Cambio permanente de precios de venta (ajuste masivo). */
    suspend fun actualizarPrecios(nuevos: Map<Long, Cup>): AppResult<Unit>

    suspend fun receta(productoId: Long): Receta?
    /** Elaborados cuya receta usa el insumo (para recalcular costos o impedir su borrado). */
    suspend fun productosQueUsan(insumoId: Long): List<Producto>
}

interface InsumoRepository {
    fun observarTodos(): Flow<List<Insumo>>
    suspend fun obtener(id: Long): Insumo?
    suspend fun obtenerVarios(ids: Collection<Long>): List<Insumo>
    suspend fun todos(): List<Insumo>
    suspend fun crear(insumo: Insumo): AppResult<Long>
    suspend fun actualizar(insumo: Insumo): AppResult<Unit>
    /** 0.21.6: ver `ProductoRepository.actualizar(producto, receta, cantidadLeida)`. */
    suspend fun actualizar(insumo: Insumo, cantidadLeida: Cantidad?): AppResult<Unit> = actualizar(insumo)
    /** Falla con EnUso si alguna receta lo usa. */
    suspend fun eliminar(id: Long): AppResult<Unit>
    suspend fun ajustarStock(id: Long, delta: Cantidad, nota: String?): AppResult<Cantidad>
}

/**
 * P29: servicios que ofrece el negocio. Borrado lógico (las ventas antiguas conservan nombre e importe congelados).
 * Los insumos que consume cada servicio se guardan junto al servicio (misma transacción).
 */
interface ServicioRepository {
    /** Activos, más recientes primero. */
    fun observarTodos(): Flow<List<Servicio>>
    suspend fun fotosEnUso(): Set<String> = observarTodos().first().mapNotNull { it.fotoUri }.toSet()
    /** servicioId → insumos que gasta por vez (solo de los servicios que consumen alguno). */
    fun observarInsumos(): Flow<Map<Long, List<RecetaLinea>>>
    suspend fun obtener(id: Long): Servicio?
    suspend fun obtenerVarios(ids: Collection<Long>): List<Servicio>
    suspend fun insumos(servicioId: Long): List<RecetaLinea>
    suspend fun tiposEnUso(): List<String>
    suspend fun crear(servicio: Servicio, insumos: List<RecetaLinea>): AppResult<Long>
    suspend fun actualizar(servicio: Servicio, insumos: List<RecetaLinea>): AppResult<Unit>
    suspend fun eliminar(id: Long): AppResult<Unit>
    /** Servicios activos que gastan el insumo (para impedir su borrado y para su ficha). */
    suspend fun serviciosQueUsan(insumoId: Long): List<Servicio>
}

/**
 * Fotos de productos, guardadas en el almacenamiento privado de la app y re-codificadas (JPEG, lado máximo
 * acotado, sin metadatos EXIF). Devuelven una URI local `file://`.
 */
interface FotoRepository {
    /** Copia una imagen elegida por el usuario con el selector de fotos del sistema (content://). */
    suspend fun importar(origen: String): AppResult<String>
    suspend fun eliminar(uri: String)
    /** Borra fotos que ningún producto usa y tienen más de un día (borradores abandonados). */
    suspend fun limpiarHuerfanas(enUso: Set<String>)
}
