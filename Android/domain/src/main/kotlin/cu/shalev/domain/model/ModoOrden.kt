package cu.shalev.domain.model

/**
 * Modos de ordenación de listas (Inventario y Servicios):
 * - [CRONOLOGICO]: Orden por defecto según fecha de creación / registro (más reciente primero).
 * - [ALFABETICO]: Alfabéticamente por nombre (A-Z).
 * - [IMPORTE_DESC]: Por importe o precio de venta de mayor a menor.
 * - [IMPORTE_ASC]: Por importe o precio de venta de menor a mayor.
 */
enum class ModoOrden {
    CRONOLOGICO,
    ALFABETICO,
    IMPORTE_DESC,
    IMPORTE_ASC;

    fun siguiente(): ModoOrden = when (this) {
        CRONOLOGICO -> ALFABETICO
        ALFABETICO -> IMPORTE_DESC
        IMPORTE_DESC -> IMPORTE_ASC
        IMPORTE_ASC -> CRONOLOGICO
    }
}
