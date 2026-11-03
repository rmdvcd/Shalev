package cu.shalev.app.registros

import cu.shalev.core.money.Cup
import cu.shalev.domain.model.FiltroRegistros
import cu.shalev.domain.model.PeriodoRegistro
import cu.shalev.domain.usecase.TipoRegistro
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** P18 (A12): codificación de los filtros de Registros para SavedStateHandle. */
class FiltrosGuardadosTest {
    @Test fun idaYVueltaConservaTodo() {
        val f = FiltroRegistros(
            texto = "pan | dulce", periodo = PeriodoRegistro.PERSONALIZADO,
            desde = LocalDate.of(2026, 8, 1), hasta = LocalDate.of(2026, 8, 21),
            importeMin = Cup.ofPesos(10), importeMax = Cup(123_456), vendedor = "Luis",
        )
        assertEquals(f, FiltrosGuardados.decodificar(FiltrosGuardados.codificar(f)))
        // 0.20.0: el estado guardado por la 0.19 (6 valores, sin vendedor) se sigue leyendo.
        assertEquals(f.copy(vendedor = null), FiltrosGuardados.decodificar(FiltrosGuardados.codificar(f).take(6)))
        assertEquals(FiltroRegistros(), FiltrosGuardados.decodificar(FiltrosGuardados.codificar(FiltroRegistros())))
    }

    @Test fun valorDanadoSeIgnora() {
        assertNull(FiltrosGuardados.decodificar(null))
        assertNull(FiltrosGuardados.decodificar(listOf("a", "b")))
        assertNull(FiltrosGuardados.decodificar(listOf("", "NO_EXISTE", "", "", "", "")))
        assertNull(FiltrosGuardados.decodificar(listOf("", "TODO", "2026-13-40", "", "", "")))
    }

    @Test fun pestanaDesconocidaVuelveAVentas() {
        assertEquals(PestanaRegistros.VENTAS, FiltrosGuardados.pestana(null))
        assertEquals(PestanaRegistros.VENTAS, FiltrosGuardados.pestana("OTRA"))
        assertEquals(PestanaRegistros.TURNOS, FiltrosGuardados.pestana("TURNOS"))
    }

    @Test fun leerSoloDevuelveLosTiposGuardados() {
        val guardado = mapOf(FiltrosGuardados.clave(TipoRegistro.MOVIMIENTOS) to FiltrosGuardados.codificar(FiltroRegistros(texto = "x")))
        assertEquals(mapOf(TipoRegistro.MOVIMIENTOS to FiltroRegistros(texto = "x")), FiltrosGuardados.leer { guardado[it] })
    }

    @Test fun permanenciaEnUnaCadenaSinElTextoDelBuscador() {
        // Al cerrar y volver a abrir la app se recuerdan los filtros marcados, no lo que se estaba buscando.
        val f = FiltroRegistros(
            texto = "pan", periodo = cu.shalev.domain.model.PeriodoRegistro.HOY,
            vendedor = "María",
        )
        val vuelta = FiltrosGuardados.decodificarTexto(FiltrosGuardados.codificarTexto(f))
        assertEquals(f.copy(texto = ""), vuelta)
        assertNull(FiltrosGuardados.decodificarTexto(null))
        assertNull(FiltrosGuardados.decodificarTexto("cadena de otra versión"))
    }
}
