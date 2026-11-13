package cu.shalev.data.repository

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.abortar
import cu.shalev.data.db.dao.idActivo
import cu.shalev.data.db.dao.localActivo
import cu.shalev.data.db.entity.LoteInventarioEntity
import cu.shalev.data.db.entity.MovimientoEntity
import cu.shalev.data.db.entity.RecepcionEntity
import cu.shalev.data.db.tx
import cu.shalev.data.mapper.toDomain
import cu.shalev.data.sync.ConfigSync
import cu.shalev.domain.model.*
import cu.shalev.domain.repository.GestionInventarioRepository
import cu.shalev.domain.usecase.ajusteValido
import cu.shalev.domain.usecase.recepcionValida
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class GestionInventarioRepositoryImpl @Inject constructor(
    private val db: ShalevDatabase,
    private val productos: ProductoRepositoryImpl,
    private val insumos: InsumoRepositoryImpl,
    private val config: ConfigSync,
    private val clock: Clock,
) : GestionInventarioRepository {
    override fun articulos() = combine(productos.observarTodos(), insumos.observarTodos()) { ps, xs ->
        ps.filterNot { it.esElaborado }.map { ArticuloGestion(TipoEntidad.PRODUCTO, it.id, it.nombreCompleto, UnidadMedida.UNIDAD, Cantidad(Math.multiplyExact(it.cantidad, 1000L)), it.precioCosto) } +
            xs.map { ArticuloGestion(TipoEntidad.INSUMO, it.id, it.nombre, it.unidad, it.cantidad, it.precio) }
    }
    override fun recepciones() = db.gestionDao().observarRecepciones().map { xs -> xs.map {
        RecepcionInventario(it.uuid, Instant.ofEpochMilli(it.fecha), it.proveedor, it.comprobante, it.motivo, it.hechoPor, Cup(it.totalCent))
    } }
    override fun lotes() = db.gestionDao().observarLotes().map { xs -> xs.map {
        LoteInventario(it.id, it.recepcionId, TipoEntidad.valueOf(it.entidad), it.entidadId, it.nombre, it.unidad, it.codigo,
            Cantidad(it.cantidadInicialMil), Cantidad(it.disponibleMil), Cup(it.costoUnitarioCent), it.caducidad?.let(LocalDate::ofEpochDay))
    } }

    override suspend fun recibir(recepcion: NuevaRecepcion, autor: String): AppResult<String> {
        if (config.tipo.first() != TipoApp.PRINCIPAL) return AppResult.Err(AppError.SinPermiso)
        return db.tx {
            if (!recepcionValida(recepcion)) abortar(AppError.Validacion("recepcion"))
            val local = db.almacenLocalDao().localActivo()
            val localId = db.almacenLocalDao().idActivo()
            db.gestionDao().recepcion(recepcion.uuid)?.let { previa ->
                if (previa.localId != localId) abortar(AppError.SinPermiso)
                val guardadas = db.gestionDao().lotesDeRecepcion(recepcion.uuid).map {
                    LineaRecepcion(TipoEntidad.valueOf(it.entidad), it.entidadId, Cantidad(it.cantidadInicialMil), Cup(it.costoUnitarioCent), it.codigo, it.caducidad?.let(LocalDate::ofEpochDay))
                }.groupingBy { it }.eachCount()
                val solicitadas = recepcion.lineas.map { it.copy(lote = it.lote.trim()) }.groupingBy { it }.eachCount()
                if (previa.proveedor != recepcion.proveedor.trim() || previa.comprobante != recepcion.comprobante.trim() || previa.motivo != recepcion.motivo.trim() || guardadas != solicitadas)
                    abortar(AppError.Duplicado("recepcion"))
                return@tx previa.uuid // reintento confirmado: nunca volver a sumar la entrada
            }
            var total = Cup.ZERO
            val lotes = mutableListOf<LoteInventarioEntity>()
            for (l in recepcion.lineas) {
                val mil = l.cantidad.milesimas
                val nombre: String
                val unidad: String
                val existencia: Long
                when (l.entidad) {
                    TipoEntidad.PRODUCTO -> {
                        val e = db.productoDao().obtener(l.id)?.takeUnless { it.eliminado } ?: abortar(AppError.NoEncontrado)
                        if (e.local != local || Categorias.esElaborado(e.categoria)) abortar(AppError.SinPermiso)
                        val cantidad = mil / 1000
                        val costo = costoPromedio(Cup(e.precioCostoCent), e.cantidad.coerceAtLeast(0), l.costoUnitario, cantidad)
                        val p = e.toDomain().copy(cantidad = Math.addExact(e.cantidad, cantidad), precioCosto = costo, actualizadoEn = clock.now())
                        productos.actualizar(p, null).exigirExito()
                        nombre = p.nombreCompleto; unidad = "u"; existencia = p.cantidad
                    }
                    TipoEntidad.INSUMO -> {
                        val e = db.insumoDao().obtener(l.id) ?: abortar(AppError.NoEncontrado)
                        if (e.local != local) abortar(AppError.SinPermiso)
                        val costo = costoPromedio(Cup(e.precioCent), e.cantidadMil.coerceAtLeast(0), l.costoUnitario, mil)
                        val i = e.toDomain().copy(cantidad = Cantidad(Math.addExact(e.cantidadMil, mil)), precio = costo, actualizadoEn = clock.now())
                        insumos.actualizar(i).exigirExito()
                        nombre = i.nombre; unidad = i.unidad.simbolo; existencia = i.cantidad.milesimas
                    }
                }
                // Las actualizaciones anteriores dejan su movimiento Edición; se añade la recepción estructurada sin
                // otro delta de stock. El movimiento original se etiqueta como compra dentro de esta misma transacción.
                db.movimientoDao().ultimoDe(l.entidad.name, l.id)?.let { m ->
                    db.movimientoDao().actualizar(m.copy(nota = "Compra · ${recepcion.proveedor.trim()} · ${recepcion.comprobante.trim()} · ${recepcion.motivo.trim()}", hechoPor = autor, motivo = MotivoMovimiento.COMPRA.name))
                }
                total += l.costoUnitario.porMilesimas(mil)
                // Parte de la entrada puede cubrir una existencia negativa conciliada de ventas offline.
                val existenciaMil = if (l.entidad == TipoEntidad.PRODUCTO) Math.multiplyExact(existencia.coerceAtLeast(0), 1000L) else existencia.coerceAtLeast(0)
                lotes += LoteInventarioEntity(UUID.randomUUID().toString(), recepcion.uuid, localId, l.entidad.name, l.id, nombre,
                    unidad, l.lote.trim(), mil, minOf(mil, existenciaMil), l.costoUnitario.centavos, l.caducidad?.toEpochDay())
            }
            db.gestionDao().insertarRecepcion(RecepcionEntity(recepcion.uuid, localId, clock.now().toEpochMilli(), recepcion.proveedor.trim(),
                recepcion.comprobante.trim(), recepcion.motivo.trim(), autor, total.centavos))
            db.gestionDao().insertarLotes(lotes)
            recepcion.uuid
        }
    }

    override suspend fun ajustar(entidad: TipoEntidad, id: Long, delta: Cantidad, motivo: MotivoMovimiento, detalle: String, autor: String): AppResult<Unit> {
        if (config.tipo.first() != TipoApp.PRINCIPAL) return AppResult.Err(AppError.SinPermiso)
        return db.tx {
            if (!ajusteValido(id, delta, motivo, detalle)) abortar(AppError.Validacion("ajuste"))
            if (entidad == TipoEntidad.PRODUCTO && delta.milesimas % 1000 != 0L) abortar(AppError.Validacion("cantidad"))
            val nota = "${motivo.titulo} · ${detalle.trim()}"
            when (entidad) {
                TipoEntidad.PRODUCTO -> productos.ajustarStock(id, delta.milesimas / 1000, nota).exigirExito()
                TipoEntidad.INSUMO -> insumos.ajustarStock(id, delta, nota).exigirExito()
            }
            db.movimientoDao().ultimoDe(entidad.name, id)?.let { db.movimientoDao().actualizar(it.copy(motivo = motivo.name, hechoPor = autor)) }
            Unit
        }
    }
}

/** Misma unidad de cantidad a ambos lados; no usa coma flotante ni promedia porcentajes. */
internal fun costoPromedio(anterior: Cup, cantidadAnterior: Long, recibido: Cup, cantidadRecibida: Long): Cup {
    val cantidad = Math.addExact(cantidadAnterior, cantidadRecibida)
    require(cantidad > 0 && cantidadAnterior >= 0 && cantidadRecibida > 0)
    return Cup.of(anterior.toBigDecimal().multiply(BigDecimal.valueOf(cantidadAnterior))
        .add(recibido.toBigDecimal().multiply(BigDecimal.valueOf(cantidadRecibida)))
        .divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP))
}
private fun <T> AppResult<T>.exigirExito(): T = when (this) { is AppResult.Ok -> value; is AppResult.Err -> abortar(error) }
