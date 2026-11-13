package cu.shalev.app.vinculacion.qr

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lector QR mínimo (solo vinculación): lo leído se devuelve tal cual; el vacío se ignora. */
class QrVinculacionViewModelTest {

    @Test fun loLeidoSeDevuelveTalCual() = runTest {
        val vm = QrVinculacionViewModel()
        vm.qrDetectado("SPVI1:abc123")
        assertEquals(EventoQr.QrLeido("SPVI1:abc123"), vm.eventos.first())
    }

    @Test fun vacioSeIgnora() = runTest {
        val vm = QrVinculacionViewModel()
        vm.qrDetectado("   ")
        assertNull(withTimeoutOrNull(100) { vm.eventos.first() })
    }
}
