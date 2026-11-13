package cu.shalev.app.herramientas

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.*
import cu.shalev.domain.repository.GestionInventarioRepository
import cu.shalev.domain.repository.PerfilRepository
import cu.shalev.domain.usecase.RegistrarRecepcion
import cu.shalev.domain.usecase.RegistrarAjusteInventario
import cu.shalev.domain.usecase.UsuarioActual
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GestionInventarioViewModelTest {
    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()
    private class Repo : GestionInventarioRepository {
        var fallar = false
        val catalogo = MutableStateFlow<List<ArticuloGestion>>(emptyList())
        override fun articulos() = flow { if (fallar) error("Lectura simulada"); emitAll(catalogo) }
        override fun recepciones() = MutableStateFlow<List<RecepcionInventario>>(emptyList())
        override fun lotes() = MutableStateFlow<List<LoteInventario>>(emptyList())
        override suspend fun recibir(recepcion: NuevaRecepcion, autor: String) = AppResult.Ok(recepcion.uuid)
        override suspend fun ajustar(entidad: TipoEntidad, id: Long, delta: Cantidad, motivo: MotivoMovimiento, detalle: String, autor: String) = AppResult.Ok(Unit)
    }
    private val articulo = ArticuloGestion(TipoEntidad.PRODUCTO, 1, "Refresco", UnidadMedida.UNIDAD, Cantidad(1_000), Cup(125))
    private fun vm(repo: Repo = Repo()): GestionInventarioViewModel {
        val usuario = UsuarioActual(object : PerfilRepository {
            override val perfil = MutableStateFlow(Perfil())
            override suspend fun guardar(perfil: Perfil) = Unit
        })
        return GestionInventarioViewModel(repo, RegistrarRecepcion(repo, usuario), RegistrarAjusteInventario(repo, usuario))
    }
    @Test fun refrescarCatalogoNoReescribeElCostoDeCompraEnEdicion() = runTest {
        val vm = vm()
        vm.actualizarCatalogo(listOf(articulo))
        assertEquals("1.25", vm.costo)
        assertFalse(vm.tieneDatos)
        vm.costo = "2.75"
        vm.actualizarCatalogo(listOf(articulo.copy(cantidad = Cantidad(2_000), costo = Cup(150))))
        assertEquals("2.75", vm.costo)
        assertEquals(Cup(150), vm.articulo!!.costo)
        assertTrue(vm.tieneDatos)
    }
    @Test fun comprobanteOMotivoSolosNoSeDescartanSinAviso() = runTest {
        val vm = vm()
        assertFalse(vm.tieneDatos)
        vm.comprobante = "F-1"
        assertTrue(vm.tieneDatos)
        vm.comprobante = ""; vm.motivo = "Compra inicial"
        assertTrue(vm.tieneDatos)
    }
    @Test fun reintentarReabreElFlujoYConservaElBorrador() = runTest {
        val repo = Repo().apply { fallar = true }
        val vm = vm(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.articulos.collect {} }
        runCurrent()
        assertTrue(vm.hayErrorCarga)
        vm.comprobante = "F-1"
        repo.fallar = false; repo.catalogo.value = listOf(articulo)
        vm.reintentarCarga()
        runCurrent()
        assertFalse(vm.hayErrorCarga)
        assertEquals(listOf(articulo), vm.articulos.value)
        assertEquals("F-1", vm.comprobante)
    }
}
