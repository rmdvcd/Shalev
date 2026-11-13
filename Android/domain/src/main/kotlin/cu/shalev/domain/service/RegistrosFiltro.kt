package cu.shalev.domain.service

import cu.shalev.domain.model.ErrorFiltroRegistro
import cu.shalev.domain.model.FiltroRegistros
import cu.shalev.domain.model.ItemMovimiento
import cu.shalev.domain.model.PeriodoRegistro
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.FiltroRegistro
import cu.shalev.domain.usecase.TipoRegistro
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reglas puras del filtro de Registros.
 *
 * Fecha e importe van a la consulta de [cu.shalev.domain.repository.RegistroRepository] (índices de la BD,
 * sin SQL nuevo). El texto se filtra aquí con el mismo buscador que Inventario ([InventarioFiltro.plano]:
 * sin mayúsculas ni tildes, «cafe» encuentra «Café») y sobre más campos que el LIKE de la BD.
 */
object RegistrosFiltro {

    /** Días inclusivos [desde, hasta] del filtro; null = abierto por ese lado. */
    fun rango(f: FiltroRegistros, hoy: LocalDate): Pair<LocalDate?, LocalDate?> = when (f.periodo) {
        PeriodoRegistro.TODO -> null to null
        PeriodoRegistro.HOY -> hoy to hoy
        PeriodoRegistro.SIETE_DIAS -> hoy.minusDays(6) to hoy
        PeriodoRegistro.ESTE_MES -> hoy.withDayOfMonth(1) to hoy
        PeriodoRegistro.PERSONALIZADO -> f.desde to f.hasta
    }

    /**
     * Consulta al repositorio: [desde] a las 00:00 y [hasta] exclusivo a las 00:00 del día siguiente (así
     * «hasta el 30» incluye todo el día 30). El importe no aplica a Movimientos (Shalev.txt).
     */
    fun consulta(f: FiltroRegistros, tipo: TipoRegistro, hoy: LocalDate, zona: ZoneId): FiltroRegistro {
        val (d, h) = rango(f, hoy)
        val conImporte = tipo != TipoRegistro.MOVIMIENTOS
        return FiltroRegistro(
            desde = d?.atStartOfDay(zona)?.toInstant(),
            hasta = h?.plusDays(1)?.atStartOfDay(zona)?.toInstant(),
            importeMin = f.importeMin.takeIf { conImporte },
            importeMax = f.importeMax.takeIf { conImporte },
            texto = null,
        )
    }

    /** 0.20.0 (H1): sin vendedor elegido, todos; si no, exactamente ese nombre (sin distinguir mayúsculas). */
    fun deVendedor(vendedor: String, f: FiltroRegistros): Boolean =
        f.vendedor == null || vendedor.trim().equals(f.vendedor.trim(), ignoreCase = true)

    fun validar(f: FiltroRegistros): ErrorFiltroRegistro? = when {
        f.periodo == PeriodoRegistro.PERSONALIZADO && f.desde != null && f.hasta != null && f.desde > f.hasta ->
            ErrorFiltroRegistro.FECHAS_INVERTIDAS
        f.importeMin != null && f.importeMax != null && f.importeMin > f.importeMax -> ErrorFiltroRegistro.IMPORTES_INVERTIDOS
        else -> null
    }

    /**
     * Venta: artículo, categoría, método de pago o nº de transferencia / cliente. Sin búsqueda por el id interno
     * (P25/P26: las ventas se identifican por fecha; el id nunca se muestra, así que tampoco se busca).
     */
    fun coincide(v: Venta, texto: String): Boolean {
        val q = InventarioFiltro.plano(texto)
        if (q.isEmpty()) return true
        val campos = v.detalles.flatMap { listOf(it.nombre, it.categoria) } + Estadisticas.etiqueta(v.metodoPago) +
            listOfNotNull(v.transaccion?.numero, v.transaccion?.cliente?.nombreApellidos)
        return campos.any { InventarioFiltro.plano(it).contains(q) }
    }

    /** Transferencia: nº de transacción (del banco), cliente (nombre, CI, teléfono). Sin el id interno de la venta. */
    fun coincide(t: Transaccion, texto: String): Boolean {
        val q = InventarioFiltro.plano(texto)
        if (q.isEmpty()) return true
        return listOf(t.numero, t.cliente.nombreApellidos, t.cliente.ci, t.cliente.telefono)
            .any { InventarioFiltro.plano(it).contains(q) }
    }

    /** Movimiento: nombre, nota, tipo («consumo», «producción»…) o «producto» / «insumo». */
    fun coincide(i: ItemMovimiento, texto: String): Boolean {
        val q = InventarioFiltro.plano(texto)
        if (q.isEmpty()) return true
        val m = i.movimiento
        val entidad = if (m.entidad == TipoEntidad.PRODUCTO) "Producto" else "Insumo"
        return listOfNotNull(m.nombre, m.nota, Estadisticas.etiqueta(m.tipo), entidad).any { InventarioFiltro.plano(it).contains(q) }
    }
}
