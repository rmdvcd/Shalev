package cu.shalev.app.soporte

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import cu.shalev.app.BuildConfig
import cu.shalev.app.common.Contacto
import cu.shalev.core.contact.DeveloperContact
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevCard
import cu.shalev.designsystem.component.ShalevLogo
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSecondaryButton
import cu.shalev.designsystem.component.ShalevSecondaryText
import cu.shalev.designsystem.theme.ShalevTextos
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.component.shalevContentWidth
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSize
import cu.shalev.designsystem.token.ShalevSpacing
import kotlinx.coroutines.launch

/** Filas de la ficha (una sola fuente: DeveloperContact, la misma que usa Licencia). Pura, testeada. */
object SoporteInfo {
    val filas: List<Pair<String, String>> = listOf(
        "Desarrollador" to DeveloperContact.NOMBRE,
        "CI" to DeveloperContact.CI,
        "Teléfono" to DeveloperContact.TELEFONO_LOCAL,
        "Especialidad" to DeveloperContact.ESPECIALIDAD,
    )

    /** 0.27.0 (T12): icono de cada fila de la ficha. */
    fun icono(etiqueta: String): ImageVector = when (etiqueta) {
        "Desarrollador" -> ShalevIcons.Desarrollador
        "CI" -> ShalevIcons.Identidad
        "Teléfono" -> ShalevIcons.Telefono
        else -> ShalevIcons.Especialidad
    }

    /** 0.27.0 (T12): el pie sigue siendo cierto con la contraseña del respaldo opcional. */
    const val PIE = "Nunca te pediremos tus contraseñas ni datos de tu tarjeta."
}

object SoporteTags {
    const val FICHA = "soporte.ficha"
}

/**
 * Ajustes → Soporte (también accesible desde el bloqueo). Sin datos del usuario: no necesita FLAG_SECURE.
 * [onBack] = null en el bloqueo si no hay a dónde volver.
 */
@Composable
fun SoporteScreen(onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sinApp = { scope.launch { snackbar.showSnackbar("No hay una app disponible para enviar el mensaje") } }
    SoporteContent(
        onBack = onBack,
        version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        onWhatsapp = { if (!Contacto.whatsapp(context)) sinApp() },
        onSms = { if (!Contacto.sms(context)) sinApp() },
        snackbar = snackbar,
    )
}

@Composable
fun SoporteContent(
    onBack: (() -> Unit)?,
    version: String,
    onWhatsapp: () -> Unit,
    onSms: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = { ShalevTopBar(title = "Soporte", onBack = onBack) },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().shalevContentWidth().padding(padding).verticalScroll(rememberScrollState()).padding(ShalevSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
        ) {
            ShalevLogo(size = ShalevSize.logoLarge, contentDescription = null)
            Text(
                "¿Tienes dudas o problemas? Escribe al desarrollador.", style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() },
            )
            ShalevCard(modifier = Modifier.fillMaxWidth().testTag(SoporteTags.FICHA)) {
                SelectionContainer {
                    // 2026-10-31: cada fila alinea el icono y el texto seleccionable a la izquierda.
                    Column {
                        SoporteInfo.filas.forEach { (etiqueta, valor) -> FilaSoporte(etiqueta, valor) }
                    }
                }
            }
            ShalevButtonRow {
                ShalevSecondaryButton("Enviar SMS", onClick = onSms, icon = ShalevIcons.Sms)
                ShalevPrimaryButton("Escribir por WhatsApp", onClick = onWhatsapp, icon = ShalevIcons.WhatsApp)
            }
            ShalevSecondaryText(
                "${SoporteInfo.PIE}\nShalev $version",
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Cada dato de contacto: icono, separación constante y texto seleccionable alineado a la izquierda. */
@Composable
private fun FilaSoporte(etiqueta: String, valor: String) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = ShalevSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShalevSpacing.md),
    ) {
        Icon(
            SoporteInfo.icono(etiqueta), contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(ShalevSize.icon),
        )
        Text(
            ShalevTextos.resaltar("$etiqueta: $valor", valor),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f),
        )
    }
}
