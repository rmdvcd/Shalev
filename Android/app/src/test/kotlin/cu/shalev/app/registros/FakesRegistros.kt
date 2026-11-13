package cu.shalev.app.registros

import cu.shalev.app.T0
import cu.shalev.core.money.Cup
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Venta
import cu.shalev.domain.repository.FiltroRegistro
import cu.shalev.domain.repository.RegistroRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Mismas reglas que las consultas de :data: fecha en [desde, hasta), importe inclusivo, más recientes primero. */
class RegRepo : RegistroRepository {
    val ventas = MutableStateFlow<List<Venta>>(emptyList())
    val transacciones = MutableStateFlow<List<Transaccion>>(emptyList())
    val movimientos = MutableStateFlow<List<MovimientoInventario>>(emptyList())
    val consultas = mutableListOf<FiltroRegistro>()
    var fallar = false

    private fun FiltroRegistro.fecha(i: Instant) = (desde?.let { i >= it } ?: true) && (hasta?.let { i < it } ?: true)
    private fun FiltroRegistro.importe(c: Cup) = (importeMin?.let { c >= it } ?: true) && (importeMax?.let { c <= it } ?: true)
    private fun <T> vigilar(f: FiltroRegistro, fuente: Flow<T>): Flow<T> {
        consultas += f
        return if (fallar) flow { error("disco") } else fuente
    }

    override fun ventas(filtro: FiltroRegistro): Flow<List<Venta>> =
        vigilar(filtro, ventas.map { l -> l.filter { filtro.fecha(it.fecha) && filtro.importe(it.total) }.sortedByDescending { it.fecha } })
    override fun transacciones(filtro: FiltroRegistro): Flow<List<Transaccion>> =
        vigilar(filtro, transacciones.map { l -> l.filter { filtro.fecha(it.fecha) && filtro.importe(it.importe) }.sortedByDescending { it.fecha } })
    override fun movimientos(filtro: FiltroRegistro): Flow<List<MovimientoInventario>> =
        vigilar(filtro, movimientos.map { l -> l.filter { filtro.fecha(it.fecha) }.sortedByDescending { it.fecha } })
    override fun vendedores(): Flow<List<String>> = ventas.map { l -> l.map { it.vendedor }.filter { it.isNotBlank() }.distinct().sorted() }
}

/** Venta de [unidades] × [nombre] a [precio] CUP; minutos después de T0 (12:00 en La Habana). */
fun venta(id: Long, precio: Long, minutos: Long = id, nombre: String = "Refresco", unidades: Long = 1, metodo: MetodoPago = MetodoPago.EFECTIVO) = Venta(
    id = id, turnoId = 1, fecha = T0.plusSeconds(minutos * 60), metodoPago = metodo,
    detalles = listOf(
        DetalleVenta(productoId = id, nombre = nombre, categoria = "Bebidas", cantidad = unidades, precioBase = Cup.ofPesos(precio),
            precioUnitario = Cup.ofPesos(precio), costoUnitario = Cup.ofPesos(precio / 2)),
    ),
    transaccion = if (metodo == MetodoPago.TRANSFERENCIA) transferencia(id, precio * unidades, minutos) else null,
)

fun transferencia(ventaId: Long, importe: Long, minutos: Long = ventaId, cliente: String = "Ana Díaz") = Transaccion(
    id = ventaId, ventaId = ventaId, fecha = T0.plusSeconds(minutos * 60), importe = Cup.ofPesos(importe), numero = "TM$ventaId",
    cliente = DatosCliente(cliente, "90020212345", "53000000"),
)

fun movimiento(id: Long, nombre: String, delta: Long, entidad: TipoEntidad = TipoEntidad.PRODUCTO, tipo: TipoMovimiento = TipoMovimiento.VENTA, minutos: Long = id) =
    MovimientoInventario(id = id, fecha = T0.plusSeconds(minutos * 60), tipo = tipo, entidad = entidad, entidadId = id, nombre = nombre,
        delta = delta, existenciaResultante = 10, turnoId = 1)
