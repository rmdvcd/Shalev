package cu.shalev.app.producto

import androidx.lifecycle.SavedStateHandle
import cu.shalev.app.FakeFotos
import cu.shalev.app.InsRepo
import cu.shalev.app.ProdRepo
import cu.shalev.app.RelojFijo
import cu.shalev.app.T0
import cu.shalev.app.prod
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.Receta
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.UnidadMedida
import cu.shalev.domain.usecase.GuardarProducto
import cu.shalev.domain.usecase.ObtenerCategorias
import java.time.LocalDate
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProductoFormLogicTest {
    private val base = ProductoForm(categoria = "Bebidas", nombre = "Refresco", precioCosto = "60", precioVenta = "1,450.00", cantidad = "10")

    @Test fun ventaDebeSuperarElCosto() {
        listOf("59", "60").forEach { venta ->
            assertTrue(Campos.PRECIO_VENTA in ProductoFormLogic.validar(base.copy(precioVenta = venta)))
        }
        assertFalse(Campos.PRECIO_VENTA in ProductoFormLogic.validar(base.copy(precioVenta = "60.01")))
    }

    @Test fun formularioValidoProduceProducto() {
        assertTrue(ProductoFormLogic.validar(base).isEmpty())
        val (p, receta) = ProductoFormLogic.aProducto(base.copy(descripcion = "  "), T0)!!
        assertEquals(Cup.ofPesos(1450), p.precioVenta)
        assertNull(p.descripcion)
        assertNull(receta)
    }

    @Test fun descripcionResumidaConAyudaYMensajes() { // 0.24.0
        assertEquals("Lo que lo distingue de otros con el mismo nombre · 20/40", ProductoFormLogic.ayudaDescripcion("Lata 350 ml Superior"))
        val largo = ProductoFormLogic.validar(base.copy(descripcion = "x".repeat(41)))
        assertEquals("Máximo 40 caracteres.", largo[Campos.DESCRIPCION])
        assertNull(ProductoFormLogic.errorIdentidad(0, "Refresco", "", emptyList(), "producto"))
        assertNull(ProductoFormLogic.errorIdentidad(5, "Refresco", "", listOf(Triple(5L, "Refresco", null)), "producto"))
        assertEquals(
            "Ya hay otro servicio con este nombre. Escribe una descripción que lo diferencie (p. ej. Lata 350 ml Superior).",
            ProductoFormLogic.errorIdentidad(0, "Corte", "", listOf(Triple(1L, "corte", null)), "servicio"),
        )
    }

    @Test fun erroresClarosPorCampo() {
        val e = ProductoFormLogic.validar(ProductoForm(precioCosto = "12.345", precioVenta = "0", cantidad = "-1", nivelBajo = "2", nivelCritico = "5"))
        assertEquals("Elige o escribe una categoría.", e[Campos.CATEGORIA])
        assertEquals("Escribe el nombre.", e[Campos.NOMBRE])
        assertEquals(ProductoFormLogic.FORMATO_IMPORTE, e[Campos.PRECIO_COSTO])
        assertEquals("El precio de venta debe ser mayor que el costo y que 0.", e[Campos.PRECIO_VENTA])
        assertEquals(ProductoFormLogic.FORMATO_ENTERO, e[Campos.CANTIDAD])
        assertTrue(e.getValue(Campos.NIVEL_CRITICO).contains("no mayor que el nivel bajo"))
        assertNull(ProductoFormLogic.aProducto(ProductoForm(), T0))
    }

    @Test fun elaboradoExigeRecetaYCalculaCosto() {
        val f = base.copy(categoria = "elaborado", precioCosto = "", fotoUri = "file:///x.jpg", fechaCaducidad = LocalDate.of(2027, 1, 1))
        assertEquals("Agrega al menos un insumo a la receta.", ProductoFormLogic.validar(f)[Campos.RECETA])
        val lineas = listOf(
            LineaRecetaForm(1, "Harina", "kg", Cup.ofPesos(200), "0.25"),  // 50
            LineaRecetaForm(2, "Huevo", "u", Cup.ofPesos(30), "2"),        // 60
        )
        val con = f.copy(receta = lineas)
        assertEquals(Cup.ofPesos(110), ProductoFormLogic.costoReceta(lineas))
        assertTrue(ProductoFormLogic.validar(con).isEmpty()) // foto y fecha se ignoran en Elaborado
        val (p, receta) = ProductoFormLogic.aProducto(con, T0)!!
        assertEquals(Cup.ofPesos(110), p.precioCosto)
        assertNull(p.fotoUri); assertNull(p.fechaCaducidad)
        // P26: sin existencias ni niveles propios: lo tecleado (aunque sea inválido) se ignora y se guarda 0 / vacío.
        val conBasura = con.copy(cantidad = "abc", nivelBajo = "x", nivelCritico = "-1")
        assertTrue(ProductoFormLogic.validar(conBasura).isEmpty())
        val (pb, _) = ProductoFormLogic.aProducto(conBasura, T0)!!
        assertEquals(0L, pb.cantidad); assertNull(pb.nivelBajo); assertNull(pb.nivelCritico)
        assertEquals(0L, p.cantidad)
        assertEquals(Cantidad(250), receta!!.lineas.first().cantidad)
        assertNull(ProductoFormLogic.costoReceta(lineas.map { it.copy(cantidad = "0") }))
        assertTrue(ProductoFormLogic.validar(con.copy(receta = lineas.map { it.copy(cantidad = "abc") })).containsKey(Campos.RECETA))
    }

    @Test fun ediciónConservaLosDatos() {
        val p = prod(4, "Pan").copy(nivelBajo = 8, fechaCaducidad = LocalDate.of(2026, 10, 5))
        val f = ProductoFormLogic.desde(p, null, emptyMap())
        assertEquals("60", f.precioCosto)
        assertEquals("8", f.nivelBajo)
        assertEquals("", f.nivelCritico)
        val (vuelta, _) = ProductoFormLogic.aProducto(f, T0.plusSeconds(99))!!
        assertEquals(p.copy(creadoEn = p.creadoEn), vuelta.copy(actualizadoEn = p.actualizadoEn))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProductoFormViewModelTest {
    private val productos = ProdRepo()
    private val insumos = InsRepo()
    private val fotos = FakeFotos()
    private val reloj = RelojFijo()
    private val eventos = mutableListOf<EventoForm>()

    @Before fun antes() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun despues() = Dispatchers.resetMain()

    private fun TestScope.vm(vararg args: Pair<String, Any?>): ProductoFormViewModel {
        val vm = ProductoFormViewModel(
            saved = SavedStateHandle(mapOf(*args)),
            productos = productos,
            insumosRepo = insumos,
            obtenerCategorias = ObtenerCategorias(productos),
            guardarProducto = GuardarProducto(productos, insumos, reloj),
            fotos = fotos,
            clock = reloj,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect { eventos += it } }
        return vm
    }

    private fun ProductoFormViewModel.completar() {
        categoria("Bebidas"); nombre("Malta"); precioCosto("80"); precioVenta("150"); cantidad("24")
    }

    @Test fun elNombrePrellenadoLlegaAlFormulario() = runTest {
        // La ruta solo trae el nombre (la descripción y la fecha se ponen en el formulario).
        val vm = vm("nombre" to "Malta")
        assertEquals("Malta", vm.state.value.form.nombre)
        assertEquals("", vm.state.value.form.descripcion)
        assertNull(vm.state.value.form.fechaCaducidad)
    }

    @Test fun erroresSoloTrasTocarOIntentarGuardar() = runTest {
        val vm = vm()
        assertTrue(vm.state.value.errores.isEmpty())
        vm.nombre("x"); vm.nombre("")
        assertEquals(setOf(Campos.NOMBRE), vm.state.value.errores.keys)
        vm.guardar()
        assertTrue(vm.state.value.intentado)
        assertTrue(vm.state.value.errores.keys.containsAll(listOf(Campos.CATEGORIA, Campos.PRECIO_VENTA, Campos.CANTIDAD)))
        assertTrue(productos.guardados.isEmpty())
    }

    @Test fun nombreRepetidoExigeDescripcionDistintaEnVivoYAlGuardar() = runTest { // 0.24.0
        productos.items.value = listOf(prod(1).copy(nombre = "Malta", descripcion = "Lata 355 ml"))
        val vm = vm()
        vm.completar()
        val aviso = vm.state.value.errores[Campos.DESCRIPCION].orEmpty()
        assertTrue(aviso, aviso.startsWith("Ya hay otro producto con este nombre") && aviso.contains("«Lata 355 ml»"))
        vm.guardar()
        assertTrue(productos.guardados.isEmpty())
        vm.descripcion("lata 355 ML")
        assertEquals(ProductoFormLogic.DESCRIPCION_REPETIDA, vm.state.value.errores[Campos.DESCRIPCION])
        vm.descripcion("Botella\n355 ml") // se queda en una línea
        assertEquals("Botella 355 ml", vm.state.value.form.descripcion)
        assertNull(vm.state.value.errores[Campos.DESCRIPCION])
        vm.guardar()
        assertEquals("Botella 355 ml", productos.guardados.single().descripcion)
    }

    @Test fun crearNuevoYAvisar() = runTest {
        val vm = vm()
        vm.completar()
        vm.guardar()
        val p = productos.items.value.single()
        assertEquals("Malta", p.nombre)
        assertEquals(T0, p.creadoEn)
        assertEquals("Producto agregado.", (eventos.single() as EventoForm.Guardado).mensaje)
    }

    @Test fun prellenadoDesdeLaRutaLlegaAlFormulario() = runTest {
        val vm = vm("nombre" to "Malta Guajira", "foto" to "file:///f.jpg")
        val f = vm.state.value.form
        assertEquals("Malta Guajira", f.nombre)
        assertEquals("file:///f.jpg", f.fotoUri)
        assertNull(f.fechaCaducidad)
        vm.categoria("Bebidas"); vm.precioCosto("1"); vm.precioVenta("2"); vm.cantidad("1")
        vm.guardar()
        assertEquals("Malta Guajira", productos.guardados.single().nombre)
    }

    @Test fun nombrePrellenadoSeLimpia() = runTest {
        // 0.21.4 (E4): lo que llega por la ruta pasa por la misma limpieza que antes el resultado del escáner
        val vm = vm("nombre" to "2 PCS GORILLA GLUE  100% &amp; MORE\tGLUE,")
        assertEquals("2 PCS GORILLA GLUE 100% & MORE GLUE", vm.state.value.form.nombre)
    }

    @Test fun editarProductoElaboradoConReceta() = runTest {
        insumos.items.value = listOf(
            Insumo(1, "Harina", UnidadMedida.KILOGRAMO, Cup.ofPesos(200), Cantidad(10_000), creadoEn = T0),
            Insumo(2, "Azúcar", UnidadMedida.KILOGRAMO, Cup.ofPesos(300), Cantidad(5_000), creadoEn = T0),
        )
        productos.items.value = listOf(prod(7, "Panetela", categoria = "Elaborado"))
        productos.recetas[7] = Receta(7, listOf(RecetaLinea(1, Cantidad(500))))
        val vm = vm("id" to 7L)
        val s = vm.state.value
        assertEquals("Panetela", s.form.nombre)
        assertEquals("0.5", s.form.receta.single().cantidad)
        assertEquals(Cup.ofPesos(100), s.costoReceta)
        assertEquals(listOf(2L), s.insumosDisponibles.map { it.id })
        vm.agregarInsumo(insumos.items.value[1])
        vm.cantidadInsumo(2, "0.1")
        assertEquals(Cup.ofPesos(130), vm.state.value.costoReceta)
        vm.quitarInsumo(1)
        vm.guardar()
        assertEquals("Cambios guardados.", (eventos.single() as EventoForm.Guardado).mensaje)
        assertEquals(Cup.ofPesos(30), productos.items.value.single().precioCosto)
        assertEquals(0L, productos.items.value.single().cantidad) // P26: las 10 u guardadas se descartan
        assertEquals(listOf(2L), productos.recetas.getValue(7).lineas.map { it.insumoId })
    }

    @Test fun productoInexistente() = runTest {
        val vm = vm("id" to 99L)
        assertTrue(vm.state.value.noEncontrado)
    }

    @Test fun fotoElegidaSeCopiaYErroresSeAvisan() = runTest {
        val vm = vm()
        vm.fotoElegida("content://media/1")
        assertEquals("file:///fotos/importada.jpg", vm.state.value.form.fotoUri)
        vm.quitarFoto()
        assertNull(vm.state.value.form.fotoUri)
        fotos.fallarImportar = true
        vm.fotoElegida("content://media/2")
        assertEquals(ProductoFormViewModel.ERROR_FOTO, (eventos.single() as EventoForm.Mensaje).texto)
        assertFalse(vm.state.value.importandoFoto)
    }

    @Test fun categoriaInsumosEnUnNuevoPasaAlFormularioDeInsumo() = runTest {
        val vm = vm()
        vm.nombre("Harina ")
        vm.categoria("insumos")
        assertEquals(EventoForm.EsInsumo("Harina"), eventos.single())
        assertEquals("", vm.state.value.form.categoria)
    }

    @Test fun categoriaInsumosAlEditarSigueReservada() = runTest {
        productos.items.value = listOf(prod(7))
        val vm = vm("id" to 7L)
        vm.categoria("Insumos")
        assertTrue(eventos.none { it is EventoForm.EsInsumo })
        assertEquals("Insumos", vm.state.value.form.categoria)
    }
}
