package cu.shalev.domain.usecase

import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.result.map
import cu.shalev.core.result.toResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.Identificacion
import cu.shalev.domain.model.identidad
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Receta
import cu.shalev.domain.repository.InsumoRepository
import cu.shalev.domain.repository.ProductoRepository
import cu.shalev.domain.service.Recetas
import cu.shalev.domain.validation.Validadores
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ObtenerCategorias @Inject constructor(private val repo: ProductoRepository) {
    suspend operator fun invoke(): List<String> = Categorias.combinar(repo.categoriasEnUso())
}

/**
 * Crea o edita un producto. Normaliza campos, aplica las reglas de Elaborado (receta obligatoria,
 * costo calculado, sin foto/caducidad/código), valida y rechaza códigos duplicados.
 */
class GuardarProducto @Inject constructor(
    private val productos: ProductoRepository,
    private val insumos: InsumoRepository,
    private val clock: Clock,
) {
    /**
     * [cantidadLeida]: existencia mostrada al abrir la ficha (0.21.6, ver `Stock.cantidadAlGuardar`); null = manda la escrita.
     * [exigirDiferenciar] (0.24.0): aplica las reglas de la descripción: [Identificacion] (mismo nombre →
     * descripción distinta) y su formato (una línea, 40 caracteres). Solo el escáner lo apaga al actualizar la caducidad de un producto existente sin tocar su nombre ni su descripción.
     */
    suspend operator fun invoke(
        producto: Producto, receta: Receta?, cantidadLeida: Long? = null, exigirDiferenciar: Boolean = true,
    ): AppResult<Long> {
        val now = clock.now()
        var p = producto.copy(
            nombre = producto.nombre.trim(),
            categoria = producto.categoria.trim(),
            descripcion = producto.descripcion?.trim()?.ifEmpty { null },
        )
        val r: Receta?
        if (p.esElaborado) {
            // P26: sin existencias ni niveles propios (se vende mientras alcancen los insumos).
            p = p.copy(categoria = Categorias.ELABORADO, fotoUri = null, fechaCaducidad = null, cantidad = 0, nivelBajo = null, nivelCritico = null)
            Validadores.receta(receta).toResult().let { if (it is AppResult.Err) return it }
            r = receta!!.copy(productoId = p.id)
            val costo = Recetas.costo(r, insumos.obtenerVarios(r.lineas.map { it.insumoId }).associateBy { it.id })
            p = p.copy(precioCosto = ((costo as? AppResult.Ok)?.value ?: return costo as AppResult.Err) + p.costoAdicional)
        } else {
            r = null
        }
        // Sin exigir: una descripción anterior a 0.24.0 (más larga o de varias líneas) no impide guardar otro cambio.
        Validadores.producto(p).filter { exigirDiferenciar || it.campo != "descripcion" }.toResult().let { if (it is AppResult.Err) return it }
        if (exigirDiferenciar) errorIdentificacion(p.id, p.nombre, p.descripcion, productos.observarTodos().first().map { it.identidad })
            ?.let { return AppResult.Err(it) }

        return if (p.id == 0L) {
            productos.crear(p.copy(creadoEn = now, actualizadoEn = now), r)
        } else {
            val actual = productos.obtener(p.id)?.takeUnless { it.eliminado } ?: return AppResult.Err(AppError.NoEncontrado)
            productos.actualizar(p.copy(creadoEn = actual.creadoEn, actualizadoEn = now), r, cantidadLeida).map { p.id }
        }
    }
}

/**
 * 0.24.0: error de la regla «mismo nombre → descripción distinta» ([Identificacion]), o null si se distingue.
 * Falta descripción → Validacion("descripcion", REQUERIDO); descripción repetida → Duplicado("descripcion").
 */
fun errorIdentificacion(id: Long, nombre: String, descripcion: String?, otros: List<Triple<Long, String, String?>>): AppError? =
    when (Identificacion.conflicto(id, nombre, descripcion, otros)) {
        Identificacion.Conflicto.FALTA_DESCRIPCION -> AppError.Validacion("descripcion", AppError.Regla.REQUERIDO)
        Identificacion.Conflicto.DESCRIPCION_REPETIDA -> AppError.Duplicado("descripcion")
        null -> null
    }
