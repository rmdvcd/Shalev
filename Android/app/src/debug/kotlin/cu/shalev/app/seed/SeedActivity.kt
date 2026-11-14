package cu.shalev.app.seed

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import cu.shalev.data.seed.SembradorDebug
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.repository.ConfiguracionInicialRepository
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.PreferenciasRepository
import cu.shalev.licencia.contract.CodigoLicencia
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * **Solo builds de depuración.** Pantalla mínima que carga los datos de demostración al abrirse:
 *
 * ```
 * adb shell am start -n cu.shalev.app.debug/cu.shalev.app.seed.SeedActivity
 * ```
 *
 * Siembra el histórico de 2 años (tres locales con catálogo e historial propios) y además deja la app
 * lista para usar: activa la **clave maestra de desarrollo** (solo vale en builds `DEBUG`) y da por
 * terminado el asistente de primera ejecución, para que al abrir Shalev se vea la app y no el bloqueo.
 *
 * Vive en `app/src/debug`, así que no existe en el APK de release: el sembrador está en `data/src/debug`.
 */
class SeedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val titulo = TextView(this).apply {
            text = "Sembrando datos de demostración…"
            textSize = 18f
        }
        val detalle = TextView(this).apply {
            text = ""
            textSize = 14f
            setPadding(0, 32, 0, 0)
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(64, 160, 64, 64)
                addView(titulo)
                addView(detalle)
            },
        )
        lifecycleScope.launch {
            val resultado = runCatching {
                withContext(Dispatchers.IO) {
                    val acceso = EntryPointAccessors.fromApplication(applicationContext, AccesoSeed::class.java)
                    val partes = mutableListOf<String>()
                    partes += SembradorDebug.ejecutar(applicationContext)
                    partes += activarLicencia(acceso.licencia())
                    partes += cerrarAsistente(acceso)
                    partes.joinToString("\n")
                }
            }
            titulo.text = if (resultado.isSuccess) "Listo" else "Error"
            detalle.text = resultado.fold({ it }, { it.stackTraceToString().take(1500) })
            android.util.Log.i("Sembrador", detalle.text.toString())
        }
    }

    /** Activa la clave maestra de desarrollo (solo en builds `DEBUG`) para que la app no quede bloqueada. */
    private suspend fun activarLicencia(licencia: LicenciaRepository): String {
        val r = licencia.activar(CodigoLicencia.MAESTRA)
        return "Licencia: " + when (r) {
            is cu.shalev.licencia.ActivationResult.Accepted -> "activada (${r.state})"
            is cu.shalev.licencia.ActivationResult.Rejected -> "rechazada"
            is cu.shalev.licencia.ActivationResult.Outdated -> "hay una más reciente"
            cu.shalev.licencia.ActivationResult.NotFound -> "no encontrada"
        }
    }

    /** Da por hechos los pasos del asistente: la app abre en Inicio, no en el recorrido inicial. */
    private suspend fun cerrarAsistente(acceso: AccesoSeed): String {
        acceso.preferencias().completarOnboarding()
        acceso.configuracionInicial().confirmar(PasoConfiguracion.DATOS)
        acceso.configuracionInicial().confirmar(PasoConfiguracion.ALERTAS)
        acceso.configuracionInicial().confirmar(PasoConfiguracion.PRUEBA)
        return "Asistente de primera ejecución: completado"
    }
}

/** Repositorios que necesita la pantalla de seed (grafo de Hilt). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AccesoSeed {
    fun licencia(): LicenciaRepository
    fun preferencias(): PreferenciasRepository
    fun configuracionInicial(): ConfiguracionInicialRepository
}
