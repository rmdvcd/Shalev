package cu.shalev.app.licencia

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.common.Contacto
import cu.shalev.app.common.SecureWindow
import cu.shalev.core.money.Money
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevChip
import cu.shalev.designsystem.component.ShalevIconAction
import cu.shalev.designsystem.component.ShalevListItem
import cu.shalev.designsystem.component.ShalevLoading
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.ShalevStepper
import cu.shalev.designsystem.component.ShalevTextButton
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.component.shalevContentWidth
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.theme.ShalevTheme
import cu.shalev.designsystem.theme.ShalevTextos
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.domain.model.Licencia
import cu.shalev.licencia.contract.TipoLicencia
import cu.shalev.licencia.contract.Via
import java.time.Instant

/** Acciones del panel (agrupadas para que [LicenciaContent] sea sin estado y testeable sin Hilt). */
class LicenciaAcciones(
    val onBack: (() -> Unit)?,
    val onEditar: ((LicenciaForm) -> LicenciaForm) -> Unit,
    val onAbrirEdicion: () -> Unit,
    val onCerrarEdicion: () -> Unit,
    val onSolicitar: () -> Unit,
    val onActivar: () -> Unit,
    val onPegar: () -> Unit,
    /** Solo en el bloqueo: abre Soporte desde el aviso (fuera del bloqueo, Soporte está en Ajustes). */
    val onSoporte: (() -> Unit)? = null,
    /** 0.22.0 (L-e): mismo tipo y secundarias que la instalada; true = datos válidos (salta a «Pide la licencia»). */
    val onRenovarIgual: () -> Boolean = { false },
    /** Prepara la ampliación de apps secundarias de la licencia vigente; true = datos válidos (salta al paso 2). */
    val onAmpliarSecundarias: () -> Boolean = { false },
)

/**
 * Panel de Licencia (con estado). Única pantalla operativa durante el bloqueo (con Soporte).
 * FLAG_SECURE: muestra CI y teléfono.
 */
@Composable
fun LicenciaScreen(
    onBack: (() -> Unit)?,
    bloqueada: Boolean = false,
    onSoporte: (() -> Unit)? = null,
    viewModel: LicenciaViewModel = hiltViewModel(),
) {
    SecureWindow()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // 0.25.0: mensaje de licencia compartido con Shalev → se rellena y se activa sin pegarlo (también en el bloqueo).
    val entradaVm: cu.shalev.app.common.EntradaViewModel = hiltViewModel()
    val licenciaCompartida by entradaVm.licenciaPendiente.collectAsStateWithLifecycle()
    LaunchedEffect(licenciaCompartida, state.licencia != null) {
        if (licenciaCompartida && state.licencia != null) entradaVm.tomarLicencia()?.let { texto ->
            viewModel.editar { it.copy(mensajeLicencia = texto) }
            viewModel.activarLicencia()
        }
    }
    // 0.25.0 (§3): tras importar un respaldo con licencia, el ID a recuperar ya viene escrito.
    val recuperacionVm: RecuperacionViewModel = hiltViewModel()
    val recuperable by recuperacionVm.recuperable.collectAsStateWithLifecycle()
    LaunchedEffect(recuperable, state.licencia?.instalada) {
        val r = recuperable
        if (r != null && state.licencia?.permiteRecuperar() == true && state.form.recupera.isBlank()) viewModel.editar { it.copy(recupera = r.id) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is LicenciaEvento.Mensaje -> snackbar.showSnackbar(e.texto)
                is LicenciaEvento.Enviar -> {
                    // 0.23.1: solo texto por los dos canales (sin imágenes que manejar en el teléfono).
                    val ok = when (e.via) {
                        Via.WHATSAPP -> Contacto.whatsapp(context, e.texto)
                        Via.SMS -> Contacto.sms(context, e.texto)
                    }
                    if (!ok) snackbar.showSnackbar("No hay una app disponible para enviar el mensaje")
                }
            }
        }
    }

    Scaffold(
        topBar = { ShalevTopBar(title = "Licencia", onBack = onBack) },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        LicenciaContent(
            state = state,
            ahora = remember(state.licencia) { Instant.now() },
            bloqueada = bloqueada,
            acciones = LicenciaAcciones(
                onBack = onBack,
                onEditar = viewModel::editar,
                onAbrirEdicion = viewModel::abrirEdicion,
                onCerrarEdicion = viewModel::cerrarEdicion,
                onSolicitar = viewModel::solicitarLicencia,
                onActivar = viewModel::activarLicencia,
                onPegar = { clipboard.getText()?.text?.let { t -> viewModel.editar { it.copy(mensajeLicencia = t) } } },
                onSoporte = onSoporte,
                onRenovarIgual = viewModel::renovarIgual,
                onAmpliarSecundarias = viewModel::ampliarSecundarias,
            ),
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

object LicenciaTags {
    const val SECUNDARIAS = "licencia.secundarias"
    const val SECUNDARIAS_MENOS = "licencia.secundarias_menos"
    const val SECUNDARIAS_MAS = "licencia.secundarias_mas"
    const val PRECIO_TOTAL = "licencia.precio_total"
    const val AVISO_BLOQUEO = "licencia.aviso_bloqueo"
    const val SOPORTE = "licencia.soporte"
    const val BOTON_SOLICITAR = "licencia.solicitar"
    const val BOTON_ACTIVAR = "licencia.activar"
    const val RESUMEN_DATOS = "licencia.resumen"
    const val FORM_DATOS = "licencia.form"
    const val STEPPER = "licencia.stepper"
    const val RENOVAR_IGUAL = "licencia.renovar_igual"
    const val AMPLIAR_SECUNDARIAS = "licencia.ampliar_secundarias"
    const val SIN_PERDER_DIAS = "licencia.sin_perder_dias"
    const val RECUPERA = "licencia.recupera"
    const val PRECIOS = "licencia.precios"
    fun precio(t: TipoLicencia) = "licencia.precio_${t.name}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LicenciaContent(
    state: LicenciaUiState,
    ahora: Instant,
    bloqueada: Boolean,
    acciones: LicenciaAcciones,
    modifier: Modifier = Modifier,
) {
    val lic = state.licencia ?: return ShalevLoading(modifier)
    val form = state.form
    val accion = lic.accionSolicitud(form.ampliandoSecundarias)
    Column(
        modifier.fillMaxSize().shalevContentWidth().imePadding().verticalScroll(rememberScrollState()).padding(ShalevSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally, // P28: lo que va solo en su fila, centrado
    ) {
        if (bloqueada) {
            ShalevCard(tone = CardTone.Tonal, modifier = Modifier.testTag(LicenciaTags.AVISO_BLOQUEO)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                    Icon(ShalevIcons.Bloqueo, contentDescription = null)
                    Text("Shalev está bloqueado hasta que actives una licencia válida.", style = MaterialTheme.typography.bodyMedium)
                }
                ShalevSecondaryText("Tus datos siguen guardados.")
                acciones.onSoporte?.let { soporte ->
                    ShalevButtonRow { ShalevTextButton("Contactar soporte", icon = ShalevIcons.Soporte, onClick = soporte, modifier = Modifier.testTag(LicenciaTags.SOPORTE)) }
                }
            }
        }

        EstadoLicenciaCard(lic, ahora)
        var paso by rememberSaveable { mutableStateOf(pasoInicialLicencia(form)) }
        // 0.22.0 (L-e): renovar con lo mismo en un toque (tipo y secundarias de la instalada).
        val instalada = lic.instalada
        if ((lic.renovable() || lic.ampliable()) && instalada != null && paso != PasoLicencia.SOLICITAR) {
            ShalevButtonRow {
                if (lic.renovable()) {
                    ShalevSecondaryButton(
                        text = TextosRenovacion.BOTON, icon = ShalevIcons.Reintentar,
                        onClick = { paso = if (acciones.onRenovarIgual()) PasoLicencia.SOLICITAR else PasoLicencia.DATOS },
                        modifier = Modifier.testTag(LicenciaTags.RENOVAR_IGUAL),
                    )
                }
                if (lic.ampliable()) {
                    ShalevSecondaryButton(
                        text = TextosRenovacion.BOTON_AMPLIAR, icon = ShalevIcons.Aumentar,
                        onClick = { paso = if (acciones.onAmpliarSecundarias()) PasoLicencia.SOLICITAR else PasoLicencia.DATOS },
                        modifier = Modifier.testTag(LicenciaTags.AMPLIAR_SECUNDARIAS),
                    )
                }
            }
            if (lic.renovable()) {
                ShalevSecondaryText(
                    ShalevTextos.resaltar(TextosRenovacion.detalle(instalada), Money.cup(instalada.tipo.precio(instalada.secundarias))),
                    textAlign = TextAlign.Center,
                )
            }
        }

        lic.avisos().forEach { aviso ->
            ShalevCard(tone = CardTone.Tonal) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                    Icon(ShalevIcons.Alerta, contentDescription = null)
                    Text(aviso.titulo, style = MaterialTheme.typography.titleSmall)
                }
                Text(aviso.detalle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        // 0.30.1: precios de cada tipo de licencia, con lo que suma cada app secundaria.
        PreciosLicenciaCard()

        ShalevStepper(
            actual = paso.ordinal + 1, total = PasoLicencia.entries.size, titulo = paso.titulo,
            onAtras = paso.anterior?.let { a -> { paso = a } },
            onSiguiente = paso.siguiente?.let { s ->
                {
                    if (puedeAvanzar(paso, form)) {
                        if (paso == PasoLicencia.DATOS && form.editando) acciones.onCerrarEdicion()
                        paso = s
                    } else {
                        acciones.onEditar { it.copy(mostrarErrores = true, editando = true) }
                    }
                }
            },
            modifier = Modifier.testTag(LicenciaTags.STEPPER),
        )

        when (paso) {
            PasoLicencia.DATOS -> DatosCard(state, acciones)
            PasoLicencia.SOLICITAR -> {
                ShalevCard(title = accion.titulo) {
                    if (accion == AccionSolicitud.NINGUNA) {
                        ShalevSecondaryText("Tu licencia perpetua ya incluye el máximo de apps secundarias (${cu.shalev.licencia.contract.GlContract.SECUNDARIAS_MAX}).")
                    } else {
                        if (lic.renovable() && lic.ampliable() && instalada != null) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                ShalevChip(
                                    label = "Renovar o cambiar",
                                    selected = accion != AccionSolicitud.AMPLIAR,
                                    onClick = { acciones.onEditar { it.conModoAmpliacion(false, instalada) } },
                                )
                                ShalevChip(
                                    label = "Ampliar secundarias",
                                    selected = accion == AccionSolicitud.AMPLIAR,
                                    onClick = { acciones.onEditar { it.conModoAmpliacion(true, instalada) } },
                                )
                            }
                        }
                        if (accion == AccionSolicitud.AMPLIAR && instalada != null) {
                            ShalevSecondaryText(
                                "Plan actual: ${instalada.tipo.etiqueta} · Apps secundarias actuales: ${instalada.secundarias}",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SelectorSecundarias(form, acciones, previasAmpliacion = instalada.secundarias)
                        } else {
                            ShalevSecondaryText("Tipo de licencia")
                            // 0.28.0: un tipo debajo del otro, a todo lo ancho.
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                                TipoLicencia.entries.forEach { t ->
                                    ShalevChip(
                                        label = t.etiqueta, supportingLabel = Money.cup(t.precioCup), supportingEsDato = true,
                                        selected = form.tipo == t, onClick = { acciones.onEditar { it.copy(tipo = t) } },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            // 0.21.0 (C4): cuántas apps secundarias (empleados) cubre; cada una suma un importe fijo.
                            SelectorSecundarias(form, acciones)
                            // 0.25.0 (§3): recuperar la licencia de otro teléfono; 0.25.1 (C): también con la instalada vencida.
                            if (lic.permiteRecuperar()) {
                                ShalevSecondaryText(TextosRecuperacion.TITULO)
                                ShalevTextField(
                                    filtro = FiltroEntrada.LIBRE,
                                    value = form.recupera, onValueChange = { v -> acciones.onEditar { it.copy(recupera = v.take(200)) } },
                                    label = TextosRecuperacion.CAMPO, supportingText = TextosRecuperacion.DETALLE,
                                    isError = !form.recuperaOk, errorText = TextosRecuperacion.ID_INVALIDO,
                                    modifier = Modifier.testTag(LicenciaTags.RECUPERA),
                                )
                            }
                        }
                        ShalevSecondaryText("Enviar por")
                        ShalevButtonRow {
                            ShalevIconAction(
                                ShalevIcons.WhatsApp, "Enviar por WhatsApp", onClick = { acciones.onEditar { it.copy(via = Via.WHATSAPP) } },
                                selected = form.via == Via.WHATSAPP,
                                containerColor = ShalevTheme.colors.whatsapp.takeIf { form.via != Via.WHATSAPP },
                            )
                            ShalevIconAction(
                                ShalevIcons.Sms, "Enviar por SMS", onClick = { acciones.onEditar { it.copy(via = Via.SMS) } },
                                selected = form.via == Via.SMS, style = IconActionStyle.Tonal,
                            )
                        }
                        ShalevSecondaryText(
                            if (form.via == Via.WHATSAPP) "Se abrirá WhatsApp con tu solicitud de licencia."
                            else "Se abrirá Mensajes con tu solicitud de licencia.",
                        )
                        if (accion == AccionSolicitud.AMPLIAR) {
                            ShalevSecondaryText(
                                TextosRenovacion.CONSERVA_VENCIMIENTO, textAlign = TextAlign.Center,
                                modifier = Modifier.testTag(LicenciaTags.SIN_PERDER_DIAS),
                            )
                        } else if (lic.renovable()) {
                            ShalevSecondaryText(
                                TextosRenovacion.SIN_PERDER_DIAS, textAlign = TextAlign.Center,
                                modifier = Modifier.testTag(LicenciaTags.SIN_PERDER_DIAS),
                            )
                        }
                        ShalevButtonRow {
                            ShalevPrimaryButton(
                                text = accion.boton, icon = ShalevIcons.Enviar, onClick = acciones.onSolicitar,
                                enabled = lic.puedeSolicitar, loading = state.ocupado,
                                modifier = Modifier.testTag(LicenciaTags.BOTON_SOLICITAR),
                            )
                        }
                    }
                }
                if (accion != AccionSolicitud.NINGUNA) {
                    ShalevSecondaryText("Cuando recibas la respuesta, actívala en el paso siguiente.", textAlign = TextAlign.Center)
                }
            }
            PasoLicencia.ACTIVAR -> {
                ShalevCard(title = "Activar licencia") {
                    ShalevSecondaryText(TextosActivacion.INSTRUCCION, textAlign = TextAlign.Center)
                    ShalevTextField(
                        filtro = FiltroEntrada.LIBRE,
                        value = form.mensajeLicencia, onValueChange = { v -> acciones.onEditar { it.copy(mensajeLicencia = v) } },
                        label = "Mensaje de licencia", singleLine = false, minLines = 3, maxLines = 6,
                        modifier = Modifier,
                    )
                    ShalevButtonRow {
                        ShalevIconAction(ShalevIcons.Pegar, "Pegar", style = IconActionStyle.Tonal, onClick = acciones.onPegar)
                        ShalevPrimaryButton(
                            text = "Activar", icon = ShalevIcons.Confirmar, onClick = acciones.onActivar,
                            enabled = form.mensajeLicencia.isNotBlank() && lic.validacionDisponible, loading = state.ocupado,
                            modifier = Modifier.testTag(LicenciaTags.BOTON_ACTIVAR),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DatosCard(state: LicenciaUiState, acciones: LicenciaAcciones) {
    val form = state.form
    ShalevCard(title = "Tus datos") {
        if (!form.editando) {
            Column(Modifier.testTag(LicenciaTags.RESUMEN_DATOS), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${form.nombre} ${form.apellidos}".trim(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                ShalevSecondaryText("CI: ${form.ci}")
                ShalevSecondaryText("Teléfono: ${form.telefono}")
                ShalevSecondaryText("De tu Perfil y de Pago electrónico.") // P28: los teléfonos ya no están en Perfil
                ShalevButtonRow { ShalevTextButton(text = "Editar", icon = ShalevIcons.Editar, onClick = acciones.onAbrirEdicion) }
            }
        } else {
            DatosForm(state, acciones)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DatosForm(state: LicenciaUiState, acciones: LicenciaAcciones) {
    val form = state.form
    val err = form.mostrarErrores
    Column(Modifier.testTag(LicenciaTags.FORM_DATOS), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.perfilVacio) ShalevSecondaryText("Tu Perfil está vacío: estos datos se guardarán también en él al solicitar.")
        ShalevTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = form.nombre, onValueChange = { v -> acciones.onEditar { it.copy(nombre = v) } },
            imeAction = ImeAction.Next,
            label = "Nombre", isError = !form.nombreOk, errorText = "Solo letras, 2 a 80", validarAlSalir = true, forzarError = err,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier,
        )
        ShalevTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = form.apellidos, onValueChange = { v -> acciones.onEditar { it.copy(apellidos = v) } },
            imeAction = ImeAction.Next,
            label = "Apellidos", isError = !form.apellidosOk, errorText = "Solo letras, 2 a 80", validarAlSalir = true, forzarError = err,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier,
        )
        ShalevTextField(
            filtro = FiltroEntrada.CARNE,
            value = form.ci, onValueChange = { v -> acciones.onEditar { it.copy(ci = v) } },
            imeAction = ImeAction.Next,
            label = "Carné de identidad", isError = !form.ciOk, errorText = "5 a 20 letras o dígitos", validarAlSalir = true, forzarError = err,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier,
        )
        if (state.telefonosPerfil.isNotEmpty()) {
            ShalevSecondaryText("Tus teléfonos")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs, Alignment.CenterHorizontally)) {
                state.telefonosPerfil.forEach { n ->
                    ShalevChip(label = n, selected = form.telefonoSeleccionado(n), onClick = { acciones.onEditar { it.conTelefono(n) } })
                }
            }
        }
        ShalevTextField(
            filtro = FiltroEntrada.TELEFONO,
            value = form.telefono, onValueChange = { v -> acciones.onEditar { it.conTelefono(v) } },
            imeAction = ImeAction.Done,
            label = "Teléfono", placeholder = "5XXXXXXX", isError = !form.telefonoOk, errorText = "Número no válido", validarAlSalir = true, forzarError = err,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier,
        )
        if (!state.perfilVacio) {
            ShalevSecondaryButton(text = "Listo", icon = ShalevIcons.Listo, onClick = acciones.onCerrarEdicion)
        }
    }
}

/**
 * 0.30.1: Card «Precios de la licencia». Una fila por tipo: el precio base y, debajo, lo que suma cada app secundaria
 * (empleado). Los importes salen de [TipoLicencia] (los mismos que cobra la solicitud), nunca se repiten a mano.
 */
@Composable
private fun PreciosLicenciaCard() {
    ShalevCard(title = TextosLicencia.PRECIOS_TITULO, modifier = Modifier.testTag(LicenciaTags.PRECIOS)) {
        ShalevSecondaryText(TextosLicencia.PRECIOS_DETALLE)
        Column(Modifier.fillMaxWidth()) {
            TipoLicencia.entries.forEach { t ->
                ShalevListItem(
                    title = t.etiqueta,
                    value = Money.cup(t.precioCup),
                    indicatorColor = null,
                    subtitleResaltado = ShalevTextos.resaltar(TextosLicencia.precioSecundaria(t), Money.cup(t.precioSecundariaCup)),
                    modifier = Modifier.testTag(LicenciaTags.precio(t)),
                )
            }
        }
    }
}

/** 0.21.0 (C4): selector ◄ [n] ► de secundarias con el precio total actualizado al momento. */
@Composable
private fun SelectorSecundarias(form: LicenciaForm, acciones: LicenciaAcciones, previasAmpliacion: Int? = null) {
    val minimo = previasAmpliacion?.let { (it + 1).coerceAtMost(cu.shalev.licencia.contract.GlContract.SECUNDARIAS_MAX) } ?: 0
    val mostradas = form.secundarias.coerceIn(minimo, cu.shalev.licencia.contract.GlContract.SECUNDARIAS_MAX)
    ShalevSecondaryText(if (previasAmpliacion != null) "Nuevo total de apps secundarias" else TextosLicencia.SECUNDARIAS)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ShalevIconAction(
            ShalevIcons.Disminuir, "Una secundaria menos", onClick = { acciones.onEditar { it.conSecundarias(mostradas - 1, minimo) } },
            enabled = mostradas > minimo, style = IconActionStyle.Tonal, modifier = Modifier.testTag(LicenciaTags.SECUNDARIAS_MENOS),
        )
        Text(
            mostradas.toString(), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag(LicenciaTags.SECUNDARIAS).semantics { contentDescription = TextosLicencia.secundarias(mostradas) },
        )
        ShalevIconAction(
            ShalevIcons.Aumentar, "Una secundaria más", onClick = { acciones.onEditar { it.conSecundarias(mostradas + 1, minimo) } },
            enabled = mostradas < cu.shalev.licencia.contract.GlContract.SECUNDARIAS_MAX, style = IconActionStyle.Tonal,
            modifier = Modifier.testTag(LicenciaTags.SECUNDARIAS_MAS),
        )
    }
    if (previasAmpliacion != null) {
        val adicionales = (mostradas - previasAmpliacion).coerceAtLeast(1)
        val totalAmp = form.tipo.precioAmpliacion(adicionales)
        ShalevSecondaryText(
            ShalevTextos.resaltar(
                TextosLicencia.precioAmpliacion(form.tipo, previasAmpliacion, mostradas),
                Money.cup(totalAmp), Money.cup(form.tipo.precioSecundariaCup),
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag(LicenciaTags.PRECIO_TOTAL),
        )
    } else {
        ShalevSecondaryText(
            // 0.28.0: los importes («Total: 1 500.00 CUP…») van en seminegrita.
            ShalevTextos.resaltar(
                TextosLicencia.precioTotal(form),
                Money.cup(form.tipo.precioCup), Money.cup(form.precio), Money.cup(form.tipo.precioSecundariaCup),
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag(LicenciaTags.PRECIO_TOTAL),
        )
    }
}

/** 0.21.0 (C4): textos del selector de secundarias (puros, probados en JVM). */
object TextosLicencia {
    const val PRECIOS_TITULO = "Precios de la licencia"
    val PRECIOS_DETALLE = "Cada licencia cubre tu app principal. Cada app secundaria (empleado, hasta " +
        "${cu.shalev.licencia.contract.GlContract.SECUNDARIAS_MAX}) suma un importe fijo."
    fun precioSecundaria(t: TipoLicencia): String = "Cada app secundaria: +${Money.cup(t.precioSecundariaCup)}"
    const val SECUNDARIAS = "Apps secundarias (empleados)"
    fun secundarias(n: Int): String = when (n) { 0 -> "Sin apps secundarias"; 1 -> "1 app secundaria"; else -> "$n apps secundarias" }
    fun precioTotal(f: LicenciaForm): String =
        if (f.secundarias == 0) "Total: ${Money.cup(f.tipo.precioCup)}"
        else "Total: ${Money.cup(f.precio)} (${Money.cup(f.tipo.precioCup)} + ${f.secundarias} × ${Money.cup(f.tipo.precioSecundariaCup)})"
    fun precioAmpliacion(t: TipoLicencia, previas: Int, totales: Int): String {
        val adicionales = (totales - previas).coerceAtLeast(1)
        return "Ampliación: ${Money.cup(t.precioAmpliacion(adicionales))} (+$adicionales × ${Money.cup(t.precioSecundariaCup)})"
    }
}
