package cu.shalev.app.herramientas

import cu.shalev.app.producto.Campos
import cu.shalev.app.producto.LineaRecetaForm
import cu.shalev.app.producto.ProductoForm
import cu.shalev.app.producto.ProductoFormLogic
import cu.shalev.app.servicios.CamposServicio
import cu.shalev.app.servicios.ServicioForm
import cu.shalev.app.servicios.ServicioFormLogic
import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.domain.model.Categorias
import cu.shalev.domain.model.Insumo
import cu.shalev.domain.model.UnidadMedida
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class CostosAdicionalesFormTest {
    private val ahora = Instant.parse("2026-11-13T12:00:00Z")
    @Test fun editarElaboradoConservaCentavosYAgregaCostoAdicionalALaReceta() {
        val f = ProductoForm(categoria = Categorias.ELABORADO, nombre = "Pan", precioVenta = "5.25", costoAdicional = "1.25",
            receta = listOf(LineaRecetaForm(1, "Harina", "kg", Cup(150), "0.5")))
        val (producto, receta) = ProductoFormLogic.aProducto(f, ahora)!!
        assertEquals(Cup(200), producto.precioCosto)
        assertEquals(Cup(125), producto.costoAdicional)
        assertEquals(Cup(525), producto.precioVenta)
        val i = Insumo(1, "Harina", UnidadMedida.KILOGRAMO, Cup(150), Cantidad(1000), creadoEn = ahora)
        assertEquals("1.25", ProductoFormLogic.desde(producto, receta, mapOf(1L to i)).costoAdicional)
    }
    @Test fun servicioSinMaterialesPuedeConservarCostoDeTrabajoYEditarSinRedondear() {
        val f = ServicioForm(nombre = "Corte", tipo = "Barbería", importe = "8.25", costoAdicional = "1.25")
        val (servicio, lineas) = ServicioFormLogic.aServicio(f, ahora)!!
        assertEquals(Cup(125), ServicioFormLogic.costo(f))
        assertEquals(Cup(125), servicio.costoAdicional)
        assertEquals("1.25", ServicioFormLogic.desde(servicio, lineas, emptyMap()).costoAdicional)
        assertEquals("8.25", ServicioFormLogic.desde(servicio, lineas, emptyMap()).importe)
    }
    @Test fun costosFueraDeLongSeMuestranComoErrorDeFormularioNoComoExcepcion() {
        val linea = LineaRecetaForm(1, "Material", "u", Cup(Long.MAX_VALUE), "2")
        val f = ProductoForm(categoria = Categorias.ELABORADO, nombre = "Extremo", precioVenta = "1", receta = listOf(linea))
        assertNull(ProductoFormLogic.costoReceta(f.receta))
        assertTrue(ProductoFormLogic.validar(f).containsKey(Campos.RECETA))
        assertNull(ProductoFormLogic.aProducto(f, ahora))
        val conAdicional = f.copy(receta = listOf(linea.copy(cantidad = "1")), costoAdicional = "0.01")
        assertNull(ProductoFormLogic.costoElaborado(conAdicional))
        val servicio = ServicioForm(nombre = "Extremo", tipo = "General", importe = "1", insumos = conAdicional.receta, costoAdicional = "0.01")
        assertNull(ServicioFormLogic.costo(servicio))
        assertTrue(ServicioFormLogic.validar(servicio).containsKey(CamposServicio.INSUMOS))
        assertNull(ServicioFormLogic.aServicio(servicio, ahora))
    }

    @Test fun costosAdicionalesNegativosOConTresDecimalesSeMarcanAntesDeGuardar() {
        assertTrue(ServicioFormLogic.validar(ServicioForm(costoAdicional = "-0.01")).containsKey(CamposServicio.COSTO_ADICIONAL))
        assertTrue(ProductoFormLogic.validar(ProductoForm(categoria = Categorias.ELABORADO, costoAdicional = "1.001")).containsKey(Campos.COSTO_ADICIONAL))
    }
}
