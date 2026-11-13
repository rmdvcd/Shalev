package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.TopPersona
import cu.shalev.domain.model.Transaccion
import cu.shalev.domain.model.Venta
import cu.shalev.domain.service.Estadisticas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopsInicioTest {
    private fun detalle(totalPesos: Long) = DetalleVenta(
        productoId = 1, nombre = "P1", categoria = "X", cantidad = 1,
        precioBase = Cup.ofPesos(totalPesos), precioUnitario = Cup.ofPesos(totalPesos),
        costoUnitario = Cup.ofPesos(0),
    )

    private fun venta(id: Long, vendedor: String, totalPesos: Long, cliente: DatosCliente? = null): Venta {
        val tx = cliente?.let {
            Transaccion(fecha = T0, importe = Cup.ofPesos(totalPesos), numero = "TX$id", cliente = it)
        }
        return Venta(
            id, 1, T0,
            if (cliente == null) MetodoPago.EFECTIVO else MetodoPago.TRANSFERENCIA,
            listOf(detalle(totalPesos)), transaccion = tx, vendedor = vendedor,
        )
    }

    @Test fun `top empleados por importe con desempate alfabetico`() {
        val vs = listOf(
            venta(1, "Jorge", 500),
            venta(2, "María", 900),
            venta(3, "Jorge", 200),
            venta(4, "Ana", 900),
            venta(5, "Luis", 100),
        )
        assertEquals(
            listOf(TopPersona("Ana", Cup.ofPesos(900), 1), TopPersona("María", Cup.ofPesos(900), 1), TopPersona("Jorge", Cup.ofPesos(700), 2)),
            Estadisticas.topEmpleados(vs),
        )
    }

    @Test fun `top empleados ignora anuladas y vacio sin ventas`() {
        assertTrue(Estadisticas.topEmpleados(emptyList()).isEmpty())
        val anulada = venta(1, "Jorge", 5000).copy(anulacion = cu.shalev.domain.model.Anulacion(T0, "Cliente desistió", "Jorge"))
        assertTrue(Estadisticas.topEmpleados(listOf(anulada)).isEmpty())
    }

    @Test fun `top clientes solo transferencias por importe`() {
        val ana = DatosCliente("Ana Pérez", "90010112345", "51234567")
        val juan = DatosCliente("Juan Ruiz", "85020212345", "52345678")
        val vs = listOf(
            venta(1, "María", 400, ana),
            venta(2, "María", 100), // efectivo: sin cliente, no cuenta
            venta(3, "Jorge", 700, juan),
            venta(4, "María", 100, ana),
        )
        assertEquals(
            listOf(TopPersona("Juan Ruiz", Cup.ofPesos(700), 1), TopPersona("Ana Pérez", Cup.ofPesos(500), 2)),
            Estadisticas.topClientes(vs),
        )
    }

    @Test fun `top clientes distingue por ci aunque coincida el nombre`() {
        val a1 = DatosCliente("Ana Pérez", "90010112345", "51234567")
        val a2 = DatosCliente("Ana Pérez", "91111112345", "57654321")
        val vs = listOf(venta(1, "María", 300, a1), venta(2, "María", 200, a2))
        assertEquals(2, Estadisticas.topClientes(vs).size)
    }
}
