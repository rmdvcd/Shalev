package cu.shalev.domain.service

import cu.shalev.domain.model.nombreCompleto
import cu.shalev.domain.model.comoProducto
import cu.shalev.domain.model.IdArticulo
import cu.shalev.domain.model.TopServicios
import cu.shalev.domain.model.Servicio
import cu.shalev.domain.model.ClaseArticulo
import cu.shalev.core.money.Cup
import cu.shalev.domain.model.Granularidad
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Periodo
import cu.shalev.domain.model.PeriodoPreset
import cu.shalev.domain.model.Porcion
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.PuntoSerie
import cu.shalev.domain.model.Serie
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.Top3
import cu.shalev.domain.model.TopItem
import cu.shalev.domain.model.TopPersona
import cu.shalev.domain.model.Venta
import cu.shalev.domain.model.validas
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Cálculos puros de Inicio (gráficos y Top 3). Sin E/S: testeables y reutilizables en exportaciones. */
object Estadisticas {

    /**
     * 0.21.6: los límites se calculan con fechas (LocalDate.atStartOfDay), no sumando días a un ZonedDateTime. En Cuba
     * el horario de verano empieza a las 00:00: ese día empieza a la 01:00 y, con plusDays, todos los días siguientes
     * «empezaban» a la 01:00.
     */
    fun rango(preset: PeriodoPreset, ahora: Instant, zone: ZoneId): Periodo.Rango {
        val hoy = ahora.atZone(zone).toLocalDate()
        val (desde, hasta) = when (preset) {
            PeriodoPreset.HOY -> hoy to hoy.plusDays(1)
            PeriodoPreset.SEMANA -> hoy.minusDays(6) to hoy.plusDays(1)
            PeriodoPreset.MES -> hoy.withDayOfMonth(1) to hoy.withDayOfMonth(1).plusMonths(1)
            PeriodoPreset.ANIO -> hoy.with(TemporalAdjusters.firstDayOfYear()) to hoy.with(TemporalAdjusters.firstDayOfYear()).plusYears(1)
        }
        return Periodo.Rango(desde.atStartOfDay(zone).toInstant(), hasta.atStartOfDay(zone).toInstant())
    }

    fun granularidad(desde: Instant, hasta: Instant): Granularidad {
        val d = Duration.between(desde, hasta)
        return when {
            d <= Duration.ofHours(48) -> Granularidad.HORA
            d <= Duration.ofDays(62) -> Granularidad.DIA
            else -> Granularidad.MES
        }
    }

    /** Serie continua (incluye cubos vacíos) para Ventas (barras) y Ganancia Neta (área). */
    fun serie(ventas: List<Venta>, desde: Instant, hasta: Instant, zone: ZoneId): Serie {
        val g = granularidad(desde, hasta)
        fun cubo(i: Instant): ZonedDateTime = i.atZone(zone).let {
            when (g) {
                Granularidad.HORA -> it.truncatedTo(ChronoUnit.HOURS)
                Granularidad.DIA -> it.toLocalDate().atStartOfDay(zone)
                Granularidad.MES -> it.toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
            }
        }
        // 0.21.6: el cubo siguiente sale de la fecha, igual que [cubo] (ver [rango]): con plusDays, tras el cambio de
        // hora de Cuba (00:00 → 01:00) los cubos quedaban a la 01:00 y las ventas de los días siguientes no se sumaban.
        fun siguiente(z: ZonedDateTime) = when (g) {
            Granularidad.HORA -> z.plusHours(1)
            Granularidad.DIA -> z.toLocalDate().plusDays(1).atStartOfDay(zone)
            Granularidad.MES -> z.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(zone)
        }
        val agregados = ventas.filter { it.fecha >= desde && it.fecha < hasta }
            .groupBy { cubo(it.fecha).toInstant() }
            .mapValues { (_, vs) -> vs.fold(Cup.ZERO to Cup.ZERO) { (t, c), v -> (t + v.total) to (c + v.costoTotal) } }
        val puntos = buildList {
            var z = cubo(desde)
            while (z.toInstant() < hasta) {
                val (t, c) = agregados[z.toInstant()] ?: (Cup.ZERO to Cup.ZERO)
                add(PuntoSerie(z.toInstant(), t, c))
                z = siguiente(z)
            }
        }
        return Serie(g, puntos)
    }

    /**
     * Dona Inventario: existencias por categoría (artículos activos; P26: los Elaborados no tienen existencias).
     *
     * 0.27.0 (N1): los insumos cuentan en la porción «Insumos», cada uno en su propia medida (kg, L, u…). Para poder
     * sumarlos, los valores van en **milésimas** (un producto con 3 unidades aporta 3000; un insumo con 2,5 kg, 2500).
     * La pantalla los muestra como números sin unidad («12,5»).
     */
    fun distribucionCategorias(productos: List<Producto>, insumos: List<Insumo> = emptyList()): List<Porcion> {
        val porCategoria = productos.filterNot { it.eliminado || it.esElaborado }.groupBy { it.categoria }
            .mapValues { (_, ps) -> ps.sumOf { it.cantidad.coerceAtLeast(0) * MILESIMAS } }.toMutableMap()
        val enInsumos = insumos.sumOf { it.cantidad.milesimas.coerceAtLeast(0) }
        if (enInsumos > 0) porCategoria[CATEGORIA_INSUMOS] = (porCategoria[CATEGORIA_INSUMOS] ?: 0L) + enInsumos
        return porciones(porCategoria)
    }

    /** 0.27.0 (N1): nombre de la porción de los insumos en la dona Inventario. */
    const val CATEGORIA_INSUMOS = "Insumos"
    private const val MILESIMAS = 1000L

    /** Dona Métodos de pago: importe cobrado por método. */
    fun distribucionMetodos(ventas: List<Venta>): List<Porcion> =
        porciones(ventas.groupBy { it.metodoPago }.entries.associate { (m, vs) -> etiqueta(m) to vs.sumOf { it.total.centavos } })

    fun etiqueta(m: MetodoPago) = when (m) {
        MetodoPago.EFECTIVO -> "Efectivo"
        MetodoPago.TRANSFERENCIA -> "Transferencia"
    }

    /** Nombre del tipo de movimiento para personas (Registros, turno, textos compartidos). */
    fun etiqueta(t: TipoMovimiento) = when (t) {
        TipoMovimiento.ALTA -> "Alta"
        TipoMovimiento.AJUSTE -> "Ajuste"
        TipoMovimiento.VENTA -> "Venta"
        TipoMovimiento.PRODUCCION -> "Producción"
        TipoMovimiento.CONSUMO -> "Consumo"
        TipoMovimiento.BAJA -> "Baja"
        TipoMovimiento.ANULACION -> "Venta anulada"
    }

    private fun porciones(valores: Map<String, Long>): List<Porcion> {
        val positivos = valores.filterValues { it > 0 }
        val total = positivos.values.sum()
        if (total == 0L) return emptyList()
        return positivos.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .map { Porcion(it.key, it.value, it.value.toDouble() / total) }
    }

    /**
     * Top 3 (Shalev.txt). Vacío si no hay ventas en el período ("no se muestran si no hay datos").
     * 1.0.1: los **insumos con precio de venta** cuentan como productos para las estadísticas; van con id negativo
     * ([IdArticulo.deInsumo]) para no confundirlos con un producto de id igual, y los de receta (sin precio de
     * venta) no entran. Una línea vendida de insumo cuenta siempre (al venderlo tenía precio de venta).
     *  - Más vendido: unidades ↓; empate → artículo más RECIENTE.
     *  - Lento movimiento: unidades ↑ entre TODOS los artículos activos (0 si no se vendió); empate → más ANTIGUO.
     *  - Rentabilidad: ganancia total (precio − costo congelados × unidades) ↓, solo ganancia > 0;
     *    empate → mayor margen, luego más reciente.
     */
    fun top3(ventas: List<Venta>, productos: List<Producto>, insumos: List<Insumo> = emptyList()): Top3 {
        // P29: solo artículos del inventario (los servicios tienen sus propios indicadores).
        val detalles = ventas.flatMap { it.detalles }.mapNotNull { d ->
            when (d.clase) {
                ClaseArticulo.PRODUCTO -> d to d.productoId
                ClaseArticulo.INSUMO -> d to IdArticulo.deInsumo(d.productoId)
                ClaseArticulo.SERVICIO -> null
            }
        }
        if (detalles.isEmpty()) return Top3(emptyList(), emptyList(), emptyList())
        val catalogo = productos.filterNot { it.eliminado || it.esInsumo } +
            insumos.filter { it.precioVenta != null }.map { it.comoProducto() }
        val creado = catalogo.associate { it.id to it.creadoEn }
        fun creadoEn(id: Long) = creado[id] ?: Instant.EPOCH

        val vendidos = detalles.groupBy { it.second }.map { (id, pares) ->
            val ds = pares.map { it.first }
            TopItem(
                productoId = id,
                nombre = catalogo.firstOrNull { it.id == id }?.nombreCompleto ?: ds.last().nombre,
                unidades = ds.sumOf { it.cantidad },
                ingresos = ds.fold(Cup.ZERO) { a, d -> a + d.subtotal },
                ganancia = ds.fold(Cup.ZERO) { a, d -> a + d.ganancia },
            )
        }
        val porId = vendidos.associateBy { it.productoId }

        val mas = vendidos.sortedWith(compareByDescending<TopItem> { it.unidades }.thenByDescending { creadoEn(it.productoId) }).take(3)

        val lento = catalogo.filterNot { it.eliminado }
            .map { p -> porId[p.id] ?: TopItem(p.id, p.nombreCompleto, 0, Cup.ZERO, Cup.ZERO) }
            .sortedWith(compareBy<TopItem> { it.unidades }.thenBy { creadoEn(it.productoId) })
            .take(3)

        val renta = vendidos.filter { it.ganancia > Cup.ZERO }
            .sortedWith(
                compareByDescending<TopItem> { it.ganancia }
                    .thenByDescending { if (it.ingresos.centavos == 0L) 0.0 else it.ganancia.centavos.toDouble() / it.ingresos.centavos }
                    .thenByDescending { creadoEn(it.productoId) },
            ).take(3)

        return Top3(mas, lento, renta)
    }

    /**
     * P29: indicadores de servicios con las mismas reglas que el Top 3 de productos. Vacío si en el período no se
     * vendió ningún servicio.
     *  - Top ventas: veces ↓; empate → más reciente.
     *  - Menos vendidos: veces ↑ entre TODOS los servicios activos (0 si no se vendió); empate → más antiguo.
     */
    fun topServicios(ventas: List<Venta>, servicios: List<Servicio>): TopServicios {
        val detalles = ventas.flatMap { it.detalles }.filter { it.clase == ClaseArticulo.SERVICIO }
        if (detalles.isEmpty()) return TopServicios.VACIO
        val creado = servicios.associate { it.id to it.creadoEn }
        fun creadoEn(id: Long) = creado[id] ?: Instant.EPOCH
        val vendidos = detalles.groupBy { it.productoId }.map { (id, ds) ->
            TopItem(
                productoId = id,
                nombre = servicios.firstOrNull { it.id == id }?.nombreCompleto ?: ds.last().nombre,
                unidades = ds.sumOf { it.cantidad },
                ingresos = ds.fold(Cup.ZERO) { a, d -> a + d.subtotal },
                ganancia = ds.fold(Cup.ZERO) { a, d -> a + d.ganancia },
            )
        }
        val porId = vendidos.associateBy { it.productoId }
        val mas = vendidos.sortedWith(compareByDescending<TopItem> { it.unidades }.thenByDescending { creadoEn(it.productoId) }).take(3)
        val menos = servicios.filterNot { it.eliminado }
            .map { s -> porId[s.id] ?: TopItem(s.id, s.nombreCompleto, 0, Cup.ZERO, Cup.ZERO) }
            .sortedWith(compareBy<TopItem> { it.unidades }.thenBy { creadoEn(it.productoId) })
            .take(3)
        return TopServicios(mas, menos)
    }

    /**
     * Top 3 de empleados: importe total vendido por vendedor (nombre congelado de la venta) ↓;
     * empate → alfabético. Solo ventas válidas; vacío si no hay ventas.
     */
    fun topEmpleados(ventas: List<Venta>): List<TopPersona> {
        val validas = ventas.validas().filter { it.vendedor.isNotBlank() }
        if (validas.isEmpty()) return emptyList()
        return validas.groupBy { it.vendedor }.map { (nombre, vs) ->
            TopPersona(nombre, vs.fold(Cup.ZERO) { a, v -> a + v.total }, vs.size)
        }.sortedWith(compareByDescending<TopPersona> { it.importe }.thenBy { it.nombre }).take(3)
    }

    /**
     * Top 3 de clientes: importe total comprado por cliente de transferencia (nombre + CI) ↓;
     * empate → alfabético. El efectivo no registra cliente y no cuenta; vacío si no hay transferencias.
     */
    fun topClientes(ventas: List<Venta>): List<TopPersona> {
        val txs = ventas.validas().mapNotNull { it.transaccion }
        if (txs.isEmpty()) return emptyList()
        return txs.groupBy { it.cliente.ci to it.cliente.nombreApellidos }.map { (_, ts) ->
            TopPersona(ts.last().cliente.nombreApellidos, ts.fold(Cup.ZERO) { a, t -> a + t.importe }, ts.size)
        }.sortedWith(compareByDescending<TopPersona> { it.importe }.thenBy { it.nombre }).take(3)
    }
}
