package cu.shalev.app.registros

import cu.shalev.app.common.FechasUi
import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
import cu.shalev.domain.model.ErrorFiltroRegistro
import cu.shalev.domain.model.FiltroRegistros
import cu.shalev.domain.model.ItemMovimiento
import cu.shalev.domain.model.PeriodoRegistro
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Venta
import cu.shalev.domain.model.VistaRegistro
import cu.shalev.domain.service.RegistrosFiltro
import cu.shalev.domain.usecase.TipoRegistro
import java.time.LocalDate
import java.time.ZoneId

/** Textos de Registros en un solo sitio (UI y tests). */
object TextosRegistros {
    const val TITULO = "Registros"
    const val FILTRAR = "Filtrar por fecha e importe"
    const val FILTRAR_FECHA = "Filtrar por fecha"
    /** 0.20.0 (H1). */
    const val VENDEDOR = "Vendedor"
    const val TODOS_VENDEDORES = "Todos"
    const val SIN_RESULTADOS_TITULO = "Sin resultados"
    const val SIN_RESULTADOS_DETALLE = "Prueba otra búsqueda o quita los filtros."
    const val QUITAR_FILTROS = "Quitar filtros"
    const val ERROR_CARGA = "No se pudo cargar el registro."
    const val VENTA_NO_DISPONIBLE = "No se encontró la venta de esta transferencia."
    const val VER_VENTA = "Ver la venta"
    const val FECHAS_INVERTIDAS = "La fecha «Desde» no puede ser posterior a «Hasta»."
    const val IMPORTES_INVERTIDOS = "El importe mínimo no puede ser mayor que el máximo."
    const val IMPORTE_INVALIDO = "Escribe un importe válido, por ejemplo 1450 o 1,450.00."
    const val EXPORTAR = "Exportar PDF o Excel"
    const val EXPORTANDO = "Creando el archivo…"
    const val ERROR_EXPORTAR = "No se pudo crear el archivo. Inténtalo de nuevo."
    const val AVISO_DATOS_CLIENTES = "Incluye datos de clientes: compártelo con cuidado."
    /** Máximo de filas en «Compartir tabla» (Android rechaza textos enormes); el resto se resume. */

    fun vacioTitulo(t: TipoRegistro) = when (t) {
        TipoRegistro.VENTAS -> "Aún no hay ventas"
        TipoRegistro.SERVICIOS -> "Aún no hay servicios vendidos"
        TipoRegistro.TRANSACCIONES -> "Aún no hay transferencias"
        TipoRegistro.MOVIMIENTOS -> "Aún no hay movimientos"
    }

    fun vacioDetalle(t: TipoRegistro) = when (t) {
        TipoRegistro.VENTAS -> "Las ventas de cada turno aparecen aquí."
        TipoRegistro.SERVICIOS -> "Las ventas de servicios aparecen aquí."
        TipoRegistro.TRANSACCIONES -> "Los pagos por Transfermóvil aparecen aquí."
        TipoRegistro.MOVIMIENTOS -> "Entradas y salidas de existencias aparecen aquí."
    }

    fun buscar(t: TipoRegistro) = when (t) {
        TipoRegistro.VENTAS -> "Artículo, categoría o cliente"
        TipoRegistro.SERVICIOS -> "Servicio, tipo o cliente"
        TipoRegistro.TRANSACCIONES -> "Nº de transacción, cliente, CI o teléfono"
        TipoRegistro.MOVIMIENTOS -> "Nombre, tipo o nota"
    }

    fun tituloExportar(t: TipoRegistro) = when (t) {
        TipoRegistro.VENTAS -> "Exportar ventas"
        TipoRegistro.SERVICIOS -> "Exportar servicios"
        TipoRegistro.TRANSACCIONES -> "Exportar transferencias"
        TipoRegistro.MOVIMIENTOS -> "Exportar movimientos"
    }

    private fun elementos(t: TipoRegistro, n: Int) = when (t) {
        TipoRegistro.VENTAS -> if (n == 1) "1 venta" else "$n ventas"
        TipoRegistro.SERVICIOS -> if (n == 1) "1 servicio" else "$n servicios"
        TipoRegistro.TRANSACCIONES -> if (n == 1) "1 transferencia" else "$n transferencias"
        TipoRegistro.MOVIMIENTOS -> if (n == 1) "1 movimiento" else "$n movimientos"
    }

    /** "Se exportará lo que ves: 12 ventas (esta semana · búsqueda «pan»)." */
    fun alcanceExportar(t: TipoRegistro, n: Int, detalle: String?) =
        "Lo que ves: ${elementos(t, n)}${detalle?.let { " ($it)" }.orEmpty()}"

    /** 0.31.0 (P3): «3 ventas seleccionadas» (la exportación sale solo de lo marcado). */
    fun seleccionados(t: TipoRegistro, n: Int) = "${elementos(t, n)} seleccionados"

    fun guardado(nombre: String) = "Guardado: $nombre"

    /** "Shalev_ventas_2026-09-30.pdf". Sin datos personales en el nombre. */
    fun nombreArchivo(t: TipoRegistro, fecha: java.time.LocalDate, extension: String): String {
        val que = when (t) {
            TipoRegistro.VENTAS -> "ventas"
            TipoRegistro.SERVICIOS -> "servicios"
            TipoRegistro.TRANSACCIONES -> "transferencias"
            TipoRegistro.MOVIMIENTOS -> "movimientos"
        }
        return "Shalev_${que}_$fecha.$extension"
    }

    fun asunto(t: TipoRegistro) = when (t) {
        TipoRegistro.VENTAS -> "Shalev · Ventas"
        TipoRegistro.SERVICIOS -> "Shalev · Servicios vendidos"
        TipoRegistro.TRANSACCIONES -> "Shalev · Transferencias recibidas"
        TipoRegistro.MOVIMIENTOS -> "Shalev · Movimientos de inventario e insumos"
    }

    private fun plural(n: Int, uno: String, varios: String) = if (n == 1) "1 $uno" else "$n $varios"

    /** Línea bajo el buscador: «3 ventas · 650.00 CUP», «1 transferencia · 50.00 CUP», «5 movimientos». */
    fun resumen(v: VistaRegistro): String = when (v) {
        is VistaRegistro.Ventas -> "${plural(v.cantidad, "venta", "ventas")} · ${Money.format(v.total)}"
        is VistaRegistro.Transferencias -> "${plural(v.cantidad, "transferencia", "transferencias")} · ${Money.format(v.total)}"
        is VistaRegistro.Movimientos -> plural(v.cantidad, "movimiento", "movimientos")
    }
}

/** Pestañas de Registros (chips). Turnos es el registro del Prompt 7: sigue en la misma pantalla. */
enum class PestanaRegistros(val etiqueta: String, val tipo: TipoRegistro?) {
    VENTAS("Ventas", TipoRegistro.VENTAS),
    SERVICIOS("Servicios", TipoRegistro.SERVICIOS), // P29
    TRANSFERENCIAS("Transferencias", TipoRegistro.TRANSACCIONES),
    MOVIMIENTOS("Movimientos", TipoRegistro.MOVIMIENTOS),
    /** 0.27.0 (N2): clientes fijos (sin filtro ni exportar). */
    CLIENTES("Clientes", null),
    TURNOS("Turnos", null),
}

/**
 * 0.21.0 (C12): pestañas según los módulos del negocio. Ventas solo con Ventas; Servicios solo con Servicios;
 * Movimientos (de inventario) con Ventas o Inventario. Transferencias y Turnos, siempre.
 */
fun pestanasVisibles(p: cu.shalev.domain.model.PermisosApp): List<PestanaRegistros> = PestanaRegistros.entries.filter {
    when (it) {
        PestanaRegistros.VENTAS -> p.verVentas
        PestanaRegistros.SERVICIOS -> p.verServicios
        PestanaRegistros.MOVIMIENTOS -> p.verInventario
        PestanaRegistros.TRANSFERENCIAS, PestanaRegistros.CLIENTES, PestanaRegistros.TURNOS -> true
    }
}

object RegistrosTags {
    const val LISTA = "registros_lista"
    const val REINTENTAR = "registros_reintentar"
    const val BUSCAR = "registros_buscar"
    const val FILTRO = "registros_filtro"
    const val COMPARTIR = "registros_compartir"
    const val EXPORTAR = "registros_exportar"
    const val EXPORTANDO = "registros_exportando"
    fun formato(f: cu.shalev.domain.service.FormatoExport) = "registros_formato_${f.name}"
    fun guardar(f: cu.shalev.domain.service.FormatoExport) = "registros_guardar_${f.name}"
    fun enviar(f: cu.shalev.domain.service.FormatoExport) = "registros_enviar_${f.name}"
    const val RESUMEN = "registros_resumen"
    const val QUITAR_FILTROS = "registros_quitar_filtros"
    const val FICHA = "registros_ficha"
    const val FICHA_VER_VENTA = "registros_ficha_ver_venta"
    const val FILTRO_MIN = "registros_filtro_min"
    const val FILTRO_MAX = "registros_filtro_max"
    const val FILTRO_DESDE = "registros_filtro_desde"
    const val FILTRO_HASTA = "registros_filtro_hasta"
    const val FILTRO_APLICAR = "registros_filtro_aplicar"
    const val FILTRO_PERIODO = "registros_filtro_periodo"
    const val FILTRO_VENDEDOR = "registros_filtro_vendedor"
    const val PERIODO_TODOS = "registros_periodo_todos"
    const val VENDEDOR_TODOS = "registros_vendedor_todos"
    fun pestana(p: PestanaRegistros) = "registros_pestana_${p.name}"
    fun periodo(p: PeriodoRegistro) = "registros_periodo_${p.name}"
    fun vendedor(nombre: String) = "registros_vendedor_$nombre"
    fun turno(id: Long) = "registros_turno_$id"
    fun venta(id: Long) = "registros_venta_$id"
    fun transferencia(id: Long) = "registros_transferencia_$id"
    fun movimiento(id: Long) = "registros_movimiento_$id"
    const val SELECCION = "registros_seleccion"
    const val SELECCIONAR_TODO = "registros_seleccionar_todo"
    const val EXPORTAR_SELECCION = "registros_exportar_seleccion"
    const val LIMPIAR_SELECCION = "registros_limpiar_seleccion"
    fun check(id: Long) = "registros_check_$id"
}

fun etiqueta(p: PeriodoRegistro): String = when (p) {
    PeriodoRegistro.TODO -> "Todo"
    PeriodoRegistro.HOY -> "Hoy"
    PeriodoRegistro.SIETE_DIAS -> "Últimos 7 días"
    PeriodoRegistro.ESTE_MES -> "Este mes"
    PeriodoRegistro.PERSONALIZADO -> "Elegir fechas"
}

/** Chip «Quitar filtro» bajo el buscador: «Hoy · desde 100.00 CUP»; null sin filtros. */
fun resumenFiltro(f: FiltroRegistros, tipo: TipoRegistro): String? {
    val partes = buildList {
        if (f.porFecha) {
            add(
                if (f.periodo != PeriodoRegistro.PERSONALIZADO) etiqueta(f.periodo)
                else when {
                    f.desde != null && f.hasta != null && f.desde == f.hasta -> FechasUi.texto(f.desde)
                    f.desde != null && f.hasta != null -> "${FechasUi.texto(f.desde)} – ${FechasUi.texto(f.hasta)}"
                    f.desde != null -> "Desde ${FechasUi.texto(f.desde)}"
                    else -> "Hasta ${FechasUi.texto(f.hasta)}"
                },
            )
        }
        if (tipo != TipoRegistro.MOVIMIENTOS && f.porImporte) {
            val min = f.importeMin; val max = f.importeMax
            add(
                when {
                    min != null && max != null -> "${Money.format(min)} – ${Money.format(max)}"
                    min != null -> "desde ${Money.format(min)}"
                    else -> "hasta ${Money.format(max!!)}"
                },
            )
        }
        if (tipo != TipoRegistro.MOVIMIENTOS) f.vendedor?.let { add(it) } // 0.20.0 (H1)
    }
    return partes.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

// ---------------- Filas ----------------

// P24 (fila = dato principal): cuándo y cuánto. Método de pago, artículos, tipo de movimiento… quedan en la ficha
// (y en TalkBack los movimientos dicen si entra o sale).
// P25: cada registro se identifica por su FECHA (título), nunca por un número interno. El subtítulo solo aparece
// cuando aporta algo que la fecha no dice: el cliente de una transferencia o el artículo de un movimiento.
fun tituloFila(v: Venta, zona: ZoneId): String = fechaHora(v.fecha, zona)

/**
 * P33: en una venta de servicios, el subtítulo dice CUÁNTOS servicios se prestaron («1 servicio», «3 servicios»;
 * 2 × Lavado cuenta 2). Los nombres se ven al abrir la ficha de esa fecha. Las ventas de productos no llevan subtítulo.
 */
fun subtituloFila(v: Venta): String? = listOfNotNull(
    // 0.25.0 (§6bis): la anulada sigue en la lista, marcada; la corregida dice a cuál corrige.
    if (v.anulada) "ANULADA" else v.corrigeVentaId?.let { "Corrige #$it" },
    if (v.esServicio) v.unidades.let { n -> if (n == 1L) "1 servicio" else "$n servicios" } else null,
    v.vendedor.ifBlank { null }, // 0.21.0 (C7): quién vendió, en todas las filas
).joinToString(" · ").ifEmpty { null }
fun valorFila(v: Venta): String = Money.format(v.total)

fun tituloFila(t: Transaccion, zona: ZoneId): String = fechaHora(t.fecha, zona)
fun subtituloFila(t: Transaccion): String? =
    listOfNotNull(t.cliente.nombreApellidos.ifBlank { null }, t.vendedor.ifBlank { null }).joinToString(" · ").ifEmpty { null }
fun valorFila(t: Transaccion): String = Money.format(t.importe)

fun tituloFila(i: ItemMovimiento, zona: ZoneId): String = fechaHora(i.movimiento.fecha, zona)
fun subtituloFila(i: ItemMovimiento): String =
    listOfNotNull(i.movimiento.nombre, i.movimiento.hechoPor.ifBlank { null }).joinToString(" · ")
fun valorFila(i: ItemMovimiento): String = cantidadConSigno(i.movimiento.delta, i.movimiento.entidad, i.simbolo)

/**
 * 0.31.0 (P3): id de cada fila de una tabla de Registros. La casilla de selección marca por id, igual que en
 * Inventario, y la exportación se limita a lo marcado.
 */
fun idsVista(v: VistaRegistro): List<Long> = when (v) {
    is VistaRegistro.Ventas -> v.items.map { it.id }
    is VistaRegistro.Transferencias -> v.items.map { it.id }
    is VistaRegistro.Movimientos -> v.items.map { it.movimiento.id }
}

/** 0.31.0 (P3): de la selección, lo que todavía se ve (una fila que ya no está deja de estar marcada). */
fun seleccionVigente(v: VistaRegistro, seleccion: Set<Long>): Set<Long> {
    val visibles = idsVista(v).toHashSet()
    return seleccion.filterTo(linkedSetOf()) { it in visibles }
}

/** Para TalkBack y el color del indicador: entra o sale existencia. */
fun esEntrada(i: ItemMovimiento): Boolean = i.movimiento.delta >= 0
fun descripcionMovimiento(i: ItemMovimiento): String = if (esEntrada(i)) "entrada de existencias" else "salida de existencias"

// ---------------- Formulario del filtro ----------------

/** Lo que el usuario edita en la hoja Filtrar: los importes son texto hasta que se validan. */
data class FormFiltroRegistros(
    val periodo: PeriodoRegistro = PeriodoRegistro.TODO,
    val desde: LocalDate? = null,
    val hasta: LocalDate? = null,
    val importeMin: String = "",
    val importeMax: String = "",
    /** 0.20.0 (H1): null = todos los vendedores. */
    val vendedor: String? = null,
)

object CamposFiltroRegistros {
    const val FECHAS = "fechas"
    const val MIN = "min"
    const val MAX = "max"
}

object FiltroRegistrosLogic {
    fun desde(f: FiltroRegistros) = FormFiltroRegistros(
        periodo = f.periodo, desde = f.desde, hasta = f.hasta,
        importeMin = f.importeMin?.let(::importeEditable).orEmpty(), importeMax = f.importeMax?.let(::importeEditable).orEmpty(),
        vendedor = f.vendedor,
    )

    /** «1450.00» (sin separador de miles ni «CUP») para editarlo cómodamente. */
    private fun importeEditable(c: Cup): String = Money.editar(c)

    /**
     * Valida y construye el filtro conservando el texto del buscador de [actual]. En Movimientos se ignoran los
     * importes. Devuelve el filtro o los errores por campo, nunca los dos.
     */
    fun aplicar(form: FormFiltroRegistros, actual: FiltroRegistros, tipo: TipoRegistro): Pair<FiltroRegistros?, Map<String, String>> {
        val errores = mutableMapOf<String, String>()
        val conImporte = tipo != TipoRegistro.MOVIMIENTOS
        fun importe(texto: String, campo: String): Cup? {
            if (!conImporte || texto.isBlank()) return null
            val c = Money.parse(texto)
            if (c == null || c.centavos < 0) errores[campo] = TextosRegistros.IMPORTE_INVALIDO
            return c
        }
        val min = importe(form.importeMin, CamposFiltroRegistros.MIN)
        val max = importe(form.importeMax, CamposFiltroRegistros.MAX)
        val personalizado = form.periodo == PeriodoRegistro.PERSONALIZADO
        val filtro = FiltroRegistros(
            texto = actual.texto,
            periodo = form.periodo,
            desde = form.desde.takeIf { personalizado },
            hasta = form.hasta.takeIf { personalizado },
            importeMin = min, importeMax = max,
            vendedor = form.vendedor.takeIf { conImporte },
        )
        when (RegistrosFiltro.validar(filtro)) {
            ErrorFiltroRegistro.FECHAS_INVERTIDAS -> errores[CamposFiltroRegistros.FECHAS] = TextosRegistros.FECHAS_INVERTIDAS
            ErrorFiltroRegistro.IMPORTES_INVERTIDOS -> errores.putIfAbsent(CamposFiltroRegistros.MAX, TextosRegistros.IMPORTES_INVERTIDOS)
            null -> Unit
        }
        return if (errores.isEmpty()) filtro to emptyMap() else null to errores
    }
}

/** "esta semana · búsqueda «pan»" o null sin filtros (título de lo compartido/exportado y aviso de la hoja). */
fun detalleFiltro(s: RegistrosUiState): String? {
    val tipo = s.tipo ?: return null
    return listOfNotNull(resumenFiltro(s.filtro, tipo), s.filtro.texto.trim().ifEmpty { null }?.let { "búsqueda «$it»" })
        .ifEmpty { null }?.joinToString(" · ")
}

/**
 * P18 (A12): convierte los filtros de Registros a valores que admite [androidx.lifecycle.SavedStateHandle]
 * (listas de texto). Funciones puras para probarlas sin Android. Un valor dañado o de otra versión se ignora
 * (se vuelve al filtro vacío), nunca rompe la pantalla.
 */
object FiltrosGuardados {
    const val CLAVE_PESTANA = "registros_pestana"
    fun clave(tipo: TipoRegistro) = "registros_filtro_${tipo.name}"

    fun pestana(nombre: String?): PestanaRegistros =
        PestanaRegistros.entries.firstOrNull { it.name == nombre } ?: PestanaRegistros.VENTAS

    fun codificar(f: FiltroRegistros): ArrayList<String> = arrayListOf(
        f.texto, f.periodo.name, f.desde?.toString().orEmpty(), f.hasta?.toString().orEmpty(),
        f.importeMin?.centavos?.toString().orEmpty(), f.importeMax?.centavos?.toString().orEmpty(),
        f.vendedor.orEmpty(),
    )

    /** 0.20.0: 7 valores (con vendedor); se aceptan los 6 de la 0.19 (estado guardado antes de actualizar). */
    fun decodificar(v: List<String>?): FiltroRegistros? {
        if (v == null || (v.size != 6 && v.size != 7)) return null
        return runCatching {
            FiltroRegistros(
                texto = v[0].take(80),
                periodo = PeriodoRegistro.valueOf(v[1]),
                desde = v[2].takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                hasta = v[3].takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                importeMin = v[4].takeIf { it.isNotEmpty() }?.let { Cup(it.toLong()) },
                importeMax = v[5].takeIf { it.isNotEmpty() }?.let { Cup(it.toLong()) },
                vendedor = v.getOrNull(6)?.takeIf { it.isNotEmpty() }?.take(60),
            )
        }.getOrNull()
    }

    fun leer(obtener: (String) -> List<String>?): Map<TipoRegistro, FiltroRegistros> =
        TipoRegistro.entries.mapNotNull { t -> decodificar(obtener(clave(t)))?.let { t to it } }.toMap()

    // Permanencia al cerrar la app (EstadoPantallasRepository): la misma codificación, en una sola cadena por
    // clave y SIN el texto del buscador: al volver otro día se recuerdan la pestaña y los filtros marcados,
    // no lo que se estaba buscando.
    const val CLAVE_PESTANA_PERSISTENTE = "registros.pestana"
    fun clavePersistente(tipo: TipoRegistro) = "registros.filtro.${tipo.name}"

    private const val SEPARADOR = "\u001F"

    fun codificarTexto(f: FiltroRegistros): String = codificar(f.copy(texto = "")).joinToString(SEPARADOR)

    fun decodificarTexto(s: String?): FiltroRegistros? = s?.let { decodificar(it.split(SEPARADOR)) }
}
