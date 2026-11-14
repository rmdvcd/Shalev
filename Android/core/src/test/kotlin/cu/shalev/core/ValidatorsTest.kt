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
