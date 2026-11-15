package cu.shalev.app.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.shalev.designsystem.component.ShalevAvisosState
import cu.shalev.designsystem.component.TipoAviso
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 1.0.4: aviso de «operación realizada» compartido por toda la app. Cualquier pantalla llama a [mostrar] (aunque se
 * esté yendo, p. ej. un formulario que guarda y vuelve) y [AvisoExitoHost], en MainScaffold, lo pasa a la isla de
 * avisos como éxito: se ve abajo, como el resto de avisos, y se va solo (Notificacion.md, «urgencia = permanencia»).
 */
object AvisoExito {
    private val _texto = MutableStateFlow<String?>(null)
    val texto: StateFlow<String?> = _texto

    fun mostrar(mensaje: String) { _texto.value = mensaje }
    fun cerrar() { _texto.value = null }
}

@Composable
fun AvisoExitoHost(avisos: ShalevAvisosState) {
    val texto by AvisoExito.texto.collectAsStateWithLifecycle()
    LaunchedEffect(texto) {
        val t = texto ?: return@LaunchedEffect
        avisos.mostrar(t, TipoAviso.EXITO)
        AvisoExito.cerrar()
    }
}
