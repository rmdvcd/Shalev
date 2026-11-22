package cu.shalev.app.licencia

import cu.shalev.app.perfil.TextosPerfil
import cu.shalev.app.vinculacion.VinculacionLogic
import cu.shalev.core.contact.PaymentInstructions
import cu.shalev.app.recorrido.ObjetivosRecorrido
import cu.shalev.designsystem.component.coachMark
import cu.shalev.app.recorrido.RecorridoModulo
import cu.shalev.app.recorrido.Recorrido
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import cu.shalev.core.format.NumberPresentation
import cu.shalev.core.money.Money
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.AutoColapsarPanelesAlEscribir
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.IconActionStyle
import cu.shalev.designsystem.component.PanelesInformativos
import cu.shalev.designsystem.component.ShalevAccordionCard
import cu.shalev.designsystem.component.rememberPanelesInformativos
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
import cu.shalev.designsystem.component.rememberAvisos

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
    /**
     * Solo en el bloqueo por prueba vencida: vincula este teléfono como app secundaria de una principal con licencia
     * activa. null = no se ofrece (licencia vencida, revocada o reloj manipulado).
     */
    val onSecundaria: (() -> Unit)? = null,
    /** 0.22.0 (L-e): mismo tipo y secundarias que la instalada; true = datos válidos (salta a «Pide la licencia»). */
    val onRenovarIgual: () -> Boolean = { false },
    /** Prepara la ampliación de apps secundarias de la licencia vigente; true = datos válidos (salta al paso 2). */
    val onAmpliarSecundarias: () -> Boolean = { false },
    val onCanjear: () -> Unit = {},
    val onPegarCodigo: () -> Unit = {},
    val onConsultarEstado: () -> Unit = {},
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
    onSecundaria: (() -> Unit)? = null,
    viewModel: LicenciaViewModel = hiltViewModel(),
    entradaVm: cu.shalev.app.common.EntradaViewModel = hiltViewModel(),
    recuperacionVm: RecuperacionViewModel = hiltViewModel(),
) {
    SecureWindow()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberAvisos()
    // 0.25.0: mensaje de licencia compartido con Shalev → se rellena y se activa sin pegarlo (también en el bloqueo).
    val licenciaCompartida by entradaVm.licenciaPendiente.collectAsStateWithLifecycle()
    LaunchedEffect(licenciaCompartida, state.licencia != null) {
        if (licenciaCompartida && state.licencia != null) entradaVm.tomarLicencia()?.let { texto ->
            viewModel.editar { it.copy(mensajeLicencia = texto) }
            viewModel.activarLicencia()
        }
    }
    // 0.25.0 (§3): tras importar un respaldo con licencia, el ID a recuperar ya viene escrito.
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

    RecorridoModulo(Recorrido.LICENCIA, listo = true) {
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
                    onSecundaria = onSecundaria,
                    onRenovarIgual = viewModel::renovarIgual,
                    onAmpliarSecundarias = viewModel::ampliarSecundarias,
                    onCanjear = viewModel::canjearCodigo,
                    onPegarCodigo = { clipboard.getText()?.text?.let { t -> viewModel.editar { it.copy(codigoCanje = t) } } },
                    onConsultarEstado = viewModel::consultarEstado,
                ),
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            )
        }
    }
}

object LicenciaTags {
    const val SECUNDARIAS = "licencia.secundarias"
    const val SECUNDARIAS_MENOS = "licencia.secundarias_menos"
    const val SECUNDARIAS_MAS = "licencia.secundarias_mas"
    const val PRECIO_TOTAL = "licencia.precio_total"
    const val AVISO_BLOQUEO = "licencia.aviso_bloqueo"
    const val SOPORTE = "licencia.soporte"
    /** 1.0.2: «Seguir como app secundaria» desde el bloqueo por prueba vencida. */
    const val SECUNDARIA = "licencia.seguir_secundaria"
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
    const val VIA_WHATSAPP = "licencia.via_whatsapp"
    const val VIA_SMS = "licencia.via_sms"
    fun precio(t: TipoLicencia) = "licencia.precio_${t.name}"
    fun tipo(t: TipoLicencia) = "licencia.tipo_${t.name}"
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
    // Paneles de solo información (bloqueo, estado, avisos, precios) como acordeones: empiezan abiertos los que
    // explican la situación; los precios (referencia) empiezan cerrados. Al aparecer el teclado para escribir en
    // el asistente se cierran solos, dejando ver los campos y los botones (AutoColapsarPanelesAlEscribir).
    val paneles = rememberPanelesInformativos(
        PanelesLicencia.BLOQUEO, PanelesLicencia.ESTADO,
        *lic.avisos().map { PanelesLicencia.aviso(it) }.toTypedArray(),
    )
    AutoColapsarPanelesAlEscribir(paneles)
    Column(
        modifier.fillMaxSize().shalevContentWidth().imePadding().verticalScroll(rememberScrollState()).padding(ShalevSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally, // P28: lo que va solo en su fila, centrado
    ) {
        if (bloqueada) {
            ShalevAccordionCard(
                title = "Shalev está bloqueado",
                subtitle = "Activa una licencia válida para seguir usando la app.",
                icon = ShalevIcons.Bloqueo,
                tone = CardTone.Tonal,
                expanded = paneles.abierto(PanelesLicencia.BLOQUEO),
                onToggle = { paneles.alternar(PanelesLicencia.BLOQUEO) },
                modifier = Modifier.testTag(LicenciaTags.AVISO_BLOQUEO),
            ) {
                Text("Shalev está bloqueado hasta que actives una licencia válida.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                ShalevSecondaryText("Tus datos siguen guardados.")
                // 1.0.2: con la prueba vencida también se puede seguir trabajando como app secundaria de otra principal.
                acciones.onSecundaria?.let { secundaria ->
                    ShalevSecondaryText(VinculacionLogic.SEGUIR_COMO_SECUNDARIA_DETALLE)
                    ShalevButtonRow {
                        ShalevTextButton(
                            text = VinculacionLogic.SEGUIR_COMO_SECUNDARIA,
                            icon = ShalevIcons.Vinculacion,
                            etiqueta = "Secundaria",
                            onClick = secundaria,
                            modifier = Modifier.testTag(LicenciaTags.SECUNDARIA),
                        )
                    }
                }
                acciones.onSoporte?.let { soporte ->
                    ShalevButtonRow { ShalevTextButton("Contactar soporte", icon = ShalevIcons.Soporte, etiqueta = "Soporte", onClick = soporte, modifier = Modifier.testTag(LicenciaTags.SOPORTE)) }
                }
            }
        }

        Box(Modifier.coachMark(ObjetivosRecorrido.LICENCIA_ESTADO)) {
            EstadoLicenciaCard(lic, ahora, expandida = paneles.abierto(PanelesLicencia.ESTADO), onAlternar = { paneles.alternar(PanelesLicencia.ESTADO) })
        }
        var paso by rememberSaveable { mutableStateOf(pasoInicialLicencia(form)) }
        // Arranque sin repetir lo ya dado: el Perfil puede llegar un instante después que la licencia; en cuanto
        // los datos ya están completos (asistente inicial o una solicitud anterior) se entra en «Pide la
        // licencia». Solo antes de que la persona navegue por su cuenta y nunca mientras edita sus datos.
        var pasoTocado by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(form.valido, form.editando, form.mensajeLicencia) {
            if (!pasoTocado && !form.editando) {
                val sugerido = pasoInicialLicencia(form)
                if (sugerido != paso) paso = sugerido
            }
        }
        LaunchedEffect(paso) { paneles.colapsar(PanelesLicencia.PRECIOS) }
        // 0.22.0 (L-e): renovar con lo mismo en un toque (tipo y secundarias de la instalada).
        val instalada = lic.instalada
        if ((lic.renovable() || lic.ampliable()) && instalada != null && paso != PasoLicencia.SOLICITAR) {
            ShalevButtonRow {
                if (lic.renovable()) {
                    ShalevSecondaryButton(
                        text = TextosRenovacion.BOTON, icon = ShalevIcons.Reintentar, etiqueta = "Renovar",
                        onClick = { pasoTocado = true; paso = if (acciones.onRenovarIgual()) PasoLicencia.SOLICITAR else PasoLicencia.DATOS },
                        modifier = Modifier.testTag(LicenciaTags.RENOVAR_IGUAL).coachMark(ObjetivosRecorrido.LICENCIA_RENOVAR),
                    )
                }
                if (lic.ampliable()) {
                    ShalevSecondaryButton(
                        text = TextosRenovacion.BOTON_AMPLIAR, icon = ShalevIcons.Aumentar, etiqueta = "Ampliar",
                        onClick = { pasoTocado = true; paso = if (acciones.onAmpliarSecundarias()) PasoLicencia.SOLICITAR else PasoLicencia.DATOS },
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
            val clave = PanelesLicencia.aviso(aviso)
            ShalevAccordionCard(
                title = aviso.titulo,
                subtitle = aviso.detalle,
                icon = ShalevIcons.Alerta,
                tone = CardTone.Tonal,
                expanded = paneles.abierto(clave),
                onToggle = { paneles.alternar(clave) },
            ) {
                Text(aviso.detalle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        // 0.30.1: precios de cada tipo de licencia, con lo que suma cada app secundaria.
        PreciosLicenciaCard(paneles)

        ShalevStepper(
            actual = paso.ordinal + 1, total = PasoLicencia.entries.size, titulo = paso.titulo,
            onAtras = paso.anterior?.let { a -> { pasoTocado = true; paso = a } },
            onSiguiente = paso.siguiente?.let { s ->
                {
                    if (puedeAvanzar(paso, form)) {
                        if (paso == PasoLicencia.DATOS && form.editando) acciones.onCerrarEdicion()
                        pasoTocado = true
                        paso = s
                    } else {
                        acciones.onEditar { it.copy(mostrarErrores = true, editando = true) }
                    }
                }
            },
            modifier = Modifier.testTag(LicenciaTags.STEPPER).coachMark(ObjetivosRecorrido.LICENCIA_PASOS),
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
                            // Cada tipo ocupa una fila con nombre a la izquierda y precio alineado a la derecha.
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                                TipoLicencia.entries.forEach { t ->
                                    ShalevListItem(
                                        title = t.etiqueta,
                                        value = Money.cup(t.precioCup),
                                        selected = form.tipo == t,
                                        onClick = { acciones.onEditar { it.copy(tipo = t) } },
                                        modifier = Modifier.testTag(LicenciaTags.tipo(t)),
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
                                etiqueta = "WhatsApp", mostrarTexto = true,
                                modifier = Modifier.testTag(LicenciaTags.VIA_WHATSAPP),
                            )
                            ShalevIconAction(
                                ShalevIcons.Sms, "Enviar por SMS", onClick = { acciones.onEditar { it.copy(via = Via.SMS) } },
                                selected = form.via == Via.SMS, style = IconActionStyle.Tonal,
                                etiqueta = "SMS", mostrarTexto = true,
                                modifier = Modifier.testTag(LicenciaTags.VIA_SMS),
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

                        // T2: Tarjeta "Cómo pagar" antes del botón de envío (oculta en recuperación)
                        if (form.recupera.isBlank()) {
                            val importePagar = if (accion == AccionSolicitud.AMPLIAR && instalada != null) {
                                val adic = (form.secundarias - instalada.secundarias).coerceAtLeast(1)
                                Money.cup(instalada.tipo.precioAmpliacion(adic))
                            } else {
                                Money.cup(form.tipo.precio(form.secundarias))
                            }
                            ShalevCard(title = PaymentInstructions.TITULO) {
                                ShalevSecondaryText("Importe a pagar: $importePagar")
                                if (PaymentInstructions.METODOS.isEmpty()) {
                                    ShalevSecondaryText(PaymentInstructions.AVISO_SIN_METODOS)
                                } else {
                                    ShalevSecondaryText(PaymentInstructions.FRASE_PAGA_SOLO)
                                    val clipboard = LocalClipboardManager.current
                                    PaymentInstructions.METODOS.forEach { metodo ->
                                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xxs)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    ShalevSecondaryText(metodo.nombre)
                                                    ShalevSecondaryText(metodo.destino)
                                                    metodo.nota?.let { ShalevSecondaryText(it) }
                                                }
                                                ShalevSecondaryButton(
                                                    text = "Copiar",
                                                    icon = ShalevIcons.Copiar,
                                                    onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(metodo.destino)) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        ShalevButtonRow {
                            ShalevPrimaryButton(
                                text = accion.boton, etiqueta = "Solicitar", icon = ShalevIcons.Enviar, onClick = acciones.onSolicitar,
                                enabled = lic.puedeSolicitar, loading = state.ocupado,
                                modifier = Modifier.testTag(LicenciaTags.BOTON_SOLICITAR).coachMark(ObjetivosRecorrido.LICENCIA_ENVIAR),
                            )
                        }
                    }
                }
                if (accion != AccionSolicitud.NINGUNA) {
                    ShalevSecondaryText("Cuando recibas la respuesta, actívala en el paso siguiente.", textAlign = TextAlign.Center)
                }
            }
            PasoLicencia.ACTIVAR -> {
                ShalevCard(title = if (lic.instalada != null) "Renovar con código" else "Tengo un código") {
                    ShalevSecondaryText("Escribe o pega tu código comercial de 16 caracteres.", textAlign = TextAlign.Center)
                    ShalevTextField(
                        filtro = FiltroEntrada.LIBRE,
                        value = form.codigoCanje,
                        onValueChange = { v -> acciones.onEditar { it.copy(codigoCanje = v) } },
                        label = "Código comercial (16 caracteres)",
                        singleLine = true,
                        modifier = Modifier.testTag("licencia.codigo_canje"),
                    )
                    ShalevButtonRow {
                        ShalevIconAction(ShalevIcons.Pegar, "Pegar", style = IconActionStyle.Tonal, onClick = acciones.onPegarCodigo)
                        ShalevPrimaryButton(
                            text = "Canjear",
                            icon = ShalevIcons.Confirmar,
                            onClick = acciones.onCanjear,
                            enabled = form.codigoCanje.isNotBlank() && !state.ocupado,
                            loading = state.ocupado,
                            modifier = Modifier.testTag("licencia.boton_canjear"),
                        )
                    }
                    if (lic.instalada != null) {
                        ShalevButtonRow {
                            ShalevSecondaryButton(
                                text = "Consultar",
                                etiqueta = "Estado",
                                icon = ShalevIcons.Reintentar,
                                onClick = acciones.onConsultarEstado,
                                enabled = !state.ocupado,
                                loading = state.ocupado,
                                modifier = Modifier.testTag("licencia.boton_estado"),
                            )
                        }
                    }
                }
                ShalevCard(title = "Activar licencia tradicional") {
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
                            modifier = Modifier.testTag(LicenciaTags.BOTON_ACTIVAR).coachMark(ObjetivosRecorrido.LICENCIA_ENVIAR),
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
            Column(Modifier.testTag(LicenciaTags.RESUMEN_DATOS).coachMark(ObjetivosRecorrido.LICENCIA_DATOS), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${form.nombre} ${form.apellidos}".trim(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                ShalevSecondaryText("CI: ${form.ci}")
                ShalevSecondaryText("Teléfono: ${NumberPresentation.phone(form.telefono)}")
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
    Column(Modifier.testTag(LicenciaTags.FORM_DATOS).coachMark(ObjetivosRecorrido.LICENCIA_DATOS), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.perfilVacio) ShalevSecondaryText("Tu Perfil está vacío: estos datos se guardarán también en él al solicitar.")
        ShalevTextField(
            filtro = FiltroEntrada.PERSONA,
            value = form.nombre, onValueChange = { v -> acciones.onEditar { it.copy(nombre = v) } },
            imeAction = ImeAction.Next,
            label = "Nombre", isError = !form.nombreOk, errorText = TextosPerfil.ERROR_NOMBRE, validarAlSalir = true, forzarError = err,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier,
        )
        ShalevTextField(
            filtro = FiltroEntrada.PERSONA,
            value = form.apellidos, onValueChange = { v -> acciones.onEditar { it.copy(apellidos = v) } },
            imeAction = ImeAction.Next,
            label = "Apellidos", isError = !form.apellidosOk, errorText = TextosPerfil.ERROR_NOMBRE, validarAlSalir = true, forzarError = err,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier,
        )
        ShalevTextField(
            filtro = FiltroEntrada.CARNE,
            value = form.ci, onValueChange = { v -> acciones.onEditar { it.copy(ci = v) } },
            imeAction = ImeAction.Next,
            label = "Carné de identidad", isError = !form.ciOk, errorText = TextosPerfil.ERROR_CI, validarAlSalir = true, forzarError = err,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier,
        )
        if (state.telefonosPerfil.isNotEmpty()) {
            ShalevSecondaryText("Tus teléfonos")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.xs, Alignment.CenterHorizontally)) {
                state.telefonosPerfil.forEach { n ->
                    ShalevChip(label = NumberPresentation.phone(n), selected = form.telefonoSeleccionado(n), onClick = { acciones.onEditar { it.conTelefono(n) } })
                }
            }
        }
        ShalevTextField(
            filtro = FiltroEntrada.TELEFONO,
            value = form.telefono, onValueChange = { v -> acciones.onEditar { it.conTelefono(v) } },
            imeAction = ImeAction.Done,
            label = "Teléfono", placeholder = "5XXXXXXX", isError = !form.telefonoOk, errorText = TextosPerfil.ERROR_MOVIL, validarAlSalir = true, forzarError = err,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier,
        )
        if (!state.perfilVacio) {
            ShalevSecondaryButton(text = "Listo", icon = ShalevIcons.Listo, onClick = acciones.onCerrarEdicion)
        }
    }
}

/** Claves de los paneles informativos de esta pantalla (ver [PanelesInformativos]). */
private object PanelesLicencia {
    const val BLOQUEO = "bloqueo"
    const val ESTADO = "estado"
    const val PRECIOS = "precios"
    fun aviso(a: AvisoLicencia) = "aviso.${a.name}"
}

/**
 * 0.30.1: Card «Precios de la licencia». Una fila por tipo: el precio base y, debajo, lo que suma cada app secundaria
 * (empleado). Los importes salen de [TipoLicencia] (los mismos que cobra la solicitud), nunca se repiten a mano.
 */
@Composable
private fun PreciosLicenciaCard(paneles: PanelesInformativos) {
    ShalevAccordionCard(
        title = TextosLicencia.PRECIOS_TITULO,
        subtitle = TextosLicencia.PRECIOS_DETALLE,
        icon = ShalevIcons.Precios,
        expanded = paneles.abierto(PanelesLicencia.PRECIOS),
        onToggle = { paneles.alternar(PanelesLicencia.PRECIOS) },
        modifier = Modifier.testTag(LicenciaTags.PRECIOS).coachMark(ObjetivosRecorrido.LICENCIA_PRECIOS),
    ) {
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

/** 0.21.0 (C4): selector de secundarias con flechas de disminuir/aumentar y precio total actualizado al momento. */
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
            ShalevIcons.Disminuir, "Una secundaria menos", mostrarTexto = false, onClick = { acciones.onEditar { it.conSecundarias(mostradas - 1, minimo) } },
            enabled = mostradas > minimo, style = IconActionStyle.Tonal, modifier = Modifier.testTag(LicenciaTags.SECUNDARIAS_MENOS), repetible = true,
        )
        Text(
            mostradas.toString(), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag(LicenciaTags.SECUNDARIAS).semantics { contentDescription = TextosLicencia.secundarias(mostradas) },
        )
        ShalevIconAction(
            ShalevIcons.Aumentar, "Una secundaria más", mostrarTexto = false, onClick = { acciones.onEditar { it.conSecundarias(mostradas + 1, minimo) } },
            enabled = mostradas < cu.shalev.licencia.contract.GlContract.SECUNDARIAS_MAX, style = IconActionStyle.Tonal, repetible = true,
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
