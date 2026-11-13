package cu.shalev.data.sync

import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 1.0.1 (datos móviles): qué direcciones entran en el QR de vinculación y en qué orden (ver [RedLocal.utiles]).
 * Las privadas (wifi/zona wifi) van primero y después las demás (datos móviles); la secundaria prueba por orden.
 */
class RedLocalTest {
    private fun a(host: String): Inet4Address = InetAddress.getByName(host) as Inet4Address

    @Test fun `primero las privadas y despues las de datos moviles`() {
        assertEquals(
            listOf("10.0.0.5", "192.168.1.10", "172.20.1.2", "189.30.20.15"),
            RedLocal.utiles(listOf(a("189.30.20.15"), a("10.0.0.5"), a("192.168.1.10"), a("172.20.1.2"))),
        )
    }

    @Test fun `sin loopback ni link local y sin repetidas`() {
        assertEquals(
            listOf("192.168.1.10", "189.30.20.15"),
            RedLocal.utiles(listOf(a("127.0.0.1"), a("169.254.3.4"), a("192.168.1.10"), a("192.168.1.10"), a("189.30.20.15"))),
        )
    }

    @Test fun `vacio si no hay ninguna util`() {
        assertEquals(emptyList<String>(), RedLocal.utiles(emptyList()))
        assertEquals(emptyList<String>(), RedLocal.utiles(listOf(a("127.0.0.1"), a("169.254.0.1"))))
    }
}
