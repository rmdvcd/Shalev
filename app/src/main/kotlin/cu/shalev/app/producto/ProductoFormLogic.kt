package cu.shalev.app.producto

import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.validation.Validadores
import java.time.Instant
import java.time.LocalDate

/** Línea de receta en edición: la cantidad es texto (validación en tiempo real). */
data class LineaRecetaForm(val insumoId: Long, val nombre: String, val simbolo: String, val precio: Cup, val cantidad: String = "1")

/**
 * Formulario de producto con los atributos EXACTOS del Prompt Maestro, en su orden:
 * categoría, foto (no Elaborado), nombre, descripción, receta (solo Elaborado), fecha de caducidad (no Elaborado),
 * precio costo, precio venta, cantidad, nivel bajo, nivel crítico, código (no Elaborado).
 * P26: un Elaborado no tiene existencias ni niveles propios (se vende mientras alcancen los insumos), así que
 * no pide cantidad ni niveles y se guarda con cantidad 0 y niveles vacíos.
 */
data class ProductoForm(
    val id: Long = 0,
    val creadoEn: Instant? = null,
    val categoria: String = "",
    val fotoUri: String? = null,
    val nombre: String = "",
    val descripcion: String = "",
    val receta: List<LineaRecetaForm> = emptyList(),
    val fechaCaducidad: LocalDate? = null,
    val precioCosto: String = "",
    val precioVenta: String = "",
    val cantidad: String = "",
    val nivelBajo: String = "",
    val nivelCritico: String = "",
) {
    val esElaborado: Boolean get() = Categorias.esElaborado(categoria)
}

object Campos {
    const val CATEGORIA = "categoria"
    const val NOMBRE = "nombre"
    const val DESCRIPCION = "descripcion"
    const val RECETA = "receta"
    const val PRECIO_COSTO = "precioCosto"
    const val PRECIO_VENTA = "precioVenta"
    const val CANTIDAD = "cantidad"
    const val NIVEL_BAJO = "nivelBajo"
    const val NIVEL_CRITICO = "nivelCritico"
}

object ProductoFormLogic {

    /** Costo de UNA unidad del Elaborado según la receta; null si alguna cantidad no es válida o no hay líneas. */
    fun costoReceta(lineas: List<LineaRecetaForm>): Cup? {
        if (lineas.isEmpty()) return null
        return lineas.fold(Cup.ZERO) { acc, l ->
            val c = Cantidad.parse(l.cantidad)?.takeIf { it > Cantidad.ZERO } ?: return null
            acc + l.precio.porMilesimas(c.milesimas)
        }
    }

    /**
     * Todos los errores del formulario (campo → mensaje). Primero los de formato (lo que se teclea); después,
     * sobre el producto ya interpretado, las reglas de dominio ([Validadores.producto]) para no duplicarlas.
     */
    fun validar(f: ProductoForm): Map<String, String> {
        val e = linkedMapOf<String, String>()
        val costo = if (f.esElaborado) costoReceta(f.receta) ?: Cup.ZERO else Money.parse(f.precioCosto)
        if (!f.esElaborado) {
            when {
                f.precioCosto.isBlank() -> e[Campos.PRECIO_COSTO] = "Escribe el precio de costo."
                costo == null -> e[Campos.PRECIO_COSTO] = FORMATO_IMPORTE
            }
        }
        val venta = Money.parse(f.precioVenta)
        when {
            f.precioVenta.isBlank() -> e[Campos.PRECIO_VENTA] = "Escribe el precio de venta."
            venta == null -> e[Campos.PRECIO_VENTA] = FORMATO_IMPORTE
        }
        val cantidad = if (f.esElaborado) 0L else entero(f.cantidad)
        if (!f.esElaborado) {
            when {
                f.cantidad.isBlank() -> e[Campos.CANTIDAD] = "Escribe cuántas unidades tienes (puede ser 0)."
                cantidad == null -> e[Campos.CANTIDAD] = FORMATO_ENTERO
            }
            if (f.nivelBajo.isNotBlank() && entero(f.nivelBajo) == null) e[Campos.NIVEL_BAJO] = FORMATO_ENTERO
            if (f.nivelCritico.isNotBlank() && entero(f.nivelCritico) == null) e[Campos.NIVEL_CRITICO] = FORMATO_ENTERO
        }
        if (f.esElaborado) {
            when {
                f.receta.isEmpty() -> e[Campos.RECETA] = "Agrega al menos un insumo a la receta."
                f.receta.any { l -> Cantidad.parse(l.cantidad)?.let { it > Cantidad.ZERO } != true } ->
                    e[Campos.RECETA] = "Cada insumo necesita una cantidad mayor que 0 (hasta 3 decimales)."
            }
        }
        val p = producto(f, costo ?: Cup.ZERO, venta ?: Cup(1), cantidad ?: 0, Instant.EPOCH)
        Validadores.producto(p).forEach { v -> if (v.campo !in e) mensaje(v)?.let { e[v.campo] = it } }
        return e
    }

    /** Producto listo para guardar, o null si el formulario tiene errores. */
    fun aProducto(f: ProductoForm, ahora: Instant): Pair<Producto, Receta?>? {
        if (validar(f).isNotEmpty()) return null
        val costo = if (f.esElaborado) costoReceta(f.receta)!! else Money.parse(f.precioCosto)!!
        val p = producto(f, costo, Money.parse(f.precioVenta)!!, if (f.esElaborado) 0 else entero(f.cantidad)!!, f.creadoEn ?: ahora)
        val receta = if (f.esElaborado) Receta(f.id, f.receta.map { RecetaLinea(it.insumoId, Cantidad.parse(it.cantidad)!!) }) else null
        return p to receta
    }

    fun desde(p: Producto, receta: Receta?, insumos: Map<Long, Insumo>): ProductoForm = ProductoForm(
        id = p.id,
        creadoEn = p.creadoEn,
        categoria = p.categoria,
        fotoUri = p.fotoUri,
        nombre = p.nombre,
        descripcion = p.descripcion.orEmpty(),
        receta = receta?.lineas.orEmpty().mapNotNull { l ->
            insumos[l.insumoId]?.let { LineaRecetaForm(it.id, it.nombre, it.unidad.simbolo, it.precio, Cantidad.format(l.cantidad)) }
        },
        fechaCaducidad = p.fechaCaducidad,
        precioCosto = decimal(p.precioCosto),
        precioVenta = decimal(p.precioVenta),
        cantidad = p.cantidad.toString(),
        nivelBajo = p.nivelBajo?.toString().orEmpty(),
        nivelCritico = p.nivelCritico?.toString().orEmpty(),
    )

    // ---------------- 0.24.0: Descripción e identificación ----------------

    const val EJEMPLO_DESCRIPCION = "Lata 350 ml Superior"
    const val DESCRIPCION_REPETIDA = "Ya hay uno con este nombre y esta descripción. Escribe otra que lo diferencie."

    /** Texto de ayuda bajo el campo: para qué sirve y cuánto queda. */
    fun ayudaDescripcion(descripcion: String): String =
        "Lo que lo distingue de otros con el mismo nombre · ${descripcion.trim().length}/${Validadores.MAX_DESCRIPCION}"

    /** [articulo] = «producto» o «servicio». */
    fun mensajeDescripcion(regla: AppError.Regla, articulo: String): String = when (regla) {
        AppError.Regla.REQUERIDO -> "Ya hay otro $articulo con este nombre. Escribe una descripción que lo diferencie (p. ej. $EJEMPLO_DESCRIPCION)."
        AppError.Regla.FORMATO -> "Escribe la descripción en una sola línea."
        else -> "Máximo ${Validadores.MAX_DESCRIPCION} caracteres."
    }

    /**
     * Comprobación en vivo de la regla de [cu.shalev.domain.model.Identificacion] contra los demás artículos activos
     * ([otros] = id, nombre, descripción). La misma regla se aplica al guardar en el caso de uso.
     */
    fun errorIdentidad(id: Long, nombre: String, descripcion: String, otros: List<Triple<Long, String, String?>>, articulo: String): String? {
        if (nombre.isBlank()) return null
        val iguales = otros.filter { it.first != id && cu.shalev.domain.model.Identificacion.clave(it.second) == cu.shalev.domain.model.Identificacion.clave(nombre) }
        return when (cu.shalev.domain.model.Identificacion.conflicto(id, nombre, descripcion, otros)) {
            cu.shalev.domain.model.Identificacion.Conflicto.FALTA_DESCRIPCION -> {
                val existentes = iguales.mapNotNull { it.third?.trim()?.ifEmpty { null } }
                val cuales = if (existentes.isEmpty()) "" else " Ya existe: " + existentes.take(3).joinToString(", ") { "«$it»" } + "."
                mensajeDescripcion(AppError.Regla.REQUERIDO, articulo) + cuales
            }
            cu.shalev.domain.model.Identificacion.Conflicto.DESCRIPCION_REPETIDA -> DESCRIPCION_REPETIDA
            null -> null
        }
    }

    /** Elimina los saltos de línea (la descripción es de una línea). */
    fun unaLinea(v: String): String = v.replace(Regex("[\r\n]+"), " ")

    /**
     * Limpia el nombre inicial recibido por la ruta: decodifica entidades básicas, colapsa el espacio en blanco,
     * quita la puntuación colgada al final y corta a [max].
     */
    fun limpiarNombre(v: String, max: Int): String = v
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("\\s+"), " ").trim().trimEnd(',', ';', '.', ':').take(max)

    /** Mensaje claro por campo (sin jerga) para los errores de dominio y los devueltos al guardar. */
    fun mensaje(e: AppError): String? = when (e) {
        is AppError.Validacion -> when (e.campo) {
            Campos.NOMBRE -> if (e.regla == AppError.Regla.REQUERIDO) "Escribe el nombre." else "Máximo ${Validadores.MAX_NOMBRE} caracteres."
            Campos.CATEGORIA -> when (e.regla) {
                AppError.Regla.REQUERIDO -> "Elige o escribe una categoría."
                AppError.Regla.NO_PERMITIDO -> "«Insumos» es solo para insumos: créalos en Agregar → Insumo."
                else -> "Máximo ${Validadores.MAX_CATEGORIA} caracteres."
            }
            Campos.DESCRIPCION -> mensajeDescripcion(e.regla, "producto")
            Campos.PRECIO_COSTO -> "El precio de costo no puede ser negativo."
            Campos.PRECIO_VENTA -> "El precio de venta debe ser mayor que el costo y que 0."
            Campos.CANTIDAD -> "La cantidad no puede ser negativa."
            Campos.NIVEL_BAJO -> "El nivel bajo no puede ser negativo."
            Campos.NIVEL_CRITICO -> "El nivel crítico debe ser 0 o más y no mayor que el nivel bajo."
            Campos.RECETA -> if (e.regla == AppError.Regla.REQUERIDO) "Agrega al menos un insumo a la receta." else "Revisa las cantidades de la receta."
            else -> null
        }
        is AppError.Duplicado -> when (e.campo) {
            Campos.DESCRIPCION -> DESCRIPCION_REPETIDA
            else -> "Ya existe."
        }
        else -> null
    }

    private fun producto(f: ProductoForm, costo: Cup, venta: Cup, cantidad: Long, creado: Instant) = Producto(
        id = f.id,
        categoria = f.categoria.trim(),
        nombre = f.nombre.trim(),
        descripcion = f.descripcion.trim().ifEmpty { null },
        fotoUri = f.fotoUri.takeUnless { f.esElaborado },
        fechaCaducidad = f.fechaCaducidad.takeUnless { f.esElaborado },
        precioCosto = costo,
        precioVenta = venta,
        cantidad = if (f.esElaborado) 0 else cantidad,
        nivelBajo = entero(f.nivelBajo).takeUnless { f.esElaborado },
        nivelCritico = entero(f.nivelCritico).takeUnless { f.esElaborado },
        creadoEn = creado,
    )

    /** Entero ≥ 0 (unidades). */
    fun entero(s: String): Long? = s.trim().takeIf { it.matches(Regex("^[0-9]{1,9}$")) }?.toLong()

    /** 1450.5 CUP → "1450.50" (lo que se ve al editar; se acepta también con comas de miles). */
    /** Valor monetario visible/editable: entero positivo, coherente con Money.format. */
    fun decimal(c: Cup): String = Money.format(c).removeSuffix(" CUP").replace(",", "")

    const val FORMATO_IMPORTE = "Escribe un importe válido, por ejemplo 25.50 (máximo 2 decimales)."
    const val FORMATO_ENTERO = "Escribe un número entero, 0 o más."
}
