package cu.shalev.domain.model

import cu.shalev.core.money.Cup

/**
 * Conteo físico: productos en unidades e insumos en milésimas. No incluye elaborados virtuales.
 *
 * 0.32.0: el conteo es **parcial**: solo se cuentan los artículos que cambiaron durante el turno (los que el
 * empleado movió). [categoria] los agrupa en la pantalla («Alimentos», «Bebidas», «Insumos»…).
 */
data class LineaConteo(
    val entidad: TipoEntidad,
    val id: Long,
    val nombre: String,
    val unidad: String,
    val esperado: Long,
    val contado: Long,
    val actualizadoEn: Long,
    val categoria: String = "",
) {
    val diferencia: Long get() = Math.subtractExact(contado, esperado)
}

data class ConteoCierre(
    val billetes: Map<Int, Long> = emptyMap(),
    val otroEfectivo: Cup = Cup.ZERO,
    val inventario: List<LineaConteo> = emptyList(),
    val motivo: String = "",
    val aprobadoPor: String? = null,
    val aprobadoEn: Long? = null,
) {
    val efectivo: Cup get() = BilletesCUP.total(billetes, otroEfectivo)
    val tieneDiferencias: Boolean get() = inventario.any { it.contado != it.esperado }
    fun valido(): Boolean = runCatching {
        efectivo
        inventario.size <= 10000 && inventario.all { it.id > 0 && it.contado >= 0 && runCatching { it.diferencia; true }.getOrDefault(false) } &&
            inventario.map { it.entidad to it.id }.distinct().size == inventario.size &&
            (!tieneDiferencias || motivo.trim().length in 3..160)
    }.getOrDefault(false)
}

object BilletesCUP {
    val denominaciones = listOf(1, 3, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 20000)
    fun total(cantidades: Map<Int, Long>, otro: Cup = Cup.ZERO): Cup {
        require(!otro.isNegative)
        val centavos = cantidades.entries.fold(otro.centavos) { total, (denominacion, cantidad) ->
            require(denominacion in denominaciones && cantidad in 0..999999)
            Math.addExact(total, Math.multiplyExact(denominacion.toLong() * 100, cantidad))
        }
        return Cup(centavos)
    }
}
