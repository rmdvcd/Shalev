package cu.shalev.app.licencia

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.padding
import cu.shalev.designsystem.component.ShalevEmptyState
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.ilustracion.ShalevIlustracion

object TextosTransferida {
    const val TITULO = "Licencia transferida"
    const val DETALLE = "La licencia de Shalev se recuperó en otro teléfono. Por seguridad, aquí se borraron los datos del " +
        "negocio y la app quedó bloqueada. Puedes desinstalarla."
    const val DESINSTALAR = "Desinstalar Shalev"
    const val TAG = "licencia_transferida"
}

/**
 * 0.25.0 (§3.4): pantalla final del teléfono ANTIGUO tras una recuperación automática de la licencia. Android no permite
 * desinstalar en silencio: el botón abre el diálogo del sistema y el usuario confirma.
 */
@Composable
fun LicenciaTransferida() {
    val context = LocalContext.current
    Scaffold(topBar = { ShalevTopBar(title = "Shalev", marca = true) }) { padding ->
        ShalevEmptyState(
            title = TextosTransferida.TITULO,
            detail = TextosTransferida.DETALLE,
            ilustracion = ShalevIlustracion.NoExiste,
            modifier = Modifier.padding(padding).testTag(TextosTransferida.TAG),
            action = {
                ShalevPrimaryButton(
                    TextosTransferida.DESINSTALAR, icon = ShalevIcons.Eliminar,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )
            },
        )
    }
}
