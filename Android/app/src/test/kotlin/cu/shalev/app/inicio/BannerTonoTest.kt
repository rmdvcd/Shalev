package cu.shalev.app.inicio

import cu.shalev.designsystem.component.BannerTone
import cu.shalev.domain.model.Licencia
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** P18 (A10): el banner de licencia cambia de tono al acercarse el vencimiento. */
class BannerTonoTest {
    private val ahora = Instant.parse("2026-09-30T16:00:00Z")
    private fun lic(e: LicenseState) = Licencia(e, "SPVI:x", true, "ab")
    private fun activa(horas: Long) = lic(LicenseState.Active(TipoLicencia.MENSUAL, ahora.plusSeconds(3_600L * horas), "L1"))

    @Test fun activaSegunLosDiasQueQuedan() {
        assertEquals(BannerTone.Info, tonoBanner(activa(24 * 20), ahora))
        assertEquals(BannerTone.Info, tonoBanner(activa(24 * 7 + 1), ahora)) // 8 días redondeando hacia arriba
        assertEquals(BannerTone.Aviso, tonoBanner(activa(24 * 7), ahora))
        assertEquals(BannerTone.Aviso, tonoBanner(activa(25), ahora))
        assertEquals(BannerTone.Critico, tonoBanner(activa(24), ahora))
        assertEquals(BannerTone.Critico, tonoBanner(activa(1), ahora))
    }

    @Test fun pruebaSiempreAvisaYElUltimoDiaEsCritico() {
        assertEquals(BannerTone.Aviso, tonoBanner(lic(LicenseState.Trial(7)), ahora))
        assertEquals(BannerTone.Aviso, tonoBanner(lic(LicenseState.Trial(2)), ahora))
        assertEquals(BannerTone.Critico, tonoBanner(lic(LicenseState.Trial(1)), ahora))
    }

    @Test fun sinBannerElTonoEsNeutro() {
        assertEquals(BannerTone.Info, tonoBanner(lic(LicenseState.Perpetual("L3")), ahora))
        assertEquals(BannerTone.Info, tonoBanner(null, ahora))
    }

    @Test fun soloAvisoYCriticoLlevanSegundaLinea() {
        assertNull(detalleBanner(BannerTone.Info))
        assertNotNull(detalleBanner(BannerTone.Aviso))
        assertEquals(TEXTO_BANNER_CRITICO, detalleBanner(BannerTone.Critico))
    }
}
