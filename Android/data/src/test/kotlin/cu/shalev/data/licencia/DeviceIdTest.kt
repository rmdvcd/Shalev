package cu.shalev.data.licencia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P19: el deviceId cumple el contrato GL v1 (8–128 caracteres `[A-Za-z0-9:_-]`) y no cambia para ANDROID_ID reales. */
class DeviceIdTest {
    private val contrato = Regex("[A-Za-z0-9:_-]{8,128}")

    @Test
    fun `ANDROID_ID real da el mismo valor que antes`() {
        assertEquals("SPVI:9774d56d682e549c", DeviceIdProvider.deDispositivo("9774D56D682E549C"))
    }

    @Test
    fun `sin ANDROID_ID usa el valor de reserva`() {
        assertEquals("SPVI:0000000000000000", DeviceIdProvider.deDispositivo(null))
        assertEquals("SPVI:0000000000000000", DeviceIdProvider.deDispositivo("  -- "))
    }

    @Test
    fun `letras no ASCII y simbolos se descartan`() {
        val id = DeviceIdProvider.deDispositivo("ab\u00e9\u00f1\u0661cd-12")
        assertEquals("SPVI:abcd12", id)
        assertTrue("debe cumplir el contrato tras completar", contrato.matches(DeviceIdProvider.deDispositivo("ab\u00e9cd1234")))
    }

    @Test
    fun `nunca supera 128 caracteres`() {
        val id = DeviceIdProvider.deDispositivo("a".repeat(500))
        assertEquals(128, id.length)
        assertTrue(contrato.matches(id))
    }
}
