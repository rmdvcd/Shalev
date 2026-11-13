package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Identificacion
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.Servicio
import cu.shalev.domain.model.TipoAlerta
import cu.shalev.domain.model.FiltroInventario
import cu.shalev.domain.model.nombreCompleto
import cu.shalev.domain.service.InventarioFiltro
import cu.shalev.domain.service.PlanificadorVenta
import cu.shalev.domain.service.LineaSolicitada
import cu.shalev.domain.service.Stock
import cu.shalev.domain.usecase.GuardarProducto
import cu.shalev.domain.usecase.GuardarServicio
import cu.shalev.domain.validation.Validadores
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 0.24.0: la Descripción distingue artículos con el mismo nombre («Cerveza Cristal» · «Lata 350 ml Superior»). */
class IdentificacionTest {

    private val productos = FakeProductos()
    private val insumos = FakeInsumos()
    private val guardar = GuardarProducto(productos, insumos, FixedClock())

    @Test fun nombreCompletoUneNombreYDescripcionEnUnaLinea() {
        assertEquals("Cerveza Cristal · Lata 350 ml Superior", Identificacion.nombreCompleto(" Cerveza Cristal ", " Lata 350 ml Superior "))
        assertEquals("Cerveza Cristal", Identificacion.nombreCompleto("Cerveza Cristal", null))
        assertEquals("Cerveza Cristal", Identificacion.nombreCompleto("Cerveza Cristal", "   "))
        assertEquals("Pan · Suave de 80 g", Identificacion.nombreCompleto("Pan", "Suave\nde  80 g")) // datos anteriores a 0.24.0
    }

    @Test fun laComparacionIgnoraMayusculasTildesYEspacios() {
        assertEquals(Identificacion.clave("Café  Serrano"), Identificacion.clave(" cafe serrano "))
    }

    @Test fun conflictoSoloSiElNombreSeRepite() {
        val otros = listOf(Triple(1L, "Cerveza Cristal", "Lata 350 ml"))
        assertNull(Identificacion.conflicto(0, "Cerveza Bucanero", null, otros))
        assertEquals(Identificacion.Conflicto.FALTA_DESCRIPCION, Identificacion.conflicto(0, "cerveza  cristal", null, otros))
        assertEquals(Identificacion.Conflicto.DESCRIPCION_REPETIDA, Identificacion.conflicto(0, "Cerveza Cristal", "LATA 350 ML", otros))
        assertNull(Identificacion.conflicto(0, "Cerveza Cristal", "Botella 1 L", otros))
        assertNull(Identificacion.conflicto(1, "Cerveza Cristal", null, otros)) // él mismo no cuenta
    }

    @Test fun guardarExigeDescripcionDistintaCuandoElNombreSeRepite() = runTest {
        productos.put(producto(1, "Cerveza Cristal").copy(descripcion = "Lata 350 ml Superior"))
        val sin = guardar(producto(0, "Cerveza Cristal"), null)
        assertEquals(AppError.Validacion("descripcion", AppError.Regla.REQUERIDO), (sin as AppResult.Err).error)
        val igual = guardar(producto(0, " cerveza cristal ").copy(descripcion = "lata 350 ML superior"), null)
        assertEquals(AppError.Duplicado("descripcion"), (igual as AppResult.Err).error)
        assertTrue(guardar(producto(0, "Cerveza Cristal").copy(descripcion = "Botella 1 L"), null) is AppResult.Ok)
        assertTrue(guardar(producto(0, "Cerveza Bucanero"), null) is AppResult.Ok) // nombre único: descripción opcional
    }

    @Test fun losYaRepetidosSeMarcanYSeExigeAlEditar() = runTest {
        productos.put(producto(1, "Ron"), producto(2, "Ron"), producto(3, "Ron").copy(descripcion = "Añejo 7 años"), producto(4, "Azúcar"))
        assertEquals(setOf(1L, 2L), Identificacion.productosPorDiferenciar(productos.items.value.values.toList()))
        val n = NivelesMinimos()
        val hoy = LocalDate.of(2026, 9, 1)
        assertEquals(2, Stock.conteo(productos.items.value.values.toList(), emptyList(), n, hoy, 7).de(TipoAlerta.NOMBRE_REPETIDO))
        val vista = InventarioFiltro.aplicar(productos.items.value.values.toList(), FiltroInventario(alerta = TipoAlerta.NOMBRE_REPETIDO), n, hoy, 7)
        assertEquals(setOf(1L, 2L), vista.items.map { it.producto.id }.toSet())
        // Editar el 1 sin diferenciarlo no se guarda; con descripción, sí, y deja de estar marcado (el 2 sigue sin ella).
        assertTrue(guardar(producto(1, "Ron").copy(cantidad = 3), null) is AppResult.Err)
        assertTrue(guardar(producto(1, "Ron").copy(descripcion = "Blanco 3 años"), null) is AppResult.Ok)
        assertEquals(setOf(2L), Identificacion.productosPorDiferenciar(productos.items.value.values.toList()))
    }

    @Test fun elEscanerPuedeCambiarLaCaducidadSinExigirla() = runTest {
        productos.put(producto(1, "Ron"), producto(2, "Ron"))
        val r = guardar(producto(1, "Ron").copy(fechaCaducidad = LocalDate.of(2027, 1, 1)), null, exigirDiferenciar = false)
        assertTrue(r is AppResult.Ok)
    }

    @Test fun descripcionDeUnaLineaYHasta40Caracteres() {
        assertNull(Validadores.descripcion("Lata 350 ml Superior"))
        assertEquals(AppError.Regla.RANGO, Validadores.descripcion("x".repeat(Validadores.MAX_DESCRIPCION + 1))?.regla)
        assertNull(Validadores.descripcion("x".repeat(Validadores.MAX_DESCRIPCION)))
        assertEquals(AppError.Regla.FORMATO, Validadores.descripcion("Lata\n350 ml")?.regla)
    }

    @Test fun laVentaCongelaNombreYDescripcion() {
        val p = producto(1, "Cerveza Cristal", cantidad = 5).copy(descripcion = "Lata 350 ml Superior")
        val r = PlanificadorVenta.cotizar(
            lineas = listOf(LineaSolicitada(1, 2)), productos = mapOf(1L to p),
            metodoPago = cu.shalev.domain.model.MetodoPago.EFECTIVO, preajustes = emptyList(),
        )
        assertEquals("Cerveza Cristal · Lata 350 ml Superior", (r as AppResult.Ok).value.detalles.single().nombre)
        assertEquals(p.nombreCompleto, r.value.detalles.single().nombre)
    }

    @Test fun losServiciosSiguenLaMismaRegla() = runTest {
        val servicios = FakeServicios()
        val g = GuardarServicio(servicios, FixedClock())
        fun s(nombre: String, d: String? = null) = Servicio(nombre = nombre, tipo = "Peluquería", importe = Cup.ofPesos(300), descripcion = d, creadoEn = T0)
        assertTrue(g(s("Corte"), emptyList()) is AppResult.Ok)
        assertEquals(AppError.Validacion("descripcion", AppError.Regla.REQUERIDO), (g(s("corte"), emptyList()) as AppResult.Err).error)
        assertTrue(g(s("Corte", "A domicilio"), emptyList()) is AppResult.Ok)
        assertEquals(AppError.Duplicado("descripcion"), (g(s("Corte", "a domicilio"), emptyList()) as AppResult.Err).error)
    }
}
