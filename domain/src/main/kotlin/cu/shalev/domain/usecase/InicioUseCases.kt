package cu.shalev.domain.usecase

import cu.shalev.core.time.Dates
import cu.shalev.domain.di.IoDispatcher
import cu.shalev.domain.model.validas

import cu.shalev.domain.repository.ServicioRepository
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.ConteoAlertas
import cu.shalev.domain.model.GraficosPeriodo
import cu.shalev.domain.model.OpcionPeriodo
import cu.shalev.domain.model.Periodo
import cu.shalev.domain.model.ResumenGeneral
import cu.shalev.domain.model.PeriodoPreset
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.repository.PreferenciasRepository
import cu.shalev.domain.repository.ProductoRepository
import cu.shalev.domain.repository.TurnoRepository
import cu.shalev.domain.repository.VentaRepository
import cu.shalev.domain.service.Estadisticas
import cu.shalev.domain.service.Stock
import java.time.ZoneId
import cu.shalev.domain.model.Preferencias
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Contadores de alertas de Inicio; se recalculan al cambiar productos, insumos o niveles.
 *
 * 0.30.0 (F1): `Stock.conteo` recorre todos los productos e insumos y sumaba en el hilo del colector (Main, porque
 * lo recolectan los `stateIn` de los ViewModels). Con [io] el cálculo ocurre fuera del hilo principal.
 */
class ObservarAlertas @Inject constructor(
    private val productos: ProductoRepository,
    private val insumos: InsumoRepository,
    private val preferencias: PreferenciasRepository,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    operator fun invoke(zone: ZoneId = ZoneId.systemDefault()): Flow<ConteoAlertas> =
        combine(productos.observarTodos(), insumos.observarTodos(), preferencias.preferencias) { ps, ins, pref ->
            Stock.conteo(ps, ins, pref.niveles, Dates.localDate(clock.now(), zone), Preferencias.DIAS_AVISO_CADUCIDAD)
        }.flowOn(io)
}

/** Período por defecto del gráfico Ventas: turno abierto; si no, el último cerrado; si no hay turnos, hoy. */
class PeriodoPorDefecto @Inject constructor(private val turnos: TurnoRepository, private val clock: Clock) {
    suspend operator fun invoke(zone: ZoneId = ZoneId.systemDefault()): Periodo =
        (turnos.activo() ?: turnos.ultimoCerrado())?.let { Periodo.DeTurno(it.id) }
            ?: Estadisticas.rango(PeriodoPreset.HOY, clock.now(), zone)
}

class RangoDePreset @Inject constructor(private val clock: Clock) {
    operator fun invoke(preset: PeriodoPreset, zone: ZoneId = ZoneId.systemDefault()): Periodo.Rango =
        Estadisticas.rango(preset, clock.now(), zone)
}

/** Opción del selector → período concreto. */
class ResolverPeriodo @Inject constructor(
    private val porDefecto: PeriodoPorDefecto,
    private val rango: RangoDePreset,
) {
    suspend operator fun invoke(opcion: OpcionPeriodo, zone: ZoneId = ZoneId.systemDefault()): Periodo = when (opcion) {
        OpcionPeriodo.TURNO -> porDefecto(zone)
        OpcionPeriodo.HOY -> rango(PeriodoPreset.HOY, zone)
        OpcionPeriodo.SEMANA -> rango(PeriodoPreset.SEMANA, zone)
        OpcionPeriodo.MES -> rango(PeriodoPreset.MES, zone)
        OpcionPeriodo.ANIO -> rango(PeriodoPreset.ANIO, zone)
    }
}

/**
 * Ventas (barras) y Ganancia Neta (área costo vs venta) del período. Un turno abierto se mide hasta "ahora".
 * [pedidoTurno] = el usuario eligió TURNO: si el período resultó ser un rango es que aún no hay turnos.
 */
class ObtenerGraficosPeriodo @Inject constructor(
    private val ventas: VentaRepository,
    private val turnos: TurnoRepository,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /**
     * 0.30.0 (F1): con el período «Año» esto carga miles de ventas con sus líneas, las convierte a dominio y las
     * agrupa por cubo; antes todo eso ocurría en el hilo que llamaba (el Main del ViewModel) y la pantalla se
     * congelaba. [io] lo saca del hilo principal sin cambiar ninguna firma ni el resultado.
     */
    suspend operator fun invoke(periodo: Periodo, pedidoTurno: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): AppResult<GraficosPeriodo> =
        withContext(io) {
            when (periodo) {
                is Periodo.DeTurno -> {
                    val t = turnos.obtener(periodo.turnoId) ?: return@withContext AppResult.Err(AppError.NoEncontrado)
                    val hasta = maxOf(t.cerradoEn ?: clock.now(), t.abiertoEn.plusSeconds(1))
                    AppResult.Ok(GraficosPeriodo(periodo, Estadisticas.serie(ventas.deTurno(t.id).validas(), t.abiertoEn, hasta, zone), turno = t))
                }
                is Periodo.Rango -> AppResult.Ok(
                    GraficosPeriodo(
                        periodo = periodo,
                        serie = Estadisticas.serie(ventas.entre(periodo.desde, periodo.hasta).validas(), periodo.desde, periodo.hasta, zone),
                        sinTurnos = pedidoTurno,
                    ),
                )
            }
        }
}

/**
 * Inventario por categorías (existencias actuales), Métodos de pago y Top 3 de los últimos
 * [ResumenGeneral.dias] días (incluido hoy). No depende del selector de período.
 */
class ObtenerResumenGeneral @Inject constructor(
    private val ventas: VentaRepository,
    private val productos: ProductoRepository,
    private val insumos: InsumoRepository,
    private val servicios: ServicioRepository,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** 0.30.0 (F1): consulta de 30 días y cinco agregaciones: fuera del hilo de UI, igual que [ObtenerGraficosPeriodo]. */
    suspend operator fun invoke(zone: ZoneId = ZoneId.systemDefault()): ResumenGeneral = withContext(io) {
        val manana = clock.now().atZone(zone).toLocalDate().plusDays(1) // 0.21.6: por fecha (cambio de hora a las 00:00)
        val lista = ventas.entre(manana.minusDays(DIAS.toLong()).atStartOfDay(zone).toInstant(), manana.atStartOfDay(zone).toInstant()).validas()
        val inventario = productos.observarTodos().first()
        // 1.0.1: los insumos con precio de venta cuentan como productos en el Top 3.
        val ins = insumos.observarTodos().first()
        ResumenGeneral(
            categorias = Estadisticas.distribucionCategorias(inventario, ins),
            metodosPago = Estadisticas.distribucionMetodos(lista),
            top3 = Estadisticas.top3(lista, inventario, ins),
            dias = DIAS,
            servicios = Estadisticas.topServicios(lista, servicios.observarTodos().first()),
            empleados = Estadisticas.topEmpleados(lista),
            clientes = Estadisticas.topClientes(lista),
        )
    }

    companion object {
        const val DIAS = 30
    }
}
