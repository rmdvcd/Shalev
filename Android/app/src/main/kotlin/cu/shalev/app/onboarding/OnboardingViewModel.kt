package cu.shalev.app.onboarding

import cu.shalev.domain.repository.ConfiguracionInicialRepository
import cu.shalev.domain.repository.LicenciaRepository
import cu.shalev.domain.repository.PreferenciasRepository
import cu.shalev.domain.repository.PerfilRepository
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.DatosIniciales
import cu.shalev.domain.model.PasoConfiguracion
import cu.shalev.domain.usecase.GuardarAlertasIniciales
import cu.shalev.domain.usecase.GuardarDatosIniciales
import cu.shalev.domain.usecase.ObservarResumenConfiguracion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface OnboardingEvento {
    data object Terminado : OnboardingEvento
    data class Mensaje(val texto: String) : OnboardingEvento
}

/**
 * Asistente de primera ejecución. Cada paso se GUARDA al pulsar el botón principal (Perfil en SQLCipher,
 * niveles en el DataStore cifrado), así que cerrar la app a mitad no pierde lo ya hecho.
 * El índice del paso sobrevive a la muerte del proceso (SavedStateHandle).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val observarResumen: ObservarResumenConfiguracion,
    private val perfilRepo: PerfilRepository,
    private val preferenciasRepo: PreferenciasRepository,
    private val licenciaRepo: LicenciaRepository,
    private val guardarDatos: GuardarDatosIniciales,
    private val guardarAlertas: GuardarAlertasIniciales,
    private val configuracionRepo: ConfiguracionInicialRepository,
    private val ajustesDispositivo: cu.shalev.domain.repository.AjustesDispositivoRepository,
    private val guardarAcceso: cu.shalev.app.acceso.GuardarAccesoClave,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()
    private val eventos = Channel<OnboardingEvento>(Channel.BUFFERED)
    val events: Flow<OnboardingEvento> = eventos.receiveAsFlow()

    private var iniciado = false

    /** Idempotente: la pantalla lo llama al entrar. */
    fun iniciar(modo: ModoWizard, soloPaso: PasoConfiguracion? = null) {
        if (iniciado) return
        iniciado = true
        viewModelScope.launch {
            val licencia = licenciaRepo.snapshot.filterNotNull().first()
            val resumen = observarResumen().first()
            val perfil = perfilRepo.perfil.first()
            val prefs = preferenciasRepo.preferencias.first()
            val pasos = planWizard(modo, resumen, soloPaso)
            _state.value = OnboardingUiState(
                cargando = false,
                modo = modo,
                pasos = pasos,
                indice = (saved.get<Int>(K_INDICE) ?: 0).coerceIn(0, pasos.lastIndex.coerceAtLeast(0)),
                datos = DatosIniciales.desde(perfil),
                niveles = NivelesForm.desde(prefs.niveles),
                licencia = licencia,
                modulos = prefs.modulos,
                empleados = prefs.empleadosPrevistos.coerceIn(0, licencia.secundariasPermitidas),
            )
        }
        viewModelScope.launch {
            licenciaRepo.snapshot.filterNotNull().collect { l -> _state.update { it.copy(licencia = l, empleados = it.empleados.coerceAtMost(l.secundariasPermitidas)) } }
        }
        viewModelScope.launch {
            ajustesDispositivo.ajustes.collect { a -> _state.update { it.copy(accesoClave = a.accesoConClave) } }
        }
    }

    // ---------- Edición ----------

    fun editarDatos(f: (DatosIniciales) -> DatosIniciales) = _state.update { it.copy(datos = f(it.datos)) }
    fun editarNivel(c: CampoNivel, v: String) = _state.update { it.copy(niveles = it.niveles.con(c, v.take(10))) }
    fun sumarNivel(c: CampoNivel, delta: Int) = _state.update { it.copy(niveles = it.niveles.sumar(c, delta)) }
    fun restablecerNiveles() = _state.update { it.copy(niveles = NivelesForm.RECOMENDADOS, mostrarErroresNiveles = false) }

    // 0.21.0 (C12): recorrido inicial.
    fun elegirTipo(secundaria: Boolean) = _state.update { it.copy(esSecundaria = secundaria) }
    /** Marca/desmarca un módulo. Marcar VENTAS marca también INVENTARIO; desmarcar INVENTARIO desmarca VENTAS. */
    fun alternarModulo(m: cu.shalev.domain.model.Modulo) = _state.update { it.copy(modulos = cu.shalev.domain.model.Modulo.alternar(it.modulos, m)) }
    /** 0.27.0 (T11): ya confirmada la identidad por la pantalla. */
    fun guardarAccesoClave(activo: Boolean) { viewModelScope.launch { guardarAcceso(activo) } }
    fun cambiarEmpleados(n: Int) = _state.update { it.copy(empleados = n.coerceIn(0, it.licencia?.secundariasPermitidas ?: 0)) }

    // ---------- Navegación ----------

    fun atras() = irA(_state.value.indice - 1)

    /** Sin guardar. Los valores por defecto (p. ej. niveles 5/1) siguen aplicándose. */
    fun omitirPaso() = avanzar()

    fun omitirTodo() = finalizar()

    /** Guarda el paso actual (si hay algo que guardar) y avanza. */
    fun siguiente() {
        val s = _state.value
        if (s.ocupado) return
        when (s.paso) {
            PasoWizard.BIENVENIDA, null -> avanzar()
            // 0.21.0 (C12): una secundaria no configura nada aquí: todo le llega de la principal al vincularse.
            // 0.27.0 (T9): la elección queda guardada; solo una app que eligió «Secundaria» puede vincularse como tal.
            PasoWizard.TIPO_APP -> ocupado {
                ajustesDispositivo.guardarEligioSecundaria(s.esSecundaria)
                if (s.esSecundaria) { TourPendiente.marcarVincularSecundaria(); finalizar() } else avanzar()
            }
            PasoWizard.ACCESO_CLAVE -> avanzar()
            PasoWizard.OBJETIVO -> if (s.modulos.isNotEmpty()) ocupado { preferenciasRepo.guardarModulos(s.modulos); avanzar() }
            PasoWizard.EMPLEADOS -> ocupado { preferenciasRepo.guardarEmpleadosPrevistos(s.empleados); avanzar() }
            PasoWizard.DATOS -> pasoDatos(s)
            PasoWizard.ALERTAS -> pasoAlertas(s)
            PasoWizard.PRUEBA -> ocupado { configuracionRepo.confirmar(PasoConfiguracion.PRUEBA); avanzar() }
        }
    }

    private fun pasoDatos(s: OnboardingUiState) {
        if (erroresDatos(s.datos).isNotEmpty()) { _state.update { it.copy(mostrarErroresDatos = true) }; return }
        ocupado {
            when (val r = guardarDatos(s.datos)) {
                is AppResult.Ok -> { _state.update { it.copy(mostrarErroresDatos = false) }; avanzar() }
                is AppResult.Err -> { _state.update { it.copy(mostrarErroresDatos = true) }; eventos.send(OnboardingEvento.Mensaje(mensajeErrorGuardado(r.error))) }
            }
        }
    }

    private fun pasoAlertas(s: OnboardingUiState) {
        val n = s.niveles.aNiveles() ?: run { _state.update { it.copy(mostrarErroresNiveles = true) }; return }
        ocupado {
            when (val r = guardarAlertas(n)) {
                is AppResult.Ok -> avanzar()
                is AppResult.Err -> { _state.update { it.copy(mostrarErroresNiveles = true) }; eventos.send(OnboardingEvento.Mensaje(mensajeErrorGuardado(r.error))) }
            }
        }
    }

    private fun avanzar() {
        val s = _state.value
        if (s.esUltimo || s.pasos.isEmpty()) finalizar() else irA(s.indice + 1)
    }

    private fun irA(i: Int) {
        val s = _state.value
        if (i !in s.pasos.indices) return
        saved[K_INDICE] = i
        _state.update { it.copy(indice = i) }
    }

    /** Marca el asistente como visto (la app deja de mostrarlo al arrancar). Lo pendiente queda en Ajustes. */
    private fun finalizar() = ocupado {
        preferenciasRepo.completarOnboarding()
        saved.remove<Int>(K_INDICE)
        eventos.send(OnboardingEvento.Terminado)
    }

    private fun ocupado(block: suspend () -> Unit) {
        // La guarda contra la doble pulsación vive en siguiente() (if (s.ocupado) return): aquí NO se corta,
        // porque finalizar() se invoca desde dentro de otro ocupado{} (paso TIPO_APP como secundaria).
        _state.update { it.copy(ocupado = true) } // síncrono: los botones se apagan antes de lanzar el trabajo
        viewModelScope.launch {
            try { block() } finally { _state.update { it.copy(ocupado = false) } }
        }
    }

    private companion object { const val K_INDICE = "onboarding.indice" }
}
