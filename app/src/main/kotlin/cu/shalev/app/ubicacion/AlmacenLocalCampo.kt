package cu.shalev.app.ubicacion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import cu.shalev.domain.repository.VentanaUbicacion

@Composable
fun AlmacenLocalCampo(ventana: VentanaUbicacion, editable: Boolean, viewModel: AlmacenLocalViewModel = hiltViewModel()) {
    // Descontinuado: el componente se ha unificado en LocalCampo (ubicado en Inicio).
    // Se mantiene por compatibilidad con InventarioScreen y ServiciosScreen que se actualizarán.
    // Esta función ahora redirige a LocalCampo si es posible, o se elimina en el futuro.
    LocalCampo(editable = editable, viewModel = viewModel as LocalViewModel)
}
