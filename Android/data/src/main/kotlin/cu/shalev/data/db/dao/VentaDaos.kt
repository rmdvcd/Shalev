package cu.shalev.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import cu.shalev.data.db.entity.ArqueoRow
import cu.shalev.data.db.entity.DetalleVentaEntity
import cu.shalev.data.db.entity.MovimientoCajaEntity
import cu.shalev.data.db.entity.ResumenTurnoRow
import cu.shalev.data.db.entity.TransaccionEntity
import cu.shalev.data.db.entity.TurnoEntity
import cu.shalev.data.db.entity.VentaCompleta
import cu.shalev.data.db.entity.VentaEntity
import kotlinx.coroutines.flow.Flow

/** 0.20.0 (H1). */
data class VendedorDeVenta(val ventaId: Long, val vendedor: String)

data class CambiosVentaRow(val numero: Long, val ultimoId: Long, val total: Long, val costo: Long, val anuladas: Long)

@Dao
interface VentaDao {
    @Query("SELECT COUNT(*) AS numero, COALESCE(MAX(id), 0) AS ultimoId, COALESCE(SUM(totalCent), 0) AS total, COALESCE(SUM(costoCent), 0) AS costo, COUNT(anuladaEn) AS anuladas FROM venta WHERE local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), local)")
    fun observarCambios(): Flow<CambiosVentaRow>

    @Query("SELECT EXISTS(SELECT 1 FROM transaccion t JOIN venta v ON v.id = t.ventaId WHERE v.anuladaEn IS NULL AND UPPER(TRIM(t.numero)) = UPPER(TRIM(:numero)))")
    suspend fun referenciaUsada(numero: String): Boolean

    /** Se ejecuta tras registrar/anular/importar: el aviso desaparece cuando se resuelve la duplicidad. */
    @Query("UPDATE transaccion SET referenciaDuplicada = EXISTS(SELECT 1 FROM transaccion t JOIN venta v ON v.id = t.ventaId JOIN venta propia ON propia.id = transaccion.ventaId WHERE t.ventaId != transaccion.ventaId AND v.anuladaEn IS NULL AND propia.anuladaEn IS NULL AND UPPER(TRIM(t.numero)) = UPPER(TRIM(transaccion.numero)))")
    suspend fun recalcularReferenciasDuplicadas()

    @Insert suspend fun insertarVenta(v: VentaEntity): Long
    @Insert suspend fun insertarDetalles(ds: List<DetalleVentaEntity>)
    @Insert suspend fun insertarTransaccion(t: TransaccionEntity): Long

    @Transaction
    @Query("SELECT * FROM venta WHERE id = :id")
    suspend fun obtener(id: Long): VentaCompleta?

    @Transaction
    @Query("SELECT * FROM venta WHERE fecha >= :desde AND fecha < :hasta AND local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), local) ORDER BY fecha, id")
    suspend fun entre(desde: Long, hasta: Long): List<VentaCompleta>

    @Transaction
    @Query("SELECT * FROM venta WHERE turnoId = :turnoId ORDER BY fecha, id")
    suspend fun deTurno(turnoId: Long): List<VentaCompleta>

    /** Registros → Ventas. [texto] busca en los nombres de los artículos vendidos. */
    @Transaction
    @Query(
        """
        SELECT * FROM venta
        WHERE (:desde IS NULL OR fecha >= :desde)
          AND (:hasta IS NULL OR fecha < :hasta)
          AND local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), local)
          AND (:minCent IS NULL OR totalCent >= :minCent)
          AND (:maxCent IS NULL OR totalCent <= :maxCent)
          AND (:texto IS NULL OR id IN (SELECT ventaId FROM detalle_venta WHERE nombre LIKE '%' || :texto || '%' ESCAPE '\'))
        ORDER BY fecha DESC, id DESC
        """,
    )
    fun filtrar(desde: Long?, hasta: Long?, minCent: Long?, maxCent: Long?, texto: String?): Flow<List<VentaCompleta>>

    /** 0.20.0 (H1): quién vendió cada venta = quien abrió su turno (nombre congelado al abrir). */
    @Query("SELECT v.id AS ventaId, t.abiertoPor AS vendedor FROM venta v JOIN turno t ON t.id = v.turnoId WHERE t.abiertoPor IS NOT NULL AND v.local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), v.local)")
    fun observarVendedores(): Flow<List<VendedorDeVenta>>

    /** 0.29.2: quién vendió cada venta = quien abrió su turno (nombre congelado al abrir); por lote para `obtener`/`entre`/`deTurno`. */
    @Query("SELECT v.id AS ventaId, t.abiertoPor AS vendedor FROM venta v JOIN turno t ON t.id = v.turnoId WHERE v.id IN (:ids) AND t.abiertoPor IS NOT NULL")
    suspend fun vendedoresDe(ids: List<Long>): List<VendedorDeVenta>

    /** 0.20.0 (H1): opciones del filtro «Vendedor». */
    @Query("SELECT DISTINCT abiertoPor FROM turno WHERE abiertoPor IS NOT NULL AND TRIM(abiertoPor) != '' AND local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), local) ORDER BY abiertoPor COLLATE NOCASE")
    fun nombresVendedores(): Flow<List<String>>

    /** Registros → Transferencias recibidas. */
    @Query(
        """
        SELECT * FROM transaccion
        WHERE (:desde IS NULL OR fecha >= :desde)
          AND (:hasta IS NULL OR fecha < :hasta)
          AND local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), local)
          AND (:minCent IS NULL OR importeCent >= :minCent)
          AND (:maxCent IS NULL OR importeCent <= :maxCent)
          AND (:texto IS NULL OR numero LIKE '%' || :texto || '%' ESCAPE '\' OR clienteNombre LIKE '%' || :texto || '%' ESCAPE '\'
               OR clienteCi LIKE '%' || :texto || '%' ESCAPE '\' OR clienteTelefono LIKE '%' || :texto || '%' ESCAPE '\')
        ORDER BY fecha DESC, id DESC
        """,
    )
    fun filtrarTransacciones(desde: Long?, hasta: Long?, minCent: Long?, maxCent: Long?, texto: String?): Flow<List<TransaccionEntity>>

    // ------------------------------------------------------------------ 0.25.0: anular / modificar
    /** Marca la venta como anulada (solo si aún no lo estaba). [bajar] = es de una secundaria: hay que avisarle. */
    @Query(
        """
        UPDATE venta SET anuladaEn = :en, motivoAnulacion = :motivo, anuladaPor = :por, bajarCambio = :bajar
        WHERE id = :id AND anuladaEn IS NULL
        """,
    )
    suspend fun anular(id: Long, en: Long, motivo: String, por: String, bajar: Boolean): Int

    @Query("UPDATE venta SET motivoAnulacion = :motivo WHERE id = :id")
    suspend fun cambiarMotivo(id: Long, motivo: String)

    /** Principal: cambios hechos aquí en ventas de la secundaria [empleadoId] que aún no confirmó. */
    @Transaction
    @Query("SELECT * FROM venta WHERE bajarCambio = 1 AND empleadoId = :empleadoId ORDER BY id")
    suspend fun cambiosParaEmpleado(empleadoId: Long): List<VentaCompleta>

    @Query("UPDATE venta SET bajarCambio = 0 WHERE empleadoId = :empleadoId AND uuid IN (:uuids)")
    suspend fun cambiosConfirmados(empleadoId: Long, uuids: List<String>)

    @Query("SELECT * FROM venta WHERE uuid = :uuid LIMIT 1")
    suspend fun porUuid(uuid: String): VentaEntity?

    @Query("SELECT * FROM venta ORDER BY id") suspend fun todas(): List<VentaEntity>
    @Query("SELECT * FROM detalle_venta ORDER BY id") suspend fun todosDetalles(): List<DetalleVentaEntity>
    @Query("SELECT * FROM transaccion ORDER BY id") suspend fun todasTransacciones(): List<TransaccionEntity>
    @Insert suspend fun insertarVentas(vs: List<VentaEntity>)
    @Insert suspend fun insertarTransacciones(ts: List<TransaccionEntity>)
}

/** P37: el turno «activo» es siempre el de ESTA app; los turnos de las secundarias (empleadoId) solo se ven en el historial. */
@Dao
interface TurnoDao {
    /** Incluye los turnos remotos, no solo el de esta app. */
    @Query("SELECT EXISTS(SELECT 1 FROM turno WHERE cerradoEn IS NULL)")
    suspend fun hayAbiertos(): Boolean

    @Query("SELECT * FROM turno WHERE cerradoEn IS NULL AND empleadoId IS NULL AND local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), local) ORDER BY abiertoEn DESC LIMIT 1")
    fun observarActivo(): Flow<TurnoEntity?>

    @Query("SELECT * FROM turno WHERE cerradoEn IS NULL AND empleadoId IS NULL AND local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), local) ORDER BY abiertoEn DESC LIMIT 1")
    suspend fun activo(): TurnoEntity?

    /** 0.19.3: turno abierto de una secundaria en la principal (para asociar sus cambios de stock remotos). */
    @Query("SELECT * FROM turno WHERE cerradoEn IS NULL AND empleadoId = :empleadoId ORDER BY abiertoEn DESC LIMIT 1")
    suspend fun activoDeEmpleado(empleadoId: Long): TurnoEntity?

    @Query("SELECT * FROM turno WHERE cerradoEn IS NOT NULL AND empleadoId IS NULL AND local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), local) ORDER BY cerradoEn DESC LIMIT 1")
    suspend fun ultimoCerrado(): TurnoEntity?

    /** 0.26.0 (§4): último turno cerrado de esa secundaria (propone el fondo que asigna el dueño). */
    @Query("SELECT * FROM turno WHERE cerradoEn IS NOT NULL AND empleadoId = :empleadoId ORDER BY cerradoEn DESC LIMIT 1")
    suspend fun ultimoCerradoDeEmpleado(empleadoId: Long): TurnoEntity?

    @Query("SELECT * FROM turno WHERE local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), local) ORDER BY abiertoEn DESC")
    fun observarHistorial(): Flow<List<TurnoEntity>>

    @Query("SELECT * FROM turno WHERE id = :id")
    suspend fun obtener(id: Long): TurnoEntity?

    @Insert suspend fun insertar(t: TurnoEntity): Long

    @Query(
        """
        UPDATE turno SET cerradoEn = :cerradoEn, numVentas = :numVentas, unidades = :unidades, totalCent = :totalCent,
            efectivoCent = :efectivoCent, transferenciaCent = :transferenciaCent, costoCent = :costoCent, numMovimientos = :numMovimientos,
            cerradoPor = :cerradoPor, ventasEfectivo = :ventasEfectivo, ventasTransferencia = :ventasTransferencia,
            movimientosProducto = :movimientosProducto, movimientosInsumo = :movimientosInsumo, sincronizado = 0,
            contadoCent = :contadoCent, entradasCent = :entradasCent, salidasCent = :salidasCent, numAnuladas = :numAnuladas
        WHERE id = :id AND cerradoEn IS NULL
        """,
    )
    suspend fun cerrar(
        id: Long, cerradoEn: Long, numVentas: Int, unidades: Long, totalCent: Long,
        efectivoCent: Long, transferenciaCent: Long, costoCent: Long, numMovimientos: Int,
        cerradoPor: String, ventasEfectivo: Int, ventasTransferencia: Int, movimientosProducto: Int, movimientosInsumo: Int,
        contadoCent: Long?, entradasCent: Long, salidasCent: Long, numAnuladas: Int,
    ): Int

    /** 0.25.0: conteo declarado con el turno aún abierto (secundaria que pide el cierre). Vuelve a quedar pendiente de enviar. */
    @Query("UPDATE turno SET contadoCent = :contadoCent, sincronizado = 0 WHERE id = :id AND cerradoEn IS NULL")
    suspend fun declararContado(id: Long, contadoCent: Long): Int

    /** 0.25.0: arqueo en vivo del turno activo de ESTA app (Room lo recalcula al cambiar turno, venta o caja). */
    @Query(
        """
        SELECT t.fondoCent AS fondoCent, t.contadoCent AS contadoCent,
               COALESCE((SELECT SUM(v.totalCent) FROM venta v WHERE v.turnoId = t.id AND v.anuladaEn IS NULL AND v.metodoPago = 'EFECTIVO'), 0) AS efectivoCent,
               COALESCE((SELECT SUM(c.importeCent) FROM movimiento_caja c WHERE c.turnoId = t.id AND c.tipo = 'ENTRADA'), 0) AS entradasCent,
               COALESCE((SELECT SUM(c.importeCent) FROM movimiento_caja c WHERE c.turnoId = t.id AND c.tipo = 'SALIDA'), 0) AS salidasCent
        FROM turno t WHERE t.cerradoEn IS NULL AND t.empleadoId IS NULL
          AND t.local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), t.local)
        ORDER BY t.abiertoEn DESC LIMIT 1
        """,
    )
    fun observarArqueoActivo(): Flow<ArqueoRow?>

    /** 0.25.0: arqueo de cualquier turno (p. ej. el de una secundaria que pide cerrar). */
    @Query(
        """
        SELECT t.fondoCent AS fondoCent, t.contadoCent AS contadoCent,
               COALESCE((SELECT SUM(v.totalCent) FROM venta v WHERE v.turnoId = t.id AND v.anuladaEn IS NULL AND v.metodoPago = 'EFECTIVO'), 0) AS efectivoCent,
               COALESCE((SELECT SUM(c.importeCent) FROM movimiento_caja c WHERE c.turnoId = t.id AND c.tipo = 'ENTRADA'), 0) AS entradasCent,
               COALESCE((SELECT SUM(c.importeCent) FROM movimiento_caja c WHERE c.turnoId = t.id AND c.tipo = 'SALIDA'), 0) AS salidasCent
        FROM turno t WHERE t.id = :turnoId
        """,
    )
    suspend fun arqueoDe(turnoId: Long): ArqueoRow?

    @Query(
        """
        SELECT COUNT(*) AS numVentas,
               COALESCE(SUM(unidades), 0) AS unidades,
               COALESCE(SUM(totalCent), 0) AS totalCent,
               COALESCE(SUM(CASE WHEN metodoPago = 'EFECTIVO' THEN totalCent ELSE 0 END), 0) AS efectivoCent,
               COALESCE(SUM(CASE WHEN metodoPago = 'TRANSFERENCIA' THEN totalCent ELSE 0 END), 0) AS transferenciaCent,
               COALESCE(SUM(costoCent), 0) AS costoCent,
               COALESCE(SUM(CASE WHEN metodoPago = 'EFECTIVO' THEN 1 ELSE 0 END), 0) AS ventasEfectivo,
               COALESCE(SUM(CASE WHEN metodoPago = 'TRANSFERENCIA' THEN 1 ELSE 0 END), 0) AS ventasTransferencia,
               (SELECT COUNT(*) FROM venta a WHERE a.turnoId = :turnoId AND a.anuladaEn IS NOT NULL) AS numAnuladas
        FROM venta WHERE turnoId = :turnoId AND anuladaEn IS NULL
        """,
    )
    suspend fun resumen(turnoId: Long): ResumenTurnoRow

    @Query("SELECT * FROM turno ORDER BY id") suspend fun todos(): List<TurnoEntity>
    @Insert suspend fun insertarTodos(ts: List<TurnoEntity>)
}

/** 0.25.0: entradas y salidas de efectivo. */
@Dao
interface CajaDao {
    @Insert suspend fun insertar(m: MovimientoCajaEntity): Long
    @Insert suspend fun insertarTodos(ms: List<MovimientoCajaEntity>)

    @Query("SELECT * FROM movimiento_caja WHERE turnoId = :turnoId ORDER BY fecha, id")
    suspend fun deTurno(turnoId: Long): List<MovimientoCajaEntity>

    @Query("SELECT COALESCE(SUM(importeCent), 0) FROM movimiento_caja WHERE turnoId = :turnoId AND tipo = :tipo")
    suspend fun suma(turnoId: Long, tipo: String): Long

    @Query("SELECT * FROM movimiento_caja WHERE uuid = :uuid LIMIT 1")
    suspend fun porUuid(uuid: String): MovimientoCajaEntity?

    /** Secundaria: lo aún no recibido por la principal. */
    @Query("SELECT * FROM movimiento_caja WHERE sincronizado = 0 ORDER BY fecha, id")
    suspend fun pendientes(): List<MovimientoCajaEntity>

    @Query("UPDATE movimiento_caja SET sincronizado = 1 WHERE uuid IN (:uuids)")
    suspend fun marcarRecibidos(uuids: List<String>)

    @Query("SELECT * FROM movimiento_caja ORDER BY id") suspend fun todos(): List<MovimientoCajaEntity>
    @Query("DELETE FROM movimiento_caja") suspend fun borrarTodos()
}
