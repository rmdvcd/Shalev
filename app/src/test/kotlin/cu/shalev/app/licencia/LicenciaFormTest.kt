package cu.shalev.app.licencia

import cu.shalev.core.result.AppError
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.model.Telefono
import cu.shalev.licencia.ActivationResult
import cu.shalev.licencia.InstalledLicense
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenciaFormTest {
    private val ahora = Instant.parse("2026-09-30T12:00:00Z")
    private val utc = ZoneOffset.UTC
    private val completo = Perfil(
        nombre = "María", apellidos = "Pérez González", ci = "85010112345",
        telefonos = listOf(Telefono(1, "52345678"), Telefono(2, "53334444")),
    )

    private fun lic(estado: LicenseState, instalada: InstalledLicense? = null, huella: String? = "ab".repeat(32), validacion: Boolean = true) =
        Licencia(estado, "SPVI:abc", huella != null, huella, instalada, validacion)

    private fun instalada(tipo: TipoLicencia, dias: Long?) =
        InstalledLicense("3f2a9c0d-1e4b-5a67-8899-aabbccddeeff", tipo, ahora.minus(Duration.ofDays(1)), dias?.let { ahora.plus(Duration.ofDays(it)) })

    // ---------- Formulario ↔ Perfil ----------

    @Test fun perfilVacioAbreFormularioEditable() {
        val f = LicenciaForm().desdePerfil(Perfil())
        assertTrue(f.editando)
        assertEquals("", f.nombre)
    }

    @Test fun perfilCompletoMuestraResumenConPrimerTelefono() {
        val f = LicenciaForm().desdePerfil(completo)
        assertFalse(f.editando)
        assertEquals("52345678", f.telefono)
        assertTrue(f.valido)
    }

    @Test fun perfilIncompletoObligaAEditar() {
        val f = LicenciaForm().desdePerfil(completo.copy(ci = "1"))
        assertTrue(f.editando)
    }

    @Test fun sincronizarConservaTipoViaYMensaje() {
        val f = LicenciaForm(tipo = TipoLicencia.ANUAL, mensajeLicencia = "x").desdePerfil(completo)
        assertEquals(TipoLicencia.ANUAL, f.tipo)
        assertEquals("x", f.mensajeLicencia)
    }

    @Test fun chipDeTelefonoSeleccionaAunqueCambieElFormato() {
        val f = LicenciaForm().conTelefono("5234 5678")
        assertTrue(f.telefonoSeleccionado("52345678"))
        assertFalse(f.telefonoSeleccionado("53334444"))
    }

    @Test fun cerrarEdicionSoloConDatosValidos() {
        val invalido = LicenciaForm(nombre = "M").cerrarEdicion()
        assertTrue(invalido.editando); assertTrue(invalido.mostrarErrores)
        val valido = LicenciaForm().desdePerfil(completo).copy(editando = true).cerrarEdicion()
        assertFalse(valido.editando)
    }

    @Test fun inputRecortaEspacios() {
        val i = LicenciaForm(nombre = " María ", apellidos = " Pérez ", ci = " 850 ", telefono = " 52345678 ").aInput()
        assertEquals("María", i.nombre); assertEquals("Pérez", i.apellidos); assertEquals("850", i.ci); assertEquals("52345678", i.telefono)
    }

    // ---------- Acción y tipo sugerido ----------

    @Test fun sinLicenciaSeSolicitaMensual() {
        val l = lic(LicenseState.Trial(3))
        assertEquals(AccionSolicitud.SOLICITAR, l.accionSolicitud())
        assertEquals(TipoLicencia.MENSUAL, l.tipoSugerido())
    }

    @Test fun conLicenciaSeRenuevaPreseleccionandoElTipoActual() {
        val l = lic(LicenseState.Expired(TipoLicencia.SEMESTRAL), instalada(TipoLicencia.SEMESTRAL, -1))
        assertEquals(AccionSolicitud.ACTUALIZAR, l.accionSolicitud())
        assertEquals(TipoLicencia.SEMESTRAL, l.tipoSugerido())
    }

    /** 0.25.1 (C): la recuperación se ofrece sin licencia o con la instalada vencida; con una vigente, no. */
    @Test fun recuperarConLicenciaVencidaSiPeroVigenteNo() {
        assertTrue(lic(LicenseState.Trial(3)).permiteRecuperar())
        assertTrue(lic(LicenseState.Expired(TipoLicencia.MENSUAL), instalada(TipoLicencia.MENSUAL, -2)).permiteRecuperar())
        assertFalse(lic(LicenseState.Perpetual("id"), instalada(TipoLicencia.PERPETUA, null)).permiteRecuperar())
        val vigente = lic(LicenseState.Active(TipoLicencia.MENSUAL, ahora.plus(Duration.ofDays(20)), "3f2a9c0d-1e4b-5a67-8899-aabbccddeeff"), instalada(TipoLicencia.MENSUAL, 20))
        assertFalse(vigente.permiteRecuperar())
    }

    @Test fun perpetuaPermiteAmpliarSecundariasHastaElMaximo() {
        val conMargen = lic(LicenseState.Perpetual("id", 2), instalada(TipoLicencia.PERPETUA, null).copy(secundarias = 2))
        assertTrue(conMargen.ampliable())
        assertEquals(AccionSolicitud.AMPLIAR, conMargen.accionSolicitud())

        val alMaximo = lic(LicenseState.Perpetual("id", 10), instalada(TipoLicencia.PERPETUA, null).copy(secundarias = 10))
        assertFalse(alMaximo.ampliable())
        assertEquals(AccionSolicitud.NINGUNA, alMaximo.accionSolicitud())
    }

    @Test fun modoAmpliacionFijaTipoCalculaImporteYGeneraAmplia() {
        val inst = InstalledLicense("lic-activa-1", TipoLicencia.SEMESTRAL, ahora.minus(Duration.ofDays(5)), ahora.plus(Duration.ofDays(120)), 2)
        val activa = lic(LicenseState.Active(TipoLicencia.SEMESTRAL, inst.venceEn!!, inst.id, 2), inst)
        val form = LicenciaForm().desdePerfil(completo).conModoAmpliacion(true, inst).conSecundarias(5, inst.secundarias + 1)
        assertTrue(activa.ampliable())
        assertEquals(AccionSolicitud.AMPLIAR, activa.accionSolicitud(form.ampliandoSecundarias))
        assertEquals(15_000L, form.precioAmpliacion(inst.secundarias))
        val inp = form.aInput(activa)
        assertEquals("lic-activa-1", inp.amplia)
        assertEquals(2, inp.secundariasPrevias)
        assertEquals(5, inp.secundarias)
        assertEquals(TipoLicencia.SEMESTRAL, inp.tipo)
    }

    // ---------- Avisos ----------

    @Test fun buildSinClaveOSinValidacionSonAvisosDistintos() {
        assertEquals(listOf(AvisoLicencia.SIN_SOLICITUD), lic(LicenseState.Trial(7), huella = null).avisos())
        assertEquals(listOf(AvisoLicencia.SIN_VALIDACION), lic(LicenseState.Trial(7), validacion = false).avisos())
        // Ninguno lo resuelve el usuario: ambos remiten al desarrollador (la clave del emisor va fijada en el build).
        AvisoLicencia.entries.forEach { assertTrue(it.detalle.contains("desarrollador")) }
        assertEquals(emptyList<AvisoLicencia>(), lic(LicenseState.Trial(7)).avisos())
    }

    // ---------- Detalle ----------

    @Test fun detalleDeLicenciaActiva() {
        val inst = instalada(TipoLicencia.MENSUAL, 10)
        val filas = lic(LicenseState.Active(TipoLicencia.MENSUAL, inst.venceEn!!, inst.id), inst).detalle(ahora, utc).toMap()
        assertEquals("10 días restantes", filas["Tiempo restante"])
        assertTrue("Emitida" in filas)
        // P28: sin IDs internos ni filas que repiten el encabezado (tipo y vencimiento).
        assertEquals(setOf("Emitida", "Tiempo restante"), filas.keys)
    }

    @Test fun detalleDePruebaNoRepiteLosDias() {
        assertEquals(emptyList<Pair<String, String>>(), lic(LicenseState.Trial(5)).detalle(ahora, utc))
    }

    @Test fun detalleDePruebaYPerpetua() {
        assertEquals("1 día", lic(LicenseState.Trial(1)).tiempoRestante(ahora, utc))
        assertEquals("Ilimitado", lic(LicenseState.Perpetual("x")).tiempoRestante(ahora, utc))
        assertEquals("Vencida", lic(LicenseState.TrialExpired).tiempoRestante(ahora, utc))
        val perp = lic(LicenseState.Perpetual("x"), instalada(TipoLicencia.PERPETUA, null)).detalle(ahora, utc).toMap()
        assertEquals(setOf("Emitida"), perp.keys)
    }

    // ---------- Mensajes genéricos ----------

    @Test fun mensajesNoRevelanDetallesCriptograficos() {
        val textos = listOf(
            mensajeActivacion(ActivationResult.Rejected),
            mensajeErrorSolicitud(AppError.Cripto),
        ).joinToString(" ").lowercase()
        listOf("firma", "gcm", "tag", "aad", "ecdh", "hkdf").forEach { assertFalse(it, textos.contains(it)) }
    }


    // ---------- 0.22.0 ----------

    @Test fun soloSeOfreceRenovarIgualConUnaLicenciaConVencimiento() {
        val mensual = instalada(TipoLicencia.MENSUAL, 30)
        assertTrue(lic(LicenseState.Active(TipoLicencia.MENSUAL, mensual.venceEn!!, mensual.id), mensual).renovable())
        assertTrue(lic(LicenseState.Expired(TipoLicencia.MENSUAL), mensual).renovable())
        val perpetua = instalada(TipoLicencia.PERPETUA, null)
        assertFalse(lic(LicenseState.Perpetual(perpetua.id), perpetua).renovable())
        assertFalse(lic(LicenseState.Trial(3)).renovable())
        assertFalse(lic(LicenseState.TrialExpired).renovable())
    }

    @Test fun renovarIgualCopiaTipoYSecundariasYDiceElPrecio() {
        val i = InstalledLicense("x", TipoLicencia.ANUAL, Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(365)), 3)
        val f = LicenciaForm(tipo = TipoLicencia.MENSUAL, secundarias = 0).igualQue(i)
        assertEquals(TipoLicencia.ANUAL, f.tipo)
        assertEquals(3, f.secundarias)
        assertEquals(cu.shalev.core.money.Money.cup(50_000L + 3 * 9_000L), cu.shalev.core.money.Money.cup(f.precio))
        assertTrue(TextosRenovacion.detalle(i).startsWith("Anual · 3 apps secundarias · "))
        assertTrue(TextosRenovacion.detalle(i).endsWith(cu.shalev.core.money.Money.cup(77_000L)))
    }
}
