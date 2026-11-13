package cu.shalev.app.vinculacion.qr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import cu.shalev.app.common.AccionPermiso
import cu.shalev.app.common.EstadoPermiso
import cu.shalev.app.common.abrirAjustesDeLaApp
import cu.shalev.app.common.explicacionCamara
import cu.shalev.app.common.rememberPermisoCamara
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevRadius
import cu.shalev.designsystem.token.ShalevSpacing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.CardTone
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevSnackbarHost
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

object QrTags {
    const val CAMARA = "qr.camara"
    const val EXPLICACION = "qr.explicacion"
    const val ACCION_PERMISO = "qr.accion_permiso"
}

/** Lector QR mínimo (solo vinculación): cámara → texto → atrás. Lo inválido lo informa quien lo usa. */
@Composable
fun QrVinculacionScreen(
    onBack: () -> Unit,
    onQrLeido: (String) -> Unit = {},
    viewModel: QrVinculacionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val permiso = rememberPermisoCamara(false, onSolicitado = {})
    val camaraOk = permiso.estado == EstadoPermiso.CONCEDIDO
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { e ->
            when (e) {
                is EventoQr.QrLeido -> onQrLeido(e.texto)
            }
        }
    }
    Scaffold(
        topBar = { ShalevTopBar(title = "Escanear QR", onBack = onBack) },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(ShalevSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        ) {
            if (!camaraOk) {
                val exp = explicacionCamara(permiso.estado)
                ShalevCard(tone = CardTone.Tonal, modifier = Modifier.testTag(QrTags.EXPLICACION)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md)) {
                        Icon(ShalevIcons.Camara, contentDescription = null)
                        Text(exp.titulo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    }
                    Text(exp.detalle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    exp.boton?.let {
                        ShalevButtonRow {
                            ShalevPrimaryButton(
                                text = it,
                                icon = if (exp.accion == AccionPermiso.ABRIR_AJUSTES) ShalevIcons.Ajustes else ShalevIcons.Camara,
                                onClick = {
                                    when (exp.accion) {
                                        AccionPermiso.SOLICITAR -> permiso.solicitar()
                                        AccionPermiso.ABRIR_AJUSTES -> abrirAjustesDeLaApp(context)
                                        AccionPermiso.NINGUNA -> Unit
                                    }
                                },
                                modifier = Modifier.testTag(QrTags.ACCION_PERMISO),
                            )
                        }
                    }
                }
            } else {
                CamaraEscaner(
                    onQr = viewModel::qrDetectado, onError = onBack,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(ShalevRadius.lg)).testTag(QrTags.CAMARA),
                )
                ShalevSecondaryText("Apunta al código QR de vinculación.", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
