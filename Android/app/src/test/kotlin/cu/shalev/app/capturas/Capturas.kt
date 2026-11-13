package cu.shalev.app.capturas

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import cu.shalev.designsystem.theme.ShalevTheme
import java.time.Instant
import java.time.ZoneId

/**
 * P23 — Capturas reales de la interfaz con Robolectric + Roborazzi (JVM, sin emulador y sin red).
 *
 * Cada caso pinta un `*Content` sin estado (los mismos que usan los tests instrumentados) con datos de ejemplo y
 * guarda DOS imágenes con el mismo nombre: `app/capturas/claro/ID.png` y `app/capturas/oscuro/ID.png`.
 * Se usa `captureScreenRoboImage` (toda la pantalla), así los diálogos y hojas, que viven en su propia ventana,
 * también salen en la imagen.
 *
 * Ejecutar: `./gradlew :app:recordRoborazziDebug` (ver CAPTURAS.md). En `test`/`shalevTests` están excluidas.
 */
object Captura {
    /** Carpeta relativa al módulo :app. */
    const val DIR = "capturas"

    /** Teléfono de referencia: 360×800 dp a 320 dpi (xhdpi). Igual que las maquetas HTML. */
    const val TELEFONO = "w360dp-h800dp-xhdpi"

    /** Para pantallas largas (formularios, Inicio completo): misma anchura, más alto para ver todo sin desplazar. */
    const val LARGA = "+h1600dp"

    val ZONA: ZoneId = ZoneId.of("America/Havana")
    val AHORA: Instant = Instant.parse("2026-10-01T16:30:00Z")
}

/**
 * Pinta [contenido] en claro, captura, cambia a oscuro sin recrear la actividad y vuelve a capturar.
 * [antes] permite interactuar (p. ej. pulsar «Siguiente» del asistente) antes de la primera captura.
 */
fun ComposeContentTestRule.capturar(
    id: String,
    antes: ComposeContentTestRule.() -> Unit = {},
    contenido: @Composable () -> Unit,
) {
    var oscuro by mutableStateOf(false)
    setContent {
        // La actividad de prueba (ui-test-manifest) hereda Theme.Shalev.Splash, que en Robolectric tiene ActionBar y
        // tapaba la parte de arriba de cada captura. En el teléfono, installSplashScreen() pasa a Theme.Shalev (sin barra).
        val actividad = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
        androidx.compose.runtime.LaunchedEffect(actividad) { actividad?.actionBar?.hide() }
        ShalevTheme(darkTheme = oscuro) { contenido() }
    }
    waitForIdle()
    antes()
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
    captureScreenRoboImage("${Captura.DIR}/claro/$id.png")
    oscuro = true
    mainClock.advanceTimeBy(1_000)
    waitForIdle()
    captureScreenRoboImage("${Captura.DIR}/oscuro/$id.png")
}

/** Snackbar visible desde el primer fotograma (los mensajes reales los emite el ViewModel). */
@Composable
fun snackbarCon(mensaje: String): SnackbarHostState {
    val s = remember { SnackbarHostState() }
    LaunchedEffect(mensaje) { s.showSnackbar(mensaje) }
    return s
}

/**
 * 0.27.0 (T2): letra del sistema al 200 % (como Ajustes → Pantalla → Tamaño de fuente al máximo) sin depender de la
 * versión de Robolectric: se cambia `fontScale` de la densidad que ve Compose.
 */
@Composable
fun LetraGrande(escala: Float = 2f, contenido: @Composable () -> Unit) {
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, escala),
    ) { contenido() }
}
