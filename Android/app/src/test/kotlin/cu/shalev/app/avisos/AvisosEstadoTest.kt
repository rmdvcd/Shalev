package cu.shalev.app.avisos

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import cu.shalev.app.actualizacion.TextosActualizacion
import cu.shalev.app.inventario.TextosInventario
import cu.shalev.app.pagos.TextosPago
import cu.shalev.app.perfil.TextosIdLicencia
import cu.shalev.app.perfil.TextosPerfil
import cu.shalev.app.registros.TextosAnulacion
import cu.shalev.app.registros.TextosRegistros
import cu.shalev.app.registros.TextosTurno
import cu.shalev.app.servicios.TextosServicios
import cu.shalev.app.venta.TextosVenta
import cu.shalev.designsystem.component.AtencionAviso
import cu.shalev.designsystem.component.ShalevAvisosState
import cu.shalev.designsystem.component.TipoAviso
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Notificacion.md aplicado a los avisos de la app: la pila de la isla, sin pantalla. */
class AvisosEstadoTest {

    @Test fun urgenciaEsPermanencia() {
        val s = ShalevAvisosState()
        assertEquals(ShalevAvisosState.TIEMPO_MS, s.mostrar("Producto eliminado.", TipoAviso.EXITO).tiempoBaseMs)
        assertEquals(ShalevAvisosState.TIEMPO_MS, s.mostrar("Tienes la versión más reciente.", TipoAviso.INFO).tiempoBaseMs)
        assertNull(s.mostrar("Revisa los datos", TipoAviso.ADVERTENCIA).tiempoBaseMs)
        assertNull(s.mostrar("No se pudo guardar.", TipoAviso.ERROR).tiempoBaseMs)
        assertNull(s.mostrar("Guardando", TipoAviso.CARGA).tiempoBaseMs)
        // Con acción hay que leer y decidir: más tiempo.
        assertEquals(
            ShalevAvisosState.TIEMPO_CON_ACCION_MS,
            s.mostrar("Turno cerrado", TipoAviso.EXITO, accion = "Ver").tiempoBaseMs,
        )
        assertNull(s.mostrar("Fijo", TipoAviso.INFO, duracion = SnackbarDuration.Indefinite).tiempoBaseMs)
    }

    @Test fun lasRafagasSeAgrupanConContador() {
        val s = ShalevAvisosState()
        val a = s.mostrar("Archivo guardado.")
        val version = a.version
        repeat(4) { assertSame(a, s.mostrar("Archivo guardado.")) }
        assertEquals(1, s.total)
        assertEquals(5, a.repeticiones)
        assertTrue("cada repetición reinicia su tiempo", a.version > version)
        assertTrue(a.descripcionAccesible.contains("5 veces"))
        // Otro texto sí apila.
        s.mostrar("Producto eliminado.")
        assertEquals(2, s.total)
    }

    @Test fun loMasNuevoArribaYPaginador() {
        val s = ShalevAvisosState()
        s.mostrar("uno", TipoAviso.INFO)
        s.mostrar("dos", TipoAviso.INFO)
        s.mostrar("tres", TipoAviso.INFO)
        assertEquals("tres", s.actual?.titulo)
        assertEquals(3, s.contador)
        s.siguiente(); s.siguiente(); s.siguiente()
        assertEquals("uno", s.actual?.titulo)
        assertEquals(2, s.indice)
        s.anterior()
        assertEquals("dos", s.actual?.titulo)
        s.cerrar(s.actual!!)
        assertEquals(listOf("tres", "uno"), s.avisos.map { it.titulo })
        // Un aviso nuevo vuelve a la primera página.
        s.mostrar("cuatro", TipoAviso.INFO)
        assertEquals(0, s.indice)
        assertEquals("cuatro", s.actual?.titulo)
    }

    @Test fun alPasarDelTopeLaInsigniaCuentaLosPerdidos() {
        val s = ShalevAvisosState()
        repeat(ShalevAvisosState.MAXIMO + 5) { s.mostrar("aviso $it", TipoAviso.ERROR) }
        assertEquals(ShalevAvisosState.MAXIMO, s.total)
        assertEquals(5, s.perdidos)
        assertEquals(ShalevAvisosState.MAXIMO + 5, s.contador)
        assertEquals("aviso 104", s.avisos.first().titulo)
        assertEquals("se descartan los más antiguos", "aviso 5", s.avisos.last().titulo)
    }

    @Test fun limpiarEnDosPasos() {
        val s = ShalevAvisosState()
        s.mostrar("uno", TipoAviso.ERROR)
        s.mostrar("dos", TipoAviso.ERROR)
        s.abrir()
        assertFalse(s.pulsarLimpiar())
        assertTrue(s.limpiarArmado)
        assertEquals("la primera pulsación solo arma", 2, s.total)
        // Cualquier otra acción lo desarma.
        s.siguiente()
        assertFalse(s.limpiarArmado)
        assertFalse(s.pulsarLimpiar())
        assertTrue(s.pulsarLimpiar())
        assertEquals(0, s.total)
        assertFalse(s.abierta)
    }

    @Test fun atencionDecideSiSeAbreLaTarjeta() {
        val s = ShalevAvisosState()
        s.mostrar("Archivo guardado.")
        assertFalse("sin nada que hacer ni leer, píldora", s.abierta)
        s.mostrar("Turno cerrado: 3 ventas", TipoAviso.EXITO, accion = "Ver")
        assertTrue("con acción, se abre", s.abierta)
        s.contraer()
        s.mostrar("Nota", TipoAviso.INFO, descripcion = "Detalle")
        assertTrue("con descripción, se abre", s.abierta)
        s.contraer()
        s.mostrar("Silencio", TipoAviso.INFO, accion = "Ver", atencion = AtencionAviso.APILAR)
        assertFalse(s.abierta)
        s.mostrar("Importante", TipoAviso.INFO, atencion = AtencionAviso.ABRIR)
        assertTrue(s.abierta)
    }

    @Test fun showSnackbarSinAccionNoEsperaAQueSeCierre() = runBlocking {
        val s = ShalevAvisosState()
        // Un error se queda en pantalla, pero no bloquea los avisos siguientes.
        val r = withTimeout(1_000) { s.showSnackbar("No se pudo guardar. Inténtalo de nuevo.") }
        assertEquals(SnackbarResult.Dismissed, r)
        assertEquals(TipoAviso.ERROR, s.actual?.tipo)
        withTimeout(1_000) { s.showSnackbar("Producto eliminado.") }
        assertEquals(2, s.total)
    }

    @Test fun showSnackbarConAccionDevuelveLoQueEligio() = runBlocking {
        val s = ShalevAvisosState()
        val ver = async { s.showSnackbar("Turno cerrado", actionLabel = "Ver", tipo = TipoAviso.EXITO) }
        yield()
        s.accionar(s.actual!!)
        assertEquals(SnackbarResult.ActionPerformed, ver.await())
        assertEquals(0, s.total)

        val cerrar = async { s.showSnackbar("Turno cerrado", actionLabel = "Ver", tipo = TipoAviso.EXITO) }
        yield()
        s.cerrar(s.actual!!)
        assertEquals(SnackbarResult.Dismissed, cerrar.await())
    }

    @Test fun unaPromesaEsUnSoloAvisoQueCambia() {
        val s = ShalevAvisosState()
        var intentos = 0
        val seg = s.seguir("Guardando…")
        val id = seg.aviso.id
        assertEquals(TipoAviso.CARGA, seg.aviso.tipo)
        assertTrue(seg.aviso.persistente)
        assertFalse("la carga se acumula en silencio", s.abierta)

        seg.error("No se pudo guardar", reintentar = { intentos++ })
        assertEquals(1, s.total)
        assertEquals(id, s.actual?.id)
        assertEquals(TipoAviso.ERROR, s.actual?.tipo)
        assertEquals(ShalevAvisosState.REINTENTAR, s.actual?.accion)
        assertTrue("un error con Reintentar abre la tarjeta", s.abierta)

        s.accionar(s.actual!!)
        assertEquals(1, intentos)
        assertEquals("Reintentar no cierra: vuelve a cargando", 1, s.total)
        assertEquals(TipoAviso.CARGA, s.actual?.tipo)

        seg.exito("Guardado")
        assertEquals(id, s.actual?.id)
        assertEquals(TipoAviso.EXITO, s.actual?.tipo)
        assertEquals(ShalevAvisosState.TIEMPO_MS, s.actual?.tiempoBaseMs)
        seg.cancelar()
        assertEquals(0, s.total)
    }

    @Test fun elMensajeDeOperacionEnMarchaLoSustituyeElSiguiente() {
        val s = ShalevAvisosState()
        val a = s.mostrar(TextosActualizacion.GUARDANDO_RESPALDO)
        assertEquals(TipoAviso.INFO, a.tipo)
        assertTrue(a.provisional)
        val b = s.mostrar(TextosActualizacion.ERROR_RESPALDO_PREVIO)
        assertSame("el mismo aviso cambia, no se apila otro", a, b)
        assertEquals(1, s.total)
        assertEquals(TipoAviso.ERROR, b.tipo)
        assertFalse(b.provisional)
        // Un aviso normal no se sustituye.
        s.mostrar("Producto eliminado.")
        assertEquals(2, s.total)
    }

    @Test fun cerrarElUltimoReiniciaLaIsla() {
        val s = ShalevAvisosState()
        repeat(ShalevAvisosState.MAXIMO + 1) { s.mostrar("e$it", TipoAviso.ERROR) }
        s.abrir()
        s.limpiarTodo()
        assertEquals(0, s.perdidos)
        assertFalse(s.abierta)
        assertNull(s.actual)
    }

    /** Los textos reales de la app caen en el tipo correcto (icono, color, anuncio y permanencia). */
    @Test fun clasificaLosMensajesDeLaApp() {
        val esperado = mapOf(
            // Éxito
            TextosInventario.ELIMINADO to TipoAviso.EXITO,
            TextosInventario.GUARDADO_EN_DISPOSITIVO to TipoAviso.EXITO,
            TextosServicios.ELIMINADO to TipoAviso.EXITO,
            TextosPerfil.GUARDADO to TipoAviso.EXITO,
            TextosPago.GUARDADO to TipoAviso.EXITO,
            TextosTurno.TURNO_ABIERTO to TipoAviso.EXITO,
            TextosAnulacion.ANULADA to TipoAviso.EXITO,
            TextosIdLicencia.COPIADO to TipoAviso.EXITO,
            TextosActualizacion.RESPALDO_RESTAURADO to TipoAviso.EXITO,
            "Solicitud guardada. Se envía al encargado al sincronizar." to TipoAviso.EXITO,
            // Error
            TextosInventario.ERROR_EXPORTAR to TipoAviso.ERROR,
            TextosRegistros.ERROR_EXPORTAR to TipoAviso.ERROR,
            TextosServicios.ERROR_ELIMINAR to TipoAviso.ERROR,
            TextosVenta.ERROR to TipoAviso.ERROR,
            TextosTurno.ERROR_ABRIR to TipoAviso.ERROR,
            TextosActualizacion.ERROR_DESCARGA to TipoAviso.ERROR,
            TextosActualizacion.ERROR_RED to TipoAviso.ERROR,
            TextosActualizacion.INSTALACION_CANCELADA to TipoAviso.ERROR,
            TextosActualizacion.RESPALDO_NO_RESTAURADO to TipoAviso.ERROR,
            TextosRegistros.VENTA_NO_DISPONIBLE to TipoAviso.ERROR,
            // Advertencia (hay que corregir algo)
            TextosVenta.PORTAPAPELES_VACIO to TipoAviso.ADVERTENCIA,
            TextosVenta.SMS_SIN_NUMERO to TipoAviso.ADVERTENCIA,
            TextosVenta.CANTIDAD_AJUSTADA to TipoAviso.ADVERTENCIA,
            TextosServicios.NO_VENDIBLE to TipoAviso.ADVERTENCIA,
            TextosInventario.EN_USO to TipoAviso.ADVERTENCIA,
            TextosActualizacion.PERMISO to TipoAviso.ADVERTENCIA,
            TextosActualizacion.SIN_CONEXION_PRINCIPAL to TipoAviso.ADVERTENCIA,
            "Revisa los datos marcados" to TipoAviso.ADVERTENCIA,
            "Ya hay un turno abierto." to TipoAviso.ADVERTENCIA,
            "El importe supera el efectivo disponible en caja." to TipoAviso.ADVERTENCIA,
            "El encargado no aprobó cerrar el turno. Sigue abierto." to TipoAviso.ADVERTENCIA,
            "Turno cerrado: ábrelo para vender." to TipoAviso.ADVERTENCIA,
            // Información
            TextosActualizacion.SIN_NOVEDADES to TipoAviso.INFO,
            TextosActualizacion.SIN_REPO to TipoAviso.INFO,
            TextosActualizacion.GUARDANDO_RESPALDO to TipoAviso.INFO,
            "Pediste cerrar el turno. Puedes vender hasta que el encargado lo apruebe." to TipoAviso.INFO,
        )
        esperado.forEach { (texto, tipo) -> assertEquals(texto, tipo, TipoAviso.de(texto)) }
    }

    @Test fun elTipoSeLeeAntesDelMensaje() {
        val s = ShalevAvisosState()
        val a = s.mostrar("No se pudo guardar.", descripcion = "Libera espacio.")
        assertEquals("Error: No se pudo guardar. Libera espacio.", a.descripcionAccesible)
        assertTrue(TipoAviso.ERROR.urgente)
        assertTrue(TipoAviso.ADVERTENCIA.urgente)
        assertFalse(TipoAviso.EXITO.urgente)
        assertFalse(TipoAviso.CARGA.urgente)
    }
}
