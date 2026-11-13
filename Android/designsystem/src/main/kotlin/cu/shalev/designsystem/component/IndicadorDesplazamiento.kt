package cu.shalev.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Degradados discretos en los bordes cuando queda contenido por desplazar. No interceptan gestos. */
fun Modifier.shalevIndicadorDesplazamiento(
    color: Color,
    hayContenidoArriba: () -> Boolean,
    hayContenidoAbajo: () -> Boolean,
): Modifier = drawWithContent {
    drawContent()
    val alto = minOf(20.dp.toPx(), size.height / 2f)
    if (alto > 0f && hayContenidoArriba()) {
        drawRect(
            brush = Brush.verticalGradient(listOf(color, Color.Transparent), startY = 0f, endY = alto),
            size = Size(size.width, alto),
        )
    }
    if (alto > 0f && hayContenidoAbajo()) {
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, color), startY = size.height - alto, endY = size.height),
            topLeft = Offset(0f, size.height - alto), size = Size(size.width, alto),
        )
    }
}
