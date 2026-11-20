package cu.shalev.app.root

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cu.shalev.designsystem.component.ShalevLogo
import cu.shalev.designsystem.component.construirTituloMIPVentaX
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

/**
 * Pantalla animada de carga inicial con el logo de MIPVentaX.
 *
 * Estilo: Trazo Vectorial y Rebote (Path Draw & Snap).
 * 1. El contorno vectorial del marco se dibuja con precisión geométrica (0 -> 100%).
 * 2. Al completarse el trazo, el isotipo central destella y realiza un micro-rebote elástico (spring physics).
 * 3. Las siglas IPV se fijan con resplandor en acento cálido (Ámbar/Naranja) y tipografía Orbitron.
 */
@Composable
fun AnimacionCargaMIPVentaX(
    modifier: Modifier = Modifier,
) {
    // Progreso del trazo vectorial (0f a 1f)
    val progresoTrazo = remember { Animatable(0f) }
    // Escala del logo con física de resorte (rebote elástico al fijarse)
    val escalaRebote = remember { Animatable(0.82f) }
    // Opacidad del contenido interior del logo y texto
    val opacidadContenido = remember { Animatable(0f) }

    val colorTrazo = MaterialTheme.colorScheme.primary
    val colorAcentoCalido = MaterialTheme.colorScheme.secondary

    LaunchedEffect(Unit) {
        // Fase 1: Dibuja el trazo vectorial
        progresoTrazo.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        )
        // Fase 2: Rebote elástico (Snap) y aparición del contenido
        opacidadContenido.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 200),
        )
        escalaRebote.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(110.dp)
                    .scale(escalaRebote.value),
            ) {
                // Trazo vectorial animado del contorno
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val sweep = 360f * progresoTrazo.value
                    drawArc(
                        color = if (progresoTrazo.value >= 1f) colorAcentoCalido else colorTrazo,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }

                // Isotipo interior que destella con el rebote
                Box(
                    modifier = Modifier.alpha(opacidadContenido.value),
                    contentAlignment = Alignment.Center,
                ) {
                    ShalevLogo(
                        size = ShalevSize.logoMedium,
                        contentDescription = "Logo MIPVentaX",
                    )
                }
            }

            Spacer(Modifier.height(ShalevSpacing.md))

            // Título MIPVentaX con IPV resaltado en acento cálido
            Text(
                text = construirTituloMIPVentaX(
                    marca = true,
                    colorDestacado = colorAcentoCalido,
                ),
                style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 2.sp),
                modifier = Modifier.alpha(opacidadContenido.value),
            )

            Spacer(Modifier.height(ShalevSpacing.lg))

            LinearProgressIndicator(
                modifier = Modifier
                    .width(140.dp)
                    .height(3.dp)
                    .alpha(opacidadContenido.value * 0.85f),
                color = colorAcentoCalido,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}
