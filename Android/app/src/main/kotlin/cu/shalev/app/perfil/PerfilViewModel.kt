package cu.shalev.app.perfil

import cu.shalev.domain.repository.PerfilRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.repository.FotoRepository
import cu.shalev.domain.usecase.GuardarPerfil
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [form] = lo que ve el usuario: el Perfil guardado mientras no toque nada, o su borrador.
 * [sinGuardar] = hay cambios pendientes (habilita "Guardar" y pregunta antes de salir).
 */
data class PerfilUiState(
    val cargado: Boolean = false,
    val perfil: Perfil = Perfil(),
    val form: DatosPerfilForm = DatosPerfilForm(),
    val guardando: Boolean = false,
    /** La selección se copia al almacenamiento privado antes de asociarla al Perfil. */
    val importandoLogo: Boolean = false,
    /** 0.21.0 (C12): módulos activos del negocio; null = no se muestra (app secundaria o aún cargando). */
    val modulos: Set<cu.shalev.domain.model.Modulo>? = null,
    /** ID para recuperar la licencia en otro teléfono; null = no se muestra la tarjeta (app secundaria o cargando). */
    val idLicencia: IdLicenciaUi? = null,
) {
    val sinGuardar: Boolean get() = cargado && form.cambiado(perfil)
}

/**
 * Datos personales y comerciales del Perfil. Las listas de teléfonos y tarjetas las gestiona PagoElectronicoViewModel
 * (mismas reglas y la misma hoja de edición). Los datos personales se ven al momento en Licencia (desdePerfil).
 */
@HiltViewModel
class PerfilViewModel @Inject constructor(
    private val perfilRepo: PerfilRepository,
    private val guardarPerfil: GuardarPerfil,
    private val preferenciasRepo: cu.shalev.domain.repository.PreferenciasRepository,
    private val tipoAppRepo: cu.shalev.domain.repository.TipoAppRepository,
    private val licenciaRepo: cu.shalev.domain.repository.LicenciaRepository,
    private val fotos: FotoRepository,
) : ViewModel() {

    /** null = sin borrador: el formulario refleja el Perfil guardado. */
    private val borrador = MutableStateFlow<DatosPerfilForm?>(null)
    private val guardando = MutableStateFlow(false)
    private val importandoLogo = MutableStateFlow(false)
    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes: Flow<String> = _mensajes.receiveAsFlow()

    private val modulos = combine(preferenciasRepo.preferencias, tipoAppRepo.tipo) { pr, t ->
        pr.modulos.takeIf { t != cu.shalev.domain.model.TipoApp.SECUNDARIA }
    }

    private val idLicencia = combine(licenciaRepo.snapshot, tipoAppRepo.tipo) { l, t ->
        if (t == cu.shalev.domain.model.TipoApp.SECUNDARIA || l == null) null else IdLicenciaUi(idRecuperacion(l))
    }

    private val perfilEditando = combine(perfilRepo.perfil, borrador, guardando, importandoLogo) { p, b, g, importando ->
        PerfilUiState(
            cargado = true, perfil = p, form = b ?: DatosPerfilForm.desde(p), guardando = g,
            importandoLogo = importando,
        )
    }

    val state: StateFlow<PerfilUiState> = combine(perfilEditando, modulos, idLicencia) { base, m, id ->
        base.copy(modulos = m, idLicencia = id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerfilUiState())

    fun editar(f: (DatosPerfilForm) -> DatosPerfilForm) = borrador.update { f(it ?: state.value.form) }

    fun descartar() { borrador.value = null }

    /** Importa el logo a una ruta privada y estable antes de guardarlo en el Perfil. */
    fun logoElegido(uri: String) {
        if (importandoLogo.value) return
        importandoLogo.value = true
        viewModelScope.launch {
            try {
                when (val r = fotos.importar(uri)) {
                    is AppResult.Ok -> editar { it.copy(logoUri = r.value) }
                    is AppResult.Err -> _mensajes.send(TextosPerfil.ERROR_LOGO)
                }
            } finally {
                importandoLogo.value = false
            }
        }
    }

    fun quitarLogo() = editar { it.copy(logoUri = null) }

    /** 0.21.0 (C12): se guarda al momento (no depende del botón Guardar de los datos). Nunca deja 0 módulos. */
    fun alternarModulo(m: cu.shalev.domain.model.Modulo) {
        val actual = state.value.modulos ?: return
        val nuevo = cu.shalev.domain.model.Modulo.alternar(actual, m)
        if (nuevo.isEmpty()) return
        viewModelScope.launch { preferenciasRepo.guardarModulos(nuevo) }
    }

    fun guardar() {
        val form = state.value.form
        if (!form.valido) { borrador.value = form.copy(mostrarErrores = true); return }
        if (guardando.value || importandoLogo.value) return
        guardando.value = true
        viewModelScope.launch {
            // Se relee el Perfil: las listas pueden haber cambiado mientras se editaban los datos.
            val actual = perfilRepo.perfil.first()
            when (val r = guardarPerfil(form.aplicarA(actual))) {
                is AppResult.Ok -> { borrador.value = null; _mensajes.send(TextosPerfil.GUARDADO) }
                is AppResult.Err -> _mensajes.send(mensajePerfil(r.error))
            }
            guardando.value = false
        }
    }
}
