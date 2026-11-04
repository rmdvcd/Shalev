package cu.shalev.app.registros

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.ResumenTurno
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.Turno
import cu.shalev.domain.model.Venta
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TurnoLogicTest {
    private val zona = ZoneId.of("America/Havana") // UTC-4 en septiembre
    private val a = Instant.parse("2026-09-30T12:30:00Z") // 08:30

    @Test fun horarioDelTurno() {
        assertEquals("30/09 desde las 08:30", horarioTurno(Turno(1, a), zona))
        assertEquals("30/09 08:30 – 17:00", horarioTurno(Turno(1, a, Instant.parse("2026-09-30T21:00:00Z")), zona))
        assertEquals("30/09 08:30 – 01/10 02:00", horarioTurno(Turno(1, a, Instant.parse("2026-10-01T06:00:00Z")), zona))
    }

    @Test fun duracionLegible() {
        assertEquals("0 min", duracionTexto(Duration.ZERO))
        assertEquals("45 min", duracionTexto(Duration.ofMinutes(45)))
        assertEquals("8 h", duracionTexto(Duration.ofHours(8)))
        assertEquals("8 h 30 min", duracionTexto(Duration.ofMinutes(510)))
    }

    @Test fun filaDelRegistro() {
        val abierto = Turno(3, a, abiertoPor = "Ana Pérez")
        assertEquals("30/09/2026", tituloTurno(abierto, zona)) // por fecha, nunca «Turno #3»
        assertEquals("Turno del 30/09/2026", tituloDetalleTurno(abierto, zona))
        assertEquals("Desde las 08:30 · Ana Pérez", subtituloTurno(abierto, zona))
        assertEquals("Abierto", valorTurno(abierto))
        val cerrado = abierto.copy(cerradoEn = a.plusSeconds(3600), resumen = ResumenTurno.VACIO.copy(total = Cup.ofPesos(1450)))
        assertEquals("1,450 CUP", valorTurno(cerrado))
        assertEquals("08:30 – 09:30", subtituloTurno(cerrado.copy(abiertoPor = ""), zona))
        assertEquals("08:30 – 01/10 08:30", horasTurno(cerrado.copy(cerradoEn = a.plusSeconds(86_400)), zona))
    }

    @Test fun cantidadesConSigno() {
        assertEquals("-2", cantidadConSigno(-2, TipoEntidad.PRODUCTO))
        assertEquals("+3", cantidadConSigno(3, TipoEntidad.PRODUCTO))
        assertEquals("-1.25 kg", cantidadConSigno(-1_250, TipoEntidad.INSUMO, "kg"))
        assertEquals("+0.5", cantidadConSigno(500, TipoEntidad.INSUMO))
        assertEquals("-0.1 L", cantidadInsumo(Cantidad(-100), "L"))
    }

    @Test fun textosDeMovimientosYVentas() {
        val m = MovimientoInventario(fecha = a, tipo = TipoMovimiento.CONSUMO, entidad = TipoEntidad.INSUMO, entidadId = 1, nombre = "Harina",
            delta = -1000, existenciaResultante = 0, nota = " merma ")
        assertEquals("08:30 · Consumo · merma", subtituloMovimiento(m, zona))
        val d = { n: String, c: Long -> DetalleVenta(productoId = 1, nombre = n, categoria = "C", cantidad = c, precioBase = Cup.ofPesos(1), precioUnitario = Cup.ofPesos(1), costoUnitario = Cup.ZERO) }
        val v = Venta(turnoId = 1, fecha = a, metodoPago = MetodoPago.TRANSFERENCIA, detalles = listOf(d("Refresco", 2), d("Pan", 1), d("Café", 1), d("Té", 4)))
        assertEquals("08:30 · Transferencia", tituloVenta(v, zona))
        assertEquals("2 × Refresco, 1 × Pan, 1 × Café y 1 más", subtituloVenta(v))
        assertEquals("1 venta", ventasTexto(1))
        assertEquals("2 unidades", unidadesTexto(2))
    }
}
