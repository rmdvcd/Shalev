package cu.shalev.app.common

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevSheetTextos
import cu.shalev.designsystem.icon.ShalevIcons

object TextosGuardia {
    const val TITULO = ShalevSheetTextos.SALIR_TITULO
    const val TEXTO = ShalevSheetTextos.SALIR_TEXTO
    const val SALIR = ShalevSheetTextos.SALIR
    const val SEGUIR = ShalevSheetTextos.SEGUIR
}

/**
 * P18 (A03): protege formularios y hojas con cambios sin guardar.
 *
 * Devuelve la acción «atrás» que debe usar la flecha de la barra superior o el botón Cancelar. Si [hayCambios] es
 * true, intercepta también el gesto/botón atrás del sistema y pregunta antes de salir; si no, sale directamente.
 * La confirmación es destructiva (botón rojo con icono y descripción) y el diálogo sobrevive a la rotación.
 */
@Composable
fun rememberSalidaProtegida(
    hayCambios: Boolean,
    onSalir: () -> Unit,
    texto: String = TextosGuardia.TEXTO,
): () -> Unit {
    var preguntar by rememberSaveable { mutableStateOf(false) }
    val salir by rememberUpdatedState(onSalir)
    BackHandler(enabled = hayCambios && !preguntar) { preguntar = true }
    if (preguntar) {
        ShalevDialog(
            title = TextosGuardia.TITULO,
            text = texto,
            onDismiss = { preguntar = false },
            onConfirm = { preguntar = false; salir() },
            confirmDescription = TextosGuardia.SALIR,
            dismissDescription = TextosGuardia.SEGUIR,
            destructive = true,
            confirmIcon = ShalevIcons.Salir,
        )
    }
    return { if (hayCambios) preguntar = true else salir() }
}
