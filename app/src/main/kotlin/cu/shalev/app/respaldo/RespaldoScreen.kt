package cu.shalev.app.respaldo

import cu.shalev.designsystem.component.ShalevListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.common.SecureWindow
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.AutoColapsarPanelesAlEscribir
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.ShalevAccordionCard
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.rememberPanelesInformativos
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevLinearProgress
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.ShalevStepper
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.component.shalevContentWidth
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing
import java.time.ZoneId

object RespaldoTags {
    const val CONTRASENA = "respaldo.contrasena"
    const val PROTEGER = "respaldo.proteger"
    const val SIN_CONTRASENA = "respaldo.sin_contrasena"
    const val REPETIR = "respaldo.repetir"
    const val GUARDAR = "respaldo.guardar"
    const val IMPORTAR = "respaldo.importar"
    const val CONTRASENA_IMPORTAR = "respaldo.contrasena_importar"
    const val PROGRESO = "respaldo.progreso"
    const val RESULTADO = "respaldo.resultado"
    const val ARCHIVO = "respaldo.archivo"
    const val STEPPER = "respaldo.stepper"
    const val PASO_IMPORTAR = "respaldo.paso_importar"
    const val STEPPER_IMPORTAR = "respaldo.stepper_importar"
    const val HECHO = "respaldo.hecho"
    const val OTRO_RESPALDO = "respaldo.otro_respaldo"
}

class AccionesRespaldo(
    val onEditarExport: ((ExportForm) -> ExportForm) -> Unit,
    val onGuardar: () -> Unit,
    val onElegirArchivo: () -> Unit,
    val onContrasenaImport: (String) -> Unit,
    val onConfirmarImport: () -> Unit,
    val onCancelarImport: () -> Unit,
    val onCerrarResultado: () -> Unit = {},
    val onNuevoRespaldo: () -> Unit = {},
    val onGuardarCopiaPrevia: () -> Unit = {},
)

/**
 * Ajustes → Respaldo: exportar/importar el `.shalev` cifrado solo hacia y desde el almacenamiento interno.
 * "Guardar en el teléfono" y "Elegir archivo" abren el gestor de archivos del dispositivo, enraizado en
 * Descargas internas (el usuario elige la ubicación). Sin enviar a otras apps ni recibir archivos de ellas.
 * 0.26.0: el respaldo se exporta solo como `.shalev` cifrado (sin el PDF de configuración). Sin permisos de almacenamiento. FLAG_SECURE: se escriben contraseñas.
 */
@Composable
fun RespaldoScreen(onBack: () -> Unit, viewModel: RespaldoViewModel = hiltViewModel()) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var copiaPrevia by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    // Gestor de archivos del dispositivo, enraizado en el almacenamiento interno (Descargas). Si el
    // dispositivo no resuelve esa ubicación, el selector abre donde pueda: igual lo elige el usuario.
    val guardar = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.destinoElegido(it.data?.data?.toString(), previa = copiaPrevia)
        else viewModel.destinoElegido(null)
    }
    // "*/*": algunos gestores no conservan un MIME reconocible para .shalev.
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.archivoElegido(it.data?.data?.toString())
        else viewModel.archivoElegido(null)
    }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoRespaldo.ElegirDestino -> { copiaPrevia = e.copiaPrevia; guardar.launch(crearDocumento(e.nombre)) }
                is EventoRespaldo.Mensaje -> snackbar.showSnackbar(e.texto)
            }
        }
    }
    RespaldoContent(
        state = state,
        onBack = onBack,
        acciones = AccionesRespaldo(
            onEditarExport = viewModel::editarExport,
            onGuardar = viewModel::guardarEnTelefono,
            onElegirArchivo = { abrir.launch(abrirDocumento()) },
            onContrasenaImport = viewModel::editarImport, onConfirmarImport = viewModel::confirmarImport,
            onCancelarImport = viewModel::cancelarImport,
            onCerrarResultado = viewModel::cerrarResultado,
            onNuevoRespaldo = viewModel::nuevoRespaldo,
            onGuardarCopiaPrevia = viewModel::guardarCopiaPrevia,
        ),
        snackbar = snackbar,
    )
}

/** "Guardar como" en el almacenamiento interno (Descargas). Solo proveedores locales (sin nubes). */
private fun crearDocumento(nombre: String): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = TextosRespaldo.MIME
    putExtra(Intent.EXTRA_TITLE, nombre)
    putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    putExtra(DocumentsContract.EXTRA_INITIAL_URI, descargasInternas())
}

/** "Abrir" en el almacenamiento interno (Descargas). Solo proveedores locales (sin nubes). */
private fun abrirDocumento(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = "*/*"
    putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    putExtra(DocumentsContract.EXTRA_INITIAL_URI, descargasInternas())
}

/** Carpeta Descargas del almacenamiento interno. Si no existe, el selector abre donde pueda. */
private fun descargasInternas(): android.net.Uri =
    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")

@Composable
fun RespaldoContent(
    state: RespaldoUiState,
    onBack: () -> Unit,
    acciones: AccionesRespaldo,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    zona: ZoneId = ZoneId.systemDefault(),
) {
    Scaffold(
        topBar = { ShalevTopBar(title = TextosRespaldo.TITULO, onBack = onBack) },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().shalevContentWidth().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(ShalevSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        ) {
            state.progreso?.let { TarjetaProgreso(it) }

            // La explicación es información fija: panel colapsable que se pliega solo al escribir la contraseña.
            val paneles = rememberPanelesInformativos("explicacion")
            AutoColapsarPanelesAlEscribir(paneles)
            ShalevAccordionCard(
                title = "Copia de todo el negocio",
                subtitle = TextosRespaldo.EXPLICACION,
                icon = ShalevIcons.Respaldo,
                tone = CardTone.Tonal,
                expanded = paneles.abierto("explicacion"),
                onToggle = { paneles.alternar("explicacion") },
            ) {
                Text(TextosRespaldo.EXPLICACION, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                ShalevSecondaryText(TextosRespaldo.SIN_LICENCIA)
            }

            if (state.hayCopiaPrevia) ShalevCard(title = "Copia previa a la actualización") {
                ShalevSecondaryText("La app conserva la base migrada; esta copia NO se restaura automáticamente. Puede no contener las últimas operaciones. Guárdala solo para una recuperación y haz antes un respaldo actual.")
                ShalevSecondaryButton("Guardar copia previa", acciones.onGuardarCopiaPrevia, ShalevIcons.Exportar, enabled = !state.ocupado, mostrarTexto = true)
            }
            ShalevCard(title = "Exportar") {
                Text(TextosRespaldo.CONTENIDO, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                val e = state.export
                var elegido by rememberSaveable { mutableIntStateOf(1) }
                val paso = PasosRespaldo.pasoExportar(elegido, e, hecho = state.exportado != null)
                ShalevStepper(
                    actual = paso, total = PasosRespaldo.TOTAL_EXPORTAR, titulo = PasosRespaldo.tituloExportar(paso),
                    onAtras = if (paso == 2) ({ elegido = 1 }) else null,
                    onSiguiente = if (paso == 1) ({
                        if (e.valido) elegido = 2 else acciones.onEditarExport { it.copy(mostrarErrores = true) }
                    }) else null,
                    modifier = Modifier.testTag(RespaldoTags.STEPPER),
                )
                if (paso == 1) {
                    // S-01: contraseña opcional, ENCENDIDA por defecto; apagarla es una decisión explícita.
                    ShalevListItem(
                        title = TextosRespaldo.PROTEGER,
                        indicatorColor = null,
                        leading = { Icon(ShalevIcons.Bloqueo, contentDescription = null) },
                        onClick = { acciones.onEditarExport { it.copy(conContrasena = !it.conContrasena, mostrarErrores = false) } },
                        trailing = { Switch(checked = e.conContrasena, onCheckedChange = null) },
                        modifier = Modifier.testTag(RespaldoTags.PROTEGER),
                    )
                    if (!e.conContrasena) {
                        ShalevSecondaryText(TextosRespaldo.SIN_CONTRASENA_AVISO, modifier = Modifier.testTag(RespaldoTags.SIN_CONTRASENA))
                    }
                }
                if (paso == 1 && e.conContrasena) {
                    CampoContrasena(
                        e.contrasena, { v -> acciones.onEditarExport { it.copy(contrasena = v) } }, "Contraseña",
                        error = e.errorContrasena, validarAlSalir = true, forzarError = e.mostrarErrores,
                        ayuda = "Anótala en un lugar seguro: sin ella no se puede recuperar",
                        tag = RespaldoTags.CONTRASENA, imeAction = ImeAction.Next,
                    )
                    CampoContrasena(
                        e.repetir, { v -> acciones.onEditarExport { it.copy(repetir = v) } }, "Repite la contraseña",
                        error = e.errorRepetir, validarAlSalir = true, forzarError = e.mostrarErrores, tag = RespaldoTags.REPETIR,
                        imeAction = ImeAction.Done,
                        onImeAction = { if (e.valido) elegido = 2 else acciones.onEditarExport { it.copy(mostrarErrores = true) } },
                    )
                } else if (paso == 1) {
                    // Sin contraseña no hay campos: el paso 1 es solo el interruptor y el aviso de arriba.
                } else if (paso == 3) {
                    Text(
                        state.exportado.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(RespaldoTags.HECHO),
                    )
                    ShalevButtonRow {
                        ShalevSecondaryButton(
                            PasosRespaldo.OTRO_RESPALDO, onClick = { elegido = 1; acciones.onNuevoRespaldo() }, icon = ShalevIcons.Respaldo,
                            modifier = Modifier.testTag(RespaldoTags.OTRO_RESPALDO),
                        )
                    }
                } else {
                    ShalevSecondaryText(if (e.conContrasena) TextosRespaldo.CONTRASENA_LISTA else TextosRespaldo.LISTO_SIN_CONTRASENA)
                    ShalevButtonRow {
                        ShalevPrimaryButton(
                            "Guardar en el teléfono", onClick = acciones.onGuardar, enabled = !state.ocupado, icon = ShalevIcons.Exportar,
                            modifier = Modifier.testTag(RespaldoTags.GUARDAR),
                        )
                    }
                }
            }

            ShalevCard(title = "Importar") {
                val comprobando = state.progreso?.texto == EtapasRespaldo.COMPROBANDO && state.importacion == null
                val pasoImp = PasosRespaldo.pasoImportarTarjeta(comprobando, state.importacion)
                ShalevStepper(
                    actual = pasoImp, total = PasosRespaldo.TOTAL_IMPORTAR, titulo = PasosRespaldo.tituloImportar(pasoImp),
                    modifier = Modifier.testTag(RespaldoTags.STEPPER_IMPORTAR),
                )
                Text(TextosRespaldo.IMPORTAR_AYUDA, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                ShalevButtonRow {
                    ShalevSecondaryButton(
                        "Elegir archivo", onClick = acciones.onElegirArchivo, enabled = !state.ocupado, icon = ShalevIcons.ElegirArchivo,
                        modifier = Modifier.testTag(RespaldoTags.IMPORTAR),
                    )
                }
            }
        }
    }

    state.importacion?.let { imp ->
        ShalevDialog(
            title = "Importar respaldo",
            onDismiss = acciones.onCancelarImport,
            onConfirm = acciones.onConfirmarImport.takeUnless { state.ocupado },
            confirmDescription = "Importar",
            destructive = true,
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs)) {
                    Text(
                        listOfNotNull(describirArchivo(imp.info, zona), imp.nombre).joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag(RespaldoTags.ARCHIVO),
                    )
                    ShalevSecondaryText(PasosRespaldo.COMPROBADO)
                    ShalevSecondaryText(
                        PasosRespaldo.textoImportar(imp),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(RespaldoTags.PASO_IMPORTAR),
                    )
                    Text(TextosRespaldo.AVISO_IMPORTAR, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (PasosRespaldo.pideContrasena(imp)) {
                        CampoContrasena(
                            imp.contrasena, acciones.onContrasenaImport, "Contraseña del respaldo",
                            error = imp.error, tag = RespaldoTags.CONTRASENA_IMPORTAR, imeAction = ImeAction.Done,
                            onImeAction = { if (!state.ocupado && imp.contrasena.isNotEmpty()) acciones.onConfirmarImport() },
                        )
                    } else {
                        ShalevSecondaryText(TextosRespaldo.SIN_CONTRASENA_AVISO) // 0.27.0 (T10): no se pide contraseña
                    }
                    state.progreso?.let { p ->
                        ShalevLinearProgress(progress = p.fraccion)
                        ShalevSecondaryText(p.etiqueta)
                    }
                }
            },
        )
    }

    state.resultado?.let { r ->
        ShalevDialog(
            title = r.titulo,
            text = r.detalle,
            onDismiss = acciones.onCerrarResultado,
            onConfirm = acciones.onCerrarResultado,
            confirmDescription = TextosRespaldo.ACEPTAR,
            dismissDescription = "Cerrar",
            modifier = Modifier.testTag(RespaldoTags.RESULTADO),
        )
    }
}

/** Paso n de m con barra determinada. Región "viva": TalkBack anuncia cada paso. */
@Composable
private fun TarjetaProgreso(p: Progreso) {
    ShalevCard(tone = CardTone.Tonal, modifier = Modifier.testTag(RespaldoTags.PROGRESO)) {
        Text(
            p.etiqueta, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        ShalevLinearProgress(progress = p.fraccion)
    }
}

@Composable
private fun CampoContrasena(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    error: String?,
    tag: String,
    ayuda: String? = null,
    imeAction: ImeAction? = null,
    onImeAction: (() -> Unit)? = null,
    validarAlSalir: Boolean = false,
    forzarError: Boolean = false,
) {
    ShalevTextField(
        filtro = FiltroEntrada.LIBRE,
        value = valor, onValueChange = onCambio, label = etiqueta, imeAction = imeAction, onImeAction = onImeAction,
        validarAlSalir = validarAlSalir, forzarError = forzarError,
        isError = error != null, errorText = error, supportingText = ayuda,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.testTag(tag),
    )
}
