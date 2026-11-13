package cu.shalev.app.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.domain.model.PermisosApp
import cu.shalev.domain.repository.TipoAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * P37: lo que puede hacer esta app. En la principal, todo (valor por defecto: pantallas y tests de siempre no cambian).
 * En una secundaria, solo lo que el dueño permitió: las pantallas ocultan lo demás (y los repositorios lo rechazan).
 */
val LocalPermisosApp = staticCompositionLocalOf { PermisosApp.PRINCIPAL }

@HiltViewModel
class PermisosAppViewModel @Inject constructor(tipo: TipoAppRepository) : ViewModel() {
    val permisos: StateFlow<PermisosApp> = tipo.permisos.stateIn(viewModelScope, SharingStarted.Eagerly, PermisosApp.PRINCIPAL)
}
