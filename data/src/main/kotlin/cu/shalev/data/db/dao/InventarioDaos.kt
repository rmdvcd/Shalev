package cu.shalev.data.db.dao

import cu.shalev.data.db.entity.ServicioInsumoEntity
import cu.shalev.data.db.entity.ServicioEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import cu.shalev.data.db.entity.InsumoEntity
import cu.shalev.data.db.entity.MovimientoEntity
import cu.shalev.data.db.entity.MovimientosTurnoRow
import cu.shalev.data.db.entity.ProductoEntity
import cu.shalev.data.db.entity.RecetaLineaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductoDao {
    @Query("SELECT * FROM producto WHERE eliminado = 0 AND local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), local) ORDER BY creadoEn DESC, id DESC")
    fun observarActivos(): Flow<List<ProductoEntity>>

    @Query("SELECT * FROM producto WHERE id = :id")
    fun observar(id: Long): Flow<ProductoEntity?>

    @Query("SELECT * FROM producto WHERE id = :id")
    suspend fun obtener(id: Long): ProductoEntity?

    @Query("SELECT * FROM producto WHERE id IN (:ids)")
    suspend fun obtenerVarios(ids: List<Long>): List<ProductoEntity>

    @Query("SELECT DISTINCT categoria FROM producto WHERE eliminado = 0 AND local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), local)")
    suspend fun categorias(): List<String>

    @Insert suspend fun insertar(p: ProductoEntity): Long
    @Update suspend fun actualizar(p: ProductoEntity)

    /**
     * Suma condicional y atómica: 0 filas afectadas = no existe, eliminado o una resta que dejaría negativo.
     * Sumar siempre se permite (0.21.6: antes, con existencia negativa, una entrada también se rechazaba).
     */
    @Query("UPDATE producto SET cantidad = cantidad + :delta, actualizadoEn = :ahora WHERE id = :id AND eliminado = 0 AND (:delta >= 0 OR cantidad + :delta >= 0)")
    suspend fun sumarStock(id: Long, delta: Long, ahora: Long): Int

    @Query("UPDATE producto SET precioVentaCent = :precioCent, actualizadoEn = :ahora WHERE id = :id AND eliminado = 0")
    suspend fun actualizarPrecio(id: Long, precioCent: Long, ahora: Long): Int

    @Query("SELECT cantidad FROM producto WHERE id = :id")
    suspend fun cantidad(id: Long): Long?

    @Query("SELECT * FROM producto ORDER BY id")
    suspend fun todos(): List<ProductoEntity>

    @Insert suspend fun insertarTodos(ps: List<ProductoEntity>)

    /** 0.32.0: la foto que la secundaria se descargó (no cambia `actualizadoEn`: la foto no es una existencia). */
    @Query("UPDATE producto SET fotoUri = :uri WHERE id = :id")
    suspend fun actualizarFoto(id: Long, uri: String): Int
}

@Dao
interface RecetaDao {
    @Query("SELECT * FROM receta_linea WHERE productoId = :productoId")
    suspend fun lineas(productoId: Long): List<RecetaLineaEntity>

    @Query("SELECT DISTINCT productoId FROM receta_linea WHERE insumoId = :insumoId")
    suspend fun productosQueUsan(insumoId: Long): List<Long>

    @Query("DELETE FROM receta_linea WHERE productoId = :productoId")
    suspend fun borrarDe(productoId: Long)

    @Insert suspend fun insertarTodas(lineas: List<RecetaLineaEntity>)

    @Query("SELECT * FROM receta_linea")
    suspend fun todas(): List<RecetaLineaEntity>
}

@Dao
interface InsumoDao {
    @Query("SELECT * FROM insumo WHERE local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), local) ORDER BY creadoEn DESC, id DESC")
    fun observarTodos(): Flow<List<InsumoEntity>>

    @Query("SELECT * FROM insumo WHERE id = :id")
    suspend fun obtener(id: Long): InsumoEntity?

    @Query("SELECT * FROM insumo WHERE id IN (:ids)")
    suspend fun obtenerVarios(ids: List<Long>): List<InsumoEntity>

    @Query("SELECT * FROM insumo ORDER BY creadoEn DESC, id DESC")
    suspend fun todos(): List<InsumoEntity>

    @Insert suspend fun insertar(i: InsumoEntity): Long
    @Update suspend fun actualizar(i: InsumoEntity)

    @Query("DELETE FROM insumo WHERE id = :id")
    suspend fun borrar(id: Long): Int

    /** Resta solo si alcanza; sumar siempre se permite (0.21.6). */
    @Query("UPDATE insumo SET cantidadMil = cantidadMil + :deltaMil, actualizadoEn = :ahora WHERE id = :id AND (:deltaMil >= 0 OR cantidadMil + :deltaMil >= 0)")
    suspend fun sumarStock(id: Long, deltaMil: Long, ahora: Long): Int

    @Query("SELECT cantidadMil FROM insumo WHERE id = :id")
    suspend fun cantidad(id: Long): Long?

    @Insert suspend fun insertarTodos(xs: List<InsumoEntity>)
}

@Dao
interface MovimientoDao {
    @Query("SELECT * FROM movimiento WHERE entidad = :entidad AND entidadId = :id ORDER BY id DESC LIMIT 1")
    suspend fun ultimoDe(entidad: String, id: Long): MovimientoEntity?

    @Insert suspend fun insertar(m: MovimientoEntity): Long
    @Insert suspend fun insertarTodos(ms: List<MovimientoEntity>)

    @Query(
        """
        SELECT m.id, m.fecha, m.tipo, m.entidad, m.entidadId, m.nombre, m.delta, m.existencia, m.turnoId, m.ventaId, m.nota,
               m.local, m.motivo,
               COALESCE(NULLIF(m.hechoPor, ''), t.abiertoPor, NULLIF(TRIM(COALESCE(p.nombre, '') || ' ' || COALESCE(p.apellidos, '')), ''),
                        'Titular del dispositivo') AS hechoPor
        FROM movimiento m
        LEFT JOIN turno t ON t.id = m.turnoId
        LEFT JOIN perfil p ON p.id = 1
        WHERE (:desde IS NULL OR m.fecha >= :desde)
          AND (:hasta IS NULL OR m.fecha < :hasta)
          AND m.local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), m.local)
          AND (:texto IS NULL OR m.nombre LIKE '%' || :texto || '%' ESCAPE '\' OR m.nota LIKE '%' || :texto || '%' ESCAPE '\')
        ORDER BY m.fecha DESC, m.id DESC
        """,
    )
    fun filtrar(desde: Long?, hasta: Long?, texto: String?): Flow<List<MovimientoEntity>>

    @Query("SELECT COUNT(*) FROM movimiento WHERE turnoId = :turnoId")
    suspend fun contarDeTurno(turnoId: Long): Int

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN entidad = 'PRODUCTO' THEN 1 ELSE 0 END), 0) AS producto,
               COALESCE(SUM(CASE WHEN entidad = 'INSUMO' THEN 1 ELSE 0 END), 0) AS insumo
        FROM movimiento WHERE turnoId = :turnoId
        """,
    )
    suspend fun contarDeTurnoPorEntidad(turnoId: Long): MovimientosTurnoRow

    @Query("SELECT * FROM movimiento WHERE turnoId = :turnoId ORDER BY fecha, id")
    suspend fun deTurno(turnoId: Long): List<MovimientoEntity>

    @Query("SELECT * FROM movimiento WHERE id = :id")
    suspend fun obtener(id: Long): MovimientoEntity?

    @Update suspend fun actualizar(m: MovimientoEntity)

    /**
     * 0.31.0: al corregir la cantidad de un ajuste, la existencia resultante de los movimientos posteriores de esa
     * entidad se desplaza lo mismo, para que el libro siga cuadrando.
     */
    @Query("UPDATE movimiento SET existencia = existencia + :delta WHERE entidad = :entidad AND entidadId = :entidadId AND id > :id")
    suspend fun desplazarExistencia(entidad: String, entidadId: Long, id: Long, delta: Long)

    @Query("SELECT * FROM movimiento ORDER BY id")
    suspend fun todos(): List<MovimientoEntity>
}

/** v4 (P29): servicios y los insumos que consumen. */
@Dao
interface ServicioDao {
    @Query("SELECT * FROM servicio WHERE eliminado = 0 AND local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), local) ORDER BY creadoEn DESC, id DESC")
    fun observarTodos(): Flow<List<ServicioEntity>>

    @Query("SELECT * FROM servicio WHERE id = :id")
    suspend fun obtener(id: Long): ServicioEntity?

    @Query("SELECT * FROM servicio WHERE id IN (:ids)")
    suspend fun obtenerVarios(ids: List<Long>): List<ServicioEntity>

    @Query("SELECT * FROM servicio ORDER BY id")
    suspend fun todos(): List<ServicioEntity>

    @Query("SELECT DISTINCT tipo FROM servicio WHERE eliminado = 0 AND local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), local) ORDER BY tipo COLLATE NOCASE")
    suspend fun tiposEnUso(): List<String>

    @Insert suspend fun insertar(s: ServicioEntity): Long
    @Update suspend fun actualizar(s: ServicioEntity)
    @Insert suspend fun insertarTodos(xs: List<ServicioEntity>)

    /** 0.32.0: la foto que la secundaria se descargó (no cambia `actualizadoEn`). */
    @Query("UPDATE servicio SET fotoUri = :uri WHERE id = :id")
    suspend fun actualizarFoto(id: Long, uri: String): Int

    @Query("UPDATE servicio SET eliminado = 1, actualizadoEn = :ahora WHERE id = :id AND eliminado = 0")
    suspend fun marcarEliminado(id: Long, ahora: Long): Int

    @Query("SELECT si.* FROM servicio_insumo si JOIN servicio s ON s.id = si.servicioId WHERE s.eliminado = 0 AND s.local = COALESCE((SELECT nombre FROM almacen_local WHERE inventario = 1 LIMIT 1), s.local)")
    fun observarInsumos(): Flow<List<ServicioInsumoEntity>>

    @Query("SELECT * FROM servicio_insumo WHERE servicioId = :servicioId")
    suspend fun insumos(servicioId: Long): List<ServicioInsumoEntity>

    @Query("SELECT * FROM servicio_insumo")
    suspend fun todosInsumos(): List<ServicioInsumoEntity>

    @Insert suspend fun insertarInsumos(xs: List<ServicioInsumoEntity>)

    @Query("DELETE FROM servicio_insumo WHERE servicioId = :servicioId")
    suspend fun borrarInsumosDe(servicioId: Long)

    @Query("SELECT DISTINCT servicioId FROM servicio_insumo WHERE insumoId = :insumoId")
    suspend fun serviciosQueUsan(insumoId: Long): List<Long>
}
