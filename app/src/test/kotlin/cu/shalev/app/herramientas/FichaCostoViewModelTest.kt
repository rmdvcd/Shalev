package cu.shalev.app.herramientas

import cu.shalev.core.result.AppResult
import cu.shalev.domain.fichacosto.DestinoFicha
import cu.shalev.domain.fichacosto.FichaCostoRepository
import cu.shalev.domain.fichacosto.UnidadMargen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FichaCostoViewModelTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()
    private val ficha = FichaCostoGuardada(1, "Borrador", "", UnidadMargen.PORCENTAJE, "", emptyList())
    private class Repo : FichaCostoRepository {
        var fallar = true
        val fichas = MutableStateFlow<List<FichaCostoGuardada>>(emptyList())
        override fun observar(): Flow<List<FichaCostoGuardada>> = flow {
            if (fallar) error("fallo de lectura simulado")
            emitAll(fichas)
        }
        override suspend fun guardar(ficha: FichaCostoGuardada): AppResult<Unit> {
            fichas.value = fichas.value.filterNot { it.id == ficha.id } + ficha
            return AppResult.Ok(Unit)
        }
        override suspend fun eliminar(id: Long) = AppResult.Ok(Unit)
        override suspend fun exportar(ficha: FichaCostoGuardada, destino: DestinoFicha) = AppResult.Ok(1L)
    }
    @Test fun reintentarRecuperaLaSuscripcionSinPerderLoQueSeEstabaEditando() = runTest {
        val repo = Repo()
        val vm = FichaCostoViewModel(repo)
        runCurrent()
        assertNotNull(vm.error)
        vm.nombre = "En edición"
        repo.fallar = false
        repo.fichas.value = listOf(ficha)
        vm.reintentarCarga()
        runCurrent()
        assertNull(vm.error)
        assertEquals(listOf(ficha), vm.fichas)
        assertEquals("En edición", vm.nombre)
        repo.fichas.value = emptyList()
        runCurrent()
        assertTrue(vm.fichas.isEmpty()) // la observación sigue viva después de recuperarse
    }
    @Test fun editarUnaFichaExistenteTambienSeConsideraCambioSinGuardar() = runTest {
        val repo = Repo().apply { fallar = false }
        val vm = FichaCostoViewModel(repo)
        vm.abrir(ficha)
        assertFalse(vm.sinGuardar)
        vm.nombre = "Otro nombre"
        assertTrue(vm.sinGuardar)
        assertTrue(vm.guardarFicha().isSuccess)
        assertFalse(vm.sinGuardar)
        assertEquals("Otro nombre", repo.fichas.value.single().nombre)
    }
}
