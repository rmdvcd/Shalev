package cu.shalev.data.repository

import cu.shalev.domain.model.ClaseArticulo
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.abortar
import cu.shalev.data.db.dao.localActivo
import cu.shalev.data.db.entity.MovimientoEntity
import cu.shalev.data.db.tx
import cu.shalev.data.db.dao.registrar
import cu.shalev.data.mapper.toDomain
import cu.shalev.data.mapper.toEntity
import cu.shalev.domain.model.ElaboradoEnVenta
import cu.shalev.domain.model.Anulacion
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.VentaRepository
import java.time.Instant
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VentaRepositoryImpl @Inject constructor(private val db: ShalevDatabase) : VentaRepository {

    override fun observarCambios() = db.ventaDao().observarCambios().map { Unit }

    private val ventas = db.ventaDao()
    private val servicios = db.servicioDao()
    private val productos = db.productoDao()
    private val turnos = db.turnoDao()
    private val movimientos = db.movimientoDao()
    private val insumos = db.insumoDao()
    private val clientesFijos = db.clienteFijoDao()

    /**
     * Todo en una transacción: si cualquier línea no tiene existencias, rollback completo (ni venta, ni
     * descuentos parciales). El descuento es un UPDATE condicional (cantidad + delta >= 0), así dos ventas
     * concurrentes nunca dejan stock negativo.
     *
     * P26: los Elaborados de [elaborados] no tienen existencias ni pasan por producción: se descuentan sus insumos
     * (CONSUMO por insumo con UPDATE condicional, ligado a la venta y al turno) y su línea no toca el stock del
     * Elaborado. Si un insumo no alcanza, StockInsuficiente(insumo) y rollback de TODO.
     */
    override suspend fun registrar(venta: Venta, elaborados: List<ElaboradoEnVenta>): AppResult<Long> = db.tx {
        registrarEnTx(venta, elaborados)
    }

    override suspend fun registrarVerificada(venta: Venta, elaborados: List<ElaboradoEnVenta>, verificar: suspend () -> AppResult<Unit>): AppResult<Long> = db.tx {
        when (val r = verificar()) { is AppResult.Err -> abortar(r.error); is AppResult.Ok -> Unit }
        registrarEnTx(venta, elaborados)
    }

    /** Cuerpo de [registrar]; debe llamarse dentro de una transacción. */
    private suspend fun registrarEnTx(venta: Venta, elaborados: List<ElaboradoEnVenta>): Long {
        if (venta.detalles.isEmpty()) abortar(AppError.Validacion("detalles", AppError.Regla.REQUERIDO))
        val turno = turnos.obtener(venta.turnoId)
        if (turno == null || turno.cerradoEn != null) abortar(AppError.TurnoCerrado)

        venta.transaccion?.let {
            if (ventas.referenciaUsada(it.numero)) abortar(AppError.Duplicado("numeroTransaccion"))
        }
        // P37: id global para que la principal no la duplique al sincronizar.
        // 0.25.0: una venta corregida en la principal dentro del turno de una secundaria es de ese empleado y se le envía.
        val deSecundaria = turno.empleadoId != null
        val local = turno.local.ifEmpty { db.almacenLocalDao().localActivo() }
        for (d in venta.detalles) {
            val propietario = when (d.clase) {
                ClaseArticulo.PRODUCTO -> productos.obtener(d.productoId)?.local
                ClaseArticulo.INSUMO -> insumos.obtener(d.productoId)?.local
                ClaseArticulo.SERVICIO -> servicios.obtener(d.productoId)?.local
            }
            if (propietario != local) abortar(AppError.SinPermiso)
        }
        for (e in elaborados) for (id in e.consumo.keys) {
            if (insumos.obtener(id)?.local != local) abortar(AppError.SinPermiso)
        }

        val ventaId = ventas.insertarVenta(
            venta.toEntity().copy(
                id = 0, uuid = java.util.UUID.randomUUID().toString(), anuladaEn = null, motivoAnulacion = null, anuladaPor = null,
                empleadoId = turno.empleadoId, sincronizado = deSecundaria, bajarCambio = deSecundaria, local = local,
            ),
        )
        ventas.insertarDetalles(venta.detalles.map { it.toEntity(ventaId).copy(id = 0) })
        venta.transaccion?.let { ventas.insertarTransaccion(it.toEntity(ventaId).copy(id = 0, local = local)) }
        // 0.27.0 (N2): «Cliente fijo» → alta o actualización por carné, en la misma transacción.
        venta.transaccion?.takeIf { it.clienteFijo }?.let { t ->
            clientesFijos.registrar(t.cliente.nombreApellidos, t.cliente.ci, t.cliente.telefono, venta.fecha.toEpochMilli())
        }

        val fecha = venta.fecha.toEpochMilli()
        elaborados.forEach { e -> consumirInsumos(e, fecha, venta.turnoId, ventaId, local) }
        val idsElaborados = elaborados.filter { it.clase == ClaseArticulo.PRODUCTO }.map { it.productoId }.toSet()
        val faltantes = mutableListOf<String>()
        // P29: insumos vendidos sueltos (unidades enteras de su medida); los servicios no tienen existencias.
        venta.detalles.filter { it.clase == ClaseArticulo.INSUMO }.groupBy { it.productoId }.forEach { (insumoId, lineas) ->
            val mil = lineas.sumOf { it.cantidad } * MIL
            val nombre = lineas.first().nombre
            insumos.obtener(insumoId) ?: abortar(AppError.NoEncontrado)
            if (insumos.sumarStock(insumoId, -mil, fecha) == 0) {
                faltantes += nombre
            } else {
                movimientos.insertar(
                    MovimientoEntity(
                        fecha = fecha, tipo = TipoMovimiento.VENTA.name, entidad = TipoEntidad.INSUMO.name,
                        entidadId = insumoId, nombre = nombre, delta = -mil,
                        existencia = insumos.cantidad(insumoId) ?: 0, turnoId = venta.turnoId, ventaId = ventaId, nota = null,
                        local = local,
                    ),
                )
            }
        }
        // Agrupa por producto: dos líneas del mismo artículo se verifican contra el total.
        venta.detalles.filter { it.clase == ClaseArticulo.PRODUCTO && it.productoId !in idsElaborados }.groupBy { it.productoId }.forEach { (productoId, lineas) ->
            val unidades = lineas.sumOf { it.cantidad }
            val nombre = lineas.first().nombre
            // 0.21.9 (P59): un producto borrado mientras estaba en el carrito es «ya no existe», no «sin existencias»
            // (igual que los Elaborados y servicios en consumirInsumos).
            productos.obtener(productoId)?.takeUnless { it.eliminado } ?: abortar(AppError.NoEncontrado)
            if (productos.sumarStock(productoId, -unidades, fecha) == 0) {
                faltantes += nombre
            } else {
                movimientos.insertar(
                    MovimientoEntity(
                        fecha = fecha, tipo = TipoMovimiento.VENTA.name, entidad = TipoEntidad.PRODUCTO.name,
                        entidadId = productoId, nombre = nombre, delta = -unidades,
                        existencia = productos.cantidad(productoId) ?: 0, turnoId = venta.turnoId, ventaId = ventaId, nota = null,
                        local = local,
                    ),
                )
            }
        }
        if (faltantes.isNotEmpty()) abortar(AppError.StockInsuficiente(faltantes))
        for (m in db.syncDao().movimientosDeVentas(listOf(ventaId))) {
            if (m.delta < 0) db.consumirLotes(local, m.entidad, m.entidadId,
                if (m.entidad == TipoEntidad.PRODUCTO.name) Math.multiplyExact(Math.negateExact(m.delta), MIL) else Math.negateExact(m.delta), ventaId)
        }
        return ventaId
    }

    // ------------------------------------------------------------------ 0.25.0: anular / modificar
    override suspend fun anular(ventaId: Long, anulacion: Anulacion): AppResult<Unit> = db.tx {
        anularEnTx(ventaId, anulacion)
    }

    override suspend fun modificar(
        ventaId: Long, anulacion: Anulacion, nueva: Venta, elaborados: List<ElaboradoEnVenta>,
    ): AppResult<Long> = db.tx {
        val original = anularEnTx(ventaId, anulacion)
        // Mismo turno que la original (las existencias ya volvieron: se comprueban como en una venta normal).
        val nuevaId = registrarEnTx(nueva.copy(turnoId = original.turnoId, corrigeVentaId = ventaId, anulacion = null), elaborados)
        ventas.cambiarMotivo(ventaId, Anulacion.motivoModificada(nuevaId, anulacion.motivo))
        nuevaId
    }

    /**
     * Marca la venta y devuelve sus salidas de existencias: un movimiento ANULACION (el contrario) por cada salida.
     * Las existencias se suman sin condición (devolver nunca deja negativo); un artículo ya borrado no se toca.
     */
    private suspend fun anularEnTx(ventaId: Long, a: Anulacion): cu.shalev.data.db.entity.VentaEntity {
        val v = ventas.obtener(ventaId)?.venta ?: abortar(AppError.NoEncontrado)
        if (v.anuladaEn != null) abortar(AppError.Validacion("venta", AppError.Regla.NO_PERMITIDO))
        val turno = turnos.obtener(v.turnoId)
        if (turno == null || turno.cerradoEn != null) abortar(AppError.TurnoCerrado)
        val fecha = maxOf(a.en.toEpochMilli(), v.fecha)
        if (ventas.anular(ventaId, fecha, a.motivo.trim(), a.por, bajar = turno.empleadoId != null) == 0) {
            abortar(AppError.Validacion("venta", AppError.Regla.NO_PERMITIDO))
        }
        val sync = db.syncDao()
        for (m in sync.movimientosDeVentas(listOf(ventaId)).filter { it.delta < 0 && it.tipo != TipoMovimiento.ANULACION.name }) {
            val devuelto = -m.delta
            val existencia = if (m.entidad == TipoEntidad.PRODUCTO.name) {
                if (sync.forzarStockProducto(m.entidadId, devuelto, fecha) == 0) continue
                productos.cantidad(m.entidadId) ?: 0
            } else {
                if (sync.forzarStockInsumo(m.entidadId, devuelto, fecha) == 0) continue
                insumos.cantidad(m.entidadId) ?: 0
            }
            movimientos.insertar(
                MovimientoEntity(
                    fecha = fecha, tipo = TipoMovimiento.ANULACION.name, entidad = m.entidad, entidadId = m.entidadId,
                    nombre = m.nombre, delta = devuelto, existencia = existencia, turnoId = v.turnoId, ventaId = ventaId,
                    nota = "Venta anulada · ${a.motivo.trim()}".take(NOTA_MAX), hechoPor = a.por, local = v.local,
                ),
            )
        }
        db.devolverLotes(ventaId)
        ventas.recalcularReferenciasDuplicadas()
        return v
    }

    override suspend fun movimientosDe(ventaId: Long): List<MovimientoInventario> =
        db.syncDao().movimientosDeVentas(listOf(ventaId)).map { it.toDomain() }

    /** P26: el Elaborado no tiene existencias; se descuentan sus insumos. Debe llamarse dentro de la transacción de [registrar]. */
    private suspend fun consumirInsumos(e: ElaboradoEnVenta, fecha: Long, turnoId: Long, ventaId: Long, local: String) {
        // P29: también los servicios que gastan insumos.
        val existe = if (e.clase == ClaseArticulo.SERVICIO) servicios.obtener(e.productoId)?.takeUnless { it.eliminado } != null
        else productos.obtener(e.productoId)?.takeUnless { it.eliminado } != null
        if (!existe) abortar(AppError.NoEncontrado)
        val faltantes = mutableListOf<String>()
        e.consumo.forEach { (insumoId, c) ->
            val i = insumos.obtener(insumoId) ?: abortar(AppError.NoEncontrado)
            if (insumos.sumarStock(insumoId, -c.milesimas, fecha) == 0) {
                faltantes += i.nombre
            } else {
                movimientos.insertar(
                    MovimientoEntity(
                        fecha = fecha, tipo = TipoMovimiento.CONSUMO.name, entidad = TipoEntidad.INSUMO.name,
                        entidadId = insumoId, nombre = i.nombre, delta = -c.milesimas,
                        existencia = insumos.cantidad(insumoId) ?: 0, turnoId = turnoId, ventaId = ventaId,
                        nota = "Venta de ${e.nombre} ×${e.unidades}", local = local,
                    ),
                )
            }
        }
        if (faltantes.isNotEmpty()) abortar(AppError.StockInsuficiente(faltantes))
    }

    override suspend fun obtener(id: Long): Venta? = ventas.obtener(id)?.toDomain()?.let { v ->
        listOf(v).conVendedoresDb().first()
    }

    override suspend fun entre(desde: Instant, hasta: Instant): List<Venta> =
        ventas.entre(desde.toEpochMilli(), hasta.toEpochMilli()).map { it.toDomain() }.conVendedoresDb()

    override suspend fun deTurno(turnoId: Long): List<Venta> = ventas.deTurno(turnoId).map { it.toDomain() }.conVendedoresDb()

    /** Lee el mapa venta→vendedor en una sola consulta (lista vacía = sin consulta). */
    private suspend fun List<Venta>.conVendedoresDb(): List<Venta> =
        if (isEmpty()) this else conVendedores(ventas.vendedoresDe(map { it.id }).associate { it.ventaId to it.vendedor })
}

/**
 * 0.20.0 (H1) / 0.29.2: el vendedor no se guarda en la venta, llega del turno que la contiene
 * (`turno.abiertoPor`, nombre congelado al abrir). Se rellena en lectura, igual que la pantalla
 * Registros; sin turno asociado queda vacío.
 */
internal fun List<Venta>.conVendedores(mapa: Map<Long, String>): List<Venta> =
    map { v -> val q = mapa[v.id].orEmpty(); v.copy(vendedor = q, transaccion = v.transaccion?.copy(vendedor = q)) }

private const val MIL = 1000L
private const val NOTA_MAX = 120
