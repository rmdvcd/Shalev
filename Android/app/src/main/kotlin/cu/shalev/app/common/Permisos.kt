package cu.shalev.app.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

class PermisoState(val estado: EstadoPermiso, val solicitar: () -> Unit)

/**
 * Permiso de CÁMARA para leer el QR de vinculación. [yaSolicitado] se persiste cifrado (ConfiguracionInicial) porque
 * Android no dice si el permiso se pidió alguna vez. Se re-evalúa al volver a primer plano (el usuario
 * pudo activarlo en Ajustes del sistema).
 */
@Composable
fun rememberPermisoCamara(yaSolicitado: Boolean, onSolicitado: () -> Unit): PermisoState {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val alSolicitar by rememberUpdatedState(onSolicitado)
    val estado = remember(tick, yaSolicitado) {
        estadoPermiso(
            tieneHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            concedido = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            yaSolicitado = yaSolicitado,
            sistemaSugiereExplicar = context.findActivity()
                ?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA) } ?: false,
        )
    }
    return PermisoState(estado) {
        alSolicitar()
        launcher.launch(Manifest.permission.CAMERA)
    }
}

/** Abre la ficha de Shalev en los ajustes del sistema (único camino cuando el permiso está BLOQUEADO). */
fun abrirAjustesDeLaApp(context: Context): Boolean = try {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (e: Exception) {
    false
}
