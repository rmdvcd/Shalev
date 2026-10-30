package cu.shalev.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.ShalevMarca
import cu.shalev.designsystem.token.ShalevElevation
import cu.shalev.designsystem.token.ShalevSize

@Immutable
data class ShalevNavItem(val key: String, val icon: ImageVector, val label: String)

/**
 * Barra inferior: icono sólido con el **texto del destino debajo** (1.0.2). Seleccionado = resaltado invertido
 * (indicador primary + icono y texto onPrimary). El texto es también el nombre accesible de TalkBack.
 */
@Composable
fun ShalevNavigationBar(
    items: List<ShalevNavItem>,
    selectedKey: String?,
    onSelect: (ShalevNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    NavigationBar(modifier = modifier, containerColor = cs.surfaceContainer, tonalElevation = ShalevElevation.none) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item.key == selectedKey,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = cs.onPrimary,
                    indicatorColor = cs.primary,
                    unselectedIconColor = cs.onSurfaceVariant,
                    selectedTextColor = cs.primary,
                    unselectedTextColor = cs.onSurfaceVariant,
                    disabledTextColor = cs.onSurfaceVariant,
                    disabledIconColor = cs.onSurfaceVariant,
                ),
            )
        }
    }
}

/**
 * 1.0.2: botón de ayuda «?» de la esquina superior izquierda de cada pestaña principal.
 * Va en el hueco de navegación de [ShalevTopBar]; abre el contenido de ayuda de esa pestaña.
 */
@Composable
fun ShalevBotonAyuda(
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.size(ShalevSize.touchTarget).semantics { contentDescription = descripcion },
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Text("?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.primary)
    }
}

/**
 * Barra superior estándar, centrada (0.27.0, T1): título en el centro y sin logo; Atrás a la izquierda y las acciones
 * (ShalevIconAction) a la derecha. Un título largo pasa a 2 líneas como mucho y nunca tapa las acciones.
 * [marca] = true dibuja el título con la tipografía de marca (solo cuando el título es el nombre de la app, «Shalev»).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShalevTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    marca: Boolean = false,
    /** 1.0.2: ayuda de la pestaña; se dibuja como «?» en la esquina superior izquierda. */
    onAyuda: (() -> Unit)? = null,
    ayudaDescripcion: String = "Ayuda de esta pestaña",
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(
                title,
                style = if (marca) MaterialTheme.typography.titleLarge.copy(fontFamily = ShalevMarca.fuente, letterSpacing = 2.sp)
                else MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        },
        navigationIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onAyuda != null) ShalevBotonAyuda(descripcion = ayudaDescripcion, onClick = onAyuda)
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(ShalevIcons.Atras, contentDescription = "Atrás") }
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}
