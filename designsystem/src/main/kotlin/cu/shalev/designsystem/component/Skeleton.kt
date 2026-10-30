package cu.shalev.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * 1.0.2: formas del marcador de carga. Se dibujan con bloques grises en el sitio donde irá el contenido, en lugar de
 * una rueda: la pantalla conserva la estructura que tendrá (menos saltos al cargar).
 *
 * Los bloques son **estáticos a propósito**: no hay `rememberInfiniteTransition` (la app no usa animaciones infinitas,
 * porque impiden que los tests de Compose queden inactivos y porque «Quitar animaciones» debe dejarlas quietas).
 */
enum class FormaSkeleton {
    /** Filas de una lista: miniatura + dos líneas (Inventario, Registros, Servicios…). */
    LISTA,

    /** Tarjetas apiladas (Inicio, ajustes, resúmenes). */
    TARJETA,

    /** Un bloque alto, con la altura de un gráfico. */
    GRAFICO,

    /** Una sola fila (cabeceras, totales). */
    FILA,
}

/** Marcador de carga con la forma indicada. Va dentro de un contenedor con el tamaño de la pantalla o de la sección. */
@Composable
fun ShalevSkeleton(modifier: Modifier = Modifier, forma: FormaSkeleton = FormaSkeleton.LISTA) {
    Box(modifier.clearAndSetSemantics { }) {
        when (forma) {
            FormaSkeleton.LISTA -> Column(
                Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
            ) {
                repeat(6) { FilaSkeleton() }
            }

            FormaSkeleton.TARJETA -> Column(
                Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
            ) {
                repeat(3) { Bloque(Modifier.fillMaxWidth().height(88.dp)) }
            }

            FormaSkeleton.GRAFICO -> Column(
                Modifier.fillMaxWidth().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
            ) {
                Bloque(Modifier.fillMaxWidth(0.4f).height(20.dp))
                Bloque(Modifier.fillMaxWidth().height(ShalevSize.chartHeight))
            }

            FormaSkeleton.FILA -> FilaSkeleton()
        }
    }
}

@Composable
private fun FilaSkeleton() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
    ) {
        Bloque(Modifier.size(ShalevSize.miniatura), CircleShape)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
            Bloque(Modifier.fillMaxWidth(0.65f).height(16.dp))
            Bloque(Modifier.fillMaxWidth(0.4f).height(12.dp))
        }
    }
}

/** Bloque gris del marcador. [forma] permite círculos (miniaturas) y esquinas redondeadas (tarjetas, líneas). */
@Composable
private fun Bloque(modifier: Modifier = Modifier, forma: Shape = Esquinas) {
    Box(modifier.clip(forma).background(MaterialTheme.colorScheme.onSurface.copy(alpha = ALFA_BLOQUE)))
}

/** Opacidad de los bloques del marcador: visible sobre `surface` en claro y en oscuro sin parecer un error. */
private const val ALFA_BLOQUE = 0.12f

private val Esquinas = RoundedCornerShape(ShalevRadius.sm)
