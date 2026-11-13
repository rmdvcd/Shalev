package cu.shalev.app.insumos

import cu.shalev.app.ProdRepo
import cu.shalev.app.T0
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.UnidadMedida
import cu.shalev.domain.repository.InsumoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Insumo de prueba: [cantidad] en unidades enteras de [unidad]; ids mayores = más recientes. */
fun ins(id: Long, nombre: String = "I$id", cantidad: Long = 10, precio: Long = 10, unidad: UnidadMedida = UnidadMedida.KILOGRAMO) = Insumo(
    id = id, nombre = nombre, unidad = unidad, precio = Cup.ofPesos(precio), cantidad = Cantidad.enteras(cantidad),
    creadoEn = T0.plusSeconds(id * 60),
)

/**
 * Insumos en memoria con las reglas de :data que importan a la UI: orden cronológico (más recientes primero),
 * EnUso si alguna receta de [productos] lo usa y registro de movimientos (tipo, delta).
 */
class InsumosMem(private val productos: ProdRepo? = null) : InsumoRepository {
    val items = MutableStateFlow<List<Insumo>>(emptyList())
    val movimientos = mutableListOf<Pair<String, Cantidad>>()
    val guardados = mutableListOf<Insumo>()
    private var next = 100L

    fun put(vararg xs: Insumo) { items.value = (items.value.filterNot { i -> xs.any { it.id == i.id } } + xs) }

    override fun observarTodos(): Flow<List<Insumo>> = items.map { l -> l.sortedWith(compareByDescending<Insumo> { it.creadoEn }.thenByDescending { it.id }) }
    override suspend fun obtener(id: Long) = items.value.firstOrNull { it.id == id }
    override suspend fun obtenerVarios(ids: Collection<Long>) = items.value.filter { it.id in ids }
    override suspend fun todos() = items.value
    override suspend fun crear(insumo: Insumo): AppResult<Long> {
        val id = next++
        put(insumo.copy(id = id)); guardados += insumo.copy(id = id)
        if (insumo.cantidad != Cantidad.ZERO) movimientos += "ALTA" to insumo.cantidad
        return AppResult.Ok(id)
    }
    override suspend fun actualizar(insumo: Insumo): AppResult<Unit> {
        val viejo = obtener(insumo.id) ?: return AppResult.Err(AppError.NoEncontrado)
        put(insumo); guardados += insumo
        if (insumo.cantidad != viejo.cantidad) movimientos += "AJUSTE" to (insumo.cantidad - viejo.cantidad)
        return AppResult.Ok(Unit)
    }
    override suspend fun eliminar(id: Long): AppResult<Unit> {
        val i = obtener(id) ?: return AppResult.Err(AppError.NoEncontrado)
        val usos = productos?.productosQueUsan(id).orEmpty()
        if (usos.isNotEmpty()) return AppResult.Err(AppError.EnUso(usos.joinToString(", ") { it.nombre }))
        items.value = items.value.filterNot { it.id == id }
        if (i.cantidad != Cantidad.ZERO) movimientos += "BAJA" to Cantidad(-i.cantidad.milesimas)
        return AppResult.Ok(Unit)
    }
    override suspend fun ajustarStock(id: Long, delta: Cantidad, nota: String?): AppResult<Cantidad> {
        val i = obtener(id) ?: return AppResult.Err(AppError.NoEncontrado)
        val nueva = i.cantidad + delta
        if (nueva.isNegative) return AppResult.Err(AppError.StockInsuficiente(listOf(i.nombre)))
        put(i.copy(cantidad = nueva))
        movimientos += (if (nota == null && delta.isNegative) "CONSUMO" else "AJUSTE") to delta
        return AppResult.Ok(nueva)
    }
}
