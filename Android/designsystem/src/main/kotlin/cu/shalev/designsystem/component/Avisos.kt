package cu.shalev.designsystem.component

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.LocalShalevColors
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.designsystem.token.ColorTokens
import cu.shalev.designsystem.token.ShalevElevation
import cu.shalev.designsystem.token.ShalevMotion
import cu.shalev.designsystem.token.ShalevOpacity
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.delay

/** Etiquetas de prueba de la isla de avisos. */
object AvisosTags {
    const val ISLA = "avisos_isla"
    const val PILDORA = "avisos_pildora"
    const val TARJETA = "avisos_tarjeta"
    const val INSIGNIA = "avisos_insignia"
    const val ACCION = "avisos_accion"
    const val CERRAR = "avisos_cerrar"
    const val ANTERIOR = "avisos_anterior"
    const val SIGUIENTE = "avisos_siguiente"
    const val PAGINA = "avisos_pagina"
    const val LIMPIAR = "avisos_limpiar"
}

/** Textos fijos de la isla (los botones siguen la regla: una palabra visible + descripción completa). */
object TextosAvisos {
    const val ABRIR = "Abrir los avisos"
    const val CONTRAER = "Contraer los avisos"
    const val CERRAR = "Cerrar"
    const val CERRAR_DESCRIPCION = "Cerrar este aviso"
    const val ANTERIOR = "Aviso anterior"
    const val SIGUIENTE = "Aviso siguiente"
    const val LIMPIAR = "Limpiar"
    const val LIMPIAR_DESCRIPCION = "Limpiar todos los avisos"
    const val BORRAR = "Borrar"

    fun borrarDescripcion(n: Int) = if (n == 1) "Confirmar: borrar 1 aviso" else "Confirmar: borrar los $n avisos"
    fun pagina(i: Int, n: Int) = "${i + 1} de $n"
    fun insignia(n: Int) = if (n > 99) "99+" else n.toString()
    fun insigniaDescripcion(n: Int) = if (n == 1) "1 aviso" else "$n avisos"
    fun perdidos(n: Int) = if (n == 1) "1 aviso antiguo no cupo y se descartó." else "$n avisos antiguos no cupieron y se descartaron."
}

/** Prioridad del anfitrión: la isla de respaldo de MainScaffold cede ante la de cualquier pantalla. */
const val PRIORIDAD_AVISOS_RESPALDO = 0
const val PRIORIDAD_AVISOS_PANTALLA = 1

private val RADIO_ISLA = 24.dp
private val ANCHO_ISLA_MAX = 480.dp
private val TAMANO_ICONO_AVISO = 20.dp
private val NUMEROS_TABULARES = TextStyle(fontFeatureSettings = "tnum")

/**
 * Anfitrión de la isla de avisos en el `snackbarHost` del Scaffold de cada pantalla (abajo, sobre la barra inferior y
 * el FAB: la posición de Android en Notificacion.md §5.2). Como la isla es compartida, varias pantallas pueden tener
 * un anfitrión compuesto a la vez (durante una transición): solo pinta el de mayor [prioridad] y, a igualdad, el
 * último que entró; así nunca hay dos islas ni dos anuncios.
 */
@Composable
fun ShalevSnackbarHost(
    state: ShalevAvisosState,
    modifier: Modifier = Modifier,
    prioridad: Int = PRIORIDAD_AVISOS_PANTALLA,
) {
    val token = remember { Any() }
    DisposableEffect(state, token) {
        state.registrar(token, prioridad)
        onDispose { state.retirar(token) }
    }
    if (state.anfitrionActivo === token) ShalevIslaAvisos(state, modifier)
}

/**
 * La isla: en reposo, una píldora de una línea con el icono del tipo, el mensaje, «×N» si se repitió y la insignia
 * roja con los acumulados. Al tocarla se transforma (la misma forma crece con un muelle interrumpible) en una tarjeta
 * con la descripción, la acción, «Cerrar», el paginador «1 de N» y «Limpiar» en dos pasos.
 *
 * - Deslizar a un lado cierra el aviso que se ve (con TalkBack: acción «Cerrar aviso»).
 * - Mientras se toca, se arrastra o la tarjeta está abierta, nada se va solo; luego sigue con el tiempo que quedaba.
 * - El tiempo de cada aviso respeta «Tiempo para actuar» de Accesibilidad del sistema.
 * - La región viva está montada antes de que llegue el mensaje: «polite» para éxito/información/carga y «assertive»
 *   para advertencia y error.
 * - Con «Quitar animaciones» del sistema, Compose lleva los muelles a su estado final sin recorrido.
 */
@Composable
fun ShalevIslaAvisos(state: ShalevAvisosState, modifier: Modifier = Modifier) {
    val haptics = rememberShalevHaptics()
    val nuevo = state.avisos.firstOrNull()
    LaunchedEffect(nuevo?.id, nuevo?.version, nuevo?.tipo) {
        if (nuevo != null && state.marcarAvisado(nuevo)) when (nuevo.tipo) {
            TipoAviso.EXITO -> haptics.exito()
            TipoAviso.ERROR -> haptics.error()
            else -> haptics.seleccion()
        }
    }

    val toque = remember { MutableInteractionSource() }
    val arrastre = remember { MutableInteractionSource() }
    val presionado by toque.collectIsPressedAsState()
    val arrastrando by arrastre.collectIsDraggedAsState()
    val pausado = state.abierta || presionado || arrastrando
    state.avisos.forEach { a -> key(a.id) { Temporizador(state, a, pausado) } }

    val actual = state.actual
    // Durante la salida (ya sin avisos) se sigue pintando el último para que se vaya con su forma.
    val memoria = remember { arrayOfNulls<Aviso>(1) }
    if (actual != null) memoria[0] = actual
    val mostrado = actual ?: memoria[0]
    val urgente = actual?.tipo?.urgente == true

    Box(
        modifier
            .fillMaxWidth()
            .semantics { liveRegion = if (urgente) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = actual != null,
            enter = slideInVertically(ShalevMotion.muelleEntrada()) { it } +
                fadeIn(ShalevMotion.tween(ShalevMotion.MEDIUM)),
            exit = slideOutVertically(ShalevMotion.muelle()) { it } + fadeOut(ShalevMotion.tween(ShalevMotion.SHORT)),
        ) {
            if (mostrado != null) Isla(state, mostrado, toque, arrastre)
        }
    }
}

/** Tiempo de un aviso que se va solo. Se pausa con [pausado] y reanuda con lo que le quedaba. */
@Composable
private fun Temporizador(state: ShalevAvisosState, aviso: Aviso, pausado: Boolean) {
    val base = aviso.tiempoBaseMs ?: return
    val accesibilidad = LocalAccessibilityManager.current
    val conAccion = aviso.accion != null
    val total = remember(base, conAccion, accesibilidad) {
        accesibilidad?.calculateRecommendedTimeoutMillis(
            base, containsIcons = true, containsText = true, containsControls = conAccion,
        ) ?: base
    }
    val restante = remember(aviso.version, total) { longArrayOf(total) }
    LaunchedEffect(aviso.version, total, pausado) {
        if (pausado) return@LaunchedEffect
        val inicio = System.nanoTime()
        try {
            delay(restante[0])
            state.cerrar(aviso)
        } finally {
            restante[0] = (restante[0] - (System.nanoTime() - inicio) / 1_000_000).coerceAtLeast(0)
        }
    }
}

@Composable
private fun Isla(
    state: ShalevAvisosState,
    aviso: Aviso,
    toque: MutableInteractionSource,
    arrastre: MutableInteractionSource,
) {
    val cs = MaterialTheme.colorScheme
    val oscuro = LocalShalevColors.current.isDark
    // El desplazamiento sigue al dedo de forma síncrona (sin lanzar corrutinas por cada delta, que podrían cancelar la
    // animación de salida); al soltar, se decide y el aviso se cierra aunque la animación se interrumpa.
    var desplazamiento by remember(aviso.id) { mutableFloatStateOf(0f) }
    val umbral = with(LocalDensity.current) { 96.dp.toPx() }
    Surface(
        modifier = Modifier
            .padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs)
            .offset { IntOffset(desplazamiento.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(desplazamiento) / (umbral * 2)).coerceIn(0f, 0.6f) }
            .draggable(
                state = rememberDraggableState { d -> desplazamiento += d },
                orientation = Orientation.Horizontal,
                interactionSource = arrastre,
                onDragStopped = { velocidad ->
                    if (abs(desplazamiento) > umbral || abs(velocidad) > umbral * 8) {
                        val lado = if (desplazamiento != 0f) sign(desplazamiento) else sign(velocidad)
                        try {
                            animate(desplazamiento, lado * umbral * 4, animationSpec = ShalevMotion.muelle()) { v, _ ->
                                desplazamiento = v
                            }
                        } finally {
                            state.cerrar(aviso)
                        }
                    } else {
                        animate(desplazamiento, 0f, animationSpec = ShalevMotion.muelle()) { v, _ -> desplazamiento = v }
                    }
                },
            )
            .widthIn(max = ANCHO_ISLA_MAX)
            .then(if (state.abierta) Modifier.fillMaxWidth() else Modifier)
            // Píldora → tarjeta: la misma forma crece con un muelle sin rebote (interrumpible).
            .animateContentSize(ShalevMotion.muelle())
            .testTag(AvisosTags.ISLA),
        shape = RoundedCornerShape(RADIO_ISLA),
        color = cs.inverseSurface,
        contentColor = cs.inverseOnSurface,
        // En oscuro la elevación se expresa con la superficie clara, no con sombras negras (Notificacion.md §8).
        shadowElevation = if (oscuro) ShalevElevation.none else ShalevElevation.mid,
    ) {
        MaterialTheme(colorScheme = cs.invertido()) {
            if (state.abierta) Tarjeta(state, aviso) else Pildora(state, aviso, toque)
        }
    }
}

@Composable
private fun Pildora(state: ShalevAvisosState, aviso: Aviso, toque: MutableInteractionSource) {
    Row(
        Modifier
            .heightIn(min = ShalevSize.touchTarget)
            .clickable(
                interactionSource = toque,
                indication = LocalIndication.current,
                onClickLabel = TextosAvisos.ABRIR,
                role = Role.Button,
            ) { state.abrir() }
            .semantics(mergeDescendants = true) {
                customActions = listOf(CustomAccessibilityAction(TextosAvisos.CERRAR_DESCRIPCION) { state.cerrar(aviso); true })
            }
            .padding(horizontal = ShalevSpacing.md, vertical = 10.dp)
            .testTag(AvisosTags.PILDORA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
    ) {
        IconoAviso(aviso.tipo)
        Text(
            aviso.titulo,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Repeticiones(aviso.repeticiones)
        if (state.contador > 1) Insignia(state.contador)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tarjeta(state: ShalevAvisosState, aviso: Aviso) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.padding(ShalevSpacing.md).testTag(AvisosTags.TARJETA),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ShalevSize.touchTarget)
                .clickable(onClickLabel = TextosAvisos.CONTRAER, role = Role.Button) { state.contraer() }
                .semantics(mergeDescendants = true) { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            IconoAviso(aviso.tipo)
            Text(aviso.titulo, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Repeticiones(aviso.repeticiones)
        }
        aviso.descripcion?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = ShalevOpacity.secondary + 0.2f),
            )
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs, Alignment.End),
        ) {
            aviso.accion?.let { accion ->
                ShalevIconAction(
                    icon = iconoAccion(accion),
                    contentDescription = accion,
                    etiqueta = palabraBoton(accion),
                    onClick = { state.accionar(aviso) },
                    style = IconActionStyle.Filled,
                    modifier = Modifier.testTag(AvisosTags.ACCION),
                )
            }
            ShalevIconAction(
                icon = ShalevIcons.Cancelar,
                contentDescription = TextosAvisos.CERRAR_DESCRIPCION,
                etiqueta = TextosAvisos.CERRAR,
                onClick = { state.cerrar(aviso) },
                modifier = Modifier.testTag(AvisosTags.CERRAR),
            )
        }
        if (state.total > 1 || state.perdidos > 0) {
            HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.2f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (state.total > 1) {
                    ShalevIconAction(
                        icon = ShalevIcons.Atras,
                        contentDescription = TextosAvisos.ANTERIOR,
                        onClick = state::anterior,
                        enabled = state.indice > 0,
                        mostrarTexto = false,
                        repetible = true,
                        modifier = Modifier.testTag(AvisosTags.ANTERIOR),
                    )
                    Text(
                        TextosAvisos.pagina(state.indice, state.total),
                        style = MaterialTheme.typography.labelLarge.merge(NUMEROS_TABULARES),
                        modifier = Modifier.testTag(AvisosTags.PAGINA),
                    )
                    ShalevIconAction(
                        icon = ShalevIcons.Siguiente,
                        contentDescription = TextosAvisos.SIGUIENTE,
                        onClick = state::siguiente,
                        enabled = state.indice < state.total - 1,
                        mostrarTexto = false,
                        repetible = true,
                        modifier = Modifier.testTag(AvisosTags.SIGUIENTE),
                    )
                }
                Spacer(Modifier.weight(1f))
                // «Limpiar» en dos pasos: la primera pulsación lo arma (tinte de error y «Borrar»); la segunda borra.
                val armado = state.limpiarArmado
                ShalevIconAction(
                    icon = ShalevIcons.Eliminar,
                    contentDescription = if (armado) TextosAvisos.borrarDescripcion(state.total) else TextosAvisos.LIMPIAR_DESCRIPCION,
                    etiqueta = if (armado) TextosAvisos.BORRAR else TextosAvisos.LIMPIAR,
                    onClick = { state.pulsarLimpiar() },
                    containerColor = if (armado) cs.error else null,
                    repetible = true,
                    modifier = Modifier.testTag(AvisosTags.LIMPIAR),
                )
            }
            if (state.perdidos > 0) {
                Text(
                    TextosAvisos.perdidos(state.perdidos),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = ShalevOpacity.secondary + 0.2f),
                )
            }
        }
    }
}

/** Icono del tipo (forma distinta para cada uno: el color nunca es la única señal). */
@Composable
private fun IconoAviso(tipo: TipoAviso) {
    val color = colorTipo(tipo)
    if (tipo == TipoAviso.CARGA) {
        CircularProgressIndicator(
            Modifier.size(TAMANO_ICONO_AVISO).semantics { contentDescription = tipo.nombre },
            color = color,
            strokeWidth = ShalevSize.strokeRegular,
        )
    } else {
        Icon(iconoTipo(tipo), contentDescription = tipo.nombre, tint = color, modifier = Modifier.size(TAMANO_ICONO_AVISO))
    }
}

@Composable
private fun Repeticiones(n: Int) {
    if (n <= 1) return
    Text(
        "×$n",
        style = MaterialTheme.typography.labelLarge.merge(NUMEROS_TABULARES),
        modifier = Modifier.semantics { contentDescription = "$n veces" },
    )
}

@Composable
private fun Insignia(n: Int) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .background(Color(ColorTokens.AVISO_INSIGNIA), RoundedCornerShape(50))
            .padding(horizontal = 6.dp)
            .clearAndSetSemantics { contentDescription = TextosAvisos.insigniaDescripcion(n) }
            .testTag(AvisosTags.INSIGNIA),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            TextosAvisos.insignia(n),
            color = Color(ColorTokens.ON_AVISO_INSIGNIA),
            style = MaterialTheme.typography.labelSmall.merge(NUMEROS_TABULARES),
        )
    }
}

fun iconoTipo(tipo: TipoAviso): ImageVector = when (tipo) {
    TipoAviso.EXITO -> ShalevIcons.AvisoExito
    TipoAviso.INFO, TipoAviso.CARGA -> ShalevIcons.AvisoInfo
    TipoAviso.ADVERTENCIA -> ShalevIcons.AvisoAdvertencia
    TipoAviso.ERROR -> ShalevIcons.AvisoError
}

/** Icono del botón de acción según su verbo. */
fun iconoAccion(accion: String): ImageVector = when (ClasificadorAviso.normalizar(palabraBoton(accion))) {
    "reintentar" -> ShalevIcons.Reintentar
    "deshacer" -> ShalevIcons.Restablecer
    "copiar" -> ShalevIcons.Copiar
    "compartir" -> ShalevIcons.Compartir
    else -> ShalevIcons.Abrir
}

/**
 * Color del icono de estado sobre la isla. La isla usa la superficie inversa (oscura en tema claro y clara en tema
 * oscuro), así que toma el tono del tema contrario: ≥3:1 contra la isla en ambos temas (ContrastTest, WCAG 1.4.11).
 */
@Composable
private fun colorTipo(tipo: TipoAviso): Color {
    val oscuro = LocalShalevColors.current.isDark
    return Color(colorTipoArgb(tipo, temaOscuro = oscuro))
}

fun colorTipoArgb(tipo: TipoAviso, temaOscuro: Boolean): Long = when (tipo) {
    TipoAviso.EXITO -> if (temaOscuro) ColorTokens.L_SUCCESS else ColorTokens.D_SUCCESS
    TipoAviso.ERROR -> if (temaOscuro) ColorTokens.L_ERROR else ColorTokens.D_ERROR
    TipoAviso.ADVERTENCIA -> if (temaOscuro) ColorTokens.L_ALERT_STOCK_BAJO else ColorTokens.D_ALERT_STOCK_BAJO
    TipoAviso.INFO, TipoAviso.CARGA -> if (temaOscuro) ColorTokens.D_INVERSE_PRIMARY else ColorTokens.L_INVERSE_PRIMARY
}

/** Esquema para lo que va dentro de la isla: los botones y textos se leen sobre la superficie inversa. */
private fun ColorScheme.invertido(): ColorScheme = copy(
    surface = inverseSurface,
    onSurface = inverseOnSurface,
    onSurfaceVariant = inverseOnSurface,
    primary = inversePrimary,
    onPrimary = inverseSurface,
    inverseSurface = surface,
    inverseOnSurface = onSurface,
    inversePrimary = primary,
)

// ---------------------------------------------------------------- vistas previas

@Preview(name = "Avisos — claro", showBackground = true)
@Composable
private fun AvisosPreviewClaro() = AvisosPreview()

@Preview(name = "Avisos — oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun AvisosPreviewOscuro() = AvisosPreview()

@Composable
private fun AvisosPreview() {
    ShalevTheme {
        val estado = remember {
            ShalevAvisosState().apply {
                mostrar("Producto eliminado.")
                mostrar("No se pudo guardar. Inténtalo de nuevo.")
            }
        }
        Column(Modifier.padding(ShalevSpacing.md)) { ShalevSnackbarHost(estado) }
    }
}
