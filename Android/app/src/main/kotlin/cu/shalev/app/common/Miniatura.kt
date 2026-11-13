package cu.shalev.app.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

/**
 * 0.21.0 (C11, P46): miniatura de 40 dp de la foto de un producto o servicio en las filas de listas y del carrito.
 * Decorativa para TalkBack (el nombre ya está en la fila). Sin foto no se dibuja nada: la fila queda como antes.
 * Solo en la app: los PDF/Excel exportados no llevan fotos.
 */
@Composable
fun Miniatura(uri: String?, modifier: Modifier = Modifier) {
    if (uri.isNullOrBlank()) return
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(ShalevSize.miniatura).clip(RoundedCornerShape(ShalevRadius.sm)),
    )
}

/** Casilla de selección + miniatura, para el `leading` de las filas seleccionables. */
@Composable
fun ConMiniatura(uri: String?, casilla: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        casilla()
        Miniatura(uri)
    }
}
