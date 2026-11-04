package cu.shalev.app.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.designsystem.component.ShalevAvisoExito
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 1.0.4: aviso flotante de «operación realizada» compartido por toda la app. Lo pinta [AvisoExitoHost]
 * una sola vez en MainScaffold, así que cualquier pantalla solo tiene que llamar a [mostrar].
 */
object AvisoExito {
    private val _texto = MutableStateFlow<String?>(null)
    val texto: StateFlow<String?> = _texto

    fun mostrar(mensaje: String) { _texto.value = mensaje }
    fun cerrar() { _texto.value = null }
}

@Composable
fun AvisoExitoHost(modifier: Modifier = Modifier) {
    val texto by AvisoExito.texto.collectAsStateWithLifecycle()
    texto?.let { ShalevAvisoExito(it, AvisoExito::cerrar, modifier) }
}
