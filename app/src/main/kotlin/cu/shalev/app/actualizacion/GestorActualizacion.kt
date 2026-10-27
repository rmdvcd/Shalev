package cu.shalev.app.actualizacion

import android.content.Context
import androidx.lifecycle.ViewModel
import cu.shalev.core.result.AppError
import cu.shalev.core.result.AppResult
import cu.shalev.domain.model.Actualizaciones
import cu.shalev.domain.model.ApkEnPrincipal
import cu.shalev.domain.model.InfoActualizacion
import cu.shalev.domain.model.InfoApp
import cu.shalev.domain.repository.ActualizacionesRepository
import cu.shalev.domain.repository.EstadoAppRepository
import cu.shalev.domain.repository.SecundariaRepository
import cu.shalev.domain.usecase.ComprobarActualizaciones
import cu.shalev.domain.usecase.EstadoActualizacionObligatoria
import cu.shalev.domain.model.ActualizacionObligatoria
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado visible de las actualizaciones (tarjeta de Inicio y Ajustes). */
data class EstadoActualizacion(
    /** Versión más nueva publicada en GitHub. */
    val disponible: InfoActualizacion? = null,
    /** Secundaria: la principal tiene un APK más nuevo para repartir por la red local. */
    val desdePrincipal: ApkEnPrincipal? = null,
    /** 0..1 mientras descarga; null = sin descarga. */
    val progreso: Float? = null,
    val buscando: Boolean = false,
    /** Hace falta activar «Permitir de esta fuente» para Shalev. */
    val necesitaPermiso: Boolean = false,
    /** APK comprobado y listo (se puede volver a abrir el instalador si el usuario lo cerró). */
    val listo: Boolean = false,
    val repoConfigurado: Boolean = false,
    /** 0.26.0 (P73 §6): aviso con fecha límite o bloqueo (plazo de 30 días vencido). */
    val obligatoria: ActualizacionObligatoria.Estado = ActualizacionObligatoria.Estado.Ninguna,
    val esSecundaria: Boolean = false,
    /** 0.31.0 (P5): cómo va el proceso que abre «Actualizar» en Ajustes (null = no se ha abierto). */
    val proceso: ProcesoActualizar? = null,
) {
    /** Pantalla de bloqueo en toda la app (MainScaffold la omite durante una venta). */
    val bloqueada: Boolean get() = obligatoria is ActualizacionObligatoria.Estado.Bloqueo
    /** Fecha límite (aviso o bloqueo); null = la versión no es instalable desde la app (solo aviso). */
    val limite: java.time.Instant? get() = when (val o = obligatoria) {
        is ActualizacionObligatoria.Estado.Aviso -> o.limite
        is ActualizacionObligatoria.Estado.Bloqueo -> o.limite
        ActualizacionObligatoria.Estado.Ninguna -> null
    }
    /** La tarjeta de Inicio se ve si hay versión y el usuario no la aplazó hoy. */
    val tarjetaVisible: Boolean get() = hayAviso && (obligatoria as? ActualizacionObligatoria.Estado.Aviso)?.oculto != true

    val hayAviso: Boolean get() = disponible != null || desdePrincipal != null
    val version: String? get() = desdePrincipal?.version ?: disponible?.version
    val bytes: Long get() = desdePrincipal?.bytes ?: disponible?.bytes ?: 0
}

/**
 * 0.31.0 (P5): lo que muestra el cuadro circular de «Actualizar» (Ajustes). La decisión es pura para poder probarla
 * sin red: [enCurso] = la barra gira; el resto son los tres resultados que ve el usuario.
 */
sealed interface ProcesoActualizar {
    /** Comprobando si hay versión nueva o descargándola: barra de progreso circular. */
    data class EnCurso(val progreso: Float?) : ProcesoActualizar

    /** La versión nueva está descargada y el instalador abierto. */
    data object Actualizado : ProcesoActualizar

    /** No hay versión más nueva que la instalada. */
    data object AlDia : ProcesoActualizar

    /** No se pudo comprobar o descargar (sin red). */
    data object SinConexion : ProcesoActualizar

    val enCurso: Boolean get() = this is EnCurso
}

/** 0.31.0 (P5): texto del resultado para el usuario. */
fun ProcesoActualizar.texto(): String = when (this) {
    is ProcesoActualizar.EnCurso -> if (progreso == null) TextosActualizacion.COMPROBANDO else TextosActualizacion.DESCARGANDO
    ProcesoActualizar.Actualizado -> TextosActualizacion.RESULTADO_ACTUALIZADO
    ProcesoActualizar.AlDia -> TextosActualizacion.RESULTADO_AL_DIA
    ProcesoActualizar.SinConexion -> TextosActualizacion.RESULTADO_SIN_CONEXION
}

object TextosActualizacion {
    fun disponible(version: String, bytes: Long) =
        "Versión $version disponible${if (bytes > 0) " (${"%.1f".format(java.util.Locale.US, bytes / 1_048_576.0)} MB)" else ""}."
    fun desdePrincipal(version: String) = "Versión $version disponible desde la app principal."
    const val ACTUALIZAR = "Actualizar"
    /** 0.26.0: «Más tarde» solo oculta el aviso hasta mañana; la fecha límite no cambia. */
    const val DESCARTAR = "Más tarde"
    fun obligatoriaDesde(limite: java.time.Instant, zona: java.time.ZoneId = java.time.ZoneId.systemDefault()) =
        "Obligatoria desde el ${java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(limite.atZone(zona))}."
    const val BLOQUEO_TITULO = "Actualiza Shalev para seguir"
    fun bloqueoDetalle(version: String?, limite: java.time.Instant, zona: java.time.ZoneId = java.time.ZoneId.systemDefault()) =
        "La versión ${version ?: "nueva"} es obligatoria desde el ${java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(limite.atZone(zona))}. " +
            "Hasta actualizar, Shalev no se puede usar."
    const val BLOQUEO_SECUNDARIA = "Pon los dos teléfonos en la misma red (wifi o datos móviles) y toca «Actualizar desde la principal»."
    const val ACTUALIZAR_DESDE_PRINCIPAL = "Actualizar desde la principal"
    const val SIN_CONEXION_PRINCIPAL = "Aún no se ve la app principal. Compartid la misma red (wifi o datos móviles) y sincroniza."
    const val EXPORTAR_RESPALDO = "Exportar respaldo"
    const val CERRAR_TURNO = "Cerrar turno"
    const val CANCELAR = "Cancelar descarga"
    const val DESCARGANDO = "Descargando la actualización…"
    const val PERMISO = "Para instalar, activa «Permitir de esta fuente» para Shalev y vuelve a tocar Actualizar."
    const val SIN_NOVEDADES = "Tienes la versión más reciente."
    const val SIN_REPO = "Las actualizaciones automáticas aún no están configuradas en esta versión."
    const val ERROR_RED = "No se pudo consultar ahora. Se reintentará al abrir la app."
    const val ERROR_DESCARGA = "La descarga no terminó o el archivo no coincide. Vuelve a intentarlo."
    const val INSTALACION_CANCELADA = "La instalación no se completó. Puedes volver a tocar Actualizar."
    const val BUSCAR = "Actualizar"
    /** 0.31.0 (P5): resultados del cuadro de «Actualizar». */
    const val RESULTADO_ACTUALIZADO = "Actualizado"
    const val RESULTADO_AL_DIA = "Estas al día"
    const val RESULTADO_SIN_CONEXION = "Sin conexión"
    const val COMPROBANDO = "Comprobando si hay una versión nueva…"
    const val BUSCAR_DETALLE_CORTO = "Se comprueba si hay una versión nueva y, si la hay, se descarga y se instala."
    const val GUARDANDO_RESPALDO = "Guardando un respaldo de tus datos antes de actualizar…"
    const val ERROR_RESPALDO_PREVIO = "No se pudo guardar el respaldo de tus datos, así que no se actualizó. Libera espacio en el teléfono e inténtalo de nuevo."
    const val RESPALDO_RESTAURADO = "Shalev se actualizó y tus datos se restauraron desde el respaldo."
    const val RESPALDO_NO_RESTAURADO = "Shalev se actualizó, pero no se pudo restaurar el respaldo. Tus datos siguen como estaban; se reintentará al abrir la app."

    private val LINEA_TECNICA = Regex("github|repositorio|repository|\\brepo\\b", RegexOption.IGNORE_CASE)

    /** Notas de la versión para mostrar: sin las líneas que hablan del repositorio o de su alojamiento. */
    fun notasVisibles(notas: String): String =
        notas.lines().filterNot { LINEA_TECNICA.containsMatchIn(it) }.joinToString("\n").trim()
    const val BUSCAR_AHORA = "Buscar ahora"
}

/**
 * 0.25.0 (§6): consulta semanal al abrir la app, descarga (GitHub o, en una secundaria, la principal por la red local),
 * comprobación e instalación. Singleton para que Inicio y Ajustes compartan la misma descarga; sin trabajo en segundo
 * plano (si la app se cierra, la descarga se reanuda al volver).
 */
@Singleton
class GestorActualizacion @Inject constructor(
    @ApplicationContext private val context: Context,
    private val comprobar: ComprobarActualizaciones,
    private val github: ActualizacionesRepository,
    private val estadoApp: EstadoAppRepository,
    private val secundaria: SecundariaRepository,
    private val instalador: InstaladorApk,
    private val info: InfoApp,
    private val obligatoria: EstadoActualizacionObligatoria,
    private val respaldoPrevio: RespaldoPrevio,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val local = MutableStateFlow(EstadoActualizacion(repoConfigurado = github.repoConfigurado))
    private val mensajes = Channel<String>(Channel.BUFFERED)
    val eventos: Flow<String> = mensajes.receiveAsFlow()
    private var comprobado = false
    private var descarga: Job? = null
    private var apkListo: File? = null
    /** Trabajo del cuadro de «Actualizar»: cerrarlo lo cancela para que no reaparezca solo. */
    private var procesoJob: Job? = null
    /** 1.0.1: cierre automático del cuadro tras «Actualizado» o «Estas al día» (null = no hay cierre pendiente). */
    private var cierreAuto: Job? = null

    /** Re-evalúa el plazo cada hora con la app abierta (el día 30 llega sin reiniciar). */
    private val reloj = flow { while (true) { emit(Unit); delay(3_600_000) } }

    val estado: StateFlow<EstadoActualizacion> = combine(
        local,
        estadoApp.estado.catch { emit(cu.shalev.domain.model.EstadoApp()) },
        secundaria.estado,
        reloj,
    ) { l, e, s, _ ->
        val hayNueva = obligatoria.hayNueva(e, info.versionName, s.vinculada, github.repoConfigurado, s.apk.takeIf { s.vinculada }, info.versionCode)
        // 0.26.0 (§6): primera detección → empieza el plazo; ya actualizada → se borra. Una más nueva no lo reinicia.
        if (ActualizacionObligatoria.necesitaRegistro(e, info.versionName, hayNueva)) {
            scope.launch { seguro { obligatoria.registrar(info.versionName, hayNueva) } }
        }
        l.copy(
            disponible = Actualizaciones.aviso(e, info.versionName),
            desdePrincipal = s.apk?.takeIf { s.vinculada && it.versionCode > info.versionCode },
            obligatoria = obligatoria.estado(e, info.versionName, hayNueva),
            esSecundaria = s.vinculada,
        )
    }.stateIn(scope, SharingStarted.Eagerly, local.value)

    /** Al abrir la app (una vez por proceso). Borra APK descargados que ya no hacen falta. */
    fun alAbrir() {
        if (comprobado) return
        comprobado = true
        scope.launch {
            // Primero se restaura el respaldo guardado antes de actualizar (si la app acaba de actualizarse).
            when (seguro { respaldoPrevio.restaurarSiCorresponde(RespaldoPrevio.clave(info)) }) {
                RespaldoPrevio.Restauracion.RESTAURADO -> mensajes.trySend(TextosActualizacion.RESPALDO_RESTAURADO)
                RespaldoPrevio.Restauracion.FALLO -> mensajes.trySend(TextosActualizacion.RESPALDO_NO_RESTAURADO)
                else -> Unit
            }
            limpiarDescargasViejas()
            seguro { comprobar(info.versionName) }
        }
    }

    /** «Buscar ahora» (Ajustes). */
    fun buscarAhora() {
        if (!github.repoConfigurado) { mensajes.trySend(TextosActualizacion.SIN_REPO); return }
        if (local.value.buscando) return
        scope.launch {
            local.update { it.copy(buscando = true) }
            val r = seguro { comprobar(info.versionName, forzar = true) }
            local.update { it.copy(buscando = false) }
            mensajes.trySend(
                when {
                    r == null || r.error -> TextosActualizacion.ERROR_RED
                    r.aviso != null -> TextosActualizacion.disponible(r.aviso!!.version, r.aviso!!.bytes)
                    else -> TextosActualizacion.SIN_NOVEDADES
                },
            )
        }
    }

    /** «Más tarde»: oculta la tarjeta hasta mañana. La fecha límite no se mueve (§6). */
    fun descartar() {
        scope.launch { seguro { obligatoria.aplazar() } }
    }

    fun cancelar() {
        descarga?.cancel()
        descarga = null
        local.update { it.copy(progreso = null) }
    }

    /**
     * Descarga (o reutiliza) y abre el instalador. Solo se lanza desde Inicio o Ajustes, nunca durante una venta (§6.3).
     */
    fun actualizar() {
        if (descarga?.isActive == true) return
        if (!instalador.puedeInstalar()) {
            local.update { it.copy(necesitaPermiso = true) }
            mensajes.trySend(TextosActualizacion.PERMISO)
            runCatching { context.startActivity(instalador.intentPermiso()) }
            return
        }
        local.update { it.copy(necesitaPermiso = false) }
        apkListo?.takeIf { it.exists() }?.let { apk -> descarga = scope.launch { instalar(apk) }; return }
        // conProceso = false: la descarga de Inicio no debe dejar abierto el cuadro de «Actualizar» de Ajustes.
        descarga = scope.launch { descargarEInstalar(estado.value, conMensajes = true, conProceso = false) }
    }

    /**
     * 0.31.0 (P5): «Actualizar» de Ajustes. Comprueba si hay versión nueva y, si la hay, la descarga y abre el
     * instalador. El cuadro de Ajustes muestra la barra circular mientras tanto y, al terminar, el resultado:
     * «Actualizado», «Estas al día» o «Sin conexión». 1.0.1: con «Actualizado» o «Estas al día» el cuadro **se cierra
     * solo** tras mostrar el resultado; «Sin conexión» se queda abierto para que el usuario lo lea y lo cierre.
     */
    fun actualizarAhora() {
        if (local.value.proceso?.enCurso == true) return
        procesoJob?.cancel()
        cierreAuto?.cancel() // 1.0.1: un proceso nuevo no lo debe cerrar el cierre automático pendiente del anterior
        procesoJob = scope.launch {
            local.update { it.copy(proceso = ProcesoActualizar.EnCurso(null)) }
            val r = if (github.repoConfigurado) seguro { comprobar(info.versionName, forzar = true) } else null
            when {
                r == null || r.error -> resultadoProceso(ProcesoActualizar.SinConexion)
                r.aviso == null -> resultadoProceso(ProcesoActualizar.AlDia)
                else -> {
                    val ok = descargarEInstalar(estado.value.copy(disponible = r.aviso), conMensajes = false, conProceso = true)
                    // La descarga pone ella sola el resultado; si ni llegó a empezar (ya había otra en curso),
                    // el cuadro se quedaría girando: aquí se cierra.
                    if (ok) resultadoProceso(ProcesoActualizar.Actualizado)
                    else resultadoProceso(ProcesoActualizar.SinConexion)
                }
            }
        }
    }

    /** Cierra el cuadro de «Actualizar»: cancela lo que estuviera en curso para que no reaparezca solo. */
    fun cerrarProceso() {
        procesoJob?.cancel()
        procesoJob = null
        cierreAuto?.cancel()
        cierreAuto = null
        local.update { it.copy(proceso = null) }
    }

    /**
     * Escribe el resultado del cuadro solo si sigue abierto: si el usuario lo cerró a mitad, el resultado
     * tardío no lo reabre solo (el cuadro se quedaba colgado).
     *
     * 1.0.1: al terminar con «Actualizado» o «Estas al día» el cuadro se cierra **solo** tras [CIERRE_AUTO_MS]
     * (tiempo de leer el resultado). El cierre solo surte efecto si ese resultado sigue a la vista: un proceso
     * nuevo abierto entretanto no se cierra.
     */
    private fun resultadoProceso(p: ProcesoActualizar) {
        local.update { if (it.proceso is ProcesoActualizar.EnCurso) it.copy(proceso = p) else it }
        if (p == ProcesoActualizar.Actualizado || p == ProcesoActualizar.AlDia) {
            cierreAuto?.cancel()
            cierreAuto = scope.launch {
                delay(CIERRE_AUTO_MS)
                local.update { if (it.proceso == p) it.copy(proceso = null) else it }
            }
        }
    }

    /**
     * Descarga el APK (informando del progreso) y abre el instalador. [conMensajes] = true en el camino normal
     * (Inicio y Ajustes por la tarjeta), donde cada paso se avisa con un mensaje; false en el cuadro circular,
     * que habla por su barra y su resultado. [conProceso] = true refleja el avance en [EstadoActualizacion.proceso]
     * (lo que ve el cuadro circular). Devuelve true si se llegó a abrir el instalador.
     */
    private suspend fun descargarEInstalar(
        e: EstadoActualizacion,
        conMensajes: Boolean = true,
        conProceso: Boolean = true,
    ): Boolean {
        if (descarga?.isActive == true) return false
        local.update { it.copy(progreso = 0f, proceso = if (conProceso) ProcesoActualizar.EnCurso(0f) else null) }
        val progreso: (Long, Long) -> Unit = { hecho, total ->
            if (total > 0) {
                val p = (hecho.toFloat() / total).coerceIn(0f, 1f)
                local.update { it.copy(progreso = p, proceso = if (conProceso) ProcesoActualizar.EnCurso(p) else null) }
            }
        }
        // El resultado solo se refleja en el cuadro circular si este proceso lo abrió y sigue abierto.
        val resultado: (ProcesoActualizar) -> Unit = { p -> if (conProceso) resultadoProceso(p) }
        val r: AppResult<File>? = when {
            // Una sola descarga por internet: la secundaria la pide primero a la principal.
            e.desdePrincipal != null -> seguro { secundaria.descargarApk(progreso) }
            e.disponible != null -> seguro { github.descargar(e.disponible, progreso) }
            else -> {
                if (e.esSecundaria && conMensajes) mensajes.trySend(TextosActualizacion.SIN_CONEXION_PRINCIPAL)
                null
            }
        }
        local.update { it.copy(progreso = null) }
        return when (r) {
            is AppResult.Ok -> {
                val rechazo = instalador.comprobar(r.value)
                if (rechazo != null) {
                    r.value.delete()
                    if (conMensajes) mensajes.trySend(rechazo.texto)
                    resultado(ProcesoActualizar.SinConexion)
                    false
                } else {
                    apkListo = r.value
                    local.update { it.copy(listo = true) }
                    resultado(ProcesoActualizar.Actualizado)
                    instalar(r.value)
                    true
                }
            }
            is AppResult.Err -> {
                if (conMensajes) mensajes.trySend(if (r.error is AppError.Red) TextosActualizacion.ERROR_RED else TextosActualizacion.ERROR_DESCARGA)
                resultado(ProcesoActualizar.SinConexion)
                false
            }
            null -> {
                resultado(if (e.esSecundaria) ProcesoActualizar.SinConexion else ProcesoActualizar.AlDia)
                false
            }
        }
    }

    /**
     * Antes de abrir el instalador se guarda un respaldo completo de los datos (se restaura al abrir la versión nueva).
     * Sin respaldo no se instala: es lo que protege los datos si algo sale mal.
     */
    private suspend fun instalar(apk: File) {
        mensajes.trySend(TextosActualizacion.GUARDANDO_RESPALDO)
        val listo = seguro { respaldoPrevio.crear(RespaldoPrevio.clave(info)) } == true
        if (!listo) { mensajes.trySend(TextosActualizacion.ERROR_RESPALDO_PREVIO); return }
        instalador.instalar(apk) { ok ->
            if (!ok) {
                respaldoPrevio.descartar() // no se instaló: el respaldo sobra
                mensajes.trySend(TextosActualizacion.INSTALACION_CANCELADA)
            }
        }
    }

    /** Tras actualizar, los APK descargados de versiones iguales o anteriores sobran (§6.3 paso 4). */
    private fun limpiarDescargasViejas() {
        val dir = File(context.cacheDir, "actualizacion")
        dir.listFiles()?.forEach { f ->
            val v = f.name.removePrefix("Shalev-").removeSuffix(".apk")
            // recibida.apk (de la principal) se conserva: su descarga se reanuda.
            if (f.name != "recibida.apk" && !Actualizaciones.esMasNueva(v, info.versionName)) runCatching { f.delete() }
        }
    }

    private suspend fun <T> seguro(bloque: suspend () -> T): T? = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** 1.0.1: cuánto se muestra el resultado («Actualizado» / «Estas al día») antes de cerrar el cuadro solo. */
        const val CIERRE_AUTO_MS = 1_500L
    }
}

/** Puente para Compose: Inicio y Ajustes leen el mismo [GestorActualizacion]. */
@HiltViewModel
class ActualizacionViewModel @Inject constructor(private val gestor: GestorActualizacion) : ViewModel() {
    val estado: StateFlow<EstadoActualizacion> = gestor.estado
    val eventos: Flow<String> = gestor.eventos
    fun alAbrir() = gestor.alAbrir()
    fun actualizar() = gestor.actualizar()
    fun descartar() = gestor.descartar()
    fun cancelar() = gestor.cancelar()
    fun buscarAhora() = gestor.buscarAhora()
    /** 0.31.0 (P5): «Actualizar» de Ajustes (cuadro con barra circular y resultado). */
    fun actualizarAhora() = gestor.actualizarAhora()
    fun cerrarProceso() = gestor.cerrarProceso()
}
