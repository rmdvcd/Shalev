package cu.shalev.app.recorrido

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Indicadores «ya visto» de los recorridos en un Preferences DataStore real (archivo temporal, sin Android). */
class OnboardingPreferencesTest {
    @get:Rule val carpeta = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun prefs(nombre: String = "recorridos"): OnboardingPreferences = OnboardingPreferences(
        PreferenceDataStoreFactory.create(scope = scope) { File(carpeta.root, "$nombre.preferences_pb") },
    )

    @After fun cerrar() = scope.cancel()

    @Test fun clavesPorModulo() {
        assertEquals("ventas_tour_shown", OnboardingPreferences.clave(Recorrido.VENTAS).name)
        assertEquals("inventario_tour_shown", OnboardingPreferences.clave(Recorrido.INVENTARIO).name)
        assertEquals("caja_tour_shown", OnboardingPreferences.clave(Recorrido.CAJA).name)
        assertEquals("ajustes_tour_shown", OnboardingPreferences.clave(Recorrido.AJUSTES).name)
        assertEquals("ventana_fondo_tour_shown", OnboardingPreferences.clave(Recorrido.VENTANA_FONDO).name)
        assertEquals(Recorrido.entries.size, Recorrido.entries.map { OnboardingPreferences.clave(it).name }.toSet().size)
    }

    @Test fun lasPantallasYLasVentanasConControlesTienenRecorrido() {
        val claves = Recorrido.entries.map { it.clave }.toSet()
        // Pestañas, Ajustes y cada módulo de Ajustes.
        listOf("caja", "ventas", "inventario", "servicios", "registros", "ajustes", "perfil", "licencia", "vinculacion", "pago", "precios", "respaldo", "manual", "soporte")
            .forEach { assertTrue(it, it in claves) }
        // Ventanas con varios controles (formularios, filtros, exportar, fondo de caja, cierre de turno…).
        listOf("ventana_fondo", "ventana_conteo", "ventana_cierre", "ventana_movimiento", "ventana_filtro_inventario", "ventana_exportar_registros")
            .forEach { assertTrue(it, it in claves) }
    }

    @Test fun sinDatosNingunRecorridoEstaVisto() = runBlocking {
        val p = prefs()
        Recorrido.entries.forEach { assertFalse(it.name, p.visto(it).first()) }
    }

    @Test fun marcarUnoNoAfectaALosDemasYReiniciarLosVuelveAMostrar() = runBlocking {
        val p = prefs()
        p.marcarVisto(Recorrido.VENTAS)
        assertTrue(p.visto(Recorrido.VENTAS).first())
        assertFalse(p.visto(Recorrido.INVENTARIO).first())

        p.reiniciar()
        Recorrido.entries.forEach { assertFalse(it.name, p.visto(it).first()) }
    }

    @Test fun cadaRecorridoTieneHastaOchoPasosAgrupadosConObjetivosUnicos() {
        Recorrido.entries.forEach { r ->
            assertTrue(r.name, r.pasos.size in 1..8)
            assertEquals(r.name, r.pasos.size, r.pasos.map { it.objetivo }.toSet().size)
            r.pasos.forEach { p ->
                assertTrue("${r.name}: título largo «${p.titulo}»", p.titulo.length <= 28)
                assertTrue("${r.name}: descripción larga «${p.descripcion}»", p.descripcion.length <= 120)
            }
        }
    }
}
