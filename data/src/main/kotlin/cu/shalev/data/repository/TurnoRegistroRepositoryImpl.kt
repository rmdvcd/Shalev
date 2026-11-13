package cu.shalev.data.repository

import cu.shalev.data.dto.aJson
import cu.shalev.data.dto.conteoDeJson
import cu.shalev.domain.model.ConteoCierre
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.data.db.ShalevDatabase
import cu.shalev.data.db.abortar
import cu.shalev.data.db.dao.localActivo
import cu.shalev.data.db.entity.TurnoEntity
import cu.shalev.data.db.entity.ArqueoRow
import cu.shalev.data.db.entity.MovimientoCajaEntity
import cu.shalev.core.money.Cup
import cu.shalev.domain.model.Arqueo
import cu.shalev.domain.model.MovimientoCaja
import cu.shalev.domain.model.TipoMovimientoCaja
import cu.shalev.data.db.tx
import cu.shalev.data.mapper.toDomain
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Turno
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.FiltroRegistro
import cu.shalev.domain.repository.RegistroRepository
import cu.shalev.domain.repository.TurnoRepository
import cu.shalev.domain.service.CorreccionMovimiento
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@Singleton
class TurnoRepositoryImpl @Inject constructor(private val db: ShalevDatabase) : TurnoRepository {

    private val turnos = db.turnoDao()
    private val movimientos = db.movimientoDao()
    private val caja = db.cajaDao()

    override suspend fun prepararConteo() = db.tx {
        val t = turnos.activo() ?: abortar(AppError.TurnoCerrado)
        t.id to db.instantaneaConteo(t.id)
    }

    override suspend fun guardarConteo(turnoId: Long, conteo: ConteoCierre): AppResult<Unit> = db.tx {
        val t = turnos.activo()?.takeIf { it.id == turnoId } ?: abortar(AppError.TurnoCerrado)
        db.comprobarConteo(conteo, turnoId, compararFecha = true)
        // El dispositivo que declara no puede autorizar su propia conciliación remota.
        val limpio = conteo.copy(aprobadoPor = null, aprobadoEn = null)
        db.syncDao().actualizarTurno(t.copy(conteoCierreJson = limpio.aJson(), contadoCent = limpio.efectivo.centavos, sincronizado = false))
    }

    override fun observarActivo(): Flow<Turno?> = turnos.observarActivo().map { it?.toDomain() }
    override suspend fun activo() = turnos.activo()?.toDomain()
    override suspend fun ultimoCerrado() = turnos.ultimoCerrado()?.toDomain()
    override fun observarHistorial(): Flow<List<Turno>> = turnos.observarHistorial().map { l -> l.map { it.toDomain() } }
    override suspend fun obtener(id: Long) = turnos.obtener(id)?.toDomain()

    override suspend fun abrir(ahora: Instant, usuario: String, fondo: Cup?): AppResult<Turno> = db.tx {
        if (turnos.activo() != null) abortar(AppError.TurnoYaAbierto)
        if (fondo != null && fondo.isNegative) abortar(AppError.Validacion("fondo", AppError.Regla.RANGO))
        val e = TurnoEntity(abiertoEn = ahora.toEpochMilli(), cerradoEn = null, numVentas = null, unidades = null,
            totalCent = null, efectivoCent = null, transferenciaCent = null, costoCent = null, numMovimientos = null,
            abiertoPor = usuario, uuid = java.util.UUID.randomUUID().toString(), fondoCent = fondo?.centavos,
            local = db.almacenLocalDao().localActivo())
        e.copy(id = turnos.insertar(e)).toDomain()
    }

    /**
     * Todo en UNA transacción: se calcula el resumen desde las ventas y movimientos del turno y se congela.
     * La venta y el ajuste de stock usan la misma BD con transacciones, así que ninguno puede colarse entre
     * el cálculo y el cierre; después, `VentaRepositoryImpl` rechaza vender en un turno cerrado y los
     * movimientos nuevos ya no se asocian a este turno (solo al activo).
     */
    override suspend fun cerrar(ahora: Instant, usuario: String, contado: Cup?): AppResult<Turno> = cerrarInterno(ahora, usuario, contado, true)

    suspend fun cerrarSinAjustar(ahora: Instant, usuario: String, contado: Cup?): AppResult<Turno> = cerrarInterno(ahora, usuario, contado, false)

    private suspend fun cerrarInterno(ahora: Instant, usuario: String, contado: Cup?, aplicar: Boolean): AppResult<Turno> = db.tx {
        val t = turnos.activo() ?: abortar(AppError.TurnoCerrado)
        if (contado != null && contado.isNegative) abortar(AppError.Validacion("contado", AppError.Regla.RANGO))
        val declarado = t.conteoCierreJson?.conteoDeJson()
        if (!aplicar && declarado != null && declarado.aprobadoEn == null)
            abortar(AppError.Validacion("conteoSinAprobar"))
        if (declarado != null && contado != null && declarado.efectivo != contado)
            abortar(AppError.Validacion("conteo", AppError.Regla.RANGO))
        if (aplicar) db.aplicarConteo(t.id, usuario, ahora.toEpochMilli())
        val r = turnos.resumen(t.id)
        val m = movimientos.contarDeTurnoPorEntidad(t.id)
        val n = turnos.cerrar(
            id = t.id, cerradoEn = maxOf(ahora.toEpochMilli(), t.abiertoEn), numVentas = r.numVentas, unidades = r.unidades,
            totalCent = r.totalCent, efectivoCent = r.efectivoCent, transferenciaCent = r.transferenciaCent,
            costoCent = r.costoCent, numMovimientos = m.producto + m.insumo, cerradoPor = usuario,
            ventasEfectivo = r.ventasEfectivo, ventasTransferencia = r.ventasTransferencia,
            movimientosProducto = m.producto, movimientosInsumo = m.insumo,
            // 0.25.0: sin contado (turno sin fondo) se conserva el que hubiera.
            contadoCent = contado?.centavos ?: t.contadoCent,
            entradasCent = caja.suma(t.id, TipoMovimientoCaja.ENTRADA.name), salidasCent = caja.suma(t.id, TipoMovimientoCaja.SALIDA.name),
            numAnuladas = r.numAnuladas,
        )
        if (n == 0) abortar(AppError.TurnoCerrado)
        (turnos.obtener(t.id) ?: abortar(AppError.NoEncontrado)).toDomain()
    }

    override suspend fun movimientosDe(turnoId: Long): List<MovimientoInventario> = movimientos.deTurno(turnoId).map { it.toDomain() }

    /**
     * 0.31.0: corregir la cantidad de un ajuste del turno ABIERTO (Registros → Movimientos). Todo en una transacción:
     * el stock, el movimiento (cantidad nueva, motivo en la nota) y la existencia de los movimientos posteriores de
     * esa entidad. Un movimiento de otro turno, de un turno cerrado o de otra clase no se corrige.
     */
    override suspend fun corregirMovimiento(movimientoId: Long, delta: Long, motivo: String, ahora: Long): AppResult<Unit> = db.tx {
        val e = movimientos.obtener(movimientoId) ?: abortar(AppError.NoEncontrado)
        val m = e.toDomain()
        val t = turnos.activo() ?: abortar(AppError.TurnoCerrado)
        if (m.motivo == cu.shalev.domain.model.MotivoMovimiento.COMPRA || !CorreccionMovimiento.puede(m.tipo, m.turnoId, t.id)) {
            abortar(AppError.Validacion("movimiento", AppError.Regla.NO_PERMITIDO))
        }
        val diff = CorreccionMovimiento.diferencia(m.delta, delta)
        if (diff != 0L) {
            val filas = when (m.entidad) {
                TipoEntidad.PRODUCTO -> db.productoDao().sumarStock(m.entidadId, diff, ahora)
                TipoEntidad.INSUMO -> db.insumoDao().sumarStock(m.entidadId, diff, ahora)
            }
            if (filas == 0) abortar(AppError.StockInsuficiente(listOf(m.nombre)))
            if (diff < 0) db.consumirLotes(e.local, e.entidad, e.entidadId,
                if (m.entidad == TipoEntidad.PRODUCTO) Math.multiplyExact(Math.negateExact(diff), 1000L) else Math.negateExact(diff))
            movimientos.desplazarExistencia(e.entidad, e.entidadId, e.id, diff)
        }
        db.gestionDao().insertarRevision(cu.shalev.data.db.entity.MovimientoRevisionEntity(
            movimientoId = e.id, fecha = ahora, deltaAnterior = e.delta, deltaNuevo = delta,
            existenciaAnterior = e.existencia, existenciaNueva = Math.addExact(e.existencia, diff),
            hechoPor = t.abiertoPor.orEmpty().ifBlank { "Principal" }, motivo = motivo.trim(), local = e.local,
        ))
        movimientos.actualizar(e.copy(delta = delta, existencia = e.existencia + diff, nota = CorreccionMovimiento.nota(m.nota, motivo)))
        Unit
    }

    // ------------------------------------------------------------------ 0.25.0: caja
    override suspend fun registrarCaja(
        tipo: TipoMovimientoCaja, importe: Cup, motivo: String, hechoPor: String, ahora: Instant,
    ): AppResult<Long> = db.tx {
        if (importe.centavos <= 0) abortar(AppError.Validacion("importe", AppError.Regla.RANGO))
        val t = turnos.activo() ?: abortar(AppError.TurnoCerrado)
        caja.insertar(
            MovimientoCajaEntity(
                turnoId = t.id, fecha = maxOf(ahora.toEpochMilli(), t.abiertoEn), tipo = tipo.name, importeCent = importe.centavos,
                motivo = motivo.trim(), hechoPor = hechoPor, uuid = java.util.UUID.randomUUID().toString(),
            ),
        )
    }

    override suspend fun cajaDe(turnoId: Long): List<MovimientoCaja> = caja.deTurno(turnoId).map { it.toDomain() }

    override fun observarArqueoActivo(): Flow<Arqueo?> = turnos.observarArqueoActivo().map { it?.toArqueo() }

    override suspend fun declararContado(contado: Cup): AppResult<Unit> = db.tx {
        if (contado.isNegative) abortar(AppError.Validacion("contado", AppError.Regla.RANGO))
        val t = turnos.activo() ?: abortar(AppError.TurnoCerrado)
        if (turnos.declararContado(t.id, contado.centavos) == 0) abortar(AppError.TurnoCerrado)
    }
}

/** 0.25.0: null si el turno no tiene fondo (anterior a 0.25.0). */
internal fun ArqueoRow.toArqueo(): Arqueo? = fondoCent?.let { f ->
    Arqueo(Cup(f), Cup(efectivoCent), Cup(entradasCent), Cup(salidasCent), contadoCent?.let(::Cup))
}

@Singleton
class RegistroRepositoryImpl @Inject constructor(private val db: ShalevDatabase) : RegistroRepository {
    private val ventas = db.ventaDao()
    private val movimientos = db.movimientoDao()

    /** 0.20.0 (H1): ventaId → vendedor; cambia solo al abrir/cerrar turnos o registrar ventas. */
    private val vendedores: Flow<Map<Long, String>> = ventas.observarVendedores().map { l -> l.associate { it.ventaId to it.vendedor } }

    override fun ventas(filtro: FiltroRegistro): Flow<List<Venta>> = with(filtro) {
        combine(ventas.filtrar(desde?.toEpochMilli(), hasta?.toEpochMilli(), importeMin?.centavos, importeMax?.centavos, textoLike()), vendedores) { l, vs ->
            l.map { v -> v.toDomain().let { it.copy(vendedor = vs[it.id].orEmpty(), transaccion = it.transaccion?.copy(vendedor = vs[it.id].orEmpty())) } }
        }
    }

    override fun transacciones(filtro: FiltroRegistro): Flow<List<Transaccion>> = with(filtro) {
        combine(ventas.filtrarTransacciones(desde?.toEpochMilli(), hasta?.toEpochMilli(), importeMin?.centavos, importeMax?.centavos, textoLike()), vendedores) { l, vs ->
            l.map { t -> t.toDomain().let { it.copy(vendedor = vs[it.ventaId].orEmpty()) } }
        }
    }

    override fun vendedores(): Flow<List<String>> = ventas.nombresVendedores().map { l -> l.map { it.trim() }.distinctBy { it.lowercase() } }

    override fun movimientos(filtro: FiltroRegistro): Flow<List<MovimientoInventario>> = with(filtro) {
        movimientos.filtrar(desde?.toEpochMilli(), hasta?.toEpochMilli(), textoLike()).map { l ->
            val revisiones = db.gestionDao().revisiones().groupBy { it.movimientoId }
            l.map { m -> m.toDomain().copy(revisiones = revisiones[m.id].orEmpty().map { r ->
                cu.shalev.domain.model.RevisionMovimiento(java.time.Instant.ofEpochMilli(r.fecha), r.deltaAnterior, r.deltaNuevo, r.existenciaAnterior, r.existenciaNueva, r.hechoPor, r.motivo)
            }) }
        }
    }

    /**
     * Texto vacío = sin filtro. Búsqueda literal: los comodines de LIKE se escapan (consultas con `ESCAPE '\'`).
     * 0.21.6: antes se quitaban, y «LOTE_7» buscaba «LOTE7» y no encontraba nada.
     */
    private fun FiltroRegistro.textoLike(): String? = texto?.trim()?.takeIf { it.isNotEmpty() }?.let(::escaparLike)
}

/** Escapa `\`, `%` y `_` para un LIKE con `ESCAPE '\'` (búsqueda literal). */
internal fun escaparLike(texto: String): String = buildString {
    texto.forEach { c -> if (c == '\\' || c == '%' || c == '_') append('\\'); append(c) }
}
