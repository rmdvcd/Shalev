package cu.shalev.designsystem.component

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.tooling.preview.Preview
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
 * Si es pulsable, una doble pulsación seguida solo ejecuta la acción una vez (ver [GuardiaDobleToque]).
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
    val accionSegura = rememberAccionUnica(onClick = { onClick?.invoke() })
    if (onClick != null) {
        Surface(onClick = accionSegura, modifier = medida, shape = shape, color = container, shadowElevation = shadow, content = body)
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
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val alternar = rememberAccionUnica(onToggle)
    val giro by animateFloatAsState(if (expanded) 180f else 0f, ShalevMotion.tween(ShalevMotion.SHORT), label = "acordeon")
    ShalevCard(modifier = modifier, tone = tone) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ShalevSize.touchTarget)
                .clickable(role = Role.Button, onClick = alternar)
                .semantics(mergeDescendants = true) {
                    heading()
                    stateDescription = if (expanded) "Expandido" else "Contraído"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(ShalevSize.icon))
            else Spacer(Modifier.size(ShalevSize.icon))
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
 * Estado abierto/cerrado de los paneles informativos (acordeones de solo lectura) de una pantalla, indexado por clave.
 * Cada panel nace en su valor por defecto ([abiertosPorDefecto]: los que importan de entrada, como el estado de la
 * licencia; el resto, cerrado) y el usuario lo abre o cierra tocando su cabecera.
 *
 * No se persiste entre visitas: al volver a la pantalla los paneles vuelven a su defecto (misma decisión que los
 * acordeones de Inicio). La lógica es probada en JVM (la animación y el render los pone [ShalevAccordionCard]).
 */
@Stable
class PanelesInformativos(private val abiertosPorDefecto: Set<String> = emptySet()) {
    private val estados = mutableStateMapOf<String, Boolean>()

    fun abierto(clave: String): Boolean = estados[clave] ?: (clave in abiertosPorDefecto)

    fun alternar(clave: String) {
        estados[clave] = !abierto(clave)
    }

    fun abrir(clave: String) {
        estados[clave] = true
    }

    fun colapsar(clave: String) {
        estados[clave] = false
    }

    /** Cierra todos los paneles conocidos (tocados o abiertos por defecto): libera la pantalla para escribir o actuar. */
    fun colapsarTodos() {
        (estados.keys + abiertosPorDefecto).forEach { estados[it] = false }
    }
}

@Composable
fun rememberPanelesInformativos(vararg abiertosPorDefecto: String): PanelesInformativos =
    remember { PanelesInformativos(abiertosPorDefecto.toSet()) }

/**
 * Auto-colapso de los paneles informativos: cuando el teclado aparece (la persona va a escribir en un campo y
 * necesita verlo, junto con los botones de acción), los paneles fijos se cierran solos dejando visible solo su
 * título y resumen. Una sola llamada por pantalla, junto a la declaración de los paneles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoColapsarPanelesAlEscribir(paneles: PanelesInformativos) {
    val tecladoVisible = WindowInsets.isImeVisible
    LaunchedEffect(tecladoVisible) {
        if (tecladoVisible) paneles.colapsarTodos()
    }
}

/**
 * Elemento de lista: indicador visual (barra de color) + nombre/subtítulo + dato principal alineado a la derecha.
 * Espaciado vertical compacto (8dp) y altura mínima táctil de 48dp.
 *
 * P18 (A02), letra grande:
 * - El título usa hasta [titleMaxLines] líneas (2 por defecto) y el valor ocupa como mucho el 45 % del ancho en letra normal.
 * - Con letra grande del sistema ([letraGrande]), el valor pasa **debajo** del título.
 * - La suma de título, subtítulo y valor apilado no supera **3 líneas**; los textos largos se recortan visualmente con «…».
 * - [valueMaxLines] / [subtitleMaxLines] permiten repartir hasta 3 líneas entre los datos visibles.
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
    subtitleMaxLines: Int = 1,
    tituloEsDato: Boolean = false,
    /** 0.28.0: subtítulo con importes/fechas en seminegrita (ver [ShalevTextos.resaltar]); si no es null, vale este. */
    subtitleResaltado: AnnotatedString? = null,
    /** 0.28.0: como [subtitleResaltado] pero para el título («Turno abierto desde las 09:30»). */
    titleResaltado: AnnotatedString? = null,
    titleMaxLines: Int = 2,
) {
    val cs = MaterialTheme.colorScheme
    val apilar = letraGrande()
    val haySubtitulo = subtitleResaltado != null || !subtitle.isNullOrBlank()
    val lineasValorApilado = if (apilar && value != null) 1 else 0
    val lineasSubtituloSolicitadas = if (haySubtitulo) subtitleMaxLines.coerceAtLeast(1) else 0
    // Cada fila limita el bloque de texto a tres líneas, también al apilar el valor con letra grande.
    val lineasTitulo = (3 - lineasValorApilado - lineasSubtituloSolicitadas)
        .coerceAtLeast(1).coerceAtMost(titleMaxLines.coerceIn(1, 3))
    val lineasSubtitulo = if (haySubtitulo) {
        lineasSubtituloSolicitadas.coerceAtMost((3 - lineasValorApilado - lineasTitulo).coerceAtLeast(1))
    } else 0
    val textoSubtitulo = subtitle?.split(" · ", "\n")?.filter { it.isNotBlank() }?.joinToString(" · ")
    // Una doble pulsación en una fila (p. ej. para abrir una ficha) solo actúa una vez.
    val accionSegura = rememberAccionUnica(onClick = { onClick?.invoke() })
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = accionSegura) else Modifier
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
                    titleResaltado,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = lineasTitulo,
                    overflow = TextOverflow.Ellipsis,
                )
                else Text(
                    title,
                    style = if (tituloEsDato) ShalevTextos.datoEn(MaterialTheme.typography.bodyLarge) else MaterialTheme.typography.bodyLarge,
                    maxLines = lineasTitulo,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitleResaltado != null) {
                    ShalevSecondaryText(subtitleResaltado, maxLines = lineasSubtitulo)
                } else if (!textoSubtitulo.isNullOrBlank()) {
                    ShalevSecondaryText(textoSubtitulo, maxLines = lineasSubtitulo)
                }
                if (apilar) value?.let {
                    Text(it, style = ShalevTextos.dato, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!apilar) {
                value?.let {
                    // P24: los importes no se cortan con «…»: primero se reduce un poco la letra (ShalevTextoAjustable).
                    ShalevTextoAjustable(
                        it, style = ShalevTextos.dato, color = valueColor, textAlign = TextAlign.End,
                        maxLines = valueMaxLines.coerceIn(1, 3), modifier = Modifier.anchoMaximo(VALOR_MAX),
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

// -----------------------------------------------------------------------------------------------------------
// Plan UI/UX/IX 2026-11 (SK-2): previews PRIVADAS Light/Dark solo en :designsystem; Roborazzi sigue siendo la
// fuente de verdad de la regresión visual (ver CAPTURAS.md).

@Preview(name = "Cards — claro")
@Composable
private fun CardsPreviewClaro() = CardsPreview()

@Preview(name = "Cards — oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CardsPreviewOscuro() = CardsPreview()

@Composable
private fun CardsPreview() {
    ShalevTheme {
        Surface {
            Column(Modifier.padding(ShalevSpacing.md)) {
                ShalevCard(title = "Ventas de hoy") {
                    Text("1,250 CUP")
                    ShalevSecondaryText("12 ventas en el turno")
                }
            }
        }
    }
}

@Preview(name = "Vacío — claro", showBackground = true)
@Composable
private fun VacioPreviewClaro() = VacioPreview()

@Preview(name = "Vacío — oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun VacioPreviewOscuro() = VacioPreview()

@Composable
private fun VacioPreview() {
    ShalevTheme {
        ShalevEmptyState(
            title = "Sin productos todavía",
            detail = "Agrega el primero para empezar a vender.",
        )
    }
}
