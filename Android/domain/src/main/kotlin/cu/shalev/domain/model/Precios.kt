package cu.shalev.domain.model

import cu.shalev.core.money.Cup

/** La única condición que activa un ajuste de precios. */
enum class CondicionPreajuste { METODO_PAGO, VOLUMEN_VENTA, CLIENTE }

/** Tipo declarado al cobrar; sirve para distinguir una venta particular de una corporativa. */
enum class TipoClientePreajuste { PARTICULAR, EMPRESA }

/**
 * Preajuste de precios: ±[puntosBasicos] (1000 = +10 %) sobre [productoIds] y [servicioIds]. Cada regla tiene una
 * sola [condicion]: método de pago (null = todos), volumen mínimo de unidades o tipo de cliente. [importeMinimo] se
 * conserva únicamente para leer respaldos antiguos; desde v14 no se edita ni interviene en el cálculo.
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
    val condicion: CondicionPreajuste = CondicionPreajuste.METODO_PAGO,
    /** Unidades de toda la venta a partir de las que aplica [CondicionPreajuste.VOLUMEN_VENTA]. */
    val volumenMinimo: Long? = null,
    val tipoCliente: TipoClientePreajuste? = null,
)
