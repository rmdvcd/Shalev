package cu.shalev.app.notificacion

import cu.shalev.designsystem.component.TipoAviso
import cu.shalev.designsystem.token.ColorTokens
import cu.shalev.domain.model.FaseSync
import cu.shalev.domain.model.ProgresoSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Notificacion.md aplicado a las notificaciones del sistema: reglas puras, sin Android. */
class ReglasNotificacionTest {

    // ---------------------------------------------------------------- operaciones en curso

    @Test fun elAvanceSoloSePublicaCuandoCambiaElPorcentaje() {
        val ops = OperacionesEnCurso()
        val vistos = mutableListOf<Map<Operacion, EstadoOperacion>>()
        ops.avanzar(Operacion.ACTUALIZACION, "Actualización", "Descargando", 0.101f, AccionOperacion.CANCELAR)
        vistos += ops.estados.value
        ops.avanzar(Operacion.ACTUALIZACION, "Actualización", "Descargando", 0.104f, AccionOperacion.CANCELAR) // mismo 10 %
        vistos += ops.estados.value
        ops.avanzar(Operacion.ACTUALIZACION, "Actualización", "Descargando", 0.11f, AccionOperacion.CANCELAR)
        vistos += ops.estados.value
        assertTrue("10 % → 10 % no repinta", vistos[0] === vistos[1])
        assertEquals(11, ops.estado(Operacion.ACTUALIZACION)!!.porcentaje)
        assertTrue(ops.estado(Operacion.ACTUALIZACION)!!.enCurso)
    }

    @Test fun laFraccionSeAcotaYSinFraccionEsIndeterminada() {
        val ops = OperacionesEnCurso()
        ops.avanzar(Operacion.RESPALDO, "Respaldo", "Leyendo", 1.7f)
        assertEquals(100, ops.estado(Operacion.RESPALDO)!!.porcentaje)
        ops.avanzar(Operacion.RESPALDO, "Respaldo", "Comprobando")
        assertNull(ops.estado(Operacion.RESPALDO)!!.porcentaje)
    }

    @Test fun terminarMutaEnSuSitioYQuitarLaRetira() {
        val ops = OperacionesEnCurso()
        ops.avanzar(Operacion.RESPALDO, "Respaldo", "Etapa 1 de 3", 0.33f)
        ops.terminar(Operacion.RESPALDO, TipoAviso.EXITO, "Respaldo guardado", "3 productos")
        val fin = ops.estado(Operacion.RESPALDO)!!
        assertEquals(EstadoOperacion(TipoAviso.EXITO, "Respaldo guardado", "3 productos", null, AccionOperacion.ABRIR), fin)
        assertFalse(fin.enCurso)
        ops.quitar(Operacion.RESPALDO)
        assertNull(ops.estado(Operacion.RESPALDO))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unaOperacionTerminadaNoPuedeSeguirCargando() {
        OperacionesEnCurso().terminar(Operacion.RESPALDO, TipoAviso.CARGA, "x", "y")
    }

    // ---------------------------------------------------------------- decisión de la notificación

    private fun plan(d: DecisionOperacion) = (d as DecisionOperacion.Mostrar).plan

    @Test fun laCargaLlevaBarraHorizontalFijaYSilenciosa() {
        val ops = OperacionesEnCurso()
        ops.avanzar(Operacion.ACTUALIZACION, "Actualización de Shalev", "Descargando la actualización…", 0.42f, AccionOperacion.CANCELAR)
        val p = plan(PlanOperacion.decidir(Operacion.ACTUALIZACION, ops.estado(Operacion.ACTUALIZACION), appVisible = true))
        assertEquals(40, p.id)
        assertEquals(PlanOperacion.CANAL_OPERACIONES, p.canal)
        assertEquals(42, p.progreso)
        assertEquals("Descargando la actualización… · 42 %", p.texto)
        assertTrue(p.fija)
        assertTrue(p.soloAlertarUnaVez)
        assertNull("la carga no caduca", p.expiraMs)
        assertEquals(AccionOperacion.CANCELAR, p.accion)
    }

    @Test fun sinFraccionLaBarraEsIndeterminada() {
        val e = EstadoOperacion(TipoAviso.CARGA, "Respaldo de Shalev", "Comprobando el archivo…")
        val p = plan(PlanOperacion.decidir(Operacion.RESPALDO, e, appVisible = false))
        assertEquals(-1, p.progreso)
        assertEquals("Comprobando el archivo…", p.texto)
        assertEquals(41, p.id)
    }

    @Test fun laUrgenciaDecideCanalPermanenciaYPrioridad() {
        fun p(t: TipoAviso) = plan(PlanOperacion.decidir(Operacion.RESPALDO, EstadoOperacion(t, "T", "x"), appVisible = false))
        // Discretos: sin sonido, prioridad baja y se van solos.
        listOf(TipoAviso.EXITO, TipoAviso.INFO).forEach { t ->
            assertEquals(PlanOperacion.CANAL_OPERACIONES, p(t).canal)
            assertEquals(ReglasNotificacion.EXPIRA_MS, p(t).expiraMs)
            assertEquals(androidx.core.app.NotificationCompat.PRIORITY_LOW, p(t).prioridad)
            assertNull(p(t).progreso)
            assertFalse(p(t).fija)
        }
        // Urgentes: canal «Problemas», se quedan hasta que se tocan.
        listOf(TipoAviso.ADVERTENCIA, TipoAviso.ERROR).forEach { t ->
            assertEquals(PlanOperacion.CANAL_PROBLEMAS, p(t).canal)
            assertNull(p(t).expiraMs)
            assertEquals(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT, p(t).prioridad)
            assertFalse("el resultado avisa una vez aunque ocupe el sitio de la barra", p(t).soloAlertarUnaVez)
        }
        assertEquals(androidx.core.app.NotificationCompat.CATEGORY_ERROR, p(TipoAviso.ERROR).categoria)
        assertEquals(androidx.core.app.NotificationCompat.CATEGORY_PROGRESS, plan(PlanOperacion.decidir(Operacion.RESPALDO, EstadoOperacion(TipoAviso.CARGA, "T", "x"), true)).categoria)
    }

    @Test fun conLaAppDelanteElResultadoNoSeDuplica() {
        val fin = EstadoOperacion(TipoAviso.ERROR, "No se pudo actualizar", "Sin conexión")
        assertEquals(DecisionOperacion.Quitar(consumir = true), PlanOperacion.decidir(Operacion.ACTUALIZACION, fin, appVisible = true))
        assertTrue(PlanOperacion.decidir(Operacion.ACTUALIZACION, fin, appVisible = false) is DecisionOperacion.Mostrar)
        assertEquals(DecisionOperacion.Quitar(consumir = false), PlanOperacion.decidir(Operacion.ACTUALIZACION, null, appVisible = false))
    }

    @Test fun coloresDeEstadoDistintos() {
        val colores = TipoAviso.entries.filter { it != TipoAviso.CARGA }.map { ReglasNotificacion.color(it) }
        assertEquals(4, colores.toSet().size)
        assertEquals(ReglasNotificacion.COLOR_MARCA, ReglasNotificacion.color(TipoAviso.CARGA))
    }

    @Test fun elAcentoDeLaNotificacionSigueElTemaActivo() {
        assertEquals(ColorTokens.L_SUCCESS.toInt(), ReglasNotificacion.colorParaTema(TipoAviso.EXITO, oscuro = false))
        assertEquals(ColorTokens.D_SUCCESS.toInt(), ReglasNotificacion.colorParaTema(TipoAviso.EXITO, oscuro = true))
        assertEquals(ColorTokens.L_ALERT_STOCK_BAJO.toInt(), ReglasNotificacion.colorParaTema(TipoAviso.ADVERTENCIA, oscuro = false))
        assertEquals(ColorTokens.D_ALERT_STOCK_BAJO.toInt(), ReglasNotificacion.colorParaTema(TipoAviso.ADVERTENCIA, oscuro = true))
        assertEquals(ColorTokens.L_PRIMARY.toInt(), ReglasNotificacion.colorParaTema(TipoAviso.INFO, oscuro = false))
        assertEquals(ColorTokens.D_PRIMARY.toInt(), ReglasNotificacion.colorParaTema(TipoAviso.INFO, oscuro = true))
    }

    // ---------------------------------------------------------------- ráfagas y textos de la sincronización

    @Test fun lasRafagasCuentanDentroDeLaVentana() {
        val c = ContadorRafagas(ventanaMs = 1_000)
        assertEquals(1, c.contar("a", 0))
        assertEquals(2, c.contar("a", 900))
        assertEquals(3, c.contar("a", 1_800)) // la ventana se cuenta desde el último
        assertEquals(1, c.contar("b", 1_900))
        assertEquals(1, c.contar("a", 5_000))
        c.olvidar("a")
        assertEquals(1, c.contar("a", 5_100))
    }

    @Test fun textosDeLaSincronizacion() {
        assertEquals("La app de Luis se conectó", NotificacionesSync.textoConexion("Luis", conectado = true, repeticiones = 1))
        assertEquals("La app de Luis se desconectó (×3)", NotificacionesSync.textoConexion("Luis", conectado = false, repeticiones = 3))
        assertEquals("La app de un empleado se conectó", NotificacionesSync.textoConexion(null, true, 1))
        assertEquals("Con Ana", NotificacionesSync.textoSincronizada("Ana", 1))
        assertEquals("Datos actualizados (×2)", NotificacionesSync.textoSincronizada(null, 2))
        assertEquals("Recibiendo el catálogo…", NotificacionesSync.textoFase(ProgresoSync(0, 0, FaseSync.RECIBIENDO)))
        assertEquals("Enviando tus datos…", NotificacionesSync.textoFase(ProgresoSync(0, 0, FaseSync.ENVIANDO)))
        assertEquals("50 %", NotificacionesSync.textoFase(ProgresoSync(5, 10)))
        assertEquals("1 aviso de las apps vinculadas", ReglasNotificacion.resumen(1))
        assertEquals("3 avisos de las apps vinculadas", ReglasNotificacion.resumen(3))
    }

    @Test fun cadaAppVinculadaTieneSuNotificacionSinChocarConLasFijas() {
        assertEquals(3_105, AvisoSync.idConexion(5))
        assertEquals(3_105, AvisoSync.idConexion(1_005))
        assertTrue(AvisoSync.idConexion(-1) in 3_100..4_099)
        val fijas = setOf(AvisoTurno.ID, AvisoSync.ID_PROGRESO, AvisoSync.ID_RESUMEN) + Operacion.entries.map { it.idNotificacion }
        assertEquals("los ID fijos no se repiten", 5, fijas.size)
        (0L..999L).forEach { assertFalse(AvisoSync.idConexion(it) in fijas) }
    }
}
