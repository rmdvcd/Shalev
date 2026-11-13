package cu.shalev.app.seed

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import cu.shalev.data.seed.SembradorDebug
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
                withContext(Dispatchers.IO) { SembradorDebug.ejecutar(applicationContext) }
            }
            titulo.text = if (resultado.isSuccess) "Listo" else "Error"
            detalle.text = resultado.fold({ it }, { it.stackTraceToString().take(1500) })
            android.util.Log.i("Sembrador", detalle.text.toString())
        }
    }
}
