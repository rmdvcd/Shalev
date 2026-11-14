package cu.shalev.app.navigation

import android.app.Activity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import cu.shalev.app.ajustes.AjustesScreen
import cu.shalev.app.ayuda.AyudaScreen
import cu.shalev.app.common.EntradaViewModel
import cu.shalev.app.insumos.InsumoFormScreen
import cu.shalev.app.servicios.ServicioFormScreen
import cu.shalev.app.servicios.ServiciosScreen
import cu.shalev.app.servicios.ServiciosViewModel
import cu.shalev.app.vinculacion.qr.QrVinculacionScreen
import cu.shalev.app.inicio.AlertaPendiente
import cu.shalev.app.inicio.InicioScreen
import cu.shalev.app.inicio.TipoVenta
import cu.shalev.app.inventario.InventarioScreen
import cu.shalev.app.inventario.InventarioViewModel
import cu.shalev.app.inventario.SeleccionVenta
import cu.shalev.app.licencia.LicenciaScreen
import cu.shalev.app.vinculacion.VinculacionScreen
import cu.shalev.app.onboarding.ModoWizard
import cu.shalev.app.onboarding.OnboardingScreen
import cu.shalev.app.pagos.PagoElectronicoScreen
import cu.shalev.app.perfil.PerfilScreen
import cu.shalev.app.precios.PreciosScreen
import cu.shalev.app.producto.ProductoFormScreen
import cu.shalev.app.registros.RegistrosScreen
import cu.shalev.app.registros.TurnoDetalleScreen
import cu.shalev.app.respaldo.RespaldoScreen
import cu.shalev.app.soporte.SoporteScreen
import cu.shalev.app.venta.VentaScreen
import cu.shalev.designsystem.component.ShalevNavItem
import cu.shalev.designsystem.component.ShalevNavigationBar
import cu.shalev.designsystem.token.ShalevMotion
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.model.TipoAlerta

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun MainScaffold(snapshot: Licencia) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination
    // Inventario/Servicios en modo «elegir qué vender» son pantallas completas (sin barra inferior).
    val enSeleccionVenta = entry?.enSeleccionVenta() == true
    // P37: permisos de esta app (todo en la principal; lo que permitió el dueño en una secundaria).
    val permisosVm: cu.shalev.app.common.PermisosAppViewModel = hiltViewModel()
    val permisos by permisosVm.permisos.collectAsStateWithLifecycle()
    // 0.21.0 (C12): solo las secciones de los módulos del negocio.
    val secciones = remember(permisos) { topLevelVisibles(permisos) }
    val selected = secciones.firstOrNull { current.isIn(it.route) }?.takeUnless { enSeleccionVenta }
    val items = remember(secciones) { secciones.map { ShalevNavItem(it.name, it.icon, it.label) } }
    // P29: deslizar a izquierda/derecha cambia de sección.
    val bloqueoGestos = remember { cu.shalev.designsystem.component.BloqueoGestos() }
    val enRutaRaiz = current != null && TopLevel.entries.any { current.hasRoute(it.route::class) }
    val deslizar = deslizarPermitido(enRutaRaiz, enSeleccionVenta, bloqueoGestos.activo)

    // 0.25.0: un mensaje de licencia compartido con Shalev abre Licencia, que lo activa sola.
    // (Los .shalev solo entran por el gestor de archivos desde Respaldo: lo compartido desde otras apps se ignora.)
    val entradaVm: EntradaViewModel = hiltViewModel()
    val licenciaPendiente by entradaVm.licenciaPendiente.collectAsStateWithLifecycle()
    LaunchedEffect(licenciaPendiente) {
        if (licenciaPendiente && nav.currentDestination?.hasRoute(Route.Licencia::class) != true) {
            nav.navigate(Route.Licencia) { launchSingleTop = true }
        }
    }

    // La dirección queda asociada al origen y destino de este gesto, no a otras navegaciones.
    var gesto by remember { mutableStateOf<GestoSeccion?>(null) }
    // 0.26.0 (P73 §6): plazo de la actualización vencido → bloqueo de toda la app. Nunca en mitad de una venta (ni al
    // elegir artículos para ella) ni en Respaldo (salida «Exportar respaldo», suposición 3).
    val bloqueoVm: cu.shalev.app.actualizacion.ActualizacionViewModel = hiltViewModel()
    val actualizacion by bloqueoVm.estado.collectAsStateWithLifecycle()
    val permitidaConBloqueo = enSeleccionVenta || current?.hasRoute(Route.Venta::class) == true ||
        current?.hasRoute(Route.QrVinculacion::class) == true || current?.hasRoute(Route.Respaldo::class) == true
    val bloquear = actualizacion.bloqueada && !permitidaConBloqueo

    // Plan UI/UX/IX 2026-11 (SK-4, piloto): en teléfono (Compact) todo queda como estaba; en tablet/plegable
    // (Medium/Expanded) el contenido se centra con el ancho máximo de lectura (P18/A22). La navegación no cambia:
    // la barra inferior sigue a todo lo ancho y el deslizar entre secciones se detecta en toda la pantalla.
    val anchoClase = (LocalContext.current as? Activity)
        ?.let { calculateWindowSizeClass(it).widthSizeClass } ?: WindowWidthSizeClass.Compact

    CompositionLocalProvider(cu.shalev.app.common.LocalPermisosApp provides permisos, cu.shalev.designsystem.component.LocalBloqueoGestos provides bloqueoGestos) {
    Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().then(if (bloquear) Modifier.clearAndSetSemantics { } else Modifier)) {
    Scaffold(
        bottomBar = {
            // La barra aparece en los cinco destinos principales, incluido Ajustes.
            AnimatedVisibility(
                visible = selected != null,
                enter = slideInVertically(ShalevMotion.muelleEntrada()) { it },
                exit = slideOutVertically(ShalevMotion.muelle()) { it },
            ) {
                ShalevNavigationBar(
                    items = items,
                    selectedKey = selected?.name,
                    // 0.31.0 (P10): tocar la barra es un fundido (gesto = 0), no un desplazamiento.
                    onSelect = { item -> gesto = null; nav.navigateTopLevel(TopLevel.valueOf(item.key).route) },
                )
            }
        },
    ) { padding ->
        // 0.21.0 (C12): si en el recorrido inicial se eligió «Secundaria», se abre la vinculación al entrar.
        LaunchedEffect(Unit) {
            if (cu.shalev.app.onboarding.TourPendiente.consumirVincularSecundaria()) nav.navigateTo(Route.VincularSecundaria)
        }
        // 0.25.0 (§3.4, §6.2): consulta semanal (revocaciones + versión) al abrir la app; nada en segundo plano.
        val actualizacionVm: cu.shalev.app.actualizacion.ActualizacionViewModel = hiltViewModel()
        LaunchedEffect(Unit) { actualizacionVm.alAbrir() }
        Box(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .deslizarEntreSecciones(activo = deslizar && selected != null) { dir ->
                    val actual = selected ?: return@deslizarEntreSecciones
                    secciones.getOrNull(secciones.indexOf(actual) + dir)?.let {
                        // 0.31.0 (P10): el desplazamiento lateral lleva la dirección del dedo.
                        gesto = entry?.let { e -> GestoSeccion(e.id, it.name, dir) }
                        nav.navigateTopLevel(it.route)
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
        NavHost(
            navController = nav,
            startDestination = Route.Inicio,
            // SK-4: en Medium/Expanded el contenido se limita al ancho de lectura y queda centrado en el Box.
            modifier = if (anchoClase == WindowWidthSizeClass.Compact) {
                Modifier.fillMaxSize()
            } else {
                Modifier.widthIn(max = ShalevSize.contentMaxWidth).fillMaxHeight()
            },
            // P18 (A17): al entrar en una pantalla de detalle, desliza 1/4 del ancho hacia la izquierda y al volver,
            // hacia la derecha (muelle de entrada blando en el recorrido, FastOutSlowIn en el fundido).
            // 0.31.0 (P10): entre secciones, el DESLIZAMIENTO lleva ese mismo desplazamiento lateral (con la
            // dirección del dedo) y el TOQUE en la barra inferior es un fundido. Con «Quitar animaciones» del
            // sistema Compose acorta todo a 0.
            enterTransition = {
                when {
                    !entrePestanas() -> ShalevMotion.screenForwardEnter
                    direccionGesto(gesto) == CambioSeccion.ATRAS -> ShalevMotion.screenBackEnter
                    direccionGesto(gesto) == CambioSeccion.ADELANTE -> ShalevMotion.screenForwardEnter
                    else -> ShalevMotion.screenEnter
                }
            },
            exitTransition = {
                if (!entrePestanas()) ShalevMotion.screenForwardExit else when (direccionGesto(gesto)) {
                    CambioSeccion.FUNDIDO -> ShalevMotion.screenExit
                    CambioSeccion.ADELANTE -> ShalevMotion.screenForwardExit
                    CambioSeccion.ATRAS -> ShalevMotion.screenBackExit
                }
            },
            popEnterTransition = {
                when {
                    !entrePestanas() -> ShalevMotion.screenBackEnter
                    direccionGesto(gesto) == CambioSeccion.ATRAS -> ShalevMotion.screenBackEnter
                    direccionGesto(gesto) == CambioSeccion.ADELANTE -> ShalevMotion.screenForwardEnter
                    else -> ShalevMotion.screenEnter
                }
            },
            popExitTransition = {
                if (!entrePestanas()) ShalevMotion.screenBackExit else when (direccionGesto(gesto)) {
                    CambioSeccion.FUNDIDO -> ShalevMotion.screenExit
                    CambioSeccion.ADELANTE -> ShalevMotion.screenForwardExit
                    CambioSeccion.ATRAS -> ShalevMotion.screenBackExit
                }
            },
        ) {
            composable<Route.Inicio> { e ->
                val mensaje = e.savedStateHandle.getStateFlow<String?>(KEY_INICIO_MENSAJE, null).collectAsStateWithLifecycle().value
                InicioScreen(
                    onNavigate = nav::navigateTo,
                    onVerAlerta = { tipo -> gesto = null; nav.abrirAlertaInventario(tipo) },
                    mensajeInicial = mensaje,
                    onMensajeMostrado = { e.savedStateHandle[KEY_INICIO_MENSAJE] = null },
                )
            }
            composable<Route.Inventario> { e ->
                // El filtro por alerta llega por `AlertaPendiente` y lo lee el ViewModel.
                val mensaje = e.savedStateHandle.getStateFlow<String?>(KEY_PRODUCTO_GUARDADO, null).collectAsStateWithLifecycle().value
                InventarioScreen(
                    onNavigate = nav::navigateTo,
                    mensajeInicial = mensaje,
                    onMensajeMostrado = { e.savedStateHandle[KEY_PRODUCTO_GUARDADO] = null },
                    // Modo venta: devuelve la selección a la venta por el back stack.
                    onSeleccionVenta = { ids ->
                        nav.previousBackStackEntry?.savedStateHandle?.set(KEY_VENTA_SELECCION, ids.toLongArray())
                        nav.popBackStack()
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<Route.ProductoForm> {
                ProductoFormScreen(
                    onBack = { nav.popBackStack() },
                    onGuardado = { msg ->
                        // Vuelve a Inventario y muestra el aviso flotante de «Producto agregado».
                        cu.shalev.app.common.AvisoExito.mostrar(msg)
                        val volvio = nav.popBackStack<Route.Inventario>(inclusive = false)
                        if (!volvio) nav.navigate(Route.Inventario()) { popUpTo<Route.ProductoForm> { inclusive = true } }
                    },
                    // P31: la categoría «Insumos» sustituye este formulario por el de insumo (Atrás no vuelve aquí).
                    onCrearInsumo = { nombre ->
                        nav.navigate(Route.InsumoForm(nombre = nombre.ifBlank { null })) { popUpTo<Route.ProductoForm> { inclusive = true } }
                    },
                )
            }
            composable<Route.Servicios> { e ->
                val mensaje = e.savedStateHandle.getStateFlow<String?>(KEY_SERVICIO_MENSAJE, null).collectAsStateWithLifecycle().value
                ServiciosScreen(
                    onNavigate = nav::navigateTo,
                    mensajeInicial = mensaje,
                    onMensajeMostrado = { e.savedStateHandle[KEY_SERVICIO_MENSAJE] = null },
                    // Modo venta: devuelve la selección a la venta por el back stack.
                    onSeleccionVenta = { ids ->
                        nav.previousBackStackEntry?.savedStateHandle?.set(KEY_VENTA_SELECCION, ids.toLongArray())
                        nav.popBackStack()
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable<Route.ServicioForm> {
                ServicioFormScreen(
                    onBack = { nav.popBackStack() },
                    onGuardado = { msg ->
                        cu.shalev.app.common.AvisoExito.mostrar(msg)
                        nav.popBackStack()
                    },
                )
            }
            composable<Route.InsumoForm> {
                InsumoFormScreen(
                    onBack = { nav.popBackStack() },
                    onGuardado = { msg ->
                        // P29: los insumos viven en Inventario; al guardar se vuelve allí con el aviso.
                        cu.shalev.app.common.AvisoExito.mostrar(msg)
                        val volvio = nav.popBackStack<Route.Inventario>(inclusive = false)
                        if (!volvio) nav.navigate(Route.Inventario()) { popUpTo<Route.InsumoForm> { inclusive = true } }
                    },
                )
            }
            composable<Route.Registros> {
                RegistrosScreen(onNavigate = nav::navigateTo)
            }
            composable<Route.TurnoDetalle> { TurnoDetalleScreen(onBack = { nav.popBackStack() }) }
            composable<Route.Ajustes> { AjustesScreen(onNavigate = nav::navigateTo, onBack = { nav.popBackStack() }) }
            composable<Route.Venta> { e ->
                val tipo = e.toRoute<Route.Venta>().tipo
                val seleccion = e.savedStateHandle.getStateFlow<LongArray?>(KEY_VENTA_SELECCION, null).collectAsStateWithLifecycle().value
                VentaScreen(
                    tipo = TipoVenta.entries.firstOrNull { it.name == tipo } ?: TipoVenta.VENTA,
                    onBack = { nav.popBackStack() },
                    onElegirProductos = { t, pre ->
                        val csv = SeleccionVenta.csv(pre).ifEmpty { null }
                        // P29: la venta de servicios elige en Servicios; la de productos, en Inventario.
                        // launchSingleTop: una doble pulsación no apila la selección dos veces.
                        nav.navigate(if (t == TipoVenta.SERVICIO) Route.Servicios(venta = true, seleccion = csv) else Route.Inventario(venta = t.name, seleccion = csv)) { launchSingleTop = true }
                    },
                    onTerminada = { msg ->
                        // Vuelve a Inicio (gráficos al día) y muestra el aviso flotante de venta registrada.
                        cu.shalev.app.common.AvisoExito.mostrar(msg)
                        if (!nav.popBackStack<Route.Inicio>(inclusive = false)) nav.navigateTopLevel(Route.Inicio)
                    },
                    onConfigurarPago = { nav.navigate(Route.PagoElectronico) { launchSingleTop = true } },
                    seleccion = seleccion?.toList(),
                    onSeleccionConsumida = { e.savedStateHandle[KEY_VENTA_SELECCION] = null },
                )
            }
            composable<Route.Precios> { PreciosScreen(onBack = { nav.popBackStack() }) }
            composable<Route.PagoElectronico> { PagoElectronicoScreen(onBack = { nav.popBackStack() }) }
            composable<Route.Licencia> {
                LicenciaScreen(onBack = { nav.popBackStack() })
            }
            composable<Route.Vinculacion> { e ->
                val qr = e.savedStateHandle.getStateFlow<String?>(KEY_QR_LEIDO, null).collectAsStateWithLifecycle().value
                VinculacionScreen(
                    onBack = { nav.popBackStack() },
                    onEscanear = { nav.navigateTo(Route.QrVinculacion) },
                    qrLeido = qr,
                    onQrConsumido = { e.savedStateHandle[KEY_QR_LEIDO] = null },
                )
            }
            composable<Route.VincularSecundaria> { e ->
                val qr = e.savedStateHandle.getStateFlow<String?>(KEY_QR_LEIDO, null).collectAsStateWithLifecycle().value
                VinculacionScreen(
                    onBack = { nav.popBackStack() },
                    onEscanear = { nav.navigateTo(Route.QrVinculacion) },
                    qrLeido = qr,
                    onQrConsumido = { e.savedStateHandle[KEY_QR_LEIDO] = null },
                    abrirSecundaria = true,
                )
            }
            composable<Route.QrVinculacion> {
                QrVinculacionScreen(
                    onBack = { nav.popBackStack() },
                    onQrLeido = { c ->
                        nav.previousBackStackEntry?.savedStateHandle?.set(KEY_QR_LEIDO, c)
                        nav.popBackStack()
                    },
                )
            }
            composable<Route.ConfiguracionInicial> { entry ->
                val paso = entry.toRoute<Route.ConfiguracionInicial>().paso
                OnboardingScreen(
                    modo = ModoWizard.RETOMAR,
                    soloPaso = PasoConfiguracion.entries.firstOrNull { it.name == paso },
                    onTerminar = { nav.popBackStack() },
                )
            }
            composable<Route.Perfil> { PerfilScreen(onBack = { nav.popBackStack() }) }
            composable<Route.Respaldo> { RespaldoScreen(onBack = { nav.popBackStack() }) }
            composable<Route.Ayuda> { AyudaScreen(onBack = { nav.popBackStack() }) }
            composable<Route.Soporte> { SoporteScreen(onBack = { nav.popBackStack() }) }
        }
        }
    }
    }
    if (bloquear) {
        cu.shalev.app.actualizacion.BloqueoActualizacionRuta(
            estado = actualizacion, actualizacion = bloqueoVm,
            onExportarRespaldo = { nav.navigate(Route.Respaldo) { launchSingleTop = true } },
        )
    }
    // 1.0.4: cuadro flotante de «operación realizada» (guardar, agregar, eliminar…), común a todas las pantallas.
    cu.shalev.app.common.AvisoExitoHost(
        Modifier
            .align(androidx.compose.ui.Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = cu.shalev.designsystem.token.ShalevSpacing.xs),
    )
    }
    }
}

private const val KEY_PRODUCTO_GUARDADO = "producto_guardado"
private const val KEY_QR_LEIDO = "qr_leido"
private const val KEY_SERVICIO_MENSAJE = "servicio_mensaje"
private const val KEY_VENTA_SELECCION = "venta_seleccion"
private const val KEY_INICIO_MENSAJE = "inicio_mensaje"

private fun NavDestination?.isIn(route: Route): Boolean =
    this?.hierarchy?.any { it.hasRoute(route::class) } == true

private fun NavBackStackEntry.esPrincipal(): Boolean =
    !enSeleccionVenta() && TopLevel.entries.any { destination.isIn(it.route) }

/** Inventario (venta = TipoVenta) o Servicios (venta = true) abiertos para elegir qué vender. */
private fun NavBackStackEntry.enSeleccionVenta(): Boolean =
    if (destination.hasRoute(Route.Servicios::class)) arguments?.getBoolean(ServiciosViewModel.KEY_VENTA) == true
    else arguments?.getString(InventarioViewModel.KEY_VENTA) != null

private fun AnimatedContentTransitionScope<NavBackStackEntry>.entrePestanas(): Boolean =
    initialState.esPrincipal() && targetState.esPrincipal()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.direccionGesto(gesto: GestoSeccion?): CambioSeccion =
    cambioSeccion(gesto, initialState.id, TopLevel.entries.firstOrNull { targetState.destination.isIn(it.route) }?.name)

/** Cambio de destino principal: una sola instancia por destino, conserva estado. */
private fun NavHostController.navigateTopLevel(route: Route) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/**
 * 0.31.0: contar una alerta de Inicio abre la pestaña Inventario con el filtro. Antes se empujaba
 * `Inventario(alerta)` como pantalla de detalle —el **mismo destino** que la pestaña—, y la barra inferior y el
 * gesto volvían a poner esa entrada encima al ir a cualquier sección (Inicio incluido): no se llegaba a Inicio y el
 * Inventario se quedaba filtrado. Ahora se abre la pestaña y el ViewModel recibe la alerta por [AlertaPendiente]
 * (escribir en el `SavedStateHandle` de la entrada no llegaba al ViewModel: Hilt le inyecta otro).
 */
private fun NavHostController.abrirAlertaInventario(tipo: TipoAlerta) {
    AlertaPendiente.pedir(tipo)
    navigateTopLevel(Route.Inventario())
}

private fun NavHostController.navigateTo(route: Route) = navigate(route) { launchSingleTop = true }
