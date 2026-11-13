package cu.shalev.domain.model

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import java.time.Instant

/**
 * Materia prima de los Elaborados. Shalev.txt: Nombre, Precio (costo), Cantidad, Nivel bajo/crítico.
 * Suposición: [unidad] de medida (kg, L, u…) porque las recetas necesitan cantidades fraccionarias;
 * [precio] es el costo por 1 unidad de medida.
 */
data class Insumo(
    val id: Long = 0,
    val nombre: String,
    val unidad: UnidadMedida = UnidadMedida.UNIDAD,
    val precio: Cup,
    val cantidad: Cantidad,
    val nivelBajo: Cantidad? = null,
    val nivelCritico: Cantidad? = null,
    val creadoEn: Instant,
    val actualizadoEn: Instant = creadoEn,
    /**
     * P29: precio de venta por 1 unidad de medida (1 kg, 1 L…). Null = no se vende, solo se usa en recetas.
     * Se vende en unidades ENTERAS de su medida (decisión del usuario). Último parámetro: no altera las llamadas
     * posicionales existentes.
     */
    val precioVenta: Cup? = null,
)

/** P29: se puede vender si tiene precio de venta y queda al menos 1 unidad entera. */
val Insumo.vendible: Boolean get() = precioVenta != null && cantidad.milesimas >= 1000L
/** Unidades enteras de su medida que hay (para vender). */
val Insumo.unidadesEnteras: Long get() = cantidad.milesimas.coerceAtLeast(0) / 1000L

enum class UnidadMedida(val simbolo: String) { UNIDAD("u"), KILOGRAMO("kg"), GRAMO("g"), LITRO("L"), MILILITRO("ml") }

/** Receta de UNA unidad de un Elaborado. */
data class Receta(val productoId: Long, val lineas: List<RecetaLinea>)

data class RecetaLinea(val insumoId: Long, val cantidad: Cantidad)
