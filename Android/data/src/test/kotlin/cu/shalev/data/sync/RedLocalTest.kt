package cu.shalev.data.sync

import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ordena candidatos locales antes de las IP celulares privadas/CGNAT para que LAN y hotspot sean el primer intento. */
class RedLocalTest {
    private fun a(host: String): Inet4Address = InetAddress.getByName(host) as Inet4Address
    private fun candidata(interfaz: String, host: String) = CandidataIPv4(interfaz, a(host))

    @Test fun `las direcciones wifi y hotspot preceden a la IP celular privada aunque esta sea 10x`() {
        assertEquals(
            listOf("192.168.43.1", "192.168.44.1", "10.0.0.5"),
            RedLocal.candidatas(listOf(
                candidata("rmnet_data0", "10.0.0.5"),
                candidata("wlan0", "192.168.43.1"),
                candidata("ap0", "192.168.44.1"),
            )),
        )
    }

    @Test fun `prioriza lan privada y luego una direccion publica antes de cgnat celular`() {
        assertEquals(
            listOf("192.168.1.10", "189.30.20.15", "100.80.20.5"),
            RedLocal.candidatas(listOf(
                candidata("rmnet_data0", "100.80.20.5"), // rango CGNAT 100.64/10
                candidata("rmnet_data0", "189.30.20.15"),
                candidata("wlan0", "192.168.1.10"),
            )),
        )
    }

    @Test fun `compatibilidad ordena privadas primero`() {
        assertEquals(
            listOf("10.0.0.5", "192.168.1.10", "172.20.1.2", "189.30.20.15"),
            RedLocal.utiles(listOf(a("189.30.20.15"), a("10.0.0.5"), a("192.168.1.10"), a("172.20.1.2"))),
        )
    }

    @Test fun `excluye loopback link local sin especificar multicast y duplicadas`() {
        assertEquals(
            listOf("192.168.1.10", "189.30.20.15"),
            RedLocal.utiles(listOf(
                a("127.0.0.1"), a("169.254.3.4"), a("0.0.0.0"), a("224.0.0.1"), a("240.0.0.1"),
                a("192.168.1.10"), a("192.168.1.10"), a("189.30.20.15"),
            )),
        )
    }

    @Test fun `limita candidatas para que el QR no crezca sin limite`() {
        val varias = (1..DireccionIPv4Sync.MAX_DIRECCIONES + 5).map { candidata("wlan0", "10.0.0.$it") }
        assertEquals(DireccionIPv4Sync.MAX_DIRECCIONES, RedLocal.candidatas(varias).size)
    }

    @Test fun `vacio si no hay ninguna util`() {
        assertEquals(emptyList<String>(), RedLocal.utiles(emptyList()))
        assertEquals(emptyList<String>(), RedLocal.utiles(listOf(a("127.0.0.1"), a("169.254.0.1"))))
    }
}
