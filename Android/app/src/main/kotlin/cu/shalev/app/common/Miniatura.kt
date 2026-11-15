package cu.shalev.app.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

/**
 * Miniatura de 40 dp de la foto de un producto o servicio. En tablas puede mostrar un icono discreto de «sin foto»:
 * así todas las filas mantienen la misma referencia visual sin inventar una imagen. Solo en la app: los PDF/Excel
 * exportados no llevan fotos.
 */
@Composable
fun Miniatura(
    uri: String?,
    modifier: Modifier = Modifier,
    mostrarSinFoto: Boolean = false,
) {
    if (uri.isNullOrBlank()) {
        if (!mostrarSinFoto) return
        Surface(
            modifier = modifier.size(ShalevSize.miniatura),
            shape = RoundedCornerShape(ShalevRadius.sm),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(ShalevIcons.SinFoto, contentDescription = "Sin foto", modifier = Modifier.size(ShalevSize.iconSmall))
            }
        }
        return
    }
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
        Miniatura(uri, mostrarSinFoto = true)
    }
}
