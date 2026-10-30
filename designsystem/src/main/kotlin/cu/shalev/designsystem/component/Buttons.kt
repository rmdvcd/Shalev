package cu.shalev.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cu.shalev.designsystem.token.ShalevFontScale
import cu.shalev.designsystem.token.ShalevMotion
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

/** 1.0.2: margen ligero de los botones con texto (16dp a los lados, 8dp arriba y abajo). */
private val PaddingTexto = PaddingValues(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs)

/**
 * P18 (A01): altura de los botones. Normalmente va de 40 a 48dp. Con letra grande del sistema
 * ([ShalevFontScale.GRANDE]) se quita el máximo: la etiqueta puede ocupar 2 líneas y nunca se recorta (WCAG 1.4.4).
 * P24: los botones solo icono miden siempre 48dp de alto (área táctil completa).
 */
@Composable
private fun Modifier.alturaBoton(soloIcono: Boolean): Modifier = when {
    soloIcono -> heightIn(min = ShalevSize.touchTarget).widthIn(min = ShalevSize.touchTarget)
    letraGrande() -> heightIn(min = ShalevSize.buttonMin)
    else -> heightIn(min = ShalevSize.buttonMin, max = ShalevSize.buttonMax)
}

/** true si el usuario eligió letra grande en el sistema (≥ [ShalevFontScale.GRANDE]). */
@Composable
@ReadOnlyComposable
fun letraGrande(): Boolean = LocalDensity.current.fontScale >= ShalevFontScale.GRANDE

/**
 * P24 → 1.0.2: los botones de Shalev muestran **texto**.
 * - [text] es la etiqueta visible, la descripción de TalkBack y el nombre accesible.
 * - El ancho se ajusta al texto con un margen ligero; nunca ocupan todo el ancho.
 * - [icon] se conserva en la firma por compatibilidad con las llamadas existentes: ya no se dibuja.
 */
@Composable
fun ShalevPrimaryButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.alturaBoton(soloIcono = false).semantics { contentDescription = text },
        enabled = enabled && !loading,
        shape = RoundedCornerShape(ShalevRadius.md),
        contentPadding = PaddingTexto,
    ) { EtiquetaBoton(text, loading) }
}

/** Acción secundaria: contorno, sin relleno, con el texto visible (ver [ShalevPrimaryButton]). */
@Composable
fun ShalevSecondaryButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.alturaBoton(soloIcono = false).semantics { contentDescription = text },
        enabled = enabled && !loading,
        shape = RoundedCornerShape(ShalevRadius.md),
        contentPadding = PaddingTexto,
        border = ButtonDefaults.outlinedButtonBorder(enabled),
    ) { EtiquetaBoton(text, loading) }
}

/** Acción terciaria: sin fondo ni borde, con el texto visible (ver [ShalevPrimaryButton]). */
@Composable
fun ShalevTextButton(text: String, onClick: () -> Unit, icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        modifier = modifier.alturaBoton(soloIcono = false).semantics { contentDescription = text },
        enabled = enabled,
        shape = RoundedCornerShape(ShalevRadius.md),
        contentPadding = PaddingTexto,
    ) { EtiquetaBoton(text, loading = false) }
}

/** Etiqueta común de los botones: texto (y el indicador de carga delante cuando trabaja). */
@Composable
private fun EtiquetaBoton(text: String, loading: Boolean) {
    if (loading) {
        CircularProgressIndicator(
            Modifier.size(ShalevSize.iconSmall).semantics { contentDescription = text },
            strokeWidth = 2.dp, color = LocalContentColor.current,
        )
        Spacer(Modifier.width(ShalevSpacing.xs))
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

enum class IconActionStyle { Standard, Tonal, Filled }

/**
 * Acción con etiqueta corta (Confirmar, Cancelar, Editar, Agregar, Eliminar, Compartir, Importar, Exportar, WhatsApp…).
 * - 1.0.2: se dibuja **texto**, no el icono. [contentDescription] es la etiqueta visible y la que lee TalkBack.
 * - [etiqueta] permite un texto visible más corto cuando [contentDescription] es largo (p. ej. «−1» en el conteo de billetes).
 * - Altura de 40 a 48dp y ancho ajustado al texto, con área táctil mínima.
 * - [selected] = resaltado invertido (fondo primary, texto onPrimary), animado.
 */
@Composable
fun ShalevIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    style: IconActionStyle = IconActionStyle.Standard,
    containerColor: Color? = null,
    etiqueta: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when {
        selected -> cs.primary to cs.onPrimary
        containerColor != null -> containerColor to contentColorFor(containerColor).takeOrElse(Color.White)
        style == IconActionStyle.Filled -> cs.primary to cs.onPrimary
        // 0.21.1 (H2): el tonal usa el terciario (petróleo). El naranja (secondaryContainer) queda solo para avisos.
        style == IconActionStyle.Tonal -> cs.tertiaryContainer to cs.onTertiaryContainer
        else -> Color.Transparent to cs.onSurfaceVariant
    }
    val animBg by animateColorAsState(bg, ShalevMotion.tween(), label = "iconActionBg")
    val animFg by animateColorAsState(fg, ShalevMotion.tween(), label = "iconActionFg")
    val texto = etiqueta ?: contentDescription
    val base = modifier
        .alturaBoton(soloIcono = false)
        .semantics {
            this.contentDescription = contentDescription
            if (selected) this.selected = true
        }

    if (style == IconActionStyle.Standard && !selected && containerColor == null) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = base,
            shape = RoundedCornerShape(ShalevRadius.md),
            contentPadding = PaddingTexto,
            colors = ButtonDefaults.textButtonColors(contentColor = animFg),
        ) { EtiquetaAccion(texto) }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = base,
            shape = RoundedCornerShape(ShalevRadius.md),
            contentPadding = PaddingTexto,
            colors = ButtonDefaults.buttonColors(containerColor = animBg, contentColor = animFg),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) { EtiquetaAccion(texto) }
    }
}

@Composable
private fun EtiquetaAccion(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

private fun Color.takeOrElse(fallback: Color) = if (this == Color.Unspecified) fallback else this

/**
 * Fila de acciones de formularios, diálogos y ventanas emergentes.
 * P24: **centradas** y con separación media (16dp), en orden Cancelar → Confirmar (Jakob: el orden de Android).
 */
@Composable
fun ShalevButtonRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * P24: barra inferior fija de los formularios (Guardar, Siguiente…). Sustituye al «Guardar» repetido arriba y abajo:
 * una sola acción, siempre visible y al alcance del pulgar (Fitts), centrada (ver [ShalevButtonRow]).
 */
@Composable
fun ShalevBarraAcciones(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ShalevButtonRow(Modifier.navigationBarsPadding().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs), content = content)
        }
    }
}
