package cu.shalev.app.perfil

import cu.shalev.app.recorrido.ObjetivosRecorrido
import cu.shalev.designsystem.component.coachMark
import cu.shalev.app.recorrido.RecorridoModulo
import cu.shalev.app.recorrido.Recorrido
import cu.shalev.designsystem.component.ShalevCheckbox
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevListItem
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.app.common.SecureWindow
import cu.shalev.app.common.rememberSalidaProtegida
import cu.shalev.designsystem.component.FiltroEntrada
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.PersistableBundle
import android.text.TextPaint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.launch
import cu.shalev.designsystem.component.ShalevAvisosState
import cu.shalev.designsystem.component.ShalevBottomSheet
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.ShalevTextButton
import cu.shalev.designsystem.component.ShalevTextField
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.component.shalevContentWidth
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing
import cu.shalev.designsystem.component.rememberAvisos
import java.io.File

object PerfilTags {
    const val MODULOS = "perfil.modulos"
    fun modulo(m: cu.shalev.domain.model.Modulo) = "perfil.modulo.${m.name}"
    const val NOMBRE = "perfil.nombre"
    const val APELLIDOS = "perfil.apellidos"
    const val CI = "perfil.ci"
    const val NOMBRE_NEGOCIO = "perfil.nombre_negocio"
    const val LOGO = "perfil.logo"
    const val GUARDAR = "perfil.guardar"
    const val ID_MOSTRAR = "perfil.id_licencia.mostrar"
    const val ID_DIALOGO = "perfil.id_licencia.dialogo"
    const val ID_TEXTO = "perfil.id_licencia.texto"
    const val ID_COPIAR = "perfil.id_licencia.copiar"
    const val ID_EXPORTAR = "perfil.id_licencia.exportar"
    const val ID_EXPORTAR_IMAGEN = "perfil.id_licencia.exportar_imagen"
    const val ID_EXPORTAR_TEXTO = "perfil.id_licencia.exportar_texto"
}

class AccionesPerfil(
    val onEditar: ((DatosPerfilForm) -> DatosPerfilForm) -> Unit,
    val onGuardar: () -> Unit,
    val onDescartar: () -> Unit,
    /** Logo o foto del negocio, ya elegida en Cámara/Galería. */
    val onLogo: (String) -> Unit = {},
    val onQuitarLogo: () -> Unit = {},
    /** 0.21.0 (C12). */
    val onModulo: (cu.shalev.domain.model.Modulo) -> Unit = {},
)

/**
 * Ajustes → Perfil: datos personales, nombre comercial y logo/foto del negocio. FLAG_SECURE: ni capturas ni miniatura en Recientes.
 * P28: los teléfonos y tarjetas ya no se repiten aquí; viven solo en Pago electrónico (decisión del usuario,
 * se aparta a propósito de Shalev.txt 119–123).
 */
@Composable
fun PerfilScreen(
    onBack: () -> Unit,
    viewModel: PerfilViewModel = hiltViewModel(),
) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberAvisos()
    LaunchedEffect(viewModel) { viewModel.mensajes.collect { snackbar.showSnackbar(it) } }
    RecorridoModulo(Recorrido.PERFIL, listo = true) {
        PerfilContent(
            state = state,
            onBack = onBack,
            acciones = AccionesPerfil(
                viewModel::editar, viewModel::guardar, viewModel::descartar,
                viewModel::logoElegido, viewModel::quitarLogo, viewModel::alternarModulo,
            ),
            snackbar = snackbar,
        )
    }
}

@Composable
fun PerfilContent(
    state: PerfilUiState,
    onBack: () -> Unit,
    acciones: AccionesPerfil,
    snackbar: ShalevAvisosState = rememberAvisos(),
) {
    val atras = rememberSalidaProtegida(state.sinGuardar, { acciones.onDescartar(); onBack() }, TextosPerfil.DESCARTAR_TEXTO)
    var verId by rememberSaveable { mutableStateOf(false) }
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    Scaffold(
        topBar = { ShalevTopBar(title = TextosPerfil.TITULO, onBack = atras) },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().shalevContentWidth().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(vertical = ShalevSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.xs),
        ) {
            item(key = "intro") {
                ShalevCard(tone = CardTone.Tonal, modifier = Modifier.padding(horizontal = ShalevSpacing.md)) {
                    Text(TextosPerfil.EXPLICACION, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            item(key = "datos") { DatosPersonales(state, acciones) }
            state.idLicencia?.let { item(key = "id_licencia") { TarjetaIdLicencia(onMostrar = { verId = true }) } }
            state.modulos?.let { m -> item(key = "modulos") { ModulosNegocio(m, acciones) } }
        }
    }
    val idLicencia = state.idLicencia
    if (verId && idLicencia != null) {
        DialogoIdLicencia(
            id = idLicencia.id,
            nombreNegocio = state.form.nombreNegocio.ifBlank { state.perfil.nombreNegocio },
            onCerrar = { verId = false },
            onCopiar = { id ->
                copiarIdLicencia(contexto, id)
                verId = false
                alcance.launch { snackbar.showSnackbar(TextosIdLicencia.COPIADO) }
            },
        )
    }
}

/** Tarjeta de Perfil con el botón que muestra el ID de la licencia. */
@Composable
private fun TarjetaIdLicencia(onMostrar: () -> Unit) {
    ShalevCard(title = TextosIdLicencia.TITULO, modifier = Modifier.padding(horizontal = ShalevSpacing.md).coachMark(ObjetivosRecorrido.PERFIL_ID_LICENCIA)) {
        ShalevSecondaryText(TextosIdLicencia.EXPLICACION)
        ShalevButtonRow {
            ShalevSecondaryButton(
                TextosIdLicencia.BOTON, onClick = onMostrar, icon = ShalevIcons.Licencia,
                modifier = Modifier.testTag(PerfilTags.ID_MOSTRAR),
            )
        }
    }
}

/**
 * Ventana con el ID de la licencia (seleccionable, en monoespaciada para leerlo sin confundir caracteres), el botón
 * Copiar y la opción de exportar como imagen o texto. Sin licencia activada solo explica cuándo aparece.
 */
@Composable
internal fun DialogoIdLicencia(
    id: String?,
    nombreNegocio: String = "",
    onCerrar: () -> Unit,
    onCopiar: (String) -> Unit,
) {
    val contexto = LocalContext.current
    var mostrarExportar by rememberSaveable { mutableStateOf(false) }

    ShalevDialog(
        title = TextosIdLicencia.DIALOGO,
        onDismiss = onCerrar,
        onConfirm = id?.let { { onCopiar(it) } },
        confirmDescription = TextosIdLicencia.COPIAR_DESCRIPCION,
        confirmEtiqueta = TextosIdLicencia.COPIAR,
        confirmIcon = ShalevIcons.Copiar,
        confirmTag = PerfilTags.ID_COPIAR,
        secundarias = id?.let {
            {
                ShalevTextButton(
                    text = TextosIdLicencia.EXPORTAR,
                    etiqueta = TextosIdLicencia.EXPORTAR,
                    icon = ShalevIcons.Compartir,
                    onClick = { mostrarExportar = true },
                    modifier = Modifier.testTag(PerfilTags.ID_EXPORTAR),
                )
            }
        },
        modifier = Modifier.testTag(PerfilTags.ID_DIALOGO),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShalevSpacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            if (id == null) {
                Text(TextosIdLicencia.SIN_LICENCIA, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            } else {
                Text(TextosIdLicencia.DETALLE, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                ShalevCard(tone = CardTone.Tonal) {
                    SelectionContainer {
                        Text(
                            id, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().testTag(PerfilTags.ID_TEXTO),
                        )
                    }
                }
                ShalevSecondaryText(TextosIdLicencia.AVISO)
            }
        }
    }

    if (mostrarExportar && id != null) {
        ShalevBottomSheet(
            title = TextosIdLicencia.TITULO,
            onDismiss = { mostrarExportar = false },
        ) {
            ShalevListItem(
                title = TextosIdLicencia.EXPORTAR_IMAGEN,
                subtitle = TextosIdLicencia.EXPORTAR_IMAGEN_DETALLE,
                indicatorColor = null,
                leading = { Icon(ShalevIcons.Imagen, contentDescription = null, modifier = Modifier.size(ShalevSize.icon)) },
                onClick = {
                    mostrarExportar = false
                    exportarIdImagen(contexto, id, nombreNegocio)
                },
                modifier = Modifier.testTag(PerfilTags.ID_EXPORTAR_IMAGEN),
            )
            ShalevListItem(
                title = TextosIdLicencia.EXPORTAR_TEXTO,
                subtitle = TextosIdLicencia.EXPORTAR_TEXTO_DETALLE,
                indicatorColor = null,
                leading = { Icon(ShalevIcons.Lista, contentDescription = null, modifier = Modifier.size(ShalevSize.icon)) },
                onClick = {
                    mostrarExportar = false
                    exportarIdTexto(contexto, id, nombreNegocio)
                },
                modifier = Modifier.testTag(PerfilTags.ID_EXPORTAR_TEXTO),
            )
        }
    }
}

/**
 * Exporta el ID de la licencia como texto plano compartido con la app que elija el usuario.
 */
internal fun exportarIdTexto(contexto: Context, id: String, nombreNegocio: String) {
    val negocio = nombreNegocio.trim().ifBlank { "Shalev" }
    val texto = "$negocio\nID de la licencia: $id\nConserva este ID para transferir tu licencia a otro dispositivo."
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "ID de la licencia - $negocio")
        putExtra(Intent.EXTRA_TEXT, texto)
    }
    contexto.startActivity(Intent.createChooser(intent, "Exportar ID de la licencia").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Exporta el ID de la licencia como imagen PNG con encabezado del negocio centrado.
 */
internal fun exportarIdImagen(contexto: Context, id: String, nombreNegocio: String) {
    val ancho = 1080
    val alto = 640
    val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)

    val marcaColor = android.graphics.Color.parseColor("#1F5F70")
    val textoColor = android.graphics.Color.parseColor("#1B1B1B")
    val secundarioColor = android.graphics.Color.parseColor("#5F6368")
    val fondoCaja = android.graphics.Color.parseColor("#F5F7F8")
    val bordeCaja = android.graphics.Color.parseColor("#BDC1C6")

    val m = 54f
    val negocio = nombreNegocio.trim().ifBlank { "Shalev" }

    // Encabezado inicial centrado: Nombre del Negocio
    val pNegocio = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = marcaColor
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(negocio, ancho / 2f, m + 44f, pNegocio)

    // Título
    val pTitulo = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textoColor
        textSize = 34f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("ID de la licencia", ancho / 2f, m + 110f, pTitulo)

    // Tarjeta del ID
    val cajaRect = RectF(m, m + 140f, ancho - m, m + 320f)
    val pFondoCaja = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fondoCaja }
    val pBordeCaja = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = bordeCaja
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    canvas.drawRoundRect(cajaRect, 24f, 24f, pFondoCaja)
    canvas.drawRoundRect(cajaRect, 24f, 24f, pBordeCaja)

    // Texto ID
    val pId = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textoColor
        textSize = 34f
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }
    val yId = cajaRect.centerY() - (pId.descent() + pId.ascent()) / 2f
    canvas.drawText(id, ancho / 2f, yId, pId)

    // Explicación
    val pDetalle = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secundarioColor
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("Conserva este ID para transferir tu licencia a otro dispositivo.", ancho / 2f, m + 380f, pDetalle)

    // Pie
    val pPie = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = marcaColor
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("Shalev", ancho / 2f, alto - m, pPie)

    val dir = File(contexto.cacheDir, "compartir").apply { mkdirs() }
    val f = File(dir, "Licencia_${id.take(8)}.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()

    val uri = androidx.core.content.FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", f)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "ID de la licencia - $negocio")
        clipData = ClipData.newUri(contexto.contentResolver, "ID de la licencia", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    contexto.startActivity(Intent.createChooser(intent, "Exportar ID de la licencia").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * Copia el ID al portapapeles marcado como dato sensible: Android 13+ no lo muestra en la vista previa del
 * portapapeles (se usa el nombre literal del extra para no depender de la API 33).
 */
internal fun copiarIdLicencia(contexto: Context, id: String) {
    val portapapeles = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText(TextosIdLicencia.TITULO, id)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    portapapeles.setPrimaryClip(clip)
}

@Composable
private fun DatosPersonales(state: PerfilUiState, acciones: AccionesPerfil) {
    val f = state.form
    ShalevCard(modifier = Modifier.padding(horizontal = ShalevSpacing.md)) {
        ShalevTextField(
            filtro = FiltroEntrada.PERSONA,
            value = f.nombre, onValueChange = { v -> acciones.onEditar { it.copy(nombre = v) } },
            label = "Nombre", leadingIcon = ShalevIcons.Perfil,
            isError = f.errorNombre != null, errorText = f.errorNombre, validarAlSalir = false, forzarError = f.mostrarErrores,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(PerfilTags.NOMBRE).coachMark(ObjetivosRecorrido.PERFIL_DATOS),
        )
        ShalevTextField(
            filtro = FiltroEntrada.PERSONA,
            value = f.apellidos, onValueChange = { v -> acciones.onEditar { it.copy(apellidos = v) } },
            label = "Apellidos",
            isError = f.errorApellidos != null, errorText = f.errorApellidos, validarAlSalir = false, forzarError = f.mostrarErrores,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(PerfilTags.APELLIDOS),
        )
        ShalevTextField(
            filtro = FiltroEntrada.CARNE,
            value = f.ci, onValueChange = { v -> acciones.onEditar { it.copy(ci = v) } },
            label = "Carnet de identidad (CI)", placeholder = "85010112345",
            isError = f.errorCi != null, errorText = f.errorCi, validarAlSalir = true, forzarError = f.mostrarErrores,
            supportingText = "Solo se envía al pedir la licencia",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            imeAction = ImeAction.Next,
            modifier = Modifier.testTag(PerfilTags.CI),
        )
        ShalevTextField(
            filtro = FiltroEntrada.NOMBRE,
            value = f.nombreNegocio, onValueChange = { v -> acciones.onEditar { it.copy(nombreNegocio = v) } },
            label = "Nombre del negocio", leadingIcon = ShalevIcons.Local,
            isError = f.errorNombreNegocio != null, errorText = f.errorNombreNegocio,
            validarAlSalir = true, forzarError = f.mostrarErrores,
            imeAction = ImeAction.Done, onImeAction = { if (state.sinGuardar && !state.importandoLogo) acciones.onGuardar() },
            modifier = Modifier.testTag(PerfilTags.NOMBRE_NEGOCIO),
        )
        Text("Logo o foto del negocio", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
        cu.shalev.app.common.SelectorFoto(
            uri = f.logoUri, descripcion = f.nombreNegocio.ifBlank { "negocio" }, importando = state.importandoLogo,
            onFoto = acciones.onLogo, onQuitar = acciones.onQuitarLogo,
            modifier = Modifier.testTag(PerfilTags.LOGO),
        )
        ShalevButtonRow {
            ShalevPrimaryButton(
                "Guardar", onClick = acciones.onGuardar, enabled = state.sinGuardar && !state.importandoLogo, loading = state.guardando,
                icon = ShalevIcons.Guardar, modifier = Modifier.testTag(PerfilTags.GUARDAR).coachMark(ObjetivosRecorrido.PERFIL_GUARDAR),
            )
        }
    }
}

/** 0.21.0 (C12): «Tu negocio»: qué secciones usa Shalev. Lo desmarcado desaparece de la barra inferior, Inicio y Registros. */
@Composable
private fun ModulosNegocio(modulos: Set<cu.shalev.domain.model.Modulo>, acciones: AccionesPerfil) {
    ShalevCard(title = TextosPerfil.MODULOS_TITULO, modifier = Modifier.padding(horizontal = ShalevSpacing.md).testTag(PerfilTags.MODULOS).coachMark(ObjetivosRecorrido.PERFIL_MODULOS)) {
        ShalevSecondaryText(TextosPerfil.MODULOS_EXPLICACION)
        cu.shalev.domain.model.Modulo.entries.forEach { m ->
            val marcado = m in modulos
            // No se puede desmarcar el último (la app nunca se queda sin secciones).
            val bloqueado = marcado && cu.shalev.domain.model.Modulo.alternar(modulos, m).isEmpty()
            ShalevListItem(
                title = m.etiqueta, subtitle = m.detalle, indicatorColor = null, selected = marcado,
                leading = { androidx.compose.material3.Checkbox(checked = marcado, onCheckedChange = null, enabled = !bloqueado) },
                onClick = if (bloqueado) null else ({ acciones.onModulo(m) }),
                modifier = Modifier.testTag(PerfilTags.modulo(m)),
            )
        }
    }
}
