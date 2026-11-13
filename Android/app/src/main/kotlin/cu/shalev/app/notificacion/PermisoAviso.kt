package cu.shalev.app.notificacion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * P29: pide POST_NOTIFICATIONS (Android 13+) en el momento en que tiene sentido: al abrir un turno. Devuelve la
 * acción que hay que llamar junto con «Abrir turno». Si se niega, la app funciona igual (solo no hay aviso);
 * el sistema deja de mostrar el diálogo tras dos negativas y no se insiste.
 */
@Composable
fun rememberPedirPermisoAviso(): () -> Unit {
    val context = LocalContext.current
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, lanzador) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                lanzador.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
