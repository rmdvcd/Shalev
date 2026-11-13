package cu.shalev.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

/**
 * P18 (A10): tono de un aviso. Nunca depende solo del color: cada tono lleva su icono y el texto dice lo que pasa.
 * - [Info]: informativo (licencia con más de 7 días).
 * - [Aviso]: requiere atención pronto (7 días o menos).
 * - [Critico]: hoy (último día) o bloqueo inminente.
 */
enum class BannerTone { Info, Aviso, Critico }

/** Pares de color del tema (contenedor / contenido), todos AA en claro y oscuro (ver ContrastTest). */
@Composable
private fun BannerTone.colores(): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (this) {
        BannerTone.Info -> cs.primaryContainer to cs.onPrimaryContainer
        BannerTone.Aviso -> cs.secondaryContainer to cs.onSecondaryContainer
        BannerTone.Critico -> cs.errorContainer to cs.onErrorContainer
    }
}

private val BannerTone.icono: ImageVector
    get() = when (this) {
        BannerTone.Info -> ShalevIcons.Licencia
        BannerTone.Aviso -> ShalevIcons.Reloj
        BannerTone.Critico -> ShalevIcons.Alerta
    }

/**
 * Banner de estado con tono, icono, texto y una acción opcional (botón de icono con descripción).
 * Los tonos Aviso y Crítico se anuncian con una región viva cuando aparecen o cambian.
 */
@Composable
fun ShalevStatusBanner(
    text: String,
    tone: BannerTone,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionIcon: ImageVector? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val (fondo, contenido) = tone.colores()
    val vivo = if (tone == BannerTone.Info) Modifier else Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    val cuerpo: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        ) {
            Icon(tone.icono, contentDescription = null, modifier = Modifier.size(ShalevSize.icon))
            Column(Modifier.weight(1f).then(vivo)) {
                Text(text, style = MaterialTheme.typography.titleSmall)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (actionIcon != null && onAction != null) {
                ShalevIconAction(actionIcon, actionDescription ?: text, onClick = onAction)
            } else if (onClick != null) {
                // El banner entero es clicable: la flecha lo hace evidente sin añadir otra acción.
                Icon(ShalevIcons.Abrir, contentDescription = null, modifier = Modifier.size(ShalevSize.icon))
            }
        }
    }
    val shape = RoundedCornerShape(ShalevRadius.md)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = fondo, contentColor = contenido, content = cuerpo)
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = fondo, contentColor = contenido, content = cuerpo)
    }
}
