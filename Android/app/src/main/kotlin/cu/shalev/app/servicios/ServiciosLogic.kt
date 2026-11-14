package cu.shalev.app.servicios

import cu.shalev.domain.model.nombreCompleto
import cu.shalev.app.inventario.textoAlcance
import cu.shalev.app.inventario.FormatoSalida
import cu.shalev.core.money.Money
import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.FiltroServicios
import cu.shalev.domain.model.ServicioDisponible

/** P29: textos de la pantalla Servicios (sustituye a Elaboración). */
object TextosServicios {
    const val TITULO = "Servicios"
    const val BUSCAR = "Buscar servicio por nombre o tipo"
    const val VACIO_TITULO = "Aún no tienes servicios"
    const val VACIO_DETALLE = "Arreglos, entregas, cortes de pelo… lo que ofreces además de tus productos."
    const val SIN_RESULTADOS_TITULO = "Sin resultados"
    const val SIN_RESULTADOS_DETALLE = "Ningún servicio coincide con la búsqueda o el filtro."
    const val SELECCIONADOS_TITULO = "Todos los resultados están seleccionados"
    const val SELECCIONADOS_DETALLE = "Los servicios seleccionados están fijados encima del buscador."
    const val QUITAR_FILTROS = "Quitar filtros"
    const val ERROR_CARGA = "No se pudieron cargar los servicios."
    const val AGREGAR = "Agregar servicio"
    const val FILTRAR = "Filtrar"
    const val EXPORTAR = "Exportar"
    const val ELIMINAR_UNO = "¿Eliminar este servicio?"
    const val ELIMINAR_DETALLE = "Desaparece de Servicios. Las ventas ya registradas no cambian."
    const val ELIMINADO = "Servicio eliminado."
    const val ERROR_ELIMINAR = "No se pudo eliminar. Inténtalo de nuevo."
    const val ERROR_EXPORTAR = "No se pudo generar el archivo. Inténtalo de nuevo."
    const val GUARDADO_EN_DISPOSITIVO = "Archivo guardado."
    const val FICHA_NO_DISPONIBLE = "Este servicio ya no existe."
    const val SIN_INSUMOS_FICHA = "No gasta insumos."
    const val NO_VENDIBLE = "Sin insumos suficientes: no se puede vender."
    const val ELIGE = "Elige los servicios"

    fun servicios(n: Int) = if (n == 1) "1 servicio" else "$n servicios"
    fun contador(visibles: Int, total: Int) = if (visibles == total) servicios(total) else "$visibles de ${servicios(total)}"
    fun seleccionados(n: Int) = if (n == 1) "1 seleccionado" else "$n seleccionados"
    fun eliminarVarios(n: Int) = "¿Eliminar ${servicios(n)}?"
    fun eliminados(ok: Int, fallidos: Int) = buildString {
        append(if (ok == 1) "1 servicio eliminado." else "$ok servicios eliminados.")
        if (fallidos > 0) append(" $fallidos no se pudieron eliminar.")
    }
    fun elegidos(n: Int) = when (n) {
        0 -> "Marca los servicios a vender"
        1 -> "1 elegido"
        else -> "$n elegidos"
    }
}

object ServiciosTags {
    const val FICHAS_COSTO = "serv_fichas_costo"
    const val GESTION_INVENTARIO = "serv_gestion_inventario"
    const val LISTA = "serv_lista"
    const val BUSCAR = "serv_buscar"
    const val FILTRO = "serv_filtro"
    const val EXPORTAR = "serv_exportar"
    const val AGREGAR = "serv_agregar"
    const val SELECCION = "serv_seleccion"
    const val FIJADOS = "serv_fijados"
    const val SELECCIONAR_TODO = "serv_seleccionar_todo"
    const val ELIMINAR_SELECCION = "serv_eliminar_seleccion"
    const val QUITAR_FILTROS = "serv_quitar_filtros"
    const val FILTRO_TIPO = "serv_filtro_tipo"
    const val FILTRO_APLICAR = "serv_filtro_aplicar"
    const val TIPO_TODOS = "serv_filtro_tipo_todos"
    fun tipoOpcion(tipo: String) = "serv_filtro_tipo_$tipo"
    const val FICHA = "serv_ficha"
    const val FICHA_EDITAR = "serv_ficha_editar"
    const val FICHA_COMPARTIR = "serv_ficha_compartir"
    const val FICHA_ELIMINAR = "serv_ficha_eliminar"
    const val CONTINUAR = "serv_continuar"
    fun fila(id: Long) = "serv_fila_$id"
    fun check(id: Long) = "serv_check_$id"
    fun formato(f: FormatoSalida) = "serv_formato_${f.name}"
}

/** Exportar la lista de servicios: PDF, Excel o texto. */
val FORMATOS_EXPORTAR_SERVICIOS = FormatoSalida.EXPORTAR

/** Reglas puras de la lista. */
object ServiciosLogic {
    /** P24: la fila muestra lo principal (nombre + importe); el subtítulo, el tipo y si los insumos no alcanzan. */
    /** [repetido] (0.24.0): comparte nombre con otro servicio y no se distingue por la descripción. */
    fun subtitulo(s: ServicioDisponible, repetido: Boolean = false): String = listOfNotNull(
        s.servicio.tipo,
        s.alcanza?.takeIf { it == 0L }?.let { textoAlcance(0) },
        cu.shalev.app.inventario.NOMBRE_REPETIDO.takeIf { repetido },
    ).joinToString(" · ")

    fun valor(s: ServicioDisponible): String = Money.format(s.servicio.importe)

    fun vendible(s: ServicioDisponible): Boolean = s.vendible

    fun resumenFiltro(f: FiltroServicios): String? = f.tipo

    /** Campos de la ficha (el nombre va en el título). */
    fun campos(s: ServicioDisponible): List<Pair<String, String>> = buildList {
        add("Tipo" to s.servicio.tipo)
        add("Importe" to Money.format(s.servicio.importe))
        s.servicio.descripcion?.let { add("Descripción" to it) }
        if (s.consumeInsumos) {
            s.costo?.let { add("Costo de insumos" to Money.format(it)) }
            add("Alcanza" to textoAlcance(s.alcanza ?: 0))
            s.lineas.forEach { l -> add(l.nombre to "${Cantidad.format(l.porVez)} ${l.simbolo} por vez") }
        }
    }

    /** Tabla para exportar (PDF/Excel). */
    fun productoPromocional(s: cu.shalev.domain.model.Servicio) = cu.shalev.domain.model.Producto(
        id = s.id, categoria = s.tipo, nombre = s.nombre, descripcion = s.descripcion, fotoUri = s.fotoUri,
        precioCosto = cu.shalev.core.money.Cup.ZERO, precioVenta = s.importe, cantidad = 0, creadoEn = s.creadoEn,
    )

    fun tabla(xs: List<ServicioDisponible>) = cu.shalev.domain.service.TablaExport(
        titulo = "Servicios", // 0.25.1 (E3): como las demás tablas
        columnas = listOf("Tipo", "Servicio", "Importe", "Insumos"),
        filas = xs.map { s ->
            listOf(
                s.servicio.tipo, s.servicio.nombreCompleto, Money.format(s.servicio.importe),
                s.lineas.joinToString(", ") { "${it.nombre} ${Cantidad.format(it.porVez)} ${it.simbolo}" },
            )
        },
    )
}

/**
 * Permanencia del filtro de Servicios al cerrar la app (EstadoPantallasRepository): se recuerda solo el tipo
 * elegido, no el texto del buscador. null = sin filtro (se olvida el ajuste).
 */
object FiltroServiciosGuardado {
    const val CLAVE = "servicios.filtro"

    fun codificar(f: FiltroServicios): String? = f.tipo

    fun decodificar(s: String?): FiltroServicios? =
        s?.takeIf { it.isNotBlank() }?.let { FiltroServicios(tipo = it.take(60)) }
}
