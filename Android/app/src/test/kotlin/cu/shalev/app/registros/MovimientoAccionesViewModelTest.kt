package cu.shalev.app.registros

import cu.shalev.app.RelojFijo
import cu.shalev.app.SecundariaRepoFake
import cu.shalev.app.TipoRepo
import cu.shalev.app.TurnoRepo
import cu.shalev.core.result.AppError
import cu.shalev.domain.model.ItemMovimiento
import cu.shalev.domain.model.MovimientoInventario
import cu.shalev.domain.model.TipoApp
import cu.shalev.domain.model.TipoEntidad
import cu.shalev.domain.model.TipoMovimiento
import cu.shalev.domain.model.Turno
import cu.shalev.domain.usecase.CorregirMovimiento
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 0.31.0: corrección de la cantidad de un ajuste desde la ficha de Registros → Movimientos. Solo la app principal,
 * solo los ajustes y solo con el turno del movimiento abierto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MovimientoAccionesViewModelTest {
    private val t0: Instant = Instant.parse("2026-10-17T10:00:00Z")

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private fun movimiento(tipo: TipoMovimiento, turnoId: Long?, delta: Long = 5, entidad: TipoEntidad = TipoEntidad.PRODUCTO) = MovimientoInventario(
        id = 9, fecha = t0, tipo = tipo, entidad = entidad, entidadId = 1, nombre = "Refresco",
        delta = delta, existenciaResultante = 10, turnoId = turnoId,
    )

    private fun TestScope.vm(tipo: TipoApp = TipoApp.PRINCIPAL, turnoAbierto: Long? = 1): Pair<MovimientoAccionesViewModel, TurnoRepo> {
        val turnos = TurnoRepo()
        turnoAbierto?.let { turnos.turnos.value = listOf(Turno(id = it, abiertoEn = t0, abiertoPor = "Ana")) }
        val secundaria = SecundariaRepoFake()
        if (tipo == TipoApp.SECUNDARIA) secundaria.flow.value = secundaria.flow.value.copy(vinculada = true)
        val vm = MovimientoAccionesViewModel(
            turnos, TipoRepo(tipo), CorregirMovimiento(turnos, RelojFijo(t0), secundaria),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm to turnos
    }

    @Test fun soloSeOfreceEditarEnAjustesDelTurnoAbiertoDeLaPrincipal() = runTest {
        val (vm, _) = vm()
        vm.comprobar(ItemMovimiento(movimiento(TipoMovimiento.AJUSTE, turnoId = 1)))
        assertTrue(vm.state.value.puede)

        vm.comprobar(ItemMovimiento(movimiento(TipoMovimiento.VENTA, turnoId = 1)))
        assertFalse("una salida por venta se corrige desde su venta", vm.state.value.puede)

        vm.comprobar(ItemMovimiento(movimiento(TipoMovimiento.AJUSTE, turnoId = 2)))
        assertFalse("de otro turno", vm.state.value.puede)

        vm.comprobar(ItemMovimiento(movimiento(TipoMovimiento.AJUSTE, turnoId = null)))
        assertFalse("fuera de turno", vm.state.value.puede)

        val (sinTurno, _) = vm(turnoAbierto = null)
        sinTurno.comprobar(ItemMovimiento(movimiento(TipoMovimiento.AJUSTE, turnoId = 1)))
        assertFalse("sin turno abierto", sinTurno.state.value.puede)

        val (secundaria, _) = vm(tipo = TipoApp.SECUNDARIA)
        secundaria.comprobar(ItemMovimiento(movimiento(TipoMovimiento.AJUSTE, turnoId = 1)))
        assertFalse("solo la principal corrige lo registrado", secundaria.state.value.puede)
    }

    @Test fun abrirTraeLaCantidadActualYElMotivoVacio() = runTest {
        val (vm, _) = vm()
        val i = ItemMovimiento(movimiento(TipoMovimiento.AJUSTE, turnoId = 1, delta = -1250, entidad = TipoEntidad.INSUMO), simbolo = "kg")

        vm.abrir(i)

        assertTrue(vm.state.value.dialogo)
        assertEquals("-1.25", vm.state.value.cantidad)
        assertEquals("", vm.state.value.motivo)
        assertFalse("mientras no se toque nada, no hay nada que guardar", vm.state.value.puedeGuardar(i.movimiento))
        vm.motivo("eran 2 kg")
        assertFalse("la cantidad es la misma", vm.state.value.puedeGuardar(i.movimiento))
        vm.cantidad("-2")
        assertTrue(vm.state.value.puedeGuardar(i.movimiento))
    }

    @Test fun guardarPideLaCorreccionConLaCantidadEscritaYElMotivo() = runTest {
        val (vm, turnos) = vm()
        val i = ItemMovimiento(movimiento(TipoMovimiento.AJUSTE, turnoId = 1, delta = 5))
        val mensajes = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { mensajes += it } }
        var hecho = false

        vm.abrir(i)
        vm.cantidad("+50")
        vm.motivo("  eran   50  ")
        vm.guardar(i) { hecho = true }

        assertEquals(listOf(Triple(9L, 50L, "eran 50")), turnos.correcciones) // el motivo se guarda normalizado
        assertTrue(hecho)
        assertFalse("el diálogo se cierra", vm.state.value.dialogo)
        assertFalse("ya no se puede tocar otra vez", vm.state.value.puede)
        assertEquals(listOf("Ajuste corregido: +50."), mensajes)
    }

    @Test fun unaCantidadIlegibleNoSeGuardaYMarcaElCampo() = runTest {
        val (vm, turnos) = vm()
        val i = ItemMovimiento(movimiento(TipoMovimiento.AJUSTE, turnoId = 1))

        vm.abrir(i)
        vm.cantidad("bastante")
        vm.motivo("me equivoqué")
        vm.guardar(i) {}

        assertTrue(vm.state.value.error)
        assertTrue(vm.state.value.dialogo)
        assertTrue(turnos.correcciones.isEmpty())
    }

    @Test fun siElTurnoYaNoEstaAbiertoSeAvisaYSigueElDialogo() = runTest {
        val (vm, turnos) = vm()
        val i = ItemMovimiento(movimiento(TipoMovimiento.AJUSTE, turnoId = 1))
        turnos.errorCorregir = AppError.TurnoCerrado
        val mensajes = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { mensajes += it } }

        vm.abrir(i)
        vm.cantidad("50")
        vm.motivo("eran 50")
        vm.guardar(i) {}

        assertEquals(listOf(TextosMovimiento.TURNO_CERRADO), mensajes)
        assertTrue("el diálogo sigue abierto para reintentar", vm.state.value.dialogo)
    }
}
