package cu.shalev.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cu.shalev.data.local.SecureDataStore
import cu.shalev.data.repository.ConfiguracionInicialRepositoryImpl
import cu.shalev.data.repository.PreferenciasRepositoryImpl
import cu.shalev.domain.model.NivelesMinimos
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.data.security.KeystoreAead
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Configuración inicial en el DataStore cifrado REAL (Keystore): persiste entre instancias ("reinicios"). */
@RunWith(AndroidJUnit4::class)
class ConfiguracionInicialInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = androidx.room.Room.inMemoryDatabaseBuilder(context, cu.shalev.data.db.ShalevDatabase::class.java).build()
    private val ds = SecureDataStore(context, KeystoreAead(context))

    private fun limpiar() = runBlocking { listOf("setup.v1", "pref.v1", "pref.onboarding_done").forEach { ds.put(it, null) } }
    @Before fun antes() = limpiar()
    @After fun despues() { limpiar(); db.close() }

    @Test fun progresoPersisteCifrado() = runBlocking<Unit> {
        ConfiguracionInicialRepositoryImpl(ds).apply { confirmar(PasoConfiguracion.ALERTAS); marcarCamaraSolicitada() }
        val leido = ConfiguracionInicialRepositoryImpl(ds).estado.first()
        assertEquals(setOf(PasoConfiguracion.ALERTAS), leido.confirmados)
        assertTrue(leido.camaraSolicitada)
        // En disco no queda el texto en claro.
        val crudo = context.filesDir.resolve("datastore/spvi_secure.preferences_pb").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(crudo.contains("ALERTAS"))
    }

    @Test fun nivelesModulosYOnboardingPersisten() = runBlocking<Unit> {
        val prefs = PreferenciasRepositoryImpl(ds, db)
        assertEquals(NivelesMinimos(), prefs.preferencias.first().niveles) // 5/1 por defecto
        prefs.guardarNiveles(NivelesMinimos(productoBajo = 8, productoCritico = 2))
        prefs.guardarEmpleadosPrevistos(2)
        prefs.completarOnboarding()
        val otra = PreferenciasRepositoryImpl(ds, db).preferencias.first()
        assertEquals(8L, otra.niveles.productoBajo)
        assertEquals(2, otra.empleadosPrevistos)
        assertTrue(otra.onboardingCompletado)
    }
}
