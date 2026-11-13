package cu.shalev.app.common

/** Descripción de TalkBack del botón de filtros: añade cuántos filtros hay activos. */
fun descripcionFiltro(base: String, activos: Int): String =
    if (activos <= 0) base else "$base, $activos ${if (activos == 1) "filtro activo" else "filtros activos"}"
