package cu.shalev.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.ShalevMarca
import cu.shalev.designsystem.token.ShalevElevation
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

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
                // Mismo tamaño de caja para todos los destinos: las proporciones del ítem no dependen del trazado.
                icon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(ShalevSize.icon)) },
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
 * Botón compartido de Ayuda en la esquina superior izquierda; abre el contenido de esa pestaña.
 * Va en el hueco de navegación de [ShalevTopBar]. 1.1 (regla de botones): icono «?» + «Ayuda», en el color primario.
 */
@Composable
fun ShalevBotonAyuda(
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ShalevSize.touchTarget).semantics { contentDescription = descripcion },
        contentPadding = PaddingValues(horizontal = ShalevSpacing.xs),
        colors = ButtonDefaults.textButtonColors(contentColor = cs.primary),
    ) {
        Icon(ShalevIcons.Ayuda, contentDescription = null, modifier = Modifier.size(ShalevSize.iconSmall))
        Spacer(Modifier.width(ShalevSpacing.xxs))
        Text(TEXTO_AYUDA, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Palabra visible del botón de ayuda. */
const val TEXTO_AYUDA = "Ayuda"

/**
 * Genera el texto enriquecido para el título "MIPVentaX", resaltando "IPV" en color y grosor de fuente.
 */
@Composable
fun construirTituloMIPVentaX(
    marca: Boolean = false,
    colorBase: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground,
    colorDestacado: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondary,
): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        withStyle(
            SpanStyle(
                color = colorBase,
                fontFamily = if (marca) ShalevMarca.fuente else null,
                fontWeight = FontWeight.Bold,
            )
        ) {
            append("M")
        }
        withStyle(
            SpanStyle(
                color = colorDestacado,
                fontFamily = ShalevMarca.fuente,
                fontWeight = FontWeight.ExtraBold,
            )
        ) {
            append("IPV")
        }
        withStyle(
            SpanStyle(
                color = colorBase,
                fontFamily = if (marca) ShalevMarca.fuente else null,
                fontWeight = FontWeight.Bold,
            )
        ) {
            append("entaX")
        }
    }
}

/**
 * Barra superior estándar, centrada (0.27.0, T1): título en el centro y sin logo; Atrás a la izquierda y las acciones
 * (ShalevIconAction) a la derecha. Un título largo pasa a 2 líneas como mucho y nunca tapa las acciones.
 * [marca] = true dibuja el título con la tipografía de marca (resaltando "IPV" con fuente y color en "MIPVentaX").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShalevTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    marca: Boolean = false,
    /** Ayuda de la pestaña; dibuja `ShalevIcons.Ayuda`. */
    onAyuda: (() -> Unit)? = null,
    ayudaDescripcion: String = "Ayuda de esta pestaña",
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            if (marca || title == "Shalev" || title == "MIPVentaX") {
                Text(
                    text = construirTituloMIPVentaX(marca = true),
                    style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 2.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            } else if (title.contains("MIPVentaX")) {
                val partes = title.split("MIPVentaX")
                val annotated = buildAnnotatedString {
                    append(partes.getOrElse(0) { "" })
                    append(construirTituloMIPVentaX(marca = false))
                    if (partes.size > 1) append(partes[1])
                }
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            } else {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
        },
        navigationIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onAyuda != null) {
                    if (LocalShalevVentana.current) {
                        ShalevIconAction(ShalevIcons.Ayuda, ayudaDescripcion, onClick = onAyuda, etiqueta = "Ayuda", modifier = Modifier.coachMark(CoachMarkObjetivos.AYUDA))
                    } else ShalevBotonAyuda(descripcion = ayudaDescripcion, onClick = onAyuda, modifier = Modifier.coachMark(CoachMarkObjetivos.AYUDA))
                }
                if (onBack != null) {
                    if (LocalShalevVentana.current) {
                        ShalevIconAction(ShalevIcons.Atras, "Atrás", onClick = onBack, etiqueta = "Atrás")
                    } else IconButton(onClick = onBack) { Icon(ShalevIcons.Atras, contentDescription = "Atrás") }
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
