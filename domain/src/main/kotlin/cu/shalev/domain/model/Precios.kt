package cu.shalev.domain.model

import cu.shalev.core.money.Cup

/**
 * Preajuste de precios: ±[puntosBasicos] (1000 = +10 %) sobre [productoIds] y [servicioIds].
 * [metodoPago] limita el ajuste a ese método (null = cualquiera). [importeMinimo] se conserva únicamente para leer
 * respaldos antiguos; desde v14 no se edita ni interviene en el cálculo.
 * Varios preajustes aplicables al mismo artículo se suman.
 */
data class PreajustePrecios(
    val id: Long = 0,
    val nombre: String,
    val puntosBasicos: Int,
    val productoIds: Set<Long>,
    val metodoPago: MetodoPago? = null,
    val importeMinimo: Cup? = null,
    val activo: Boolean = true,
    /** Servicios incluidos; vacío mantiene compatibilidad con ajustes creados antes de admitir servicios. */
    val servicioIds: Set<Long> = emptySet(),
)
