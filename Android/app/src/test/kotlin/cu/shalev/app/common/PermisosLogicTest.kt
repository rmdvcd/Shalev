package cu.shalev.app.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermisosLogicTest {

    @Test fun tablaDeEstados() {
        assertEquals(EstadoPermiso.SIN_HARDWARE, estadoPermiso(false, true, true, true))
        assertEquals(EstadoPermiso.CONCEDIDO, estadoPermiso(true, true, false, false))
        assertEquals(EstadoPermiso.EXPLICAR, estadoPermiso(true, false, false, false))
        assertEquals(EstadoPermiso.DENEGADO, estadoPermiso(true, false, true, true))
        assertEquals(EstadoPermiso.BLOQUEADO, estadoPermiso(true, false, true, false))
    }

    @Test fun antesDelDialogoDelSistemaSiempreHayExplicacion() {
        val e = explicacionCamara(EstadoPermiso.EXPLICAR)
        assertEquals(AccionPermiso.SOLICITAR, e.accion)
        assertTrue(e.detalle.contains("No se guardan fotos"))
    }

    @Test fun bloqueadoLlevaAAjustesDelTelefono() {
        assertEquals(AccionPermiso.ABRIR_AJUSTES, explicacionCamara(EstadoPermiso.BLOQUEADO).accion)
    }

    @Test fun textosHablanDelQrDeVinculacion() {
        EstadoPermiso.entries.forEach {
            assertTrue(it.name, explicacionCamara(it).detalle.contains("QR"))
        }
    }

    @Test fun textosSinTecnicismos() {
        val todo = EstadoPermiso.entries.map { explicacionCamara(it).detalle }.joinToString(" ").lowercase()
        listOf("permission", "runtime", "api", "http", "rationale", "código de barras").forEach { assertFalse(it, todo.contains(it)) }
    }
}
