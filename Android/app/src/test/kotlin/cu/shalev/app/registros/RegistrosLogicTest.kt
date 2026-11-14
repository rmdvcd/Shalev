package cu.shalev.app.registros

import cu.shalev.core.money.Cup
import cu.shalev.domain.model.FiltroRegistros
import java.time.Instant
import cu.shalev.domain.model.Venta
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.ClaseArticulo
import cu.shalev.domain.model.ItemMovimiento
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.PeriodoRegistro
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.VistaRegistro
import cu.shalev.domain.usecase.TipoRegistro
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrosLogicTest {
    private val habana = ZoneId.of("America/Havana")

    @Test fun filasDeLasTresTablas() {
        val v = venta(12, 725, minutos = 5, unidades = 2, metodo = MetodoPago.TRANSFERENCIA)
        assertEquals("30/09/2026 12:05", tituloFila(v, habana)) // por fecha, nunca «Venta Nº 12»
        assertEquals("1\u202F450 CUP", valorFila(v))

        val t = transferencia(3, 50)
        assertEquals("30/09/2026 12:03", tituloFila(t, habana))
        assertEquals("Ana Díaz", subtituloFila(t))
        assertNull(subtituloFila(transferencia(3, 50, cliente = " ")))
        assertEquals("50 CUP", valorFila(t))

        val m = ItemMovimiento(movimiento(7, "Harina", -1250, TipoEntidad.INSUMO, TipoMovimiento.CONSUMO), "kg")
        assertEquals("30/09/2026 12:07", tituloFila(m, habana))
        assertEquals("Harina", subtituloFila(m))
        assertEquals("-1.25 kg", valorFila(m))
        assertEquals("salida de existencias", descripcionMovimiento(m))
        assertEquals("entrada de existencias", descripcionMovimiento(ItemMovimiento(movimiento(8, "Pan", 3, tipo = TipoMovimiento.PRODUCCION))))
    }

    @Test fun resumenesEnSingularYPlural() {
        assertEquals("1 venta · 100 CUP", TextosRegistros.resumen(VistaRegistro.Ventas(listOf(venta(1, 100)))))
        assertEquals("0 transferencias · 0 CUP", TextosRegistros.resumen(VistaRegistro.Transferencias(emptyList())))
        assertEquals("1 movimiento", TextosRegistros.resumen(VistaRegistro.Movimientos(listOf(ItemMovimiento(movimiento(1, "Pan", 1))))))
    }

    @Test fun chipDelFiltro() {
        assertNull(resumenFiltro(FiltroRegistros(texto = "pan"), TipoRegistro.VENTAS))
        assertEquals("Hoy · desde 100 CUP", resumenFiltro(FiltroRegistros(periodo = PeriodoRegistro.HOY, importeMin = Cup.ofPesos(100)), TipoRegistro.VENTAS))
        val rango = FiltroRegistros(periodo = PeriodoRegistro.PERSONALIZADO, desde = LocalDate.of(2026, 9, 1), hasta = LocalDate.of(2026, 9, 15), importeMax = Cup.ofPesos(5))
        assertEquals("01/09/2026 – 15/09/2026 · hasta 5 CUP", resumenFiltro(rango, TipoRegistro.TRANSACCIONES))
        assertEquals("01/09/2026 – 15/09/2026", resumenFiltro(rango, TipoRegistro.MOVIMIENTOS)) // sin importe
        assertEquals("Desde 01/09/2026", resumenFiltro(rango.copy(hasta = null), TipoRegistro.MOVIMIENTOS))
        assertEquals("15/09/2026", resumenFiltro(rango.copy(desde = LocalDate.of(2026, 9, 15)), TipoRegistro.MOVIMIENTOS))
    }

    @Test fun formularioDelFiltroValidaYConservaElBuscador() {
        val actual = FiltroRegistros(texto = "pan")
        val (ok, sinErrores) = FiltroRegistrosLogic.aplicar(FormFiltroRegistros(PeriodoRegistro.HOY, importeMin = "1,450.50"), actual, TipoRegistro.VENTAS)
        assertEquals(FiltroRegistros(texto = "pan", periodo = PeriodoRegistro.HOY, importeMin = Cup(145050)), ok)
        assertTrue(sinErrores.isEmpty())

        val (nada, errores) = FiltroRegistrosLogic.aplicar(FormFiltroRegistros(importeMin = "abc", importeMax = "-5"), actual, TipoRegistro.VENTAS)
        assertNull(nada)
        assertEquals(TextosRegistros.IMPORTE_INVALIDO, errores[CamposFiltroRegistros.MIN])
        assertEquals(TextosRegistros.IMPORTE_INVALIDO, errores[CamposFiltroRegistros.MAX])

        val (_, invertidos) = FiltroRegistrosLogic.aplicar(FormFiltroRegistros(importeMin = "10", importeMax = "5"), actual, TipoRegistro.VENTAS)
        assertEquals(TextosRegistros.IMPORTES_INVERTIDOS, invertidos[CamposFiltroRegistros.MAX])

        val fechas = FormFiltroRegistros(PeriodoRegistro.PERSONALIZADO, desde = LocalDate.of(2026, 9, 20), hasta = LocalDate.of(2026, 9, 1))
        assertEquals(TextosRegistros.FECHAS_INVERTIDAS, FiltroRegistrosLogic.aplicar(fechas, actual, TipoRegistro.MOVIMIENTOS).second[CamposFiltroRegistros.FECHAS])
    }

    @Test fun enMovimientosSeIgnoranImportesYFechasSueltas() {
        val (f, e) = FiltroRegistrosLogic.aplicar(
            FormFiltroRegistros(PeriodoRegistro.HOY, desde = LocalDate.of(2026, 1, 1), importeMin = "abc"), FiltroRegistros(), TipoRegistro.MOVIMIENTOS,
        )
        assertTrue(e.isEmpty())
        assertEquals(FiltroRegistros(periodo = PeriodoRegistro.HOY), f) // «desde» solo cuenta con «Elegir fechas»
    }

    @Test fun formularioDesdeElFiltroActual() {
        val f = FiltroRegistrosLogic.desde(FiltroRegistros(importeMin = Cup(145050), importeMax = Cup.ofPesos(2000)))
        assertEquals("1450.5", f.importeMin)
        assertEquals("2000", f.importeMax)
        val grande = FiltroRegistrosLogic.desde(FiltroRegistros(importeMin = Cup(123_456_789)))
        assertEquals("1234567.89", grande.importeMin)
        val grandeAplicado = FiltroRegistrosLogic.aplicar(grande, FiltroRegistros(), TipoRegistro.VENTAS).first
        assertEquals(Cup(123_456_789), grandeAplicado?.importeMin)
        assertEquals("", FiltroRegistrosLogic.desde(FiltroRegistros()).importeMin)
    }

    @Test fun ventaDeServiciosMuestraCuantosServicios() {
        fun det(nombre: String, clase: ClaseArticulo = ClaseArticulo.SERVICIO, cantidad: Long = 1) = DetalleVenta(
            productoId = 1, nombre = nombre, categoria = "Barbería", cantidad = cantidad,
            precioBase = Cup.ofPesos(300), precioUnitario = Cup.ofPesos(300), costoUnitario = Cup.ofPesos(0), clase = clase,
        )
        fun v(vararg d: DetalleVenta) = Venta(turnoId = 1, fecha = Instant.EPOCH, metodoPago = MetodoPago.EFECTIVO, detalles = d.toList())
        assertEquals("1 servicio", subtituloFila(v(det("Corte de pelo"))))
        assertEquals("3 servicios", subtituloFila(v(det("Corte de pelo"), det("Lavado", cantidad = 2))))
        assertNull(subtituloFila(v(det("Pan", ClaseArticulo.PRODUCTO)))) // las ventas de productos no cambian
    }
}
