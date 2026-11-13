package cu.shalev.app.insumos

import cu.shalev.app.producto.ProductoFormLogic
import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.UnidadMedida
import cu.shalev.domain.validation.Validadores
import java.time.Instant

/**
 * Formulario de insumo con los atributos de Shalev.txt: Nombre, Precio (costo), Cantidad, Nivel bajo (opcional)
 * y Nivel crítico (opcional). La unidad de medida (suposición documentada en [Insumo]) no es un campo aparte:
 * acompaña a la cantidad (kg, L, u…) porque las recetas usan cantidades fraccionarias.
 */
data class InsumoForm(
    val id: Long = 0,
    val creadoEn: Instant? = null,
    val nombre: String = "",
    val precio: String = "",
    val cantidad: String = "",
    val unidad: UnidadMedida = UnidadMedida.UNIDAD,
    val nivelBajo: String = "",
    val nivelCritico: String = "",
    /** P29: opcional; con precio, el insumo se puede vender por unidades enteras de su medida. */
    val precioVenta: String = "",
)

/** Coinciden con los campos de [Validadores.insumo] para mapear sus errores sin duplicar reglas. */
object CamposInsumo {
    const val NOMBRE = "nombre"
    const val UNIDAD = "unidad"
    const val PRECIO = "precio"
    const val CANTIDAD = "cantidad"
    const val NIVEL_BAJO = "nivelBajo"
    const val NIVEL_CRITICO = "nivelCritico"
    const val PRECIO_VENTA = "precioVenta"
}

object InsumoFormLogic {

    fun validar(f: InsumoForm): Map<String, String> {
        val e = linkedMapOf<String, String>()
        val precio = Money.parse(f.precio)
        when {
            f.precio.isBlank() -> e[CamposInsumo.PRECIO] = "Escribe el precio de costo."
            precio == null -> e[CamposInsumo.PRECIO] = ProductoFormLogic.FORMATO_IMPORTE
        }
        val cantidad = Cantidad.parse(f.cantidad)
        when {
            f.cantidad.isBlank() -> e[CamposInsumo.CANTIDAD] = "Escribe cuánto tienes (puede ser 0)."
            cantidad == null -> e[CamposInsumo.CANTIDAD] = FORMATO_CANTIDAD
        }
        if (f.precioVenta.isNotBlank()) {
            val pv = Money.parse(f.precioVenta)
            when {
                pv == null -> e[CamposInsumo.PRECIO_VENTA] = ProductoFormLogic.FORMATO_IMPORTE
                pv <= Cup.ZERO || (precio != null && pv <= precio) -> e[CamposInsumo.PRECIO_VENTA] = "El precio de venta debe superar el costo y 0 (o déjalo vacío)."
            }
        }
        if (f.nivelBajo.isNotBlank() && Cantidad.parse(f.nivelBajo) == null) e[CamposInsumo.NIVEL_BAJO] = FORMATO_CANTIDAD
        if (f.nivelCritico.isNotBlank() && Cantidad.parse(f.nivelCritico) == null) e[CamposInsumo.NIVEL_CRITICO] = FORMATO_CANTIDAD
        // Reglas de dominio (nombre, negativos, crítico ≤ bajo) sobre el insumo ya interpretado.
        Validadores.insumo(insumo(f, precio ?: Cup.ZERO, cantidad ?: Cantidad.ZERO, Instant.EPOCH))
            .forEach { v -> if (v.campo !in e) mensaje(v)?.let { e[v.campo] = it } }
        return e
    }

    /** Insumo listo para guardar con [cu.shalev.domain.usecase.GuardarInsumo], o null si hay errores. */
    fun aInsumo(f: InsumoForm, ahora: Instant): Insumo? {
        if (validar(f).isNotEmpty()) return null
        return insumo(f, Money.parse(f.precio)!!, Cantidad.parse(f.cantidad)!!, f.creadoEn ?: ahora)
    }

    fun desde(i: Insumo) = InsumoForm(
        id = i.id,
        creadoEn = i.creadoEn,
        nombre = i.nombre,
        precio = ProductoFormLogic.decimal(i.precio),
        cantidad = texto(i.cantidad),
        unidad = i.unidad,
        nivelBajo = i.nivelBajo?.let(::texto).orEmpty(),
        nivelCritico = i.nivelCritico?.let(::texto).orEmpty(),
        precioVenta = i.precioVenta?.let(ProductoFormLogic::decimal).orEmpty(),
    )

    /** Mensajes sin jerga para los errores de dominio y los devueltos al guardar. */
    fun mensaje(e: AppError): String? = when (e) {
        is AppError.Validacion -> when (e.campo) {
            CamposInsumo.NOMBRE -> if (e.regla == AppError.Regla.REQUERIDO) "Escribe el nombre." else "Máximo ${Validadores.MAX_NOMBRE} caracteres."
            CamposInsumo.UNIDAD -> "Este insumo ya tiene existencias, recetas o historial. Crea otro para usar una unidad de medida diferente."
            CamposInsumo.PRECIO -> "El precio de costo no puede ser negativo."
            CamposInsumo.CANTIDAD -> "La cantidad no puede ser negativa."
            CamposInsumo.NIVEL_BAJO -> "El nivel bajo no puede ser negativo."
            CamposInsumo.NIVEL_CRITICO -> "El nivel crítico debe ser 0 o más y no mayor que el nivel bajo."
            CamposInsumo.PRECIO_VENTA -> "El precio de venta debe ser mayor que 0 (o déjalo vacío)."
            else -> null
        }
        else -> null
    }

    /** Nombre de la unidad para el selector (texto claro, el símbolo va entre paréntesis). */
    fun etiqueta(u: UnidadMedida): String = when (u) {
        UnidadMedida.UNIDAD -> "Unidades (u)"
        UnidadMedida.KILOGRAMO -> "Kilogramos (kg)"
        UnidadMedida.GRAMO -> "Gramos (g)"
        UnidadMedida.LITRO -> "Litros (L)"
        UnidadMedida.MILILITRO -> "Mililitros (ml)"
    }

    /** 1250 milésimas → "1.25" (sin separador de miles para que se pueda volver a editar). */
    private fun texto(c: Cantidad): String = java.math.BigDecimal.valueOf(c.milesimas, 3).stripTrailingZeros().toPlainString()

    private fun insumo(f: InsumoForm, precio: Cup, cantidad: Cantidad, creado: Instant) = Insumo(
        id = f.id,
        nombre = f.nombre.trim(),
        unidad = f.unidad,
        precio = precio,
        cantidad = cantidad,
        nivelBajo = f.nivelBajo.takeIf { it.isNotBlank() }?.let(Cantidad::parse),
        nivelCritico = f.nivelCritico.takeIf { it.isNotBlank() }?.let(Cantidad::parse),
        creadoEn = creado,
        precioVenta = f.precioVenta.takeIf { it.isNotBlank() }?.let(Money::parse),
    )

    const val FORMATO_CANTIDAD = "Escribe una cantidad válida, por ejemplo 2.5 (0 o más, hasta 3 decimales)."
}
