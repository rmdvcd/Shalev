package cu.shalev.data

import cu.shalev.data.dto.PreferenciasDto
import cu.shalev.data.dto.toDomain
import cu.shalev.data.dto.toDto
import cu.shalev.data.repository.ConfiguracionInicialRepositoryImpl.Companion.codificar
import cu.shalev.data.repository.ConfiguracionInicialRepositoryImpl.Companion.decodificar
import cu.shalev.domain.model.ConfiguracionInicial
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.model.Preferencias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfiguracionInicialCodecTest {

    @Test fun idaYVuelta() {
        val c = ConfiguracionInicial(
            setOf(PasoConfiguracion.ALERTAS, PasoConfiguracion.PRUEBA),
            camaraSolicitada = true, permisosInicialesPedidos = true,
        )
        assertEquals(c, decodificar(codificar(c)))
    }

    @Test fun ausenteOIlegibleEsEstadoInicial() {
        assertEquals(ConfiguracionInicial(), decodificar(null))
        assertEquals(ConfiguracionInicial(), decodificar("{roto"))
    }

    @Test fun pasosDesconocidosSeIgnoran() {
        val c = decodificar("""{"confirmados":["ALERTAS","PASO_FUTURO"]}""")
        assertEquals(setOf(PasoConfiguracion.ALERTAS), c.confirmados)
    }

    @Test fun unaVersionAnteriorSinElCampoNuevoSigueValiendo() {
        // 0.31.0 (P9): un JSON guardado antes no trae el campo: se lee como "sin pedir".
        val c = decodificar("""{"confirmados":["ALERTAS"],"camaraSolicitada":true}""")
        assertEquals(setOf(PasoConfiguracion.ALERTAS), c.confirmados)
        assertTrue(c.camaraSolicitada)
        assertFalse(c.permisosInicialesPedidos)
    }

    @Test fun preferenciasSinConsentimientoVanYVienen() {
        val p = Preferencias()
        assertEquals(p, p.toDto().toDomain())
    }
}
