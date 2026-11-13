package cu.shalev.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

/** Una pestaña: texto visible y, opcionalmente, un número (p. ej. elementos en alerta), que se lee tras el nombre. */
data class ShalevTab(val label: String, val count: Int? = null, val testTag: String? = null) {
    val texto: String get() = if (count != null) "$label ($count)" else label
}

/**
 * P18 (A11): pestañas reales en lugar de chips. Elaboración (Insumos / Elaborados) y Registros las usan.
 *
 * - TalkBack anuncia «pestaña, seleccionada, 2 de 3»: rol Tab más información de colección.
 * - Seleccionada = resaltado invertido (fondo primary, texto onPrimary), igual que la barra inferior. No depende
 *   solo del color: el estado seleccionado también va en la semántica.
 * - Altura mínima de 48dp; si no cabe (letra grande, «Transferencias»), el texto ocupa 2 líneas.
 * - Con más de 4 pestañas (Registros, de Ventas a Turnos) hay una sola fila horizontal desplazable,
 *   por decisión del dueño: se desliza el dedo para verlas todas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShalevTabs(
    tabs: List<ShalevTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val indice = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val mod = modifier.semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = tabs.size) }
    // Con más de 4 pestañas (Registros, de Ventas a Turnos): una sola fila horizontal
    // desplazable, por decisión del dueño.
    val contenido: @Composable () -> Unit = {
        tabs.forEachIndexed { i, tab ->
            val seleccionada = i == selectedIndex
            Tab(
                selected = seleccionada,
                onClick = { onSelect(i) },
                modifier = Modifier
                    .padding(horizontal = ShalevSpacing.xs / 2, vertical = ShalevSpacing.xs / 2)
                    .clip(RoundedCornerShape(ShalevRadius.sm))
                    .background(if (seleccionada) cs.primary else Color.Transparent)
                    .heightIn(min = ShalevSize.touchTarget)
                    .then(if (tab.testTag != null) Modifier.testTag(tab.testTag) else Modifier)
                    .semantics { collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = i, columnSpan = 1) },
                selectedContentColor = cs.onPrimary,
                unselectedContentColor = cs.onSurfaceVariant,
                text = {
                    Text(
                        tab.texto, style = MaterialTheme.typography.labelLarge, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    )
                },
            )
        }
    }
    if (tabs.size > MAX_FIJAS) {
        ScrollableTabRow(
            selectedTabIndex = indice, modifier = mod.selectableGroup(),
            containerColor = cs.surface, contentColor = cs.onSurface,
            edgePadding = ShalevSpacing.xs, indicator = {}, divider = {}, tabs = contenido,
        )
    } else {
        SecondaryTabRow(
            selectedTabIndex = indice, modifier = mod, containerColor = cs.surface, contentColor = cs.onSurface,
            indicator = {}, divider = {}, tabs = contenido,
        )
    }
}

private const val MAX_FIJAS = 4
