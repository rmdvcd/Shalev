package cu.shalev.app.herramientas

import cu.shalev.domain.fichacosto.CalculoCambio
import cu.shalev.domain.fichacosto.MonedaCosto
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

/** La pantalla no debe redondear de nuevo a una unidad el cálculo exacto del dominio. */
class CalculadoraPresentacionTest {
    @Test fun conversionAUsdConservaUnCentavo() {
        val resultado = CalculoCambio.convertir(BigDecimal("2"), BigDecimal("400"), MonedaCosto.CUP)
        assertEquals("Resultado: 0.01 USD", textoResultadoCambio(resultado, MonedaCosto.USD))
    }

    @Test fun conversionACupConservaLaFraccionSinInventarDecimales() {
        val resultado = CalculoCambio.convertir(BigDecimal("0.001"), BigDecimal("400"), MonedaCosto.USD)
        assertEquals("Resultado: 0.4 CUP", textoResultadoCambio(resultado, MonedaCosto.CUP))
    }

    @Test fun enterosYCeroNoUsanNotacionCientifica() {
        assertEquals("Resultado: 1000 CUP", textoResultadoCambio(BigDecimal("1000.00"), MonedaCosto.CUP))
        assertEquals("Resultado: 0 USD", textoResultadoCambio(BigDecimal("0.00"), MonedaCosto.USD))
    }
}
