package cu.shalev.app.venta

import cu.shalev.core.money.Cup
import cu.shalev.domain.usecase.ExtraerNumeroTransaccion
import cu.shalev.domain.usecase.SmsPago
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapturaSmsLogicaTest {
    private val parser = ExtraerNumeroTransaccion()

    @Test fun soloExtraeDatosDeUnMensajeBancarioReconocible() {
        val texto = """
            Banco Popular de Ahorro
            Monto: 540 CUP
            Nro. Transaccion: BR601ADLM8997
        """.trimIndent()

        assertEquals(
            SmsPago("BR601ADLM8997", Cup.ofPesos(540)),
            CapturaSmsLogica.extraer(listOf(null, texto, "texto repetido"), parser),
        )
    }

    @Test fun ignoraNotificacionesSinCabeceraBancariaOTransaccion() {
        val sinBanco = "Monto: 540 CUP\nNro. Transaccion: BR601ADLM8997"
        val sinNumero = "Banco Popular de Ahorro: operación aprobada"

        assertNull(CapturaSmsLogica.extraer(listOf(sinBanco), parser))
        assertNull(CapturaSmsLogica.extraer(listOf(sinNumero), parser))
    }
}
