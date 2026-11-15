package cu.shalev.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.designsystem.token.ShalevFontScale
import cu.shalev.designsystem.token.ShalevMotion
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

/** 1.0.2: margen ligero de los botones con texto (16dp a los lados, 8dp arriba y abajo). */
private val PaddingTexto = PaddingValues(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs)

/**
 * P18 (A01): altura de los botones. Normalmente va de 40 a 48dp. Con letra grande del sistema
 * ([ShalevFontScale.GRANDE]) se quita el máximo y el mínimo sube a 48dp: la etiqueta puede ocupar 2 líneas y nunca
 * se recorta (WCAG 1.4.4).
 * P24: los botones solo icono miden siempre 48dp de alto (área táctil completa).
 */
@Composable
private fun Modifier.alturaBoton(soloIcono: Boolean): Modifier = when {
    soloIcono -> heightIn(min = ShalevSize.touchTarget).widthIn(min = ShalevSize.touchTarget)
    // Con letra grande el botón crece con su etiqueta y nunca baja del área táctil de 48dp.
    letraGrande() -> heightIn(min = ShalevSize.touchTarget)
    else -> heightIn(min = ShalevSize.buttonMin, max = ShalevSize.buttonMax)
}

/** Respuesta inmediata al pulsar; la transformación no cambia el área táctil de 48dp del componente. */
@Composable
private fun Modifier.feedbackPulsacion(interactionSource: MutableInteractionSource): Modifier {
    val presionado by interactionSource.collectIsPressedAsState()
    return graphicsLayer {
        val escala = if (presionado) ShalevMotion.PRESS_SCALE else 1f
        scaleX = escala
        scaleY = escala
    }
}

/** true si el usuario eligió letra grande en el sistema (≥ [ShalevFontScale.GRANDE]). */
@Composable
@ReadOnlyComposable
fun letraGrande(): Boolean = LocalDensity.current.fontScale >= ShalevFontScale.GRANDE

/**
 * Marca el contenido de un diálogo o ventana emergente: sus acciones comunes muestran siempre una etiqueta escrita,
 * además del icono en los contextos compactos fuera de estas ventanas.
 */
val LocalShalevVentana = compositionLocalOf { false }

@Composable
private fun Modifier.formaBoton(mostrarTexto: Boolean): Modifier =
    if (mostrarTexto) alturaBoton(soloIcono = false) else size(ShalevSize.touchTarget)

/**
 * Regla de botones (1.1): todo botón muestra **texto** (en ventanas emergentes) o **icono + texto** (fuera de ellas),
 * y el texto visible es **una sola palabra** ([etiqueta]; si falta se usa [text], que entonces debe ser de una
 * palabra). [text] es además la descripción completa para TalkBack, así que puede ser más larga que [etiqueta].
 * Solo icono ([mostrarTexto] = false, también dentro de ventanas) se reserva a las excepciones: cantidad −/+,
 * adelante/atrás, filtro y exportar/compartir. `tools/verificacion/botones.py` lo comprueba en cada push.
 */

/**
 * Botón principal con icono + palabra (ver la regla de botones de arriba). Protegido contra doble pulsación.
 */
@Composable
fun ShalevPrimaryButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    mostrarTexto: Boolean = true,
    etiqueta: String? = null,
) {
    val accion = rememberAccionUnica(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val enVentana = LocalShalevVentana.current
    val dibujarTexto = mostrarTexto
    val visible = etiqueta ?: text
    Button(
        onClick = accion,
        modifier = modifier.formaBoton(dibujarTexto).feedbackPulsacion(interactionSource).semantics { contentDescription = text },
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(ShalevRadius.md),
        contentPadding = if (dibujarTexto) PaddingTexto else PaddingValues(0.dp),
    ) {
        if (dibujarTexto) EtiquetaBoton(visible, loading, if (enVentana) null else icon) else ContenidoIcono(icon, loading)
    }
}

/** Acción secundaria con icono + palabra (ver la regla de botones de arriba). */
@Composable
fun ShalevSecondaryButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    mostrarTexto: Boolean = true,
    etiqueta: String? = null,
) {
    val accion = rememberAccionUnica(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val enVentana = LocalShalevVentana.current
    val dibujarTexto = mostrarTexto
    val visible = etiqueta ?: text
    OutlinedButton(
        onClick = accion,
        modifier = modifier.formaBoton(dibujarTexto).feedbackPulsacion(interactionSource).semantics { contentDescription = text },
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(ShalevRadius.md),
        contentPadding = if (dibujarTexto) PaddingTexto else PaddingValues(0.dp),
        border = ButtonDefaults.outlinedButtonBorder(enabled),
    ) {
        if (dibujarTexto) EtiquetaBoton(visible, loading, if (enVentana) null else icon) else ContenidoIcono(icon, loading)
    }
}

/** Acción terciaria con icono + palabra (ver la regla de botones de arriba). */
@Composable
fun ShalevTextButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    mostrarTexto: Boolean = true,
    etiqueta: String? = null,
) {
    val accion = rememberAccionUnica(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val enVentana = LocalShalevVentana.current
    val dibujarTexto = mostrarTexto
    val visible = etiqueta ?: text
    TextButton(
        onClick = accion,
        modifier = modifier.formaBoton(dibujarTexto).feedbackPulsacion(interactionSource).semantics { contentDescription = text },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(ShalevRadius.md),
        contentPadding = if (dibujarTexto) PaddingTexto else PaddingValues(0.dp),
    ) {
        if (dibujarTexto) EtiquetaBoton(visible, loading = false, if (enVentana) null else icon) else Icon(icon, contentDescription = null, modifier = Modifier.size(ShalevSize.icon))
    }
}

/** En modo compacto conserva el indicador de carga; el texto está disponible para TalkBack en el botón. */
@Composable
private fun ContenidoIcono(icon: ImageVector, loading: Boolean) {
    if (loading) {
        // El botón ya anuncia su etiqueta; el indicador no la repite (si la repitiera, TalkBack la diría dos veces).
        CircularProgressIndicator(
            Modifier.size(ShalevSize.iconSmall),
            strokeWidth = 2.dp, color = LocalContentColor.current,
        )
    } else {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ShalevSize.icon))
    }
}

/**
 * Etiqueta de los botones: dentro de ventanas solo la palabra; fuera, [icono] + palabra (con el indicador de carga
 * en lugar del icono mientras trabaja).
 */
@Composable
private fun EtiquetaBoton(text: String, loading: Boolean, icono: ImageVector? = null) {
    if (loading) {
        // El botón ya anuncia su etiqueta; el indicador no la repite.
        CircularProgressIndicator(
            Modifier.size(ShalevSize.iconSmall),
            strokeWidth = 2.dp, color = LocalContentColor.current,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(ShalevSpacing.xs))
    } else if (icono != null) {
        Icon(icono, contentDescription = null, modifier = Modifier.size(ShalevSize.iconSmall))
        androidx.compose.foundation.layout.Spacer(Modifier.size(ShalevSpacing.xs))
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

/**
 * Palabra visible de un botón a partir de su descripción completa, que empieza por el verbo
 * («Cerrar turno» → «Cerrar»). Para textos que llegan como parámetro; los fijos llevan su `etiqueta` escrita.
 */
fun palabraBoton(descripcion: String): String = descripcion.trim().substringBefore(' ')

enum class IconActionStyle { Standard, Tonal, Filled }

/**
 * Acción con icono + palabra (ver la regla de botones de arriba): [etiqueta] es la palabra visible y [contentDescription] la
 * descripción completa para TalkBack. En ventanas emergentes solo se ve la palabra. [mostrarTexto] = false deja solo
 * el icono: reservado a cantidad −/+, adelante/atrás, filtro y exportar/compartir.
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
    mostrarTexto: Boolean = true,
    repetible: Boolean = false,
) {
    val accion = rememberAccionUnica(onClick, activa = !repetible)
    val interactionSource = remember { MutableInteractionSource() }
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when {
        selected -> cs.primary to cs.onPrimary
        containerColor != null -> containerColor to contentColorFor(containerColor).takeOrElse(Color.White)
        style == IconActionStyle.Filled -> cs.primary to cs.onPrimary
        // 0.21.1 (H2): el tonal usa el terciario (petróleo). El naranja (secondaryContainer) queda solo para avisos.
        style == IconActionStyle.Tonal -> cs.tertiaryContainer to cs.onTertiaryContainer
        else -> Color.Transparent to cs.onSurfaceVariant
    }
    val textoVisible = etiqueta ?: contentDescription
    val enVentana = LocalShalevVentana.current
    val dibujarTexto = mostrarTexto
    val base = modifier
        .formaBoton(dibujarTexto)
        .feedbackPulsacion(interactionSource)
        .semantics {
            this.contentDescription = contentDescription
            if (selected) this.selected = true
        }
    val relleno = if (dibujarTexto) PaddingTexto else PaddingValues(0.dp)
    val contenido: @Composable RowScope.() -> Unit = {
        if (dibujarTexto) {
            if (!enVentana) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(ShalevSize.iconSmall))
                androidx.compose.foundation.layout.Spacer(Modifier.size(ShalevSpacing.xxs))
            }
            EtiquetaAccion(textoVisible)
        } else Icon(icon, contentDescription = null, modifier = Modifier.size(ShalevSize.icon))
    }

    if (style == IconActionStyle.Standard && !selected && containerColor == null) {
        TextButton(
            onClick = accion,
            enabled = enabled,
            modifier = base,
            shape = RoundedCornerShape(ShalevRadius.md),
            contentPadding = relleno,
            interactionSource = interactionSource,
            colors = ButtonDefaults.textButtonColors(contentColor = fg),
            content = contenido,
        )
    } else {
        Button(
            onClick = accion,
            enabled = enabled,
            modifier = base,
            shape = RoundedCornerShape(ShalevRadius.md),
            contentPadding = relleno,
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            content = contenido,
        )
    }
}

@Composable
private fun EtiquetaAccion(texto: String) {
    // Una sola palabra: una sola línea (regla de botones).
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

// -----------------------------------------------------------------------------------------------------------
// Plan UI/UX/IX 2026-11 (SK-2): previews PRIVADAS Light/Dark solo en este módulo (:designsystem), para feedback
// rápido en el IDE. En :app no se escriben @Preview: la regresión visual la cubre Roborazzi (ver CAPTURAS.md),
// que sigue siendo la fuente de verdad de píxeles.

@Preview(name = "Botones — claro")
@Composable
private fun BotonesPreviewClaro() = BotonesPreview()

@Preview(name = "Botones — oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BotonesPreviewOscuro() = BotonesPreview()

@Composable
private fun BotonesPreview() {
    ShalevTheme {
        Surface {
            Column(Modifier.padding(ShalevSpacing.md)) {
                ShalevPrimaryButton("Guardar", onClick = {}, icon = ShalevIcons.Guardar, mostrarTexto = true)
                Spacer(Modifier.height(ShalevSpacing.xs))
                ShalevSecondaryButton("Cancelar", onClick = {}, icon = ShalevIcons.Cancelar, mostrarTexto = true)
                Spacer(Modifier.height(ShalevSpacing.xs))
                ShalevTextButton("Volver", onClick = {}, icon = ShalevIcons.Atras, mostrarTexto = true)
            }
        }
    }
}
