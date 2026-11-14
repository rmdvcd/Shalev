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

    @Test fun elBancoSaleDeLosDigitos5a8NoDeLosCuatroPrimeros() {
        // 9205 es «ahorro en CUP» y lo emiten los tres bancos: el banco lo decide el código de los dígitos 5–8.
        assertEquals(BancoCubano.BPA, bancoPorTarjeta("9205129970876454"))
        assertEquals(BancoCubano.BANDEC, bancoPorTarjeta("9205069995328054"))
        assertEquals(BancoCubano.BANMET, bancoPorTarjeta("9205959870876454"))
        // El mismo producto (9225, USD con respaldo en MLC) emitido por bancos distintos.
        assertEquals(BancoCubano.BANDEC, bancoPorTarjeta("9225069900001234"))
        assertEquals(BancoCubano.BPA, bancoPorTarjeta("9225129900001234"))
        // 9206 aparece documentado en BANDEC, pero una 9206 1299 es del BPA (issue #23 del catálogo).
        assertEquals(BancoCubano.BPA, bancoPorTarjeta("9206129900001234"))
        // 9227 quedaba sin logo con la regla anterior; con el código 0699 es BANDEC.
        assertEquals(BancoCubano.BANDEC, bancoPorTarjeta("9227069995328054"))
    }

    @Test fun sinCodigoDeBancoConocidoNoHayLogo() {
        assertNull(bancoPorTarjeta("9205049900001234")) // 04xx: BICSA, sin miniatura
        assertNull(bancoPorTarjeta("9550129970876454")) // AIS: no es red RED
        assertNull(bancoPorTarjeta("9760129970876454")) // Clásica: no lleva código de banco
        assertNull(bancoPorTarjeta("9205000000001234")) // 0000 no es un código conocido
        assertNull(bancoPorTarjeta("9205129")) // todavía no hay dígitos 5–8 completos
    }

    @Test fun codigoDeBancoSonLosDigitos5a8() {
        assertEquals("1299", codigoBanco("9205 1299 7087 6454"))
        assertEquals("0699", codigoBanco("9205069995328054"))
        assertEquals("9598", codigoBanco("9225-9598-0000-1234"))
        assertNull(codigoBanco("9205129"))
        assertNull(codigoBanco("9550129970876454"))
        assertEquals("Código de banco 1299 · orientativo", TextosPago.bancoEstimado("1299"))
    }

    @Test fun cadaBancoUsaLaMiniaturaConSuLogo() {
        assertEquals(R.drawable.bpa_miniatura, miniaturaBanco(BancoCubano.BPA))
        assertEquals(R.drawable.bandec_miniatura, miniaturaBanco(BancoCubano.BANDEC))
        assertEquals(R.drawable.banmet_miniatura, miniaturaBanco(BancoCubano.BANMET))
    }

    @Test fun enEdicionElNumeroEsObligatorio() {
        assertEquals(TextosPago.ERROR_TELEFONO, EdicionPago(TipoCuentaPago.TELEFONO).error)
        assertEquals(TextosPago.ERROR_TARJETA, EdicionPago(TipoCuentaPago.TARJETA, numero = "").error)
        assertNull(EdicionPago(TipoCuentaPago.TARJETA, numero = "9205 1299 0000 1234").error)
        assertEquals(TextosPago.DUPLICADO, EdicionPago(TipoCuentaPago.TELEFONO, numero = "51234567", errorServidor = TextosPago.DUPLICADO).error)
    }

    @Test fun formatoYMensajes() {
        assertEquals("9205-1299-0000-1234", formatoCuenta("9205129900001234"))
        assertEquals(TextosPago.DUPLICADO, mensajePago(AppError.Duplicado("tarjetas")))
        assertEquals(TextosPago.ERROR_TELEFONO, mensajePago(AppError.Validacion("telefonos")))
        assertEquals(TextosPago.ERROR_GENERICO, mensajePago(AppError.Almacenamiento))
    }
}
