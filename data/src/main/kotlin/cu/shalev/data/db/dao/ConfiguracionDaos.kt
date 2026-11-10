package cu.shalev.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import cu.shalev.data.db.entity.PerfilEntity
import cu.shalev.data.db.entity.PreajusteCompleto
import cu.shalev.data.db.entity.PreajusteEntity
import cu.shalev.data.db.entity.PreajusteProductoEntity
import cu.shalev.data.db.entity.PreajusteServicioEntity
import cu.shalev.data.db.entity.TarjetaEntity
import cu.shalev.data.db.entity.TelefonoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PerfilDao {
    @Query("SELECT * FROM perfil WHERE id = 1") fun observar(): Flow<PerfilEntity?>
    @Query("SELECT * FROM tarjeta ORDER BY id") fun observarTarjetas(): Flow<List<TarjetaEntity>>
    @Query("SELECT * FROM telefono ORDER BY id") fun observarTelefonos(): Flow<List<TelefonoEntity>>

    @Query("SELECT * FROM perfil WHERE id = 1") suspend fun obtener(): PerfilEntity?
    @Query("SELECT * FROM tarjeta ORDER BY id") suspend fun tarjetas(): List<TarjetaEntity>
    @Query("SELECT * FROM telefono ORDER BY id") suspend fun telefonos(): List<TelefonoEntity>

    @Upsert suspend fun guardar(p: PerfilEntity)
    @Query("DELETE FROM tarjeta") suspend fun borrarTarjetas()
    @Query("DELETE FROM telefono") suspend fun borrarTelefonos()
    @Insert suspend fun insertarTarjeta(t: TarjetaEntity): Long
    @Insert suspend fun insertarTelefono(t: TelefonoEntity): Long
}

@Dao
interface PreciosDao {
    @Transaction
    @Query("SELECT * FROM preajuste ORDER BY nombre COLLATE NOCASE")
    fun observarPreajustes(): Flow<List<PreajusteCompleto>>

    @Transaction
    @Query("SELECT * FROM preajuste WHERE activo = 1")
    suspend fun activos(): List<PreajusteCompleto>

    @Transaction
    @Query("SELECT * FROM preajuste ORDER BY id")
    suspend fun todos(): List<PreajusteCompleto>

    @Insert suspend fun insertar(p: PreajusteEntity): Long
    @Upsert suspend fun guardar(p: PreajusteEntity)
    @Query("DELETE FROM preajuste WHERE id = :id") suspend fun borrar(id: Long): Int
    @Query("DELETE FROM preajuste_producto WHERE preajusteId = :id") suspend fun borrarProductos(id: Long)
    @Insert suspend fun insertarProductos(ps: List<PreajusteProductoEntity>)
    @Query("DELETE FROM preajuste_servicio WHERE preajusteId = :id") suspend fun borrarServicios(id: Long)
    @Insert suspend fun insertarServicios(ss: List<PreajusteServicioEntity>)
}

/** Vaciado ordenado (hijos antes que padres) para restaurar respaldos dentro de una transacción. */
@Dao
interface MantenimientoDao {
    @Query("DELETE FROM transaccion") suspend fun borrarTransacciones()
    @Query("DELETE FROM detalle_venta") suspend fun borrarDetalles()
    @Query("DELETE FROM venta") suspend fun borrarVentas()
    @Query("DELETE FROM movimiento_caja") suspend fun borrarCaja()
    @Query("DELETE FROM movimiento") suspend fun borrarMovimientos()
    @Query("DELETE FROM turno") suspend fun borrarTurnos()
    @Query("DELETE FROM receta_linea") suspend fun borrarRecetas()
    @Query("DELETE FROM preajuste_producto") suspend fun borrarPreajusteProductos()
    @Query("DELETE FROM preajuste_servicio") suspend fun borrarPreajusteServicios()
    @Query("DELETE FROM preajuste") suspend fun borrarPreajustes()
    @Query("DELETE FROM producto") suspend fun borrarProductos()
    @Query("DELETE FROM insumo") suspend fun borrarInsumos()
    @Query("DELETE FROM servicio_insumo") suspend fun borrarServicioInsumos()
    @Query("DELETE FROM servicio") suspend fun borrarServicios()
    @Query("DELETE FROM tarjeta") suspend fun borrarTarjetas()
    @Query("DELETE FROM telefono") suspend fun borrarTelefonos()
    @Query("DELETE FROM perfil") suspend fun borrarPerfil()
    @Query("DELETE FROM cliente_fijo") suspend fun borrarClientesFijos()
    /** Los recibos se invalidan al restaurar otra versión de los datos de negocio. */
    @Query("DELETE FROM comando_recibo") suspend fun borrarRecibosComandos()

    suspend fun borrarOperacion() {
        borrarTransacciones(); borrarDetalles(); borrarVentas(); borrarMovimientos(); borrarCaja(); borrarTurnos()
        borrarRecetas(); borrarServicioInsumos(); borrarPreajusteServicios(); borrarServicios(); borrarPreajusteProductos(); borrarProductos(); borrarInsumos()
    }

    @Query("DELETE FROM almacen_local") suspend fun borrarAlmacenesLocales()

    suspend fun borrarConfiguracion() {
        borrarRecibosComandos()
        borrarAlmacenesLocales()
        borrarPreajusteServicios(); borrarPreajusteProductos(); borrarPreajustes(); borrarTarjetas(); borrarTelefonos(); borrarPerfil(); borrarClientesFijos()
    }
}
