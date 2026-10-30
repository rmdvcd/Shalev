package cu.shalev.app.ayuda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.Modulo

/**
 * 1.0.2: ayuda de cada pestaña principal, la que abre el botón «?» de la esquina superior izquierda.
 * Reutiliza los temas del manual ([AyudaContenido]) para que el texto sea el mismo en toda la app.
 */
enum class PestanaAyuda(val titulo: String, private val temasPorTitulo: List<String>) {
    INICIO("Inicio", listOf("Primeros pasos", "Abrir y cerrar el turno", "Vender")),
    INVENTARIO("Inventario", listOf("Agregar productos", "Buscar y filtrar el inventario", "Insumos y elaborados")),
    SERVICIOS("Servicios", listOf("Servicios", "Vender", "Cobrar por transferencia")),
    REGISTROS("Registros", listOf("Ver lo vendido", "Cobrar por transferencia", "Abrir y cerrar el turno")),
    AJUSTES("Ajustes", listOf("Guardar una copia", "Licencia", "Apps vinculadas", "¿Necesitas ayuda?")),
    ;

    /** Temas del manual que corresponden a esta pestaña, ya filtrados por los módulos activos del negocio. */
    fun temas(modulos: Set<Modulo>): List<TemaAyuda> =
        AyudaContenido.temasPara(modulos).filter { it.titulo in temasPorTitulo }
}

/**
 * Ventana con el contenido de ayuda de la pestaña: título y pasos de cada tema, en una columna con desplazamiento.
 * [onVerManual] abre el manual completo (Ajustes → Ayuda).
 */
@Composable
fun DialogoAyudaPestana(
    pestana: PestanaAyuda,
    modulos: Set<Modulo>,
    onDismiss: () -> Unit,
    onVerManual: (() -> Unit)? = null,
) {
    val temas = remember(pestana, modulos) { pestana.temas(modulos) }
    ShalevDialog(
        title = "Ayuda · ${pestana.titulo}",
        text = if (temas.isEmpty()) "El manual completo está en Ajustes → Ayuda." else null,
        onDismiss = onDismiss,
        onConfirm = onVerManual,
        confirmDescription = "Ver todo el manual",
        dismissDescription = "Cerrar",
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        ) {
            temas.forEach { tema ->
                Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                    Text(
                        tema.titulo,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    tema.pasos.forEachIndexed { i, paso ->
                        Text(
                            "${i + 1}. $paso",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = ShalevSpacing.xs),
                        )
                    }
                }
            }
        }
    }
}
