package cu.shalev.app.root

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cu.shalev.app.licencia.prueba.TrialTags
import cu.shalev.app.licencia.prueba.TrialViewModel
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevTextButton
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.repository.ConfiguracionInicialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Para qué sirve cada permiso de la primera apertura (el icono lo pone la vista). */
enum class TipoPermisoInicial(val titulo: String, val detalle: String) {
    CAMARA("Cámara", "Escanear códigos QR y tomar fotos de productos y servicios."),
    NOTIFICACIONES("Notificaciones", "Avisarte del turno abierto y del avance de sincronizaciones, actualizaciones y respaldos."),
    FOTOS("Fotos", "Conservar tu periodo de prueba si reinstalas la app."),
}

/** Un permiso del checklist: el permiso de Android y para qué se pide. */
data class PermisoInicial(val permiso: String, val tipo: TipoPermisoInicial)

/**
 * Permisos que Shalev necesita, pedidos UNA vez al abrir la app por primera vez.
 * La lista sale de los permisos PELIGROSOS del manifiesto (los demás los da el sistema al instalar):
 *  - CAMERA: vincular por QR, fotos de productos y leer el QR de migración.
 *  - POST_NOTIFICATIONS (Android 13+): turno abierto, sincronización, actualización y respaldos.
 *  - Fotos (READ_MEDIA_IMAGES desde Android 13; READ_EXTERNAL_STORAGE antes): el registro de prueba.
 */
object PermisosIniciales {
    fun lista(sdk: Int = Build.VERSION.SDK_INT): List<PermisoInicial> = buildList {
        add(PermisoInicial(Manifest.permission.CAMERA, TipoPermisoInicial.CAMARA))
        if (sdk >= Build.VERSION_CODES.TIRAMISU) {
            add(PermisoInicial(Manifest.permission.POST_NOTIFICATIONS, TipoPermisoInicial.NOTIFICACIONES))
            add(PermisoInicial(Manifest.permission.READ_MEDIA_IMAGES, TipoPermisoInicial.FOTOS))
        } else {
            add(PermisoInicial(Manifest.permission.READ_EXTERNAL_STORAGE, TipoPermisoInicial.FOTOS))
        }
    }
}

/** Cómo va cada fila del checklist. */
enum class EstadoPermiso(val etiqueta: String) {
    PENDIENTE("Pendiente"),
    PIDIENDO("Pidiendo"),
    CONCEDIDO("Permitido"),
    DENEGADO("Denegado"),
}

/**
 * El checklist de la primera apertura, sin Android (se prueba en la JVM): los permisos se piden **uno a uno**, en
 * el orden de la lista, y cada fila se marca en cuanto el usuario responde al diálogo del sistema. Los que ya
 * estaban concedidos salen marcados desde el principio y no se vuelven a pedir.
 */
data class ChecklistPermisos(val items: List<PermisoInicial>, val estados: Map<String, EstadoPermiso>) {
    fun estado(permiso: String): EstadoPermiso = estados[permiso] ?: EstadoPermiso.PENDIENTE

    /** El que tiene abierto ahora el diálogo del sistema. */
    val pidiendo: PermisoInicial? get() = items.firstOrNull { estado(it.permiso) == EstadoPermiso.PIDIENDO }

    /** El próximo por pedir. */
    val siguiente: PermisoInicial? get() = items.firstOrNull { estado(it.permiso) == EstadoPermiso.PENDIENTE }

    /** Todos respondidos (o concedidos de antes): el botón pasa a «Listo». */
    val terminado: Boolean get() = pidiendo == null && siguiente == null

    val concedidos: Int get() = items.count { estado(it.permiso) == EstadoPermiso.CONCEDIDO }
    val denegados: Int get() = items.count { estado(it.permiso) == EstadoPermiso.DENEGADO }

    fun pedir(permiso: String): ChecklistPermisos =
        if (estado(permiso) == EstadoPermiso.PENDIENTE) copy(estados = estados + (permiso to EstadoPermiso.PIDIENDO)) else this

    /** Respuesta del diálogo del sistema. Solo cuenta para el permiso que se estaba pidiendo. */
    fun responder(permiso: String, concedido: Boolean): ChecklistPermisos =
        if (estado(permiso) != EstadoPermiso.PIDIENDO) this
        else copy(estados = estados + (permiso to if (concedido) EstadoPermiso.CONCEDIDO else EstadoPermiso.DENEGADO))

    companion object {
        fun inicial(items: List<PermisoInicial>, yaConcedido: (String) -> Boolean) = ChecklistPermisos(
            items,
            items.associate { it.permiso to if (yaConcedido(it.permiso)) EstadoPermiso.CONCEDIDO else EstadoPermiso.PENDIENTE },
        )

        /** Sobrevive al giro de pantalla y a la muerte del proceso mientras el diálogo del sistema está delante. */
        fun saver(items: List<PermisoInicial>): Saver<ChecklistPermisos, ArrayList<String>> = Saver(
            save = { c -> ArrayList(c.items.map { c.estado(it.permiso).name }) },
            restore = { nombres -> ChecklistPermisos(items, items.zip(nombres).associate { (p, n) -> p.permiso to EstadoPermiso.valueOf(n) }) },
        )
    }
}

/** Textos del checklist de la primera apertura. */
object TextosPermisosIniciales {
    const val TITULO = "Permisos necesarios"
    const val EXPLICACION = "Te los pediremos uno a uno. Puedes negar cualquiera: la función que lo usa te lo volverá a pedir."
    const val CONTINUAR = "Continuar"
    const val PIDIENDO = "Pidiendo"
    const val LISTO = "Listo"
    const val OMITIR = "Omitir"
    const val OMITIR_DESCRIPCION = "Ahora no, seguir sin dar permisos"
    const val AJUSTES = "Lo que negaste puedes activarlo después en Ajustes de Android, Apps, Shalev, Permisos."

    fun resumen(concedidos: Int, total: Int): String = "Permitidos: $concedidos de $total."
}

object PermisosTags {
    const val CONTINUAR = "permisos_continuar"
    const val OMITIR = "permisos_omitir"
    fun fila(permiso: String) = "permisos_fila_$permiso"
    fun estado(permiso: String) = "permisos_estado_$permiso"
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
 * Primera apertura: UNA ventana con el checklist de los permisos que Shalev necesita y para qué. «Continuar» los
 * pide uno a uno y cada fila se marca (Permitido o Denegado) según responde el usuario; al terminar, «Listo».
 * «Omitir» (o cerrar antes de empezar) no insiste: cada función vuelve a pedir su permiso donde hace falta.
 * Se monta en [cu.shalev.app.root.ShalevRoot].
 */
@Composable
fun PermisosIniciales(
    iniciales: PermisosInicialesViewModel = hiltViewModel(),
    trial: TrialViewModel = hiltViewModel(),
) {
    val pedirIni by iniciales.pedir.collectAsStateWithLifecycle()
    val pedirTrial by trial.pedirPermiso.collectAsStateWithLifecycle()
    var cerrado by rememberSaveable { mutableStateOf(false) }
    if (cerrado || (pedirIni != true && !pedirTrial)) return
    val context = LocalContext.current
    ChecklistPermisosIniciales(
        items = remember { PermisosIniciales.lista() },
        yaConcedido = { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED },
        // Da igual lo que conteste: no se vuelve a pedir en masa ni se insiste. Se relee el registro de prueba.
        onTerminar = { cerrado = true; iniciales.yaPedidos(); trial.alResponder() },
        onOmitir = { cerrado = true; iniciales.yaPedidos(); trial.descartar() },
    )
}

/** Pausa para que se vea la marca de la fila antes de que el siguiente diálogo del sistema la tape. */
internal const val PAUSA_ENTRE_PERMISOS_MS = 600L

@Composable
internal fun ChecklistPermisosIniciales(
    items: List<PermisoInicial>,
    yaConcedido: (String) -> Boolean,
    onTerminar: () -> Unit,
    onOmitir: () -> Unit,
) {
    var check by rememberSaveable(stateSaver = ChecklistPermisos.saver(items)) { mutableStateOf(ChecklistPermisos.inicial(items, yaConcedido)) }
    var enCurso by rememberSaveable { mutableStateOf(false) }
    val terminar by rememberUpdatedState(onTerminar)
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        check.pidiendo?.let { check = check.responder(it.permiso, concedido) }
    }
    // Todo concedido de antes: no hay nada que preguntar.
    LaunchedEffect(Unit) { if (check.terminado && !enCurso) terminar() }
    // Encadena los diálogos del sistema: en cuanto se marca una fila, se pide la siguiente.
    LaunchedEffect(enCurso, check) {
        if (!enCurso || check.pidiendo != null) return@LaunchedEffect
        val siguiente = check.siguiente ?: return@LaunchedEffect
        delay(PAUSA_ENTRE_PERMISOS_MS)
        check = check.pedir(siguiente.permiso)
        lanzador.launch(siguiente.permiso)
    }
    val terminado = check.terminado
    ShalevDialog(
        title = TextosPermisosIniciales.TITULO,
        // Antes de empezar, cerrar es «Omitir»; al terminar, «Listo». Con un diálogo del sistema delante no se llega.
        onDismiss = { if (terminado) onTerminar() else if (!enCurso) onOmitir() },
        onConfirm = { if (terminado) onTerminar() else enCurso = true },
        confirmDescription = when {
            terminado -> TextosPermisosIniciales.LISTO
            enCurso -> TextosPermisosIniciales.PIDIENDO
            else -> TextosPermisosIniciales.CONTINUAR
        },
        confirmLoading = enCurso && !terminado,
        confirmTag = PermisosTags.CONTINUAR,
        confirmIcon = if (terminado) ShalevIcons.Confirmar else ShalevIcons.Siguiente,
        modifier = Modifier.testTag(TrialTags.DIALOGO),
        secundarias = if (enCurso || terminado) null else {
            {
                ShalevTextButton(
                    text = TextosPermisosIniciales.OMITIR_DESCRIPCION, etiqueta = TextosPermisosIniciales.OMITIR,
                    onClick = onOmitir, icon = ShalevIcons.Saltar, mostrarTexto = true,
                    modifier = Modifier.testTag(PermisosTags.OMITIR),
                )
            }
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                Text(
                    if (terminado) TextosPermisosIniciales.resumen(check.concedidos, items.size) else TextosPermisosIniciales.EXPLICACION,
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                items.forEach { p -> FilaPermiso(p, check.estado(p.permiso)) }
                if (terminado && check.denegados > 0) {
                    Text(
                        TextosPermisosIniciales.AJUSTES, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

private fun TipoPermisoInicial.icono(): ImageVector = when (this) {
    TipoPermisoInicial.CAMARA -> ShalevIcons.Camara
    TipoPermisoInicial.NOTIFICACIONES -> ShalevIcons.Turno
    TipoPermisoInicial.FOTOS -> ShalevIcons.Imagen
}

@Composable
private fun FilaPermiso(p: PermisoInicial, estado: EstadoPermiso) {
    ShalevListItem(
        title = p.tipo.titulo,
        subtitle = p.tipo.detalle,
        subtitleMaxLines = 3,
        indicatorColor = null,
        leading = { Icon(p.tipo.icono(), contentDescription = null) },
        trailing = { MarcaPermiso(p.permiso, estado) },
        modifier = Modifier.testTag(PermisosTags.fila(p.permiso)),
    )
}

/** La marca del checklist: icono + palabra + color (el estado no se comunica solo con el color). */
@Composable
private fun MarcaPermiso(permiso: String, estado: EstadoPermiso) {
    val cs = MaterialTheme.colorScheme
    val color: Color = when (estado) {
        EstadoPermiso.CONCEDIDO -> ShalevTheme.colors.success
        EstadoPermiso.DENEGADO -> cs.error
        EstadoPermiso.PIDIENDO -> cs.primary
        EstadoPermiso.PENDIENTE -> cs.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        // TalkBack anuncia el cambio («Permitido», «Denegado») sin mover el foco.
        modifier = Modifier.testTag(PermisosTags.estado(permiso)).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        when (estado) {
            EstadoPermiso.PIDIENDO -> CircularProgressIndicator(Modifier.size(ShalevSize.icon), color = color)
            else -> Icon(
                when (estado) {
                    EstadoPermiso.CONCEDIDO -> ShalevIcons.AvisoExito
                    EstadoPermiso.DENEGADO -> ShalevIcons.AvisoError
                    else -> ShalevIcons.Reloj
                },
                contentDescription = null, tint = color, modifier = Modifier.size(ShalevSize.icon),
            )
        }
        Text(estado.etiqueta, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
