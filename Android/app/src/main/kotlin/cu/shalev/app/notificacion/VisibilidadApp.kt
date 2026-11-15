package cu.shalev.app.notificacion

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Si Shalev está a la vista (alguna actividad iniciada). Lo actualiza [AvisoTurno], que ya cuenta las actividades
 * visibles; lo leen el aviso del turno y [NotificadorOperaciones] (con la app delante, los resultados los enseña la
 * isla de la app y no se duplican en la barra de notificaciones).
 */
@Singleton
class VisibilidadApp @Inject constructor() {
    private val _aLaVista = MutableStateFlow(false)
    val aLaVista: StateFlow<Boolean> = _aLaVista.asStateFlow()

    internal fun cambiar(visible: Boolean) { _aLaVista.value = visible }
}
