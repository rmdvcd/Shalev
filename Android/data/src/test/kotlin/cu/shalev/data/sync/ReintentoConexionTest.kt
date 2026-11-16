package cu.shalev.data.sync

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ReintentoConexionTest {
    @Test fun reintentaConEsperaCrecienteSinDuplicarElBucle() = runTest {
        var intentos = 0
        val reintento = ReintentoConexion(this, 100, 400) { ++intentos >= 3 }
        reintento.iniciar()
        reintento.iniciar()
        runCurrent()
        assertEquals(0, intentos)

        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, intentos)
        advanceTimeBy(199)
        runCurrent()
        assertEquals(1, intentos)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, intentos)
        advanceTimeBy(400)
        runCurrent()
        assertEquals(3, intentos)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3, intentos)
    }

    @Test fun detenerCancelaUnaReconexiónPendiente() = runTest {
        var intentos = 0
        val reintento = ReintentoConexion(this, 100, 400) { intentos++; false }
        reintento.iniciar()
        runCurrent()
        reintento.detener()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(0, intentos)
    }

    @Test fun noPierdeUnaCaidaJustoAlTerminarElIntentoDeConexion() = runTest {
        var intentos = 0
        lateinit var reintento: ReintentoConexion
        reintento = ReintentoConexion(this, 100, 400) {
            intentos++
            if (intentos == 1) reintento.iniciar()
            true
        }
        reintento.iniciar()
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, intentos)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, intentos)
    }
}
