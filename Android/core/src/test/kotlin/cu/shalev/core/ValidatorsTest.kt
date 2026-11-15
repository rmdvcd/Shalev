package cu.shalev.core

import cu.shalev.core.contact.DeveloperContact
import cu.shalev.core.money.Money
import cu.shalev.core.validation.Phone
import cu.shalev.core.validation.Validators

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {
    @Test fun names() {
        listOf("Li", "María", "Pérez González", "De la Cruz", "O'Neil", "Ruiz-Tagle", "Ñúñez").forEach {
            assertTrue(it, Validators.nombre(it))
        }
        listOf("", "A", "Juan2", " ", "Ana-", "-Ana", "a".repeat(81)).forEach {
            assertFalse(it, Validators.nombre(it))
        }
    }

    @Test fun ci() {
        assertTrue(Validators.ci("91040922502"))
        assertTrue(Validators.ci("AB12345"))
        assertFalse(Validators.ci("1234"))
        assertFalse(Validators.ci("9104-0922"))
    }

    @Test fun carneCubanoComprueba11DigitosYFechaDeNacimiento() {
        val hoy = java.time.LocalDate.of(2026, 11, 13)
        // Siglo por el 7.º dígito: 0–5 → 1900, 6–8 → 2000, 9 → 1800.
        assertEquals(java.time.LocalDate.of(1985, 1, 1), Validators.fechaCarne("85010112345"))
        assertEquals(java.time.LocalDate.of(2005, 3, 4), Validators.fechaCarne("05030467891"))
        assertEquals(java.time.LocalDate.of(1899, 12, 31), Validators.fechaCarne("99123190001"))
        listOf("85010112345", "91040922502", "00022965432", "10123167890").forEach { assertTrue(it, Validators.carneCubano(it, hoy)) }
        listOf(
            "", "8501011234", "850101123456", "85O10112345", // longitud o letra
            "85130112345", "85000112345", "85010012345", "85043112345", // mes 13 o 00, día 00, 31 de abril
            "01022965432", // 29-feb-2001 (no bisiesto)
            "27010167890", // 2027: posterior a hoy
        ).forEach { assertFalse(it, Validators.carneCubano(it, hoy)) }
    }

    @Test fun luhnDeTarjetasCubanas() {
        assertTrue(Validators.luhn("9205129900001232"))
        assertTrue(Validators.luhn("9227069995328054"))
        assertFalse(Validators.luhn("9205129900001234"))
        assertFalse(Validators.luhn("9205129900001223")) // dos dígitos cambiados de sitio
        assertFalse(Validators.luhn(""))
        assertTrue(Validators.pareceTarjeta("9205129900001232"))
        assertFalse(Validators.pareceTarjeta("059812345678")) // cuenta
    }

    @Test fun movilCubanoEmpiezaPor5O6() {
        listOf("51815604", "5234 5678", "+53 5234-5678", "63123456", "+5363123456").forEach { assertTrue(it, Phone.esMovilCubano(it)) }
        listOf("72345678", "21234567", "1234", "+34612345678", "").forEach { assertFalse(it, Phone.esMovilCubano(it)) }
    }

    @Test fun phones() {
        assertEquals("+5351815604", Phone.normalize("51815604"))
        assertEquals("+5352345678", Phone.normalize("5234 5678"))
        assertEquals("+5352345678", Phone.normalize("+53 5234-5678"))
        assertEquals("+5352345678", Phone.normalize("5352345678"))
        assertEquals("+5352345678", Phone.normalize("0053 52345678"))
        assertNull(Phone.normalize("1234"))
    }

    @Test fun money() {
        assertEquals("1\u202F450 CUP", Money.cup(1450))
        assertEquals("90\u202F000 CUP", Money.cup(90_000))
    }

    @Test fun developerNumbersAreConsistent() {
        assertEquals("https://wa.me/5351815604", DeveloperContact.WHATSAPP_URL)
        assertEquals("smsto:+5351815604", DeveloperContact.SMS_URI)
    }
}
