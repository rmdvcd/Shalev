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
        assertEquals("12.5", FiltroEntrada.PORCENTAJE.aplicar("12,5%"))
    }

    /** Móvil cubano: empieza por 5 o 6 (serie 63 de ETECSA); pegar «+53 …» sigue funcionando. */
    @Test fun telefonoSoloMovilCubano() {
        assertEquals("", FiltroEntrada.TELEFONO.aceptar("", "7"))          // fijo de La Habana: no aparece
        assertEquals("5", FiltroEntrada.TELEFONO.aceptar("", "5"))
        assertEquals("6", FiltroEntrada.TELEFONO.aceptar("", "6"))
        assertEquals("63123456", FiltroEntrada.TELEFONO.aceptar("", "+53 6312 3456"))
        assertEquals("52345678", FiltroEntrada.TELEFONO.aceptar("", "+53 5234-5678"))
        assertEquals("5234", FiltroEntrada.TELEFONO.aceptar("5234", "72345678"))
        assertEquals("2345678", FiltroEntrada.TELEFONO.aceptar("52345678", "2345678")) // borrar nunca se bloquea
    }

    /** Carné: los 6 primeros dígitos tienen que poder ser una fecha AAMMDD. */
    @Test fun carneConFechaPosible() {
        assertEquals("85010112345", FiltroEntrada.CARNE.aceptar("", "850101-12345"))
        assertEquals("85", FiltroEntrada.CARNE.aceptar("85", "852"))        // mes 2x imposible
        assertEquals("851", FiltroEntrada.CARNE.aceptar("851", "8513"))     // mes 13
        assertEquals("850", FiltroEntrada.CARNE.aceptar("850", "8500"))     // mes 00
        assertEquals("8512", FiltroEntrada.CARNE.aceptar("8512", "85124"))  // día 4x
        assertEquals("85123", FiltroEntrada.CARNE.aceptar("85123", "851232")) // día 32
        assertEquals("851231", FiltroEntrada.CARNE.aceptar("85123", "851231"))
        assertEquals("8513112345", FiltroEntrada.CARNE.aceptar("85123112345", "8513112345")) // borrar nunca se bloquea
    }

    @Test fun porcentajeConDosDecimalesHasta100() {
        assertEquals("12.5", FiltroEntrada.PORCENTAJE.aceptar("", "12,5"))
        assertEquals("12.75", FiltroEntrada.PORCENTAJE.aceptar("12.7", "12.75"))
        assertEquals("12.75", FiltroEntrada.PORCENTAJE.aceptar("12.75", "12.755")) // tercer decimal
        assertEquals("100", FiltroEntrada.PORCENTAJE.aceptar("100", "100.5"))
        assertEquals("100.", FiltroEntrada.PORCENTAJE.aceptar("100", "100."))
        assertEquals("10", FiltroEntrada.PORCENTAJE.aceptar("10", "-10"))
    }

    @Test fun nombresDePersonaSoloLetras() {
        assertEquals("María José", FiltroEntrada.PERSONA.aplicar(" María  José2"))
        assertEquals("O'Neil Ruiz-Tagle", FiltroEntrada.PERSONA.aplicar("O’Neil Ruiz–Tagle"))
        assertEquals("Núñez", FiltroEntrada.PERSONA.aplicar("Nu\u0301n\u0303ez")) // tildes de dos pulsaciones → NFC
        assertEquals("Ana", FiltroEntrada.PERSONA.aplicar("Ana@#1"))
        assertEquals(80, FiltroEntrada.PERSONA.aplicar("a".repeat(90)).length)
    }

    @Test fun textosDeUnaLineaConSuLimite() {
        assertEquals(40, FiltroEntrada.DESCRIPCION.aplicar("d".repeat(60)).length)
        assertEquals("Roja grande", FiltroEntrada.DESCRIPCION.aplicar("Roja\ngrande"))
        assertEquals(40, FiltroEntrada.CATEGORIA.aplicar("c".repeat(60)).length)
        assertEquals("Bebidas", FiltroEntrada.CATEGORIA.aplicar("  Bebidas"))
        assertEquals("clave con espacios", FiltroEntrada.CONTRASENA.aplicar("clave con espacios\n"))
        assertEquals(256, FiltroEntrada.CONTRASENA.aplicar("k".repeat(300)).length)
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
        assertEquals("99", FiltroEntrada.PORCENTAJE.aceptar("99", "99.999"))
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
