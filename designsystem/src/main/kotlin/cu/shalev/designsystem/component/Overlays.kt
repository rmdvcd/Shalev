package cu.shalev.designsystem.component

import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevElevation
import cu.shalev.designsystem.token.ShalevMotion
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

/**
 * Snackbar sobre inverseSurface (contraste AA en ambos temas).
 * P18 (A15): todos los mensajes de la app pasan por aquí, con el mismo aspecto, y cada uno que aparece va acompañado
 * de una vibración muy breve (respeta el ajuste del sistema) para quien no está mirando la pantalla.
 */
@Composable
fun ShalevSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val haptics = rememberShalevHaptics()
    val actual = state.currentSnackbarData
    LaunchedEffect(actual) { if (actual != null) haptics.seleccion() }
    SnackbarHost(state, modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(ShalevRadius.md),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary,
        )
    }
}

/**
 * Diálogo con el título centrado arriba (0.27.0: sin logo). Confirmar/Cancelar son iconos (Shalev.txt) con descripción accesible.
 * [destructive] pinta la confirmación en color de error (eliminar).
 *
 * P24: ventana emergente **centrada**; título centrado arriba; los botones Cancelar y Confirmar, centrados y con
 * separación media (16dp). La confirmación va rellena (Von Restorff) y Cancelar sin fondo. El contenido largo se
 * desplaza dentro del diálogo y los botones siguen siempre a la vista.
 */
@Composable
fun ShalevDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)?,
    modifier: Modifier = Modifier,
    text: String? = null,
    confirmDescription: String = "Confirmar",
    dismissDescription: String = "Cancelar",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmLoading: Boolean = false,
    confirmTag: String? = null,
    confirmIcon: ImageVector? = null,
    /**
     * 0.31.0: acción suelta en la **esquina inferior derecha** de la ventana (p. ej. «Omitir»). Cancelar y Confirmar
     * siguen centrados; con `esquina = null` el pie es el de siempre. Va antes de [content] para no romper la lambda
     * final de las llamadas.
     */
    esquina: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    ShalevVentana(
        onDismiss = onDismiss,
        title = title,
        modifier = modifier,
        footer = { cerrar ->
            // 1.0.2: con acciones de texto, la acción de la esquina va en su propia línea a la derecha para que
            // no se pise con Cancelar/Confirmar; sin ella, el pie es la fila centrada de siempre.
            Column(Modifier.fillMaxWidth()) {
                ShalevButtonRow {
                    ShalevIconAction(ShalevIcons.Cancelar, dismissDescription, cerrar)
                    if (onConfirm != null) {
                        if (confirmLoading) {
                            Box(Modifier.size(ShalevSize.touchTarget), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(ShalevSize.icon).semantics { contentDescription = confirmDescription })
                            }
                        } else {
                            ShalevIconAction(
                                icon = confirmIcon ?: if (destructive) ShalevIcons.Eliminar else ShalevIcons.Confirmar,
                                contentDescription = confirmDescription,
                                onClick = onConfirm,
                                enabled = confirmEnabled,
                                style = IconActionStyle.Filled,
                                containerColor = if (destructive) cs.error else null,
                                modifier = if (confirmTag != null) Modifier.testTag(confirmTag) else Modifier,
                            )
                        }
                    }
                }
                esquina?.let { accion ->
                    Box(Modifier.fillMaxWidth().padding(top = ShalevSpacing.xs), contentAlignment = Alignment.CenterEnd) { accion() }
                }
            }
        },
        divisor = false,
    ) {
        when {
            content != null -> content()
            text != null -> Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * Ventana emergente centrada para formularios rápidos, filtros, exportar, etc. (antes, hoja inferior).
 * Cabecera con el título centrado (0.27.0: sin logo), contenido desplazable y [footer] FIJO con las acciones, centradas.
 *
 * P18 (A03): con [sinGuardar] = true, cerrar SIN querer (tocar fuera o el botón atrás) no descarta lo escrito:
 * pregunta «¿Salir sin guardar?». El botón Cancelar del pie es una decisión explícita y cierra directamente.
 *
 * P24: todas las ventanas emergentes de la app son centradas. Ancho del 92 % de la pantalla (máximo 560dp) y alto
 * máximo del 90 %; con el teclado abierto se encoge para que los campos sigan visibles.
 */
@Composable
fun ShalevBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
    sinGuardar: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var preguntar by rememberSaveable { mutableStateOf(false) }
    if (preguntar) {
        ShalevDialog(
            title = ShalevSheetTextos.SALIR_TITULO,
            text = ShalevSheetTextos.SALIR_TEXTO,
            onDismiss = { preguntar = false },
            onConfirm = { preguntar = false; onDismiss() },
            confirmDescription = ShalevSheetTextos.SALIR,
            dismissDescription = ShalevSheetTextos.SEGUIR,
            destructive = true,
            confirmIcon = ShalevIcons.Salir,
        )
    }
    ShalevVentana(
        onDismiss = { if (sinGuardar) preguntar = true else onDismiss() },
        title = title,
        footer = if (footer == null) null else { _ -> footer(this) },
        animarCierre = !sinGuardar,
        content = content,
    )
}

/**
 * Base común de [ShalevDialog] y [ShalevBottomSheet]: superficie centrada, radio 24dp y título centrado arriba.
 *
 * 0.27.0 (T1): sin logo en la cabecera; el título va arriba del todo, centrado.
 * 0.27.0 (N4): la ventana sube desde abajo hasta el centro (con fundido y el velo oscuro que aparece detrás) y, al
 * cerrarse con Cancelar, tocando fuera o con el botón atrás, hace el recorrido inverso antes de desaparecer. Con
 * [animarCierre] = false (p. ej. una hoja con cambios sin guardar, que antes pregunta) la petición de cierre llega
 * directa. Si quien la usa ignora el cierre, la ventana vuelve a subir.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ShalevVentana(
    onDismiss: () -> Unit,
    title: String?,
    modifier: Modifier = Modifier,
    footer: (@Composable RowScope.(cerrar: () -> Unit) -> Unit)?,
    divisor: Boolean = true,
    animarCierre: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visible = remember { MutableTransitionState(false) }
    val cerrarActual by rememberUpdatedState(onDismiss)
    var pendiente by remember { mutableStateOf(false) }
    val cerrar: () -> Unit = {
        if (!animarCierre) cerrarActual()
        else if (!pendiente) { pendiente = true; visible.targetState = false }
    }
    LaunchedEffect(Unit) { visible.targetState = true }
    BloquearGestos() // 0.27.0 (T5)
    LaunchedEffect(visible.currentState, visible.isIdle) {
        if (pendiente && visible.isIdle && !visible.currentState) {
            pendiente = false
            cerrarActual()
            visible.targetState = true
        }
    }
    Dialog(onDismissRequest = cerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // El velo lo dibuja la propia ventana (con fundido); se quita el oscurecido fijo del sistema.
        val vista = LocalView.current
        SideEffect { (vista.parent as? DialogWindowProvider)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) }
        val alto = LocalConfiguration.current.screenHeightDp.dp
        val cs = MaterialTheme.colorScheme
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(ShalevMotion.tween(ShalevMotion.VELO)),
            exit = fadeOut(ShalevMotion.tween(ShalevMotion.VELO)),
        ) {
            // La ventana del diálogo ocupa toda la pantalla: tocar fuera de la tarjeta cierra (la tarjeta consume sus toques).
            Box(
                Modifier.fillMaxSize().background(cs.scrim.copy(alpha = ALFA_VELO))
                    // 0.32.0 (A13): la ventana respeta barras del sistema, recorte (notch) y teclado. Antes solo
                    // apartaba el teclado: en horizontal la hoja podía quedar bajo la barra de estado o el notch.
                    .pointerInput(Unit) { detectTapGestures { cerrar() } }.safeDrawingPadding().padding(ShalevSpacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = modifier.animateEnterExit(enter = ShalevMotion.ventanaEntra, exit = ShalevMotion.ventanaSale)
                        .fillMaxWidth(ANCHO_VENTANA).widthIn(max = ShalevSize.contentMaxWidth).heightIn(max = alto * ALTO_VENTANA)
                        .pointerInput(Unit) { detectTapGestures { } },
                    shape = RoundedCornerShape(ShalevRadius.xl),
                    color = cs.surfaceContainerHigh,
                    tonalElevation = ShalevElevation.mid,
                    shadowElevation = ShalevElevation.high,
                ) {
                    Column(Modifier.padding(top = ShalevSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                        title?.let {
                            Text(
                                it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.lg).padding(bottom = ShalevSpacing.xs).semantics { heading() },
                            )
                        }
                        Column(
                            Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = ShalevSpacing.lg, vertical = ShalevSpacing.xs),
                            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
                            horizontalAlignment = Alignment.CenterHorizontally, // P28: lo que va solo en su fila, centrado
                        ) {
                            CompositionLocalProvider(LocalShalevCentrado provides true) { content() }
                        }
                        footer?.let { pie ->
                            if (divisor) HorizontalDivider(color = cs.outlineVariant)
                            ShalevButtonRow(Modifier.padding(horizontal = ShalevSpacing.lg, vertical = ShalevSpacing.md)) { pie(this, cerrar) }
                        } ?: Spacer(Modifier.height(ShalevSpacing.lg))
                    }
                }
            }
        }
    }
}

/** Opacidad del velo que oscurece la pantalla detrás de una ventana emergente (la del oscurecido de Material). */
private const val ALFA_VELO = 0.32f

/** Fracción del ancho y del alto de la pantalla que puede ocupar una ventana emergente. */
private const val ANCHO_VENTANA = 0.92f
private const val ALTO_VENTANA = 0.9f

/** Textos de la confirmación al cerrar una hoja con cambios. */
object ShalevSheetTextos {
    const val SALIR_TITULO = "¿Salir sin guardar?"
    const val SALIR_TEXTO = "Se perderán los cambios que escribiste."
    const val SALIR = "Salir sin guardar"
    const val SEGUIR = "Seguir editando"
}

/** FAB de una sola acción (p. ej. "Agregar insumo"): contenedor primary sobre onPrimary. */
@Composable
fun ShalevFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 1.0.2: texto visible del botón flotante. Si es null se usa [contentDescription]; [etiqueta] permite un texto
     * más corto cuando la descripción es larga («Nueva venta» → «Vender»).
     */
    etiqueta: String? = null,
) {
    val cs = MaterialTheme.colorScheme
    // 1.0.2: el botón flotante también se rotula con texto (ya no es un icono suelto).
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(ShalevRadius.lg),
        color = cs.primary,
        contentColor = cs.onPrimary,
        shadowElevation = ShalevElevation.mid,
    ) {
        Row(
            Modifier.heightIn(min = ShalevSize.touchTarget).padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(etiqueta ?: contentDescription, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

