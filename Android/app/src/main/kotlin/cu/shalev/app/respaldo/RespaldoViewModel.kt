package cu.shalev.app.respaldo

import cu.shalev.core.time.Dates
import cu.shalev.domain.repository.RespaldoRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cu.shalev.app.common.ArchivosApp
import cu.shalev.app.common.ExportadorArchivos
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.core.time.Clock
import cu.shalev.domain.repository.EtapaRespaldo
import cu.shalev.domain.usecase.ExportarRespaldo
import cu.shalev.domain.usecase.ImportarRespaldo
import cu.shalev.app.notificacion.Operacion
import cu.shalev.app.notificacion.OperacionesEnCurso
import cu.shalev.designsystem.component.TipoAviso
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RespaldoUiState(
    val export: ExportForm = ExportForm(),
    val importacion: ImportForm? = null,
    /** Operación en curso (paso n de m). Mientras no es null, todo lo demás queda deshabilitado. */
    val progreso: Progreso? = null,
    /** Diálogo final de éxito o de fallo. */
    val resultado: ResultadoRespaldo? = null,
    /** Paso 3 de exportar («Respaldo listo»): texto con el archivo creado. null = asistente en los pasos 1–2. */
    val exportado: String? = null,
    val hayCopiaPrevia: Boolean = false,
) {
    val ocupado: Boolean get() = progreso != null
}

sealed interface EventoRespaldo {
    /** Abrir "Guardar como" del sistema con este nombre sugerido (respaldo .shalev). */
    data class ElegirDestino(val nombre: String, val copiaPrevia: Boolean = false) : EventoRespaldo
    data class Mensaje(val texto: String) : EventoRespaldo
}

/**
 * Ajustes → Respaldo. Las contraseñas solo viven en el estado de la pantalla: se pasan como CharArray al
 * caso de uso (que las borra de memoria) y se vacían del formulario al terminar. Nunca se registran.
 *
 * Solo almacenamiento interno: exportar = "Guardar como" del gestor de archivos; importar = "Abrir" del
 * gestor de archivos. Sin enviar a otras apps ni recibir archivos de ellas.
 *
 * Prompt 14: importar = (1) comprobar el archivo SIN contraseña (ajeno, cortado o dañado se explica antes de
 * pedir nada), (2) contraseña, (3) progreso por pasos, (4) diálogo de confirmación.
 */
@HiltViewModel
class RespaldoViewModel @Inject constructor(
    private val exportar: ExportarRespaldo,
    private val importar: ImportarRespaldo,
    private val respaldoRepo: RespaldoRepository,
    private val archivos: ArchivosApp,
    private val clock: Clock,
    private val copiaPrevia: cu.shalev.app.actualizacion.RespaldoPrevio,
    /** Barra de progreso y resultado en la barra de notificaciones (NotificadorOperaciones). */
    private val operaciones: OperacionesEnCurso = OperacionesEnCurso(),
) : ViewModel() {

    private val zona: ZoneId = ZoneId.systemDefault()
    private val _state = MutableStateFlow(RespaldoUiState(hayCopiaPrevia = copiaPrevia.existe))
    val state: StateFlow<RespaldoUiState> = _state.asStateFlow()
    private val _eventos = Channel<EventoRespaldo>(Channel.BUFFERED)
    val eventos: Flow<EventoRespaldo> = _eventos.receiveAsFlow()

    fun editarExport(f: (ExportForm) -> ExportForm) = _state.update { it.copy(export = f(it.export)) }
    fun cerrarResultado() = _state.update { it.copy(resultado = null) }

    /** «Hacer otro respaldo»: vuelve al paso 1 del asistente de exportar. */
    fun nuevoRespaldo() = _state.update { it.copy(exportado = null) }

    /** Enviar / guardar compartido con las demás pantallas (P17 S5). El destino lo pide la UI con su propio evento. */
    private val exportador = ExportadorArchivos<Unit>(archivos)

    private fun hoy() = Dates.localDate(clock.now(), zona)
    private fun nombre() = nombreRespaldo(hoy())

    // ------------------------------------------------------------ exportar

    /** Valida y pide a la UI el destino ("Guardar en el teléfono"). */
    fun guardarEnTelefono() {
        if (!validarExport()) return
        _eventos.trySend(EventoRespaldo.ElegirDestino(nombre()))
    }

    /** Destino elegido en el selector del sistema (null = cancelado: no pasa nada). */
    fun guardarCopiaPrevia() {
        if (!_state.value.ocupado && copiaPrevia.existe) _eventos.trySend(EventoRespaldo.ElegirDestino("Shalev_recuperacion_previa.shalev", copiaPrevia = true))
    }

    fun destinoElegido(uri: String?, previa: Boolean = false) {
        if (uri == null) return
        if (previa) {
            ejecutar(EtapasRespaldo.progreso(EtapaRespaldo.ESCRIBIENDO), TextosRespaldo.NOTIF_EXPORTANDO) {
                when (val r = exportador.aDestino(uri) { copiaPrevia.copiarA(it) }) {
                    is AppResult.Ok -> mensaje("Copia previa guardada, sin contraseña. No incluye operaciones posteriores. Haz primero un respaldo actual y usa Importar solo si necesitas recuperar esta copia.")
                    is AppResult.Err -> mensaje(mensajeRespaldo(r.error))
                }
            }
            return
        }
        val nombre = nombre()
        val con = _state.value.export.conContrasena
        ejecutar(EtapasRespaldo.progreso(EtapaRespaldo.PREPARANDO), TextosRespaldo.NOTIF_EXPORTANDO) {
            when (val r = exportador.aDestino(uri) { exportar(_state.value.export.contrasenaFinal.toCharArray(), it, ::avanzar) }) {
                is AppResult.Ok -> { limpiarExport(); _state.update { it.copy(resultado = exitoRespaldo(r.value, importado = false, nombre, con), exportado = PasosRespaldo.textoHecho(nombre, enviado = false, con)) } }
                is AppResult.Err -> mensaje(mensajeRespaldo(r.error))
            }
        }
    }

    // ------------------------------------------------------------ importar

    /**
     * Archivo elegido con "Abrir" o recibido de otra app (null = cancelado). Primero se comprueba sin contraseña:
     * si no es de Shalev, está cortado o dañado, se explica y no se pide nada.
     */
    fun archivoElegido(uri: String?, nombre: String? = null) {
        if (uri == null) return
        ejecutar(EtapasRespaldo.unico(EtapasRespaldo.COMPROBANDO), TextosRespaldo.NOTIF_COMPROBANDO) {
            val input = archivos.abrirOrigen(uri)
                ?: return@ejecutar _state.update { it.copy(resultado = fallo(AppError.Almacenamiento)) }
            when (val r = input.use { respaldoRepo.inspeccionar(it) }) {
                is AppResult.Ok -> _state.update { it.copy(importacion = ImportForm(uri, r.value, nombre = nombre)) }
                is AppResult.Err -> _state.update { it.copy(resultado = fallo(r.error)) }
            }
        }
    }

    fun editarImport(contrasena: String) =
        _state.update { s -> s.copy(importacion = s.importacion?.copy(contrasena = contrasena, error = null)) }

    fun cancelarImport() = _state.update { it.copy(importacion = null) }

    fun confirmarImport() {
        val imp = _state.value.importacion ?: return
        val pide = PasosRespaldo.pideContrasena(imp)
        if (pide && imp.contrasena.isEmpty()) {
            _state.update { it.copy(importacion = imp.copy(error = TextosRespaldo.CONTRASENA_INCORRECTA)) }
            return
        }
        ejecutar(EtapasRespaldo.progreso(EtapaRespaldo.LEYENDO), TextosRespaldo.NOTIF_IMPORTANDO) {
            val input = archivos.abrirOrigen(imp.uri)
            if (input == null) {
                _state.update { it.copy(importacion = null, resultado = fallo(AppError.Almacenamiento)) }
                return@ejecutar
            }
            when (val r = input.use { importar(it, (if (pide) imp.contrasena else "").toCharArray(), ::avanzar) }) {
                is AppResult.Ok -> _state.update { it.copy(importacion = null, resultado = exitoRespaldo(r.value, importado = true, imp.nombre)) }
                // Contraseña mal escrita: se queda el diálogo abierto para reintentar.
                is AppResult.Err -> if (r.error == AppError.ContrasenaIncorrecta) {
                    _state.update { it.copy(importacion = imp.copy(contrasena = "", error = TextosRespaldo.CONTRASENA_INCORRECTA)) }
                } else {
                    _state.update { it.copy(importacion = null, resultado = fallo(r.error)) }
                }
            }
        }
    }

    // ------------------------------------------------------------ util

    /** Llamado desde el hilo de E/S del repositorio: StateFlow es seguro entre hilos. */
    private fun avanzar(e: EtapaRespaldo) {
        val p = EtapasRespaldo.progreso(e)
        _state.update { it.copy(progreso = p) }
        tituloNotificacion?.let { notificarAvance(it, p) }
    }

    /** Título de la operación en curso en la notificación (null = ninguna). */
    @Volatile private var tituloNotificacion: String? = null

    /** Último mensaje suelto de la operación en curso (su resultado cuando no hay diálogo final). */
    @Volatile private var ultimoMensaje: String? = null

    /** Barra horizontal por etapas («Etapa 2 de 4 · Cifrando…»); una etapa única no sabe cuánto falta. */
    private fun notificarAvance(titulo: String, p: Progreso) =
        operaciones.avanzar(Operacion.RESPALDO, titulo, p.etiqueta, if (p.total > 1) p.fraccion else null)

    /**
     * Al terminar, la misma notificación pasa al resultado: el diálogo final (éxito o fallo) o el último mensaje.
     * Sin resultado (contraseña mal escrita, cancelada), se retira. Con la app delante, NotificadorOperaciones
     * no lo duplica: lo enseña la pantalla.
     */
    private fun notificarFin() {
        val r = _state.value.resultado
        val m = ultimoMensaje
        when {
            r != null -> operaciones.terminar(Operacion.RESPALDO, if (r.exito) TipoAviso.EXITO else TipoAviso.ERROR, r.titulo, r.detalle)
            m != null -> operaciones.terminar(Operacion.RESPALDO, TipoAviso.de(m), TextosRespaldo.NOTIF_TITULO, m)
            else -> operaciones.quitar(Operacion.RESPALDO)
        }
    }

    private fun validarExport(): Boolean {
        val e = _state.value.export
        if (_state.value.ocupado) return false
        if (!e.valido) { _state.update { it.copy(export = e.copy(mostrarErrores = true)) }; return false }
        return true
    }

    private fun limpiarExport() = _state.update { it.copy(export = ExportForm()) }

    private suspend fun mensaje(t: String) {
        ultimoMensaje = t
        _eventos.send(EventoRespaldo.Mensaje(t))
    }

    private fun ejecutar(inicial: Progreso, titulo: String, bloque: suspend () -> Unit) {
        if (_state.value.ocupado) return
        _state.update { it.copy(progreso = inicial, resultado = null) }
        tituloNotificacion = titulo
        ultimoMensaje = null
        notificarAvance(titulo, inicial)
        viewModelScope.launch {
            var completado = false
            try {
                bloque()
                completado = true
            } finally {
                _state.update { it.copy(progreso = null) }
                tituloNotificacion = null
                if (completado) notificarFin() else operaciones.quitar(Operacion.RESPALDO)
            }
        }
    }
}
