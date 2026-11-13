package cu.shalev.designsystem.component

import cu.shalev.designsystem.theme.ShalevTextos
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.designsystem.token.ShalevMotion
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

/**
 * Gráficos de Inicio dibujados con Canvas (sin librería): SIN rejilla, solo una línea base sutil, y animación
 * de entrada con muelle (sin rebote) que se aplica en la fase de dibujo (no recompone). Cada gráfico expone una
 * [contentDescription] con el resumen para TalkBack; las donas llevan leyenda con nombre y porcentaje, así
 * que el color nunca es el único portador de información.
 */
object ChartMath {

    /** Valores relativos 0..1 respecto al máximo positivo. Negativos cuentan como 0. Todo 0 si no hay positivos. */
    fun normalizar(valores: List<Double>): List<Float> {
        val max = valores.maxOrNull()?.takeIf { it > 0 } ?: return valores.map { 0f }
        return valores.map { (it.coerceAtLeast(0.0) / max).toFloat() }
    }

    /** Normaliza varias series contra el MISMO máximo (costo y venta comparables en el área). */
    fun normalizarJuntas(series: List<List<Double>>): List<List<Float>> {
        val max = series.flatten().maxOrNull()?.takeIf { it > 0 } ?: return series.map { s -> s.map { 0f } }
        return series.map { s -> s.map { (it.coerceAtLeast(0.0) / max).toFloat() } }
    }

    /**
     * Arcos de la dona como (inicio, barrido) en grados; 0° = las 12 en punto, sentido horario.
     * Con más de una porción se deja [separacion] grados entre ellas; nunca un barrido negativo.
     */
    fun arcos(fracciones: List<Double>, separacion: Float = 2f): List<Pair<Float, Float>> {
        val total = fracciones.filter { it > 0 }.sum()
        if (total <= 0) return emptyList()
        val hueco = if (fracciones.count { it > 0 } > 1) separacion else 0f
        var inicio = -90f
        return fracciones.map { f ->
            val barrido = (f.coerceAtLeast(0.0) / total * 360f).toFloat()
            val arco = inicio + hueco / 2 to (barrido - hueco).coerceAtLeast(0f)
            inicio += barrido
            arco
        }
    }

    /** Opacidad mínima de una barra (valor casi 0): sigue viéndose sobre el fondo. */
    const val INTENSIDAD_MIN = 0.30f

    /** P24: opacidad de una barra según su altura relativa (0..1) a la escala Y: de [INTENSIDAD_MIN] a 1, lineal. */
    fun intensidad(relativo: Float): Float = INTENSIDAD_MIN + (1f - INTENSIDAD_MIN) * relativo.coerceIn(0f, 1f)

    /** Índices del eje X que llevan etiqueta: primero y último siempre, y como mucho [max] en total, repartidos. */
    fun indicesEtiquetas(n: Int, max: Int = 4): List<Int> = when {
        n <= 0 -> emptyList()
        n <= max -> (0 until n).toList()
        else -> (0 until max).map { i -> Math.round(i * (n - 1) / (max - 1).toDouble()).toInt() }.distinct()
    }
}

@Immutable
data class ChartSlice(val label: String, val fraction: Double, val valueText: String)

@Immutable
data class ChartSeries(val label: String, val values: List<Double>, val color: Color)

@Composable
private fun rememberEntrada(): Animatable<Float, *> {
    val progreso = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progreso.animateTo(1f, ShalevMotion.muelle())
    }
    return progreso
}

/**
 * Barras redondeadas (Ventas).
 * P24: la **intensidad** del color de cada barra depende de su valor respecto al máximo de la escala Y
 * ([ChartMath.intensidad]): las barras más altas, más intensas. La altura sigue diciendo el valor, así que el color
 * nunca es el único portador de la información.
 */
@Composable
fun ShalevBarChart(
    values: List<Double>,
    labels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = ShalevTheme.colors.chart.first(),
) {
    val progreso = rememberEntrada()
    Box(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Crossfade(targetState = values to labels, animationSpec = ShalevMotion.tween(ShalevMotion.SHORT), modifier = Modifier.clearAndSetSemantics { }, label = "barras") { (datos, nombres) ->
            BarrasContenido(datos, nombres, color, progreso)
        }
    }
}

@Composable
private fun BarrasContenido(values: List<Double>, labels: List<String>, color: Color, progreso: Animatable<Float, *>) {
    val alturas = remember(values) { ChartMath.normalizar(values) }
    val base = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(ShalevSize.chartHeight).clearAndSetSemantics { }) {
            if (alturas.isEmpty()) return@Canvas
            val paso = size.width / alturas.size
            val ancho = (paso * 0.64f).coerceAtMost(ShalevSize.chartBarMax.toPx())
            val radio = CornerRadius(ancho / 3, ancho / 3)
            alturas.forEachIndexed { i, h ->
                val alto = h * size.height * progreso.value
                if (alto <= 0f) return@forEachIndexed
                drawRoundRect(
                    color = color.copy(alpha = ChartMath.intensidad(h)),
                    topLeft = Offset(i * paso + (paso - ancho) / 2, size.height - alto),
                    size = Size(ancho, alto),
                    cornerRadius = radio,
                )
            }
            drawLine(base, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = ShalevSize.strokeHairline.toPx())
        }
        EjeX(labels)
    }
}

/** Área bajo curva suavizada; varias series sobre el mismo máximo (Ganancia Neta: venta vs costo). */
@Composable
fun ShalevAreaChart(
    series: List<ChartSeries>,
    labels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
) {
    val progreso = rememberEntrada()
    Box(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Crossfade(targetState = series to labels, animationSpec = ShalevMotion.tween(ShalevMotion.SHORT), modifier = Modifier.clearAndSetSemantics { }, label = "área") { (datos, nombres) ->
            AreaContenido(datos, nombres, progreso)
        }
    }
}

@Composable
private fun AreaContenido(series: List<ChartSeries>, labels: List<String>, progreso: Animatable<Float, *>) {
    val normalizadas = remember(series) { ChartMath.normalizarJuntas(series.map { it.values }) }
    val base = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
    ) {
        // P18 (A19): los Path se construyen una vez por tamaño y datos (drawWithCache), no en cada fotograma.
        // La animación de entrada solo escala en vertical desde la base.
        Spacer(
            Modifier.fillMaxWidth().height(ShalevSize.chartHeight).clearAndSetSemantics { }.drawWithCache {
                val trazos = normalizadas.mapIndexedNotNull { s, ys ->
                    if (ys.isEmpty()) return@mapIndexedNotNull null
                    val dx = if (ys.size > 1) size.width / (ys.size - 1) else 0f
                    val pts = ys.mapIndexed { i, y -> Offset(if (ys.size > 1) i * dx else size.width / 2, size.height - y * size.height) }
                    val linea = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        for (i in 1 until pts.size) {
                            val a = pts[i - 1]; val b = pts[i]; val mx = (a.x + b.x) / 2
                            cubicTo(mx, a.y, mx, b.y, b.x, b.y) // curva suave sin sobrepasar extremos
                        }
                    }
                    val area = Path().apply {
                        addPath(linea)
                        lineTo(pts.last().x, size.height)
                        lineTo(pts.first().x, size.height)
                        close()
                    }
                    Triple(series[s].color, linea, area)
                }
                val grosor = Stroke(width = ShalevSize.strokeRegular.toPx(), cap = StrokeCap.Round)
                onDrawBehind {
                    scale(scaleX = 1f, scaleY = progreso.value, pivot = Offset(0f, size.height)) {
                        trazos.forEach { (c, linea, area) ->
                            drawPath(area, c.copy(alpha = 0.18f))
                            drawPath(linea, c, style = grosor)
                        }
                    }
                    drawLine(base, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = ShalevSize.strokeHairline.toPx())
                }
            },
        )
        EjeX(labels)
        Leyenda(series.map { it.color to it.label })
    }
}

/** Dona con texto central opcional y leyenda (nombre + porcentaje/valor). */
@Composable
fun ShalevDonutChart(
    slices: List<ChartSlice>,
    description: String,
    modifier: Modifier = Modifier,
    centerText: String? = null,
    colors: List<Color> = ShalevTheme.colors.chart,
) {
    val progreso = rememberEntrada()
    Box(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Crossfade(targetState = slices to centerText, animationSpec = ShalevMotion.tween(ShalevMotion.SHORT), modifier = Modifier.clearAndSetSemantics { }, label = "dona") { (datos, centro) ->
            DonaContenido(datos, centro, colors, progreso)
        }
    }
}

@Composable
private fun DonaContenido(slices: List<ChartSlice>, centerText: String?, colors: List<Color>, progreso: Animatable<Float, *>) {
    val arcos = remember(slices) { ChartMath.arcos(slices.map { it.fraction }) }
    val pista = MaterialTheme.colorScheme.surfaceVariant
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
    ) {
        Box(Modifier.size(ShalevSize.donut).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(ShalevSize.donut)) {
                val grosor = ShalevSize.donutStroke.toPx()
                val lado = size.minDimension - grosor
                val origen = Offset((size.width - lado) / 2, (size.height - lado) / 2)
                drawArc(pista, 0f, 360f, useCenter = false, topLeft = origen, size = Size(lado, lado), style = Stroke(grosor))
                arcos.forEachIndexed { i, (inicio, barrido) ->
                    drawArc(
                        color = colors[i % colors.size], startAngle = inicio, sweepAngle = barrido * progreso.value,
                        useCenter = false, topLeft = origen, size = Size(lado, lado), style = Stroke(grosor),
                    )
                }
            }
            centerText?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            }
        }
        Column(Modifier.fillMaxWidth().clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            slices.forEachIndexed { i, s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                    Punto(colors[i % colors.size])
                    Text(s.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(s.valueText, style = ShalevTextos.dato)
                }
            }
        }
    }
}

@Composable
private fun Punto(color: Color) {
    Box(Modifier.size(ShalevSize.legendDot).background(color, CircleShape))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Leyenda(items: List<Pair<Color, String>>) {
    FlowRow(
        Modifier.fillMaxWidth().clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md, Alignment.CenterHorizontally),
    ) {
        items.forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                Punto(c)
                ShalevSecondaryText(t)
            }
        }
    }
}

/** Etiquetas del eje X (pocas, repartidas); sin marcas ni rejilla. */
@Composable
private fun EjeX(labels: List<String>) {
    if (labels.isEmpty()) return
    val idx = remember(labels) { ChartMath.indicesEtiquetas(labels.size) }
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.SpaceBetween) {
        idx.forEach { ShalevSecondaryText(labels[it]) }
    }
}
