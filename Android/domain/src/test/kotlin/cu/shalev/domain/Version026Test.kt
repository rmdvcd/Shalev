package cu.shalev.domain

import cu.shalev.domain.model.ActualizacionObligatoria
import cu.shalev.domain.model.ActualizacionObligatoria.Estado
import cu.shalev.domain.model.ApkEnPrincipal
import cu.shalev.domain.model.EstadoApp
import cu.shalev.domain.model.InfoActualizacion
import cu.shalev.domain.repository.EstadoAppRepository
import cu.shalev.domain.usecase.EstadoActualizacionObligatoria
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.26.0 (P73 §6): actualizaciones obligatorias, aplazables 30 días. */
class Version026Test {
    private fun dias(n: Long) = T0.plus(Duration.ofDays(n))
    private val pendiente = EstadoApp(obligatoriaDesde = T0, obligatoriaPara = "0.26.0")

    @Test fun elDia29AvisaYElDia30Bloquea() {
        val limite = dias(30)
        assertEquals(Estado.Aviso(limite, oculto = false), ActualizacionObligatoria.estado(dias(29), pendiente, "0.26.0", hayNueva = true))
        assertEquals(Estado.Aviso(limite, false), ActualizacionObligatoria.estado(dias(30).minusSeconds(1), pendiente, "0.26.0", true))
        assertEquals(Estado.Bloqueo(limite), ActualizacionObligatoria.estado(dias(30), pendiente, "0.26.0", true))
        assertEquals(Estado.Bloqueo(limite), ActualizacionObligatoria.estado(dias(90), pendiente, "0.26.0", true))
    }

    @Test fun sinVersionNuevaNoHayBloqueo() {
        assertEquals(Estado.Ninguna, ActualizacionObligatoria.estado(dias(90), pendiente, "0.26.0", hayNueva = false))
        assertEquals(Estado.Ninguna, ActualizacionObligatoria.estado(dias(90), EstadoApp(), "0.26.0", hayNueva = true))
        // Ya actualizada (otra versión instalada): el plazo viejo no cuenta.
        assertEquals(Estado.Ninguna, ActualizacionObligatoria.estado(dias(90), pendiente, "0.26.1", hayNueva = true))
    }

    @Test fun unaVersionAunMasNuevaNoReiniciaElPlazo() {
        val e = ActualizacionObligatoria.registrar(dias(20), pendiente, "0.26.0", hayNueva = true)
        assertEquals(T0, e.obligatoriaDesde)
        assertFalse(ActualizacionObligatoria.necesitaRegistro(pendiente, "0.26.0", true))
    }

    @Test fun primeraDeteccionYBorradoAlActualizar() {
        val e = ActualizacionObligatoria.registrar(T0, EstadoApp(), "0.26.0", hayNueva = true)
        assertEquals(T0, e.obligatoriaDesde); assertEquals("0.26.0", e.obligatoriaPara)
        // Actualizó a 0.26.1 y luego sale 0.26.2: el plazo empieza de nuevo para la versión instalada.
        val tras = ActualizacionObligatoria.registrar(dias(40), e, "0.26.1", hayNueva = true)
        assertEquals(dias(40), tras.obligatoriaDesde); assertEquals("0.26.1", tras.obligatoriaPara)
        val limpio = ActualizacionObligatoria.registrar(dias(41), tras.copy(aplazadaHasta = dias(42)), "0.26.2", hayNueva = false)
        assertNull(limpio.obligatoriaDesde); assertNull(limpio.obligatoriaPara); assertNull(limpio.aplazadaHasta)
    }

    @Test fun masTardeOcultaHastaMananaSinMoverElPlazo() = runBlocking {
        val repo = Mem(pendiente)
        val clock = FixedClock(dias(10))
        val uc = EstadoActualizacionObligatoria(repo, clock)
        uc.aplazar()
        assertEquals(dias(11), repo.flujo.value.aplazadaHasta)
        assertEquals(T0, repo.flujo.value.obligatoriaDesde)
        assertEquals(Estado.Aviso(dias(30), oculto = true), uc.estado(repo.flujo.value, "0.26.0", true))
        clock.now = dias(11)
        assertEquals(Estado.Aviso(dias(30), oculto = false), uc.estado(repo.flujo.value, "0.26.0", true))
    }

    @Test fun registrarSoloEscribeSiCambia() = runBlocking {
        val repo = Mem(EstadoApp())
        val uc = EstadoActualizacionObligatoria(repo, FixedClock(dias(3)))
        uc.registrar("0.26.0", hayNueva = false)
        assertEquals(0, repo.escrituras)
        uc.registrar("0.26.0", hayNueva = true)
        assertEquals(dias(3), repo.flujo.value.obligatoriaDesde)
        uc.registrar("0.26.0", hayNueva = true)
        assertEquals(1, repo.escrituras)
    }

    @Test fun queCuentaComoVersionNueva() {
        val uc = EstadoActualizacionObligatoria(Mem(EstadoApp()), FixedClock())
        val conApk = InfoActualizacion("0.26.1", "p", apkUrl = "https://x/SPVI-0.26.1.apk", sha256 = "ab".repeat(32))
        val sinApk = InfoActualizacion("0.26.1", "p")
        // Principal: solo una Release instalable (APK + huella) y con GitHub configurado.
        assertTrue(uc.hayNueva(EstadoApp(disponible = conApk), "0.26.0", false, true, null, 48))
        assertFalse(uc.hayNueva(EstadoApp(disponible = sinApk), "0.26.0", false, true, null, 48))
        assertFalse(uc.hayNueva(EstadoApp(disponible = conApk), "0.26.0", false, repoConfigurado = false, apkPrincipal = null, versionCodeInstalado = 48))
        assertFalse(uc.hayNueva(EstadoApp(disponible = conApk), "0.26.1", false, true, null, 49))
        // Secundaria: APK más nuevo en la principal; sin sincronizar aún, se mantiene lo ya visto con esta versión.
        assertTrue(uc.hayNueva(EstadoApp(), "0.26.0", true, false, ApkEnPrincipal("0.26.1", 49, 1), 48))
        assertFalse(uc.hayNueva(EstadoApp(), "0.26.0", true, false, ApkEnPrincipal("0.26.0", 48, 1), 48))
        assertTrue(uc.hayNueva(pendiente, "0.26.0", true, false, null, 48))
        assertFalse(uc.hayNueva(EstadoApp(), "0.26.0", true, false, null, 48))
    }

    private class Mem(inicial: EstadoApp) : EstadoAppRepository {
        val flujo = MutableStateFlow(inicial)
        var escrituras = 0
        override val estado: Flow<EstadoApp> = flujo
        override suspend fun actual() = flujo.value
        override suspend fun editar(cambio: (EstadoApp) -> EstadoApp) { escrituras++; flujo.value = cambio(flujo.value) }
        override suspend fun borrar() { flujo.value = EstadoApp() }
    }
}
