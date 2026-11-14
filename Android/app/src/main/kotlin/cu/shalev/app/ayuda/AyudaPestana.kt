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
import androidx.compose.ui.platform.testTag
import cu.shalev.designsystem.component.LocalCoachMarkState
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.Modulo

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

/** Botón del diálogo de ayuda que repite el recorrido guiado de la pantalla. */
const val VER_RECORRIDO = "Ver recorrido"

object AyudaPestanaTags {
    const val VER_RECORRIDO = "ayuda_pestana_ver_recorrido"
}

/** Obtiene las ayudas de ventana para una pestaña dada y los módulos activos. */
fun pestanaTemas(pestana: PestanaAyuda, modulos: Set<Modulo>): List<AyudaVentana> {
    val categoria = when (pestana) {
        PestanaAyuda.INICIO -> CategoriaAyuda.INICIO
        PestanaAyuda.INVENTARIO -> CategoriaAyuda.INVENTARIO
        PestanaAyuda.SERVICIOS -> CategoriaAyuda.SERVICIOS
        PestanaAyuda.REGISTROS -> CategoriaAyuda.REGISTROS
        PestanaAyuda.AJUSTES -> CategoriaAyuda.AJUSTES
        PestanaAyuda.LICENCIA -> CategoriaAyuda.LICENCIA
        PestanaAyuda.RESPALDO -> CategoriaAyuda.RESPALDO
        PestanaAyuda.EMPLEADOS -> CategoriaAyuda.EMPLEADOS
        PestanaAyuda.SOPORTE -> CategoriaAyuda.SOPORTE
    }
    return AyudaContenido.temasPara(modulos).filter { it.categoria == categoria }
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
    // Recorrido guiado de la pantalla (si la pantalla tiene uno y hay algo que resaltar): repetirlo no cambia el
    // indicador «ya visto».
    val recorrido = LocalCoachMarkState.current?.takeIf { it.hayObjetivos() }
    val verRecorrido: (@Composable () -> Unit)? = if (recorrido == null) null else {
        {
            ShalevSecondaryButton(
                VER_RECORRIDO, icon = cu.shalev.designsystem.icon.ShalevIcons.Restablecer,
                onClick = { onDismiss(); recorrido.iniciar() },
                modifier = Modifier.testTag(AyudaPestanaTags.VER_RECORRIDO),
            )
        }
    }
    ShalevDialog(
        title = "Manual · ${pestana.titulo}",
        onDismiss = onDismiss,
        onConfirm = onVerManual,
        confirmDescription = "Ver manual completo",
        confirmIcon = cu.shalev.designsystem.icon.ShalevIcons.Ayuda,
        dismissDescription = "Cerrar",
        secundarias = verRecorrido,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        ) {
            if (ayudas.isEmpty()) Text("Consulta el manual de usuario en Ajustes.")
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