package cu.shalev.app

import cu.shalev.app.caja.CajaForm
import cu.shalev.app.caja.TextosCaja
import cu.shalev.app.common.EntradaLicencia
import cu.shalev.app.registros.ModificacionLogic
import cu.shalev.core.money.Cup
import cu.shalev.domain.model.Arqueo
import cu.shalev.domain.model.ClaseArticulo
import cu.shalev.domain.model.DetalleVenta
import cu.shalev.domain.model.EstadoArqueo
import cu.shalev.domain.model.MetodoPago
import cu.shalev.domain.model.Venta
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.25.0: lógica pura de caja, modificación de ventas y licencia compartida. */
class Version025AppTest {
    private fun pesos(n: Long) = Cup.ofPesos(n)

    @Test fun fondoInicialYParseoDeImportes() {
        assertEquals("", CajaForm.textoInicial(null))
        assertEquals("1500", CajaForm.textoInicial(pesos(1500)))
        assertEquals("1234567.89", CajaForm.textoInicial(Cup(123_456_789)))
        assertEquals(pesos(1500), CajaForm.importe(CajaForm.textoInicial(pesos(1500))))
        assertEquals(Cup.ZERO, CajaForm.importe("0"))
        assertNull(CajaForm.importe(""))
        assertNull(CajaForm.importe("-5"))
        assertNull(CajaForm.importe("abc"))
    }

    @Test fun motivosDeCajaYDeVenta() {
        assertFalse(CajaForm.motivoValido("  a   "))
        assertTrue(CajaForm.motivoValido("  a  b "))
        assertTrue(CajaForm.motivoValido("Pago   luz"))
        assertFalse(CajaForm.motivoValido("x".repeat(61)))
        assertTrue(ModificacionLogic.motivoValido("Cliente devolvió"))
        assertFalse(ModificacionLogic.motivoValido(" no "))
    }

    @Test fun resumenDelArqueo() {
        val a = Arqueo(pesos(100), pesos(500), pesos(0), pesos(50), null)
        assertEquals("Esperado 550 CUP · Sin contar", TextosCaja.resumen(a))
        assertEquals("Esperado 550 CUP · Contado 550 CUP · Cuadra", TextosCaja.resumen(a.copy(contado = pesos(550))))
        assertEquals("Esperado 550 CUP · Contado 500 CUP · Faltante 50 CUP", TextosCaja.resumen(a.copy(contado = pesos(500))))
        assertEquals("Sobrante", TextosCaja.estado(EstadoArqueo.SOBRANTE))
        assertEquals("Sin contar", TextosCaja.estado(null))
    }

    @Test fun modificacionQuitaLineasACeroYRecalcula() {
        fun d(id: Long, cant: Long, precio: Long) = DetalleVenta(
            productoId = id, nombre = "P$id", categoria = "X", cantidad = cant,
            precioBase = pesos(precio), precioUnitario = pesos(precio), costoUnitario = pesos(1),
        )
        val v = Venta(id = 1, turnoId = 1, fecha = Instant.EPOCH, metodoPago = MetodoPago.EFECTIVO, detalles = listOf(d(1, 2, 100), d(2, 1, 30)))
        val lineas = ModificacionLogic.lineas(v, listOf(3, 0))
        assertEquals(1, lineas.size)
        assertEquals(1L, lineas.single().productoId)
        assertEquals(3L, lineas.single().cantidad)
        assertEquals(ClaseArticulo.PRODUCTO, lineas.single().clase)
        assertEquals(pesos(300), ModificacionLogic.total(v, listOf(3, 0)))
        assertEquals(pesos(230), ModificacionLogic.total(v, listOf(2, 1)))
    }

    @Test fun reconoceLaLicenciaCompartida() {
        assertTrue(EntradaLicencia.esLicencia("Licencia Shalev\nID: x\n\nSPVI2:AAAA"))
        assertFalse(EntradaLicencia.esLicencia("SPVIR1:abc"))
        assertFalse(EntradaLicencia.esLicencia("Nro. Transaccion: MM10040FEJ987"))
    }

    /** 0.25.1 (E3): la tabla de Servicios se titula como las demás. */
    @Test fun serviciosSinShalevDuplicado() {
        val t = cu.shalev.app.servicios.ServiciosLogic.tabla(emptyList())
        assertEquals("Servicios", t.titulo)
    }
}
