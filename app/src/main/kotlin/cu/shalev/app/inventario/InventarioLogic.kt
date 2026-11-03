package cu.shalev.app.inventario

import cu.shalev.domain.model.vendible
import cu.shalev.app.common.FechasUi
import cu.shalev.app.inicio.TipoVenta
import cu.shalev.domain.model.EstadoCaducidad
import cu.shalev.domain.model.FiltroInventario
import cu.shalev.domain.model.ItemInventario
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.NivelStock
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.TipoArticulo

object TextosInventario {
    const val TITULO = "Inventario"
    const val BUSCAR = "Buscar por nombre, categoría o código"
    const val VACIO_TITULO = "Tu inventario está vacío"
    const val VACIO_DETALLE = "Toca + para agregar tu primer producto."
    const val SIN_RESULTADOS_TITULO = "Sin resultados"
    const val SIN_RESULTADOS_DETALLE = "Prueba otra búsqueda o quita los filtros."
    const val SELECCIONADOS_TITULO = "Todos los resultados están seleccionados"
    const val SELECCIONADOS_DETALLE = "Los elementos seleccionados están fijados encima del buscador."
    const val QUITAR_FILTROS = "Quitar filtros"
    const val ERROR_CARGA = "No se pudo cargar el inventario."
    const val AGREGAR = "Agregar producto"
    const val EXPORTAR = "Exportar"
    const val FILTRAR = "Filtrar"
    const val ELIMINAR_UNO = "¿Eliminar este producto?"
    const val ELIMINAR_DETALLE = "Las ventas registradas no cambian."
    const val ELIMINADO = "Producto eliminado."
    const val ERROR_ELIMINAR = "No se pudo eliminar. Inténtalo de nuevo."
    const val ERROR_EXPORTAR = "No se pudo generar el archivo. Inténtalo de nuevo."
    const val GUARDADO_EN_DISPOSITIVO = "Archivo guardado."
    const val FICHA_NO_DISPONIBLE = "Ya no existe."
    const val EN_USO = "No se puede eliminar: lo usa una receta o un servicio."

    fun eliminarVarios(n: Int) = "¿Eliminar $n ${if (n == 1) "producto" else "productos"}?"
    fun eliminados(ok: Int, fallidos: Int, enUso: List<String> = emptyList()) = buildString {
        append(if (ok == 1) "1 eliminado." else "$ok eliminados.")
        if (enUso.isNotEmpty()) append(" En uso (no se borran): ${enUso.joinToString(", ")}.")
        if (fallidos > 0) append(" $fallidos no se pudieron eliminar.")
    }
    fun seleccionados(n: Int) = if (n == 1) "1 seleccionado" else "$n seleccionados"
    fun contador(visibles: Int, total: Int) = if (visibles == total) productos(total) else "$visibles de ${productos(total)}"
    fun productos(n: Int) = if (n == 1) "1 producto" else "$n productos"
}

object InventarioTags {
    const val CONTINUAR_VENTA = "inventario_continuar_venta"
    const val LISTA = "inventario_lista"
    const val BUSCAR = "inventario_buscar"
    const val FILTRO = "inventario_filtro"
    const val EXPORTAR = "inventario_exportar"
    const val AGREGAR = "inventario_agregar"
    const val SELECCION = "inventario_seleccion"
    const val FIJADOS = "inventario_fijados"
    const val SELECCIONAR_TODO = "inventario_seleccionar_todo"
    const val ELIMINAR_SELECCION = "inventario_eliminar_seleccion"
    const val FICHA = "inventario_ficha"
    const val FICHA_EDITAR = "inventario_ficha_editar"
    const val FICHA_COMPARTIR = "inventario_ficha_compartir"
    const val FICHA_ELIMINAR = "inventario_ficha_eliminar"
    const val QUITAR_FILTROS = "inventario_quitar_filtros"
    const val FILTRO_ESTADO = "inventario_filtro_estado"
    const val FILTRO_TIPO = "inventario_filtro_tipo"
    const val FILTRO_CATEGORIA = "inventario_filtro_categoria"
    const val FILTRO_APLICAR = "inventario_filtro_aplicar"
    const val ALERTA_TODAS = "inventario_filtro_alerta_todas"
    const val CATEGORIA_TODAS = "inventario_filtro_categoria_todas"
    fun alertaOpcion(a: TipoAlerta) = "inventario_filtro_alerta_${a.name}"
    fun tipoOpcion(t: TipoArticulo) = "inventario_filtro_tipo_${t.name}"
    fun categoriaOpcion(c: String) = "inventario_filtro_categoria_$c"
    fun fila(id: Long) = "inventario_fila_$id"
    fun check(id: Long) = "inventario_check_$id"
    fun formato(f: FormatoSalida) = "inventario_formato_${f.name}"
}

/** Salidas del botón Exportar (lista) y de Compartir (ficha). */
enum class FormatoSalida(val etiqueta: String, val detalle: String) {
    PDF("PDF", "Documento para imprimir o archivar"),
    EXCEL("Excel", "Hoja de cálculo (.xlsx)"),
    /** Prompt 14: lista de precios en PNG (para clientes: sin costo ni existencias). */
    IMAGEN("Imagen", "Lista de precios en imagen, para clientes"),
    TARJETAS("Tarjetas promocionales", "Foto, nombre y precio para redes y mensajería"),
    ;

    /** Se puede «Guardar en el dispositivo» (un solo archivo por SAF). Imagen y tarjetas pueden ser varias. */
    val guardable: Boolean get() = this == PDF || this == EXCEL

    /** 0.25.1 (E4): la lista en PDF o Excel lleva el precio de costo: es para el negocio, no para clientes. */
    val interno: Boolean get() = this == PDF || this == EXCEL

    /** Aviso de la hoja Exportar: quién puede recibir cada formato. */
    val destinatario: String get() = if (interno) USO_INTERNO else PARA_CLIENTES

    companion object {
        /** 0.26.0: sin exportar como texto (solo archivos). */
        val EXPORTAR = listOf(PDF, EXCEL, IMAGEN, TARJETAS)
        const val USO_INTERNO = "Incluye costos" // 0.27.1: «Uso interno (incluye c…» no cabía junto a los dos botones
        const val PARA_CLIENTES = "Para clientes (sin costos)"
        val COMPARTIR_FICHA = listOf(IMAGEN, TARJETAS)
    }
}

/** Tono del indicador visual de cada fila (lo traduce a color la pantalla). */
enum class TonoFila { NORMAL, AVISO, PELIGRO }

fun tonoDe(i: ItemInventario): TonoFila = when {
    i.nivel == NivelStock.CRITICO || i.nivel == NivelStock.SIN_EXISTENCIA || i.caducidad == EstadoCaducidad.VENCIDA || i.alcanza == 0L -> TonoFila.PELIGRO
    i.nivel == NivelStock.BAJO || i.caducidad == EstadoCaducidad.PROXIMA -> TonoFila.AVISO
    else -> TonoFila.NORMAL
}

/** "Bebidas · 12 u · vence 03/10/2026". La caducidad solo se muestra si está próxima o vencida. */
/**
 * P24 (fila = solo el dato principal): el subtítulo aparece únicamente si hay una alerta, para que el color
 * nunca sea la única señal. Categoría, precio, costo, código… están en la ficha.
 */
fun subtituloFila(i: ItemInventario): String? = buildList {
    when (i.nivel) {
        NivelStock.SIN_EXISTENCIA -> add("Sin existencia")
        NivelStock.CRITICO -> add("Crítico")
        NivelStock.BAJO -> add("Bajo")
        NivelStock.NORMAL -> Unit
    }
    when (i.caducidad) {
        EstadoCaducidad.VENCIDA -> add("Venció ${FechasUi.texto(i.producto.fechaCaducidad)}")
        EstadoCaducidad.PROXIMA -> add("Vence ${FechasUi.texto(i.producto.fechaCaducidad)}")
        else -> Unit
    }
    if (i.nombreRepetido) add(NOMBRE_REPETIDO) // 0.24.0
}.takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** Dato principal de la fila: existencias; en un Elaborado (P26), cuánto alcanza con sus insumos. */
fun valorFila(i: ItemInventario): String =
    i.insumo?.let { "${Cantidad.format(it.cantidad)} ${it.unidad.simbolo}" } ?: i.alcanza?.let(::textoAlcance) ?: "${i.producto.cantidad} u"

/**
 * 0.31.0 (P2): precio de costo de la fila. Un insumo guarda su coste en [Producto.precioCosto] al verse como fila.
 */
fun costoFila(i: ItemInventario): Cup = i.producto.precioCosto

/**
 * 0.31.0 (P2): precio de venta de la fila; null si todavía no tiene (un insumo puede servir solo para recetas).
 * En la fila los dos se distinguen solo por el color, así que hay que saber cuál falta.
 */
fun ventaFila(i: ItemInventario): Cup? = i.producto.precioVenta.takeIf { it > Cup.ZERO }

/** 0.31.0 (P2): categoría de la fila (los insumos comparten la reservada «Insumos»). */
fun categoriaFila(i: ItemInventario): String = i.producto.categoria

/**
 * P26: un Elaborado no tiene existencias; se vende mientras alcancen sus insumos (el más escaso manda).
 * P29: igual para un servicio que consume insumos. Mismo texto en Inventario, Servicios y el carrito de la venta.
 */
fun textoAlcance(alcanza: Long): String = if (alcanza > 0) "Alcanza para $alcanza" else SIN_INSUMOS

const val SIN_INSUMOS = "Sin insumos suficientes"

/** 0.24.0: subtítulo de la fila de un producto que comparte nombre con otro sin distinguirse. */
const val NOMBRE_REPETIDO = "Nombre repetido: añade descripción"

/** Texto para TalkBack que incluye el estado que el color transmite (el color nunca es el único canal). */
/** P29: estado de un insumo para TalkBack y la ficha (el color nunca es el único canal). */
fun descripcionNivel(nivel: NivelStock): String? = when (nivel) {
    NivelStock.SIN_EXISTENCIA -> "sin existencia"
    NivelStock.CRITICO -> "existencias en nivel crítico"
    NivelStock.BAJO -> "existencias bajas"
    NivelStock.NORMAL -> null
}

fun descripcionEstado(i: ItemInventario): String? = buildList {
    when (i.nivel) {
        NivelStock.SIN_EXISTENCIA -> add("sin existencia")
        NivelStock.CRITICO -> add("stock crítico")
        NivelStock.BAJO -> add("stock bajo")
        NivelStock.NORMAL -> Unit
    }
    when (i.caducidad) {
        EstadoCaducidad.VENCIDA -> add("vencido")
        EstadoCaducidad.PROXIMA -> add("próximo a caducar")
        else -> Unit
    }
    if (i.nombreRepetido) add("nombre repetido, añade una descripción")
}.takeIf { it.isNotEmpty() }?.joinToString(", ")

fun etiqueta(a: TipoAlerta): String = when (a) {
    TipoAlerta.STOCK_BAJO -> "Stock bajo"
    TipoAlerta.STOCK_CRITICO -> "Stock crítico"
    TipoAlerta.PROXIMO_A_CADUCAR -> "Próximos a caducar"
    TipoAlerta.INSUMO_BAJO -> "Insumo bajo"
    TipoAlerta.INSUMO_CRITICO -> "Insumo crítico"
    TipoAlerta.SIN_EXISTENCIA -> "Sin existencia"
    TipoAlerta.NOMBRE_REPETIDO -> "Nombre repetido"
}

fun etiqueta(t: TipoArticulo): String = when (t) {
    TipoArticulo.TODOS -> "Todos"
    TipoArticulo.ARTICULOS -> "Artículos"
    TipoArticulo.ELABORADOS -> "Elaborados"
    TipoArticulo.INSUMOS -> "Insumos"
}

val ALERTAS_PRODUCTO = listOf(TipoAlerta.STOCK_BAJO, TipoAlerta.STOCK_CRITICO, TipoAlerta.PROXIMO_A_CADUCAR)

/** P29: alertas de insumos (llegan desde los contadores de Inicio). */
val ALERTAS_INSUMO = listOf(TipoAlerta.INSUMO_BAJO, TipoAlerta.INSUMO_CRITICO)

/** 0.20.0 (H6): productos e insumos por debajo de 0 (ventas a la vez en varias apps con poco stock). */
val ALERTAS_GENERALES = listOf(TipoAlerta.SIN_EXISTENCIA, TipoAlerta.NOMBRE_REPETIDO)

/** Opciones del combo «Estado» del filtro de Inventario (null = Todos). */
val ALERTAS_FILTRO: List<TipoAlerta?> = listOf(null) + ALERTAS_PRODUCTO + TipoAlerta.SIN_EXISTENCIA


/** Resumen de los filtros activos para el chip bajo el buscador ("Bebidas · Stock bajo"). */
fun resumenFiltro(f: FiltroInventario): String? = listOfNotNull(
    f.categoria,
    f.alerta?.let(::etiqueta),
    f.tipo.takeIf { it != TipoArticulo.TODOS }?.let(::etiqueta),
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** Modo «elegir qué vender» del Inventario (Prompt 13). Reglas puras. */
object SeleccionVenta {
    /** Un artículo sin existencias no se puede vender; un Elaborado (P26), solo si sus insumos alcanzan para 1. */
    fun vendible(i: ItemInventario): Boolean = when {
        i.producto.eliminado -> false
        i.insumo != null -> i.insumo?.vendible == true // P29: con precio de venta y al menos 1 unidad entera
        i.producto.esElaborado -> (i.alcanza ?: 0) > 0
        else -> i.producto.cantidad > 0
    }

    fun motivo(i: ItemInventario): String = when {
        i.insumo != null && i.insumo?.precioVenta == null -> NO_VENDIBLE_INSUMO
        i.producto.esElaborado -> NO_VENDIBLE_ELABORADO
        else -> NO_VENDIBLE
    }

    /** P29: la venta de productos muestra todo el Inventario (artículos, Elaborados e insumos). */
    @Suppress("UNUSED_PARAMETER")
    fun filtroInicial(tipo: TipoVenta?): TipoArticulo = TipoArticulo.TODOS

    fun csv(ids: Collection<Long>): String = ids.joinToString(",")
    fun parse(csv: String?): Set<Long> = csv.orEmpty().split(',').mapNotNullTo(linkedSetOf()) { it.trim().toLongOrNull() }

    fun titulo(tipo: TipoVenta) = if (tipo == TipoVenta.SERVICIO) "Elige los servicios" else "Elige los productos"
    /** P28: cuántos van elegidos (antes «Continuar con N», que repetía el botón Continuar de al lado). */
    fun continuar(n: Int) = when (n) {
        0 -> "Marca los productos a vender"
        1 -> "1 elegido"
        else -> "$n elegidos"
    }
    const val NO_VENDIBLE = "Sin existencias: no se puede vender."
    const val NO_VENDIBLE_ELABORADO = "Sin insumos suficientes: no se puede vender."
    const val NO_VENDIBLE_INSUMO = "Este insumo no tiene precio de venta. Edítalo para venderlo."
}

/**
 * Permanencia del filtro del Inventario al cerrar la app (EstadoPantallasRepository): una sola cadena y SIN el
 * texto del buscador —al volver otro día se recuerdan los filtros marcados, no lo que se estaba buscando—.
 * Un valor dañado o de otra versión se ignora (null = filtro vacío), nunca rompe la pantalla.
 */
object FiltroInventarioGuardado {
    const val CLAVE = "inventario.filtro"
    private const val SEPARADOR = "\u001F"

    fun codificar(f: FiltroInventario): String =
        listOf(f.categoria.orEmpty(), f.alerta?.name.orEmpty(), f.tipo.name).joinToString(SEPARADOR)

    fun decodificar(s: String?): FiltroInventario? {
        val v = s?.split(SEPARADOR) ?: return null
        if (v.size != 3) return null
        return FiltroInventario(
            categoria = v[0].takeIf { it.isNotEmpty() }?.take(60),
            alerta = if (v[1].isEmpty()) null else TipoAlerta.entries.firstOrNull { it.name == v[1] } ?: return null,
            tipo = TipoArticulo.entries.firstOrNull { it.name == v[2] } ?: return null,
        )
    }
}
