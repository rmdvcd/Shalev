package cu.shalev.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import cu.shalev.designsystem.theme.ShalevTextos
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

/** Badge numérico. Con 0 no se muestra. */
@Composable
fun ShalevBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Badge(modifier = modifier) {
        Text(if (count > 99) "99+" else count.toString(), fontWeight = ShalevTextos.PESO_DATO)
    }
}

/** Chip de filtro/selección con resaltado invertido al seleccionarse. */
@Composable
fun ShalevChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supportingLabel: String? = null,
    /** 0.28.0: el texto de apoyo también es un dato (p. ej. el precio del tipo de licencia) y va en seminegrita. */
    supportingEsDato: Boolean = false,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        label = {
            if (supportingLabel == null) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            } else {
                // 0.30.1: margen vertical para que las dos líneas no queden pegadas al borde del chip.
                Column(Modifier.padding(vertical = ShalevSpacing.xs)) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Text(
                        supportingLabel,
                        style = if (supportingEsDato) ShalevTextos.datoEn(MaterialTheme.typography.labelSmall)
                        else MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(ShalevSize.iconSmall)) } },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = cs.primary,
            selectedLabelColor = cs.onPrimary,
            selectedLeadingIconColor = cs.onPrimary,
        ),
    )
}

/**
 * Carga a pantalla completa (o dentro de un contenedor).
 * 1.0.2: se dibuja con un marcador tipo **skeleton** ([forma]) en vez de una rueda: la pantalla conserva la
 * estructura del contenido mientras llegan los datos. TalkBack anuncia «Cargando».
 */
@Composable
fun ShalevLoading(modifier: Modifier = Modifier, forma: FormaSkeleton = FormaSkeleton.LISTA) {
    Box(modifier.fillMaxSize().semantics { contentDescription = "Cargando" }) {
        ShalevSkeleton(Modifier.fillMaxWidth(), forma)
    }
}

/**
 * 0.31.0 (P5): progreso circular (determinado si [progress] no es null). Para el cuadro de «Actualizar», donde el
 * resultado se lee de un vistazo: la barra mientras trabaja y el mensaje cuando termina.
 */
@Composable
fun ShalevCircularProgress(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    contentDescription: String = "Progreso",
) {
    if (progress == null) {
        CircularProgressIndicator(
            modifier.size(ShalevSize.progresoCircular).semantics { this.contentDescription = contentDescription },
        )
    } else {
        CircularProgressIndicator(
            progress = { progress },
            modifier = modifier.size(ShalevSize.progresoCircular).semantics { this.contentDescription = contentDescription },
        )
    }
}

/** Progreso lineal (determinado si [progress] no es null). */
@Composable
fun ShalevLinearProgress(modifier: Modifier = Modifier, progress: Float? = null) {
    if (progress == null) {
        LinearProgressIndicator(modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(progress = { progress }, modifier = modifier.fillMaxWidth())
    }
}
