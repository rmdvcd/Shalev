package cu.shalev.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing

/**
 * Formas del marcador de carga. Los «huesos» siguen la geometría y el orden de los componentes reales (idea de
 * Boneyard, adaptada a Compose): filas con miniatura, texto y valor; tarjetas; y gráfico. Son declarativos y estáticos
 * para respetar «Quitar animaciones» y no dejar transiciones infinitas activas en los tests de Compose.
 */
enum class FormaSkeleton {
    /** Filas de una lista/tabla: indicador, miniatura, hasta tres líneas y valor. */
    LISTA,

    /** Tarjetas apiladas (Inicio, ajustes, resúmenes). */
    TARJETA,

    /** Un gráfico con encabezado, resumen y barras. */
    GRAFICO,

    /** Una sola fila (cabeceras, totales). */
    FILA,
}

/** Marcador de carga: conserva la silueta del contenido mientras los datos aún no están disponibles. */
@Composable
fun ShalevSkeleton(modifier: Modifier = Modifier, forma: FormaSkeleton = FormaSkeleton.LISTA) {
    Box(modifier.clearAndSetSemantics { }) {
        when (forma) {
            FormaSkeleton.LISTA -> Column(
                Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
            ) {
                repeat(6) { FilaSkeleton() }
            }

            FormaSkeleton.TARJETA -> Column(
                Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
            ) {
                repeat(3) { TarjetaSkeleton() }
            }

            FormaSkeleton.GRAFICO -> Column(
                Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
            ) {
                Hueso(Modifier.fillMaxWidth(0.38f).height(18.dp))
                Hueso(Modifier.fillMaxWidth(0.72f).height(12.dp))
                GraficoSkeleton()
            }

            FormaSkeleton.FILA -> FilaSkeleton(compacta = true)
        }
    }
}

/** Fila ósea alineada como ShalevListItem: indicador, miniatura, título/subtítulo y valor. */
@Composable
private fun FilaSkeleton(compacta: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = ShalevSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Hueso(Modifier.size(ShalevSize.indicatorWidth, ShalevSize.indicatorHeight))
        Hueso(Modifier.size(if (compacta) ShalevSize.icon else ShalevSize.miniatura), CircleShape)
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            Hueso(Modifier.fillMaxWidth(0.78f).height(16.dp))
            Hueso(Modifier.fillMaxWidth(0.5f).height(12.dp))
        }
        Hueso(Modifier.width(52.dp).height(16.dp))
    }
}

/** Tres líneas óseas con proporciones variables, como una tarjeta de resumen. */
@Composable
private fun TarjetaSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
    ) {
        Hueso(Modifier.fillMaxWidth(0.42f).height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                Hueso(Modifier.fillMaxWidth(0.82f).height(20.dp))
                Hueso(Modifier.fillMaxWidth(0.55f).height(12.dp))
            }
            Hueso(Modifier.width(64.dp).height(24.dp))
        }
    }
}

/** Silueta de barras, sin rejilla ni animación infinita. */
@Composable
private fun GraficoSkeleton() {
    val alturas = listOf(0.45f, 0.74f, 0.56f, 0.9f, 0.62f, 0.8f, 0.38f)
    Row(
        Modifier.fillMaxWidth().height(ShalevSize.chartHeight).padding(top = ShalevSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        verticalAlignment = androidx.compose.ui.Alignment.Bottom,
    ) {
        alturas.forEach { altura ->
            Hueso(Modifier.weight(1f).fillMaxHeight(altura).clip(Esquinas))
        }
    }
}

@Composable
private fun Hueso(modifier: Modifier = Modifier, forma: Shape = Esquinas) {
    Box(modifier.clip(forma).background(MaterialTheme.colorScheme.onSurface.copy(alpha = ALFA_HUESO)))
}

private const val ALFA_HUESO = 0.12f
private val Esquinas = RoundedCornerShape(ShalevRadius.sm)
