package cu.shalev.app.onboarding

import cu.shalev.designsystem.component.ShalevCheckbox
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import cu.shalev.designsystem.component.ShalevListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.common.SecureWindow
import cu.shalev.core.money.Money
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevBarraAcciones
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevLoading
import cu.shalev.designsystem.component.ShalevLogo
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.ShalevStepper
import cu.shalev.designsystem.component.ShalevTextButton
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.shalevContentWidth
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.ShalevTextos
import cu.shalev.designsystem.token.ShalevMotion
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.DatosIniciales
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.licencia.LicenseState
import cu.shalev.licencia.contract.TipoLicencia

/**
 * Asistente de primera ejecución (y "Completar configuración" desde Ajustes).
 * [onTerminar] solo se usa en modo RETOMAR: en PRIMERA_VEZ, RootViewModel cambia de estado solo.
 */
@Composable
fun OnboardingScreen(
    modo: ModoWizard,
    onTerminar: () -> Unit,
    soloPaso: PasoConfiguracion? = null,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel) { viewModel.iniciar(modo, soloPaso) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                OnboardingEvento.Terminado -> onTerminar()
                is OnboardingEvento.Mensaje -> snackbar.showSnackbar(e.texto)
            }
        }
    }
    // Atrás del sistema = paso anterior (en el primer paso: salir, como siempre en Android).
    BackHandler(enabled = state.puedeVolver) { viewModel.atras() }
    if (state.paso == PasoWizard.DATOS) SecureWindow() // CI y teléfono

    OnboardingContent(
        state = state,
        acciones = OnboardingAcciones(
            onSiguiente = viewModel::siguiente,
            onAtras = viewModel::atras,
            onOmitirPaso = viewModel::omitirPaso,
            onOmitirTodo = viewModel::omitirTodo,
            onDatos = viewModel::editarDatos,
            onNivel = viewModel::editarNivel,
            onSumarNivel = viewModel::sumarNivel,
            onRestablecerNiveles = viewModel::restablecerNiveles,
            onTipo = viewModel::elegirTipo,
            onModulo = viewModel::alternarModulo,
            onEmpleados = viewModel::cambiarEmpleados,
            onAccesoClave = cu.shalev.app.acceso.rememberCambiarAccesoClave(
                guardar = viewModel::guardarAccesoClave,
                mensaje = { m -> scope.launch { snackbar.showSnackbar(m) } },
            ),
        ),
        snackbar = snackbar,
    )
}

class OnboardingAcciones(
    val onSiguiente: () -> Unit,
    val onAtras: () -> Unit,
    val onOmitirPaso: () -> Unit,
    val onOmitirTodo: () -> Unit,
    val onDatos: ((DatosIniciales) -> DatosIniciales) -> Unit,
    val onNivel: (CampoNivel, String) -> Unit,
    val onSumarNivel: (CampoNivel, Int) -> Unit,
    val onRestablecerNiveles: () -> Unit,
    // 0.21.0 (C12): recorrido inicial.
    val onTipo: (Boolean) -> Unit = {},
    val onModulo: (cu.shalev.domain.model.Modulo) -> Unit = {},
    val onEmpleados: (Int) -> Unit = {},
    /** 0.27.0 (T11). */
    val onAccesoClave: (Boolean) -> Unit = {},
)

object OnboardingTags {
    const val SIGUIENTE = "onboarding.siguiente"
    const val OMITIR_PASO = "onboarding.omitir_paso"
    const val OMITIR_TODO = "onboarding.omitir_todo"
    const val PROGRESO = "onboarding.progreso"
    fun paso(p: PasoWizard) = "onboarding.paso.${p.name}"
    const val TIPO_PRINCIPAL = "onboarding.tipo_principal"
    const val TIPO_SECUNDARIA = "onboarding.tipo_secundaria"
    const val EMPLEADOS = "onboarding.empleados"
    fun modulo(m: cu.shalev.domain.model.Modulo) = "onboarding.modulo.${m.name}"
}

@Composable
fun OnboardingContent(state: OnboardingUiState, acciones: OnboardingAcciones, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    val paso = state.paso
    if (state.cargando || paso == null) {
        Surface(color = MaterialTheme.colorScheme.background) { ShalevLoading(Modifier.fillMaxSize()) }
        return
    }
    Scaffold(
        topBar = { BarraSuperior(state, acciones) },
        bottomBar = { BarraInferior(state, acciones) },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        AnimatedContent(
            targetState = paso,
            transitionSpec = { ShalevMotion.screenEnter togetherWith ShalevMotion.screenExit },
            label = "paso",
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) { p ->
            // P28: todo centrado y repartido a partes iguales en el alto disponible (si no cabe, se desplaza con 16dp
            // entre elementos). Ver [Equitativo].
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val alto = maxHeight
                Column(
                    Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).heightIn(min = alto)
                        .shalevContentWidth().padding(ShalevSpacing.lg).testTag(OnboardingTags.paso(p)),
                    verticalArrangement = Equitativo,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (p) {
                        PasoWizard.BIENVENIDA -> PasoBienvenida()
                        PasoWizard.TIPO_APP -> PasoTipoApp(state, acciones)
                        PasoWizard.ACCESO_CLAVE -> PasoAccesoClave(state, acciones)
                        PasoWizard.OBJETIVO -> PasoObjetivo(state, acciones)
                        PasoWizard.EMPLEADOS -> PasoEmpleados(state, acciones)
                        PasoWizard.DATOS -> PasoDatos(state, acciones)
                        PasoWizard.ALERTAS -> PasoAlertas(state, acciones)
                        PasoWizard.PRUEBA -> PasoPrueba(state)
                    }
                }
            }
        }
    }
}

/**
 * P28: reparte los elementos con el MISMO espacio entre ellos y en los bordes. Si no caben, 16dp entre ellos
 * desde arriba (y se desplaza).
 */
private val Equitativo = object : Arrangement.Vertical {
    override val spacing = ShalevSpacing.md
    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        val minimo = spacing.roundToPx()
        val libre = (totalSize - sizes.sum()) / (sizes.size + 1)
        val hueco = maxOf(minimo, libre)
        var y = if (libre >= minimo) hueco else 0
        sizes.forEachIndexed { i, s -> outPositions[i] = y; y += s + hueco }
    }
}

@Composable
private fun BarraSuperior(state: OnboardingUiState, acciones: OnboardingAcciones) {
    // P28: en Bienvenida no hay cabecera: «Bienvenido» repetía «Bienvenido a Shalev» y «Saltar todo» quedaba solo.
    if (state.paso == PasoWizard.BIENVENIDA) {
        Spacer(Modifier.statusBarsPadding())
        return
    }
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = ShalevSpacing.md, vertical = ShalevSpacing.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (state.totalPasos <= 1) {
                Text(
                    state.paso!!.titulo, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).testTag(OnboardingTags.PROGRESO).semantics { liveRegion = LiveRegionMode.Polite },
                )
            } else {
                // P18 (L9): misma cabecera que Licencia y Respaldo. Atrás/Siguiente siguen abajo, con texto.
                ShalevStepper(
                    actual = state.numeroPaso, total = state.totalPasos, titulo = state.paso!!.titulo,
                    modifier = Modifier.weight(1f).testTag(OnboardingTags.PROGRESO),
                )
            }
            ShalevTextButton(
                text = if (state.modo == ModoWizard.PRIMERA_VEZ) "Saltar todo" else "Cerrar",
                icon = ShalevIcons.Cancelar,
                onClick = acciones.onOmitirTodo,
                modifier = Modifier.testTag(OnboardingTags.OMITIR_TODO),
            )
        }
    }
}

@Composable
private fun BarraInferior(state: OnboardingUiState, acciones: OnboardingAcciones) {
    // P24: botones solo con icono, agrupados y centrados (Atrás · Ahora no · Siguiente), como en los diálogos.
    ShalevBarraAcciones(Modifier.imePadding()) {
        if (state.paso == PasoWizard.BIENVENIDA) {
            ShalevTextButton(
                text = if (state.modo == ModoWizard.PRIMERA_VEZ) "Saltar todo" else "Cerrar", icon = ShalevIcons.Cancelar,
                onClick = acciones.onOmitirTodo, modifier = Modifier.testTag(OnboardingTags.OMITIR_TODO),
            )
        }
        if (state.puedeVolver) ShalevTextButton(text = "Atrás", icon = ShalevIcons.Atras, onClick = acciones.onAtras, enabled = !state.ocupado)
        if (state.pasoOmitible) {
            ShalevTextButton(
                text = "Ahora no", icon = ShalevIcons.Saltar, onClick = acciones.onOmitirPaso, enabled = !state.ocupado,
                modifier = Modifier.testTag(OnboardingTags.OMITIR_PASO),
            )
        }
        ShalevPrimaryButton(
            text = state.textoBotonPrincipal, onClick = acciones.onSiguiente, loading = state.ocupado, enabled = state.puedeSeguir,
            icon = if (state.esUltimo) ShalevIcons.Listo else ShalevIcons.Siguiente,
            modifier = Modifier.testTag(OnboardingTags.SIGUIENTE),
        )
    }
}

@Composable
private fun Titulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
}

/** P28: icono encima y texto centrado (antes icono a la izquierda y texto alineado a la izquierda). */
@Composable
private fun Punto(icon: ImageVector, texto: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(texto, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Explicacion(texto: String) = ShalevSecondaryText(texto, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

// ---------------- Pasos ----------------

@Composable
private fun PasoBienvenida() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md)) {
        ShalevLogo(size = ShalevSize.logoLarge, contentDescription = "Shalev")
        Text("Bienvenido a Shalev", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(
            "Tu punto de venta e inventario. Funciona sin internet.",
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
        )
    }
    Punto(ShalevIcons.Inventario, "Controla lo que tienes y recibe avisos cuando quede poco.")
    Punto(ShalevIcons.Venta, "Vende y cobra en efectivo o por transferencia.")
    Punto(ShalevIcons.Registros, "Revisa todo lo que vendiste.")
    ShalevCard(tone = CardTone.Tonal) {
        Text("Te haremos unas pocas preguntas (unos 2 minutos).", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        ShalevSecondaryText("Puedes saltar cualquiera.", textAlign = TextAlign.Center)
    }
}

@Composable
private fun PasoDatos(state: OnboardingUiState, acciones: OnboardingAcciones) {
    // P28: sin título propio: la cabecera ya dice «Paso 2 de 4: Tus datos».
    Explicacion("Se usan para pedir tu licencia y para cobrar por transferencia. Puedes dejarlos en blanco.")
    val d = state.datos
    val err = erroresDatos(d)
    val forzar = state.mostrarErroresDatos
    ShalevTextField(
        filtro = FiltroEntrada.NOMBRE,
        value = d.nombre, onValueChange = { v -> acciones.onDatos { it.copy(nombre = v) } }, label = "Nombre",
        isError = "nombre" in err, errorText = err["nombre"], validarAlSalir = true, forzarError = forzar, imeAction = ImeAction.Next,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier,
    )
    ShalevTextField(
        filtro = FiltroEntrada.NOMBRE,
        value = d.apellidos, onValueChange = { v -> acciones.onDatos { it.copy(apellidos = v) } }, label = "Apellidos",
        isError = "apellidos" in err, errorText = err["apellidos"], validarAlSalir = true, forzarError = forzar, imeAction = ImeAction.Next,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier,
    )
    ShalevTextField(
        filtro = FiltroEntrada.CARNE,
        value = d.ci, onValueChange = { v -> acciones.onDatos { it.copy(ci = v) } }, label = "Carné de identidad",
        isError = "ci" in err, errorText = err["ci"], validarAlSalir = true, forzarError = forzar, imeAction = ImeAction.Next,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier,
    )
    ShalevTextField(
        filtro = FiltroEntrada.TELEFONO,
        value = d.telefono, onValueChange = { v -> acciones.onDatos { it.copy(telefono = v) } }, label = "Teléfono",
        placeholder = "52345678", isError = "telefono" in err, errorText = err["telefono"], validarAlSalir = true, forzarError = forzar,
        imeAction = ImeAction.Done, onImeAction = acciones.onSiguiente,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier,
    )
}

@Composable
private fun PasoAlertas(state: OnboardingUiState, acciones: OnboardingAcciones) {
    // P28: sin título propio: la cabecera ya dice «Avisos de inventario».
    Text("Shalev te avisará cuando quede poco de algo.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Explicacion("«Bajo»: conviene reponer pronto. «Crítico»: casi no queda, repón ya.")
    val err = state.niveles.errores()
    // Los botones − / + cuentan como una edición terminada: su resultado se valida al momento.
    var conBotones by rememberSaveable { mutableStateOf(false) }
    val forzar = state.mostrarErroresNiveles || conBotones
    val sumar: (CampoNivel, Int) -> Unit = { c, d -> conBotones = true; acciones.onSumarNivel(c, d) }
    ShalevCard(title = "Productos (en unidades)") {
        CampoNivelUi("Bajo", CampoNivel.PRODUCTO_BAJO, state.niveles, err, forzar, sumar, acciones, decimal = false)
        CampoNivelUi("Crítico", CampoNivel.PRODUCTO_CRITICO, state.niveles, err, forzar, sumar, acciones, decimal = false)
    }
    ShalevCard(title = "Insumos (en su medida: kg, L, unidades…)") {
        CampoNivelUi("Bajo", CampoNivel.INSUMO_BAJO, state.niveles, err, forzar, sumar, acciones, decimal = true)
        CampoNivelUi("Crítico", CampoNivel.INSUMO_CRITICO, state.niveles, err, forzar, sumar, acciones, decimal = true)
    }
    ShalevTextButton(text = "Usar valores recomendados (5 y 1)", icon = ShalevIcons.Restablecer, onClick = { conBotones = false; acciones.onRestablecerNiveles() })
}

@Composable
private fun CampoNivelUi(
    etiqueta: String, campo: CampoNivel, form: NivelesForm, err: Map<CampoNivel, String>,
    forzar: Boolean, sumar: (CampoNivel, Int) -> Unit, acciones: OnboardingAcciones, decimal: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        ShalevIconAction(ShalevIcons.Disminuir, "Restar 1 a $etiqueta", onClick = { sumar(campo, -1) }, style = IconActionStyle.Tonal)
        ShalevTextField(
            filtro = if (decimal) FiltroEntrada.DECIMAL else FiltroEntrada.ENTERO,
            value = form.valor(campo), onValueChange = { acciones.onNivel(campo, it) }, label = etiqueta,
            isError = campo in err, errorText = err[campo], clearable = false, validarAlSalir = true, forzarError = forzar, textAlign = TextAlign.Center,
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        ShalevIconAction(ShalevIcons.Aumentar, "Sumar 1 a $etiqueta", onClick = { sumar(campo, 1) }, style = IconActionStyle.Tonal)
    }
}

@Composable
private fun PasoPrueba(state: OnboardingUiState) {
    val dias = (state.licencia?.estado as? LicenseState.Trial)?.daysLeft
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(ShalevIcons.Prueba, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(ShalevSize.logoLarge))
    }
    Titulo(
        when (dias) {
            null -> "Periodo de prueba"
            1 -> "Te queda 1 día de prueba gratis"
            else -> "Tienes $dias días de prueba gratis"
        },
    )
    Punto(ShalevIcons.Verificado, "Durante la prueba puedes usar todas las funciones.")
    Punto(ShalevIcons.Bloqueo, "Cuando termine, Shalev se bloqueará hasta que actives una licencia. Tus datos NO se borran.")
    Punto(ShalevIcons.Licencia, "Puedes pedir la licencia cuando quieras en Ajustes → Licencia.")
    ShalevCard(title = "Precios", tone = CardTone.Tonal) {
        TipoLicencia.entries.forEach { t ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(t.etiqueta, style = MaterialTheme.typography.bodyMedium)
                // 0.28.0: los importes van en seminegrita.
                Text(Money.cup(t.precioCup), style = ShalevTextos.datoEn(MaterialTheme.typography.bodyMedium))
            }
        }
    }
}

// ---------------- 0.21.0 (C12): recorrido inicial ----------------

@Composable
private fun PasoTipoApp(state: OnboardingUiState, acciones: OnboardingAcciones) {
    Explicacion(TextosTour.TIPO_EXPLICACION)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        OpcionTour(TextosTour.PRINCIPAL, TextosTour.PRINCIPAL_DETALLE, ShalevIcons.Perfil, !state.esSecundaria, radio = true, tag = OnboardingTags.TIPO_PRINCIPAL) { acciones.onTipo(false) }
        OpcionTour(TextosTour.SECUNDARIA, TextosTour.SECUNDARIA_DETALLE, ShalevIcons.Movil, state.esSecundaria, radio = true, tag = OnboardingTags.TIPO_SECUNDARIA) { acciones.onTipo(true) }
    }
    Explicacion(TextosTour.TIPO_DEFINITIVO) // 0.27.0 (T9)
    if (state.esSecundaria) {
        ShalevCard(tone = CardTone.Tonal) {
            Text(TextosTour.SECUNDARIA_SIGUIENTE, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 0.27.0 (T11): paso opcional «Acceso con clave». */
@Composable
private fun PasoAccesoClave(state: OnboardingUiState, acciones: OnboardingAcciones) {
    Explicacion(TextosTour.ACCESO_EXPLICACION)
    cu.shalev.app.acceso.FilaAccesoClave(state.accesoClave, acciones.onAccesoClave)
}

@Composable
private fun PasoObjetivo(state: OnboardingUiState, acciones: OnboardingAcciones) {
    Explicacion(TextosTour.OBJETIVO_EXPLICACION)
    Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
        cu.shalev.domain.model.Modulo.entries.forEach { m ->
            OpcionTour(m.etiqueta, m.detalle, iconoModulo(m), m in state.modulos, radio = false, tag = OnboardingTags.modulo(m)) { acciones.onModulo(m) }
        }
    }
    if (cu.shalev.domain.model.Modulo.VENTAS in state.modulos) Explicacion(TextosTour.VENTAS_ACTIVA_INVENTARIO)
}

@Composable
private fun PasoEmpleados(state: OnboardingUiState, acciones: OnboardingAcciones) {
    Explicacion(TextosTour.EMPLEADOS_EXPLICACION)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ShalevIconAction(
            ShalevIcons.Disminuir, "Un empleado menos", onClick = { acciones.onEmpleados(state.empleados - 1) },
            enabled = state.empleados > 0, style = IconActionStyle.Tonal,
        )
        Text(
            state.empleados.toString(), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag(OnboardingTags.EMPLEADOS).semantics { contentDescription = TextosTour.empleados(state.empleados) },
        )
        ShalevIconAction(
            ShalevIcons.Aumentar, "Un empleado más", onClick = { acciones.onEmpleados(state.empleados + 1) },
            enabled = state.empleados < cu.shalev.domain.model.Vinculacion.SECUNDARIAS_MAX, style = IconActionStyle.Tonal,
        )
    }
    Explicacion(TextosTour.EMPLEADOS_AYUDA)
}

@Composable
internal fun iconoModulo(m: cu.shalev.domain.model.Modulo): ImageVector = when (m) {
    cu.shalev.domain.model.Modulo.VENTAS -> ShalevIcons.Venta
    cu.shalev.domain.model.Modulo.INVENTARIO -> ShalevIcons.Inventario
    cu.shalev.domain.model.Modulo.SERVICIOS -> ShalevIcons.Servicios
}

/** Opción seleccionable de fila completa (radio o casilla), con icono, título y detalle. */
@Composable
private fun OpcionTour(titulo: String, detalle: String, icon: ImageVector, marcado: Boolean, radio: Boolean, tag: String, onClick: () -> Unit) {
    ShalevListItem(
        title = titulo, subtitle = detalle, subtitleMaxLines = 3, indicatorColor = null, selected = marcado,
        leading = {
            if (radio) RadioButton(selected = marcado, onClick = null) else ShalevCheckbox(checked = marcado, onCheckedChange = null)
        },
        trailing = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        onClick = onClick,
        modifier = Modifier.testTag(tag).semantics { stateDescription = if (marcado) "Marcado" else "Sin marcar" },
    )
}
