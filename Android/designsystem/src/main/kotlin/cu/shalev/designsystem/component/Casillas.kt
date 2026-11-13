package cu.shalev.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize

/**
 * 0.32.0: casilla de verificación de Shalev. Es **más pequeña** que la de Material 3 (28 dp de lado, 18 dp de
 * cuadro): aquí la casilla mide [ShalevSize.casilla] y el cuadro visible [ShalevSize.casillaCuadro].
 *
 * Con [onCheckedChange] nulo no es pulsable (igual que la de Material 3): la fila entera es la que responde.
 */
@Composable
fun ShalevCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val esquema = MaterialTheme.colorScheme
    val lado = ShalevSize.casilla
    val cuadro = ShalevSize.casillaCuadro
    val trazo = 2.dp
    val base = modifier.size(lado)
    val interactivo = if (onCheckedChange != null) {
        base.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
    } else {
        base.semantics {
            this.role = Role.Checkbox
            this.toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
        }
    }
    Canvas(modifier = interactivo) {
        val radio = CornerRadius(ShalevRadius.sm.toPx() / 2f)
        // El cuadro va centrado y mide lo que dice el token (no todo el lienzo).
        val ladoCuadro = cuadro.toPx()
        val x0 = (size.width - ladoCuadro) / 2f
        val y0 = (size.height - ladoCuadro) / 2f
        val esquinas = androidx.compose.ui.geometry.Size(ladoCuadro, ladoCuadro)
        if (checked) {
            drawRoundRect(color = esquema.primary, topLeft = Offset(x0, y0), size = esquinas, cornerRadius = radio)
            // La marca, dibujada sobre un lienzo de 24 (la de Material) y escalada al tamaño del cuadro.
            val s = (ladoCuadro - 2 * trazo.toPx()) / 24f
            val dx = x0 + (ladoCuadro - 24f * s) / 2f
            val dy = y0 + (ladoCuadro - 24f * s) / 2f
            withTransform({ translate(dx, dy); scale(s, s) }) {
                drawPath(path = MARCA, color = esquema.onPrimary)
            }
        } else {
            val borde = if (enabled) esquema.onSurfaceVariant else esquema.onSurface.copy(alpha = 0.38f)
            drawRoundRect(
                color = borde, topLeft = Offset(x0, y0), size = esquinas, cornerRadius = radio,
                style = Stroke(width = trazo.toPx()),
            )
        }
    }
}

/** La «✓» de Material (forma rellena, lienzo 24 × 24). */
private val MARCA = Path().apply {
    moveTo(9f, 16.17f)
    lineTo(4.83f, 12f)
    lineTo(3.41f, 13.41f)
    lineTo(9f, 19f)
    lineTo(21f, 7f)
    lineTo(19.59f, 5.59f)
    close()
}
