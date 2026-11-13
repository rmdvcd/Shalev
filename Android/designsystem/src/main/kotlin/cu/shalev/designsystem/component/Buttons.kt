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
 * ([ShalevFontScale.GRANDE]) se quita el máximo: la etiqueta puede ocupar 2 líneas y nunca se recorta (WCAG 1.4.4).
 * P24: los botones solo icono miden siempre 48dp de alto (área táctil completa).
 */
@Composable
private fun Modifier.alturaBoton(soloIcono: Boolean): Modifier = when {
    soloIcono -> heightIn(min = ShalevSize.touchTarget).widthIn(min = ShalevSize.touchTarget)
    letraGrande() -> heightIn(min = ShalevSize.buttonMin)
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
 * En ventanas emergentes y cuadros de diálogo los botones son siempre solo icono (1.0.3):
 * no se conservan etiquetas escritas. La etiqueta textual se usa como `contentDescription` para TalkBack.
 */
val LocalShalevVentana = compositionLocalOf { false }

private fun String.textoConservadoEnVentana(): String? = null

@Composable
private fun Modifier.formaBoton(mostrarTexto: Boolean): Modifier =
    if (mostrarTexto) alturaBoton(soloIcono = false) else size(ShalevSize.touchTarget)

/**
 * Botón principal con icono visible y etiqueta accesible. [mostrarTexto] permite conservar una etiqueta cuando hace
 * falta (fuera de diálogos/ventanas); dentro de diálogos/ventanas todos los botones son solo icono (1.0.3).
 * Una doble pulsación seguida solo ejecuta la acción una vez (ver [GuardiaDobleToque]).
 */
@Composable
fun ShalevPrimaryButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    mostrarTexto: Boolean = false,
) {
    val accion = rememberAccionUnica(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val textoVentana = if (LocalShalevVentana.current) text.textoConservadoEnVentana() else null
    val dibujarTexto = mostrarTexto || textoVentana != null
    Button(
        onClick = accion,
        modifier = modifier.formaBoton(dibujarTexto).feedbackPulsacion(interactionSource).semantics { contentDescription = text },
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(ShalevRadius.md),
        contentPadding = if (dibujarTexto) PaddingTexto else PaddingValues(0.dp),
    ) {
        if (dibujarTexto) EtiquetaBoton(textoVentana ?: text, loading) else ContenidoIcono(icon, text, loading)
    }
}

/** Acción secundaria con icono visible (o etiqueta si se solicita en un diálogo). Protegida contra la doble pulsación. */
@Composable
fun ShalevSecondaryButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    mostrarTexto: Boolean = false,
) {
    val accion = rememberAccionUnica(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val textoVentana = if (LocalShalevVentana.current) text.textoConservadoEnVentana() else null
    val dibujarTexto = mostrarTexto || textoVentana != null
    OutlinedButton(
        onClick = accion,
        modifier = modifier.formaBoton(dibujarTexto).feedbackPulsacion(interactionSource).semantics { contentDescription = text },
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(ShalevRadius.md),
        contentPadding = if (dibujarTexto) PaddingTexto else PaddingValues(0.dp),
        border = ButtonDefaults.outlinedButtonBorder(enabled),
    ) {
        if (dibujarTexto) EtiquetaBoton(textoVentana ?: text, loading) else ContenidoIcono(icon, text, loading)
    }
}

/** Acción terciaria con icono visible y etiqueta accesible. Protegida contra la doble pulsación. */
@Composable
fun ShalevTextButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    mostrarTexto: Boolean = false,
) {
    val accion = rememberAccionUnica(onClick)
    val interactionSource = remember { MutableInteractionSource() }
    val textoVentana = if (LocalShalevVentana.current) text.textoConservadoEnVentana() else null
    val dibujarTexto = mostrarTexto || textoVentana != null
    TextButton(
        onClick = accion,
        modifier = modifier.formaBoton(dibujarTexto).feedbackPulsacion(interactionSource).semantics { contentDescription = text },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(ShalevRadius.md),
        contentPadding = if (dibujarTexto) PaddingTexto else PaddingValues(0.dp),
    ) {
        if (dibujarTexto) EtiquetaBoton(textoVentana ?: text, loading = false) else Icon(icon, contentDescription = null, modifier = Modifier.size(ShalevSize.icon))
    }
}

/** En modo compacto conserva el indicador de carga; el texto está disponible para TalkBack en el botón. */
@Composable
private fun ContenidoIcono(icon: ImageVector, descripcion: String, loading: Boolean) {
    if (loading) {
        CircularProgressIndicator(
            Modifier.size(ShalevSize.iconSmall).semantics { contentDescription = descripcion },
            strokeWidth = 2.dp, color = LocalContentColor.current,
        )
    } else {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ShalevSize.icon))
    }
}

/** Etiqueta común de los botones de diálogo cuando se conserva texto. */
@Composable
private fun EtiquetaBoton(text: String, loading: Boolean) {
    if (loading) {
        CircularProgressIndicator(
            Modifier.size(ShalevSize.iconSmall).semantics { contentDescription = text },
            strokeWidth = 2.dp, color = LocalContentColor.current,
        )
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

enum class IconActionStyle { Standard, Tonal, Filled }

/**
 * Acción de icono con área táctil mínima de 48dp y una descripción accesible completa.
 * En diálogos y ventanas emergentes conserva las etiquetas Confirmar, Eliminar, Omitir y Cancelar.
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
    mostrarTexto: Boolean = false,
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
    val texto = etiqueta ?: contentDescription
    val textoVentana = if (LocalShalevVentana.current) {
        contentDescription.textoConservadoEnVentana() ?: etiqueta?.textoConservadoEnVentana()
    } else null
    val textoVisible = textoVentana ?: texto
    val dibujarTexto = mostrarTexto || textoVentana != null
    val base = modifier
        .formaBoton(dibujarTexto)
        .feedbackPulsacion(interactionSource)
        .semantics {
            this.contentDescription = contentDescription
            if (selected) this.selected = true
        }
    val relleno = if (dibujarTexto) PaddingTexto else PaddingValues(0.dp)
    val contenido: @Composable RowScope.() -> Unit = {
        if (dibujarTexto) EtiquetaAccion(textoVisible)
        else Icon(icon, contentDescription = null, modifier = Modifier.size(ShalevSize.icon))
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
