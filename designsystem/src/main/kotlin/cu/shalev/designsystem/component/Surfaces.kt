package cu.shalev.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import cu.shalev.designsystem.R
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.ilustracion.ShalevIlustracion
import cu.shalev.designsystem.ilustracion.ShalevIlustracionImagen
import cu.shalev.designsystem.theme.ShalevTextos
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.designsystem.token.ShalevElevation
import cu.shalev.designsystem.token.ShalevMotion
import cu.shalev.designsystem.token.ShalevOpacity
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

/**
 * Tono de la card: Default (superficie), Tonal (contenedor), Highlight (primaryContainer) o Error (fondo error y
 * contenido onError; 0.21.1, contador «Sin existencia»).
 */
enum class CardTone { Default, Tonal, Highlight, Error }

/**
 * Card estándar: radio 16dp, padding interno 16dp.
 * Claro: fondo blanco + sombra sutil (1dp). Oscuro: sin sombra; la elevación se ve por el tono más claro.
 * [titleCentered] = true para cards de gráficos (títulos centrados, Shalev.txt).
 */
@Composable
fun ShalevCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleCentered: Boolean = true,
    tone: CardTone = CardTone.Default,
    onClick: (() -> Unit)? = null,
    /** true = la card mide lo que mide su contenido (en vez de ocupar todo el ancho que le den). */
    ajustarAlContenido: Boolean = false,
    /**
     * 1.0.2: relleno vertical de la card. Por defecto 16dp; los contenedores de Inicio usan uno ligero (8dp)
     * para que la tarjeta quede ajustada al texto que contiene.
     */
    paddingVertical: Dp = ShalevSpacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val dark = ShalevTheme.colors.isDark
    val container = when (tone) {
        CardTone.Default -> cs.surface
        CardTone.Tonal -> if (dark) cs.surfaceContainerHigh else cs.surfaceContainer
        CardTone.Highlight -> cs.primaryContainer
        CardTone.Error -> cs.error
    }
    val shadow = if (dark || tone != CardTone.Default) ShalevElevation.none else ShalevElevation.low
    val shape = RoundedCornerShape(ShalevRadius.lg)
    val body: @Composable () -> Unit = {
        // P28: lo que va solo en su fila (título, texto de ayuda, botón, grupo de chips) se centra. Las filas de
        // tabla (ShalevListItem) y los campos a todo el ancho no cambian.
        Column(
            Modifier.padding(horizontal = ShalevSpacing.md, vertical = paddingVertical),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CompositionLocalProvider(LocalShalevCentrado provides true) {
                title?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = if (titleCentered) TextAlign.Center else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().semantics { heading() },
                    )
                }
                content()
            }
        }
    }
    val medida = if (ajustarAlContenido) modifier else modifier.fillMaxWidth()
    if (onClick != null) {
        Surface(onClick = onClick, modifier = medida, shape = shape, color = container, shadowElevation = shadow, content = body)
    } else {
        Surface(modifier = medida, shape = shape, color = container, shadowElevation = shadow, content = body)
    }
}

/**
 * Card acordeón: la cabecera (título centrado + flecha) se toca para abrir o cerrar el contenido. El estado lo lleva
 * quien la usa ([expanded]/[onToggle]) para poder recordarlo fuera de una lista que recicla sus filas. La cabecera mide
 * como mínimo 48 dp, avisa a TalkBack de si está expandida y no cambia de tamaño con la letra grande.
 */
@Composable
fun ShalevAccordionCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Default,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val giro by animateFloatAsState(if (expanded) 180f else 0f, ShalevMotion.tween(ShalevMotion.SHORT), label = "acordeon")
    ShalevCard(modifier = modifier, tone = tone) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ShalevSize.touchTarget)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics(mergeDescendants = true) {
                    heading()
                    stateDescription = if (expanded) "Expandido" else "Contraído"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(ShalevSize.icon))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                // El resumen es «de un vistazo»: al abrir, el contenido ya muestra el dato y no se repite.
                if (!expanded) subtitle?.let { ShalevSecondaryText(it, modifier = Modifier.fillMaxWidth(), maxLines = 1, textAlign = TextAlign.Center) }
            }
            Icon(ShalevIcons.Desplegar, contentDescription = null, modifier = Modifier.size(ShalevSize.icon).rotate(giro))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(ShalevMotion.tween(ShalevMotion.SHORT)) + fadeIn(ShalevMotion.tween(ShalevMotion.SHORT)),
            exit = shrinkVertically(ShalevMotion.tween(ShalevMotion.SHORT)) + fadeOut(ShalevMotion.tween(ShalevMotion.FAST)),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

/**
 * Elemento de lista: indicador visual (barra de color) + nombre/subtítulo + dato principal alineado a la derecha.
 * Espaciado vertical compacto (8dp) y altura mínima táctil de 48dp.
 *
 * P18 (A02), letra grande:
 * - El título admite 2 líneas.
 * - El valor ocupa como mucho el 45 % del ancho y se corta con «…»: así nunca empuja el nombre fuera.
 * - Con letra grande del sistema ([letraGrande]), el valor pasa **debajo** del título y se ve entero.
 * - [valueMaxLines] / [subtitleMaxLines] (0.16.1): textos largos que deben leerse enteros («Sin insumos suficientes»,
 *   «400.00 CUP c/u · Alcanza para 15») pasan a 2 líneas en vez de cortarse. Por defecto 1: el resto no cambia.
 * - 0.26.0 (P73 §5): el valor usa [ShalevTextos.dato] (seminegrita). [tituloEsDato]: el título también es un dato
 *   (la fecha de las filas de Registros y Turnos) y va en seminegrita.
 */
@Composable
fun ShalevListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color = Color.Unspecified,
    indicatorColor: Color? = MaterialTheme.colorScheme.primary,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    valueMaxLines: Int = 1,
    subtitleMaxLines: Int = 3, // 0.27.0 (T2, capturas): con 1 línea cortaba «Vincula las apps de tus empl…»
    tituloEsDato: Boolean = false,
    /** 0.28.0: subtítulo con importes/fechas en seminegrita (ver [ShalevTextos.resaltar]); si no es null, vale este. */
    subtitleResaltado: AnnotatedString? = null,
    /** 0.28.0: como [subtitleResaltado] pero para el título («Turno abierto desde las 09:30»). */
    titleResaltado: AnnotatedString? = null,
) {
    val cs = MaterialTheme.colorScheme
    val apilar = letraGrande()
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    CompositionLocalProvider(LocalShalevCentrado provides false) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(if (selected) cs.primaryContainer else Color.Transparent)
                .then(clickable)
                .semantics(mergeDescendants = true) { if (selected) this.selected = true }
                .heightIn(min = ShalevSize.touchTarget)
                .padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        ) {
            indicatorColor?.let {
                Box(
                    Modifier.size(ShalevSize.indicatorWidth, ShalevSize.indicatorHeight)
                        .clip(RoundedCornerShape(ShalevRadius.sm))
                        .background(it),
                )
            }
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                if (titleResaltado != null) Text(
                    titleResaltado, style = MaterialTheme.typography.bodyLarge,
                )
                else Text(
                    title, style = if (tituloEsDato) ShalevTextos.datoEn(MaterialTheme.typography.bodyLarge) else MaterialTheme.typography.bodyLarge,
                    // 0.30.1: el título nunca se corta con «…»: con nombres largos o letra al 200 % pasa a otra línea.
                )
                if (subtitleResaltado != null) ShalevSecondaryText(subtitleResaltado, maxLines = if (apilar) maxOf(2, subtitleMaxLines) else subtitleMaxLines)
                else subtitle?.split(" · ", "\n")?.filter { it.isNotBlank() }?.forEach { linea ->
                    ShalevSecondaryText(linea, maxLines = if (apilar) maxOf(2, subtitleMaxLines) else subtitleMaxLines)
                }
                if (apilar) value?.let { Text(it, style = ShalevTextos.dato, color = valueColor) }
            }
            if (!apilar) {
                value?.let {
                    // P24: los importes no se cortan con «…»: primero se reduce un poco la letra (ShalevTextoAjustable).
                    ShalevTextoAjustable(
                        it, style = ShalevTextos.dato, color = valueColor, textAlign = TextAlign.End,
                        maxLines = valueMaxLines, modifier = Modifier.anchoMaximo(VALOR_MAX),
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/** Fracción máxima del ancho disponible para el valor de [ShalevListItem] en una fila. */
private const val VALOR_MAX = 0.45f

/** Limita el ancho a una fracción del disponible (sin `BoxWithConstraints`: una sola medida). */
private fun Modifier.anchoMaximo(fraccion: Float): Modifier = layout { medible, c ->
    val max = if (c.hasBoundedWidth) (c.maxWidth * fraccion).toInt().coerceAtLeast(c.minWidth) else c.maxWidth
    val p = medible.measure(c.copy(maxWidth = max))
    layout(p.width, p.height) { p.place(0, 0) }
}

/**
 * P28: `true` dentro de tarjetas y ventanas emergentes. [ShalevSecondaryText] sin `textAlign` explícito se centra ahí
 * (texto suelto que ocupa su fila). [ShalevListItem] lo vuelve a `false` para sus subtítulos.
 */
val LocalShalevCentrado = compositionLocalOf { false }

/** Texto secundario: bodySmall con 65 % de opacidad (verificado ≥4.5:1 en ambos temas). */
@Composable
fun ShalevSecondaryText(text: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, textAlign: TextAlign? = null) =
    ShalevSecondaryText(AnnotatedString(text), modifier, maxLines, textAlign)

/** 0.28.0: igual que el anterior pero con partes en seminegrita (ver [ShalevTextos.resaltar]). */
@Composable
fun ShalevSecondaryText(text: AnnotatedString, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, textAlign: TextAlign? = null) {
    val alineacion = textAlign ?: if (LocalShalevCentrado.current) TextAlign.Center else null
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = ShalevOpacity.secondary),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = alineacion,
    )
}

/**
 * SVIPicono.png: bienvenida y Soporte. Decorativo por defecto.
 * 0.27.0 (N3): fondo transparente (solo la figura, como el icono del lanzador), sin recorte redondeado.
 */
@Composable
fun ShalevLogo(size: Dp = ShalevSize.logoSmall, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(
        painter = painterResource(R.drawable.shalev_logo),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

/**
 * Estado vacío, sin resultados o error.
 * P18 (A23): con [ayuda], muestra el botón «Ayuda», que abre el tema correspondiente.
 * P24: [ilustracion] muestra una ilustración acorde al contexto (unDraw, ver [ShalevIlustracion]) en lugar del logo.
 * La acción y la ayuda van juntas, centradas y separadas 16dp (proximidad).
 */
@Composable
fun ShalevEmptyState(
    title: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
    ilustracion: ShalevIlustracion? = null,
    ayuda: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(ShalevSpacing.lg), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = ShalevSize.contentMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            if (ilustracion != null) {
                ShalevIlustracionImagen(ilustracion, Modifier.padding(bottom = ShalevSpacing.xs))
            } else {
                ShalevLogo(ShalevSize.logoLarge)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            detail?.let { ShalevSecondaryText(it, textAlign = TextAlign.Center) }
            if (action != null || ayuda != null) {
                Row(
                    Modifier.padding(top = ShalevSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    action?.invoke()
                    ayuda?.let { ShalevIconAction(ShalevIcons.Ayuda, "Ayuda", onClick = it) }
                }
            }
        }
    }
}

/**
 * P18 (A18): animación común de las filas de listas perezosas (aparecer, desaparecer y reordenarse) con los tiempos
 * de [ShalevMotion]. Úsese en `items(…, key = …)`: sin clave estable no hay animación.
 */
fun LazyItemScope.shalevAnimateItem(): Modifier =
    Modifier.animateItem(fadeInSpec = ShalevMotion.listFade, placementSpec = ShalevMotion.listPlacement, fadeOutSpec = ShalevMotion.listFade)

/**
 * P18 (A22): en tablets o en horizontal, los formularios no se estiran más de [ShalevSize.contentMaxWidth] y quedan
 * centrados (líneas de lectura cómodas). Úsese justo después de `fillMaxSize()`/`fillMaxWidth()`.
 */
fun Modifier.shalevContentWidth(): Modifier =
    this.wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = ShalevSize.contentMaxWidth).fillMaxWidth()
