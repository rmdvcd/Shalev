package cu.shalev.domain

import cu.shalev.core.money.Cup
import cu.shalev.core.quantity.Cantidad
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.RecetaLinea
import cu.shalev.domain.model.Servicio
import cu.shalev.domain.usecase.EliminarServicios
import cu.shalev.domain.usecase.GuardarServicio
import cu.shalev.domain.usecase.ObservarServicios
import cu.shalev.domain.usecase.ObtenerFichaServicio
import cu.shalev.domain.usecase.ServiciosFiltro
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.Dispatchers

/** 0.21.9 (P59): casos de uso de Servicios que no tenían prueba propia (ficha, borrado en bloque, disponibles, guardar). */
class ServiciosUseCasesTest {

    private val insumos = FakeInsumos().apply { put(insumo(1, "Tinte", precio = 30, cantidad = 5)) }
    private val servicios = FakeServicios()
    private fun servicio(id: Long, nombre: String = "S$id", tipo: String = "Peluquería") =
        Servicio(id = id, nombre = nombre, tipo = tipo, importe = Cup.ofPesos(200), creadoEn = T0)

    @Test fun fichaResuelveInsumosYMarcaLosBorrados() = runTest {
        servicios.crear(servicio(1, "Tinte completo"), listOf(RecetaLinea(1, Cantidad.enteras(2)), RecetaLinea(77, Cantidad.enteras(1))))
        val f = (ObtenerFichaServicio(servicios, insumos)(1) as AppResult.Ok).value
        assertEquals(listOf("Tinte", "Insumo eliminado"), f.lineas.map { it.nombre })
        assertEquals(listOf(true, false), f.lineas.map { it.existe })
        assertFalse(f.vendible)                                              // le falta un insumo
        servicios.eliminar(1)
        assertEquals(AppError.NoEncontrado, (ObtenerFichaServicio(servicios, insumos)(1) as AppResult.Err).error)
        assertEquals(AppError.NoEncontrado, (ObtenerFichaServicio(servicios, insumos)(5) as AppResult.Err).error)
    }

    @Test fun eliminarVariosCuentaLosQueFallanYNoRepite() = runTest {
        servicios.crear(servicio(1), emptyList()); servicios.crear(servicio(2), emptyList())
        val r = EliminarServicios(servicios)(listOf(1L, 1L, 2L, 99L))
        assertEquals(EliminarServicios.Resultado(eliminados = 2, fallidos = 1), r)
    }

    @Test fun disponiblesExcluyeBorradosYReaccionaALasExistencias() = runTest {
        servicios.crear(servicio(1), listOf(RecetaLinea(1, Cantidad.enteras(2))))
        servicios.crear(servicio(2), emptyList())
        servicios.eliminar(2)
        val obs = ObservarServicios(servicios, insumos, Dispatchers.Unconfined)
        val d = obs.disponibles().first()
        assertEquals(setOf(1L), d.keys)
        assertEquals(2L, d.getValue(1).alcanza)
        insumos.put(insumo(1, "Tinte", precio = 30, cantidad = 1))
        assertEquals(0L, obs.disponibles().first().getValue(1).alcanza)
    }

    @Test fun guardarRecortaValidaYNoEditaUnoBorrado() = runTest {
        val guardar = GuardarServicio(servicios, FixedClock())
        val id = (guardar(servicio(0, "  Corte  ", "  Peluquería ").copy(descripcion = "   "), emptyList()) as AppResult.Ok).value
        val s = servicios.obtener(id)!!
        assertEquals("Corte", s.nombre)
        assertEquals("Peluquería", s.tipo)
        assertEquals(null, s.descripcion)

        val invalido = guardar(servicio(0, " "), emptyList())
        assertTrue((invalido as AppResult.Err).error is AppError.Validacion)
        assertEquals(1, servicios.items.value.size)                         // no escribió nada

        servicios.eliminar(id)
        assertEquals(AppError.NoEncontrado, (guardar(s.copy(nombre = "Otro"), emptyList()) as AppResult.Err).error)
    }

    @Test fun tiposSinRepetirPorMayusculasNiVaciosYOrdenadosSinTildes() {
        val ss = listOf(servicio(1, tipo = "Uñas"), servicio(2, tipo = "uñas "), servicio(3, tipo = " "), servicio(4, tipo = "Árbol"),
            servicio(5, tipo = "Barbería"), servicio(6, tipo = "Zeta").copy(eliminado = true))
        assertEquals(listOf("Árbol", "Barbería", "Uñas"), ServiciosFiltro.tipos(ss))
    }
}
