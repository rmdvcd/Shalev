package cu.shalev.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevElevation
import cu.shalev.designsystem.token.ShalevMotion
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing
import kotlin.math.roundToInt

/*
 * Recorrido guiado (coach marks con efecto spotlight), implementado sin librerías externas.
 *
 * - La pantalla marca sus elementos clave con [Modifier.coachMark]; sin un [CoachMarkHost] alrededor el modificador
 *   no hace nada (los *Content se siguen probando y capturando igual).
 * - [CoachMarkHost] dibuja encima un velo negro al 50 % con un hueco redondeado sobre el objetivo y un globo con
 *   título, descripción y «Omitir» / «Anterior» / «Siguiente» (o «Finalizar» en el último paso).
 * - El velo NO intercepta toques: se puede seguir usando la pantalla (también el elemento resaltado) con el
 *   recorrido abierto. Solo el globo recibe toques.
 * - Guardar que el recorrido ya se vio le corresponde a quien lo usa (onFinalizar / onOmitir).
 */

/** Velo del recorrido: negro al 50 % (oscurece sin ocultar la pantalla). */
val CoachMarkVelo: Color = Color.Black.copy(alpha = 0.5f)

/** Un paso del recorrido. [objetivo] es la clave que la pantalla pone con [Modifier.coachMark]. */
@Immutable
data class CoachMarkPaso(val objetivo: String, val titulo: String, val descripcion: String)

object CoachMarkTags {
    const val VELO = "coach_velo"
    const val GLOBO = "coach_globo"
    const val OMITIR = "coach_omitir"
    const val ANTERIOR = "coach_anterior"
    const val SIGUIENTE = "coach_siguiente"
    const val FINALIZAR = "coach_finalizar"
}

/** Textos fijos del globo. */
object TextosCoachMark {
    const val OMITIR = "Omitir"
    const val ANTERIOR = "Anterior"
    const val SIGUIENTE = "Siguiente"
    const val FINALIZAR = "Finalizar"
    fun progreso(paso: Int, total: Int) = "$paso de $total"
}

/**
 * Estado de un recorrido: qué objetivos hay en pantalla (y dónde) y en qué paso va.
 *
 * Al [iniciar] se fijan los pasos de la vuelta: solo los que tienen su objetivo visible en ese momento (así «2 de 3»
 * no cambia a mitad). Si durante el recorrido un objetivo desaparece (p. ej. se desplazó la lista), el globo se
 * muestra centrado y sin hueco hasta que vuelva.
 */
@Stable
class CoachMarkState(val pasos: List<CoachMarkPaso>) {
    private val objetivos = mutableStateMapOf<String, Rect>()

    /** Pasos de la vuelta en curso; vacío = recorrido cerrado. */
    var enCurso: List<CoachMarkPaso> by mutableStateOf(emptyList())
        private set
    var indice: Int by mutableIntStateOf(0)
        private set

    val activo: Boolean get() = enCurso.isNotEmpty()
    val pasoActual: CoachMarkPaso? get() = enCurso.getOrNull(indice)
    val esPrimero: Boolean get() = indice == 0
    val esUltimo: Boolean get() = indice == enCurso.lastIndex

    /** true si al menos un paso tiene su objetivo en pantalla (se puede iniciar). */
    fun hayObjetivos(): Boolean = pasos.any { it.objetivo in objetivos }

    /** Empieza (o repite) el recorrido con los pasos visibles. Devuelve false si no hay ninguno. */
    fun iniciar(): Boolean {
        val visibles = pasos.filter { it.objetivo in objetivos }
        if (visibles.isEmpty()) return false
        enCurso = visibles
        indice = 0
        return true
    }

    fun siguiente() { if (indice < enCurso.lastIndex) indice++ }
    fun anterior() { if (indice > 0) indice-- }
    fun cerrar() { enCurso = emptyList(); indice = 0 }

    /** Límites del objetivo en coordenadas de la raíz, o null si no está en pantalla. */
    fun limites(objetivo: String): Rect? = objetivos[objetivo]

    internal fun registrar(objetivo: String, limites: Rect) { objetivos[objetivo] = limites }
    internal fun quitar(objetivo: String) { objetivos.remove(objetivo) }

    private fun restaurar(ids: List<String>, indiceGuardado: Int) {
        enCurso = pasos.filter { it.objetivo in ids }
        indice = indiceGuardado.coerceIn(0, (enCurso.size - 1).coerceAtLeast(0))
    }

    companion object {
        /** Sobrevive a la rotación y a volver a la pantalla desde otra: [índice, objetivos de la vuelta…]. */
        fun saver(pasos: List<CoachMarkPaso>): Saver<CoachMarkState, Any> = listSaver(
            save = { e -> listOf(e.indice.toString()) + e.enCurso.map { it.objetivo } },
            restore = { l -> CoachMarkState(pasos).apply { restaurar(l.drop(1), l.first().toIntOrNull() ?: 0) } },
        )
    }
}

@Composable
fun rememberCoachMarkState(pasos: List<CoachMarkPaso>): CoachMarkState =
    rememberSaveable(saver = CoachMarkState.saver(pasos)) { CoachMarkState(pasos) }

/** Recorrido de la pantalla actual, o null fuera de un [CoachMarkHost] (p. ej. «Ver recorrido» de la ayuda). */
val LocalCoachMarkState = staticCompositionLocalOf<CoachMarkState?> { null }

/**
 * Marca este elemento como objetivo [objetivo] del recorrido. Fuera de un [CoachMarkHost] no hace nada.
 * Usa los límites ya recortados por sus contenedores: lo que queda fuera de una lista desplazada no se resalta.
 */
fun Modifier.coachMark(objetivo: String): Modifier = composed {
    val estado = LocalCoachMarkState.current ?: return@composed Modifier
    DisposableEffect(estado, objetivo) { onDispose { estado.quitar(objetivo) } }
    Modifier.onGloballyPositioned { c ->
        val r = if (c.isAttached) c.boundsInRoot() else Rect.Zero
        if (r.width <= 0f || r.height <= 0f) estado.quitar(objetivo) else estado.registrar(objetivo, r)
    }
}

/**
 * Contenedor del recorrido. [content] ocupa todo el espacio; encima, mientras [estado] esté activo y [visible] sea
 * true, se dibujan el velo con el hueco y el globo. [visible] = false lo pausa (p. ej. con un diálogo abierto)
 * sin perder el paso.
 */
@Composable
fun CoachMarkHost(
    estado: CoachMarkState,
    onFinalizar: () -> Unit,
    onOmitir: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    content: @Composable () -> Unit,
) {
    var origen by remember { mutableStateOf(Offset.Zero) }
    CompositionLocalProvider(LocalCoachMarkState provides estado) {
        Box(modifier.onGloballyPositioned { origen = it.positionInRoot() }) {
            content()
            AnimatedVisibility(
                visible = visible && estado.activo,
                modifier = Modifier.matchParentSize(),
                enter = fadeIn(ShalevMotion.tween(ShalevMotion.MEDIUM)),
                exit = fadeOut(ShalevMotion.tween(ShalevMotion.SHORT)),
            ) {
                Box(Modifier.fillMaxSize()) {
                    estado.pasoActual?.let { paso ->
                        VeloYGlobo(
                            estado = estado, paso = paso,
                            foco = estado.limites(paso.objetivo)?.translate(-origen),
                            onFinalizar = onFinalizar, onOmitir = onOmitir,
                        )
                    }
                }
            }
        }
    }
}

/** Ancho máximo del globo en tablet u horizontal. */
private val GloboAnchoMax = 360.dp

@Composable
private fun BoxScope.VeloYGlobo(
    estado: CoachMarkState,
    paso: CoachMarkPaso,
    foco: Rect?,
    onFinalizar: () -> Unit,
    onOmitir: () -> Unit,
) {
    val densidad = LocalDensity.current
    val holgura = with(densidad) { ShalevSpacing.xs.toPx() }
    val radio = with(densidad) { ShalevRadius.md.toPx() }
    val trazo = with(densidad) { ShalevSize.strokeRegular.toPx() }
    val anillo = MaterialTheme.colorScheme.inversePrimary
    val hueco = foco?.inflate(holgura)

    // Sin pointerInput: los toques atraviesan el velo y llegan a la pantalla.
    Canvas(Modifier.matchParentSize().testTag(CoachMarkTags.VELO)) {
        val velo = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            hueco?.let { addRoundRect(RoundRect(it, CornerRadius(radio))) }
        }
        drawPath(velo, CoachMarkVelo)
        hueco?.let { drawRoundRect(anillo, it.topLeft, it.size, CornerRadius(radio), style = Stroke(trazo)) }
    }

    Layout(
        content = {
            Globo(
                paso = paso,
                numero = estado.indice + 1,
                total = estado.enCurso.size,
                esPrimero = estado.esPrimero,
                esUltimo = estado.esUltimo,
                onAnterior = estado::anterior,
                onSiguiente = estado::siguiente,
                onOmitir = onOmitir,
                onFinalizar = onFinalizar,
            )
        },
        modifier = Modifier.matchParentSize(),
    ) { medibles, restricciones ->
        val margen = ShalevSpacing.md.roundToPx()
        val ancho = minOf(restricciones.maxWidth - 2 * margen, GloboAnchoMax.roundToPx()).coerceAtLeast(0)
        val alto = (restricciones.maxHeight - 2 * margen).coerceAtLeast(0)
        val globo = medibles.first().measure(Constraints(maxWidth = ancho, maxHeight = alto))
        layout(restricciones.maxWidth, restricciones.maxHeight) {
            globo.place(
                CoachMarkGeometria.posicionGlobo(
                    contenedor = IntSize(restricciones.maxWidth, restricciones.maxHeight),
                    globo = IntSize(globo.width, globo.height),
                    foco = hueco,
                    margen = margen,
                    separacion = ShalevSpacing.sm.roundToPx(),
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Globo(
    paso: CoachMarkPaso,
    numero: Int,
    total: Int,
    esPrimero: Boolean,
    esUltimo: Boolean,
    onAnterior: () -> Unit,
    onSiguiente: () -> Unit,
    onOmitir: () -> Unit,
    onFinalizar: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    // Surface consume los toques de su área: tocar el globo no activa lo que hay debajo.
    Surface(
        shape = RoundedCornerShape(ShalevRadius.lg),
        color = cs.surface,
        contentColor = cs.onSurface,
        tonalElevation = ShalevElevation.mid,
        shadowElevation = ShalevElevation.high,
        modifier = Modifier.testTag(CoachMarkTags.GLOBO).semantics {
            // TalkBack anuncia el globo al aparecer y lo lee antes que la pantalla de debajo.
            paneTitle = paso.titulo
            isTraversalGroup = true
            traversalIndex = -1f
        },
    ) {
        Column(
            Modifier.padding(ShalevSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            Text(
                TextosCoachMark.progreso(numero, total),
                style = MaterialTheme.typography.labelMedium,
                color = cs.primary,
            )
            Text(
                paso.titulo,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
            )
            Text(paso.descripcion, style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                Modifier.fillMaxWidth().padding(top = ShalevSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
            ) {
                // «Omitir» siempre disponible (en el último paso «Finalizar» ya cierra).
                if (!esUltimo) ShalevTextButton(
                    TextosCoachMark.OMITIR, onClick = onOmitir, icon = ShalevIcons.Saltar, mostrarTexto = true,
                    modifier = Modifier.testTag(CoachMarkTags.OMITIR),
                )
                if (!esPrimero) ShalevSecondaryButton(
                    TextosCoachMark.ANTERIOR, onClick = onAnterior, icon = ShalevIcons.Atras, mostrarTexto = true,
                    modifier = Modifier.testTag(CoachMarkTags.ANTERIOR),
                )
                if (esUltimo) ShalevPrimaryButton(
                    TextosCoachMark.FINALIZAR, onClick = onFinalizar, icon = ShalevIcons.Confirmar, mostrarTexto = true,
                    modifier = Modifier.testTag(CoachMarkTags.FINALIZAR),
                ) else ShalevPrimaryButton(
                    TextosCoachMark.SIGUIENTE, onClick = onSiguiente, icon = ShalevIcons.Siguiente, mostrarTexto = true,
                    modifier = Modifier.testTag(CoachMarkTags.SIGUIENTE),
                )
            }
        }
    }
}

/** Geometría pura del globo (probada en JVM). */
internal object CoachMarkGeometria {
    /**
     * Posición del globo dentro de [contenedor]: debajo del [foco] si está en la mitad superior y cabe; si no,
     * encima; si no cabe en ningún lado, pegado al borde contrario al objetivo. Sin foco, centrado. Siempre dentro
     * del contenedor con [margen], y centrado en horizontal sobre el objetivo cuando el ancho lo permite.
     */
    fun posicionGlobo(contenedor: IntSize, globo: IntSize, foco: Rect?, margen: Int, separacion: Int): IntOffset {
        val maxX = (contenedor.width - globo.width - margen).coerceAtLeast(margen)
        val maxY = (contenedor.height - globo.height - margen).coerceAtLeast(margen)
        if (foco == null) {
            return IntOffset(
                ((contenedor.width - globo.width) / 2).coerceIn(margen, maxX),
                ((contenedor.height - globo.height) / 2).coerceIn(margen, maxY),
            )
        }
        val x = (foco.center.x - globo.width / 2f).roundToInt().coerceIn(margen, maxX)
        val abajo = foco.bottom.roundToInt() + separacion
        val arriba = foco.top.roundToInt() - separacion - globo.height
        val cabeAbajo = abajo + globo.height <= contenedor.height - margen
        val cabeArriba = arriba >= margen
        val enMitadSuperior = foco.center.y <= contenedor.height / 2f
        val y = when {
            enMitadSuperior && cabeAbajo -> abajo
            cabeArriba -> arriba
            cabeAbajo -> abajo
            foco.top > contenedor.height - foco.bottom -> margen
            else -> maxY
        }
        return IntOffset(x, y.coerceIn(margen, maxY))
    }
}
