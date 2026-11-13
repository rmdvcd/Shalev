package cu.shalev.designsystem

import cu.shalev.designsystem.component.FiltroEntrada
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.21.0 (C10): cada filtro de entrada deja pasar solo lo que podría ser válido, y es idempotente. */
class FiltroEntradaTest {
    @Test fun numericos() {
        assertEquals("52345678", FiltroEntrada.TELEFONO.aplicar("+53 5234-5678x"))  // solo dígitos; recorta a 8
        assertEquals("920012341234123499", FiltroEntrada.TARJETA.aplicar("9200 1234 1234 1234 99"))
        assertEquals("85010112345", FiltroEntrada.CARNE.aplicar("850101-12345"))
        assertEquals("12", FiltroEntrada.ENTERO.aplicar("-1.2"))
        assertEquals("100", FiltroEntrada.PORCENTAJE.aplicar("1000%"))
    }

    @Test fun decimales() {
        assertEquals("12.50", FiltroEntrada.DINERO.aplicar("12.505"))
        assertEquals("12.53", FiltroEntrada.DINERO.aplicar("12,5.3"))     // un solo separador (el segundo se ignora); la coma pasa a punto
        assertEquals("2.5", FiltroEntrada.DECIMAL.aplicar("2,5"))       // 0.21.6: antes quedaba «2,5» y Cantidad.parse lo leía como 25
        assertEquals("1.250", FiltroEntrada.DECIMAL.aplicar("1.2509"))
        assertEquals("", FiltroEntrada.DINERO.aplicar("abc"))
    }

    /** 0.31.0: el ajuste de existencias se corrige con una cantidad con signo («-1.25»). */
    @Test fun cantidadConSignoDeUnAjuste() {
        assertEquals("-1.25", FiltroEntrada.CANTIDAD.aplicar("-1,25")) // la coma del teclado pasa a punto
        assertEquals("+3", FiltroEntrada.CANTIDAD.aplicar(" +3 "))
        assertEquals("1.250", FiltroEntrada.CANTIDAD.aplicar("1.2509"))
        assertEquals("-", FiltroEntrada.CANTIDAD.aceptar("5", "-"))   // estado parcial mientras se escribe
        assertEquals("-0.5", FiltroEntrada.CANTIDAD.aceptar("", "-0.5"))
        assertEquals("5", FiltroEntrada.CANTIDAD.aceptar("5", "5.1.2"))
        assertEquals("12", FiltroEntrada.CANTIDAD.aceptar("12", "-1.2e3"))
    }

    @Test fun rechazaNumerosAmbiguosSinCambiarElValorAnterior() {
        listOf("-12", "1,234.56", "1e3", "12abc", "2.999").forEach {
            assertEquals("10", FiltroEntrada.DINERO.aceptar("10", it))
        }
        assertEquals("2.50", FiltroEntrada.DINERO.aceptar("10", "2,50"))
        assertEquals("3", FiltroEntrada.ENTERO.aceptar("3", "-1.2"))
        assertEquals("99", FiltroEntrada.PORCENTAJE.aceptar("99", "101"))
        assertEquals("", FiltroEntrada.DINERO.aceptar("10", ""))
        assertEquals("12345678901234567890", FiltroEntrada.TARJETA.aceptar("", "12345678901234567890"))
    }

    @Test fun textos() {
        assertEquals("Pan de bono", FiltroEntrada.NOMBRE.aplicar("Pan\tde   bono"))
        assertEquals("línea 1\nlínea 2", FiltroEntrada.TEXTO.aplicar("línea 1\nlínea 2\u0007"))
        assertEquals(500, FiltroEntrada.TEXTO.aplicar("x".repeat(900)).length)
        assertEquals("ABC-12.3", FiltroEntrada.CODIGO.aplicar("ABC-12.3 ñ"))
        // 0.21.4 (E7): mismo formato que el dominio: «_» admitido y hasta 64 caracteres
        assertEquals("LOTE_7", FiltroEntrada.CODIGO.aplicar("LOTE_7"))
        assertEquals(64, FiltroEntrada.CODIGO.aplicar("A".repeat(70)).length)
        assertEquals("MM10040FEJ987", FiltroEntrada.TRANSACCION.aplicar("mm10040fej987 "))
        assertEquals(5000, FiltroEntrada.LIBRE.aplicar("y".repeat(5000)).length)
    }

    @Test fun idempotentes() {
        val muestras = listOf("+53 5234 5678", "12,505.1", "Hola\u0000 mundo  ", "x".repeat(700), "ab-CD.9 ")
        FiltroEntrada.entries.forEach { f ->
            muestras.forEach { m -> val una = f.aplicar(m); assertEquals("$f «$m»", una, f.aplicar(una)) }
        }
    }
}
