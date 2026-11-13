package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.repository.PuertaTurno
import cu.shalev.domain.usecase.AbrirTurno
import cu.shalev.domain.usecase.UsuarioActual
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.32.0: el fondo de caja lo escribe **quien abre el turno**, también en una app secundaria (se invierte la
 * decisión de 0.26.0 P73 §4: el dueño ya no asigna el fondo ni lo pide).
 */
class FondoEnTurnoTest {
    private val turnos = FakeTurnos()
    private val usuario = UsuarioActual(FakePerfil(Perfil(nombre = "Luis")))
    private val clock = FixedClock()

    private class Puerta : PuertaTurno {
        var abierto = 0
        override suspend fun antesDeAbrir(): AppResult<Unit> { abierto++; return AppResult.Ok(Unit) }
    }

    @Test fun `la secundaria abre con el fondo que escribe quien abre`() = runBlocking {
        val p = Puerta()
        val r = AbrirTurno(turnos, usuario, clock, p)(Cup.ofPesos(1500))
        assertTrue(r is AppResult.Ok)
        assertEquals(Cup.ofPesos(1500), turnos.activo()?.fondo)
        assertEquals(1, p.abierto)
    }

    @Test fun `un fondo de cero tambien abre el turno`() = runBlocking {
        val p = Puerta()
        assertTrue(AbrirTurno(turnos, usuario, clock, p)(Cup.ZERO) is AppResult.Ok)
        assertEquals(Cup.ZERO, turnos.activo()?.fondo)
    }

    @Test fun `un fondo negativo no abre el turno`() = runBlocking {
        val p = Puerta()
        assertTrue(AbrirTurno(turnos, usuario, clock, p)(Cup.ofPesos(-1)) is AppResult.Err)
    }
}
