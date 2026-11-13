package cu.shalev.domain.model

/**
 * 0.21.0 (C12, P46 «a»): para qué usa el negocio Shalev. Se elige en el recorrido de la primera ejecución y se cambia
 * en Ajustes → Perfil. Lo no elegido desaparece de la barra inferior, de Inicio y de Registros (no se borra nada:
 * volver a marcarlo lo muestra otra vez con sus datos).
 *  - VENTAS: vender productos. Necesita el inventario, así que lo activa también.
 *  - INVENTARIO: controlar existencias de productos e insumos (sin vender, o vendiendo solo servicios con insumos).
 *  - SERVICIOS: vender servicios.
 * Suposición documentada: sin INVENTARIO (ni VENTAS) los servicios no muestran ni consumen insumos visibles.
 */
enum class Modulo(val etiqueta: String, val detalle: String) {
    VENTAS("Ventas", "Vender productos del inventario"),
    INVENTARIO("Inventario", "Controlar existencias de productos e insumos"),
    SERVICIOS("Servicios", "Vender servicios"),
    ;

    companion object {
        val TODOS: Set<Modulo> = entries.toSet()

        /** Al menos uno; VENTAS arrastra INVENTARIO. Vacío = todos (nunca se deja la app sin secciones). */
        fun normalizar(m: Set<Modulo>): Set<Modulo> = when {
            m.isEmpty() -> TODOS
            VENTAS in m -> m + INVENTARIO
            else -> m
        }

        /**
         * Marca/desmarca [m] en [actual]: marcar VENTAS marca también INVENTARIO; desmarcar INVENTARIO desmarca VENTAS.
         * Puede devolver vacío (el recorrido inicial no deja seguir; Perfil no deja desmarcar el último).
         */
        fun alternar(actual: Set<Modulo>, m: Modulo): Set<Modulo> = when {
            m in actual -> actual - m - (if (m == INVENTARIO) setOf(VENTAS) else emptySet())
            m == VENTAS -> actual + VENTAS + INVENTARIO
            else -> actual + m
        }

        fun deNombres(nombres: Collection<String>?): Set<Modulo> =
            if (nombres == null) TODOS else normalizar(nombres.mapNotNull { n -> entries.firstOrNull { it.name == n } }.toSet())
    }
}
