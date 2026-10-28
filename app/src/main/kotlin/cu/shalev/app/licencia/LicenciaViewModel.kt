package cu.shalev.app.licencia

import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.PerfilRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Licencia
import cu.shalev.domain.model.Perfil
import cu.shalev.domain.usecase.ActivarLicencia
import cu.shalev.domain.usecase.SolicitarLicencia
import cu.shalev.licencia.contract.Via
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LicenciaUiState(
    val licencia: Licencia? = null,
    val form: LicenciaForm = LicenciaForm(),
    val telefonosPerfil: List<String> = emptyList(),
    val perfilVacio: Boolean = true,
    val ocupado: Boolean = false,
    /** 0.22.0 (L-c): el sistema ya mostró su diálogo de cámara (para distinguir «denegado para siempre»). */
)

sealed interface LicenciaEvento {
    /** 0.23.0: [texto] = datos legibles + renglón en blanco + solicitud cifrada `SPVIR1:`. Solo texto (0.23.1). */
    data class Enviar(val via: Via, val texto: String) : LicenciaEvento
    data class Mensaje(val texto: String) : LicenciaEvento
}

@HiltViewModel
class LicenciaViewModel @Inject constructor(
    private val licenciaRepo: LicenciaRepository,
    private val perfilRepo: PerfilRepository,
    private val solicitar: SolicitarLicencia,
    private val activar: ActivarLicencia,
    private val preferenciasRepo: cu.shalev.domain.repository.PreferenciasRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LicenciaUiState())
    val state: StateFlow<LicenciaUiState> = _state.asStateFlow()

    private val eventos = Channel<LicenciaEvento>(Channel.BUFFERED)
    val events: Flow<LicenciaEvento> = eventos.receiveAsFlow()

    private var tipoElegidoPorUsuario = false
    private var secundariasElegidasPorUsuario = false

    init {
        viewModelScope.launch {
            // 0.21.0 (C4): tipo y nº de secundarias sugeridos hasta que el usuario los cambie.
            kotlinx.coroutines.flow.combine(licenciaRepo.snapshot, preferenciasRepo.preferencias) { lic, p -> lic to p.empleadosPrevistos }
                .collect { (lic, previstos) ->
                    _state.update { s ->
                        var form = if (lic != null && !tipoElegidoPorUsuario) s.form.copy(tipo = lic.tipoSugerido()) else s.form
                        if (lic != null && !secundariasElegidasPorUsuario) {
                            val sugeridas = if (lic.estado is cu.shalev.licencia.LicenseState.Perpetual && lic.ampliable()) {
                                (lic.instalada?.secundarias ?: 0) + 1
                            } else {
                                lic.secundariasSugeridas(previstos)
                            }
                            form = form.conSecundarias(sugeridas)
                        }
                        if (lic != null && !lic.ampliable() && form.ampliandoSecundarias) {
                            form = form.copy(ampliandoSecundarias = false)
                        }
                        s.copy(licencia = lic, form = form)
                    }
                }
        }
        viewModelScope.launch {
            // Perfil → Licencia: mientras el usuario no esté editando, el formulario refleja el Perfil.
            perfilRepo.perfil.collect { p -> _state.update { it.conPerfil(p) } }
        }
        viewModelScope.launch { licenciaRepo.refrescar() }
    }


    /**
     * 0.22.0 (L-e): «Renovar igual»: mismo tipo y secundarias que la instalada. Devuelve si los datos del solicitante
     * son válidos (la pantalla salta a «Pide la licencia»; si no, a «Revisa tus datos» con los errores marcados).
     */
    fun renovarIgual(): Boolean {
        val instalada = _state.value.licencia?.instalada ?: return false
        editar { it.igualQue(instalada) }
        val ok = _state.value.form.valido
        if (!ok) editar { it.copy(mostrarErrores = true, editando = true) }
        return ok
    }

    /** Prepara una solicitud de ampliación de secundarias (+1 inicial) sobre la licencia vigente instalada. */
    fun ampliarSecundarias(): Boolean {
        val lic = _state.value.licencia ?: return false
        val instalada = lic.instalada?.takeIf { lic.ampliable() } ?: return false
        editar { it.conModoAmpliacion(true, instalada) }
        val ok = _state.value.form.valido
        if (!ok) editar { it.copy(mostrarErrores = true, editando = true) }
        return ok
    }

    private var perfilCargado = false

    /** Primera carga: siempre. Después: solo si no se está editando (no se pisan datos a medio escribir). */
    private fun LicenciaUiState.conPerfil(p: Perfil): LicenciaUiState = copy(
        form = if (!perfilCargado || !form.editando) form.desdePerfil(p).also { perfilCargado = true } else form,
        telefonosPerfil = p.telefonos.map { it.numero },
        perfilVacio = p.vacio,
    )

    fun editar(transform: (LicenciaForm) -> LicenciaForm) {
        _state.update { s ->
            val nuevo = transform(s.form)
            if (nuevo.tipo != s.form.tipo) tipoElegidoPorUsuario = true
            if (nuevo.secundarias != s.form.secundarias) secundariasElegidasPorUsuario = true
            s.copy(form = nuevo)
        }
    }

    fun abrirEdicion() = editar { it.copy(editando = true) }
    fun cerrarEdicion() = editar { it.cerrarEdicion() }

    /** Los datos introducidos se guardan en el Perfil al solicitar (Licencia → Perfil, en SolicitarLicencia). */
    fun solicitarLicencia() {
        val lic = _state.value.licencia
        val f = _state.value.form
        if (!f.valido) { editar { it.copy(mostrarErrores = true, editando = true) }; return }
        val esAmpliacion = lic?.accionSolicitud(f.ampliandoSecundarias) == AccionSolicitud.AMPLIAR
        if (!esAmpliacion && !f.recuperaOk) {
            viewModelScope.launch { eventos.send(LicenciaEvento.Mensaje(TextosRecuperacion.ID_INVALIDO)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(ocupado = true) }
            when (val r = solicitar(f.aInput(lic))) {
                is AppResult.Ok -> {
                    editar { it.copy(editando = false, mostrarErrores = false) }
                    eventos.send(LicenciaEvento.Enviar(f.via, r.value.texto))
                }
                is AppResult.Err -> eventos.send(LicenciaEvento.Mensaje(mensajeErrorSolicitud(r.error)))
            }
            _state.update { it.copy(ocupado = false) }
        }
    }

    fun activarLicencia() {
        val texto = _state.value.form.mensajeLicencia
        if (texto.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(ocupado = true) }
            val r = activar(texto)
            _state.update { it.copy(ocupado = false, form = it.form.copy(mensajeLicencia = "")) }
            eventos.send(LicenciaEvento.Mensaje(mensajeActivacion(r)))
        }
    }
}

/** 0.25.0 (§3): textos de la recuperación automática de la licencia. */
object TextosRecuperacion {
    const val TITULO = "¿Ya tenías licencia en otro teléfono?"
    const val DETALLE = "Escribe o pega el ID de tu licencia anterior: el desarrollador la pasa a este teléfono gratis, con el " +
        "mismo vencimiento y secundarias. El teléfono anterior se bloquea, borra sus datos y pide desinstalar Shalev."
    const val CAMPO = "ID de la licencia anterior (opcional)"
    const val ID_INVALIDO = "El ID de la licencia anterior no es válido. Cópialo del mensaje de licencia (ID: …)."
    const val DESDE_RESPALDO = "Rellenado con la licencia del respaldo importado."
}

/** Rellena el ID a recuperar con el del respaldo importado ([cu.shalev.domain.model.EstadoApp.licenciaRecuperable]). */
@dagger.hilt.android.lifecycle.HiltViewModel
class RecuperacionViewModel @javax.inject.Inject constructor(
    estadoApp: cu.shalev.domain.repository.EstadoAppRepository,
) : ViewModel() {
    val recuperable: StateFlow<cu.shalev.domain.model.LicenciaRecuperable?> = estadoApp.estado
        .map { it.licenciaRecuperable }
        .catch { emit(null) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)
}
