package cu.shalev.app.vinculacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.designsystem.component.ShalevButtonRow
import cu.shalev.designsystem.component.ShalevDialog
import cu.shalev.designsystem.component.ShalevEmptyState
import cu.shalev.designsystem.component.ShalevPrimaryButton
import cu.shalev.designsystem.component.ShalevSnackbarHost
import cu.shalev.designsystem.component.ShalevTextButton
import cu.shalev.designsystem.component.ShalevTopBar
import cu.shalev.designsystem.icon.ShalevIcons
import cu.shalev.designsystem.token.ShalevSpacing

/**
 * P37: puerta de la app SECUNDARIA cuando la licencia de la principal no está activa. Aquí no se activa nada (la
 * licencia es de la principal): se sincroniza para recibir la licencia nueva, o se desvincula.
 */
@Composable
fun BloqueoSecundaria(viewModel: VinculacionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.mensajes.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        topBar = { ShalevTopBar(title = "Shalev", marca = true) },
        snackbarHost = { ShalevSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(ShalevSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ShalevSpacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShalevEmptyState(
                title = TITULO_BLOQUEO,
                detail = DETALLE_BLOQUEO,
            )
            ShalevButtonRow {
                ShalevPrimaryButton(
                    text = VinculacionLogic.SINCRONIZAR, icon = ShalevIcons.Sincronizar, onClick = viewModel::sincronizar,
                    loading = state.trabajando, enabled = !state.trabajando,
                )
            }
            ShalevButtonRow {
                ShalevTextButton(text = VinculacionLogic.DESVINCULAR, icon = ShalevIcons.Desvincular, onClick = viewModel::pedirDesvincular, enabled = !state.trabajando)
            }
        }
    }
    if (state.confirmarDesvincular) {
        ShalevDialog(
            title = "¿Desvincular esta app?",
            text = VinculacionLogic.CONFIRMAR_DESVINCULAR,
            onDismiss = viewModel::cancelarDesvincular,
            onConfirm = viewModel::confirmarDesvincular,
            confirmDescription = VinculacionLogic.DESVINCULAR,
            confirmIcon = ShalevIcons.Desvincular,
            destructive = true,
        )
    }
}

private const val TITULO_BLOQUEO = "La licencia de la app principal no está activa"
private const val DETALLE_BLOQUEO =
    "Esta app trabaja con la licencia de la app principal. Pide al dueño que la active y luego toca «Sincronizar ahora» con los dos teléfonos en la misma red (wifi, zona wifi o datos móviles)."
