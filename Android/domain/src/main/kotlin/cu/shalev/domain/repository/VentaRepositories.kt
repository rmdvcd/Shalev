package cu.shalev.domain.repository

import cu.shalev.core.money.Cup
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Anulacion
import cu.shalev.domain.model.Arqueo
import cu.shalev.domain.model.MovimientoCaja
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.TipoMovimientoCaja
import cu.shalev.domain.model.ElaboradoEnVenta
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Turno
import cu.shalev.domain.model.Venta
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface VentaRepository {
    fun observarCambios(): Flow<Unit> = kotlinx.coroutines.flow.flowOf(Unit)
    /** Total reactivo de las ventas válidas (efectivo + transferencia) de [turnoId]. */
    fun observarTotalDeTurno(turnoId: Long): Flow<Cup>
    /**
     * Registra la venta de forma ATÓMICA: comprueba que [Venta.turnoId] sigue abierto, re-verifica
     * existencias, inserta venta + detalles + transacción, descuenta stock de los artículos y crea los movimientos VENTA.
     * Errores: TurnoCerrado, StockInsuficiente(nombres), NoEncontrado.
     *
     * [elaborados] (P26): los Elaborados de la venta no tienen existencias; en la misma transacción se descuentan
     * sus insumos (movimientos CONSUMO ligados a la venta) y NO se toca la existencia del Elaborado.
     * Si un insumo ya no alcanza → StockInsuficiente(nombre del insumo) y rollback completo.
     */
    suspend fun registrar(venta: Venta, elaborados: List<ElaboradoEnVenta> = emptyList()): AppResult<Long>
    /** La última comprobación de cotización se ejecuta DENTRO del mismo commit que la venta. */
    suspend fun registrarVerificada(venta: Venta, elaborados: List<ElaboradoEnVenta>, verificar: suspend () -> AppResult<Unit>): AppResult<Long> =
        when (val r = verificar()) { is AppResult.Err -> r; is AppResult.Ok -> registrar(venta, elaborados) }
    suspend fun obtener(id: Long): Venta?
    /** Ventas con detalle en [desde, hasta). */
    suspend fun entre(desde: Instant, hasta: Instant): List<Venta>
    suspend fun deTurno(turnoId: Long): List<Venta>

    /**
     * 0.25.0 (solo app principal): anula la venta de forma ATÓMICA si su turno sigue ABIERTO: la marca (no se borra),
     * devuelve las existencias (movimientos ANULACION, el contrario de cada salida de la venta) y, si el turno es de
     * una secundaria, deja el cambio pendiente de enviarle. Errores: NoEncontrado, TurnoCerrado, Validacion (ya anulada).
     */
    suspend fun anular(ventaId: Long, anulacion: Anulacion): AppResult<Unit>

    /**
     * 0.25.0: «Modificar» = [anular] la original + [registrar] [nueva] (mismo turno), todo en UNA transacción. La original
     * queda con el motivo «Modificada → venta #N». Devuelve el id de la nueva.
     */
    suspend fun modificar(ventaId: Long, anulacion: Anulacion, nueva: Venta, elaborados: List<ElaboradoEnVenta>): AppResult<Long>
    suspend fun modificarVerificada(ventaId: Long, anulacion: Anulacion, nueva: Venta, elaborados: List<ElaboradoEnVenta>, verificar: suspend () -> AppResult<Unit>): AppResult<Long> =
        when (val r = verificar()) { is AppResult.Err -> r; is AppResult.Ok -> modificar(ventaId, anulacion, nueva, elaborados) }

    /** 0.25.0: salidas de existencias de una venta (para devolverlas al planificar una modificación). */
    suspend fun movimientosDe(ventaId: Long): List<MovimientoInventario>
}

interface TurnoRepository {
    /** Instantánea del inventario local para el conteo físico del turno activo. */
    suspend fun prepararConteo(): AppResult<Pair<Long, List<cu.shalev.domain.model.LineaConteo>>> =
        AppResult.Err(cu.shalev.core.result.AppError.SinPermiso)
    /** Guarda la declaración, sin ajustar stock. La principal aplica al cerrar o aprobar. */
    suspend fun guardarConteo(turnoId: Long, conteo: cu.shalev.domain.model.ConteoCierre): AppResult<Unit> =
        AppResult.Err(cu.shalev.core.result.AppError.SinPermiso)

    fun observarActivo(): Flow<Turno?>
    suspend fun activo(): Turno?
    suspend fun ultimoCerrado(): Turno?
    fun observarHistorial(): Flow<List<Turno>>
    suspend fun obtener(id: Long): Turno?
    /** Error TurnoYaAbierto si ya hay uno abierto (verificado en transacción). [usuario] se congela en el turno. */
    suspend fun abrir(ahora: Instant, usuario: String, fondo: Cup? = null): AppResult<Turno>
    /**
     * Cierra y congela el resumen (ventas, totales y nº de ventas por método, costo, movimientos de productos e
     * insumos) calculado en la BD dentro de la misma transacción. Error TurnoCerrado si no hay turno abierto.
     */
    suspend fun cerrar(ahora: Instant, usuario: String, contado: Cup? = null): AppResult<Turno>
    /** Movimientos de inventario e insumos asociados al turno, en orden cronológico. */
    suspend fun movimientosDe(turnoId: Long): List<MovimientoInventario>

    /**
     * 0.31.0: corrige la cantidad de un ajuste de existencias del turno abierto (Registros → Movimientos). Aplica la
     * diferencia al stock ahora, deja el movimiento con la cantidad nueva (y el motivo en la nota) y ajusta la
     * existencia de los movimientos posteriores de esa entidad. [delta] va en la unidad nativa (unidades o milésimas).
     * Errores: [AppError.TurnoCerrado], [AppError.StockInsuficiente] o [AppError.Validacion].
     */
    suspend fun corregirMovimiento(movimientoId: Long, delta: Long, motivo: String, ahora: Long): AppResult<Unit> =
        AppResult.Err(AppError.SinPermiso)

    /** 0.25.0: entrada/salida de efectivo en el turno ACTIVO de esta app. Error TurnoCerrado si no hay. */
    suspend fun registrarCaja(tipo: TipoMovimientoCaja, importe: Cup, motivo: String, hechoPor: String, ahora: Instant): AppResult<Long>
    suspend fun cajaDe(turnoId: Long): List<MovimientoCaja>
    /** 0.25.0: arqueo EN VIVO del turno activo de esta app (null sin turno o sin fondo). */
    fun observarArqueoActivo(): Flow<Arqueo?>
    /** 0.25.0 (secundaria): efectivo contado al solicitar el cierre; se envía con el turno. */
    suspend fun declararContado(contado: Cup): AppResult<Unit>
}

/** Filtros de Registros: fecha (todas las tablas), importe (ventas y transferencias), texto libre. */
data class FiltroRegistro(
    val desde: Instant? = null,
    val hasta: Instant? = null,
    val importeMin: Cup? = null,
    val importeMax: Cup? = null,
    val texto: String? = null,
)

/** Consultas de solo lectura de la pantalla Registros, más recientes primero. */
interface RegistroRepository {
    fun ventas(filtro: FiltroRegistro): Flow<List<Venta>>
    fun transacciones(filtro: FiltroRegistro): Flow<List<Transaccion>>
    /** El filtro de importe no aplica a movimientos (Shalev.txt). */
    fun movimientos(filtro: FiltroRegistro): Flow<List<MovimientoInventario>>
    /** 0.20.0 (H1): nombres distintos de quienes abrieron turnos (filtro «Vendedor»), en orden alfabético. */
    fun vendedores(): Flow<List<String>>
}
