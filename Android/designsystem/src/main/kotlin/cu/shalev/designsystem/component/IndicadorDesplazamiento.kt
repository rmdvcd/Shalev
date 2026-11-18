package cu.shalev.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Degradados discretos e indicador de desplazamiento: cuando quedan elementos sin mostrar abajo,
 * emite un flash/pulso azul adaptado al tema (azul marino en tema claro, azul cielo en tema oscuro)
 * que avisa al usuario para que haga scroll. No intercepta gestos.
 */
fun Modifier.shalevIndicadorDesplazamiento(
    color: Color? = null,
    hayContenidoArriba: () -> Boolean,
    hayContenidoAbajo: () -> Boolean,
): Modifier = composed {
    val azulTema = MaterialTheme.colorScheme.primary
    val colorEfectivo = color ?: azulTema
    val animacion = rememberInfiniteTransition(label = "flashScroll")
    val alphaAnim by animacion.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alphaFlashScroll",
    )

    drawWithContent {
        drawContent()
        val alto = minOf(20.dp.toPx(), size.height / 2f)
        val lineaAlto = 3.dp.toPx()
        if (alto > 0f && hayContenidoArriba()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(colorEfectivo.copy(alpha = 0.35f), Color.Transparent),
                    startY = 0f,
                    endY = alto,
                ),
                size = Size(size.width, alto),
            )
        }
        if (alto > 0f && hayContenidoAbajo()) {
            val alphaActual = alphaAnim
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, colorEfectivo.copy(alpha = alphaActual * 0.5f)),
                    startY = size.height - alto,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - alto),
                size = Size(size.width, alto),
            )
            drawRect(
                color = colorEfectivo.copy(alpha = alphaActual),
                topLeft = Offset(0f, size.height - lineaAlto),
                size = Size(size.width, lineaAlto),
            )
        }
    }
}
