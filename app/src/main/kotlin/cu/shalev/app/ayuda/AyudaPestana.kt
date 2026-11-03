package cu.shalev.app.ayuda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.Modulo
import cu.shalev.app.ayuda.AyudaVentana

/** 
 * 1.0.2: ayuda de cada pestaña principal, la que abre el botón «?» de la esquina superior izquierda. 
 * Ahora usa AyudaVentana resumido por ventana para un contenido más breve y concreto.
 */
enum class PestanaAyuda(val titulo: String) {
    INICIO("Inicio"),
    INVENTARIO("Inventario"),
    SERVICIOS("Servicios"),
    REGISTROS("Registros"),
    AJUSTES("Ajustes"),
    LICENCIA("Licencia"),
    RESPALDO("Respaldo"),
    EMPLEADOS("Apps vinculadas"),
    SOPORTE("Soporte"),
}

/** Obtiene las ayudas de ventana para una pestaña dada y los módulos activos. */
fun pestanaTemas(pestana: PestanaAyuda, modulos: Set<Modulo>): List<AyudaVentana> = when (pestana) {
    PestanaAyuda.INICIO -> listOf(AyudaContenido.inicio)
    PestanaAyuda.INVENTARIO -> listOf(AyudaContenido.inventario)
    PestanaAyuda.SERVICIOS -> listOf(AyudaContenido.servicios)
    PestanaAyuda.REGISTROS -> listOf(AyudaContenido.registros)
    PestanaAyuda.AJUSTES -> listOf(AyudaContenido.ajustes)
    PestanaAyuda.LICENCIA -> listOf(AyudaContenido.licencia)
    PestanaAyuda.RESPALDO -> listOf(AyudaContenido.respaldo)
    PestanaAyuda.EMPLEADOS -> listOf(AyudaContenido.empleados)
    PestanaAyuda.SOPORTE -> listOf(AyudaContenido.soporte)
    else -> emptyList()
}

/** 
 * Ventana con el contenido de ayuda de la pestaña: título y pasos breves por ventana. 
 * [onVerManual] navega al manual resumido en Ajustes.
 */
@Composable
fun DialogoAyudaPestana(
    pestana: PestanaAyuda,
    modulos: Set<Modulo>,
    onDismiss: () -> Unit,
    onVerManual: (() -> Unit)? = null,
) {
    val ayudas = remember(pestana, modulos) { pestanaTemas(pestana, modulos) }
    ShalevDialog(
        title = "Manual · ${pestana.titulo}",
        onDismiss = onDismiss,
        onConfirm = onVerManual,
        confirmDescription = "Ver manual completo",
        confirmIcon = cu.shalev.designsystem.icon.ShalevIcons.Ayuda,
        dismissDescription = "Cerrar",
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        ) {
            if (ayudas.isEmpty()) Text("No hay ayuda disponible para esta pantalla.")
            ayudas.forEach { ayuda ->
                Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                    Text(
                        ayuda.titulo,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    ayuda.pasos.forEachIndexed { i, paso ->
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