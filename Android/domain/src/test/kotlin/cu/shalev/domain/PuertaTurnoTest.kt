package cu.shalev.domain

import cu.shalev.core.money.Cup

import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.PermisoEmpleado
import cu.shalev.domain.model.PermisosApp
import cu.shalev.domain.model.TipoApp
import cu.shalev.domain.model.Vinculacion
import cu.shalev.domain.repository.PuertaTurno
import cu.shalev.domain.usecase.AbrirTurno
import cu.shalev.domain.usecase.UsuarioActual
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P37: «no puede empezar un turno nuevo sin sincronizar» y reglas de la vinculación. */
class PuertaTurnoTest {
    private val turnos = FakeTurnos()
    private val usuario = UsuarioActual(FakePerfil(Perfil(nombre = "Luis")))
    private val clock = FixedClock()

    @Test fun secundariaSinPrincipalNoAbreTurno() = runBlocking {
        val sinRed = object : PuertaTurno {
            override suspend fun antesDeAbrir(): AppResult<Unit> = AppResult.Err(AppError.SinPrincipal(2))
        }
        assertEquals(AppResult.Err(AppError.SinPrincipal(2)), AbrirTurno(turnos, usuario, clock, sinRed)(Cup.ZERO))
        assertNull(turnos.activo())
    }

    @Test fun sincronizadaAbreTurno() = runBlocking {
        var llamadas = 0
        val ok = object : PuertaTurno {
            override suspend fun antesDeAbrir(): AppResult<Unit> { llamadas++; return AppResult.Ok(Unit) }
        }
        assertTrue(AbrirTurno(turnos, usuario, clock, ok)(Cup.ZERO) is AppResult.Ok)
        assertEquals(1, llamadas)
        // Con un turno ya abierto no se intenta sincronizar.
        assertEquals(AppResult.Err(AppError.TurnoYaAbierto), AbrirTurno(turnos, usuario, clock, ok)(Cup.ZERO))
        assertEquals(1, llamadas)
    }

    @Test fun nombreDeLaSecundaria() {
        assertEquals(Vinculacion.ErrorNombre.VACIO, Vinculacion.validarNombre("  ", emptyList()))
        assertEquals(Vinculacion.ErrorNombre.LARGO, Vinculacion.validarNombre("x".repeat(Vinculacion.MAX_NOMBRE + 1), emptyList()))
        assertEquals(Vinculacion.ErrorNombre.REPETIDO, Vinculacion.validarNombre("luis ", listOf("Luis")))
        assertNull(Vinculacion.validarNombre("Ana", listOf("Luis")))
        assertTrue(Vinculacion.puedeAgregar(4, 5))
        assertTrue(!Vinculacion.puedeAgregar(5, 5)); assertTrue(Vinculacion.puedeAgregar(9, 10))
    }

    /** 0.19.3: la principal es un punto de venta completo, no un monitor: lo puede todo, tenga o no secundarias. */
    @Test fun laPrincipalLoPuedeTodo() {
        val p = PermisosApp.PRINCIPAL
        assertFalse(p.esSecundaria)
        PermisoEmpleado.entries.forEach { assertTrue(it.name, p.puede(it)) }
        assertTrue(p.vender && p.venderProductos && p.venderServicios && p.editarInventario && p.cambiarPrecios && p.exportar)
        // Aunque por error llegara sin la lista de permisos, el tipo PRINCIPAL basta.
        assertTrue(PermisoEmpleado.entries.all { PermisosApp(TipoApp.PRINCIPAL, emptySet()).puede(it) })
    }
}
