package cu.shalev.app.notificacion

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import cu.shalev.designsystem.component.TipoAviso
import cu.shalev.domain.model.FaseSync
import cu.shalev.domain.model.ProgresoSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Notificaciones del sistema construidas de verdad (Robolectric): barra de progreso horizontal de la actualización y
 * de los respaldos, el resultado en el mismo sitio, y los avisos de la sincronización.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificacionesSistemaTest {
    private lateinit var app: Application
    private lateinit var nm: NotificationManager

    @Before fun preparar() {
        app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        nm = app.getSystemService(NotificationManager::class.java)
    }

    private fun Notification.titulo() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    private fun Notification.texto() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    private fun Notification.progreso() = extras.getInt(Notification.EXTRA_PROGRESS)
    private fun Notification.maximo() = extras.getInt(Notification.EXTRA_PROGRESS_MAX)
    private fun Notification.indeterminada() = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
    private fun Notification.fija() = flags and Notification.FLAG_ONGOING_EVENT != 0
    private fun publicada(id: Int): Notification? = shadowOf(nm).getNotification(id)

    @Test fun descargaConBarraDeterminadaYCancelar() {
        val n = NotificadorOperaciones(app, OperacionesEnCurso(), VisibilidadApp())
        n.aplicar(Operacion.ACTUALIZACION, EstadoOperacion(TipoAviso.CARGA, "Actualización de Shalev", "Descargando la actualización…", 0.42f, AccionOperacion.CANCELAR), visible = false)
        val notif = publicada(40)!!
        assertEquals("Actualización de Shalev", notif.titulo())
        assertEquals("Descargando la actualización… · 42 %", notif.texto())
        assertEquals(100, notif.maximo())
        assertEquals(42, notif.progreso())
        assertFalse(notif.indeterminada())
        assertTrue("la barra no se descarta con un deslizamiento", notif.fija())
        assertEquals(PlanOperacion.CANAL_OPERACIONES, notif.channelId)
        assertEquals("Cancelar", notif.actions.single().title.toString())
        assertNotNull(notif.contentIntent)
        // El canal existe y es silencioso.
        val canal = nm.getNotificationChannel(PlanOperacion.CANAL_OPERACIONES)!!
        assertEquals(NotificationManager.IMPORTANCE_LOW, canal.importance)
        assertNull(canal.sound)
    }

    @Test fun respaldoDeUnaEtapaConBarraIndeterminada() {
        val n = NotificadorOperaciones(app, OperacionesEnCurso(), VisibilidadApp())
        n.aplicar(Operacion.RESPALDO, EstadoOperacion(TipoAviso.CARGA, "Comprobando el respaldo", "Etapa 1 de 1 · Comprobando el archivo…"), visible = true)
        val notif = publicada(41)!!
        assertTrue(notif.indeterminada())
        assertTrue(notif.fija())
        assertTrue(notif.actions.isNullOrEmpty())
    }

    @Test fun laBarraPasaAErrorEnSuSitioYSeQuedaEnProblemas() {
        val ops = OperacionesEnCurso()
        val n = NotificadorOperaciones(app, ops, VisibilidadApp())
        n.aplicar(Operacion.ACTUALIZACION, EstadoOperacion(TipoAviso.CARGA, "Actualización", "Descargando", 0.5f), visible = false)
        n.aplicar(Operacion.ACTUALIZACION, EstadoOperacion(TipoAviso.ERROR, "No se pudo actualizar", "Sin conexión", accion = AccionOperacion.ABRIR), visible = false)
        assertEquals("una sola notificación: el resultado ocupa el sitio de la barra", 1, shadowOf(nm).size())
        val notif = publicada(40)!!
        assertEquals("No se pudo actualizar", notif.titulo())
        assertEquals(PlanOperacion.CANAL_PROBLEMAS, notif.channelId)
        assertFalse(notif.fija())
        assertEquals(0, notif.maximo())
        assertEquals("el error no caduca", 0L, notif.timeoutAfter)
        assertEquals("Abrir", notif.actions.single().title.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, nm.getNotificationChannel(PlanOperacion.CANAL_PROBLEMAS)!!.importance)
    }

    @Test fun elExitoCaducaYConLaAppDelanteSeRetiraYSeConsume() {
        val ops = OperacionesEnCurso()
        val n = NotificadorOperaciones(app, ops, VisibilidadApp())
        ops.terminar(Operacion.RESPALDO, TipoAviso.EXITO, "Respaldo guardado", "3 productos")
        n.aplicar(Operacion.RESPALDO, ops.estado(Operacion.RESPALDO), visible = false)
        assertEquals(ReglasNotificacion.EXPIRA_MS, publicada(41)!!.timeoutAfter)
        // Vuelve a Shalev: la isla o el diálogo lo enseñan; la notificación sobra.
        n.aplicar(Operacion.RESPALDO, ops.estado(Operacion.RESPALDO), visible = true)
        assertNull(publicada(41))
        assertNull(ops.estado(Operacion.RESPALDO))
    }

    @Test fun cancelarRetiraLaBarra() {
        val n = NotificadorOperaciones(app, OperacionesEnCurso(), VisibilidadApp())
        n.aplicar(Operacion.ACTUALIZACION, EstadoOperacion(TipoAviso.CARGA, "Actualización", "Descargando", 0.1f), visible = false)
        assertNotNull(publicada(40))
        n.aplicar(Operacion.ACTUALIZACION, null, visible = false)
        assertNull(publicada(40))
    }

    @Test fun sinPermisoNoSePublicaNada() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val n = NotificadorOperaciones(app, OperacionesEnCurso(), VisibilidadApp())
        n.aplicar(Operacion.RESPALDO, EstadoOperacion(TipoAviso.CARGA, "Respaldo", "Leyendo", 0.2f), visible = false)
        assertEquals(0, shadowOf(nm).size())
    }

    // ---------------------------------------------------------------- sincronización

    @Test fun barraDeSincronizacionYResultado() {
        val barra = NotificacionesSync.progreso(app, ProgresoSync(30, 120, FaseSync.RECIBIENDO), apps = 2)
        assertEquals("Sincronizando 2 apps", barra.titulo())
        assertEquals(120, barra.maximo())
        assertEquals(30, barra.progreso())
        assertEquals("25 %", barra.texto())
        assertTrue(barra.fija())
        val sinTotal = NotificacionesSync.progreso(app, ProgresoSync(0, 0, FaseSync.ENVIANDO), apps = 1)
        assertTrue(sinTotal.indeterminada())
        assertEquals("Enviando tus datos…", sinTotal.texto())

        val fin = NotificacionesSync.sincronizada(app, "Ana", repeticiones = 1)
        assertEquals("Sincronización con Ana terminada", fin.texto())
        assertEquals(AvisoSync.CANAL_SINCRONIZADA, fin.channelId)
        assertEquals(ReglasNotificacion.EXPIRA_MS, fin.timeoutAfter)
        assertFalse(fin.fija())
    }

    @Test fun conexionesConRafagaGrupoYResumen() {
        val conectada = NotificacionesSync.conexion(app, "Luis", conectado = true, repeticiones = 1)
        assertEquals(AvisoSync.GRUPO_SYNC, conectada.group)
        assertEquals(ReglasNotificacion.EXPIRA_MS, conectada.timeoutAfter)
        assertEquals(0, conectada.flags and Notification.FLAG_ONLY_ALERT_ONCE)

        val repetida = NotificacionesSync.conexion(app, "Luis", conectado = false, repeticiones = 3)
        assertEquals("La app de Luis se desconectó (×3)", repetida.texto())
        assertEquals(AvisoSync.CANAL_DESCONECTADA, repetida.channelId)
        assertEquals("la desconexión se queda", 0L, repetida.timeoutAfter)
        assertTrue("la ráfaga no vuelve a sonar", repetida.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)

        val resumen = NotificacionesSync.resumen(app, listOf("La app de Luis se conectó", "La app de Ana se desconectó"), persistente = true)
        assertTrue(resumen.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        assertEquals(2, resumen.number)
        assertEquals("2 avisos de las apps vinculadas", resumen.texto())
        assertEquals(0L, resumen.timeoutAfter)
    }

    @Test fun sincronizacionCortadaConReintentar() {
        val n = NotificacionesSync.cortada(app)
        assertEquals(NotificacionesSync.CORTADA, n.texto())
        assertEquals(PlanOperacion.CANAL_PROBLEMAS, n.channelId)
        assertEquals(0L, n.timeoutAfter)
        assertEquals("Reintentar", n.actions.single().title.toString())
    }
}
