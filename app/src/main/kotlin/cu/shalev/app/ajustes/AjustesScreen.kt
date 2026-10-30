package cu.shalev.app.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cu.shalev.app.BuildConfig
import cu.shalev.app.common.abrirAjustesDeLaApp
import cu.shalev.app.actualizacion.texto
import cu.shalev.app.navigation.Route
import cu.shalev.designsystem.component.ShalevBadge
import cu.shalev.designsystem.component.ShalevCircularProgress
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.model.ResumenConfiguracion
import cu.shalev.domain.repository.PreferenciasRepository
import cu.shalev.domain.repository.PrincipalRepository
import cu.shalev.domain.repository.SecundariaRepository
import cu.shalev.domain.repository.TipoAppRepository
import cu.shalev.domain.model.PermisosApp
import cu.shalev.app.vinculacion.VinculacionLogic
import cu.shalev.domain.usecase.ObservarResumenConfiguracion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AjustesUiState(
    val resumen: ResumenConfiguracion? = null,
    /** P37: en una secundaria se ocultan los ajustes que pertenecen a la principal (licencia, perfil, respaldo…). */
    val permisos: PermisosApp = PermisosApp.PRINCIPAL,
    val secundarias: Int = 0,
    val nombreNegocio: String = "",
)

@HiltViewModel
class AjustesViewModel @Inject constructor(
    observarResumen: ObservarResumenConfiguracion,
    private val preferenciasRepo: PreferenciasRepository,
    tipoRepo: TipoAppRepository,
    principalRepo: PrincipalRepository,
    secundariaRepo: SecundariaRepository,
) : ViewModel() {

    val state: StateFlow<AjustesUiState> = combine(
        observarResumen(), preferenciasRepo.preferencias, tipoRepo.permisos, principalRepo.observarEmpleados(), secundariaRepo.estado,
    ) { r, p, permisos, empleados, sec ->
        AjustesUiState(r, permisos, empleados.size, sec.nombreNegocio)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AjustesUiState())
}

/** Subtítulo de "Completar configuración" (pura, testeada en AjustesTextosTest). */
fun ResumenConfiguracion?.subtitulo(): String = when {
    this == null -> "Cargando…"
    completa -> "Todo listo"
    pendientes.size == 1 -> "Falta 1 paso"
    else -> "Faltan ${pendientes.size} pasos"
}

object AjustesTags {
    const val CONFIGURACION = "ajustes.configuracion"
    const val PAGO = "ajustes.pago"
    const val RESPALDO = "ajustes.respaldo"
    const val AYUDA = "ajustes.ayuda"
    const val SOPORTE = "ajustes.soporte"
    const val VINCULACION = "ajustes.vinculacion"
    const val ACTUALIZACIONES = "ajustes.actualizaciones"
}

/** Ajustes: configuración pendiente, Perfil, Licencia, Pago electrónico, Precios, avisos, consultas, permisos, Respaldo, Ayuda y Soporte. */
@Composable
fun AjustesScreen(onNavigate: (Route) -> Unit, viewModel: AjustesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 0.26.0 (§6): sin interruptor (la consulta semanal es fija); queda «Buscar ahora».
    val actVm: cu.shalev.app.actualizacion.ActualizacionViewModel = hiltViewModel()
    val actualizacion by actVm.estado.collectAsStateWithLifecycle()
    val snackbar = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    androidx.compose.runtime.LaunchedEffect(actVm) { actVm.eventos.collect { snackbar.showSnackbar(it) } }
    // 0.27.0 (T11): acceso con clave (este teléfono); cambiarlo pide antes la huella o el PIN.
    val accesoVm: cu.shalev.app.acceso.AccesoViewModel = hiltViewModel()
    val accesoClave by accesoVm.activo.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val cambiarAcceso = cu.shalev.app.acceso.rememberCambiarAccesoClave(
        guardar = { accesoVm.guardar(it) },
        mensaje = { m -> scope.launch { snackbar.showSnackbar(m) } },
    )
    AjustesContent(
        accesoClave = accesoClave,
        onAccesoClave = cambiarAcceso,
        state = state,
        onNavigate = onNavigate,
        onPermisos = { abrirAjustesDeLaApp(context) },
        actualizacion = actualizacion,
        onBuscarAhora = actVm::buscarAhora,
        onActualizarAhora = actVm::actualizarAhora,
        onCerrarActualizar = actVm::cerrarProceso,
        snackbar = snackbar,
    )
}

@Composable
fun AjustesContent(
    state: AjustesUiState,
    onNavigate: (Route) -> Unit,
    onPermisos: () -> Unit,
    actualizacion: cu.shalev.app.actualizacion.EstadoActualizacion = cu.shalev.app.actualizacion.EstadoActualizacion(),
    onBuscarAhora: () -> Unit = {},
    /** 0.31.0 (P5): «Actualizar» (cuadro con barra circular) y cerrar ese cuadro. */
    onActualizarAhora: () -> Unit = {},
    onCerrarActualizar: () -> Unit = {},
    snackbar: androidx.compose.material3.SnackbarHostState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() },
    accesoClave: Boolean = false,
    onAccesoClave: (Boolean) -> Unit = {},
) {
    // 0.31.0 (P5): el cuadro de «Actualizar» lo abre el propio proceso (barra circular + resultado).
    // 1.0.1: al terminar con «Actualizado» o «Estas al día» el cuadro se cierra solo (el proceso lo cierra).
    val dialogoActualizaciones = actualizacion.proceso != null
    // 1.0.2: ayuda de la pestaña (botón «?» de la esquina superior izquierda).
    val ayudaPestana = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Scaffold(
        topBar = {
            ShalevTopBar(
                title = "Ajustes",
                onAyuda = { ayudaPestana.value = true },
                ayudaDescripcion = "Ayuda de Ajustes",
            )
        },
        snackbarHost = { cu.shalev.designsystem.component.ShalevSnackbarHost(snackbar) },
    ) { padding ->
        val principal = !state.permisos.esSecundaria
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (principal) item {
                val pendientes = state.resumen?.pendientes?.size ?: 0
                Entry(
                    "Completar configuración", state.resumen.subtitulo(), ShalevIcons.Configurar,
                    modifier = Modifier.testTag(AjustesTags.CONFIGURACION),
                    badge = pendientes.takeIf { it > 0 },
                ) { onNavigate(Route.ConfiguracionInicial()) }
            }
            if (principal) item { Entry("Perfil", "Nombre, apellidos y carné", ShalevIcons.Perfil) { onNavigate(Route.Perfil) } }
            if (principal) item { Entry("Licencia", "Estado, solicitud y activación", ShalevIcons.Licencia) { onNavigate(Route.Licencia) } }
            // P37: Sistema, debajo de Licencia.
            item {
                Entry(
                    VinculacionLogic.TITULO, VinculacionLogic.subtituloAjustes(state.permisos.tipo, state.secundarias, state.nombreNegocio),
                    ShalevIcons.Movil, modifier = Modifier.testTag(AjustesTags.VINCULACION),
                ) { onNavigate(Route.Vinculacion) }
            }
            if (principal) item {
                Entry("Pago electrónico", "Teléfonos y cuentas para cobrar por transferencia", ShalevIcons.PagoElectronico, modifier = Modifier.testTag(AjustesTags.PAGO)) {
                    onNavigate(Route.PagoElectronico)
                }
            }
            if (state.permisos.cambiarPrecios) item { Entry("Precios", "Por pago o por importe", ShalevIcons.Precios) { onNavigate(Route.Precios) } }
            if (principal) item {
                Entry("Avisos de inventario", "Cuándo avisar de stock bajo o crítico", ShalevIcons.Alerta) {
                    onNavigate(Route.ConfiguracionInicial(PasoConfiguracion.ALERTAS.name))
                }
            }
            item { Entry("Permisos", "", ShalevIcons.Bloqueo, onClick = onPermisos) }
            item { cu.shalev.app.acceso.FilaAccesoClave(accesoClave, onAccesoClave) } // 0.27.0 (T11)
            if (principal) item {
                Entry("Respaldo", "Copia cifrada de todo e importar", ShalevIcons.Respaldo, modifier = Modifier.testTag(AjustesTags.RESPALDO)) {
                    onNavigate(Route.Respaldo)
                }
            }
            // 0.31.0 (P5): se llama «Actualizar» y abre directamente el cuadro con la barra circular.
            item {
                Entry(
                    cu.shalev.app.actualizacion.TextosActualizacion.BUSCAR,
                    when {
                        actualizacion.version != null -> "Versión ${actualizacion.version} disponible"
                        else -> "Versión ${BuildConfig.VERSION_NAME}"
                    },
                    ShalevIcons.Sincronizar, modifier = Modifier.testTag(AjustesTags.ACTUALIZACIONES),
                ) { onActualizarAhora() }
            }
            item { Entry("Ayuda", "Manual breve de uso", ShalevIcons.Ayuda, modifier = Modifier.testTag(AjustesTags.AYUDA)) { onNavigate(Route.Ayuda) } }
            item { Entry("Soporte", "Datos de contacto del desarrollador", ShalevIcons.Soporte, modifier = Modifier.testTag(AjustesTags.SOPORTE)) { onNavigate(Route.Soporte) } }
        }
    }
    if (dialogoActualizaciones) {
        val T = cu.shalev.app.actualizacion.TextosActualizacion
        val proceso = actualizacion.proceso
        ShalevDialog(
            title = T.BUSCAR,
            onDismiss = onCerrarActualizar,
            onConfirm = null,
            dismissDescription = "Cerrar",
            content = {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (!actualizacion.repoConfigurado) {
                        cu.shalev.designsystem.component.ShalevSecondaryText(T.SIN_REPO, maxLines = 3, textAlign = TextAlign.Center)
                    } else {
                        ShalevCircularProgress(
                            progress = (proceso as? cu.shalev.app.actualizacion.ProcesoActualizar.EnCurso)?.progreso,
                            contentDescription = T.BUSCAR,
                        )
                        Text(
                            proceso?.texto().orEmpty(), style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                        cu.shalev.designsystem.component.ShalevSecondaryText(
                            T.BUSCAR_DETALLE_CORTO, maxLines = 4, textAlign = TextAlign.Center,
                        )
                    }
                }
            },
        )
    }

    if (ayudaPestana.value) cu.shalev.app.ayuda.DialogoAyudaPestana(
        pestana = cu.shalev.app.ayuda.PestanaAyuda.AJUSTES,
        modulos = state.permisos.modulos,
        onDismiss = { ayudaPestana.value = false },
        onVerManual = { ayudaPestana.value = false; onNavigate(Route.Ayuda) },
    )
}

@Composable
private fun Entry(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    badge: Int? = null,
    onClick: () -> Unit,
) {
    ShalevListItem(
        title = title,
        subtitle = subtitle,
        indicatorColor = null,
        leading = { Icon(icon, contentDescription = null) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                badge?.let { ShalevBadge(count = it) }
                Icon(ShalevIcons.Abrir, contentDescription = null)
            }
        },
        onClick = onClick,
        modifier = modifier,
    )
}

