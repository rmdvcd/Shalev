package cu.shalev.domain.fichacosto

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FichaCostoTest {
    private fun partida(c: CategoriaCosto, m: MonedaCosto, cantidad: String, precio: String) =
        PartidaCosto("Concepto", c, BigDecimal(cantidad), "kg", BigDecimal(precio), m)

    @Test fun sumaPorCategoriaYMonedaSinConvertir() {
        val resultado = CalculoFichaCosto.calcular(listOf(
            partida(CategoriaCosto.MATERIAS, MonedaCosto.CUP, "1.5", "10"),
            partida(CategoriaCosto.MATERIAS, MonedaCosto.CUP, "1", "0.005"),
            partida(CategoriaCosto.TRABAJO, MonedaCosto.MLC, "2", "3"),
        ))
        assertEquals(BigDecimal("15.01"), resultado.costoTotal.getValue(MonedaCosto.CUP))
        assertEquals(BigDecimal("6.00"), resultado.costoTotal.getValue(MonedaCosto.MLC))
        assertEquals(BigDecimal("0.00"), resultado.porCategoria.getValue(CategoriaCosto.INDIRECTOS).getValue(MonedaCosto.CUP))
        assertNull(resultado.precioEstimado)
    }

    @Test fun calculaPorcentajeYFactorSinAplicarCoeficientesImplicitos() {
        val partidas = listOf(partida(CategoriaCosto.OTROS, MonedaCosto.CUP, "1", "10"))
        assertEquals(BigDecimal("12.50"), CalculoFichaCosto.calcular(partidas, BigDecimal("25")).precioEstimado!!.getValue(MonedaCosto.CUP))
        assertEquals(BigDecimal("15.00"), CalculoFichaCosto.calcular(partidas, BigDecimal("1.5"), UnidadMargen.FACTOR).precioEstimado!!.getValue(MonedaCosto.CUP))
    }

    @Test fun entradaNumericaValidada() {
        assertEquals(BigDecimal("1.25"), CalculoFichaCosto.numero("1,25"))
        listOf("", "-1", "1e4", "1.2.3", "1234567890123", "1.12345").forEach { assertNull(CalculoFichaCosto.numero(it)) }
    }
}
