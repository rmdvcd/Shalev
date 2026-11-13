package cu.shalev.domain.model

import cu.shalev.core.money.Cup
import cu.shalev.core.money.sumOfCup
import java.time.LocalDate

/**
 * Filtro por fecha de Registros. Los días son de calendario en la zona del teléfono (Cuba) y el rango
 * es inclusivo: «Hoy» = de las 00:00 de hoy a las 00:00 de mañana.
 */
enum class PeriodoRegistro { TODO, HOY, SIETE_DIAS, ESTE_MES, PERSONALIZADO }

/**
 * Filtro de una tabla de Registros (Shalev.txt: buscador y filtro por fecha en todas; por importe en
 * Ventas y Transferencias, no en Movimientos). [desde]/[hasta] solo cuentan con [PeriodoRegistro.PERSONALIZADO];
 * cualquiera de los dos puede quedar abierto.
 */
data class FiltroRegistros(
    val texto: String = "",
    val periodo: PeriodoRegistro = PeriodoRegistro.TODO,
    val desde: LocalDate? = null,
    val hasta: LocalDate? = null,
    val importeMin: Cup? = null,
    val importeMax: Cup? = null,
    /** 0.20.0 (H1): solo las ventas, servicios y transferencias de este vendedor (null = todos). */
    val vendedor: String? = null,
) {
    val porFecha: Boolean get() = periodo != PeriodoRegistro.TODO && !(periodo == PeriodoRegistro.PERSONALIZADO && desde == null && hasta == null)
    val porImporte: Boolean get() = importeMin != null || importeMax != null

    /** Nº de filtros activos (distintivo del botón Filtrar); el texto del buscador no cuenta. */
    val activos: Int get() = (if (porFecha) 1 else 0) + (if (porImporte) 1 else 0) + (if (vendedor != null) 1 else 0)

    /** Sin fecha ni importe, conservando lo escrito en el buscador. */
    fun sinFiltros(): FiltroRegistros = FiltroRegistros(texto = texto)
}

/** Motivo por el que un filtro no se puede aplicar (la UI lo explica junto al campo). */
enum class ErrorFiltroRegistro { FECHAS_INVERTIDAS, IMPORTES_INVERTIDOS }

/** Movimiento con el símbolo de la unidad del insumo ("kg"), vacío para productos o insumos ya eliminados. */
data class ItemMovimiento(val movimiento: MovimientoInventario, val simbolo: String = "")

/**
 * Lo que muestra una tabla de Registros ya filtrada, del más reciente al más antiguo.
 * [cantidad] y los totales se calculan sobre lo filtrado (lo que se ve y lo que se comparte).
 */
sealed interface VistaRegistro {
    val cantidad: Int

    data class Ventas(val items: List<Venta>) : VistaRegistro {
        override val cantidad: Int get() = items.size
        val total: Cup get() = items.sumOfCup { it.total }
    }

    data class Transferencias(val items: List<Transaccion>) : VistaRegistro {
        override val cantidad: Int get() = items.size
        val total: Cup get() = items.sumOfCup { it.importe }
    }

    data class Movimientos(val items: List<ItemMovimiento>) : VistaRegistro {
        override val cantidad: Int get() = items.size
    }
}
