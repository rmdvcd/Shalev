package cu.shalev.data

import cu.shalev.core.money.Cup
import cu.shalev.data.repository.conVendedores
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Venta
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.20.0 (H1): el vendedor llega del turno (JOIN en lectura); `entre`/`deTurno` deben rellenarlo igual que `obtener`. */
class VendedoresVentaTest {

    private fun venta(id: Long) = Venta(
        id = id,
        turnoId = 7,
        fecha = Instant.parse("2026-10-01T12:00:00Z"),
        metodoPago = MetodoPago.EFECTIVO,
        detalles = listOf(
            DetalleVenta(productoId = 1, nombre = "Pan", categoria = "Pan", cantidad = 1, precioBase = Cup.ofPesos(100), precioUnitario = Cup.ofPesos(100), costoUnitario = Cup.ofPesos(60)),
        ),
    )

    @Test fun rellenaVendedorDesdeElMapa() {
        val r = listOf(venta(1), venta(2)).conVendedores(mapOf(1L to "Ana Pérez"))
        assertEquals("Ana Pérez", r[0].vendedor)
        assertEquals("", r[1].vendedor) // sin turno asociado: vacío, como `obtener`
    }
}
