package cu.shalev.licencia

import cu.shalev.licencia.contract.TipoLicencia
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Banner de Inicio (Shalev.txt): prueba y Mensual en días, Semestral/Anual en meses, Perpetua sin banner. */
class BannerTest {
    private val now = Instant.parse("2026-09-30T12:00:00Z")
    private val zone = ZoneId.of("America/Havana")
    private fun active(tipo: TipoLicencia, dias: Long) = LicenseState.Active(tipo, now.plus(Duration.ofDays(dias)), "id")

    @Test fun prueba() {
        assertEquals("Periodo de prueba restante: 5 días", LicenseState.Trial(5).bannerText(now, zone))
        assertEquals("Periodo de prueba restante: 1 día", LicenseState.Trial(1).bannerText(now, zone))
    }

    @Test fun mensualSiempreEnDias() {
        assertEquals("Licencia mensual: 30 días restantes", active(TipoLicencia.MENSUAL, 30).bannerText(now, zone))
        assertEquals("Licencia mensual: 1 día restante", active(TipoLicencia.MENSUAL, 1).bannerText(now, zone))
    }

    @Test fun semestralYAnualEnMeses() {
        assertEquals("Licencia semestral: 5 meses restantes", active(TipoLicencia.SEMESTRAL, 170).bannerText(now, zone))
        assertEquals("Licencia anual: 11 meses restantes", active(TipoLicencia.ANUAL, 350).bannerText(now, zone))
        assertEquals("Licencia anual: 1 mes restante", active(TipoLicencia.ANUAL, 40).bannerText(now, zone))
    }

    @Test fun ultimoMesPasaADias() {
        assertEquals("Licencia anual: 20 días restantes", active(TipoLicencia.ANUAL, 20).bannerText(now, zone))
    }

    @Test fun perpetuaYEstadosBloqueadosSinBanner() {
        assertNull(LicenseState.Perpetual("id").bannerText(now, zone))
        assertNull(LicenseState.TrialExpired.bannerText(now, zone))
        assertNull(LicenseState.Expired(TipoLicencia.MENSUAL).bannerText(now, zone))
        assertNull(LicenseState.Revoked.bannerText(now, zone))
    }
}
