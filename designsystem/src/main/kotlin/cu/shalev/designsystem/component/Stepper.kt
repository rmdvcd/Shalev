package cu.shalev.designsystem.component

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

/** Textos del asistente: funciones puras para poder probarlas en JVM. */
object ShalevStepperTextos {
    fun paso(actual: Int, total: Int): String = "Paso $actual de $total"
    fun anuncio(actual: Int, total: Int, titulo: String): String = "${paso(actual, total)}: $titulo"
    const val ATRAS = "Paso anterior"
    const val SIGUIENTE = "Paso siguiente"
}

/**
 * P18 (A08, A09): cabecera de asistente (Licencia, Respaldo, Onboarding).
 *
 * - Muestra «Paso 2 de 3», el título del paso y una barra de progreso.
 * - Al cambiar de paso, TalkBack lo anuncia («Paso 2 de 3: Enviar solicitud») gracias a una región viva.
 * - Atrás y Siguiente son botones de icono de 48dp. Si no se pasan, no se muestran; si falta solo uno, su hueco
 *   se reserva para que el título no salte. Sin ninguno (Onboarding, Importar), el título usa todo el ancho.
 * - [siguienteHabilitado] = false hasta que el paso tenga datos válidos.
 */
@Composable
fun ShalevStepper(
    actual: Int,
    total: Int,
    titulo: String,
    modifier: Modifier = Modifier,
    onAtras: (() -> Unit)? = null,
    onSiguiente: (() -> Unit)? = null,
    siguienteHabilitado: Boolean = true,
) {
    val conBotones = onAtras != null || onSiguiente != null
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            if (onAtras != null) {
                ShalevIconAction(ShalevIcons.Disminuir, ShalevStepperTextos.ATRAS, onClick = onAtras)
            } else if (conBotones) {
                Spacer(Modifier.size(ShalevSize.touchTarget))
            }
            Column(
                Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = ShalevStepperTextos.anuncio(actual, total, titulo)
                    liveRegion = LiveRegionMode.Polite
                    heading()
                },
                horizontalAlignment = Alignment.CenterHorizontally, // P28: centrado entre Atrás y Siguiente
            ) {
                ShalevSecondaryText(ShalevStepperTextos.paso(actual, total), textAlign = TextAlign.Center)
                Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            if (onSiguiente != null) {
                ShalevIconAction(
                    ShalevIcons.Aumentar, ShalevStepperTextos.SIGUIENTE, onClick = onSiguiente,
                    enabled = siguienteHabilitado, style = IconActionStyle.Filled,
                )
            } else if (conBotones) {
                Spacer(Modifier.size(ShalevSize.touchTarget))
            }
        }
        ShalevLinearProgress(Modifier.fillMaxWidth(), progress = actual.toFloat() / total.coerceAtLeast(1))
    }
}
