package cu.shalev.app.servicios

import cu.shalev.app.producto.LineaRecetaForm
import cu.shalev.app.producto.ProductoFormLogic
import cu.shalev.core.money.Cup
import cu.shalev.core.money.Money
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.Servicio
import cu.shalev.domain.validation.Validadores
import java.time.Instant

/** P29: formulario de servicio. Nombre, Tipo e Importe obligatorios; Foto, Descripción e insumos opcionales. */
data class ServicioForm(
    val id: Long = 0,
    val creadoEn: Instant? = null,
    val nombre: String = "",
    val tipo: String = "",
    val importe: String = "",
    val descripcion: String = "",
    val fotoUri: String? = null,
    /** Insumos que gasta UNA vez el servicio (misma línea de edición que la receta de un Elaborado). */
    val insumos: List<LineaRecetaForm> = emptyList(),
    val costoAdicional: Cup = Cup.ZERO,
)

/** Coinciden con los campos de [Validadores.servicio]. */
object CamposServicio {
    const val NOMBRE = "nombre"
    const val TIPO = "tipo"
    const val IMPORTE = "importe"
    const val DESCRIPCION = "descripcion"
    const val INSUMOS = "insumos"
}

object ServicioFormLogic {

    fun validar(f: ServicioForm): Map<String, String> {
        val e = linkedMapOf<String, String>()
        val importe = Money.parse(f.importe)
        when {
            f.importe.isBlank() -> e[CamposServicio.IMPORTE] = "Escribe el importe."
            importe == null -> e[CamposServicio.IMPORTE] = ProductoFormLogic.FORMATO_IMPORTE
        }
        if (f.tipo.isBlank()) e[CamposServicio.TIPO] = "Elige o escribe un tipo."
        if (f.insumos.any { l -> Cantidad.parse(l.cantidad)?.let { it > Cantidad.ZERO } != true }) {
            e[CamposServicio.INSUMOS] = "Cada insumo necesita una cantidad mayor que 0 (hasta 3 decimales)."
        }
        Validadores.servicio(servicio(f, importe ?: Cup(1), Instant.EPOCH), lineasValidas(f))
            .forEach { v -> if (v.campo !in e) mensaje(v)?.let { e[v.campo] = it } }
        return e
    }

    /** Servicio + insumos listos para [cu.shalev.domain.usecase.GuardarServicio], o null si hay errores. */
    fun aServicio(f: ServicioForm, ahora: Instant): Pair<Servicio, List<RecetaLinea>>? {
        if (validar(f).isNotEmpty()) return null
        return servicio(f, Money.parse(f.importe)!!, f.creadoEn ?: ahora) to lineasValidas(f)
    }

    fun desde(s: Servicio, lineas: List<RecetaLinea>, insumos: Map<Long, Insumo>) = ServicioForm(
        id = s.id, creadoEn = s.creadoEn, nombre = s.nombre, tipo = s.tipo, importe = ProductoFormLogic.decimal(s.importe),
        costoAdicional = s.costoAdicional,
        descripcion = s.descripcion.orEmpty(), fotoUri = s.fotoUri,
        insumos = lineas.mapNotNull { l ->
            insumos[l.insumoId]?.let { LineaRecetaForm(it.id, it.nombre, it.unidad.simbolo, it.precio, Cantidad.format(l.cantidad)) }
        },
    )

    /** Costo de los insumos de UNA vez; null si alguna cantidad no es válida. Cero si no gasta insumos. */
    fun costo(f: ServicioForm): Cup? = (if (f.insumos.isEmpty()) Cup.ZERO else ProductoFormLogic.costoReceta(f.insumos))?.plus(f.costoAdicional)

    fun mensaje(e: AppError): String? = when (e) {
        is AppError.Validacion -> when (e.campo) {
            CamposServicio.NOMBRE -> if (e.regla == AppError.Regla.REQUERIDO) "Escribe el nombre." else "Máximo ${Validadores.MAX_NOMBRE} caracteres."
            CamposServicio.TIPO -> if (e.regla == AppError.Regla.REQUERIDO) "Elige o escribe un tipo." else "Máximo ${Validadores.MAX_CATEGORIA} caracteres."
            CamposServicio.IMPORTE -> "El importe debe ser mayor que 0."
            CamposServicio.DESCRIPCION -> ProductoFormLogic.mensajeDescripcion(e.regla, "servicio")
            CamposServicio.INSUMOS -> "Revisa las cantidades de los insumos."
            else -> null
        }
        is AppError.Duplicado -> if (e.campo == CamposServicio.DESCRIPCION) ProductoFormLogic.DESCRIPCION_REPETIDA else null
        else -> null
    }

    private fun lineasValidas(f: ServicioForm) = f.insumos.mapNotNull { l ->
        Cantidad.parse(l.cantidad)?.let { RecetaLinea(l.insumoId, it) }
    }

    private fun servicio(f: ServicioForm, importe: Cup, creado: Instant) = Servicio(
        id = f.id,
        nombre = f.nombre.trim(),
        tipo = f.tipo.trim(),
        importe = importe,
        descripcion = f.descripcion.trim().ifEmpty { null },
        fotoUri = f.fotoUri,
        creadoEn = creado,
        costoAdicional = f.costoAdicional,
    )
}
