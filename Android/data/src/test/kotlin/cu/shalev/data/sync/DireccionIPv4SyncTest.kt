package cu.shalev.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DireccionIPv4SyncTest {
    @Test fun `solo admite IPv4 numericas y enroutables como candidata`() {
        listOf("10.0.0.4", "192.168.43.1", "100.64.0.1", "189.30.20.15").forEach {
            assertTrue("debe admitir $it", DireccionIPv4Sync.esCandidata(it))
        }
        listOf(
            "localhost", "192.168.1", "192.168.1.999", "192.168.01.2", "192.168.1.2:47811",
            "0.0.0.0", "127.0.0.1", "169.254.1.2", "224.0.0.1", "240.0.0.1", "",
        ).forEach { assertFalse("debe rechazar $it", DireccionIPv4Sync.esCandidata(it)) }
    }

    @Test fun `la lista no admite duplicadas vacias ni mas de 32`() {
        assertTrue(DireccionIPv4Sync.listaValida(listOf("192.168.1.2")))
        assertFalse(DireccionIPv4Sync.listaValida(emptyList()))
        assertTrue(DireccionIPv4Sync.listaValida(emptyList(), permitirVacia = true))
        assertFalse(DireccionIPv4Sync.listaValida(listOf("192.168.1.2", "192.168.1.2")))
        assertFalse(DireccionIPv4Sync.listaValida((1..33).map { "10.0.0.$it" }))
    }
}
