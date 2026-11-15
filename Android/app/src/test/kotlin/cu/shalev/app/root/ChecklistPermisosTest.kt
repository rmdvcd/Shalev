package cu.shalev.app.root

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El checklist de permisos de la primera apertura: se piden uno a uno y cada fila se marca al responder. */
class ChecklistPermisosTest {
    private val cam = Manifest.permission.CAMERA
    private val notif = Manifest.permission.POST_NOTIFICATIONS
    private val fotos = Manifest.permission.READ_MEDIA_IMAGES

    @Test fun laListaDependeDeLaVersionDeAndroid() {
        assertEquals(listOf(cam, notif, fotos), PermisosIniciales.lista(33).map { it.permiso })
        assertEquals(listOf(cam, Manifest.permission.READ_EXTERNAL_STORAGE), PermisosIniciales.lista(32).map { it.permiso })
        assertEquals(TipoPermisoInicial.FOTOS, PermisosIniciales.lista(26).last().tipo)
        // Cada permiso dice para qué es.
        TipoPermisoInicial.entries.forEach { assertTrue(it.detalle.isNotBlank()) }
    }

    @Test fun sePidenEnOrdenYCadaRespuestaMarcaSuFila() {
        var c = ChecklistPermisos.inicial(PermisosIniciales.lista(35)) { false }
        assertEquals(cam, c.siguiente!!.permiso)
        assertNull(c.pidiendo)
        c = c.pedir(cam)
        assertEquals(EstadoPermiso.PIDIENDO, c.estado(cam))
        assertEquals(notif, c.siguiente!!.permiso)
        assertFalse(c.terminado)
        c = c.responder(cam, concedido = true)
        assertEquals(EstadoPermiso.CONCEDIDO, c.estado(cam))
        c = c.pedir(notif).responder(notif, concedido = false)
        assertEquals(EstadoPermiso.DENEGADO, c.estado(notif))
        c = c.pedir(fotos).responder(fotos, concedido = true)
        assertTrue(c.terminado)
        assertEquals(2, c.concedidos)
        assertEquals(1, c.denegados)
        assertEquals("Permitidos: 2 de 3.", TextosPermisosIniciales.resumen(c.concedidos, c.items.size))
    }

    @Test fun losConcedidosDeAntesSalenMarcadosYNoSePiden() {
        val c = ChecklistPermisos.inicial(PermisosIniciales.lista(35)) { it == cam }
        assertEquals(EstadoPermiso.CONCEDIDO, c.estado(cam))
        assertEquals(notif, c.siguiente!!.permiso)
        assertTrue(ChecklistPermisos.inicial(PermisosIniciales.lista(35)) { true }.terminado)
    }

    @Test fun unaRespuestaSueltaNoCambiaNada() {
        val c = ChecklistPermisos.inicial(PermisosIniciales.lista(35)) { false }
        assertEquals(c, c.responder(cam, true)) // no se estaba pidiendo
        val pidiendo = c.pedir(cam)
        assertEquals(pidiendo, pidiendo.pedir(cam))
        val hecho = pidiendo.responder(cam, false)
        assertEquals(hecho, hecho.responder(cam, true)) // ya contestado
    }
}
