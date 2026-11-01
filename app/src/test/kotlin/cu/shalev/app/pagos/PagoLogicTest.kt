package cu.shalev.app.pagos

import cu.shalev.app.R
import cu.shalev.core.result.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PagoLogicTest {

    @Test fun camposNuevosSonOpcionales() {
        assertNull(errorTelefono(""))
        assertNull(errorTarjeta("  "))
    }

    @Test fun validacionDeFormato() {
        assertNull(errorTelefono("5123 4567"))
        assertEquals(TextosPago.ERROR_TELEFONO, errorTelefono("123"))
        assertNull(errorTarjeta("9205-1299-0000-1234"))
        assertEquals(TextosPago.ERROR_TARJETA, errorTarjeta("9205"))
    }

    @Test fun logosBancariosUsanSoloPrefijosNoAmbiguos() {
        assertEquals(BancoCubano.BPA, bancoPorTarjeta("9205-1299-0000-1234"))
        assertEquals(BancoCubano.BANDEC, bancoPorTarjeta("9225-0000-0000-1234"))
        assertEquals(BancoCubano.BANMET, bancoPorTarjeta("9226-0000-0000-1234"))
        assertNull(bancoPorTarjeta("9224-0000-0000-1234")) // las fuentes consultadas se contradicen
        assertNull(bancoPorTarjeta("9204-0000-0000-1234")) // el prefijo CUP no basta para identificar el banco
        assertNull(bancoPorTarjeta("123"))
    }

    @Test fun cadaBancoUsaLaMiniaturaConSuLogo() {
        assertEquals(R.drawable.bpa_miniatura, miniaturaBanco(bancoPorTarjeta("9205")!!))
        assertEquals(R.drawable.bandec_miniatura, miniaturaBanco(bancoPorTarjeta("9225")!!))
        assertEquals(R.drawable.banmet_miniatura, miniaturaBanco(bancoPorTarjeta("9226")!!))
    }

    @Test fun elLogoSeDecideSoloPorLosCuatroPrimerosDigitos() {
        assertEquals(BancoCubano.BANDEC, bancoPorTarjeta("9225 0000 0000 1234"))
        assertEquals(BancoCubano.BANDEC, bancoPorTarjeta("9225000000001234"))
        assertEquals(BancoCubano.BANDEC, bancoPorTarjeta("9225"))
        assertEquals(BancoCubano.BPA, bancoPorTarjeta(" 9205 - 1299 "))
        assertNull(bancoPorTarjeta("9235-0000-0000-1234")) // compartido entre bancos: sin logo
        assertNull(bancoPorTarjeta("9227-0000-0000-1234"))
        assertNull(bancoPorTarjeta("5205-1299-0000-1234")) // el 9205 solo cuenta al inicio
    }

    @Test fun enEdicionElNumeroEsObligatorio() {
        assertEquals(TextosPago.ERROR_TELEFONO, EdicionPago(TipoCuentaPago.TELEFONO).error)
        assertEquals(TextosPago.ERROR_TARJETA, EdicionPago(TipoCuentaPago.TARJETA, numero = "").error)
        assertNull(EdicionPago(TipoCuentaPago.TARJETA, numero = "9205 1299 0000 1234").error)
        assertEquals(TextosPago.DUPLICADO, EdicionPago(TipoCuentaPago.TELEFONO, numero = "51234567", errorServidor = TextosPago.DUPLICADO).error)
    }

    @Test fun formatoYMensajes() {
        assertEquals("9205 1299 0000 1234", formatoCuenta("9205129900001234"))
        assertEquals(TextosPago.DUPLICADO, mensajePago(AppError.Duplicado("tarjetas")))
        assertEquals(TextosPago.ERROR_TELEFONO, mensajePago(AppError.Validacion("telefonos")))
        assertEquals(TextosPago.ERROR_GENERICO, mensajePago(AppError.Almacenamiento))
    }
}
