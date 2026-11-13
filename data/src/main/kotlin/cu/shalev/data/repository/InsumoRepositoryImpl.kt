package cu.shalev.data.repository

import cu.shalev.domain.model.Identificacion
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.abortar
import cu.shalev.data.db.dao.localActivo
import cu.shalev.data.db.entity.MovimientoEntity
import cu.shalev.data.db.tx
import cu.shalev.data.mapper.toDomain
import cu.shalev.data.mapper.toEntity
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.service.Stock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class InsumoRepositoryImpl @Inject constructor(
    private val db: ShalevDatabase,
    private val clock: Clock,
) : InsumoRepository {

    private val insumos = db.insumoDao()
    private val recetas = db.recetaDao()
    private val productos = db.productoDao()
    private val movimientos = db.movimientoDao()
    private val turnos = db.turnoDao()
    private val servicios = db.servicioDao()
    private val locales = db.almacenLocalDao()

    override fun observarTodos(): Flow<List<Insumo>> = insumos.observarTodos().map { l -> l.map { it.toDomain() } }
    override suspend fun obtener(id: Long) = insumos.obtener(id)?.toDomain()
    override suspend fun obtenerVarios(ids: Collection<Long>) =
        if (ids.isEmpty()) emptyList() else ids.distinct().chunked(900).flatMap { insumos.obtenerVarios(it) }.map { it.toDomain() }
    override suspend fun todos() = insumos.todos().filter { it.local == locales.localOperacion() }.map { it.toDomain() }

    override suspend fun crear(insumo: Insumo): AppResult<Long> = db.tx {
        val id = insumos.insertar(insumo.toEntity().copy(id = 0, local = locales.localOperacion()))
        if (insumo.cantidad.milesimas != 0L) mov(TipoMovimiento.ALTA, id, insumo.nombre, insumo.cantidad.milesimas, insumo.cantidad.milesimas)
        id
    }

    override suspend fun actualizar(insumo: Insumo): AppResult<Unit> = actualizar(insumo, null)

    override suspend fun actualizar(insumo: Insumo, cantidadLeida: Cantidad?): AppResult<Unit> = db.tx {
        val viejo = insumos.obtener(insumo.id) ?: abortar(AppError.NoEncontrado)
        if (viejo.local != locales.localOperacion()) abortar(AppError.SinPermiso)
        val i = insumo.copy(cantidad = Cantidad(Stock.cantidadAlGuardar(cantidadLeida?.milesimas, insumo.cantidad.milesimas, viejo.cantidadMil)))
        insumos.actualizar(i.toEntity().copy(creadoEn = viejo.creadoEn, local = viejo.local))
        val delta = i.cantidad.milesimas - viejo.cantidadMil
        if (delta != 0L) mov(TipoMovimiento.AJUSTE, i.id, i.nombre, delta, i.cantidad.milesimas, "Edición")
    }

    override suspend fun eliminar(id: Long): AppResult<Unit> = db.tx {
        val i = insumos.obtener(id) ?: abortar(AppError.NoEncontrado)
        if (i.local != locales.localOperacion()) abortar(AppError.SinPermiso)
        val usos = recetas.productosQueUsan(id).takeIf { it.isNotEmpty() }
            ?.let { ids -> productos.obtenerVarios(ids).filterNot { it.eliminado }.map { Identificacion.nombreCompleto(it.nombre, it.descripcion) } }.orEmpty() +
            servicios.serviciosQueUsan(id).takeIf { it.isNotEmpty() }
                ?.let { ids -> servicios.obtenerVarios(ids).filterNot { it.eliminado }.map { Identificacion.nombreCompleto(it.nombre, it.descripcion) } }.orEmpty() // P29
        if (usos.isNotEmpty()) abortar(AppError.EnUso(usos.joinToString(", ")))
        insumos.borrar(id)
        if (i.cantidadMil != 0L) mov(TipoMovimiento.BAJA, id, i.nombre, -i.cantidadMil, 0)
    }

    override suspend fun ajustarStock(id: Long, delta: Cantidad, nota: String?): AppResult<Cantidad> = db.tx {
        val i = insumos.obtener(id) ?: abortar(AppError.NoEncontrado)
        if (i.local != locales.localOperacion()) abortar(AppError.SinPermiso)
        if (insumos.sumarStock(id, delta.milesimas, clock.now().toEpochMilli()) == 0) abortar(AppError.StockInsuficiente(listOf(i.nombre)))
        val nueva = insumos.cantidad(id) ?: 0
        mov(TipoMovimiento.AJUSTE, id, i.nombre, delta.milesimas, nueva, nota)
        Cantidad(nueva)
    }

    private suspend fun mov(tipo: TipoMovimiento, id: Long, nombre: String, deltaMil: Long, existenciaMil: Long, nota: String? = null) {
        movimientos.insertar(
            MovimientoEntity(
                fecha = clock.now().toEpochMilli(), tipo = tipo.name, entidad = TipoEntidad.INSUMO.name, entidadId = id,
                nombre = nombre, delta = deltaMil, existencia = existenciaMil, turnoId = turnos.turnoParaMovimiento(), ventaId = null, nota = nota,
                hechoPor = hechoPorActual(), local = locales.localOperacion(),
            ),
        )
    }
}
