package cu.shalev.app.precios

import cu.shalev.domain.model.nombreCompleto
import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
import cu.shalev.core.money.Percent
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.PreajustePrecios
import cu.shalev.domain.model.Producto
import java.math.BigDecimal

enum class CampoPrecio { PORCENTAJE, IMPORTE, PRODUCTOS, NOMBRE }
enum class TipoSeleccionPrecio(val titulo: String) { PRODUCTOS("Productos"), SERVICIOS("Servicios") }

object TextosPrecios {
    const val TITULO = "Precios"
    const val EXPLICACION = "Sube o baja precios al cobrar según el método de pago. El precio guardado no cambia."
    const val VACIO_TITULO = "Aún no hay ajustes de precio"
    const val VACIO_DETALLE = "Ej.: +5 % con transferencia."
    const val SIN_PRODUCTOS = "Primero agrega productos en Inventario."
    const val ERROR_PORCENTAJE = "Escribe un porcentaje, por ejemplo 10"
    const val ERROR_SUBIR = "Puedes subir hasta un 100 %"
    const val ERROR_BAJAR = "Puedes bajar hasta un 90 %"
    @Deprecated("El importe mínimo ya no forma parte de Precios")
    const val ERROR_IMPORTE = "El importe mínimo ya no se utiliza"
    const val ERROR_PRODUCTOS = "Elige al menos un producto o servicio"
    const val ERROR_NOMBRE = "El nombre es demasiado largo"
    const val ERROR_GENERICO = "No se pudo guardar. Inténtalo de nuevo."
    const val MAX_LISTA = 50
}

/** Formulario de un preajuste. El porcentaje se escribe en positivo y [sube] decide el signo. */
data class PreajusteForm(
    val id: Long = 0,
    val nombre: String = "",
    val sube: Boolean = true,
    val porcentaje: String = "",
    val metodo: MetodoPago? = null,
    @Deprecated("El importe mínimo ya no forma parte de Precios") val importeMinimo: String = "",
    val productoIds: Set<Long> = emptySet(),
    val servicioIds: Set<Long> = emptySet(),
    val tipoSeleccion: TipoSeleccionPrecio = TipoSeleccionPrecio.PRODUCTOS,
    val busqueda: String = "",
    val activo: Boolean = true,
    val mostrarErrores: Boolean = false,
) {
    val nuevo: Boolean get() = id == 0L

    /** Puntos básicos con signo, o null si el texto no es un porcentaje positivo válido. */
    val puntosBasicos: Int? get() = Percent.parse(porcentaje.replace(',', '.'))?.takeIf { it > 0 }?.let { if (sube) it else -it }
}

fun validar(f: PreajusteForm): Map<CampoPrecio, String> = buildMap {
    val pb = f.puntosBasicos
    when {
        pb == null -> put(CampoPrecio.PORCENTAJE, TextosPrecios.ERROR_PORCENTAJE)
        pb > 10_000 -> put(CampoPrecio.PORCENTAJE, TextosPrecios.ERROR_SUBIR)
        pb < -9_000 -> put(CampoPrecio.PORCENTAJE, TextosPrecios.ERROR_BAJAR)
    }
    if (f.productoIds.isEmpty() && f.servicioIds.isEmpty()) put(CampoPrecio.PRODUCTOS, TextosPrecios.ERROR_PRODUCTOS)
    if (f.nombre.trim().length > MAX_NOMBRE) put(CampoPrecio.NOMBRE, TextosPrecios.ERROR_NOMBRE)
}

private const val MAX_NOMBRE = 80

/** Nombre automático si el usuario no escribe uno: "+10 % con Transferencia". */
fun nombreSugerido(f: PreajusteForm): String {
    val pct = f.puntosBasicos?.let { Percent.format(it) } ?: if (f.sube) "Subir precio" else "Bajar precio"
    val metodo = f.metodo?.let { " con ${etiqueta(it)}" }.orEmpty()
    return "$pct$metodo"
}

fun aPreajuste(f: PreajusteForm): PreajustePrecios = PreajustePrecios(
    id = f.id,
    nombre = f.nombre.trim().ifEmpty { nombreSugerido(f) },
    puntosBasicos = f.puntosBasicos ?: 0,
    productoIds = f.productoIds, servicioIds = f.servicioIds,
    metodoPago = f.metodo,
    importeMinimo = null,
    activo = f.activo,
)

fun desde(p: PreajustePrecios): PreajusteForm = PreajusteForm(
    id = p.id,
    nombre = p.nombre,
    sube = p.puntosBasicos > 0,
    porcentaje = BigDecimal.valueOf(kotlin.math.abs(p.puntosBasicos).toLong(), 2).stripTrailingZeros().toPlainString(),
    metodo = p.metodoPago,
    productoIds = p.productoIds, servicioIds = p.servicioIds,
    tipoSeleccion = if (p.productoIds.isEmpty() && p.servicioIds.isNotEmpty()) TipoSeleccionPrecio.SERVICIOS else TipoSeleccionPrecio.PRODUCTOS,
    activo = p.activo,
)

fun etiqueta(m: MetodoPago): String = when (m) {
    MetodoPago.EFECTIVO -> "Efectivo"
    MetodoPago.TRANSFERENCIA -> "Transferencia"
}

/** Resumen accesible: método de pago y número de artículos. */
fun resumenPreajuste(p: PreajustePrecios, existentes: Int = p.productoIds.size): String = listOfNotNull(
    p.metodoPago?.let { "Solo ${etiqueta(it)}" } ?: "Cualquier pago",
    if (existentes == 1) "1 artículo" else "$existentes artículos",
).joinToString(" · ")

/** Ejemplo con el primer producto elegido: "Refresco: 100.00 CUP → 110.00 CUP". */
fun vistaPrevia(f: PreajusteForm, productos: List<Producto>): String? {
    val pb = f.puntosBasicos ?: return null
    val p = productos.firstOrNull { it.id in f.productoIds } ?: return null
    val nuevo = p.precioVenta.ajustar(pb).let { if (it.isNegative) Cup.ZERO else it }
    return "${p.nombreCompleto}: ${Money.format(p.precioVenta)} → ${Money.format(nuevo)}"
}

/** Búsqueda literal por nombre, descripción o categoría; como máximo [TextosPrecios.MAX_LISTA] resultados. */
fun filtrar(productos: List<Producto>, busqueda: String): List<Producto> {
    val q = busqueda.trim().lowercase()
    val lista = if (q.isEmpty()) productos else productos.filter {
        it.nombre.lowercase().contains(q) || it.descripcion?.lowercase()?.contains(q) == true ||
            it.categoria.lowercase().contains(q)
    }
    return lista.take(TextosPrecios.MAX_LISTA)
}
