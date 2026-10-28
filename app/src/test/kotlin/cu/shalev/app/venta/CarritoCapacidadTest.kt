package cu.shalev.app.venta

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.ElaboradoDisponible
import cu.shalev.domain.model.IdArticulo
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.LineaProduccion
import cu.shalev.domain.model.LineaServicio
import cu.shalev.domain.model.Producto
import cu.shalev.domain.model.Servicio
import cu.shalev.domain.model.ServicioDisponible
import cu.shalev.domain.model.UnidadMedida
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CarritoCapacidadTest {
    private val t0 = Instant.parse("2026-10-28T12:00:00Z")

    private fun insumo(id: Long, nombre: String, mil: Long, venta: Long? = null) = Insumo(
        id = id,
        nombre = nombre,
        unidad = UnidadMedida.KILOGRAMO,
        precio = Cup.ofPesos(100),
        cantidad = Cantidad(mil),
        precioVenta = venta?.let(Cup::ofPesos),
        creadoEn = t0,
    )

    private fun elaborado(id: Long, nombre: String, consumoMil: Long, insumo: Insumo): Pair<Producto, ElaboradoDisponible> {
        val prod = Producto(
            id = id,
            categoria = "Elaborado",
            nombre = nombre,
            precioCosto = Cup.ofPesos(50),
            precioVenta = Cup.ofPesos(150),
            cantidad = 0,
            creadoEn = t0,
        )
        val disp = ElaboradoDisponible(
            producto = prod,
            lineas = listOf(
                LineaProduccion(
                    insumoId = insumo.id,
                    nombre = insumo.nombre,
                    simbolo = insumo.unidad.simbolo,
                    porUnidad = Cantidad(consumoMil),
                    disponible = insumo.cantidad,
                    existe = true,
                ),
            ),
            costoUnidad = Cup.ofPesos(50),
        )
        return prod to disp
    }

    @Test
    fun elaboradosEInsumoVendibleCompartenElMismoTopeDeExistencias() {
        val queso = insumo(1L, "Queso", mil = 1_000L, venta = 300L)
        val (pizzaA, dispA) = elaborado(10L, "Pizza Napolitana", 200L, queso) // hasta 5 sola
        val (pizzaB, dispB) = elaborado(11L, "Pizza Jamón", 250L, queso)      // hasta 4 sola
        val cat = CatalogoVenta.deProductos(listOf(pizzaA, pizzaB), listOf(dispA, dispB), listOf(queso))

        // Con 3 Pizza Napolitana (600 g usados), quedan 400 g: Pizza Jamón solo alcanza para 1 y el kg entero de Queso para 0.
        val carrito = linkedMapOf(10L to 3L)
        val topes = Carrito.topesCompartidos(carrito, cat)
        assertEquals(5L, topes[10L])
        assertEquals(1L, topes[11L])
        assertEquals(0L, topes[IdArticulo.deInsumo(1L)])

        // Si el usuario intenta pedir 3 de Napolitana + 3 de Jamón + 1 kg de Queso, se recorta a 3 + 1 y se descarta el kg agotado.
        val recortado = Carrito.recortarACapacidad(
            linkedMapOf(10L to 3L, 11L to 3L, IdArticulo.deInsumo(1L) to 1L),
            cat,
        )
        assertEquals(linkedMapOf(10L to 3L, 11L to 1L), recortado)
    }

    @Test
    fun serviciosConInsumosCompartidosYDevolucionAlModificarRespetanLaCapacidad() {
        val shampoo = insumo(5L, "Champú", mil = 300L)
        fun serv(id: Long, nombre: String, mil: Long) = ServicioDisponible(
            servicio = Servicio(id = id, nombre = nombre, tipo = "Peluquería", importe = Cup.ofPesos(200), creadoEn = t0),
            lineas = listOf(LineaServicio(shampoo.id, shampoo.nombre, "ml", Cantidad(mil), shampoo.cantidad, existe = true)),
            costo = Cup.ofPesos(30),
        )
        val cat = CatalogoVenta.deServicios(mapOf(20L to serv(20L, "Lavado corto", 100L), 21L to serv(21L, "Lavado largo", 200L)))

        // En una venta original había 2 Lavados largos (400 ml que volverán al inventario: 300 + 400 = 700 ml totales).
        val devueltos = mapOf(21L to 2L)
        val topes = Carrito.topesCompartidos(mapOf(21L to 2L, 20L to 1L), cat, devueltos)
        assertEquals(3L, topes[21L]) // tras reservar 100 ml del corto quedan 600 ml → 3 largos
        assertEquals(3L, topes[20L]) // tras reservar 400 ml de 2 largos quedan 300 ml → 3 cortos
    }
}
