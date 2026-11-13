package cu.shalev.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cu.shalev.data.db.entity.ClienteFijoEntity
import kotlinx.coroutines.flow.Flow

/** Compras (transferencias de ventas no anuladas) de un cliente fijo. */
data class ComprasClienteFila(val ci: String, val compras: Int, val totalCent: Long, val ultima: Long?)

/** 0.27.0 (N2): clientes fijos. */
@Dao
interface ClienteFijoDao {
    @Query("SELECT * FROM cliente_fijo ORDER BY nombreApellidos COLLATE NOCASE")
    fun observar(): Flow<List<ClienteFijoEntity>>

    @Query(
        "SELECT t.clienteCi AS ci, COUNT(*) AS compras, SUM(t.importeCent) AS totalCent, MAX(t.fecha) AS ultima " +
            "FROM transaccion t JOIN venta v ON v.id = t.ventaId " +
            "WHERE v.anuladaEn IS NULL AND t.clienteCi IN (SELECT ci FROM cliente_fijo) " +
            "AND v.local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), v.local) GROUP BY t.clienteCi",
    )
    fun observarCompras(): Flow<List<ComprasClienteFila>>

    /**
     * 0.31.0: al corregir un cliente, sus transferencias ya registradas se corrigen igual (el carné, si cambió, y el
     * nombre y el teléfono), para que Registros → Transferencias y su ficha muestren siempre los datos actuales.
     */
    @Query("UPDATE transaccion SET clienteCi = :nuevoCi, clienteNombre = :nombre, clienteTelefono = :telefono WHERE clienteCi = :ci")
    suspend fun corregirCompras(ci: String, nuevoCi: String, nombre: String, telefono: String)

    @Query("SELECT * FROM cliente_fijo ORDER BY id") suspend fun todos(): List<ClienteFijoEntity>
    @Query("SELECT * FROM cliente_fijo WHERE ci = :ci") suspend fun porCi(ci: String): ClienteFijoEntity?
    @Insert suspend fun insertar(c: ClienteFijoEntity): Long
    @Insert suspend fun insertarTodos(l: List<ClienteFijoEntity>)
    @Update suspend fun actualizar(c: ClienteFijoEntity)
    @Query("DELETE FROM cliente_fijo WHERE ci = :ci") suspend fun quitar(ci: String): Int
}

/**
 * Alta o actualización por carné (llamar dentro de una transacción). Sin carné válido no hace nada: el carné es lo
 * que identifica al cliente.
 */
suspend fun ClienteFijoDao.registrar(nombreApellidos: String, ci: String, telefono: String, ahora: Long) {
    val carne = ci.trim()
    if (carne.isEmpty()) return
    val e = porCi(carne)
    if (e == null) {
        insertar(ClienteFijoEntity(nombreApellidos = nombreApellidos.trim(), ci = carne, telefono = telefono.trim(), creadoEn = ahora, actualizadoEn = ahora))
    } else {
        actualizar(e.copy(nombreApellidos = nombreApellidos.trim(), telefono = telefono.trim(), actualizadoEn = ahora))
    }
}
