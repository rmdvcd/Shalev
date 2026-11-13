package cu.shalev.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import cu.shalev.data.db.entity.EmpleadoEntity
import cu.shalev.data.db.entity.ReciboComandoEntity
import cu.shalev.data.db.entity.MovimientoEntity
import cu.shalev.data.db.entity.TurnoEntity
import cu.shalev.data.db.entity.VentaCompleta
import kotlinx.coroutines.flow.Flow

/**
 * P37: consultas de la sincronización Principal ⇄ Secundaria.
 * Las de «secundaria» leen lo pendiente de enviar; las de «principal» aplican lo recibido sin duplicar (uuid).
 */
/** 0.20.0 (H5): turno abierto de una secundaria (epoch ms). */
data class TurnoDeEmpleado(
    val empleadoId: Long,
    val abiertoEn: Long,
    /** 0.25.0: arqueo del turno abierto (null = sin fondo). */
    val fondoCent: Long? = null,
    val contadoCent: Long? = null,
    val efectivoCent: Long = 0,
    val entradasCent: Long = 0,
    val salidasCent: Long = 0,
    val conteoCierreJson: String? = null,
)

@Dao
interface SyncDao {
    // ------------------------------------------------------------------ principal: apps secundarias
    @Query("SELECT * FROM empleado WHERE activo = 1 ORDER BY nombre COLLATE NOCASE")
    fun observarEmpleados(): Flow<List<EmpleadoEntity>>

    @Query("SELECT * FROM empleado WHERE activo = 1 ORDER BY nombre COLLATE NOCASE")
    suspend fun empleados(): List<EmpleadoEntity>

    @Query("SELECT * FROM empleado WHERE id = :id")
    suspend fun empleado(id: Long): EmpleadoEntity?

    @Insert suspend fun insertarEmpleado(e: EmpleadoEntity): Long
    @Update suspend fun actualizarEmpleado(e: EmpleadoEntity)

    /** 0.20.0 (H4/H5): updates de un campo, para no pisar con una copia vieja lo que cambió a la vez. */
    @Query("UPDATE empleado SET tarjetaId = :tarjetaId, telefonoId = :telefonoId WHERE id = :id AND activo = 1")
    suspend fun cambiarCobro(id: Long, tarjetaId: Long?, telefonoId: Long?): Int

    @Query("UPDATE empleado SET cierreSolicitadoEn = :ahora WHERE id = :id AND activo = 1")
    suspend fun pedirCierre(id: Long, ahora: Long): Int

    @Query("UPDATE empleado SET cierreSolicitadoEn = NULL, cierrePedidoPorEmpleadoEn = NULL WHERE id = :id")
    suspend fun olvidarCierre(id: Long)

    /** 0.21.0 (C6): solicitud del empleado. No pisa una ya guardada ni un rechazo pendiente de avisar (0). */
    @Query("UPDATE empleado SET cierrePedidoPorEmpleadoEn = :ahora WHERE id = :id AND activo = 1 AND cierrePedidoPorEmpleadoEn IS NULL")
    suspend fun registrarSolicitudCierre(id: Long, ahora: Long): Int

    /** 0.21.0 (C6): null = sin solicitud; 0 = rechazada (falta avisar); otro = pedida en ese instante. */
    @Query("UPDATE empleado SET cierrePedidoPorEmpleadoEn = :valor WHERE id = :id")
    suspend fun marcarSolicitudCierre(id: Long, valor: Long?)

    /** 0.21.0 (C2): teléfono que escribió el empleado en su app. */
    @Query("UPDATE empleado SET telefono = :telefono WHERE id = :id AND activo = 1")
    suspend fun cambiarTelefono(id: Long, telefono: String?)

    @Query("UPDATE empleado SET versionCode = :code WHERE id = :id")
    suspend fun cambiarVersion(id: Long, code: Int?)

    /**
     * 0.31.0 (mitigación 7): `deviceId` de la app vinculada a este empleado (lo necesita la principal para firmarle su
     * sub-token). NULL = todavía no se conoce.
     */
    @Query("UPDATE empleado SET device = :device WHERE id = :id")
    suspend fun cambiarDevice(id: Long, device: String?)

    /** 0.31.0: ids de los empleados activos, en el orden de vinculación (define la ranura 1..N de cada secundaria). */
    @Query("SELECT id FROM empleado WHERE activo = 1 ORDER BY id")
    suspend fun empleadosActivosIds(): List<Long>

    /** 0.20.0 (H5): turnos abiertos de las secundarias, para «Turno abierto desde las HH:MM» en su ficha. */
    @Query(
        """
        SELECT t.conteoCierreJson AS conteoCierreJson, t.empleadoId AS empleadoId, t.abiertoEn AS abiertoEn, t.fondoCent AS fondoCent, t.contadoCent AS contadoCent,
               COALESCE((SELECT SUM(v.totalCent) FROM venta v WHERE v.turnoId = t.id AND v.anuladaEn IS NULL AND v.metodoPago = 'EFECTIVO'), 0) AS efectivoCent,
               COALESCE((SELECT SUM(c.importeCent) FROM movimiento_caja c WHERE c.turnoId = t.id AND c.tipo = 'ENTRADA'), 0) AS entradasCent,
               COALESCE((SELECT SUM(c.importeCent) FROM movimiento_caja c WHERE c.turnoId = t.id AND c.tipo = 'SALIDA'), 0) AS salidasCent
        FROM turno t
        WHERE t.cerradoEn IS NULL AND t.empleadoId IS NOT NULL
          AND t.abiertoEn = (SELECT MIN(x.abiertoEn) FROM turno x WHERE x.cerradoEn IS NULL AND x.empleadoId = t.empleadoId)
        """,
    )
    fun observarTurnosDeEmpleados(): Flow<List<TurnoDeEmpleado>>

    @Query("UPDATE empleado SET ultimaSincronizacion = :ahora WHERE id = :id")
    suspend fun marcarSincronizado(id: Long, ahora: Long)

    // ------------------------------------------------------------------ principal: aplicar lo recibido
    /** 0.19.2: turnos abiertos en este teléfono (en la principal incluye los de las secundarias ya recibidos). */
    @Query("SELECT COUNT(*) FROM turno WHERE cerradoEn IS NULL")
    fun observarTurnosAbiertos(): Flow<Int>

    @Query("SELECT * FROM turno WHERE uuid = :uuid LIMIT 1")
    suspend fun turnoPorUuid(uuid: String): TurnoEntity?

    @Query("SELECT id FROM venta WHERE uuid = :uuid LIMIT 1")
    suspend fun ventaPorUuid(uuid: String): Long?

    @Insert suspend fun insertarTurno(t: TurnoEntity): Long
    @Update suspend fun actualizarTurno(t: TurnoEntity)

    /**
     * Existencias de una venta hecha en una secundaria: se aplican SIEMPRE (la venta ya ocurrió). Si dos apps vendieron
     * a la vez lo último, la existencia queda negativa y el producto sale en «Stock crítico» para que el dueño lo revise.
     */
    @Query("UPDATE producto SET cantidad = cantidad + :delta, actualizadoEn = :ahora WHERE id = :id AND typeof(cantidad + :delta) = 'integer'")
    suspend fun forzarStockProducto(id: Long, delta: Long, ahora: Long): Int

    @Query("UPDATE insumo SET cantidadMil = cantidadMil + :deltaMil, actualizadoEn = :ahora WHERE id = :id AND typeof(cantidadMil + :deltaMil) = 'integer'")
    suspend fun forzarStockInsumo(id: Long, deltaMil: Long, ahora: Long): Int

    // ------------------------------------------------------------------ secundaria: lo pendiente de enviar
    @Query("SELECT * FROM turno WHERE sincronizado = 0 ORDER BY abiertoEn, id")
    suspend fun turnosPendientes(): List<TurnoEntity>

    @Transaction
    @Query("SELECT * FROM venta WHERE sincronizado = 0 ORDER BY fecha, id")
    suspend fun ventasPendientes(): List<VentaCompleta>

    @Query("SELECT * FROM movimiento WHERE ventaId IN (:ventaIds) ORDER BY id")
    suspend fun movimientosDeVentas(ventaIds: List<Long>): List<MovimientoEntity>

    /** Movimientos de las ventas aún no recibidas: se vuelven a aplicar sobre el catálogo que llega de la principal. */
    @Query("SELECT m.* FROM movimiento m JOIN venta v ON v.id = m.ventaId WHERE v.sincronizado = 0 ORDER BY m.id")
    suspend fun movimientosPendientes(): List<MovimientoEntity>

    @Query("SELECT (SELECT COUNT(*) FROM venta WHERE sincronizado = 0) + (SELECT COUNT(*) FROM turno WHERE sincronizado = 0) + (SELECT COUNT(*) FROM movimiento_caja WHERE sincronizado = 0)")
    fun observarPendientes(): Flow<Int>

    @Query("SELECT (SELECT COUNT(*) FROM venta WHERE sincronizado = 0) + (SELECT COUNT(*) FROM turno WHERE sincronizado = 0) + (SELECT COUNT(*) FROM movimiento_caja WHERE sincronizado = 0)")
    suspend fun pendientes(): Int

    @Query("UPDATE venta SET sincronizado = 1 WHERE uuid IN (:uuids)")
    suspend fun marcarVentasRecibidas(uuids: List<String>)

    /** Solo si sigue en el estado que se envió: un turno cerrado DESPUÉS de enviarlo abierto queda pendiente. */
    @Query(
        """
        UPDATE turno SET sincronizado = 1
        WHERE uuid = :uuid AND ((:cerrado = 1 AND cerradoEn IS NOT NULL) OR (:cerrado = 0 AND cerradoEn IS NULL))
        """,
    )
    suspend fun marcarTurnoRecibido(uuid: String, cerrado: Boolean)

    // ------------------------------------------------------------------ principal: recibos idempotentes de comandos
    @Query("SELECT * FROM comando_recibo WHERE empleadoId = :empleadoId AND clave = :clave LIMIT 1")
    suspend fun reciboComando(empleadoId: Long, clave: String): ReciboComandoEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun guardarReciboComando(recibo: ReciboComandoEntity)
}
