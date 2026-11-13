package cu.shalev.domain.model

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import java.time.Instant
import java.time.LocalDate

/** Cantidades de gestión en milésimas para productos e insumos (un producto debe ser múltiplo de 1000). */
data class ArticuloGestion(val entidad: TipoEntidad, val id: Long, val nombre: String, val unidad: UnidadMedida, val cantidad: Cantidad, val costo: Cup)
data class LineaRecepcion(val entidad: TipoEntidad, val id: Long, val cantidad: Cantidad, val costoUnitario: Cup, val lote: String, val caducidad: LocalDate? = null)
data class NuevaRecepcion(val uuid: String, val proveedor: String, val comprobante: String, val motivo: String, val lineas: List<LineaRecepcion>)
data class RecepcionInventario(val uuid: String, val fecha: Instant, val proveedor: String, val comprobante: String, val motivo: String, val autor: String, val total: Cup)
data class LoteInventario(val id: String, val recepcionId: String, val entidad: TipoEntidad, val entidadId: Long, val nombre: String, val unidad: String, val codigo: String, val inicial: Cantidad, val disponible: Cantidad, val costo: Cup, val caducidad: LocalDate?)
enum class MotivoMovimiento(val titulo: String) { COMPRA("Compra"), MERMA("Merma"), CONSUMO_INTERNO("Consumo interno"), CORRECCION("Corrección de conteo"), DEVOLUCION("Devolución") }
