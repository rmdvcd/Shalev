package cu.shalev.data.sync

import cu.shalev.core.result.AppError
import cu.shalev.domain.model.CodigoVinculacion
import cu.shalev.domain.model.LicenciaPrincipal
import cu.shalev.domain.model.PermisoEmpleado
import cu.shalev.licencia.LicenseState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** P37: protocolo Principal ⇄ Secundaria sin red (flujos en memoria). */
class SyncProtocoloTest {
    private val vacio = ByteArrayInputStream(ByteArray(0))

    private fun codigo() = CodigoVinculacion(
        negocioId = "a1b2c3d4e5f6a1b2c3d4e5f6", nombreNegocio = "María Pérez", direcciones = listOf("192.168.43.1", "10.0.0.5"),
        puerto = 47_811, empleadoId = 3, nombreEmpleado = "Luis", token = CriptoSync.aleatorio(CodigoQr.TOKEN_BYTES),
        venceEn = Instant.parse("2026-10-02T15:10:00Z"),
    )

    @Test fun qrIdaYVuelta() {
        val c = codigo()
        val texto = CodigoQr.codificar(c)
        assertTrue(texto.startsWith(CodigoQr.PREFIJO))
        assertNotNull(CodigoQr.decodificar(texto))
        val d = CodigoQr.decodificar(texto)!!
        assertEquals(c.negocioId, d.negocioId)
        assertEquals(c.direcciones, d.direcciones)
        assertEquals(c.puerto, d.puerto)
        assertEquals(c.empleadoId, d.empleadoId)
        assertEquals(c.venceEn, d.venceEn)
        assertArrayEquals(c.token, d.token)
    }

    @Test fun qrAjenoODanadoEsNull() {
        assertNull(CodigoQr.decodificar("TRANSFERMOVIL_ETECSA,TRANSFERENCIA,9200000000000000,55555555,"))
        assertNull(CodigoQr.decodificar(CodigoQr.PREFIJO + "no-es-base64-%%%"))
        assertNull(CodigoQr.decodificar(CodigoQr.PREFIJO + "e30")) // {}
    }

    @Test fun lasDosAppsLleganALaMismaClaveSinQueViaje() {
        val token = CriptoSync.aleatorio(CodigoQr.TOKEN_BYTES)
        val sec = CriptoSync.parNuevo()
        val pri = CriptoSync.parNuevo()
        val enSecundaria = CriptoSync.claveEmpleado(sec, pri.public.encoded, token)
        val enPrincipal = CriptoSync.claveEmpleado(pri, sec.public.encoded, token)
        assertArrayEquals(enSecundaria, enPrincipal)
        // Con otro token (QR viejo o adivinado) la clave es otra.
        assertFalse(enPrincipal.contentEquals(CriptoSync.claveEmpleado(pri, sec.public.encoded, CriptoSync.aleatorio(16))))
        // El MAC del vínculo solo lo puede calcular quien leyó el QR.
        val mac = CriptoSync.macVinculoSecundaria(token, sec.public.encoded)
        assertTrue(CriptoSync.iguales(mac, CriptoSync.macVinculoSecundaria(token, sec.public.encoded)))
        assertFalse(CriptoSync.iguales(mac, CriptoSync.macVinculoSecundaria(CriptoSync.aleatorio(16), sec.public.encoded)))
    }

    private fun claves() = CriptoSync.clavesSesion(CriptoSync.aleatorio(32), CriptoSync.aleatorio(16), CriptoSync.aleatorio(16))

    @Test fun canalCifradoEntregaLosMensajesEnOrden() {
        val claves = claves()
        val cable = ByteArrayOutputStream()
        val sec = CanalCifrado.paraSecundaria(vacio, cable, claves.copiar())
        val lote = Lote(turnos = emptyList(), ventas = emptyList())
        sec.enviar(Sincronizar(1, lote, "h"))
        sec.enviar(Ping(2))
        val pri = CanalCifrado.paraPrincipal(ByteArrayInputStream(cable.toByteArray()), ByteArrayOutputStream(), claves.copiar())
        assertEquals(Sincronizar(1, lote, "h"), pri.recibir())
        assertEquals(Ping(2), pri.recibir())
        // El texto no viaja en claro.
        assertFalse(String(cable.toByteArray(), Charsets.ISO_8859_1).contains("sincronizar"))
    }

    @Test fun mensajeAlteradoRepetidoOConOtraClaveNoSeAcepta() {
        val claves = claves()
        val cable = ByteArrayOutputStream()
        CanalCifrado.paraSecundaria(vacio, cable, claves.copiar()).enviar(Ping(1))
        val trama = cable.toByteArray()

        val alterada = trama.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        falla { CanalCifrado.paraPrincipal(ByteArrayInputStream(alterada), ByteArrayOutputStream(), claves.copiar()).recibir() }

        // Repetida: la segunda copia lleva un número de secuencia que ya pasó.
        val pri = CanalCifrado.paraPrincipal(ByteArrayInputStream(trama + trama), ByteArrayOutputStream(), claves.copiar())
        assertEquals(Ping(1), pri.recibir())
        falla { pri.recibir() }

        falla { CanalCifrado.paraPrincipal(ByteArrayInputStream(trama), ByteArrayOutputStream(), claves()).recibir() }

        // Un mensaje de la principal no se puede devolver como si fuera de la secundaria (clave y etiqueta por sentido).
        val deVuelta = ByteArrayOutputStream()
        CanalCifrado.paraPrincipal(vacio, deVuelta, claves.copiar()).enviar(Ping(1))
        falla { CanalCifrado.paraPrincipal(ByteArrayInputStream(deVuelta.toByteArray()), ByteArrayOutputStream(), claves.copiar()).recibir() }
    }

    @Test fun saludoEnClaroIdaYVuelta() {
        val out = ByteArrayOutputStream()
        Saludos.enviar(out, Rechazo(Rechazo.CODIGO_VENCIDO))
        assertEquals(Rechazo(Rechazo.CODIGO_VENCIDO), Saludos.recibir(ByteArrayInputStream(out.toByteArray())))
    }

    @Test fun licenciaDeLaPrincipalViajaSinIdsYSeReconstruye() {
        val ahora = Instant.parse("2026-10-02T12:00:00Z")
        val prueba = LicenciaPrincipal.de(LicenseState.Trial(3), ahora)
        assertEquals(LicenseState.Trial(3), prueba.aDto().aDominio()!!.estado(ahora))
        assertFalse(LicenciaPrincipal.de(LicenseState.TrialExpired, ahora).aDto().aDominio()!!.estado(ahora).unlocked)
        assertTrue(LicenciaPrincipal.de(LicenseState.Perpetual("x"), ahora).aDto().aDominio()!!.estado(ahora).unlocked)
        // Prueba vencida en la secundaria aunque no haya sincronizado desde entonces.
        assertFalse(prueba.estado(ahora.plusSeconds(4L * 24 * 3600)).unlocked)
    }

    @Test fun erroresRemotosIdaYVuelta() {
        listOf(
            AppError.SinPermiso, AppError.NoEncontrado, AppError.Duplicado("codigo"),
            AppError.Validacion("nombre", AppError.Regla.REQUERIDO),
        ).forEach { assertEquals(it, EjecutorComandos.error(EjecutorComandos.codigo(it))) }
        assertTrue(EjecutorComandos.error(EjecutorComandos.codigo(AppError.StockInsuficiente(listOf("x")))) is AppError.StockInsuficiente)
    }

    @Test fun cadaAccionPideSuPermiso() {
        assertEquals(PermisoEmpleado.CAMBIAR_PRECIOS, EjecutorComandos.permisoDe(CambiarPrecios(mapOf(1L to 100L))))
        assertEquals(PermisoEmpleado.CAMBIAR_PRECIOS, EjecutorComandos.permisoDe(EliminarPreajuste(1)))
        assertEquals(PermisoEmpleado.EDITAR_INVENTARIO, EjecutorComandos.permisoDe(EliminarProducto(1)))
        assertEquals(PermisoEmpleado.EDITAR_INVENTARIO, EjecutorComandos.permisoDe(AjustarStockInsumo(1, 1000)))
    }

    @Test fun datosSecundariaNoMuestranLaClave() {
        val d = DatosSecundaria("n", "Negocio", 1, "Luis", clave = "SECRETO", direcciones = listOf("1.2.3.4"), puerto = 1)
        assertFalse(d.toString().contains("SECRETO"))
    }

    private fun Pair<ByteArray, ByteArray>.copiar() = first.copyOf() to second.copyOf()

    private fun falla(bloque: () -> Unit) {
        try { bloque(); fail("debía fallar") } catch (e: ProtocoloException) { /* esperado */ }
    }
}
