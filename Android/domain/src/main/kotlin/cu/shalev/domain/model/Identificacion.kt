package cu.shalev.domain.model

import java.text.Normalizer

/**
 * 0.24.0: cómo se identifica un artículo ante el usuario y sus clientes.
 *
 * - **Nombre** = qué es («Cerveza Cristal»). **Descripción** = rasgos breves que lo distinguen («Lata 350 ml Superior»),
 *   una sola línea de hasta [cu.shalev.domain.validation.Validadores.MAX_DESCRIPCION] caracteres.
 * - Se muestra junto al nombre en TODAS partes: «Cerveza Cristal · Lata 350 ml Superior» ([nombreCompleto]).
 *   Ventas y movimientos congelan ese texto al registrarse.
 * - Regla: si dos o más productos (o dos o más servicios) tienen el mismo nombre, cada uno necesita una descripción
 *   y todas deben ser distintas. Se compara sin mayúsculas, tildes ni espacios repetidos ([clave]).
 * - Productos y Elaborados comparten tabla y se comparan entre sí; los servicios, entre servicios. Los insumos no
 *   tienen descripción y no entran en la regla.
 */
object Identificacion {
    const val SEPARADOR = " · "

    /** «Nombre · Descripción», o solo el nombre si no hay descripción. */
    fun nombreCompleto(nombre: String, descripcion: String?): String {
        val n = nombre.trim()
        // Descripciones anteriores a 0.24.0 podían tener varias líneas: se muestran en una.
        val d = descripcion.orEmpty().replace(Regex("\\s+"), " ").trim()
        return if (d.isEmpty()) n else n + SEPARADOR + d
    }

    /** Forma de comparar: sin mayúsculas, tildes ni espacios repetidos. */
    fun clave(texto: String?): String =
        Normalizer.normalize(texto.orEmpty().trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ")

    enum class Conflicto {
        /** Hay otro con el mismo nombre y este no tiene descripción. */
        FALTA_DESCRIPCION,
        /** Hay otro con el mismo nombre y la misma descripción. */
        DESCRIPCION_REPETIDA,
    }

    /**
     * Conflicto de un artículo ([id] = 0 si es nuevo) con los [otros] activos, dados como (id, nombre, descripción).
     * null = se distingue bien.
     */
    fun conflicto(id: Long, nombre: String, descripcion: String?, otros: List<Triple<Long, String, String?>>): Conflicto? {
        val n = clave(nombre)
        val mismos = otros.filter { it.first != id && clave(it.second) == n }
        if (mismos.isEmpty()) return null
        val d = clave(descripcion)
        if (d.isEmpty()) return Conflicto.FALTA_DESCRIPCION
        return if (mismos.any { clave(it.third) == d }) Conflicto.DESCRIPCION_REPETIDA else null
    }

    /** Ids de los artículos que comparten nombre con otro y no se distinguen (sin descripción o con la misma). */
    fun porDiferenciar(items: List<Triple<Long, String, String?>>): Set<Long> =
        items.groupBy { clave(it.second) }.values.filter { it.size > 1 }.flatMap { grupo ->
            grupo.filter { conflicto(it.first, it.second, it.third, grupo) != null }.map { it.first }
        }.toSet()

    fun productosPorDiferenciar(ps: List<Producto>): Set<Long> =
        porDiferenciar(ps.filter { !it.eliminado && !it.esInsumo }.map { it.identidad })

    fun serviciosPorDiferenciar(ss: List<Servicio>): Set<Long> =
        porDiferenciar(ss.filterNot { it.eliminado }.map { it.identidad })
}

val Producto.nombreCompleto: String get() = Identificacion.nombreCompleto(nombre, descripcion)
val Servicio.nombreCompleto: String get() = Identificacion.nombreCompleto(nombre, descripcion)
val Producto.identidad: Triple<Long, String, String?> get() = Triple(id, nombre, descripcion)
val Servicio.identidad: Triple<Long, String, String?> get() = Triple(id, nombre, descripcion)
