package cu.shalev.domain.model

import cu.shalev.core.money.Cup
import java.time.Instant
import java.time.LocalDate

/**
 * Artículo del inventario (incluye los Elaborados). Existencias en unidades enteras.
 * - Elaborado: sin foto ni caducidad; su [precioCosto] se calcula desde la [Receta].
 * - C4 (decisión): el Elaborado SÍ tiene [precioVenta] (se vende).
 * - Borrado lógico ([eliminado]) para no romper el historial de ventas.
 */
data class Producto(
    val id: Long = 0,
    val categoria: String,
    val nombre: String,
    val descripcion: String? = null,
    val fotoUri: String? = null,
    val fechaCaducidad: LocalDate? = null,
    val precioCosto: Cup,
    val precioVenta: Cup,
    val cantidad: Long,
    val nivelBajo: Long? = null,
    val nivelCritico: Long? = null,
    val creadoEn: Instant,
    val actualizadoEn: Instant = creadoEn,
    val eliminado: Boolean = false,
    /** Mano de obra, gastos directos e indirectos por unidad; no se pierden al recalcular la receta. */
    val costoAdicional: Cup = Cup.ZERO,
) {
    val esElaborado: Boolean get() = Categorias.esElaborado(categoria)
    /** P29: fila de Inventario que en realidad es un insumo (ver [IdArticulo] y [comoProducto]). */
    val esInsumo: Boolean get() = IdArticulo.esInsumo(id)
}

/**
 * P29: el insumo visto como fila del Inventario (categoría «Insumos», id negativo). Las existencias se muestran
 * en unidades ENTERAS de su medida; el detalle con decimales está en la ficha del insumo.
 */
fun Insumo.comoProducto(): Producto = Producto(
    id = IdArticulo.deInsumo(id),
    categoria = Categorias.INSUMOS,
    nombre = nombre,
    precioCosto = precio,
    precioVenta = precioVenta ?: Cup.ZERO,
    cantidad = unidadesEnteras,
    creadoEn = creadoEn,
    actualizadoEn = actualizadoEn,
)

object Categorias {
    const val ELABORADO = "Elaborado"
    /** P29: categoría reservada de los insumos en el Inventario (no se puede usar para un producto). */
    const val INSUMOS = "Insumos"
    /** Última categoría habitual: el escáner la deja a la vista (editable) cuando la imagen no la trae. */
    const val OTROS = "Otros"
    fun esInsumos(categoria: String) = categoria.trim().equals(INSUMOS, ignoreCase = true)

    /** Categorías habituales del comercio minorista. El usuario puede escribir otras (personalizadas). */
    val PREDEFINIDAS: List<String> = listOf(
        "Alimentos", "Bebidas", "Bebidas alcohólicas", "Lácteos", "Cárnicos y embutidos", "Pescados y mariscos",
        "Panadería y dulcería", "Confituras y snacks", "Conservas", "Granos y cereales", "Frutas y vegetales",
        "Congelados", "Condimentos y especias", "Aceites y grasas", "Aseo personal", "Limpieza del hogar",
        "Cosméticos", "Farmacia y salud", "Bebé", "Mascotas", "Ferretería", "Electrónica", "Electrodomésticos",
        "Ropa y calzado", "Papelería", "Juguetes", "Hogar y cocina", "Cigarros y tabacos", OTROS,
        ELABORADO,
    )

    fun esElaborado(categoria: String) = categoria.trim().equals(ELABORADO, ignoreCase = true)

    /** Predefinidas + personalizadas en uso, sin duplicados (ignorando mayúsculas), en orden estable. */
    fun combinar(enUso: Collection<String>): List<String> {
        val vistos = PREDEFINIDAS.map { it.lowercase() }.toMutableSet()
        val extra = enUso.map(String::trim).filter { it.isNotEmpty() && vistos.add(it.lowercase()) }.sortedBy { it.lowercase() }
        return PREDEFINIDAS + extra
    }
}
