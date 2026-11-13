package cu.shalev.app

import cu.shalev.domain.model.EstadoConexion
import cu.shalev.domain.model.FaseSync
import cu.shalev.domain.model.ProgresoSync
import org.junit.Assert.assertEquals
import org.junit.Test

/** 0.32.0: el fondo lo escribe quien abre el turno (también en una secundaria) y el avance se muestra en %). */
class FondoAperturaTest {
    @Test fun `el avance de la sincronizacion se lee en porcentaje`() {
        assertEquals("Recibiendo · 50 %", cu.shalev.app.vinculacion.VinculacionLogic.progreso(ProgresoSync(50, 100, FaseSync.RECIBIENDO)))
        assertEquals("Enviando · 10 %", cu.shalev.app.vinculacion.VinculacionLogic.progreso(ProgresoSync(10, 100, FaseSync.ENVIANDO)))
        assertEquals("Guardando", cu.shalev.app.vinculacion.VinculacionLogic.progreso(ProgresoSync(0, 0, FaseSync.APLICANDO)))
    }

    @Test fun `el texto de la conexion sigue siendo el de siempre`() {
        assertEquals("Desconectada", cu.shalev.app.vinculacion.VinculacionLogic.conexion(EstadoConexion.Desconectada))
        assertEquals("Conectada con la app principal", cu.shalev.app.vinculacion.VinculacionLogic.conexion(EstadoConexion.Conectada(java.time.Instant.now())))
    }
}
