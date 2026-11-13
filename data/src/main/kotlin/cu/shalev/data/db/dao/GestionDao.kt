package cu.shalev.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import cu.shalev.data.db.entity.ConfiguracionNegocioEntity
import cu.shalev.data.db.entity.FichaCostoEntity
import cu.shalev.data.db.entity.LoteConsumoEntity
import cu.shalev.data.db.entity.LoteInventarioEntity
import cu.shalev.data.db.entity.MovimientoRevisionEntity
import cu.shalev.data.db.entity.RecepcionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GestionDao {
    @Query("SELECT * FROM configuracion_negocio WHERE id = 1") fun observarConfiguracion(): Flow<ConfiguracionNegocioEntity?>
    @Query("SELECT * FROM configuracion_negocio WHERE id = 1") suspend fun configuracion(): ConfiguracionNegocioEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun guardarConfiguracion(valor: ConfiguracionNegocioEntity)
    @Query("DELETE FROM configuracion_negocio") suspend fun borrarConfiguracion()

    @Query("SELECT * FROM ficha_costo WHERE localId = (SELECT id FROM almacen_local WHERE inventario = 1 LIMIT 1) ORDER BY actualizadoEn DESC, id DESC")
    fun observarFichas(): Flow<List<FichaCostoEntity>>

    @Query("SELECT * FROM ficha_costo WHERE id = :id")
    suspend fun ficha(id: Long): FichaCostoEntity?

    @Query("SELECT * FROM ficha_costo") suspend fun fichas(): List<FichaCostoEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun guardarFicha(ficha: FichaCostoEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun importarFichas(fichas: List<FichaCostoEntity>)
    @Query("DELETE FROM ficha_costo WHERE id = :id AND localId = (SELECT id FROM almacen_local WHERE inventario = 1 LIMIT 1)")
    suspend fun borrarFicha(id: Long): Int
    @Query("DELETE FROM ficha_costo") suspend fun borrarFichas()

    @Query("SELECT * FROM recepcion WHERE localId = (SELECT id FROM almacen_local WHERE inventario = 1 LIMIT 1) ORDER BY fecha DESC, uuid DESC LIMIT 200")
    fun observarRecepciones(): Flow<List<RecepcionEntity>>
    @Query("SELECT * FROM lote_inventario WHERE localId = (SELECT id FROM almacen_local WHERE inventario = 1 LIMIT 1) ORDER BY caducidad IS NULL, caducidad, id")
    fun observarLotes(): Flow<List<LoteInventarioEntity>>
    @Query("SELECT * FROM lote_inventario WHERE localId = :localId AND entidad = :entidad AND entidadId = :entidadId AND disponibleMil > 0 ORDER BY caducidad IS NULL, caducidad, id")
    suspend fun disponibles(localId: String, entidad: String, entidadId: Long): List<LoteInventarioEntity>
    @Query("SELECT * FROM lote_inventario WHERE id = :id") suspend fun lote(id: String): LoteInventarioEntity?
    @Update suspend fun actualizarLote(lote: LoteInventarioEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertarRecepcion(recepcion: RecepcionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertarLotes(lotes: List<LoteInventarioEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertarConsumo(consumo: LoteConsumoEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun importarConsumos(consumos: List<LoteConsumoEntity>)
    @Query("SELECT * FROM lote_consumo WHERE ventaId = :ventaId") suspend fun consumosDe(ventaId: Long): List<LoteConsumoEntity>
    @Query("DELETE FROM lote_consumo WHERE ventaId = :ventaId") suspend fun borrarConsumosDe(ventaId: Long)
    @Query("SELECT * FROM recepcion") suspend fun recepciones(): List<RecepcionEntity>
    @Query("SELECT * FROM lote_inventario") suspend fun lotes(): List<LoteInventarioEntity>
    @Query("SELECT * FROM lote_consumo") suspend fun consumos(): List<LoteConsumoEntity>
    @Query("DELETE FROM lote_consumo") suspend fun borrarConsumos()
    @Query("DELETE FROM lote_inventario") suspend fun borrarLotes()
    @Query("DELETE FROM recepcion") suspend fun borrarRecepciones()

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertarRevision(revision: MovimientoRevisionEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun importarRevisiones(revisiones: List<MovimientoRevisionEntity>)
    @Query("SELECT * FROM movimiento_revision ORDER BY fecha, id") suspend fun revisiones(): List<MovimientoRevisionEntity>
    @Query("DELETE FROM movimiento_revision") suspend fun borrarRevisiones()
}
