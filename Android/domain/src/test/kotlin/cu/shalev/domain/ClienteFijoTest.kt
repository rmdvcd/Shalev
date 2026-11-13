package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.domain.model.ClienteFijo
import cu.shalev.domain.model.DatosCliente
import cu.shalev.domain.usecase.DatosTransferencia
import cu.shalev.domain.usecase.transaccion
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.27.0 (N2): clientes fijos (sugerencias y marca en la transacción). */
class ClienteFijoTest {
    private fun c(nombre: String, ci: String) =
        ClienteFijo(nombreApellidos = nombre, ci = ci, telefono = "+5351234567", creadoEn = Instant.EPOCH, actualizadoEn = Instant.EPOCH)

    private val clientes = listOf(
        c("Ana María López", "1"), c("María Pérez", "2"), c("Mariano Gil", "3"), c("Rosa Martí", "4"), c("José Mármol", "5"),
    )

    @Test fun sugiereHastaTresSinTildesNiMayusculasYPrimeroLosQueEmpiezan() {
        val s = ClienteFijo.sugerencias(clientes, "MAR")
        assertEquals(3, s.size)
        assertEquals(listOf("María Pérez", "Mariano Gil"), s.take(2).map { it.nombreApellidos })
    }

    @Test fun conMenosDeDosLetrasOCoincidenciaExactaNoSugiere() {
        assertTrue(ClienteFijo.sugerencias(clientes, "m").isEmpty())
        assertTrue(ClienteFijo.sugerencias(clientes, " ").isEmpty())
        assertTrue(ClienteFijo.sugerencias(listOf(c("María Pérez", "2")), "maria  perez").isEmpty())
        assertTrue(ClienteFijo.sugerencias(clientes, "xyz").isEmpty())
    }

    @Test fun laTransaccionLlevaLaMarcaDeClienteFijo() {
        val d = DatosCliente("  María   Pérez ", "85010112345", "51234567")
        val fijo = transaccion(DatosTransferencia(d, "br601adlm8997", clienteFijo = true), Instant.EPOCH, Cup(10000), null, null)
        assertTrue(fijo.clienteFijo)
        assertEquals("María Pérez", fijo.cliente.nombreApellidos)
        assertFalse(transaccion(DatosTransferencia(d, "BR601ADLM8997"), Instant.EPOCH, Cup(10000), null, null).clienteFijo)
    }
}
