package cu.shalev.app.notificacion

import cu.shalev.data.sync.AtencionSync
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.19.2 · Cuándo corre el servicio en primer plano de la principal y qué dice la notificación única. */
class PlanAvisoTest {
    @Test fun detalleExpandidoNoIncluyeDatosFinancieros() {
        val texto = PlanAviso.detalle(PlanAviso(servicio = true, mostrar = true, turnoDesde = null, conectadas = 2), null)
        org.junit.Assert.assertTrue(texto.contains("Turno de esta app cerrado"))
        org.junit.Assert.assertTrue(texto.contains("Sincronización local activa"))
        org.junit.Assert.assertFalse(texto.contains("CUP"))
    }


    private val desde = Instant.parse("2026-10-02T13:30:00Z")
    private val sinSecundarias = AtencionSync()
    private fun principal(abiertos: Int = 0, conectadas: Int = 0) = AtencionSync(true, abiertos, conectadas)

    @Test fun sinSecundariasEsComoEnP29() {
        val fuera = PlanAviso.calcular(visible = false, turnoPropioDesde = desde, atencion = sinSecundarias)
        assertFalse(fuera.servicio); assertTrue(fuera.mostrar)
        val dentro = PlanAviso.calcular(visible = true, turnoPropioDesde = desde, atencion = sinSecundarias)
        assertFalse(dentro.servicio); assertFalse(dentro.mostrar)
        assertFalse(PlanAviso.calcular(false, null, sinSecundarias).mostrar)
    }

    @Test fun principalConSecundariasAtiendeMientrasEstaAbierta() {
        val p = PlanAviso.calcular(visible = true, turnoPropioDesde = null, atencion = principal())
        assertTrue(p.servicio); assertTrue(p.mostrar)
    }

    @Test fun fueraDeLaAppSigueSoloSiHayTurnosAbiertos() {
        assertTrue(PlanAviso.calcular(false, null, principal(abiertos = 1)).servicio)
        val noche = PlanAviso.calcular(false, null, principal(abiertos = 0))
        assertFalse(noche.servicio); assertFalse(noche.mostrar)
    }

    @Test fun unaSecundariaNoTieneServicio() {
        // ArranqueSync solo marca principalConSecundarias en la PRINCIPAL; una secundaria nunca lo arranca.
        assertFalse(PlanAviso.calcular(true, desde, AtencionSync(false, 3, 0)).servicio)
    }

    @Test fun textos() {
        val turnoYApps = PlanAviso.calcular(false, desde, principal(abiertos = 2, conectadas = 2))
        assertEquals("Turno abierto desde las 9:30 · 2 apps conectadas", PlanAviso.texto(turnoYApps, "9:30"))
        val turnoSinApps = PlanAviso.calcular(false, desde, principal(abiertos = 1))
        assertEquals("Turno abierto desde las 9:30", PlanAviso.texto(turnoSinApps, "9:30"))
        val unaApp = PlanAviso.calcular(true, null, principal(conectadas = 1))
        assertEquals("1 app conectada", PlanAviso.texto(unaApp, null))
        val esperando = PlanAviso.calcular(true, null, principal())
        assertEquals(PlanAviso.ESPERANDO, PlanAviso.texto(esperando, null))
    }

    @Test fun detalleMuestraTurnosDeLaRedSinDatosPersonales() {
        val p = PlanAviso.calcular(false, desde, principal(abiertos = 3, conectadas = 2))
        assertEquals(3, p.turnosAbiertos)
        assertTrue(PlanAviso.detalle(p, "9:30").contains("3 turnos abiertos en el negocio"))
        assertEquals(0, PlanAviso.calcular(false, desde, sinSecundarias).turnosAbiertos)
    }

    @Test fun sinServicioNoSeCuentanConexiones() {
        val p = PlanAviso.calcular(false, desde, AtencionSync(false, 1, 4))
        assertEquals(0, p.conectadas)
        assertFalse(PlanAviso.texto(p, "9:30").contains("app"))
        assertFalse(PlanAviso.texto(p, "9:30").contains("CUP"))
    }
}
