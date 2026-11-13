package cu.shalev.app.root

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cu.shalev.app.licencia.prueba.TrialTags
import cu.shalev.app.licencia.prueba.TrialViewModel
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.domain.repository.ConfiguracionInicialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Permisos que Shalev necesita, pedidos UNA vez al abrir la app por primera vez.
 * La lista sale de los permisos PELIGROSOS del manifiesto (los demás los da el sistema al instalar):
 *  - CAMERA: vincular por QR, fotos de productos y leer el QR de migración.
 *  - POST_NOTIFICATIONS: el aviso de turno abierto.
 *  - Fotos (READ_MEDIA_IMAGES desde Android 13; READ_EXTERNAL_STORAGE antes): el registro de prueba.
 */
object PermisosIniciales {
    fun lista(): List<String> = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

/** Textos del checklist de la primera apertura: qué permiso y para qué (una línea cada uno). */
object TextosPermisosIniciales {
    const val TITULO = "Permisos necesarios"
    const val CAMARA = "Cámara"
    const val CAMARA_DETALLE = "Escanear códigos QR y tomar fotos de productos y servicios."
    const val AVISO = "Aviso de turno"
    const val AVISO_DETALLE = "Mostrar «Turno abierto» en las notificaciones."
    const val FOTOS = "Fotos"
    const val FOTOS_DETALLE = "Conservar tu periodo de prueba si reinstalas la app."
    const val CONTINUAR = "Continuar"
    const val AHORA_NO = "Ahora no"
}

@HiltViewModel
class PermisosInicialesViewModel @Inject constructor(
    private val configuracion: ConfiguracionInicialRepository,
) : ViewModel() {
    /** null = todavía no se sabe (se está leyendo). */
    val pedir: StateFlow<Boolean?> = configuracion.estado.map { !it.permisosInicialesPedidos }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun yaPedidos() = viewModelScope.launch { configuracion.marcarPermisosInicialesPedidos() }
}

/**
 * Primera apertura: UNA ventana con el checklist de los permisos que Shalev necesita y para qué.
 * «Continuar» abre el diálogo del sistema y el usuario decide; «Ahora no» (o cerrar) no insiste:
 * cada función vuelve a pedir su permiso donde hace falta (cámara, fotos…). El aviso de
 * notificaciones solo sale en Android 13+. Se monta en [cu.shalev.app.root.ShalevRoot].
 */
@Composable
fun PermisosIniciales(
    iniciales: PermisosInicialesViewModel = hiltViewModel(),
    trial: TrialViewModel = hiltViewModel(),
) {
    val pedirIni by iniciales.pedir.collectAsStateWithLifecycle()
    val pedirTrial by trial.pedirPermiso.collectAsStateWithLifecycle()
    if (pedirIni != true && !pedirTrial) return
    var lanzado by rememberSaveable { mutableStateOf(false) }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Da igual lo que conteste: no se vuelve a pedir en masa ni se insiste.
        iniciales.yaPedidos()
        trial.alResponder()
    }
    val omitir = { iniciales.yaPedidos(); trial.descartar() }
    if (!lanzado) {
        ShalevDialog(
            title = TextosPermisosIniciales.TITULO,
            onDismiss = { lanzado = true; omitir() },
            onConfirm = { lanzado = true; lanzador.launch(PermisosIniciales.lista().toTypedArray()) },
            confirmDescription = TextosPermisosIniciales.CONTINUAR,
            dismissDescription = TextosPermisosIniciales.AHORA_NO,
            modifier = Modifier.testTag(TrialTags.DIALOGO),
            content = {
                ShalevListItem(
                    title = TextosPermisosIniciales.CAMARA, subtitle = TextosPermisosIniciales.CAMARA_DETALLE,
                    indicatorColor = null, leading = { Icon(ShalevIcons.Camara, contentDescription = null) },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ShalevListItem(
                        title = TextosPermisosIniciales.AVISO, subtitle = TextosPermisosIniciales.AVISO_DETALLE,
                        indicatorColor = null, leading = { Icon(ShalevIcons.Turno, contentDescription = null) },
                    )
                }
                ShalevListItem(
                    title = TextosPermisosIniciales.FOTOS, subtitle = TextosPermisosIniciales.FOTOS_DETALLE,
                    indicatorColor = null, leading = { Icon(ShalevIcons.Imagen, contentDescription = null) },
                )
            },
        )
    }
}
