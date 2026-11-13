package cu.shalev.domain.model

import cu.shalev.core.money.Cup
import java.time.Instant

/** Período de los gráficos Ventas y Ganancia bruta. */
sealed interface Periodo {
    data class DeTurno(val turnoId: Long) : Periodo
    /** [desde] inclusive, [hasta] exclusivo. */
    data class Rango(val desde: Instant, val hasta: Instant) : Periodo
}

enum class PeriodoPreset { HOY, SEMANA, MES, ANIO }

/**
 * Opciones del selector de período de Inicio. [TURNO] = turno abierto o, si no hay, el último cerrado
 * (Shalev.txt: "si el turno está cerrado mostrar las del cerrado"). Es la opción por defecto.
 */
enum class OpcionPeriodo { TURNO, HOY, SEMANA, MES, ANIO }

/** 0.21.0 (C1): SIN_EXISTENCIA = productos o insumos con 0 o menos (antes «Existencias negativas», H6). */
/** 0.24.0: [NOMBRE_REPETIDO] = productos con el mismo nombre que no se distinguen por la descripción ([Identificacion]). */
enum class TipoAlerta { STOCK_BAJO, STOCK_CRITICO, INSUMO_BAJO, INSUMO_CRITICO, PROXIMO_A_CADUCAR, SIN_EXISTENCIA, NOMBRE_REPETIDO }

/** Contadores de Inicio. Un contador en 0 se oculta en la UI. */
data class ConteoAlertas(
    val stockBajo: Int = 0,
    val stockCritico: Int = 0,
    val insumoBajo: Int = 0,
    val insumoCritico: Int = 0,
    val proximosACaducar: Int = 0,
    val sinExistencia: Int = 0,
    val nombreRepetido: Int = 0,
) {
    fun de(tipo: TipoAlerta) = when (tipo) {
        TipoAlerta.STOCK_BAJO -> stockBajo
        TipoAlerta.STOCK_CRITICO -> stockCritico
        TipoAlerta.INSUMO_BAJO -> insumoBajo
        TipoAlerta.INSUMO_CRITICO -> insumoCritico
        TipoAlerta.PROXIMO_A_CADUCAR -> proximosACaducar
        TipoAlerta.SIN_EXISTENCIA -> sinExistencia
        TipoAlerta.NOMBRE_REPETIDO -> nombreRepetido
    }
}

/** 0.21.0 (C1): SIN_EXISTENCIA = cantidad ≤ 0 (no cuenta como CRITICO). */
enum class NivelStock { NORMAL, BAJO, CRITICO, SIN_EXISTENCIA }

enum class Granularidad { HORA, DIA, MES }

/** Un punto de las series Ventas / Ganancia bruta. */
data class PuntoSerie(val inicio: Instant, val ventas: Cup, val costo: Cup) {
    val ganancia: Cup get() = ventas - costo
}

data class Serie(val granularidad: Granularidad, val puntos: List<PuntoSerie>)

/** Porción de un gráfico de dona. */
data class Porcion(val etiqueta: String, val valor: Long, val fraccion: Double)

data class TopItem(val productoId: Long, val nombre: String, val unidades: Long, val ingresos: Cup, val ganancia: Cup)

/** Una fila de los tops de personas de Inicio (empleados y clientes): nombre, importe total y nº de operaciones. */
data class TopPersona(val nombre: String, val importe: Cup, val operaciones: Int)

data class Top3(val masVendidos: List<TopItem>, val lentoMovimiento: List<TopItem>, val rentabilidad: List<TopItem>) {
    val vacio: Boolean get() = masVendidos.isEmpty() && lentoMovimiento.isEmpty() && rentabilidad.isEmpty()

    companion object {
        val VACIO = Top3(emptyList(), emptyList(), emptyList())
    }
}

/**
 * Gráficos Ventas (barras) y Ganancia bruta (área costo vs venta) del período elegido.
 * [turno] = turno mostrado cuando el período es de turno; [sinTurnos] = se pidió TURNO pero aún no hay
 * ninguno, así que se muestra "hoy".
 */
data class GraficosPeriodo(
    val periodo: Periodo,
    val serie: Serie,
    val turno: Turno? = null,
    val sinTurnos: Boolean = false,
) {
    val totalVentas: Cup get() = serie.puntos.fold(Cup.ZERO) { a, p -> a + p.ventas }
    val totalCosto: Cup get() = serie.puntos.fold(Cup.ZERO) { a, p -> a + p.costo }
    val ganancia: Cup get() = totalVentas - totalCosto

    /** Margen = ganancia / venta, en puntos básicos (2500 = 25 %). Null si no hubo ventas. */
    val margen: Int? get() = if (totalVentas.centavos <= 0) null else (ganancia.centavos * 10_000 / totalVentas.centavos).toInt()

    val vacio: Boolean get() = serie.puntos.all { it.ventas == Cup.ZERO && it.costo == Cup.ZERO }
}

/**
 * Parte de Inicio que NO depende del selector de período (Shalev.txt: el selector afecta solo a Ventas y
 * Ganancia bruta): Inventario (existencias actuales), Métodos de pago y Top 3 de los últimos [dias] días.
 */
data class ResumenGeneral(
    val categorias: List<Porcion>,
    val metodosPago: List<Porcion>,
    val top3: Top3,
    val dias: Int,
    /** P29: indicadores propios de los servicios. */
    val servicios: TopServicios = TopServicios.VACIO,
    /** Empleados con mayor importe vendido y clientes por transferencia con mayor importe comprado. */
    val empleados: List<TopPersona> = emptyList(),
    val clientes: List<TopPersona> = emptyList(),
) {
    val vacio: Boolean get() = categorias.isEmpty() && metodosPago.isEmpty() && top3.vacio && servicios.vacio &&
        empleados.isEmpty() && clientes.isEmpty()
}

/** P29: Top ventas y Menos vendidos de los servicios ([TopItem.productoId] = id del servicio; unidades = veces). */
data class TopServicios(val masVendidos: List<TopItem>, val menosVendidos: List<TopItem>) {
    val vacio: Boolean get() = masVendidos.isEmpty() && menosVendidos.isEmpty()

    companion object {
        val VACIO = TopServicios(emptyList(), emptyList())
    }
}
